"use client";

import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "./lib";

const buttonVariants = cva(
  "inline-flex h-8 shrink-0 items-center justify-center gap-2 rounded-md border px-3 text-[12px] font-medium transition disabled:pointer-events-none disabled:opacity-45",
  {
    variants: {
      variant: {
        primary: "border-blue-600 bg-blue-600 text-white hover:bg-blue-500",
        secondary: "border-slate-700 bg-slate-900 text-slate-100 hover:border-slate-500",
        ghost: "border-transparent bg-transparent text-slate-300 hover:bg-slate-800",
        danger: "border-red-700 bg-red-950 text-red-200 hover:bg-red-900"
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
