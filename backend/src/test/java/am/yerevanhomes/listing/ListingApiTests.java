package am.yerevanhomes.listing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

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
class ListingApiTests {

    @Autowired
    RestTestClient client;

    @Autowired
    ListingRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createsReadsUpdatesAndDeletesAListing() {
        ListingResponse created = client.post().uri("/api/listings").body(validRequest(950))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ListingResponse.class).returnResult().getResponseBody();

        assertThat(created.id()).isNotBlank();
        assertThat(created.createdAt()).isNotNull();

        client.get().uri("/api/listings/{id}", created.id()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.priceUsd").isEqualTo(950);

        client.put().uri("/api/listings/{id}", created.id()).body(validRequest(1100)).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.priceUsd").isEqualTo(1100);

        client.delete().uri("/api/listings/{id}", created.id()).exchange()
                .expectStatus().isNoContent();

        client.get().uri("/api/listings/{id}", created.id()).exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody().jsonPath("$.detail").isEqualTo("Listing " + created.id() + " not found");
    }

    @Test
    void rejectsInvalidListingWithFieldErrors() {
        Map<String, Object> invalid = Map.of("title", "", "priceUsd", 10);

        client.post().uri("/api/listings").body(invalid).exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.title").exists()
                .jsonPath("$.errors.priceUsd").exists()
                .jsonPath("$.errors.district").exists();
    }

    static ListingRequest validRequest(int price) {
        return new ListingRequest("Bright 2-room apartment near Cascade", "Renovated, quiet street, close to the Opera.",
                District.KENTRON, "Tamanyan St 5", price, 2, 68, 4, 9, BuildingType.STONE,
                List.of("balcony", "elevator"), true, false, null);
    }
}
