package am.yerevanhomes.listing;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ListingRepository extends MongoRepository<Listing, String> {

    List<Listing> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
}
