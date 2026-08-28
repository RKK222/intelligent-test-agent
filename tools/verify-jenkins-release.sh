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
grep -Fq 'NODE_IMAGE=${NODE_IMAGE:-node:22.16.0-bookworm}' "${release_script}"
grep -Fq 'JAVA_RUNTIME_IMAGE=${JAVA_RUNTIME_IMAGE:-maven:3.9.9-eclipse-temurin-21}' "${release_script}"
grep -Fq "command -v git >/dev/null" "${release_script}"
grep -Fq 'validate_backend_jar "${release_dir}/backend.jar"' "${release_script}"
grep -Fq 'mkdir -p "${release_dir}/source/backend/logs" "${release_dir}/source/temp"' "${release_script}"
grep -Fq 'printf -v cleanup_trap' "${release_script}"
grep -Fq 'source_db_name=$(runtime_env_value TEST_AGENT_TEST_DB_NAME)' "${release_script}"
grep -Fq 'pg_restore --exit-on-error --no-owner --no-privileges --role="$2"' "${release_script}"
grep -Fq -- '--env SERVER_ADDRESS=0.0.0.0' "${release_script}"
grep -Fq -- '--workspace-dir "${verify_root}/data/agent-opencode/workspace/experience"' "${release_script}"
grep -Fq -- '--env HOME=/verify/data' "${release_script}"
grep -Fq '"HOME": "/release/source/temp"' "${release_script}"
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
