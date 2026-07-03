<script lang="ts">
import type { RunDiffFile } from "@test-agent/shared-types";

export type DiffSummaryRowProps = {
  files: RunDiffFile[];
};
</script>

<script setup lang="ts">
import { FileDiff } from "lucide-vue-next";
import { formatDisplayPath } from "../../state/tool-registry";

const props = defineProps<DiffSummaryRowProps>();
const emit = defineEmits<{
  openDiff: [];
  openFile: [path: string];
}>();

function handleHeaderClick() {
  if (props.files.length === 1) {
    emit("openFile", props.files[0].path);
  } else {
    emit("openDiff");
  }
}
</script>

<template>
  <section class="oc-diff-summary" data-testid="oc-diff-summary">
    <button type="button" class="oc-diff-summary__header" @click="handleHeaderClick">
      <span class="oc-diff-summary__title">
        <FileDiff class="oc-tool__icon" />
        文件修改 {{ files.length }}
      </span>
      <span class="oc-diff-summary__action">查看文件</span>
    </button>
    <div class="oc-diff-summary__files">
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
