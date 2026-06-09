package com.financialapigateway.gatewayservice.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

@Component
public class RateLimitFilter implements WebFilter, Ordered {

    private final ProxyManager<String> proxyManager;
    private final Supplier<BucketConfiguration> bucketConfiguration;

    @Autowired
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
            Bucket bucket = this.proxyManager.builder().build(ip, this.bucketConfiguration);
            return bucket.tryConsumeAndReturnRemaining(1);
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(probe -> probe.isConsumed()
                        ? allowRequest(exchange, chain, probe.getRemainingTokens())
                        : rejectRequest(exchange, probe.getNanosToWaitForRefill()));
    }

    private Mono<Void> allowRequest(ServerWebExchange exchange, WebFilterChain chain, long remaining) {
        exchange.getResponse().getHeaders()
                .add("X-Rate-Limit-Remaining", String.valueOf(remaining));
        return chain.filter(exchange);
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, long nanosToWait) {
        long waitSeconds = nanosToWait / 1_000_000_000;
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"error\": \"Too many requests\", \"retryAfter\": %d}", waitSeconds
        );
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes());
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }


}
