import type { Component } from "vue";
import {
  Bot,
  Camera,
  CheckCircle2,
  FileDiff,
  FileText,
  GitBranch,
  Minimize2,
  PlayCircle,
  RefreshCw
} from "lucide-vue-next";
import type { MessagePart } from "@test-agent/shared-types";

// 色条家族：用左侧 2px hairline 的颜色编码 part 家族，把一条助手消息串成有类型的操作时间线
export type PartAccent = "neutral" | "ok" | "warn" | "error" | "muted";

export type MessagePartType = MessagePart["type"];

export type PartMeta = {
  icon: Component;
  label: string;
  accent: PartAccent;
};

// 类型 → 图标 / 中文标签 / 色条家族 的集中映射，避免长列表里重复 class 计算
export const PART_META: Record<MessagePartType, PartMeta> = {
  text: { icon: FileText, label: "最终回答", accent: "neutral" },
  reasoning: { icon: PlayCircle, label: "思考状态", accent: "neutral" },
  tool: { icon: FileText, label: "能力调用", accent: "neutral" },
  file: { icon: FileText, label: "文件", accent: "ok" },
  subtask: { icon: GitBranch, label: "子任务", accent: "neutral" },
  "step-start": { icon: PlayCircle, label: "步骤开始", accent: "muted" },
  "step-finish": { icon: CheckCircle2, label: "步骤完成", accent: "ok" },
  snapshot: { icon: Camera, label: "消息快照", accent: "muted" },
  patch: { icon: FileDiff, label: "文件修改", accent: "ok" },
  agent: { icon: Bot, label: "Agent", accent: "muted" },
  retry: { icon: RefreshCw, label: "重试", accent: "warn" },
  compaction: { icon: Minimize2, label: "上下文压缩", accent: "muted" },
  event: { icon: FileText, label: "事件", accent: "muted" }
};

// 把色条家族映射为左侧 border 颜色的 class（沿用 --ta-chat-* token，不引入新色）
export function accentBorderClass(accent: PartAccent): string {
  switch (accent) {
    case "ok":
      return "border-l-2 border-l-[var(--ta-chat-status-done)]";
    case "warn":
      return "border-l-2 border-l-[var(--ta-warn)]";
    case "error":
      return "border-l-2 border-l-[var(--ta-chat-status-error)]";
    case "muted":
      return "border-l-2 border-l-[var(--ta-chat-muted)]";
    case "neutral":
    default:
      return "border-l-2 border-l-[var(--ta-chat-border-strong)]";
  }
}

// 内联标记行左侧色条用的纯色 class（不带 border-l-2，由调用方控制边框宽度）
export function accentColorVar(accent: PartAccent): string {
  switch (accent) {
    case "ok":
      return "var(--ta-chat-status-done)";
    case "warn":
      return "var(--ta-warn)";
    case "error":
      return "var(--ta-chat-status-error)";
    case "muted":
      return "var(--ta-chat-muted)";
    case "neutral":
    default:
      return "var(--ta-chat-border-strong)";
  }
}
