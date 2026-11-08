package am.yerevanhomes.search;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import am.yerevanhomes.listing.District;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ListingSearchService {

    private static final int SUGGESTIONS = 6;

    private final ElasticsearchClient client;
    private final JsonMapper json;
    private final ListingQueryBuilder queryBuilder = new ListingQueryBuilder();

    public ListingSearchService(ElasticsearchClient client, JsonMapper json) {
        this.client = client;
        this.json = json;
    }

    public SearchResult search(SearchCriteria criteria) {
        SearchCursor cursor = criteria.cursor() == null ? null : SearchCursor.decode(criteria.cursor(), criteria.sort(), json);
        SearchResponse<ListingSearchDocument> response = execute(queryBuilder.build(criteria, cursor), ListingSearchDocument.class);

        List<Hit<ListingSearchDocument>> hits = response.hits().hits();
        List<SearchResult.Item> items = hits.stream().map(ListingSearchService::toItem).toList();
        long total = response.hits().total() == null ? items.size() : response.hits().total().value();

        // A full page means there may be more; the last hit's sort values are where the next page starts
        String next = null;
        if (hits.size() == criteria.size()) {
            List<Object> after = hits.getLast().sort().stream().map(ListingSearchService::plain).toList();
            next = new SearchCursor(criteria.sort(), after).encode(json);
        }
        return new SearchResult(total, items, facets(response.aggregations()), next);
    }

    public Suggestions suggest(String prefix) {
        String text = prefix.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        List<Suggestions.DistrictSuggestion> districts = Arrays.stream(District.values())
                .filter(d -> d.displayName().toLowerCase(Locale.ROOT).contains(lower))
                .limit(3)
                .map(d -> new Suggestions.DistrictSuggestion(d.name(), d.displayName()))
                .toList();

        Map<String, Object> body = Map.of(
                "size", SUGGESTIONS,
                "_source", List.of("id", "title", "districtName"),
                "query", Map.of("multi_match", Map.of(
                        "query", text,
                        // search_as_you_type: matches word prefixes anywhere in the title
                        "type", "bool_prefix",
                        "fields", List.of("title.suggest", "title.suggest._2gram", "title.suggest._3gram"))));
        List<Suggestions.ListingSuggestion> listings = execute(body, Suggestions.ListingSuggestion.class).hits().hits().stream()
                .map(Hit::source)
                .filter(s -> s != null)
                .toList();
        return new Suggestions(districts, listings);
    }

    private <T> SearchResponse<T> execute(Map<String, Object> body, Class<T> type) {
        try {
            String requestJson = json.writeValueAsString(body);
            SearchRequest request = SearchRequest.of(s -> s.index(ListingIndex.NAME).withJson(new StringReader(requestJson)));
            return client.search(request, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static SearchResult.Item toItem(Hit<ListingSearchDocument> hit) {
        ListingSearchDocument d = hit.source();
        Map<String, List<String>> highlight = hit.highlight();
        return new SearchResult.Item(d.id(), d.title(), d.district(), d.districtName(), d.address(), d.priceUsd(),
                d.rooms(), d.areaSqm(), d.floor(), d.totalFloors(), d.buildingType(), d.amenities(), d.furnished(),
                d.petsAllowed(), d.imageUrl(), d.createdAt(), first(highlight, "title"), first(highlight, "description"));
    }

    private static String first(Map<String, List<String>> highlight, String field) {
        List<String> fragments = highlight == null ? null : highlight.get(field);
        return fragments == null || fragments.isEmpty() ? null : fragments.getFirst();
    }

    private static SearchResult.Facets facets(Map<String, Aggregate> aggs) {
        List<SearchResult.Bucket> districts = values(aggs, ListingQueryBuilder.DISTRICT).sterms().buckets().array().stream()
                .map(b -> {
                    String value = b.key().stringValue();
                    return new SearchResult.Bucket(value, District.valueOf(value).displayName(), b.docCount());
                })
                .toList();
        List<SearchResult.Bucket> rooms = values(aggs, ListingQueryBuilder.ROOMS).lterms().buckets().array().stream()
                .map(b -> new SearchResult.Bucket(String.valueOf(b.key()), String.valueOf(b.key()), b.docCount()))
                .toList();
        List<SearchResult.Bucket> buildingTypes = values(aggs, ListingQueryBuilder.BUILDING_TYPE).sterms().buckets().array().stream()
                .map(b -> new SearchResult.Bucket(b.key().stringValue(), label(b.key().stringValue()), b.docCount()))
                .toList();
        Map<String, Aggregate> price = aggs.get(ListingQueryBuilder.PRICE).filter().aggregations();
        return new SearchResult.Facets(districts, rooms, buildingTypes,
                new SearchResult.PriceRange(whole(price.get("min").min().value()), whole(price.get("max").max().value())));
    }

    private static Aggregate values(Map<String, Aggregate> aggs, String facet) {
        return aggs.get(facet).filter().aggregations().get("values");
    }

    /** STONE becomes "Stone". */
    private static String label(String constant) {
        return constant.charAt(0) + constant.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Min/max of no documents comes back as null or infinity. */
    private static Integer whole(Double value) {
        return value == null || !Double.isFinite(value) ? null : (int) Math.round(value);
    }

    /** Converts a sort value to a plain JSON value for the cursor. */
    private static Object plain(FieldValue value) {
        if (value.isLong()) {
            return value.longValue();
        }
        if (value.isDouble()) {
            return value.doubleValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isNull()) {
            return null;
        }
        return value.stringValue();
    }
}
