#!/usr/bin/env bash
# Connect once, then upload and deploy through the same verified SSH connection.
set -Eeuo pipefail
umask 077

for key in VPS_HOST VPS_USER VPS_SSH_KEY VPS_KNOWN_HOSTS IMAGE_TAG RELEASE_ID; do
  [[ -n "${!key:-}" ]] || { echo "::error::Missing $key."; exit 1; }
done
port=${VPS_PORT:-22}
[[ "$VPS_HOST" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]] || { echo 'Invalid VPS_HOST.' >&2; exit 1; }
[[ "$VPS_USER" =~ ^[a-z_][a-z0-9_-]*$ ]] || { echo 'Invalid VPS_USER.' >&2; exit 1; }
[[ "$port" =~ ^[0-9]{1,5}$ ]] && (( 10#$port >= 1 && 10#$port <= 65535 )) || { echo 'Invalid VPS_PORT.' >&2; exit 1; }
[[ "$IMAGE_TAG" =~ ^[0-9a-f]{40}$ && "$RELEASE_ID" =~ ^[0-9a-f]{40}-[0-9]+-[0-9]+$ ]] || {
  echo 'Invalid commit or release ID.' >&2; exit 1;
}

cd "$(dirname "$0")/.."
ssh_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/cinema-ssh.XXXXXXXX")
remote="$VPS_USER@$VPS_HOST"
cleanup() {
  ssh -S "$ssh_dir/control" -p "$port" -O exit "$remote" >/dev/null 2>&1 || true
  rm -f -- "$ssh_dir/key" "$ssh_dir/known_hosts" "$ssh_dir/control"
  rmdir -- "$ssh_dir"
}
trap cleanup EXIT

# Accept Windows CRLF secrets without weakening host verification.
printf '%s\n' "$VPS_SSH_KEY" | tr -d '\r' > "$ssh_dir/key"
printf '%s\n' "$VPS_KNOWN_HOSTS" | tr -d '\r' > "$ssh_dir/known_hosts"
if ! ssh-keygen -y -P '' -f "$ssh_dir/key" >/dev/null 2>&1; then
  echo '::error::VPS_SSH_KEY must contain a complete, unencrypted OpenSSH private key.'
  exit 1
fi

ssh_opts=(-i "$ssh_dir/key" -p "$port" -o IdentitiesOnly=yes -o BatchMode=yes
  -o StrictHostKeyChecking=yes -o "UserKnownHostsFile=$ssh_dir/known_hosts"
  -o ConnectTimeout=15 -o ServerAliveInterval=15 -o ServerAliveCountMax=3
  -o ControlMaster=auto -o ControlPersist=60 -S "$ssh_dir/control")

connected=false
for attempt in 1 2 3; do
  echo "Establishing verified SSH connection ($attempt/3)..."
  if ssh "${ssh_opts[@]}" -MNf "$remote"; then
    connected=true
    break
  fi
  if (( attempt < 3 )); then sleep 5; fi
done
if [[ "$connected" != true ]]; then
  echo '::error::SSH connection failed after 3 attempts. Check VPS_HOST, VPS_PORT, authorized_keys and VPS SSH/firewall logs.'
  exit 1
fi

release="/opt/cinema/releases/$RELEASE_ID"
echo 'Uploading files and deploying through the established SSH connection...'
# No SCP/SFTP subsystem required. Do not retry this command: deployment may have
# started before a disconnect. The remote script retains its health/rollback checks.
if ! tar -cf - compose.prod.yml scripts/deploy.sh | ssh "${ssh_opts[@]}" -T "$remote" \
  "bash -c 'set -Eeuo pipefail
    test -s /opt/cinema/.env || { echo \"Missing /opt/cinema/.env\" >&2; exit 1; }
    docker compose version
    install -d -m 700 \"$release\"
    tar -xf - -C \"$release\"
    echo \"Upload complete. Starting deployment...\"
    bash \"$release/scripts/deploy.sh\" \"$IMAGE_TAG\" \"$release/compose.prod.yml\"'"; then
  echo '::error::Upload or deployment failed. Inspect the messages above and VPS logs; deployment has not been reported as successful.'
  exit 1
fi
echo 'Production deployment completed successfully.'
