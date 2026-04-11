#!/usr/bin/env bash
set -e

LOG=".cursor/hooks.log"
TOUCHED=".cursor/touched-files.txt"
SUMMARY=".cursor/session-summary.txt"

TS="$(date '+%Y-%m-%d %H:%M:%S')"

dedup_touched() {
  if [ -f "$TOUCHED" ]; then
    sort -u "$TOUCHED"
  fi
}

# Heuristics: infer what was likely changed
has_match() {
  local pattern="$1"
  if [ ! -f "$TOUCHED" ]; then return 1; fi
  grep -Eiq "$pattern" "$TOUCHED"
}

changed_build=false
changed_config=false
changed_java=false
changed_tests=false
changed_logging=false
changed_db=false
changed_migrations=false
changed_docker=false

has_match '(^|/)(pom\.xml|build\.gradle|settings\.gradle|gradle\.properties)$' && changed_build=true
has_match '(^|/)(application.*\.(yml|yaml|properties)|.*\.properties|.*\.yml|.*\.yaml)$' && changed_config=true
has_match '\.java$' && changed_java=true
has_match '(^|/)(src/test/|.*Test\.java$|.*IT\.java$)' && changed_tests=true
has_match '(logback\.xml|logging.*\.(yml|yaml|properties)|/logging/|logger|slf4j)' && changed_logging=true
has_match '(/db/|/migration/|\.sql$)' && changed_db=true
has_match '(liquibase|changelog|db\.changelog|/db/changelog/)' && changed_migrations=true
has_match '(Dockerfile|docker-compose\.yml|docker-compose\.yaml|/k8s/|/helm/)' && changed_docker=true

{
  echo "=== Cursor session stop @ $TS ==="
  echo
  echo "Touched files (dedup):"
  if [ -f "$TOUCHED" ]; then
    dedup_touched
  else
    echo "- (none recorded)"
  fi

  echo
  echo "Recommended verification checklist:"
  echo "- Run full test suite before continuing/merging."

  # Recommend commands without executing them
  if $changed_build || $changed_java || $changed_tests || $changed_config; then
    echo
    echo "Suggested commands:"
    echo "- mvn -q test"
    echo "- mvn -q -DskipTests=false test    # if you use profiles, add -P<profile>"
  fi

  if $changed_migrations; then
    echo
    echo "Liquibase (migrations touched):"
    echo "- Verify changesets apply cleanly on a fresh DB and upgrade from previous version (CI ideally)."
    echo "- Ensure expand->migrate->contract approach for breaking schema changes."
  fi

  if $changed_logging; then
    echo
    echo "Logging/Observability:"
    echo "- Ensure no duplicated logs across layers and no hot-path WARN/ERROR flood."
    echo "- Ensure parameterized logging only (no '+') and no PII/secrets."
  fi

  if $changed_db; then
    echo
    echo "Data/JPA:"
    echo "- Check for N+1/unbounded reads/pagination safety; ensure tx boundaries are service-level."
  fi

  if $changed_docker; then
    echo
    echo "Container/Runtime:"
    echo "- Validate image/compose changes locally and ensure runtime config is externalized."
  fi

  echo
  echo "Tuning knobs reminder:"
  echo "- If timeouts/retries/pools/log sampling/TTL changed: tune based on p95/p99, error rate, retry rate, pool saturation, log volume."
  echo
} > "$SUMMARY"

echo "$TS stop (wrote $SUMMARY)" >> "$LOG"
echo '{"permission":"allow"}'
