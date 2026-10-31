package br.com.zelo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-01T12:00:00Z"));

        void advance(Duration d) {
            now.updateAndGet(i -> i.plus(d));
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    @DisplayName("bloqueia a partir da requisição seguinte ao limite e libera na janela seguinte")
    void blocksAfterLimit_andResetsNextWindow() {
        MutableClock clock = new MutableClock();
        RateLimitFilter filter = new RateLimitFilter(clock, 3, 2);

        assertThat(filter.tryAcquire("user:1", 3)).isTrue();
        assertThat(filter.tryAcquire("user:1", 3)).isTrue();
        assertThat(filter.tryAcquire("user:1", 3)).isTrue();
        assertThat(filter.tryAcquire("user:1", 3)).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(filter.tryAcquire("user:1", 3)).isTrue();
    }

    @Test
    @DisplayName("os contadores são independentes por usuário")
    void countersAreIndependentPerKey() {
        RateLimitFilter filter = new RateLimitFilter(new MutableClock(), 1, 1);

        assertThat(filter.tryAcquire("user:1", 1)).isTrue();
        assertThat(filter.tryAcquire("user:1", 1)).isFalse();
        assertThat(filter.tryAcquire("user:2", 1)).isTrue();
    }
}
