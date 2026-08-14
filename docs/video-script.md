# Video Submission Script — Unified Service Scheduler (Scenario A)

Target: 8–9 minutes. Timings are a guide, not a stopwatch — talk naturally,
cut a demo beat if you're running long rather than rushing the design
walkthrough. `[ON SCREEN: ...]` marks what to have open/visible at that point.

---

## 1. Intro (0:00–0:45)

`[ON SCREEN: your face / a title slide]`

> Hi, I'm [YOUR NAME]. This is my submission for the Keyloop technical
> assessment, Scenario A — the Unified Service Scheduler, an appointment
> booking system for dealership service departments.
>
> The core problem this system exists to solve is narrow and specific: two
> customers requesting the same technician or the same service bay at the
> same time must never both get confirmed. Everything about the design is
> built around making that guarantee actually true under real concurrent
> load, not just documented as an assumption.

---

## 2. System Design Walkthrough (0:45–3:00)

`[ON SCREEN: docs/design.md, scroll to the §1 architecture diagram]`

> Here's the architecture. It's a single Spring Boot app, one MySQL
> database, and — added after the initial submission — a Redis layer and
> an observability stack. I'll cover those as additions in a minute.
>
> The request path: a client hits `POST /appointments`, it goes through a
> correlation-ID filter, into the controller, into `BookingService` — that's
> the only place business logic lives — and out to MySQL through Spring
> Data repositories.

`[ON SCREEN: §3, the "why this actually prevents double-booking" section]`

> The guarantee itself comes from pessimistic row locking. When
> `BookingService` finds a candidate technician, it takes a `SELECT ... FOR
> UPDATE` lock on that technician's row before checking for overlapping
> appointments. Two transactions racing for the same technician — the
> second one blocks on that lock until the first commits. Once unblocked, it
> re-checks overlap against whatever the first transaction just committed.
> Lock, then observe, then act — that's what turns a check-then-act race
> into something safe.
>
> That single mechanism is also horizontally-scalable for free — it's a
> database-level lock, not an in-process one, so it holds even if the app
> runs as multiple instances behind a load balancer.

`[ON SCREEN: observability-design.md architecture diagram]`

> After the initial submission, I added two more pieces, both explicitly
> additive: a Redis layer for an idempotency dedup guard and a candidate-
> list cache, and a Prometheus, Grafana, and Loki stack for metrics and
> logs. Neither one is allowed to touch the correctness guarantee — MySQL
> row locking is still the only thing that has ever prevented a double
> booking. If Redis goes down, the app just degrades to uncached reads and
> skips the dedup check; it never fails closed.

---

## 3. Implementation Highlights (3:00–4:30)

`[ON SCREEN: BookingService.java]`

> A few things worth calling out in the implementation. `bookAppointment`
> runs under `READ_COMMITTED` isolation — deliberately overriding MySQL's
> default `REPEATABLE_READ` — for a reason I'll get to in the challenges
> section, because it's the single most interesting bug this project
> surfaced.
>
> Booking supports three paths: an existing vehicle by ID, a guest
> booking where the customer and vehicle get created inline from a VIN and
> email, and an optional explicit technician choice — if you don't pick
> one, the service auto-assigns the first qualified, available technician.

`[ON SCREEN: AppointmentControllerTest.java or BookingServiceConcurrencyTest.java]`

> The test suite runs against a real MySQL container via Testcontainers,
> not mocks or H2 — every test in here, including the concurrency test that
> fires eight simultaneous booking requests at the same technician and slot
> and asserts exactly one succeeds, is proving real database locking
> behavior, not asserting the logic agrees with itself.

`[ON SCREEN: the frontend, briefly]`

> On the frontend — not the graded deliverable, just a way to exercise the
> API visually — it's Next.js with Tailwind and shadcn: a real calendar
> picker, animated selects, and a booking flow themed as a dealership work
> order.

---

## 4. AI Collaboration Story (4:30–6:00)

`[ON SCREEN: README.md, "AI Collaboration Narrative" section]`

> I used Claude Code as an active collaborator through this whole project,
> not autocomplete. I came in with the technical plan mostly formed —
> stack, locking approach, test strategy — from prior experience with this
> class of problem. Claude's job was less "propose a design" and more
> "find the holes before I write code."
>
> Two real gaps got caught before implementation started: the request body
> had no field for choosing a technician, so something had to own
> auto-assignment logic — that became the `specialty` match on `Technician`.
> And the brief's deliverables list mentioned Testcontainers while an
> earlier section described an H2-only test strategy — a direct
> contradiction, resolved in favor of real MySQL for every test.
>
> But the more important pattern was verification, not trust. The
> concurrency test caught a real bug in AI-written code that looked
> completely correct in review: eight of eight concurrent bookings
> succeeded instead of one, because of a MySQL isolation-level gotcha I'll
> walk through in a minute. That bug only exists under real concurrent
> load — no amount of reading the code would have caught it, which is
> exactly why the test fires real concurrent requests instead of asserting
> the locking logic in isolation.
>
> Later, adding the Redis and observability layer, the same pattern held:
> Claude caught that Docker's native Loki logging driver needs a
> `docker plugin install` step on the host, which would have broken my own
> "docker compose up, nothing else" requirement — reversed before I ever
> implemented it. And a missing Prometheus dependency didn't get caught by
> planning or by the test suite at all — only by actually bringing the full
> stack up and checking whether Prometheus could see the app.

---

## 5. Live Demo (6:00–8:00)

`[ON SCREEN: terminal]`

> Starting the whole stack:

```bash
docker compose up --build
```

`[ON SCREEN: browser, http://localhost:3000]`

> This is the booking flow. I'll pick a dealership and a service — the
> slot grid is live, it's calling the backend's availability endpoint
> right now for every qualified technician that day. I'll fill in a guest
> vehicle — this creates the customer and vehicle records inline, no
> signup step — pick a slot, and book.

`[Submit the booking, show the confirmation]`

> Confirmed. Now watch what happens if I submit the exact same request
> again —

`[Submit again]`

> — 409, rejected. That's the guarantee working, not a UI-level check.

`[ON SCREEN: Grafana, localhost:3001]`

> And here's the observability side — Prometheus metrics and the app's
> logs in one dashboard, provisioned automatically, no manual setup.

---

## 6. What I Learned / Challenges (8:00–9:00)

`[ON SCREEN: design.md §3, the isolation-level explanation]`

> The strongest lesson from this project was the MySQL isolation-level bug
> I mentioned earlier. `REPEATABLE_READ` — MySQL's default — fixes a
> transaction's read snapshot at its *first* plain read. In the original
> code, that was the vehicle lookup, which happens *before* the row lock is
> even acquired. So every concurrent thread would correctly wait for the
> lock, get unblocked, and then check for overlaps against a snapshot from
> before any of them had booked anything — all eight would see zero
> existing appointments and all eight would succeed. Forcing
> `READ_COMMITTED` fixed it, because it makes every plain read re-read the
> latest committed state instead of a fixed snapshot. It's a well-known
> InnoDB gotcha, but it's exactly the kind of bug that looks completely
> correct in a code review and only shows up under real concurrent load.
>
> The other challenge worth mentioning: integrating shadcn's UI components
> meant reconciling a Tailwind-utility-driven design system with a
> hand-built CSS token system I'd already committed to — shadcn's own
> init tooling actually overwrote some of my custom CSS variables on
> first run, which took some untangling. Small thing compared to the
> database bug, but a good reminder that generated tooling output still
> needs to be reviewed line by line, not trusted because it ran without
> errors.
>
> Thanks for watching.

---

## Notes for recording

- Fill in `[YOUR NAME]` before recording.
- If short on time, the safest cuts are: trim the frontend mention in §3,
  and shorten the shadcn/CSS story in §6 to one sentence — keep the MySQL
  isolation-level story, it's the strongest material in the whole project.
- Have `docker compose up --build` already pulled/built once beforehand so
  the live demo doesn't stall on a cold image pull.
