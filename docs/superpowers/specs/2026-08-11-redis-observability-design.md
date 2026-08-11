# Design: Redis Layer + Observability Stack

Adds two independent, additive pieces to the Unified Service Scheduler:
Redis (idempotency lock + candidate-list cache) and a Prometheus/Grafana/Loki
observability stack. Neither changes the existing booking-correctness
mechanism (MySQL `SELECT ... FOR UPDATE` row locks under `READ_COMMITTED`,
see [`docs/design.md`](../../design.md) §3) — both are explicitly additive
and fail-open.

## 1. Architecture

```mermaid
flowchart LR
    Client --> Filter --> Controller
    Controller -->|Idempotency-Key header| IdemLock["Redisson RLock<br/>(idempotency:{key})"]
    Controller --> Service["BookingService"]
    Service -->|@Cacheable| CandCache["Spring Cache<br/>(Redis, TTL 10min)"]
    Service --> Repos --> MySQL[("MySQL<br/>FOR UPDATE = source of truth")]
    Redis[("Redis<br/>own container")]
    IdemLock --> Redis
    CandCache --> Redis
    App -->|/actuator/prometheus| Prometheus --> Grafana
    App -->|stdout, Docker socket| Promtail --> Loki --> Grafana
```

Five new Docker Compose services: `redis`, `prometheus`, `grafana`, `loki`,
`promtail` (see §5 for why Promtail rather than Docker's native Loki
logging driver). No changes to the existing `app`/`mysql` services' core
behavior.

## 2. Components

- **Redisson `RLock`** — keyed `idempotency:{Idempotency-Key header}`,
  acquired at controller entry via `tryLock` (0 wait, fixed lease), released
  after the response is written. A second concurrent submit with the same
  key gets an immediate `409`, not a block. The header is optional: if
  absent, the lock step is skipped entirely and behavior is identical to
  today.
- **Spring Cache (`@Cacheable`) backed by Redis** — applied to
  `TechnicianRepository.findBySpecialtyAndDealershipIdOrderById` and
  `ServiceBayRepository.findByDealershipIdOrderById`. TTL 10 minutes, no
  active invalidation — there is no update/delete endpoint for technician/bay
  master data today, so TTL-only expiry is sufficient and simpler than wiring
  cache eviction for writes that don't exist yet.
- **Prometheus** — scrapes the existing `/actuator/prometheus` endpoint.
  No application code changes; the Micrometer counters
  (`booking.attempts.total`, `booking.conflicts.total`) and default JVM/HTTP
  metrics are already exposed.
- **Loki + Promtail** — Promtail tails the `app` container's stdout via the
  Docker socket and ships it to Loki. Docker's native `loki` logging driver
  was considered instead (one fewer container) but rejected: it requires
  `docker plugin install grafana/loki-docker-driver` on the host, which
  breaks this project's zero-manual-step guarantee (`docker compose up
  --build` is enough for a reviewer, no host configuration). Promtail's own
  config stays trivial because logs are already structured JSON (ECS
  format) — no parsing regex needed, just ship lines as-is.
- **Grafana** — one dashboard, two datasources (Prometheus + Loki), so
  metrics and logs for the same request/correlation id are visible in one
  place.

## 3. Data Flow — `POST /appointments` (with `Idempotency-Key`)

1. Request passes through `CorrelationIdFilter` as today.
2. In the controller: if an `Idempotency-Key` header is present,
   `tryLock(key, waitSeconds=0, leaseSeconds=30)` is attempted against
   Redis (30s comfortably exceeds normal request latency, short enough that
   a crashed request doesn't block retries for long).
   - Lock not acquired (another in-flight request holds it) → `409`
     immediately, `BookingService` is never invoked.
   - Redis unreachable → **log a warning and proceed without the lock**
     (fail-open). Redis is a UX/dedup convenience layer here, never the
     correctness guarantee.
3. `BookingService.bookAppointment` runs exactly as it does today.
   Candidate-list repository reads go through the `@Cacheable` layer; the
   overlap-detection query and the `FOR UPDATE` row lock still hit MySQL on
   every call, uncached. This is the line booking correctness sits behind —
   nothing cached ever participates in the overlap/availability decision.
4. Response is written, then the idempotency lock is released (the lease
   TTL is also a backstop if release is somehow skipped, e.g. process crash
   mid-request).

## 4. Failure-Mode Principle

Redis is additive, never load-bearing for correctness:

- Redis down → cache falls back to a direct DB read on every call (plain
  cache-miss behavior); idempotency locking is skipped with a warning log.
- MySQL row locks remain the only mechanism that has ever prevented
  double-booking. This design does not change that, and no code path lets a
  Redis failure or a Redis cache hit bypass the MySQL overlap check.

## 5. Technology Choices

| Choice | Why |
|---|---|
| Redisson (not plain Jedis/Lettuce + manual `SETNX`) | `RLock` provides lease/watchdog semantics out of the box instead of hand-rolling TTL-based mutual exclusion and its edge cases (clock skew, missed unlock). |
| Spring Cache abstraction (`@Cacheable`) over raw `RedisTemplate` | Keeps caching declarative and out of `BookingService`'s business logic, consistent with the codebase's existing "thin, single-purpose components" pattern (`docs/design.md` §2). |
| Prometheus + Grafana + Loki over an ELK-style stack | Lower resource footprint for a demo/assessment project; requires no application code changes since Micrometer metrics and structured JSON logs already exist for exactly this purpose. |
| Promtail sidecar over Docker's native Loki logging driver | The driver needs a one-time `docker plugin install` on the host, which a `docker compose up --build`-only reviewer workflow can't do; Promtail is one extra container but needs zero host setup. |

## 6. Testing

- **New:** idempotency-lock test — two concurrent identical requests with
  the same `Idempotency-Key` → exactly one `201`, one `409`. Fast (no DB
  contention involved, the lock is rejected before `BookingService` runs).
- **New:** cache test — verify the candidate-list repository method is not
  re-invoked on a second call within the TTL window (Testcontainers Redis,
  same pattern as the existing MySQL Testcontainers tests).
- **Unaffected:** the existing 19 tests — none send an `Idempotency-Key`
  header, so the new lock path is never exercised by them, and cache-miss
  behavior (cold cache, every test run) is functionally identical to the
  current uncached reads.

## 7. Out of Scope

- Replacing MySQL row locks with Redis locks for the core booking
  concurrency guarantee — explicitly rejected; see design.md §3 for why the
  DB-level lock is the correct mechanism, and this design's §4 for why
  Redis must stay non-load-bearing.
- Caching the availability/overlap check result — would require
  invalidate-on-write correctness work disproportionate to a demo-scope
  addition, and directly risks the double-booking failure mode this system
  exists to prevent.
- Redis clustering/Sentinel — single-node Redis container, matching the
  demo-scope MySQL setup (also single node).
