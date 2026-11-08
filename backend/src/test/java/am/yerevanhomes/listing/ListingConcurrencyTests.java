package am.yerevanhomes.listing;

import static am.yerevanhomes.TestListings.validRequest;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

import am.yerevanhomes.TestcontainersConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ListingConcurrencyTests {

    @Autowired
    RestTestClient client;

    @Autowired
    ListingRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void updateWithoutIfMatchIsRejected() {
        String id = create();

        client.put().uri("/api/listings/{id}", id).body(validRequest(1000)).exchange()
                .expectStatus().isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        client.delete().uri("/api/listings/{id}", id).exchange()
                .expectStatus().isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
    }

    @Test
    void malformedIfMatchIsRejected() {
        String id = create();

        client.put().uri("/api/listings/{id}", id).header("If-Match", "*").body(validRequest(1000)).exchange()
                .expectStatus().isEqualTo(HttpStatus.PRECONDITION_FAILED);
    }

    @Test
    void secondEditorWorkingFromTheSameVersionGets412() {
        String id = create();

        // Both editors loaded version 0. The first saves...
        client.put().uri("/api/listings/{id}", id).header("If-Match", "\"0\"").body(validRequest(1000)).exchange()
                .expectStatus().isOk();
        // ...the second must not overwrite that change.
        client.put().uri("/api/listings/{id}", id).header("If-Match", "\"0\"").body(validRequest(2000)).exchange()
                .expectStatus().isEqualTo(HttpStatus.PRECONDITION_FAILED);

        assertThat(repository.findById(id).orElseThrow().getPriceUsd()).isEqualTo(1000);
    }

    @Test
    void deleteOfAnOutdatedVersionIsRejected() {
        String id = create();
        client.put().uri("/api/listings/{id}", id).header("If-Match", "\"0\"").body(validRequest(1000)).exchange()
                .expectStatus().isOk();

        client.delete().uri("/api/listings/{id}", id).header("If-Match", "\"0\"").exchange()
                .expectStatus().isEqualTo(HttpStatus.PRECONDITION_FAILED);

        assertThat(repository.existsById(id)).isTrue();
    }

    @Test
    void parallelEditsOfTheSameVersionLetExactlyOneWin() throws Exception {
        String id = create();
        int editors = 10;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<HttpStatus>> calls = new ArrayList<>();
        for (int i = 0; i < editors; i++) {
            int price = 1000 + i;
            calls.add(() -> {
                start.await();
                return (HttpStatus) client.put().uri("/api/listings/{id}", id).header("If-Match", "\"0\"")
                        .body(validRequest(price)).exchange()
                        .returnResult(String.class).getStatus();
            });
        }

        List<HttpStatus> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(editors)) {
            List<Future<HttpStatus>> futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<HttpStatus> f : futures) {
                statuses.add(f.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == HttpStatus.OK).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == HttpStatus.PRECONDITION_FAILED).hasSize(editors - 1);
        assertThat(repository.findById(id).orElseThrow().getVersion()).isEqualTo(1L);
    }

    private String create() {
        return client.post().uri("/api/listings").body(validRequest(950)).exchange()
                .expectStatus().isCreated()
                .expectBody(ListingResponse.class).returnResult().getResponseBody().id();
    }
}
