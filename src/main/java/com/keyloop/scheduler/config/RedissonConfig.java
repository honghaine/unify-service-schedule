package com.keyloop.scheduler.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the RedissonClient from Spring Boot's DataRedisConnectionDetails
 * (not raw spring.data.redis.* properties directly) so it automatically
 * follows the Testcontainers @ServiceConnection override in tests, the same
 * abstraction Spring Data Redis itself uses.
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(DataRedisConnectionDetails connectionDetails) {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://%s:%d".formatted(
                connectionDetails.getStandalone().getHost(),
                connectionDetails.getStandalone().getPort()));
        return Redisson.create(config);
    }
}
