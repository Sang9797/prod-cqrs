.PHONY: run test build docker-up docker-down pentest load-test stress-test postman clean help

help:
	@echo ""
	@echo "CQRS Order Service — Available commands"
	@echo "────────────────────────────────────────────────────"
	@echo "  make run            Start with local profile (needs PostgreSQL)"
	@echo "  make test           Run all tests"
	@echo "  make build          Build production JAR"
	@echo "  make docker-up      Start full stack (app + postgres + nginx + monitoring)"
	@echo "  make docker-down    Stop all containers"
	@echo "  make pentest        Run security penetration tests"
	@echo "  make load-test      Run k6 load test  (SLO enforcement)"
	@echo "  make stress-test    Run k6 stress test (find breaking point)"
	@echo "  make postman        Generate Postman collection from OpenAPI spec"
	@echo "  make clean          Remove build artifacts"
	@echo "────────────────────────────────────────────────────"
	@echo ""
	@echo "  Override env vars:"
	@echo "    BASE_URL=https://staging.example.com make load-test"
	@echo "    K6_USERNAME=admin K6_PASSWORD=changeme make load-test"
	@echo "────────────────────────────────────────────────────"

run:
	mvn spring-boot:run -Dspring-boot.run.profiles=local

test:
	mvn test -Dspring.profiles.active=test

build:
	mvn clean package -DskipTests

docker-up:
	cp -n .env.example .env 2>/dev/null || true
	docker compose up --build -d
	@echo ""
	@echo "Services started:"
	@echo "  App:        http://localhost:8080"
	@echo "  Swagger:    http://localhost:8080/swagger-ui.html"
	@echo "  H2 Console: http://localhost:8080/h2-console"
	@echo "  Prometheus: http://localhost:9091"
	@echo "  Grafana:    http://localhost:3000"
	@echo "  PgAdmin:    postgres://localhost:5432/orders_db"

docker-down:
	docker compose down

pentest:
	@chmod +x scripts/pentest.sh
	@BASE_URL=http://localhost:8080 ./scripts/pentest.sh

# ── k6 performance tests ───────────────────────────────────────────────────────
# Requires k6: https://k6.io/docs/get-started/installation/
#
# Metrics are pushed to Prometheus via remote-write (port 9091) and
# visualised in Grafana at http://localhost:3000 (admin/admin).
# Run `make docker-up` first so Prometheus and Grafana are available.

K6_PROMETHEUS_RW_SERVER_URL ?= http://localhost:9091/api/v1/write

load-test:
	@command -v k6 >/dev/null 2>&1 || { echo "k6 not found — install from https://k6.io/docs/get-started/installation/"; exit 1; }
	BASE_URL=$${BASE_URL:-http://localhost:8080} \
	K6_USERNAME=$${K6_USERNAME:-admin} \
	K6_PASSWORD=$${K6_PASSWORD:-changeme} \
	K6_PROMETHEUS_RW_SERVER_URL=$(K6_PROMETHEUS_RW_SERVER_URL) \
	K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=false \
	K6_PROMETHEUS_RW_TREND_STATS=p(50),p(90),p(95),p(99) \
	k6 run --out experimental-prometheus-rw k6/load-test.js

stress-test:
	@command -v k6 >/dev/null 2>&1 || { echo "k6 not found — install from https://k6.io/docs/get-started/installation/"; exit 1; }
	BASE_URL=$${BASE_URL:-http://localhost:8080} \
	K6_USERNAME=$${K6_USERNAME:-admin} \
	K6_PASSWORD=$${K6_PASSWORD:-changeme} \
	K6_PROMETHEUS_RW_SERVER_URL=$(K6_PROMETHEUS_RW_SERVER_URL) \
	K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM=false \
	K6_PROMETHEUS_RW_TREND_STATS=p(50),p(90),p(95),p(99) \
	k6 run --out experimental-prometheus-rw k6/stress-test.js

postman:
	@chmod +x scripts/gen-postman.sh
	@./scripts/gen-postman.sh

clean:
	mvn clean
	docker compose down -v 2>/dev/null || true
