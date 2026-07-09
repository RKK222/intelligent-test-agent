<script lang="ts">
import type { RunDiffFile } from "@test-agent/shared-types";

export type DiffSummaryRowProps = {
  files: RunDiffFile[];
};
</script>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { ChevronDown, ChevronRight, FileDiff } from "lucide-vue-next";
import { formatDisplayPath } from "../../state/tool-registry";
import { FileIcon } from "@test-agent/file-explorer";

const props = defineProps<DiffSummaryRowProps>();
const emit = defineEmits<{
  openDiff: [];
  openFile: [path: string];
}>();

const expanded = ref(false);

function getFileName(path: string): string {
  if (!path) return "";
  const normalized = path.replace(/\\/g, "/");
  return normalized.split("/").filter(Boolean).pop() || path;
}

const lineTotals = computed(() =>
  props.files.reduce(
    (totals, file) => ({
      additions: totals.additions + (Number.isFinite(file.additions) ? file.additions : 0),
      deletions: totals.deletions + (Number.isFinite(file.deletions) ? file.deletions : 0)
    }),
    { additions: 0, deletions: 0 }
  )
);
const totalsBumping = ref(false);
const totalsBumpKey = ref(0);
let totalsBumpTimer: ReturnType<typeof setTimeout> | undefined;

function clearTotalsBumpTimer() {
  if (totalsBumpTimer) {
    clearTimeout(totalsBumpTimer);
    totalsBumpTimer = undefined;
  }
}

watch(lineTotals, (next, previous) => {
  if (!previous || expanded.value) return;
  if (next.additions === previous.additions && next.deletions === previous.deletions) return;

  clearTotalsBumpTimer();
  // 折叠态数字变化时重新挂载汇总节点，确保连续变化也会重新触发 CSS 跳动动画。
  totalsBumpKey.value += 1;
  totalsBumping.value = true;
  totalsBumpTimer = setTimeout(() => {
    totalsBumping.value = false;
    totalsBumpTimer = undefined;
  }, 320);
});

onBeforeUnmount(clearTotalsBumpTimer);
</script>

<template>
  <section class="oc-diff-summary" data-testid="oc-diff-summary">
    <button
      type="button"
      class="oc-diff-summary__header"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <span class="oc-diff-summary__title">
        <FileDiff class="oc-tool__icon" />
        文件修改 {{ files.length }}
      </span>
      <span
        :key="totalsBumpKey"
        :class="['oc-diff-summary__totals', totalsBumping ? 'is-bumping' : '']"
        aria-label="文件总增减行"
      >
        <span class="oc-diff-line is-add">+{{ lineTotals.additions }}</span>
        <span class="oc-diff-line is-del">-{{ lineTotals.deletions }}</span>
      </span>
      <ChevronDown v-if="expanded" class="oc-tool__chevron" />
      <ChevronRight v-else class="oc-tool__chevron" />
    </button>
    <div v-if="expanded" class="oc-diff-summary__files">
      <div
        v-for="file in files"
        :key="file.path"
        class="oc-diff-file is-clickable"
        @click="emit('openFile', file.path)"
      >
        <span class="oc-diff-file__path-wrapper">
          <FileIcon :entry="{ name: getFileName(file.path), path: file.path, type: 'file' }" class="oc-diff-file__icon" />
          <span class="oc-diff-file__path" :title="file.path">{{ getFileName(file.path) }}</span>
        </span>
        <span class="oc-diff-line is-add">+{{ file.additions }}</span>
        <span class="oc-diff-line is-del">-{{ file.deletions }}</span>
      </div>
    </div>
  </section>
</template>
