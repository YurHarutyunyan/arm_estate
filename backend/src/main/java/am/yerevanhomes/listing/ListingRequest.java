package am.yerevanhomes.listing;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body for creating or replacing a listing. */
public record ListingRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotNull District district,
        @NotBlank @Size(max = 200) String address,
        @NotNull @Min(50) @Max(20_000) Integer priceUsd,
        @NotNull @Min(1) @Max(10) Integer rooms,
        @NotNull @Min(10) @Max(1_000) Integer areaSqm,
        @NotNull @Min(0) @Max(40) Integer floor,
        @NotNull @Min(1) @Max(40) Integer totalFloors,
        @NotNull BuildingType buildingType,
        @Size(max = 20) List<@NotBlank @Size(max = 40) String> amenities,
        Boolean furnished,
        Boolean petsAllowed,
        @Size(max = 500) String imageUrl) {

    void applyTo(Listing listing) {
        listing.setTitle(title.strip());
        listing.setDescription(description.strip());
        listing.setDistrict(district);
        listing.setAddress(address.strip());
        listing.setPriceUsd(priceUsd);
        listing.setRooms(rooms);
        listing.setAreaSqm(areaSqm);
        listing.setFloor(floor);
        listing.setTotalFloors(totalFloors);
        listing.setBuildingType(buildingType);
        listing.setAmenities(amenities == null ? List.of() : amenities.stream().map(String::strip).distinct().toList());
        listing.setFurnished(Boolean.TRUE.equals(furnished));
        listing.setPetsAllowed(Boolean.TRUE.equals(petsAllowed));
        listing.setImageUrl(imageUrl);
    }
}
