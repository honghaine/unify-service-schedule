# Scheduler Demo UI

Next.js frontend for the [Unified Service Scheduler API](../README.md)
(Keyloop assessment, Scenario A backend). Not the graded deliverable — a
demo client for exercising the booking flow visually instead of via cURL.

Two routes:

- **`/`** — book an appointment: dealership/service → guest vehicle
  (make/model/VIN) → date + calendar slot picker → technician (optional,
  "no preference" auto-assigns) → contact info. `POST /appointments`.
- **`/lookup`** — look up a ticket by appointment number. `GET /appointments/{id}`.

Styled with Tailwind v4 + shadcn/ui (Select, Calendar, Popover, Sonner
toast) on a custom "workshop service-ticket" theme (see
[design.md](../docs/design.md) §8).

## Run

Backend must be running first (`docker compose up --build` from the repo
root — see the main README).

```bash
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

By default the UI calls `http://localhost:8080`. Override with a
`.env.local` (copy `.env.local.example`) if the backend is elsewhere:

```
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

The backend's `WebConfig` allows CORS from `http://localhost:3000`
specifically for this dev UI (`src/main/java/com/keyloop/scheduler/config/WebConfig.java`
in the backend).

## Try the conflict path

Book the same dealership/service/slot twice (any guest vehicle details —
they're find-or-created by VIN/email, so reusing them across two bookings
is fine). First submission confirms; second returns a `409`.
