#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd -P)"
ENV_FILE="${SCRIPT_DIR}/memory/build.env.example"
OUTPUT_DIR="${SCRIPT_DIR}/dist"
PLATFORM="linux/amd64"
VALIDATE_ONLY=0

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-memory-offline.sh [options]

Build the stateless Mem0 service, fixed CPU BGE service, pgvector and memory
VIP Nginx as docker-loadable linux/amd64 artifacts. The output also contains
SHA-256, SPDX SBOMs, source locks, licenses, model identity and Alembic files.

Options:
  --env-file <path>    Non-secret build dotenv. Default: memory/build.env.example.
  --output-dir <path>  Output parent; artifacts are written to <path>/memory.
  --platform <value>   Must be linux/amd64.
  --validate-only      Validate immutable inputs without building or writing artifacts.
  -h, --help           Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --platform) PLATFORM="$2"; shift 2 ;;
    --validate-only) VALIDATE_ONLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

load_build_env() {
  local file="$1" line key value duplicates
  [[ -f "${file}" ]] || { echo "Memory build env not found: ${file}" >&2; exit 1; }
  duplicates="$(awk '
    { line=$0; sub(/\r$/, "", line); sub(/^[[:space:]]+/, "", line) }
    line == "" || line ~ /^#/ { next }
    { sub(/^export[[:space:]]+/, "", line); separator=index(line, "=") }
    separator > 0 {
      key=substr(line, 1, separator - 1); gsub(/[[:space:]]/, "", key)
      if (key ~ /^[A-Za-z_][A-Za-z0-9_]*$/ && ++seen[key] == 2) print key
    }
  ' "${file}")"
  [[ -z "${duplicates}" ]] || {
    echo "Duplicate memory build key(s): ${duplicates//$'\n'/, }" >&2
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
    [[ "${key}" =~ ^TEST_AGENT_[A-Z0-9_]+$ ]] || {
      echo "Unsupported memory build key: ${key}" >&2
      exit 1
    }
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf -v "${key}" '%s' "${value}"
  done <"${file}"
}

require_digest_image() {
  local name="$1" value="$2"
  [[ "${value}" =~ ^[A-Za-z0-9._:/-]+(@sha256:[0-9a-f]{64})$ ]] || {
    echo "${name} must be pinned by a lowercase sha256 digest" >&2
    exit 1
  }
}

require_runtime_tag() {
  local name="$1" value="$2"
  [[ "${value}" =~ ^[a-z0-9][a-z0-9._/-]+:[A-Za-z0-9][A-Za-z0-9._-]+$ ]] \
    && [[ "${value}" != *:latest ]] || {
      echo "${name} must use an explicit non-latest local tag" >&2
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

copy_python_tree() {
  local source="$1" target="$2"
  # 企业交付源码只保留可审计输入；本机缓存可能包含已经删除模块的陈旧字节码。
  rsync -a \
    --exclude='__pycache__/' \
    --exclude='*.pyc' \
    --exclude='*.pyo' \
    --exclude='.pytest_cache/' \
    --exclude='.mypy_cache/' \
    --exclude='.ruff_cache/' \
    "${source%/}/" "${target%/}/"
}

load_build_env "${ENV_FILE}"

VERSION="${TEST_AGENT_MEMORY_RELEASE_VERSION:-}"
PYTHON_IMAGE="${TEST_AGENT_MEMORY_PYTHON_IMAGE:-}"
UV_IMAGE="${TEST_AGENT_MEMORY_UV_IMAGE:-}"
SYFT_IMAGE="${TEST_AGENT_MEMORY_SYFT_IMAGE:-}"
PGVECTOR_SOURCE_IMAGE="${TEST_AGENT_MEMORY_PGVECTOR_SOURCE_IMAGE:-}"
NGINX_SOURCE_IMAGE="${TEST_AGENT_MEMORY_NGINX_SOURCE_IMAGE:-}"
MEMORY_IMAGE="${TEST_AGENT_MEMORY_SERVICE_IMAGE:-}"
EMBEDDING_IMAGE="${TEST_AGENT_EMBEDDING_SERVICE_IMAGE:-}"
PGVECTOR_IMAGE="${TEST_AGENT_MEMORY_PGVECTOR_IMAGE:-}"
NGINX_IMAGE="${TEST_AGENT_MEMORY_NGINX_IMAGE:-}"

[[ "${PLATFORM}" == linux/amd64 ]] || {
  echo "Memory offline artifacts must be built for linux/amd64" >&2
  exit 1
}
[[ "${VERSION}" =~ ^V[0-9]{8}\.[0-9]{6}$ ]] || {
  echo "TEST_AGENT_MEMORY_RELEASE_VERSION must match VyyyyMMdd.HHmmss" >&2
  exit 1
}
for pair in \
  "TEST_AGENT_MEMORY_PYTHON_IMAGE:${PYTHON_IMAGE}" \
  "TEST_AGENT_MEMORY_UV_IMAGE:${UV_IMAGE}" \
  "TEST_AGENT_MEMORY_SYFT_IMAGE:${SYFT_IMAGE}" \
  "TEST_AGENT_MEMORY_PGVECTOR_SOURCE_IMAGE:${PGVECTOR_SOURCE_IMAGE}" \
  "TEST_AGENT_MEMORY_NGINX_SOURCE_IMAGE:${NGINX_SOURCE_IMAGE}"; do
  require_digest_image "${pair%%:*}" "${pair#*:}"
done
for pair in \
  "TEST_AGENT_MEMORY_SERVICE_IMAGE:${MEMORY_IMAGE}" \
  "TEST_AGENT_EMBEDDING_SERVICE_IMAGE:${EMBEDDING_IMAGE}" \
  "TEST_AGENT_MEMORY_PGVECTOR_IMAGE:${PGVECTOR_IMAGE}" \
  "TEST_AGENT_MEMORY_NGINX_IMAGE:${NGINX_IMAGE}"; do
  require_runtime_tag "${pair%%:*}" "${pair#*:}"
done

for required in \
  memory-service/Dockerfile memory-service/pyproject.toml memory-service/uv.lock \
  memory-service/alembic.ini memory-service/alembic/env.py \
  memory-service/alembic/versions/20260809_01_shared_memory_control.py \
  embedding-service/Dockerfile embedding-service/pyproject.toml embedding-service/uv.lock \
  embedding-service/scripts/download_model.py deploy/internal/memory/LICENSES.txt \
  deploy/internal/memory/PinnedImage.Dockerfile \
  deploy/internal/memory.env.example deploy/internal/embedding.env.example \
  deploy/internal/memory-docker.sh docs/deployment/qa-memory.md; do
  [[ -f "${ROOT_DIR}/${required}" ]] || {
    echo "Required memory release input is missing: ${required}" >&2
    exit 1
  }
done
grep -Fq 'mem0ai==2.0.17' "${ROOT_DIR}/memory-service/pyproject.toml"
grep -Fq '7999e1d3359715c523056ef9478215996d62a620' \
  "${ROOT_DIR}/embedding-service/scripts/download_model.py"
grep -Fq 'DIMENSION = 512' \
  "${ROOT_DIR}/embedding-service/src/testagent_embedding_service/settings.py"

if [[ "${VALIDATE_ONLY}" -eq 1 ]]; then
  echo "Memory offline build inputs are valid for ${PLATFORM}."
  exit 0
fi

for command_name in docker git rsync python3; do require_command "${command_name}"; done
docker buildx version >/dev/null

TARGET_DIR="${OUTPUT_DIR%/}/memory"
[[ "${TARGET_DIR}" != / && "${TARGET_DIR}" != "${OUTPUT_DIR%/}" ]] || {
  echo "Unsafe memory artifact target: ${TARGET_DIR}" >&2
  exit 1
}
[[ "${OUTPUT_DIR%/}" != / && -n "${OUTPUT_DIR%/}" ]] || {
  echo "Output directory must not be the filesystem root" >&2
  exit 1
}
mkdir -p "${OUTPUT_DIR}"
OUTPUT_DIR="$(cd "${OUTPUT_DIR}" && pwd -P)"
TARGET_DIR="${OUTPUT_DIR}/memory"
STAGING_DIR="$(mktemp -d "${OUTPUT_DIR%/}/.memory-release.XXXXXX")"
cleanup() { rm -rf "${STAGING_DIR}"; }
trap cleanup EXIT
mkdir -p "${STAGING_DIR}/images" "${STAGING_DIR}/sbom" \
  "${STAGING_DIR}/source/memory-service" "${STAGING_DIR}/source/embedding-service" \
  "${STAGING_DIR}/alembic"

revision="$(git -C "${ROOT_DIR}" rev-parse HEAD)"
created="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
labels=(
  --label "org.opencontainers.image.created=${created}"
  --label "org.opencontainers.image.revision=${revision}"
  --label "org.opencontainers.image.version=${VERSION}"
  --label "org.opencontainers.image.source=test-agent"
)

docker buildx build --platform "${PLATFORM}" --load --provenance=false \
  "${labels[@]}" --build-arg "PYTHON_IMAGE=${PYTHON_IMAGE}" \
  --build-arg "UV_IMAGE=${UV_IMAGE}" -t "${MEMORY_IMAGE}" \
  "${ROOT_DIR}/memory-service"
docker buildx build --platform "${PLATFORM}" --load --provenance=false \
  "${labels[@]}" --build-arg "PYTHON_IMAGE=${PYTHON_IMAGE}" \
  --build-arg "UV_IMAGE=${UV_IMAGE}" -t "${EMBEDDING_IMAGE}" \
  "${ROOT_DIR}/embedding-service"
docker pull "${SYFT_IMAGE}"

memory_tar="test-agent-memory-service_internal-linux-amd64.tar"
embedding_tar="test-agent-embedding-bge-small-zh-v1.5_internal-linux-amd64.tar"
pgvector_tar="test-agent-pgvector_0.8.1-pg16_internal-linux-amd64.tar"
nginx_tar="test-agent-memory-nginx_1.27.2_internal-linux-amd64.tar"
declare -a refs=("${MEMORY_IMAGE}" "${EMBEDDING_IMAGE}" "${PGVECTOR_IMAGE}" "${NGINX_IMAGE}")
declare -a tars=("${memory_tar}" "${embedding_tar}" "${pgvector_tar}" "${nginx_tar}")
declare -a names=(memory-service embedding-service pgvector memory-nginx)

# 两个自研镜像需要在本机创建容器核对模型身份，因此先 --load 再导出。
for index in 0 1; do
  architecture="$(docker image inspect --format '{{.Architecture}}' "${refs[$index]}")"
  [[ "${architecture}" == amd64 ]] || {
    echo "Image architecture mismatch for ${refs[$index]}: ${architecture}" >&2
    exit 1
  }
  docker save -o "${STAGING_DIR}/images/${tars[$index]}" "${refs[$index]}"
done

# Mac Docker Desktop 不能在本地 image store 中同时保存同一 manifest-list digest 的
# arm64 与 amd64 变体。基础镜像直接由 buildx 生成 docker archive，避免已有开发镜像
# 导致 `cannot overwrite digest`，同时仍把发布 tar 固定为 linux/amd64。
docker buildx build --platform "${PLATFORM}" --provenance=false "${labels[@]}" \
  --build-arg "SOURCE_IMAGE=${PGVECTOR_SOURCE_IMAGE}" -t "${PGVECTOR_IMAGE}" \
  --output "type=docker,dest=${STAGING_DIR}/images/${pgvector_tar}" \
  -f "${ROOT_DIR}/deploy/internal/memory/PinnedImage.Dockerfile" \
  "${ROOT_DIR}/deploy/internal/memory"
docker buildx build --platform "${PLATFORM}" --provenance=false "${labels[@]}" \
  --build-arg "SOURCE_IMAGE=${NGINX_SOURCE_IMAGE}" -t "${NGINX_IMAGE}" \
  --output "type=docker,dest=${STAGING_DIR}/images/${nginx_tar}" \
  -f "${ROOT_DIR}/deploy/internal/memory/PinnedImage.Dockerfile" \
  "${ROOT_DIR}/deploy/internal/memory"

for index in "${!refs[@]}"; do
  # 从 docker archive 的 config 核对实际 OS/架构；不能相信文件名或本机 tag。
  python3 - "${STAGING_DIR}/images/${tars[$index]}" \
    "${STAGING_DIR}/images/${names[$index]}.image.json" "${refs[$index]}" <<'PY'
import json
import pathlib
import sys
import tarfile

archive = pathlib.Path(sys.argv[1])
report = pathlib.Path(sys.argv[2])
expected_tag = sys.argv[3]
with tarfile.open(archive) as source:
    manifest = json.load(source.extractfile("manifest.json"))
    matches = [item for item in manifest if expected_tag in item.get("RepoTags", [])]
    if len(matches) != 1:
        raise SystemExit(f"docker archive tag mismatch: {expected_tag}")
    config = json.load(source.extractfile(matches[0]["Config"]))
if config.get("architecture") != "amd64" or config.get("os") != "linux":
    raise SystemExit(
        f"docker archive platform mismatch for {expected_tag}: "
        f"{config.get('os')}/{config.get('architecture')}"
    )
report.write_text(
    json.dumps(
        {"archiveRepoTags": matches[0]["RepoTags"], "imageConfig": config},
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
    )
    + "\n",
    encoding="utf-8",
)
PY
  docker run --rm -v "${STAGING_DIR}:/work:ro" \
    "${SYFT_IMAGE}" "docker-archive:/work/images/${tars[$index]}" -o spdx-json \
    >"${STAGING_DIR}/sbom/${names[$index]}.spdx.json"
done

container_id="$(docker create "${EMBEDDING_IMAGE}" true)"
trap 'docker rm -f "${container_id}" >/dev/null 2>&1 || true; cleanup' EXIT
docker cp "${container_id}:/models/BAAI__bge-small-zh-v1.5/.testagent-embedding-model.json" \
  "${STAGING_DIR}/MODEL-IDENTITY.json"
docker rm -f "${container_id}" >/dev/null
container_id=""
python3 - "${STAGING_DIR}/MODEL-IDENTITY.json" <<'PY'
import json, pathlib, sys
data = json.loads(pathlib.Path(sys.argv[1]).read_text())
expected = {
    "model": "BAAI/bge-small-zh-v1.5",
    "revision": "7999e1d3359715c523056ef9478215996d62a620",
    "dimension": 512,
    "normalized": True,
}
for key, value in expected.items():
    if data.get(key) != value:
        raise SystemExit(f"model identity mismatch for {key}")
PY

install -m 0644 "${ROOT_DIR}/deploy/internal/memory/LICENSES.txt" "${STAGING_DIR}/LICENSES.txt"
install -m 0644 "${ROOT_DIR}/deploy/internal/memory.env.example" "${STAGING_DIR}/memory.env.example"
install -m 0644 "${ROOT_DIR}/deploy/internal/embedding.env.example" "${STAGING_DIR}/embedding.env.example"
install -m 0755 "${ROOT_DIR}/deploy/internal/memory-docker.sh" "${STAGING_DIR}/memory-docker.sh"
install -m 0644 "${ROOT_DIR}/docs/deployment/qa-memory.md" "${STAGING_DIR}/DEPLOYMENT.md"
copy_python_tree "${ROOT_DIR}/memory-service/alembic" "${STAGING_DIR}/alembic"
install -m 0644 "${ROOT_DIR}/memory-service/alembic.ini" "${STAGING_DIR}/alembic.ini"

for relative in .dockerignore Dockerfile README.md pyproject.toml uv.lock; do
  install -m 0644 "${ROOT_DIR}/memory-service/${relative}" \
    "${STAGING_DIR}/source/memory-service/${relative}"
  install -m 0644 "${ROOT_DIR}/embedding-service/${relative}" \
    "${STAGING_DIR}/source/embedding-service/${relative}"
done
copy_python_tree "${ROOT_DIR}/memory-service/src" "${STAGING_DIR}/source/memory-service/src"
copy_python_tree "${ROOT_DIR}/embedding-service/src" "${STAGING_DIR}/source/embedding-service/src"
copy_python_tree "${ROOT_DIR}/embedding-service/scripts" "${STAGING_DIR}/source/embedding-service/scripts"

{
  printf 'TEST_AGENT_MEMORY_RELEASE_VERSION=%s\n' "${VERSION}"
  printf 'TEST_AGENT_MEMORY_RELEASE_REVISION=%s\n' "${revision}"
  printf 'TEST_AGENT_MEMORY_RELEASE_CREATED=%s\n' "${created}"
  printf 'TEST_AGENT_MEMORY_SERVICE_IMAGE=%s\n' "${MEMORY_IMAGE}"
  printf 'TEST_AGENT_EMBEDDING_SERVICE_IMAGE=%s\n' "${EMBEDDING_IMAGE}"
  printf 'TEST_AGENT_MEMORY_PGVECTOR_IMAGE=%s\n' "${PGVECTOR_IMAGE}"
  printf 'TEST_AGENT_MEMORY_NGINX_IMAGE=%s\n' "${NGINX_IMAGE}"
  printf 'TEST_AGENT_MEMORY_MEM0_VERSION=2.0.17\n'
  printf 'TEST_AGENT_MEMORY_ALEMBIC_HEAD=20260809_01\n'
  printf 'TEST_AGENT_MEMORY_MODEL_ID=BAAI/bge-small-zh-v1.5\n'
  printf 'TEST_AGENT_MEMORY_MODEL_REVISION=7999e1d3359715c523056ef9478215996d62a620\n'
  printf 'TEST_AGENT_MEMORY_MODEL_DIMENSION=512\n'
} >"${STAGING_DIR}/release.env"

(
  cd "${STAGING_DIR}"
  : >SHA256SUMS
  while IFS= read -r file; do
    printf '%s  %s\n' "$(sha256_file "${file}")" "${file#./}" >>SHA256SUMS
  done < <(find . -type f ! -name SHA256SUMS | LC_ALL=C sort)
)

rm -rf "${TARGET_DIR}"
mv "${STAGING_DIR}" "${TARGET_DIR}"
trap - EXIT
echo "Memory offline artifacts: ${TARGET_DIR}"
echo "Manifest: ${TARGET_DIR}/SHA256SUMS"
