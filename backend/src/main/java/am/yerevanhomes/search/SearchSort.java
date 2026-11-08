package am.yerevanhomes.search;

import java.util.List;
import java.util.Map;

/**
 * Sort orders. Each ends with the listing id as a tiebreaker, so the order is total and
 * search_after paging never skips or repeats a listing that shares a price or score with another.
 */
public enum SearchSort {
    RELEVANCE(List.of(order("_score", "desc"), order("createdAt", "desc"), order("id", "asc"))),
    PRICE_ASC(List.of(order("priceUsd", "asc"), order("id", "asc"))),
    PRICE_DESC(List.of(order("priceUsd", "desc"), order("id", "asc"))),
    NEWEST(List.of(order("createdAt", "desc"), order("id", "asc")));

    private final List<Map<String, Object>> clauses;

    SearchSort(List<Map<String, Object>> clauses) {
        this.clauses = clauses;
    }

    List<Map<String, Object>> clauses() {
        return clauses;
    }

    /** Long form {@code {"field": {"order": "asc"}}}, which the Java client accepts for every field. */
    private static Map<String, Object> order(String field, String direction) {
        return Map.of(field, Map.of("order", direction));
    }
}
