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
  mustBeVisible: true;
  forbiddenLocator: ".oc-unknown-part";
};

export type PartSpec = {
  kind: PartKind;
  requiredFields: readonly string[];
  ui: { current: PartUiContract; history: PartUiContract };
};

const common = ["id", "sessionID", "messageID", "type"] as const;

/**
 * OpenCode 1.17.7 的 12 类 Part 验收矩阵。locator 可以提供兼容候选，但最终命中的
 * 元素必须可见；`.oc-unknown-part` 即使存在于 DOM 也不能算通过。
 */
export const PART_SPECS: readonly PartSpec[] = [
  spec("text", ["text"], [".oc-text-part"], "最终文本可见", "copy", "button[aria-label='复制']"),
  spec("subtask", ["prompt", "description", "agent"], [".oc-subagent-card", "[data-part-type='subtask']"], "子任务 description 与 agent 可见", "subagent-navigation", ".oc-subagent-card.is-clickable"),
  spec("reasoning", ["text", "time.start"], [".oc-reasoning-part"], "思考状态可展开且正文可见", "expand", ".oc-reasoning-part .oc-disclosure__trigger"),
  spec("file", ["mime", "url"], [".oc-file-part .oc-file-path", ".oc-file-part"], "文件名或路径可见", "none"),
  spec("tool", ["callID", "tool", "state.status", "state.input"], ["[data-testid='oc-tool-group']", ".oc-tool"], "工具名称、状态与输出可展开", "expand", "[data-testid='oc-tool-group'] .oc-tool-group__trigger"),
  spec("step-start", [], [".oc-step-start-marker", "[data-part-type='step-start']"], "步骤边界以低噪可见标记呈现", "none"),
  spec("step-finish", ["reason", "cost", "tokens.input", "tokens.output", "tokens.reasoning", "tokens.cache.read", "tokens.cache.write"], [".oc-step-finish-marker", "[data-part-type='step-finish']"], "完成原因与 token 摘要可见", "none"),
  spec("snapshot", ["snapshot"], [".oc-snapshot-part", "[data-part-type='snapshot']"], "snapshot 标记可见", "none"),
  spec("patch", ["hash", "files"], [".oc-patch-part", "[data-part-type='patch']"], "hash 与文件摘要可见", "open-diff", ".oc-patch-part button"),
  spec("agent", ["name"], [".oc-agent-part", "[data-part-type='agent']"], "Agent 名称可见", "none"),
  spec("retry", ["attempt", "error.name", "error.data.message", "error.data.isRetryable", "time.created"], [".oc-retry-row"], "重试次数与错误可见", "none"),
  spec("compaction", ["auto"], [".oc-compaction-part", "[data-part-type='compaction']"], "上下文压缩标记可见", "none")
];

function spec(
  kind: PartKind,
  fields: readonly string[],
  locators: readonly string[],
  semantic: string,
  interaction: PartUiContract["interaction"],
  interactionLocator?: string
): PartSpec {
  const contract: PartUiContract = {
    locators,
    semantic,
    interaction,
    interactionLocator,
    mustBeVisible: true,
    forbiddenLocator: ".oc-unknown-part"
  };
  return { kind, requiredFields: [...common, ...fields], ui: { current: contract, history: { ...contract } } };
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
  const result: JsonRecord = {};
  for (const field of requiredFields) {
    const value = readPath(source, field);
    if (value === undefined || value === null) throw new Error(`${kind} part missing required field ${field}`);
    writePath(result, field, value);
  }
  return result;
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
    if (!segment || segment === "." || segment === ".." || segment.includes("/") || segment.includes("\\") || segment.includes("\0")) {
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
  return normalized === "authorization" || normalized === "cookie" || normalized === "token" || normalized.endsWith("token") || normalized === "key" || normalized.endsWith("apikey") || normalized === "secret" || normalized.endsWith("secret");
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
