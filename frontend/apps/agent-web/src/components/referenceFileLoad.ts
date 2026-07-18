export type ReferenceFileTabInfo = {
  workspaceId: string;
  referenceAlias: string;
  referencePath: string;
  logicalPath: string;
};

const REFERENCE_FILE_PREFIX = "workspace-reference:";

export function isReferenceFilePath(path: string): boolean {
  return path.startsWith(REFERENCE_FILE_PREFIX);
}

/**
 * 引用 tab 身份同时固化工作区、别名、引用内路径和逻辑路径，避免同名文件或同路径不同别名互相覆盖。
 */
export function referenceTabPath(info: ReferenceFileTabInfo): string {
  return `${REFERENCE_FILE_PREFIX}${[
    info.workspaceId,
    info.referenceAlias,
    info.referencePath,
    info.logicalPath
  ].map(encodeURIComponent).join(":")}`;
}

export function referenceFileInfo(tabPath: string): ReferenceFileTabInfo {
  if (!isReferenceFilePath(tabPath)) {
    throw new Error("不是引用文件 tab 身份");
  }
  const [workspaceId = "", referenceAlias = "", referencePath = "", logicalPath = ""] = tabPath
    .slice(REFERENCE_FILE_PREFIX.length)
    .split(":")
    .map(decodeURIComponent);
  return { workspaceId, referenceAlias, referencePath, logicalPath };
}

/** 引用读取失败只在确有成功快照时恢复 loaded；空白 error tab 的重试失败必须继续暴露 Retry。 */
export function referenceReadFailurePatch(hasLoadedSnapshot: boolean, message?: string) {
  return {
    readonly: true as const,
    loadState: hasLoadedSnapshot ? "loaded" as const : "error" as const,
    loadError: message,
    hasLoadedSnapshot
  };
}
