package am.yerevanhomes.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public class User {

    /** Upper bound on saved favorites, enforced atomically in the update query. */
    public static final int MAX_FAVORITES = 200;

    @Id
    private String id;

    /** Stored lower-cased; the unique index makes concurrent sign-ups with one email safe. */
    @Indexed(unique = true)
    private String email;

    private String passwordHash;
    private String displayName;
    private Set<Role> roles = EnumSet.of(Role.USER);
    private List<String> favoriteListingIds = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
    public List<String> getFavoriteListingIds() { return favoriteListingIds; }
    public void setFavoriteListingIds(List<String> favoriteListingIds) { this.favoriteListingIds = favoriteListingIds; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
