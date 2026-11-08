package am.yerevanhomes.listing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import am.yerevanhomes.TestAuth;
import am.yerevanhomes.TestcontainersConfiguration;

import static am.yerevanhomes.TestListings.validRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class ListingApiTests {

    @Autowired
    RestTestClient client;

    @Autowired
    ListingRepository repository;

    String owner;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        owner = TestAuth.owner(client);
    }

    @Test
    void createsReadsUpdatesAndDeletesAListing() {
        EntityExchangeResult<ListingResponse> created = client.post().uri("/api/listings").header("Authorization", owner).body(validRequest(950))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("ETag", "\"0\"")
                .expectBody(ListingResponse.class).returnResult();
        String id = created.getResponseBody().id();
        assertThat(created.getResponseBody().createdAt()).isNotNull();

        client.get().uri("/api/listings/{id}", id).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("ETag", "\"0\"")
                .expectBody().jsonPath("$.priceUsd").isEqualTo(950);

        client.put().uri("/api/listings/{id}", id).header("Authorization", owner).header("If-Match", "\"0\"").body(validRequest(1100)).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("ETag", "\"1\"")
                .expectBody().jsonPath("$.priceUsd").isEqualTo(1100);

        client.delete().uri("/api/listings/{id}", id).header("Authorization", owner).header("If-Match", "\"1\"").exchange()
                .expectStatus().isNoContent();

        client.get().uri("/api/listings/{id}", id).exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody().jsonPath("$.detail").isEqualTo("Listing " + id + " not found");
    }

    @Test
    void rejectsInvalidListingWithFieldErrors() {
        Map<String, Object> invalid = Map.of("title", "", "priceUsd", 10);

        client.post().uri("/api/listings").header("Authorization", owner).body(invalid).exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.title").exists()
                .jsonPath("$.errors.priceUsd").exists()
                .jsonPath("$.errors.district").exists()
                .jsonPath("$.errors.rooms").exists();
    }

    @Test
    void rejectsFloorAboveTheBuildingAndNonHttpsImages() {
        ListingRequest valid = validRequest(900);
        ListingRequest tooHigh = new ListingRequest(valid.title(), valid.description(), valid.district(), valid.address(),
                900, 2, 60, 12, 9, valid.buildingType(), valid.amenities(), true, false, null);
        ListingRequest scriptUrl = new ListingRequest(valid.title(), valid.description(), valid.district(), valid.address(),
                900, 2, 60, 3, 9, valid.buildingType(), valid.amenities(), true, false, "javascript:alert(1)");

        client.post().uri("/api/listings").header("Authorization", owner).body(tooHigh).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors.floorWithinBuilding").exists();
        client.post().uri("/api/listings").header("Authorization", owner).body(scriptUrl).exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors.imageUrl").isEqualTo("must be an https URL");
    }
}
