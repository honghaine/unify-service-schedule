# Scheduler Demo UI

Minimal Next.js frontend for the [Unified Service Scheduler API](../README.md)
(Keyloop assessment, Scenario A backend). Not part of the graded backend
deliverable — a demo client for exercising the booking flow visually instead
of via cURL.

Two forms, both calling the backend directly from the browser:

- **Book an appointment** — `POST /appointments`. Shows the confirmed
  technician/bay on success, or the error body (400/404/409) on failure.
- **Look up an appointment** — `GET /appointments/{id}`.

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

Book vehicle `1`, `OIL_CHANGE`, Downtown Keyloop Motors, any date/time —
then submit the exact same form again. First submission confirms; second
returns a `409` (dealership 1 has exactly one `OIL_CHANGE` technician in the
seed data).
