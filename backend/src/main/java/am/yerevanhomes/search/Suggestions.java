package am.yerevanhomes.search;

import java.util.List;

/** Autocomplete results: matching districts and listing titles. */
public record Suggestions(List<DistrictSuggestion> districts, List<ListingSuggestion> listings) {

    public record DistrictSuggestion(String value, String label) {
    }

    public record ListingSuggestion(String id, String title, String districtName) {
    }
}
