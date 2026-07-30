#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ARTIFACT_DIR="${LOBEHUB_ARTIFACT_DIR:-${RELEASE_ROOT}/dist/lobehub}"
BASE_DIR="${TEST_AGENT_BASE_DIR:-/data/testagent}"
ENV_FILE="${BASE_DIR}/config/lobehub.env"
SYSTEMD_UNIT_DIR="${TEST_AGENT_SYSTEMD_UNIT_DIR:-/etc/systemd/system}"

require_file() {
  [[ -f "$1" ]] || { echo "Required LobeHub artifact not found: $1" >&2; exit 1; }
}

release_value() {
  local key="$1"
  awk -F= -v wanted="${key}" '$1 == wanted { print substr($0, index($0, "=") + 1); found=1 } END { if (!found) exit 1 }' \
    "${ARTIFACT_DIR}/release.env"
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

verify_artifact_contract() {
  local internal_version required_file line path listed_paths expected_paths
  local checksum_line_pattern client_sha evidence_sha

  [[ -d "${ARTIFACT_DIR}" && ! -L "${ARTIFACT_DIR}" ]] || {
    echo "LobeHub artifact directory must be a real directory: ${ARTIFACT_DIR}" >&2
    exit 1
  }
  if [[ -n "$(find "${ARTIFACT_DIR}" -type l -print -quit)" ]]; then
    echo "LobeHub artifact set must not contain symbolic links" >&2
    exit 1
  fi
  if [[ -n "$(find "${ARTIFACT_DIR}" -type f -name '*[[:space:]]*' -print -quit)" ]]; then
    echo "LobeHub artifact filenames must not contain whitespace" >&2
    exit 1
  fi

  internal_version="$(release_value LOBEHUB_INTERNAL_VERSION)"
  [[ "${internal_version}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-platform\.[0-9]+$ ]] || {
    echo "LobeHub release manifest contains an invalid internal version" >&2
    exit 1
  }
  for required_file in \
    release.env SHA256SUMS approved-resources.json LICENSES.txt \
    windows-authenticode-verification.txt \
    images/lobehub-image.tar images/paradedb-image.tar images/rustfs-image.tar \
    clients/lobehub-windows-x64.exe clients/lobehub-linux-x86_64.tar.gz \
    bin/mc-linux-amd64 sbom/lobehub.spdx.json \
    "source/lobehub-${internal_version}.tar.gz"; do
    require_file "${ARTIFACT_DIR}/${required_file}"
    [[ -s "${ARTIFACT_DIR}/${required_file}" ]] || {
      echo "Required LobeHub artifact is empty: ${required_file}" >&2
      exit 1
    }
  done

  [[ "$(release_value LOBEHUB_WINDOWS_AUTHENTICODE_VERIFIED)" == true ]] || {
    echo "Windows Authenticode verification is not recorded as successful" >&2
    exit 1
  }
  [[ "$(release_value LOBEHUB_LINUX_EXECUTION_DEFAULT)" == false ]] || {
    echo "Linux local execution must be disabled by default" >&2
    exit 1
  }
  [[ "$(release_value LOBEHUB_PARADEDB_POSTGRES_MAJOR)" == 17 ]] || {
    echo "LobeHub offline deployment requires PostgreSQL 17" >&2
    exit 1
  }
  [[ "$(awk -F= '$1 == "AUTHENTICODE_STATUS" {print substr($0,index($0,"=")+1); exit}' \
    "${ARTIFACT_DIR}/windows-authenticode-verification.txt")" == Valid ]] || {
    echo "Windows Authenticode evidence status is not Valid" >&2
    exit 1
  }
  for evidence_key in AUTHENTICODE_SUBJECT AUTHENTICODE_THUMBPRINT AUTHENTICODE_FILE_SHA256; do
    [[ -n "$(awk -F= -v wanted="${evidence_key}" \
      '$1 == wanted {print substr($0,index($0,"=")+1); exit}' \
      "${ARTIFACT_DIR}/windows-authenticode-verification.txt")" ]] || {
      echo "Windows Authenticode evidence is incomplete: ${evidence_key}" >&2
      exit 1
    }
  done
  client_sha="$(sha256_file "${ARTIFACT_DIR}/clients/lobehub-windows-x64.exe")"
  evidence_sha="$(awk -F= '$1 == "AUTHENTICODE_FILE_SHA256" {print substr($0,index($0,"=")+1); exit}' \
    "${ARTIFACT_DIR}/windows-authenticode-verification.txt")"
  [[ "${evidence_sha}" == "${client_sha}" ]] || {
    echo "Windows Authenticode evidence does not match the packaged client" >&2
    exit 1
  }

  # 现场安装器再次要求清单完整覆盖，不能用只列出少数未篡改文件的清单绕过校验。
  checksum_line_pattern='^([0-9a-f]{64})[[:space:]]+\*?([^[:space:]]+)$'
  listed_paths=""
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ "${line}" =~ ${checksum_line_pattern} ]] || {
      echo "Malformed line in LobeHub SHA256SUMS" >&2
      exit 1
    }
    path="${BASH_REMATCH[2]}"
    [[ -n "${path}" && "${path}" != /* && "${path}" != ../* && "${path}" != */../* \
      && "${path}" != SHA256SUMS && -f "${ARTIFACT_DIR}/${path}" ]] || {
      echo "Unsafe or missing path in LobeHub SHA256SUMS: ${path}" >&2
      exit 1
    }
    listed_paths+="${path}"$'\n'
  done <"${ARTIFACT_DIR}/SHA256SUMS"
  listed_paths="$(printf '%s' "${listed_paths}" | LC_ALL=C sort)"
  [[ -z "$(printf '%s\n' "${listed_paths}" | uniq -d)" ]] || {
    echo "LobeHub SHA256SUMS contains duplicate paths" >&2
    exit 1
  }
  expected_paths="$(cd "${ARTIFACT_DIR}" && find . -type f ! -name SHA256SUMS -print \
    | sed 's#^\./##' | LC_ALL=C sort)"
  [[ "${listed_paths}" == "${expected_paths}" ]] || {
    echo "LobeHub SHA256SUMS must cover every artifact exactly once" >&2
    exit 1
  }

  if command -v sha256sum >/dev/null 2>&1; then
    (cd "${ARTIFACT_DIR}" && sha256sum -c SHA256SUMS)
  else
    (cd "${ARTIFACT_DIR}" && shasum -a 256 -c SHA256SUMS)
  fi
}

[[ "$(id -u)" -eq 0 ]] || { echo "Run the offline installer as root" >&2; exit 1; }
require_file "${ARTIFACT_DIR}/release.env"
[[ "${BASE_DIR}" == /* && "${SYSTEMD_UNIT_DIR}" == /* ]] || {
  echo "LobeHub install and systemd directories must be absolute paths" >&2
  exit 1
}
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
command -v systemctl >/dev/null 2>&1 || { echo "systemctl is required" >&2; exit 1; }

# 所有完整性、客户端和签名证据检查都必须在 docker load 或目标目录写入之前完成。
verify_artifact_contract

for image_tar in lobehub-image.tar paradedb-image.tar rustfs-image.tar; do
  require_file "${ARTIFACT_DIR}/images/${image_tar}"
  docker load -i "${ARTIFACT_DIR}/images/${image_tar}" >/dev/null
done
for image_key in LOBEHUB_APP_IMAGE LOBEHUB_PARADEDB_IMAGE LOBEHUB_RUSTFS_IMAGE; do
  image_ref="$(release_value "${image_key}")"
  expected_image_id="$(release_value "${image_key}_ID")"
  docker image inspect "${image_ref}" >/dev/null 2>&1 || {
    echo "Loaded image archive does not provide the expected tag ${image_ref}" >&2
    exit 1
  }
  actual_image_id="$(docker image inspect -f '{{.Id}}' "${image_ref}")"
  [[ "${actual_image_id}" == "${expected_image_id}" ]] || {
    echo "Loaded image ID mismatch for ${image_ref}" >&2
    exit 1
  }
  architecture="$(docker image inspect -f '{{.Architecture}}' "${image_ref}")"
  image_os="$(docker image inspect -f '{{.Os}}' "${image_ref}")"
  [[ "${architecture}" == amd64 && "${image_os}" == linux ]] || {
    echo "LobeHub image must be linux/amd64, got ${image_os}/${architecture}: ${image_ref}" >&2
    exit 1
  }
done

install -d -m 0750 "${BASE_DIR}/lobehub/bin" "${BASE_DIR}/lobehub/clients" \
  "${BASE_DIR}/lobehub/release" "${BASE_DIR}/config" "${BASE_DIR}/tmp/model-gateway" \
  "${BASE_DIR}/deploy/internal"
install -m 0755 "${ARTIFACT_DIR}/bin/mc-linux-amd64" "${BASE_DIR}/lobehub/bin/mc-linux-amd64"
install -m 0644 "${ARTIFACT_DIR}/clients/lobehub-windows-x64.exe" \
  "${BASE_DIR}/lobehub/clients/lobehub-windows-x64.exe"
install -m 0644 "${ARTIFACT_DIR}/clients/lobehub-linux-x86_64.tar.gz" \
  "${BASE_DIR}/lobehub/clients/lobehub-linux-x86_64.tar.gz"
cp -a "${ARTIFACT_DIR}/release.env" "${ARTIFACT_DIR}/sbom" "${ARTIFACT_DIR}/source" \
  "${ARTIFACT_DIR}/approved-resources.json" "${ARTIFACT_DIR}/LICENSES.txt" \
  "${ARTIFACT_DIR}/windows-authenticode-verification.txt" "${BASE_DIR}/lobehub/release/"

install -m 0755 "${SCRIPT_DIR}/lobehub-docker.sh" "${BASE_DIR}/deploy/internal/lobehub-docker.sh"
if [[ ! -f "${ENV_FILE}" ]]; then
  install -m 0600 "${SCRIPT_DIR}/lobehub.env.example" "${ENV_FILE}"
  echo "Created ${ENV_FILE}; populate all REPLACE_ values before starting LobeHub." >&2
fi
install -d -m 0755 "${SYSTEMD_UNIT_DIR}"
install -m 0644 "${SCRIPT_DIR}/systemd/test-agent-lobehub.service" \
  "${SYSTEMD_UNIT_DIR}/test-agent-lobehub.service"
systemctl daemon-reload

echo "LobeHub offline artifacts installed under ${BASE_DIR}."
echo "After configuring ${ENV_FILE}, run: systemctl enable --now test-agent-lobehub"
