#!/usr/bin/env bash
# Run JobEmailer and ask systemd to block laptop sleep while this process runs.
# Screen off is fine; suspend/sleep will pause the JVM and stop Telegram polling.
set -euo pipefail
cd "$(dirname "$0")/.."

if command -v systemd-inhibit >/dev/null 2>&1; then
  exec systemd-inhibit \
    --what=sleep:idle \
    --who=JobEmailer \
    --why="Telegram bot must keep polling" \
    --mode=block \
    mvn -q spring-boot:run "$@"
else
  echo "systemd-inhibit not found; running without sleep block." >&2
  exec mvn -q spring-boot:run "$@"
fi
