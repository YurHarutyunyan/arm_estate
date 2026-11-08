package am.yerevanhomes.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the Elasticsearch search body for a {@link SearchCriteria}.
 *
 * <p>Filters are not part of the main query. They go into {@code post_filter}, and each facet is a
 * filter aggregation that applies every filter <em>except its own</em>. With "Kentron" selected the
 * district facet still counts the other districts, so the user sees what switching would give,
 * while the rooms facet only counts listings in Kentron.
 */
class ListingQueryBuilder {

    static final String DISTRICT = "district";
    static final String ROOMS = "rooms";
    static final String BUILDING_TYPE = "buildingType";
    static final String PRICE = "price";

    /** Text fields with boosts. Each text field is indexed with English, Russian and Armenian stemming. */
    private static final List<String> TEXT_FIELDS = List.of(
            "title^3", "title.en^3", "title.ru^3", "title.hy^3",
            "description", "description.en", "description.ru", "description.hy",
            "districtName^2", "address");

    Map<String, Object> build(SearchCriteria criteria, SearchCursor cursor) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", criteria.size());
        body.put("track_total_hits", true);
        body.put("query", query(criteria));
        body.put("post_filter", bool(filters(criteria, null)));
        body.put("aggs", facets(criteria));
        body.put("sort", criteria.sort().clauses());
        if (cursor != null) {
            body.put("search_after", cursor.after());
        }
        if (criteria.hasText()) {
            body.put("highlight", highlight());
        }
        return body;
    }

    private Map<String, Object> query(SearchCriteria criteria) {
        if (!criteria.hasText()) {
            return Map.of("match_all", Map.of());
        }
        String text = criteria.text();
        Map<String, Object> words = Map.of("multi_match", ordered(
                "query", text,
                "fields", TEXT_FIELDS,
                "type", "best_fields",
                // AUTO allows 1 typo in 3-5 letter words and 2 in longer ones; first letter must match
                "fuzziness", "AUTO",
                "prefix_length", 1,
                // Short queries need every word, longer ones three quarters of them
                "minimum_should_match", "2<75%"));
        Map<String, Object> phrase = Map.of("match_phrase", Map.of("title", Map.of("query", text, "slop", 2, "boost", 4)));
        Map<String, Object> textQuery = Map.of("bool", Map.of("must", List.of(words), "should", List.of(phrase)));

        // Newer listings get up to twice the score; a listing a month old gets about 1.5x
        Map<String, Object> recency = Map.of(
                "gauss", Map.of("createdAt", Map.of("origin", "now", "scale", "30d", "decay", 0.5)),
                "weight", 1);
        Map<String, Object> base = Map.of("weight", 1);
        return Map.of("function_score", ordered(
                "query", textQuery,
                "functions", List.of(recency, base),
                "score_mode", "sum",
                "boost_mode", "multiply"));
    }

    /** All filter clauses, leaving out the one named {@code except} (used by its own facet). */
    List<Map<String, Object>> filters(SearchCriteria c, String except) {
        List<Map<String, Object>> filters = new ArrayList<>();
        if (!c.districts().isEmpty() && !DISTRICT.equals(except)) {
            filters.add(terms("district", c.districts().stream().map(Enum::name).toList()));
        }
        if (!c.rooms().isEmpty() && !ROOMS.equals(except)) {
            filters.add(terms("rooms", c.rooms()));
        }
        if (!c.buildingTypes().isEmpty() && !BUILDING_TYPE.equals(except)) {
            filters.add(terms("buildingType", c.buildingTypes().stream().map(Enum::name).toList()));
        }
        if ((c.minPrice() != null || c.maxPrice() != null) && !PRICE.equals(except)) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (c.minPrice() != null) {
                range.put("gte", c.minPrice());
            }
            if (c.maxPrice() != null) {
                range.put("lte", c.maxPrice());
            }
            filters.add(Map.of("range", Map.of("priceUsd", range)));
        }
        // Every requested amenity must be present
        for (String amenity : c.amenities()) {
            filters.add(Map.of("term", Map.of("amenities", amenity)));
        }
        if (c.furnished() != null) {
            filters.add(Map.of("term", Map.of("furnished", c.furnished())));
        }
        if (c.petsAllowed() != null) {
            filters.add(Map.of("term", Map.of("petsAllowed", c.petsAllowed())));
        }
        return filters;
    }

    private Map<String, Object> facets(SearchCriteria c) {
        Map<String, Object> aggs = new LinkedHashMap<>();
        aggs.put(DISTRICT, facet(c, DISTRICT, Map.of("terms", Map.of("field", "district", "size", 20))));
        aggs.put(ROOMS, facet(c, ROOMS, Map.of("terms", Map.of("field", "rooms", "size", 10, "order", Map.of("_key", "asc")))));
        aggs.put(BUILDING_TYPE, facet(c, BUILDING_TYPE, Map.of("terms", Map.of("field", "buildingType", "size", 5))));
        aggs.put(PRICE, Map.of(
                "filter", bool(filters(c, PRICE)),
                "aggs", Map.of(
                        "min", Map.of("min", Map.of("field", "priceUsd")),
                        "max", Map.of("max", Map.of("field", "priceUsd")))));
        return aggs;
    }

    private Map<String, Object> facet(SearchCriteria c, String name, Map<String, Object> values) {
        return Map.of("filter", bool(filters(c, name)), "aggs", Map.of("values", values));
    }

    private static Map<String, Object> highlight() {
        return ordered(
                // Escapes HTML in the listing text so only our <mark> tags reach the browser
                "encoder", "html",
                "pre_tags", List.of("<mark>"),
                "post_tags", List.of("</mark>"),
                "require_field_match", false,
                "fields", List.of(
                        Map.of("title", Map.of("number_of_fragments", 0)),
                        Map.of("description", Map.of("fragment_size", 160, "number_of_fragments", 1))));
    }

    private static Map<String, Object> bool(List<Map<String, Object>> filters) {
        return filters.isEmpty() ? Map.of("match_all", Map.of()) : Map.of("bool", Map.of("filter", filters));
    }

    private static Map<String, Object> terms(String field, List<?> values) {
        return Map.of("terms", Map.of(field, values));
    }

    /** Map that keeps insertion order, so the JSON sent to Elasticsearch is readable in logs. */
    private static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
