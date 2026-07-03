<!-- 作废说明：旧气泡/结构化工具详情路径已被 opencode-like 的 tool 视图取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
import type { ProcessStatusKind } from "./process-status";

export type ToolDetailProps = {
  label: string;
  status: unknown;
  purpose?: string;
  input?: Record<string, unknown>;
  output?: unknown;
  metadata?: Record<string, unknown>;
  path?: string;
  statusKind: "tool" | "skill";
  startedAt?: string;
  endedAt?: string;
};
</script>

<script setup lang="ts">
import { computed, ref, onMounted } from "vue";
import { normalizeProcessStatus, statusLabel, statusToneClass, textValue } from "./process-status";
import { formatTime } from "./chat-utils";
import hljs from "highlight.js";

const props = defineProps<ToolDetailProps>();
const normalizedStatus = computed(() => normalizeProcessStatus(props.status));
const metaPurpose = computed(
  () => textValue(props.metadata?.purpose) ?? textValue(props.metadata?.summary) ?? textValue(props.metadata?.description)
);
const hasInput = computed(() => Boolean(props.input && Object.keys(props.input).length));

// 检测 read 类工具输出中的文件内容
const fileContent = computed<{ path: string; content: string; language: string } | null>(() => {
  const output = props.output;
  if (typeof output !== "string") return null;
  if (!output.includes("<path>") || !output.includes("<content>")) return null;

  const pathMatch = output.match(/<path>(.+?)<\/path>/);
  const contentMatch = output.match(/<content>([\s\S]*?)<\/content>/);
  if (!pathMatch || !contentMatch) return null;

  const filePath = pathMatch[1].trim();
  // 去除行号前缀 "N: "
  const rawContent = contentMatch[1].replace(/^\d+:\s?/gm, "").trim();
  // 从文件扩展名推断语言
  const ext = filePath.split(".").pop()?.toLowerCase() ?? "";
  const langMap: Record<string, string> = {
    js: "javascript", ts: "typescript", jsx: "javascript", tsx: "typescript",
    vue: "vue", html: "xml", css: "css", scss: "scss", less: "less",
    java: "java", kt: "kotlin", py: "python", rb: "ruby", go: "go",
    rs: "rust", cpp: "cpp", c: "c", h: "c", cs: "csharp",
    php: "php", swift: "swift", md: "markdown", json: "json",
    yml: "yaml", yaml: "yaml", xml: "xml", sql: "sql", sh: "bash",
    bash: "bash", zsh: "bash", dockerfile: "dockerfile", conf: "ini",
    ini: "ini", toml: "ini", gradle: "groovy", mjs: "javascript",
    cjs: "javascript", mts: "typescript", cts: "typescript",
  };
  return {
    path: filePath,
    content: rawContent,
    language: langMap[ext] ?? ext,
  };
});

const highlighted = ref("");
onMounted(() => {
  const fc = fileContent.value;
  if (fc) {
    try {
      highlighted.value = hljs.highlight(fc.content, { language: fc.language }).value;
    } catch {
      highlighted.value = hljs.highlightAuto(fc.content).value;
    }
  }
});

const outputDisplay = computed(() => {
  if (props.output === undefined || props.output === null) return undefined;
  if (typeof props.output === "string") return props.output;
  try {
    return JSON.stringify(props.output, null, 2);
  } catch {
    return String(props.output);
  }
});
</script>

<template>
  <div class="space-y-2 text-[12px] leading-5 text-[var(--ta-chat-muted)]">
    <div class="flex flex-wrap items-center gap-2">
      <span class="rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-2 py-0.5 font-mono text-[11px] text-[var(--ta-chat-text)]">
        {{ label }}
      </span>
      <span :class="['rounded-full border px-1.5 py-0.5 text-[10px]', statusToneClass(normalizedStatus)]">
        {{ statusLabel(normalizedStatus, statusKind as ProcessStatusKind) }}
      </span>
      <span v-if="path">路径: {{ path }}</span>
      <span v-if="startedAt">开始: {{ formatTime(startedAt) }}</span>
      <span v-if="endedAt">结束: {{ formatTime(endedAt) }}</span>
    </div>
    <div v-if="purpose || metaPurpose" class="whitespace-pre-wrap">{{ purpose ?? metaPurpose }}</div>
    <pre
      v-if="hasInput"
      class="max-h-28 overflow-auto rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 text-[11px]"
    >{{ JSON.stringify(input, null, 2) }}</pre>

    <!-- 文件内容查看器：read 工具的输出美化展示 -->
    <div
      v-if="fileContent"
      class="overflow-hidden rounded border border-[var(--ta-chat-border)]"
    >
      <div class="flex items-center gap-2 border-b border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] px-3 py-1.5 text-[11px] text-[var(--ta-chat-muted)]">
        <svg class="h-3.5 w-3.5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
        </svg>
        <span class="truncate font-mono">{{ fileContent.path }}</span>
        <span class="ml-auto text-[10px] uppercase opacity-60">{{ fileContent.language }}</span>
      </div>
      <pre
        class="m-0 max-h-80 overflow-auto p-3 text-[12px] leading-5"
        style="background: #1e1e1e; color: #d4d4d4;"
      ><code v-html="highlighted" /></pre>
    </div>

    <!-- 普通工具输出 -->
    <pre
      v-else-if="outputDisplay"
      class="max-h-36 overflow-auto whitespace-pre-wrap rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 text-[11px]"
    >{{ outputDisplay }}</pre>
  </div>
</template>
