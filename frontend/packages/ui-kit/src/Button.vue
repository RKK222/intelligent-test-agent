<script setup lang="ts">
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "./lib";

// 按钮视觉变体，与原 React 版保持一致的设计 token
const buttonVariants = cva(
  "inline-flex h-8 shrink-0 items-center justify-center gap-2 rounded border px-3 text-[12px] font-medium leading-none transition disabled:pointer-events-none disabled:opacity-45",
  {
    variants: {
      variant: {
        primary:
          "border-[var(--ta-ink)] bg-[var(--ta-ink)] text-white hover:bg-[#111111]",
        secondary:
          "border-[var(--ta-border)] bg-[var(--ta-control)] text-[var(--ta-text)] hover:border-[var(--ta-border-strong)] hover:bg-[var(--ta-hover)]",
        ghost:
          "border-transparent bg-transparent text-[var(--ta-muted)] hover:bg-[var(--ta-hover)] hover:text-[var(--ta-text)]",
        danger: "border-[#9e3b34] bg-[#f4e3e1] text-red-200 hover:bg-[#ecc9c5]"
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
