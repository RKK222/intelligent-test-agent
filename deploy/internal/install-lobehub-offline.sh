#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RELEASE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ARTIFACT_DIR="${LOBEHUB_ARTIFACT_DIR:-${RELEASE_ROOT}/dist/lobehub}"
BASE_DIR="${TEST_AGENT_BASE_DIR:-/data/testagent}"
ENV_FILE="${BASE_DIR}/config/lobehub.env"

require_file() {
  [[ -f "$1" ]] || { echo "Required LobeHub artifact not found: $1" >&2; exit 1; }
}

release_value() {
  local key="$1"
  awk -F= -v wanted="${key}" '$1 == wanted { print substr($0, index($0, "=") + 1); found=1 } END { if (!found) exit 1 }' \
    "${ARTIFACT_DIR}/release.env"
}

[[ "$(id -u)" -eq 0 ]] || { echo "Run the offline installer as root" >&2; exit 1; }
require_file "${ARTIFACT_DIR}/SHA256SUMS"
require_file "${ARTIFACT_DIR}/release.env"

if command -v sha256sum >/dev/null 2>&1; then
  (cd "${ARTIFACT_DIR}" && sha256sum -c SHA256SUMS)
else
  (cd "${ARTIFACT_DIR}" && shasum -a 256 -c SHA256SUMS)
fi

for image_tar in lobehub-image.tar paradedb-image.tar rustfs-image.tar; do
  require_file "${ARTIFACT_DIR}/images/${image_tar}"
  docker load -i "${ARTIFACT_DIR}/images/${image_tar}" >/dev/null
done
for image_key in LOBEHUB_APP_IMAGE LOBEHUB_PARADEDB_IMAGE LOBEHUB_RUSTFS_IMAGE; do
  image_ref="$(release_value "${image_key}")"
  docker image inspect "${image_ref}" >/dev/null 2>&1 || {
    echo "Loaded image archive does not provide the pinned reference ${image_ref}" >&2
    exit 1
  }
  architecture="$(docker image inspect -f '{{.Architecture}}' "${image_ref}")"
  [[ "${architecture}" == amd64 ]] || {
    echo "LobeHub image must be linux/amd64, got ${architecture}: ${image_ref}" >&2
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
install -m 0644 "${SCRIPT_DIR}/systemd/test-agent-lobehub.service" \
  /etc/systemd/system/test-agent-lobehub.service
systemctl daemon-reload

echo "LobeHub offline artifacts installed under ${BASE_DIR}."
echo "After configuring ${ENV_FILE}, run: systemctl enable --now test-agent-lobehub"
