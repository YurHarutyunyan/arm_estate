package am.yerevanhomes.search;

import java.util.List;

import am.yerevanhomes.common.BadRequestException;
import am.yerevanhomes.listing.BuildingType;
import am.yerevanhomes.listing.District;

/** What the user searches for. Empty lists and nulls mean "no filter". */
public record SearchCriteria(
        String text,
        List<District> districts,
        Integer minPrice,
        Integer maxPrice,
        List<Integer> rooms,
        List<BuildingType> buildingTypes,
        List<String> amenities,
        Boolean furnished,
        Boolean petsAllowed,
        SearchSort sort,
        int size,
        String cursor) {

    public static final int MAX_SIZE = 50;

    public SearchCriteria {
        text = text == null || text.isBlank() ? null : text.strip();
        districts = districts == null ? List.of() : List.copyOf(districts);
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        buildingTypes = buildingTypes == null ? List.of() : List.copyOf(buildingTypes);
        amenities = amenities == null ? List.of() : amenities.stream().map(String::strip).filter(a -> !a.isEmpty()).toList();
        // Sorting by relevance only makes sense with text; without it, newest first
        sort = sort != null ? sort : (text != null ? SearchSort.RELEVANCE : SearchSort.NEWEST);
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_SIZE);
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }
        if (text != null && text.length() > 200) {
            throw new BadRequestException("q must be at most 200 characters");
        }
    }

    public boolean hasText() {
        return text != null;
    }
}
