"use client";

import * as React from "react";
import type {
  AgentInfo,
  AgentMessage,
  CommandInfo,
  MessagePart,
  ModelInfo,
  PermissionRequest,
  ProviderInfo,
  QuestionRequest,
  RuntimeResourceInfo,
  RuntimeStatus,
  RuntimeToolInfo,
  TodoItem
} from "@test-agent/shared-types";
import { Button, Input, SegmentedTabs, Textarea } from "@test-agent/ui-kit";
import { AgentCard } from "./cards";

export type AgentChatProps = {
  messages: AgentMessage[];
  history: { id: string; title: string; preview: string; status: string; updatedAt: string }[];
  running?: boolean;
  onSend: (prompt: string) => void;
  onOpenDiff: () => void;
  onRetry: () => void;
  onCancel: () => void;
  permissions?: PermissionRequest[];
  questions?: QuestionRequest[];
  todos?: TodoItem[];
  onReplyPermission?: (requestId: string, decision: "once" | "always" | "reject") => void;
  onReplyQuestion?: (requestId: string, answers: unknown[]) => void;
  onRejectQuestion?: (requestId: string) => void;
  agents?: AgentInfo[];
  models?: ModelInfo[];
  providers?: ProviderInfo[];
  commands?: CommandInfo[];
  resources?: RuntimeResourceInfo[];
  tools?: RuntimeToolInfo[];
  runtimeStatus?: RuntimeStatus;
  selectedAgent?: string;
  selectedProvider?: string;
  selectedModel?: string;
  mode?: string;
  onAgentChange?: (agentId: string) => void;
  onProviderChange?: (providerId: string) => void;
  onModelChange?: (modelId: string) => void;
  onModeChange?: (mode: string) => void;
  onSelectHistory?: (sessionId: string) => void;
  onRequestNotifications?: () => void;
};

type AgentTab = "agent" | "history";

export function AgentChat({
  messages,
  history,
  running,
  onSend,
  onOpenDiff,
  onRetry,
  onCancel,
  permissions = [],
  questions = [],
  todos = [],
  onReplyPermission,
  onReplyQuestion,
  onRejectQuestion,
  agents = [],
  models = [],
  providers = [],
  commands = [],
  resources = [],
  tools = [],
  runtimeStatus,
  selectedAgent,
  selectedProvider,
  selectedModel,
  mode = "build",
  onAgentChange,
  onProviderChange,
  onModelChange,
  onModeChange,
  onSelectHistory,
  onRequestNotifications
}: AgentChatProps) {
  const [tab, setTab] = React.useState<AgentTab>("agent");
  const [text, setText] = React.useState("");
  const [historySearch, setHistorySearch] = React.useState("");
  const slashQuery = React.useMemo(() => commandQuery(text), [text]);
  const atQuery = React.useMemo(() => contextQuery(text), [text]);
  const filteredHistory = React.useMemo(() => {
    const query = historySearch.trim().toLowerCase();
    return query
      ? history.filter((item) => `${item.title} ${item.preview} ${item.status}`.toLowerCase().includes(query))
      : history;
  }, [history, historySearch]);
  const contextItems = React.useMemo(() => resources.slice(0, 12).map((item) => ({
    id: item.id,
    label: item.name,
    detail: item.uri ?? item.type ?? item.id
  })), [resources]);

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const prompt = text.trim();
    if (!prompt) {
      return;
    }
    onSend(prompt);
    setText("");
    setTab("agent");
  }

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--ta-panel-2)]">
      <SegmentedTabs
        value={tab}
        onValueChange={setTab}
        items={[
          { id: "agent", label: "Agent" },
          { id: "history", label: "历史", count: history.length }
        ]}
      />
      {tab === "agent" ? (
        <>
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
          <RuntimeStatusPanel runtimeStatus={runtimeStatus} tools={tools} resources={resources} />
          <div className="min-h-0 flex-1 space-y-3 overflow-auto p-3">
            <RuntimeDock
              permissions={permissions}
              questions={questions}
              todos={todos}
              onReplyPermission={onReplyPermission}
              onReplyQuestion={onReplyQuestion}
              onRejectQuestion={onRejectQuestion}
            />
            {messages.map((message) =>
              message.role === "card" ? (
                <AgentCard key={message.id} message={message} onOpenDiff={onOpenDiff} />
              ) : (
                <div key={message.id} className={message.role === "user" ? "flex justify-end" : "flex justify-start"}>
                  <div className={message.role === "user" ? "max-w-[92%] rounded-md border border-blue-900 bg-blue-950 px-3 py-2" : "max-w-[92%] rounded-md border border-slate-800 bg-slate-950 px-3 py-2"}>
                    <div className="mb-1 text-[11px] text-slate-500">{message.role === "user" ? "You" : "Agent"}</div>
                    {message.role === "assistant" && message.parts?.length ? (
                      <MessageParts parts={message.parts} fallbackText={message.text} />
                    ) : (
                      <div className="whitespace-pre-wrap text-[12px] leading-6 text-slate-100">{message.text}</div>
                    )}
                  </div>
                </div>
              )
            )}
          </div>
          <form className="border-t border-slate-800 bg-slate-950 p-3" onSubmit={submit}>
            <Textarea
              value={text}
              rows={3}
              placeholder="描述测试任务，例如：跑 checkout 模块并分析失败原因"
              onChange={(event) => setText(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && !event.shiftKey) {
                  event.preventDefault();
                  event.currentTarget.form?.requestSubmit();
                }
              }}
            />
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
                    onPick: () => setText(replaceCommandQuery(text, command.name))
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
                    onPick: () => setText(replaceContextQuery(text, item.label))
                  }))}
              />
            ) : null}
            <div className="mt-2 flex items-center justify-between gap-2">
              <div className="text-[11px] text-slate-500">{running ? "Run 正在执行" : "Enter 发送"}</div>
              <div className="flex gap-2">
                <Button type="button" size="sm" variant="secondary" disabled={!running} onClick={onCancel}>
                  取消
                </Button>
                <Button type="button" size="sm" variant="secondary" onClick={onRetry}>
                  重试
                </Button>
                <Button type="submit" size="sm" variant="primary" disabled={running || !text.trim()}>
                  发送
                </Button>
              </div>
            </div>
          </form>
        </>
      ) : (
        <div className="min-h-0 flex-1 space-y-2 overflow-auto p-3">
          <Input value={historySearch} onChange={(event) => setHistorySearch(event.target.value)} placeholder="搜索 Session" />
          {filteredHistory.map((item) => (
            <button
              key={item.id}
              type="button"
              className="w-full rounded-md border border-slate-800 bg-slate-950 p-3 text-left hover:border-slate-600"
              onClick={() => onSelectHistory?.(item.id)}
            >
              <div className="flex items-center gap-2">
                <span className="rounded-full bg-slate-800 px-2 py-0.5 text-[11px] text-slate-300">{item.status}</span>
                <span className="min-w-0 flex-1 truncate text-[12px] font-semibold text-slate-100">{item.title}</span>
                <span className="text-[11px] text-slate-500">{item.updatedAt}</span>
              </div>
              <div className="mt-1 truncate text-[12px] text-slate-500">{item.preview}</div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
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
    <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,1fr)_auto] gap-2 border-b border-slate-800 bg-slate-950 p-2">
      <label className="min-w-0">
        <span className="sr-only">Agent</span>
        <select
          value={selectedAgent ?? ""}
          className="h-8 w-full rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200"
          onChange={(event) => onAgentChange?.(event.target.value)}
        >
          <option value="">Agent</option>
          {agents.map((agent) => (
            <option key={agent.agentId} value={agent.agentId}>
              {agent.name}
            </option>
          ))}
        </select>
      </label>
      <label className="min-w-0">
        <span className="sr-only">Provider</span>
        <select
          value={selectedProvider ?? ""}
          className="h-8 w-full rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200"
          onChange={(event) => onProviderChange?.(event.target.value)}
        >
          <option value="">Provider</option>
          {providers.map((provider) => (
            <option key={provider.providerId} value={provider.providerId}>
              {provider.name}
            </option>
          ))}
        </select>
      </label>
      <label className="min-w-0">
        <span className="sr-only">Model</span>
        <select
          value={selectedModel ?? ""}
          className="h-8 w-full rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200"
          onChange={(event) => onModelChange?.(event.target.value)}
        >
          <option value="">Model</option>
          {models.map((model) => (
            <option key={modelOptionValue(model)} value={modelOptionValue(model)}>
              {model.name}
            </option>
          ))}
        </select>
      </label>
      <label className="min-w-0">
        <span className="sr-only">Mode</span>
        <select
          value={mode}
          className="h-8 w-full rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200"
          onChange={(event) => onModeChange?.(event.target.value)}
        >
          <option value="build">Build</option>
          <option value="plan">Plan</option>
          <option value="shell">Shell</option>
          {commands.slice(0, 8).map((command) => (
            <option key={command.commandId} value={`command:${command.name}`}>
              /{command.name}
            </option>
          ))}
        </select>
      </label>
      <Button type="button" size="sm" variant="secondary" onClick={onRequestNotifications}>
        通知
      </Button>
    </div>
  );
}

function RuntimeStatusPanel({
  runtimeStatus,
  tools,
  resources
}: {
  runtimeStatus?: RuntimeStatus;
  tools: RuntimeToolInfo[];
  resources: RuntimeResourceInfo[];
}) {
  const percent = contextPercent(runtimeStatus);
  if (!runtimeStatus && tools.length === 0 && resources.length === 0) {
    return null;
  }
  return (
    <div className="flex min-h-8 flex-wrap items-center gap-2 border-b border-slate-800 bg-[var(--ta-panel)] px-3 py-1.5 text-[11px] text-slate-400">
      <span className="rounded border border-slate-800 px-2 py-0.5">Session {runtimeStatus?.status ?? "idle"}</span>
      {runtimeStatus?.branch ? <span className="rounded border border-slate-800 px-2 py-0.5">{runtimeStatus.branch}</span> : null}
      {runtimeStatus?.lsp ? <span className="rounded border border-slate-800 px-2 py-0.5">LSP {runtimeStatus.lsp.status}</span> : null}
      {runtimeStatus?.mcp ? <span className="rounded border border-slate-800 px-2 py-0.5">MCP {runtimeStatus.mcp.status}</span> : null}
      {tools.length ? <span className="rounded border border-slate-800 px-2 py-0.5">{tools.length} tools</span> : null}
      {resources.length ? <span className="rounded border border-slate-800 px-2 py-0.5">{resources.length} refs</span> : null}
      {percent != null ? (
        <span className="flex min-w-[96px] items-center gap-2">
          <span className="h-1.5 flex-1 rounded bg-slate-800">
            <span className="block h-1.5 rounded bg-blue-500" style={{ width: `${percent}%` }} />
          </span>
          {percent}%
        </span>
      ) : null}
    </div>
  );
}

function RuntimeDock({
  permissions,
  questions,
  todos,
  onReplyPermission,
  onReplyQuestion,
  onRejectQuestion
}: {
  permissions: PermissionRequest[];
  questions: QuestionRequest[];
  todos: TodoItem[];
  onReplyPermission?: (requestId: string, decision: "once" | "always" | "reject") => void;
  onReplyQuestion?: (requestId: string, answers: unknown[]) => void;
  onRejectQuestion?: (requestId: string) => void;
}) {
  const [answers, setAnswers] = React.useState<Record<string, string>>({});
  const [multiAnswers, setMultiAnswers] = React.useState<Record<string, string[]>>({});
  if (permissions.length === 0 && questions.length === 0 && todos.length === 0) {
    return null;
  }
  return (
    <div className="space-y-2">
      {permissions.map((item) => (
        <div key={item.requestId} className="rounded-md border border-amber-900 bg-amber-950/40 p-3">
          <div className="text-[12px] font-semibold text-amber-100">{item.title ?? item.type}</div>
          <div className="mt-1 whitespace-pre-wrap text-[12px] text-amber-200/80">{item.description ?? item.pattern ?? item.requestId}</div>
          <div className="mt-2 flex flex-wrap gap-2">
            <Button type="button" size="sm" variant="secondary" onClick={() => onReplyPermission?.(item.requestId, "once")}>
              一次
            </Button>
            <Button type="button" size="sm" variant="secondary" onClick={() => onReplyPermission?.(item.requestId, "always")}>
              始终
            </Button>
            <Button type="button" size="sm" variant="secondary" onClick={() => onReplyPermission?.(item.requestId, "reject")}>
              拒绝
            </Button>
          </div>
        </div>
      ))}
      {questions.map((item) => (
        <div key={item.requestId} className="rounded-md border border-cyan-900 bg-cyan-950/30 p-3">
          {item.questions.map((question) => (
            <div key={question.questionId} className="space-y-2">
              <div className="text-[12px] font-semibold text-cyan-100">{question.text}</div>
              {question.kind === "text" ? (
                <div className="flex gap-2">
                  <Input
                    value={answers[question.questionId] ?? ""}
                    onChange={(event) => setAnswers((current) => ({ ...current, [question.questionId]: event.target.value }))}
                    placeholder="回答"
                  />
                  <Button
                    type="button"
                    size="sm"
                    variant="primary"
                    disabled={!answers[question.questionId]?.trim()}
                    onClick={() => onReplyQuestion?.(item.requestId, [answers[question.questionId]?.trim()])}
                  >
                    回复
                  </Button>
                  <Button type="button" size="sm" variant="secondary" onClick={() => onRejectQuestion?.(item.requestId)}>
                    拒绝
                  </Button>
                </div>
              ) : question.kind === "multiple" ? (
                <div className="space-y-2">
                  <div className="flex flex-wrap gap-2">
                    {(question.options?.length ? question.options : [{ id: "confirm", label: "确认" }]).map((option) => {
                      const selected = multiAnswers[question.questionId]?.includes(option.id) ?? false;
                      return (
                        <label key={option.id} className="flex items-center gap-1 rounded border border-slate-800 px-2 py-1 text-[12px] text-slate-200">
                          <input
                            type="checkbox"
                            checked={selected}
                            onChange={(event) =>
                              setMultiAnswers((current) => ({
                                ...current,
                                [question.questionId]: event.target.checked
                                  ? [...(current[question.questionId] ?? []), option.id]
                                  : (current[question.questionId] ?? []).filter((id) => id !== option.id)
                              }))
                            }
                          />
                          {option.label}
                        </label>
                      );
                    })}
                  </div>
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="primary"
                      disabled={!multiAnswers[question.questionId]?.length}
                      onClick={() => onReplyQuestion?.(item.requestId, multiAnswers[question.questionId] ?? [])}
                    >
                      回复
                    </Button>
                    <Button type="button" size="sm" variant="secondary" onClick={() => onRejectQuestion?.(item.requestId)}>
                      拒绝
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {(question.options?.length ? question.options : [{ id: "confirm", label: "确认" }]).map((option) => (
                    <Button
                      key={option.id}
                      type="button"
                      size="sm"
                      variant="secondary"
                      onClick={() => onReplyQuestion?.(item.requestId, [option.id])}
                    >
                      {option.label}
                    </Button>
                  ))}
                  <Button type="button" size="sm" variant="secondary" onClick={() => onRejectQuestion?.(item.requestId)}>
                    拒绝
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      ))}
      {todos.length ? (
        <div className="rounded-md border border-slate-800 bg-slate-950 p-3">
          <div className="mb-2 text-[12px] font-semibold text-slate-200">Todo</div>
          <div className="space-y-1">
            {todos.map((item) => (
              <div key={item.id} className="flex items-center gap-2 text-[12px] text-slate-300">
                <span className="rounded bg-slate-800 px-1.5 py-0.5 text-[11px] text-slate-400">{item.status}</span>
                <span className="min-w-0 flex-1 truncate">{item.text}</span>
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </div>
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
    <div className="mt-2 max-h-44 overflow-auto rounded-md border border-slate-800 bg-slate-950 p-1">
      <div className="px-2 py-1 text-[11px] uppercase text-slate-500">{title}</div>
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          className="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left hover:bg-slate-800"
          onClick={item.onPick}
        >
          <span className="min-w-0 flex-1 truncate font-mono text-[12px] text-slate-200">{item.label}</span>
          {item.detail ? <span className="max-w-[45%] truncate text-[11px] text-slate-500">{item.detail}</span> : null}
        </button>
      ))}
    </div>
  );
}

function MessageParts({ parts, fallbackText }: { parts: MessagePart[]; fallbackText: string }) {
  if (!parts.length) {
    return <div className="whitespace-pre-wrap text-[12px] leading-6 text-slate-100">{fallbackText}</div>;
  }
  return (
    <div className="space-y-2">
      {parts.map((part) => (
        <div key={part.partId} className="rounded border border-slate-800 bg-slate-950/70 p-2">
          {part.type === "text" || part.type === "reasoning" ? (
            <div className="whitespace-pre-wrap text-[12px] leading-6 text-slate-100">{part.text}</div>
          ) : part.type === "tool" ? (
            <div className="space-y-1 text-[12px] text-slate-300">
              <div className="font-mono text-slate-200">{part.toolName}</div>
              <div className="text-slate-500">{part.status}</div>
              {part.output ? <pre className="max-h-32 overflow-auto whitespace-pre-wrap text-[11px]">{String(part.output)}</pre> : null}
            </div>
          ) : part.type === "file" ? (
            <div className="font-mono text-[12px] text-slate-300">{part.path ?? part.name ?? part.partId}</div>
          ) : (
            <pre className="max-h-32 overflow-auto whitespace-pre-wrap text-[11px] text-slate-400">
              {JSON.stringify(part.payload, null, 2)}
            </pre>
          )}
        </div>
      ))}
    </div>
  );
}

function modelOptionValue(model: ModelInfo) {
  return model.providerId ? `${model.providerId}/${model.id}` : model.id;
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

function contextPercent(runtimeStatus?: RuntimeStatus) {
  const tokens = runtimeStatus?.tokens;
  if (!tokens?.contextWindow) {
    return null;
  }
  const used = (tokens.input ?? 0) + (tokens.output ?? 0) + (tokens.reasoning ?? 0) + (tokens.cacheRead ?? 0) + (tokens.cacheWrite ?? 0);
  return Math.min(100, Math.max(0, Math.round((used / tokens.contextWindow) * 100)));
}
