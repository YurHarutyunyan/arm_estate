package am.yerevanhomes.listing;

import java.time.Instant;
import java.util.List;

public record ListingResponse(
        String id,
        String ownerId,
        String title,
        String description,
        District district,
        String address,
        int priceUsd,
        int rooms,
        int areaSqm,
        int floor,
        int totalFloors,
        BuildingType buildingType,
        List<String> amenities,
        boolean furnished,
        boolean petsAllowed,
        String imageUrl,
        Instant createdAt,
        Instant updatedAt) {

    public static ListingResponse from(Listing l) {
        return new ListingResponse(l.getId(), l.getOwnerId(), l.getTitle(), l.getDescription(), l.getDistrict(), l.getAddress(),
                l.getPriceUsd(), l.getRooms(), l.getAreaSqm(), l.getFloor(), l.getTotalFloors(), l.getBuildingType(),
                l.getAmenities(), l.isFurnished(), l.isPetsAllowed(), l.getImageUrl(), l.getCreatedAt(), l.getUpdatedAt());
    }
}
