<script setup lang="ts">
import { computed } from "vue";
import { UploadCloud } from "lucide-vue-next";
import type { FileUploadOverlayState } from "./fileUploadOverlayState";

const props = defineProps<FileUploadOverlayState>();

const overallUploadedBytes = computed(() => Math.min(
  props.totalBytes,
  props.completedBytes + props.fileUploadedBytes
));
const overallPercent = computed(() => {
  if (props.totalBytes === 0) return 0;
  return Math.min(100, Math.round((overallUploadedBytes.value / props.totalBytes) * 100));
});
const progressStyle = computed(() => ({ width: `${overallPercent.value}%` }));

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KiB", "MiB", "GiB", "TiB"];
  const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / (1024 ** unitIndex);
  return `${value >= 10 || unitIndex === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[unitIndex]}`;
}
</script>

<template>
  <Teleport to="body">
    <div
      class="file-upload-mask"
      data-testid="file-upload-overlay"
      role="status"
      aria-live="polite"
      aria-busy="true"
      :aria-label="`正在上传 ${fileName}，整体进度 ${overallPercent}%`"
    >
      <section class="file-upload-card">
        <header class="file-upload-header">
          <span class="file-upload-icon" aria-hidden="true">
            <UploadCloud :size="17" :stroke-width="1.7" />
          </span>
          <div class="file-upload-heading">
            <strong>正在上传文件</strong>
            <span>请保持当前页面打开</span>
          </div>
          <span class="file-upload-count">{{ fileIndex }} / {{ fileCount }}</span>
        </header>

        <div class="file-upload-file" :title="fileName">{{ fileName }}</div>
        <div class="file-upload-stats">
          <span>{{ formatBytes(fileUploadedBytes) }} / {{ formatBytes(fileBytes) }}</span>
          <span>{{ overallPercent }}%</span>
        </div>
        <div
          class="file-upload-track"
          role="progressbar"
          aria-label="上传总进度"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-valuenow="overallPercent"
        >
          <span class="file-upload-progress" :style="progressStyle" />
        </div>
        <div class="file-upload-total">
          批次进度 {{ formatBytes(overallUploadedBytes) }} / {{ formatBytes(totalBytes) }}
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.file-upload-mask {
  position: fixed;
  inset: 0;
  z-index: 1400;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgb(15 23 42 / 32%);
  backdrop-filter: blur(2px);
  cursor: wait;
}

.file-upload-card {
  width: min(420px, calc(100vw - 32px));
  border: 1px solid var(--ta-border, #dfe3e8);
  border-radius: 8px;
  background: var(--ta-panel, #fff);
  box-shadow: 0 18px 44px rgb(15 23 42 / 22%);
  color: var(--ta-text, #18181b);
  padding: 16px;
}

.file-upload-header {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
}

.file-upload-icon {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid #bfdbfe;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
}

.file-upload-heading {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.file-upload-heading strong {
  font-size: 13px;
  font-weight: 650;
  line-height: 18px;
}

.file-upload-heading span,
.file-upload-total {
  color: var(--ta-muted, #6b7280);
  font-size: 11px;
  line-height: 16px;
}

.file-upload-count {
  border: 1px solid var(--ta-border, #e4e4e7);
  border-radius: 999px;
  background: var(--ta-panel-2, #f8fafc);
  color: var(--ta-muted, #64748b);
  font: 600 11px/20px ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  padding: 0 8px;
}

.file-upload-file {
  overflow: hidden;
  margin-top: 16px;
  color: var(--ta-text, #27272a);
  font: 500 12px/18px ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-upload-stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  color: var(--ta-muted, #64748b);
  font: 500 11px/16px ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.file-upload-track {
  height: 5px;
  overflow: hidden;
  margin-top: 6px;
  border-radius: 999px;
  background: #e2e8f0;
}

.file-upload-progress {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #2563eb;
  transition: width 120ms ease-out;
}

.file-upload-total {
  margin-top: 7px;
  text-align: right;
}

@media (prefers-reduced-motion: reduce) {
  .file-upload-progress {
    transition: none;
  }
}
</style>
