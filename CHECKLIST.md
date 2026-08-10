# Keyloop Assessment — Submission Checklist

Scenario A: Unified Service Scheduler (Ownership domain). Backend chosen as
the fully-implemented service layer.

## Part 1: System Design Document

File: [`docs/design.md`](docs/design.md)

- [x] Architecture diagram — Mermaid flowchart, §1
- [x] Component roles — §2 (filter, controllers, exception handler, booking
      service, metrics, repositories, MySQL, Flyway)
- [x] Data flow explanation — §3 (`POST /appointments` walkthrough +
      why the locking actually prevents double-booking)
- [x] Tech stack with justifications — §4 (table: Java/Boot version, MySQL
      vs H2, Flyway, Testcontainers, pessimistic locking)
- [x] Observability strategy — §5 (structured JSON logs + correlation id,
      Micrometer conflict-rate counters, health checks, tracing seam noted)
- [x] GenAI-in-design-phase section — §6

## Part 2: Service Implementation (Backend chosen)

- [x] RESTful API — `POST /appointments`, `GET /appointments/{id}`,
      `GET /technicians/{id}/availability` (`src/main/java/.../web/`)
- [x] Persistent database — MySQL 8.4, Flyway-managed schema + seed data
- [x] Client layer mocked/stubbed — cURL examples in `README.md`, plus an
      optional minimal Next.js demo UI in [`frontend/`](frontend/) for
      visual demoing (not the graded layer, per brief's "Backend" option)
- [x] Resource-constrained booking — technician + service bay, both
      required for confirmation
- [x] Real-time availability check — pessimistic lock + overlap query
      before confirming (`BookingService`)
- [x] Confirmed appointment record — persisted `Appointment` linking
      customer, vehicle, technician, service bay
- [x] Scalability/reliability consideration — row locks are DB-level, so
      the guarantee holds under horizontal app scaling (docs/design.md §3)
- [x] Maintainability — thin controllers, business logic isolated in
      `BookingService`, overlap semantics isolated in repository queries
- [x] Observability — see above

## Deliverable 1: System Design Document

- [x] `docs/design.md` committed

## Deliverable 2: Working Code (Git repository)

- [x] Git repo initialized, all source committed (`git log` — commit
      `6fda005`)
- [x] `README.md` — build/run/test instructions (`docker compose up
      --build`, `./mvnw test`), OpenAPI-style cURL examples
- [x] README "AI Collaboration Narrative" section — strategy, verification
      process, two real bugs caught by tests (MySQL isolation-level bug,
      LazyInitializationException) and how each was diagnosed/fixed
- [x] Test suite validating core business logic — 19 tests, 4 suites, all
      against real MySQL via Testcontainers:
  - `AppointmentRepositoryOverlapTest` (10 tests) — overlap-detection edge
    cases
  - `BookingServiceConcurrencyTest` (1 test) — 8 concurrent bookings at the
    same slot, exactly 1 succeeds
  - `AppointmentControllerTest` (7 tests) — REST layer CRUD/validation/
    conflict/not-found
  - `SchedulerApplicationTests` (1 test) — context loads

## Deliverable 3: Video Submission (5–10 min)

**Not started — yours to record.** Suggested outline per the brief:

- [ ] Intro: yourself + Scenario A (Unified Service Scheduler)
- [ ] System design walkthrough — use `docs/design.md`'s architecture
      diagram and §3 (data flow / locking) as the spine
- [ ] Implementation highlights — booking service auto-assignment,
      pessimistic locking, REST layer
- [ ] AI collaboration story (1–2 min) — pull directly from
      `README.md`'s narrative: the plan-review catch (missing
      technicianId in the request shape), then the two bugs the
      concurrency/REST tests caught and how they were root-caused
- [ ] Live demo — `docker compose up --build`, then either the cURL
      walkthrough from `README.md` (201 → 409 → 200 → 400) or the
      `frontend/` demo UI (`npm run dev`, book the same slot twice to show
      the 409 visually)
- [ ] What you learned / challenges faced — the MySQL `REPEATABLE_READ`
      snapshot gotcha is the strongest material here

## Pre-submission sanity checks

- [x] `./mvnw test` passes clean (19/19)
- [x] `docker compose up --build` verified end-to-end with live cURL calls
- [ ] Final `git log` / `git status` clean before zipping or pushing
- [ ] Confirm repo has no secrets/credentials committed (seed DB creds in
      `docker-compose.yml` are dev-only defaults, fine to publish)
- [ ] Record and attach video
- [ ] Package/submit: System Design Document + Git repo link + video
