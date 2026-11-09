package am.yerevanhomes.search;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import am.yerevanhomes.listing.Listing;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;

/**
 * Writes listings to Elasticsearch.
 *
 * <p>Every write uses external versioning based on the MongoDB document version. Elasticsearch
 * rejects a write whose version is not higher than the stored one, so a late or replayed event
 * (a retry, or a full reindex racing with live updates) can never replace newer data. Deletes use
 * a version above the last indexed one, which leaves a tombstone that blocks a stale re-insert.
 */
@Component
public class ListingIndex {

    public static final String NAME = "listings";

    private static final Logger log = LoggerFactory.getLogger(ListingIndex.class);
    private static final int CONFLICT = 409;

    private final ElasticsearchClient client;

    public ListingIndex(ElasticsearchClient client) {
        this.client = client;
    }

    /** Elasticsearch version for a MongoDB version. Mongo starts at 0; shift by one to keep it positive. */
    static long indexVersion(long mongoVersion) {
        return mongoVersion + 1;
    }

    /** Version for deleting a listing whose last MongoDB version was {@code mongoVersion}. */
    static long deleteVersion(long mongoVersion) {
        return indexVersion(mongoVersion) + 1;
    }

    /**
     * Creates the index if needed and waits until its shard can serve reads and writes.
     *
     * @return true if this call created the index, so it is empty and needs a full reindex
     */
    public boolean createIfMissing() {
        try {
            boolean created = false;
            if (!client.indices().exists(e -> e.index(NAME)).value()) {
                try (InputStream json = new ClassPathResource("elasticsearch/listings-index.json").getInputStream()) {
                    client.indices().create(c -> c.index(NAME).withJson(json));
                    log.info("Created Elasticsearch index '{}'", NAME);
                    created = true;
                } catch (ElasticsearchException e) {
                    // Another instance may have created it at the same moment
                    if (!"resource_already_exists_exception".equals(e.error().type())) {
                        throw e;
                    }
                }
            }
            // A just-created index answers 503 until its primary shard is started
            client.cluster().health(h -> h.index(NAME).waitForStatus(HealthStatus.Yellow).timeout(t -> t.time("30s")));
            return created;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return true if written, false if Elasticsearch already had the same or a newer version */
    public boolean index(Listing listing) {
        try {
            client.index(i -> i.index(NAME)
                    .id(listing.getId())
                    .document(ListingSearchDocument.from(listing))
                    .version(indexVersion(listing.getVersion()))
                    .versionType(VersionType.External));
            return true;
        } catch (ElasticsearchException e) {
            if (e.status() == CONFLICT) {
                log.debug("Skipped stale index write for listing {} v{}", listing.getId(), listing.getVersion());
                return false;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param lastMongoVersion version of the deleted listing, or null if unknown (no pre-image);
     *        then the version currently in Elasticsearch is used
     * @return true if deleted, false if Elasticsearch already had a newer version
     */
    public boolean delete(String id, Long lastMongoVersion) {
        try {
            long version;
            if (lastMongoVersion != null) {
                version = deleteVersion(lastMongoVersion);
            } else {
                GetResponse<Void> current = client.get(g -> g.index(NAME).id(id).source(s -> s.fetch(false)), Void.class);
                if (!current.found()) {
                    return false;
                }
                version = current.version() + 1;
            }
            client.delete(d -> d.index(NAME)
                    .id(id)
                    .version(version)
                    .versionType(VersionType.External));
            return true;
        } catch (ElasticsearchException e) {
            if (e.status() == CONFLICT) {
                return false;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Indexes many listings in one request. Stale ones are skipped the same way as in {@link #index}. */
    public BulkResult bulkIndex(List<Listing> listings) {
        if (listings.isEmpty()) {
            return new BulkResult(0, 0);
        }
        List<BulkOperation> operations = listings.stream()
                .map(l -> BulkOperation.of(op -> op.index(i -> i.index(NAME)
                        .id(l.getId())
                        .document(ListingSearchDocument.from(l))
                        .version(indexVersion(l.getVersion()))
                        .versionType(VersionType.External))))
                .toList();
        try {
            BulkResponse response = client.bulk(b -> b.operations(operations));
            int written = 0;
            int stale = 0;
            for (BulkResponseItem item : response.items()) {
                if (item.error() == null) {
                    written++;
                } else if (item.status() == CONFLICT) {
                    stale++;
                } else {
                    throw new IllegalStateException("Bulk index failed for " + item.id() + ": " + item.error().reason());
                }
            }
            return new BulkResult(written, stale);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Optional<ListingSearchDocument> find(String id) {
        try {
            GetResponse<ListingSearchDocument> response = client.get(g -> g.index(NAME).id(id), ListingSearchDocument.class);
            return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Number of indexed listings, or 0 while the index doesn't exist yet. */
    public long count() {
        try {
            client.indices().refresh(r -> r.index(NAME));
            return client.count(c -> c.index(NAME)).count();
        } catch (ElasticsearchException e) {
            if (isIndexMissing(e)) {
                return 0;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean isIndexMissing(ElasticsearchException e) {
        return "index_not_found_exception".equals(e.error().type());
    }

    public record BulkResult(int written, int stale) {
    }
}
