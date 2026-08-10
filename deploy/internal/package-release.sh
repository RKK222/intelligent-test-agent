#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=archive-common.sh
source "${SCRIPT_DIR}/archive-common.sh"
LOBEHUB_CLIENT_CONTRACT_FILE="${SCRIPT_DIR}/lobehub-client-artifact-contract.sh"

ENV_FILE="${SCRIPT_DIR}/.env"
if [[ ! -f "${ENV_FILE}" ]]; then
  ENV_FILE="${SCRIPT_DIR}/env.example"
fi
OUTPUT_DIR="${SCRIPT_DIR}/dist"
OUTPUT_DIR_FROM_ARG=0
ENV_FILE_FROM_ARG=0
PLATFORM="linux/amd64"
PACKAGE_BACKEND=1
PACKAGE_FRONTEND=1
PACKAGE_OPENCODE_WORKER=1
PACKAGE_PYTHON_LIBS=0
PACKAGE_WORKFLOW=0
PACKAGE_TOOLBOX=1
PACKAGE_MYSQL_IMAGE=0
PACKAGE_LOBEHUB=0
PACKAGE_MEMORY=0
WITH_WORKFLOW_IN_RELEASE=0
WITH_LOBEHUB_IN_RELEASE=0
WITH_MEMORY_IN_RELEASE=0
SAVE_TARBALL=1
PACKAGE_ZIP=1
PACKAGE_ZIP_ONLY=0
PACKAGE_MODE=full
INCLUDE_ALL_COMPONENTS=0
COMPONENT_PLAN_ONLY=0
COMPONENT_STATE_FILE=""
OUTPUT_DIR_FROM_ENV_BEFORE_DOTENV="${TEST_AGENT_IMAGE_OUTPUT_DIR+x}"
TOOLBOX_ENTERPRISE_MIGRATION_RESOURCE="db/migration/V20260728160800__create_toolbox_click_tracking.sql"
TOOLBOX_ENTERPRISE_MIGRATION_SHA256="777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2"
LOBEHUB_MAIN_MIGRATION_RESOURCE="db/migration/V20260730090000__add_lobehub_model_gateway.sql"
LOBEHUB_MAIN_MIGRATION_SHA256="0f16f1b2f3108e60580cfeb00102e10ac21e20220be255fae77bad9871f0bcb7"
LOBEHUB_FORWARD_MIGRATION_RESOURCE="db/migration-compat/lobehub-missing/V20260802173416__backfill_lobehub_model_gateway.sql"
LOBEHUB_FORWARD_MIGRATION_SHA256="4f773e35e55380592f094c03cc5a66fb69b7dee4a2799e1c9ab5d0b9d8f1634a"
ROLLOUT_SUPERSEDE_MIGRATION_RESOURCE="db/migration/V20260803133000__support_public_agent_config_rollout_supersede.sql"
ROLLOUT_SUPERSEDE_MIGRATION_SHA256="8b3cbad538f856d5daa06d15f118554ecefb2380a249287cdfe291eb71199022"
LOBEHUB_RELEASE_FORWARD_MIGRATION_RESOURCE="db/migration-compat/lobehub-missing-after-rollout/V20260803141754__backfill_lobehub_model_gateway_after_rollout.sql"
LOBEHUB_RELEASE_FORWARD_MIGRATION_SHA256="b73b06fb14f407979646df32a8342603ab957c2f4812a4013ab9635cdfdcce64"
SUPPORT_ACCESS_MIGRATION_RESOURCE="db/migration/V20260805132000__create_support_access_audit.sql"
SUPPORT_ACCESS_MIGRATION_SHA256="54cea9a84948f8e4cee14d630772b8ee0668c2a7e5fc897ede5e792a15edd761"
SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE="db/migration/V20260806143000__classify_skill_hub_assets.sql"
SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256="f59f641527fdabaf21393319cd70ed578c6f75a55decae4d8839bc2b561ac06d"
PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_RESOURCE="db/migration/V20260806190000__persist_public_skill_hub_snapshots.sql"
PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_SHA256="1b2547cf466c09fe11a63b1f76e5e17ec1773e2187aa01e052288a9bb4861e75"
PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE="db/migration/V20260806190500__classify_public_skill_hub_snapshots.sql"
PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256="19a0e5af5f361179ac3887d541c274f75f43f89a683ee8037a5e0391444a92bf"
MEMORY_PROFILE_MIGRATION_RESOURCE="db/migration/V20260809230000__generalize_memory_and_embedding_profiles.sql"
MEMORY_PROFILE_MIGRATION_SHA256="2740ff6d4a97c5b8a4c438586f55d58078c3cfce93b06e4efeb6b77b039c66c3"
RUN_RESEND_MAIN_MIGRATION_RESOURCE="db/migration/V20260807190000__create_run_resends.sql"
RUN_RESEND_BEFORE_BATCH_MIGRATION_RESOURCE="db/migration-compat/run-resend-after-internal-model-before-batch/V20260807229999__create_run_resends_after_legacy_internal_model.sql"
BATCH_SESSION_MIGRATION_RESOURCE="db/migration/V20260807230000__add_batch_session_attribution.sql"
RUN_RESEND_AFTER_BATCH_MIGRATION_RESOURCE="db/migration-compat/run-resend-after-internal-model-after-batch/V20260808143303__create_run_resends_after_legacy_internal_model.sql"
RUN_RESEND_MIGRATION_SHA256="ca044d9819c7259b62e29243e9d72a06a2f01a532f1803f37e117de1d2f5d83d"
BATCH_SESSION_MIGRATION_SHA256="42ec1917deb16d96b910f81a0a4739487500453f90dc742e516822f800fdd6e3"
INTERNAL_MODEL_LEGACY_CREATE_MIGRATION_RESOURCE="db/migration-compat/internal-model-observability-legacy/V20260807130134__create_internal_model_observability.sql"
INTERNAL_MODEL_LEGACY_FIRST_TOKEN_MIGRATION_RESOURCE="db/migration-compat/internal-model-observability-legacy/V20260807203000__add_internal_model_first_token_metrics.sql"
INTERNAL_MODEL_LEGACY_STREAM_MIGRATION_RESOURCE="db/migration-compat/internal-model-observability-legacy/V20260807222227__add_internal_model_stream_complete_metrics.sql"
INTERNAL_MODEL_CREATE_MIGRATION_RESOURCE="db/migration/V20260808143300__create_internal_model_observability.sql"
INTERNAL_MODEL_FIRST_TOKEN_MIGRATION_RESOURCE="db/migration/V20260808143301__add_internal_model_first_token_metrics.sql"
INTERNAL_MODEL_STREAM_MIGRATION_RESOURCE="db/migration/V20260808143302__add_internal_model_stream_complete_metrics.sql"
INTERNAL_MODEL_CREATE_MIGRATION_SHA256="f214dfd0d4f26de830452d9f4121bc938cf031e4867555d5248e159d99377084"
INTERNAL_MODEL_FIRST_TOKEN_MIGRATION_SHA256="de7188e3ba5d01148a655dbc238783cf7881abf168bd7b6e422c9f2fa118a5c3"
INTERNAL_MODEL_STREAM_MIGRATION_SHA256="46f0a8e687f59c037a7e02cb1f9ba3db4893633ba20edd67d4ae75f0b6fd0d9e"
EXTERNAL_API_MAIN_MIGRATION_RESOURCE="db/migration/V20260809110000__create_external_api_credentials.sql"
QA_MEMORY_APPLIED_MIGRATION_RESOURCE="db/migration-compat/qa-memory-applied/V20260809120000__create_qa_memory_governance.sql"
EXTERNAL_API_FORWARD_MIGRATION_RESOURCE="db/migration-compat/external-api-after-qa-memory/V20260810110000__create_external_api_credentials_after_qa_memory.sql"
EXTERNAL_API_MIGRATION_SHA256="356f2cf9127fb514c614ccb8fc77473e6269f6e1e5cd373b0750d2f207009d53"
QA_MEMORY_APPLIED_MIGRATION_SHA256="b2ae5639284208be8bc09952d9143c3dd0d8a2bf649b6601aed4225e586af18a"
QA_MEMORY_GENERALIZE_MIGRATION_RESOURCE="db/migration-compat/qa-memory-extended/V20260809230000__generalize_memory_and_embedding_profiles.sql"
QA_MEMORY_IDENTITY_MIGRATION_RESOURCE="db/migration-compat/qa-memory-extended/V20260810090000__enforce_qa_memory_identity.sql"
QA_MEMORY_GENERALIZE_MIGRATION_SHA256="2740ff6d4a97c5b8a4c438586f55d58078c3cfce93b06e4efeb6b77b039c66c3"
QA_MEMORY_IDENTITY_MIGRATION_SHA256="619f886b093c80c1e1f71569c5c44309fa4f8184dd2791c0cf1955beb77c9af3"
QA_MEMORY_AFTER_SESSION_SHARE_MIGRATION_RESOURCE="db/migration-compat/qa-memory-after-session-share/V20260810173117__qa_memories_create_governance_after_session_share.sql"
QA_MEMORY_AFTER_SESSION_SHARE_MIGRATION_SHA256="44ea89c0ea5b9edb7fc5cbfb682e540b251f0c106b1d3c2762576d04ade6f984"
SESSION_SHARE_MAIN_MIGRATION_RESOURCE="db/migration/V20260809170000__session_shares_create_collaboration_share.sql"
SESSION_SHARE_FORWARD_MIGRATION_RESOURCE="db/migration-compat/qa-memory-extended/V20260810110001__session_shares_create_collaboration_share_after_qa_memory.sql"
SESSION_SHARE_ATTRIBUTION_MAIN_MIGRATION_RESOURCE="db/migration/V20260809170001__session_messages_add_delegated_attribution.sql"
SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION_RESOURCE="db/migration-compat/qa-memory-extended/V20260810110002__session_messages_add_delegated_attribution_after_qa_memory.sql"
SESSION_SHARE_MIGRATION_SHA256="b0b04355fcfe64f3d22d8a8ff297fa62a30db9d97bf6bf82968588f5da72d0c9"
SESSION_SHARE_ATTRIBUTION_MIGRATION_SHA256="dfb5d65b474416c28ec6131e95c7b9e7f744f9d2903c0bc4fcd0065632a4eee5"
XXL_INTERNAL_MODEL_PROBE_MIGRATION_RESOURCE="xxl-job/db/migration/V10__register_internal_model_probe_task.sql"
XXL_INTERNAL_MODEL_PROBE_MIGRATION_SHA256="665b22835a9871828fcaceca2941d1ca83de248698fde76f3380b12bec49fb47"
XXL_INTERNAL_MODEL_RETENTION_MIGRATION_RESOURCE="xxl-job/db/migration/V11__register_internal_model_observability_retention_task.sql"
XXL_INTERNAL_MODEL_RETENTION_MIGRATION_SHA256="03e7054a56daac14bd1cb62fd2302c7752c5d93ba88f255ad8d10f7320736236"

usage() {
  cat <<'USAGE'
Usage: deploy/internal/package-release.sh [options]

Build enterprise internal delivery artifacts:
  - backend executable jar
  - frontend dist files and tar.gz
  - opencode-worker image and docker-loadable tar
  - optional independent Python third-party library bundle
  - optional Python workflow-service, Runner and analysis-task linux/amd64 image bundle with SBOMs
  - pinned IT-Tools and OmniTools images, checksums and complete modified source
  - repository session logs under .agents/

The default release ZIP is platform-only. Build the standalone MySQL 8.4
linux/amd64 image separately with --mysql-only.

Options:
  --env-file <path>       Dotenv file to read. Defaults to deploy/internal/.env, then env.example.
  --output-dir <path>     Artifact output directory. Defaults to deploy/internal/dist.
                          TEST_AGENT_IMAGE_OUTPUT_DIR is honored only when exported by the shell
                          or loaded from an explicit --env-file.
  --platform <platform>   Docker build platform for opencode-worker. Defaults to linux/amd64.
  --backend-only          Package only the backend jar.
  --frontend-only         Package only the frontend dist.
  --opencode-only         Package only the opencode worker image.
  --python-libs-only      Package only the independent Python third-party library bundle.
  --workflow-only         Package only the Python workflow/Runner/analysis image set.
  --with-workflow         Include the workflow bundle and enable its frontend entry in a full release.
  --toolbox-only          Package only the two toolbox images and modified source.
  --mysql-only            Package only the standalone MySQL image.
  --with-lobehub          Include the verified external LobeHub artifact set in a full release.
  --lobehub-only          Package only the verified LobeHub offline artifact set and operations kit.
  --with-memory           Include independent Mem0/CPU/pgvector/VIP offline artifacts in a full release.
  --memory-only           Package only the independent memory data-plane artifacts.
  --zip-only              Reassemble the release ZIP from current verified artifacts and component state.
  --include-all-components
                          Force worker runtime (Python/OpenCode Manager/Codex MCP) and toolbox into the ZIP.
                          Use for first installation, disaster recovery or a new build machine.
  --component-state-file <path>
                          Persistent component fingerprint state. Default: <output-dir>/.release-component-state.env.
  --component-plan-only   Print include/reuse decisions and fingerprints without building or packaging.
  --no-save               Build/pull Docker images but do not export image tarballs.
  --no-zip                Do not create the complete enterprise release zip.
  -h, --help              Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      ENV_FILE_FROM_ARG=1
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      OUTPUT_DIR_FROM_ARG=1
      shift 2
      ;;
    --platform)
      PLATFORM="$2"
      shift 2
      ;;
    --backend-only)
      PACKAGE_MODE=backend-only
      PACKAGE_BACKEND=1
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_PYTHON_LIBS=0
      PACKAGE_WORKFLOW=0
      PACKAGE_TOOLBOX=0
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_LOBEHUB=0
      shift
      ;;
    --frontend-only)
      PACKAGE_MODE=frontend-only
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=1
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_PYTHON_LIBS=0
      PACKAGE_WORKFLOW=0
      PACKAGE_TOOLBOX=0
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_LOBEHUB=0
      shift
      ;;
    --opencode-only)
      PACKAGE_MODE=opencode-only
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=1
      PACKAGE_PYTHON_LIBS=0
      PACKAGE_WORKFLOW=0
      PACKAGE_TOOLBOX=0
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_LOBEHUB=0
      shift
      ;;
    --python-libs-only)
      PACKAGE_MODE=python-libs-only
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_PYTHON_LIBS=1
      PACKAGE_WORKFLOW=0
      PACKAGE_TOOLBOX=0
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_LOBEHUB=0
      shift
      ;;
    --workflow-only)
      PACKAGE_MODE=workflow-only
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_PYTHON_LIBS=0
      PACKAGE_WORKFLOW=1
      PACKAGE_TOOLBOX=0
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_LOBEHUB=0
      shift
      ;;
    --toolbox-only)
      PACKAGE_MODE=toolbox-only
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_PYTHON_LIBS=0
      PACKAGE_WORKFLOW=0
      PACKAGE_TOOLBOX=1
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_LOBEHUB=0
      shift
      ;;
    --mysql-only)
      PACKAGE_MODE=mysql-only
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_PYTHON_LIBS=0
      PACKAGE_WORKFLOW=0
      PACKAGE_TOOLBOX=0
      PACKAGE_MYSQL_IMAGE=1
      PACKAGE_LOBEHUB=0
      shift
      ;;
    --with-lobehub)
      WITH_LOBEHUB_IN_RELEASE=1
      shift
      ;;
    --with-workflow)
      WITH_WORKFLOW_IN_RELEASE=1
      shift
      ;;
    --with-memory)
      WITH_MEMORY_IN_RELEASE=1
      shift
      ;;
    --memory-only)
      PACKAGE_MODE=memory-only
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_PYTHON_LIBS=0
      PACKAGE_WORKFLOW=0
      PACKAGE_TOOLBOX=0
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_LOBEHUB=0
      PACKAGE_MEMORY=1
      shift
      ;;
    --lobehub-only)
      PACKAGE_MODE=lobehub-only
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_PYTHON_LIBS=0
      PACKAGE_WORKFLOW=0
      PACKAGE_TOOLBOX=0
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_LOBEHUB=1
      shift
      ;;
    --zip-only)
      PACKAGE_MODE=zip-only
      PACKAGE_BACKEND=0
      PACKAGE_FRONTEND=0
      PACKAGE_OPENCODE_WORKER=0
      PACKAGE_PYTHON_LIBS=0
      PACKAGE_WORKFLOW=0
      PACKAGE_TOOLBOX=0
      PACKAGE_MYSQL_IMAGE=0
      PACKAGE_ZIP_ONLY=1
      shift
      ;;
    --include-all-components)
      INCLUDE_ALL_COMPONENTS=1
      shift
      ;;
    --component-state-file)
      COMPONENT_STATE_FILE="$2"
      shift 2
      ;;
    --component-plan-only)
      COMPONENT_PLAN_ONLY=1
      shift
      ;;
    --no-save)
      SAVE_TARBALL=0
      shift
      ;;
    --no-zip)
      PACKAGE_ZIP=0
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

# 可选能力采用显式 opt-in，并在参数解析后应用，保证选项先后顺序不改变最终交付范围。
if [[ "${WITH_WORKFLOW_IN_RELEASE}" -eq 1 ]]; then
  if [[ "${PACKAGE_MODE}" != full && "${PACKAGE_MODE}" != zip-only ]]; then
    echo "--with-workflow can only be combined with the full or --zip-only release mode" >&2
    exit 2
  fi
  PACKAGE_WORKFLOW=1
fi
if [[ "${WITH_LOBEHUB_IN_RELEASE}" -eq 1 ]]; then
  if [[ "${PACKAGE_MODE}" != full && "${PACKAGE_MODE}" != zip-only ]]; then
    echo "--with-lobehub can only be combined with the full or --zip-only release mode" >&2
    exit 2
  fi
  PACKAGE_LOBEHUB=1
fi
if [[ "${WITH_MEMORY_IN_RELEASE}" -eq 1 ]]; then
  if [[ "${PACKAGE_MODE}" != full && "${PACKAGE_MODE}" != zip-only ]]; then
    echo "--with-memory can only be combined with the full or --zip-only release mode" >&2
    exit 2
  fi
  PACKAGE_MEMORY=1
fi

load_dotenv() {
  local file="$1"
  [[ -f "${file}" ]] || return 0
  local line key value
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -z "${line}" || "${line}" == \#* ]] && continue
    [[ "${line}" == export\ * ]] && line="${line#export }"
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    key="${key//[[:space:]]/}"
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
      value="${value:1:${#value}-2}"
    elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
      value="${value:1:${#value}-2}"
    fi
    if [[ -z "${!key+x}" ]]; then
      printf -v "${key}" '%s' "${value}"
      export "${key}"
    fi
  done <"${file}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_digest_pinned_image() {
  local variable_name="$1"
  local image_reference="$2"
  # 工具镜像必须可复现；允许切换受控 registry 前缀，但禁止退化为可漂移的纯 tag。
  if [[ ! "${image_reference}" =~ ^[A-Za-z0-9._:/-]+@sha256:[0-9a-f]{64}$ ]]; then
    echo "${variable_name} must be an image reference pinned by a lowercase sha256 digest: ${image_reference}" >&2
    exit 1
  fi
}

require_lobehub_loadable_image() {
  local variable_name="$1" image_reference="$2" image_id="$3" leaf tag
  leaf="${image_reference##*/}"
  tag="${leaf#*:}"
  # docker save/load 不恢复 Registry RepoDigest 映射，因此离线介质使用不可变 tag，
  # 再以 Docker image ID 和镜像 tar 的 SHA256SUMS 双重校验内容。
  if [[ ! "${image_reference}" =~ ^[A-Za-z0-9._:/-]+$ \
      || "${image_reference}" == *@* || "${leaf}" != *:* \
      || -z "${tag}" || "${tag}" == latest ]]; then
    echo "${variable_name} must use an immutable non-latest image tag: ${image_reference}" >&2
    exit 1
  fi
  if [[ ! "${image_id}" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    echo "${variable_name}_ID must be a lowercase Docker image ID: ${image_id}" >&2
    exit 1
  fi
}

require_platform_image() {
  local variable_name="$1" image_reference="$2" repository="$3" version="$4"
  # 企业 registry 前缀可配置，但源码、镜像 tag 与离线 tar 不能跨平台版本混用。
  if [[ "${image_reference}" != "${repository}:${version}" \
      && "${image_reference}" != */"${repository}:${version}" ]]; then
    echo "${variable_name} must end with ${repository}:${version}: ${image_reference}" >&2
    exit 1
  fi
}

configure_java_home() {
  local detected_home="" java_version
  local versions=()

  if [[ -n "${JAVA_HOME:-}" && -z "${JAVA_VERSION:-}" ]] && java_home_is_usable "${JAVA_HOME}"; then
    return
  fi

  if [[ -n "${JAVA_VERSION:-}" ]]; then
    versions=("${JAVA_VERSION}")
  else
    versions=(25 24 23 22 21)
  fi

  for java_version in "${versions[@]}"; do
    if [[ "$(uname -s)" == "Darwin" ]]; then
      detected_home="$(/usr/libexec/java_home -v "${java_version}" 2>/dev/null || true)"
      if [[ -n "${detected_home}" ]] && ! java_home_is_usable "${detected_home}" "${java_version}"; then
        detected_home=""
      fi
    fi

    if [[ -z "${detected_home}" ]]; then
      for candidate in \
        "${HOME}/Library/Java/JavaVirtualMachines/openjdk-${java_version}.0.1/Contents/Home" \
        "/Library/Java/JavaVirtualMachines/openjdk-${java_version}/Contents/Home" \
        "/usr/lib/jvm/java-${java_version}" \
        "/usr/lib/jvm/openjdk-${java_version}" \
        "${HOME}/.sdkman/candidates/java/current"; do
        if [[ -d "${candidate}" ]] && java_home_is_usable "${candidate}" "${java_version}"; then
          detected_home="${candidate}"
          break
        fi
      done
    fi

    [[ -n "${detected_home}" ]] && break
  done

  if [[ -n "${detected_home}" ]]; then
    export JAVA_HOME="${detected_home}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
}

java_home_is_usable() {
  local java_home="$1"
  local expected_major="${2:-}"
  local version_line major
  [[ -x "${java_home}/bin/java" ]] || return 1
  version_line="$("${java_home}/bin/java" -version 2>&1 | head -n 1 || true)"
  if [[ "${version_line}" =~ \"1\.([0-9]+) ]]; then
    major="${BASH_REMATCH[1]}"
  elif [[ "${version_line}" =~ \"([0-9]+) ]]; then
    major="${BASH_REMATCH[1]}"
  else
    return 1
  fi
  if [[ -n "${expected_major}" ]]; then
    [[ "${major}" -eq "${expected_major}" ]]
  else
    [[ "${major}" -ge 21 ]]
  fi
}

tag_to_tar_name() {
  local tag="$1"
  local platform="$2"
  local platform_suffix="${platform//\//-}"
  local safe="${tag//\//_}"
  safe="${safe//:/_}"
  printf '%s-%s.tar' "${safe}" "${platform_suffix}"
}

write_artifact_checksum() {
  local artifact="$1" directory name
  directory="$(dirname "${artifact}")"
  name="$(basename "${artifact}")"
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${directory}" && sha256sum "${name}" >"${name}.sha256")
  elif command -v shasum >/dev/null 2>&1; then
    (cd "${directory}" && shasum -a 256 "${name}" >"${name}.sha256")
  else
    echo "Neither sha256sum nor shasum is available; cannot checksum ${artifact}" >&2
    exit 1
  fi
  archive_strip_file_metadata "${artifact}" "${artifact}.sha256"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "Neither sha256sum nor shasum is available" >&2
    exit 1
  fi
}

sha256_jar_resource() {
  local jar="$1" resource="$2"
  if command -v sha256sum >/dev/null 2>&1; then
    unzip -p "${jar}" "${resource}" | sha256sum | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    unzip -p "${jar}" "${resource}" | shasum -a 256 | awk '{print $1}'
  else
    echo "Neither sha256sum nor shasum is available" >&2
    exit 1
  fi
}

find_unique_persistence_jar() {
  local lib_dir="$1" count
  count="$(find "${lib_dir}" -maxdepth 1 -type f -name 'test-agent-persistence-*.jar' | wc -l | tr -d '[:space:]')"
  if [[ "${count}" != 1 ]]; then
    echo "Expected exactly one test-agent-persistence JAR under ${lib_dir}, found ${count}" >&2
    exit 1
  fi
  find "${lib_dir}" -maxdepth 1 -type f -name 'test-agent-persistence-*.jar' -print -quit
}

find_unique_xxl_job_integration_jar() {
  local lib_dir="$1" count
  count="$(find "${lib_dir}" -maxdepth 1 -type f -name 'test-agent-xxl-job-integration-*.jar' | wc -l | tr -d '[:space:]')"
  if [[ "${count}" != 1 ]]; then
    echo "Expected exactly one test-agent-xxl-job-integration JAR under ${lib_dir}, found ${count}" >&2
    exit 1
  fi
  find "${lib_dir}" -maxdepth 1 -type f -name 'test-agent-xxl-job-integration-*.jar' -print -quit
}

verify_release_flyway_resource() {
  local jar="$1" label="$2" resource="$3" expected="$4" actual
  if ! unzip -Z1 "${jar}" | grep -Fx "${resource}" >/dev/null; then
    echo "${label} is missing release Flyway migration: ${resource}" >&2
    exit 1
  fi
  actual="$(sha256_jar_resource "${jar}" "${resource}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${label} contains the wrong release Flyway migration: resource=${resource} expected=${expected} actual=${actual}" >&2
    exit 1
  fi
  printf '%s Flyway migration verified: resource=%s sha256=%s\n' "${label}" "${resource}" "${actual}"
}

verify_release_flyway_migrations_jar() {
  local jar="$1" label="$2"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${TOOLBOX_ENTERPRISE_MIGRATION_RESOURCE}" "${TOOLBOX_ENTERPRISE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${LOBEHUB_MAIN_MIGRATION_RESOURCE}" "${LOBEHUB_MAIN_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${LOBEHUB_FORWARD_MIGRATION_RESOURCE}" "${LOBEHUB_FORWARD_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${ROLLOUT_SUPERSEDE_MIGRATION_RESOURCE}" "${ROLLOUT_SUPERSEDE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${LOBEHUB_RELEASE_FORWARD_MIGRATION_RESOURCE}" "${LOBEHUB_RELEASE_FORWARD_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SUPPORT_ACCESS_MIGRATION_RESOURCE}" "${SUPPORT_ACCESS_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE}" "${SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_RESOURCE}" "${PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE}" "${PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${MEMORY_PROFILE_MIGRATION_RESOURCE}" "${MEMORY_PROFILE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${RUN_RESEND_MAIN_MIGRATION_RESOURCE}" "${RUN_RESEND_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${RUN_RESEND_BEFORE_BATCH_MIGRATION_RESOURCE}" "${RUN_RESEND_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${BATCH_SESSION_MIGRATION_RESOURCE}" "${BATCH_SESSION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${RUN_RESEND_AFTER_BATCH_MIGRATION_RESOURCE}" "${RUN_RESEND_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${INTERNAL_MODEL_LEGACY_CREATE_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_CREATE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${INTERNAL_MODEL_LEGACY_FIRST_TOKEN_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_FIRST_TOKEN_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${INTERNAL_MODEL_LEGACY_STREAM_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_STREAM_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${INTERNAL_MODEL_CREATE_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_CREATE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${INTERNAL_MODEL_FIRST_TOKEN_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_FIRST_TOKEN_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${INTERNAL_MODEL_STREAM_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_STREAM_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${EXTERNAL_API_MAIN_MIGRATION_RESOURCE}" "${EXTERNAL_API_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${QA_MEMORY_APPLIED_MIGRATION_RESOURCE}" "${QA_MEMORY_APPLIED_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${EXTERNAL_API_FORWARD_MIGRATION_RESOURCE}" "${EXTERNAL_API_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${QA_MEMORY_GENERALIZE_MIGRATION_RESOURCE}" "${QA_MEMORY_GENERALIZE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${QA_MEMORY_IDENTITY_MIGRATION_RESOURCE}" "${QA_MEMORY_IDENTITY_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${QA_MEMORY_AFTER_SESSION_SHARE_MIGRATION_RESOURCE}" "${QA_MEMORY_AFTER_SESSION_SHARE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SESSION_SHARE_MAIN_MIGRATION_RESOURCE}" "${SESSION_SHARE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SESSION_SHARE_FORWARD_MIGRATION_RESOURCE}" "${SESSION_SHARE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SESSION_SHARE_ATTRIBUTION_MAIN_MIGRATION_RESOURCE}" "${SESSION_SHARE_ATTRIBUTION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION_RESOURCE}" "${SESSION_SHARE_ATTRIBUTION_MIGRATION_SHA256}"
}

verify_release_xxl_flyway_migrations_jar() {
  local jar="$1" label="$2"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${XXL_INTERNAL_MODEL_PROBE_MIGRATION_RESOURCE}" "${XXL_INTERNAL_MODEL_PROBE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${XXL_INTERNAL_MODEL_RETENTION_MIGRATION_RESOURCE}" "${XXL_INTERNAL_MODEL_RETENTION_MIGRATION_SHA256}"
}

verify_memory_artifact_set() {
  local directory="$1" required
  for required in \
    images/test-agent-memory-service_internal-linux-amd64.tar \
    images/test-agent-embedding-bge-small-zh-v1.5_internal-linux-amd64.tar \
    images/test-agent-pgvector_0.8.1-pg16_internal-linux-amd64.tar \
    images/test-agent-memory-nginx_1.27.2_internal-linux-amd64.tar \
    MODEL-IDENTITY.json LICENSES.txt release.env SHA256SUMS \
    alembic/versions/20260809_01_shared_memory_control.py \
    memory-docker.sh memory.env.example embedding.env.example; do
    [[ -f "${directory}/${required}" ]] || {
      echo "Required memory artifact is missing: ${directory}/${required}" >&2
      exit 1
    }
  done
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${directory}" && sha256sum -c SHA256SUMS)
  elif command -v shasum >/dev/null 2>&1; then
    (cd "${directory}" && shasum -a 256 -c SHA256SUMS)
  else
    echo "Neither sha256sum nor shasum is available for memory artifact verification" >&2
    exit 1
  fi
}

state_value() {
  local file="$1" key="$2"
  [[ -f "${file}" ]] || return 0
  awk -F= -v wanted="${key}" '$1 == wanted { value=substr($0, index($0, "=") + 1) } END { print value }' "${file}"
}

# 组件指纹只读取 Git 已跟踪或未忽略的新文件内容，不使用 mtime，避免同一源码仅因复制时间变化而误打大包。
component_fingerprint() {
  local config="$1"
  shift
  local fingerprint_input file
  fingerprint_input="$(mktemp "${OUTPUT_DIR}/.component-fingerprint.XXXXXX")"
  printf 'config=%s\n' "${config}" >"${fingerprint_input}"
  while IFS= read -r -d '' file; do
    if [[ -f "${ROOT_DIR}/${file}" ]]; then
      printf '%s  %s\n' "$(sha256_file "${ROOT_DIR}/${file}")" "${file}" >>"${fingerprint_input}"
    fi
  done < <(git -C "${ROOT_DIR}" ls-files -co --exclude-standard -z -- "$@")
  sha256_file "${fingerprint_input}"
  rm -f "${fingerprint_input}"
}

write_component_fingerprints() {
  local target="$1" tmp
  tmp="$(mktemp "${target}.new.XXXXXX")"
  {
    printf 'TEST_AGENT_RELEASE_COMPONENT_STATE_VERSION=1\n'
    printf 'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=%s\n' "${WORKER_RUNTIME_FINGERPRINT}"
    printf 'TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=%s\n' "${TOOLBOX_FINGERPRINT}"
  } >"${tmp}"
  chmod 0600 "${tmp}"
  mv -f "${tmp}" "${target}"
}

write_worker_artifact_state() {
  local target="${OUTPUT_DIR}/.worker-runtime-artifact.env" tmp
  tmp="$(mktemp "${target}.new.XXXXXX")"
  printf 'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=%s\n' \
    "${WORKER_RUNTIME_FINGERPRINT}" >"${tmp}"
  chmod 0600 "${tmp}"
  mv -f "${tmp}" "${target}"
}

write_toolbox_artifact_state() {
  local target="${OUTPUT_DIR}/.toolbox-artifact.env" tmp
  tmp="$(mktemp "${target}.new.XXXXXX")"
  printf 'TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=%s\n' \
    "${TOOLBOX_FINGERPRINT}" >"${tmp}"
  chmod 0600 "${tmp}"
  mv -f "${tmp}" "${target}"
}

require_artifact_fingerprint() {
  local file="$1" key="$2" expected="$3" actual
  actual="$(state_value "${file}" "${key}")"
  [[ "${actual}" == "${expected}" ]] || {
    echo "Component artifacts are missing or stale for ${key}; run the normal build instead of --zip-only" >&2
    exit 1
  }
}

plan_release_components() {
  local previous_worker previous_toolbox worker_config toolbox_config
  local current_release current_manifest current_worker_mode current_worker_fingerprint
  local current_toolbox_mode current_toolbox_fingerprint
  worker_config="schema=2|platform=${PLATFORM}|image=${TEST_AGENT_OPENCODE_WORKER_IMAGE}|go=${GO_IMAGE}|node=${NODE_IMAGE}|python=${PYTHON_VERSION}|pythonSourceSize=${PYTHON_SOURCE_SIZE}|pythonSourceSha=${PYTHON_SOURCE_SHA256}|pythonSourceBase=${PYTHON_SOURCE_BASE_URL}|opencode=${OPENCODE_VERSION}|opencodeCommit=${OPENCODE_RELEASE_COMMIT}|opencodeAsset=${OPENCODE_ASSET_SHA256}|opencodeBinary=${OPENCODE_BINARY_SHA256}|codex=${CODEX_VERSION}|codexAsset=${CODEX_ASSET_SHA256}|bwrap=${CODEX_BWRAP_ASSET_SHA256}|bwrapBinary=${CODEX_BWRAP_BINARY_SHA256}|runtimePackage=${OPENCODE_RUNTIME_PACKAGE_JSON}|runtimeLock=${OPENCODE_RUNTIME_PACKAGE_LOCK}"
  toolbox_config="schema=1|platform=${PLATFORM}|it=${TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE}|omni=${TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE}|node=${TEST_AGENT_TOOLBOX_NODE_BASE_IMAGE}|nginx=${TEST_AGENT_TOOLBOX_NGINX_BASE_IMAGE}"

  WORKER_RUNTIME_FINGERPRINT="$(component_fingerprint "${worker_config}" \
    opencode-manager/go.mod \
    opencode-manager/go.sum \
    opencode-manager/cmd \
    opencode-manager/internal \
    ':(exclude)opencode-manager/**/*_test.go' \
    deploy/internal/opencode-worker.Dockerfile \
    deploy/internal/opencode-worker.Dockerfile.dockerignore \
    deploy/internal/opencode-worker-entrypoint.sh \
    deploy/internal/validate-opencode-models.sh \
    deploy/internal/opencode-node-runtime.package.json \
    deploy/internal/opencode-node-runtime.package-lock.json \
    deploy/internal/opencode-official-launcher.mjs \
    deploy/internal/opencode-runtime.gitignore \
    deploy/internal/codex-whitebox-mcp-launcher.sh \
    deploy/internal/codex-whitebox-requirements.toml \
    tools/probe-codex-whitebox-e2e.mjs \
    opencode-source/opencode-1.18.4/LICENSE)"
  TOOLBOX_FINGERPRINT="$(component_fingerprint "${toolbox_config}" \
    toolbox-source \
    backend/test-agent-integration/src/main/resources/toolbox/catalog-v1.json)"

  previous_worker="$(state_value "${COMPONENT_STATE_FILE}" TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT)"
  previous_toolbox="$(state_value "${COMPONENT_STATE_FILE}" TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT)"
  WORKER_COMPONENT_MODE=reuse
  TOOLBOX_COMPONENT_MODE=reuse
  if [[ "${INCLUDE_ALL_COMPONENTS}" -eq 1 || -z "${previous_worker}" \
    || "${previous_worker}" != "${WORKER_RUNTIME_FINGERPRINT}" ]]; then
    WORKER_COMPONENT_MODE=included
  fi
  if [[ "${INCLUDE_ALL_COMPONENTS}" -eq 1 || -z "${previous_toolbox}" \
    || "${previous_toolbox}" != "${TOOLBOX_FINGERPRINT}" ]]; then
    TOOLBOX_COMPONENT_MODE=included
  fi

  # zip-only 用于同一发布批次补会话日志或重新封装，必须保持现有 ZIP 的组件选择；
  # 源码指纹变化时不会命中，仍按持久化基线重新判断并要求当前制品指纹匹配。
  current_release="${OUTPUT_DIR}/test-agent-internal-release.zip"
  if [[ "${PACKAGE_MODE}" == zip-only && "${INCLUDE_ALL_COMPONENTS}" -eq 0 \
    && -f "${current_release}" ]]; then
    require_command unzip
    current_manifest="$(unzip -p "${current_release}" deploy/internal/release-components.env 2>/dev/null || true)"
    current_worker_mode="$(awk -F= '$1 == "TEST_AGENT_RELEASE_WORKER_RUNTIME" { print substr($0, index($0, "=") + 1) }' <<<"${current_manifest}")"
    current_worker_fingerprint="$(awk -F= '$1 == "TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT" { print substr($0, index($0, "=") + 1) }' <<<"${current_manifest}")"
    current_toolbox_mode="$(awk -F= '$1 == "TEST_AGENT_RELEASE_TOOLBOX" { print substr($0, index($0, "=") + 1) }' <<<"${current_manifest}")"
    current_toolbox_fingerprint="$(awk -F= '$1 == "TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT" { print substr($0, index($0, "=") + 1) }' <<<"${current_manifest}")"
    if [[ ( "${current_worker_mode}" == included || "${current_worker_mode}" == reuse ) \
      && "${current_worker_fingerprint}" == "${WORKER_RUNTIME_FINGERPRINT}" ]]; then
      WORKER_COMPONENT_MODE="${current_worker_mode}"
    fi
    if [[ ( "${current_toolbox_mode}" == included || "${current_toolbox_mode}" == reuse ) \
      && "${current_toolbox_fingerprint}" == "${TOOLBOX_FINGERPRINT}" ]]; then
      TOOLBOX_COMPONENT_MODE="${current_toolbox_mode}"
    fi
  fi
  [[ "${PACKAGE_MODE}" != opencode-only ]] || WORKER_COMPONENT_MODE=included
  [[ "${PACKAGE_MODE}" != toolbox-only ]] || TOOLBOX_COMPONENT_MODE=included

  printf 'worker runtime component: %s\n' "${WORKER_COMPONENT_MODE}"
  printf 'worker runtime fingerprint: %s\n' "${WORKER_RUNTIME_FINGERPRINT}"
  printf 'toolbox component: %s\n' "${TOOLBOX_COMPONENT_MODE}"
  printf 'toolbox fingerprint: %s\n' "${TOOLBOX_FINGERPRINT}"
  printf 'component state: %s\n' "${COMPONENT_STATE_FILE}"
}

package_backend() {
  local backend_dir="${OUTPUT_DIR}/backend"
  local backend_jar_path extract_dir manifest_dir manifest_file persistence_jar xxl_job_integration_jar
  require_command unzip
  require_command zip
  mkdir -p "${backend_dir}"
  echo "Validating Spring bean constructor wiring"
  # 发布前必须扫描全部生产 Spring Bean，避免多构造器未显式注入导致企业环境启动失败。
  (cd "${ROOT_DIR}/backend" && mvn -q -pl test-agent-app -am \
    -Dtest=SpringBeanConstructorWiringTest \
    -Dsurefire.failIfNoSpecifiedTests=false test)
  echo "Building backend jar"
  # 强制装配审计通过后，交付构建只需要主代码和运行时依赖；不再重复执行其它测试。
  (cd "${ROOT_DIR}/backend" && mvn -q -pl test-agent-app -am -Dmaven.test.skip=true package)

  local jar
  jar="$(find "${ROOT_DIR}/backend/test-agent-app/target" -maxdepth 1 -type f -name 'test-agent-app-*.jar' ! -name '*.original' | sort | tail -n 1)"
  if [[ -z "${jar}" ]]; then
    echo "Backend jar not found under backend/test-agent-app/target" >&2
    exit 1
  fi
  rm -rf "${backend_dir}"
  mkdir -p "${backend_dir}"
  cp "${jar}" "${backend_dir}/test-agent-app.jar"
  backend_jar_path="$(cd "${backend_dir}" && pwd)/test-agent-app.jar"
  extract_dir="$(mktemp -d "${OUTPUT_DIR}/.backend-lib.XXXXXX")"
  unzip -q "${backend_dir}/test-agent-app.jar" 'BOOT-INF/lib/*' -d "${extract_dir}"
  mv "${extract_dir}/BOOT-INF/lib" "${backend_dir}/lib"
  rm -rf "${extract_dir}"
  # 交付包只保留启动器和业务 classes，所有依赖由 PropertiesLauncher 从外置 lib 加载。
  zip -qd "${backend_dir}/test-agent-app.jar" 'BOOT-INF/lib/*' >/dev/null
  manifest_dir="$(mktemp -d "${OUTPUT_DIR}/.backend-manifest.XXXXXX")"
  mkdir -p "${manifest_dir}/META-INF"
  manifest_file="${manifest_dir}/META-INF/MANIFEST.MF"
  unzip -p "${backend_dir}/test-agent-app.jar" META-INF/MANIFEST.MF | tr -d '\r' \
    | sed 's#Main-Class: org.springframework.boot.loader.launch.JarLauncher#Main-Class: org.springframework.boot.loader.launch.PropertiesLauncher#' \
    | sed '${/^$/d;}' >"${manifest_file}"
  printf 'Loader-Path: /data/testagent/dist/backend/lib\n\n' >>"${manifest_file}"
  (cd "${manifest_dir}" && zip -X -q -u "${backend_jar_path}" META-INF/MANIFEST.MF)
  rm -rf "${manifest_dir}"
  unzip -p "${backend_dir}/test-agent-app.jar" META-INF/MANIFEST.MF | tr -d '\r' \
    | grep -Fx 'Loader-Path: /data/testagent/dist/backend/lib' >/dev/null || {
    echo "Backend jar manifest is missing the external Loader-Path" >&2
    exit 1
  }
  [[ -n "$(find "${backend_dir}/lib" -maxdepth 1 -type f -name '*.jar' -print -quit)" ]] || {
    echo "External backend libraries were not extracted" >&2
    exit 1
  }
  persistence_jar="$(find_unique_persistence_jar "${backend_dir}/lib")"
  verify_release_flyway_migrations_jar "${persistence_jar}" "Packaged persistence JAR"
  xxl_job_integration_jar="$(find_unique_xxl_job_integration_jar "${backend_dir}/lib")"
  verify_release_xxl_flyway_migrations_jar "${xxl_job_integration_jar}" "Packaged XXL integration JAR"
  unzip -Z1 "${backend_dir}/test-agent-app.jar" | grep -Fx 'BOOT-INF/classes/rsa-private.key' >/dev/null || {
    echo "Backend jar is missing the embedded RSA private key resource" >&2
    exit 1
  }

  # 离线包随平台应用交付固定上游源码、许可证和版本证据，便于履行 GPL-3.0 义务与后续升级核对。
  local xxl_compliance_dir="${backend_dir}/xxl-job-upstream"
  mkdir -p "${xxl_compliance_dir}/source"
  cp "${ROOT_DIR}/backend/test-agent-xxl-job-admin-upstream/LICENSE" "${xxl_compliance_dir}/LICENSE"
  cp "${ROOT_DIR}/backend/test-agent-xxl-job-admin-upstream/UPSTREAM.md" "${xxl_compliance_dir}/UPSTREAM.md"
  cp "${ROOT_DIR}/backend/test-agent-xxl-job-admin-upstream/README.md" "${xxl_compliance_dir}/README.md"
  cp -R "${ROOT_DIR}/backend/test-agent-xxl-job-admin-upstream/src/." "${xxl_compliance_dir}/source/"
  printf '%s\n' \
    'component=XXL-JOB Admin' \
    'version=3.4.2' \
    'commit=c2bbb46c9a3af8e2a69246728a452c606240b80e' \
    'license=GPL-3.0' \
    'integration=test-agent-xxl-job-integration' \
    >"${xxl_compliance_dir}/VERSION"
  ls -lh "${backend_dir}/test-agent-app.jar"
}

package_frontend() {
  local frontend_dir="${OUTPUT_DIR}/frontend"
  local workflow_enabled lobehub_enabled
  workflow_enabled="$([[ "${PACKAGE_WORKFLOW}" -eq 1 ]] && printf true || printf false)"
  lobehub_enabled="$([[ "${PACKAGE_LOBEHUB}" -eq 1 ]] && printf true || printf false)"
  echo "Building frontend dist"
  (cd "${ROOT_DIR}/frontend" && corepack pnpm install --frozen-lockfile && \
    VITE_TEST_AGENT_API_BASE_URL="${VITE_TEST_AGENT_API_BASE_URL:-}" \
    VITE_TEST_AGENT_WORKFLOW_ENABLED="${workflow_enabled}" \
    VITE_TEST_AGENT_LOBEHUB_ENABLED="${lobehub_enabled}" \
    corepack pnpm --filter @test-agent/agent-web build)

  rm -rf "${frontend_dir}"
  mkdir -p "${frontend_dir}"
  cp -R "${ROOT_DIR}/frontend/apps/agent-web/dist/." "${frontend_dir}/"
  archive_create_tar_gz "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz" "${OUTPUT_DIR}" frontend
  ls -lh "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz"
}

build_opencode_worker_image() {
  local manager_build_version
  manager_build_version="$(TZ=Asia/Shanghai date '+V%Y%m%d.%H%M%S')"
  echo "Building ${TEST_AGENT_OPENCODE_WORKER_IMAGE} for ${PLATFORM}"
  docker buildx build \
    --platform "${PLATFORM}" \
    -f "${ROOT_DIR}/deploy/internal/opencode-worker.Dockerfile" \
    -t "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" \
    --load \
    --build-arg "GOPROXY=${GOPROXY}" \
    --build-arg "NPM_REGISTRY=${NPM_REGISTRY}" \
    --build-arg "DEBIAN_MIRROR=${DEBIAN_MIRROR}" \
    --build-arg "DEBIAN_SECURITY_MIRROR=${DEBIAN_SECURITY_MIRROR}" \
    --build-arg "GO_IMAGE=${GO_IMAGE}" \
    --build-arg "MANAGER_BUILD_VERSION=${manager_build_version}" \
    --build-arg "NODE_IMAGE=${NODE_IMAGE}" \
    --build-arg "PYTHON_VERSION=${PYTHON_VERSION}" \
    --build-arg "PYTHON_SOURCE_SIZE=${PYTHON_SOURCE_SIZE}" \
    --build-arg "PYTHON_SOURCE_SHA256=${PYTHON_SOURCE_SHA256}" \
    --build-arg "PYTHON_SOURCE_BASE_URL=${PYTHON_SOURCE_BASE_URL}" \
    --build-arg "OPENCODE_VERSION=${OPENCODE_VERSION}" \
    --build-arg "OPENCODE_RELEASE_COMMIT=${OPENCODE_RELEASE_COMMIT}" \
    --build-arg "OPENCODE_ASSET_NAME=${OPENCODE_ASSET_NAME}" \
    --build-arg "OPENCODE_ASSET_SIZE=${OPENCODE_ASSET_SIZE}" \
    --build-arg "OPENCODE_ASSET_SHA256=${OPENCODE_ASSET_SHA256}" \
    --build-arg "OPENCODE_BINARY_SHA256=${OPENCODE_BINARY_SHA256}" \
    --build-arg "OPENCODE_RELEASE_BASE_URL=${OPENCODE_RELEASE_BASE_URL}" \
    --build-arg "CODEX_VERSION=${CODEX_VERSION}" \
    --build-arg "CODEX_ASSET_NAME=${CODEX_ASSET_NAME}" \
    --build-arg "CODEX_ASSET_SIZE=${CODEX_ASSET_SIZE}" \
    --build-arg "CODEX_ASSET_SHA256=${CODEX_ASSET_SHA256}" \
    --build-arg "CODEX_BWRAP_ASSET_NAME=${CODEX_BWRAP_ASSET_NAME}" \
    --build-arg "CODEX_BWRAP_ASSET_SIZE=${CODEX_BWRAP_ASSET_SIZE}" \
    --build-arg "CODEX_BWRAP_ASSET_SHA256=${CODEX_BWRAP_ASSET_SHA256}" \
    --build-arg "CODEX_BWRAP_BINARY_SHA256=${CODEX_BWRAP_BINARY_SHA256}" \
    --build-arg "CODEX_RELEASE_BASE_URL=${CODEX_RELEASE_BASE_URL}" \
    --build-arg "OPENCODE_RUNTIME_PACKAGE_JSON=${OPENCODE_RUNTIME_PACKAGE_JSON}" \
    --build-arg "OPENCODE_RUNTIME_PACKAGE_LOCK=${OPENCODE_RUNTIME_PACKAGE_LOCK}" \
    "${ROOT_DIR}"
  docker image inspect "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" >/dev/null

  # 构建机先验证固定版本、摘要、MCP 契约和失败关闭；native amd64 的 namespace E2E 由脚本自动执行。
  EXPECTED_PYTHON_VERSION="${PYTHON_VERSION}" \
    "${ROOT_DIR}/tools/verify-codex-whitebox-worker-image.sh" "${TEST_AGENT_OPENCODE_WORKER_IMAGE}"

  export_worker_programs

  if [[ "${SAVE_TARBALL}" -eq 1 ]]; then
    local tar_path="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
    echo "Saving ${TEST_AGENT_OPENCODE_WORKER_IMAGE} to ${tar_path}"
    docker save -o "${tar_path}" "${TEST_AGENT_OPENCODE_WORKER_IMAGE}"
    ls -lh "${tar_path}"
    write_worker_artifact_state
  fi
}

package_workflow_images() {
  local tools_id workflow_version build_env workflow_output archive
  [[ "${PLATFORM}" == "linux/amd64" ]] || {
    echo "Python workflow artifacts only support linux/amd64" >&2
    exit 1
  }
  if ! docker image inspect "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" >/dev/null 2>&1; then
    local worker_tar
    worker_tar="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
    [[ -f "${worker_tar}" ]] || {
      echo "Workflow analysis image requires the verified OpenCode worker image or tar: ${worker_tar}" >&2
      exit 1
    }
    docker load -i "${worker_tar}" >/dev/null
  fi
  tools_id="$(docker image inspect --format '{{.Id}}' "${TEST_AGENT_OPENCODE_WORKER_IMAGE}")"
  workflow_version="${TEST_AGENT_WORKFLOW_RELEASE_VERSION:-$(TZ=Asia/Shanghai date '+V%Y%m%d.%H%M%S')}"
  workflow_output="${OUTPUT_DIR}/workflow"
  mkdir -p "${workflow_output}"
  build_env="$(mktemp "${OUTPUT_DIR}/.workflow-build.XXXXXX")"
  chmod 0600 "${build_env}"
  {
    printf 'TEST_AGENT_WORKFLOW_RELEASE_VERSION=%s\n' "${workflow_version}"
    printf 'TEST_AGENT_WORKFLOW_PYTHON_IMAGE=%s\n' "${TEST_AGENT_WORKFLOW_PYTHON_IMAGE}"
    printf 'TEST_AGENT_WORKFLOW_UV_IMAGE=%s\n' "${TEST_AGENT_WORKFLOW_UV_IMAGE}"
    printf 'TEST_AGENT_WORKFLOW_SYFT_IMAGE=%s\n' "${TEST_AGENT_WORKFLOW_SYFT_IMAGE}"
    printf 'TEST_AGENT_ANALYSIS_TOOLS_IMAGE=%s\n' "${TEST_AGENT_OPENCODE_WORKER_IMAGE}"
    printf 'TEST_AGENT_ANALYSIS_TOOLS_IMAGE_ID=%s\n' "${tools_id}"
    printf 'TEST_AGENT_WORKFLOW_SERVICE_TAG=test-agent-workflow-service\n'
    printf 'TEST_AGENT_RUNNER_CONTROLLER_TAG=test-agent-runner-controller\n'
    printf 'TEST_AGENT_ANALYSIS_TASK_TAG=test-agent-analysis-task\n'
  } >"${build_env}"
  "${SCRIPT_DIR}/package-workflow-offline.sh" \
    --env-file "${build_env}" \
    --output-dir "${workflow_output}" \
    --platform "${PLATFORM}"
  rm -f "${build_env}"
  archive="${workflow_output}/test-agent-workflow-offline-${workflow_version}.tar.gz"
  [[ -f "${archive}" && -f "${archive}.sha256" ]] || {
    echo "Workflow offline archive was not generated" >&2
    exit 1
  }
  cp -a "${archive}" "${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz"
  cp -a "${archive}.sha256" "${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz.sha256"
  {
    printf 'TEST_AGENT_WORKFLOW_RELEASE_VERSION=%s\n' "${workflow_version}"
    printf 'TEST_AGENT_WORKFLOW_ARCHIVE_SHA256=%s\n' "$(sha256_file "${archive}")"
  } >"${OUTPUT_DIR}/.workflow-artifact.env"
}

build_toolbox_image() {
  local image="$1" context="$2" tar_path architecture
  echo "Building ${image} for ${PLATFORM}"
  docker buildx build \
    --platform "${PLATFORM}" \
    -t "${image}" \
    --load \
    --build-arg "TOOLBOX_NODE_BASE_IMAGE=${TEST_AGENT_TOOLBOX_NODE_BASE_IMAGE}" \
    --build-arg "TOOLBOX_NGINX_BASE_IMAGE=${TEST_AGENT_TOOLBOX_NGINX_BASE_IMAGE}" \
    "${context}"
  architecture="$(docker image inspect -f '{{.Architecture}}' "${image}")"
  [[ "${architecture}" == "amd64" ]] || {
    echo "Toolbox image architecture must be amd64, got ${architecture}: ${image}" >&2
    exit 1
  }
  if [[ "${SAVE_TARBALL}" -eq 1 ]]; then
    tar_path="${OUTPUT_DIR}/$(tag_to_tar_name "${image}" "${PLATFORM}")"
    docker save -o "${tar_path}" "${image}"
    write_artifact_checksum "${tar_path}"
    ls -lh "${tar_path}" "${tar_path}.sha256"
  fi
}

package_toolbox() {
  local source_archive="${OUTPUT_DIR}/test-agent-toolbox-source.tar.gz"
  build_toolbox_image "${TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE}" "${ROOT_DIR}/toolbox-source/it-tools"
  build_toolbox_image "${TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE}" "${ROOT_DIR}/toolbox-source/omni-tools"

  # GPL/MIT 完整修改源码、锁定证据、许可证和已校验运行资源随离线包一起交付；构建缓存不入包。
  archive_create_tar_gz "${source_archive}" "${ROOT_DIR}" \
    --exclude='toolbox-source/it-tools/node_modules' \
    --exclude='toolbox-source/it-tools/dist' \
    --exclude='toolbox-source/omni-tools/node_modules' \
    --exclude='toolbox-source/omni-tools/dist' \
    --exclude='toolbox-source/**/.git' \
    toolbox-source
  write_artifact_checksum "${source_archive}"
  cp "${ROOT_DIR}/backend/test-agent-integration/src/main/resources/toolbox/catalog-v1.json" \
    "${OUTPUT_DIR}/toolbox-catalog-v1.json"
  write_artifact_checksum "${OUTPUT_DIR}/toolbox-catalog-v1.json"
  install -m 0644 "${SCRIPT_DIR}/toolbox.env.example" "${OUTPUT_DIR}/toolbox.env.example"
  install -m 0755 "${SCRIPT_DIR}/toolbox-docker.sh" "${OUTPUT_DIR}/toolbox-docker.sh"
  install -m 0755 "${SCRIPT_DIR}/diagnose-toolbox.sh" "${OUTPUT_DIR}/diagnose-toolbox.sh"
  install -m 0644 "${ROOT_DIR}/docs/deployment/toolbox.md" "${OUTPUT_DIR}/TOOLBOX.md"
  if [[ "${SAVE_TARBALL}" -eq 1 ]]; then
    write_toolbox_artifact_state
  fi
}

package_mysql_image() {
  local tar_path architecture
  tar_path="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" "${PLATFORM}")"
  echo "Pulling ${TEST_AGENT_XXL_JOB_MYSQL_IMAGE} for ${PLATFORM}"
  docker pull --platform "${PLATFORM}" "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" >/dev/null
  architecture="$(docker image inspect -f '{{.Architecture}}' "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}")"
  [[ "${architecture}" == "amd64" ]] || {
    echo "MySQL image architecture must be amd64, got ${architecture}" >&2
    exit 1
  }
  if [[ "${SAVE_TARBALL}" -eq 1 ]]; then
    echo "Saving ${TEST_AGENT_XXL_JOB_MYSQL_IMAGE} to ${tar_path}"
    docker save -o "${tar_path}" "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}"
    ls -lh "${tar_path}"
  fi
}

verify_lobehub_artifact_set() {
  local source_dir="$1" required_file path line checksum_line_pattern
  local internal_version upstream_version upstream_commit fork_commit image_ref image_id
  local expected_paths listed_paths
  local lock_file locked_internal_version locked_upstream_version locked_upstream_commit locked_fork_commit locked_postgres_major
  local locked_contract_version
  [[ -d "${source_dir}" ]] || {
    echo "LobeHub artifact directory not found: ${source_dir}" >&2
    exit 1
  }
  if [[ -n "$(find "${source_dir}" -type l -print -quit)" ]]; then
    echo "LobeHub artifact set must not contain symbolic links" >&2
    exit 1
  fi
  if [[ -n "$(find "${source_dir}" ! -type d ! -type f -print -quit)" ]]; then
    echo "LobeHub artifact set may contain only directories and regular files" >&2
    exit 1
  fi
  if [[ -n "$(find "${source_dir}" -type f -name '*[[:space:]]*' -print -quit)" ]]; then
    echo "LobeHub artifact filenames must not contain whitespace" >&2
    exit 1
  fi
  lock_file="${SCRIPT_DIR}/lobehub/version.env"
  [[ -f "${lock_file}" ]] || { echo "LobeHub version lock is missing: ${lock_file}" >&2; exit 1; }
  locked_internal_version="$(state_value "${lock_file}" LOBEHUB_INTERNAL_VERSION)"
  locked_upstream_version="$(state_value "${lock_file}" LOBEHUB_UPSTREAM_VERSION)"
  locked_upstream_commit="$(state_value "${lock_file}" LOBEHUB_UPSTREAM_COMMIT)"
  locked_fork_commit="$(state_value "${lock_file}" LOBEHUB_FORK_COMMIT)"
  locked_postgres_major="$(state_value "${lock_file}" LOBEHUB_PARADEDB_POSTGRES_MAJOR)"
  locked_contract_version="$(state_value "${lock_file}" LOBEHUB_PLATFORM_CONTRACT_VERSION)"
  [[ "${locked_internal_version}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-platform\.[0-9]+$ \
    && "${locked_upstream_version}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ \
    && "${locked_upstream_commit}" =~ ^[0-9a-f]{7}([0-9a-f]{33})?$ \
    && "${locked_fork_commit}" =~ ^[0-9a-f]{40}$ \
    && "${locked_postgres_major}" =~ ^[0-9]+$ \
    && "${locked_contract_version}" =~ ^[0-9]+$ ]] || {
    echo "LobeHub version lock is malformed: ${lock_file}" >&2
    exit 1
  }
  for required_file in \
    release.env SHA256SUMS approved-resources.json LICENSES.txt windows-authenticode-verification.txt \
    linux-client-verification.txt linux-client-acceptance-record.txt \
    linux-client-build-evidence.txt \
    images/lobehub-image.tar images/paradedb-image.tar images/rustfs-image.tar \
    clients/lobehub-windows-x64.exe clients/lobehub-linux-x86_64.tar.gz \
    bin/mc-linux-amd64 sbom/lobehub.spdx.json \
    "source/lobehub-${locked_internal_version}.tar.gz"; do
    [[ -f "${source_dir}/${required_file}" ]] || {
      echo "Required LobeHub artifact is missing: ${required_file}" >&2
      exit 1
    }
  done

  # SHA 清单只允许相对普通路径，禁止校验时逃出制品根目录；并且必须完整覆盖全部普通文件。
  checksum_line_pattern='^([0-9a-f]{64})[[:space:]]+\*?([^[:space:]]+)$'
  listed_paths=""
  while IFS= read -r line || [[ -n "${line}" ]]; do
    if [[ ! "${line}" =~ ${checksum_line_pattern} ]]; then
      echo "Malformed line in LobeHub SHA256SUMS" >&2
      exit 1
    fi
    path="${BASH_REMATCH[2]}"
    if [[ -z "${path}" || "${path}" == /* || "${path}" == ../* || "${path}" == */../* ]]; then
      echo "Unsafe path in LobeHub SHA256SUMS: ${path}" >&2
      exit 1
    fi
    [[ "${path}" != SHA256SUMS && -f "${source_dir}/${path}" ]] || {
      echo "LobeHub SHA256SUMS references a missing or forbidden file: ${path}" >&2
      exit 1
    }
    listed_paths+="${path}"$'\n'
  done <"${source_dir}/SHA256SUMS"
  listed_paths="$(printf '%s' "${listed_paths}" | LC_ALL=C sort)"
  if [[ -n "$(printf '%s\n' "${listed_paths}" | uniq -d)" ]]; then
    echo "LobeHub SHA256SUMS contains duplicate paths" >&2
    exit 1
  fi
  expected_paths="$(cd "${source_dir}" && find . -type f ! -name SHA256SUMS -print \
    | sed 's#^\./##' | LC_ALL=C sort)"
  if [[ "${listed_paths}" != "${expected_paths}" ]]; then
    echo "LobeHub SHA256SUMS must cover every artifact exactly once" >&2
    exit 1
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${source_dir}" && sha256sum -c SHA256SUMS)
  elif command -v shasum >/dev/null 2>&1; then
    (cd "${source_dir}" && shasum -a 256 -c SHA256SUMS)
  else
    echo "Neither sha256sum nor shasum is available" >&2
    exit 1
  fi

  internal_version="$(state_value "${source_dir}/release.env" LOBEHUB_INTERNAL_VERSION)"
  upstream_version="$(state_value "${source_dir}/release.env" LOBEHUB_UPSTREAM_VERSION)"
  upstream_commit="$(state_value "${source_dir}/release.env" LOBEHUB_UPSTREAM_COMMIT)"
  fork_commit="$(state_value "${source_dir}/release.env" LOBEHUB_FORK_COMMIT)"
  [[ "${internal_version}" == "${locked_internal_version}" ]] || {
    echo "Unexpected LobeHub internal version: ${internal_version}" >&2
    exit 1
  }
  [[ "${upstream_version}" == "${locked_upstream_version}" ]] || {
    echo "Unexpected LobeHub upstream version: ${upstream_version}" >&2
    exit 1
  }
  if [[ "${#locked_upstream_commit}" -eq 7 ]]; then
    [[ "${upstream_commit}" == "${locked_upstream_commit}" \
      || "${upstream_commit}" =~ ^${locked_upstream_commit}[0-9a-f]{33}$ ]] || {
      echo "Unexpected LobeHub upstream commit: ${upstream_commit}" >&2
      exit 1
    }
  elif [[ "${upstream_commit}" != "${locked_upstream_commit}" ]]; then
    echo "Unexpected LobeHub upstream commit: ${upstream_commit}" >&2
    exit 1
  fi
  [[ "${fork_commit}" == "${locked_fork_commit}" ]] || {
    echo "Unexpected LobeHub fork commit: ${fork_commit}" >&2
    exit 1
  }
  [[ "$(state_value "${source_dir}/release.env" LOBEHUB_PARADEDB_POSTGRES_MAJOR)" == "${locked_postgres_major}" ]] || {
    echo "LobeHub ParadeDB artifact must use PostgreSQL major ${locked_postgres_major}" >&2
    exit 1
  }
  [[ "$(state_value "${source_dir}/release.env" LOBEHUB_PLATFORM_CONTRACT_VERSION)" == "${locked_contract_version}" ]] || {
    echo "LobeHub platform contract version does not match lock ${locked_contract_version}" >&2
    exit 1
  }
  [[ "$(state_value "${source_dir}/release.env" LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED)" == true ]] || {
    echo "Windows Authenticode verification is not recorded as successful" >&2
    exit 1
  }
  [[ "$(state_value "${source_dir}/release.env" LOBEHUB_LINUX_CLIENT_APPROVED)" == true ]] || {
    echo "Linux client approval is not recorded as successful" >&2
    exit 1
  }
  [[ "$(state_value "${source_dir}/release.env" LOBEHUB_LINUX_EXECUTION_DEFAULT)" == false ]] || {
    echo "Linux local execution must be disabled by default" >&2
    exit 1
  }
  [[ -f "${LOBEHUB_CLIENT_CONTRACT_FILE}" ]] || {
    echo "LobeHub client artifact contract is missing: ${LOBEHUB_CLIENT_CONTRACT_FILE}" >&2
    exit 1
  }
  # 最终打包不能信任外部阶段目录的自报状态，必须重新绑定两端客户端、版本和 fork commit。
  source "${LOBEHUB_CLIENT_CONTRACT_FILE}"
  lobehub_verify_client_artifacts \
    "${source_dir}/clients/lobehub-windows-x64.exe" \
    "${source_dir}/windows-authenticode-verification.txt" \
    "${source_dir}/clients/lobehub-linux-x86_64.tar.gz" \
    "${source_dir}/linux-client-verification.txt" \
    "${source_dir}/linux-client-acceptance-record.txt" \
    "${source_dir}/linux-client-build-evidence.txt" \
    "${locked_internal_version}" "${locked_fork_commit}"
  for image_ref in LOBEHUB_APP_IMAGE LOBEHUB_PARADEDB_IMAGE LOBEHUB_RUSTFS_IMAGE; do
    image_id="$(state_value "${source_dir}/release.env" "${image_ref}_ID")"
    require_lobehub_loadable_image \
      "${image_ref}" "$(state_value "${source_dir}/release.env" "${image_ref}")" "${image_id}"
  done
}

package_lobehub_artifacts() {
  local source_dir target_dir
  [[ -d "${TEST_AGENT_LOBEHUB_ARTIFACT_DIR}" ]] || {
    echo "LobeHub artifact directory not found: ${TEST_AGENT_LOBEHUB_ARTIFACT_DIR}" >&2
    exit 1
  }
  source_dir="$(cd "${TEST_AGENT_LOBEHUB_ARTIFACT_DIR}" && pwd)"
  target_dir="$(cd "${OUTPUT_DIR}" && pwd)/lobehub"
  if [[ "${source_dir}" == "${target_dir}" || "${source_dir}" == "${target_dir}/"* ]]; then
    echo "LobeHub input artifacts must be outside the package output directory" >&2
    exit 1
  fi
  verify_lobehub_artifact_set "${source_dir}"
  rm -rf "${target_dir}"
  mkdir -p "${target_dir}"
  cp -a "${source_dir}/." "${target_dir}/"
  chmod 0755 "${target_dir}/bin/mc-linux-amd64"
  echo "Verified LobeHub artifact set copied to ${target_dir}"
}

package_lobehub_zip() {
  local staging_dir="${OUTPUT_DIR}/.lobehub-release-zip"
  local zip_path="${OUTPUT_DIR}/test-agent-lobehub-offline.zip"
  require_command zip
  verify_lobehub_artifact_set "${OUTPUT_DIR}/lobehub"
  rm -rf "${staging_dir}"
  mkdir -p "${staging_dir}/dist/lobehub" "${staging_dir}/deploy/internal"
  cp -a "${OUTPUT_DIR}/lobehub/." "${staging_dir}/dist/lobehub/"
  for required_file in \
    lobehub.env.example lobehub-docker.sh lobehub-backup.sh install-lobehub-offline.sh \
    lobehub-client-artifact-contract.sh lobehub-platform-probe.mjs lobehub-redis-acl.sh \
    systemd/test-agent-lobehub.service lobehub/version.env nginx/lobehub.conf.template; do
    mkdir -p "${staging_dir}/deploy/internal/$(dirname "${required_file}")"
    cp -a "${SCRIPT_DIR}/${required_file}" "${staging_dir}/deploy/internal/${required_file}"
  done
  mkdir -p "${staging_dir}/docs/deployment" "${staging_dir}/docs/architecture"
  cp -a "${ROOT_DIR}/docs/deployment/lobehub-offline.md" "${staging_dir}/docs/deployment/"
  cp -a "${ROOT_DIR}/docs/deployment/lobehub-client-build.md" "${staging_dir}/docs/deployment/"
  cp -a "${ROOT_DIR}/docs/architecture/lobehub-integration.md" "${staging_dir}/docs/architecture/"
  rm -f "${zip_path}"
  (cd "${staging_dir}" && zip -qr "${zip_path}" .)
  rm -rf "${staging_dir}"
  write_artifact_checksum "${zip_path}"
  ls -lh "${zip_path}" "${zip_path}.sha256"
}

package_release_zip() {
  local staging_dir="${OUTPUT_DIR}/.release-zip"
  local zip_path session_log session_log_count=0
  local worker_tar it_tools_tar omni_tools_tar required_artifact persistence_jar xxl_job_integration_jar

  require_command zip
  require_command rsync
  rm -rf "${staging_dir}"
  mkdir -p "${staging_dir}/dist" "${staging_dir}/deploy/internal"
  zip_path="$(cd "${OUTPUT_DIR}" && pwd)/test-agent-internal-release.zip"
  worker_tar="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
  it_tools_tar="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE}" "${PLATFORM}")"
  omni_tools_tar="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE}" "${PLATFORM}")"

  # 后端与前端每次交付；大体积 worker runtime 和 toolbox 只在指纹变化时加入。
  for required_artifact in \
    "${OUTPUT_DIR}/backend/test-agent-app.jar" \
    "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz"; do
    if [[ ! -f "${required_artifact}" ]]; then
      echo "Required release artifact not found: ${required_artifact}" >&2
      exit 1
    fi
  done
  if [[ "${PACKAGE_WORKFLOW}" -eq 1 ]]; then
    for required_artifact in \
      "${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz" \
      "${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz.sha256" \
      "${OUTPUT_DIR}/.workflow-artifact.env"; do
      if [[ ! -f "${required_artifact}" ]]; then
        echo "Required workflow release artifact not found: ${required_artifact}" >&2
        exit 1
      fi
    done
  fi
  persistence_jar="$(find_unique_persistence_jar "${OUTPUT_DIR}/backend/lib")"
  verify_release_flyway_migrations_jar "${persistence_jar}" "Release ZIP input persistence JAR"
  xxl_job_integration_jar="$(find_unique_xxl_job_integration_jar "${OUTPUT_DIR}/backend/lib")"
  verify_release_xxl_flyway_migrations_jar "${xxl_job_integration_jar}" "Release ZIP input XXL integration JAR"
  if [[ "${WORKER_COMPONENT_MODE}" == included ]]; then
    require_artifact_fingerprint "${OUTPUT_DIR}/.worker-runtime-artifact.env" \
      TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT "${WORKER_RUNTIME_FINGERPRINT}"
    for required_artifact in "${OUTPUT_DIR}/test-agent-programs.tar.gz" "${worker_tar}"; do
      [[ -f "${required_artifact}" ]] || {
        echo "Required worker runtime artifact not found: ${required_artifact}" >&2
        exit 1
      }
    done
  fi
  if [[ "${TOOLBOX_COMPONENT_MODE}" == included ]]; then
    require_artifact_fingerprint "${OUTPUT_DIR}/.toolbox-artifact.env" \
      TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT "${TOOLBOX_FINGERPRINT}"
    for required_artifact in \
      "${it_tools_tar}" "${it_tools_tar}.sha256" \
      "${omni_tools_tar}" "${omni_tools_tar}.sha256" \
      "${OUTPUT_DIR}/test-agent-toolbox-source.tar.gz" \
      "${OUTPUT_DIR}/test-agent-toolbox-source.tar.gz.sha256" \
      "${OUTPUT_DIR}/toolbox-catalog-v1.json" \
      "${OUTPUT_DIR}/toolbox-catalog-v1.json.sha256" \
      "${OUTPUT_DIR}/TOOLBOX.md"; do
      [[ -f "${required_artifact}" ]] || {
        echo "Required toolbox artifact not found: ${required_artifact}" >&2
        exit 1
      }
    done
  fi
  if [[ "${PACKAGE_LOBEHUB}" -eq 1 ]]; then
    verify_lobehub_artifact_set "${OUTPUT_DIR}/lobehub"
  fi
  if [[ "${PACKAGE_MEMORY}" -eq 1 ]]; then
    verify_memory_artifact_set "${OUTPUT_DIR}/memory"
  fi

  # 交付 zip 只放部署所需产物和脚本，避免把 deploy/internal/dist 自身递归打进去。
  mkdir -p "${staging_dir}/dist/backend"
  cp -a "${OUTPUT_DIR}/backend/." "${staging_dir}/dist/backend/"
  cp -a "${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz" "${staging_dir}/dist/"
  if [[ "${PACKAGE_WORKFLOW}" -eq 1 ]]; then
    cp -a "${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz" \
      "${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz.sha256" \
      "${staging_dir}/dist/"
  fi
  if [[ "${WORKER_COMPONENT_MODE}" == included ]]; then
    cp -a "${OUTPUT_DIR}/test-agent-programs.tar.gz" "${worker_tar}" "${staging_dir}/dist/"
  fi
  if [[ "${TOOLBOX_COMPONENT_MODE}" == included ]]; then
    cp -a "${it_tools_tar}" "${it_tools_tar}.sha256" "${staging_dir}/dist/"
    cp -a "${omni_tools_tar}" "${omni_tools_tar}.sha256" "${staging_dir}/dist/"
    cp -a "${OUTPUT_DIR}/test-agent-toolbox-source.tar.gz" \
      "${OUTPUT_DIR}/test-agent-toolbox-source.tar.gz.sha256" \
      "${OUTPUT_DIR}/toolbox-catalog-v1.json" \
      "${OUTPUT_DIR}/toolbox-catalog-v1.json.sha256" \
      "${staging_dir}/dist/"
  fi
  if [[ "${PACKAGE_LOBEHUB}" -eq 1 ]]; then
    mkdir -p "${staging_dir}/dist/lobehub"
    cp -a "${OUTPUT_DIR}/lobehub/." "${staging_dir}/dist/lobehub/"
    mkdir -p "${staging_dir}/docs/deployment" "${staging_dir}/docs/architecture"
    cp -a "${ROOT_DIR}/docs/deployment/lobehub-offline.md" "${staging_dir}/docs/deployment/"
    cp -a "${ROOT_DIR}/docs/architecture/lobehub-integration.md" "${staging_dir}/docs/architecture/"
  fi
  if [[ "${PACKAGE_MEMORY}" -eq 1 ]]; then
    mkdir -p "${staging_dir}/dist/memory" "${staging_dir}/docs/deployment"
    cp -a "${OUTPUT_DIR}/memory/." "${staging_dir}/dist/memory/"
    cp -a "${ROOT_DIR}/docs/deployment/qa-memory.md" "${staging_dir}/docs/deployment/"
  fi

  if [[ "${PACKAGE_MYSQL_IMAGE}" -eq 1 ]]; then
    local mysql_tar
    mysql_tar="${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" "${PLATFORM}")"
    [[ -f "${mysql_tar}" ]] && cp -a "${mysql_tar}" "${staging_dir}/dist/"
  fi

  # 排除默认、当前及历史命名的 dist-* 输出目录，避免旧交付物递归进入新 zip。
  local output_dir_name
  output_dir_name="$(basename "${OUTPUT_DIR}")"
  rsync -a \
    --exclude 'dist' \
    --exclude 'dist-*' \
    --exclude "${output_dir_name}" \
    --exclude '.env' \
    --exclude '.DS_Store' \
    --exclude '._*' \
    --exclude '__MACOSX' \
    --exclude '.Spotlight-V100' \
    --exclude '.Trashes' \
    --exclude '.fseventsd' \
    "${SCRIPT_DIR}/" "${staging_dir}/deploy/internal/"
  install -m 0755 "${ROOT_DIR}/tools/verify-python-libs.sh" \
    "${staging_dir}/deploy/internal/verify-python-libs.sh"
  if [[ "${TOOLBOX_COMPONENT_MODE}" == included ]]; then
    install -m 0644 "${OUTPUT_DIR}/TOOLBOX.md" "${staging_dir}/deploy/internal/TOOLBOX.md"
  fi
  {
    printf 'TEST_AGENT_RELEASE_COMPONENT_MANIFEST_VERSION=1\n'
    printf 'TEST_AGENT_RELEASE_WORKER_RUNTIME=%s\n' "${WORKER_COMPONENT_MODE}"
    printf 'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=%s\n' "${WORKER_RUNTIME_FINGERPRINT}"
    printf 'TEST_AGENT_RELEASE_TOOLBOX=%s\n' "${TOOLBOX_COMPONENT_MODE}"
    printf 'TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=%s\n' "${TOOLBOX_FINGERPRINT}"
    printf 'TEST_AGENT_RELEASE_WORKFLOW=%s\n' "$([[ "${PACKAGE_WORKFLOW}" -eq 1 ]] && printf included || printf disabled)"
    printf 'TEST_AGENT_RELEASE_WORKFLOW_VERSION=%s\n' "$([[ "${PACKAGE_WORKFLOW}" -eq 1 ]] && state_value "${OUTPUT_DIR}/.workflow-artifact.env" TEST_AGENT_WORKFLOW_RELEASE_VERSION || printf none)"
    printf 'TEST_AGENT_RELEASE_WORKFLOW_ARCHIVE_SHA256=%s\n' "$([[ "${PACKAGE_WORKFLOW}" -eq 1 ]] && state_value "${OUTPUT_DIR}/.workflow-artifact.env" TEST_AGENT_WORKFLOW_ARCHIVE_SHA256 || printf none)"
    printf 'TEST_AGENT_RELEASE_LOBEHUB=%s\n' "$([[ "${PACKAGE_LOBEHUB}" -eq 1 ]] && printf included || printf disabled)"
    printf 'TEST_AGENT_RELEASE_LOBEHUB_VERSION=%s\n' "$([[ "${PACKAGE_LOBEHUB}" -eq 1 ]] && state_value "${OUTPUT_DIR}/lobehub/release.env" LOBEHUB_INTERNAL_VERSION || printf none)"
    printf 'TEST_AGENT_RELEASE_MEMORY=%s\n' "$([[ "${PACKAGE_MEMORY}" -eq 1 ]] && printf included || printf disabled)"
    printf 'TEST_AGENT_RELEASE_MEMORY_VERSION=%s\n' "$([[ "${PACKAGE_MEMORY}" -eq 1 ]] && state_value "${OUTPUT_DIR}/memory/release.env" TEST_AGENT_MEMORY_RELEASE_VERSION || printf none)"
  } >"${staging_dir}/deploy/internal/release-components.env"
  chmod 0644 "${staging_dir}/deploy/internal/release-components.env"
  # 升级脚本和官方启动器共用这份忽略清单；任一文件漏包都会让存量节点或新增节点重新出现 Git 脏状态。
  for required_artifact in \
    "${staging_dir}/deploy/internal/ensure-opencode-runtime-gitignore.sh" \
    "${staging_dir}/deploy/internal/opencode-runtime.gitignore"; do
    if [[ ! -f "${required_artifact}" ]]; then
      echo "Required OpenCode Git ignore deployment artifact not found: ${required_artifact}" >&2
      exit 1
    fi
  done

  # 会话日志属于本次交付基线，保留原始文件名放入 .agents，便于内网追溯变更、坑点和未完成事项。
  mkdir -p "${staging_dir}/.agents"
  for session_log in "${ROOT_DIR}"/.agents/session-log*.md; do
    [[ -f "${session_log}" ]] || continue
    install -m 0644 "${session_log}" "${staging_dir}/.agents/$(basename "${session_log}")"
    session_log_count=$((session_log_count + 1))
  done
  if [[ "${session_log_count}" -eq 0 ]]; then
    echo "No .agents/session-log*.md files found for release provenance" >&2
    exit 1
  fi

  rm -f "${zip_path}"
  archive_create_zip "${zip_path}" "${staging_dir}" .
  rm -rf "${staging_dir}"
  ls -lh "${zip_path}"
}

write_release_checksum() {
  local zip_path="${OUTPUT_DIR}/test-agent-internal-release.zip"
  local zip_name
  zip_name="$(basename "${zip_path}")"

  # 校验文件只记录包名，复制到企业服务器任意目录后仍可直接执行校验命令。
  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${OUTPUT_DIR}" && sha256sum "${zip_name}" >"${zip_name}.sha256")
  elif command -v shasum >/dev/null 2>&1; then
    (cd "${OUTPUT_DIR}" && shasum -a 256 "${zip_name}" >"${zip_name}.sha256")
  else
    echo "Neither sha256sum nor shasum is available; cannot create release checksum" >&2
    exit 1
  fi
  archive_strip_file_metadata "${zip_path}" "${zip_path}.sha256"
  cat "${zip_path}.sha256"
}

export_worker_programs() {
  local programs_dir="${OUTPUT_DIR}/programs"
  local container_id
  container_id="$(docker create --platform "${PLATFORM}" "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" true)"

  rm -rf "${programs_dir}"
  mkdir -p "${programs_dir}/bin" "${programs_dir}/opencode" "${programs_dir}/codex"

  if ! docker cp "${container_id}:/usr/local/bin/opencode-manager" "${programs_dir}/bin/opencode-manager" \
    || ! docker cp "${container_id}:/usr/local/lib/opencode/." "${programs_dir}/opencode/" \
    || ! docker cp "${container_id}:/usr/local/lib/codex/." "${programs_dir}/codex/"; then
    docker rm -f "${container_id}" >/dev/null 2>&1 || true
    return 1
  fi
  docker rm -f "${container_id}" >/dev/null

  chmod +x "${programs_dir}/bin/opencode-manager" || true
  chmod +x "${programs_dir}/opencode/bin/opencode" || true
  chmod +x "${programs_dir}/codex/bin/codex-official" "${programs_dir}/codex/bin/codex-resources/bwrap" "${programs_dir}/codex/bin/test-agent-codex-mcp" || true
  printf 'official opencode: %s\nasset: %s\narchive size: %s\narchive sha256: %s\nrelease commit: %s\nofficial codex: %s\ncodex asset: %s\ncodex archive size: %s\ncodex archive sha256: %s\ncodex bwrap asset: %s\ncodex bwrap archive size: %s\ncodex bwrap archive sha256: %s\ncodex bwrap binary sha256: %s\n' \
    "${OPENCODE_VERSION}" \
    "${OPENCODE_ASSET_NAME}" \
    "${OPENCODE_ASSET_SIZE}" \
    "${OPENCODE_ASSET_SHA256}" \
    "${OPENCODE_RELEASE_COMMIT}" \
    "${CODEX_VERSION}" \
    "${CODEX_ASSET_NAME}" \
    "${CODEX_ASSET_SIZE}" \
    "${CODEX_ASSET_SHA256}" \
    "${CODEX_BWRAP_ASSET_NAME}" \
    "${CODEX_BWRAP_ASSET_SIZE}" \
    "${CODEX_BWRAP_ASSET_SHA256}" \
    "${CODEX_BWRAP_BINARY_SHA256}" >"${programs_dir}/VERSION"
  archive_create_tar_gz "${OUTPUT_DIR}/test-agent-programs.tar.gz" "${OUTPUT_DIR}" programs
  ls -lh "${OUTPUT_DIR}/test-agent-programs.tar.gz"
}

load_dotenv "${ENV_FILE}"

if [[ "${OUTPUT_DIR_FROM_ARG}" -eq 0 && -n "${TEST_AGENT_IMAGE_OUTPUT_DIR:-}" && ( "${ENV_FILE_FROM_ARG}" -eq 1 || -n "${OUTPUT_DIR_FROM_ENV_BEFORE_DOTENV}" ) ]]; then
  if [[ "${TEST_AGENT_IMAGE_OUTPUT_DIR}" = /* ]]; then
    OUTPUT_DIR="${TEST_AGENT_IMAGE_OUTPUT_DIR}"
  else
    OUTPUT_DIR="${ROOT_DIR}/${TEST_AGENT_IMAGE_OUTPUT_DIR}"
  fi
fi

TEST_AGENT_OPENCODE_WORKER_IMAGE="${TEST_AGENT_OPENCODE_WORKER_IMAGE:-test-agent-opencode-worker:internal}"
TEST_AGENT_WORKFLOW_PYTHON_IMAGE="${TEST_AGENT_WORKFLOW_PYTHON_IMAGE:-ghcr.io/astral-sh/uv:0.8.14-python3.12-bookworm-slim@sha256:b748a09ec61c993083278020a84e16f0c81485c5349b30c64dd29d6a22da7462}"
TEST_AGENT_WORKFLOW_UV_IMAGE="${TEST_AGENT_WORKFLOW_UV_IMAGE:-ghcr.io/astral-sh/uv:0.8.14@sha256:f3660c56d5b08d6c516360981bedc439f499b9bf37f46a216018da3777a74011}"
TEST_AGENT_WORKFLOW_SYFT_IMAGE="${TEST_AGENT_WORKFLOW_SYFT_IMAGE:-ghcr.io/anchore/syft:v1.30.0@sha256:bd5357d2cd087f03af748dac24df48bfbc1723080d78f75f69aca1f2d429060e}"
TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE="${TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE:-test-agent/it-tools:2024.10.22-7ca5933-platform.2}"
TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE="${TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE:-test-agent/omni-tools:0.6.0-platform.1}"
TEST_AGENT_TOOLBOX_NODE_BASE_IMAGE="${TEST_AGENT_TOOLBOX_NODE_BASE_IMAGE:-node:20.18.0-alpine3.20@sha256:a1d39fe127e43881c6770abf2f0843c955607fb56eb9b45bf6f103c992c5442a}"
TEST_AGENT_TOOLBOX_NGINX_BASE_IMAGE="${TEST_AGENT_TOOLBOX_NGINX_BASE_IMAGE:-nginx:1.27.2-alpine3.20@sha256:d213b2a02ef4e7ec85882e8955343cdd08ab49d6548995ad18623f47017c65ee}"
TEST_AGENT_XXL_JOB_MYSQL_IMAGE="${TEST_AGENT_XXL_JOB_MYSQL_IMAGE:-mysql:8.4}"
TEST_AGENT_LOBEHUB_ARTIFACT_DIR="${TEST_AGENT_LOBEHUB_ARTIFACT_DIR:-${ROOT_DIR}/lobehub-release-artifacts}"
TEST_AGENT_MEMORY_BUILD_ENV_FILE="${TEST_AGENT_MEMORY_BUILD_ENV_FILE:-${SCRIPT_DIR}/memory/build.env.example}"
if [[ "${TEST_AGENT_LOBEHUB_ARTIFACT_DIR}" != /* ]]; then
  TEST_AGENT_LOBEHUB_ARTIFACT_DIR="${ROOT_DIR}/${TEST_AGENT_LOBEHUB_ARTIFACT_DIR}"
fi
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com}"
GOPROXY="${GOPROXY:-https://goproxy.cn,direct}"
DEBIAN_MIRROR="${DEBIAN_MIRROR:-https://mirrors.ustc.edu.cn/debian}"
DEBIAN_SECURITY_MIRROR="${DEBIAN_SECURITY_MIRROR:-https://mirrors.ustc.edu.cn/debian-security}"
PYTHON_VERSION="${PYTHON_VERSION:-3.13.14}"
PYTHON_SOURCE_SIZE="${PYTHON_SOURCE_SIZE:-23021880}"
PYTHON_SOURCE_SHA256="${PYTHON_SOURCE_SHA256:-639e43243c620a308f968213df9e00f2f8f62332f7adbaa7a7eeb9783057c690}"
PYTHON_SOURCE_BASE_URL="${PYTHON_SOURCE_BASE_URL:-https://mirrors.huaweicloud.com/python}"
PYTHON_PACKAGE_INDEX_URL="${PYTHON_PACKAGE_INDEX_URL:-https://mirrors.huaweicloud.com/repository/pypi/simple}"
OPENCODE_VERSION="${OPENCODE_VERSION:-1.18.4}"
OPENCODE_RELEASE_COMMIT="${OPENCODE_RELEASE_COMMIT:-49c69c5ed3ccf706b61b3febb43c8aaff7f8325e}"
OPENCODE_ASSET_NAME="${OPENCODE_ASSET_NAME:-opencode-linux-x64-baseline.tar.gz}"
OPENCODE_ASSET_SIZE="${OPENCODE_ASSET_SIZE:-59265643}"
OPENCODE_ASSET_SHA256="${OPENCODE_ASSET_SHA256:-4d87e414607b77fef940256021e42fbbf37b8c62b06ced76b69e26c5dcbfbabc}"
OPENCODE_BINARY_SHA256="${OPENCODE_BINARY_SHA256:-6ce6570e7db9a40e7bd3304ebdfff607920bde8cafd2eb5587bd7a26f89ba0b5}"
OPENCODE_RELEASE_BASE_URL="${OPENCODE_RELEASE_BASE_URL:-https://github.com/anomalyco/opencode/releases/download}"
CODEX_VERSION="${CODEX_VERSION:-0.145.0}"
CODEX_ASSET_NAME="${CODEX_ASSET_NAME:-codex-x86_64-unknown-linux-musl.tar.gz}"
CODEX_ASSET_SIZE="${CODEX_ASSET_SIZE:-113724150}"
CODEX_ASSET_SHA256="${CODEX_ASSET_SHA256:-bfaf13c9ba34f2ad764e4a916c49cf7177aeba329cf0f719e2227566fc8d662a}"
CODEX_BWRAP_ASSET_NAME="${CODEX_BWRAP_ASSET_NAME:-bwrap-x86_64-unknown-linux-musl.tar.gz}"
CODEX_BWRAP_ASSET_SIZE="${CODEX_BWRAP_ASSET_SIZE:-261563}"
CODEX_BWRAP_ASSET_SHA256="${CODEX_BWRAP_ASSET_SHA256:-bf829ae02652acdb13732e3b00b3e656baaa56be2a65d50309d676df2b5d7581}"
CODEX_BWRAP_BINARY_SHA256="${CODEX_BWRAP_BINARY_SHA256:-77360cb751ccedc5971391444ac86a8a33c15b04d6b4a6fe45f5d25496e62c4c}"
CODEX_RELEASE_BASE_URL="${CODEX_RELEASE_BASE_URL:-https://github.com/openai/codex/releases/download}"
OPENCODE_RUNTIME_PACKAGE_JSON="${OPENCODE_RUNTIME_PACKAGE_JSON:-deploy/internal/opencode-node-runtime.package.json}"
OPENCODE_RUNTIME_PACKAGE_LOCK="${OPENCODE_RUNTIME_PACKAGE_LOCK:-deploy/internal/opencode-node-runtime.package-lock.json}"
GO_IMAGE="${GO_IMAGE:-golang@sha256:e87b2a5f6df2dff71ea330d55d54f4979eb380ae58a7e3aabc9d53121243e689}"
NODE_IMAGE="${NODE_IMAGE:-node@sha256:b042c6d46a90773b82ea3f95b05457ea93ee127a73b1b47ad5ebbb1a08ec3df8}"
VITE_TEST_AGENT_API_BASE_URL="${VITE_TEST_AGENT_API_BASE_URL:-}"

if [[ "${PACKAGE_LOBEHUB}" -eq 1 \
  && "${PACKAGE_MODE}" != full \
  && "${PACKAGE_MODE}" != zip-only \
  && "${PACKAGE_MODE}" != lobehub-only ]]; then
  echo "--with-lobehub can only be combined with the full or --zip-only release mode" >&2
  exit 2
fi

mkdir -p "${OUTPUT_DIR}"
bash "${SCRIPT_DIR}/validate-opencode-models.sh" \
  "${SCRIPT_DIR}/opencode-models.json" \
  "${SCRIPT_DIR}/opencode.jsonc.example"
if [[ -z "${COMPONENT_STATE_FILE}" ]]; then
  COMPONENT_STATE_FILE="${OUTPUT_DIR}/.release-component-state.env"
fi
mkdir -p "$(dirname "${COMPONENT_STATE_FILE}")"

WORKER_COMPONENT_MODE=reuse
TOOLBOX_COMPONENT_MODE=reuse
WORKER_RUNTIME_FINGERPRINT=""
TOOLBOX_FINGERPRINT=""
if [[ "${PACKAGE_MODE}" == full || "${PACKAGE_MODE}" == zip-only \
  || "${PACKAGE_MODE}" == opencode-only || "${PACKAGE_MODE}" == toolbox-only \
  || "${COMPONENT_PLAN_ONLY}" -eq 1 ]]; then
  require_command git
  plan_release_components
fi
if [[ "${COMPONENT_PLAN_ONLY}" -eq 1 ]]; then
  exit 0
fi
if [[ "${PACKAGE_MODE}" == full ]]; then
  [[ "${WORKER_COMPONENT_MODE}" != reuse ]] || PACKAGE_OPENCODE_WORKER=0
  [[ "${TOOLBOX_COMPONENT_MODE}" != reuse ]] || PACKAGE_TOOLBOX=0
fi

if [[ "${PACKAGE_TOOLBOX}" -eq 1 ]]; then
  require_platform_image "TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE" "${TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE}" "test-agent/it-tools" "2024.10.22-7ca5933-platform.2"
  require_platform_image "TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE" "${TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE}" "test-agent/omni-tools" "0.6.0-platform.1"
  require_digest_pinned_image "TEST_AGENT_TOOLBOX_NODE_BASE_IMAGE" "${TEST_AGENT_TOOLBOX_NODE_BASE_IMAGE}"
  require_digest_pinned_image "TEST_AGENT_TOOLBOX_NGINX_BASE_IMAGE" "${TEST_AGENT_TOOLBOX_NGINX_BASE_IMAGE}"
fi
if [[ "${PACKAGE_WORKFLOW}" -eq 1 ]]; then
  require_digest_pinned_image "TEST_AGENT_WORKFLOW_PYTHON_IMAGE" "${TEST_AGENT_WORKFLOW_PYTHON_IMAGE}"
  require_digest_pinned_image "TEST_AGENT_WORKFLOW_UV_IMAGE" "${TEST_AGENT_WORKFLOW_UV_IMAGE}"
  require_digest_pinned_image "TEST_AGENT_WORKFLOW_SYFT_IMAGE" "${TEST_AGENT_WORKFLOW_SYFT_IMAGE}"
fi

echo "Using env file: ${ENV_FILE}"
echo "Output dir: ${OUTPUT_DIR}"
echo "Platform: ${PLATFORM}"

if [[ "${PACKAGE_BACKEND}" -eq 1 ]]; then
  configure_java_home
  require_command mvn
  package_backend
fi

if [[ "${PACKAGE_FRONTEND}" -eq 1 ]]; then
  require_command corepack
  package_frontend
fi

if [[ "${PACKAGE_OPENCODE_WORKER}" -eq 1 ]]; then
  require_command docker
  build_opencode_worker_image
fi

if [[ "${PACKAGE_PYTHON_LIBS}" -eq 1 ]]; then
  require_command docker
  PYTHON_PACKAGE_INDEX_URL="${PYTHON_PACKAGE_INDEX_URL}" \
    "${SCRIPT_DIR}/package-python-libs.sh" \
      --image "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" \
      --output-dir "${OUTPUT_DIR}" \
      --platform "${PLATFORM}"
fi

if [[ "${PACKAGE_WORKFLOW}" -eq 1 && "${SAVE_TARBALL}" -eq 1 \
  && "${PACKAGE_MODE}" != zip-only ]]; then
  require_command docker
  package_workflow_images
fi

if [[ "${PACKAGE_TOOLBOX}" -eq 1 ]]; then
  require_command docker
  package_toolbox
fi

if [[ "${PACKAGE_MYSQL_IMAGE}" -eq 1 ]]; then
  require_command docker
  package_mysql_image
fi

if [[ "${PACKAGE_LOBEHUB}" -eq 1 && "${PACKAGE_MODE}" != zip-only ]]; then
  package_lobehub_artifacts
fi

if [[ "${PACKAGE_MEMORY}" -eq 1 && "${PACKAGE_MODE}" != zip-only ]]; then
  "${SCRIPT_DIR}/package-memory-offline.sh" \
    --env-file "${TEST_AGENT_MEMORY_BUILD_ENV_FILE}" \
    --output-dir "${OUTPUT_DIR}" \
    --platform "${PLATFORM}"
fi

if [[ "${PACKAGE_MODE}" == lobehub-only && "${PACKAGE_ZIP}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  package_lobehub_zip
fi

if [[ "${PACKAGE_ZIP}" -eq 1 && "${SAVE_TARBALL}" -eq 1 \
  && ( "${PACKAGE_MODE}" == full || "${PACKAGE_MODE}" == zip-only ) ]]; then
  package_release_zip
  write_release_checksum
  write_component_fingerprints "${COMPONENT_STATE_FILE}"
fi

echo
echo "Artifacts:"
if [[ "${PACKAGE_BACKEND}" -eq 1 ]]; then
  echo "  backend jar: ${OUTPUT_DIR}/backend/test-agent-app.jar"
fi
if [[ "${PACKAGE_FRONTEND}" -eq 1 ]]; then
  echo "  frontend dist: ${OUTPUT_DIR}/frontend"
  echo "  frontend archive: ${OUTPUT_DIR}/test-agent-frontend-dist.tar.gz"
fi
if [[ "${PACKAGE_OPENCODE_WORKER}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  echo "  opencode worker image tar: ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
  echo "  external programs: ${OUTPUT_DIR}/programs"
  echo "  external programs archive: ${OUTPUT_DIR}/test-agent-programs.tar.gz"
  echo
  echo "Target import:"
  echo "  docker load -i ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_OPENCODE_WORKER_IMAGE}" "${PLATFORM}")"
fi
if [[ "${PACKAGE_PYTHON_LIBS}" -eq 1 ]]; then
  echo "  Python libraries: ${OUTPUT_DIR}/test-agent-python-libs-py313-linux-amd64.tar.gz"
  echo "  Python libraries checksum: ${OUTPUT_DIR}/test-agent-python-libs-py313-linux-amd64.tar.gz.sha256"
fi
if [[ "${PACKAGE_WORKFLOW}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  echo "  workflow offline archive: ${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz"
  echo "  workflow checksum: ${OUTPUT_DIR}/test-agent-workflow-offline.tar.gz.sha256"
fi
if [[ "${PACKAGE_TOOLBOX}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  echo "  IT-Tools image tar: ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE}" "${PLATFORM}")"
  echo "  OmniTools image tar: ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE}" "${PLATFORM}")"
  echo "  toolbox modified source: ${OUTPUT_DIR}/test-agent-toolbox-source.tar.gz"
  echo "  toolbox catalog: ${OUTPUT_DIR}/toolbox-catalog-v1.json"
  echo "  toolbox deployment kit: ${OUTPUT_DIR}/toolbox.env.example, toolbox-docker.sh, diagnose-toolbox.sh, TOOLBOX.md"
fi
if [[ "${PACKAGE_MYSQL_IMAGE}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  echo "  MySQL image tar: ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" "${PLATFORM}")"
  echo "  MySQL target import: docker load -i ${OUTPUT_DIR}/$(tag_to_tar_name "${TEST_AGENT_XXL_JOB_MYSQL_IMAGE}" "${PLATFORM}")"
fi
if [[ "${PACKAGE_LOBEHUB}" -eq 1 ]]; then
  echo "  LobeHub verified artifacts: ${OUTPUT_DIR}/lobehub"
fi
if [[ "${PACKAGE_MEMORY}" -eq 1 ]]; then
  echo "  memory data-plane artifacts: ${OUTPUT_DIR}/memory"
fi
if [[ "${PACKAGE_MODE}" == lobehub-only && "${PACKAGE_ZIP}" -eq 1 && "${SAVE_TARBALL}" -eq 1 ]]; then
  echo "  LobeHub offline zip: ${OUTPUT_DIR}/test-agent-lobehub-offline.zip"
  echo "  LobeHub offline checksum: ${OUTPUT_DIR}/test-agent-lobehub-offline.zip.sha256"
fi
if [[ "${PACKAGE_ZIP}" -eq 1 && "${SAVE_TARBALL}" -eq 1 \
  && ( "${PACKAGE_MODE}" == full || "${PACKAGE_MODE}" == zip-only ) ]]; then
  echo "  complete release zip: ${OUTPUT_DIR}/test-agent-internal-release.zip"
  echo "  release checksum: ${OUTPUT_DIR}/test-agent-internal-release.zip.sha256"
  echo "  worker runtime component: ${WORKER_COMPONENT_MODE}"
  echo "  toolbox component: ${TOOLBOX_COMPONENT_MODE}"
fi
