package am.yerevanhomes;

import java.util.List;

import am.yerevanhomes.listing.BuildingType;
import am.yerevanhomes.listing.District;
import am.yerevanhomes.listing.ListingRequest;

/** Sample listings for tests. */
public final class TestListings {

    private TestListings() {
    }

    public static ListingRequest validRequest(int price) {
        return request("Bright 2-room apartment near Cascade", "Renovated, quiet street, close to the Opera.",
                District.KENTRON, price, 2);
    }

    public static ListingRequest request(String title, String description, District district, int price, int rooms) {
        return new ListingRequest(title, description, district, "Tamanyan St 5", price, rooms, 30 + rooms * 20, 4, 9,
                BuildingType.STONE, List.of("balcony", "elevator"), true, false, null);
    }
}
