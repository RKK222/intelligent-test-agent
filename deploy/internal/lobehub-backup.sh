#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="${TEST_AGENT_BASE_DIR:-/data/testagent}"
DB_CONTAINER="test-agent-lobehub-db"
RUSTFS_CONTAINER="test-agent-lobehub-rustfs"
APP_CONTAINER="test-agent-lobehub-app"
BACKUP_SCHEMA_VERSION=1

usage() {
  cat <<'USAGE' >&2
Usage:
  lobehub-backup.sh create --output-dir <absolute-directory>
  lobehub-backup.sh verify --archive <backup.tar.gz>
  lobehub-backup.sh restore --archive <backup.tar.gz> --confirm-restore

Creates and verifies a cold LobeHub backup containing ParadeDB, RustFS,
lobehub.env and the installed release manifest. All three LobeHub containers
must already be stopped. Restore keeps the previous files under
/data/testagent/lobehub/restore-rollback/<timestamp>/.
USAGE
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Required command not found: $1" >&2
    exit 1
  }
}

require_root() {
  [[ "$(id -u)" -eq 0 ]] || {
    echo 'Run the LobeHub cold backup tool as root' >&2
    exit 1
  }
}

normalize_existing_file() {
  local file="$1" parent
  [[ -f "${file}" && ! -L "${file}" ]] || {
    echo "Required regular file not found: ${file}" >&2
    exit 1
  }
  parent="$(cd "$(dirname "${file}")" && pwd -P)"
  printf '%s/%s' "${parent}" "$(basename "${file}")"
}

normalize_output_dir() {
  local directory="$1" parent
  [[ "${directory}" == /* ]] || {
    echo 'LobeHub backup output directory must be absolute' >&2
    exit 1
  }
  mkdir -p "${directory}"
  [[ -d "${directory}" && ! -L "${directory}" ]] || {
    echo "LobeHub backup output must be a real directory: ${directory}" >&2
    exit 1
  }
  parent="$(cd "$(dirname "${directory}")" && pwd -P)"
  printf '%s/%s' "${parent}" "$(basename "${directory}")"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo 'Neither sha256sum nor shasum is available' >&2
    exit 1
  fi
}

container_running() {
  local container="$1"
  [[ "$(docker inspect -f '{{.State.Running}}' "${container}" 2>/dev/null || true)" == true ]]
}

require_stack_stopped() {
  local container
  require_command docker
  docker info >/dev/null 2>&1 || {
    echo 'Cannot verify LobeHub container state because the Docker daemon is unavailable' >&2
    exit 1
  }
  for container in "${APP_CONTAINER}" "${RUSTFS_CONTAINER}" "${DB_CONTAINER}"; do
    if container_running "${container}"; then
      echo "Cold backup or restore requires the container to be stopped: ${container}" >&2
      exit 1
    fi
  done
}

require_backup_sources() {
  local env_file="${BASE_DIR}/config/lobehub.env" mode
  [[ "${BASE_DIR}" == /* && "${BASE_DIR}" != / && ! -L "${BASE_DIR}" ]] || {
    echo "Unsafe LobeHub base directory: ${BASE_DIR}" >&2
    exit 1
  }
  for directory in \
    "${BASE_DIR}/lobehub/paradedb" \
    "${BASE_DIR}/lobehub/rustfs" \
    "${BASE_DIR}/lobehub/release"; do
    [[ -d "${directory}" && ! -L "${directory}" ]] || {
      echo "Required LobeHub backup directory not found: ${directory}" >&2
      exit 1
    }
  done
  [[ -f "${env_file}" && ! -L "${env_file}" ]] || {
    echo "Required LobeHub env not found: ${env_file}" >&2
    exit 1
  }
  mode="$(stat -c '%a' "${env_file}" 2>/dev/null || stat -f '%Lp' "${env_file}")"
  [[ "${mode}" == 600 ]] || {
    echo "LobeHub env must have mode 0600 before backup: ${env_file}" >&2
    exit 1
  }
  [[ -f "${BASE_DIR}/lobehub/release/release.env" ]] || {
    echo 'Installed LobeHub release manifest is missing' >&2
    exit 1
  }
}

release_value() {
  local key="$1"
  awk -F= -v wanted="${key}" \
    '$1 == wanted { print substr($0, index($0, "=") + 1); found=1; exit } END { if (!found) exit 1 }' \
    "${BASE_DIR}/lobehub/release/release.env"
}

write_checksum() {
  local archive="$1" parent name digest
  parent="$(dirname "${archive}")"
  name="$(basename "${archive}")"
  digest="$(sha256_file "${archive}")"
  printf '%s  %s\n' "${digest}" "${name}" >"${archive}.sha256"
  chmod 0600 "${archive}.sha256"
}

verify_checksum() {
  local archive="$1" checksum expected_digest expected_name actual_digest archive_mode checksum_mode
  checksum="${archive}.sha256"
  [[ -f "${checksum}" && ! -L "${checksum}" ]] || {
    echo "LobeHub backup checksum not found: ${checksum}" >&2
    exit 1
  }
  archive_mode="$(stat -c '%a' "${archive}" 2>/dev/null || stat -f '%Lp' "${archive}")"
  checksum_mode="$(stat -c '%a' "${checksum}" 2>/dev/null || stat -f '%Lp' "${checksum}")"
  [[ "${archive_mode}" == 600 && "${checksum_mode}" == 600 ]] || {
    echo 'LobeHub backup archive and checksum must both use mode 0600' >&2
    exit 1
  }
  read -r expected_digest expected_name <"${checksum}"
  expected_name="${expected_name#\*}"
  [[ "${expected_digest}" =~ ^[0-9a-f]{64}$ \
    && "${expected_name}" == "$(basename "${archive}")" ]] || {
    echo 'Malformed LobeHub backup checksum file' >&2
    exit 1
  }
  actual_digest="$(sha256_file "${archive}")"
  [[ "${actual_digest}" == "${expected_digest}" ]] || {
    echo 'LobeHub backup checksum mismatch' >&2
    exit 1
  }
}

validate_archive_paths() {
  local archive="$1" path normalized paths verbose_line entry_type
  paths="$(tar -tzf "${archive}")"
  [[ -n "${paths}" ]] || { echo 'LobeHub backup archive is empty' >&2; exit 1; }
  # 必须在解压前拒绝链接和特殊文件，避免归档条目借助链接逃出 staging 目录。
  while IFS= read -r verbose_line; do
    entry_type="${verbose_line:0:1}"
    [[ "${entry_type}" == - || "${entry_type}" == d ]] || {
      echo 'LobeHub backup archive contains links or special files' >&2
      exit 1
    }
  done < <(tar -tvzf "${archive}")
  if [[ -n "$(printf '%s\n' "${paths}" | LC_ALL=C sort | uniq -d)" ]]; then
    echo 'LobeHub backup archive contains duplicate paths' >&2
    exit 1
  fi
  while IFS= read -r path; do
    normalized="${path#./}"
    normalized="${normalized%/}"
    [[ -n "${normalized}" && "${normalized}" != /* \
      && "${normalized}" != .. && "${normalized}" != ../* \
      && "${normalized}" != */../* && "${normalized}" != */.. ]] || {
      echo "Unsafe path in LobeHub backup archive: ${path}" >&2
      exit 1
    }
    case "${normalized}" in
      backup-manifest.env|config/lobehub.env|lobehub/paradedb|lobehub/paradedb/*|\
      lobehub/rustfs|lobehub/rustfs/*|lobehub/release|lobehub/release/*) ;;
      *)
        echo "Unknown path in LobeHub backup archive: ${path}" >&2
        exit 1
        ;;
    esac
  done <<<"${paths}"
}

verify_extracted_snapshot() {
  local staging="$1" manifest="${staging}/backup-manifest.env" mode
  if find "${staging}" -type l -o \( ! -type d ! -type f \) | grep -q .; then
    echo 'LobeHub backup archive contains links or special files' >&2
    exit 1
  fi
  [[ -f "${manifest}" \
    && -f "${staging}/config/lobehub.env" \
    && -d "${staging}/lobehub/paradedb" \
    && -d "${staging}/lobehub/rustfs" \
    && -f "${staging}/lobehub/release/release.env" ]] || {
    echo 'LobeHub backup archive is incomplete' >&2
    exit 1
  }
  grep -Fx "BACKUP_SCHEMA_VERSION=${BACKUP_SCHEMA_VERSION}" "${manifest}" >/dev/null || {
    echo 'Unsupported LobeHub backup schema' >&2
    exit 1
  }
  grep -Eq '^CREATED_AT_UTC=[0-9]{8}T[0-9]{6}Z$' "${manifest}" || {
    echo 'Malformed LobeHub backup timestamp' >&2
    exit 1
  }
  grep -Eq '^LOBEHUB_INTERNAL_VERSION=v[0-9]+\.[0-9]+\.[0-9]+-platform\.[0-9]+$' \
    "${manifest}" || {
    echo 'Malformed LobeHub backup version' >&2
    exit 1
  }
  grep -Eq '^LOBEHUB_FORK_COMMIT=[0-9a-f]{40}$' "${manifest}" || {
    echo 'Malformed LobeHub backup fork commit' >&2
    exit 1
  }
  mode="$(stat -c '%a' "${staging}/config/lobehub.env" 2>/dev/null || \
    stat -f '%Lp' "${staging}/config/lobehub.env")"
  [[ "${mode}" == 600 ]] || {
    echo 'Archived LobeHub env must have mode 0600' >&2
    exit 1
  }
}

extract_verified_snapshot() {
  local archive="$1" staging="$2"
  verify_checksum "${archive}"
  validate_archive_paths "${archive}"
  tar -xzf "${archive}" -C "${staging}"
  verify_extracted_snapshot "${staging}"
}

create_backup() {
  local output_dir="$1" timestamp archive part staging
  require_root
  require_command tar
  require_stack_stopped
  require_backup_sources
  output_dir="$(normalize_output_dir "${output_dir}")"
  case "${output_dir}/" in
    "${BASE_DIR}/"*)
      echo 'LobeHub backup output must be outside the live platform directory' >&2
      exit 1
      ;;
  esac
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  archive="${output_dir}/test-agent-lobehub-backup-${timestamp}.tar.gz"
  part="${archive}.part"
  [[ ! -e "${archive}" && ! -e "${part}" ]] || {
    echo "LobeHub backup already exists for timestamp ${timestamp}" >&2
    exit 1
  }
  staging="$(mktemp -d "${output_dir}/.lobehub-backup.XXXXXX")"
  trap 'rm -rf "${staging}" "${part}"' RETURN
  cat >"${staging}/backup-manifest.env" <<EOF
BACKUP_SCHEMA_VERSION=${BACKUP_SCHEMA_VERSION}
CREATED_AT_UTC=${timestamp}
LOBEHUB_INTERNAL_VERSION=$(release_value LOBEHUB_INTERNAL_VERSION)
LOBEHUB_FORK_COMMIT=$(release_value LOBEHUB_FORK_COMMIT)
EOF
  chmod 0600 "${staging}/backup-manifest.env"
  umask 077
  tar -C "${BASE_DIR}" -czf "${part}" \
    config/lobehub.env lobehub/paradedb lobehub/rustfs lobehub/release \
    -C "${staging}" backup-manifest.env
  chmod 0600 "${part}"
  mv "${part}" "${archive}"
  write_checksum "${archive}"
  rm -rf "${staging}"
  trap - RETURN
  echo "LobeHub cold backup created: ${archive}"
}

verify_backup() {
  local archive="$1" staging
  require_command tar
  archive="$(normalize_existing_file "${archive}")"
  staging="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-lobehub-backup-verify.XXXXXX")"
  trap 'rm -rf "${staging}"' RETURN
  extract_verified_snapshot "${archive}" "${staging}"
  rm -rf "${staging}"
  trap - RETURN
  echo "LobeHub cold backup verification passed: ${archive}"
}

restore_backup() {
  local archive="$1" confirmed="$2" timestamp staging rollback_root rel
  local -a replaced_targets=()
  local -a original_targets=()
  [[ "${confirmed}" == true ]] || {
    echo 'Restore requires --confirm-restore' >&2
    exit 1
  }
  require_root
  require_command tar
  require_stack_stopped
  [[ "${BASE_DIR}" == /* && "${BASE_DIR}" != / && ! -L "${BASE_DIR}" ]] || {
    echo "Unsafe LobeHub base directory: ${BASE_DIR}" >&2
    exit 1
  }
  archive="$(normalize_existing_file "${archive}")"
  install -d -m 0750 "${BASE_DIR}/lobehub" "${BASE_DIR}/config"
  staging="$(mktemp -d "${BASE_DIR}/lobehub/restore-staging.XXXXXX")"
  extract_verified_snapshot "${archive}" "${staging}"
  timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
  rollback_root="${BASE_DIR}/lobehub/restore-rollback/${timestamp}"
  [[ ! -e "${rollback_root}" ]] || {
    echo "LobeHub restore rollback directory already exists: ${rollback_root}" >&2
    exit 1
  }
  install -d -m 0700 "${rollback_root}/original/lobehub" \
    "${rollback_root}/original/config" "${rollback_root}/failed-new/lobehub" \
    "${rollback_root}/failed-new/config"

  rollback_failed_restore() {
    local target
    for target in "${replaced_targets[@]-}"; do
      if [[ -e "${BASE_DIR}/${target}" ]]; then
        mkdir -p "$(dirname "${rollback_root}/failed-new/${target}")"
        mv "${BASE_DIR}/${target}" "${rollback_root}/failed-new/${target}" || true
      fi
    done
    for target in "${original_targets[@]-}"; do
      if [[ -e "${rollback_root}/original/${target}" ]]; then
        mkdir -p "$(dirname "${BASE_DIR}/${target}")"
        mv "${rollback_root}/original/${target}" "${BASE_DIR}/${target}" || true
      fi
    done
    rm -rf "${staging}"
  }
  trap rollback_failed_restore ERR INT TERM

  for rel in lobehub/paradedb lobehub/rustfs lobehub/release config/lobehub.env; do
    if [[ -e "${BASE_DIR}/${rel}" ]]; then
      mkdir -p "$(dirname "${rollback_root}/original/${rel}")"
      mv "${BASE_DIR}/${rel}" "${rollback_root}/original/${rel}"
      original_targets+=("${rel}")
    fi
    mkdir -p "$(dirname "${BASE_DIR}/${rel}")"
    mv "${staging}/${rel}" "${BASE_DIR}/${rel}"
    replaced_targets+=("${rel}")
  done
  chmod 0600 "${BASE_DIR}/config/lobehub.env"
  cp -a "${staging}/backup-manifest.env" "${rollback_root}/restored-backup-manifest.env"
  rm -rf "${staging}"
  trap - ERR INT TERM
  echo "LobeHub cold backup restored. Previous files retained at ${rollback_root}"
}

action="${1:-}"
shift || true
output_dir=""
archive=""
confirm_restore=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) output_dir="${2:-}"; shift 2 ;;
    --archive) archive="${2:-}"; shift 2 ;;
    --confirm-restore) confirm_restore=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown LobeHub backup option: $1" >&2; usage; exit 2 ;;
  esac
done

case "${action}" in
  create)
    [[ -n "${output_dir}" && -z "${archive}" && "${confirm_restore}" == false ]] || {
      usage
      exit 2
    }
    create_backup "${output_dir}"
    ;;
  verify)
    [[ -n "${archive}" && -z "${output_dir}" && "${confirm_restore}" == false ]] || {
      usage
      exit 2
    }
    verify_backup "${archive}"
    ;;
  restore)
    [[ -n "${archive}" && -z "${output_dir}" ]] || { usage; exit 2; }
    restore_backup "${archive}" "${confirm_restore}"
    ;;
  --help|-h) usage ;;
  *) usage; exit 2 ;;
esac
