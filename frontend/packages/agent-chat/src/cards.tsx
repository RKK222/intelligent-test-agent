"use client";

import { CheckCircle2, Code2, GitCompare, ListChecks, Terminal } from "lucide-react";
import type * as React from "react";
import type { AgentMessage, RunDiffFile } from "@test-agent/shared-types";
import { Badge, Button } from "@test-agent/ui-kit";

export function AgentCard({ message, onOpenDiff }: { message: Extract<AgentMessage, { role: "card" }>; onOpenDiff: () => void }) {
  if (message.cardType === "plan") {
    const steps = arrayOfRecords(message.payload.steps);
    return (
      <CardShell icon={<ListChecks className="h-4 w-4 text-cyan-300" />} title={message.title}>
        <div className="space-y-1">
          {steps.map((step, index) => (
            <div key={`${step.title}-${index}`} className="rounded-md border border-slate-800 bg-slate-950 px-2 py-1.5">
              <div className="flex items-center gap-2">
                <span className="text-[11px] text-slate-500">{index + 1}</span>
                <span className="min-w-0 flex-1 text-[12px] text-slate-200">{String(step.title ?? "执行步骤")}</span>
                <Badge tone={step.status === "done" ? "success" : step.status === "active" ? "info" : "neutral"}>{String(step.status ?? "pending")}</Badge>
              </div>
            </div>
          ))}
        </div>
      </CardShell>
    );
  }
  if (message.cardType === "tool") {
    return (
      <CardShell icon={<Code2 className="h-4 w-4 text-blue-300" />} title={message.title}>
        <div className="flex flex-wrap gap-2 text-[11px] text-slate-400">
          <Badge tone="info">{String(message.payload.toolName ?? message.payload.rawType ?? "tool")}</Badge>
          {message.payload.path ? <span className="font-mono">{String(message.payload.path)}</span> : null}
        </div>
        <div className="mt-2 rounded-md border border-slate-800 bg-slate-950 p-2 text-[12px] text-slate-300">
          {String(message.payload.summary ?? message.payload.status ?? "工具调用已更新")}
        </div>
      </CardShell>
    );
  }
  if (message.cardType === "test") {
    return (
      <CardShell icon={<Terminal className="h-4 w-4 text-emerald-300" />} title={message.title}>
        <div className="flex items-center gap-2">
          <Badge tone={message.payload.status === "failed" ? "danger" : "success"}>{String(message.payload.status ?? "finished")}</Badge>
          <span className="font-mono text-[12px] text-slate-300">{String(message.payload.command ?? "test run")}</span>
        </div>
      </CardShell>
    );
  }
  if (message.cardType === "diff") {
    const files = (message.payload.files as RunDiffFile[] | undefined) ?? [];
    return (
      <CardShell icon={<GitCompare className="h-4 w-4 text-amber-300" />} title={message.title}>
        <div className="space-y-1">
          {files.slice(0, 4).map((file) => (
            <div key={file.path} className="flex items-center gap-2 rounded-md border border-slate-800 bg-slate-950 px-2 py-1.5">
              <Badge tone="warning">{file.status}</Badge>
              <span className="min-w-0 flex-1 truncate font-mono text-[12px] text-slate-200">{file.path}</span>
              <span className="text-[11px] text-emerald-300">+{file.additions}</span>
              <span className="text-[11px] text-red-300">-{file.deletions}</span>
            </div>
          ))}
        </div>
        <Button className="mt-2" size="sm" variant="primary" onClick={onOpenDiff}>
          查看 Diff
        </Button>
      </CardShell>
    );
  }
  return (
    <CardShell icon={<CheckCircle2 className="h-4 w-4 text-slate-400" />} title={message.title}>
      <pre className="max-h-40 overflow-auto whitespace-pre-wrap text-[12px] text-slate-400">{JSON.stringify(message.payload, null, 2)}</pre>
    </CardShell>
  );
}

function CardShell({ icon, title, children }: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return (
    <div className="overflow-hidden rounded-md border border-slate-800 bg-slate-900">
      <div className="flex items-center gap-2 border-b border-slate-800 bg-slate-950 px-3 py-2">
        {icon}
        <div className="min-w-0 flex-1 truncate text-[12px] font-semibold text-slate-100">{title}</div>
      </div>
      <div className="p-3">{children}</div>
    </div>
  );
}

function arrayOfRecords(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null) : [];
}
