"use client";

import { AlertCircle, CheckCircle2 } from "lucide-react";
import { cn } from "./lib";

export type Feedback = {
  kind: "info" | "success" | "error";
  title: string;
  description?: string;
  traceId?: string;
};

export function FeedbackBanner({ feedback, className }: { feedback?: Feedback | null; className?: string }) {
  if (!feedback) {
    return null;
  }
  const Icon = feedback.kind === "success" ? CheckCircle2 : AlertCircle;
  return (
    <div
      className={cn(
        "flex items-start gap-2 border-t border-slate-800 bg-slate-950 px-3 py-2 text-[12px]",
        feedback.kind === "error" && "border-red-900/70 bg-red-950/30",
        feedback.kind === "success" && "border-emerald-900/70 bg-emerald-950/30",
        className
      )}
    >
      <Icon className="mt-0.5 h-4 w-4 shrink-0" />
      <div className="min-w-0">
        <div className="font-medium text-slate-100">{feedback.title}</div>
        {feedback.description ? <div className="mt-0.5 text-slate-400">{feedback.description}</div> : null}
        {feedback.traceId ? <div className="mt-1 font-mono text-[11px] text-slate-500">traceId: {feedback.traceId}</div> : null}
      </div>
    </div>
  );
}
