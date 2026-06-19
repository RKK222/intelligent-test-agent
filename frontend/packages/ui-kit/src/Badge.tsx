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
    neutral: "bg-[rgba(148,163,184,.14)] text-[#cbd5e1]",
    info: "bg-[rgba(96,165,250,.15)] text-[#8db6f5]",
    success: "bg-[rgba(34,197,94,.14)] text-[#86efac]",
    warning: "bg-[rgba(245,158,11,.15)] text-[#fcd34d]",
    danger: "bg-[rgba(239,68,68,.14)] text-[#fca5a5]"
  }[tone];
  return <span className={cn("inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium", toneClass, className)}>{children}</span>;
}
