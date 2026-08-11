# Redis Layer + Observability Stack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Redis layer (idempotency lock on booking submit + candidate-list cache) and a Prometheus/Grafana/Loki observability stack to the Unified Service Scheduler, both additive and never load-bearing for the existing MySQL-lock-based booking-correctness guarantee.

**Architecture:** Redisson `RLock` gates duplicate concurrent `POST /appointments` submits sharing an `Idempotency-Key` header before they reach `BookingService`; Spring Cache (Redis-backed) caches the technician/service-bay candidate-list reads inside `BookingService`, never the overlap/availability check itself. Prometheus scrapes the existing `/actuator/prometheus` endpoint; Promtail ships the app's existing structured JSON logs to Loki; Grafana visualizes both. All five pieces are new Docker Compose services — no changes to `app`/`mysql` core behavior.

**Tech Stack:** Redis 7.4, Redisson 4.7.0 (Java client), Spring Data Redis + Spring Cache (Boot 4.0.7 starters), `com.redis:testcontainers-redis` 2.2.4, Prometheus v3.0.1, Grafana 11.4.0, Loki + Promtail 3.3.2.

## Global Constraints

- Redis must never be load-bearing for booking correctness — MySQL `SELECT ... FOR UPDATE` under `READ_COMMITTED` remains the only mechanism that prevents double-booking (see `docs/design.md` §3). Any Redis failure must fail open (log a warning, proceed as if Redis weren't there), never fail closed and never silently change the booking outcome.
- Candidate-list cache TTL: 10 minutes, no active invalidation (no update/delete endpoint exists for technician/service-bay master data).
- Idempotency lock: keyed `idempotency:{Idempotency-Key header}`, `tryLock(wait=0, lease=30s)`. Header is optional — absent header means the lock step is skipped entirely, behavior identical to before this plan.
- Never cache the overlap/availability check or anything that participates in the booking decision — only the read-only candidate list (`findBySpecialtyAndDealershipIdOrderById`, `findByDealershipIdOrderById`).
- Observability stack must not require any one-time host setup — `docker compose up --build` alone must be sufficient for a reviewer (this is why Promtail is used instead of Docker's native `loki` logging driver, which needs `docker plugin install`).
- Single-node Redis, matching the existing single-node MySQL setup — no clustering/Sentinel.
- Pinned versions (not managed by the Spring Boot BOM, verified to resolve against this project's Boot 4.0.7 parent): `org.redisson:redisson:4.7.0`, `com.redis:testcontainers-redis:2.2.4`.

---

## Task 1: Redis Infrastructure Plumbing

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `docker-compose.yml`
- Modify: `src/test/java/com/keyloop/scheduler/TestcontainersConfiguration.java`
- Create: `src/main/java/com/keyloop/scheduler/config/RedissonConfig.java`

**Interfaces:**
- Produces: `RedissonClient` bean (from `RedissonConfig`), consumed by Task 3's `IdempotencyLockService`.
- Produces: `spring.data.redis.host` / `spring.data.redis.port` properties (and their Testcontainers override via `@ServiceConnection`), consumed by Task 2's Redis-backed `CacheManager` autoconfiguration and by `RedissonConfig`.

- [ ] **Step 1: Add Redis dependencies to `pom.xml`**

Add these four dependencies. Place the first two next to the existing `spring-boot-starter-data-jpa` entry, and the test-scoped ones next to the other `-test` suffixed starters:

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-redis</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-cache</artifactId>
		</dependency>
		<dependency>
			<groupId>org.redisson</groupId>
			<artifactId>redisson</artifactId>
			<version>4.7.0</version>
		</dependency>
```

```xml
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-redis-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-cache-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>com.redis</groupId>
			<artifactId>testcontainers-redis</artifactId>
			<version>2.2.4</version>
			<scope>test</scope>
		</dependency>
```

- [ ] **Step 2: Add Redis connection properties to `application.properties`**

Append after the Flyway section:

```properties
# --- Redis (idempotency lock + candidate-list cache; overridden by docker-compose / Testcontainers) ---
spring.data.redis.host=${SPRING_DATA_REDIS_HOST:localhost}
spring.data.redis.port=${SPRING_DATA_REDIS_PORT:6379}
```

- [ ] **Step 3: Add the `redis` service to `docker-compose.yml` and wire it into `app`**

Add a new `redis` service (alongside `mysql`), and add the two `SPRING_DATA_REDIS_*` env vars plus a `redis` dependency to the existing `app` service:

```yaml
  redis:
    image: redis:7.4-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 10
```

In the existing `app` service, change:

```yaml
    depends_on:
      mysql:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/scheduler
      SPRING_DATASOURCE_USERNAME: scheduler
      SPRING_DATASOURCE_PASSWORD: scheduler
```

to:

```yaml
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/scheduler
      SPRING_DATASOURCE_USERNAME: scheduler
      SPRING_DATASOURCE_PASSWORD: scheduler
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: "6379"
```

- [ ] **Step 4: Add a Redis Testcontainer to `TestcontainersConfiguration`**

Full new contents of `src/test/java/com/keyloop/scheduler/TestcontainersConfiguration.java`:

```java
package com.keyloop.scheduler;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
	}

	@Bean
	@ServiceConnection
	RedisContainer redisContainer() {
		return new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));
	}

}
```

- [ ] **Step 5: Create `RedissonConfig`**

`src/main/java/com/keyloop/scheduler/config/RedissonConfig.java`:

```java
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
```

- [ ] **Step 6: Run the full test suite to verify nothing broke**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`, all 19 existing tests still pass (check `target/surefire-reports/*.txt` for `Failures: 0, Errors: 0` in all 4 suites). This confirms the `RedissonClient` bean wires up correctly against the Testcontainers Redis instance at application-context-startup time, before any feature code uses it.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/resources/application.properties docker-compose.yml \
  src/test/java/com/keyloop/scheduler/TestcontainersConfiguration.java \
  src/main/java/com/keyloop/scheduler/config/RedissonConfig.java
git commit -m "$(cat <<'EOF'
Add Redis infrastructure: dependencies, config, Testcontainers, Redisson client

Plumbing only — no feature behavior yet. Verified via full existing test
suite still passing with a Redis Testcontainer present in the context.
EOF
)"
```

---

## Task 2: Candidate-List Caching

**Files:**
- Create: `src/main/java/com/keyloop/scheduler/config/CacheConfig.java`
- Modify: `src/main/java/com/keyloop/scheduler/repository/TechnicianRepository.java`
- Modify: `src/main/java/com/keyloop/scheduler/repository/ServiceBayRepository.java`
- Create: `src/test/java/com/keyloop/scheduler/repository/TechnicianCacheTest.java`

**Interfaces:**
- Consumes: Redis connection from Task 1 (`spring.data.redis.*` properties, Testcontainers `@ServiceConnection`).
- Produces: `CacheConfig.TECHNICIAN_CANDIDATES_CACHE` and `CacheConfig.SERVICE_BAY_CANDIDATES_CACHE` cache-name constants — not consumed by any later task in this plan, but this is the naming any future cache addition should follow.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/keyloop/scheduler/repository/TechnicianCacheTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=TechnicianCacheTest`
Expected: FAIL — `cacheManager.getCache(CacheConfig.TECHNICIAN_CANDIDATES_CACHE)` returns `null` (no such cache configured yet, `CacheConfig` doesn't exist), or a compile error since `CacheConfig` doesn't exist yet. Either is the expected pre-implementation failure.

- [ ] **Step 3: Create `CacheConfig`**

`src/main/java/com/keyloop/scheduler/config/CacheConfig.java`:

```java
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
```

`@EnableCaching` auto-detects this `CacheErrorHandler` bean without needing a `CachingConfigurer` — Spring's caching support wires in any uniquely-typed `CacheErrorHandler`/`KeyGenerator`/`CacheResolver` bean found in the context automatically.

- [ ] **Step 4: Annotate the two candidate-list repository methods**

In `src/main/java/com/keyloop/scheduler/repository/TechnicianRepository.java`, add the import `org.springframework.cache.annotation.Cacheable` and annotate the candidate-list method (leave `lockById` untouched — that one must never be cached):

```java
    @Cacheable(CacheConfig.TECHNICIAN_CANDIDATES_CACHE)
    List<Technician> findBySpecialtyAndDealershipIdOrderById(String specialty, Long dealershipId);
```

Add `import com.keyloop.scheduler.config.CacheConfig;` to the file's imports.

In `src/main/java/com/keyloop/scheduler/repository/ServiceBayRepository.java`, same pattern:

```java
    @Cacheable(CacheConfig.SERVICE_BAY_CANDIDATES_CACHE)
    List<ServiceBay> findByDealershipIdOrderById(Long dealershipId);
```

Add `import com.keyloop.scheduler.config.CacheConfig;` and `import org.springframework.cache.annotation.Cacheable;` to this file's imports too.

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=TechnicianCacheTest`
Expected: PASS.

- [ ] **Step 6: Run the full suite to confirm no regression**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`, 20/20 (19 existing + 1 new). The booking flow's own tests (`AppointmentControllerTest`, `BookingServiceConcurrencyTest`) still pass — caching the candidate list doesn't change booking outcomes because the overlap check and `FOR UPDATE` lock are never cached.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/keyloop/scheduler/config/CacheConfig.java \
  src/main/java/com/keyloop/scheduler/repository/TechnicianRepository.java \
  src/main/java/com/keyloop/scheduler/repository/ServiceBayRepository.java \
  src/test/java/com/keyloop/scheduler/repository/TechnicianCacheTest.java
git commit -m "$(cat <<'EOF'
Cache technician/service-bay candidate-list reads (Redis, 10min TTL)

Only the read-only candidate list is cached — the overlap/availability
check and FOR UPDATE lock still hit MySQL on every call, so booking
correctness is unaffected.
EOF
)"
```

---

## Task 3: Idempotency Lock on Booking Submit

**Files:**
- Create: `src/main/java/com/keyloop/scheduler/service/IdempotencyLockService.java`
- Modify: `src/main/java/com/keyloop/scheduler/web/AppointmentController.java`
- Create: `src/test/java/com/keyloop/scheduler/web/IdempotencyLockControllerTest.java`

**Interfaces:**
- Consumes: `RedissonClient` bean from Task 1 (`RedissonConfig`).
- Produces: `IdempotencyLockService.Outcome` enum (`ACQUIRED`, `REJECTED`, `SKIPPED`) and `IdempotencyLockService.LockAttempt` record (`outcome`, `lock`) — not consumed by any later task in this plan, but this is the shape any caller of the lock service uses.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/keyloop/scheduler/web/IdempotencyLockControllerTest.java`:

```java
package com.keyloop.scheduler.web;

import com.keyloop.scheduler.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Idempotency-Key lock is a UX/dedup layer in front of BookingService,
 * not a replacement for the MySQL-level correctness guarantee (see
 * BookingServiceConcurrencyTest for that one). Deliberately uses five
 * *non-overlapping* time windows for the same technician — MySQL's overlap
 * lock would happily confirm all five on its own, so if this test observes
 * only one 201, that can only be explained by the shared Idempotency-Key
 * rejecting the other four before they ever reach BookingService. (An
 * earlier version of this test used identical, overlapping requests, which
 * would have passed even with a no-op lock, since MySQL alone already
 * serializes those to one winner — it didn't isolate the Redis-specific
 * behavior.)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IdempotencyLockControllerTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void concurrentSubmitsWithSameIdempotencyKey_onlyOneReaches201() throws Exception {
        LocalDateTime baseHour = LocalDateTime.now().plusDays(10).withHour(8).withMinute(0).withSecond(0).withNano(0);
        String idempotencyKey = "test-key-" + System.nanoTime();
        int attempts = 5;

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        for (int i = 0; i < attempts; i++) {
            LocalDateTime start = baseHour.plusHours(i);
            LocalDateTime end = start.plusHours(1);
            String requestJson = createRequestJson(1L, "OIL_CHANGE", 1L, start, end);
            executor.submit(() -> {
                try {
                    startSignal.await();
                    int responseStatus = mockMvc.perform(post("/appointments")
                                    .contentType("application/json")
                                    .header("Idempotency-Key", idempotencyKey)
                                    .content(requestJson))
                            .andReturn().getResponse().getStatus();
                    if (responseStatus == 201) {
                        created.incrementAndGet();
                    } else if (responseStatus == 409) {
                        conflicted.incrementAndGet();
                    } else {
                        unexpected.add(new AssertionError("unexpected status " + responseStatus));
                    }
                } catch (Throwable t) {
                    unexpected.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        startSignal.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(unexpected).isEmpty();
        assertThat(created.get()).isEqualTo(1);
        assertThat(conflicted.get()).isEqualTo(attempts - 1);
    }

    @Test
    void submitWithoutIdempotencyKey_behavesAsBefore() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(11).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        mockMvc.perform(post("/appointments")
                        .contentType("application/json")
                        .content(createRequestJson(3L, "OIL_CHANGE", 2L, start, end)))
                .andExpect(status().isCreated());
    }

    private String createRequestJson(Long vehicleId, String serviceType, Long dealershipId, LocalDateTime start, LocalDateTime end) {
        return """
                {"vehicleId":%d,"serviceType":"%s","dealershipId":%d,"desiredStart":"%s","desiredEnd":"%s"}
                """.formatted(vehicleId, serviceType, dealershipId, ISO.format(start), ISO.format(end));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=IdempotencyLockControllerTest`
Expected: FAIL on `concurrentSubmitsWithSameIdempotencyKey_onlyOneReaches201` with `created.get() == 5` (not the asserted `1`). The `Idempotency-Key` header exists on the request but nothing in the controller reads it yet, so all five non-overlapping-window requests reach `BookingService` and all five succeed — MySQL sees no time-window overlap between them, so its own locking has nothing to reject here. This is the deterministic pre-implementation failure; the second test (`submitWithoutIdempotencyKey_behavesAsBefore`) should already pass since it only exercises the unchanged existing behavior.

- [ ] **Step 3: Create `IdempotencyLockService`**

`src/main/java/com/keyloop/scheduler/service/IdempotencyLockService.java`:

```java
package com.keyloop.scheduler.service;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Gates duplicate concurrent POST /appointments submits that share the same
 * client-supplied Idempotency-Key header, before they ever reach
 * BookingService. This is a UX/dedup convenience, not a correctness
 * mechanism — MySQL row locking in BookingService is what actually prevents
 * double-booking. If Redis is unreachable, this fails open (proceeds as if
 * no key were supplied) rather than blocking bookings.
 */
@Service
public class IdempotencyLockService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyLockService.class);
    private static final long LEASE_SECONDS = 30;

    private final RedissonClient redissonClient;

    public IdempotencyLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public enum Outcome { ACQUIRED, REJECTED, SKIPPED }

    public record LockAttempt(Outcome outcome, RLock lock) {}

    public LockAttempt tryAcquire(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new LockAttempt(Outcome.SKIPPED, null);
        }
        try {
            RLock lock = redissonClient.getLock("idempotency:" + idempotencyKey);
            boolean acquired = lock.tryLock(0, LEASE_SECONDS, TimeUnit.SECONDS);
            return acquired ? new LockAttempt(Outcome.ACQUIRED, lock) : new LockAttempt(Outcome.REJECTED, null);
        } catch (Exception e) {
            log.warn("idempotency.lock.unavailable key={} reason={}", idempotencyKey, e.toString());
            return new LockAttempt(Outcome.SKIPPED, null);
        }
    }

    public void release(LockAttempt attempt) {
        if (attempt.outcome() == Outcome.ACQUIRED && attempt.lock().isHeldByCurrentThread()) {
            attempt.lock().unlock();
        }
    }
}
```

- [ ] **Step 4: Wire the lock into `AppointmentController`**

Full new contents of `src/main/java/com/keyloop/scheduler/web/AppointmentController.java`:

```java
package com.keyloop.scheduler.web;

import com.keyloop.scheduler.domain.Appointment;
import com.keyloop.scheduler.exception.BookingConflictException;
import com.keyloop.scheduler.exception.ResourceNotFoundException;
import com.keyloop.scheduler.repository.AppointmentRepository;
import com.keyloop.scheduler.service.BookAppointmentCommand;
import com.keyloop.scheduler.service.BookingService;
import com.keyloop.scheduler.service.IdempotencyLockService;
import com.keyloop.scheduler.web.dto.AppointmentResponse;
import com.keyloop.scheduler.web.dto.CreateAppointmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final BookingService bookingService;
    private final AppointmentRepository appointmentRepository;
    private final IdempotencyLockService idempotencyLockService;

    public AppointmentController(
            BookingService bookingService,
            AppointmentRepository appointmentRepository,
            IdempotencyLockService idempotencyLockService) {
        this.bookingService = bookingService;
        this.appointmentRepository = appointmentRepository;
        this.idempotencyLockService = idempotencyLockService;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> create(
            @Valid @RequestBody CreateAppointmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        IdempotencyLockService.LockAttempt lockAttempt = idempotencyLockService.tryAcquire(idempotencyKey);
        if (lockAttempt.outcome() == IdempotencyLockService.Outcome.REJECTED) {
            throw new BookingConflictException(
                    "Duplicate request in flight for Idempotency-Key=%s".formatted(idempotencyKey));
        }
        try {
            Appointment appointment = bookingService.bookAppointment(new BookAppointmentCommand(
                    request.vehicleId(),
                    request.serviceType(),
                    request.dealershipId(),
                    request.desiredStart(),
                    request.desiredEnd()));
            return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
        } finally {
            idempotencyLockService.release(lockAttempt);
        }
    }

    @GetMapping("/{id}")
    public AppointmentResponse getById(@PathVariable Long id) {
        Appointment appointment = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment %d not found".formatted(id)));
        return AppointmentResponse.from(appointment);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=IdempotencyLockControllerTest`
Expected: PASS — both tests.

- [ ] **Step 6: Run the full suite to confirm no regression**

Run: `./mvnw test`
Expected: `BUILD SUCCESS`, 22/22 (20 from before + 2 new). In particular, `AppointmentControllerTest` and `BookingServiceConcurrencyTest` still pass unchanged — none of their requests send an `Idempotency-Key` header, so the new lock path is skipped for all of them (`Outcome.SKIPPED`), confirming backward compatibility.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/keyloop/scheduler/service/IdempotencyLockService.java \
  src/main/java/com/keyloop/scheduler/web/AppointmentController.java \
  src/test/java/com/keyloop/scheduler/web/IdempotencyLockControllerTest.java
git commit -m "$(cat <<'EOF'
Add optional Idempotency-Key lock on POST /appointments (Redis, fail-open)

Rejects a duplicate concurrent submit sharing the same client-supplied key
before it reaches BookingService. UX/dedup layer only — MySQL row locking
remains the sole correctness guarantee against double-booking. Missing
header or unreachable Redis both fall back to pre-existing behavior.
EOF
)"
```

---

## Task 4: Observability Stack (Prometheus + Grafana + Loki + Promtail)

**Files:**
- Create: `observability/prometheus/prometheus.yml`
- Create: `observability/promtail/promtail-config.yml`
- Create: `observability/grafana/provisioning/datasources/datasources.yml`
- Create: `observability/grafana/provisioning/dashboards/dashboards.yml`
- Create: `observability/grafana/dashboards/scheduler.json`
- Modify: `docker-compose.yml`

**Interfaces:**
- Consumes: the app's existing `/actuator/prometheus` endpoint (unchanged, already exposed) and existing structured JSON stdout logs (unchanged).
- Produces: nothing consumed by later tasks — this is a leaf, infra-only addition.

- [ ] **Step 1: Create the Prometheus scrape config**

`observability/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: scheduler-app
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["app:8080"]
```

- [ ] **Step 2: Create the Promtail config**

`observability/promtail/promtail-config.yml`:

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 5s
    relabel_configs:
      - source_labels: ['__meta_docker_container_label_com_docker_compose_service']
        target_label: 'compose_service'
      - source_labels: ['__meta_docker_container_name']
        regex: '/(.*)'
        target_label: 'container'
```

- [ ] **Step 3: Create Grafana datasource + dashboard provisioning**

`observability/grafana/provisioning/datasources/datasources.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    uid: prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
  - name: Loki
    uid: loki
    type: loki
    access: proxy
    url: http://loki:3100
```

`observability/grafana/provisioning/dashboards/dashboards.yml`:

```yaml
apiVersion: 1

providers:
  - name: scheduler
    folder: Scheduler
    type: file
    options:
      path: /var/lib/grafana/dashboards
```

- [ ] **Step 4: Create the Grafana dashboard JSON**

`observability/grafana/dashboards/scheduler.json`:

```json
{
  "title": "Unified Service Scheduler",
  "uid": "scheduler-overview",
  "timezone": "browser",
  "schemaVersion": 39,
  "version": 1,
  "refresh": "10s",
  "time": { "from": "now-6h", "to": "now" },
  "panels": [
    {
      "id": 1,
      "title": "Booking attempts vs conflicts (rate/5m)",
      "type": "timeseries",
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 0 },
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "targets": [
        { "expr": "rate(booking_attempts_total[5m])", "legendFormat": "attempts", "refId": "A" },
        { "expr": "rate(booking_conflicts_total[5m])", "legendFormat": "conflicts", "refId": "B" }
      ]
    },
    {
      "id": 2,
      "title": "HTTP request rate by status",
      "type": "timeseries",
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 0 },
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "targets": [
        { "expr": "sum by (status) (rate(http_server_requests_seconds_count[5m]))", "legendFormat": "{{status}}", "refId": "A" }
      ]
    },
    {
      "id": 3,
      "title": "JVM heap used",
      "type": "timeseries",
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 8 },
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "targets": [
        { "expr": "jvm_memory_used_bytes{area=\"heap\"}", "legendFormat": "{{id}}", "refId": "A" }
      ]
    },
    {
      "id": 4,
      "title": "App logs",
      "type": "logs",
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 8 },
      "datasource": { "type": "loki", "uid": "loki" },
      "targets": [
        { "expr": "{compose_service=\"app\"}", "refId": "A" }
      ]
    }
  ]
}
```

- [ ] **Step 5: Add the four services to `docker-compose.yml`**

Add after the `redis` service (from Task 1):

```yaml
  prometheus:
    image: prom/prometheus:v3.0.1
    volumes:
      - ./observability/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "9090:9090"
    depends_on:
      - app

  loki:
    image: grafana/loki:3.3.2
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml

  promtail:
    image: grafana/promtail:3.3.2
    volumes:
      - ./observability/promtail/promtail-config.yml:/etc/promtail/config.yml:ro
      - /var/run/docker.sock:/var/run/docker.sock
    command: -config.file=/etc/promtail/config.yml
    depends_on:
      - loki
      - app

  grafana:
    image: grafana/grafana:11.4.0
    ports:
      - "3001:3000"
    environment:
      GF_AUTH_ANONYMOUS_ENABLED: "true"
      GF_AUTH_ANONYMOUS_ORG_ROLE: Admin
    volumes:
      - ./observability/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./observability/grafana/dashboards:/var/lib/grafana/dashboards:ro
    depends_on:
      - prometheus
      - loki
```

- [ ] **Step 6: Bring the stack up and verify each piece**

Run: `docker compose up --build -d`
Expected: all 7 services (`mysql`, `redis`, `app`, `prometheus`, `loki`, `promtail`, `grafana`) report `Started` or `Healthy`.

Run: `curl -sf http://localhost:9090/-/healthy`
Expected: `Prometheus Server is Healthy.`

Run: `curl -s http://localhost:9090/api/v1/targets | grep -o '"health":"up"'`
Expected: at least one line of output (the `scheduler-app` target is up).

Run: `curl -sf http://localhost:3100/ready`
Expected: `ready`

Run:
```bash
curl -X POST http://localhost:8080/appointments \
  -H "Content-Type: application/json" \
  -d '{"vehicleId":1,"serviceType":"OIL_CHANGE","dealershipId":1,"desiredStart":"2026-12-01T09:00:00","desiredEnd":"2026-12-01T10:00:00"}'
sleep 20
curl -s 'http://localhost:9090/api/v1/query?query=booking_attempts_total' | grep -o '"value":\[[^]]*\]'
```
Expected: the booking call returns `201`, and the Prometheus query returns a value greater than 0 (scrape_interval is 15s, so allow it to run at least once).

Run: `curl -sf http://localhost:3001/api/health`
Expected: `{"database": "ok", ...}` (Grafana up).

Run: `curl -s http://localhost:3001/api/search?query=Unified`
Expected: JSON array containing the "Unified Service Scheduler" dashboard (proves provisioning picked up the dashboard file).

- [ ] **Step 7: Tear down**

Run: `docker compose down`

- [ ] **Step 8: Commit**

```bash
git add observability/ docker-compose.yml
git commit -m "$(cat <<'EOF'
Add Prometheus + Grafana + Loki + Promtail observability stack

Prometheus scrapes the existing /actuator/prometheus endpoint; Promtail
ships existing structured JSON logs to Loki (Docker's native loki logging
driver was rejected — it needs docker plugin install on the host, which
breaks the docker-compose-only reviewer workflow). No app code changes.
EOF
)"
```

---

## Task 5: Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/design.md`

**Interfaces:**
- Consumes: nothing (documentation only, describes the finished state of Tasks 1–4).

- [ ] **Step 1: Add a Redis + Observability section to `README.md`**

Insert a new section after the existing "## Seed data" section (before "## API"):

```markdown
## Redis + Observability Stack

`docker compose up --build` also starts Redis (idempotency lock + candidate
cache), Prometheus, Grafana, Loki, and Promtail. None of these are required
for the core booking guarantee — MySQL row locking is (see [System Design
Document](docs/design.md) §3) — they're additive.

| Service | URL | Notes |
|---|---|---|
| Redis | `localhost:6379` | idempotency lock + candidate-list cache |
| Prometheus | http://localhost:9090 | scrapes `/actuator/prometheus` |
| Grafana | http://localhost:3001 | anonymous admin access enabled for local demo; dashboard: "Unified Service Scheduler" |
| Loki | http://localhost:3100 | log storage, queried via Grafana |

### Idempotency-Key (optional)

`POST /appointments` accepts an optional `Idempotency-Key` header. A second
concurrent request with the same key gets an immediate `409` before it ever
reaches the booking logic — useful for retry-safe clients (e.g. a double
form submit). Omitting the header is unchanged from before this feature.

```bash
curl -X POST http://localhost:8080/appointments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: my-retry-key-123" \
  -d '{"vehicleId":1,"serviceType":"OIL_CHANGE","dealershipId":1,"desiredStart":"2026-09-01T09:00:00","desiredEnd":"2026-09-01T10:00:00"}'
```
```

- [ ] **Step 2: Add an addendum section to `docs/design.md`**

Append after §6 (GenAI usage):

```markdown

## 7. Addendum: Redis Layer + Observability Stack

Added after the initial submission: an optional Redis layer (idempotency
lock on `POST /appointments`, 10-minute-TTL cache on the read-only
technician/service-bay candidate lists) and a Prometheus/Grafana/Loki
observability stack. Full design rationale, including why these are
explicitly additive and fail-open with respect to the booking-correctness
guarantee in §3, is in
[`docs/superpowers/specs/2026-08-11-redis-observability-design.md`](superpowers/specs/2026-08-11-redis-observability-design.md).
The one-sentence version: MySQL row locking is still the only thing that
has ever prevented double-booking — Redis being down never changes a
booking outcome, only whether a duplicate submit gets deduped early and
whether a candidate-list read hits cache or DB.
```

- [ ] **Step 3: Verify the docs render sensibly**

Run: `grep -c "Redis" README.md docs/design.md`
Expected: non-zero count in both files (sanity check the sections were actually inserted, not just staged).

- [ ] **Step 4: Commit**

```bash
git add README.md docs/design.md
git commit -m "$(cat <<'EOF'
Document Redis layer and observability stack in README and design.md
EOF
)"
```
