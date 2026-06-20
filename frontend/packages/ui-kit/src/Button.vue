<script setup lang="ts">
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "./lib";

// 按钮视觉变体，与原 React 版保持一致的设计 token
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

type ButtonVariants = VariantProps<typeof buttonVariants>;

withDefaults(defineProps<{ variant?: ButtonVariants["variant"]; size?: ButtonVariants["size"] }>(), {
  variant: "secondary",
  size: "md"
});
</script>

<template>
  <!-- 原生 button 属性（disabled/type 等）通过 $attrs 自动透传 -->
  <button :class="cn(buttonVariants({ variant, size }))"><slot /></button>
</template>
