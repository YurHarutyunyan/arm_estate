package am.yerevanhomes.listing;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import am.yerevanhomes.auth.CurrentUser;
import am.yerevanhomes.common.ETags;

/**
 * Listings CRUD. Responses carry the listing version as an ETag; PUT and DELETE must send it back
 * in If-Match so two people editing at once can't silently overwrite each other.
 */
@RestController
@RequestMapping("/api/listings")
public class ListingController {

    private final ListingService service;

    public ListingController(ListingService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingResponse> get(@PathVariable String id) {
        return withETag(ResponseEntity.ok(), service.get(id));
    }

    @PostMapping
    public ResponseEntity<ListingResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ListingRequest request) {
        Listing created = service.create(CurrentUser.from(jwt), request);
        return withETag(ResponseEntity.created(URI.create("/api/listings/" + created.getId())), created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ListingResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody ListingRequest request) {
        return withETag(ResponseEntity.ok(), service.update(CurrentUser.from(jwt), id, ETags.parseIfMatch(ifMatch), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        service.delete(CurrentUser.from(jwt), id, ETags.parseIfMatch(ifMatch));
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<ListingResponse> withETag(ResponseEntity.BodyBuilder builder, Listing listing) {
        return builder.eTag(ETags.of(listing.getVersion())).body(ListingResponse.from(listing));
    }
}
