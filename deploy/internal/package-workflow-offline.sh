#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${SCRIPT_DIR}/workflow/build.env"
OUTPUT_DIR="${SCRIPT_DIR}/dist"
PLATFORM="linux/amd64"
VALIDATE_ONLY=0

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-workflow-offline.sh [options]

Build the Python workflow-service, Runner controller and Debian 11 analysis task
as fixed linux/amd64 Docker artifacts. The package contains image tar files,
SPDX SBOMs, dependency locks, license inventories, source/configuration inputs,
Docker image metadata and SHA-256 manifests. Docker Compose is not used.

Options:
  --env-file <path>    Non-secret build dotenv. Default: deploy/internal/workflow/build.env.
  --output-dir <path>  Output parent. Default: deploy/internal/dist.
  --platform <value>   Must be linux/amd64.
  --validate-only      Validate all static build inputs without Docker writes.
  -h, --help           Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    --platform)
      PLATFORM="$2"
      shift 2
      ;;
    --validate-only)
      VALIDATE_ONLY=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

load_dotenv() {
  local file="$1" line key value duplicates
  [[ -f "${file}" ]] || {
    echo "Build env file not found: ${file}" >&2
    exit 1
  }
  duplicates="$(awk '
    {
      line = $0
      sub(/\r$/, "", line)
      sub(/^[[:space:]]+/, "", line)
      if (line == "" || line ~ /^#/) next
      sub(/^export[[:space:]]+/, "", line)
      separator = index(line, "=")
      if (!separator) next
      key = substr(line, 1, separator - 1)
      gsub(/[[:space:]]/, "", key)
      if (key ~ /^[A-Za-z_][A-Za-z0-9_]*$/ && ++seen[key] == 2) print key
    }
  ' "${file}")"
  [[ -z "${duplicates}" ]] || {
    echo "Duplicate build dotenv key(s): ${duplicates//$'\n'/, }" >&2
    exit 1
  }
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == export\ * ]] && line="${line#export }"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    [[ "${key}" == TEST_AGENT_* ]] || {
      echo "Unsupported build dotenv key: ${key}" >&2
      exit 1
    }
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    # 显式env文件是可复现构建的唯一输入，禁止调用shell中的同名变量悄悄覆盖。
    printf -v "${key}" '%s' "${value}"
    export -n "${key}" 2>/dev/null || true
  done <"${file}"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

require_digest_image() {
  local name="$1" value="$2"
  [[ "${value}" =~ ^[A-Za-z0-9._:/-]+@sha256:[0-9a-f]{64}$ ]] || {
    echo "${name} must be pinned by a lowercase sha256 digest: ${value}" >&2
    exit 1
  }
}

safe_tag() {
  local name="$1" value="$2"
  [[ "${value}" =~ ^[a-z0-9][a-z0-9._/-]{1,120}$ ]] || {
    echo "${name} is not a safe Docker repository name: ${value}" >&2
    exit 1
  }
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

load_dotenv "${ENV_FILE}"

VERSION="${TEST_AGENT_WORKFLOW_RELEASE_VERSION:-}"
PYTHON_IMAGE="${TEST_AGENT_WORKFLOW_PYTHON_IMAGE:-}"
UV_IMAGE="${TEST_AGENT_WORKFLOW_UV_IMAGE:-}"
SYFT_IMAGE="${TEST_AGENT_WORKFLOW_SYFT_IMAGE:-}"
TOOLS_IMAGE="${TEST_AGENT_ANALYSIS_TOOLS_IMAGE:-}"
TOOLS_IMAGE_ID="${TEST_AGENT_ANALYSIS_TOOLS_IMAGE_ID:-}"
WORKFLOW_REPOSITORY="${TEST_AGENT_WORKFLOW_SERVICE_TAG:-test-agent-workflow-service}"
RUNNER_REPOSITORY="${TEST_AGENT_RUNNER_CONTROLLER_TAG:-test-agent-runner-controller}"
ANALYSIS_REPOSITORY="${TEST_AGENT_ANALYSIS_TASK_TAG:-test-agent-analysis-task}"

[[ "${PLATFORM}" == "linux/amd64" ]] || {
  echo "Workflow offline artifacts must be built for linux/amd64" >&2
  exit 1
}
[[ "${VERSION}" =~ ^V[0-9]{8}\.[0-9]{6}$ ]] || {
  echo "TEST_AGENT_WORKFLOW_RELEASE_VERSION must match VyyyyMMdd.HHmmss" >&2
  exit 1
}
require_digest_image TEST_AGENT_WORKFLOW_PYTHON_IMAGE "${PYTHON_IMAGE}"
require_digest_image TEST_AGENT_WORKFLOW_UV_IMAGE "${UV_IMAGE}"
require_digest_image TEST_AGENT_WORKFLOW_SYFT_IMAGE "${SYFT_IMAGE}"
[[ "${TOOLS_IMAGE}" =~ ^[A-Za-z0-9._:/-]+:[A-Za-z0-9._-]+$ ]] || {
  echo "TEST_AGENT_ANALYSIS_TOOLS_IMAGE must use an explicit non-latest local tag" >&2
  exit 1
}
[[ "${TOOLS_IMAGE}" != *:latest ]] || {
  echo "TEST_AGENT_ANALYSIS_TOOLS_IMAGE cannot use latest" >&2
  exit 1
}
[[ "${TOOLS_IMAGE_ID}" =~ ^sha256:[0-9a-f]{64}$ ]] || {
  echo "TEST_AGENT_ANALYSIS_TOOLS_IMAGE_ID must be a full Docker image ID" >&2
  exit 1
}
safe_tag TEST_AGENT_WORKFLOW_SERVICE_TAG "${WORKFLOW_REPOSITORY}"
safe_tag TEST_AGENT_RUNNER_CONTROLLER_TAG "${RUNNER_REPOSITORY}"
safe_tag TEST_AGENT_ANALYSIS_TASK_TAG "${ANALYSIS_REPOSITORY}"

for required in \
  workflow-service/Dockerfile workflow-service/pyproject.toml workflow-service/uv.lock \
  runner-controller/Dockerfile runner-controller/pyproject.toml runner-controller/uv.lock \
  analysis-task/Dockerfile analysis-task/test-agent-analysis.py \
  analysis-task/test-agent-model-relay.py analysis-task/test-agent-clean-output.py \
  analysis-task/test-agent-safe-shell; do
  [[ -f "${ROOT_DIR}/${required}" ]] || {
    echo "Required workflow source is missing: ${required}" >&2
    exit 1
  }
done

if [[ "${VALIDATE_ONLY}" == "1" ]]; then
  echo "Workflow offline build inputs are valid for ${PLATFORM}."
  exit 0
fi

require_command docker
require_command tar

actual_tools_id="$(docker image inspect --format '{{.Id}}' "${TOOLS_IMAGE}")"
[[ "${actual_tools_id}" == "${TOOLS_IMAGE_ID}" ]] || {
  echo "Analysis tools image ID mismatch: expected ${TOOLS_IMAGE_ID}, got ${actual_tools_id}" >&2
  exit 1
}

STAGING_DIR="${OUTPUT_DIR%/}/workflow-${VERSION}"
ARCHIVE="${OUTPUT_DIR%/}/test-agent-workflow-offline-${VERSION}.tar.gz"
[[ ! -e "${STAGING_DIR}" && ! -e "${ARCHIVE}" ]] || {
  echo "Refusing to overwrite existing workflow release: ${VERSION}" >&2
  exit 1
}
mkdir -p "${STAGING_DIR}/images" "${STAGING_DIR}/sbom" \
  "${STAGING_DIR}/licenses" "${STAGING_DIR}/source" "${STAGING_DIR}/deploy" \
  "${STAGING_DIR}/docs"

revision="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
created="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
workflow_image="${WORKFLOW_REPOSITORY}:${VERSION}"
runner_image="${RUNNER_REPOSITORY}:${VERSION}"
analysis_image="${ANALYSIS_REPOSITORY}:${VERSION}"

common_labels=(
  --label "org.opencontainers.image.created=${created}"
  --label "org.opencontainers.image.revision=${revision}"
  --label "org.opencontainers.image.version=${VERSION}"
  --label "org.opencontainers.image.source=test-agent"
)

docker buildx build --platform "${PLATFORM}" --load --provenance=false \
  "${common_labels[@]}" \
  --build-arg "PYTHON_IMAGE=${PYTHON_IMAGE}" \
  --build-arg "UV_IMAGE=${UV_IMAGE}" \
  -t "${workflow_image}" "${ROOT_DIR}/workflow-service"

docker buildx build --platform "${PLATFORM}" --load --provenance=false \
  "${common_labels[@]}" \
  --build-arg "PYTHON_IMAGE=${PYTHON_IMAGE}" \
  --build-arg "UV_IMAGE=${UV_IMAGE}" \
  -t "${runner_image}" "${ROOT_DIR}/runner-controller"

docker buildx build --platform "${PLATFORM}" --load --provenance=false \
  "${common_labels[@]}" \
  --build-arg "TOOLS_IMAGE=${TOOLS_IMAGE}" \
  -t "${analysis_image}" "${ROOT_DIR}/analysis-task"

for image in "${workflow_image}" "${runner_image}" "${analysis_image}"; do
  architecture="$(docker image inspect --format '{{.Architecture}}' "${image}")"
  [[ "${architecture}" == "amd64" ]] || {
    echo "Image architecture mismatch for ${image}: ${architecture}" >&2
    exit 1
  }
done

analysis_user="$(docker image inspect --format '{{.Config.User}}' "${analysis_image}")"
[[ "${analysis_user}" == "10001:10003" ]] || {
  echo "Analysis image must run as uid 10001 with Runner shared output gid 10003" >&2
  exit 1
}
docker run --rm --platform "${PLATFORM}" --network none --read-only --cap-drop ALL \
  --security-opt no-new-privileges --entrypoint /bin/sh "${analysis_image}" -c \
  'test "$(getconf GNU_LIBC_VERSION)" = "glibc 2.31" && test -x /usr/local/lib/codex/bin/codex-official && test -x /usr/local/lib/opencode/bin/opencode-official && test -x /usr/local/bin/test-agent-analysis && test -x /usr/local/bin/test-agent-model-relay && test -x /usr/local/bin/test-agent-clean-output && test -x /usr/local/bin/test-agent-safe-shell && test "$(getent passwd 10002 | cut -d: -f3)" = "10002"'

workflow_id="$(docker image inspect --format '{{.Id}}' "${workflow_image}")"
runner_id="$(docker image inspect --format '{{.Id}}' "${runner_image}")"
analysis_id="$(docker image inspect --format '{{.Id}}' "${analysis_image}")"

docker save -o "${STAGING_DIR}/images/workflow-service.tar" "${workflow_image}"
docker save -o "${STAGING_DIR}/images/runner-controller.tar" "${runner_image}"
docker save -o "${STAGING_DIR}/images/analysis-task.tar" "${analysis_image}"

generate_sbom() {
  local image="$1" output="$2"
  if command -v syft >/dev/null 2>&1; then
    syft "${image}" -o "spdx-json=${output}"
    return
  fi
  # Syft 会先把 Docker daemon 中的完整镜像导出到 /tmp；当前 analysis 镜像已超过 1 GiB。
  docker run --rm --platform "${PLATFORM}" --network none --read-only --cap-drop ALL \
    --security-opt no-new-privileges \
    --env SYFT_CHECK_FOR_APP_UPDATE=false --env XDG_CACHE_HOME=/tmp/syft-cache \
    --tmpfs /tmp:rw,noexec,nosuid,nodev,size=2g \
    --volume /var/run/docker.sock:/var/run/docker.sock \
    "${SYFT_IMAGE}" "docker:${image}" -o spdx-json >"${output}"
}

generate_sbom "${workflow_image}" "${STAGING_DIR}/sbom/workflow-service.spdx.json"
generate_sbom "${runner_image}" "${STAGING_DIR}/sbom/runner-controller.spdx.json"
generate_sbom "${analysis_image}" "${STAGING_DIR}/sbom/analysis-task.spdx.json"

license_program='import json; from importlib import metadata; rows=[{"name":d.metadata.get("Name"),"version":d.version,"license":d.metadata.get("License") or "UNKNOWN"} for d in metadata.distributions() if d.metadata.get("Name")]; print(json.dumps(sorted(rows,key=lambda x:x["name"].lower()),ensure_ascii=False,indent=2))'
docker run --rm --platform "${PLATFORM}" --network none --read-only --cap-drop ALL \
  --security-opt no-new-privileges --tmpfs /tmp:rw,noexec,nosuid,nodev,size=16m \
  --entrypoint python "${workflow_image}" -c "${license_program}" \
  >"${STAGING_DIR}/licenses/workflow-service-python.json"
docker run --rm --platform "${PLATFORM}" --network none --read-only --cap-drop ALL \
  --security-opt no-new-privileges --tmpfs /tmp:rw,noexec,nosuid,nodev,size=16m \
  --entrypoint python "${runner_image}" -c "${license_program}" \
  >"${STAGING_DIR}/licenses/runner-controller-python.json"

printf '%s\n' \
  'Third-party package coordinates and declared licenses are recorded in licenses/*.json.' \
  'Complete package/file license evidence is recorded in sbom/*.spdx.json.' \
  'The immutable base image references and final image metadata are recorded in release.env and images/docker-image-inspect.json.' \
  >"${STAGING_DIR}/licenses/README.txt"

docker image inspect "${workflow_image}" "${runner_image}" "${analysis_image}" \
  >"${STAGING_DIR}/images/docker-image-inspect.json"

cp "${ROOT_DIR}/workflow-service/pyproject.toml" "${STAGING_DIR}/source/workflow-service.pyproject.toml"
cp "${ROOT_DIR}/workflow-service/uv.lock" "${STAGING_DIR}/source/workflow-service.uv.lock"
cp "${ROOT_DIR}/runner-controller/pyproject.toml" "${STAGING_DIR}/source/runner-controller.pyproject.toml"
cp "${ROOT_DIR}/runner-controller/uv.lock" "${STAGING_DIR}/source/runner-controller.uv.lock"
cp "${ROOT_DIR}/workflow-service/Dockerfile" "${STAGING_DIR}/source/workflow-service.Dockerfile"
cp "${ROOT_DIR}/runner-controller/Dockerfile" "${STAGING_DIR}/source/runner-controller.Dockerfile"
cp "${ROOT_DIR}/analysis-task/Dockerfile" "${STAGING_DIR}/source/analysis-task.Dockerfile"
cp "${ROOT_DIR}/analysis-task/test-agent-analysis.py" "${STAGING_DIR}/source/test-agent-analysis.py"
cp "${ROOT_DIR}/analysis-task/test-agent-model-relay.py" \
  "${STAGING_DIR}/source/test-agent-model-relay.py"
cp "${ROOT_DIR}/analysis-task/test-agent-clean-output.py" \
  "${STAGING_DIR}/source/test-agent-clean-output.py"
cp "${ROOT_DIR}/analysis-task/test-agent-safe-shell" \
  "${STAGING_DIR}/source/test-agent-safe-shell"
cp "${SCRIPT_DIR}/workflow/workflow.env.example" "${STAGING_DIR}/deploy/workflow.env.example"
cp "${SCRIPT_DIR}/workflow/java-capability.env.example" "${STAGING_DIR}/deploy/java-capability.env.example"
cp "${SCRIPT_DIR}/workflow/analysis-network.sh" "${STAGING_DIR}/deploy/analysis-network.sh"
cp "${SCRIPT_DIR}/workflow/workflow-docker.sh" "${STAGING_DIR}/deploy/workflow-docker.sh"
cp "${SCRIPT_DIR}/workflow/bootstrap-workflow.sql" "${STAGING_DIR}/deploy/bootstrap-workflow.sql"
cp "${SCRIPT_DIR}/workflow/redis-workflow-acl.sh" "${STAGING_DIR}/deploy/redis-workflow-acl.sh"
cp "${ROOT_DIR}/docs/deployment/workflow-offline.md" "${STAGING_DIR}/docs/workflow-offline.md"

cat >"${STAGING_DIR}/release.env" <<RELEASE
TEST_AGENT_WORKFLOW_RELEASE_VERSION=${VERSION}
TEST_AGENT_WORKFLOW_PLATFORM=${PLATFORM}
TEST_AGENT_WORKFLOW_SOURCE_REVISION=${revision}
TEST_AGENT_WORKFLOW_SERVICE_IMAGE=${workflow_image}
TEST_AGENT_WORKFLOW_SERVICE_IMAGE_ID=${workflow_id}
TEST_AGENT_RUNNER_CONTROLLER_IMAGE=${runner_image}
TEST_AGENT_RUNNER_CONTROLLER_IMAGE_ID=${runner_id}
TEST_AGENT_ANALYSIS_TASK_IMAGE=${analysis_image}
TEST_AGENT_ANALYSIS_TASK_IMAGE_ID=${analysis_id}
TEST_AGENT_ANALYSIS_TOOLS_IMAGE=${TOOLS_IMAGE}
TEST_AGENT_ANALYSIS_TOOLS_IMAGE_ID=${TOOLS_IMAGE_ID}
RELEASE

(
  cd "${STAGING_DIR}"
  find . -type f ! -name SHA256SUMS -print0 \
    | sort -z \
    | while IFS= read -r -d '' file; do
        digest="$(sha256_file "${file}")"
        printf '%s  %s\n' "${digest}" "${file#./}"
      done >SHA256SUMS
)

# macOS归档时禁止把com.apple.*扩展属性写成PAX header，避免Linux部署出现未知header告警。
COPYFILE_DISABLE=1 tar -czf "${ARCHIVE}" -C "${OUTPUT_DIR%/}" "workflow-${VERSION}"
sha256_file "${ARCHIVE}" >"${ARCHIVE}.sha256"
echo "Workflow offline release created: ${ARCHIVE}"
