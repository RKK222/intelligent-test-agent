#!/usr/bin/env bash
set -euo pipefail

ARCHIVE="/data/0709/test-agent-internal-release.zip"
EXTRACT_DIR="/data/0709/test-agent-internal-release"
EXTRACT_DIR_EXPLICIT=0
INSTALL_ROOT="/data/testagent"
BACKEND_HOST="122.233.30.114"
FRONTEND_HOST="122.233.30.2"
FRONTEND_USER=""
FRONTEND_ROOT="/data/testagent"
BACKEND_SERVICE="test-agent-backend"
BACKEND_PORT=8080
BACKEND_HEALTH_URL=""
BACKEND_READINESS_URL=""
FRONTEND_HEALTH_URL="http://122.233.30.2/health"
FRONTEND_URL="http://122.233.30.2/"
NGINX_ENV="/data/testagent/config/nginx.env"
DOCKER_ENV="/data/testagent/config/docker.env"
EXPECTED_SERVER_ID=""
EXPECTED_SERVER_HOST=""
SKIP_FRONTEND=0
SKIP_WORKER=0
SKIP_WORKER_EXPLICIT=0
WORKER_RUNTIME_REUSE=0
KEEP_EXTRACT=0
VALIDATE_ONLY=0
SYSTEMD_UNIT_DIR="${TEST_AGENT_SYSTEMD_UNIT_DIR:-/etc/systemd/system}"
TOOLBOX_ENTERPRISE_MIGRATION_RESOURCE="db/migration/V20260728160800__create_toolbox_click_tracking.sql"
TOOLBOX_ENTERPRISE_MIGRATION_SHA256="777a96f12342b0cc049748a6f910e56214a4c8ca52488e1429edb1409adb51f2"
SUPPORT_ACCESS_MIGRATION_RESOURCE="db/migration/V20260805132000__create_support_access_audit.sql"
SUPPORT_ACCESS_MIGRATION_SHA256="54cea9a84948f8e4cee14d630772b8ee0668c2a7e5fc897ede5e792a15edd761"
SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE="db/migration/V20260806143000__classify_skill_hub_assets.sql"
SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256="f59f641527fdabaf21393319cd70ed578c6f75a55decae4d8839bc2b561ac06d"
PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_RESOURCE="db/migration/V20260806190000__persist_public_skill_hub_snapshots.sql"
PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_SHA256="1b2547cf466c09fe11a63b1f76e5e17ec1773e2187aa01e052288a9bb4861e75"
PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE="db/migration/V20260806190500__classify_public_skill_hub_snapshots.sql"
PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256="19a0e5af5f361179ac3887d541c274f75f43f89a683ee8037a5e0391444a92bf"
QA_MEMORY_MAIN_MIGRATION_RESOURCE="db/migration/V20260809120000__create_qa_memory_governance.sql"
MEMORY_PROFILE_MIGRATION_RESOURCE="db/migration/V20260809230000__generalize_memory_and_embedding_profiles.sql"
QA_MEMORY_IDENTITY_MAIN_MIGRATION_RESOURCE="db/migration/V20260810090000__enforce_qa_memory_identity.sql"
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
INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_MIGRATION_RESOURCE="db/migration/V20260810234154__internal_model_call_records_add_token_latency_inputs.sql"
INTERNAL_MODEL_CREATE_MIGRATION_SHA256="f214dfd0d4f26de830452d9f4121bc938cf031e4867555d5248e159d99377084"
INTERNAL_MODEL_FIRST_TOKEN_MIGRATION_SHA256="de7188e3ba5d01148a655dbc238783cf7881abf168bd7b6e422c9f2fa118a5c3"
INTERNAL_MODEL_STREAM_MIGRATION_SHA256="46f0a8e687f59c037a7e02cb1f9ba3db4893633ba20edd67d4ae75f0b6fd0d9e"
INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_MIGRATION_SHA256="f684bd5d323d3816fc7ae982eff7b45256763f467f540020af41753c04fb837b"
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
QA_MEMORY_AFTER_TOKEN_LATENCY_MIGRATION_RESOURCE="db/migration-compat/qa-memory-after-token-latency-inputs/V20260811170050__qa_memories_create_governance_after_token_latency_inputs.sql"
QA_MEMORY_AFTER_TOKEN_LATENCY_MIGRATION_SHA256="44ea89c0ea5b9edb7fc5cbfb682e540b251f0c106b1d3c2762576d04ade6f984"
SESSION_SHARE_MAIN_MIGRATION_RESOURCE="db/migration/V20260809170000__session_shares_create_collaboration_share.sql"
SESSION_SHARE_FORWARD_MIGRATION_RESOURCE="db/migration-compat/qa-memory-extended/V20260810110001__session_shares_create_collaboration_share_after_qa_memory.sql"
SESSION_SHARE_ATTRIBUTION_MAIN_MIGRATION_RESOURCE="db/migration/V20260809170001__session_messages_add_delegated_attribution.sql"
SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION_RESOURCE="db/migration-compat/qa-memory-extended/V20260810110002__session_messages_add_delegated_attribution_after_qa_memory.sql"
SESSION_SHARE_MIGRATION_SHA256="b0b04355fcfe64f3d22d8a8ff297fa62a30db9d97bf6bf82968588f5da72d0c9"
SESSION_SHARE_ATTRIBUTION_MIGRATION_SHA256="dfb5d65b474416c28ec6131e95c7b9e7f744f9d2903c0bc4fcd0065632a4eee5"
USER_NOTIFICATION_MIGRATION_RESOURCE="db/migration/V20260810170000__user_notifications_create_notification_center.sql"
USER_NOTIFICATION_MIGRATION_SHA256="4592eb72a69179ca91febe43278ce8ed70fe02979f7b5c0f7366004048510ca9"
USER_NOTIFICATION_DISPOSE_MIGRATION_RESOURCE="db/migration/V20260811213000__user_notifications_expand_dispose_types.sql"
USER_NOTIFICATION_DISPOSE_MIGRATION_SHA256="00bd72f2efe1916d8a33fc5310d59936c6950d3fd81e8fce91eda529ffb5096c"
EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_RESOURCE="db/migration-compat/experience-workspace-applied/V20260809210000__common_parameters_add_experience_workspace.sql"
EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_SHA256="c093695aac4305aed3caeb8fcec58f0731f1519527031f1775adaf8be86cf24a"
EXPERIENCE_WORKSPACE_FORWARD_MIGRATION_RESOURCE="db/migration/V20260812104911__common_parameters_add_experience_workspace_after_release.sql"
EXPERIENCE_WORKSPACE_FORWARD_MIGRATION_SHA256="a613f77fd42aea5f404dfb51bad5fe93c1f478d73bf131de8c9dc9931a27e5ea"
EXPERIENCE_WORKSPACE_DEFAULT_MIGRATION_RESOURCE="db/migration/V20260812144051__common_parameters_default_experience_workspace.sql"
EXPERIENCE_WORKSPACE_DEFAULT_MIGRATION_SHA256="e07d560ac0652860ed8e8788b002df0881eface861998a20e4e83da85276bfcf"
ANALYTICS_OUTBOX_MIGRATION_RESOURCE="db/migration/V20260813143000__analytics_event_outbox_create_pipeline.sql"
ANALYTICS_OUTBOX_MIGRATION_SHA256="bd286b1d992e6ff715393fb39bbb47a7d44dfe425c3b4ea6571f62e74eed0eb1"
ANALYTICS_POSTGRES_TRIGGER_MIGRATION_RESOURCE="db/migration-postgresql/V20260813143001__analytics_event_outbox_install_triggers.sql"
ANALYTICS_POSTGRES_TRIGGER_MIGRATION_SHA256="399e8db352ded3f12d5b5a91fe8a07f6242a9aafc07caa8c28589614a43dc50e"
ANALYTICS_CLICKHOUSE_MIGRATION_RESOURCE="db/clickhouse/V20260813150000__analytics_activity_facts_create_tables.sql"
ANALYTICS_CLICKHOUSE_MIGRATION_SHA256="1a1d4d77b2d92f6f97a864da7a20b6d5f040807d15f2eef940410c10e7e7a7f7"
LOCAL_CLIENT_RUNTIME_APPLIED_MIGRATION_RESOURCE="db/migration-compat/local-client-runtime-applied/V20260812202425__local_client_credentials_create_runtime_after_release.sql"
LOCAL_CLIENT_RUNTIME_APPLIED_MIGRATION_SHA256="168cbf7bf3c1a062c8fd38057cd32726804ab8bf00ced1dff39d5c2837c53026"
AUTOMATION_CODE_REPOSITORY_MIGRATION_RESOURCE="db/migration/V20260812204207__dictionaries_add_automation_code_repository.sql"
AUTOMATION_CODE_REPOSITORY_MIGRATION_SHA256="250c2761c9717cca6e689019a9a91f0cc66d52a33baa662b294e41b1d1745554"
XXL_INTERNAL_MODEL_PROBE_MIGRATION_RESOURCE="xxl-job/db/migration/V10__register_internal_model_probe_task.sql"
XXL_INTERNAL_MODEL_PROBE_MIGRATION_SHA256="665b22835a9871828fcaceca2941d1ca83de248698fde76f3380b12bec49fb47"
XXL_INTERNAL_MODEL_RETENTION_MIGRATION_RESOURCE="xxl-job/db/migration/V11__register_internal_model_observability_retention_task.sql"
XXL_INTERNAL_MODEL_RETENTION_MIGRATION_SHA256="03e7054a56daac14bd1cb62fd2302c7752c5d93ba88f255ad8d10f7320736236"
XXL_ANALYTICS_INGESTION_MIGRATION_RESOURCE="xxl-job/db/migration/V12__register_analytics_clickhouse_ingestion_task.sql"
XXL_ANALYTICS_INGESTION_MIGRATION_SHA256="70878c4544d5d8c030b1edf59406a320ceec68f86bd763d366a80d5d4ed005f0"
RELEASE_PERSISTENCE_JAR=""
RELEASE_PERSISTENCE_JAR_SHA256=""
RELEASE_XXL_JOB_INTEGRATION_JAR=""
RELEASE_XXL_JOB_INTEGRATION_JAR_SHA256=""

usage() {
  cat <<'USAGE'
Usage: deploy/internal/deploy-internal-release.sh [options]

Deploy an enterprise internal release zip from a backend server. The default
matches the current single-backend enterprise deployment:
  - release zip: /data/0709/test-agent-internal-release.zip
  - backend/worker server: local 122.233.30.114
  - frontend server: 122.233.30.2
  - install root: /data/testagent

Options:
  --archive <path>              Release zip path. Default: /data/0709/test-agent-internal-release.zip.
  --extract-dir <path>          Temporary unzip directory. Default: /data/0709/test-agent-internal-release.
  --install-root <path>         Backend install root. Default: /data/testagent.
  --backend-host <host>         Local backend advertised host. Default: 122.233.30.114.
  --frontend-host <host>        Frontend SSH/SCP host. Default: 122.233.30.2.
  --frontend-user <user>        SSH user for frontend host. Default: current ssh config user.
  --frontend-root <path>        Frontend install root. Default: /data/testagent.
  --backend-service <name>      systemd service name. Default: test-agent-backend.
  --backend-port <port>         Backend listen port. Default: 8080.
  --backend-health-url <url>    Backend health URL.
  --backend-readiness-url <url> Backend readiness URL.
  --frontend-health-url <url>   Frontend health URL.
  --frontend-url <url>          Frontend page URL.
  --nginx-env <path>            Frontend Nginx env path. Default: /data/testagent/config/nginx.env.
  --docker-env <path>           Worker docker.env path. Default: /data/testagent/config/docker.env.
  --expected-server-id <id>     Expected /data/testagent/data/.serverid value.
  --expected-server-host <host> Expected /data/testagent/data/.serverhost value.
  --skip-frontend              Do not scp or reload frontend.
  --skip-worker                Do not docker load or restart opencode-worker.
  --keep-extract               Keep extracted temporary files after success.
  --validate-only              Only unzip and validate release artifacts, without deploying.
  -h, --help                   Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --archive)
      ARCHIVE="$2"
      shift 2
      ;;
    --extract-dir)
      EXTRACT_DIR="$2"
      EXTRACT_DIR_EXPLICIT=1
      shift 2
      ;;
    --install-root)
      INSTALL_ROOT="$2"
      shift 2
      ;;
    --backend-host)
      BACKEND_HOST="$2"
      shift 2
      ;;
    --frontend-host)
      FRONTEND_HOST="$2"
      shift 2
      ;;
    --frontend-user)
      FRONTEND_USER="$2"
      shift 2
      ;;
    --frontend-root)
      FRONTEND_ROOT="$2"
      shift 2
      ;;
    --backend-service)
      BACKEND_SERVICE="$2"
      shift 2
      ;;
    --backend-port)
      BACKEND_PORT="$2"
      shift 2
      ;;
    --backend-health-url)
      BACKEND_HEALTH_URL="$2"
      shift 2
      ;;
    --backend-readiness-url)
      BACKEND_READINESS_URL="$2"
      shift 2
      ;;
    --frontend-health-url)
      FRONTEND_HEALTH_URL="$2"
      shift 2
      ;;
    --frontend-url)
      FRONTEND_URL="$2"
      shift 2
      ;;
    --nginx-env)
      NGINX_ENV="$2"
      shift 2
      ;;
    --docker-env)
      DOCKER_ENV="$2"
      shift 2
      ;;
    --expected-server-id)
      EXPECTED_SERVER_ID="$2"
      shift 2
      ;;
    --expected-server-host)
      EXPECTED_SERVER_HOST="$2"
      shift 2
      ;;
    --skip-frontend)
      SKIP_FRONTEND=1
      shift
      ;;
    --skip-worker)
      SKIP_WORKER=1
      SKIP_WORKER_EXPLICIT=1
      shift
      ;;
    --keep-extract)
      KEEP_EXTRACT=1
      shift
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

# 仅校验包时不应依赖生产机的 /data 目录，便于在 Mac 或 CI 上直接验证最终 ZIP。
if [[ "${VALIDATE_ONLY}" -eq 1 && "${EXTRACT_DIR_EXPLICIT}" -eq 0 ]]; then
  EXTRACT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-internal-release-validate.XXXXXX")"
fi

cleanup_validate_extract() {
  if [[ "${VALIDATE_ONLY}" -eq 1 && "${KEEP_EXTRACT}" -eq 0 ]]; then
    rm -rf "${EXTRACT_DIR}"
  fi
}
trap cleanup_validate_extract EXIT

log() {
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

server_id_from_host() {
  local host="$1"
  printf 'test-agent-backend-%s' "${host//./-}"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1" >&2
    exit 1
  fi
}

require_file() {
  if [[ ! -f "$1" ]]; then
    echo "Required file not found: $1" >&2
    exit 1
  fi
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
    "${SUPPORT_ACCESS_MIGRATION_RESOURCE}" "${SUPPORT_ACCESS_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE}" "${SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_RESOURCE}" "${PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE}" "${PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${QA_MEMORY_MAIN_MIGRATION_RESOURCE}" "${QA_MEMORY_APPLIED_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${MEMORY_PROFILE_MIGRATION_RESOURCE}" "${QA_MEMORY_GENERALIZE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${QA_MEMORY_IDENTITY_MAIN_MIGRATION_RESOURCE}" "${QA_MEMORY_IDENTITY_MIGRATION_SHA256}"
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
    "${INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_MIGRATION_SHA256}"
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
    "${QA_MEMORY_AFTER_TOKEN_LATENCY_MIGRATION_RESOURCE}" "${QA_MEMORY_AFTER_TOKEN_LATENCY_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SESSION_SHARE_MAIN_MIGRATION_RESOURCE}" "${SESSION_SHARE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SESSION_SHARE_FORWARD_MIGRATION_RESOURCE}" "${SESSION_SHARE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SESSION_SHARE_ATTRIBUTION_MAIN_MIGRATION_RESOURCE}" "${SESSION_SHARE_ATTRIBUTION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION_RESOURCE}" "${SESSION_SHARE_ATTRIBUTION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${USER_NOTIFICATION_MIGRATION_RESOURCE}" "${USER_NOTIFICATION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${USER_NOTIFICATION_DISPOSE_MIGRATION_RESOURCE}" "${USER_NOTIFICATION_DISPOSE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_RESOURCE}" "${EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${EXPERIENCE_WORKSPACE_FORWARD_MIGRATION_RESOURCE}" "${EXPERIENCE_WORKSPACE_FORWARD_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${EXPERIENCE_WORKSPACE_DEFAULT_MIGRATION_RESOURCE}" "${EXPERIENCE_WORKSPACE_DEFAULT_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${ANALYTICS_OUTBOX_MIGRATION_RESOURCE}" "${ANALYTICS_OUTBOX_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${ANALYTICS_POSTGRES_TRIGGER_MIGRATION_RESOURCE}" "${ANALYTICS_POSTGRES_TRIGGER_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${ANALYTICS_CLICKHOUSE_MIGRATION_RESOURCE}" "${ANALYTICS_CLICKHOUSE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${LOCAL_CLIENT_RUNTIME_APPLIED_MIGRATION_RESOURCE}" "${LOCAL_CLIENT_RUNTIME_APPLIED_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${AUTOMATION_CODE_REPOSITORY_MIGRATION_RESOURCE}" "${AUTOMATION_CODE_REPOSITORY_MIGRATION_SHA256}"
}

verify_release_xxl_flyway_migrations_jar() {
  local jar="$1" label="$2"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${XXL_INTERNAL_MODEL_PROBE_MIGRATION_RESOURCE}" "${XXL_INTERNAL_MODEL_PROBE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${XXL_INTERNAL_MODEL_RETENTION_MIGRATION_RESOURCE}" "${XXL_INTERNAL_MODEL_RETENTION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" "${label}" \
    "${XXL_ANALYTICS_INGESTION_MIGRATION_RESOURCE}" "${XXL_ANALYTICS_INGESTION_MIGRATION_SHA256}"
}

manifest_value() {
  local file="$1" key="$2"
  [[ -f "${file}" ]] || return 0
  awk -F= -v wanted="${key}" '$1 == wanted { value=substr($0, index($0, "=") + 1) } END { print value }' "${file}"
}

write_installed_component_fingerprint() {
  local key="$1" value="$2"
  local state_file="${INSTALL_ROOT}/config/release-component-state.env"
  local worker_fingerprint toolbox_fingerprint tmp
  worker_fingerprint="$(manifest_value "${state_file}" TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT)"
  toolbox_fingerprint="$(manifest_value "${state_file}" TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT)"
  case "${key}" in
    TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT) worker_fingerprint="${value}" ;;
    TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT) toolbox_fingerprint="${value}" ;;
    *) echo "Unsupported installed component key: ${key}" >&2; exit 1 ;;
  esac
  mkdir -p "$(dirname "${state_file}")"
  tmp="$(mktemp "${state_file}.new.XXXXXX")"
  {
    printf 'TEST_AGENT_RELEASE_COMPONENT_STATE_VERSION=1\n'
    [[ -z "${worker_fingerprint}" ]] || printf 'TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT=%s\n' "${worker_fingerprint}"
    [[ -z "${toolbox_fingerprint}" ]] || printf 'TEST_AGENT_RELEASE_TOOLBOX_FINGERPRINT=%s\n' "${toolbox_fingerprint}"
  } >"${tmp}"
  chmod 0600 "${tmp}"
  mv -f "${tmp}" "${state_file}"
}

verify_reused_worker_runtime() {
  local state health installed_fingerprint
  installed_fingerprint="$(manifest_value \
    "${INSTALL_ROOT}/config/release-component-state.env" \
    TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT)"
  [[ -n "${WORKER_COMPONENT_FINGERPRINT}" \
    && "${installed_fingerprint}" == "${WORKER_COMPONENT_FINGERPRINT}" ]] || {
    echo "Incremental release worker runtime fingerprint does not match the installed component; deploy a full component package" >&2
    exit 1
  }
  require_file "${INSTALL_ROOT}/programs/bin/opencode-manager"
  require_file "${INSTALL_ROOT}/programs/opencode/bin/opencode"
  require_file "${INSTALL_ROOT}/programs/codex/bin/codex-official"
  bash "${DEPLOY_INTERNAL_SRC}/verify-opencode-tool-runtime.sh" \
    --root "${INSTALL_ROOT}/programs/opencode"
  state="$(docker inspect -f '{{.State.Running}}' test-agent-opencode-worker 2>/dev/null || true)"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{end}}' test-agent-opencode-worker 2>/dev/null || true)"
  [[ "${state}" == true && ( -z "${health}" || "${health}" == healthy ) ]] || {
    echo "Incremental release reuses worker runtime, but existing worker is not healthy" >&2
    exit 1
  }
  printf 'Existing worker runtime verified for reuse: OpenCode Manager, Codex MCP and container are present\n'
}

ssh_target() {
  if [[ -n "${FRONTEND_USER}" ]]; then
    printf '%s@%s' "${FRONTEND_USER}" "${FRONTEND_HOST}"
  else
    printf '%s' "${FRONTEND_HOST}"
  fi
}

configure_backend_defaults() {
  if [[ "${BACKEND_SERVICE}" != *.service ]]; then
    BACKEND_SERVICE="${BACKEND_SERVICE}.service"
  fi
  if [[ ! "${BACKEND_SERVICE}" =~ ^[A-Za-z0-9_.@-]+\.service$ ]]; then
    echo "Invalid backend systemd service name: ${BACKEND_SERVICE}" >&2
    exit 1
  fi
  if [[ ! "${BACKEND_PORT}" =~ ^[0-9]{1,5}$ ]] || (( BACKEND_PORT < 1 || BACKEND_PORT > 65535 )); then
    echo "Invalid backend port: ${BACKEND_PORT}" >&2
    exit 1
  fi
  if [[ -z "${BACKEND_HEALTH_URL}" ]]; then
    BACKEND_HEALTH_URL="http://${BACKEND_HOST}:${BACKEND_PORT}/actuator/health"
  fi
  if [[ -z "${BACKEND_READINESS_URL}" ]]; then
    BACKEND_READINESS_URL="http://${BACKEND_HOST}:${BACKEND_PORT}/actuator/health/readiness"
  fi
  if [[ -z "${EXPECTED_SERVER_HOST}" ]]; then
    EXPECTED_SERVER_HOST="${BACKEND_HOST}"
  fi
  if [[ -z "${EXPECTED_SERVER_ID}" ]]; then
    EXPECTED_SERVER_ID="$(server_id_from_host "${BACKEND_HOST}")"
  fi
}

ensure_backend_service() {
  local backend_env="${INSTALL_ROOT}/config/backend.env"
  local java_bin unit_path

  # 升级已有环境时保留现场 unit，但必须确认它确实管理本次交付的 JAR 和 backend.env。
  if systemctl cat "${BACKEND_SERVICE}" >/dev/null 2>&1; then
    validate_existing_backend_service "${backend_env}"
    return 0
  fi

  require_file "${backend_env}"
  java_bin="$(command -v java || true)"
  if [[ -z "${java_bin}" || "${java_bin}" != /* ]]; then
    echo "Java executable not found; install JDK 21 before creating ${BACKEND_SERVICE}" >&2
    exit 1
  fi

  unit_path="${SYSTEMD_UNIT_DIR}/${BACKEND_SERVICE}"
  log "Install missing backend systemd unit: ${unit_path}"
  mkdir -p "${SYSTEMD_UNIT_DIR}"
  {
    printf '%s\n' \
      '[Unit]' \
      'Description=Test Agent Backend' \
      'Wants=network-online.target' \
      'After=network-online.target' \
      '' \
      '[Service]' \
      'Type=simple' \
      "WorkingDirectory=${INSTALL_ROOT}" \
      "EnvironmentFile=${backend_env}" \
      "ExecStart=${java_bin} -jar ${INSTALL_ROOT}/dist/backend/test-agent-app.jar" \
      'Restart=always' \
      'RestartSec=5' \
      'TimeoutStopSec=60' \
      'LimitNOFILE=65536' \
      '' \
      '[Install]' \
      'WantedBy=multi-user.target'
  } >"${unit_path}"
  chmod 0644 "${unit_path}"
  systemctl daemon-reload
  systemctl enable "${BACKEND_SERVICE}"
}

validate_existing_backend_service() {
  local backend_env="$1"
  local expected_jar="${INSTALL_ROOT}/dist/backend/test-agent-app.jar"
  local exec_start environment_files

  exec_start="$(systemctl show "${BACKEND_SERVICE}" --property=ExecStart --value 2>/dev/null || true)"
  environment_files="$(systemctl show "${BACKEND_SERVICE}" --property=EnvironmentFiles --value 2>/dev/null || true)"
  if [[ "${exec_start}" != *"${expected_jar}"* ]]; then
    echo "Existing ${BACKEND_SERVICE} does not execute ${expected_jar}" >&2
    echo "ExecStart: ${exec_start:-<empty>}" >&2
    exit 1
  fi
  if [[ "${environment_files}" != *"${backend_env}"* ]]; then
    echo "Existing ${BACKEND_SERVICE} does not load ${backend_env}" >&2
    echo "EnvironmentFiles: ${environment_files:-<empty>}" >&2
    exit 1
  fi
}

listener_pids_on_backend_port() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -t -iTCP:"${BACKEND_PORT}" -sTCP:LISTEN 2>/dev/null | sort -u || true
    return
  fi
  if command -v ss >/dev/null 2>&1; then
    ss -lntp "sport = :${BACKEND_PORT}" 2>/dev/null \
      | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' \
      | sort -u || true
    return
  fi
  echo "Neither lsof nor ss is available; cannot verify backend port ${BACKEND_PORT}" >&2
  return 1
}

backend_pid_cmdline() {
  local pid="$1"
  local proc_root="${TEST_AGENT_PROC_ROOT:-/proc}"
  if [[ ! -r "${proc_root}/${pid}/cmdline" ]]; then
    return 1
  fi
  tr '\0' ' ' <"${proc_root}/${pid}/cmdline"
}

stop_expected_backend_orphans() {
  local expected_jar="${INSTALL_ROOT}/dist/backend/test-agent-app.jar"
  local pids pid cmdline deadline remaining
  local expected_pids=()
  local foreign_pids=()

  pids="$(listener_pids_on_backend_port)"
  [[ -n "${pids}" ]] || return 0
  while IFS= read -r pid; do
    [[ "${pid}" =~ ^[0-9]+$ ]] || continue
    cmdline="$(backend_pid_cmdline "${pid}" || true)"
    if [[ "${cmdline}" == *"${expected_jar}"* ]]; then
      expected_pids+=("${pid}")
    else
      foreign_pids+=("${pid}")
      printf 'Port %s is owned by unexpected PID %s: %s\n' \
        "${BACKEND_PORT}" "${pid}" "${cmdline:-<unreadable>}" >&2
    fi
  done <<<"${pids}"

  if [[ "${#foreign_pids[@]}" -gt 0 ]]; then
    echo "Refusing to kill an unrelated process on backend port ${BACKEND_PORT}" >&2
    exit 1
  fi

  if [[ "${#expected_pids[@]}" -gt 0 ]]; then
    log "Stop orphan backend process(es) on port ${BACKEND_PORT}: ${expected_pids[*]}"
    kill -TERM "${expected_pids[@]}" 2>/dev/null || true
    deadline=$((SECONDS + 20))
    while (( SECONDS < deadline )); do
      remaining="$(listener_pids_on_backend_port)"
      [[ -z "${remaining}" ]] && return 0
      sleep 1
    done
    # TERM 超时后只对仍然运行同一交付 JAR 的 PID 执行 KILL，避免 PID 复用误杀其他进程。
    for pid in "${expected_pids[@]}"; do
      cmdline="$(backend_pid_cmdline "${pid}" || true)"
      if [[ "${cmdline}" == *"${expected_jar}"* ]]; then
        kill -KILL "${pid}" 2>/dev/null || true
      fi
    done
  fi

  sleep 1
  remaining="$(listener_pids_on_backend_port)"
  if [[ -n "${remaining}" ]]; then
    echo "Backend port ${BACKEND_PORT} is still occupied by PID(s): ${remaining//$'\n'/,}" >&2
    exit 1
  fi
}

verify_backend_service_owns_port() {
  local main_pid listener_pids
  if ! systemctl is-active --quiet "${BACKEND_SERVICE}"; then
    echo "Backend service is not active: ${BACKEND_SERVICE}" >&2
    exit 1
  fi
  main_pid="$(systemctl show "${BACKEND_SERVICE}" --property=MainPID --value 2>/dev/null || true)"
  if [[ ! "${main_pid}" =~ ^[1-9][0-9]*$ ]]; then
    echo "Backend service has no valid MainPID: ${main_pid:-<empty>}" >&2
    exit 1
  fi
  listener_pids="$(listener_pids_on_backend_port)"
  if [[ -z "${listener_pids}" ]] || ! grep -Fxq "${main_pid}" <<<"${listener_pids}"; then
    echo "Backend health responded, but ${BACKEND_SERVICE} MainPID ${main_pid} does not own port ${BACKEND_PORT}" >&2
    echo "Listener PID(s): ${listener_pids:-<none>}" >&2
    exit 1
  fi
}

find_first_file() {
  local root="$1"
  local name="$2"
  # 交付 zip 可能包住 deploy/internal，也可能只包 dist；按文件名在有限层级内定位产物。
  find "${root}" -maxdepth 6 -type f -name "${name}" | sort | head -n 1
}

find_local_client_dist() {
  find "$1" -maxdepth 6 -type d -path '*/dist/local-opencode-client' | sort | head -n 1
}

find_first_tar() {
  local root="$1"
  find "${root}" -maxdepth 6 -type f -name 'test-agent-opencode-worker*linux-amd64.tar' | sort | head -n 1
}

wait_http() {
  local url="$1"
  local label="$2"
  local timeout_seconds="${3:-90}"
  local deadline=$((SECONDS + timeout_seconds))

  until curl -fsS "${url}" >/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for ${label}: ${url}" >&2
      return 1
    fi
    sleep 3
  done
}

assert_identity_file() {
  local path="$1"
  local expected="$2"
  local label="$3"
  local actual

  # Java 写出的服务器身份是 worker 连接本机 Java 的来源，值不对时必须先中断部署。
  require_file "${path}"
  actual="$(tr -d '\r\n' <"${path}")"
  if [[ -n "${expected}" && "${actual}" != "${expected}" ]]; then
    echo "${label} mismatch: expected '${expected}', got '${actual}'" >&2
    exit 1
  fi
  printf '%s=%s\n' "${label}" "${actual}"
}

run_frontend_update() {
  local frontend_target="$1"
  local frontend_archive="$2"
  local deploy_internal_src="$3"
  local local_client_dist="$4"
  local remote_deploy_tmp="${FRONTEND_ROOT}/deploy/internal.new"

  # 前端服务器只接收静态包和 deploy/internal 模板；不把后端 jar 或 worker 镜像传过去。
  log "Copy frontend artifacts to ${frontend_target}"
  if ! ssh -o BatchMode=yes -o ConnectTimeout=10 "${frontend_target}" true >/dev/null 2>&1; then
    cat >&2 <<EOF
Cannot access frontend server with non-interactive ssh: ${frontend_target}

If unified login or bastion policy blocks direct ssh/scp from this backend host,
copy the same release zip to the frontend server through the approved channel,
then deploy the frontend on 122.233.30.2 itself:

  unzip -p ${ARCHIVE} deploy/internal/deploy-internal-frontend.sh > /tmp/deploy-internal-frontend.sh
  bash /tmp/deploy-internal-frontend.sh --archive ${ARCHIVE} --nginx-env ${NGINX_ENV}

Then deploy this backend node without touching the frontend:

  bash $0 --archive ${ARCHIVE} --backend-host ${BACKEND_HOST} --skip-frontend

EOF
    exit 1
  fi
  ssh "${frontend_target}" "mkdir -p '${FRONTEND_ROOT}/dist' '${FRONTEND_ROOT}/deploy'"
  scp "${frontend_archive}" "${frontend_target}:${FRONTEND_ROOT}/dist/test-agent-frontend-dist.tar.gz"
  ssh "${frontend_target}" "rm -rf '${FRONTEND_ROOT}/dist/local-opencode-client.new'"
  scp -r "${local_client_dist}" "${frontend_target}:${FRONTEND_ROOT}/dist/local-opencode-client.new"

  if [[ -d "${deploy_internal_src}" ]]; then
    ssh "${frontend_target}" "rm -rf '${remote_deploy_tmp}'"
    scp -r "${deploy_internal_src}" "${frontend_target}:${remote_deploy_tmp}"
  fi

  log "Update frontend files and reload nginx on ${frontend_target}"
  # 远程更新先备份旧目录，再解压新静态资源；nginx 校验失败会阻断 reload。
  ssh "${frontend_target}" "FRONTEND_ROOT='${FRONTEND_ROOT}' FRONTEND_HEALTH_URL='${FRONTEND_HEALTH_URL}' FRONTEND_URL='${FRONTEND_URL}' NGINX_ENV='${NGINX_ENV}' bash -s" <<'REMOTE_FRONTEND'
set -euo pipefail
timestamp="$(date +%Y%m%d%H%M%S)"
mkdir -p "${FRONTEND_ROOT}/frontend" "${FRONTEND_ROOT}/dist" "${FRONTEND_ROOT}/deploy"

if [[ -d "${FRONTEND_ROOT}/deploy/internal.new" ]]; then
  if [[ -d "${FRONTEND_ROOT}/deploy/internal" ]]; then
    rm -rf "${FRONTEND_ROOT}/deploy/internal.bak.${timestamp}"
    mv "${FRONTEND_ROOT}/deploy/internal" "${FRONTEND_ROOT}/deploy/internal.bak.${timestamp}"
  fi
  mv "${FRONTEND_ROOT}/deploy/internal.new" "${FRONTEND_ROOT}/deploy/internal"
fi

if [[ -d "${FRONTEND_ROOT}/frontend" ]]; then
  rm -rf "${FRONTEND_ROOT}/frontend.bak.${timestamp}"
  cp -a "${FRONTEND_ROOT}/frontend" "${FRONTEND_ROOT}/frontend.bak.${timestamp}"
fi

tar -C "${FRONTEND_ROOT}" -xzf "${FRONTEND_ROOT}/dist/test-agent-frontend-dist.tar.gz"
if [[ -d "${FRONTEND_ROOT}/dist/local-opencode-client" ]]; then
  rm -rf "${FRONTEND_ROOT}/dist/local-opencode-client.bak.${timestamp}"
  mv "${FRONTEND_ROOT}/dist/local-opencode-client" "${FRONTEND_ROOT}/dist/local-opencode-client.bak.${timestamp}"
fi
mv "${FRONTEND_ROOT}/dist/local-opencode-client.new" "${FRONTEND_ROOT}/dist/local-opencode-client"
bash "${FRONTEND_ROOT}/deploy/internal/configure-nginx.sh" --env-file "${NGINX_ENV}"
curl -fsS "${FRONTEND_HEALTH_URL}" >/dev/null
curl -fsS "${FRONTEND_URL}" >/dev/null
REMOTE_FRONTEND
}

wait_worker_config_update() {
  local timeout_seconds="${1:-90}"
  local deadline=$((SECONDS + timeout_seconds))

  # worker 重启后必须等 manager 收到 Java 下发配置，否则用户进程启动会缺少运行参数。
  # 当前 manager 使用结构化事件日志；同时兼容旧版自然语言日志，便于滚动升级旧 worker。
  # grep 读取完整的有限日志，避免 pipefail 下 grep -q 提前退出让 docker logs 因 SIGPIPE 被误判失败。
  until docker logs --tail 200 test-agent-opencode-worker 2>&1 \
    | grep -E 'event=manager_config_update status=applied|manager config update applied' >/dev/null; do
    if (( SECONDS >= deadline )); then
      echo "Timed out waiting for worker manager config update" >&2
      docker logs --tail 120 test-agent-opencode-worker || true
      return 1
    fi
    sleep 3
  done
}

configure_backend_defaults

require_command unzip
require_command find
require_command tar
require_file "${ARCHIVE}"

if [[ "${VALIDATE_ONLY}" -eq 0 ]]; then
  require_command curl
  require_command systemctl
fi

if [[ "${VALIDATE_ONLY}" -eq 0 && "${SKIP_FRONTEND}" -eq 0 ]]; then
  require_command ssh
  require_command scp
fi
log "Extract release archive"
rm -rf "${EXTRACT_DIR}"
mkdir -p "${EXTRACT_DIR}"
unzip -q "${ARCHIVE}" -d "${EXTRACT_DIR}"

FRONTEND_ARCHIVE="$(find_first_file "${EXTRACT_DIR}" 'test-agent-frontend-dist.tar.gz')"
LOCAL_CLIENT_DIST="$(find_local_client_dist "${EXTRACT_DIR}")"
BACKEND_JAR="$(find_first_file "${EXTRACT_DIR}" 'test-agent-app.jar')"
BACKEND_LIB_DIR="$(find "${EXTRACT_DIR}" -maxdepth 6 -type d -path '*/backend/lib' | sort | head -n 1)"
PROGRAMS_ARCHIVE="$(find_first_file "${EXTRACT_DIR}" 'test-agent-programs.tar.gz')"
WORKER_IMAGE_TAR="$(find_first_tar "${EXTRACT_DIR}")"
DEPLOY_WORKER_SCRIPT="$(find_first_file "${EXTRACT_DIR}" 'opencode-worker-docker.sh')"
DEPLOY_INTERNAL_SRC=""
if [[ -n "${DEPLOY_WORKER_SCRIPT}" ]]; then
  DEPLOY_INTERNAL_SRC="$(cd "$(dirname "${DEPLOY_WORKER_SCRIPT}")" && pwd)"
fi

COMPONENT_MANIFEST="${EXTRACT_DIR}/deploy/internal/release-components.env"
WORKER_COMPONENT_MODE="$(manifest_value "${COMPONENT_MANIFEST}" TEST_AGENT_RELEASE_WORKER_RUNTIME)"
WORKER_COMPONENT_FINGERPRINT="$(manifest_value "${COMPONENT_MANIFEST}" TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT)"
WORKER_COMPONENT_MODE="${WORKER_COMPONENT_MODE:-included}"
[[ "${WORKER_COMPONENT_MODE}" == included || "${WORKER_COMPONENT_MODE}" == reuse ]] || {
  echo "Invalid TEST_AGENT_RELEASE_WORKER_RUNTIME: ${WORKER_COMPONENT_MODE}" >&2
  exit 1
}
if [[ "${WORKER_COMPONENT_MODE}" == reuse ]]; then
  WORKER_RUNTIME_REUSE=1
  SKIP_WORKER=1
fi
if [[ "${VALIDATE_ONLY}" -eq 0 && ( "${SKIP_WORKER}" -eq 0 || "${WORKER_RUNTIME_REUSE}" -eq 1 ) ]]; then
  require_command docker
  require_file "${DOCKER_ENV}"
fi

require_file "${FRONTEND_ARCHIVE}"
require_file "${LOCAL_CLIENT_DIST}/install.sh"
require_file "${LOCAL_CLIENT_DIST}/stable/manifest.json"
require_file "${LOCAL_CLIENT_DIST}/stable/manifest.json.sig"
require_file "${BACKEND_JAR}"
[[ -n "${BACKEND_LIB_DIR}" && -n "$(find "${BACKEND_LIB_DIR}" -maxdepth 1 -type f -name '*.jar' -print -quit)" ]] || {
  echo "backend external lib directory not found in archive" >&2
  exit 1
}
RELEASE_PERSISTENCE_JAR="$(find_unique_persistence_jar "${BACKEND_LIB_DIR}")"
verify_release_flyway_migrations_jar "${RELEASE_PERSISTENCE_JAR}" "Release archive persistence JAR"
RELEASE_PERSISTENCE_JAR_SHA256="$(sha256_file "${RELEASE_PERSISTENCE_JAR}")"
RELEASE_XXL_JOB_INTEGRATION_JAR="$(find_unique_xxl_job_integration_jar "${BACKEND_LIB_DIR}")"
verify_release_xxl_flyway_migrations_jar "${RELEASE_XXL_JOB_INTEGRATION_JAR}" "Release archive XXL integration JAR"
RELEASE_XXL_JOB_INTEGRATION_JAR_SHA256="$(sha256_file "${RELEASE_XXL_JOB_INTEGRATION_JAR}")"
if [[ "${WORKER_RUNTIME_REUSE}" -eq 0 ]]; then
  require_file "${PROGRAMS_ARCHIVE}"
fi
if [[ "${SKIP_WORKER}" -eq 0 ]]; then
  require_file "${WORKER_IMAGE_TAR}"
fi
if [[ "${WORKER_RUNTIME_REUSE}" -eq 1 ]]; then
  [[ -z "${PROGRAMS_ARCHIVE}" && -z "${WORKER_IMAGE_TAR}" ]] || {
    echo "Worker runtime reuse release must not embed programs or worker image tar" >&2
    exit 1
  }
fi
if [[ -z "${DEPLOY_INTERNAL_SRC}" || ! -d "${DEPLOY_INTERNAL_SRC}" ]]; then
  echo "deploy/internal directory not found in archive" >&2
  exit 1
fi
require_file "${DEPLOY_INTERNAL_SRC}/ensure-opencode-runtime-gitignore.sh"
require_file "${DEPLOY_INTERNAL_SRC}/opencode-runtime.gitignore"
require_file "${DEPLOY_INTERNAL_SRC}/deploy-python-libs.sh"
require_file "${DEPLOY_INTERNAL_SRC}/verify-python-libs.sh"
require_file "${DEPLOY_INTERNAL_SRC}/opencode-node-runtime.package.json"
require_file "${DEPLOY_INTERNAL_SRC}/verify-opencode-tool-runtime.sh"
if [[ "${WORKER_RUNTIME_REUSE}" -eq 0 ]]; then
  bash "${DEPLOY_INTERNAL_SRC}/verify-opencode-tool-runtime.sh" --archive "${PROGRAMS_ARCHIVE}"
fi

if [[ "${VALIDATE_ONLY}" -eq 1 ]]; then
  log "Release archive validation passed"
  printf 'frontend archive: %s\n' "${FRONTEND_ARCHIVE}"
  printf 'local client HTTP distribution: %s\n' "${LOCAL_CLIENT_DIST}"
  printf 'backend jar: %s\n' "${BACKEND_JAR}"
  printf 'backend lib: %s\n' "${BACKEND_LIB_DIR}"
  printf 'worker runtime component: %s\n' "${WORKER_COMPONENT_MODE}"
  if [[ "${WORKER_RUNTIME_REUSE}" -eq 0 ]]; then
    printf 'programs archive: %s\n' "${PROGRAMS_ARCHIVE}"
  fi
  if [[ "${SKIP_WORKER}" -eq 0 ]]; then
    printf 'worker image tar: %s\n' "${WORKER_IMAGE_TAR}"
  fi
  printf 'deploy internal: %s\n' "${DEPLOY_INTERNAL_SRC}"
  if [[ "${KEEP_EXTRACT}" -eq 0 ]]; then
    rm -rf "${EXTRACT_DIR}"
  fi
  exit 0
fi

if [[ "${WORKER_RUNTIME_REUSE}" -eq 1 && "${SKIP_WORKER_EXPLICIT}" -eq 0 ]]; then
  verify_reused_worker_runtime
fi

if [[ "${SKIP_FRONTEND}" -eq 0 ]]; then
  run_frontend_update "$(ssh_target)" "${FRONTEND_ARCHIVE}" "${DEPLOY_INTERNAL_SRC}" "${LOCAL_CLIENT_DIST}"
fi

log "Install backend artifacts under ${INSTALL_ROOT}"
timestamp="$(date +%Y%m%d%H%M%S)"
mkdir -p "${INSTALL_ROOT}/dist/backend" "${INSTALL_ROOT}/dist" "${INSTALL_ROOT}/programs" "${INSTALL_ROOT}/deploy"

cp "${BACKEND_JAR}" "${INSTALL_ROOT}/dist/backend/test-agent-app.jar.new"
rm -rf "${INSTALL_ROOT}/dist/backend/lib.new"
cp -a "${BACKEND_LIB_DIR}" "${INSTALL_ROOT}/dist/backend/lib.new"
if [[ "${WORKER_RUNTIME_REUSE}" -eq 0 ]]; then
  cp "${PROGRAMS_ARCHIVE}" "${INSTALL_ROOT}/dist/test-agent-programs.tar.gz"
fi
if [[ "${SKIP_WORKER}" -eq 0 ]]; then
  cp "${WORKER_IMAGE_TAR}" "${INSTALL_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar"
fi

rm -rf "${INSTALL_ROOT}/deploy/internal.new"
cp -a "${DEPLOY_INTERNAL_SRC}" "${INSTALL_ROOT}/deploy/internal.new"
if [[ -d "${INSTALL_ROOT}/deploy/internal" ]]; then
  rm -rf "${INSTALL_ROOT}/deploy/internal.bak.${timestamp}"
  mv "${INSTALL_ROOT}/deploy/internal" "${INSTALL_ROOT}/deploy/internal.bak.${timestamp}"
fi
mv "${INSTALL_ROOT}/deploy/internal.new" "${INSTALL_ROOT}/deploy/internal"
chmod +x \
  "${INSTALL_ROOT}/deploy/internal/opencode-worker-docker.sh" \
  "${INSTALL_ROOT}/deploy/internal/deploy-python-libs.sh" \
  "${INSTALL_ROOT}/deploy/internal/verify-python-libs.sh" \
  "${INSTALL_ROOT}/deploy/internal/ensure-opencode-runtime-gitignore.sh" \
  "${INSTALL_ROOT}/deploy/internal/verify-opencode-tool-runtime.sh" \
  || true

# 升级已初始化节点时立即消除 OpenCode 运行文件造成的 Git 脏状态；新节点尚未 clone 时不提前创建目录，
# 后续第一个用户进程会由同批次 programs 中的官方启动器在创建 package/lockfile 前补齐相同规则。
bash "${INSTALL_ROOT}/deploy/internal/ensure-opencode-runtime-gitignore.sh" \
  --config-dir "${INSTALL_ROOT}/data/agent-opencode/.config/opencode" \
  --if-present

ensure_backend_service

log "Stop backend service and replace jar"
systemctl stop "${BACKEND_SERVICE}"
stop_expected_backend_orphans
if [[ -f "${INSTALL_ROOT}/dist/backend/test-agent-app.jar" ]]; then
  cp -a "${INSTALL_ROOT}/dist/backend/test-agent-app.jar" "${INSTALL_ROOT}/dist/backend/test-agent-app.jar.bak.${timestamp}"
fi
if [[ -d "${INSTALL_ROOT}/dist/backend/lib" ]]; then
  rm -rf "${INSTALL_ROOT}/dist/backend/lib.bak.${timestamp}"
  mv "${INSTALL_ROOT}/dist/backend/lib" "${INSTALL_ROOT}/dist/backend/lib.bak.${timestamp}"
fi
mv "${INSTALL_ROOT}/dist/backend/test-agent-app.jar.new" "${INSTALL_ROOT}/dist/backend/test-agent-app.jar"
mv "${INSTALL_ROOT}/dist/backend/lib.new" "${INSTALL_ROOT}/dist/backend/lib"
INSTALLED_PERSISTENCE_JAR="$(find_unique_persistence_jar "${INSTALL_ROOT}/dist/backend/lib")"
verify_release_flyway_migrations_jar "${INSTALLED_PERSISTENCE_JAR}" "Installed persistence JAR"
INSTALLED_PERSISTENCE_JAR_SHA256="$(sha256_file "${INSTALLED_PERSISTENCE_JAR}")"
if [[ "${INSTALLED_PERSISTENCE_JAR_SHA256}" != "${RELEASE_PERSISTENCE_JAR_SHA256}" ]]; then
  echo "Installed persistence JAR differs from the release archive: expected=${RELEASE_PERSISTENCE_JAR_SHA256} actual=${INSTALLED_PERSISTENCE_JAR_SHA256}" >&2
  exit 1
fi
printf 'Installed persistence JAR matches release archive: sha256=%s\n' "${INSTALLED_PERSISTENCE_JAR_SHA256}"
INSTALLED_XXL_JOB_INTEGRATION_JAR="$(find_unique_xxl_job_integration_jar "${INSTALL_ROOT}/dist/backend/lib")"
verify_release_xxl_flyway_migrations_jar "${INSTALLED_XXL_JOB_INTEGRATION_JAR}" "Installed XXL integration JAR"
INSTALLED_XXL_JOB_INTEGRATION_JAR_SHA256="$(sha256_file "${INSTALLED_XXL_JOB_INTEGRATION_JAR}")"
if [[ "${INSTALLED_XXL_JOB_INTEGRATION_JAR_SHA256}" != "${RELEASE_XXL_JOB_INTEGRATION_JAR_SHA256}" ]]; then
  echo "Installed XXL integration JAR differs from the release archive: expected=${RELEASE_XXL_JOB_INTEGRATION_JAR_SHA256} actual=${INSTALLED_XXL_JOB_INTEGRATION_JAR_SHA256}" >&2
  exit 1
fi
printf 'Installed XXL integration JAR matches release archive: sha256=%s\n' "${INSTALLED_XXL_JOB_INTEGRATION_JAR_SHA256}"

if [[ "${WORKER_RUNTIME_REUSE}" -eq 0 ]]; then
  log "Extract external programs (OpenCode Manager, OpenCode runtime and Codex MCP)"
  tar -C "${INSTALL_ROOT}" -xzf "${INSTALL_ROOT}/dist/test-agent-programs.tar.gz"
else
  log "Reuse existing worker runtime (OpenCode Manager, OpenCode runtime and Codex MCP)"
fi
bash "${INSTALL_ROOT}/deploy/internal/verify-opencode-tool-runtime.sh" \
  --root "${INSTALL_ROOT}/programs/opencode"

if [[ "${SKIP_WORKER}" -eq 0 ]]; then
  log "Load opencode-worker docker image"
  docker load -i "${INSTALL_ROOT}/dist/test-agent-opencode-worker_internal-linux-amd64.tar"
fi

log "Start backend service and verify health"
systemctl start "${BACKEND_SERVICE}"
journalctl -u "${BACKEND_SERVICE}" -n 120 --no-pager || true
wait_http "${BACKEND_HEALTH_URL}" "backend health" 120
wait_http "${BACKEND_READINESS_URL}" "backend readiness" 120
verify_backend_service_owns_port
assert_identity_file "${INSTALL_ROOT}/data/.serverid" "${EXPECTED_SERVER_ID}" "serverid"
assert_identity_file "${INSTALL_ROOT}/data/.serverhost" "${EXPECTED_SERVER_HOST}" "serverhost"

if [[ "${SKIP_WORKER}" -eq 0 ]]; then
  log "Restart opencode-worker"
  (cd "${INSTALL_ROOT}/deploy/internal" && ./opencode-worker-docker.sh --env-file "${DOCKER_ENV}" restart)
  (cd "${INSTALL_ROOT}/deploy/internal" && ./opencode-worker-docker.sh --env-file "${DOCKER_ENV}" status)
  wait_worker_config_update 120
  docker logs --tail 120 test-agent-opencode-worker
  if [[ -n "${WORKER_COMPONENT_FINGERPRINT}" ]]; then
    write_installed_component_fingerprint \
      TEST_AGENT_RELEASE_WORKER_RUNTIME_FINGERPRINT "${WORKER_COMPONENT_FINGERPRINT}"
  fi
fi

if [[ "${SKIP_FRONTEND}" -eq 0 ]]; then
  log "Verify frontend from backend server"
  wait_http "${FRONTEND_HEALTH_URL}" "frontend health" 60
  wait_http "${FRONTEND_URL}" "frontend page" 60
fi

if [[ "${KEEP_EXTRACT}" -eq 0 ]]; then
  rm -rf "${EXTRACT_DIR}"
fi

log "Deployment finished"
