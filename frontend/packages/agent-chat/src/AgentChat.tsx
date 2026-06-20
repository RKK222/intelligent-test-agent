"use client";

import * as React from "react";
import type {
  AgentInfo,
  AgentMessage,
  CommandInfo,
  ModelInfo,
  PermissionRequest,
  ProviderInfo,
  QuestionRequest,
  RuntimeResourceInfo,
  RuntimeStatus,
  RuntimeToolInfo,
  TodoItem
} from "@test-agent/shared-types";
import { Button, Input, SegmentedTabs } from "@test-agent/ui-kit";
import { Pin, Trash2 } from "lucide-react";
import { AssistantThread } from "./AssistantThread";
import type { ComposerAttachment } from "./prompt-parts";

export type AgentChatProps = {
  messages: AgentMessage[];
  history: { id: string; title: string; preview: string; status: string; updatedAt: string; pinned?: boolean }[];
  running?: boolean;
  onSend: (prompt: string, attachments?: ComposerAttachment[]) => void;
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
  historySearch?: string;
  onHistorySearchChange?: (query: string) => void;
  onToggleHistoryPin?: (sessionId: string, pinned: boolean) => void;
  onDeleteHistory?: (sessionId: string) => void;
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
  historySearch,
  onHistorySearchChange,
  onToggleHistoryPin,
  onDeleteHistory,
  onRequestNotifications
}: AgentChatProps) {
  const [tab, setTab] = React.useState<AgentTab>("agent");
  const [localHistorySearch, setLocalHistorySearch] = React.useState("");
  const resolvedHistorySearch = historySearch ?? localHistorySearch;
  const filteredHistory = React.useMemo(() => {
    const query = resolvedHistorySearch.trim().toLowerCase();
    return query
      ? history.filter((item) => `${item.title} ${item.preview} ${item.status}`.toLowerCase().includes(query))
      : history;
  }, [history, resolvedHistorySearch]);

  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--ta-chat-bg)] text-[var(--ta-chat-text)]">
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
          <RuntimeStatusPanel runtimeStatus={runtimeStatus} tools={tools} resources={resources} />
          <RuntimeDock
            permissions={permissions}
            questions={questions}
            onReplyPermission={onReplyPermission}
            onReplyQuestion={onReplyQuestion}
            onRejectQuestion={onRejectQuestion}
          />
          <section aria-label="Agent 对话线程" className="min-h-0 flex-1 overflow-hidden">
            <AssistantThread
              messages={messages}
              running={running}
              onSend={onSend}
              onCancel={onCancel}
              onRetry={onRetry}
              onOpenDiff={onOpenDiff}
              commands={commands}
              resources={resources}
              todos={todos}
              agents={agents}
              models={models}
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
          </section>
        </>
      ) : (
        <div className="min-h-0 flex-1 space-y-2 overflow-auto p-3">
          <Input
            value={resolvedHistorySearch}
            onChange={(event) => {
              setLocalHistorySearch(event.target.value);
              onHistorySearchChange?.(event.target.value);
            }}
            placeholder="搜索 Session"
          />
          {filteredHistory.map((item) => (
            <div
              key={item.id}
              className="rounded-md border border-slate-800 bg-slate-950 p-3 hover:border-slate-600"
            >
              <div className="flex items-start gap-2">
                <button type="button" className="min-w-0 flex-1 text-left" onClick={() => onSelectHistory?.(item.id)}>
                  <div className="flex items-center gap-2">
                    <span className="rounded-full bg-slate-800 px-2 py-0.5 text-[11px] text-slate-300">{item.status}</span>
                    <span className="min-w-0 flex-1 truncate text-[12px] font-semibold text-slate-100">{item.title}</span>
                    <span className="text-[11px] text-slate-500">{item.updatedAt}</span>
                  </div>
                  <div className="mt-1 truncate text-[12px] text-slate-500">{item.preview}</div>
                </button>
                {item.id.startsWith("ses_") ? (
                  <div className="flex shrink-0 gap-1">
                    <button
                      type="button"
                      title={item.pinned ? "取消置顶" : "置顶"}
                      className={`rounded border border-slate-800 p-1 ${item.pinned ? "text-cyan-300" : "text-slate-500"} hover:border-slate-600 hover:text-slate-100`}
                      onClick={() => onToggleHistoryPin?.(item.id, !item.pinned)}
                    >
                      <Pin className="h-3.5 w-3.5" />
                    </button>
                    <button
                      type="button"
                      title="删除"
                      className="rounded border border-slate-800 p-1 text-slate-500 hover:border-red-900 hover:text-red-200"
                      onClick={() => onDeleteHistory?.(item.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  </div>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      )}
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
    <div className="flex min-h-8 flex-wrap items-center gap-2 border-b border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] px-3 py-1.5 text-[11px] text-[var(--ta-chat-muted)]">
      <span className="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">Session {runtimeStatus?.status ?? "idle"}</span>
      {runtimeStatus?.branch ? <span className="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">{runtimeStatus.branch}</span> : null}
      {runtimeStatus?.lsp ? <span className="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">LSP {runtimeStatus.lsp.status}</span> : null}
      {runtimeStatus?.mcp ? <span className="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">MCP {runtimeStatus.mcp.status}</span> : null}
      {tools.length ? <span className="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">{tools.length} tools</span> : null}
      {resources.length ? <span className="rounded border border-[var(--ta-chat-border)] px-2 py-0.5">{resources.length} refs</span> : null}
      {percent != null ? (
        <span className="flex min-w-[96px] items-center gap-2">
          <span className="h-1.5 flex-1 rounded bg-[var(--ta-chat-detail-bg)]">
            <span className="block h-1.5 rounded bg-[var(--ta-chat-status-running)]" style={{ width: `${percent}%` }} />
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
  onReplyPermission,
  onReplyQuestion,
  onRejectQuestion
}: {
  permissions: PermissionRequest[];
  questions: QuestionRequest[];
  onReplyPermission?: (requestId: string, decision: "once" | "always" | "reject") => void;
  onReplyQuestion?: (requestId: string, answers: unknown[]) => void;
  onRejectQuestion?: (requestId: string) => void;
}) {
  const [answers, setAnswers] = React.useState<Record<string, string>>({});
  const [multiAnswers, setMultiAnswers] = React.useState<Record<string, string[]>>({});
  if (permissions.length === 0 && questions.length === 0) {
    return null;
  }
  return (
    <div className="space-y-2">
      {permissions.map((item) => (
        <div key={item.requestId} className="rounded-md border border-[rgba(245,158,11,.3)] bg-[rgba(245,158,11,.08)] p-3">
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
        <div key={item.requestId} className="rounded-md border border-[rgba(34,211,238,.4)] bg-[rgba(34,211,238,.08)] p-3">
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
    </div>
  );
}

function contextPercent(runtimeStatus?: RuntimeStatus) {
  const tokens = runtimeStatus?.tokens;
  if (!tokens?.contextWindow) {
    return null;
  }
  const used = (tokens.input ?? 0) + (tokens.output ?? 0) + (tokens.reasoning ?? 0) + (tokens.cacheRead ?? 0) + (tokens.cacheWrite ?? 0);
  return Math.min(100, Math.max(0, Math.round((used / tokens.contextWindow) * 100)));
}
