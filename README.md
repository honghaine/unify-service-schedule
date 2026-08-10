# Unified Service Scheduler (Keyloop Technical Assessment — Scenario A)

Backend-only implementation of a dealership appointment scheduler. The core
challenge this project solves: two customers requesting the same technician
or service bay at the same time must never both get confirmed — one gets a
`201`, the other gets a `409`, even under concurrent load.

The backend is the graded deliverable; the API is exercised via cURL
examples below and a suite of automated tests. A minimal Next.js demo UI
also lives in [`frontend/`](frontend/) for visually exercising the booking
flow — see [`frontend/README.md`](frontend/README.md).

## Tech Stack

- Java 21, Spring Boot 4.0.7 (Web, Data JPA, Validation, Actuator)
- MySQL 8.4, run as its own container via Docker Compose
- Flyway for schema migration + seed data
- Testcontainers (real MySQL) for integration tests, JUnit 5
- Maven

## Prerequisites

- Docker (with the daemon running) — for `docker compose up` and for running
  the test suite (Testcontainers spins up a real MySQL container per test
  run)
- JDK 21+ if you want to build/run outside Docker

## Run the app

```bash
docker compose up --build
```

This builds the app image, starts a MySQL 8.4 container, waits for it to be
healthy, then starts the app. Flyway runs the schema + seed migrations
automatically on startup. The API is available at `http://localhost:8080`.

- MySQL is also reachable on the host at `localhost:3307` (mapped from the
  container's `3306`) for manual inspection, e.g. `mysql -h 127.0.0.1 -P 3307
  -u scheduler -pscheduler scheduler`. Port `3307` (not `3306`) was chosen
  because `3306` was already bound by another local MySQL instance in
  development.
- `docker compose down` stops everything; add `-v` to also drop the
  `mysql_data` volume and reset seed data.

Health check: `curl http://localhost:8080/actuator/health`

## Run the tests

```bash
./mvnw test
```

Requires Docker running locally — every test class boots a real MySQL 8.4
container via Testcontainers (no H2/mocking of the database). 19 tests across
4 suites:

- `AppointmentRepositoryOverlapTest` — overlap-detection query edge cases
  (exact match, partial overlap at each end, contained-within, back-to-back
  adjacency on both sides, disjoint, cancelled appointments ignored).
- `BookingServiceConcurrencyTest` — **the most important test.** Fires 8
  concurrent booking requests at the same seeded technician/slot and asserts
  exactly 1 succeeds and 7 get a conflict. This is what actually proves the
  pessimistic-locking strategy works, not just that it's asserted in prose
  (see [System Design Document](docs/design.md) for why `READ_COMMITTED`
  isolation was required to make this true under MySQL).
- `AppointmentControllerTest` — REST layer: create, get, 400/404/409 paths.
- `SchedulerApplicationTests` — context loads smoke test.

## Seed data

`V2__seed_data.sql` seeds two dealerships:

| Dealership 1 (id=1) | Dealership 2 (id=2) |
|---|---|
| Technicians: Sam Rivera (OIL_CHANGE), Jordan Lee (BRAKES), Taylor Brooks (TIRE_ROTATION) | Morgan Blake (OIL_CHANGE), Casey Kim (BRAKES) |
| Service bays: A1, A2 | Service bay: B1 |

Vehicles: id=1 (Honda Accord, customer 1), id=2 (Ford F-150, customer 2),
id=3 (BMW 3 Series, customer 3).

## API

### `POST /appointments`

Books an appointment. The caller does **not** pick a technician or bay — the
service auto-assigns the first technician whose `specialty` matches
`serviceType` at the given `dealershipId` and is free for the window, plus
the first free bay at that dealership. See "Assumptions" below.

```bash
curl -X POST http://localhost:8080/appointments \
  -H "Content-Type: application/json" \
  -d '{
    "vehicleId": 1,
    "serviceType": "OIL_CHANGE",
    "dealershipId": 1,
    "desiredStart": "2026-09-01T09:00:00",
    "desiredEnd": "2026-09-01T10:00:00"
  }'
# -> 201 Created
# {"id":1,"vehicleId":1,"vehicleVin":"1HGCM82633A004352","customerId":1,
#  "technicianId":1,"technicianName":"Sam Rivera","serviceBayId":1,
#  "bayNumber":"A1","serviceType":"OIL_CHANGE",
#  "startTime":"2026-09-01T09:00:00","endTime":"2026-09-01T10:00:00",
#  "status":"CONFIRMED"}
```

Booking the same slot again returns `409 Conflict`:

```bash
curl -X POST http://localhost:8080/appointments \
  -H "Content-Type: application/json" \
  -d '{"vehicleId":1,"serviceType":"OIL_CHANGE","dealershipId":1,"desiredStart":"2026-09-01T09:00:00","desiredEnd":"2026-09-01T10:00:00"}'
# -> 409 Conflict
```

Missing/invalid fields return `400 Bad Request` with a `fieldErrors` list;
an unknown `vehicleId` returns `404 Not Found`.

### `GET /appointments/{id}`

```bash
curl http://localhost:8080/appointments/1
```

### `GET /technicians/{id}/availability?date=YYYY-MM-DD`

Returns the technician's confirmed busy windows for that day (nice-to-have,
for manual/demo inspection rather than a hard scenario requirement):

```bash
curl "http://localhost:8080/technicians/1/availability?date=2026-09-01"
# {"technicianId":1,"date":"2026-09-01","busyWindows":[{"startTime":"2026-09-01T09:00:00","endTime":"2026-09-01T10:00:00"}]}
```

## Assumptions

The brief left several details unspecified. Decisions made, and why:

1. **Auto-assignment.** The request has no `technicianId`/`serviceBayId`
   field ("request a service appointment for a specific vehicle, service
   type, and dealership"), so the backend must pick both. `Technician` got a
   `specialty` string matched exactly against `serviceType`, plus a
   `dealershipId` (not in the original field list, added because "qualified
   Technician" only makes sense scoped to the dealership being booked at —
   otherwise a technician from a different location could be assigned to a
   bay they don't work out of).
2. **Tech stack version.** The plan called for Spring Boot 3; by the time
   this was built, `start.spring.io` no longer offered Boot 3.x (only
   4.0+), so this targets Spring Boot 4.0.7 instead. No functional
   difference for this project.
3. **MySQL over H2.** An early design considered H2 for zero-setup local
   dev; this was changed to a real MySQL 8 container so the concurrency test
   proves the locking strategy against the actual production-grade engine,
   not H2's weaker locking semantics.

## AI Collaboration Narrative

This project was built with Claude Code as an active collaborator, not just
an autocomplete. Rough breakdown of how the work was split:

**Design phase.** The user (me) came in with a mostly-formed technical plan
already (stack, data model, locking approach, docker layout, test strategy)
written from prior experience with this exact class of problem. Claude's
job here was less "propose a design" and more "find the holes in mine" —
it flagged two real gaps before any code was written: the `POST
/appointments` request body had no `technicianId`/`serviceBayId`, so
something had to decide auto-assignment logic; and the deliverables
checklist mentioned Testcontainers while an earlier section described
H2-only testing — a genuine contradiction that needed resolving before
committing to a test strategy. Both got resolved via direct questions
before implementation started, not silently guessed at.

**Implementation phase.** Claude wrote the full Spring Boot application —
entities, repositories, the booking service, REST layer, Flyway migrations,
and the full test suite — working directly against a real MySQL
Testcontainers instance rather than mocks, so failures were real failures
and not artifacts of a fake datastore.

**Verification, not trust.** The concurrency test is the clearest example of
why "the AI wrote it and it compiled" isn't the bar. The first version of
`BookingService` looked correct — pessimistic `SELECT ... FOR UPDATE` locks
on the technician/bay rows, standard pattern — and it compiled and passed
every other test. Running the concurrency test (8 threads racing for the
same technician/slot) failed loudly: **8 out of 8 succeeded** instead of 1.
Root cause: MySQL's default `REPEATABLE_READ` isolation fixes a
transaction's consistent-read snapshot at its *first* plain `SELECT` — in
this case, the vehicle lookup that runs before the lock is even acquired.
So every thread's overlap check kept reading a snapshot from before any
appointment existed, even after correctly acquiring the row lock. The fix
was forcing `READ_COMMITTED` isolation on the booking transaction so every
plain read re-reads the latest committed state, which combined with `FOR
UPDATE` actually serializes the bookings. This is a well-known MySQL/InnoDB
gotcha, but it's exactly the kind of bug that looks correct in a code review
and only shows up under real concurrent load — which is why the test was
written to fire real concurrent requests through a real MySQL container
rather than asserting the locking logic in isolation.

A second, smaller bug surfaced the same way: `GET /appointments/{id}` threw
`LazyInitializationException` in its own test, because `open-in-view` is
disabled (deliberately, to avoid its N+1/performance foot-guns) and the
controller read lazy `vehicle`/`technician`/`serviceBay` associations after
the Hibernate session had already closed. Fixed with an explicit `JOIN
FETCH` query for the single-appointment read path. It worked in the `POST`
response purely by accident (those entities were already fully loaded
during the booking transaction), which is exactly the kind of thing that
would have shipped silently without a dedicated `GET` test.

Net process: propose implementation → run it for real (Testcontainers, not
mocks) → treat a passing build as a hypothesis, not a result → when a test
failed, diagnose the actual mechanism (isolation levels, session lifecycle)
rather than patching symptoms. Both fixes above are documented inline in the
code with the *why*, not just the *what*.
