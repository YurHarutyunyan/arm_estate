package am.yerevanhomes;

import java.util.Map;
import java.util.UUID;

import org.springframework.test.web.servlet.client.RestTestClient;

import am.yerevanhomes.auth.AuthDtos.AuthResponse;

/** Signs up test accounts through the real API and returns Authorization header values. */
public final class TestAuth {

    private TestAuth() {
    }

    public static String owner(RestTestClient client) {
        return register(client, "OWNER").bearer();
    }

    public static String user(RestTestClient client) {
        return register(client, "USER").bearer();
    }

    public static Account register(RestTestClient client, String accountType) {
        String email = accountType.toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        AuthResponse response = client.post().uri("/api/auth/register")
                .body(Map.of("email", email, "password", "correct horse battery", "displayName", "Test", "accountType", accountType))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AuthResponse.class).returnResult().getResponseBody();
        return new Account(response.user().id(), email, "Bearer " + response.token());
    }

    public record Account(String id, String email, String bearer) {
    }
}
