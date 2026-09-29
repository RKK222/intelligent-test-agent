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
XXL_JOB_ADMIN_PORT=${XXL_JOB_ADMIN_PORT:-18083}
XXL_JOB_EXECUTOR_PORT=${XXL_JOB_EXECUTOR_PORT:-9999}
VERIFY_BACKEND_PORT=${VERIFY_BACKEND_PORT:-28082}
POSTGRES_HOST_PORT=${POSTGRES_HOST_PORT:-15432}
DATABASE_CONTAINER=${DATABASE_CONTAINER:-test-agent-postgres}
PROJECT_NAME=${PROJECT_NAME:-intelligent-test-agent-jenkins}
WORKER_PROJECT_NAME=${WORKER_PROJECT_NAME:-intelligent-test-agent-jenkins-opencode}
BACKEND_CONTAINER_NAME=${BACKEND_CONTAINER_NAME:-test-agent-jenkins-backend}
FRONTEND_CONTAINER_NAME=${FRONTEND_CONTAINER_NAME:-test-agent-jenkins-frontend}
VERIFY_NAMESPACE=${VERIFY_NAMESPACE:-test_agent_jenkins_verify}
ISOLATED_ACCEPTANCE=${ISOLATED_ACCEPTANCE:-false}
ISOLATED_REDIS_PORT=${ISOLATED_REDIS_PORT:-26380}
ISOLATED_REDIS_CONTAINER_NAME=${ISOLATED_REDIS_CONTAINER_NAME:-test-agent-v2-redis}
WORKER_IMAGE_REPOSITORY=${WORKER_IMAGE_REPOSITORY:-test-agent-opencode-worker}
WORKER_CONTAINER_NAME=${WORKER_CONTAINER_NAME:-test-agent-jenkins-opencode-worker}
WORKER_PORT_START=${WORKER_PORT_START:-4096}
WORKER_PORT_END=${WORKER_PORT_END:-4105}
OPENCODE_ABI=${OPENCODE_ABI:-V2}
MAVEN_IMAGE=${MAVEN_IMAGE:-maven:3.9.9-eclipse-temurin-21}
# 宿主机检查必须有界结束，避免远端 Docker/数据库/端口探测异常时无声占住 Jenkins。
HOST_CHECK_TIMEOUT_SECONDS=${HOST_CHECK_TIMEOUT_SECONDS:-60}
# 用户手册构建会读取 Git 提交时间，使用含 git 的固定 Node 完整镜像。
NODE_IMAGE=${NODE_IMAGE:-node:22.16.0-bookworm}
# 体验工作区和应用资产运行期需要执行 Git；复用已固定的 Maven JDK 21 镜像，避免使用不含 Git 的纯 JRE 镜像。
JAVA_RUNTIME_IMAGE=${JAVA_RUNTIME_IMAGE:-maven:3.9.9-eclipse-temurin-21}
NGINX_IMAGE=${NGINX_IMAGE:-nginx:1.27-alpine}
VERIFY_REDIS_IMAGE=${VERIFY_REDIS_IMAGE:-redis:7.4.9-alpine}
HOST_CONTROL=${HOST_CONTROL:-/usr/local/sbin/test-agent-jenkins-host-control}
RUNTIME_SERVICE_HOST=${RUNTIME_SERVICE_HOST:-192.168.8.100}

usage() {
    cat <<'EOF' >&2
Usage: deploy/local/jenkins-release.sh <command> [arguments]

Commands:
  validate-tag TAG
  validate-host
  validate-manifest RELEASE_DIR TAG
  manifest-commit RELEASE_DIR TAG
  build
  build-worker
  prepare TAG COMMIT RELEASE_DIR
  prepare-rollback SOURCE_TAG TARGET_TAG TARGET_RELEASE_DIR
  verify-database-upgrade RELEASE_DIR TAG
  deploy RELEASE_DIR TAG
  verify-deployment TAG
  collect-logs OUTPUT_DIR
  cleanup-worker-image-guard TAG
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
    [[ "${tag}" =~ ^(release|rollback-v1)-[1-9][0-9]*-[0-9a-f]{8}$ ]] || {
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

validate_tcp_port() {
    local value=$1 label=$2
    [[ "${value}" =~ ^[1-9][0-9]{0,4}$ && "${value}" -le 65535 ]] || {
        echo "Invalid ${label} port: ${value}" >&2
        return 1
    }
}

validate_isolated_acceptance() {
    [[ "${ISOLATED_ACCEPTANCE}" == true ]] || return 0
    # 独立验收必须与 release 的物理数据、密钥、Compose 项目和监听端口分开。
    [[ "${RELEASE_ROOT}" == /data2/deploy/intelligent-test-agent/v2-acceptance/releases &&
       "${SHARED_ROOT}" == /data2/deploy/intelligent-test-agent/v2-acceptance/shared &&
       "${ENV_FILE}" == "${SHARED_ROOT}/runtime.env" &&
       "${RUNTIME_DATA_SOURCE}" == /data2/deploy/intelligent-test-agent/v2-acceptance/data &&
       "${PROJECT_NAME}" == intelligent-test-agent-v2 &&
       "${WORKER_PROJECT_NAME}" == intelligent-test-agent-v2-opencode &&
       "${BACKEND_CONTAINER_NAME}" == test-agent-v2-backend &&
       "${FRONTEND_CONTAINER_NAME}" == test-agent-v2-frontend &&
       "${WORKER_CONTAINER_NAME}" == test-agent-v2-opencode-worker &&
       "${BACKEND_PORT}" == 18182 && "${FRONTEND_PORT}" == 3100 &&
       "${WORKER_PORT_START}" == 4296 && "${WORKER_PORT_END}" == 4305 &&
       "${ISOLATED_REDIS_PORT}" == 26380 ]] || {
        echo 'Isolated V2 acceptance paths, projects or ports differ from the dedicated contract.' >&2
        return 1
    }
    [[ "$(runtime_env_value TEST_AGENT_TEST_DB_NAME)" == testagent_v2_acceptance ]] || {
        echo 'Isolated V2 acceptance must use its cloned database.' >&2
        return 1
    }
}

cleanup_isolated_redis_port() {
    [[ "${ISOLATED_ACCEPTANCE}" == true ]] || return 0
    local container_id container_name compose_project compose_service found_container=false
    # docker compose 可能在上一次失败后留下一个与当前清单不同名的容器；按端口和 Compose 项目精确回收，避免误碰 release 栈。
    while IFS= read -r container_id; do
        [[ -n "${container_id}" ]] || continue
        found_container=true
        container_name=$(docker inspect --format '{{.Name}}' "${container_id}" 2>/dev/null || true)
        compose_project=$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "${container_id}" 2>/dev/null || true)
        compose_service=$(docker inspect --format '{{index .Config.Labels "com.docker.compose.service"}}' "${container_id}" 2>/dev/null || true)
        if [[ "${container_name}" == "/${ISOLATED_REDIS_CONTAINER_NAME}" ||
              "${compose_project}" == "${PROJECT_NAME}" ||
              ("${compose_project}" == "${PROJECT_NAME}"* && "${compose_service}" == redis) ]]; then
            echo "Removing stale isolated Redis container: ${container_name#\/} (project=${compose_project:-unknown})"
            docker rm --force "${container_id}" >/dev/null 2>&1 || true
        else
            echo "Isolated Redis port ${ISOLATED_REDIS_PORT} is held by unrelated container ${container_name#\/} (project=${compose_project:-unknown}, service=${compose_service:-unknown}); refusing to remove it." >&2
            return 1
        fi
    done < <(docker ps -aq --filter "publish=${ISOLATED_REDIS_PORT}" 2>/dev/null || true)

    [[ "${found_container}" == true ]] && return 0
    # 没有 Docker 容器时保留宿主进程的诊断，并拒绝静默覆盖未知监听者。
    if command -v ss >/dev/null 2>&1 && ss -ltn "( sport = :${ISOLATED_REDIS_PORT} )" 2>/dev/null | tail -n +2 | grep -q LISTEN; then
        echo "Isolated Redis port ${ISOLATED_REDIS_PORT} is held by a host process; refusing to stop an unrelated process." >&2
        ss -ltnp "( sport = :${ISOLATED_REDIS_PORT} )" 2>/dev/null || true
        return 1
    fi
}

validate_timeout() {
    local value=$1
    [[ "${value}" =~ ^[1-9][0-9]*$ ]] || {
        echo "Invalid host check timeout: ${value}" >&2
        return 1
    }
}

validate_opencode_abi() {
    case "${1:-${OPENCODE_ABI}}" in
        V1|V2) ;;
        *)
            echo "Unsupported OpenCode ABI: ${1:-${OPENCODE_ABI}} (expected V1 or V2)" >&2
            return 1
            ;;
    esac
}

opencode_version_for_abi() {
    validate_opencode_abi "$1"
    case "$1" in
        V1) printf '%s\n' '1.18.4' ;;
        V2) printf '%s\n' '2.0.18' ;;
    esac
}

# 所有 OpenCode 下载参数集中在这里，V1 回滚与 V2 发布共用同一个受控 Dockerfile，
# 避免回滚时只替换 VERSION 而实际仍下载 V2 二进制或安装 V2 ABI。
opencode_build_args() {
    validate_opencode_abi
    case "${OPENCODE_ABI}" in
        V1)
            OPENCODE_BUILD_ARGS=(
                --build-arg 'OPENCODE_VERSION=1.18.4'
                --build-arg 'OPENCODE_RELEASE_COMMIT=49c69c5ed3ccf706b61b3febb43c8aaff7f8325e'
                --build-arg 'OPENCODE_RELEASE_BASE_URL=https://github.com/anomalyco/opencode/releases/download/v1.18.4'
                --build-arg 'OPENCODE_ASSET_NAME=opencode-linux-x64-baseline.tar.gz'
                --build-arg 'OPENCODE_ASSET_SIZE=59265643'
                --build-arg 'OPENCODE_ASSET_SHA256=4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc'
                --build-arg 'OPENCODE_BINARY_SHA256=not-recorded'
                --build-arg 'OPENCODE_RUNTIME_PACKAGE_JSON=deploy/internal/opencode-node-runtime-1.18.4.package.json'
                --build-arg 'OPENCODE_RUNTIME_PACKAGE_LOCK=deploy/internal/opencode-node-runtime-1.18.4.package-lock.json'
            )
            ;;
        V2)
            OPENCODE_BUILD_ARGS=(
                --build-arg 'OPENCODE_VERSION=2.0.18'
                --build-arg 'OPENCODE_RELEASE_COMMIT=cd9a14a6b688d4021bee381dfd39d2cef9c0f862'
                --build-arg 'OPENCODE_RELEASE_BASE_URL=https://registry.npmjs.org/@opencode/cli-linux-x64-baseline/-'
                --build-arg 'OPENCODE_ASSET_NAME=cli-linux-x64-baseline-2.0.18.tgz'
                --build-arg 'OPENCODE_ASSET_SIZE=90140661'
                --build-arg 'OPENCODE_ASSET_SHA256=548b709efa8229f97c35f7cc6ba635425c407c5b3382a7435e92a80ce006cfcd'
                --build-arg 'OPENCODE_BINARY_SHA256=not-recorded'
                --build-arg 'OPENCODE_RUNTIME_PACKAGE_JSON=deploy/internal/opencode-node-runtime.package.json'
                --build-arg 'OPENCODE_RUNTIME_PACKAGE_LOCK=deploy/internal/opencode-node-runtime.package-lock.json'
            )
            ;;
    esac
}

worker_image_for_tag() {
    local tag=$1
    validate_tag "${tag}"
    printf '%s:%s\n' "${WORKER_IMAGE_REPOSITORY}" "${tag}"
}

worker_image_guard_name() {
    local tag=$1
    validate_tag "${tag}"
    printf '%s-image-guard-%s\n' "${WORKER_CONTAINER_NAME}" "${tag}"
}

start_worker_image_guard() {
    local tag=$1 image=$2 guard_name
    [[ "${ISOLATED_ACCEPTANCE}" == true ]] || return 0
    guard_name=$(worker_image_guard_name "${tag}")
    # 独立验收的镜像在制备 release 期间必须保持被容器引用，避免宿主清理任务移除未运行的镜像。
    docker run --detach --name "${guard_name}" --network none \
        --entrypoint sleep "${image}" infinity >/dev/null
    [[ "$(docker inspect -f '{{.State.Running}}' "${guard_name}")" == true ]]
}

cleanup_worker_image_guard() {
    local tag=$1 guard_name
    [[ "${ISOLATED_ACCEPTANCE}" == true ]] || return 0
    guard_name=$(worker_image_guard_name "${tag}")
    if docker container inspect "${guard_name}" >/dev/null 2>&1; then
        docker rm --force "${guard_name}" >/dev/null
    fi
}

manager_build_version() {
    local commit=${1:-HEAD} epoch
    epoch=$(git -C "${repository_root}" show -s --format=%ct "${commit}")
    python3 - "${epoch}" <<'PY'
from datetime import datetime, timedelta, timezone
import sys

epoch = int(sys.argv[1])
beijing = timezone(timedelta(hours=8))
print(datetime.fromtimestamp(epoch, beijing).strftime("V%Y%m%d.%H%M%S"))
PY
}

database_linux_data_root() {
    local database_name=$1 value
    validate_postgres_identifier "${database_name}" database
    value=$(timeout --foreground "${HOST_CHECK_TIMEOUT_SECONDS}s" docker exec "${DATABASE_CONTAINER}" sh -lc \
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
    require_command timeout
    validate_timeout "${HOST_CHECK_TIMEOUT_SECONDS}"
    validate_opencode_abi
    echo "==> Validate Jenkins host prerequisites (timeout ${HOST_CHECK_TIMEOUT_SECONDS}s)"
    require_command docker
    require_command curl
    require_command git
    require_command python3
    validate_tcp_port "${XXL_JOB_ADMIN_PORT}" "XXL Admin"
    validate_tcp_port "${XXL_JOB_EXECUTOR_PORT}" "XXL executor"
    validate_tcp_port "${WORKER_PORT_START}" "OpenCode worker start"
    validate_tcp_port "${WORKER_PORT_END}" "OpenCode worker end"
    [[ "${WORKER_PORT_START}" -le "${WORKER_PORT_END}" ]] || {
        echo "OpenCode worker port range is reversed: ${WORKER_PORT_START}-${WORKER_PORT_END}" >&2
        return 1
    }
    validate_secret_file
    validate_isolated_acceptance
    echo '    - database runtime data root'
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
    if [[ "${ISOLATED_ACCEPTANCE}" != true ]]; then
        [[ -x "${HOST_CONTROL}" ]] || {
            echo "Fixed host control helper is not installed: ${HOST_CONTROL}" >&2
            return 1
        }
    fi
    for item in "${RELEASE_ROOT}" "${LOG_ROOT}" "${SHARED_ROOT}" "${MAVEN_CACHE_DIR}" "${PNPM_STORE_DIR}" "${COREPACK_CACHE_DIR}"; do
        [[ -d "${item}" && -w "${item}" ]] || {
            echo "Jenkins directory is missing or not writable: ${item}" >&2
            return 1
        }
    done
    echo '    - Docker daemon'
    timeout --foreground "${HOST_CHECK_TIMEOUT_SECONDS}s" docker info >/dev/null
    echo "    - runtime image ${JAVA_RUNTIME_IMAGE}"
    timeout --foreground "${HOST_CHECK_TIMEOUT_SECONDS}s" docker run --rm "${JAVA_RUNTIME_IMAGE}" sh -euc 'command -v java >/dev/null; command -v git >/dev/null'
    if [[ "${ISOLATED_ACCEPTANCE}" == true ]]; then
        # FUSE 挂载上的 0600 配置不能依赖 Redis UID 直接读取；启动前验证容器内复制后的可读性。
        [[ -f "${SHARED_ROOT}/redis.conf" && ! -L "${SHARED_ROOT}/redis.conf" ]] || {
            echo "Isolated Redis config must be a regular non-symlink file." >&2
            return 1
        }
        echo '    - isolated Redis config mount'
        timeout --foreground "${HOST_CHECK_TIMEOUT_SECONDS}s" docker run --rm --network none --user 0:0 \
            --mount "type=bind,src=${SHARED_ROOT}/redis.conf,dst=/usr/local/etc/redis/redis.conf,readonly" \
            --entrypoint sh "${VERIFY_REDIS_IMAGE}" -euc '
                install -m 0600 /usr/local/etc/redis/redis.conf /tmp/test-agent-redis.conf
                chown redis:redis /tmp/test-agent-redis.conf
                exec /usr/bin/setpriv --reuid redis --regid redis --clear-groups test -s /tmp/test-agent-redis.conf
            '
    fi
    if [[ "${ISOLATED_ACCEPTANCE}" != true ]]; then
        echo "    - fixed host control ${HOST_CONTROL}"
        timeout --foreground "${HOST_CHECK_TIMEOUT_SECONDS}s" sudo "${HOST_CONTROL}" status
    fi
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

validate_runtime_release_access() {
    local release_dir=$1
    # /data2 is a FUSE merge mount on the shared test host.  The runtime UID must be able to
    # traverse the host-side release path; stat/checksum checks executed as Jenkins do not prove
    # that Java can open the same bind-mounted JAR.
    docker run --rm \
        --user 1000:1000 \
        --volume "${release_dir}:/release:ro" \
        --env HOME=/tmp/jenkins-home \
        --env MAVEN_CONFIG=/tmp/jenkins-home/.m2 \
        "${MAVEN_IMAGE}" \
        sh -euc 'test -r /release/backend.jar; test -d /release/source/backend; jar tf /release/backend.jar' \
        >/dev/null
}

build_worker_image() {
    local worker_image build_version worker_build_log
    validate_opencode_abi
    [[ -n "${RELEASE_TAG:-}" ]] || {
        echo 'RELEASE_TAG is required to build the immutable OpenCode worker image.' >&2
        return 1
    }
    worker_image=$(worker_image_for_tag "${RELEASE_TAG}")
    build_version=$(manager_build_version "${GIT_COMMIT_FULL:-HEAD}")
    opencode_build_args
    echo "==> Build and verify immutable OpenCode ${OPENCODE_ABI} worker image ${worker_image}"
    worker_build_log=$(mktemp "${TMPDIR:-/tmp}/test-agent-worker-build.XXXXXX")
    worker_retry_log=$(mktemp "${TMPDIR:-/tmp}/test-agent-worker-build-retry.XXXXXX")
    worker_recovery_log=$(mktemp "${TMPDIR:-/tmp}/test-agent-worker-build-recovery.XXXXXX")
    if ! docker build \
        --file "${repository_root}/deploy/internal/opencode-worker.Dockerfile" \
        --tag "${worker_image}" \
        --build-arg "MANAGER_BUILD_VERSION=${build_version}" \
        "${OPENCODE_BUILD_ARGS[@]}" \
        "${repository_root}" 2>&1 | tee "${worker_build_log}"; then
        # 测试机构建缓存偶发丢失 BuildKit snapshot；只对该明确错误做一次无缓存重建，业务构建错误仍立即失败。
        if ! grep -Eq 'failed to stat active key during commit|snapshot .* does not exist' "${worker_build_log}"; then
            rm -f -- "${worker_build_log}" "${worker_retry_log}" "${worker_recovery_log}"
            return 1
        fi
        echo 'BuildKit snapshot cache is inconsistent; retrying the worker image once without cache.' >&2
        if ! docker build --no-cache \
            --file "${repository_root}/deploy/internal/opencode-worker.Dockerfile" \
            --tag "${worker_image}" \
            --build-arg "MANAGER_BUILD_VERSION=${build_version}" \
            "${OPENCODE_BUILD_ARGS[@]}" \
            "${repository_root}" 2>&1 | tee "${worker_retry_log}"; then
            if ! grep -Eq 'failed to stat active key during commit|snapshot .* does not exist' "${worker_retry_log}"; then
                rm -f -- "${worker_build_log}" "${worker_retry_log}" "${worker_recovery_log}"
                return 1
            fi
            # 两次均命中同一精确错误时，只清理 BuildKit 自己的构建缓存再重试一次；
            # 不删除镜像、容器或卷，业务构建错误仍立即失败。worker Dockerfile 使用
            # BuildKit 的只读上下文挂载，不能退回不支持该语法的 legacy builder。
            echo 'BuildKit snapshot cache remains inconsistent; pruning builder cache before one final retry.' >&2
            docker builder prune --all --force
            if ! docker build --no-cache \
                --file "${repository_root}/deploy/internal/opencode-worker.Dockerfile" \
                --tag "${worker_image}" \
                --build-arg "MANAGER_BUILD_VERSION=${build_version}" \
                "${OPENCODE_BUILD_ARGS[@]}" \
                "${repository_root}" 2>&1 | tee "${worker_recovery_log}"; then
                rm -f -- "${worker_build_log}" "${worker_retry_log}" "${worker_recovery_log}"
                return 1
            fi
        fi
    fi
    rm -f -- "${worker_build_log}" "${worker_retry_log}" "${worker_recovery_log}"
    start_worker_image_guard "${RELEASE_TAG}" "${worker_image}"
    EXPECTED_OPENCODE_ABI="${OPENCODE_ABI}" \
        "${repository_root}/tools/verify-opencode-node-worker-image.sh" "${worker_image}"
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
    # 100 测试机复用本地账号密码登录页；企业交付构建仍保持 production 环境的 AAM 默认。
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
        --env VITE_ENV=localhost \
        --env VITE_TEST_AGENT_LOBEHUB_ENABLED=false \
        "${NODE_IMAGE}" \
        sh -euc '
            corepack pnpm --version | grep -Fx 10.25.0
            # 容器以 Jenkins UID 运行，显式参数避免 pnpm 向只读挂载的 HOME 父目录写配置。
            corepack pnpm install --frozen-lockfile --store-dir=/pnpm-store
            corepack pnpm typecheck
            corepack pnpm build
        '

    build_worker_image
}

write_worker_stack() {
    local output=$1 worker_image=$2
    python3 - "${output}" "${RUNTIME_DATA_SOURCE}" \
        "${RUNTIME_DATA_ROOT}" "${WORKER_CONTAINER_NAME}" "${worker_image}" "${BACKEND_PORT}" \
        "${WORKER_PORT_START}" "${WORKER_PORT_END}" "${WORKER_PROJECT_NAME}" <<'PY'
import json
import sys

(
    output,
    runtime_data_source,
    runtime_data_root,
    container_name,
    worker_image,
    backend_port,
    worker_port_start,
    worker_port_end,
    worker_project_name,
) = sys.argv[1:]

stack = {
    "name": worker_project_name,
    "services": {
        "opencode-worker": {
            "image": worker_image,
            "container_name": container_name,
            "hostname": container_name,
            "network_mode": "host",
            "privileged": True,
            "pids_limit": 8192,
            "ulimits": {
                "nofile": {"soft": 262144, "hard": 262144},
                "nproc": {"soft": 8192, "hard": 8192},
            },
            "environment": {
                "OPENCODE_MANAGER_BACKEND_PORT": backend_port,
                "OPENCODE_MANAGER_PORT_START": worker_port_start,
                "OPENCODE_MANAGER_PORT_END": worker_port_end,
                "OPENCODE_MANAGER_TOKEN": "${TEST_AGENT_OPENCODE_MANAGER_TOKEN:?TEST_AGENT_OPENCODE_MANAGER_TOKEN is required}",
                "SYS_DATA_ROOT_DIR": runtime_data_root,
                "OPENCODE_MANAGER_STATE_DIR": f"{runtime_data_root}/agent-opencode/manager/jenkins-worker",
                "OPENCODE_BIN": "/usr/local/bin/opencode",
                "TEST_AGENT_PROGRAM_ROOT": "/data/testagent/programs",
                "OPENCODE_ALLOWED_CORS": "",
                "OPENCODE_MANAGER_HEARTBEAT_INTERVAL": "5s",
                "OPENCODE_MANAGER_RECONNECT_INTERVAL": "10s",
                "TEST_AGENT_OPENCODE_SERVER_PASSWORD": "${TEST_AGENT_OPENCODE_SERVER_PASSWORD:?TEST_AGENT_OPENCODE_SERVER_PASSWORD is required}",
            },
            "volumes": [f"{runtime_data_source}:{runtime_data_root}:rw"],
            "healthcheck": {
                "test": ["CMD-SHELL", "pgrep -f 'opencode-manager run' >/dev/null"],
                "interval": "10s",
                "timeout": "3s",
                "retries": 12,
            },
            "stop_grace_period": "30s",
            "restart": "unless-stopped",
        }
    },
}

with open(output, "w", encoding="utf-8") as target:
    json.dump(stack, target, ensure_ascii=True, indent=2)
    target.write("\n")
PY
}

write_stack() {
    local release_dir=$1 output=$2 xxl_job_mysql_port xxl_job_mysql_database xxl_job_mysql_url
    xxl_job_mysql_port=$(runtime_env_value TEST_AGENT_XXL_JOB_MYSQL_PORT)
    xxl_job_mysql_database=$(runtime_env_value TEST_AGENT_XXL_JOB_MYSQL_DATABASE)
    [[ "${xxl_job_mysql_port}" =~ ^[1-9][0-9]{0,4}$ && "${xxl_job_mysql_port}" -le 65535 ]] || {
        echo "Invalid XXL MySQL port in runtime environment." >&2
        return 1
    }
    validate_postgres_identifier "${xxl_job_mysql_database}" "XXL MySQL database"
    xxl_job_mysql_url="jdbc:mysql://${RUNTIME_SERVICE_HOST}:${xxl_job_mysql_port}/${xxl_job_mysql_database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
    python3 - "${output}" "${release_dir}" "${ENV_FILE}" "${RUNTIME_DATA_SOURCE}" \
        "${RUNTIME_DATA_ROOT}" "${SHARED_ROOT}" "${FRONTEND_BIND_ADDRESS}" "${FRONTEND_PORT}" \
        "${JAVA_RUNTIME_IMAGE}" "${NGINX_IMAGE}" "${BACKEND_PORT}" "${RUNTIME_SERVICE_HOST}" \
        "${xxl_job_mysql_url}" "${XXL_JOB_ADMIN_PORT}" "${XXL_JOB_EXECUTOR_PORT}" \
        "${PROJECT_NAME}" "${BACKEND_CONTAINER_NAME}" "${FRONTEND_CONTAINER_NAME}" \
        "${ISOLATED_ACCEPTANCE}" "${ISOLATED_REDIS_PORT}" "${ISOLATED_REDIS_CONTAINER_NAME}" <<'PY'
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
    runtime_service_host,
    xxl_job_mysql_url,
    xxl_job_admin_port,
    xxl_job_executor_port,
    project_name,
    backend_container_name,
    frontend_container_name,
    isolated_acceptance,
    isolated_redis_port,
    isolated_redis_container_name,
) = sys.argv[1:]

stack = {
    "name": project_name,
    "services": {
        "backend": {
            "image": java_image,
            "container_name": backend_container_name,
            "network_mode": "host",
            "user": "1000:1000",
            "working_dir": "/release/source/backend",
            "env_file": [env_file],
            "environment": {
                "SPRING_PROFILES_ACTIVE": "test",
                "SERVER_ADDRESS": "0.0.0.0",
                "SERVER_PORT": backend_port,
                "TEST_AGENT_BASE_URL": f"http://192.168.8.100:{backend_port}",
                "TEST_AGENT_FRONTEND_URL": f"http://192.168.8.100:{frontend_port}",
                "TEST_AGENT_ROOT": "/release/source",
                "TESTAGENT": "/release/source",
                "HOME": "/release/source/temp",
                "TEST_AGENT_START_OPENCODE_MANAGER": "false",
                "TEST_AGENT_SERVER_ADVERTISED_HOST": runtime_service_host,
                "TEST_AGENT_REDIS_HOST": runtime_service_host,
                "TEST_AGENT_XXL_JOB_MYSQL_URL": xxl_job_mysql_url,
                "TEST_AGENT_XXL_JOB_ADMIN_PORT": xxl_job_admin_port,
                "TEST_AGENT_XXL_JOB_EXECUTOR_PORT": xxl_job_executor_port,
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
            "container_name": frontend_container_name,
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

if isolated_acceptance == "true":
    # V2 验收与 release 分别使用 Redis、数据库副本和物理数据根；Compose 不触碰 release 的容器或端口。
    stack["services"]["backend"]["environment"].update({
        "TEST_AGENT_REDIS_HOST": "127.0.0.1",
        "TEST_AGENT_REDIS_PORT": isolated_redis_port,
        "TEST_AGENT_CORS_ALLOWED_ORIGINS": f"http://192.168.8.100:{frontend_port}",
        "TEST_AGENT_XXL_JOB_ENABLED": "false",
    })
    stack["services"]["redis"] = {
        "image": "redis:7.4.9-alpine",
        "container_name": isolated_redis_container_name,
        "ports": [f"127.0.0.1:{isolated_redis_port}:6379"],
        "volumes": [
            f"{shared_root}/redis.conf:/usr/local/etc/redis/redis.conf:ro",
            f"{project_name}-redis-data:/data:rw",
        ],
        # 与企业离线 Redis 启动脚本保持一致：先用 root 复制 0600 配置，再以 redis UID 启动。
        "entrypoint": ["sh", "-euc", "install -m 0600 /usr/local/etc/redis/redis.conf /tmp/test-agent-redis.conf && chown redis:redis /tmp/test-agent-redis.conf && exec /usr/bin/setpriv --reuid redis --regid redis --clear-groups redis-server /tmp/test-agent-redis.conf"],
        "command": [],
        "restart": "unless-stopped",
    }
    stack["volumes"] = {f"{project_name}-redis-data": {}}

with open(output, "w", encoding="utf-8") as target:
    json.dump(stack, target, ensure_ascii=True, indent=2)
    target.write("\n")
PY
}

write_manifest() {
    local release_dir=$1 tag=$2 commit=$3
    local jar_sha nginx_sha stack_sha worker_stack_sha worker_image worker_image_id runtime_abi runtime_version
    validate_opencode_abi
    runtime_abi=${OPENCODE_ABI}
    runtime_version=$(opencode_version_for_abi "${runtime_abi}")
    jar_sha=$(sha256_file "${release_dir}/backend.jar")
    nginx_sha=$(sha256_file "${release_dir}/nginx.conf")
    stack_sha=$(sha256_file "${release_dir}/stack.json")
    worker_stack_sha=$(sha256_file "${release_dir}/worker-stack.json")
    worker_image=$(worker_image_for_tag "${tag}")
    worker_image_id=$(docker image inspect --format '{{.Id}}' "${worker_image}")
    (
        cd "${release_dir}"
        find frontend -type f -print0 \
            | LC_ALL=C sort -z \
            | xargs -0 sha256sum >frontend.sha256
        find source -type f -print0 \
            | LC_ALL=C sort -z \
            | xargs -0 sha256sum >source.sha256
    )
    python3 - "${release_dir}/manifest.json" "${tag}" "${commit}" "${jar_sha}" "${nginx_sha}" \
        "${stack_sha}" "${worker_stack_sha}" "${worker_image}" "${worker_image_id}" \
        "${runtime_abi}" "${runtime_version}" "${BUILD_URL:-}" <<'PY'
import json
import sys

(
    output,
    tag,
    commit,
    jar_sha,
    nginx_sha,
    stack_sha,
    worker_stack_sha,
    worker_image,
    worker_image_id,
    runtime_abi,
    runtime_version,
    build_url,
) = sys.argv[1:]
manifest = {
    "schemaVersion": 2,
    "tag": tag,
    "commit": commit,
    "buildUrl": build_url,
    "backendJarSha256": jar_sha,
    "nginxSha256": nginx_sha,
    "stackSha256": stack_sha,
    "workerStackSha256": worker_stack_sha,
    "workerImage": worker_image,
    "workerImageId": worker_image_id,
    "runtimeAbi": runtime_abi,
    "runtimeVersion": runtime_version,
    "frontendChecksums": "frontend.sha256",
    "sourceChecksums": "source.sha256",
}
with open(output, "w", encoding="utf-8") as target:
    json.dump(manifest, target, ensure_ascii=True, indent=2)
    target.write("\n")
PY
    chmod 600 "${release_dir}/manifest.json" "${release_dir}/stack.json" "${release_dir}/worker-stack.json" \
        "${release_dir}/frontend.sha256" "${release_dir}/source.sha256"
}

prepare_release() {
    local tag=$1 commit=$2 release_dir=$3
    local backend_jar=${repository_root}/backend/test-agent-app/target/test-agent-app-0.1.0-SNAPSHOT.jar
    local frontend_dist=${repository_root}/frontend/apps/agent-web/dist
    local worker_image
    validate_release_dir "${release_dir}" "${tag}"
    validate_commit "${commit}" "${tag}"
    [[ -f "${backend_jar}" ]] || { echo "Missing backend JAR: ${backend_jar}" >&2; return 1; }
    [[ -f "${frontend_dist}/index.html" ]] || { echo "Missing frontend build: ${frontend_dist}" >&2; return 1; }
    worker_image=$(worker_image_for_tag "${tag}")
    docker image inspect "${worker_image}" >/dev/null
    [[ ! -e "${release_dir}" ]] || { echo "Immutable release already exists: ${release_dir}" >&2; return 1; }

    # 运行容器使用宿主 abc 的 UID 1000；源码和制品只读开放，清单仍单独收紧为 0600。
    umask 022
    mkdir -p "${release_dir}/source" "${release_dir}/frontend"
    git -C "${repository_root}" archive "${commit}" | tar -xf - -C "${release_dir}/source"
    # 只读源码挂载前预建嵌套写卷目标，否则 OCI 无法在只读父挂载内创建 mountpoint。
    mkdir -p "${release_dir}/source/backend/logs" "${release_dir}/source/temp"
    cp "${backend_jar}" "${release_dir}/backend.jar"
    cp -R "${frontend_dist}/." "${release_dir}/frontend/"
    sed \
        -e "s/__RUNTIME_SERVICE_HOST__/${RUNTIME_SERVICE_HOST}/g" \
        -e "s/__BACKEND_PORT__/${BACKEND_PORT}/g" \
        -e "s/__XXL_JOB_ADMIN_PORT__/${XXL_JOB_ADMIN_PORT}/g" \
        "${script_dir}/jenkins-nginx.conf" >"${release_dir}/nginx.conf"
    # mergerfs 上的默认 ACL 会覆盖 umask；显式收紧写权限并保留运行 UID 所需的只读/穿越权限。
    chmod 0644 "${release_dir}/backend.jar" "${release_dir}/nginx.conf"
    chmod -R u=rwX,go=rX "${release_dir}/source" "${release_dir}/frontend"
    grep -Fq "proxy_pass http://${RUNTIME_SERVICE_HOST}:${BACKEND_PORT};" "${release_dir}/nginx.conf"
    grep -Fq "proxy_pass http://${RUNTIME_SERVICE_HOST}:${XXL_JOB_ADMIN_PORT};" "${release_dir}/nginx.conf"
    ! grep -Eq '__[A-Z0-9_]+__' "${release_dir}/nginx.conf"
    validate_backend_jar "${release_dir}/backend.jar"
    write_stack "${release_dir}" "${release_dir}/stack.json"
    write_worker_stack "${release_dir}/worker-stack.json" "${worker_image}"
    docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}" -f "${release_dir}/stack.json" config --quiet
    docker compose --env-file "${ENV_FILE}" -p "${WORKER_PROJECT_NAME}" \
        -f "${release_dir}/worker-stack.json" config --quiet
    write_manifest "${release_dir}" "${tag}" "${commit}"
    validate_manifest "${release_dir}" "${tag}"
}

manifest_commit() {
    local release_dir=$1 tag=$2 manifest
    manifest="${release_dir}/manifest.json"
    validate_release_dir "${release_dir}" "${tag}"
    [[ -f "${manifest}" ]] || { echo "Missing release manifest: ${manifest}" >&2; return 1; }
    local commit
    commit=$(python3 - "${manifest}" <<'PY'
import json
import re
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    commit = json.load(source).get("commit", "")
if not re.fullmatch(r"[0-9a-f]{40}", commit):
    raise SystemExit("Release manifest commit is invalid")
print(commit)
PY
    )
    validate_commit "${commit}" "${tag}"
    printf '%s\n' "${commit}"
}

# 为 V1 回滚生成独立 immutable release：沿用目标 release 的平台制品和源码，
# 只重新绑定 1.18.4 worker 镜像，避免覆盖原 V2 manifest 或误把一次 V2 重部署当成 V1 回滚。
prepare_rollback_release() {
    local source_tag=$1 target_tag=$2 release_dir=$3 source_dir source_commit worker_image
    validate_release_dir "${release_dir}" "${target_tag}"
    validate_tag "${source_tag}"
    source_dir="${RELEASE_ROOT}/${source_tag}"
    [[ "${source_dir}" != "${release_dir}" ]] || {
        echo 'Rollback target release must be different from the source release.' >&2
        return 1
    }
    validate_manifest "${source_dir}" "${source_tag}"
    source_commit=$(manifest_commit "${source_dir}" "${source_tag}")
    validate_commit "${source_commit}" "${target_tag}"
    [[ ! -e "${release_dir}" ]] || { echo "Immutable rollback release already exists: ${release_dir}" >&2; return 1; }
    [[ -f "${source_dir}/backend.jar" && -f "${source_dir}/nginx.conf" ]] || {
        echo "Source release is missing platform artifacts: ${source_dir}" >&2
        return 1
    }
    [[ -d "${source_dir}/source" && -d "${source_dir}/frontend" ]] || {
        echo "Source release is missing source or frontend artifacts: ${source_dir}" >&2
        return 1
    }
    worker_image=$(worker_image_for_tag "${target_tag}")
    docker image inspect "${worker_image}" >/dev/null

    umask 022
    mkdir -p "${release_dir}"
    cp "${source_dir}/backend.jar" "${release_dir}/backend.jar"
    cp -R "${source_dir}/source" "${release_dir}/source"
    cp -R "${source_dir}/frontend" "${release_dir}/frontend"
    sed \
        -e "s/__RUNTIME_SERVICE_HOST__/${RUNTIME_SERVICE_HOST}/g" \
        -e "s/__BACKEND_PORT__/${BACKEND_PORT}/g" \
        -e "s/__XXL_JOB_ADMIN_PORT__/${XXL_JOB_ADMIN_PORT}/g" \
        "${script_dir}/jenkins-nginx.conf" >"${release_dir}/nginx.conf"
    mkdir -p "${release_dir}/source/backend/logs" "${release_dir}/source/temp"
    chmod 0644 "${release_dir}/backend.jar" "${release_dir}/nginx.conf"
    chmod -R u=rwX,go=rX "${release_dir}/source" "${release_dir}/frontend"
    validate_backend_jar "${release_dir}/backend.jar"
    write_stack "${release_dir}" "${release_dir}/stack.json"
    write_worker_stack "${release_dir}/worker-stack.json" "${worker_image}"
    docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}" -f "${release_dir}/stack.json" config --quiet
    docker compose --env-file "${ENV_FILE}" -p "${WORKER_PROJECT_NAME}" \
        -f "${release_dir}/worker-stack.json" config --quiet
    write_manifest "${release_dir}" "${target_tag}" "${source_commit}"
    validate_manifest "${release_dir}" "${target_tag}"
}

validate_manifest() {
    local release_dir=$1 tag=$2
    # Bash 会先展开同一条 local 命令的所有右值；manifest 若与 release_dir 同行声明，
    # 在嵌套回滚调用中会意外读取调用方的目标目录，而不是当前参数对应的目录。
    local manifest="${release_dir}/manifest.json"
    local values schema_version commit expected_jar actual_jar expected_nginx actual_nginx
    local expected_stack actual_stack expected_worker_stack actual_worker_stack worker_image worker_image_id actual_worker_image_id
    local runtime_abi runtime_version
    validate_release_dir "${release_dir}" "${tag}"
    [[ -f "${manifest}" ]] || { echo "Missing release manifest: ${manifest}" >&2; return 1; }
    values=$(python3 - "${manifest}" "${tag}" <<'PY'
import json
import sys

path, expected_tag = sys.argv[1:]
with open(path, encoding="utf-8") as source:
    manifest = json.load(source)
if manifest.get("tag") != expected_tag:
    raise SystemExit("Release manifest tag mismatch")
schema_version = manifest.get("schemaVersion", 1)
if schema_version not in {1, 2}:
    raise SystemExit(f"Unsupported release manifest schema: {schema_version}")
values = [
    str(schema_version),
    manifest.get("commit", ""),
    manifest.get("backendJarSha256", ""),
    manifest.get("nginxSha256", ""),
    manifest.get("stackSha256", ""),
    manifest.get("workerStackSha256", ""),
    manifest.get("workerImage", ""),
    manifest.get("workerImageId", ""),
    manifest.get("runtimeAbi", "V2"),
    manifest.get("runtimeVersion", ""),
]
if any("|" in str(value) for value in values):
    raise SystemExit("Release manifest contains an invalid delimiter")
print("|".join(values))
PY
    )
    IFS='|' read -r schema_version commit expected_jar expected_nginx expected_stack \
        expected_worker_stack worker_image worker_image_id runtime_abi runtime_version <<<"${values}"
    validate_opencode_abi "${runtime_abi}"
    if [[ -n "${runtime_version}" && "${runtime_version}" != "$(opencode_version_for_abi "${runtime_abi}")" ]]; then
        echo "OpenCode runtime version does not match ABI: ${runtime_abi}/${runtime_version}" >&2
        return 1
    fi
    validate_commit "${commit}" "${tag}"
    actual_jar=$(sha256_file "${release_dir}/backend.jar")
    actual_nginx=$(sha256_file "${release_dir}/nginx.conf")
    actual_stack=$(sha256_file "${release_dir}/stack.json")
    [[ "${actual_jar}" == "${expected_jar}" ]] || { echo "Backend JAR checksum mismatch" >&2; return 1; }
    [[ "${actual_nginx}" == "${expected_nginx}" ]] || { echo "Nginx checksum mismatch" >&2; return 1; }
    [[ "${actual_stack}" == "${expected_stack}" ]] || { echo "Stack checksum mismatch" >&2; return 1; }
    if [[ "${schema_version}" == 2 ]]; then
        [[ -f "${release_dir}/worker-stack.json" ]] || {
            echo "Missing worker stack for schema v2 release" >&2
            return 1
        }
        actual_worker_stack=$(sha256_file "${release_dir}/worker-stack.json")
        [[ "${actual_worker_stack}" == "${expected_worker_stack}" ]] || {
            echo "Worker stack checksum mismatch" >&2
            return 1
        }
        [[ "${worker_image}" == "$(worker_image_for_tag "${tag}")" ]] || {
            echo "Worker image tag mismatch" >&2
            return 1
        }
        actual_worker_image_id=$(docker image inspect --format '{{.Id}}' "${worker_image}")
        [[ "${actual_worker_image_id}" == "${worker_image_id}" ]] || {
            echo "Worker image ID mismatch" >&2
            return 1
        }
    fi
    (cd "${release_dir}" && sha256sum -c frontend.sha256 --quiet)
    (cd "${release_dir}" && sha256sum -c source.sha256 --quiet)
    validate_backend_jar "${release_dir}/backend.jar"
    validate_runtime_release_access "${release_dir}"
    docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}" -f "${release_dir}/stack.json" config --quiet
    if [[ "${schema_version}" == 2 ]]; then
        docker compose --env-file "${ENV_FILE}" -p "${WORKER_PROJECT_NAME}" \
            -f "${release_dir}/worker-stack.json" config --quiet
    fi
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
    db_name="${VERIFY_NAMESPACE}_${build_number}"
    network_name="${VERIFY_NAMESPACE//_/-}-${build_number}"
    redis_name="${VERIFY_NAMESPACE//_/-}-redis-${build_number}"
    backend_name="${VERIFY_NAMESPACE//_/-}-backend-${build_number}"
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
        --env TEST_AGENT_REDIS_PASSWORD= \
        --env TEST_AGENT_START_OPENCODE_MANAGER=false \
        --env "TEST_AGENT_OPENCODE_BASE_URL=http://host.docker.internal:${WORKER_PORT_START}" \
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
    local tag=$1 attempt backend_ready=false frontend_ready=false xxl_admin_ready=false xxl_admin_proxy_ready=false
    local xxl_executor_ready=false worker_ready=false manager_ready=false
    local manifest_runtime_version actual_runtime_version
    validate_tag "${tag}"
    [[ "$(docker inspect -f '{{.State.Running}}' "${BACKEND_CONTAINER_NAME}" 2>/dev/null || true)" == true ]]
    [[ "$(docker inspect -f '{{.State.Running}}' "${FRONTEND_CONTAINER_NAME}" 2>/dev/null || true)" == true ]]
    [[ "$(docker inspect -f '{{.State.Running}}' "${WORKER_CONTAINER_NAME}" 2>/dev/null || true)" == true ]]
    for attempt in $(seq 1 120); do
        if curl -fsS --connect-timeout 2 --max-time 5 \
            "${BACKEND_BASE_URL}/actuator/health/readiness" 2>/dev/null \
            | grep -q '"status":"UP"'; then
            backend_ready=true
            break
        fi
        [[ "$(docker inspect -f '{{.State.Running}}' "${BACKEND_CONTAINER_NAME}" 2>/dev/null || true)" == true ]] || break
        sleep 2
    done
    [[ "${backend_ready}" == true ]] || {
        echo "Backend did not become ready within the deployment window." >&2
        if [[ "${ISOLATED_ACCEPTANCE}" == true ]]; then
            docker inspect -f 'Isolated Redis state={{.State.Status}} exit={{.State.ExitCode}} ports={{json .NetworkSettings.Ports}}' \
                "${ISOLATED_REDIS_CONTAINER_NAME}" >&2 || true
            docker logs --tail 100 "${ISOLATED_REDIS_CONTAINER_NAME}" >&2 || true
        fi
        docker logs --tail 300 "${BACKEND_CONTAINER_NAME}" >&2 || true
        return 1
    }
    for attempt in $(seq 1 30); do
        if [[ "$(curl -sS --connect-timeout 2 --max-time 5 -o /dev/null -w '%{http_code}' "${FRONTEND_URL}/" 2>/dev/null || true)" == 200 ]]; then
            frontend_ready=true
            break
        fi
        [[ "$(docker inspect -f '{{.State.Running}}' "${FRONTEND_CONTAINER_NAME}" 2>/dev/null || true)" == true ]] || break
        sleep 2
    done
    [[ "${frontend_ready}" == true ]] || {
        echo "Frontend did not become ready within the deployment window." >&2
        return 1
    }
    if [[ "${ISOLATED_ACCEPTANCE}" == true ]]; then
        # 前端构建与后端端口都可能健康，但克隆的旧 CORS 白名单仍会让浏览器登录失败。
        curl -sSi --connect-timeout 2 --max-time 5 -X OPTIONS \
            "${BACKEND_BASE_URL}/api/auth/login" \
            -H "Origin: ${FRONTEND_URL}" \
            -H 'Access-Control-Request-Method: POST' \
            -H 'Access-Control-Request-Headers: content-type' \
            | grep -Fqi "Access-Control-Allow-Origin: ${FRONTEND_URL}" || {
                echo "Isolated V2 frontend origin is not allowed by backend CORS." >&2
                return 1
            }
    fi
    # 独立 V2 验收不启动 XXL，避免与 release 的调度实例共用 MySQL 和 executor。
    if [[ "${ISOLATED_ACCEPTANCE}" != true ]]; then
    # 主上下文 readiness 不包含独立 Servlet 子上下文；必须单独等待 XXL Admin，防止端口冲突被误报为发布成功。
    for attempt in $(seq 1 120); do
        if curl -fsS --connect-timeout 2 --max-time 5 \
            "http://${RUNTIME_SERVICE_HOST}:${XXL_JOB_ADMIN_PORT}/xxl-job-admin/actuator/health/readiness" 2>/dev/null \
            | grep -q '"status":"UP"'; then
            xxl_admin_ready=true
            break
        fi
        [[ "$(docker inspect -f '{{.State.Running}}' "${BACKEND_CONTAINER_NAME}" 2>/dev/null || true)" == true ]] || break
        sleep 2
    done
    [[ "${xxl_admin_ready}" == true ]] || {
        echo "XXL Admin did not become ready within the deployment window." >&2
        return 1
    }
    if curl -fsS --connect-timeout 2 --max-time 5 \
        "${FRONTEND_URL}/xxl-job-admin/actuator/health/readiness" 2>/dev/null \
        | grep -q '"status":"UP"'; then
        xxl_admin_proxy_ready=true
    fi
    [[ "${xxl_admin_proxy_ready}" == true ]] || {
        echo "Frontend same-origin XXL Admin proxy is not ready." >&2
        return 1
    }
    # executor 没有无认证 HTTP health；以 TCP connect 验证公共生命周期已在 Admin readiness 后实际启动端口。
    for attempt in $(seq 1 120); do
        if python3 - "${RUNTIME_SERVICE_HOST}" "${XXL_JOB_EXECUTOR_PORT}" <<'PY'
import socket
import sys

host, port = sys.argv[1], int(sys.argv[2])
with socket.create_connection((host, port), timeout=2):
    pass
PY
        then
            xxl_executor_ready=true
            break
        fi
        [[ "$(docker inspect -f '{{.State.Running}}' "${BACKEND_CONTAINER_NAME}" 2>/dev/null || true)" == true ]] || break
        sleep 2
    done
    [[ "${xxl_executor_ready}" == true ]] || {
        echo "XXL executor did not open its configured port within the deployment window." >&2
        return 1
    }
    fi
    for attempt in $(seq 1 120); do
        if [[ "$(docker inspect -f '{{.State.Health.Status}}' "${WORKER_CONTAINER_NAME}" 2>/dev/null || true)" == healthy ]]; then
            worker_ready=true
        fi
        if grep -Fq 'event=manager_config_update status=applied' \
            <<<"$(docker logs --tail 500 "${WORKER_CONTAINER_NAME}" 2>&1)"; then
            manager_ready=true
        fi
        if [[ "${worker_ready}" == true && "${manager_ready}" == true ]]; then
            break
        fi
        [[ "$(docker inspect -f '{{.State.Running}}' "${WORKER_CONTAINER_NAME}" 2>/dev/null || true)" == true ]] || break
        sleep 2
    done
    [[ "${worker_ready}" == true ]] || {
        echo "OpenCode worker did not become healthy within the deployment window." >&2
        return 1
    }
    [[ "${manager_ready}" == true ]] || {
        echo "OpenCode manager did not receive an applied runtime configuration from this server's backend." >&2
        return 1
    }
    manifest_runtime_version=$(python3 - "${RELEASE_ROOT}/${tag}/manifest.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    print(json.load(source).get("runtimeVersion", ""))
PY
    )
    if [[ -n "${manifest_runtime_version}" ]]; then
        actual_runtime_version=$(docker exec "${WORKER_CONTAINER_NAME}" \
            cat /usr/local/lib/opencode/VERSION 2>/dev/null || true)
        [[ "${actual_runtime_version}" == "${manifest_runtime_version}" ]] || {
            echo "OpenCode worker runtime version mismatch: expected=${manifest_runtime_version} actual=${actual_runtime_version:-missing}" >&2
            return 1
        }
    fi
}

deploy_release() {
    local release_dir=$1 tag=$2 current_link next_link source_db_name
    validate_manifest "${release_dir}" "${tag}"
    validate_host
    source_db_name=$(runtime_env_value TEST_AGENT_TEST_DB_NAME)
    validate_postgres_identifier "${source_db_name}" database
    capture_database_history "${release_dir}/pre-deploy-flyway-history.tsv" "${source_db_name}"
    if [[ -f "${release_dir}/worker-stack.json" ]]; then
        # worker 使用独立 Compose 项目，避免回滚旧的两容器应用清单时被 --remove-orphans 误删。
        # 先升级 manager，再替换 Java；manager 会在后端切换窗口内自动重连。
        docker compose --env-file "${ENV_FILE}" -p "${WORKER_PROJECT_NAME}" \
            -f "${release_dir}/worker-stack.json" up -d --force-recreate --remove-orphans
    elif [[ "$(docker inspect -f '{{.State.Running}}' "${WORKER_CONTAINER_NAME}" 2>/dev/null || true)" != true ]]; then
        echo "Legacy release does not contain a worker stack and no managed OpenCode worker is running." >&2
        return 1
    fi
    if [[ "${ISOLATED_ACCEPTANCE}" != true ]]; then
        sudo "${HOST_CONTROL}" stop-legacy
    fi
    if [[ "${ISOLATED_ACCEPTANCE}" == true ]]; then
        # 专用任务的上一次失败可能留下已创建但未启动的 Compose 容器；Docker 仍会为其保留
        # 26380 端口，下一次 up 会在绑定端口前失败。只清理本任务固定项目和容器，保留 Redis 卷。
        docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}" \
            -f "${release_dir}/stack.json" down --remove-orphans --timeout 30 >/dev/null 2>&1 || true
        for stale_container in "${BACKEND_CONTAINER_NAME}" "${FRONTEND_CONTAINER_NAME}" "${ISOLATED_REDIS_CONTAINER_NAME}"; do
            docker rm --force "${stale_container}" >/dev/null 2>&1 || true
        done
        cleanup_isolated_redis_port
    fi
    docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}" \
        -f "${release_dir}/stack.json" up -d --force-recreate --remove-orphans
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
    docker compose --env-file "${ENV_FILE}" -p "${PROJECT_NAME}" \
        -f "${RELEASE_ROOT}/current/stack.json" logs --no-color --tail 500 2>&1 \
        | sed -E 's#((PASSWORD|TOKEN|SECRET|AUTHORIZATION)[=:][[:space:]]*)[^[:space:]]+#\1***REDACTED***#Ig' \
        >"${output_dir}/containers.log" || true
    docker logs --tail 500 "${WORKER_CONTAINER_NAME}" 2>&1 \
        | sed -E 's#((PASSWORD|TOKEN|SECRET|AUTHORIZATION)[=:][[:space:]]*)[^[:space:]]+#\1***REDACTED***#Ig' \
        >"${output_dir}/opencode-worker.log" || true
    docker ps --filter "name=${BACKEND_CONTAINER_NAME}" --filter "name=${FRONTEND_CONTAINER_NAME}" \
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
    manifest-commit)
        [[ $# -eq 2 ]] || usage
        manifest_commit "$1" "$2"
        ;;
    build)
        [[ $# -eq 0 ]] || usage
        build_release
        ;;
    build-worker)
        [[ $# -eq 0 ]] || usage
        build_worker_image
        ;;
    prepare)
        [[ $# -eq 3 ]] || usage
        prepare_release "$1" "$2" "$3"
        ;;
    prepare-rollback)
        [[ $# -eq 3 ]] || usage
        prepare_rollback_release "$1" "$2" "$3"
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
    cleanup-worker-image-guard)
        [[ $# -eq 1 ]] || usage
        cleanup_worker_image_guard "$1"
        ;;
    record-result)
        [[ $# -eq 2 ]] || usage
        record_result "$1" "$2"
        ;;
    *)
        usage
        ;;
esac
