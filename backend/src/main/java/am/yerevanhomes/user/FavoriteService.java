package am.yerevanhomes.user;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.UpdateResult;

import am.yerevanhomes.common.ConflictException;
import am.yerevanhomes.common.NotFoundException;
import am.yerevanhomes.listing.Listing;
import am.yerevanhomes.listing.ListingRepository;

/**
 * Favorites are a list on the user document. Every change is one atomic MongoDB update, so
 * double clicks and parallel tabs can neither lose an entry nor add it twice.
 */
@Service
public class FavoriteService {

    private static final String FIELD = "favoriteListingIds";

    private final MongoTemplate mongo;
    private final ListingRepository listings;

    public FavoriteService(MongoTemplate mongo, ListingRepository listings) {
        this.mongo = mongo;
        this.listings = listings;
    }

    /** Idempotent: adding a favorite twice keeps one entry. */
    public void add(String userId, String listingId) {
        if (!listings.existsById(listingId)) {
            throw new NotFoundException("Listing " + listingId + " not found");
        }
        // $addToSet never duplicates. The limit check is part of the same query: the update only
        // matches if the list has fewer than MAX entries or already contains this listing, so
        // parallel adds can't push it over the limit.
        Query query = new Query(where("_id").is(userId).orOperator(
                where(FIELD + "." + (User.MAX_FAVORITES - 1)).exists(false),
                where(FIELD).is(listingId)));
        UpdateResult result = mongo.updateFirst(query, new Update().addToSet(FIELD, listingId), User.class);
        if (result.getMatchedCount() == 0) {
            if (!mongo.exists(new Query(where("_id").is(userId)), User.class)) {
                throw new NotFoundException("User not found");
            }
            throw new ConflictException("You can save at most " + User.MAX_FAVORITES + " favorites");
        }
    }

    /** Idempotent: removing a listing that isn't a favorite does nothing. */
    public void remove(String userId, String listingId) {
        mongo.updateFirst(new Query(where("_id").is(userId)), new Update().pull(FIELD, listingId), User.class);
    }

    /** Favorites in the order they were saved, most recent first. Deleted listings are skipped. */
    public List<Listing> list(String userId) {
        Query query = new Query(where("_id").is(userId));
        query.fields().include(FIELD);
        User user = mongo.findOne(query, User.class);
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        List<String> ids = user.getFavoriteListingIds();
        Map<String, Listing> byId = listings.findAllById(ids).stream()
                .collect(Collectors.toMap(Listing::getId, Function.identity()));
        return ids.reversed().stream().map(byId::get).filter(l -> l != null).toList();
    }

    public boolean isFavorite(String userId, String listingId) {
        return mongo.exists(new Query(new Criteria().andOperator(where("_id").is(userId), where(FIELD).is(listingId))), User.class);
    }
}
