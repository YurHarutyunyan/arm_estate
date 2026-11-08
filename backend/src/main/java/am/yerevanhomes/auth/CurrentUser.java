package am.yerevanhomes.auth;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.oauth2.jwt.Jwt;

import am.yerevanhomes.user.Role;

/** The signed-in user, read from the verified JWT. */
public record CurrentUser(String id, Set<Role> roles) {

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtService.ROLES_CLAIM);
        return new CurrentUser(jwt.getSubject(),
                roles == null ? Set.of() : roles.stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet()));
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }
}
