<!-- 作废说明：旧气泡消息 part 渲染路径已被 opencode-like/OpencodeTimeline 取代；仅为历史兼容保留，不再扩展新能力。 -->
<script lang="ts">
import type { MessagePart } from "@test-agent/shared-types";

export type PatchBlockProps = {
  part: Extract<MessagePart, { type: "patch" }>;
};
</script>

<script setup lang="ts">
// 文件修改 part：
// - 头部展示 hash 完整值与一键复制按钮，鼠标悬停才显示完整 hash，避免长字符串撑开时间线
// - 文件列表支持展开查看 patch 内容（来自 metadata.filesMap[path]），用于不开 Diff Viewer 时也能看到 diff
// - 没有 patch 内容时退化为仅显示文件路径与 +/– 行数（如果后端在 metadata 里带了 stats）
import { computed, ref } from "vue";
import { Check, Copy, FileDiff } from "lucide-vue-next";
import ProcessDisclosure from "./ProcessDisclosure.vue";
import { PART_META } from "./part-meta";
import { normalizeProcessStatus } from "./process-status";

const props = defineProps<PatchBlockProps>();
const meta = PART_META.patch;
const status = computed(() => normalizeProcessStatus("completed"));
const summary = computed(() => `${props.part.files.length} 个文件`);
// patch metadata.filesMap 是 path → unified diff 的映射；reducer 在 message.part.updated 里挂进来
const filesMap = computed<Record<string, string>>(() => props.part.metadata?.filesMap ?? {});
const fileStats = computed<Record<string, { additions?: number; deletions?: number }>>(() => props.part.metadata?.fileStats ?? {});
const expanded = ref<Record<string, boolean>>({});
const copied = ref(false);

async function copyHash() {
  if (!props.part.hash) return;
  try {
    await navigator.clipboard.writeText(props.part.hash);
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, 1500);
  } catch {
    // 剪贴板不可用时不抛错，留给后续 button 状态自动恢复
  }
}

function toggleFile(path: string) {
  expanded.value = { ...expanded.value, [path]: !expanded.value[path] };
}

function additionsFor(path: string): number | undefined {
  return fileStats.value[path]?.additions;
}

function deletionsFor(path: string): number | undefined {
  return fileStats.value[path]?.deletions;
}
</script>

<template>
  <ProcessDisclosure
    :id="part.partId"
    :test-id="`patch-part-${part.partId}`"
    :title="meta.label"
    :status="status"
    status-kind="task"
    accent="ok"
    :summary="summary"
    :default-open="false"
  >
    <div class="space-y-1.5 text-[12px] leading-5 text-[var(--ta-chat-muted)]">
      <div class="flex flex-wrap items-center gap-2">
        <FileDiff class="h-3.5 w-3.5 text-[var(--ta-chat-subtle)]" />
        <span
          class="rounded border border-[var(--ta-chat-border-strong)] bg-[var(--ta-chat-chip-bg)] px-1.5 py-0.5 font-mono text-[11px] text-[var(--ta-chat-text)]"
          :title="part.hash || '无可用 hash'"
        >
          {{ part.hash ? `${part.hash.slice(0, 12)}${part.hash.length > 12 ? '…' : ''}` : 'no-hash' }}
        </span>
        <button
          v-if="part.hash"
          type="button"
          class="inline-flex items-center gap-1 rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)] px-1.5 py-0.5 text-[10px] text-[var(--ta-chat-muted)] hover:border-[var(--ta-chat-border-strong)] hover:text-[var(--ta-chat-text)]"
          :title="`复制完整 hash（${part.hash.length} 字符）`"
          @click="copyHash"
        >
          <Check v-if="copied" class="h-3 w-3 text-[var(--ta-chat-status-done)]" />
          <Copy v-else class="h-3 w-3" />
          {{ copied ? "已复制" : "复制 hash" }}
        </button>
      </div>
      <ul class="space-y-1">
        <li
          v-for="file in part.files"
          :key="file"
          :data-testid="`patch-file-${file}`"
          class="rounded border border-[var(--ta-chat-border)] bg-[var(--ta-chat-surface)]"
        >
          <button
            v-if="filesMap[file]"
            type="button"
            class="flex w-full items-center gap-2 px-2 py-1 text-left hover:bg-[var(--ta-chat-hover)]"
            @click="toggleFile(file)"
          >
            <span class="min-w-0 flex-1 truncate font-mono text-[11px] text-[var(--ta-chat-text)]">{{ file }}</span>
            <span
              v-if="typeof additionsFor(file) === 'number'"
              class="shrink-0 rounded px-1 text-[10px] text-[#3f7a5a]"
            >+{{ additionsFor(file) }}</span>
            <span
              v-if="typeof deletionsFor(file) === 'number'"
              class="shrink-0 rounded px-1 text-[10px] text-[#9e3b34]"
            >-{{ deletionsFor(file) }}</span>
            <span class="shrink-0 text-[10px] text-[var(--ta-chat-muted)]">
              {{ expanded[file] ? "收起" : "展开" }}
            </span>
          </button>
          <div
            v-else
            class="flex items-center gap-2 px-2 py-1"
          >
            <span class="min-w-0 flex-1 truncate font-mono text-[11px] text-[var(--ta-chat-text)]">{{ file }}</span>
            <span
              v-if="typeof additionsFor(file) === 'number'"
              class="shrink-0 rounded px-1 text-[10px] text-[#3f7a5a]"
            >+{{ additionsFor(file) }}</span>
            <span
              v-if="typeof deletionsFor(file) === 'number'"
              class="shrink-0 rounded px-1 text-[10px] text-[#9e3b34]"
            >-{{ deletionsFor(file) }}</span>
          </div>
          <pre
            v-if="expanded[file] && filesMap[file]"
            :data-testid="`patch-file-diff-${file}`"
            class="max-h-60 overflow-auto whitespace-pre border-t border-[var(--ta-chat-border)] bg-[var(--ta-chat-detail-bg)] p-2 font-mono text-[11px] leading-[1.55] text-[var(--ta-chat-muted)]"
          >{{ filesMap[file] }}</pre>
        </li>
      </ul>
      <p v-if="part.files.length === 0" class="text-[11px] text-[var(--ta-chat-muted)]">未列出受影响文件</p>
    </div>
  </ProcessDisclosure>
</template>
