import type { WorkspaceViewLocator } from "@test-agent/shared-types";

export type ReferenceFileTabInfo = {
  workspaceId: string;
  referenceAlias: string;
  referencePath: string;
  logicalPath: string;
  kind?: "REFERENCE" | "AUTOMATION_REFERENCE";
  automationAppId?: string;
  automationRepositoryId?: string;
  automationGeneration?: number;
  automationReadLease?: string;
};

const REFERENCE_FILE_PREFIX = "workspace-reference:";

export function isReferenceFilePath(path: string): boolean {
  return path.startsWith(REFERENCE_FILE_PREFIX);
}

/**
 * 引用 tab 身份同时固化工作区、别名、引用内路径和逻辑路径，避免同名文件或同路径不同别名互相覆盖。
 */
export function referenceTabPath(info: ReferenceFileTabInfo): string {
  const fields = [
    info.workspaceId,
    info.referenceAlias,
    info.referencePath,
    info.logicalPath
  ];
  if (info.kind === "AUTOMATION_REFERENCE") {
    fields.push(
      info.kind,
      info.automationAppId ?? "",
      info.automationRepositoryId ?? "",
      String(info.automationGeneration ?? ""),
      info.automationReadLease ?? ""
    );
  }
  return `${REFERENCE_FILE_PREFIX}${fields.map(encodeURIComponent).join(":")}`;
}

export function referenceFileInfo(tabPath: string): ReferenceFileTabInfo {
  if (!isReferenceFilePath(tabPath)) {
    throw new Error("不是引用文件 tab 身份");
  }
  const [workspaceId = "", referenceAlias = "", referencePath = "", logicalPath = "", kind,
    automationAppId, automationRepositoryId, automationGeneration, automationReadLease] = tabPath
    .slice(REFERENCE_FILE_PREFIX.length)
    .split(":")
    .map(decodeURIComponent);
  return kind === "AUTOMATION_REFERENCE"
    ? {
      workspaceId,
      referenceAlias,
      referencePath,
      logicalPath,
      kind,
      automationAppId,
      automationRepositoryId,
      automationGeneration: automationGeneration ? Number(automationGeneration) : undefined,
      ...(automationReadLease ? { automationReadLease } : {})
    }
    : { workspaceId, referenceAlias, referencePath, logicalPath };
}

/** 从 tab 固化信息恢复逻辑定位器，自动化标签始终继续绑定打开时的版本 ID。 */
export function referenceLocatorFromTab(info: ReferenceFileTabInfo): WorkspaceViewLocator {
  if (info.kind === "AUTOMATION_REFERENCE") {
    return {
      kind: "AUTOMATION_REFERENCE",
      path: info.referencePath,
      automationAppId: info.automationAppId,
      automationRepositoryId: info.automationRepositoryId,
      automationGeneration: info.automationGeneration,
      ...(info.automationReadLease ? { automationReadLease: info.automationReadLease } : {})
    };
  }
  return { kind: "REFERENCE", path: info.referencePath, referenceAlias: info.referenceAlias };
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
