#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ACL_SCRIPT="${INTERNAL_DIR}/lobehub-redis-acl.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/lobehub-redis-acl-test.XXXXXX")"
trap 'rm -rf "${FIXTURE_ROOT}"' EXIT

# shellcheck source=/dev/null
source "${ACL_SCRIPT}"
ACL_COMMANDS=" ${LOBEHUB_REDIS_ACL_COMMANDS[*]} "
[[ "${ACL_COMMANDS}" != *' +@all '* ]]
for forbidden in '+acl' '+config' '+module' '+flushall' '+flushdb' '+shutdown' '+debug' '+keys'; do
  [[ "${ACL_COMMANDS}" != *" ${forbidden} "* ]]
done
for required in '+ping' '+get' '+set' '+del' '+eval' '+expire' '+hset' '+lpush' \
  '+multi' '+xadd' '+zadd' '+scan' '+publish'; do
  [[ "${ACL_COMMANDS}" == *" ${required} "* ]] || {
    echo "Explicit LobeHub Redis ACL is missing ${required}" >&2
    exit 1
  }
done

ENV_FILE="${FIXTURE_ROOT}/lobehub.env"
FAKE_BIN="${FIXTURE_ROOT}/bin"
REDIS_ARGS="${FIXTURE_ROOT}/redis.args"
REDIS_STDIN="${FIXTURE_ROOT}/redis.stdin"
mkdir "${FAKE_BIN}"
cat >"${ENV_FILE}" <<'EOF'
LOBEHUB_REDIS_HOST=redis.internal
LOBEHUB_REDIS_PORT=6379
LOBEHUB_REDIS_USERNAME=lobehub
LOBEHUB_REDIS_PASSWORD=application-password-not-in-argv
REDIS_PREFIX=lobehub:app
EOF
chmod 0600 "${ENV_FILE}"
cat >"${FAKE_BIN}/redis-cli" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" >"${LOBEHUB_ACL_TEST_ARGS:?}"
cat >"${LOBEHUB_ACL_TEST_STDIN:?}"
printf 'OK\n'
EOF
chmod 0755 "${FAKE_BIN}/redis-cli"

PATH="${FAKE_BIN}:${PATH}" REDISCLI_AUTH='admin-password-not-in-argv' \
LOBEHUB_ACL_TEST_ARGS="${REDIS_ARGS}" LOBEHUB_ACL_TEST_STDIN="${REDIS_STDIN}" \
  "${ACL_SCRIPT}" apply --env-file "${ENV_FILE}" --admin-user redis-admin >/dev/null

grep -Fx -- '--no-auth-warning' "${REDIS_ARGS}" >/dev/null
grep -Fx -- '--user' "${REDIS_ARGS}" >/dev/null
grep -Fx 'redis-admin' "${REDIS_ARGS}" >/dev/null
grep -Fx -- '-x' "${REDIS_ARGS}" >/dev/null
grep -Fx 'ACL' "${REDIS_ARGS}" >/dev/null
grep -Fx 'SETUSER' "${REDIS_ARGS}" >/dev/null
grep -Fx 'reset' "${REDIS_ARGS}" >/dev/null
grep -Fx 'on' "${REDIS_ARGS}" >/dev/null
grep -Fx '~lobehub:app:*' "${REDIS_ARGS}" >/dev/null
grep -Fx '&lobehub:app:*' "${REDIS_ARGS}" >/dev/null
grep -Fx -- '-@all' "${REDIS_ARGS}" >/dev/null
if grep -F 'application-password-not-in-argv' "${REDIS_ARGS}" >/dev/null \
  || grep -F 'admin-password-not-in-argv' "${REDIS_ARGS}" >/dev/null; then
  echo 'Redis ACL helper exposed a password in redis-cli argv' >&2
  exit 1
fi
test "$(cat "${REDIS_STDIN}")" = '>application-password-not-in-argv'

echo 'LobeHub Redis ACL contract test passed'
