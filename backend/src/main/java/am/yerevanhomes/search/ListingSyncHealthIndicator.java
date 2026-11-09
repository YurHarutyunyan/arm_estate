package am.yerevanhomes.search;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports DOWN until the search index is built and the change stream is following MongoDB, so
 * orchestrators (and tests) can wait for search to be current instead of guessing.
 */
@Component("listingSync")
public class ListingSyncHealthIndicator implements HealthIndicator {

    private final ListingSyncService sync;

    public ListingSyncHealthIndicator(ListingSyncService sync) {
        this.sync = sync;
    }

    @Override
    public Health health() {
        if (!sync.isEnabled()) {
            return Health.up().withDetail("sync", "disabled").build();
        }
        return sync.isReady() ? Health.up().build() : Health.down().withDetail("reason", "search index is syncing").build();
    }
}
