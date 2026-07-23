import type {
  AgentMessage,
  MessagePart,
  MessageScope,
  ModelInfo,
  ModelRef,
  ProviderInfo,
  TokenUsage
} from "@test-agent/shared-types";

export type SessionContextCategory = "system" | "user" | "assistant" | "tool" | "other";

export type SessionContextUsageLevel = "unknown" | "normal" | "warning" | "danger";

export type SessionContextBreakdownItem = {
  key: SessionContextCategory;
  label: string;
  tokens: number;
  percentage: number;
  color: string;
};

export type SessionContextSummary = {
  providerId?: string;
  providerName: string;
  modelId?: string;
  modelName: string;
  messageCount: number;
  eligibleRootMessageCount: number;
  contextLimit?: number;
  usagePercent?: number;
  ringPercent: number;
  totalTokens: number;
  inputTokens: number;
  outputTokens: number;
  reasoningTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  usageMessageId?: string;
};

export type SessionContextMetricsInput = {
  messages: AgentMessage[];
  messageScopesById?: Record<string, MessageScope>;
  selectedProvider?: string;
  selectedModel?: string;
  models?: ModelInfo[];
  providers?: ProviderInfo[];
};

const CATEGORY_META: Record<SessionContextCategory, { label: string; color: string }> = {
  system: { label: "系统", color: "#3b82f6" },
  user: { label: "用户", color: "#16a36a" },
  assistant: { label: "助手", color: "#8b5cf6" },
  tool: { label: "工具", color: "#d97706" },
  other: { label: "其他", color: "#94a3b8" }
};

/** 按展示用百分比分级；未知上限不推断使用风险，超过 100% 保持危险级。 */
export function calculateSessionContextUsageLevel(usagePercent: number | undefined): SessionContextUsageLevel {
  if (usagePercent === undefined) return "unknown";
  if (usagePercent < 60) return "normal";
  if (usagePercent < 80) return "warning";
  return "danger";
}

function safeToken(value: number | undefined): number {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : 0;
}

export function totalTokenUsage(tokens: TokenUsage | undefined): number {
  return safeToken(tokens?.input)
    + safeToken(tokens?.output)
    + safeToken(tokens?.reasoning)
    + safeToken(tokens?.cacheRead)
    + safeToken(tokens?.cacheWrite);
}

function messageIdentity(message: AgentMessage): string | undefined {
  return message.role === "card" ? undefined : message.messageId ?? message.id;
}

export function isRootContextMessage(
  message: AgentMessage,
  messageScopesById: Record<string, MessageScope> | undefined
): boolean {
  if (message.role === "card") return false;
  const id = messageIdentity(message);
  const scope = id ? messageScopesById?.[id] : undefined;
  if (!scope) return true;
  if (scope.isChildSession === true) return false;
  // 未显式标记时才由 OpenCode scope 自身推断 child；平台 Session ID 不参与此判断。
  if (scope.isChildSession === undefined && scope.sessionId && scope.rootSessionId && scope.sessionId !== scope.rootSessionId) return false;
  return true;
}

/** 仅识别非空正文或 text part；reasoning、工具等状态内容不能单独形成可见 assistant 轮次。 */
function hasVisibleAssistantText(message: Extract<AgentMessage, { role: "assistant" }>): boolean {
  if (message.text.trim()) return true;
  return message.parts?.some((part) => part.type === "text" && part.text.trim().length > 0) ?? false;
}

/** user 每条独立计数；在下一个 user 前连续的 assistant 只合并为一轮，且必须先出现可见正文。 */
function countVisibleContextMessages(messages: AgentMessage[]): number {
  let count = 0;
  let currentAssistantTurnHasText = false;
  for (const message of messages) {
    if (message.role === "user") {
      count += 1;
      currentAssistantTurnHasText = false;
      continue;
    }
    if (message.role === "assistant" && !currentAssistantTurnHasText && hasVisibleAssistantText(message)) {
      count += 1;
      currentAssistantTurnHasText = true;
    }
  }
  return count;
}

function selectedModelRef(selectedProvider: string | undefined, selectedModel: string | undefined): ModelRef | undefined {
  if (!selectedModel) return undefined;
  if (selectedProvider && selectedModel.startsWith(`${selectedProvider}/`)) {
    return { id: selectedModel.slice(selectedProvider.length + 1), providerId: selectedProvider };
  }
  if (!selectedProvider && selectedModel.includes("/")) {
    const [providerId, ...modelParts] = selectedModel.split("/");
    return { id: modelParts.join("/"), providerId };
  }
  return { id: selectedModel, providerId: selectedProvider };
}

function findCatalogModel(models: ModelInfo[], reference: ModelRef | undefined): ModelInfo | undefined {
  if (!reference) return undefined;
  return models.find((model) => model.id === reference.id && (!reference.providerId || model.providerId === reference.providerId))
    ?? models.find((model) => `${model.providerId}/${model.id}` === reference.id)
    ?? models.find((model) => model.id === reference.id);
}

function mergedCatalogModels(models: ModelInfo[], providers: ProviderInfo[]): ModelInfo[] {
  const byIdentity = new Map<string, ModelInfo>();
  for (const model of models) {
    byIdentity.set(`${model.providerId ?? ""}/${model.id}`, model);
  }
  // 部分 OpenCode 版本只在 Provider envelope 中返回模型；与模型选择器采用同样的补齐/覆盖顺序。
  for (const provider of providers) {
    for (const model of provider.models ?? []) {
      const normalized = { ...model, providerId: model.providerId ?? provider.providerId };
      byIdentity.set(`${normalized.providerId ?? ""}/${normalized.id}`, normalized);
    }
  }
  return Array.from(byIdentity.values());
}

export function buildSessionContextSummary(input: SessionContextMetricsInput): SessionContextSummary {
  const rootMessages = input.messages.filter((message) => isRootContextMessage(message, input.messageScopesById));
  const usageMessage = [...rootMessages].reverse().find(
    (message): message is Extract<AgentMessage, { role: "assistant" }> => message.role === "assistant" && totalTokenUsage(message.tokens) > 0
  );
  const reference = selectedModelRef(input.selectedProvider, input.selectedModel) ?? usageMessage?.model;
  const catalogModel = findCatalogModel(mergedCatalogModels(input.models ?? [], input.providers ?? []), reference);
  const providerId = reference?.providerId ?? catalogModel?.providerId;
  const provider = (input.providers ?? []).find((item) => item.providerId === providerId);
  const totalTokens = totalTokenUsage(usageMessage?.tokens);
  const contextLimit = catalogModel?.contextLimit && catalogModel.contextLimit > 0 ? catalogModel.contextLimit : undefined;
  const usagePercent = contextLimit ? Math.round((totalTokens / contextLimit) * 100) : undefined;

  return {
    providerId,
    providerName: provider?.name ?? providerId ?? "—",
    modelId: reference?.id ?? catalogModel?.id,
    modelName: catalogModel?.name ?? reference?.id ?? "—",
    messageCount: countVisibleContextMessages(rootMessages),
    eligibleRootMessageCount: rootMessages.length,
    contextLimit,
    usagePercent,
    ringPercent: usagePercent === undefined ? 0 : Math.min(100, Math.max(0, usagePercent)),
    totalTokens,
    inputTokens: safeToken(usageMessage?.tokens?.input),
    outputTokens: safeToken(usageMessage?.tokens?.output),
    reasoningTokens: safeToken(usageMessage?.tokens?.reasoning),
    cacheReadTokens: safeToken(usageMessage?.tokens?.cacheRead),
    cacheWriteTokens: safeToken(usageMessage?.tokens?.cacheWrite),
    usageMessageId: usageMessage ? messageIdentity(usageMessage) : undefined
  };
}

function serializedLength(value: unknown): number {
  if (typeof value === "string") return value.length;
  if (value == null) return 0;
  try {
    return JSON.stringify(value).length;
  } catch {
    return String(value).length;
  }
}

function promptPartLength(message: Extract<AgentMessage, { role: "user" }>): number {
  if (!message.parts?.length) return message.text.length;
  return message.parts.reduce((sum, part) => {
    if (part.type === "text") return sum + part.text.length;
    if (part.type === "file") return sum + (part.content?.length ?? part.source?.text?.length ?? 0);
    return sum;
  }, 0) || message.text.length;
}

function assistantPartLength(part: MessagePart): number {
  return part.type === "text" || part.type === "reasoning" ? part.text.length : 0;
}

function estimatedTokens(characters: number): number {
  return characters > 0 ? Math.ceil(characters / 4) : 0;
}

/**
 * 正文只能近似还原输入上下文，因此先按字符估算可见四类，再校准到最近 assistant 的 input；
 * 无法反推的系统提示、协议和压缩开销统一归入“其他”。
 */
export function estimateSessionContextBreakdown(
  messages: AgentMessage[],
  inputTokens: number,
  options: Pick<SessionContextMetricsInput, "messageScopesById"> = {}
): SessionContextBreakdownItem[] {
  const target = Math.max(0, Math.round(inputTokens));
  if (target === 0) return [];

  let userCharacters = 0;
  let assistantCharacters = 0;
  let toolCharacters = 0;
  for (const message of messages) {
    if (!isRootContextMessage(message, options.messageScopesById)) continue;
    if (message.role === "user") {
      userCharacters += promptPartLength(message);
      continue;
    }
    if (message.role !== "assistant") continue;
    const parts = message.parts ?? [];
    const visibleCharacters = parts.reduce((sum, part) => sum + assistantPartLength(part), 0);
    assistantCharacters += visibleCharacters || message.text.length;
    for (const part of parts) {
      if (part.type !== "tool") continue;
      toolCharacters += Object.keys(part.input ?? {}).length * 16 + serializedLength(part.output);
    }
  }

  const raw = {
    system: 0,
    user: estimatedTokens(userCharacters),
    assistant: estimatedTokens(assistantCharacters),
    tool: estimatedTokens(toolCharacters)
  };
  const reconstructable = raw.system + raw.user + raw.assistant + raw.tool;
  const scale = reconstructable > target ? target / reconstructable : 1;
  const calibrated = {
    system: Math.floor(raw.system * scale),
    user: Math.floor(raw.user * scale),
    assistant: Math.floor(raw.assistant * scale),
    tool: Math.floor(raw.tool * scale)
  };
  const knownTotal = calibrated.system + calibrated.user + calibrated.assistant + calibrated.tool;
  const values: Record<SessionContextCategory, number> = {
    ...calibrated,
    other: Math.max(0, target - knownTotal)
  };

  return (Object.keys(CATEGORY_META) as SessionContextCategory[]).map((key) => ({
    key,
    label: CATEGORY_META[key].label,
    color: CATEGORY_META[key].color,
    tokens: values[key],
    percentage: target > 0 ? (values[key] / target) * 100 : 0
  }));
}
