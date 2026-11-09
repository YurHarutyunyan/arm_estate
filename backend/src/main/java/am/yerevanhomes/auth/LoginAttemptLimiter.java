package am.yerevanhomes.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits failed logins per account, so nobody can try passwords against one email indefinitely.
 *
 * <p>Keyed by email rather than IP: behind the proxy every request shares an address, and an
 * attacker can rotate IPs anyway. State is in memory, which fits one backend instance; several
 * instances would need a shared store such as Redis.
 */
@Component
public class LoginAttemptLimiter {

    /** Entries above this are pruned, so many random emails can't exhaust memory. */
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Window> failures = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    public LoginAttemptLimiter(@Value("${app.login.max-failures:10}") int maxFailures,
            @Value("${app.login.window:PT15M}") Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
    }

    /** @return how long until this account may try again, or {@link Duration#ZERO} if it may now */
    public Duration retryAfter(String email) {
        Window w = failures.get(email);
        if (w == null) {
            return Duration.ZERO;
        }
        Instant now = clock.instant();
        Instant end = w.start().plus(window);
        if (!now.isBefore(end)) {
            failures.remove(email, w);
            return Duration.ZERO;
        }
        return w.count() >= maxFailures ? Duration.between(now, end) : Duration.ZERO;
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        if (failures.size() >= MAX_TRACKED) {
            failures.values().removeIf(w -> !now.isBefore(w.start().plus(window)));
        }
        // merge is atomic per key, so parallel wrong passwords are all counted
        failures.merge(email, new Window(now, 1),
                (old, fresh) -> now.isBefore(old.start().plus(window)) ? new Window(old.start(), old.count() + 1) : fresh);
    }

    public void recordSuccess(String email) {
        failures.remove(email);
    }

    private record Window(Instant start, int count) {
    }
}
