"use client";

import { Brain, CheckCircle2, ChevronDown, ChevronRight, FileText, FolderOpen, Terminal } from "lucide-react";
import * as React from "react";
import type { AgentMessage, RunDiffFile } from "@test-agent/shared-types";
import { Badge, Button } from "@test-agent/ui-kit";
import { ToolPayloadBlock } from "./process-blocks";

export function AgentCard({
  message,
  onOpenDiff,
  defaultOpen = false
}: {
  message: Extract<AgentMessage, { role: "card" }>;
  onOpenDiff: () => void;
  defaultOpen?: boolean;
}) {
  if (message.cardType === "plan") {
    const steps = arrayOfRecords(message.payload.steps);
    return (
      <TimelineCard
        id={message.id}
        icon={<Brain className="h-4 w-4 text-[var(--ta-chat-subtle)]" />}
        title="规划步骤"
        defaultOpen={defaultOpen}
      >
        <div className="space-y-1">
          {steps.map((step, index) => (
            <div key={`${step.title}-${index}`} className="rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] px-2 py-1.5">
              <div className="flex items-center gap-2">
                <span className="text-[11px] text-[var(--ta-chat-muted)]">{index + 1}</span>
                <span className="min-w-0 flex-1 text-[12px] text-[var(--ta-chat-text)]">{String(step.title ?? "执行步骤")}</span>
                <Badge tone={step.status === "done" ? "success" : step.status === "active" ? "info" : "neutral"}>{String(step.status ?? "pending")}</Badge>
              </div>
            </div>
          ))}
        </div>
      </TimelineCard>
    );
  }
  if (message.cardType === "tool") {
    const toolName = text(message.payload.toolName) ?? text(message.payload.rawType) ?? text(message.payload.tool) ?? "tool";
    const path = text(message.payload.path);
    const summary = text(message.payload.summary) ?? text(message.payload.message) ?? text(message.payload.status);
    const output = message.payload.output ?? message.payload.rawOutput;
    return (
      <ToolPayloadBlock id={message.id} title={message.title} payload={{ ...message.payload, toolName, path, summary, output }} defaultOpen={defaultOpen} />
    );
  }
  if (message.cardType === "test") {
    return (
      <TimelineCard
        id={message.id}
        icon={<Terminal className="h-4 w-4 text-[var(--ta-chat-subtle)]" />}
        title={message.title}
        defaultOpen={defaultOpen}
      >
        <div className="flex items-center gap-2">
          <Badge tone={message.payload.status === "failed" ? "danger" : "success"}>{String(message.payload.status ?? "finished")}</Badge>
          <span className="font-mono text-[12px] text-[var(--ta-chat-muted)]">{String(message.payload.command ?? "test run")}</span>
        </div>
      </TimelineCard>
    );
  }
  if (message.cardType === "diff") {
    const files = (message.payload.files as RunDiffFile[] | undefined) ?? [];
    return (
      <TimelineCard
        id={message.id}
        icon={<FolderOpen className="h-4 w-4 text-[var(--ta-chat-subtle)]" />}
        title={`本次变更涉及 ${files.length} 个文件`}
        defaultOpen={defaultOpen}
      >
        <div className="overflow-hidden rounded-md bg-[var(--ta-chat-process-bg)]">
          <div className="grid grid-cols-[minmax(0,1.5fr)_minmax(96px,.6fr)_minmax(96px,.45fr)] border-b border-[var(--ta-chat-border)] px-4 py-2 text-[12px] font-semibold text-[var(--ta-chat-subtle)]">
            <div>文件</div>
            <div>状态</div>
            <div>行变更</div>
          </div>
          {files.map((file) => (
            <div
              key={file.path}
              className="grid grid-cols-[minmax(0,1.5fr)_minmax(96px,.6fr)_minmax(96px,.45fr)] border-b border-dashed border-[var(--ta-chat-border)] px-4 py-2 last:border-b-0"
            >
              <div className="min-w-0 truncate font-mono text-[12px] text-[var(--ta-chat-text)]">{file.path}</div>
              <div>
                <Badge tone="warning" className="bg-[rgba(245,158,11,.18)] font-bold uppercase tracking-wide">
                  {file.status.toUpperCase()}
                </Badge>
              </div>
              <div className="font-semibold text-[var(--ta-chat-text)]">{lineChange(file)}</div>
            </div>
          ))}
        </div>
        <Button className="mt-2" size="sm" variant="primary" onClick={onOpenDiff}>
          查看 Diff
        </Button>
      </TimelineCard>
    );
  }
  const summary = text(message.payload.summary) ?? text(message.payload.message) ?? text(message.payload.status);
  return (
    <TimelineCard
      id={message.id}
      icon={message.cardType === "event" ? <CheckCircle2 className="h-4 w-4 text-[var(--ta-chat-subtle)]" /> : <FileText className="h-4 w-4 text-[var(--ta-chat-subtle)]" />}
      title={message.title}
      defaultOpen={defaultOpen}
    >
      {summary ? (
        <div className="rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] px-3 py-2 text-[12px] leading-6 text-[var(--ta-chat-text)]">{summary}</div>
      ) : (
        <pre className="max-h-40 overflow-auto whitespace-pre-wrap rounded-md bg-[var(--ta-chat-detail-bg)] p-3 text-[12px] text-[var(--ta-chat-muted)]">
          {JSON.stringify(message.payload, null, 2)}
        </pre>
      )}
    </TimelineCard>
  );
}

function TimelineCard({
  id,
  icon,
  title,
  defaultOpen,
  children
}: {
  id: string;
  icon: React.ReactNode;
  title: string;
  defaultOpen: boolean;
  children: React.ReactNode;
}) {
  const [open, setOpen] = React.useState(defaultOpen);
  React.useEffect(() => {
    setOpen(defaultOpen);
  }, [id]);

  return (
    <details
      data-testid={`timeline-card-${id}`}
      open={open}
      className="overflow-hidden rounded-md border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)]"
    >
      <summary
        className="flex list-none items-center gap-2 border-b border-[var(--ta-chat-border)] bg-[var(--ta-chat-process-bg)] px-3 py-2"
        onClick={(event) => event.preventDefault()}
      >
        {icon}
        <div className="min-w-0 flex-1 truncate text-[12px] font-semibold text-[var(--ta-chat-text)]">{title}</div>
        <button
          type="button"
          aria-label={`${open ? "收起" : "展开"} ${title}`}
          className="inline-flex items-center gap-1 rounded px-2 py-1 text-[11px] font-medium text-[var(--ta-chat-muted)] hover:bg-[var(--ta-chat-hover)] hover:text-[var(--ta-chat-text)]"
          onClick={(event) => {
            event.stopPropagation();
            setOpen((value) => !value);
          }}
        >
          {open ? "收起" : "展开"}
          {open ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
        </button>
      </summary>
      {open ? <div className="p-3">{children}</div> : null}
    </details>
  );
}

function arrayOfRecords(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null) : [];
}

function text(value: unknown) {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function lineChange(file: RunDiffFile) {
  return `+${file.additions} -${file.deletions}`;
}
