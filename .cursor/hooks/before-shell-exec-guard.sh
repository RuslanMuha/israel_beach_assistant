#!/usr/bin/env bash
set -e

# If jq is missing, fail-open (do not block).
if ! command -v jq >/dev/null 2>&1; then
  cat >/dev/null
  echo '{"permission":"allow"}'
  exit 0
fi

payload="$(cat)"
cmd="$(printf "%s" "$payload" | jq -r '.command // ""')"
low="$(printf "%s" "$cmd" | tr '[:upper:]' '[:lower:]')"

# Minimal denylist for destructive or supply-chain risky patterns
deny_regex='(\brm\s+-rf\b|mkfs\.|dd\s+if=|>\s*/dev/sd|curl\b.*\|\s*bash|wget\b.*\|\s*bash|\bshutdown\b|\breboot\b)'

if printf "%s" "$low" | grep -Eq "$deny_regex"; then
  echo '{"permission":"deny","reason":"Blocked dangerous shell command (destructive or supply-chain risk)."}'
  exit 0
fi

echo '{"permission":"allow"}'
