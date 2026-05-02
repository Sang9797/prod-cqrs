#!/bin/sh
# Download community dashboards into Grafana's writable data dir before startup.
# A second provisioning provider (dashboards-downloaded) points here.

DASH_DIR=/var/lib/grafana/dashboards
mkdir -p "$DASH_DIR"

download_dashboard() {
  id=$1
  out="$DASH_DIR/${id}.json"
  if [ ! -f "$out" ]; then
    echo "[entrypoint] downloading Grafana dashboard $id"
    wget -q -O "$out" "https://grafana.com/api/dashboards/${id}/revisions/latest/download" || \
      echo "[entrypoint] WARNING: could not download dashboard $id (offline?)"
  fi
}

download_dashboard 19004   # Spring Boot 3.x / Micrometer (correct for SB3 + G1GC)

exec /run.sh
