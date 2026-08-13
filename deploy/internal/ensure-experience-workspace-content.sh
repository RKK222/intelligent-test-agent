#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
WORKSPACE_DIR=""
TEMPLATE_DIR="${SCRIPT_DIR}/experience-workspace-template"
INITIAL_COMMIT_NAME="MIMO Test Agent"
INITIAL_COMMIT_EMAIL="mimo-test-agent@mails.icbc"

usage() {
  cat <<'USAGE'
Usage: ensure-experience-workspace-content.sh --workspace-dir <path> [--template-dir <path>]

Copy packaged docs/spec examples into an experience workspace without replacing
existing files. A repository without HEAD receives one local baseline commit;
an existing repository only receives missing files as uncommitted local changes.

Options:
  --workspace-dir <path>  Absolute experience workspace directory.
  --template-dir <path>   Template root. Default: packaged experience-workspace-template.
  -h, --help              Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --workspace-dir)
      [[ $# -ge 2 ]] || {
        echo "--workspace-dir requires a path" >&2
        exit 2
      }
      WORKSPACE_DIR="$2"
      shift 2
      ;;
    --template-dir)
      [[ $# -ge 2 ]] || {
        echo "--template-dir requires a path" >&2
        exit 2
      }
      TEMPLATE_DIR="$2"
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

if [[ -z "${WORKSPACE_DIR}" || "${WORKSPACE_DIR}" != /* ]]; then
  echo "--workspace-dir must be an absolute path" >&2
  exit 2
fi
if [[ "${TEMPLATE_DIR}" != /* ]]; then
  TEMPLATE_DIR="${SCRIPT_DIR}/${TEMPLATE_DIR}"
fi
if [[ ! -d "${TEMPLATE_DIR}" || -L "${TEMPLATE_DIR}" ]]; then
  echo "Experience workspace template directory is unavailable: ${TEMPLATE_DIR}" >&2
  exit 1
fi
if [[ -L "${WORKSPACE_DIR}" || ( -e "${WORKSPACE_DIR}" && ! -d "${WORKSPACE_DIR}" ) ]]; then
  echo "Experience workspace path must be a real directory: ${WORKSPACE_DIR}" >&2
  exit 1
fi

mkdir -p "${WORKSPACE_DIR}"
WORKSPACE_DIR="$(cd "${WORKSPACE_DIR}" && pwd -P)"
TEMPLATE_DIR="$(cd "${TEMPLATE_DIR}" && pwd -P)"

declare -a template_paths=()
declare -a added_paths=()

# 逐段建立模板父目录并拒绝符号链接，避免缺失文件补齐时越出体验目录。
ensure_parent_directory() {
  local relative_path="$1"
  local directory="${relative_path%/*}"
  local current="${WORKSPACE_DIR}"
  local segment
  local -a segments=()

  [[ "${directory}" != "${relative_path}" ]] || return 0
  IFS='/' read -r -a segments <<<"${directory}"
  for segment in "${segments[@]}"; do
    [[ -n "${segment}" && "${segment}" != "." && "${segment}" != ".." ]] || {
      echo "Invalid experience template directory segment: ${relative_path}" >&2
      exit 1
    }
    current="${current}/${segment}"
    if [[ -L "${current}" || ( -e "${current}" && ! -d "${current}" ) ]]; then
      echo "Experience template target parent is not a real directory: ${current}" >&2
      exit 1
    fi
    mkdir -p "${current}"
  done
}

while IFS= read -r -d '' source_file; do
  relative_path="${source_file#${TEMPLATE_DIR}/}"
  if [[ -z "${relative_path}" || "${relative_path}" == "${source_file}" || "${relative_path}" == /* ]]; then
    echo "Experience template file is outside the template root: ${source_file}" >&2
    exit 1
  fi
  template_paths+=("${relative_path}")
  ensure_parent_directory "${relative_path}"
  target_file="${WORKSPACE_DIR}/${relative_path}"
  if [[ -L "${target_file}" || ( -e "${target_file}" && ! -f "${target_file}" ) ]]; then
    echo "Experience template target is not a regular file: ${target_file}" >&2
    exit 1
  fi
  if [[ -e "${target_file}" ]]; then
    continue
  fi

  # 先在目标目录生成完整临时文件，再用硬链接仅在目标仍缺失时原子发布。
  target_parent="${target_file%/*}"
  temporary_file="$(mktemp "${target_parent}/.experience-template.XXXXXX")"
  command cp "${source_file}" "${temporary_file}"
  chmod 0644 "${temporary_file}"
  if ln "${temporary_file}" "${target_file}" 2>/dev/null; then
    added_paths+=("${relative_path}")
  elif [[ ! -f "${target_file}" || -L "${target_file}" ]]; then
    rm -f -- "${temporary_file}"
    echo "Experience template target changed while being created: ${target_file}" >&2
    exit 1
  fi
  rm -f -- "${temporary_file}"
done < <(find "${TEMPLATE_DIR}" -type f -print0)

if [[ "${#template_paths[@]}" -eq 0 ]]; then
  echo "Experience workspace template directory is empty: ${TEMPLATE_DIR}" >&2
  exit 1
fi

git_metadata="${WORKSPACE_DIR}/.git"
if [[ -L "${git_metadata}" || ( -e "${git_metadata}" && ! -d "${git_metadata}" ) ]]; then
  echo "Experience workspace Git metadata path is invalid: ${git_metadata}" >&2
  exit 1
fi
if [[ ! -d "${git_metadata}" ]]; then
  git -C "${WORKSPACE_DIR}" init -b main >/dev/null
fi

git_root="$(git -C "${WORKSPACE_DIR}" rev-parse --show-toplevel)"
git_root="$(cd "${git_root}" && pwd -P)"
if [[ "${git_root}" != "${WORKSPACE_DIR}" ]]; then
  echo "Experience workspace must be the Git worktree root: ${WORKSPACE_DIR}" >&2
  exit 1
fi

created_head=false
if ! git -C "${WORKSPACE_DIR}" rev-parse --verify HEAD >/dev/null 2>&1; then
  # 先只暂存模板路径，再用 --only 创建基线，不夹带现场已暂存的其它文件。
  git -C "${WORKSPACE_DIR}" add -- "${template_paths[@]}"
  if ! git -C "${WORKSPACE_DIR}" \
    -c "user.name=${INITIAL_COMMIT_NAME}" \
    -c "user.email=${INITIAL_COMMIT_EMAIL}" \
    commit --only --no-verify -m "初始化体验工作区示例" -- "${template_paths[@]}" >/dev/null; then
    exit 1
  fi
  created_head=true
elif [[ "${#added_paths[@]}" -gt 0 ]]; then
  printf 'Existing experience Git history preserved; missing template files remain as local changes.\n'
fi

printf 'Experience workspace content ensured: path=%s templates=%s added=%s created_head=%s\n' \
  "${WORKSPACE_DIR}" "${#template_paths[@]}" "${#added_paths[@]}" "${created_head}"
