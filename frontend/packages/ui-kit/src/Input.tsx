"use client";

import * as React from "react";
import { cn } from "./lib";

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => (
    <input
      ref={ref}
      className={cn(
        "h-8 w-full rounded-md border border-slate-700 bg-slate-950 px-2 text-[12px] text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-blue-500",
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
        "w-full resize-none rounded-md border border-slate-700 bg-slate-950 px-2 py-2 text-[12px] text-slate-100 outline-none transition placeholder:text-slate-500 focus:border-blue-500",
        className
      )}
      {...props}
    />
  )
);
Textarea.displayName = "Textarea";
