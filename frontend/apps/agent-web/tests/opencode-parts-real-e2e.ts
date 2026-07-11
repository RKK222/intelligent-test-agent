import { posix as path } from "node:path";
import { isDeepStrictEqual } from "node:util";

export const PART_KINDS = [
  "text",
  "subtask",
  "reasoning",
  "file",
  "tool",
  "step-start",
  "step-finish",
  "snapshot",
  "patch",
  "agent",
  "retry",
  "compaction"
] as const;

export type PartKind = (typeof PART_KINDS)[number];
type JsonRecord = Record<string, unknown>;

export type PartUiContract = {
  locators: readonly string[];
  semantic: string;
  interaction: "copy" | "expand" | "open-diff" | "subagent-navigation" | "none";
  interactionLocator?: string;
  interactionRequirement: "always" | "child-mapping" | "diff-available" | "never";
  mustBeVisible: true;
  forbiddenLocator: ".oc-unknown-part";
};

export type PartSpec = {
  kind: PartKind;
  requiredFields: readonly string[];
  projectionFields: readonly string[];
  ui: { current: PartUiContract; history: PartUiContract };
};

const common = ["id", "sessionID", "messageID", "type"] as const;

/**
 * OpenCode 1.17.7 的 12 类 Part 验收矩阵。locator 可以提供兼容候选，但最终命中的
 * 元素必须可见；`.oc-unknown-part` 即使存在于 DOM 也不能算通过。
 */
export const PART_SPECS: readonly PartSpec[] = [
  spec("text", ["text"], ["text", "synthetic", "ignored", "time.start", "time.end", "metadata"], [partLocator("text")], "最终文本可见", "copy", "always", ":scope button[aria-label='复制']"),
  spec("subtask", ["prompt", "description", "agent"], ["prompt", "description", "agent", "model.providerID", "model.modelID", "command"], [partLocator("subtask")], "目标原生子任务的 description 与 agent 可见", "subagent-navigation", "child-mapping", ":scope [data-child-session-id]"),
  spec("reasoning", ["text", "time.start"], ["text", "metadata", "time.start", "time.end"], [partLocator("reasoning")], "思考状态可展开且正文可见", "expand", "always", ":scope .oc-disclosure__trigger"),
  spec("file", ["mime", "url"], ["mime", "filename", "url", "source.type", "source.text.value", "source.text.start", "source.text.end", "source.path", "source.range.start.line", "source.range.start.character", "source.range.end.line", "source.range.end.character", "source.name", "source.kind", "source.clientName", "source.uri"], [partLocator("file")], "文件名或路径可见", "none", "never"),
  spec("tool", ["callID", "tool", "state.status", "state.input"], ["callID", "tool", "state.status", "state.input", "state.raw", "state.title", "state.output", "state.error", "state.metadata", "state.time.start", "state.time.end", "state.time.compacted", "state.attachments", "metadata"], [partLocator("tool")], "工具名称、状态与输出可展开", "expand", "always", ":scope .oc-tool-group__trigger, :scope .oc-disclosure__trigger"),
  spec("step-start", [], ["snapshot"], [partLocator("step-start")], "步骤边界以低噪可见标记呈现", "none", "never"),
  spec("step-finish", ["reason", "cost", "tokens.input", "tokens.output", "tokens.reasoning", "tokens.cache.read", "tokens.cache.write"], ["reason", "snapshot", "cost", "tokens.total", "tokens.input", "tokens.output", "tokens.reasoning", "tokens.cache.read", "tokens.cache.write"], [partLocator("step-finish")], "完成原因与 token 摘要可见", "none", "never"),
  spec("snapshot", ["snapshot"], ["snapshot"], [partLocator("snapshot")], "snapshot 标记可见", "none", "never"),
  spec("patch", ["hash", "files"], ["hash", "files"], [partLocator("patch")], "hash 与文件摘要可见", "open-diff", "diff-available", ":scope button"),
  spec("agent", ["name"], ["name", "source.value", "source.start", "source.end"], [partLocator("agent")], "Agent 名称可见", "none", "never"),
  spec("retry", ["attempt", "error.name", "error.data.message", "error.data.isRetryable", "time.created"], ["attempt", "error.name", "error.data.message", "error.data.statusCode", "error.data.isRetryable", "error.data.responseHeaders", "error.data.responseBody", "error.data.metadata", "time.created"], [partLocator("retry")], "目标原生 retry 的次数与错误可见", "none", "never"),
  spec("compaction", ["auto"], ["auto", "overflow", "tail_start_id"], [partLocator("compaction")], "上下文压缩标记可见", "none", "never")
];

function partLocator(kind: PartKind): string {
  return `[data-part-id='{partId}'][data-part-type='${kind}']`;
}

function spec(
  kind: PartKind,
  requiredFields: readonly string[],
  projectionFields: readonly string[],
  locators: readonly string[],
  semantic: string,
  interaction: PartUiContract["interaction"],
  interactionRequirement: PartUiContract["interactionRequirement"],
  interactionLocator?: string
): PartSpec {
  const contract: PartUiContract = {
    locators,
    semantic,
    interaction,
    interactionLocator,
    interactionRequirement,
    mustBeVisible: true,
    forbiddenLocator: ".oc-unknown-part"
  };
  return {
    kind,
    requiredFields: [...common, ...requiredFields],
    projectionFields: [...common, ...projectionFields],
    ui: { current: contract, history: { ...contract } }
  };
}

/** 根据真实资源上下文判定条件交互；缺 child/diff 时明确记为 N/A，而不是伪造失败。 */
export function interactionExpectation(
  contract: PartUiContract,
  context: { targetPartId?: string; childMappingPartId?: string; diffAvailable?: boolean }
): "required" | "n/a" {
  if (contract.interactionRequirement === "always") return "required";
  if (contract.interactionRequirement === "child-mapping") {
    return context.targetPartId !== undefined && context.childMappingPartId === context.targetPartId ? "required" : "n/a";
  }
  if (contract.interactionRequirement === "diff-available") return context.diffAvailable ? "required" : "n/a";
  return "n/a";
}

/** 从 OpenCode `/session/{id}/message` 的 envelope 数组中按原生 part id 定位。 */
export function findRawPart(rawMessages: unknown, partId: string): JsonRecord | undefined {
  for (const message of arrayValue(rawMessages)) {
    for (const part of arrayValue(recordValue(message)?.parts)) {
      const candidate = recordValue(part);
      if (candidate && partIdentifier(candidate) === partId) return candidate;
    }
  }
  return undefined;
}

/** 从平台 messages 的 `data.items` 或已解包 `items` 中按 part id 定位。 */
export function findPlatformMessagePart(platformMessages: unknown, partId: string): JsonRecord | undefined {
  const root = unwrapData(platformMessages);
  for (const message of arrayValue(recordValue(root)?.items ?? root)) {
    for (const part of arrayValue(recordValue(message)?.parts)) {
      const candidate = recordValue(part);
      if (candidate && partIdentifier(candidate) === partId) return candidate;
    }
  }
  return undefined;
}

/**
 * 从 preflight 证实的 `messagesBySessionId` 结构定位 Part；同时匹配 messageID，避免不同
 * 消息复用异常 id 时误判。
 */
export function findTreeMessagePart(tree: unknown, remoteMessageId: string, partId: string): JsonRecord | undefined {
  const root = recordValue(unwrapData(tree));
  const groups = recordValue(root?.messagesBySessionId);
  if (!groups) return undefined;
  for (const entries of Object.values(groups)) {
    for (const entryValue of arrayValue(entries)) {
      const entry = recordValue(entryValue);
      const part = recordValue(entry?.part);
      const messageId = stringValue(entry?.messageID) ?? stringValue(entry?.messageId) ?? stringValue(part?.messageID) ?? stringValue(part?.messageId);
      if (part && messageId === remoteMessageId && partIdentifier(part) === partId) return part;
    }
  }
  return undefined;
}

/** 选择每类官方必需字段；任何字段缺失都会让契约在接触 UI 之前失败。 */
export function selectPartFields(kind: PartKind, part: unknown): JsonRecord {
  const source = recordValue(part);
  if (!source) throw new Error(`${kind} part must be an object`);
  const partType = source.type;
  if (partType !== kind) throw new Error(`${kind} part type mismatch: ${String(partType)}`);
  const requiredFields = getSpec(kind).requiredFields;
  validateVariant(kind, source);
  const result: JsonRecord = {};
  for (const field of requiredFields) {
    const value = readPath(source, field);
    if (value === undefined || value === null) throw new Error(`${kind} part missing required field ${field}`);
    writePath(result, field, value);
  }
  for (const field of getSpec(kind).projectionFields) {
    const value = readPath(source, field);
    if (value !== undefined) writePath(result, field, value);
  }
  return result;
}

function validateVariant(kind: PartKind, source: JsonRecord): void {
  if (kind === "text" && source.time !== undefined) requirePaths(kind, source, ["time.start"]);
  if (kind === "subtask" && source.model !== undefined) requirePaths(kind, source, ["model.providerID", "model.modelID"]);
  if (kind === "file" && source.source !== undefined) {
    requirePaths(kind, source, ["source.type", "source.text.value", "source.text.start", "source.text.end"]);
    const sourceType = readPath(source, "source.type");
    if (sourceType === "file") requirePaths(kind, source, ["source.path"]);
    else if (sourceType === "symbol") requirePaths(kind, source, ["source.path", "source.range.start.line", "source.range.start.character", "source.range.end.line", "source.range.end.character", "source.name", "source.kind"]);
    else if (sourceType === "resource") requirePaths(kind, source, ["source.clientName", "source.uri"]);
    else throw new Error(`${kind} part has unknown source.type ${String(sourceType)}`);
  }
  if (kind === "tool") {
    const status = readPath(source, "state.status");
    if (status === "pending") requirePaths(kind, source, ["state.raw"]);
    else if (status === "running") requirePaths(kind, source, ["state.time.start"]);
    else if (status === "completed") requirePaths(kind, source, ["state.output", "state.title", "state.metadata", "state.time.start", "state.time.end"]);
    else if (status === "error") requirePaths(kind, source, ["state.error", "state.time.start", "state.time.end"]);
    else throw new Error(`${kind} part has unknown state.status ${String(status)}`);
  }
  if (kind === "agent" && source.source !== undefined) requirePaths(kind, source, ["source.value", "source.start", "source.end"]);
}

function requirePaths(kind: PartKind, source: JsonRecord, fields: readonly string[]): void {
  for (const field of fields) {
    const value = readPath(source, field);
    if (value === undefined || value === null) throw new Error(`${kind} part missing required field ${field}`);
  }
}

/** 分别验证 raw→平台 messages 与 raw→平台 tree，禁止一个来源替另一个来源兜底。 */
export function assertPartProjection(kind: PartKind, rawPart: unknown, platformMessages: unknown, platformTree: unknown): void {
  const raw = recordValue(rawPart);
  if (!raw) throw new Error(`${kind} raw part must be an object`);
  const partId = partIdentifier(raw);
  const messageId = stringValue(raw.messageID) ?? stringValue(raw.messageId);
  if (!partId || !messageId) throw new Error(`${kind} raw part has no id/messageID`);
  const expected = selectPartFields(kind, raw);
  assertProjection("platform messages", expected, findPlatformMessagePart(platformMessages, partId), kind);
  assertProjection("platform tree", expected, findTreeMessagePart(platformTree, messageId, partId), kind);
}

function assertProjection(layer: string, expected: JsonRecord, actualPart: JsonRecord | undefined, kind: PartKind): void {
  if (!actualPart) throw new Error(`${kind} ${layer} part not found`);
  let actual: JsonRecord;
  try {
    actual = selectPartFields(kind, actualPart);
  } catch (error) {
    throw new Error(`${kind} ${layer} projection invalid: ${error instanceof Error ? error.message : String(error)}`);
  }
  if (!isDeepStrictEqual(actual, expected)) throw new Error(`${kind} ${layer} projection differs from raw`);
}

/** 递归删除证据中的常见凭据字段；不会改变原对象，便于失败后仍可继续清理。 */
export function sanitizeEvidence<T>(value: T): T {
  if (Array.isArray(value)) return value.map((item) => sanitizeEvidence(item)) as T;
  if (!value || typeof value !== "object") return value;
  const sanitized: JsonRecord = {};
  for (const [key, item] of Object.entries(value as JsonRecord)) {
    if (isSecretKey(key)) continue;
    sanitized[key] = sanitizeEvidence(item);
  }
  return sanitized as T;
}

/** 生成隔离证据路径；每一段只允许安全文件名，防止跨 run 或跨 kind 覆盖。 */
export function evidencePath(runId: string, kind: PartKind, name: string): string {
  if (!PART_KINDS.includes(kind)) throw new Error(`unknown Part kind: ${String(kind)}`);
  for (const [label, segment] of [["runId", runId], ["name", name]] as const) {
    if (!/^[A-Za-z0-9._-]+$/.test(segment) || segment === "." || segment === "..") {
      throw new Error(`unsafe evidence ${label}`);
    }
  }
  return path.join(".tmp/e2e/opencode-parts", runId, kind, name);
}

function getSpec(kind: PartKind): PartSpec {
  const found = PART_SPECS.find((item) => item.kind === kind);
  if (!found) throw new Error(`unknown Part kind: ${String(kind)}`);
  return found;
}

function isSecretKey(key: string): boolean {
  const normalized = key.toLowerCase().replace(/[-_]/g, "");
  return normalized === "authorization" || normalized === "proxyauthorization" || normalized === "cookie" || normalized === "setcookie" || normalized === "token" || normalized.endsWith("token") || normalized === "key" || normalized.endsWith("apikey") || normalized === "secret" || normalized.endsWith("secret");
}

function unwrapData(value: unknown): unknown {
  const record = recordValue(value);
  return record && "data" in record ? record.data : value;
}

function recordValue(value: unknown): JsonRecord | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value) ? value as JsonRecord : undefined;
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function stringValue(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function partIdentifier(part: JsonRecord): string | undefined {
  return stringValue(part.id) ?? stringValue(part.partID) ?? stringValue(part.partId);
}

function readPath(source: JsonRecord, field: string): unknown {
  return field.split(".").reduce<unknown>((value, key) => recordValue(value)?.[key], source);
}

function writePath(target: JsonRecord, field: string, value: unknown): void {
  const segments = field.split(".");
  let cursor = target;
  segments.forEach((segment, index) => {
    if (index === segments.length - 1) cursor[segment] = value;
    else cursor = cursor[segment] = recordValue(cursor[segment]) ?? {} as JsonRecord;
  });
}
