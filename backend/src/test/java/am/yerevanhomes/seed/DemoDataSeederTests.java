package am.yerevanhomes.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import am.yerevanhomes.listing.Listing;

/** Unit tests for the generated data; no database needed. */
class DemoDataSeederTests {

    private final DemoDataSeeder seeder = new DemoDataSeeder(null, null, null, true, 0);

    @Test
    void generatedListingsPassTheApiValidationRules() {
        Random random = new Random(7);
        Instant now = Instant.now();
        List<Listing> listings = IntStream.range(0, 500).mapToObj(i -> seeder.generate(random, "owner", now)).toList();

        assertThat(listings).allSatisfy(l -> {
            assertThat(l.getPriceUsd()).isBetween(50, 20_000);
            assertThat(l.getRooms()).isBetween(1, 10);
            assertThat(l.getAreaSqm()).isBetween(10, 1_000);
            assertThat(l.getFloor()).isBetween(1, l.getTotalFloors());
            assertThat(l.getTitle()).isNotBlank().hasSizeLessThanOrEqualTo(120);
            assertThat(l.getAmenities()).doesNotHaveDuplicates().hasSizeLessThanOrEqualTo(20);
            assertThat(l.getCreatedAt()).isBeforeOrEqualTo(now).isAfter(now.minusSeconds(91L * 24 * 3600));
        });
    }

    @Test
    void sameSeedGivesTheSameData() {
        Instant now = Instant.now();
        Listing a = seeder.generate(new Random(42), "owner", now);
        Listing b = seeder.generate(new Random(42), "owner", now);

        assertThat(a.getTitle()).isEqualTo(b.getTitle());
        assertThat(a.getPriceUsd()).isEqualTo(b.getPriceUsd());
        assertThat(a.getAddress()).isEqualTo(b.getAddress());
    }

    @Test
    void centralDistrictsCostMoreOnAverage() {
        Random random = new Random(1);
        Instant now = Instant.now();
        List<Listing> listings = IntStream.range(0, 3000).mapToObj(i -> seeder.generate(random, "owner", now)).toList();

        double kentron = averagePerRoom(listings, "KENTRON");
        double nubarashen = averagePerRoom(listings, "NUBARASHEN");

        assertThat(kentron).isGreaterThan(nubarashen * 1.8);
    }

    private static double averagePerRoom(List<Listing> listings, String district) {
        return listings.stream().filter(l -> l.getDistrict().name().equals(district))
                .mapToDouble(l -> (double) l.getPriceUsd() / l.getRooms()).average().orElseThrow();
    }
}
