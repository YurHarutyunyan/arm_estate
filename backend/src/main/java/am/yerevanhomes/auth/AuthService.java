package am.yerevanhomes.auth;

import java.util.EnumSet;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import am.yerevanhomes.auth.AuthDtos.AuthResponse;
import am.yerevanhomes.auth.AuthDtos.LoginRequest;
import am.yerevanhomes.auth.AuthDtos.Profile;
import am.yerevanhomes.auth.AuthDtos.RegisterRequest;
import am.yerevanhomes.common.ConflictException;
import am.yerevanhomes.common.UnauthorizedException;
import am.yerevanhomes.user.Role;
import am.yerevanhomes.user.User;
import am.yerevanhomes.user.UserRepository;

@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    /** Compared against when the email is unknown, so both cases take the same time. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public AuthResponse register(RegisterRequest request) {
        User user = new User();
        user.setEmail(normalize(request.email()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().strip());
        user.setRoles(request.accountType() == AuthDtos.AccountType.OWNER
                ? EnumSet.of(Role.USER, Role.OWNER)
                : EnumSet.of(Role.USER));
        try {
            // No "exists?" check first: two sign-ups racing on one email would both pass it.
            // The unique index decides, and the loser gets a 409.
            users.insert(user);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("An account with this email already exists");
        }
        return respond(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        return respond(user);
    }

    private AuthResponse respond(User user) {
        JwtService.IssuedToken token = jwt.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), Profile.from(user));
    }

    static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
