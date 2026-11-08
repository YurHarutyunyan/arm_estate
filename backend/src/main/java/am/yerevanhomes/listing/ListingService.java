package am.yerevanhomes.listing;

import org.springframework.stereotype.Service;

import am.yerevanhomes.common.NotFoundException;

@Service
public class ListingService {

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

    public Listing update(String id, ListingRequest request) {
        Listing listing = get(id);
        request.applyTo(listing);
        return repository.save(listing);
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Listing " + id + " not found");
        }
        repository.deleteById(id);
    }
}
