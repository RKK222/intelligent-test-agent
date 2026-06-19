"use client";

import { Brain, CheckCircle2, ChevronDown, ChevronRight, Code2, FileText, FolderOpen, Terminal } from "lucide-react";
import * as React from "react";
import type { AgentMessage, RunDiffFile } from "@test-agent/shared-types";
import { Badge, Button } from "@test-agent/ui-kit";

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
        icon={<Brain className="h-4 w-4 text-pink-300" />}
        title="规划步骤"
        defaultOpen={defaultOpen}
      >
        <div className="space-y-1">
          {steps.map((step, index) => (
            <div key={`${step.title}-${index}`} className="rounded-md border border-[#1d2b4d] bg-[#0a1324] px-2 py-1.5">
              <div className="flex items-center gap-2">
                <span className="text-[11px] text-slate-500">{index + 1}</span>
                <span className="min-w-0 flex-1 text-[12px] text-slate-200">{String(step.title ?? "执行步骤")}</span>
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
      <TimelineCard
        id={message.id}
        icon={<Code2 className="h-4 w-4 text-slate-400" />}
        title={message.title}
        defaultOpen={defaultOpen}
      >
        <div className="flex flex-wrap gap-3 text-[12px] text-slate-400">
          <span className="rounded-[9px] bg-[#10334d] px-3 py-1 font-mono font-semibold text-[#67e8f9]">{toolName}</span>
          {path ? <span className="rounded-[9px] bg-[#162748] px-3 py-1 font-semibold text-[#a3c9ff]">路径: {path}</span> : null}
        </div>
        {summary ? (
          <div className="mt-4 rounded-[12px] border border-[#1d2b4d] bg-[#07101d] px-4 py-3 text-[13px] leading-6 text-slate-100">
            {summary}
          </div>
        ) : null}
        {output ? <pre className="mt-3 max-h-40 overflow-auto whitespace-pre-wrap rounded-md bg-[#07101d] p-3 text-[12px] text-slate-400">{String(output)}</pre> : null}
      </TimelineCard>
    );
  }
  if (message.cardType === "test") {
    return (
      <TimelineCard
        id={message.id}
        icon={<Terminal className="h-4 w-4 text-emerald-300" />}
        title={message.title}
        defaultOpen={defaultOpen}
      >
        <div className="flex items-center gap-2">
          <Badge tone={message.payload.status === "failed" ? "danger" : "success"}>{String(message.payload.status ?? "finished")}</Badge>
          <span className="font-mono text-[12px] text-slate-300">{String(message.payload.command ?? "test run")}</span>
        </div>
      </TimelineCard>
    );
  }
  if (message.cardType === "diff") {
    const files = (message.payload.files as RunDiffFile[] | undefined) ?? [];
    return (
      <TimelineCard
        id={message.id}
        icon={<FolderOpen className="h-4 w-4 text-amber-300" />}
        title={`本次变更涉及 ${files.length} 个文件`}
        defaultOpen={defaultOpen}
      >
        <div className="overflow-hidden rounded-none bg-[#07101d]">
          <div className="grid grid-cols-[minmax(0,1.5fr)_minmax(96px,.6fr)_minmax(96px,.45fr)] border-b border-[#223150] px-5 py-3 text-[13px] font-semibold text-[#a8b9dc]">
            <div>文件</div>
            <div>状态</div>
            <div>行变更</div>
          </div>
          {files.map((file) => (
            <div
              key={file.path}
              className="grid grid-cols-[minmax(0,1.5fr)_minmax(96px,.6fr)_minmax(96px,.45fr)] border-b border-dashed border-[#20304f] px-5 py-2.5 last:border-b-0"
            >
              <div className="min-w-0 truncate font-mono text-[13px] text-slate-100">{file.path}</div>
              <div>
                <Badge tone="warning" className="bg-[rgba(245,158,11,.18)] font-bold uppercase tracking-wide">
                  {file.status.toUpperCase()}
                </Badge>
              </div>
              <div className="font-semibold text-slate-100">{lineChange(file)}</div>
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
      icon={message.cardType === "event" ? <CheckCircle2 className="h-4 w-4 text-cyan-300" /> : <FileText className="h-4 w-4 text-slate-400" />}
      title={message.title}
      defaultOpen={defaultOpen}
    >
      {summary ? (
        <div className="rounded-[12px] border border-[#1d2b4d] bg-[#07101d] px-4 py-3 text-[13px] leading-6 text-slate-100">{summary}</div>
      ) : (
        <pre className="max-h-40 overflow-auto whitespace-pre-wrap rounded-md bg-[#07101d] p-3 text-[12px] text-slate-400">
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
  }, [defaultOpen, id]);

  return (
    <details
      data-testid={`timeline-card-${id}`}
      open={open}
      className="overflow-hidden rounded-[14px] border border-[var(--ta-border)] bg-[#0f1a33]"
    >
      <summary
        className="flex list-none items-center gap-2 border-b border-[var(--ta-border)] bg-[#0c1628] px-4 py-3"
        onClick={(event) => event.preventDefault()}
      >
        {icon}
        <div className="min-w-0 flex-1 truncate text-[14px] font-bold text-slate-100">{title}</div>
        <button
          type="button"
          aria-label={`${open ? "收起" : "展开"} ${title}`}
          className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-[12px] font-semibold text-[#8aa0c6] hover:bg-[#122044] hover:text-slate-100"
          onClick={(event) => {
            event.stopPropagation();
            setOpen((value) => !value);
          }}
        >
          {open ? "收起" : "展开"}
          {open ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
        </button>
      </summary>
      {open ? <div className="p-4">{children}</div> : null}
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
