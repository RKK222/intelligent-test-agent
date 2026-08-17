#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
ENSURE_SCRIPT="${ROOT_DIR}/deploy/internal/ensure-experience-workspace-content.sh"
TEMPLATE_DIR="${ROOT_DIR}/deploy/internal/experience-workspace-template"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/test-agent-experience-content.XXXXXX")"

cleanup() {
  rm -rf "${TMP_ROOT}"
}
trap cleanup EXIT

PARTIAL_TEMPLATE="${TMP_ROOT}/partial-template"
WORKSPACE_DIR="${TMP_ROOT}/experience"
mkdir -p "${PARTIAL_TEMPLATE}"
cp -R "${TEMPLATE_DIR}/." "${PARTIAL_TEMPLATE}/"
rm -f "${PARTIAL_TEMPLATE}/docs/部署架构/物理部署架构.md"

# 无 HEAD 仓库中已有的用户暂存文件不应被夹带进模板基线。
mkdir -p "${WORKSPACE_DIR}"
git -C "${WORKSPACE_DIR}" init -b main >/dev/null
printf '用户已经暂存的内容\n' >"${WORKSPACE_DIR}/用户暂存.txt"
git -C "${WORKSPACE_DIR}" add -- '用户暂存.txt'

# 新目录只提交模板自身，不创建 remote；中文 docs/spec 路径必须完整进入初始 HEAD。
bash "${ENSURE_SCRIPT}" \
  --workspace-dir "${WORKSPACE_DIR}" \
  --template-dir "${PARTIAL_TEMPLATE}" >/dev/null

[[ -f "${WORKSPACE_DIR}/README.md" ]]
[[ -f "${WORKSPACE_DIR}/docs/应用架构/测试概述.md" ]]
[[ -f "${WORKSPACE_DIR}/spec/I000001-用户登录体验/01-需求/S000001-账号密码登录/需求文档/需求说明.md" ]]
[[ -f "${WORKSPACE_DIR}/spec/I000001-用户登录体验/02-设计/S000001-账号密码登录/开发文档/详细设计.md" ]]
[[ -f "${WORKSPACE_DIR}/spec/I000001-用户登录体验/03-编码/S000001-账号密码登录/031-业务代码/实现说明.md" ]]
[[ -f "${WORKSPACE_DIR}/spec/I000001-用户登录体验/04-测试/S000001-账号密码登录/041-测试设计/测试案例.md" ]]
[[ -f "${WORKSPACE_DIR}/spec/I000001-用户登录体验/04-测试/S000001-账号密码登录/042-测试执行/测试执行记录.md" ]]
[[ ! -e "${WORKSPACE_DIR}/docs/部署架构/物理部署架构.md" ]]
[[ "$(git -C "${WORKSPACE_DIR}" rev-list --count HEAD)" == "1" ]]
[[ -z "$(git -C "${WORKSPACE_DIR}" remote)" ]]
initial_files="$(git -C "${WORKSPACE_DIR}" -c core.quotePath=false show --name-only --pretty=format: HEAD)"
grep -Fxq 'README.md' <<<"${initial_files}"
grep -Fxq 'docs/应用架构/测试概述.md' <<<"${initial_files}"
grep -Fxq 'spec/I000001-用户登录体验/04-测试/S000001-账号密码登录/041-测试设计/测试案例.md' \
  <<<"${initial_files}"
if grep -Fxq '用户暂存.txt' <<<"${initial_files}"; then
  echo "Initial template commit included an unrelated staged file" >&2
  exit 1
fi
initial_status="$(git -C "${WORKSPACE_DIR}" -c core.quotePath=false status --short --untracked-files=all)"
if [[ "${initial_status}" != 'A  用户暂存.txt' ]]; then
  echo "Initial template commit changed paths outside its baseline: ${initial_status}" >&2
  exit 1
fi

# 已有仓库升级时只补缺失文件：用户正文不覆盖、用户 index 不夹带、已有 HEAD 不推进。
printf '# 用户保留的体验说明\n' >"${WORKSPACE_DIR}/README.md"

second_output="$(bash "${ENSURE_SCRIPT}" \
  --workspace-dir "${WORKSPACE_DIR}" \
  --template-dir "${TEMPLATE_DIR}")"
grep -Fq 'added=1' <<<"${second_output}"
grep -Fxq '# 用户保留的体验说明' "${WORKSPACE_DIR}/README.md"
[[ -f "${WORKSPACE_DIR}/docs/部署架构/物理部署架构.md" ]]
[[ "$(git -C "${WORKSPACE_DIR}" rev-list --count HEAD)" == "1" ]]
status="$(git -C "${WORKSPACE_DIR}" -c core.quotePath=false status --short --untracked-files=all)"
grep -Fq 'A  用户暂存.txt' <<<"${status}"
grep -Fq '?? "docs/部署架构/物理部署架构.md"' <<<"${status}" || \
  grep -Fq '?? docs/部署架构/物理部署架构.md' <<<"${status}"

third_output="$(bash "${ENSURE_SCRIPT}" \
  --workspace-dir "${WORKSPACE_DIR}" \
  --template-dir "${TEMPLATE_DIR}")"
grep -Fq 'added=0' <<<"${third_output}"
[[ "$(git -C "${WORKSPACE_DIR}" rev-list --count HEAD)" == "1" ]]

echo "Experience workspace template deployment and non-overwrite behavior verified"
