package com.keyloop.scheduler.repository;

import com.keyloop.scheduler.TestcontainersConfiguration;
import com.keyloop.scheduler.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the candidate-list read is actually cached (real Redis via
 * Testcontainers, not a mock) — not just that @Cacheable is present in the
 * source. The overlap/availability check itself is never cached; only this
 * read-only candidate lookup is.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TechnicianCacheTest {

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void findBySpecialtyAndDealership_populatesCache() {
        Cache cache = cacheManager.getCache(CacheConfig.TECHNICIAN_CANDIDATES_CACHE);
        SimpleKey key = new SimpleKey("OIL_CHANGE", 1L);

        assertThat(cache.get(key)).isNull();

        technicianRepository.findBySpecialtyAndDealershipIdOrderById("OIL_CHANGE", 1L);

        assertThat(cache.get(key)).isNotNull();
    }
}
