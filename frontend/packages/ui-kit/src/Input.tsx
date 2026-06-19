"use client";

import * as React from "react";
import { cn } from "./lib";

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input
      ref={ref}
      className={cn(
        "h-8 w-full rounded-md border border-[var(--ta-border)] bg-[#101b33] px-2 text-[12px] text-[var(--ta-text)] outline-none transition placeholder:text-[var(--ta-muted)] focus:border-[#2a3a63]",
        className
      )}
      {...props}
    />
  )
);
Input.displayName = "Input";

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea
      ref={ref}
      className={cn(
        "w-full resize-none rounded-md border border-[var(--ta-border)] bg-[#101b33] px-2 py-2 text-[12px] text-[var(--ta-text)] outline-none transition placeholder:text-[var(--ta-muted)] focus:border-[#2a3a63]",
        className
      )}
      {...props}
    />
  )
);
Textarea.displayName = "Textarea";
