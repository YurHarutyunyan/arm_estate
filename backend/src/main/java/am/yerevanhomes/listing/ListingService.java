package am.yerevanhomes.listing;

import org.springframework.stereotype.Service;

import am.yerevanhomes.common.NotFoundException;
import am.yerevanhomes.common.PreconditionFailedException;

@Service
public class ListingService {

    private static final String STALE = "The listing was changed by someone else. Reload it and try again.";

    private final ListingRepository repository;

    public ListingService(ListingRepository repository) {
        this.repository = repository;
    }

    public Listing get(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Listing " + id + " not found"));
    }

    public Listing create(ListingRequest request) {
        Listing listing = new Listing();
        request.applyTo(listing);
        return repository.save(listing);
    }

    /**
     * Replaces a listing if the caller saw the latest version. The version check here gives a clear
     * error early; the {@code @Version} check inside save() still catches a writer that slips in
     * between this read and the write.
     */
    public Listing update(String id, long expectedVersion, ListingRequest request) {
        Listing listing = get(id);
        requireVersion(listing, expectedVersion);
        request.applyTo(listing);
        return repository.save(listing);
    }

    public void delete(String id, long expectedVersion) {
        Listing listing = get(id);
        requireVersion(listing, expectedVersion);
        // delete(entity) includes the version in the filter, so a concurrent edit makes it fail
        repository.delete(listing);
    }

    private static void requireVersion(Listing listing, long expectedVersion) {
        if (listing.getVersion() == null || listing.getVersion() != expectedVersion) {
            throw new PreconditionFailedException(STALE);
        }
    }
}
