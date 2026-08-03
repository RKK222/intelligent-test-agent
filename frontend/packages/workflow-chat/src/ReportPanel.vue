<script setup lang="ts">
import type { WorkflowReport } from "@test-agent/workflow-api-client";
import DOMPurify from "dompurify";
import { marked } from "marked";
import { computed } from "vue";
import { Download, FileText } from "lucide-vue-next";

const props = defineProps<{ reports: WorkflowReport[]; selectedId?: string; loading?: boolean }>();
const emit = defineEmits<{ select: [id: string]; download: [id: string] }>();
const selected = computed(() => props.reports.find((value) => value.id === props.selectedId) ?? props.reports[0]);
const html = computed(() => DOMPurify.sanitize(marked.parse(selected.value?.markdownReport ?? "") as string));
</script>

<template>
  <aside class="workflow-report-panel" aria-label="分析报告">
    <header>
      <div>
        <p class="workflow-kicker">不可变制品</p>
        <h2>影响报告</h2>
      </div>
      <button
        v-if="selected"
        type="button"
        class="workflow-icon-button"
        aria-label="下载 Markdown 报告"
        @click="emit('download', selected.id)"
      ><Download :size="17" /></button>
    </header>
    <label v-if="reports.length" class="workflow-version-select">
      <span>报告版本</span>
      <select :value="selected?.id" @change="emit('select', ($event.target as HTMLSelectElement).value)">
        <option v-for="report in reports" :key="report.id" :value="report.id">
          V{{ report.version }}{{ report.current ? " · 当前综合报告" : "" }}
        </option>
      </select>
    </label>
    <div v-if="loading" class="workflow-report-empty">正在读取报告…</div>
    <div v-else-if="selected" class="workflow-report-markdown" v-html="html" />
    <div v-else class="workflow-report-empty">
      <FileText :size="30" />
      <strong>报告将在分析完成后出现</strong>
      <span>结构化 JSON 与 Markdown 会作为独立版本长期保存。</span>
    </div>
  </aside>
</template>
