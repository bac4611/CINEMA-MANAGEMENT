#!/usr/bin/env bash
# Mock transport only: never connect to a VPS or run a real deployment.
set -Eeuo pipefail
source_root=$(cd "$(dirname "$0")/../.." && pwd)
test_root=$(mktemp -d)
trap 'rm -rf -- "$test_root"' EXIT
mkdir -p "$test_root/bin"
export MOCK_STATE="$test_root" RUNNER_TEMP="$test_root"
export VPS_HOST=example.invalid VPS_USER=deploy VPS_PORT=22
export VPS_SSH_KEY=test-only VPS_KNOWN_HOSTS=test-only
export IMAGE_TAG=1111111111111111111111111111111111111111
export RELEASE_ID="$IMAGE_TAG-1-1"
export PATH="$test_root/bin:$PATH"
cat > "$test_root/bin/ssh-keygen" <<'MOCK'
#!/usr/bin/env bash
exit "${MOCK_KEY_FAILURE:-0}"
MOCK
cat > "$test_root/bin/sleep" <<'MOCK'
#!/usr/bin/env bash
exit 0
MOCK
cat > "$test_root/bin/ssh" <<'MOCK'
#!/usr/bin/env bash
set -eu
if [[ " $* " == *' -O exit '* ]]; then exit 0; fi
if [[ " $* " == *' -MNf '* ]]; then
  count=0
  [[ ! -f "$MOCK_STATE/attempts" ]] || count=$(cat "$MOCK_STATE/attempts")
  count=$((count + 1))
  echo "$count" > "$MOCK_STATE/attempts"
  (( count > ${MOCK_CONNECTION_FAILURES:-0} ))
  exit $?
fi
echo deploy >> "$MOCK_STATE/deployments"
tar -tf - > "$MOCK_STATE/uploaded-files"
exit "${MOCK_DEPLOY_FAILURE:-0}"
MOCK
chmod +x "$test_root/bin/ssh" "$test_root/bin/ssh-keygen" "$test_root/bin/sleep"
run() { bash "$source_root/scripts/deploy-ssh.sh"; }
expect_failure() { if run; then echo 'Expected failure.' >&2; exit 1; fi; }
reset_state() { rm -f "$test_root/attempts" "$test_root/deployments" "$test_root/uploaded-files"; }

# Transient disconnects are retried before deployment, and files stream once.
export MOCK_CONNECTION_FAILURES=2
run
[[ $(cat "$test_root/attempts") == 3 && $(wc -l < "$test_root/deployments") == 1 ]]
grep -qx compose.prod.yml "$test_root/uploaded-files"
grep -qx scripts/deploy.sh "$test_root/uploaded-files"
reset_state

# A permanent connection failure must fail CI without deploying.
export MOCK_CONNECTION_FAILURES=3
expect_failure
[[ $(cat "$test_root/attempts") == 3 && ! -f "$test_root/deployments" ]]
reset_state

# Never repeat a remote deployment or hide a deployment failure.
export MOCK_CONNECTION_FAILURES=0 MOCK_DEPLOY_FAILURE=1
expect_failure
[[ $(cat "$test_root/attempts") == 1 && $(wc -l < "$test_root/deployments") == 1 ]]
reset_state
unset MOCK_DEPLOY_FAILURE

# Reject bad keys before making any connection.
export MOCK_KEY_FAILURE=1
expect_failure
[[ ! -f "$test_root/attempts" ]]
echo 'SSH transport checks passed.'
