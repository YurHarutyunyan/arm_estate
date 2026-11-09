package am.yerevanhomes.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import am.yerevanhomes.user.Role;
import am.yerevanhomes.user.User;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** Sign-up. Only USER and OWNER can be chosen; ADMIN is never self-assigned. */
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            // BCrypt only uses the first 72 bytes; longer passwords would be silently truncated
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 60) String displayName,
            @NotNull AccountType accountType) {

        /** @Size counts characters, but BCrypt's limit is bytes: 50 Armenian letters are 100 bytes. */
        @AssertTrue(message = "password must be at most 72 bytes; use fewer non-Latin characters")
        boolean isPasswordWithinBcryptLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }

    public enum AccountType {
        USER, OWNER
    }

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
    }

    public record AuthResponse(String token, Instant expiresAt, Profile user) {
    }

    public record Profile(String id, String email, String displayName, List<Role> roles) {

        public static Profile from(User user) {
            return new Profile(user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getRoles().stream().sorted().toList());
        }
    }
}
