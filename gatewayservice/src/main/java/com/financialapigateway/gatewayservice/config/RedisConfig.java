package com.financialapigateway.gatewayservice.config;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.function.Supplier;

@Configuration
public class RedisConfig {

    @Bean
    public ProxyManager<String> lettuceBasedProxyManager(RedisConnectionFactory redisConnectionFactory) {
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) redisConnectionFactory;
        RedisClient redisClient = (RedisClient) lettuceFactory.getNativeClient();

        StatefulRedisConnection<String, byte[]> redisConnection = redisClient
                .connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return LettuceBasedProxyManager.builderFor(redisConnection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(5L))
                ).build();
    }

    /**
     * Per-IP limit, read from {@code rate-limit.*} in application.yaml (default 5 requests per 5 minutes).
     * The load-test compose override raises it so k6 isn't throttled; the limiter still runs on every request.
     */
    @Bean
    public Supplier<BucketConfiguration> bucketConfiguration(
            @Value("${rate-limit.capacity}") long capacity,
            @Value("${rate-limit.refill-tokens}") long refillTokens,
            @Value("${rate-limit.refill-period}") Duration refillPeriod) {
        return () -> BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillIntervally(refillTokens, refillPeriod))
                .build();
    }

}
