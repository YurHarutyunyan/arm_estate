package am.yerevanhomes.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import am.yerevanhomes.common.BadRequestException;
import am.yerevanhomes.listing.District;

/** Unit tests for the generated query; no Elasticsearch needed. */
class ListingQueryBuilderTests {

    private final ListingQueryBuilder builder = new ListingQueryBuilder();

    @Test
    void eachFacetIgnoresOnlyItsOwnFilter() {
        SearchCriteria criteria = criteria("balcony", List.of(District.KENTRON), List.of(2), 500, 1500);

        assertThat(builder.filters(criteria, null)).hasSize(3);
        assertThat(builder.filters(criteria, ListingQueryBuilder.DISTRICT))
                .hasSize(2)
                .noneMatch(f -> f.toString().contains("district"));
        assertThat(builder.filters(criteria, ListingQueryBuilder.ROOMS))
                .hasSize(2)
                .noneMatch(f -> f.toString().contains("rooms"));
        assertThat(builder.filters(criteria, ListingQueryBuilder.PRICE))
                .hasSize(2)
                .noneMatch(f -> f.toString().contains("priceUsd"));
    }

    @Test
    void filtersAreAppliedAfterScoringSoFacetsSeeEverything() {
        Map<String, Object> body = builder.build(criteria("balcony", List.of(District.KENTRON), List.of(), null, null), null);

        assertThat(body.get("query").toString()).doesNotContain("KENTRON");
        assertThat(body.get("post_filter").toString()).contains("KENTRON");
    }

    @Test
    void everyRequestedAmenityIsRequired() {
        SearchCriteria criteria = new SearchCriteria(null, null, null, null, null, null,
                List.of("balcony", " parking "), null, null, null, 20, null);

        assertThat(builder.filters(criteria, null))
                .containsExactly(Map.of("term", Map.of("amenities", "balcony")),
                        Map.of("term", Map.of("amenities", "parking")));
    }

    @Test
    void withoutTextItMatchesAllAndSortsNewestFirst() {
        SearchCriteria criteria = criteria(null, List.of(), List.of(), null, null);
        Map<String, Object> body = builder.build(criteria, null);

        assertThat(criteria.sort()).isEqualTo(SearchSort.NEWEST);
        assertThat(body.get("query")).isEqualTo(Map.of("match_all", Map.of()));
        assertThat(body).doesNotContainKey("highlight");
    }

    @Test
    void withTextItSortsByRelevanceAndHighlights() {
        SearchCriteria criteria = criteria("  quiet balcony  ", List.of(), List.of(), null, null);
        Map<String, Object> body = builder.build(criteria, null);

        assertThat(criteria.text()).isEqualTo("quiet balcony");
        assertThat(criteria.sort()).isEqualTo(SearchSort.RELEVANCE);
        assertThat(body.get("query").toString()).contains("function_score", "fuzziness=AUTO", "title.hy^3");
        assertThat(body).containsKey("highlight");
    }

    @Test
    void cursorBecomesSearchAfter() {
        SearchCursor cursor = new SearchCursor(SearchSort.PRICE_ASC, List.of(900, "abc"));
        Map<String, Object> body = builder.build(new SearchCriteria(null, null, null, null, null, null, null, null,
                null, SearchSort.PRICE_ASC, 10, null), cursor);

        assertThat(body.get("search_after")).isEqualTo(List.of(900, "abc"));
    }

    @Test
    void rejectsInvalidCriteria() {
        assertThatThrownBy(() -> criteria(null, List.of(), List.of(), 2000, 1000))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("minPrice");
        assertThatThrownBy(() -> new SearchCriteria(null, null, null, null, null, null, null, null, null, null, 51, null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("size");
        assertThatThrownBy(() -> new SearchCriteria("x".repeat(201), null, null, null, null, null, null, null, null, null, 20, null))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("200");
    }

    private static SearchCriteria criteria(String text, List<District> districts, List<Integer> rooms,
            Integer minPrice, Integer maxPrice) {
        return new SearchCriteria(text, districts, minPrice, maxPrice, rooms, null, null, null, null, null, 20, null);
    }
}
