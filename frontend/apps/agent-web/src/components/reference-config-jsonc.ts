import {
  applyEdits,
  modify,
  parse,
  printParseErrorCode,
  type FormattingOptions,
  type ParseError
} from "jsonc-parser";

const OPENCODE_CONFIG_SCHEMA = "https://opencode.ai/config.json";

export type ReferenceConfigTarget = {
  alias: string;
  path: string;
  folder: string;
};

export type ReferenceConfigValue = {
  path: string;
  merge: boolean;
  sddFolderName: string;
  description: string;
};

export type ReferenceConfigPatch = ReferenceConfigTarget & Omit<ReferenceConfigValue, "path">;

export type ReferenceConfigInspection = {
  mode: "create" | "update";
  value: ReferenceConfigValue;
  /** 磁盘中实际托管字段，用于识别只读规范字段是否需要纠正。 */
  baseline: ReferenceConfigValue;
};

export class ReferenceConfigValidationError extends Error {
  constructor(readonly code: string, message: string) {
    super(message);
    this.name = "ReferenceConfigValidationError";
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

function createInspection(target: ReferenceConfigTarget): ReferenceConfigInspection {
  const value = { path: target.path, merge: true, sddFolderName: target.folder, description: "" };
  return { mode: "create", value, baseline: { ...value } };
}

/**
 * 只接受缺失引用或 path 完全匹配的本地对象；字符串简写、Git 引用与冲突 path 均不猜测覆盖。
 */
export function inspectReferenceConfig(content: string, target: ReferenceConfigTarget): ReferenceConfigInspection {
  const root = parseRoot(content);
  if (!root) {
    return createInspection(target);
  }
  const references = root.references;
  if (references === undefined) {
    return createInspection(target);
  }
  if (!isObject(references)) {
    throw new ReferenceConfigValidationError("INVALID_REFERENCES", "配置文件 references 必须是对象");
  }
  const existing = references[target.alias];
  if (existing === undefined) {
    return createInspection(target);
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
  if (existing.path !== target.path) {
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
    }
  };
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

/**
 * 每次以调用方刚读取的最新正文为输入。新增别名时只增加该节点；更新时逐字段 modify，
 * 从而保留同一对象内的 hidden、未来字段、注释以及文件其它区域的尾逗号。
 */
export function patchReferenceConfig(content: string, patch: ReferenceConfigPatch): string {
  const inspection = inspectReferenceConfig(content, patch);
  const managedValue = {
    path: patch.path,
    merge: patch.merge,
    "sdd-folder-name": patch.folder,
    description: patch.description.trim()
  };
  let output = content.trim() === ""
    ? `{\n  "$schema": "${OPENCODE_CONFIG_SCHEMA}"\n}\n`
    : content;
  if (inspection.mode === "create") {
    return applyModification(output, ["references", patch.alias], managedValue);
  }
  for (const [field, value] of Object.entries(managedValue)) {
    output = applyModification(output, ["references", patch.alias, field], value);
  }
  return output;
}
