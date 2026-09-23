#!/usr/bin/env bash
set -Eeuo pipefail

root_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
release_script="${root_dir}/deploy/local/jenkins-release.sh"
host_control="${root_dir}/deploy/local/jenkins-host-control.sh"
jenkinsfile="${root_dir}/Jenkinsfile"
sudoers_file="${root_dir}/deploy/local/jenkins-sudoers"

bash -n "${release_script}" "${host_control}"
"${release_script}" validate-tag release-1-deadbeef
if "${release_script}" validate-tag release-0-deadbeef >/dev/null 2>&1; then
    echo 'validate-tag accepted build number zero.' >&2
    exit 1
fi
if "${release_script}" validate-tag release-1-DEADBEEF >/dev/null 2>&1; then
    echo 'validate-tag accepted an uppercase commit prefix.' >&2
    exit 1
fi

grep -Fq "credentialsId: 'intelligent-test-agent-git-ssh'" "${jenkinsfile}"
grep -Fq "branches: [[name: '*/release']]" "${jenkinsfile}"
grep -Fq "disableConcurrentBuilds()" "${jenkinsfile}"
grep -Fq "verify-database-upgrade" "${jenkinsfile}"
grep -Fq "RUNTIME_DATA_SOURCE = '/data/.testagent'" "${jenkinsfile}"
grep -Fq "RUNTIME_DATA_ROOT = '/data/.testagent'" "${jenkinsfile}"
grep -Fq "RUNTIME_SERVICE_HOST = '192.168.8.100'" "${jenkinsfile}"
grep -Fq "XXL_JOB_ADMIN_PORT = '18083'" "${jenkinsfile}"
grep -Fq "XXL_JOB_EXECUTOR_PORT = '9999'" "${jenkinsfile}"
grep -Fq 'NODE_IMAGE=${NODE_IMAGE:-node:22.16.0-bookworm}' "${release_script}"
grep -Fq -- '--env VITE_ENV=localhost' "${release_script}"
grep -Fq 'JAVA_RUNTIME_IMAGE=${JAVA_RUNTIME_IMAGE:-maven:3.9.9-eclipse-temurin-21}' "${release_script}"
grep -Fq 'HOST_CHECK_TIMEOUT_SECONDS=${HOST_CHECK_TIMEOUT_SECONDS:-60}' "${release_script}"
grep -Fq 'timeout --foreground "${HOST_CHECK_TIMEOUT_SECONDS}s" docker info' "${release_script}"
grep -Fq 'timeout --foreground "${HOST_CHECK_TIMEOUT_SECONDS}s" sudo "${HOST_CONTROL}" status' "${release_script}"
grep -Fq 'RUNTIME_DATA_SOURCE=${RUNTIME_DATA_SOURCE:-/data/.testagent}' "${release_script}"
grep -Fq 'RUNTIME_DATA_ROOT=${RUNTIME_DATA_ROOT:-/data/.testagent}' "${release_script}"
grep -Fq 'RUNTIME_SERVICE_HOST=${RUNTIME_SERVICE_HOST:-192.168.8.100}' "${release_script}"
grep -Fq 'XXL_JOB_ADMIN_PORT=${XXL_JOB_ADMIN_PORT:-18083}' "${release_script}"
grep -Fq 'XXL_JOB_EXECUTOR_PORT=${XXL_JOB_EXECUTOR_PORT:-9999}' "${release_script}"
grep -Fq 'WORKER_PROJECT_NAME=${WORKER_PROJECT_NAME:-intelligent-test-agent-jenkins-opencode}' "${release_script}"
grep -Fq 'WORKER_CONTAINER_NAME=${WORKER_CONTAINER_NAME:-test-agent-jenkins-opencode-worker}' "${release_script}"
grep -Fq 'WORKER_PORT_START=${WORKER_PORT_START:-4096}' "${release_script}"
grep -Fq 'WORKER_PORT_END=${WORKER_PORT_END:-4105}' "${release_script}"
grep -Fq 'xxl_job_mysql_port=$(runtime_env_value TEST_AGENT_XXL_JOB_MYSQL_PORT)' "${release_script}"
grep -Fq 'xxl_job_mysql_database=$(runtime_env_value TEST_AGENT_XXL_JOB_MYSQL_DATABASE)' "${release_script}"
grep -Fq 'xxl_job_mysql_url="jdbc:mysql://${RUNTIME_SERVICE_HOST}:${xxl_job_mysql_port}/${xxl_job_mysql_database}?' "${release_script}"
grep -Fq "command -v git >/dev/null" "${release_script}"
grep -Fq 'configured_data_root=$(database_linux_data_root "${source_db_name}")' "${release_script}"
grep -Fq 'validate_backend_jar "${release_dir}/backend.jar"' "${release_script}"
grep -Fq 'validate_runtime_release_access "${release_dir}"' "${release_script}"
grep -Fq 'test -r /release/backend.jar' "${release_script}"
grep -Fq 'test -d /release/source/backend' "${release_script}"
grep -Fq 'chmod 0644 "${release_dir}/backend.jar" "${release_dir}/nginx.conf"' "${release_script}"
grep -Fq 'chmod -R u=rwX,go=rX "${release_dir}/source" "${release_dir}/frontend"' "${release_script}"
grep -Fq 'sha256sum -c source.sha256 --quiet' "${release_script}"
grep -Fq 'mkdir -p "${release_dir}/source/backend/logs" "${release_dir}/source/temp"' "${release_script}"
grep -Fq -- '--file "${repository_root}/deploy/internal/opencode-worker.Dockerfile"' "${release_script}"
grep -Fq -- '--build-arg "MANAGER_BUILD_VERSION=${build_version}"' "${release_script}"
grep -Fq "grep -Eq 'failed to stat active key during commit|snapshot .* does not exist'" "${release_script}"
grep -Fq 'docker build --no-cache' "${release_script}"
grep -Fq 'docker builder prune --all --force' "${release_script}"
grep -Fq 'worker_recovery_log' "${release_script}"
grep -Fq 'verify-opencode-node-worker-image.sh" "${worker_image}"' "${release_script}"
grep -Fq 'write_worker_stack "${release_dir}/worker-stack.json" "${worker_image}"' "${release_script}"
grep -Fq '"OPENCODE_MANAGER_TOKEN": "${TEST_AGENT_OPENCODE_MANAGER_TOKEN:?TEST_AGENT_OPENCODE_MANAGER_TOKEN is required}"' "${release_script}"
grep -Fq '"SYS_DATA_ROOT_DIR": runtime_data_root' "${release_script}"
grep -Fq '"OPENCODE_BIN": "/usr/local/bin/opencode"' "${release_script}"
grep -Fq '"volumes": [f"{runtime_data_source}:{runtime_data_root}:rw"]' "${release_script}"
grep -Fq '"workerImageId": worker_image_id' "${release_script}"
grep -Fq 'Legacy release does not contain a worker stack and no managed OpenCode worker is running.' "${release_script}"
grep -Fq 'event=manager_config_update status=applied' "${release_script}"
grep -Fq 'printf -v cleanup_trap' "${release_script}"
grep -Fq 'source_db_name=$(runtime_env_value TEST_AGENT_TEST_DB_NAME)' "${release_script}"
grep -Fq 'pg_restore --exit-on-error --no-owner --no-privileges --role="$2"' "${release_script}"
grep -Fq -- '--env SERVER_ADDRESS=0.0.0.0' "${release_script}"
grep -Fq -- '--workspace-dir "${verify_root}/data/agent-opencode/workspace/experience"' "${release_script}"
grep -Fq -- '--env "HOME=${RUNTIME_DATA_ROOT}"' "${release_script}"
grep -Fq -- '--volume "${verify_root}/data:${RUNTIME_DATA_ROOT}:rw"' "${release_script}"
grep -Fq '"HOME": "/release/source/temp"' "${release_script}"
grep -Fq '"TEST_AGENT_REDIS_HOST": runtime_service_host' "${release_script}"
grep -Fq '"TEST_AGENT_XXL_JOB_MYSQL_URL": xxl_job_mysql_url' "${release_script}"
grep -Fq '"TEST_AGENT_XXL_JOB_ADMIN_PORT": xxl_job_admin_port' "${release_script}"
grep -Fq '"TEST_AGENT_XXL_JOB_EXECUTOR_PORT": xxl_job_executor_port' "${release_script}"
grep -Fq '"SERVER_ADDRESS": "0.0.0.0"' "${release_script}"
grep -Fq '"TEST_AGENT_SERVER_ADVERTISED_HOST": runtime_service_host' "${release_script}"
grep -Fq 'proxy_pass http://${RUNTIME_SERVICE_HOST}:${BACKEND_PORT};' "${release_script}"
grep -Fq 'proxy_pass http://${RUNTIME_SERVICE_HOST}:${XXL_JOB_ADMIN_PORT};' "${release_script}"
grep -Fq 'http://${RUNTIME_SERVICE_HOST}:${XXL_JOB_ADMIN_PORT}/xxl-job-admin/actuator/health/readiness' \
    "${release_script}"
grep -Fq 'XXL Admin did not become ready within the deployment window.' "${release_script}"
grep -Fq 'Frontend same-origin XXL Admin proxy is not ready.' "${release_script}"
grep -Fq 'XXL executor did not open its configured port within the deployment window.' "${release_script}"
grep -Fq 'proxy_pass http://__RUNTIME_SERVICE_HOST__:__BACKEND_PORT__;' \
    "${root_dir}/deploy/local/jenkins-nginx.conf"
grep -Fq 'proxy_pass http://__RUNTIME_SERVICE_HOST__:__XXL_JOB_ADMIN_PORT__;' \
    "${root_dir}/deploy/local/jenkins-nginx.conf"
grep -Fq 'for attempt in $(seq 1 120)' "${release_script}"
grep -Fq 'Backend did not become ready within the deployment window.' "${release_script}"
if grep -Fq -- '--env SYS_DATA_ROOT_DIR=/verify/data' "${release_script}"; then
    echo 'Jenkins release attempts to override database-controlled SYS_DATA_ROOT_DIR with an environment variable.' >&2
    exit 1
fi
if grep -Fq 'TEST_AGENT_LINUX_SERVER_ID=jenkins-verify-' "${release_script}"; then
    echo 'Database verification uses a Linux server ID absent from the cloned database.' >&2
    exit 1
fi
if grep -Eq '^[[:space:]]*jar tf ' "${release_script}"; then
    echo 'Release script still requires the host jar command.' >&2
    exit 1
fi
grep -Fq 'sudo "${HOST_CONTROL}" stop-legacy' "${release_script}"
grep -Fxq 'jenkins ALL=(root) NOPASSWD: /usr/local/sbin/test-agent-jenkins-host-control status' "${sudoers_file}"
grep -Fxq 'jenkins ALL=(root) NOPASSWD: /usr/local/sbin/test-agent-jenkins-host-control stop-legacy' "${sudoers_file}"

echo 'Jenkins release contract verification passed.'
