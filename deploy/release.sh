#!/usr/bin/env bash
set -euo pipefail
cd "${BLACKJACK_ROOT:-/opt/blackjack}"
exec 9>.deploy.lock
flock -n 9 || { echo "Another deployment is running"; exit 1; }
tag="${1:-}"
[[ "$tag" =~ ^[0-9a-f]{40}$ ]] || { echo "Expected a full commit SHA"; exit 1; }
test -f .env
domain=$(sed -n 's/^DOMAIN=//p' .env | tr -d '\r')
[[ "$domain" =~ ^[a-z0-9][a-z0-9.-]+[a-z0-9]$ ]] || { echo "Invalid DOMAIN"; exit 1; }
previous=""
if test -f .release.env; then
  previous=$(sed -n 's/^RELEASE_TAG=//p' .release.env)
  [[ "$previous" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid previous release"; exit 1; }
fi
umask 077
candidate=$(mktemp .candidate.XXXXXX)
trap 'rm -f "$candidate"' EXIT
printf 'RELEASE_TAG=%s\n' "$tag" > "$candidate"
compose() { docker compose --env-file .env --env-file "$candidate" -f compose.release.yml "$@"; }
compose config --quiet
compose pull backend web db
mkdir -p backups
chmod 700 backups
if test -n "$previous"; then
  backup="backups/pre-release-$(date +%Y%m%d-%H%M%S).sql"
  compose exec -T db sh -c 'MYSQL_PWD="$(cat /run/secrets/db-root-password)" mariadb-dump -uroot --single-transaction --routines --triggers blackjack' > "$backup"
  test -s "$backup"
fi
healthy() {
  for attempt in $(seq 1 60); do
    if curl --fail --silent --show-error --max-time 5 \
      --resolve "$domain:443:127.0.0.1" "https://$domain/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 3
  done
  return 1
}
if compose up -d --remove-orphans && healthy; then
  if test -n "$previous"; then printf 'RELEASE_TAG=%s\n' "$previous" > .previous-release.env; fi
  mv "$candidate" .release.env
  echo "Release healthy: $tag"
else
  echo "Release failed. Application rollback will be attempted if a previous release exists."
  if test -n "$previous"; then
    printf 'RELEASE_TAG=%s\n' "$previous" > "$candidate"
    compose up -d --remove-orphans
    healthy || { echo "Rollback health check failed; operator action required"; exit 1; }
    echo "Previous application release restored: $previous"
  fi
  exit 1
fi
