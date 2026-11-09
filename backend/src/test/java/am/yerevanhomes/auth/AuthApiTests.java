package am.yerevanhomes.auth;

import static am.yerevanhomes.TestListings.validRequest;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

import am.yerevanhomes.TestAuth;
import am.yerevanhomes.TestcontainersConfiguration;
import am.yerevanhomes.auth.AuthDtos.AuthResponse;
import am.yerevanhomes.listing.ListingResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class AuthApiTests {

    @Autowired
    RestTestClient client;

    @LocalServerPort
    int port;

    @Test
    void registersAndLogsInWithCaseInsensitiveEmail() {
        TestAuth.Account account = TestAuth.register(client, "USER");

        AuthResponse login = client.post().uri("/api/auth/login")
                .body(Map.of("email", "  " + account.email().toUpperCase() + " ", "password", "correct horse battery"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class).returnResult().getResponseBody();

        assertThat(login.user().id()).isEqualTo(account.id());
        client.get().uri("/api/me").header("Authorization", "Bearer " + login.token()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.email").isEqualTo(account.email());
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() {
        TestAuth.Account account = TestAuth.register(client, "USER");

        String wrongPassword = loginDetail(account.email(), "wrong password!");
        String unknownEmail = loginDetail("nobody@example.com", "wrong password!");

        assertThat(wrongPassword).isEqualTo(unknownEmail).isEqualTo("Invalid email or password");
    }

    @Test
    void tooManyWrongPasswordsLockTheAccountTemporarilyEvenForTheRightOne() throws Exception {
        TestAuth.Account account = TestAuth.register(client, "USER");
        for (int i = 0; i < 10; i++) {
            loginDetail(account.email(), "wrong password " + i);
        }

        // Plain JDK client: the default test client honors Retry-After and would wait 15 minutes
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"" + account.email() + "\",\"password\":\"correct horse battery\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    void passwordLimitCountsBytesNotCharacters() {
        // 40 Armenian letters: 40 characters, but 80 bytes in UTF-8
        String armenian = "բ".repeat(40);

        client.post().uri("/api/auth/register")
                .body(Map.of("email", "bytes-" + System.nanoTime() + "@example.com", "password", armenian,
                        "displayName", "x", "accountType", "USER"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors.passwordWithinBcryptLimit").exists();
    }

    @Test
    void cannotSignUpAsAdmin() {
        client.post().uri("/api/auth/register")
                .body(Map.of("email", "sneaky@example.com", "password", "correct horse battery", "displayName", "x",
                        "accountType", "ADMIN"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void parallelSignUpsWithOneEmailCreateOneAccount() throws Exception {
        String email = "race-" + System.nanoTime() + "@example.com";
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<HttpStatus>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> {
                start.await();
                return (HttpStatus) client.post().uri("/api/auth/register")
                        .body(Map.of("email", email, "password", "correct horse battery", "displayName", "x",
                                "accountType", "USER"))
                        .exchange().returnResult(String.class).getStatus();
            });
        }
        List<HttpStatus> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<HttpStatus>> futures = calls.stream().map(pool::submit).toList();
            start.countDown();
            for (Future<HttpStatus> f : futures) {
                statuses.add(f.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == HttpStatus.CREATED).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == HttpStatus.CONFLICT).hasSize(7);
    }

    @Test
    void protectedEndpointsNeedAValidToken() {
        client.get().uri("/api/me").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail").isEqualTo("Sign in to do this");

        String bearer = TestAuth.user(client);
        // Flip one character in the signature
        String tampered = bearer.substring(0, bearer.length() - 2) + (bearer.endsWith("A") ? "B" : "A") + bearer.charAt(bearer.length() - 1);
        client.get().uri("/api/me").header("Authorization", tampered).exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void onlyOwnersCanPublishAndOnlyTheirOwnListings() {
        String user = TestAuth.user(client);
        String owner = TestAuth.owner(client);
        String otherOwner = TestAuth.owner(client);

        client.post().uri("/api/listings").header("Authorization", user).body(validRequest(900)).exchange()
                .expectStatus().isForbidden();

        ListingResponse listing = client.post().uri("/api/listings").header("Authorization", owner).body(validRequest(900))
                .exchange().expectStatus().isCreated()
                .expectBody(ListingResponse.class).returnResult().getResponseBody();

        client.put().uri("/api/listings/{id}", listing.id()).header("Authorization", otherOwner).header("If-Match", "\"0\"")
                .body(validRequest(1000)).exchange()
                .expectStatus().isForbidden();
        client.delete().uri("/api/listings/{id}", listing.id()).header("Authorization", otherOwner).header("If-Match", "\"0\"")
                .exchange()
                .expectStatus().isForbidden();

        client.put().uri("/api/listings/{id}", listing.id()).header("Authorization", owner).header("If-Match", "\"0\"")
                .body(validRequest(950)).exchange()
                .expectStatus().isOk();
    }

    @Test
    void myListingsShowsOnlyTheCallersListingsNewestFirst() {
        String owner = TestAuth.owner(client);
        String other = TestAuth.owner(client);
        String first = createListing(owner, 800);
        String second = createListing(owner, 900);
        createListing(other, 1000);

        client.get().uri("/api/me/listings").header("Authorization", owner).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo(second)
                .jsonPath("$[1].id").isEqualTo(first);
    }

    private String createListing(String bearer, int price) {
        return client.post().uri("/api/listings").header("Authorization", bearer).body(validRequest(price)).exchange()
                .expectStatus().isCreated()
                .expectBody(ListingResponse.class).returnResult().getResponseBody().id();
    }

    private String loginDetail(String email, String password) {
        return client.post().uri("/api/auth/login").body(Map.of("email", email, "password", password)).exchange()
                .expectStatus().isUnauthorized()
                .expectBody(Map.class).returnResult().getResponseBody().get("detail").toString();
    }
}
