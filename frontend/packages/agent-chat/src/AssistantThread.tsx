"use client";

import * as React from "react";
import {
  AssistantRuntimeProvider,
  ComposerPrimitive,
  ThreadPrimitive,
  useComposerRuntime
} from "@assistant-ui/react";
import { Button, Textarea } from "@test-agent/ui-kit";
import { Check, ChevronDown, ImageIcon, Paperclip, Search, Sparkles, TerminalSquare, User, X } from "lucide-react";
import type {
  AgentInfo,
  AgentMessage,
  CommandInfo,
  MessagePart,
  ModelInfo,
  ProviderInfo,
  RuntimeResourceInfo
} from "@test-agent/shared-types";
import { AgentCard } from "./cards";
import { useAgentExternalRuntime, type AgentCardMeta } from "./assistant-thread";
import { fileToPromptAttachment, type ComposerAttachment } from "./prompt-parts";

export type AssistantThreadProps = {
  messages: AgentMessage[];
  running?: boolean;
  onSend: (prompt: string, attachments?: ComposerAttachment[]) => void;
  onCancel: () => void;
  onRetry: () => void;
  onOpenDiff: () => void;
  commands: CommandInfo[];
  resources: RuntimeResourceInfo[];
  agents?: AgentInfo[];
  models?: ModelInfo[];
  providers?: ProviderInfo[];
  selectedAgent?: string;
  selectedProvider?: string;
  selectedModel?: string;
  mode?: string;
  onAgentChange?: (agentId: string) => void;
  onProviderChange?: (providerId: string) => void;
  onModelChange?: (modelId: string) => void;
  onModeChange?: (mode: string) => void;
  onRequestNotifications?: () => void;
};

/**
 * 基于 assistant-ui headless 原语的对话线程 UI。
 * - 消息流：ThreadPrimitive.Viewport + Messages，每条消息按 role 渲染气泡或 AgentCard；
 * - 输入器：ComposerPrimitive，Enter 发送 / Shift+Enter 换行由原语内置；
 * - 斜杠命令、@上下文建议、附件仍复用既有逻辑。
 */
export function AssistantThread({
  messages,
  running,
  onSend,
  onCancel,
  onRetry,
  onOpenDiff,
  commands,
  resources,
  agents = [],
  models = [],
  providers = [],
  selectedAgent,
  selectedProvider,
  selectedModel,
  mode = "build",
  onAgentChange,
  onProviderChange,
  onModelChange,
  onModeChange,
  onRequestNotifications
}: AssistantThreadProps) {
  // 待发送附件的 ref：onNew 读取此 ref 随 prompt 一起转发给 onSend
  const attachmentsRef = React.useRef<ComposerAttachment[]>([]);
  const runtime = useAgentExternalRuntime({ messages, running, onSend, onCancel, attachmentsRef });
  // id → 原始 AgentMessage，用于按原消息渲染结构化 part（tool/reasoning/file 等）
  const messageById = React.useMemo(() => {
    const map = new Map<string, AgentMessage>();
    for (const message of messages) {
      map.set(message.id, message);
    }
    return map;
  }, [messages]);
  // 时间线只自动展开用户最需要立即查看的结构化结果，避免历史项把线程撑乱。
  const defaultOpenCardIds = React.useMemo(() => {
    const reversed = [...messages].reverse();
    return {
      latestToolId: reversed.find((message) => message.role === "card" && message.cardType === "tool")?.id,
      latestDiffId: reversed.find((message) => message.role === "card" && message.cardType === "diff")?.id
    };
  }, [messages]);

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <ThreadPrimitive.Root className="flex h-full min-h-0 flex-col">
        <ThreadPrimitive.Viewport className="min-h-0 flex-1 space-y-3 overflow-y-auto overflow-x-hidden p-3 [scrollbar-gutter:stable]">
          <ThreadPrimitive.Empty>
            <div className="flex h-full flex-col items-center justify-center gap-1 py-10 text-center text-[var(--ta-muted)]">
              <div className="text-[14px] font-semibold text-[#cfe0ff]">开始与测试智能体对话</div>
              <div className="text-[12px]">描述测试任务，例如：跑 checkout 模块并分析失败原因</div>
            </div>
          </ThreadPrimitive.Empty>
          <ThreadPrimitive.Messages>
            {({ message }) => (
              <ThreadMessageItem
                key={message.id}
                message={message}
                original={message.id ? messageById.get(message.id) : undefined}
                onOpenDiff={onOpenDiff}
                defaultOpenCardIds={defaultOpenCardIds}
              />
            )}
          </ThreadPrimitive.Messages>
        </ThreadPrimitive.Viewport>
        <ComposerArea
          running={running}
          onRetry={onRetry}
          commands={commands}
          resources={resources}
          attachmentsRef={attachmentsRef}
        />
        <RuntimeControls
          agents={agents}
          models={models}
          commands={commands}
          providers={providers}
          selectedAgent={selectedAgent}
          selectedProvider={selectedProvider}
          selectedModel={selectedModel}
          mode={mode}
          onAgentChange={onAgentChange}
          onProviderChange={onProviderChange}
          onModelChange={onModelChange}
          onModeChange={onModeChange}
          onRequestNotifications={onRequestNotifications}
        />
      </ThreadPrimitive.Root>
    </AssistantRuntimeProvider>
  );
}

/** ThreadPrimitive.Messages render-prop 收到的 message（MessageState）。 */
type MessageStateItem = Parameters<NonNullable<React.ComponentProps<typeof ThreadPrimitive.Messages>["children"]>>[0]["message"];

function ThreadMessageItem({
  message,
  original,
  onOpenDiff,
  defaultOpenCardIds
}: {
  message: MessageStateItem;
  original: AgentMessage | undefined;
  onOpenDiff: () => void;
  defaultOpenCardIds: {
    latestToolId?: string;
    latestDiffId?: string;
  };
}) {
  const cardMeta = message.metadata?.custom as AgentCardMeta | undefined;
  if (cardMeta?.card && original?.role === "card") {
    return (
      <AgentCard
        message={original}
        onOpenDiff={onOpenDiff}
        defaultOpen={shouldOpenCardByDefault(original, defaultOpenCardIds)}
      />
    );
  }
  const isUser = message.role === "user";
  const displayText =
    original?.role === "user" || original?.role === "assistant"
      ? original.text
      : typeof message.content === "string"
        ? message.content
        : "";
  return (
    <div className={isUser ? "flex justify-end" : "flex justify-start"}>
      <div
        className={
          isUser
            ? "max-w-[92%] rounded-[16px] border border-[#31487d] bg-[#1b2d5a] px-4 py-3 shadow-[inset_0_1px_0_rgba(255,255,255,.04)]"
            : "max-w-[92%] rounded-[10px] border border-[var(--ta-border)] bg-[#101b33] px-3 py-2"
        }
      >
        <div className={isUser ? "mb-2 text-[12px] font-semibold text-[#a8b9dc]" : "mb-1 text-[11px] text-slate-500"}>
          {isUser ? "用户" : "Agent"}
        </div>
        {original?.role === "assistant" && original.parts?.length ? (
          <MessageParts parts={original.parts} fallbackText={original.text} />
        ) : (
          <p className="m-0 whitespace-pre-wrap text-[13px] leading-7 text-slate-100">{displayText}</p>
        )}
      </div>
    </div>
  );
}

function shouldOpenCardByDefault(
  message: Extract<AgentMessage, { role: "card" }>,
  defaultOpenCardIds: {
    latestToolId?: string;
    latestDiffId?: string;
  }
) {
  const status = typeof message.payload.status === "string" ? message.payload.status.toLowerCase() : "";
  if (["running", "active", "pending", "queued"].includes(status)) {
    return true;
  }
  return message.id === defaultOpenCardIds.latestToolId || message.id === defaultOpenCardIds.latestDiffId;
}

/** 按 part 类型区分思考过程、最终回答和工具输出，避免流式内容混在同一块里。 */
function MessageParts({
  parts,
  fallbackText
}: {
  parts: NonNullable<Extract<AgentMessage, { role: "assistant" }>["parts"]>;
  fallbackText: string;
}) {
  if (!parts.length) {
    return <div className="whitespace-pre-wrap text-[12px] leading-6 text-slate-100">{fallbackText}</div>;
  }
  const hasAnswer = parts.some((part) => part.type === "text" && part.text.trim().length > 0);
  return (
    <div className="space-y-2">
      {parts.map((part) => (
        <div key={part.partId}>
          {part.type === "text" ? (
            <AnswerPart part={part} />
          ) : part.type === "reasoning" ? (
            <ReasoningPart part={part} openByDefault={part.status === "running" || !hasAnswer} />
          ) : part.type === "tool" ? (
            <div className="space-y-1 rounded border border-[#1d2b4d] bg-[#0a1324] p-2 text-[12px] text-slate-300">
              <div className="font-mono text-slate-200">{part.toolName}</div>
              <div className="text-slate-500">{part.status}</div>
              {part.output ? <pre className="max-h-32 overflow-auto whitespace-pre-wrap text-[11px]">{String(part.output)}</pre> : null}
            </div>
          ) : part.type === "file" ? (
            <div className="rounded border border-[#1d2b4d] bg-[#0a1324] p-2 font-mono text-[12px] text-slate-300">
              {part.path ?? part.name ?? part.partId}
            </div>
          ) : (
            <pre className="max-h-32 overflow-auto rounded border border-[#1d2b4d] bg-[#0a1324] p-2 whitespace-pre-wrap text-[11px] text-slate-400">
              {JSON.stringify((part as { payload?: unknown }).payload, null, 2)}
            </pre>
          )}
        </div>
      ))}
    </div>
  );
}

function AnswerPart({ part }: { part: Extract<MessagePart, { type: "text" }> }) {
  return (
    <div className="rounded-[10px] border border-[var(--ta-border)] bg-[#0f1a33] p-3">
      <div className="mb-2 text-[12px] font-semibold text-[#a3c9ff]">回答</div>
      <div className="whitespace-pre-wrap text-[12px] leading-6 text-slate-100">{part.text}</div>
    </div>
  );
}

function ReasoningPart({
  part,
  openByDefault
}: {
  part: Extract<MessagePart, { type: "reasoning" }>;
  openByDefault: boolean;
}) {
  const running = part.status === "running";
  return (
    <details open={openByDefault} className="rounded-[10px] border border-[var(--ta-border)] bg-[#0b1426] p-3">
      <summary className="flex cursor-pointer list-none items-center gap-2 text-[11px] font-semibold text-slate-400">
        <span className={`h-1.5 w-1.5 rounded-full ${running ? "bg-[var(--ta-accent)]" : "bg-slate-600"}`} />
        <span>{running ? "思考中" : part.title ?? "思考过程"}</span>
        {part.durationMs ? <span className="font-normal text-slate-600">{Math.round(part.durationMs / 1000)}s</span> : null}
      </summary>
      <div className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap pr-1 text-[12px] leading-6 text-slate-400">
        {part.text}
      </div>
    </details>
  );
}

/** 输入区：ComposerPrimitive + 附件 + 斜杠/上下文建议 + 操作按钮。 */
function ComposerArea({
  running,
  onRetry,
  commands,
  resources,
  attachmentsRef
}: {
  running?: boolean;
  onRetry: () => void;
  commands: CommandInfo[];
  resources: RuntimeResourceInfo[];
  attachmentsRef: React.RefObject<ComposerAttachment[]>;
}) {
  const composer = useComposerRuntime();
  const [text, setText] = React.useState("");
  const [attachments, setAttachments] = React.useState<ComposerAttachment[]>([]);
  const [attachmentError, setAttachmentError] = React.useState<string | null>(null);
  const [readingAttachments, setReadingAttachments] = React.useState(false);
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const imageInputRef = React.useRef<HTMLInputElement>(null);

  // 同步 composer 文本到本地镜像，供斜杠/上下文建议查询
  React.useEffect(() => {
    setText(composer.getState().text);
    return composer.subscribe(() => setText(composer.getState().text));
  }, [composer]);

  // 保持 attachmentsRef 与本地 attachments 同步，供 onNew 读取
  React.useEffect(() => {
    attachmentsRef.current = attachments;
  }, [attachments, attachmentsRef]);

  // 发送完成后（composer 文本被 reset 清空）清除本地附件
  React.useEffect(() => {
    return composer.subscribe(() => {
      if (composer.getState().text === "" && attachments.length > 0) {
        setAttachments([]);
        setAttachmentError(null);
      }
    });
  }, [composer, attachments.length]);

  const slashQuery = React.useMemo(() => commandQuery(text), [text]);
  const atQuery = React.useMemo(() => contextQuery(text), [text]);
  const contextItems = React.useMemo(
    () =>
      resources.slice(0, 12).map((item) => ({
        id: item.id,
        label: item.name,
        detail: item.uri ?? item.type ?? item.id
      })),
    [resources]
  );

  async function addAttachments(files: FileList | null) {
    if (!files?.length) {
      return;
    }
    setReadingAttachments(true);
    setAttachmentError(null);
    try {
      const next = await Promise.all(Array.from(files).map((file) => fileToPromptAttachment(file)));
      setAttachments((current) => mergeAttachments(current, next));
    } catch (error) {
      setAttachmentError(error instanceof Error ? error.message : "附件读取失败");
    } finally {
      setReadingAttachments(false);
    }
  }

  return (
    <ComposerPrimitive.Root asChild>
      <form
        className="border-t border-[var(--ta-border)] bg-[#0d1628] p-3"
        onSubmit={(event) => {
          // 阻止浏览器默认提交，交由 ComposerPrimitive.Send 触发 composer.send()
          event.preventDefault();
        }}
      >
        <input
          ref={fileInputRef}
          className="hidden"
          type="file"
          multiple
          onChange={(event) => {
            void addAttachments(event.target.files);
            event.currentTarget.value = "";
          }}
        />
        <input
          ref={imageInputRef}
          className="hidden"
          type="file"
          accept="image/*"
          multiple
          onChange={(event) => {
            void addAttachments(event.target.files);
            event.currentTarget.value = "";
          }}
        />
        <ComposerPrimitive.Input asChild>
          <Textarea rows={3} placeholder="描述测试任务，例如：跑 checkout 模块并分析失败原因" />
        </ComposerPrimitive.Input>
        {slashQuery != null && commands.length ? (
          <SuggestionPanel
            title="Commands"
            items={commands
              .filter((command) => command.name.toLowerCase().includes(slashQuery.toLowerCase()))
              .slice(0, 6)
              .map((command) => ({
                id: command.commandId,
                label: `/${command.name}`,
                detail: command.description ?? command.arguments ?? "",
                onPick: () => composer.setText(replaceCommandQuery(composer.getState().text, command.name))
              }))}
          />
        ) : null}
        {atQuery != null && contextItems.length ? (
          <SuggestionPanel
            title="Context"
            items={contextItems
              .filter((item) => item.label.toLowerCase().includes(atQuery.toLowerCase()))
              .slice(0, 6)
              .map((item) => ({
                id: item.id,
                label: `@${item.label}`,
                detail: item.detail,
                onPick: () => composer.setText(replaceContextQuery(composer.getState().text, item.label))
              }))}
          />
        ) : null}
        {attachments.length || attachmentError || readingAttachments ? (
          <div className="mt-2 flex min-h-7 flex-wrap items-center gap-2">
            {attachments.map((attachment) => (
              <span
                key={attachment.id}
                className="inline-flex max-w-full items-center gap-1 rounded border border-[var(--ta-border)] bg-[#0f1a33] px-2 py-1 text-[11px] text-slate-200"
                title={`${attachment.name} ${formatBytes(attachment.size)}`}
              >
                <span className="max-w-[160px] truncate">{attachment.name}</span>
                <span className="text-slate-500">{formatBytes(attachment.size)}</span>
                <button
                  type="button"
                  title="移除附件"
                  className="rounded p-0.5 text-slate-500 hover:bg-[#122044] hover:text-slate-100"
                  onClick={() => setAttachments((current) => current.filter((item) => item.id !== attachment.id))}
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
            {readingAttachments ? <span className="text-[11px] text-slate-500">读取中</span> : null}
            {attachmentError ? <span className="text-[11px] text-[#fca5a5]">{attachmentError}</span> : null}
          </div>
        ) : null}
        <div className="mt-2 flex items-center justify-between gap-2">
          <div className="text-[11px] text-slate-500">{running ? "Run 正在执行，发送将排队" : "Enter 发送"}</div>
          <div className="flex gap-2">
            <Button type="button" size="icon" variant="secondary" title="添加文件" onClick={() => fileInputRef.current?.click()}>
              <Paperclip className="h-4 w-4" />
            </Button>
            <Button type="button" size="icon" variant="secondary" title="添加图片" onClick={() => imageInputRef.current?.click()}>
              <ImageIcon className="h-4 w-4" />
            </Button>
            <ComposerPrimitive.Cancel asChild>
              <Button type="button" size="sm" variant="secondary" disabled={!running}>
                取消
              </Button>
            </ComposerPrimitive.Cancel>
            <Button type="button" size="sm" variant="secondary" onClick={onRetry}>
              重试
            </Button>
            <ComposerPrimitive.Send asChild>
              <Button type="submit" size="sm" variant="primary" disabled={readingAttachments}>
                {running ? "排队" : "发送"}
              </Button>
            </ComposerPrimitive.Send>
          </div>
        </div>
      </form>
    </ComposerPrimitive.Root>
  );
}

function SuggestionPanel({
  title,
  items
}: {
  title: string;
  items: Array<{ id: string; label: string; detail?: string; onPick: () => void }>;
}) {
  if (!items.length) {
    return null;
  }
  return (
    <div className="mt-2 max-h-44 overflow-auto rounded-md border border-[var(--ta-border)] bg-[#0a1324] p-1">
      <div className="px-2 py-1 text-[11px] uppercase text-slate-500">{title}</div>
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left hover:bg-[#13203f]"
          onClick={item.onPick}
        >
          <span className="min-w-0 flex-1 truncate font-mono text-[12px] text-slate-200">{item.label}</span>
          {item.detail ? <span className="max-w-[45%] truncate text-[11px] text-slate-500">{item.detail}</span> : null}
        </button>
      ))}
    </div>
  );
}

function mergeAttachments(current: ComposerAttachment[], next: ComposerAttachment[]) {
  const seen = new Set(current.map((item) => item.id));
  return [...current, ...next.filter((item) => !seen.has(item.id))];
}

function formatBytes(size: number) {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${Math.round(size / 1024)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function commandQuery(text: string) {
  const match = /(?:^|\n)\/([^\s/]*)$/.exec(text);
  return match?.[1] ?? null;
}

function contextQuery(text: string) {
  const match = /@([^\s@]*)$/.exec(text);
  return match?.[1] ?? null;
}

function replaceCommandQuery(text: string, command: string) {
  return text.replace(/(^|\n)\/[^\s/]*$/, `$1/${command} `);
}

function replaceContextQuery(text: string, label: string) {
  return text.replace(/@[^\s@]*$/, `@${label} `);
}

function RuntimeControls({
  agents,
  models,
  providers,
  commands,
  selectedAgent,
  selectedProvider,
  selectedModel,
  mode,
  onAgentChange,
  onProviderChange,
  onModelChange,
  onModeChange,
  onRequestNotifications
}: {
  agents: AgentInfo[];
  models: ModelInfo[];
  providers: ProviderInfo[];
  commands: CommandInfo[];
  selectedAgent?: string;
  selectedProvider?: string;
  selectedModel?: string;
  mode: string;
  onAgentChange?: (agentId: string) => void;
  onProviderChange?: (providerId: string) => void;
  onModelChange?: (modelId: string) => void;
  onModeChange?: (mode: string) => void;
  onRequestNotifications?: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 border-t border-slate-800 bg-slate-950 p-2">
      <ChicPopover
        label="Agent"
        placeholder="Agent"
        icon={<User className="h-3.5 w-3.5" />}
        value={selectedAgent ?? ""}
        options={agents.map((a) => ({ value: a.agentId, label: a.name }))}
        onChange={(v) => onAgentChange?.(v)}
        searchable
      />
      <ChicPopover
        label="Provider"
        placeholder="Provider"
        icon={<TerminalSquare className="h-3.5 w-3.5" />}
        value={selectedProvider ?? ""}
        options={providers.map((p) => ({ value: p.providerId, label: p.name }))}
        onChange={(v) => onProviderChange?.(v)}
        searchable
      />
      <ChicPopover
        label="Model"
        placeholder="Model"
        icon={<Sparkles className="h-3.5 w-3.5" />}
        value={selectedModel ?? ""}
        options={models.map((m) => ({ value: modelOptionValue(m), label: m.name }))}
        onChange={(v) => onModelChange?.(v)}
        searchable
      />
      <ChicPopover
        label="Mode"
        placeholder="Build"
        icon={<TerminalSquare className="h-3.5 w-3.5" />}
        value={mode}
        options={[
          { value: "build", label: "Build" },
          { value: "plan", label: "Plan" },
          { value: "shell", label: "Shell" },
          ...commands.slice(0, 8).map((c) => ({ value: `command:${c.name}`, label: `/${c.name}` }))
        ]}
        onChange={(v) => onModeChange?.(v)}
      />
      <Button type="button" size="sm" variant="secondary" onClick={onRequestNotifications}>
        通知
      </Button>
    </div>
  );
}

function ChicPopover({
  label,
  placeholder,
  icon,
  value,
  options,
  onChange,
  searchable
}: {
  label: string;
  placeholder: string;
  icon: React.ReactNode;
  value: string;
  options: Array<{ value: string; label: string; badge?: string }>;
  onChange: (value: string) => void;
  searchable?: boolean;
}) {
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState("");
  const rootRef = React.useRef<HTMLDivElement>(null);
  const inputRef = React.useRef<HTMLInputElement>(null);

  const selectedLabel = options.find((o) => o.value === value)?.label ?? placeholder;
  const filtered = React.useMemo(() => {
    if (!searchable || !query.trim()) return options;
    const q = query.toLowerCase();
    return options.filter((o) => o.label.toLowerCase().includes(q));
  }, [query, options, searchable]);

  React.useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, [open]);

  React.useEffect(() => {
    if (open && searchable) {
      window.setTimeout(() => inputRef.current?.focus(), 0);
    }
  }, [open, searchable]);

  return (
    <div ref={rootRef} className="relative inline-flex shrink-0">
      <button
        type="button"
        aria-label={label}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="inline-flex h-8 items-center gap-1.5 rounded-full border border-slate-700 bg-slate-900 pl-2.5 pr-2.5 text-[12px] text-slate-200 transition hover:border-slate-500 hover:bg-slate-800"
      >
        <span className="shrink-0 text-slate-400">{icon}</span>
        <span className="max-w-[160px] truncate">{selectedLabel}</span>
        <ChevronDown className="h-3.5 w-3.5 text-slate-400" />
      </button>

      {open ? (
        <div
          role="listbox"
          className="absolute bottom-full left-0 z-50 mb-2 w-[260px] overflow-hidden rounded-lg border border-slate-700 bg-slate-900 shadow-xl"
        >
          {searchable ? (
            <div className="flex items-center gap-1.5 border-b border-slate-800 px-2.5 py-2 text-[12px]">
              <Search className="h-3.5 w-3.5 text-slate-400" />
              <input
                ref={inputRef}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder={`搜索${label}`}
                className="min-w-0 flex-1 bg-transparent text-slate-200 placeholder:text-slate-500 outline-none"
              />
              {query ? (
                <button
                  type="button"
                  onClick={() => setQuery("")}
                  className="text-slate-400 hover:text-slate-200"
                  aria-label="清空"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              ) : null}
            </div>
          ) : null}
          <div className="max-h-56 overflow-auto py-1">
            {filtered.length === 0 ? (
              <div className="px-3 py-2 text-[12px] text-slate-500">无匹配项</div>
            ) : (
              filtered.map((o) => {
                const selected = o.value === value;
                return (
                  <button
                    key={o.value}
                    type="button"
                    role="option"
                    aria-selected={selected}
                    onClick={() => {
                      onChange(o.value);
                      setOpen(false);
                      setQuery("");
                    }}
                    className="flex w-full items-center justify-between gap-2 px-2.5 py-1.5 text-left text-[12px] text-slate-200 hover:bg-slate-800"
                  >
                    <span className="truncate">{o.label}</span>
                    {selected ? <Check className="h-3.5 w-3.5 text-cyan-300" /> : null}
                  </button>
                );
              })
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}

function modelOptionValue(model: ModelInfo) {
  return model.providerId ? `${model.providerId}/${model.id}` : model.id;
}
