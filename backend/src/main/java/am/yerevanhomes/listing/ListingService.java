package am.yerevanhomes.listing;

import java.util.Objects;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import am.yerevanhomes.auth.CurrentUser;
import am.yerevanhomes.common.ForbiddenException;
import am.yerevanhomes.common.NotFoundException;
import am.yerevanhomes.common.PreconditionFailedException;
import am.yerevanhomes.user.User;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
public class ListingService {

    private static final String STALE = "The listing was changed by someone else. Reload it and try again.";

    private final ListingRepository repository;
    private final MongoTemplate mongo;

    public ListingService(ListingRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    public Listing get(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Listing " + id + " not found"));
    }

    public Listing create(CurrentUser user, ListingRequest request) {
        Listing listing = new Listing();
        request.applyTo(listing);
        listing.setOwnerId(user.id());
        return repository.save(listing);
    }

    /**
     * Replaces a listing if the caller may edit it and saw the latest version. The version check
     * here gives a clear error early; the {@code @Version} check inside save() still catches a
     * writer that slips in between this read and the write.
     */
    public Listing update(CurrentUser user, String id, long expectedVersion, ListingRequest request) {
        Listing listing = get(id);
        requireCanManage(user, listing);
        requireVersion(listing, expectedVersion);
        request.applyTo(listing);
        return repository.save(listing);
    }

    public void delete(CurrentUser user, String id, long expectedVersion) {
        Listing listing = get(id);
        requireCanManage(user, listing);
        requireVersion(listing, expectedVersion);
        // delete(entity) includes the version in the filter, so a concurrent edit makes it fail
        repository.delete(listing);
        // Nobody should keep a favorite that no longer exists
        mongo.updateMulti(new Query(where("favoriteListingIds").is(id)), new Update().pull("favoriteListingIds", id), User.class);
    }

    private static void requireCanManage(CurrentUser user, Listing listing) {
        if (!user.isAdmin() && !Objects.equals(listing.getOwnerId(), user.id())) {
            throw new ForbiddenException("Only the owner of this listing can change it");
        }
    }

    private static void requireVersion(Listing listing, long expectedVersion) {
        if (listing.getVersion() == null || listing.getVersion() != expectedVersion) {
            throw new PreconditionFailedException(STALE);
        }
    }
}
