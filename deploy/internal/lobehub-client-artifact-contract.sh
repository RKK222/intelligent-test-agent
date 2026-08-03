#!/usr/bin/env bash

# LobeHub 客户端证据由外部原生构建机产生，并在汇集、打包和现场安装三个边界重复校验。
# 本文件只定义可 source 的函数，不修改调用方的 shell 选项，也不接受“待审批”占位状态。

lobehub_client_contract_error() {
  printf 'LobeHub client artifact contract failed: %s\n' "$1" >&2
  return 1
}

lobehub_client_require_file() {
  local path="$1" description="$2"
  [[ -f "${path}" && ! -L "${path}" && -s "${path}" ]] ||
    lobehub_client_contract_error "${description} must be a non-empty regular file"
}

lobehub_client_sha256_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${path}" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${path}" | awk '{print $1}'
  else
    lobehub_client_contract_error 'neither sha256sum nor shasum is available'
  fi
}

lobehub_client_validate_evidence_syntax() {
  local path="$1" description="$2"
  lobehub_client_require_file "${path}" "${description}" || return 1
  awk -v description="${description}" '
    {
      sub(/\r$/, "", $0)
      if ($0 !~ /^[A-Z][A-Z0-9_]*=/) {
        printf "LobeHub client artifact contract failed: %s contains an invalid line\n", description > "/dev/stderr"
        exit 1
      }
      key = substr($0, 1, index($0, "=") - 1)
      if (seen[key]++) {
        printf "LobeHub client artifact contract failed: %s contains duplicate key %s\n", description, key > "/dev/stderr"
        exit 1
      }
    }
    END {
      if (NR == 0) {
        printf "LobeHub client artifact contract failed: %s is empty\n", description > "/dev/stderr"
        exit 1
      }
    }
  ' "${path}"
}

lobehub_client_evidence_value() {
  local path="$1" key="$2"
  awk -F= -v wanted="${key}" '
    {
      sub(/\r$/, "", $0)
      if ($1 == wanted) {
        print substr($0, index($0, "=") + 1)
        found = 1
      }
    }
    END { if (!found) exit 1 }
  ' "${path}"
}

lobehub_client_require_value() {
  local path="$1" key="$2" description="$3" value
  value="$(lobehub_client_evidence_value "${path}" "${key}")" || {
    lobehub_client_contract_error "${description} is missing ${key}"
    return 1
  }
  [[ -n "${value}" ]] || {
    lobehub_client_contract_error "${description} has an empty ${key}"
    return 1
  }
  printf '%s' "${value}"
}

lobehub_client_is_placeholder() {
  local normalized
  normalized="$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | tr -d '[:space:]_-')"
  case "${normalized}" in
    REPLACE|REPLACEME|PLACEHOLDER|PENDING|TBD|UNKNOWN|UNSET|NONE|NA) return 0 ;;
    *) return 1 ;;
  esac
}

lobehub_verify_windows_client_artifact() {
  local client="$1" evidence="$2" internal_version="$3" fork_commit="$4"
  local status subject thumbprint evidence_sha actual_sha evidence_version evidence_commit
  local architecture execution_mode

  lobehub_client_require_file "${client}" 'Windows x64 client' || return 1
  lobehub_client_validate_evidence_syntax "${evidence}" 'Windows Authenticode evidence' || return 1

  status="$(lobehub_client_require_value "${evidence}" AUTHENTICODE_STATUS 'Windows Authenticode evidence')" || return 1
  subject="$(lobehub_client_require_value "${evidence}" AUTHENTICODE_SUBJECT 'Windows Authenticode evidence')" || return 1
  thumbprint="$(lobehub_client_require_value "${evidence}" AUTHENTICODE_THUMBPRINT 'Windows Authenticode evidence')" || return 1
  evidence_sha="$(lobehub_client_require_value "${evidence}" AUTHENTICODE_FILE_SHA256 'Windows Authenticode evidence')" || return 1
  evidence_version="$(lobehub_client_require_value "${evidence}" LOBEHUB_INTERNAL_VERSION 'Windows Authenticode evidence')" || return 1
  evidence_commit="$(lobehub_client_require_value "${evidence}" LOBEHUB_FORK_COMMIT 'Windows Authenticode evidence')" || return 1
  architecture="$(lobehub_client_require_value "${evidence}" CLIENT_ARCHITECTURE 'Windows Authenticode evidence')" || return 1
  execution_mode="$(lobehub_client_require_value "${evidence}" CLIENT_EXECUTION_MODE 'Windows Authenticode evidence')" || return 1

  [[ "${status}" == Valid ]] || lobehub_client_contract_error 'Windows Authenticode status must be Valid' || return 1
  ! lobehub_client_is_placeholder "${subject}" || lobehub_client_contract_error 'Windows signer subject is a placeholder' || return 1
  [[ "${thumbprint}" =~ ^([0-9A-Fa-f]{40}|[0-9A-Fa-f]{64})$ ]] ||
    lobehub_client_contract_error 'Windows signer thumbprint must contain 40 or 64 hexadecimal characters' || return 1
  [[ "${evidence_sha}" =~ ^[0-9a-f]{64}$ ]] ||
    lobehub_client_contract_error 'Windows client SHA-256 must be lowercase hexadecimal' || return 1
  [[ "${evidence_version}" == "${internal_version}" ]] ||
    lobehub_client_contract_error 'Windows evidence internal version does not match the release lock' || return 1
  [[ "${evidence_commit}" == "${fork_commit}" ]] ||
    lobehub_client_contract_error 'Windows evidence fork commit does not match the release lock' || return 1
  [[ "${architecture}" == x64 ]] || lobehub_client_contract_error 'Windows client architecture must be x64' || return 1
  [[ "${execution_mode}" == disabled ]] ||
    lobehub_client_contract_error 'Windows device execution must remain disabled' || return 1

  actual_sha="$(lobehub_client_sha256_file "${client}")" || return 1
  [[ "${actual_sha}" == "${evidence_sha}" ]] ||
    lobehub_client_contract_error 'Windows Authenticode evidence does not match the client file' || return 1
}

lobehub_verify_linux_client_artifact() {
  local client="$1" evidence="$2" acceptance_record="$3" build_evidence="$4"
  local internal_version="$5" fork_commit="$6"
  local status approver evidence_sha actual_sha evidence_version evidence_commit
  local architecture execution_mode validation_os validation_kernel record_sha actual_record_sha
  local record_reviewer change_id validation_result login_result execution_result
  local network_result download_result isolation_result
  local build_evidence_sha actual_build_evidence_sha build_status builder build_client_sha
  local build_version build_commit build_architecture build_execution_mode build_os build_kernel
  local build_node_version build_bun_version source_archive_sha builder_normalized approver_normalized

  lobehub_client_require_file "${client}" 'Linux x86_64 client' || return 1
  lobehub_client_validate_evidence_syntax "${evidence}" 'Linux client approval evidence' || return 1
  lobehub_client_validate_evidence_syntax "${acceptance_record}" 'Linux client acceptance record' || return 1
  lobehub_client_validate_evidence_syntax "${build_evidence}" 'Linux client build evidence' || return 1

  status="$(lobehub_client_require_value "${evidence}" LINUX_APPROVAL_STATUS 'Linux client approval evidence')" || return 1
  approver="$(lobehub_client_require_value "${evidence}" LINUX_APPROVER 'Linux client approval evidence')" || return 1
  evidence_sha="$(lobehub_client_require_value "${evidence}" LINUX_CLIENT_FILE_SHA256 'Linux client approval evidence')" || return 1
  evidence_version="$(lobehub_client_require_value "${evidence}" LOBEHUB_INTERNAL_VERSION 'Linux client approval evidence')" || return 1
  evidence_commit="$(lobehub_client_require_value "${evidence}" LOBEHUB_FORK_COMMIT 'Linux client approval evidence')" || return 1
  architecture="$(lobehub_client_require_value "${evidence}" CLIENT_ARCHITECTURE 'Linux client approval evidence')" || return 1
  execution_mode="$(lobehub_client_require_value "${evidence}" CLIENT_EXECUTION_MODE 'Linux client approval evidence')" || return 1
  validation_os="$(lobehub_client_require_value "${evidence}" LINUX_VALIDATION_OS 'Linux client approval evidence')" || return 1
  validation_kernel="$(lobehub_client_require_value "${evidence}" LINUX_VALIDATION_KERNEL 'Linux client approval evidence')" || return 1
  record_sha="$(lobehub_client_require_value "${evidence}" LINUX_ACCEPTANCE_RECORD_SHA256 'Linux client approval evidence')" || return 1
  build_evidence_sha="$(lobehub_client_require_value "${evidence}" LINUX_BUILD_EVIDENCE_SHA256 'Linux client approval evidence')" || return 1

  build_status="$(lobehub_client_require_value "${build_evidence}" LINUX_BUILD_STATUS 'Linux client build evidence')" || return 1
  builder="$(lobehub_client_require_value "${build_evidence}" LINUX_BUILDER 'Linux client build evidence')" || return 1
  build_client_sha="$(lobehub_client_require_value "${build_evidence}" LINUX_CLIENT_FILE_SHA256 'Linux client build evidence')" || return 1
  build_version="$(lobehub_client_require_value "${build_evidence}" LOBEHUB_INTERNAL_VERSION 'Linux client build evidence')" || return 1
  build_commit="$(lobehub_client_require_value "${build_evidence}" LOBEHUB_FORK_COMMIT 'Linux client build evidence')" || return 1
  build_architecture="$(lobehub_client_require_value "${build_evidence}" CLIENT_ARCHITECTURE 'Linux client build evidence')" || return 1
  build_execution_mode="$(lobehub_client_require_value "${build_evidence}" CLIENT_EXECUTION_MODE 'Linux client build evidence')" || return 1
  build_os="$(lobehub_client_require_value "${build_evidence}" BUILD_OS 'Linux client build evidence')" || return 1
  build_kernel="$(lobehub_client_require_value "${build_evidence}" BUILD_KERNEL 'Linux client build evidence')" || return 1
  build_node_version="$(lobehub_client_require_value "${build_evidence}" BUILD_NODE_VERSION 'Linux client build evidence')" || return 1
  build_bun_version="$(lobehub_client_require_value "${build_evidence}" BUILD_BUN_VERSION 'Linux client build evidence')" || return 1
  source_archive_sha="$(lobehub_client_require_value "${build_evidence}" SOURCE_ARCHIVE_SHA256 'Linux client build evidence')" || return 1

  record_reviewer="$(lobehub_client_require_value "${acceptance_record}" REVIEWER 'Linux client acceptance record')" || return 1
  change_id="$(lobehub_client_require_value "${acceptance_record}" APPROVAL_CHANGE_ID 'Linux client acceptance record')" || return 1
  validation_result="$(lobehub_client_require_value "${acceptance_record}" VALIDATION_RESULT 'Linux client acceptance record')" || return 1
  login_result="$(lobehub_client_require_value "${acceptance_record}" CLIENT_LOGIN_RESULT 'Linux client acceptance record')" || return 1
  execution_result="$(lobehub_client_require_value "${acceptance_record}" DEVICE_EXECUTION_RESULT 'Linux client acceptance record')" || return 1
  network_result="$(lobehub_client_require_value "${acceptance_record}" PUBLIC_NETWORK_DEPENDENCY_RESULT 'Linux client acceptance record')" || return 1
  download_result="$(lobehub_client_require_value "${acceptance_record}" RUNTIME_DOWNLOAD_RESULT 'Linux client acceptance record')" || return 1
  isolation_result="$(lobehub_client_require_value "${acceptance_record}" LOCAL_DATA_ISOLATION_RESULT 'Linux client acceptance record')" || return 1

  [[ "${status}" == Approved ]] || lobehub_client_contract_error 'Linux client approval status must be Approved' || return 1
  ! lobehub_client_is_placeholder "${approver}" || lobehub_client_contract_error 'Linux approver is a placeholder' || return 1
  [[ "${approver}" =~ ^[A-Za-z0-9][A-Za-z0-9@._+-]{2,127}$ ]] ||
    lobehub_client_contract_error 'Linux approver must be a stable internal identity' || return 1
  [[ "${build_status}" == Candidate ]] || lobehub_client_contract_error 'Linux build status must be Candidate' || return 1
  ! lobehub_client_is_placeholder "${builder}" || lobehub_client_contract_error 'Linux builder is a placeholder' || return 1
  [[ "${builder}" =~ ^[A-Za-z0-9][A-Za-z0-9@._+-]{2,127}$ ]] ||
    lobehub_client_contract_error 'Linux builder must be a stable internal identity' || return 1
  builder_normalized="$(printf '%s' "${builder}" | tr '[:upper:]' '[:lower:]')"
  approver_normalized="$(printf '%s' "${approver}" | tr '[:upper:]' '[:lower:]')"
  [[ "${builder_normalized}" != "${approver_normalized}" ]] ||
    lobehub_client_contract_error 'Linux builder and approver must be different identities' || return 1
  ! lobehub_client_is_placeholder "${validation_os}" || lobehub_client_contract_error 'Linux validation OS is a placeholder' || return 1
  ! lobehub_client_is_placeholder "${validation_kernel}" || lobehub_client_contract_error 'Linux validation kernel is a placeholder' || return 1
  ! lobehub_client_is_placeholder "${build_os}" || lobehub_client_contract_error 'Linux build OS is a placeholder' || return 1
  ! lobehub_client_is_placeholder "${build_kernel}" || lobehub_client_contract_error 'Linux build kernel is a placeholder' || return 1
  ! lobehub_client_is_placeholder "${change_id}" || lobehub_client_contract_error 'Linux approval change ID is a placeholder' || return 1
  [[ "${record_reviewer}" == "${approver}" ]] ||
    lobehub_client_contract_error 'Linux acceptance record reviewer does not match the approver' || return 1
  [[ "${validation_result}" == Passed && "${login_result}" == Passed \
    && "${execution_result}" == Disabled && "${network_result}" == None \
    && "${download_result}" == Blocked && "${isolation_result}" == Passed ]] ||
    lobehub_client_contract_error 'Linux acceptance record contains an unsuccessful mandatory result' || return 1
  [[ "${evidence_sha}" =~ ^[0-9a-f]{64}$ ]] ||
    lobehub_client_contract_error 'Linux client SHA-256 must be lowercase hexadecimal' || return 1
  [[ "${record_sha}" =~ ^[0-9a-f]{64}$ ]] ||
    lobehub_client_contract_error 'Linux acceptance record SHA-256 must be lowercase hexadecimal' || return 1
  [[ "${build_evidence_sha}" =~ ^[0-9a-f]{64}$ ]] ||
    lobehub_client_contract_error 'Linux build evidence SHA-256 must be lowercase hexadecimal' || return 1
  [[ "${source_archive_sha}" =~ ^[0-9a-f]{64}$ ]] ||
    lobehub_client_contract_error 'Linux source archive SHA-256 must be lowercase hexadecimal' || return 1
  [[ "${evidence_version}" == "${internal_version}" ]] ||
    lobehub_client_contract_error 'Linux evidence internal version does not match the release lock' || return 1
  [[ "${evidence_commit}" == "${fork_commit}" ]] ||
    lobehub_client_contract_error 'Linux evidence fork commit does not match the release lock' || return 1
  [[ "${architecture}" == x86_64 ]] || lobehub_client_contract_error 'Linux client architecture must be x86_64' || return 1
  [[ "${execution_mode}" == disabled ]] ||
    lobehub_client_contract_error 'Linux device execution must remain disabled' || return 1
  [[ "${build_client_sha}" == "${evidence_sha}" \
    && "${build_version}" == "${internal_version}" \
    && "${build_commit}" == "${fork_commit}" \
    && "${build_architecture}" == x86_64 \
    && "${build_execution_mode}" == disabled \
    && "${build_node_version}" == v24.11.1 \
    && "${build_bun_version}" == 1.3.2 ]] ||
    lobehub_client_contract_error 'Linux build evidence does not match the approved release contract' || return 1

  actual_sha="$(lobehub_client_sha256_file "${client}")" || return 1
  [[ "${actual_sha}" == "${evidence_sha}" ]] ||
    lobehub_client_contract_error 'Linux approval evidence does not match the client file' || return 1
  actual_record_sha="$(lobehub_client_sha256_file "${acceptance_record}")" || return 1
  [[ "${actual_record_sha}" == "${record_sha}" ]] ||
    lobehub_client_contract_error 'Linux approval evidence does not match the acceptance record' || return 1
  actual_build_evidence_sha="$(lobehub_client_sha256_file "${build_evidence}")" || return 1
  [[ "${actual_build_evidence_sha}" == "${build_evidence_sha}" ]] ||
    lobehub_client_contract_error 'Linux approval evidence does not match the build evidence' || return 1
}

lobehub_verify_client_artifacts() {
  local windows_client="$1" windows_evidence="$2" linux_client="$3" linux_evidence="$4"
  local linux_acceptance_record="$5" linux_build_evidence="$6" internal_version="$7" fork_commit="$8"

  [[ "${internal_version}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-platform\.[0-9]+$ ]] ||
    lobehub_client_contract_error 'expected internal version is malformed' || return 1
  [[ "${fork_commit}" =~ ^[0-9a-f]{40}$ ]] ||
    lobehub_client_contract_error 'expected fork commit is malformed' || return 1

  lobehub_verify_windows_client_artifact \
    "${windows_client}" "${windows_evidence}" "${internal_version}" "${fork_commit}" || return 1
  lobehub_verify_linux_client_artifact \
    "${linux_client}" "${linux_evidence}" "${linux_acceptance_record}" \
    "${linux_build_evidence}" "${internal_version}" "${fork_commit}" || return 1
}
