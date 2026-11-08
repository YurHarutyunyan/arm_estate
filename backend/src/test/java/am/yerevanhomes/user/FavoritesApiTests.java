package am.yerevanhomes.user;

import static am.yerevanhomes.TestListings.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.web.servlet.client.RestTestClient;

import am.yerevanhomes.TestAuth;
import am.yerevanhomes.TestcontainersConfiguration;
import am.yerevanhomes.listing.ListingResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class FavoritesApiTests {

    @Autowired
    RestTestClient client;

    @Autowired
    UserRepository users;

    @Autowired
    MongoTemplate mongo;

    String owner;
    TestAuth.Account user;

    @BeforeEach
    void setUp() {
        owner = TestAuth.owner(client);
        user = TestAuth.register(client, "USER");
    }

    @Test
    void addListAndRemoveFavoritesNewestFirst() {
        String first = listing(800);
        String second = listing(900);

        favorite(first);
        favorite(second);

        assertThat(favoriteIds()).containsExactly(second, first);

        client.get().uri("/api/me/favorites/{id}", first).header("Authorization", user.bearer()).exchange()
                .expectStatus().isNoContent();
        client.delete().uri("/api/me/favorites/{id}", first).header("Authorization", user.bearer()).exchange()
                .expectStatus().isNoContent();
        client.get().uri("/api/me/favorites/{id}", first).header("Authorization", user.bearer()).exchange()
                .expectStatus().isNotFound();
        assertThat(favoriteIds()).containsExactly(second);
    }

    @Test
    void favoritingAMissingListingIs404() {
        client.put().uri("/api/me/favorites/{id}", "000000000000000000000000").header("Authorization", user.bearer())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void parallelClicksOnTheSameListingStoreItOnce() throws Exception {
        String id = listing(800);

        runInParallel(20, i -> () -> favorite(id));

        assertThat(storedFavorites()).containsExactly(id);
    }

    @Test
    void parallelFavoritesOfDifferentListingsAreAllKept() throws Exception {
        List<String> ids = IntStream.range(0, 15).mapToObj(i -> listing(500 + i)).toList();

        runInParallel(ids.size(), i -> () -> favorite(ids.get(i)));

        assertThat(storedFavorites()).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void theLimitHoldsEvenUnderParallelAdds() throws Exception {
        // Fill the list up to one below the limit directly, then race several adds for the last slot
        List<String> filler = IntStream.range(0, User.MAX_FAVORITES - 1).mapToObj(i -> "filler-" + i).toList();
        mongo.updateFirst(new Query(where("_id").is(user.id())), new Update().set("favoriteListingIds", filler), User.class);
        List<String> contenders = IntStream.range(0, 6).mapToObj(i -> listing(700 + i)).toList();

        List<Integer> statuses = runInParallel(contenders.size(), i -> () -> client.put()
                .uri("/api/me/favorites/{id}", contenders.get(i)).header("Authorization", user.bearer())
                .exchange().returnResult(String.class).getStatus().value());

        assertThat(statuses).filteredOn(s -> s == 204).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(contenders.size() - 1);
        assertThat(storedFavorites()).hasSize(User.MAX_FAVORITES);
    }

    @Test
    void deletingAListingRemovesItFromFavorites() {
        String id = listing(800);
        favorite(id);

        client.delete().uri("/api/listings/{id}", id).header("Authorization", owner).header("If-Match", "\"0\"").exchange()
                .expectStatus().isNoContent();

        assertThat(storedFavorites()).isEmpty();
    }

    private String listing(int price) {
        return client.post().uri("/api/listings").header("Authorization", owner).body(validRequest(price)).exchange()
                .expectStatus().isCreated()
                .expectBody(ListingResponse.class).returnResult().getResponseBody().id();
    }

    private Integer favorite(String id) {
        client.put().uri("/api/me/favorites/{id}", id).header("Authorization", user.bearer()).exchange()
                .expectStatus().isNoContent();
        return 204;
    }

    private List<String> favoriteIds() {
        return client.get().uri("/api/me/favorites").header("Authorization", user.bearer()).exchange()
                .expectStatus().isOk()
                .expectBody(ListingResponse[].class).returnResult().getResponseBody().length == 0
                ? List.of()
                : List.of(client.get().uri("/api/me/favorites").header("Authorization", user.bearer()).exchange()
                        .expectBody(ListingResponse[].class).returnResult().getResponseBody())
                        .stream().map(ListingResponse::id).toList();
    }

    private List<String> storedFavorites() {
        return users.findById(user.id()).orElseThrow().getFavoriteListingIds();
    }

    private static <T> List<T> runInParallel(int count, java.util.function.IntFunction<Callable<T>> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<T>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            for (int i = 0; i < count; i++) {
                Callable<T> call = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (java.util.concurrent.Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }
}
