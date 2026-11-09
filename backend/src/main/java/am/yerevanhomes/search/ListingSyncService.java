package am.yerevanhomes.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.mongodb.MongoCommandException;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.FullDocumentBeforeChange;

import am.yerevanhomes.listing.Listing;

/**
 * Keeps Elasticsearch in sync with MongoDB using a change stream.
 *
 * <ul>
 * <li>The resume token is saved after every event, so after a restart the stream continues
 * exactly where it stopped. Nothing is missed and nothing needs a full rebuild.</li>
 * <li>With no saved token (first start, or the history needed to resume is gone) the stream is
 * opened <em>before</em> a full reindex. Changes made during the reindex are then replayed by the
 * stream, and versioned writes make the overlap harmless.</li>
 * <li>Deletes read the deleted document's last version from the change stream pre-image.</li>
 * <li>Any failure restarts the loop from the last saved token with a growing backoff.</li>
 * </ul>
 */
@Component
public class ListingSyncService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ListingSyncService.class);

    /** ChangeStreamHistoryLost, ChangeStreamFatalError, InvalidResumeToken: the token can't be used. */
    private static final Set<Integer> TOKEN_UNUSABLE = Set.of(286, 280, 260);
    private static final int REINDEX_BATCH = 500;

    private final MongoTemplate mongo;
    private final ListingIndex index;
    private final SyncStateStore state;
    private final boolean enabled;

    private volatile boolean running;
    /** True while the index exists and the change stream is open, so search results are current. */
    private volatile boolean ready;
    private volatile Thread worker;
    private volatile MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor;

    public ListingSyncService(MongoTemplate mongo, ListingIndex index, SyncStateStore state,
            @Value("${app.sync.enabled:true}") boolean enabled) {
        this.mongo = mongo;
        this.index = index;
        this.state = state;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled) {
            log.info("Listing sync is disabled");
            return;
        }
        running = true;
        worker = Thread.ofVirtual().name("listing-sync").start(this::loop);
    }

    @Override
    public void stop() {
        running = false;
        MongoChangeStreamCursor<?> current = cursor;
        if (current != null) {
            current.close();
        }
        Thread thread = worker;
        if (thread != null) {
            try {
                thread.join(Duration.ofSeconds(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void loop() {
        long backoffMs = 500;
        while (running) {
            try {
                follow();
                backoffMs = 500;
            } catch (MongoCommandException e) {
                if (TOKEN_UNUSABLE.contains(e.getErrorCode())) {
                    log.warn("Saved resume token can't be used ({}), rebuilding the index", e.getErrorCodeName());
                    state.clear();
                    continue;
                }
                backoffMs = pause(e, backoffMs);
            } catch (RuntimeException e) {
                if (!running) {
                    break;
                }
                backoffMs = pause(e, backoffMs);
            }
        }
    }

    private void follow() {
        // Done here rather than at startup, so the app starts even if Elasticsearch isn't up yet;
        // the loop retries with backoff until both stores are reachable
        index.createIfMissing();
        enablePreImages();
        BsonDocument token = state.loadResumeToken();
        ChangeStreamIterable<Document> stream = mongo.getCollection("listings").watch()
                .fullDocument(FullDocument.UPDATE_LOOKUP)
                .fullDocumentBeforeChange(FullDocumentBeforeChange.WHEN_AVAILABLE)
                .maxAwaitTime(500, TimeUnit.MILLISECONDS);
        if (token != null) {
            stream = stream.resumeAfter(token);
        }

        // The cursor is opened before a reindex, so changes made while reindexing are already
        // captured by the stream and get replayed afterwards
        try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> c = stream.cursor()) {
            cursor = c;
            if (token == null) {
                ready = false;
                reindexAll();
                // A brand-new stream may have no token yet; then it is saved with the first event
                if (c.getResumeToken() != null) {
                    state.saveResumeToken(c.getResumeToken());
                }
            }
            ready = true;
            while (running) {
                ChangeStreamDocument<Document> event = c.tryNext();
                if (event == null) {
                    continue;
                }
                if (!apply(event)) {
                    state.clear();
                    return;
                }
                state.saveResumeToken(event.getResumeToken());
            }
        } finally {
            cursor = null;
            ready = false;
        }
    }

    /** @return false if the stream ended (collection dropped or renamed) and needs a full rebuild */
    boolean apply(ChangeStreamDocument<Document> event) {
        switch (event.getOperationType()) {
            case INSERT, UPDATE, REPLACE -> {
                Document full = event.getFullDocument();
                // null means the listing was deleted before we looked it up; its delete event follows
                if (full != null) {
                    index.index(mongo.getConverter().read(Listing.class, full));
                }
            }
            case DELETE -> {
                String id = idOf(event.getDocumentKey().get("_id"));
                Document before = event.getFullDocumentBeforeChange();
                Number version = before == null ? null : before.get("version", Number.class);
                Long lastVersion = version == null ? null : version.longValue();
                index.delete(id, lastVersion);
            }
            case DROP, RENAME, DROP_DATABASE, INVALIDATE -> {
                log.warn("Change stream ended with {}, rebuilding the index", event.getOperationType());
                return false;
            }
            default -> {
                // Index and other DDL events don't affect search
            }
        }
        return true;
    }

    /** Copies every listing into Elasticsearch. Safe to run while the stream is live. */
    public void reindexAll() {
        long started = System.currentTimeMillis();
        int written = 0;
        int stale = 0;
        List<Listing> batch = new ArrayList<>(REINDEX_BATCH);
        try (Stream<Listing> listings = mongo.stream(new Query(), Listing.class)) {
            for (Listing listing : (Iterable<Listing>) listings::iterator) {
                batch.add(listing);
                if (batch.size() == REINDEX_BATCH) {
                    ListingIndex.BulkResult result = index.bulkIndex(batch);
                    written += result.written();
                    stale += result.stale();
                    batch.clear();
                }
            }
        }
        ListingIndex.BulkResult result = index.bulkIndex(batch);
        written += result.written();
        stale += result.stale();
        log.info("Reindexed listings: {} written, {} already up to date, {} ms",
                written, stale, System.currentTimeMillis() - started);
    }

    private void enablePreImages() {
        if (!mongo.collectionExists("listings")) {
            mongo.createCollection("listings");
        }
        mongo.executeCommand(new Document("collMod", "listings")
                .append("changeStreamPreAndPostImages", new Document("enabled", true)));
    }

    private static String idOf(BsonValue id) {
        return id.isObjectId() ? id.asObjectId().getValue().toHexString() : id.asString().getValue();
    }

    private long pause(Exception e, long backoffMs) {
        log.warn("Listing sync failed, retrying in {} ms", backoffMs, e);
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            running = false;
        }
        return Math.min(backoffMs * 2, 30_000);
    }
}
