# k6 Performance Testing

Metrics are pushed to the **Prometheus** instance that ships with `make docker-up`
and are visualised in the **Grafana** dashboard that is automatically provisioned.

```
k6 (host machine)
  │  --out experimental-prometheus-rw
  ▼
Prometheus  http://localhost:9091  (remote-write receiver enabled)
  │
  ▼
Grafana     http://localhost:3000  →  "k6 Load Testing — CQRS Order Service" dashboard
```

---

## Prerequisites

### 1. Install k6

```bash
# macOS
brew install k6

# Linux (Debian/Ubuntu)
sudo gpg --no-default-keyring \
  --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 \
  --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] \
  https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Windows
winget install k6
```

### 2. Start the full stack

```bash
make docker-up
```

This starts the app, Postgres, Nginx, **Prometheus** (with remote-write enabled),
and **Grafana** (with the k6 dashboard pre-provisioned).

---

## Running Tests

### Load test — enforces SLOs

```bash
make load-test
```

**Thresholds (test fails CI if breached):**

| Metric | SLO |
|--------|-----|
| `http_req_duration p(95)` | < 300 ms |
| `http_req_failed` rate | < 1 % |
| `login` p(99) | < 500 ms |
| `createOrder` p(95) | < 400 ms |
| `order_lifecycle_success` rate | > 95 % |

VU profile: `0 → 50 (2 m) → 50 (5 m) → 100 (2 m) → 100 (5 m) → 0 (2 m)`

### Stress test — find the breaking point

```bash
make stress-test
```

Ramps to 1000 VUs. Thresholds are **soft** (never abort the test) so you can
observe the full degradation curve in Grafana.

VU profile: `0 → 100 → 300 → 600 → 1000 → 0`

---

## Viewing Results in Grafana

1. Open `http://localhost:3000` (admin / admin)
2. Go to **Dashboards → k6 Load Testing — CQRS Order Service**
3. Start a test in another terminal — panels update every 5 s

### Dashboard panels

| Panel | What to watch |
|-------|---------------|
| Active VUs | Confirm the ramp shape matches the stage config |
| Request Rate | req/s — correlate spikes with latency |
| Error Rate | Stays green (< 1 %) during load test; rises during stress |
| p95 Latency (all) | SLO line at 300 ms — turns red if breached |
| p95 by Endpoint | Which endpoint is slowest |
| Response Time Percentiles | Full p50/p90/p95/p99 over time |
| Order Create Latency | Business-critical write path |
| Order Confirm Latency | State-transition latency |
| Order Lifecycle Success | End-to-end scenario health |
| Data Throughput | Network bytes in/out |
| Iterations/s | Completed scenarios per second |

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BASE_URL` | `http://localhost:8080` | Target service URL |
| `K6_USERNAME` | `admin` | Login credentials |
| `K6_PASSWORD` | `changeme` | Login credentials |
| `K6_PROMETHEUS_RW_SERVER_URL` | `http://localhost:9091/api/v1/write` | Prometheus remote-write endpoint |

Override at runtime:

```bash
BASE_URL=https://staging.example.com \
K6_USERNAME=admin \
K6_PASSWORD=secret \
K6_PROMETHEUS_RW_SERVER_URL=http://staging-prometheus:9090/api/v1/write \
make load-test
```

---

## Running Without Prometheus (CLI only)

```bash
# Load test — summary printed to terminal, no Grafana
k6 run k6/load-test.js

# Stress test
k6 run k6/stress-test.js

# Quick smoke check (5 VUs × 30 s)
k6 run --vus 5 --duration 30s k6/load-test.js

# Save summary to JSON for offline analysis
k6 run --summary-export=results/summary.json k6/load-test.js
```

---

## How Prometheus Remote-Write Works

k6 uses the `experimental-prometheus-rw` output to **push** metrics to Prometheus
instead of Prometheus scraping k6.  The flags set in the Makefile control the format:

| Env var | Value | Effect |
|---------|-------|--------|
| `K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM` | `false` | Export Trend metrics as named gauges (simpler Grafana queries) |
| `K6_PROMETHEUS_RW_TREND_STATS` | `p(50),p(90),p(95),p(99)` | Which percentiles to emit |

The resulting metric names in Prometheus:

```
k6_http_req_duration_p50        # gauge, ms
k6_http_req_duration_p95        # gauge, ms  ← used for SLO threshold
k6_http_req_failed_rate         # gauge, 0–1
k6_http_reqs_total              # counter
k6_vus                          # gauge
k6_iterations_total             # counter
k6_data_sent_total              # counter, bytes
k6_data_received_total          # counter, bytes
k6_order_lifecycle_success_rate # gauge, custom Rate metric
k6_order_create_latency_ms_p95  # gauge, custom Trend metric
k6_order_confirm_latency_ms_p95 # gauge, custom Trend metric
```

---

## Project Structure

```
k6/
├── config/config.js          base URL, credentials, VU stages, thresholds, data pools
├── scenarios/
│   ├── order-flow.js         list → create → get → confirm/cancel (write-heavy)
│   └── read-heavy.js         list → get (read-only, 20 % of load-test VUs)
├── services/
│   └── order-service.js      HTTP adapter for all 5 order endpoints
├── utils/
│   ├── auth.js               loginOnce() for setup(), loginPerVU() for stress test
│   ├── checks.js             reusable assertions + custom Rate/Trend metrics
│   └── data.js               random payload builders (orders, cancel reasons)
├── load-test.js              SLO-enforced load test (two named scenarios)
└── stress-test.js            breaking-point stress test (per-VU login)
```
