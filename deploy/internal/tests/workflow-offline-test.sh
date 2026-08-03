#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INTERNAL_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd "${INTERNAL_DIR}/../.." && pwd)"
FIXTURE_DIR="$(mktemp -d)"
OUTPUT_DIR="$(mktemp -d)"
trap 'rm -rf "${FIXTURE_DIR}" "${OUTPUT_DIR}"' EXIT

assert_unique_env_keys() {
  local file="$1"
  if ! awk -F= '
    /^[A-Z0-9_]+=/ { if (seen[$1]++) { print $1 > "/dev/stderr"; duplicate = 1 } }
    END { exit duplicate }
  ' "${file}"; then
    echo "duplicate dotenv key: ${file}" >&2
    exit 1
  fi
}

assert_unique_env_keys "${INTERNAL_DIR}/workflow/build.env.example"
assert_unique_env_keys "${INTERNAL_DIR}/workflow/workflow.env.example"
assert_unique_env_keys "${INTERNAL_DIR}/workflow/java-capability.env.example"
assert_unique_env_keys "${INTERNAL_DIR}/backend.env.example"
grep -Fq 'TEST_AGENT_WORKFLOW_CAPABILITY_HMAC_SECRET=' "${INTERNAL_DIR}/backend.env.example"
grep -Fq 'TEST_AGENT_WORKFLOW_RUNNER_PLATFORM_HMAC_SECRET=' "${INTERNAL_DIR}/backend.env.example"
grep -Fq 'TEST_AGENT_WORKFLOW_MODEL_GATEWAY_URL=' "${INTERNAL_DIR}/backend.env.example"

duplicate_env="${FIXTURE_DIR}/duplicate.env"
cp "${INTERNAL_DIR}/workflow/build.env.example" "${duplicate_env}"
printf 'TEST_AGENT_WORKFLOW_RELEASE_VERSION=V20260731.999999\n' >>"${duplicate_env}"
if "${INTERNAL_DIR}/package-workflow-offline.sh" \
  --env-file "${duplicate_env}" --validate-only >/dev/null 2>&1; then
  echo "package-workflow-offline unexpectedly accepted a duplicate dotenv key" >&2
  exit 1
fi

mkdir -p "${FIXTURE_DIR}/bin"
cp "${SCRIPT_DIR}/fixtures/workflow-fake-docker.sh" "${FIXTURE_DIR}/bin/docker"
chmod 0755 "${FIXTURE_DIR}/bin/docker"
sed \
  -e 's/TEST_AGENT_WORKFLOW_RELEASE_VERSION=.*/TEST_AGENT_WORKFLOW_RELEASE_VERSION=V20260731.120000/' \
  -e 's/TEST_AGENT_ANALYSIS_TOOLS_IMAGE_ID=.*/TEST_AGENT_ANALYSIS_TOOLS_IMAGE_ID=sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/' \
  "${INTERNAL_DIR}/workflow/build.env.example" >"${FIXTURE_DIR}/build.env"

PATH="${FIXTURE_DIR}/bin:${PATH}" \
  "${INTERNAL_DIR}/package-workflow-offline.sh" \
  --env-file "${FIXTURE_DIR}/build.env" \
  --output-dir "${OUTPUT_DIR}" >/dev/null

archive="${OUTPUT_DIR}/test-agent-workflow-offline-V20260731.120000.tar.gz"
test -s "${archive}"
test -s "${archive}.sha256"
tar -xzf "${archive}" -C "${FIXTURE_DIR}"
release_root="${FIXTURE_DIR}/workflow-V20260731.120000"

for expected in \
  images/workflow-service.tar images/runner-controller.tar images/analysis-task.tar \
  sbom/workflow-service.spdx.json sbom/runner-controller.spdx.json sbom/analysis-task.spdx.json \
  licenses/workflow-service-python.json licenses/runner-controller-python.json licenses/README.txt \
  source/workflow-service.uv.lock source/runner-controller.uv.lock \
  source/test-agent-analysis.py source/test-agent-model-relay.py \
  source/test-agent-clean-output.py source/test-agent-safe-shell \
  deploy/workflow-docker.sh deploy/analysis-network.sh deploy/bootstrap-workflow.sql \
  deploy/redis-workflow-acl.sh deploy/java-capability.env.example \
  docs/workflow-offline.md release.env SHA256SUMS; do
  test -s "${release_root}/${expected}"
done

(
  cd "${release_root}"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c SHA256SUMS >/dev/null
  else
    shasum -a 256 -c SHA256SUMS >/dev/null
  fi
)
grep -Fq 'TEST_AGENT_WORKFLOW_PLATFORM=linux/amd64' "${release_root}/release.env"
grep -Fq 'TEST_AGENT_RUNNER_KNOWN_HOSTS_PATH' "${release_root}/deploy/workflow.env.example"
grep -Fq 'TEST_AGENT_WORKFLOW_ANALYSIS_MODEL_NAME=workflow-code-analysis' \
  "${release_root}/deploy/workflow.env.example"
grep -Fq 'TEST_AGENT_WORKFLOW_SERVER_MODEL_GATEWAY_URL=' \
  "${release_root}/deploy/workflow.env.example"
grep -Fq '10001:10003' "${release_root}/source/analysis-task.Dockerfile"
grep -Fq '10002' "${release_root}/source/analysis-task.Dockerfile"
if grep -Fq 'DOCKER_API_VERSION=' "${release_root}/source/runner-controller.Dockerfile"; then
  echo "Runner image unexpectedly disables Docker client/server API negotiation" >&2
  exit 1
fi
grep -Fq '127.0.0.1' "${release_root}/source/test-agent-model-relay.py"
grep -Fq 'OUTPUT_ROOT = Path("/workspace/output")' \
  "${release_root}/source/test-agent-clean-output.py"
assert_unique_env_keys "${release_root}/deploy/workflow.env.example"
grep -Fq 'CONTROL_ENV_FILE' "${release_root}/deploy/workflow-docker.sh"
grep -Fq 'RUNNER_ENV_FILE' "${release_root}/deploy/workflow-docker.sh"
grep -Fq 'chown 10003:10003 "${RUNNER_ROOT}"' "${release_root}/deploy/workflow-docker.sh"
grep -Fq -- '--name test-agent-workflow-runner --restart no' \
  "${release_root}/deploy/workflow-docker.sh"
grep -Fq 'Restoring workflow analysis egress policy before Runner startup.' \
  "${release_root}/deploy/workflow-docker.sh"
if grep -F -- '--name test-agent-workflow-runner --restart unless-stopped' \
  "${release_root}/deploy/workflow-docker.sh" >/dev/null; then
  echo "Runner unexpectedly auto-starts before volatile firewall policy restoration" >&2
  exit 1
fi
if grep -Fq 'install -d -m 0700 -o 10003 -g 10003' "${release_root}/deploy/workflow-docker.sh"; then
  echo "workflow deploy unexpectedly requires a host passwd entry for uid 10003" >&2
  exit 1
fi
grep -Fq -- '--tmpfs /tmp:rw,noexec,nosuid,nodev,size=2g' \
  "${INTERNAL_DIR}/package-workflow-offline.sh"
grep -Fq 'COPYFILE_DISABLE=1 tar -czf' "${INTERNAL_DIR}/package-workflow-offline.sh"
if grep -Fq '# syntax=' \
  "${ROOT_DIR}/workflow-service/Dockerfile" \
  "${ROOT_DIR}/runner-controller/Dockerfile" \
  "${ROOT_DIR}/analysis-task/Dockerfile"; then
  echo "Workflow offline images unexpectedly require a remote Dockerfile frontend" >&2
  exit 1
fi
if grep -Eq 'apt-get|apk add|dnf install|yum install' \
  "${ROOT_DIR}/analysis-task/Dockerfile"; then
  echo "Analysis image unexpectedly downloads OS packages during offline build" >&2
  exit 1
fi
if grep -F 'TEST_AGENT_WORKFLOW_MIGRATION_DATABASE_URL' \
  "${release_root}/deploy/workflow-docker.sh" | grep -Fq 'write_env_subset'; then
  echo "migration owner URL unexpectedly entered a long-running scoped env" >&2
  exit 1
fi

echo "Workflow offline packaging contract test passed"
