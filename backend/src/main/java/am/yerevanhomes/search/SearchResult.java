package am.yerevanhomes.search;

import java.util.List;

/** One page of search results with facet counts and a cursor for the next page. */
public record SearchResult(long total, List<Item> items, Facets facets, String nextCursor) {

    public record Item(
            String id,
            String title,
            String district,
            String districtName,
            String address,
            int priceUsd,
            int rooms,
            int areaSqm,
            int floor,
            int totalFloors,
            String buildingType,
            List<String> amenities,
            boolean furnished,
            boolean petsAllowed,
            String imageUrl,
            Long createdAt,
            /** Title with matched words wrapped in &lt;mark&gt;, HTML-escaped. Null without a text query. */
            String titleHighlight,
            /** Best matching snippet of the description, HTML-escaped. Null if the description didn't match. */
            String descriptionHighlight) {
    }

    public record Facets(List<Bucket> districts, List<Bucket> rooms, List<Bucket> buildingTypes, PriceRange price) {
    }

    public record Bucket(String value, String label, long count) {
    }

    /** Lowest and highest price among listings matching every other filter. Null fields if none match. */
    public record PriceRange(Integer min, Integer max) {
    }
}
