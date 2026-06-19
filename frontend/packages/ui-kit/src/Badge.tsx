"use client";

import type * as React from "react";
import { cn } from "./lib";

export function Badge({
  children,
  tone = "neutral",
  className
}: {
  children: React.ReactNode;
  tone?: "neutral" | "info" | "success" | "warning" | "danger";
  className?: string;
}) {
  const toneClass = {
    neutral: "bg-slate-800 text-slate-300",
    info: "bg-blue-950 text-blue-200",
    success: "bg-emerald-950 text-emerald-200",
    warning: "bg-amber-950 text-amber-200",
    danger: "bg-red-950 text-red-200"
  }[tone];
  return <span className={cn("inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium", toneClass, className)}>{children}</span>;
}
