#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=archive-common.sh
source "${SCRIPT_DIR}/archive-common.sh"
RELEASE_ARCHIVE="${SCRIPT_DIR}/dist/test-agent-internal-release.zip"
NODES_DIR=""
OUTPUT_DIR="${SCRIPT_DIR}/dist"
BUNDLE_NAME="test-agent-two-backend-complete"
PRESERVE_INSTALLED_MARKER="__PRESERVE_FROM_INSTALLED_BACKEND_ENV__"
LOCAL_CLIENT_SIGNING_PUBLIC_KEY="${TEST_AGENT_LOCAL_CLIENT_SIGNING_PUBLIC_KEY:-}"
LOCAL_CLIENT_SIGNING_PUBLIC_KEY_BASE64=""
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
USER_NOTIFICATION_CAPACITY_MIGRATION_RESOURCE="db/migration/V20260824100444__user_notifications_add_opencode_capacity_warning.sql"
USER_NOTIFICATION_CAPACITY_MIGRATION_SHA256="c53ce7ecdd506219337b5f3af5251dbfebbb38486c3fb311d688a28febafaf4a"
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
ANALYTICS_OUTBOX_AFTER_RELEASE_MIGRATION_RESOURCE="db/migration-compat/analytics-after-release/V20260814165300__analytics_event_outbox_create_pipeline_after_release.sql"
ANALYTICS_TRIGGER_AFTER_RELEASE_MIGRATION_RESOURCE="db/migration-compat/analytics-after-release/V20260814165301__analytics_event_outbox_install_triggers_after_release.sql"
ANALYTICS_CLICKHOUSE_MIGRATION_RESOURCE="db/clickhouse/V20260813150000__analytics_activity_facts_create_tables.sql"
ANALYTICS_CLICKHOUSE_MIGRATION_SHA256="1a1d4d77b2d92f6f97a864da7a20b6d5f040807d15f2eef940410c10e7e7a7f7"
OPENCODE_OBSERVABILITY_GENERATION_MIGRATION_RESOURCE="db/migration/V20260822201811__opencode_server_processes_add_observability_generation.sql"
OPENCODE_OBSERVABILITY_GENERATION_MIGRATION_SHA256="033a70045a188d3f322868efc83bddebd8b4b86afe18f7b0f943632eb8fa1865"
TRACE_CATALOG_CLICKHOUSE_MIGRATION_RESOURCE="db/clickhouse/V20260822174420__analytics_trace_catalog_create_tables.sql"
TRACE_CATALOG_CLICKHOUSE_MIGRATION_SHA256="7c880ee5fc4176cd179e53cfee850001f63291580e5d87ac2301ead59ae3ace7"
TRACE_DSH_METRICS_CLICKHOUSE_MIGRATION_RESOURCE="db/clickhouse/V20260822215123__analytics_trace_spans_add_dsh_metrics.sql"
TRACE_DSH_METRICS_CLICKHOUSE_MIGRATION_SHA256="328c03ca3488e64a462aa1b1e7bff0fe541bb2cf1c5bf4dce0e024fb38c2cd7c"
CAPABILITY_CUTOVER_CLICKHOUSE_MIGRATION_RESOURCE="db/clickhouse/V20260823000346__analytics_capability_facts_enforce_plugin_cutover.sql"
CAPABILITY_CUTOVER_CLICKHOUSE_MIGRATION_SHA256="e73aa51dd301619d4b729f09935c09da2ead45cbe510e1fc62daaf32ad294825"
TRACE_COST_CLICKHOUSE_MIGRATION_RESOURCE="db/clickhouse/V20260823001128__analytics_trace_spans_add_cost_decode_tokens.sql"
TRACE_COST_CLICKHOUSE_MIGRATION_SHA256="e099a0f3b780cbd0dde1cb448c8e3fc0e10bfbdbfe27400e4783afba0e108c28"
TRACE_RUN_CUTOVER_CLICKHOUSE_MIGRATION_RESOURCE="db/clickhouse/V20260824110209__analytics_capability_facts_scope_cutover_to_runs.sql"
TRACE_RUN_CUTOVER_CLICKHOUSE_MIGRATION_SHA256="7e0ae2c427a1256268d682a9be2be48b1c839b240ca05ec857e0e934d1448f9f"
LOCAL_CLIENT_RUNTIME_MIGRATION_RESOURCE="db/migration/V20260811210453__local_client_credentials_create_runtime.sql"
LOCAL_CLIENT_RUNTIME_MIGRATION_SHA256="b4ae9ca6d8dbe04ebe058ab7b01841e30c2880231e858b6233e3571d62848970"
LOCAL_CLIENT_RUNTIME_APPLIED_MIGRATION_RESOURCE="db/migration-compat/local-client-runtime-applied/V20260812202425__local_client_credentials_create_runtime_after_release.sql"
LOCAL_CLIENT_RUNTIME_APPLIED_MIGRATION_SHA256="168cbf7bf3c1a062c8fd38057cd32726804ab8bf00ced1dff39d5c2837c53026"
LOCAL_CLIENT_RUNTIME_AFTER_RELEASE_MIGRATION_RESOURCE="db/migration-compat/local-client-runtime-after-release/V20260812202425__local_client_credentials_create_runtime_after_release.sql"
LOCAL_CLIENT_RUNTIME_AFTER_ENTERPRISE_RELEASE_MIGRATION_RESOURCE="db/migration-compat/local-client-runtime-after-enterprise-release/V20260818094330__local_client_credentials_create_runtime_after_enterprise_release.sql"
LOCAL_CLIENT_RUNTIME_AFTER_ENTERPRISE_RELEASE_MIGRATION_SHA256="6d390354ddb9794c1f3730f09f1dd806ea74628f20fa6ea2857c1dee6774d25c"
LOCAL_CLIENT_ROLLOUT_MIGRATION_RESOURCE="db/migration/V20260817193414__local_client_rollout_users_create.sql"
LOCAL_CLIENT_ROLLOUT_MIGRATION_SHA256="88e870b4afc746522f2fc2a67ba3a2098fd6844e8b6ab99325ea6c7921ae5cba"
AUTOMATION_CODE_REPOSITORY_MIGRATION_RESOURCE="db/migration/V20260812204207__dictionaries_add_automation_code_repository.sql"
AUTOMATION_CODE_REPOSITORY_MIGRATION_SHA256="250c2761c9717cca6e689019a9a91f0cc66d52a33baa662b294e41b1d1745554"
USER_SCM_GIT_IDENTITIES_MIGRATION_RESOURCE="db/migration/V20260813190929__user_scm_git_identities_create.sql"
USER_SCM_GIT_IDENTITIES_MIGRATION_SHA256="fd434d47d40c9fd71c987bd6512ba6897e33fe2e1db67299ff01514b4941c92e"
AUTOMATION_WORKSPACE_ACTIVE_VERSION_MIGRATION_RESOURCE="db/migration/V20260819125704__automation_workspace_active_versions_create.sql"
AUTOMATION_WORKSPACE_ACTIVE_VERSION_MIGRATION_SHA256="a331d8b09575fae38b61471fca719af628ac898c01be82fc369c510de2772928"
SKILL_HUB_SOURCE_MIGRATION_RESOURCE="db/migration/V20260820153926__agent_skill_hub_assets_add_skillhub_source.sql"
SKILL_HUB_SOURCE_MIGRATION_SHA256="aad10cf741414906178784b6e167dea61be2b1f90537dc681fa3cc27daaad328"
APPLICATION_AUTOMATION_REFERENCE_MIGRATION_RESOURCE="db/migration/V20260821113000__application_automation_references_create.sql"
APPLICATION_AUTOMATION_REFERENCE_MIGRATION_SHA256="3d23ae0b0142cca988eb76d8c652a98081c2718f056e4533583ff8c99c74e0d6"
APPLICATION_AUTOMATION_READ_LEASE_MIGRATION_RESOURCE="db/migration/V20260822075000__application_automation_reference_read_leases_create.sql"
APPLICATION_AUTOMATION_READ_LEASE_MIGRATION_SHA256="307a12e7877d4c146e003e36073e70cbffb8234afdb43ca8e9d1e8c55695a065"
APPLICATION_AUTOMATION_ALIAS_MIGRATION_RESOURCE="db/migration/V20260822103625__application_automation_reference_generations_add_alias.sql"
APPLICATION_AUTOMATION_ALIAS_MIGRATION_SHA256="0313c4153a77cf0bb6311e2c12320454a993c41db556685876d6881a0726fdce"
LOCAL_CLIENT_PUBLIC_CAPABILITY_MIGRATION_RESOURCE="db/migration/V20260823104611__local_client_public_capability_releases_create.sql"
LOCAL_CLIENT_PUBLIC_CAPABILITY_MIGRATION_SHA256="79efa7be62438c6bc76839e0d65a3aad1cbdbdcea44b358cc6af3db568bf342d"
LOCAL_CLIENT_PUBLIC_CAPABILITY_KIND_MIGRATION_RESOURCE="db/migration/V20260823123757__local_client_release_artifacts_public_capability_kind_add.sql"
LOCAL_CLIENT_PUBLIC_CAPABILITY_KIND_MIGRATION_SHA256="3df529b51d801e7c235c230f97796e6672456cb754ac36603f7e635814ad2258"
APPLICATION_WORKSPACE_GIT_ACCESS_MIGRATION_RESOURCE="db/migration/V20260823191023__application_workspace_git_access_checks_create.sql"
APPLICATION_WORKSPACE_GIT_ACCESS_MIGRATION_SHA256="12cfe3bbaa4b0d562f2dca2a69290180c81d42aca79b1ff4aaf6ad5cf32419e2"
LOCAL_CLIENT_INSTANCE_REPLACEMENTS_MIGRATION_RESOURCE="db/migration/V20260825091459__local_client_instance_replacements_create.sql"
LOCAL_CLIENT_INSTANCE_REPLACEMENTS_MIGRATION_SHA256="6a8802dd4483df98c7289c22e30cd4d4091a7600e8faaf8649315007286c61d3"
LOCAL_CLIENT_INSTANCE_REPLACEMENTS_FLYWAY_CHECKSUM="749555545"
XXL_INTERNAL_MODEL_PROBE_MIGRATION_RESOURCE="xxl-job/db/migration/V10__register_internal_model_probe_task.sql"
XXL_INTERNAL_MODEL_PROBE_MIGRATION_SHA256="665b22835a9871828fcaceca2941d1ca83de248698fde76f3380b12bec49fb47"
XXL_INTERNAL_MODEL_RETENTION_MIGRATION_RESOURCE="xxl-job/db/migration/V11__register_internal_model_observability_retention_task.sql"
XXL_INTERNAL_MODEL_RETENTION_MIGRATION_SHA256="03e7054a56daac14bd1cb62fd2302c7752c5d93ba88f255ad8d10f7320736236"
XXL_ANALYTICS_INGESTION_MIGRATION_RESOURCE="xxl-job/db/migration-compat/analytics-v12-applied/V12__register_analytics_clickhouse_ingestion_task.sql"
XXL_ANALYTICS_INGESTION_MIGRATION_SHA256="70878c4544d5d8c030b1edf59406a320ceec68f86bd763d366a80d5d4ed005f0"
XXL_SCM_GIT_NAME_SYNC_MIGRATION_RESOURCE="xxl-job/db/migration/V12__register_scm_git_name_sync_task.sql"
XXL_SCM_GIT_NAME_SYNC_MIGRATION_SHA256="2ef19bbbffb56131981f4f99f7d58d5b1d9f25715b0e76dc0cfd44b80b196739"
XXL_V12_FORWARD_MIGRATION_RESOURCE="xxl-job/db/migration/V13__xxl_job_info_register_tasks_after_v12_branches.sql"
XXL_V12_FORWARD_MIGRATION_SHA256="d7627696bcabc9f170f7709e298b46e28ba306a38f2251572c99b6b8175ff96a"
XXL_WORKSPACE_GIT_ACCESS_MIGRATION_RESOURCE="xxl-job/db/migration/V14__register_workspace_git_access_inspection_task.sql"
XXL_WORKSPACE_GIT_ACCESS_MIGRATION_SHA256="551d90547b21440b614a40502c852b303b22a41d93ab4060acc962b7ae411718"
XXL_INACTIVE_CLEANUP_DESCRIPTION_MIGRATION_RESOURCE="xxl-job/db/migration/V20260824100401__xxl_job_info_update_inactive_cleanup_description.sql"
XXL_INACTIVE_CLEANUP_DESCRIPTION_MIGRATION_SHA256="4eda1bf4168f097f83357d097714cc66d83156f7a2e88d3dd60adc56c218be3a"

usage() {
  cat <<'USAGE'
Usage: package-two-backend-complete.sh --nodes-dir <path> [options]

Assemble the platform release ZIP and the three prepared application node
packages into one fixed-name USB delivery bundle.

Options:
  --release-archive <path>  Standard release ZIP. Default: deploy/internal/dist/test-agent-internal-release.zip.
  --nodes-dir <path>        Directory containing prepared .4, .114 and .2 node archives plus SHA files.
  --output-dir <path>       Output directory. Default: deploy/internal/dist.
  -h, --help                Show this help.

Fixed output names:
  test-agent-two-backend-complete.zip
  test-agent-two-backend-complete.zip.sha256

Existing fixed-name outputs are replaced without interaction. Source release
and node archives are validated but never modified. The output contains
sensitive node configuration and the JAR-embedded RSA key; handle it as a
controlled artifact.

Environment:
  TEST_AGENT_LOCAL_CLIENT_SIGNING_PUBLIC_KEY
      Matching organization PEM public key used by the current local-client
      release. It is injected into both backend node configs; the private key
      is never read or packaged by this script.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-archive)
      RELEASE_ARCHIVE="$2"
      shift 2
      ;;
    --nodes-dir)
      NODES_DIR="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
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

sha256_digest() {
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

verify_release_flyway_resource() {
  local jar="$1" resource="$2" expected="$3" actual
  if ! unzip -Z1 "${jar}" | grep -Fx "${resource}" >/dev/null; then
    echo "Inner release is missing release Flyway migration: ${resource}" >&2
    exit 1
  fi
  actual="$(sha256_jar_resource "${jar}" "${resource}")"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "Inner release contains the wrong release Flyway migration: resource=${resource} expected=${expected} actual=${actual}" >&2
    exit 1
  fi
  printf 'Inner release Flyway migration verified: resource=%s sha256=%s\n' "${resource}" "${actual}"
}

verify_release_flyway_migrations_jar() {
  local jar="$1"
  verify_release_flyway_resource "${jar}" \
    "${TOOLBOX_ENTERPRISE_MIGRATION_RESOURCE}" "${TOOLBOX_ENTERPRISE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOBEHUB_MAIN_MIGRATION_RESOURCE}" "${LOBEHUB_MAIN_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOBEHUB_FORWARD_MIGRATION_RESOURCE}" "${LOBEHUB_FORWARD_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${ROLLOUT_SUPERSEDE_MIGRATION_RESOURCE}" "${ROLLOUT_SUPERSEDE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOBEHUB_RELEASE_FORWARD_MIGRATION_RESOURCE}" "${LOBEHUB_RELEASE_FORWARD_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${SUPPORT_ACCESS_MIGRATION_RESOURCE}" "${SUPPORT_ACCESS_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE}" "${SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_RESOURCE}" "${PUBLIC_SKILL_HUB_SNAPSHOT_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_RESOURCE}" "${PUBLIC_SKILL_HUB_CLASSIFICATION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${QA_MEMORY_MAIN_MIGRATION_RESOURCE}" "${QA_MEMORY_APPLIED_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${MEMORY_PROFILE_MIGRATION_RESOURCE}" "${QA_MEMORY_GENERALIZE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${QA_MEMORY_IDENTITY_MAIN_MIGRATION_RESOURCE}" "${QA_MEMORY_IDENTITY_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${RUN_RESEND_MAIN_MIGRATION_RESOURCE}" "${RUN_RESEND_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${RUN_RESEND_BEFORE_BATCH_MIGRATION_RESOURCE}" "${RUN_RESEND_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${BATCH_SESSION_MIGRATION_RESOURCE}" "${BATCH_SESSION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${RUN_RESEND_AFTER_BATCH_MIGRATION_RESOURCE}" "${RUN_RESEND_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${INTERNAL_MODEL_LEGACY_CREATE_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_CREATE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${INTERNAL_MODEL_LEGACY_FIRST_TOKEN_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_FIRST_TOKEN_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${INTERNAL_MODEL_LEGACY_STREAM_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_STREAM_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${INTERNAL_MODEL_CREATE_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_CREATE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${INTERNAL_MODEL_FIRST_TOKEN_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_FIRST_TOKEN_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${INTERNAL_MODEL_STREAM_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_STREAM_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_MIGRATION_RESOURCE}" "${INTERNAL_MODEL_TOKEN_LATENCY_INPUTS_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${EXTERNAL_API_MAIN_MIGRATION_RESOURCE}" "${EXTERNAL_API_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${QA_MEMORY_APPLIED_MIGRATION_RESOURCE}" "${QA_MEMORY_APPLIED_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${EXTERNAL_API_FORWARD_MIGRATION_RESOURCE}" "${EXTERNAL_API_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${QA_MEMORY_GENERALIZE_MIGRATION_RESOURCE}" "${QA_MEMORY_GENERALIZE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${QA_MEMORY_IDENTITY_MIGRATION_RESOURCE}" "${QA_MEMORY_IDENTITY_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${QA_MEMORY_AFTER_SESSION_SHARE_MIGRATION_RESOURCE}" "${QA_MEMORY_AFTER_SESSION_SHARE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${QA_MEMORY_AFTER_TOKEN_LATENCY_MIGRATION_RESOURCE}" "${QA_MEMORY_AFTER_TOKEN_LATENCY_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${SESSION_SHARE_MAIN_MIGRATION_RESOURCE}" "${SESSION_SHARE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${SESSION_SHARE_FORWARD_MIGRATION_RESOURCE}" "${SESSION_SHARE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${SESSION_SHARE_ATTRIBUTION_MAIN_MIGRATION_RESOURCE}" "${SESSION_SHARE_ATTRIBUTION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${SESSION_SHARE_ATTRIBUTION_FORWARD_MIGRATION_RESOURCE}" "${SESSION_SHARE_ATTRIBUTION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${USER_NOTIFICATION_MIGRATION_RESOURCE}" "${USER_NOTIFICATION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${USER_NOTIFICATION_DISPOSE_MIGRATION_RESOURCE}" "${USER_NOTIFICATION_DISPOSE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${USER_NOTIFICATION_CAPACITY_MIGRATION_RESOURCE}" "${USER_NOTIFICATION_CAPACITY_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_RESOURCE}" "${EXPERIENCE_WORKSPACE_APPLIED_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${EXPERIENCE_WORKSPACE_FORWARD_MIGRATION_RESOURCE}" "${EXPERIENCE_WORKSPACE_FORWARD_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${EXPERIENCE_WORKSPACE_DEFAULT_MIGRATION_RESOURCE}" "${EXPERIENCE_WORKSPACE_DEFAULT_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${ANALYTICS_OUTBOX_MIGRATION_RESOURCE}" "${ANALYTICS_OUTBOX_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${ANALYTICS_POSTGRES_TRIGGER_MIGRATION_RESOURCE}" "${ANALYTICS_POSTGRES_TRIGGER_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${ANALYTICS_OUTBOX_AFTER_RELEASE_MIGRATION_RESOURCE}" "${ANALYTICS_OUTBOX_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${ANALYTICS_TRIGGER_AFTER_RELEASE_MIGRATION_RESOURCE}" "${ANALYTICS_POSTGRES_TRIGGER_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${ANALYTICS_CLICKHOUSE_MIGRATION_RESOURCE}" "${ANALYTICS_CLICKHOUSE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${OPENCODE_OBSERVABILITY_GENERATION_MIGRATION_RESOURCE}" "${OPENCODE_OBSERVABILITY_GENERATION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${TRACE_CATALOG_CLICKHOUSE_MIGRATION_RESOURCE}" "${TRACE_CATALOG_CLICKHOUSE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${TRACE_DSH_METRICS_CLICKHOUSE_MIGRATION_RESOURCE}" "${TRACE_DSH_METRICS_CLICKHOUSE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${CAPABILITY_CUTOVER_CLICKHOUSE_MIGRATION_RESOURCE}" "${CAPABILITY_CUTOVER_CLICKHOUSE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${TRACE_COST_CLICKHOUSE_MIGRATION_RESOURCE}" "${TRACE_COST_CLICKHOUSE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${TRACE_RUN_CUTOVER_CLICKHOUSE_MIGRATION_RESOURCE}" "${TRACE_RUN_CUTOVER_CLICKHOUSE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOCAL_CLIENT_RUNTIME_MIGRATION_RESOURCE}" "${LOCAL_CLIENT_RUNTIME_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOCAL_CLIENT_RUNTIME_APPLIED_MIGRATION_RESOURCE}" "${LOCAL_CLIENT_RUNTIME_APPLIED_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOCAL_CLIENT_RUNTIME_AFTER_RELEASE_MIGRATION_RESOURCE}" "${LOCAL_CLIENT_RUNTIME_APPLIED_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOCAL_CLIENT_RUNTIME_AFTER_ENTERPRISE_RELEASE_MIGRATION_RESOURCE}" "${LOCAL_CLIENT_RUNTIME_AFTER_ENTERPRISE_RELEASE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOCAL_CLIENT_ROLLOUT_MIGRATION_RESOURCE}" "${LOCAL_CLIENT_ROLLOUT_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${AUTOMATION_CODE_REPOSITORY_MIGRATION_RESOURCE}" "${AUTOMATION_CODE_REPOSITORY_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${USER_SCM_GIT_IDENTITIES_MIGRATION_RESOURCE}" "${USER_SCM_GIT_IDENTITIES_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${AUTOMATION_WORKSPACE_ACTIVE_VERSION_MIGRATION_RESOURCE}" "${AUTOMATION_WORKSPACE_ACTIVE_VERSION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${SKILL_HUB_SOURCE_MIGRATION_RESOURCE}" "${SKILL_HUB_SOURCE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${APPLICATION_AUTOMATION_REFERENCE_MIGRATION_RESOURCE}" "${APPLICATION_AUTOMATION_REFERENCE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${APPLICATION_AUTOMATION_READ_LEASE_MIGRATION_RESOURCE}" "${APPLICATION_AUTOMATION_READ_LEASE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${APPLICATION_AUTOMATION_ALIAS_MIGRATION_RESOURCE}" "${APPLICATION_AUTOMATION_ALIAS_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOCAL_CLIENT_PUBLIC_CAPABILITY_MIGRATION_RESOURCE}" "${LOCAL_CLIENT_PUBLIC_CAPABILITY_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOCAL_CLIENT_PUBLIC_CAPABILITY_KIND_MIGRATION_RESOURCE}" "${LOCAL_CLIENT_PUBLIC_CAPABILITY_KIND_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${APPLICATION_WORKSPACE_GIT_ACCESS_MIGRATION_RESOURCE}" "${APPLICATION_WORKSPACE_GIT_ACCESS_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${LOCAL_CLIENT_INSTANCE_REPLACEMENTS_MIGRATION_RESOURCE}" "${LOCAL_CLIENT_INSTANCE_REPLACEMENTS_MIGRATION_SHA256}"
}

verify_release_xxl_flyway_migrations_jar() {
  local jar="$1"
  verify_release_flyway_resource "${jar}" \
    "${XXL_INTERNAL_MODEL_PROBE_MIGRATION_RESOURCE}" "${XXL_INTERNAL_MODEL_PROBE_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${XXL_INTERNAL_MODEL_RETENTION_MIGRATION_RESOURCE}" "${XXL_INTERNAL_MODEL_RETENTION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${XXL_ANALYTICS_INGESTION_MIGRATION_RESOURCE}" "${XXL_ANALYTICS_INGESTION_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${XXL_SCM_GIT_NAME_SYNC_MIGRATION_RESOURCE}" "${XXL_SCM_GIT_NAME_SYNC_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${XXL_V12_FORWARD_MIGRATION_RESOURCE}" "${XXL_V12_FORWARD_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${XXL_WORKSPACE_GIT_ACCESS_MIGRATION_RESOURCE}" "${XXL_WORKSPACE_GIT_ACCESS_MIGRATION_SHA256}"
  verify_release_flyway_resource "${jar}" \
    "${XXL_INACTIVE_CLEANUP_DESCRIPTION_MIGRATION_RESOURCE}" "${XXL_INACTIVE_CLEANUP_DESCRIPTION_MIGRATION_SHA256}"
}

# SHA 文件必须指向同目录的实际文件名，避免误校验同目录中的历史版本。
verify_checksum_pair() {
  local file="$1"
  local checksum="${file}.sha256"
  local expected actual checksum_name
  require_file "${file}"
  require_file "${checksum}"
  checksum_name="$(awk 'NF >= 2 {print $2; exit}' "${checksum}")"
  checksum_name="${checksum_name#\*}"
  if [[ "${checksum_name}" != "$(basename "${file}")" ]]; then
    echo "Checksum file does not name $(basename "${file}"): ${checksum}" >&2
    exit 1
  fi
  expected="$(awk 'NF >= 2 {print $1; exit}' "${checksum}")"
  actual="$(sha256_digest "${file}")"
  if [[ -z "${expected}" || "${expected}" != "${actual}" ]]; then
    echo "SHA256 mismatch: ${file}" >&2
    exit 1
  fi
}

require_archive_entry() {
  local listing="$1"
  local entry="$2"
  if ! grep -Fx "${entry}" <<<"${listing}" >/dev/null; then
    echo "Required archive entry not found: ${entry}" >&2
    exit 1
  fi
}

require_archive_absent() {
  local listing="$1" entry="$2"
  if grep -Fx "${entry}" <<<"${listing}" >/dev/null; then
    echo "Reused component must not be embedded in incremental release: ${entry}" >&2
    exit 1
  fi
}

require_archive_prefix_absent() {
  local listing="$1" prefix="$2"
  if grep -F "${prefix}" <<<"${listing}" >/dev/null; then
    echo "Disabled component must not be embedded in release: ${prefix}" >&2
    exit 1
  fi
}

manifest_value() {
  local content="$1" key="$2"
  awk -F= -v wanted="${key}" '$1 == wanted { value=substr($0, index($0, "=") + 1) } END { print value }' \
    <<<"${content}"
}

if [[ -z "${NODES_DIR}" ]]; then
  echo "--nodes-dir is required" >&2
  usage >&2
  exit 2
fi

require_command zip
require_command unzip
require_command tar
require_command awk
require_command openssl
require_file "${RELEASE_ARCHIVE}"
if [[ -z "${LOCAL_CLIENT_SIGNING_PUBLIC_KEY}" ]]; then
  echo "TEST_AGENT_LOCAL_CLIENT_SIGNING_PUBLIC_KEY is required for the complete bundle" >&2
  exit 1
fi
require_file "${LOCAL_CLIENT_SIGNING_PUBLIC_KEY}"
if ! openssl pkey -pubin -in "${LOCAL_CLIENT_SIGNING_PUBLIC_KEY}" -noout >/dev/null 2>&1; then
  echo "Invalid local-client signing public key" >&2
  exit 1
fi
LOCAL_CLIENT_SIGNING_PUBLIC_KEY_BASE64="$(
  openssl base64 -A -in "${LOCAL_CLIENT_SIGNING_PUBLIC_KEY}"
)"
if [[ -z "${LOCAL_CLIENT_SIGNING_PUBLIC_KEY_BASE64}" ]]; then
  echo "Failed to encode the local-client signing public key" >&2
  exit 1
fi
require_file "${SCRIPT_DIR}/deploy-node-common.sh"
require_file "${SCRIPT_DIR}/deploy-backend-node.sh"
require_file "${SCRIPT_DIR}/deploy-frontend-node.sh"
require_file "${SCRIPT_DIR}/init-backend-node-config.sh"
require_file "${SCRIPT_DIR}/register-backend-on-frontend.sh"
require_file "${SCRIPT_DIR}/MULTI-BACKEND.md"
# 现场入口必须与本轮 JAR 门禁同步，避免部署脚本正确但 START-HERE 仍沿用旧 Flyway 基线。
for release_guide_marker in \
  "$(basename "${LOCAL_CLIENT_INSTANCE_REPLACEMENTS_MIGRATION_RESOURCE}")" \
  "${LOCAL_CLIENT_INSTANCE_REPLACEMENTS_MIGRATION_SHA256}" \
  "Flyway checksum 固定为 \`${LOCAL_CLIENT_INSTANCE_REPLACEMENTS_FLYWAY_CHECKSUM}\`"; do
  if ! grep -Fq "${release_guide_marker}" "${SCRIPT_DIR}/MULTI-BACKEND.md"; then
    echo "MULTI-BACKEND.md is missing the current Flyway release marker: ${release_guide_marker}" >&2
    exit 1
  fi
done

NODE_4="${NODES_DIR}/test-agent-two-backend-122.233.30.4-SENSITIVE.tar.gz"
NODE_114="${NODES_DIR}/test-agent-two-backend-122.233.30.114-SENSITIVE.tar.gz"
NODE_2="${NODES_DIR}/test-agent-two-backend-122.233.30.2.tar.gz"

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-two-backend-complete.XXXXXX")"
cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

verify_checksum_pair "${RELEASE_ARCHIVE}"
verify_checksum_pair "${NODE_4}"
verify_checksum_pair "${NODE_114}"
verify_checksum_pair "${NODE_2}"

release_listing="$(unzip -Z1 "${RELEASE_ARCHIVE}")"
require_archive_entry "${release_listing}" dist/backend/test-agent-app.jar
require_archive_entry "${release_listing}" dist/test-agent-frontend-dist.tar.gz
require_archive_entry "${release_listing}" deploy/internal/toolbox.env.example
require_archive_entry "${release_listing}" deploy/internal/toolbox-docker.sh
require_archive_entry "${release_listing}" deploy/internal/diagnose-toolbox.sh
require_archive_entry "${release_listing}" deploy/internal/deploy-multi-backend-node.sh
require_archive_entry "${release_listing}" deploy/internal/run-analytics-clickhouse-backfill.sh
persistence_entry_count="$(grep -Ec '^dist/backend/lib/test-agent-persistence-[^/]+\.jar$' <<<"${release_listing}" || true)"
if [[ "${persistence_entry_count}" != 1 ]]; then
  echo "Inner release must contain exactly one test-agent-persistence JAR, found ${persistence_entry_count}" >&2
  exit 1
fi
persistence_entry="$(grep -E '^dist/backend/lib/test-agent-persistence-[^/]+\.jar$' <<<"${release_listing}")"
unzip -p "${RELEASE_ARCHIVE}" "${persistence_entry}" >"${TMP_ROOT}/test-agent-persistence.jar"
verify_release_flyway_migrations_jar "${TMP_ROOT}/test-agent-persistence.jar"
xxl_job_integration_entry_count="$(grep -Ec '^dist/backend/lib/test-agent-xxl-job-integration-[^/]+\.jar$' <<<"${release_listing}" || true)"
if [[ "${xxl_job_integration_entry_count}" != 1 ]]; then
  echo "Inner release must contain exactly one test-agent-xxl-job-integration JAR, found ${xxl_job_integration_entry_count}" >&2
  exit 1
fi
xxl_job_integration_entry="$(grep -E '^dist/backend/lib/test-agent-xxl-job-integration-[^/]+\.jar$' <<<"${release_listing}")"
unzip -p "${RELEASE_ARCHIVE}" "${xxl_job_integration_entry}" >"${TMP_ROOT}/test-agent-xxl-job-integration.jar"
verify_release_xxl_flyway_migrations_jar "${TMP_ROOT}/test-agent-xxl-job-integration.jar"
release_component_manifest="$(unzip -p "${RELEASE_ARCHIVE}" deploy/internal/release-components.env 2>/dev/null || true)"
worker_component_mode="$(manifest_value "${release_component_manifest}" TEST_AGENT_RELEASE_WORKER_RUNTIME)"
toolbox_component_mode="$(manifest_value "${release_component_manifest}" TEST_AGENT_RELEASE_TOOLBOX)"
lobehub_component_mode="$(manifest_value "${release_component_manifest}" TEST_AGENT_RELEASE_LOBEHUB)"
# 没有组件清单的历史发布包按全量包处理，保持旧交付物可重新封装。
worker_component_mode="${worker_component_mode:-included}"
toolbox_component_mode="${toolbox_component_mode:-included}"
# 旧包按实际条目推导 LobeHub 能力；当前包必须通过清单明确 included/disabled。
if [[ -z "${lobehub_component_mode}" ]]; then
  if grep -F 'dist/lobehub/' <<<"${release_listing}" >/dev/null; then
    lobehub_component_mode=included
  else
    lobehub_component_mode=disabled
  fi
fi
[[ "${worker_component_mode}" == included || "${worker_component_mode}" == reuse ]] || {
  echo "Invalid worker runtime component mode: ${worker_component_mode}" >&2
  exit 1
}
[[ "${toolbox_component_mode}" == included || "${toolbox_component_mode}" == reuse ]] || {
  echo "Invalid toolbox component mode: ${toolbox_component_mode}" >&2
  exit 1
}
[[ "${lobehub_component_mode}" == included || "${lobehub_component_mode}" == disabled ]] || {
  echo "Invalid LobeHub component mode: ${lobehub_component_mode}" >&2
  exit 1
}
if [[ "${worker_component_mode}" == included ]]; then
  require_archive_entry "${release_listing}" dist/test-agent-programs.tar.gz
  require_archive_entry "${release_listing}" dist/test-agent-opencode-worker_internal-linux-amd64.tar
else
  require_archive_absent "${release_listing}" dist/test-agent-programs.tar.gz
  require_archive_absent "${release_listing}" dist/test-agent-opencode-worker_internal-linux-amd64.tar
fi
if [[ "${toolbox_component_mode}" == included ]]; then
  require_archive_entry "${release_listing}" dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar
  require_archive_entry "${release_listing}" dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar.sha256
  require_archive_entry "${release_listing}" dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar
  require_archive_entry "${release_listing}" dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar.sha256
  require_archive_entry "${release_listing}" dist/test-agent-toolbox-source.tar.gz
  require_archive_entry "${release_listing}" dist/test-agent-toolbox-source.tar.gz.sha256
  require_archive_entry "${release_listing}" dist/toolbox-catalog-v1.json
  require_archive_entry "${release_listing}" dist/toolbox-catalog-v1.json.sha256
else
  require_archive_absent "${release_listing}" dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar
  require_archive_absent "${release_listing}" dist/test-agent_it-tools_2024.10.22-7ca5933-platform.2-linux-amd64.tar.sha256
  require_archive_absent "${release_listing}" dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar
  require_archive_absent "${release_listing}" dist/test-agent_omni-tools_0.6.0-platform.1-linux-amd64.tar.sha256
  require_archive_absent "${release_listing}" dist/test-agent-toolbox-source.tar.gz
  require_archive_absent "${release_listing}" dist/test-agent-toolbox-source.tar.gz.sha256
  require_archive_absent "${release_listing}" dist/toolbox-catalog-v1.json
  require_archive_absent "${release_listing}" dist/toolbox-catalog-v1.json.sha256
fi
if [[ "${lobehub_component_mode}" == disabled ]]; then
  require_archive_prefix_absent "${release_listing}" dist/lobehub/
elif ! grep -F 'dist/lobehub/' <<<"${release_listing}" >/dev/null; then
  echo "LobeHub is marked included but no dist/lobehub/ artifact is present" >&2
  exit 1
fi
# 外层完整包只接受包含当前仓库全部会话日志的内层发布包，避免业务制品与交付追溯记录脱节。
session_log_count=0
for session_log in "${ROOT_DIR}"/.agents/session-log*.md; do
  [[ -f "${session_log}" ]] || continue
  require_archive_entry "${release_listing}" ".agents/$(basename "${session_log}")"
  session_log_count=$((session_log_count + 1))
done
if [[ "${session_log_count}" -eq 0 ]]; then
  echo "No .agents/session-log*.md files found for complete bundle validation" >&2
  exit 1
fi
if grep -Fx 'dist/mysql_8.4-linux-amd64.tar' <<<"${release_listing}" >/dev/null; then
  echo "Platform release archive must not contain the standalone MySQL image" >&2
  exit 1
fi

# 完整外层包仍按 RSA 密钥交付物管理，封装前必须确认内层 JAR 的固定资源存在。
unzip -p "${RELEASE_ARCHIVE}" dist/backend/test-agent-app.jar >"${TMP_ROOT}/test-agent-app.jar"
jar_listing="$(unzip -Z1 "${TMP_ROOT}/test-agent-app.jar")"
require_archive_entry "${jar_listing}" BOOT-INF/classes/rsa-private.key

validate_node_archive() {
  local archive="$1"
  local node_dir="$2"
  local required_config="$3"
  local listing archive_size
  archive_size="$(wc -c <"${archive}" | tr -d '[:space:]')"
  if [[ "${archive_size}" -gt 1048576 ]]; then
    echo "Prepared node archive exceeds 1 MiB: ${archive}" >&2
    exit 1
  fi
  listing="$(tar -tzf "${archive}")"
  require_archive_entry "${listing}" "${node_dir}/deploy-multi-backend-node.sh"
  require_archive_entry "${listing}" "${node_dir}/MULTI-BACKEND.md"
  require_archive_entry "${listing}" "${node_dir}/config/${required_config}"
}

validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 backend.env
validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 docker.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 backend.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 docker.env
validate_node_archive "${NODE_2}" test-agent-two-backend-122.233.30.2 nginx.env

# 现场敏感配置包允许复用，但封装时必须在临时副本中补齐本次发布新增的非密钥配置。
# 原始采集包及其中的密码/token 均不修改、不输出；重复键直接拒绝，避免掩盖脏配置。
replace_or_append_env_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  local count tmp_file
  count="$(grep -c "^${key}=" "${file}" || true)"
  if [[ "${count}" -gt 1 ]]; then
    echo "Prepared node configuration contains duplicate ${key}" >&2
    exit 1
  fi
  tmp_file="$(mktemp "${file}.new.XXXXXX")"
  if [[ "${count}" -eq 1 ]]; then
    awk -v key="${key}" -v value="${value}" \
      'index($0, key "=") == 1 { print key "=" value; next } { print }' \
      "${file}" >"${tmp_file}"
  else
    cp "${file}" "${tmp_file}"
    printf '%s=%s\n' "${key}" "${value}" >>"${tmp_file}"
  fi
  chmod --reference="${file}" "${tmp_file}" 2>/dev/null || chmod 0600 "${tmp_file}"
  mv -f "${tmp_file}" "${file}"
}

# 敏感值已存在时保持原值；旧节点包没有该键时写入目标机继承标记，不生成或伪造密钥。
# SkillHub Access Key 与 ClickHouse/Mem0 密钥共用这条安全继承路径。
preserve_or_append_env_value() {
  local file="$1"
  local key="$2"
  local fallback="$3"
  local count
  count="$(grep -c "^${key}=" "${file}" || true)"
  if [[ "${count}" -gt 1 ]]; then
    echo "Prepared node configuration contains duplicate ${key}" >&2
    exit 1
  fi
  if [[ "${count}" -eq 0 ]]; then
    printf '%s=%s\n' "${key}" "${fallback}" >>"${file}"
  fi
}

normalize_backend_node_archive() {
  local source="$1"
  local node_dir="$2"
  local node_ip="$3"
  local node_root="${TMP_ROOT}/normalize-${node_dir}"
  local backend_env docker_env toolbox_env target
  mkdir -p "${node_root}"
  tar -C "${node_root}" -xzf "${source}"
  backend_env="${node_root}/${node_dir}/config/backend.env"
  docker_env="${node_root}/${node_dir}/config/docker.env"
  toolbox_env="${node_root}/${node_dir}/config/toolbox.env"

  # 两台后台必须随完整包拿到同一组织公钥；私钥始终只留在外网 Mac 构建机。
  replace_or_append_env_value "${backend_env}" \
    TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64 \
    "${LOCAL_CLIENT_SIGNING_PUBLIC_KEY_BASE64}"
  replace_or_append_env_value "${backend_env}" \
    TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL \
    'http://122.233.30.2/downloads/local-opencode-client/'
  replace_or_append_env_value "${backend_env}" \
    TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH catalog.json
  replace_or_append_env_value "${backend_env}" TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL true
  replace_or_append_env_value "${backend_env}" \
    TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES 122.233.30.2
  replace_or_append_env_value "${backend_env}" TEST_AGENT_XXL_JOB_COOKIE_SECURE false
  replace_or_append_env_value "${backend_env}" TEST_AGENT_TCDS_BASE_URL \
    'http://tcds-prod.sdc.icbc:9080'
  replace_or_append_env_value "${backend_env}" TEST_AGENT_AAM_BASE_URL \
    'http://zfw.sdc.cs.icbc'
  replace_or_append_env_value "${backend_env}" TEST_AGENT_AAM_CONNECT_TIMEOUT 3s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_AAM_REQUEST_TIMEOUT 5s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_AAM_MAX_RESPONSE_BYTES 65536
  replace_or_append_env_value "${backend_env}" TEST_AGENT_SKILLHUB_ENABLED true
  replace_or_append_env_value "${backend_env}" TEST_AGENT_SKILLHUB_BASE_URL \
    'http://ai-code.sdc.icbc/icbc/skill'
  preserve_or_append_env_value "${backend_env}" TEST_AGENT_SKILLHUB_ACCESS_KEY \
    "${PRESERVE_INSTALLED_MARKER}"
  replace_or_append_env_value "${backend_env}" TEST_AGENT_SKILLHUB_CONNECT_TIMEOUT 10s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_SKILLHUB_REQUEST_TIMEOUT 30s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_SKILLHUB_SYNC_INITIAL_DELAY 10s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_SKILLHUB_SYNC_DELAY 10m
  replace_or_append_env_value "${backend_env}" TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED true
  replace_or_append_env_value "${backend_env}" TEST_AGENT_ANALYTICS_CLICKHOUSE_URL \
    'jdbc:clickhouse://122.233.30.147:8123/testagent_analytics'
  replace_or_append_env_value "${backend_env}" TEST_AGENT_ANALYTICS_CLICKHOUSE_USERNAME ck
  preserve_or_append_env_value "${backend_env}" TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD \
    "${PRESERVE_INSTALLED_MARKER}"
  replace_or_append_env_value "${backend_env}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED false
  replace_or_append_env_value "${backend_env}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_START \
    '2025-01-01T00:00:00Z'
  replace_or_append_env_value "${backend_env}" TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_END ''
  replace_or_append_env_value "${backend_env}" TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS false
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_ENABLED true
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_SERVICE_URL \
    'http://122.233.30.160:18888'
  preserve_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_SERVICE_API_KEY \
    "${PRESERVE_INSTALLED_MARKER}"
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_REQUEST_TIMEOUT 2s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_LEARNING_TIMEOUT 130s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_RETRIEVAL_TIMEOUT 2s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_MAX_INJECTED 6
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_MAX_CONTEXT_TOKENS 800
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_RETRIEVAL_TOP_K 20
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_RETRIEVAL_THRESHOLD 0.10
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_LEARNING_BATCH_SIZE 8
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_LEARNING_MAX_ATTEMPTS 8
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_LEARNING_POLL_INTERVAL 5s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_LEARNING_LEASE 5m
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_CLIENT_ID \
    mem0-cluster
  preserve_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET \
    "${PRESERVE_INSTALLED_MARKER}"
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_CLOCK_SKEW 30s
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MEMORY_MODEL_GATEWAY_NONCE_TTL 2m
  replace_or_append_env_value "${backend_env}" TEST_AGENT_MAX_PREVIEW_BYTES 5242880
  replace_or_append_env_value "${backend_env}" TEST_AGENT_UPLOAD_CHUNK_BYTES 262144
  replace_or_append_env_value "${docker_env}" OPENCODE_WORKER_BACKEND_PORT 8080
  replace_or_append_env_value "${docker_env}" OPENCODE_WORKER_PORT_START 14096
  replace_or_append_env_value "${docker_env}" OPENCODE_WORKER_PORT_END 15095
  install -m 0600 "${SCRIPT_DIR}/toolbox.env.example" "${toolbox_env}"
  replace_or_append_env_value "${toolbox_env}" TEST_AGENT_TOOLBOX_BIND_ADDRESS "${node_ip}"

  target="${TMP_ROOT}/$(basename "${source}")"
  archive_create_tar_gz "${target}" "${node_root}" "${node_dir}"
  chmod 0600 "${target}"
  printf '%s\n' "${target}"
}

NODE_4="$(normalize_backend_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 122.233.30.4)"
NODE_114="$(normalize_backend_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 122.233.30.114)"
validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 backend.env
validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 docker.env
validate_node_archive "${NODE_4}" test-agent-two-backend-122.233.30.4 toolbox.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 backend.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 docker.env
validate_node_archive "${NODE_114}" test-agent-two-backend-122.233.30.114 toolbox.env

# 完整包必须在封装前确认两台后台引用同一个外部 MySQL，且账号密码和 access token 一致。
validate_mysql_cluster_config() {
  local config_root="${TMP_ROOT}/cluster-config-check"
  local backend_4 backend_114 frontend expected_url
  local backend_password backend_token
  local backend_4_value backend_114_value expected key minimum_length secret_spec
  mkdir -m 0700 -p "${config_root}"
  backend_4="${config_root}/backend-4.env"
  backend_114="${config_root}/backend-114.env"
  frontend="${config_root}/nginx.env"
  tar -xOzf "${NODE_4}" 'test-agent-two-backend-122.233.30.4/config/backend.env' >"${backend_4}"
  tar -xOzf "${NODE_114}" 'test-agent-two-backend-122.233.30.114/config/backend.env' >"${backend_114}"
  tar -xOzf "${NODE_2}" 'test-agent-two-backend-122.233.30.2/config/nginx.env' >"${frontend}"
  chmod 0600 "${backend_4}" "${backend_114}" "${frontend}"
  if grep -q 'REPLACE_' "${backend_4}" "${backend_114}" "${frontend}"; then
    echo "Prepared cluster configuration still contains a REPLACE_ placeholder" >&2
    exit 1
  fi
  expected_url='jdbc:mysql://122.210.106.43:3306/xxl_job?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai'
  for key in TEST_AGENT_XXL_JOB_MYSQL_URL TEST_AGENT_XXL_JOB_MYSQL_USERNAME \
    TEST_AGENT_XXL_JOB_MYSQL_PASSWORD TEST_AGENT_XXL_JOB_ACCESS_TOKEN \
    TEST_AGENT_XXL_JOB_COOKIE_SECURE \
    TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL \
    TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL \
    TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH \
    TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES \
    TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64; do
    [[ "$(grep -c "^${key}=" "${backend_4}" || true)" -eq 1 \
      && "$(grep -c "^${key}=" "${backend_114}" || true)" -eq 1 ]] || {
      echo "Both backend configs must contain exactly one ${key}" >&2
      exit 1
    }
  done
  grep -Fxq "TEST_AGENT_XXL_JOB_MYSQL_URL=${expected_url}" "${backend_4}"
  grep -Fxq "TEST_AGENT_XXL_JOB_MYSQL_URL=${expected_url}" "${backend_114}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=root' "${backend_4}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_MYSQL_USERNAME=root' "${backend_114}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_COOKIE_SECURE=false' "${backend_4}"
  grep -Fxq 'TEST_AGENT_XXL_JOB_COOKIE_SECURE=false' "${backend_114}"
  grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true' "${backend_4}"
  grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_ALLOW_INSECURE_CONTROL=true' "${backend_114}"
  grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/' "${backend_4}"
  grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_DOWNLOAD_BASE_URL=http://122.233.30.2/downloads/local-opencode-client/' "${backend_114}"
  grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json' "${backend_4}"
  grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_CATALOG_PATH=catalog.json' "${backend_114}"
  grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2' "${backend_4}"
  grep -Fxq 'TEST_AGENT_LOCAL_CLIENT_TRUSTED_PROXY_ADDRESSES=122.233.30.2' "${backend_114}"
  backend_4_value="$(sed -n \
    's/^TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64=//p' \
    "${backend_4}")"
  backend_114_value="$(sed -n \
    's/^TEST_AGENT_LOCAL_CLIENT_VERSION_MANAGEMENT_SIGNING_PUBLIC_KEY_BASE64=//p' \
    "${backend_114}")"
  [[ "${backend_4_value}" == "${LOCAL_CLIENT_SIGNING_PUBLIC_KEY_BASE64}" \
    && "${backend_114_value}" == "${LOCAL_CLIENT_SIGNING_PUBLIC_KEY_BASE64}" ]] || {
    echo "Backend local-client signing public keys do not match the package input" >&2
    exit 1
  }
  grep -Fxq 'TEST_AGENT_TCDS_BASE_URL=http://tcds-prod.sdc.icbc:9080' "${backend_4}"
  grep -Fxq 'TEST_AGENT_TCDS_BASE_URL=http://tcds-prod.sdc.icbc:9080' "${backend_114}"
  for expected in \
    'TEST_AGENT_AAM_BASE_URL=http://zfw.sdc.cs.icbc' \
    'TEST_AGENT_AAM_CONNECT_TIMEOUT=3s' \
    'TEST_AGENT_AAM_REQUEST_TIMEOUT=5s' \
    'TEST_AGENT_AAM_MAX_RESPONSE_BYTES=65536'; do
    grep -Fxq "${expected}" "${backend_4}"
    grep -Fxq "${expected}" "${backend_114}"
  done
  grep -Fxq 'TEST_AGENT_SKILLHUB_ENABLED=true' "${backend_4}"
  grep -Fxq 'TEST_AGENT_SKILLHUB_ENABLED=true' "${backend_114}"
  grep -Fxq 'TEST_AGENT_SKILLHUB_BASE_URL=http://ai-code.sdc.icbc/icbc/skill' "${backend_4}"
  grep -Fxq 'TEST_AGENT_SKILLHUB_BASE_URL=http://ai-code.sdc.icbc/icbc/skill' "${backend_114}"
  for expected in \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_ENABLED=true' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_URL=jdbc:clickhouse://122.233.30.147:8123/testagent_analytics' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_USERNAME=ck' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_BACKFILL_ENABLED=false' \
    'TEST_AGENT_ANALYTICS_CLICKHOUSE_CLEANUP_LEGACY_ROLLUPS=false' \
    'TEST_AGENT_MEMORY_ENABLED=true' \
    'TEST_AGENT_MEMORY_SERVICE_URL=http://122.233.30.160:18888' \
    'TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_CLIENT_ID=mem0-cluster'; do
    grep -Fxq "${expected}" "${backend_4}"
    grep -Fxq "${expected}" "${backend_114}"
  done
  for secret_spec in \
    TEST_AGENT_ANALYTICS_CLICKHOUSE_PASSWORD:8 \
    TEST_AGENT_SKILLHUB_ACCESS_KEY:16 \
    TEST_AGENT_MEMORY_SERVICE_API_KEY:32 \
    TEST_AGENT_MEMORY_MODEL_GATEWAY_HMAC_SECRET:32; do
    key="${secret_spec%%:*}"
    minimum_length="${secret_spec##*:}"
    [[ "$(grep -c "^${key}=" "${backend_4}" || true)" -eq 1 \
      && "$(grep -c "^${key}=" "${backend_114}" || true)" -eq 1 ]] || {
      echo "Both backend configs must contain exactly one ${key}" >&2
      exit 1
    }
    backend_4_value="$(sed -n "s/^${key}=//p" "${backend_4}")"
    backend_114_value="$(sed -n "s/^${key}=//p" "${backend_114}")"
    [[ "${backend_4_value}" == "${backend_114_value}" ]] || {
      echo "Prepared backend configs disagree on ${key}" >&2
      exit 1
    }
    [[ "${backend_4_value}" == "${PRESERVE_INSTALLED_MARKER}" \
      || ${#backend_4_value} -ge "${minimum_length}" ]] || {
      echo "Prepared ${key} is shorter than ${minimum_length} characters" >&2
      exit 1
    }
  done
  grep -Fxq 'TEST_AGENT_NGINX_XXL_JOB_ADMINS=122.233.30.4:18080,122.233.30.114:18080' "${frontend}"
  grep -Fxq 'TEST_AGENT_NGINX_TOOLBOX_IT_TOOLS_UPSTREAM=122.233.30.4:18120,122.233.30.114:18120' "${frontend}"
  grep -Fxq 'TEST_AGENT_NGINX_TOOLBOX_OMNI_TOOLS_UPSTREAM=122.233.30.4:18121,122.233.30.114:18121' "${frontend}"

  backend_password="$(sed -n 's/^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=//p' "${backend_4}")"
  backend_token="$(sed -n 's/^TEST_AGENT_XXL_JOB_ACCESS_TOKEN=//p' "${backend_4}")"
  [[ ${#backend_password} -ge 8 && ${#backend_token} -ge 32 ]] || {
    echo "Prepared XXL-JOB password or access token is too short" >&2
    exit 1
  }
  [[ "${backend_password}" == "$(sed -n 's/^TEST_AGENT_XXL_JOB_MYSQL_PASSWORD=//p' "${backend_114}")" ]] || {
    echo "MySQL passwords differ between prepared backend configs" >&2
    exit 1
  }
  [[ "${backend_token}" == "$(sed -n 's/^TEST_AGENT_XXL_JOB_ACCESS_TOKEN=//p' "${backend_114}")" ]] || {
    echo "XXL-JOB access tokens differ between backend configs" >&2
    exit 1
  }
}

# 旧前端节点包可能仍只包含终端路由键。封装新交付包时在临时副本中完成一次性迁移，
# 不修改源敏感包，也不把任何路由值或其它 env 内容打印到日志。
normalize_frontend_server_routes() {
  local nginx_env="$1"
  local server_route_count legacy_route_count migrated
  server_route_count="$(grep -Ec '^TEST_AGENT_NGINX_SERVER_ROUTES=' "${nginx_env}" || true)"
  legacy_route_count="$(grep -Ec '^TEST_AGENT_NGINX_TERMINAL_ROUTES=' "${nginx_env}" || true)"
  if [[ "${server_route_count}" -eq 1 && "${legacy_route_count}" -eq 0 ]]; then
    return 0
  fi
  if [[ "${server_route_count}" -ne 0 || "${legacy_route_count}" -ne 1 ]]; then
    echo "Frontend nginx.env must contain exactly one server route key (legacy or current)" >&2
    exit 1
  fi
  migrated="$(mktemp "${nginx_env}.new.XXXXXX")"
  sed 's/^TEST_AGENT_NGINX_TERMINAL_ROUTES=/TEST_AGENT_NGINX_SERVER_ROUTES=/' \
    "${nginx_env}" >"${migrated}"
  chmod --reference="${nginx_env}" "${migrated}" 2>/dev/null || chmod 0600 "${migrated}"
  mv -f "${migrated}" "${nginx_env}"
}

normalize_frontend_node_archive() {
  local source="$1"
  local node_dir="$2"
  local node_root="${TMP_ROOT}/normalize-${node_dir}"
  local nginx_env target
  mkdir -p "${node_root}"
  tar -C "${node_root}" -xzf "${source}"
  nginx_env="${node_root}/${node_dir}/config/nginx.env"
  normalize_frontend_server_routes "${nginx_env}"
  replace_or_append_env_value "${nginx_env}" TEST_AGENT_NGINX_TOOLBOX_IT_TOOLS_UPSTREAM \
    '122.233.30.4:18120,122.233.30.114:18120'
  replace_or_append_env_value "${nginx_env}" TEST_AGENT_NGINX_TOOLBOX_OMNI_TOOLS_UPSTREAM \
    '122.233.30.4:18121,122.233.30.114:18121'
  target="${TMP_ROOT}/$(basename "${source}")"
  archive_create_tar_gz "${target}" "${node_root}" "${node_dir}"
  chmod 0600 "${target}"
  printf '%s\n' "${target}"
}

NODE_2="$(normalize_frontend_node_archive "${NODE_2}" test-agent-two-backend-122.233.30.2)"
validate_node_archive "${NODE_2}" test-agent-two-backend-122.233.30.2 nginx.env
validate_mysql_cluster_config

BUNDLE_ROOT="${TMP_ROOT}/${BUNDLE_NAME}"
mkdir -p "${BUNDLE_ROOT}/nodes" "${OUTPUT_DIR}"
install -m 0644 "${SCRIPT_DIR}/MULTI-BACKEND.md" "${BUNDLE_ROOT}/START-HERE.md"
install -m 0644 "${SCRIPT_DIR}/deploy-node-common.sh" "${BUNDLE_ROOT}/deploy-node-common.sh"
install -m 0755 "${SCRIPT_DIR}/deploy-backend-node.sh" "${BUNDLE_ROOT}/deploy-backend-node.sh"
install -m 0755 "${SCRIPT_DIR}/deploy-frontend-node.sh" "${BUNDLE_ROOT}/deploy-frontend-node.sh"
install -m 0755 "${SCRIPT_DIR}/init-backend-node-config.sh" "${BUNDLE_ROOT}/init-backend-node-config.sh"
install -m 0755 "${SCRIPT_DIR}/register-backend-on-frontend.sh" \
  "${BUNDLE_ROOT}/register-backend-on-frontend.sh"
install -m 0644 "${SCRIPT_DIR}/archive-common.sh" "${BUNDLE_ROOT}/archive-common.sh"
install -m 0600 "${RELEASE_ARCHIVE}" "${BUNDLE_ROOT}/test-agent-internal-release.zip"
printf '%s  %s\n' \
  "$(sha256_digest "${BUNDLE_ROOT}/test-agent-internal-release.zip")" \
  test-agent-internal-release.zip \
  >"${BUNDLE_ROOT}/test-agent-internal-release.zip.sha256"

# 节点包保留现场配置，但总是换入当前逐机脚本和手册，避免携带历史部署逻辑。
repack_node() {
  local source="$1"
  local node_dir="$2"
  local node_root="${TMP_ROOT}/repack-${node_dir}"
  local target="${BUNDLE_ROOT}/nodes/$(basename "${source}")"
  mkdir -p "${node_root}"
  tar -C "${node_root}" -xzf "${source}"
  if [[ "${node_dir}" == "test-agent-two-backend-122.233.30.2" ]]; then
    normalize_frontend_server_routes "${node_root}/${node_dir}/config/nginx.env"
  fi
  install -m 0755 "${SCRIPT_DIR}/deploy-multi-backend-node.sh" \
    "${node_root}/${node_dir}/deploy-multi-backend-node.sh"
  install -m 0644 "${SCRIPT_DIR}/MULTI-BACKEND.md" \
    "${node_root}/${node_dir}/MULTI-BACKEND.md"
  archive_create_tar_gz "${target}" "${node_root}" "${node_dir}"
  chmod 0600 "${target}"
  printf '%s  %s\n' "$(sha256_digest "${target}")" "$(basename "${target}")" \
    >"${target}.sha256"
}

repack_node "${NODE_4}" test-agent-two-backend-122.233.30.4
repack_node "${NODE_114}" test-agent-two-backend-122.233.30.114
repack_node "${NODE_2}" test-agent-two-backend-122.233.30.2

TMP_ARCHIVE="${TMP_ROOT}/${BUNDLE_NAME}.zip"
archive_create_zip "${TMP_ARCHIVE}" "${TMP_ROOT}" "${BUNDLE_NAME}"
unzip -tq "${TMP_ARCHIVE}" >/dev/null

OUTPUT_ARCHIVE="${OUTPUT_DIR}/${BUNDLE_NAME}.zip"
OUTPUT_CHECKSUM="${OUTPUT_ARCHIVE}.sha256"
OUTPUT_ARCHIVE_TMP="$(mktemp "${OUTPUT_DIR}/.${BUNDLE_NAME}.zip.XXXXXX")"
OUTPUT_CHECKSUM_TMP="$(mktemp "${OUTPUT_DIR}/.${BUNDLE_NAME}.zip.sha256.XXXXXX")"
install -m 0600 "${TMP_ARCHIVE}" "${OUTPUT_ARCHIVE_TMP}"
printf '%s  %s\n' "$(sha256_digest "${OUTPUT_ARCHIVE_TMP}")" "$(basename "${OUTPUT_ARCHIVE}")" \
  >"${OUTPUT_CHECKSUM_TMP}"
chmod 0600 "${OUTPUT_CHECKSUM_TMP}"

# 固定文件逐个原子替换，不生成日期或版本后缀，也不触发交互式覆盖。
mv -f "${OUTPUT_ARCHIVE_TMP}" "${OUTPUT_ARCHIVE}"
mv -f "${OUTPUT_CHECKSUM_TMP}" "${OUTPUT_CHECKSUM}"
archive_strip_file_metadata "${OUTPUT_ARCHIVE}" "${OUTPUT_CHECKSUM}"

printf 'Complete two-backend bundle: %s\n' "${OUTPUT_ARCHIVE}"
printf 'Bundle checksum: %s\n' "${OUTPUT_CHECKSUM}"
printf 'Bundle SHA256: %s\n' "$(sha256_digest "${OUTPUT_ARCHIVE}")"
