package am.yerevanhomes.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTests {

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, Duration.ofMinutes(15), clock);

    @Test
    void blocksAfterTooManyFailuresUntilTheWindowEnds() {
        limiter.recordFailure("a@x.am");
        limiter.recordFailure("a@x.am");
        assertThat(limiter.retryAfter("a@x.am")).isZero();

        limiter.recordFailure("a@x.am");
        assertThat(limiter.retryAfter("a@x.am")).isEqualTo(Duration.ofMinutes(15));

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.retryAfter("a@x.am")).isEqualTo(Duration.ofMinutes(5));

        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.retryAfter("a@x.am")).isZero();
    }

    @Test
    void accountsAreLimitedSeparately() {
        IntStream.range(0, 3).forEach(i -> limiter.recordFailure("a@x.am"));

        assertThat(limiter.retryAfter("a@x.am")).isPositive();
        assertThat(limiter.retryAfter("b@x.am")).isZero();
    }

    @Test
    void successfulLoginResetsTheCount() {
        limiter.recordFailure("a@x.am");
        limiter.recordFailure("a@x.am");
        limiter.recordSuccess("a@x.am");
        limiter.recordFailure("a@x.am");

        assertThat(limiter.retryAfter("a@x.am")).isZero();
    }

    @Test
    void parallelFailuresAreAllCounted() throws Exception {
        LoginAttemptLimiter strict = new LoginAttemptLimiter(100, Duration.ofMinutes(15), clock);
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 100; i++) {
                pool.submit(() -> strict.recordFailure("a@x.am"));
            }
        }

        assertThat(strict.retryAfter("a@x.am")).isPositive();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-09T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
