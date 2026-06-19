"use client";

import { RotateCcw, Square } from "lucide-react";
import type { Run } from "@test-agent/shared-types";
import { Badge, Button } from "@test-agent/ui-kit";

export type TestRunnerPanelProps = {
  run?: Run | null;
  logs: string[];
  onCancel: () => void;
  onRetry: () => void;
};

export function TestRunnerPanel({ run, logs, onCancel, onRetry }: TestRunnerPanelProps) {
  const running = run?.status === "RUNNING" || run?.status === "CANCELLING";
  return (
    <div className="flex h-full min-h-0 flex-col bg-[var(--ta-panel)]">
      <div className="flex h-10 items-center gap-2 border-b border-slate-800 bg-slate-950 px-3">
        <div className="min-w-0 flex-1 text-[12px] font-semibold text-slate-200">运行</div>
        <Badge tone={run?.status === "FAILED" ? "danger" : run?.status === "SUCCEEDED" ? "success" : running ? "info" : "neutral"}>
          {run?.status ?? "IDLE"}
        </Badge>
        <Button size="sm" variant="secondary" disabled={!running} onClick={onCancel}>
          <Square className="h-3.5 w-3.5" />
          取消
        </Button>
        <Button size="sm" variant="secondary" onClick={onRetry}>
          <RotateCcw className="h-3.5 w-3.5" />
          重试
        </Button>
      </div>
      <pre className="min-h-0 flex-1 overflow-auto whitespace-pre-wrap p-3 font-mono text-[12px] leading-6 text-slate-300">
        {logs.length ? logs.join("\n") : "等待运行输出..."}
      </pre>
    </div>
  );
}
