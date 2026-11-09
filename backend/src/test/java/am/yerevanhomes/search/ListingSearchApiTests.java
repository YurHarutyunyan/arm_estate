package am.yerevanhomes.search;

import static am.yerevanhomes.TestListings.request;
import static am.yerevanhomes.TestListings.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

import am.yerevanhomes.TestcontainersConfiguration;
import am.yerevanhomes.listing.District;
import am.yerevanhomes.listing.ListingRepository;
import am.yerevanhomes.listing.ListingRequest;
import am.yerevanhomes.listing.ListingService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ListingSearchApiTests {

    @Autowired
    RestTestClient client;

    @Autowired
    ListingService listings;

    @Autowired
    ListingRepository repository;

    @Autowired
    ListingIndex index;

    @Autowired
    ListingSyncService sync;

    @BeforeEach
    void clean() {
        // The index is built in the background at startup; wait until search is current
        await().atMost(Duration.ofSeconds(60)).until(sync::isReady);
        repository.deleteAll();
        await().atMost(Duration.ofSeconds(15)).until(() -> index.count() == 0);
    }

    @Test
    void titleMatchesRankAboveDescriptionMatches() {
        String inDescription = create(request("Cozy flat in Arabkir", "Large balcony with a view of Ararat", District.ARABKIR, 700, 2));
        String inTitle = create(request("Apartment with balcony", "Renovated, close to the metro", District.ARABKIR, 700, 2));
        waitIndexed(2);

        assertThat(ids(search("q=balcony"))).containsExactly(inTitle, inDescription);
    }

    @Test
    void toleratesTypos() {
        String id = create(request("Spacious apartment near the Opera", "Quiet street", District.KENTRON, 900, 3));
        create(request("Studio in Nor Nork", "New building", District.NOR_NORK, 400, 1));
        waitIndexed(2);

        assertThat(ids(search("q=apartmnt"))).containsExactly(id);
        assertThat(ids(search("q=Kentorn"))).containsExactly(id);
    }

    @Test
    void understandsEnglishRussianAndArmenianWordForms() {
        String english = create(request("Two bedrooms with balconies", "Sunny", District.AVAN, 600, 2));
        String russian = create(request("Уютная квартира в центре", "Рядом метро", District.KENTRON, 800, 2));
        String armenian = create(request("Բնակարան կենտրոնում", "Լուսավոր", District.KENTRON, 850, 2));
        waitIndexed(3);

        assertThat(ids(search("q=balcony"))).containsExactly(english);
        assertThat(ids(search("q=квартиры"))).containsExactly(russian);
        assertThat(ids(search("q=բնակարաններ"))).containsExactly(armenian);
    }

    @Test
    void facetsStayCorrectWhenFiltersAreApplied() {
        create(request("Kentron A", "x", District.KENTRON, 800, 1));
        create(request("Kentron B", "x", District.KENTRON, 900, 2));
        create(request("Kentron C", "x", District.KENTRON, 1500, 2));
        create(request("Arabkir A", "x", District.ARABKIR, 600, 3));
        create(request("Arabkir B", "x", District.ARABKIR, 650, 2));
        waitIndexed(5);

        SearchResult result = search("district=KENTRON");

        assertThat(result.total()).isEqualTo(3);
        // The district facet ignores its own filter, so the user still sees what Arabkir offers
        assertThat(result.facets().districts())
                .extracting(SearchResult.Bucket::value, SearchResult.Bucket::count)
                .containsExactlyInAnyOrder(tuple("KENTRON", 3L),
                        tuple("ARABKIR", 2L));
        // Other facets only count listings in Kentron
        assertThat(result.facets().rooms())
                .extracting(SearchResult.Bucket::value, SearchResult.Bucket::count)
                .containsExactly(tuple("1", 1L),
                        tuple("2", 2L));
        assertThat(result.facets().price()).isEqualTo(new SearchResult.PriceRange(800, 1500));

        SearchResult narrowed = search("district=KENTRON&rooms=2&maxPrice=1000");
        assertThat(narrowed.total()).isEqualTo(1);
        // The price range ignores the price filter, so the slider can still widen
        assertThat(narrowed.facets().price()).isEqualTo(new SearchResult.PriceRange(900, 1500));
    }

    @Test
    void pagingThroughEqualPricesNeverSkipsOrRepeats() {
        Set<String> created = new HashSet<>();
        for (int i = 0; i < 23; i++) {
            // Same price for everyone: only the id tiebreaker keeps the order stable
            created.add(create(request("Listing " + i, "x", District.SHENGAVIT, 500, 1)));
        }
        waitIndexed(23);

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            SearchResult page = search("sort=PRICE_ASC&size=5" + (cursor == null ? "" : "&cursor=" + cursor));
            assertThat(page.total()).isEqualTo(23);
            seen.addAll(ids(page));
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null && pages < 10);

        assertThat(pages).isEqualTo(5);
        assertThat(seen).hasSize(23).doesNotHaveDuplicates();
        assertThat(new HashSet<>(seen)).isEqualTo(created);
    }

    @Test
    void highlightsMatchesAndEscapesHtml() {
        create(request("<b>Balcony</b> & terrace", "x", District.EREBUNI, 500, 1));
        waitIndexed(1);

        SearchResult.Item item = search("q=balcony").items().getFirst();

        assertThat(item.titleHighlight()).contains("<mark>Balcony</mark>").contains("&lt;b&gt;").doesNotContain("<b>");
    }

    @Test
    void suggestsListingsAndDistrictsAsYouType() {
        String bright = create(request("Bright loft in Arabkir", "x", District.ARABKIR, 700, 1));
        create(request("Dark basement studio", "x", District.AVAN, 300, 1));
        waitIndexed(2);

        Suggestions byTitle = client.get().uri("/api/listings/suggest?q=brig").exchange()
                .expectStatus().isOk().expectBody(Suggestions.class).returnResult().getResponseBody();
        assertThat(byTitle.listings()).extracting(Suggestions.ListingSuggestion::id).containsExactly(bright);

        Suggestions byDistrict = client.get().uri("/api/listings/suggest?q=arab").exchange()
                .expectStatus().isOk().expectBody(Suggestions.class).returnResult().getResponseBody();
        assertThat(byDistrict.districts()).extracting(Suggestions.DistrictSuggestion::value).containsExactly("ARABKIR");
    }

    @Test
    void rejectsBadRequests() {
        client.get().uri("/api/listings/search?minPrice=2000&maxPrice=1000").exchange().expectStatus().isBadRequest();
        client.get().uri("/api/listings/search?district=PARIS").exchange().expectStatus().isBadRequest();
        client.get().uri("/api/listings/search?size=500").exchange().expectStatus().isBadRequest();
        client.get().uri("/api/listings/search?cursor=not-a-cursor").exchange().expectStatus().isBadRequest();
        client.get().uri("/api/listings/suggest?q=").exchange().expectStatus().isBadRequest();
    }

    @Test
    void cursorFromAnotherSortIsRejected() {
        for (int i = 0; i < 3; i++) {
            create(request("Listing " + i, "x", District.AVAN, 500 + i, 1));
        }
        waitIndexed(3);
        String cursor = search("sort=PRICE_ASC&size=1").nextCursor();

        client.get().uri("/api/listings/search?sort=NEWEST&size=1&cursor=" + cursor).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.detail").value(String.class, d -> assertThat(d).contains("PRICE_ASC"));
    }

    private String create(ListingRequest request) {
        return listings.create(OWNER, request).getId();
    }

    private void waitIndexed(int expected) {
        await().atMost(Duration.ofSeconds(15)).until(() -> index.count() == expected);
    }

    private SearchResult search(String query) {
        return client.get().uri("/api/listings/search?" + query).exchange()
                .expectStatus().isOk()
                .expectBody(SearchResult.class).returnResult().getResponseBody();
    }

    private static List<String> ids(SearchResult result) {
        return result.items().stream().map(SearchResult.Item::id).toList();
    }
}
