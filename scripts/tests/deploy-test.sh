#!/usr/bin/env bash
# Isolated deployment control-flow checks: no daemon, SSH or real database used.
set -Eeuo pipefail
source_root=$(cd "$(dirname "$0")/../.." && pwd)
test_root=$(mktemp -d)
trap 'rm -rf -- "$test_root"' EXIT
mkdir -p "$test_root/bin" "$test_root/app"
export CINEMA_DEPLOY_DIR="$test_root/app"
export MOCK_LOG="$test_root/docker.log"
export PATH="$test_root/bin:$PATH"
cat > "$test_root/bin/docker" <<'MOCK'
#!/usr/bin/env bash
set -eu
printf '%s %s\n' "${CINEMA_IMAGE_TAG:-none}" "$*" >> "$MOCK_LOG"
if [[ " $* " == *' pull '* && "${CINEMA_IMAGE_TAG:-}" == "${MOCK_PULL_FAIL_TAG:-unset}" ]]; then exit 1; fi
if [[ " $* " == *' up '* && "${CINEMA_IMAGE_TAG:-}" == "${MOCK_FAIL_TAG:-unset}" ]]; then exit 1; fi
if [[ " $* " == *' exec '* && "${CINEMA_IMAGE_TAG:-}" == "${MOCK_HTTP_FAIL_TAG:-unset}" ]]; then exit 1; fi
MOCK
chmod +x "$test_root/bin/docker"
cd "$CINEMA_DEPLOY_DIR"
printf 'test-only-environment\n' > .env
printf 'first-compose-snapshot\n' > compose.prod.yml
first=1111111111111111111111111111111111111111
second=2222222222222222222222222222222222222222
failed=3333333333333333333333333333333333333333
deploy() { bash "$source_root/scripts/deploy.sh" "$@"; }
expect_failure() {
  if "$@"; then echo 'Expected deployment to fail.' >&2; exit 1; fi
}

expect_failure deploy latest
[[ ! -e "$MOCK_LOG" ]]
deploy "$first"
[[ $(cat .deployed-tag) == "$first" && ! -e .previous-tag ]]
printf 'second-compose-snapshot\n' > candidate.yml
deploy "$second" candidate.yml
[[ $(cat .deployed-tag) == "$second" && $(cat .previous-tag) == "$first" ]]
[[ $(cat .previous-compose.yml) == first-compose-snapshot ]]

# An unhealthy release restores the last healthy config and still fails CI.
export MOCK_FAIL_TAG="$failed"
expect_failure deploy "$failed" candidate.yml
[[ $(cat .deployed-tag) == "$second" ]]
grep -q "^$second .*\.deployed-compose.yml up -d --no-deps --wait --wait-timeout 180 cinema$" "$MOCK_LOG"
unset MOCK_FAIL_TAG

# HTTP readiness failure must roll back even after Compose up succeeds.
: > "$MOCK_LOG"
export MOCK_HTTP_FAIL_TAG="$failed"
expect_failure deploy "$failed" candidate.yml
[[ $(cat .deployed-tag) == "$second" ]]
[[ $(cat compose.prod.yml) == second-compose-snapshot ]]
grep -q "^$second .*\.deployed-compose.yml up -d --no-deps --wait --wait-timeout 180 cinema$" "$MOCK_LOG"
grep -q "^$second .*\.deployed-compose.yml exec -T cinema wget .*health/readiness$" "$MOCK_LOG"
unset MOCK_HTTP_FAIL_TAG

# First deployment failure has no rollback baseline and records no release.
mkdir first-failure
cp .env candidate.yml first-failure/
export MOCK_FAIL_TAG="$failed"
expect_failure env CINEMA_DEPLOY_DIR="$CINEMA_DEPLOY_DIR/first-failure" \
  bash "$source_root/scripts/deploy.sh" "$failed" candidate.yml
[[ ! -e first-failure/.deployed-tag ]]
unset MOCK_FAIL_TAG

# Rollback failure still fails CI and preserves the last healthy state files.
export MOCK_HTTP_FAIL_TAG="$failed" MOCK_FAIL_TAG="$second"
expect_failure deploy "$failed" candidate.yml
[[ $(cat .deployed-tag) == "$second" ]]
unset MOCK_HTTP_FAIL_TAG MOCK_FAIL_TAG

# Pull errors must never replace the healthy application.
export MOCK_PULL_FAIL_TAG="$failed"
expect_failure deploy "$failed" candidate.yml
[[ $(cat .deployed-tag) == "$second" ]]
unset MOCK_PULL_FAIL_TAG

# Rolling back from .previous-compose.yml must not overwrite its input too soon.
deploy "$(cat .previous-tag)" .previous-compose.yml
[[ $(cat .deployed-tag) == "$first" && $(cat .previous-tag) == "$second" ]]
[[ $(cat compose.prod.yml) == first-compose-snapshot ]]
[[ $(cat .previous-compose.yml) == second-compose-snapshot ]]
if grep -Eq 'down|prune|volume rm' "$MOCK_LOG"; then
  echo 'Unexpected destructive Docker operation.' >&2; exit 1
fi
echo 'Deployment control-flow checks passed.'
