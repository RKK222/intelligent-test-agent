import {
  applyEdits,
  createScanner,
  findNodeAtLocation,
  modify,
  parse,
  parseTree,
  printParseErrorCode,
  type FormattingOptions,
  type ParseError
} from "jsonc-parser";

const OPENCODE_CONFIG_SCHEMA = "https://opencode.ai/config.json";
const OPENCODE_REFERENCE_ALIAS_MAX_LENGTH = 128;
const OPENCODE_REFERENCE_ALIAS_INVALID = /[\/\\\s`,]/u;

export type ReferenceConfigTarget = {
  alias: string;
  path: string;
  folder: string;
  /** 平台托管的附加身份字段；用于自动化版本/目录引用，未知字段仍由 JSONC 补丁保留。 */
  managedFields?: Record<string, string | boolean | number>;
};

export type ReferenceConfigValue = {
  path: string;
  merge: boolean;
  sddFolderName: string;
  description: string;
};

export type ReferenceConfigPatch = ReferenceConfigTarget & Omit<ReferenceConfigValue, "path">;

export type ManagedReferenceConfigPatch = ReferenceConfigPatch & {
  /** 兼容旧 workspace/version 身份的自动化引用，按当前应用与版本库保存时一并清理。 */
  supersededAutomationWorkspaceIds?: string[];
};

export type ReferenceConfigInspection = {
  mode: "create" | "update";
  value: ReferenceConfigValue;
  /** 磁盘中实际托管字段，用于识别只读规范字段是否需要纠正。 */
  baseline: ReferenceConfigValue;
  /** 当前引用是否缺少平台托管的外部目录自动允许规则。 */
  permissionNeedsUpdate: boolean;
  /** 受控自动化引用切换版本时用于清理旧的精确权限规则。 */
  previousPath?: string;
};

export class ReferenceConfigValidationError extends Error {
  constructor(readonly code: string, message: string) {
    super(message);
    this.name = "ReferenceConfigValidationError";
  }
}

/**
 * OpenCode 原生 reference loader 会忽略包含空白、斜杠、反引号或逗号的别名；平台在写盘前复用同一边界，
 * 避免文件树能展示但模型运行时无法发现的双重状态。
 */
export function isOpenCodeReferenceAlias(value: string) {
  const normalized = value.trim();
  return normalized.length > 0
    && normalized.length <= OPENCODE_REFERENCE_ALIAS_MAX_LENGTH
    && !OPENCODE_REFERENCE_ALIAS_INVALID.test(normalized);
}

/** 把展示名称转换成 OpenCode 可发现的建议别名；最终写盘仍会执行严格校验。 */
export function normalizeOpenCodeReferenceAlias(value: string) {
  return value
    .trim()
    .replace(/[\/\\\s`,]+/gu, "-")
    .replace(/-+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, OPENCODE_REFERENCE_ALIAS_MAX_LENGTH);
}

function requireOpenCodeReferenceAlias(value: string) {
  if (!isOpenCodeReferenceAlias(value)) {
    throw new ReferenceConfigValidationError(
      "INVALID_REFERENCE_ALIAS",
      "引用名称必须是 1-128 个字符，且不能包含空格、斜杠、反引号或逗号"
    );
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function parseRoot(content: string): Record<string, unknown> | null {
  if (content.trim() === "") return null;
  const errors: ParseError[] = [];
  const parsed = parse(content, errors, { allowTrailingComma: true, disallowComments: false }) as unknown;
  if (errors.length > 0) {
    const detail = errors
      .map((error) => `${printParseErrorCode(error.error)} at offset ${error.offset}`)
      .join(", ");
    throw new ReferenceConfigValidationError("INVALID_JSONC", `配置文件 JSONC 无法解析：${detail}`);
  }
  if (!isObject(parsed)) {
    throw new ReferenceConfigValidationError("INVALID_ROOT", "配置文件 JSONC 根节点必须是对象");
  }
  return parsed;
}

function externalDirectoryPattern(target: ReferenceConfigTarget): string {
  return `${target.path.replace(/\/+$/, "")}/*`;
}

type PermissionAction = "allow" | "ask" | "deny";

function isPermissionAction(value: unknown): value is PermissionAction {
  return value === "allow" || value === "ask" || value === "deny";
}

function normalizePermissionPattern(value: string): string {
  return value.replaceAll("\\", "/");
}

/** OpenCode 权限规则按声明顺序匹配，后匹配项覆盖前项；这里只复刻路径通配符语义用于漂移判断。 */
function permissionPatternMatches(value: string, pattern: string): boolean {
  const expression = normalizePermissionPattern(pattern)
    .replace(/[.+^${}()|[\]\\]/g, "\\$&")
    .replaceAll("*", ".*")
    .replaceAll("?", ".");
  return new RegExp(`^${expression}$`, "s").test(normalizePermissionPattern(value));
}

function permissionNeedsUpdate(root: Record<string, unknown> | null, target: ReferenceConfigTarget): boolean {
  if (!root || root.permission === undefined) return true;
  const permission = root.permission;
  if (typeof permission === "string") {
    if (isPermissionAction(permission)) return true;
    throw new ReferenceConfigValidationError(
      "INVALID_PERMISSION",
      "配置文件 permission 必须是 allow、ask、deny 字符串或对象"
    );
  }
  if (!isObject(permission)) {
    throw new ReferenceConfigValidationError(
      "INVALID_PERMISSION",
      "配置文件 permission 必须是 allow、ask、deny 字符串或对象"
    );
  }

  const externalDirectory = permission.external_directory;
  if (externalDirectory === undefined) return true;
  if (typeof externalDirectory === "string") {
    if (isPermissionAction(externalDirectory)) return true;
    throw new ReferenceConfigValidationError(
      "INVALID_EXTERNAL_DIRECTORY_PERMISSION",
      "配置文件 permission.external_directory 必须是 allow、ask、deny 字符串或规则对象"
    );
  }
  if (!isObject(externalDirectory)) {
    throw new ReferenceConfigValidationError(
      "INVALID_EXTERNAL_DIRECTORY_PERMISSION",
      "配置文件 permission.external_directory 必须是 allow、ask、deny 字符串或规则对象"
    );
  }
  for (const [pattern, action] of Object.entries(externalDirectory)) {
    if (!isPermissionAction(action)) {
      throw new ReferenceConfigValidationError(
        "INVALID_EXTERNAL_DIRECTORY_PERMISSION",
        `配置文件 permission.external_directory 规则 ${pattern} 的动作必须是 allow、ask 或 deny`
      );
    }
  }

  const exactPattern = externalDirectoryPattern(target);
  const entries = Object.entries(externalDirectory);
  const exactIndex = entries.findIndex(([pattern]) => pattern === exactPattern);
  if (exactIndex < 0 || externalDirectory[exactPattern] !== "allow") return true;
  return entries
    .slice(exactIndex + 1)
    .some(([pattern]) => permissionPatternMatches(exactPattern, pattern));
}

function createInspection(
  target: ReferenceConfigTarget,
  root: Record<string, unknown> | null = null
): ReferenceConfigInspection {
  const value = { path: target.path, merge: true, sddFolderName: target.folder, description: "" };
  return {
    mode: "create",
    value,
    baseline: { ...value },
    permissionNeedsUpdate: permissionNeedsUpdate(root, target),
    previousPath: undefined
  };
}

/**
 * 只接受缺失引用或 path 完全匹配的本地对象；字符串简写、Git 引用与冲突 path 均不猜测覆盖。
 */
export function inspectReferenceConfig(content: string, target: ReferenceConfigTarget): ReferenceConfigInspection {
  requireOpenCodeReferenceAlias(target.alias);
  const root = parseRoot(content);
  if (!root) {
    return createInspection(target);
  }
  const references = root.references;
  if (references === undefined) {
    return createInspection(target, root);
  }
  if (!isObject(references)) {
    throw new ReferenceConfigValidationError("INVALID_REFERENCES", "配置文件 references 必须是对象");
  }
  const existing = references[target.alias];
  if (existing === undefined) {
    return createInspection(target, root);
  }
  if (!isObject(existing)) {
    throw new ReferenceConfigValidationError(
      "ALIAS_TYPE_CONFLICT",
      `引用别名 ${target.alias} 已存在且不是本地引用对象，未执行覆盖`
    );
  }
  if (Object.prototype.hasOwnProperty.call(existing, "repository")) {
    throw new ReferenceConfigValidationError(
      "ALIAS_GIT_CONFLICT",
      `引用别名 ${target.alias} 已被 Git 引用占用，未执行覆盖`
    );
  }
  const existingManagedKind = existing["testagent-reference-kind"];
  const targetManagedKind = target.managedFields?.["testagent-reference-kind"];
  const controlledAutomationUpdate = existingManagedKind === "automation" && targetManagedKind === "automation";
  if (existing.path !== target.path && !controlledAutomationUpdate) {
    throw new ReferenceConfigValidationError(
      "PATH_CONFLICT",
      `引用别名 ${target.alias} 的 path 与当前目录不一致，未执行覆盖`
    );
  }
  const persistedSddFolderName = typeof existing["sdd-folder-name"] === "string"
    ? existing["sdd-folder-name"]
    : "";
  const merge = typeof existing.merge === "boolean" ? existing.merge : true;
  const description = typeof existing.description === "string" ? existing.description : "";
  return {
    mode: "update",
    value: {
      path: target.path,
      merge,
      sddFolderName: target.folder,
      description
    },
    baseline: {
      path: target.path,
      merge,
      sddFolderName: persistedSddFolderName,
      description
    },
    permissionNeedsUpdate: permissionNeedsUpdate(root, target),
    previousPath: typeof existing.path === "string" ? existing.path : undefined
  };
}

function previousManagedAutomationReferences(
  root: Record<string, unknown> | null,
  patch: ManagedReferenceConfigPatch
): Array<{ alias: string; path?: string }> {
  const kind = patch.managedFields?.["testagent-reference-kind"];
  const workspaceId = patch.managedFields?.["testagent-automation-workspace-id"];
  const appId = patch.managedFields?.["testagent-automation-app-id"];
  const repositoryId = patch.managedFields?.["testagent-automation-repository-id"];
  const workspaceIds = new Set([
    ...(patch.supersededAutomationWorkspaceIds ?? []),
    ...(typeof workspaceId === "string" ? [workspaceId] : [])
  ]);
  if (!root || kind !== "automation" || !isObject(root.references)) {
    return [];
  }
  return Object.entries(root.references)
    .filter(([alias, value]) => alias !== patch.alias
      && isObject(value)
      && value["testagent-reference-kind"] === "automation"
      && (
        (typeof repositoryId === "string"
          && value["testagent-automation-repository-id"] === repositoryId
          && (typeof appId !== "string"
            || value["testagent-automation-app-id"] === appId
            || value["testagent-automation-app-id"] === undefined))
        || (typeof value["testagent-automation-workspace-id"] === "string"
          && workspaceIds.has(value["testagent-automation-workspace-id"]))
      ))
    .map(([alias, value]) => ({
      alias,
      path: isObject(value) && typeof value.path === "string" ? value.path : undefined
    }));
}

function formattingOptions(content: string): FormattingOptions {
  return {
    insertSpaces: true,
    tabSize: 2,
    eol: content.includes("\r\n") ? "\r\n" : "\n"
  };
}

function applyModification(content: string, path: (string | number)[], value: unknown): string {
  return applyEdits(content, modify(content, path, value, { formattingOptions: formattingOptions(content) }));
}

/** 规则重排时只移除属性正文和分隔逗号，显式保留属性前后已有的 JSONC 注释。 */
function removePropertyKeepingComments(content: string, path: string[]): string {
  const tree = parseTree(content, [], { allowTrailingComma: true, disallowComments: false });
  const valueNode = tree ? findNodeAtLocation(tree, path) : undefined;
  const propertyNode = valueNode?.parent;
  if (!propertyNode || propertyNode.type !== "property") {
    throw new ReferenceConfigValidationError("PERMISSION_PATCH_FAILED", "无法调整外部目录权限规则顺序");
  }
  const propertyEnd = propertyNode.offset + propertyNode.length;
  const scanner = createScanner(content, true);
  scanner.setPosition(propertyEnd);
  scanner.scan();
  if (content[scanner.getTokenOffset()] !== ",") {
    throw new ReferenceConfigValidationError("PERMISSION_PATCH_FAILED", "无法调整外部目录权限规则顺序");
  }
  const commaOffset = scanner.getTokenOffset();
  const preservedTrivia = content.slice(propertyEnd, commaOffset);
  return content.slice(0, propertyNode.offset)
    + preservedTrivia
    + content.slice(commaOffset + scanner.getTokenLength());
}

function patchExternalDirectoryPermission(content: string, target: ReferenceConfigTarget): string {
  const root = parseRoot(content);
  if (!root) {
    throw new ReferenceConfigValidationError("INVALID_ROOT", "配置文件 JSONC 根节点必须是对象");
  }
  const exactPattern = externalDirectoryPattern(target);
  const permission = root.permission;
  if (permission === undefined) {
    return applyModification(content, ["permission"], {
      external_directory: { [exactPattern]: "allow" }
    });
  }
  if (isPermissionAction(permission)) {
    return applyModification(content, ["permission"], {
      "*": permission,
      external_directory: { [exactPattern]: "allow" }
    });
  }
  if (!isObject(permission)) {
    // inspectReferenceConfig 已负责给出稳定的中文校验错误；这里防止后续调用绕过校验。
    permissionNeedsUpdate(root, target);
    return content;
  }

  const externalDirectory = permission.external_directory;
  if (externalDirectory === undefined) {
    return applyModification(content, ["permission", "external_directory"], {
      [exactPattern]: "allow"
    });
  }
  if (isPermissionAction(externalDirectory)) {
    return applyModification(content, ["permission", "external_directory"], {
      "*": externalDirectory,
      [exactPattern]: "allow"
    });
  }
  if (!isObject(externalDirectory)) {
    permissionNeedsUpdate(root, target);
    return content;
  }

  // 先复用统一校验，避免在有非法规则值时产生任何可写出的补丁正文。
  permissionNeedsUpdate(root, target);
  const entries = Object.entries(externalDirectory);
  const exactIndex = entries.findIndex(([pattern]) => pattern === exactPattern);
  const hasLaterMatch = exactIndex >= 0 && entries
    .slice(exactIndex + 1)
    .some(([pattern]) => permissionPatternMatches(exactPattern, pattern));
  const path = ["permission", "external_directory", exactPattern];
  if (hasLaterMatch) {
    const withoutExact = removePropertyKeepingComments(content, path);
    return applyModification(withoutExact, path, "allow");
  }
  if (externalDirectory[exactPattern] !== "allow") {
    return applyModification(content, path, "allow");
  }
  return content;
}

function removePreviousExternalDirectoryPermission(
  content: string,
  alias: string,
  previousPath: string | undefined,
  nextPath: string
): string {
  if (!previousPath || previousPath === nextPath) return content;
  const root = parseRoot(content);
  if (!root) return content;
  const references = isObject(root.references) ? root.references : {};
  const stillUsed = Object.entries(references).some(([name, value]) =>
    name !== alias && isObject(value) && value.path === previousPath
  );
  if (stillUsed) return content;
  const permission = isObject(root.permission) ? root.permission : null;
  const externalDirectory = permission && isObject(permission.external_directory)
    ? permission.external_directory
    : null;
  const oldPattern = `${previousPath.replace(/\/+$/, "")}/*`;
  if (!externalDirectory || !Object.prototype.hasOwnProperty.call(externalDirectory, oldPattern)) return content;
  return applyModification(content, ["permission", "external_directory", oldPattern], undefined);
}

/**
 * 每次以调用方刚读取的最新正文为输入。新增别名时只增加该节点；更新时逐字段 modify，
 * 同一补丁再补齐精确外部目录 allow，从而保留 hidden、未来字段、注释以及其它区域的尾逗号。
 */
export function patchReferenceConfig(content: string, patch: ManagedReferenceConfigPatch): string {
  requireOpenCodeReferenceAlias(patch.alias);
  const root = parseRoot(content);
  const previousManagedReferences = previousManagedAutomationReferences(root, patch);
  const inspection = inspectReferenceConfig(content, patch);
  const managedValue = {
    path: patch.path,
    merge: patch.merge,
    "sdd-folder-name": patch.folder,
    description: patch.description.trim(),
    ...(patch.managedFields ?? {})
  };
  let output = content.trim() === ""
    ? `{\n  "$schema": "${OPENCODE_CONFIG_SCHEMA}"\n}\n`
    : content;
  if (inspection.mode === "create") {
    output = applyModification(output, ["references", patch.alias], managedValue);
  } else {
    for (const [field, value] of Object.entries(managedValue)) {
      output = applyModification(output, ["references", patch.alias, field], value);
    }
  }
  for (const previousReference of previousManagedReferences) {
    output = applyModification(output, ["references", previousReference.alias], undefined);
  }
  const previousPaths = [inspection.previousPath, ...previousManagedReferences.map((item) => item.path)];
  for (const previousPath of previousPaths) {
    output = removePreviousExternalDirectoryPermission(output, patch.alias, previousPath, patch.path);
  }
  return patchExternalDirectoryPermission(output, patch);
}

/**
 * 以应用当前的 `(appId, repositoryId)` 配置集合对账自动化引用。每个版本库只保留一个托管节点，
 * 同时移除旧 workspace/version 身份以及已经没有引用使用的精确外部目录权限。
 */
export function reconcileAutomationReferenceConfig(
  content: string,
  appId: string,
  patches: ManagedReferenceConfigPatch[]
): string {
  const normalizedAppId = appId.trim();
  if (!normalizedAppId) {
    throw new ReferenceConfigValidationError("INVALID_AUTOMATION_APP", "自动化引用缺少应用标识");
  }
  const repositoryIds = new Set<string>();
  const aliases = new Set<string>();
  let output = content;
  for (const patch of patches) {
    const repositoryId = patch.managedFields?.["testagent-automation-repository-id"];
    if (patch.managedFields?.["testagent-automation-app-id"] !== normalizedAppId
      || typeof repositoryId !== "string"
      || !repositoryId.trim()) {
      throw new ReferenceConfigValidationError(
        "INVALID_AUTOMATION_IDENTITY",
        "自动化引用必须携带应用和版本库标识"
      );
    }
    if (repositoryIds.has(repositoryId) || aliases.has(patch.alias)) {
      throw new ReferenceConfigValidationError(
        "DUPLICATE_AUTOMATION_REFERENCE",
        "同一应用的自动化版本库或引用别名不能重复"
      );
    }
    repositoryIds.add(repositoryId);
    aliases.add(patch.alias);
    output = patchReferenceConfig(output, patch);
    // 新模型只以 app/repository/generation 标识；同别名升级时同步清掉旧 workspace/version 身份。
    output = applyModification(
      output,
      ["references", patch.alias, "testagent-automation-workspace-id"],
      undefined
    );
    output = applyModification(
      output,
      ["references", patch.alias, "testagent-automation-version-id"],
      undefined
    );
  }

  const root = parseRoot(output);
  if (!root || !isObject(root.references)) return output;
  const stale = Object.entries(root.references)
    .filter(([alias, value]) => {
      if (!isObject(value) || value["testagent-reference-kind"] !== "automation") return false;
      const managedAppId = value["testagent-automation-app-id"];
      if (managedAppId !== undefined && managedAppId !== normalizedAppId) return false;
      const managedRepositoryId = value["testagent-automation-repository-id"];
      return !aliases.has(alias)
        || typeof managedRepositoryId !== "string"
        || !repositoryIds.has(managedRepositoryId);
    })
    .map(([alias, value]) => ({
      alias,
      path: isObject(value) && typeof value.path === "string" ? value.path : undefined
    }));
  for (const reference of stale) {
    output = applyModification(output, ["references", reference.alias], undefined);
    output = removePreviousExternalDirectoryPermission(output, reference.alias, reference.path, "");
  }
  return output;
}
