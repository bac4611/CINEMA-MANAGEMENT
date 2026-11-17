#!/usr/bin/env bash
# Run on the VPS. This script never creates or changes database schemas/volumes.
set -Eeuo pipefail
umask 077
cd "${CINEMA_DEPLOY_DIR:-/opt/cinema}"

tag=${1:?Usage: bash scripts/deploy.sh COMMIT_SHA [COMPOSE_FILE]}
candidate=${2:-compose.prod.yml}
[[ "$tag" =~ ^[0-9a-f]{40}$ ]] || { echo 'Expected a full lowercase Git commit SHA.' >&2; exit 1; }
[[ -s .env && -f "$candidate" ]] || { echo 'Configure .env and the Compose file first.' >&2; exit 1; }

# Also serialize manual invocations against GitHub Actions deployments.
exec 9>.deploy.lock
flock -w 300 9 || { echo 'Another deployment holds the lock.' >&2; exit 1; }
docker compose version

# Snapshot the candidate: rollback may use .previous-compose.yml as its input.
work=$(mktemp -d .deploy.XXXXXXXX)
trap 'rm -f "$work/candidate.yml"; rmdir "$work"' EXIT
cp -- "$candidate" "$work/candidate.yml"
candidate="$PWD/$work/candidate.yml"
previous_tag=''
if [[ -s .deployed-tag ]]; then
  previous_tag=$(cat .deployed-tag)
  [[ "$previous_tag" =~ ^[0-9a-f]{40}$ && -s .deployed-compose.yml ]] || {
    echo 'Invalid deployment state; inspect .deployed-tag and .deployed-compose.yml.' >&2; exit 1;
  }
fi

compose() {
  local image_tag=$1 compose_file=$2
  shift 2
  CINEMA_IMAGE_TAG="$image_tag" docker compose --project-name cinema \
    --project-directory "$PWD" --env-file "$PWD/.env" -f "$compose_file" "$@"
}

# Quiet validation: the rendered configuration contains credentials.
compose "$tag" "$candidate" config --quiet
compose "$tag" "$candidate" pull cinema
echo "Starting bacdepzaiz7/cinema:$tag"
healthy() {
  local image_tag=$1 compose_file=$2
  compose "$image_tag" "$compose_file" up -d --no-deps --wait --wait-timeout 180 cinema &&
    compose "$image_tag" "$compose_file" exec -T cinema \
      wget -q -O /dev/null http://127.0.0.1:8081/actuator/health/readiness
}

if ! healthy "$tag" "$candidate"; then
  echo 'Deployment failed its health check.' >&2
  compose "$tag" "$candidate" ps || true
  # Inspect application logs directly on the VPS: they may contain credentials.
  if [[ -n "$previous_tag" ]]; then
    echo "Restoring previous healthy release: $previous_tag" >&2
    if ! healthy "$previous_tag" "$PWD/.deployed-compose.yml"; then
      echo 'Rollback also failed. Inspect the application and database connectivity immediately.' >&2
    fi
  else
    echo 'No previous managed release exists; inspect the failed container logs.' >&2
  fi
  exit 1
fi

# Record only healthy releases. Keep a Compose snapshot as well as the old tag.
if [[ -n "$previous_tag" && "$previous_tag" != "$tag" ]]; then
  cp .deployed-compose.yml .previous-compose.yml
  printf '%s\n' "$previous_tag" > .previous-tag
fi
cp "$candidate" .deployed-compose.yml
cp "$candidate" compose.prod.yml
printf '%s\n' "$tag" > .deployed-tag
compose "$tag" "$candidate" ps
echo "Deployment healthy: bacdepzaiz7/cinema:$tag"
