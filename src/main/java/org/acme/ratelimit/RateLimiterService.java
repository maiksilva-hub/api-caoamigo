package org.acme.ratelimit;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class RateLimiterService {

    @ConfigProperty(name = "rate.limit.window.seconds", defaultValue = "60")
    int windowSeconds;

    @ConfigProperty(name = "rate.limit.max.requests", defaultValue = "10")
    int maxRequests;

    private static final String CACHE_NAME = "rate-limit-cache";

    public RateLimitResult checkRateLimit(String key) {
        AtomicLong counter = getCounter(key);
        long currentCount = counter.getAndIncrement();

        boolean exceeded = currentCount >= maxRequests;

        return new RateLimitResult(
                currentCount + 1,
                maxRequests,
                maxRequests - (currentCount + 1),
                exceeded
        );
    }


    @CacheResult(cacheName = CACHE_NAME)
    public AtomicLong getCounter(String key) {
        return new AtomicLong(0);
    }

    public static class RateLimitResult {
        public final long currentRequests;
        public final int limit;
        public final long remaining;
        public final boolean exceeded;

        public RateLimitResult(long currentRequests, int limit, long remaining, boolean exceeded) {
            this.currentRequests = currentRequests;
            this.limit = limit;
            this.remaining = Math.max(0, remaining); // Garante que restante não seja negativo
            this.exceeded = exceeded;
        }
    }
}