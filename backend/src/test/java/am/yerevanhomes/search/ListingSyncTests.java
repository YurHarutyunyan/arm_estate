package am.yerevanhomes.search;

import static am.yerevanhomes.TestListings.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import am.yerevanhomes.TestcontainersConfiguration;
import am.yerevanhomes.listing.Listing;
import am.yerevanhomes.listing.ListingRepository;
import am.yerevanhomes.listing.ListingService;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ListingSyncTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    ListingService service;

    @Autowired
    ListingRepository repository;

    @Autowired
    ListingIndex index;

    @Autowired
    ListingSyncService sync;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        await().atMost(TIMEOUT).until(() -> index.count() == 0);
    }

    @Test
    void createUpdateAndDeleteReachTheIndex() {
        Listing listing = service.create(validRequest(900));
        await().atMost(TIMEOUT).until(() -> priceInIndex(listing.getId()).equals(Optional.of(900)));

        service.update(listing.getId(), 0, validRequest(1200));
        await().atMost(TIMEOUT).until(() -> priceInIndex(listing.getId()).equals(Optional.of(1200)));

        service.delete(listing.getId(), 1);
        await().atMost(TIMEOUT).until(() -> index.find(listing.getId()).isEmpty());
    }

    @Test
    void staleWriteCannotOverwriteNewerData() {
        Listing listing = service.create(validRequest(900));
        Listing v1 = service.update(listing.getId(), 0, validRequest(1500));
        await().atMost(TIMEOUT).until(() -> priceInIndex(v1.getId()).equals(Optional.of(1500)));

        // A late event or a slow reindex delivers version 0 after version 1 is already indexed
        boolean written = index.index(listing);

        assertThat(written).isFalse();
        assertThat(priceInIndex(listing.getId())).contains(1500);
    }

    @Test
    void staleWriteCannotResurrectADeletedListing() {
        Listing listing = service.create(validRequest(900));
        await().atMost(TIMEOUT).until(() -> index.find(listing.getId()).isPresent());
        service.delete(listing.getId(), 0);
        await().atMost(TIMEOUT).until(() -> index.find(listing.getId()).isEmpty());

        // The delete left a versioned tombstone, so replaying the old insert is rejected
        boolean written = index.index(listing);

        assertThat(written).isFalse();
        assertThat(index.find(listing.getId())).isEmpty();
    }

    @Test
    void changesMadeWhileSyncIsStoppedAreCaughtUpOnRestart() {
        Listing kept = service.create(validRequest(900));
        Listing removed = service.create(validRequest(950));
        await().atMost(TIMEOUT).until(() -> index.find(removed.getId()).isPresent());

        sync.stop();
        try {
            service.update(kept.getId(), 0, validRequest(1300));
            service.delete(removed.getId(), 0);
            Listing added = service.create(validRequest(700));

            // Nothing moves while the sync is down
            assertThat(priceInIndex(kept.getId())).contains(900);
            assertThat(index.find(added.getId())).isEmpty();

            sync.start();

            // It resumes from the saved token and applies all three changes
            await().atMost(TIMEOUT).untilAsserted(() -> {
                assertThat(priceInIndex(kept.getId())).contains(1300);
                assertThat(index.find(removed.getId())).isEmpty();
                assertThat(index.find(added.getId())).isPresent();
            });
        } finally {
            if (!sync.isRunning()) {
                sync.start();
            }
        }
    }

    @Test
    void fullReindexIsIdempotentAndSkipsUpToDateDocuments() {
        List<Listing> listings = List.of(service.create(validRequest(800)), service.create(validRequest(850)));
        await().atMost(TIMEOUT).until(() -> index.count() == 2);

        ListingIndex.BulkResult result = index.bulkIndex(repository.findAll());

        assertThat(result.written()).isZero();
        assertThat(result.stale()).isEqualTo(listings.size());
        assertThat(index.count()).isEqualTo(2);
    }

    private Optional<Integer> priceInIndex(String id) {
        return index.find(id).map(ListingSearchDocument::priceUsd);
    }
}
