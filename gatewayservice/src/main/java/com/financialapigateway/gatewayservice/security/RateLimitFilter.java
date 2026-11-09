package com.financialapigateway.gatewayservice.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.optimization.Optimizations;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Not a @Component: Spring WebFlux runs every WebFilter bean globally, so a bean would run twice
 * per request (globally and again in the security chain). SecurityConfig creates it instead.
 */
@Slf4j
public class RateLimitFilter implements WebFilter, Ordered {

    private final ProxyManager<String> proxyManager;
    private final Supplier<BucketConfiguration> bucketConfiguration;

    /*
     * One bucket object per IP, reused across requests. The bucket state itself lives in Redis;
     * reusing the object lets Bucket4j's batching merge concurrent requests from the same IP into a
     * single Redis update. Building a new bucket per request made every concurrent request race on
     * the same Redis key with compare-and-swap and retry, which under load meant dozens of Redis
     * round trips per request. Limits stay exact. Bounded so a flood of distinct IPs can't grow it forever.
     */
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public RateLimitFilter(ProxyManager<String> proxyManager, Supplier<BucketConfiguration> bucketConfiguration) {
        this.proxyManager = proxyManager;
        this.bucketConfiguration = bucketConfiguration;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "Unknown";

        return Mono.fromCallable(() -> {
            Bucket bucket = this.buckets.get(ip, key -> this.proxyManager.builder()
                    .withOptimization(Optimizations.batching())
                    .build(key, this.bucketConfiguration));
            return bucket.tryConsumeAndReturnRemaining(1);
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(probe -> probe.isConsumed()
                        ? allowRequest(exchange, chain, probe.getRemainingTokens())
                        : rejectRequest(exchange, ip, probe.getNanosToWaitForRefill()));
    }

    private Mono<Void> allowRequest(ServerWebExchange exchange, WebFilterChain chain, long remaining) {
        exchange.getResponse().getHeaders()
                .add("X-Rate-Limit-Remaining", String.valueOf(remaining));
        return chain.filter(exchange);
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String ip, long nanosToWait) {
        long waitSeconds = nanosToWait / 1_000_000_000;
        log.warn("Rate limit exceeded ip={} retryAfter={}s", ip, waitSeconds);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"error\": \"Too many requests\", \"retryAfter\": %d}", waitSeconds
        );
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }


}
