#!/usr/bin/env bash
set -euo pipefail

# ── Generate Postman collection (no running app needed) ───────────────────────
#
# Usage:  ./scripts/gen-postman.sh
#         make postman
#         BASE_URL=https://staging.example.com make postman
#
# Reads source files directly — new @RestController endpoints and GraphQL
# operations are picked up automatically without touching this script.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v python3 &>/dev/null; then
  echo "Error: python3 is required but was not found." >&2
  exit 1
fi

exec python3 "$SCRIPT_DIR/gen-postman.py"
