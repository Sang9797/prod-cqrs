#!/bin/sh
# Download community dashboards before Grafana starts.
# Grafana file provisioning picks them up automatically on boot.

DASH_DIR=/etc/grafana/provisioning/dashboards

download_dashboard() {
  id=$1
  out="$DASH_DIR/${id}.json"
  if [ ! -f "$out" ]; then
    echo "[entrypoint] downloading Grafana dashboard $id"
    wget -q -O "$out" "https://grafana.com/api/dashboards/${id}/revisions/latest/download" || \
      echo "[entrypoint] WARNING: could not download dashboard $id (offline?)"
  fi
}

download_dashboard 19004   # Spring Boot 3.x / Micrometer
download_dashboard 4701    # JVM Micrometer

exec /run.sh
