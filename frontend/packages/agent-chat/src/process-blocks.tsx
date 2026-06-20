"use client";

import * as React from "react";
import { ChevronDown, ChevronRight } from "lucide-react";
import type { MessagePart, TodoItem } from "@test-agent/shared-types";
import {
  isActiveStatus,
  normalizeProcessStatus,
  statusLabel,
  statusToneClass,
  textValue,
  todoTitle,
  toolPartIsSkill
} from "./process-status";

export function ProcessDisclosure({
  id,
  title,
  status,
  statusKind,
  summary,
  defaultOpen = false,
  children,
  testId
}: {
  id: string;
  title: string;
  status: unknown;
  statusKind: "thinking" | "task" | "tool" | "skill";
  summary?: React.ReactNode;
  defaultOpen?: boolean;
  children?: React.ReactNode;
  testId?: string;
}) {
  const normalizedStatus = normalizeProcessStatus(status);
  const [open, setOpen] = React.useState(defaultOpen);

  React.useEffect(() => {
    setOpen(defaultOpen);
  }, [id]);

  return (
    <details
      data-testid={testId}
      open={open}
      className="overflow-hidden rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)]"
    >
      <summary
        className="flex list-none items-center gap-2 px-3 py-2 text-[11px] text-[var(--ta-chat-subtle)]"
        onClick={(event) => event.preventDefault()}
      >
        <span className={`h-1.5 w-1.5 rounded-full ${normalizedStatus === "running" ? "animate-pulse bg-[var(--ta-chat-status-running)]" : "bg-[var(--ta-chat-border-strong)]"}`} />
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-2">
            <span className="truncate font-semibold text-[var(--ta-chat-text)]">{title}</span>
            <span className={`shrink-0 rounded-full border px-1.5 py-0.5 text-[10px] ${statusToneClass(normalizedStatus)}`}>
              {statusLabel(normalizedStatus, statusKind)}
            </span>
          </div>
          {summary ? <div className="mt-0.5 truncate text-[11px] text-[var(--ta-chat-muted)]">{summary}</div> : null}
        </div>
        {children ? (
          <button
            type="button"
            aria-label={`${open ? "收起" : "展开"}${title}`}
            className="inline-flex h-6 shrink-0 items-center gap-1 rounded px-1.5 text-[11px] text-[var(--ta-chat-muted)] hover:bg-[var(--ta-chat-hover)] hover:text-[var(--ta-chat-text)]"
            onClick={(event) => {
              event.stopPropagation();
              setOpen((value) => !value);
            }}
          >
            {open ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
          </button>
        ) : null}
      </summary>
      {open && children ? <div className="border-t border-[var(--ta-chat-border)] px-3 py-2">{children}</div> : null}
    </details>
  );
}

export function AnswerPart({ part }: { part: Extract<MessagePart, { type: "text" }> }) {
  return (
    <div
      data-testid="answer-part"
      className="rounded-md border border-[var(--ta-chat-answer-border)] bg-[var(--ta-chat-answer-bg)] px-3 py-2.5"
    >
      <div className="mb-1.5 text-[11px] font-semibold text-[var(--ta-chat-subtle)]">最终回答</div>
      <div className="whitespace-pre-wrap text-[13px] leading-6 text-[var(--ta-chat-text)]">{part.text}</div>
    </div>
  );
}

export function PlainAnswer({ text }: { text: string }) {
  return (
    <div
      data-testid="answer-part"
      className="rounded-md border border-[var(--ta-chat-answer-border)] bg-[var(--ta-chat-answer-bg)] px-3 py-2.5"
    >
      <div className="whitespace-pre-wrap text-[13px] leading-6 text-[var(--ta-chat-text)]">{text}</div>
    </div>
  );
}

export function ReasoningPartBlock({
  part,
  openByDefault
}: {
  part: Extract<MessagePart, { type: "reasoning" }>;
  openByDefault: boolean;
}) {
  const status = normalizeProcessStatus(part.status ?? "not_started");
  const title = part.title ?? (status === "running" ? "正在整理信息" : "思考状态");
  return (
    <ProcessDisclosure
      id={part.partId}
      testId={`reasoning-part-${part.partId}`}
      title="思考状态"
      status={status}
      statusKind="thinking"
      summary={title}
      defaultOpen={openByDefault}
    >
      <div className="max-h-44 overflow-auto whitespace-pre-wrap pr-1 text-[12px] leading-6 text-[var(--ta-chat-muted)]">
        {part.text || "暂无详细思考内容"}
      </div>
    </ProcessDisclosure>
  );
}

export function TaskBreakdown({ todos }: { todos: TodoItem[] }) {
  if (!todos.length) {
    return null;
  }
  return (
    <section className="rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] p-2.5">
      <div className="mb-2 flex items-center justify-between gap-2">
        <div className="text-[12px] font-semibold text-[var(--ta-chat-text)]">任务分解</div>
        <div className="text-[11px] text-[var(--ta-chat-muted)]">{todos.length} 项</div>
      </div>
      <div className="space-y-1.5">
        {todos.map((item) => (
          <TaskItem key={item.id} item={item} />
        ))}
      </div>
    </section>
  );
}

function TaskItem({ item }: { item: TodoItem }) {
  const status = normalizeProcessStatus(item.status);
  const detail = item.description ?? item.summary ?? item.result ?? item.error;
  const summary = item.summary ?? item.description ?? item.result ?? item.error;
  const steps = Array.isArray(item.steps) ? item.steps : [];
  return (
    <ProcessDisclosure
      id={item.id}
      testId={`task-item-${item.id}`}
      title={todoTitle(item)}
      status={status}
      statusKind="task"
      summary={summary}
      defaultOpen={isActiveStatus(status)}
    >
      <div className="space-y-2 text-[12px] leading-5 text-[var(--ta-chat-muted)]">
        {detail ? <div className="whitespace-pre-wrap">{detail}</div> : null}
        {steps.length ? (
          <ol className="space-y-1">
            {steps.map((step, index) => (
              <li key={`${item.id}-${index}`} className="flex gap-2">
                <span className="mt-0.5 text-[10px] text-[var(--ta-chat-subtle)]">{index + 1}</span>
                <span className="min-w-0 flex-1 whitespace-pre-wrap">{step}</span>
              </li>
            ))}
          </ol>
        ) : null}
        {item.error ? <div className="text-[var(--ta-chat-status-error)]">{item.error}</div> : null}
      </div>
    </ProcessDisclosure>
  );
}

export function ToolPartBlock({ part }: { part: Extract<MessagePart, { type: "tool" }> }) {
  const skill = toolPartIsSkill(part);
  const summary = toolPurpose(part);
  const skillName = skill ? skillNameFromPart(part) : undefined;
  return (
    <ProcessDisclosure
      id={part.partId}
      title={skill ? "Skill 调用" : "能力调用"}
      status={part.status}
      statusKind={skill ? "skill" : "tool"}
      summary={skill ? <SkillSummary name={skillName} purpose={summary} /> : `${part.toolName}${summary ? `｜${summary}` : ""}`}
      defaultOpen={normalizeProcessStatus(part.status) === "running"}
      testId={`${skill ? "skill" : "tool"}-part-${part.partId}`}
    >
      <ToolDetail
        label={skill ? skillName ?? "Skill 调用" : part.toolName}
        status={part.status}
        purpose={summary}
        input={part.input}
        output={part.output}
        metadata={part.metadata}
        statusKind={skill ? "skill" : "tool"}
        startedAt={part.startedAt}
        endedAt={part.endedAt}
      />
    </ProcessDisclosure>
  );
}

export function ToolPayloadBlock({
  id,
  title,
  payload,
  defaultOpen
}: {
  id: string;
  title: string;
  payload: Record<string, unknown>;
  defaultOpen: boolean;
}) {
  const toolName = textValue(payload.toolName) ?? textValue(payload.tool) ?? textValue(payload.rawType) ?? "tool";
  const skill = toolName.toLowerCase() === "skill";
  const status = textValue(payload.status) ?? (title.includes("开始") ? "running" : "completed");
  const purpose = textValue(payload.summary) ?? textValue(payload.message) ?? textValue(payload.title);
  const input = record(payload.input);
  const metadata = record(payload.metadata);
  const output = payload.output ?? payload.rawOutput;
  const name = skill ? skillNameFromPayload(payload) : toolName;
  return (
    <ProcessDisclosure
      id={id}
      title={skill ? "Skill 调用" : title}
      status={status}
      statusKind={skill ? "skill" : "tool"}
      summary={skill ? <SkillSummary name={name} purpose={purpose} /> : `${toolName}${purpose ? `｜${purpose}` : ""}`}
      defaultOpen={defaultOpen}
      testId={`timeline-card-${id}`}
    >
      <ToolDetail
        label={name ?? toolName}
        status={status}
        purpose={purpose}
        input={input}
        output={output}
        metadata={metadata}
        path={textValue(payload.path)}
        statusKind={skill ? "skill" : "tool"}
        startedAt={textValue(payload.startedAt)}
        endedAt={textValue(payload.endedAt)}
      />
    </ProcessDisclosure>
  );
}

function SkillSummary({ name, purpose }: { name?: string; purpose?: string }) {
  return (
    <>
      {name ? <span>{name}</span> : null}
      {name && purpose ? <span>｜</span> : null}
      {purpose ? <span>{purpose}</span> : null}
    </>
  );
}

function ToolDetail({
  label,
  status,
  purpose,
  input,
  output,
  metadata,
  path,
  statusKind,
  startedAt,
  endedAt
}: {
  label: string;
  status: unknown;
  purpose?: string;
  input?: Record<string, unknown>;
  output?: unknown;
  metadata?: Record<string, unknown>;
  path?: string;
  statusKind: "tool" | "skill";
  startedAt?: string;
  endedAt?: string;
}) {
  const normalizedStatus = normalizeProcessStatus(status);
  const metaPurpose = textValue(metadata?.purpose) ?? textValue(metadata?.summary) ?? textValue(metadata?.description);
  return (
    <div className="space-y-2 text-[12px] leading-5 text-[var(--ta-chat-muted)]">
      <div className="flex flex-wrap items-center gap-2">
        <span className="rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-2 py-0.5 font-mono text-[11px] text-[var(--ta-chat-text)]">
          {label}
        </span>
        <span className={`rounded-full border px-1.5 py-0.5 text-[10px] ${statusToneClass(normalizedStatus)}`}>
          {statusLabel(normalizedStatus, statusKind)}
        </span>
        {path ? <span>路径: {path}</span> : null}
        {startedAt ? <span>开始: {formatTime(startedAt)}</span> : null}
        {endedAt ? <span>结束: {formatTime(endedAt)}</span> : null}
      </div>
      {purpose || metaPurpose ? <div className="whitespace-pre-wrap">{purpose ?? metaPurpose}</div> : null}
      {input && Object.keys(input).length ? (
        <pre className="max-h-28 overflow-auto rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 text-[11px]">
          {JSON.stringify(input, null, 2)}
        </pre>
      ) : null}
      {output ? (
        <pre className="max-h-36 overflow-auto whitespace-pre-wrap rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 text-[11px]">
          {String(output)}
        </pre>
      ) : null}
    </div>
  );
}

function toolPurpose(part: Extract<MessagePart, { type: "tool" }>) {
  return (
    textValue(part.metadata?.purpose) ??
    textValue(part.metadata?.summary) ??
    textValue(part.metadata?.description) ??
    textValue(part.metadata?.title)
  );
}

function skillNameFromPart(part: Extract<MessagePart, { type: "tool" }>) {
  return (
    textValue(part.input?.name) ??
    textValue(part.metadata?.name) ??
    skillNameFromOutput(part.output) ??
    (part.toolName.toLowerCase() === "skill" ? undefined : part.toolName)
  );
}

function skillNameFromPayload(payload: Record<string, unknown>) {
  const input = record(payload.input);
  const metadata = record(payload.metadata);
  return (
    textValue(input?.name) ??
    textValue(metadata?.name) ??
    skillNameFromOutput(payload.output) ??
    skillNameFromOutput(payload.rawOutput) ??
    textValue(payload.toolName)
  );
}

function skillNameFromOutput(value: unknown) {
  const match = typeof value === "string" ? /Loaded skill:\s*([^\n<]+)/i.exec(value) : null;
  return match?.[1]?.trim();
}

function record(value: unknown) {
  return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}
