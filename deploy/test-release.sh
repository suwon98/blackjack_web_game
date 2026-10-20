#!/usr/bin/env bash
set -euo pipefail
source_root=$(cd "$(dirname "$0")" && pwd)
test_root=$(mktemp -d)
trap 'rm -rf "$test_root"' EXIT
export BLACKJACK_ROOT="$test_root/app"
export MOCK_ROOT="$test_root"
mkdir -p "$BLACKJACK_ROOT" "$test_root/bin"
printf 'DOMAIN=game.example.com\nIMAGE_PREFIX=ghcr.io/example/blackjack\n' > "$BLACKJACK_ROOT/.env"
cat > "$test_root/bin/docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MOCK_ROOT/docker.log"
if [[ "$*" == *" pull "* && -f "$MOCK_ROOT/pull-fail" ]]; then exit 1; fi
if [[ "$*" == *" exec "* ]]; then printf 'SQL_BACKUP\n'; fi
EOF
cat > "$test_root/bin/curl" <<'EOF'
#!/usr/bin/env bash
if test -f "$MOCK_ROOT/health-fail"; then
  count=$(cat "$MOCK_ROOT/count" 2>/dev/null || echo 0)
  count=$((count + 1))
  echo "$count" > "$MOCK_ROOT/count"
  test "$count" -gt 60
fi
EOF
printf '#!/usr/bin/env bash\nexit 0\n' > "$test_root/bin/sleep"
chmod +x "$test_root/bin/"*
export PATH="$test_root/bin:$PATH"
first=$(printf 'a%.0s' {1..40})
second=$(printf 'b%.0s' {1..40})
third=$(printf 'c%.0s' {1..40})
bash "$source_root/release.sh" "$first"
grep -q "$first" "$BLACKJACK_ROOT/.release.env"
bash "$source_root/release.sh" "$second"
grep -q "$first" "$BLACKJACK_ROOT/.previous-release.env"
grep -q SQL_BACKUP "$BLACKJACK_ROOT"/backups/*.sql
touch "$test_root/health-fail"
if bash "$source_root/release.sh" "$third"; then echo "Expected release failure"; exit 1; fi
grep -q "$second" "$BLACKJACK_ROOT/.release.env"
test "$(cat "$test_root/count")" -eq 61
rm "$test_root/health-fail"
touch "$test_root/pull-fail"
if bash "$source_root/release.sh" "$third"; then echo "Expected pull failure"; exit 1; fi
grep -q "$second" "$BLACKJACK_ROOT/.release.env"
if bash "$source_root/release.sh" "invalid;command"; then echo "Expected validation failure"; exit 1; fi
echo "PASS: initial release, backup, health failure rollback, pull failure and tag validation"
