#!/usr/bin/env bash
set -Eeuo pipefail

# 独立验收只在专用 Jenkins 任务中初始化一次；任何已存在但未完成标记的资源都失败关闭，
# 不覆盖 release 的数据库、运行目录、密钥或容器。
[[ "${JOB_NAME:-}" == "intelligent-test-agent-opencode-v2" ]] || {
    echo "OpenCode V2 bootstrap must run in the dedicated Jenkins job" >&2
    exit 64
}

source_env=/data2/deploy/intelligent-test-agent/shared/runtime.env
source_data=/data/.testagent
acceptance_root=/data2/deploy/intelligent-test-agent/v2-acceptance
target_env="${acceptance_root}/shared/runtime.env"
target_data="${acceptance_root}/data"
target_db=testagent_v2_acceptance
database_container=test-agent-postgres
runtime_image=maven:3.9.9-eclipse-temurin-21

[[ -r "${source_env}" && -d "${source_data}" ]] || {
    echo "Release test fixture is unavailable for isolated V2 bootstrap" >&2
    exit 1
}
command -v docker >/dev/null
install -d -m 0751 "${acceptance_root}" "${acceptance_root}/releases" "${acceptance_root}/logs"
install -d -m 0700 "${acceptance_root}/shared"

source_value() {
    python3 - "${source_env}" "$1" <<'PY'
import sys

path, wanted = sys.argv[1:]
with open(path, encoding="utf-8") as source:
    for raw in source:
        if raw.lstrip().startswith("#") or "=" not in raw:
            continue
        key, value = raw.strip().split("=", 1)
        if key.strip() == wanted:
            print(value.strip().strip("\"'"))
            break
PY
}

source_db="$(source_value TEST_AGENT_TEST_DB_NAME)"
source_role="$(source_value TEST_AGENT_TEST_DB_USERNAME)"
[[ "${source_db}" =~ ^[a-z][a-z0-9_]{0,62}$ && "${source_role}" =~ ^[a-z][a-z0-9_]{0,62}$ ]] || {
    echo "Source PostgreSQL database or role is invalid" >&2
    exit 1
}

if [[ ! -f "${acceptance_root}/shared/.data-seed-complete" ]]; then
    if [[ -e "${target_data}" ]]; then
        echo "Unmarked V2 data directory already exists; refusing to overwrite it" >&2
        exit 1
    fi
    install -d -m 0750 "${target_data}"
    # 运行目录保留 server ID、公有配置与工作区；manager/PID state 必须由新实例自己创建。
    docker run --rm --network none --user 0:0 \
        --volume "${source_data}:/source:ro" --volume "${target_data}:/target:rw" \
        "${runtime_image}" sh -euc \
        'cd /source; tar --exclude=./agent-opencode/manager --exclude="./agent-opencode/manager/*" -cf - . | tar -C /target -xf -; test -s /target/.serverid; test ! -e /target/agent-opencode/manager'
    printf '%s\n' "source=${source_data}" "database=${target_db}" >"${acceptance_root}/shared/.data-seed-complete"
    chmod 0600 "${acceptance_root}/shared/.data-seed-complete"
fi

database_exists() {
    docker exec "${database_container}" sh -c \
        'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -c "\conninfo" >/dev/null 2>&1' \
        sh "${target_db}"
}
if [[ ! -f "${acceptance_root}/shared/.database-seed-complete" ]]; then
    if database_exists; then
        echo "Unmarked V2 database already exists; refusing to replace it" >&2
        exit 1
    fi
    docker exec "${database_container}" sh -euc '
        dump_file="/tmp/testagent-v2-acceptance-seed.dump"
        trap '\''unlink "$dump_file" 2>/dev/null || true'\'' EXIT
        pg_dump -U "$POSTGRES_USER" -d "$3" --format=custom --file="$dump_file"
        createdb -U "$POSTGRES_USER" --owner="$2" "$1"
        pg_restore --exit-on-error --no-owner --no-privileges --role="$2" \
            -U "$POSTGRES_USER" -d "$1" "$dump_file"
    ' sh "${target_db}" "${source_role}" "${source_db}"
    touch "${acceptance_root}/shared/.database-seed-complete"
    chmod 0600 "${acceptance_root}/shared/.database-seed-complete"
fi
database_exists || {
    echo "Isolated V2 PostgreSQL database is unavailable" >&2
    exit 1
}

if [[ ! -e "${target_env}" ]]; then
    python3 - "${source_env}" "${target_env}" "${target_db}" <<'PY'
import os
import secrets
import sys
from pathlib import Path

source = Path(sys.argv[1])
target = Path(sys.argv[2])
database = sys.argv[3]
overrides = {
    "TEST_AGENT_TEST_DB_NAME": database,
    "TEST_AGENT_REDIS_HOST": "127.0.0.1",
    "TEST_AGENT_REDIS_PORT": "16380",
    "TEST_AGENT_REDIS_PASSWORD": secrets.token_hex(32),
    "TEST_AGENT_OPENCODE_MANAGER_TOKEN": secrets.token_hex(32),
    "TEST_AGENT_OPENCODE_SERVER_PASSWORD": secrets.token_hex(32),
    "TEST_AGENT_OPENCODE_BASE_URL": "http://192.168.8.100:4296",
    "TEST_AGENT_BASE_URL": "http://192.168.8.100:18182",
    "TEST_AGENT_FRONTEND_URL": "http://192.168.8.100:3100",
    "TEST_AGENT_XXL_JOB_ENABLED": "false",
}
lines = []
for raw in source.read_text(encoding="utf-8").splitlines():
    key = raw.split("=", 1)[0].strip() if "=" in raw else ""
    if key not in overrides:
        lines.append(raw)
lines.extend(f"{key}={value}" for key, value in overrides.items())
fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w", encoding="utf-8") as output:
    output.write("\n".join(lines) + "\n")
PY
fi
[[ -f "${target_env}" && "$(stat -c '%a' "${target_env}")" == 600 ]] || {
    echo "Isolated V2 runtime environment must be a 0600 regular file" >&2
    exit 1
}

if [[ ! -e "${acceptance_root}/shared/redis.conf" ]]; then
    python3 - "${target_env}" "${acceptance_root}/shared/redis.conf" <<'PY'
import os
import sys

password = None
for raw in open(sys.argv[1], encoding="utf-8"):
    if raw.startswith("TEST_AGENT_REDIS_PASSWORD="):
        password = raw.partition("=")[2].strip()
if not password or not password.isalnum():
    raise SystemExit("Isolated Redis password is missing")
fd = os.open(sys.argv[2], os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w", encoding="utf-8") as output:
    output.write(f"bind 0.0.0.0\nport 6379\nprotected-mode yes\nappendonly yes\nrequirepass {password}\n")
PY
    docker run --rm --network none --user 0:0 \
        --volume "${acceptance_root}/shared:/shared:rw" \
        "${runtime_image}" sh -euc 'chown 999:999 /shared/redis.conf; chmod 0600 /shared/redis.conf'
fi

echo "Isolated OpenCode V2 acceptance resources are ready: database=${target_db} data=${target_data}"
