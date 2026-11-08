package am.yerevanhomes.search;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import am.yerevanhomes.listing.BuildingType;
import am.yerevanhomes.listing.District;

/**
 * Search and autocomplete. Multi-value filters repeat the parameter:
 * {@code /api/listings/search?q=balcony&district=KENTRON&district=ARABKIR&rooms=2&maxPrice=1200}
 */
@RestController
@RequestMapping("/api/listings")
public class ListingSearchController {

    private final ListingSearchService service;

    public ListingSearchController(ListingSearchService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public SearchResult search(
            @RequestParam(required = false) String q,
            @RequestParam(name = "district", required = false) List<District> districts,
            @RequestParam(required = false) @Min(0) Integer minPrice,
            @RequestParam(required = false) @Min(0) Integer maxPrice,
            @RequestParam(required = false) List<@Min(1) @Max(10) Integer> rooms,
            @RequestParam(name = "buildingType", required = false) List<BuildingType> buildingTypes,
            @RequestParam(name = "amenity", required = false) List<@Size(max = 40) String> amenities,
            @RequestParam(required = false) Boolean furnished,
            @RequestParam(required = false) Boolean petsAllowed,
            @RequestParam(required = false) SearchSort sort,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {
        return service.search(new SearchCriteria(q, districts, minPrice, maxPrice, rooms, buildingTypes, amenities,
                furnished, petsAllowed, sort, size, cursor));
    }

    @GetMapping("/suggest")
    public Suggestions suggest(@RequestParam @NotBlank @Size(max = 100) String q) {
        return service.suggest(q);
    }
}
