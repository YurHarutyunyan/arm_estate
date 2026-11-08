package am.yerevanhomes.search;

import java.time.Instant;
import java.util.List;

import am.yerevanhomes.listing.Listing;

/** The copy of a listing stored in Elasticsearch. Field names match listings-index.json. */
public record ListingSearchDocument(
        String id,
        String ownerId,
        String title,
        String description,
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
        Long updatedAt) {

    public static ListingSearchDocument from(Listing l) {
        return new ListingSearchDocument(l.getId(), l.getOwnerId(), l.getTitle(), l.getDescription(),
                l.getDistrict().name(), l.getDistrict().displayName(), l.getAddress(), l.getPriceUsd(), l.getRooms(),
                l.getAreaSqm(), l.getFloor(), l.getTotalFloors(), l.getBuildingType().name(),
                l.getAmenities(), l.isFurnished(), l.isPetsAllowed(), l.getImageUrl(),
                millis(l.getCreatedAt()), millis(l.getUpdatedAt()));
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
