#!/usr/bin/env bash

# 企业交付物必须来自一个明确提交。默认拒绝已跟踪、暂存和未跟踪改动，
# 避免开发中的本地文件被 Maven、前端或 Docker 构建上下文意外带入制品。
require_clean_packaging_source() {
  local root_dir="$1"
  local source_status source_commit

  if [[ "${TEST_AGENT_ALLOW_DIRTY_SOURCE:-0}" == "1" ]]; then
    echo "WARNING: dirty source guard bypassed for packaging diagnostics" >&2
    return 0
  fi

  if ! command -v git >/dev/null 2>&1 \
    || ! git -C "${root_dir}" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "Enterprise packaging requires a Git worktree at: ${root_dir}" >&2
    return 1
  fi

  source_status="$(git -C "${root_dir}" status --porcelain=v1 --untracked-files=all)"
  if [[ -n "${source_status}" ]]; then
    echo "Enterprise packaging refused: source worktree contains local changes." >&2
    echo "Commit the intended changes, then package from a clean clone or detached worktree." >&2
    return 1
  fi

  source_commit="$(git -C "${root_dir}" rev-parse HEAD)"
  printf 'Packaging source commit: %s\n' "${source_commit}"
}
