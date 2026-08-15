# cURL Examples — All Endpoints

Matches [`docs/openapi.yaml`](openapi.yaml). Each block imports into Postman
individually: **Import → Raw text → paste one block → Import**. Stack must
be up (`docker compose up --build`) for these to actually hit something.

## Scheduler API (localhost:8080)

### Book an appointment — existing vehicle, auto-assigned technician

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
```

### Book an appointment — guest vehicle, explicit technician

```bash
curl -X POST http://localhost:8080/appointments \
  -H "Content-Type: application/json" \
  -d '{
    "serviceType": "OIL_CHANGE",
    "dealershipId": 1,
    "technicianId": 1,
    "desiredStart": "2026-09-01T11:00:00",
    "desiredEnd": "2026-09-01T12:00:00",
    "customerName": "Alex Guest",
    "customerEmail": "alex@example.com",
    "customerPhone": "555-0100",
    "vehicleVin": "1HGCM82633A004999",
    "vehicleMake": "Honda",
    "vehicleModel": "Civic"
  }'
```

### Get an appointment by id

```bash
curl http://localhost:8080/appointments/1
```

### Get one technician's availability for a day

```bash
curl "http://localhost:8080/technicians/1/availability?date=2026-09-01"
```

### Get every qualified technician's availability for a service/day

```bash
curl "http://localhost:8080/technicians/availability?dealershipId=1&serviceType=OIL_CHANGE&date=2026-09-01"
```

## App Metrics (localhost:8080)

### Prometheus-format metrics scrape

```bash
curl http://localhost:8080/actuator/prometheus
```

## Loki — log query API (localhost:3100)

### Query logs over a time range

```bash
curl -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={compose_service="app"}' \
  --data-urlencode "start=$(date -u -v-1H +%s)000000000" \
  --data-urlencode "end=$(date -u +%s)000000000" \
  --data-urlencode 'limit=50'
```

### Instant log query

```bash
curl -G http://localhost:3100/loki/api/v1/query \
  --data-urlencode 'query={compose_service="app"}'
```

### List available label names

```bash
curl http://localhost:3100/loki/api/v1/labels
```

### List values for a label

```bash
curl http://localhost:3100/loki/api/v1/label/compose_service/values
```

## Prometheus — metrics query API (localhost:9090)

### Instant metrics query

```bash
curl -G http://localhost:9090/api/v1/query \
  --data-urlencode 'query=rate(booking_attempts_total[5m])'
```

### Metrics query over a time range

```bash
curl -G http://localhost:9090/api/v1/query_range \
  --data-urlencode 'query=rate(booking_conflicts_total[5m])' \
  --data-urlencode "start=$(date -u -v-1H +%s)" \
  --data-urlencode "end=$(date -u +%s)" \
  --data-urlencode 'step=15s'
```

### Scrape target health

```bash
curl http://localhost:9090/api/v1/targets
```
