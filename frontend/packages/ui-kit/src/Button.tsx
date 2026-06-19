"use client";

import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "./lib";

const buttonVariants = cva(
  "inline-flex h-8 shrink-0 items-center justify-center gap-2 rounded-md border px-3 text-[12px] font-medium transition disabled:pointer-events-none disabled:opacity-45",
  {
    variants: {
      variant: {
        primary:
          "border-[#1d4ed8] bg-[linear-gradient(180deg,#2563eb,#1d4ed8)] text-white hover:brightness-110",
        secondary:
          "border-[var(--ta-border)] bg-[#101b33] text-[var(--ta-text)] hover:border-[#2a3a63]",
        ghost:
          "border-transparent bg-transparent text-[var(--ta-muted)] hover:bg-[#122044] hover:text-[var(--ta-text)]",
        danger: "border-[#7f1d1d] bg-[#3b0d0d] text-red-200 hover:bg-[#521414]"
      },
      size: {
        sm: "h-7 px-2",
        md: "h-8 px-3",
        icon: "h-8 w-8 px-0"
      }
    },
    defaultVariants: {
      variant: "secondary",
      size: "md"
    }
  }
);

export type ButtonProps = React.ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof buttonVariants>;

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(({ className, variant, size, ...props }, ref) => (
  <button ref={ref} className={cn(buttonVariants({ variant, size }), className)} {...props} />
));
Button.displayName = "Button";
