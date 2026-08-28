#!/usr/bin/env bash
set -Eeuo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repository_root=$(cd -- "${script_dir}/../.." && pwd)

RELEASE_ROOT=${RELEASE_ROOT:-/data2/deploy/intelligent-test-agent/releases}
LOG_ROOT=${LOG_ROOT:-/data2/deploy/intelligent-test-agent/logs}
SHARED_ROOT=${SHARED_ROOT:-/data2/deploy/intelligent-test-agent/shared}
ENV_FILE=${ENV_FILE:-${SHARED_ROOT}/runtime.env}
RUNTIME_DATA_SOURCE=${RUNTIME_DATA_SOURCE:-/data/.testagent}
RUNTIME_DATA_ROOT=${RUNTIME_DATA_ROOT:-/data/.testagent}
MAVEN_CACHE_DIR=${MAVEN_CACHE_DIR:-/data2/deploy/shared/maven-repository}
PNPM_STORE_DIR=${PNPM_STORE_DIR:-/data2/deploy/shared/pnpm-store}
COREPACK_CACHE_DIR=${COREPACK_CACHE_DIR:-/data2/deploy/shared/corepack-cache}
BACKEND_BASE_URL=${BACKEND_BASE_URL:-http://192.168.8.100:18082}
FRONTEND_URL=${FRONTEND_URL:-http://192.168.8.100:3000}
BACKEND_PORT=${BACKEND_PORT:-18082}
FRONTEND_BIND_ADDRESS=${FRONTEND_BIND_ADDRESS:-192.168.8.100}
FRONTEND_PORT=${FRONTEND_PORT:-3000}
VERIFY_BACKEND_PORT=${VERIFY_BACKEND_PORT:-28082}
POSTGRES_HOST_PORT=${POSTGRES_HOST_PORT:-15432}
DATABASE_CONTAINER=${DATABASE_CONTAINER:-test-agent-postgres}
PROJECT_NAME=${PROJECT_NAME:-intelligent-test-agent-jenkins}
MAVEN_IMAGE=${MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-21}
# 用户手册构建会读取 Git 提交时间，使用含 git 的固定 Node 完整镜像。
NODE_IMAGE=${NODE_IMAGE:-node:22.16.0-bookworm}
# 体验工作区和应用资产运行期需要执行 Git；复用已固定的 Maven JDK 21 镜像，避免使用不含 Git 的纯 JRE 镜像。
JAVA_RUNTIME_IMAGE=${JAVA_RUNTIME_IMAGE:-maven:3.9.9-eclipse-temurin-21}
NGINX_IMAGE=${NGINX_IMAGE:-nginx:1.27-alpine}
VERIFY_REDIS_IMAGE=${VERIFY_REDIS_IMAGE:-redis:7.4.9-alpine}
HOST_CONTROL=${HOST_CONTROL:-/usr/local/sbin/test-agent-jenkins-host-control}

usage() {
    cat <<'EOF' >&2
Usage: deploy/local/jenkins-release.sh <command> [arguments]

Commands:
  validate-tag TAG
  validate-host
  validate-manifest RELEASE_DIR TAG
  build
  prepare TAG COMMIT RELEASE_DIR
  verify-database-upgrade RELEASE_DIR TAG
  deploy RELEASE_DIR TAG
  verify-deployment TAG
  collect-logs OUTPUT_DIR
  record-result RELEASE_DIR SUCCESS|FAILED
EOF
    exit 2
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "Required command not found: $1" >&2
        return 1
    }
}

sha256_file() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        shasum -a 256 "$1" | awk '{print $1}'
    fi
}

validate_tag() {
    local tag=$1
    [[ "${tag}" =~ ^release-[1-9][0-9]*-[0-9a-f]{8}$ ]] || {
        echo "Invalid immutable release tag: ${tag}" >&2
        return 1
    }
}

validate_commit() {
    local commit=$1 tag=$2
    [[ "${commit}" =~ ^[0-9a-f]{40}$ ]] || {
        echo "Invalid Git commit: ${commit}" >&2
        return 1
    }
    [[ "${tag}" == *"-${commit:0:8}" ]] || {
        echo "Release tag does not match Git commit: ${tag}" >&2
        return 1
    }
}

validate_release_dir() {
    local release_dir=$1 tag=$2
    validate_tag "${tag}"
    [[ "${release_dir}" == "${RELEASE_ROOT}/${tag}" ]] || {
        echo "Release directory is outside the fixed release root: ${release_dir}" >&2
        return 1
    }
}

validate_secret_file() {
    [[ -f "${ENV_FILE}" && ! -L "${ENV_FILE}" ]] || {
        echo "Runtime environment file must be a regular non-symlink file: ${ENV_FILE}" >&2
        return 1
    }
    local mode
    mode=$(stat -c '%a' "${ENV_FILE}")
    [[ "${mode}" == 600 || "${mode}" == 640 ]] || {
        echo "Runtime environment file must use mode 0600 or 0640: ${ENV_FILE} (actual ${mode})" >&2
        return 1
    }
}

runtime_env_value() {
    local key=$1
    python3 - "${ENV_FILE}" "${key}" <<'PY'
import sys

path, expected_key = sys.argv[1:]
with open(path, encoding="utf-8") as source:
    for raw_line in source:
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() != expected_key:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        if not value:
            raise SystemExit(f"Runtime environment value is empty: {expected_key}")
        print(value)
        break
    else:
        raise SystemExit(f"Runtime environment key is missing: {expected_key}")
PY
}

validate_postgres_identifier() {
    local value=$1 label=$2
    [[ "${value}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || {
        echo "Invalid PostgreSQL ${label}: ${value}" >&2
        return 1
    }
}

database_linux_data_root() {
    local database_name=$1 value
    validate_postgres_identifier "${database_name}" database
    value=$(docker exec "${DATABASE_CONTAINER}" sh -lc \
        'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -At -c "select parameter_value from common_parameters where parameter_english = '\''SYS_DATA_ROOT_DIR'\'' and platform = '\''linux'\''"' \
        sh "${database_name}")
    [[ "${value}" == /* && "${value}" != *$'\n'* ]] || {
        echo "Database SYS_DATA_ROOT_DIR must be one absolute Linux path." >&2
        return 1
    }
    printf '%s\n' "${value}"
}

validate_host() {
    local item source_db_name configured_data_root
    require_command docker
    require_command curl
    require_command git
    require_command python3
    validate_secret_file
    source_db_name=$(runtime_env_value TEST_AGENT_TEST_DB_NAME)
    configured_data_root=$(database_linux_data_root "${source_db_name}")
    [[ "${configured_data_root}" == "${RUNTIME_DATA_ROOT}" ]] || {
        echo "Runtime mount target must match database SYS_DATA_ROOT_DIR." >&2
        return 1
    }
    [[ -d "${RUNTIME_DATA_SOURCE}" && ! -L "${RUNTIME_DATA_SOURCE}" ]] || {
        echo "Runtime data source is missing or is a symbolic link: ${RUNTIME_DATA_SOURCE}" >&2
        return 1
    }
    [[ -x "${HOST_CONTROL}" ]] || {
        echo "Fixed host control helper is not installed: ${HOST_CONTROL}" >&2
        return 1
    }
    for item in "${RELEASE_ROOT}" "${LOG_ROOT}" "${SHARED_ROOT}" "${MAVEN_CACHE_DIR}" "${PNPM_STORE_DIR}" "${COREPACK_CACHE_DIR}"; do
        [[ -d "${item}" && -w "${item}" ]] || {
            echo "Jenkins directory is missing or not writable: ${item}" >&2
            return 1
        }
    done
    docker info >/dev/null
    docker run --rm "${JAVA_RUNTIME_IMAGE}" sh -euc 'command -v java >/dev/null; command -v git >/dev/null'
    sudo "${HOST_CONTROL}" status
}

maven_run() {
    docker run --rm \
        --user "$(id -u):$(id -g)" \
        --volume "${repository_root}:/workspace" \
        --volume "${MAVEN_CACHE_DIR}:/maven-cache" \
        --workdir /workspace/backend \
        --env HOME=/tmp/jenkins-home \
        --env MAVEN_CONFIG=/tmp/jenkins-home/.m2 \
        "${MAVEN_IMAGE}" \
        mvn -Dmaven.repo.local=/maven-cache "$@"
}

validate_backend_jar() {
    local backend_jar=$1
    # Jenkins 宿主只保证 Java 运行时；复用固定 JDK 构建镜像校验 JAR，避免额外安装宿主工具。
    docker run --rm \
        --user "$(id -u):$(id -g)" \
        --volume "${backend_jar}:/artifact/backend.jar:ro" \
        --env HOME=/tmp/jenkins-home \
        --env MAVEN_CONFIG=/tmp/jenkins-home/.m2 \
        "${MAVEN_IMAGE}" \
        sh -euc 'jar tf /artifact/backend.jar' >/dev/null
}

build_release() {
    validate_host
    echo '==> Verify Flyway migration naming and immutable bytes'
    maven_run \
        -pl test-agent-persistence -am \
        -Dtest=FlywayMigrationNamingTest \
        -Dsurefire.failIfNoSpecifiedTests=false \
        test

    echo '==> Build executable backend JAR with JDK 21'
    maven_run clean package -DskipTests

    echo '==> Install, typecheck and build agent-web with locked pnpm'
    docker run --rm \
        --user "$(id -u):$(id -g)" \
        --volume "${repository_root}:/workspace" \
        --volume "${PNPM_STORE_DIR}:/pnpm-store" \
        --volume "${COREPACK_CACHE_DIR}:/corepack-cache" \
        --workdir /workspace/frontend \
        --env HOME=/tmp \
        --env COREPACK_HOME=/corepack-cache \
        --env PNPM_HOME=/tmp/pnpm-home \
        --env "VITE_TEST_AGENT_API_BASE_URL=${BACKEND_BASE_URL}" \
        --env VITE_TEST_AGENT_LOBEHUB_ENABLED=false \
        "${NODE_IMAGE}" \
        sh -euc '
            corepack pnpm --version | grep -Fx 10.25.0
            # 容器以 Jenkins UID 运行，显式参数避免 pnpm 向只读挂载的 HOME 父目录写配置。
            corepack pnpm install --frozen-lockfile --store-dir=/pnpm-store
            corepack pnpm typecheck
            corepack pnpm build
        '
}

write_stack() {
    local release_dir=$1 output=$2
    python3 - "${output}" "${release_dir}" "${ENV_FILE}" "${RUNTIME_DATA_SOURCE}" \
        "${RUNTIME_DATA_ROOT}" "${SHARED_ROOT}" "${FRONTEND_BIND_ADDRESS}" "${FRONTEND_PORT}" \
        "${JAVA_RUNTIME_IMAGE}" "${NGINX_IMAGE}" "${BACKEND_PORT}" <<'PY'
import json
import sys

(
    output,
    release_dir,
    env_file,
    runtime_data_source,
    runtime_data_root,
    shared_root,
    frontend_bind_address,
    frontend_port,
    java_image,
    nginx_image,
    backend_port,
) = sys.argv[1:]

stack = {
    "name": "intelligent-test-agent-jenkins",
    "services": {
        "backend": {
            "image": java_image,
            "container_name": "test-agent-jenkins-backend",
            "network_mode": "host",
            "user": "1000:1000",
            "working_dir": "/release/source/backend",
            "env_file": [env_file],
            "environment": {
                "SPRING_PROFILES_ACTIVE": "test",
                "SERVER_PORT": backend_port,
                "TEST_AGENT_BASE_URL": f"http://192.168.8.100:{backend_port}",
                "TEST_AGENT_FRONTEND_URL": f"http://192.168.8.100:{frontend_port}",
                "TEST_AGENT_ROOT": "/release/source",
                "TESTAGENT": "/release/source",
                "HOME": "/release/source/temp",
                "TEST_AGENT_START_OPENCODE_MANAGER": "false",
            },
            "volumes": [
                f"{release_dir}:/release:ro",
                f"{runtime_data_source}:{runtime_data_root}:rw",
                f"{shared_root}/runtime-temp:/release/source/temp:rw",
                f"{shared_root}/backend-logs:/release/source/backend/logs:rw",
            ],
            "entrypoint": [
                "java",
                "-Djava.net.useSystemProxies=false",
                "-Dhttp.proxyHost=",
                "-Dhttp.proxyPort=",
                "-Dhttps.proxyHost=",
                "-Dhttps.proxyPort=",
                "-Dftp.proxyHost=",
                "-Dftp.proxyPort=",
                "-DsocksProxyHost=",
                "-DsocksProxyPort=",
                "-jar",
                "/release/backend.jar",
                "--spring.profiles.active=test",
            ],
            "restart": "unless-stopped",
        },
        "frontend": {
            "image": nginx_image,
            "container_name": "test-agent-jenkins-frontend",
            "ports": [f"{frontend_bind_address}:{frontend_port}:80"],
            "extra_hosts": ["host.docker.internal:host-gateway"],
            "volumes": [
                f"{release_dir}/frontend:/usr/share/nginx/html:ro",
                f"{release_dir}/nginx.conf:/etc/nginx/conf.d/default.conf:ro",
            ],
            "restart": "unless-stopped",
        },
    },
}

with open(output, "w", encoding="utf-8") as target:
    json.dump(stack, target, ensure_ascii=True, indent=2)
    target.write("\n")
PY
}

write_manifest() {
    local release_dir=$1 tag=$2 commit=$3
    local jar_sha nginx_sha stack_sha
    jar_sha=$(sha256_file "${release_dir}/backend.jar")
    nginx_sha=$(sha256_file "${release_dir}/nginx.conf")
    stack_sha=$(sha256_file "${release_dir}/stack.json")
    (
        cd "${release_dir}"
        find frontend -type f -print0 \
            | LC_ALL=C sort -z \
            | xargs -0 sha256sum >frontend.sha256
        find source -type f -print0 \
            | LC_ALL=C sort -z \
            | xargs -0 sha256sum >source.sha256
    )
    python3 - "${release_dir}/manifest.json" "${tag}" "${commit}" "${jar_sha}" "${nginx_sha}" "${stack_sha}" "${BUILD_URL:-}" <<'PY'
import json
import sys

output, tag, commit, jar_sha, nginx_sha, stack_sha, build_url = sys.argv[1:]
manifest = {
    "tag": tag,
    "commit": commit,
    "buildUrl": build_url,
    "backendJarSha256": jar_sha,
    "nginxSha256": nginx_sha,
    "stackSha256": stack_sha,
    "frontendChecksums": "frontend.sha256",
    "sourceChecksums": "source.sha256",
}
with open(output, "w", encoding="utf-8") as target:
    json.dump(manifest, target, ensure_ascii=True, indent=2)
    target.write("\n")
PY
    chmod 600 "${release_dir}/manifest.json" "${release_dir}/stack.json" \
        "${release_dir}/frontend.sha256" "${release_dir}/source.sha256"
}

prepare_release() {
    local tag=$1 commit=$2 release_dir=$3
    local backend_jar=${repository_root}/backend/test-agent-app/target/test-agent-app-0.1.0-SNAPSHOT.jar
    local frontend_dist=${repository_root}/frontend/apps/agent-web/dist
    validate_release_dir "${release_dir}" "${tag}"
    validate_commit "${commit}" "${tag}"
    [[ -f "${backend_jar}" ]] || { echo "Missing backend JAR: ${backend_jar}" >&2; return 1; }
    [[ -f "${frontend_dist}/index.html" ]] || { echo "Missing frontend build: ${frontend_dist}" >&2; return 1; }
    [[ ! -e "${release_dir}" ]] || { echo "Immutable release already exists: ${release_dir}" >&2; return 1; }

    # 运行容器使用宿主 abc 的 UID 1000；源码和制品只读开放，清单仍单独收紧为 0600。
    umask 022
    mkdir -p "${release_dir}/source" "${release_dir}/frontend"
    git -C "${repository_root}" archive "${commit}" | tar -xf - -C "${release_dir}/source"
    # 只读源码挂载前预建嵌套写卷目标，否则 OCI 无法在只读父挂载内创建 mountpoint。
    mkdir -p "${release_dir}/source/backend/logs" "${release_dir}/source/temp"
    cp "${backend_jar}" "${release_dir}/backend.jar"
    cp -R "${frontend_dist}/." "${release_dir}/frontend/"
    cp "${script_dir}/jenkins-nginx.conf" "${release_dir}/nginx.conf"
    validate_backend_jar "${release_dir}/backend.jar"
    write_stack "${release_dir}" "${release_dir}/stack.json"
    docker compose -p "${PROJECT_NAME}" -f "${release_dir}/stack.json" config --quiet
    write_manifest "${release_dir}" "${tag}" "${commit}"
    validate_manifest "${release_dir}" "${tag}"
}

validate_manifest() {
    local release_dir=$1 tag=$2 manifest=${release_dir}/manifest.json
    local commit expected_jar actual_jar expected_nginx actual_nginx expected_stack actual_stack
    validate_release_dir "${release_dir}" "${tag}"
    [[ -f "${manifest}" ]] || { echo "Missing release manifest: ${manifest}" >&2; return 1; }
    read -r commit expected_jar expected_nginx expected_stack < <(python3 - "${manifest}" "${tag}" <<'PY'
import json
import sys

path, expected_tag = sys.argv[1:]
with open(path, encoding="utf-8") as source:
    manifest = json.load(source)
if manifest.get("tag") != expected_tag:
    raise SystemExit("Release manifest tag mismatch")
print(
    manifest.get("commit", ""),
    manifest.get("backendJarSha256", ""),
    manifest.get("nginxSha256", ""),
    manifest.get("stackSha256", ""),
)
PY
    )
    validate_commit "${commit}" "${tag}"
    actual_jar=$(sha256_file "${release_dir}/backend.jar")
    actual_nginx=$(sha256_file "${release_dir}/nginx.conf")
    actual_stack=$(sha256_file "${release_dir}/stack.json")
    [[ "${actual_jar}" == "${expected_jar}" ]] || { echo "Backend JAR checksum mismatch" >&2; return 1; }
    [[ "${actual_nginx}" == "${expected_nginx}" ]] || { echo "Nginx checksum mismatch" >&2; return 1; }
    [[ "${actual_stack}" == "${expected_stack}" ]] || { echo "Stack checksum mismatch" >&2; return 1; }
    (cd "${release_dir}" && sha256sum -c frontend.sha256 >/dev/null)
    (cd "${release_dir}" && sha256sum -c source.sha256 >/dev/null)
    validate_backend_jar "${release_dir}/backend.jar"
    docker compose -p "${PROJECT_NAME}" -f "${release_dir}/stack.json" config --quiet
}

capture_database_history() {
    local output=$1 database_name=$2
    validate_postgres_identifier "${database_name}" database
    docker exec "${DATABASE_CONTAINER}" sh -lc \
        'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -At -F "|" -c "select installed_rank,version,description,checksum,success from flyway_schema_history order by installed_rank"' \
        sh "${database_name}" \
        >"${output}"
    [[ -s "${output}" ]] || {
        echo "Flyway history capture is empty: ${output}" >&2
        return 1
    }
}

verify_database_upgrade() {
    local release_dir=$1 tag=$2 build_number db_name network_name redis_name backend_name verify_root
    local source_db_name source_db_user
    validate_manifest "${release_dir}" "${tag}"
    validate_secret_file
    source_db_name=$(runtime_env_value TEST_AGENT_TEST_DB_NAME)
    source_db_user=$(runtime_env_value TEST_AGENT_TEST_DB_USERNAME)
    validate_postgres_identifier "${source_db_name}" database
    validate_postgres_identifier "${source_db_user}" role
    build_number=${tag#release-}
    build_number=${build_number%%-*}
    [[ "${build_number}" =~ ^[1-9][0-9]*$ ]] || return 1
    db_name="test_agent_jenkins_verify_${build_number}"
    network_name="test-agent-jenkins-verify-${build_number}"
    redis_name="test-agent-jenkins-verify-redis-${build_number}"
    backend_name="test-agent-jenkins-verify-backend-${build_number}"
    verify_root="${release_dir}/database-upgrade-verification"
    mkdir -p "${verify_root}/data" "${verify_root}/backend-logs"

    # 临时后端沿用正式启动契约：在隔离数据根补齐体验模板并初始化 Git，不读取或修改真实运行数据。
    bash "${release_dir}/source/deploy/internal/ensure-experience-workspace-content.sh" \
        --workspace-dir "${verify_root}/data/agent-opencode/workspace/experience"

    cleanup_database_verification() {
        local cleanup_backend_name=$1 cleanup_redis_name=$2 cleanup_network_name=$3 cleanup_db_name=$4
        docker rm -f "${cleanup_backend_name}" >/dev/null 2>&1 || true
        docker rm -f "${cleanup_redis_name}" >/dev/null 2>&1 || true
        docker network rm "${cleanup_network_name}" >/dev/null 2>&1 || true
        docker exec "${DATABASE_CONTAINER}" sh -c \
            'dropdb --if-exists --force -U "$POSTGRES_USER" "$1"' sh "${cleanup_db_name}" >/dev/null 2>&1 || true
    }
    local cleanup_trap
    printf -v cleanup_trap 'cleanup_database_verification %q %q %q %q' \
        "${backend_name}" "${redis_name}" "${network_name}" "${db_name}"
    trap "${cleanup_trap}" EXIT
    cleanup_database_verification "${backend_name}" "${redis_name}" "${network_name}" "${db_name}"

    capture_database_history "${verify_root}/source-flyway-history.tsv" "${source_db_name}"
    docker exec "${DATABASE_CONTAINER}" sh -c '
        set -eu
        dump_file="/tmp/$1.dump"
        trap '\''unlink "$dump_file" 2>/dev/null || true'\'' EXIT
        pg_dump -U "$POSTGRES_USER" -d "$3" --format=custom --file="$dump_file"
        createdb -U "$POSTGRES_USER" --owner="$2" "$1"
        pg_restore --exit-on-error --no-owner --no-privileges --role="$2" \
            -U "$POSTGRES_USER" -d "$1" "$dump_file"
    ' sh "${db_name}" "${source_db_user}" "${source_db_name}"
    docker network create "${network_name}" >/dev/null
    docker run -d --name "${redis_name}" --network "${network_name}" "${VERIFY_REDIS_IMAGE}" >/dev/null
    docker run -d \
        --name "${backend_name}" \
        --network "${network_name}" \
        --add-host host.docker.internal:host-gateway \
        --user "$(id -u):$(id -g)" \
        --env-file "${ENV_FILE}" \
        --env SPRING_PROFILES_ACTIVE=test \
        --env "SERVER_PORT=${VERIFY_BACKEND_PORT}" \
        --env SERVER_ADDRESS=0.0.0.0 \
        --env "TEST_AGENT_BASE_URL=http://127.0.0.1:${VERIFY_BACKEND_PORT}" \
        --env "TEST_AGENT_FRONTEND_URL=http://127.0.0.1:${FRONTEND_PORT}" \
        --env TEST_AGENT_ROOT=/release/source \
        --env TESTAGENT=/release/source \
        --env "HOME=${RUNTIME_DATA_ROOT}" \
        --env TEST_AGENT_SERVER_ADVERTISED_HOST=127.0.0.1 \
        --env TEST_AGENT_TEST_DB_HOST=host.docker.internal \
        --env "TEST_AGENT_TEST_DB_PORT=${POSTGRES_HOST_PORT}" \
        --env "TEST_AGENT_TEST_DB_NAME=${db_name}" \
        --env "TEST_AGENT_REDIS_HOST=${redis_name}" \
        --env TEST_AGENT_REDIS_PORT=6379 \
        --env TEST_AGENT_START_OPENCODE_MANAGER=false \
        --env TEST_AGENT_OPENCODE_BASE_URL=http://host.docker.internal:4096 \
        --env TEST_AGENT_XXL_JOB_ENABLED=false \
        --env TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=false \
        --env TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false \
        --publish "127.0.0.1:${VERIFY_BACKEND_PORT}:${VERIFY_BACKEND_PORT}" \
        --volume "${release_dir}:/release:ro" \
        --volume "${verify_root}:/verify:rw" \
        --volume "${verify_root}/data:${RUNTIME_DATA_ROOT}:rw" \
        --volume "${verify_root}/backend-logs:/release/source/backend/logs:rw" \
        --workdir /release/source/backend \
        "${JAVA_RUNTIME_IMAGE}" \
        java -Djava.net.useSystemProxies=false -Dhttp.proxyHost= -Dhttps.proxyHost= \
        -jar /release/backend.jar --spring.profiles.active=test >/dev/null

    local attempt
    for attempt in $(seq 1 120); do
        if curl -fsS --connect-timeout 2 --max-time 5 \
            "http://127.0.0.1:${VERIFY_BACKEND_PORT}/actuator/health/readiness" \
            | grep -q '"status":"UP"'; then
            break
        fi
        if [[ "$(docker inspect -f '{{.State.Running}}' "${backend_name}" 2>/dev/null || true)" != true ]]; then
            docker logs "${backend_name}" 2>&1 \
                | tail -n 200 \
                | sed -E 's#((PASSWORD|TOKEN|SECRET|AUTHORIZATION)[=:][[:space:]]*)[^[:space:]]+#\1***REDACTED***#Ig' \
                >"${verify_root}/backend-failure.log"
            echo "Database upgrade verification backend exited early." >&2
            return 1
        fi
        sleep 2
    done
    curl -fsS --connect-timeout 2 --max-time 5 \
        "http://127.0.0.1:${VERIFY_BACKEND_PORT}/actuator/health/readiness" \
        | grep -q '"status":"UP"'
    capture_database_history "${verify_root}/upgraded-flyway-history.tsv" "${db_name}"
    docker logs "${backend_name}" 2>&1 \
        | tail -n 300 \
        | sed -E 's#((PASSWORD|TOKEN|SECRET|AUTHORIZATION)[=:][[:space:]]*)[^[:space:]]+#\1***REDACTED***#Ig' \
        >"${verify_root}/backend.log"
    printf '%s\n' SUCCESS >"${verify_root}/result"
    cleanup_database_verification "${backend_name}" "${redis_name}" "${network_name}" "${db_name}"
    trap - EXIT
}

verify_deployment() {
    local tag=$1
    validate_tag "${tag}"
    [[ "$(docker inspect -f '{{.State.Running}}' test-agent-jenkins-backend 2>/dev/null || true)" == true ]]
    [[ "$(docker inspect -f '{{.State.Running}}' test-agent-jenkins-frontend 2>/dev/null || true)" == true ]]
    curl -fsS --connect-timeout 5 --max-time 15 \
        "${BACKEND_BASE_URL}/actuator/health/readiness" \
        | grep -q '"status":"UP"'
    [[ "$(curl -sS --connect-timeout 5 --max-time 15 -o /dev/null -w '%{http_code}' "${FRONTEND_URL}/")" == 200 ]]
}

deploy_release() {
    local release_dir=$1 tag=$2 current_link next_link source_db_name
    validate_manifest "${release_dir}" "${tag}"
    validate_host
    source_db_name=$(runtime_env_value TEST_AGENT_TEST_DB_NAME)
    validate_postgres_identifier "${source_db_name}" database
    capture_database_history "${release_dir}/pre-deploy-flyway-history.tsv" "${source_db_name}"
    sudo "${HOST_CONTROL}" stop-legacy
    docker compose -p "${PROJECT_NAME}" -f "${release_dir}/stack.json" up -d --force-recreate --remove-orphans
    verify_deployment "${tag}"
    capture_database_history "${release_dir}/post-deploy-flyway-history.tsv" "${source_db_name}"
    current_link="${RELEASE_ROOT}/current"
    next_link="${RELEASE_ROOT}/.current-${tag}"
    unlink "${next_link}" 2>/dev/null || true
    ln -s "${release_dir}" "${next_link}"
    mv -Tf "${next_link}" "${current_link}"
    printf '%s\n' "${tag}" >"${SHARED_ROOT}/deployed-release-tag"
}

collect_logs() {
    local output_dir=$1
    [[ "${output_dir}" == "${LOG_ROOT}/"* ]] || {
        echo "Log output is outside the fixed log root: ${output_dir}" >&2
        return 1
    }
    mkdir -p "${output_dir}"
    docker compose -p "${PROJECT_NAME}" -f "${RELEASE_ROOT}/current/stack.json" logs --no-color --tail 500 2>&1 \
        | sed -E 's#((PASSWORD|TOKEN|SECRET|AUTHORIZATION)[=:][[:space:]]*)[^[:space:]]+#\1***REDACTED***#Ig' \
        >"${output_dir}/containers.log" || true
    docker ps --filter 'name=test-agent-jenkins-' \
        --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' \
        >"${output_dir}/containers.tsv" || true
}

record_result() {
    local release_dir=$1 result=$2
    [[ "${result}" == SUCCESS || "${result}" == FAILED ]] || return 2
    [[ "${release_dir}" == "${RELEASE_ROOT}/"* && -d "${release_dir}" ]] || return 1
    printf '%s\n' "${result}" >"${release_dir}/result"
    chmod 600 "${release_dir}/result"
}

[[ $# -ge 1 ]] || usage
command=$1
shift
case "${command}" in
    validate-tag)
        [[ $# -eq 1 ]] || usage
        validate_tag "$1"
        ;;
    validate-host)
        [[ $# -eq 0 ]] || usage
        validate_host
        ;;
    validate-manifest)
        [[ $# -eq 2 ]] || usage
        validate_manifest "$1" "$2"
        ;;
    build)
        [[ $# -eq 0 ]] || usage
        build_release
        ;;
    prepare)
        [[ $# -eq 3 ]] || usage
        prepare_release "$1" "$2" "$3"
        ;;
    verify-database-upgrade)
        [[ $# -eq 2 ]] || usage
        verify_database_upgrade "$1" "$2"
        ;;
    deploy)
        [[ $# -eq 2 ]] || usage
        deploy_release "$1" "$2"
        ;;
    verify-deployment)
        [[ $# -eq 1 ]] || usage
        verify_deployment "$1"
        ;;
    collect-logs)
        [[ $# -eq 1 ]] || usage
        collect_logs "$1"
        ;;
    record-result)
        [[ $# -eq 2 ]] || usage
        record_result "$1" "$2"
        ;;
    *)
        usage
        ;;
esac
