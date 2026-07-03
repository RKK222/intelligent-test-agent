<script lang="ts">
import type { RunDiffFile } from "@test-agent/shared-types";

export type DiffSummaryRowProps = {
  files: RunDiffFile[];
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronDown, ChevronRight, FileDiff } from "lucide-vue-next";
import { formatDisplayPath } from "../../state/tool-registry";

const props = defineProps<DiffSummaryRowProps>();
const emit = defineEmits<{
  openDiff: [];
  openFile: [path: string];
}>();

const expanded = ref(false);
const lineTotals = computed(() =>
  props.files.reduce(
    (totals, file) => ({
      additions: totals.additions + (Number.isFinite(file.additions) ? file.additions : 0),
      deletions: totals.deletions + (Number.isFinite(file.deletions) ? file.deletions : 0)
    }),
    { additions: 0, deletions: 0 }
  )
);
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
      <span class="oc-diff-summary__totals" aria-label="文件总增减行">
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
        <span class="oc-diff-file__path" :title="file.path">{{ formatDisplayPath(file.path) ?? file.path }}</span>
        <span class="oc-diff-line is-add">+{{ file.additions }}</span>
        <span class="oc-diff-line is-del">-{{ file.deletions }}</span>
      </div>
    </div>
  </section>
</template>
