<script lang="ts">
import type * as monaco from "monaco-editor";
import type { PromptPart, RunDiffFile } from "@test-agent/shared-types";
import type { Feedback } from "@test-agent/ui-kit";

export type DiffViewerProps = {
  files: RunDiffFile[];
  selectedPath?: string;
  source?: "run" | "session" | "vcs" | "agent";
  viewMode?: "split" | "unified";
  accepting?: boolean;
  rejecting?: boolean;
  feedback?: Feedback | null;
};

type FilePromptPart = Extract<PromptPart, { type: "file" }>;
</script>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import { Check, ChevronDown, ChevronUp, MessageSquareQuote, RotateCcw } from "lucide-vue-next";
import { Badge, Button, FeedbackBanner, cn } from "@test-agent/ui-kit";
import { hunkToPromptPart, parseDiffHunks, selectAdjacentHunk } from "./hunks";
import { parseUnifiedPatch } from "./unifiedPatch";

const props = withDefaults(defineProps<DiffViewerProps>(), {
  source: "run",
  viewMode: "split"
});
const emit = defineEmits<{
  selectFile: [path: string];
  sourceChange: [source: "run" | "session" | "vcs" | "agent"];
  viewModeChange: [mode: "split" | "unified"];
  refresh: [];
  acceptRun: [];
  rejectRun: [];
  currentFileFeedback: [action: "accept-current" | "reject-current", path: string];
  useHunkContext: [part: FilePromptPart];
}>();

const selected = computed(() => props.files.find((f) => f.path === props.selectedPath) ?? props.files[0]);
const parsed = computed(() => parseUnifiedPatch(selected.value?.patch ?? ""));
const hunks = computed(() => (selected.value ? parseDiffHunks(selected.value) : []));
const selectedHunkIndex = ref(0);
const selectedHunk = computed(() => hunks.value[selectedHunkIndex.value] ?? hunks.value[0]);

const containerEl = ref<HTMLElement | null>(null);
const diffEditor = shallowRef<monaco.editor.IDiffEditor | null>(null);
let originalModel: monaco.editor.ITextModel | null = null;
let modifiedModel: monaco.editor.ITextModel | null = null;
let monacoLib: typeof monaco | null = null;

function diffLanguage(path?: string): string {
  return path?.endsWith(".py") ? "python" : "typescript";
}

function sourceTitle(source: "run" | "session" | "vcs" | "agent") {
  if (source === "session") return "Session Diff";
  if (source === "vcs") return "VCS Diff";
  if (source === "agent") return "Agent Config Diff";
  return "Run Diff";
}

// 切换文件时重置 hunk 选择
watch(
  () => [selected.value?.path, selected.value?.patch],
  () => {
    selectedHunkIndex.value = 0;
  }
);
watch(
  () => hunks.value.length,
  (len) => {
    if (selectedHunkIndex.value >= len) {
      selectedHunkIndex.value = 0;
    }
  }
);

// 选中 hunk 变化时滚动到对应位置
watch(selectedHunk, (hunk) => {
  if (!hunk || !diffEditor.value) return;
  const modified = diffEditor.value.getModifiedEditor();
  const lineNumber = Math.max(1, hunk.newStart);
  modified.revealLineInCenter(lineNumber);
  modified.setPosition({ lineNumber, column: 1 });
});

function moveHunk(direction: "previous" | "next") {
  const next = selectAdjacentHunk(hunks.value, selectedHunkIndex.value, direction);
  if (next) {
    selectedHunkIndex.value = next.index;
  }
}

async function initMonaco(el: HTMLElement) {
  if (diffEditor.value) return;
  const mod = await import("./monaco-env");
  monacoLib = mod.monaco;
  const lang = diffLanguage(selected.value?.path);
  originalModel = monacoLib.editor.createModel(parsed.value.original, lang);
  modifiedModel = monacoLib.editor.createModel(parsed.value.modified, lang);
  const inst = monacoLib.editor.createDiffEditor(el, {
    readOnly: true,
    renderSideBySide: props.viewMode === "split",
    minimap: { enabled: false },
    automaticLayout: true,
    scrollBeyondLastLine: false,
    fontSize: 13,
    lineHeight: 21
  });
  inst.setModel({ original: originalModel, modified: modifiedModel });
  diffEditor.value = inst;
}

function disposeMonaco() {
  diffEditor.value?.dispose();
  originalModel?.dispose();
  modifiedModel?.dispose();
  diffEditor.value = null;
  originalModel = null;
  modifiedModel = null;
}

watch(
  containerEl,
  (el) => {
    if (el) {
      initMonaco(el);
    } else {
      disposeMonaco();
    }
  },
  { immediate: true }
);

// patch 变化时更新模型内容
watch(
  parsed,
  (val) => {
    if (!originalModel || !modifiedModel) return;
    if (originalModel.getValue() !== val.original) originalModel.setValue(val.original);
    if (modifiedModel.getValue() !== val.modified) modifiedModel.setValue(val.modified);
  }
);

// 文件语言变化时重建模型语言
watch(
  () => selected.value?.path,
  (path) => {
    if (!monacoLib || !originalModel || !modifiedModel) return;
    const lang = diffLanguage(path);
    monacoLib.editor.setModelLanguage(originalModel, lang);
    monacoLib.editor.setModelLanguage(modifiedModel, lang);
  }
);

watch(
  () => props.viewMode,
  (mode) => {
    diffEditor.value?.updateOptions({ renderSideBySide: mode === "split" });
  }
);

onBeforeUnmount(() => {
  disposeMonaco();
});
</script>

<template>
  <div v-if="!files.length" class="flex h-full min-h-0 flex-col bg-[var(--ta-panel-2)] text-slate-500">
    <div class="flex items-center gap-1 px-3 py-1">
      <select :value="source" class="h-8 rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200" @change="emit('sourceChange', ($event.target as HTMLSelectElement).value as 'run' | 'session' | 'vcs' | 'agent')">
        <option value="run">Run</option>
        <option value="session">Session</option>
        <option value="vcs">VCS</option>
        <option value="agent">Agent Config</option>
      </select>
      <select :value="viewMode" class="h-8 rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200" @change="emit('viewModeChange', ($event.target as HTMLSelectElement).value as 'split' | 'unified')">
        <option value="split">Split</option>
        <option value="unified">Unified</option>
      </select>
      <Button size="sm" variant="secondary" @click="emit('refresh')">刷新</Button>
    </div>
    <div class="flex flex-1 items-center justify-center text-center text-[12px]">暂无 Diff</div>
  </div>
  <div v-else class="flex h-full min-h-0 flex-col bg-[var(--ta-panel-2)]">
    <div class="flex min-h-10 flex-wrap items-center gap-2 border-b border-slate-800 bg-slate-950 px-3 py-1">
      <div v-if="source !== 'vcs' && source !== 'agent'" class="flex items-center gap-1">
        <select :value="source" class="h-8 rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200" @change="emit('sourceChange', ($event.target as HTMLSelectElement).value as 'run' | 'session' | 'vcs' | 'agent')">
          <option value="run">Run</option>
          <option value="session">Session</option>
          <option value="vcs">VCS</option>
          <option value="agent">Agent Config</option>
        </select>
        <select :value="viewMode" class="h-8 rounded border border-slate-800 bg-slate-950 px-2 text-[12px] text-slate-200" @change="emit('viewModeChange', ($event.target as HTMLSelectElement).value as 'split' | 'unified')">
          <option value="split">Split</option>
          <option value="unified">Unified</option>
        </select>
        <Button size="sm" variant="secondary" @click="emit('refresh')">刷新</Button>
      </div>
      <div v-if="source !== 'vcs' && source !== 'agent'" class="min-w-0 flex-1 truncate text-[12px] font-semibold text-slate-200">{{ sourceTitle(source) }}</div>
      <div v-else class="min-w-0 flex-1" />
      <div class="flex items-center gap-1">
        <Button type="button" size="icon" variant="secondary" title="上一处 hunk" :disabled="hunks.length === 0" @click="moveHunk('previous')">
          <ChevronUp class="h-4 w-4" />
        </Button>
        <Button type="button" size="icon" variant="secondary" title="下一处 hunk" :disabled="hunks.length === 0" @click="moveHunk('next')">
          <ChevronDown class="h-4 w-4" />
        </Button>
        <span class="min-w-[52px] text-center font-mono text-[11px] text-slate-500">
          {{ selectedHunk ? `${selectedHunk.index + 1}/${hunks.length}` : "0/0" }}
        </span>
        <Button v-if="source !== 'vcs' && source !== 'agent'" type="button" size="icon" variant="secondary" title="引用 hunk" :disabled="!selected || !selectedHunk" @click="selected && selectedHunk && emit('useHunkContext', hunkToPromptPart(selected, selectedHunk))">
          <MessageSquareQuote class="h-4 w-4" />
        </Button>
      </div>
      <template v-if="source !== 'vcs' && source !== 'agent'">
        <Button size="sm" variant="secondary" :disabled="!selected" @click="selected && emit('currentFileFeedback', 'accept-current', selected.path)">当前文件接受</Button>
        <Button size="sm" variant="secondary" :disabled="!selected" @click="selected && emit('currentFileFeedback', 'reject-current', selected.path)">当前文件拒绝</Button>
        <Button size="sm" variant="primary" :disabled="accepting" @click="emit('acceptRun')">
          <Check class="h-4 w-4" />
          接受全部
        </Button>
        <Button size="sm" variant="danger" :disabled="rejecting" @click="emit('rejectRun')">
          <RotateCcw class="h-4 w-4" />
          拒绝全部
        </Button>
      </template>
    </div>
    <div :class="cn('grid min-h-0 flex-1', (source === 'vcs' || source === 'agent') ? 'grid-cols-1' : 'grid-cols-[260px_minmax(0,1fr)]')">
      <div v-if="source !== 'vcs' && source !== 'agent'" class="min-h-0 overflow-auto border-r border-slate-800 bg-[var(--ta-panel)] p-2">
        <button
          v-for="file in files"
          :key="file.path"
          type="button"
          :class="cn(
            'mb-1 flex w-full items-center gap-2 rounded-md border border-transparent px-2 py-2 text-left hover:bg-[#e7e9ed]',
            selected?.path === file.path && 'border-[#2f4a8f] bg-[rgba(96,165,250,.12)]'
          )"
          @click="emit('selectFile', file.path)"
        >
          <Badge :tone="file.status === 'deleted' ? 'danger' : file.status === 'added' ? 'success' : 'warning'">{{ file.status }}</Badge>
          <span class="min-w-0 flex-1 truncate font-mono text-[12px] text-slate-200">{{ file.path }}</span>
        </button>
        <div v-if="hunks.length" class="mt-3 border-t border-slate-800 pt-2">
          <div class="mb-1 px-1 text-[11px] font-semibold uppercase text-slate-500">Hunks</div>
          <button
            v-for="hunk in hunks"
            :key="`${hunk.filePath}:${hunk.index}`"
            type="button"
            :class="cn(
              'mb-1 w-full rounded-md border border-transparent px-2 py-1.5 text-left text-[11px] hover:bg-[#e7e9ed]',
              selectedHunk?.index === hunk.index && 'border-[rgba(34,211,238,.4)] bg-[rgba(34,211,238,.08)]'
            )"
            @click="selectedHunkIndex = hunk.index"
          >
            <div class="font-mono text-slate-200">+{{ hunk.newStart }},{{ hunk.newLines }}</div>
            <div class="truncate text-slate-500">{{ hunk.heading || hunk.patch.split('\n')[0] }}</div>
          </button>
        </div>
      </div>
      <div ref="containerEl" class="min-w-0" />
    </div>
    <FeedbackBanner :feedback="feedback" />
  </div>
</template>
