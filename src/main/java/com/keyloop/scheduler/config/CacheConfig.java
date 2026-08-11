package com.keyloop.scheduler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

/**
 * Caches only the read-only technician/service-bay candidate lists used to
 * build the search order inside BookingService — never the overlap/
 * availability check that decides whether a booking succeeds. 10-minute TTL,
 * no active invalidation: there is no update/delete endpoint for
 * technician/service-bay master data today.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    public static final String TECHNICIAN_CANDIDATES_CACHE = "technicianCandidates";
    public static final String SERVICE_BAY_CANDIDATES_CACHE = "serviceBayCandidates";

    @Bean
    public RedisCacheManagerBuilderCustomizer candidateCacheCustomizer() {
        RedisCacheConfiguration tenMinuteTtl = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10));
        return builder -> builder
                .withCacheConfiguration(TECHNICIAN_CANDIDATES_CACHE, tenMinuteTtl)
                .withCacheConfiguration(SERVICE_BAY_CANDIDATES_CACHE, tenMinuteTtl);
    }

    /**
     * Spring's default cache error behavior is to let a Redis connection
     * failure propagate as an exception out of the annotated method — the
     * opposite of fail-open. This logs and swallows get/put/evict/clear
     * errors instead, so a Redis outage degrades to "always cache miss,
     * always hit the DB" rather than breaking the booking request.
     */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("cache.get.unavailable cache={} reason={}", cache.getName(), exception.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("cache.put.unavailable cache={} reason={}", cache.getName(), exception.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("cache.evict.unavailable cache={} reason={}", cache.getName(), exception.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("cache.clear.unavailable cache={} reason={}", cache.getName(), exception.toString());
            }
        };
    }
}
