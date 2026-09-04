/** 测试工作库分支命名约定；日期同时作为应用版本号。 */
const STANDARD_WORKSPACE_BRANCH_PATTERN = /^feature_testagent_(\d{8})$/;

/**
 * 从测试工作库分支名提取版本号，并严格拒绝 2 月 30 日等不存在的日期。
 */
export function standardWorkspaceVersionFromBranch(branch: string): string | null {
  const match = STANDARD_WORKSPACE_BRANCH_PATTERN.exec(branch);
  if (!match) return null;

  const version = match[1];
  const year = Number.parseInt(version.slice(0, 4), 10);
  const month = Number.parseInt(version.slice(4, 6), 10);
  const day = Number.parseInt(version.slice(6, 8), 10);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year
    && date.getMonth() === month - 1
    && date.getDate() === day
    ? version
    : null;
}

export function isValidStandardWorkspaceBranch(branch: string): boolean {
  return standardWorkspaceVersionFromBranch(branch) !== null;
}
