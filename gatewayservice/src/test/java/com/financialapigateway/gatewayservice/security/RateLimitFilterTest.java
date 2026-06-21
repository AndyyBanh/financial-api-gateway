package com.financialapigateway.gatewayservice.security;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RateLimitFilterTest {

    @Mock
    private ProxyManager<String> proxyManager;

    @Mock
    private Supplier<BucketConfiguration> bucketConfiguration;

    @Mock
    private RemoteBucketBuilder<String> bucketBuilder;

    @Mock
    private BucketProxy bucket;

    @Mock
    private WebFilterChain filterChain;

    @InjectMocks
    private RateLimitFilter rateLimitFilter;

    private ServerWebExchange buildExchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/accounts")
                        .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
        );
    }

    @Test
    void shouldAllowRequestWhenTokenAvailable() {
        ServerWebExchange exchange = buildExchange();


        when(this.proxyManager.builder()).thenReturn(this.bucketBuilder);
        when(this.bucketBuilder.build(anyString(), eq(this.bucketConfiguration))).thenReturn(this.bucket);
        when(this.bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.consumed(4, 0));
        when(this.filterChain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(this.rateLimitFilter.filter(exchange, this.filterChain))
                .verifyComplete();

        verify(this.filterChain).filter(exchange);
        assert exchange.getResponse().getHeaders().getFirst("X-Rate-Limit-Remaining").equals("4");
    }

    @Test
    void shouldNotAllowRequestWhenTokenNotAvailable() {
        ServerWebExchange exchange = buildExchange();

        when(this.proxyManager.builder()).thenReturn(this.bucketBuilder);
        when(this.bucketBuilder.build(anyString(), eq(this.bucketConfiguration))).thenReturn(this.bucket);
        when(this.bucket.tryConsumeAndReturnRemaining(1))
                .thenReturn(ConsumptionProbe.rejected(0, 60_000_000L, 0));

        StepVerifier.create(this.rateLimitFilter.filter(exchange, this.filterChain))
                        .verifyComplete();

        verify(this.filterChain, never()).filter(any());
        assert exchange.getResponse().getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
    }
}
