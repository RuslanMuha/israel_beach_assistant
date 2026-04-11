#!/usr/bin/env bash
set -e

LOG=".cursor/hooks.log"
TOUCHED=".cursor/touched-files.txt"

TS="$(date '+%Y-%m-%d %H:%M:%S')"

# Cursor sends JSON on stdin. We extract file_path if jq exists.
if command -v jq >/dev/null 2>&1; then
  payload="$(cat)"
  file_path="$(printf "%s" "$payload" | jq -r '.file_path // empty')"
  if [ -n "$file_path" ]; then
    echo "$TS afterFileEdit $file_path" >> "$LOG"
    echo "$file_path" >> "$TOUCHED"
  else
    echo "$TS afterFileEdit (unknown file)" >> "$LOG"
  fi
else
  # fail-open: still log that hook ran
  cat >/dev/null
  echo "$TS afterFileEdit (jq missing)" >> "$LOG"
fi

echo '{"permission":"allow"}'
