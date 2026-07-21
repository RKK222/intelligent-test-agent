#!/usr/bin/env bash

# 外层一键部署脚本共用的只读辅助函数；本文件只被 source，不单独执行。

detect_site_ip() {
  local role="$1"
  local candidate
  local -a candidates=()

  if command -v hostname >/dev/null 2>&1; then
    while IFS= read -r candidate; do
      [[ -n "${candidate}" ]] && candidates+=("${candidate}")
    done < <(hostname -I 2>/dev/null | tr ' ' '\n' | sed '/^$/d' || true)
  fi
  if command -v ip >/dev/null 2>&1; then
    while IFS= read -r candidate; do
      [[ -n "${candidate}" ]] && candidates+=("${candidate}")
    done < <(ip -o -4 addr show scope global 2>/dev/null | awk '{sub(/\/.*/, "", $4); print $4}' || true)
  fi

  if [[ "${role}" == "frontend" ]]; then
    for candidate in "${candidates[@]}"; do
      [[ "${candidate}" == "122.233.30.2" ]] && {
        printf '%s\n' "${candidate}"
        return 0
      }
    done
    echo "Cannot find frontend IP 122.233.30.2 on this server" >&2
    return 1
  fi

  local selected=""
  for candidate in "${candidates[@]}"; do
    [[ "${candidate}" =~ ^122\.233\.30\.([1-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-4])$ ]] || continue
    [[ "${candidate}" != "122.233.30.2" ]] || continue
    if [[ -n "${selected}" && "${selected}" != "${candidate}" ]]; then
      echo "Multiple backend-site IPs found: ${selected}, ${candidate}" >&2
      return 1
    fi
    selected="${candidate}"
  done
  [[ -n "${selected}" ]] || {
    echo "Cannot find one backend IP in 122.233.30.0/24 on this server" >&2
    return 1
  }
  printf '%s\n' "${selected}"
}

sha256_digest() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    echo "Neither sha256sum nor shasum is available" >&2
    return 1
  fi
}

verify_checksum_pair() {
  local file="$1"
  local checksum="${file}.sha256"
  local expected actual named
  [[ -f "${file}" ]] || { echo "Required file not found: ${file}" >&2; return 1; }
  [[ -f "${checksum}" ]] || { echo "Required file not found: ${checksum}" >&2; return 1; }
  expected="$(awk 'NF >= 2 {print $1; exit}' "${checksum}")"
  named="$(awk 'NF >= 2 {print $2; exit}' "${checksum}")"
  named="${named#\*}"
  [[ "${named}" == "$(basename "${file}")" ]] || {
    echo "Checksum file does not name $(basename "${file}"): ${checksum}" >&2
    return 1
  }
  actual="$(sha256_digest "${file}")"
  [[ -n "${expected}" && "${expected}" == "${actual}" ]] || {
    echo "SHA256 mismatch: ${file}" >&2
    return 1
  }
  printf 'SHA256 OK: %s\n' "$(basename "${file}")"
}

server_id_from_host() {
  printf 'test-agent-backend-%s' "${1//./-}"
}

replace_env_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  local tmp
  tmp="$(mktemp "${file}.new.XXXXXX")"
  awk -v wanted="${key}" -v replacement="${value}" '
    BEGIN { count = 0 }
    index($0, wanted "=") == 1 { print wanted "=" replacement; count++; next }
    { print }
    END { if (count != 1) exit 42 }
  ' "${file}" >"${tmp}" || {
    rm -f "${tmp}"
    echo "${file} must contain exactly one ${key}" >&2
    return 1
  }
  chmod --reference="${file}" "${tmp}" 2>/dev/null || chmod 0600 "${tmp}"
  mv -f "${tmp}" "${file}"
}

append_env_csv_value() {
  local file="$1"
  local key="$2"
  local item="$3"
  local current
  current="$(awk -F= -v wanted="${key}" '$1 == wanted {value=substr($0, index($0, "=") + 1)} END {print value}' "${file}")"
  [[ -n "${current}" ]] || { echo "Missing ${key} in ${file}" >&2; return 1; }
  case ",${current}," in
    *",${item},"*) return 0 ;;
  esac
  replace_env_value "${file}" "${key}" "${current},${item}"
}
