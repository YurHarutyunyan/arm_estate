package am.yerevanhomes.user;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import am.yerevanhomes.auth.AuthDtos.Profile;
import am.yerevanhomes.common.NotFoundException;
import am.yerevanhomes.listing.ListingRepository;
import am.yerevanhomes.listing.ListingResponse;

/** The signed-in user's profile and favorites. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository users;
    private final FavoriteService favorites;
    private final ListingRepository listings;

    public MeController(UserRepository users, FavoriteService favorites, ListingRepository listings) {
        this.users = users;
        this.favorites = favorites;
        this.listings = listings;
    }

    @GetMapping
    public Profile me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(jwt.getSubject()).map(Profile::from)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    /** Listings the signed-in user published, newest first. */
    @GetMapping("/listings")
    public List<ListingResponse> myListings(@AuthenticationPrincipal Jwt jwt) {
        return listings.findByOwnerIdOrderByCreatedAtDesc(jwt.getSubject()).stream().map(ListingResponse::from).toList();
    }

    @GetMapping("/favorites")
    public List<ListingResponse> favorites(@AuthenticationPrincipal Jwt jwt) {
        return favorites.list(jwt.getSubject()).stream().map(ListingResponse::from).toList();
    }

    @GetMapping("/favorites/{listingId}")
    public ResponseEntity<Void> isFavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable String listingId) {
        return favorites.isFavorite(jwt.getSubject(), listingId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PutMapping("/favorites/{listingId}")
    public ResponseEntity<Void> add(@AuthenticationPrincipal Jwt jwt, @PathVariable String listingId) {
        favorites.add(jwt.getSubject(), listingId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/favorites/{listingId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal Jwt jwt, @PathVariable String listingId) {
        favorites.remove(jwt.getSubject(), listingId);
        return ResponseEntity.noContent().build();
    }
}
