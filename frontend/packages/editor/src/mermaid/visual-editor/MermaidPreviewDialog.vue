<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ensureMermaid } from "../init";

const props = withDefaults(
  defineProps<{
    code: string;
    title?: string;
  }>(),
  {
    title: "Mermaid 图表预览"
  }
);

const emit = defineEmits<{
  close: [];
  edit: [];
}>();

const svgContent = ref("");
const loading = ref(true);
const renderError = ref<string | null>(null);

let renderSequence = 0;

async function renderDiagram(source: string) {
  const sequence = ++renderSequence;
  const trimmed = source.trim();
  if (!trimmed) {
    svgContent.value = "";
    renderError.value = "内容为空，无法渲染图表";
    loading.value = false;
    return;
  }

  loading.value = true;
  renderError.value = null;
  const renderId = `ta-mmd-prev-${Math.random().toString(36).substring(2, 9)}`;

  try {
    const mermaid = await ensureMermaid();
    const { svg } = await mermaid.render(renderId, trimmed);
    if (sequence === renderSequence) {
      svgContent.value = svg;
      renderError.value = null;
    }
  } catch (err) {
    const badDiv = document.getElementById(`d${renderId}`);
    if (badDiv) badDiv.remove();
    const badSvg = document.getElementById(renderId);
    if (badSvg) badSvg.remove();
    if (sequence === renderSequence) {
      svgContent.value = "";
      renderError.value = err instanceof Error ? err.message : String(err);
    }
  } finally {
    if (sequence === renderSequence) {
      loading.value = false;
    }
  }
}

watch(
  () => props.code,
  (newCode) => {
    void renderDiagram(newCode ?? "");
  },
  { immediate: true }
);

function onKeydown(event: KeyboardEvent) {
  if (event.key === "Escape") {
    emit("close");
  }
}

onMounted(() => window.addEventListener("keydown", onKeydown));
onBeforeUnmount(() => window.removeEventListener("keydown", onKeydown));
</script>

<template>
  <Teleport to="body">
    <div class="ta-mermaid-preview-backdrop" @click.self="emit('close')">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="ta-mermaid-preview-title"
        class="ta-mermaid-preview-dialog"
      >
        <header class="ta-mermaid-preview-header">
          <div class="ta-mermaid-preview-header-left">
            <h2 id="ta-mermaid-preview-title">{{ title }}</h2>
            <p>Mermaid 图表渲染预览</p>
          </div>
          <button
            type="button"
            aria-label="关闭预览"
            class="ta-mermaid-preview-close"
            @click="emit('close')"
          >
            ×
          </button>
        </header>

        <div class="ta-mermaid-preview-body">
          <div v-if="loading" class="ta-mermaid-preview-loading">
            <div class="ta-mermaid-preview-spinner"></div>
            <span>正在渲染图表…</span>
          </div>
          <div v-else-if="renderError" class="ta-mermaid-error" role="alert">
            <div class="ta-mermaid-error-title">图表解析错误</div>
            <pre class="ta-mermaid-error-detail">{{ renderError }}</pre>
          </div>
          <div v-else class="ta-mermaid-preview-svg" v-html="svgContent" />
        </div>

        <footer class="ta-mermaid-preview-footer">
          <button type="button" class="ta-mermaid-preview-btn-edit" @click="emit('edit')">
            可视化编辑
          </button>
          <button
            type="button"
            class="ta-mermaid-preview-btn-close is-primary"
            @click="emit('close')"
          >
            关闭
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ta-mermaid-preview-backdrop {
  position: fixed;
  z-index: 140;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
}

.ta-mermaid-preview-dialog {
  display: flex;
  width: min(1100px, calc(100vw - 48px));
  height: min(720px, calc(100vh - 48px));
  min-height: 480px;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ta-border, #dbe2ea);
  border-radius: 9px;
  background: var(--ta-surface, #fff);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.24);
}

.ta-mermaid-preview-header,
.ta-mermaid-preview-footer {
  display: flex;
  flex: none;
  align-items: center;
  border-color: var(--ta-border, #e2e8f0);
  background: var(--ta-surface, #fff);
}

.ta-mermaid-preview-header {
  min-height: 58px;
  justify-content: space-between;
  border-bottom: 1px solid var(--ta-border, #e2e8f0);
  padding: 9px 14px 9px 17px;
}

.ta-mermaid-preview-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.ta-mermaid-preview-header h2 {
  margin: 0;
  color: var(--ta-ink, #172033);
  font-size: 14px;
  font-weight: 600;
}

.ta-mermaid-preview-header p {
  margin: 0;
  color: var(--ta-muted, #64748b);
  font-size: 11px;
}

.ta-mermaid-preview-close {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: var(--ta-muted, #64748b);
  font-size: 22px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ta-mermaid-preview-close:hover {
  background: var(--ta-hover, #f1f5f9);
  color: var(--ta-ink, #172033);
}

.ta-mermaid-preview-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
  background: var(--ta-surface-soft, #f8fafc);
}

.ta-mermaid-preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--ta-muted, #64748b);
  font-size: 13px;
}

.ta-mermaid-preview-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid #e2e8f0;
  border-top-color: var(--primary, #4f46e5);
  border-radius: 50%;
  animation: ta-mmd-spin 0.8s linear infinite;
}

@keyframes ta-mmd-spin {
  to {
    transform: rotate(360deg);
  }
}

.ta-mermaid-preview-svg {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
  overflow: auto;
}

.ta-mermaid-preview-svg :deep(svg) {
  max-width: 100%;
  height: auto;
}

.ta-mermaid-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 640px;
  width: 100%;
  padding: 16px;
  background: rgba(239, 68, 68, 0.06);
  border: 1px dashed rgba(239, 68, 68, 0.4);
  border-radius: 6px;
  color: #b91c1c;
  font-family: inherit;
  text-align: left;
}

.ta-mermaid-error-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 8px;
}

.ta-mermaid-error-detail {
  margin: 0;
  padding: 10px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 4px;
  font-family: Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 11px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-all;
  width: 100%;
  color: inherit;
  border: none;
}

.ta-mermaid-preview-footer {
  min-height: 50px;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid var(--ta-border, #e2e8f0);
  padding: 9px 14px;
}

.ta-mermaid-preview-footer button {
  min-width: 76px;
  min-height: 30px;
  border: 1px solid var(--ta-border, #dbe2ea);
  border-radius: 5px;
  background: var(--ta-surface, #fff);
  color: var(--ta-ink, #172033);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  padding: 0 12px;
}

.ta-mermaid-preview-footer button.is-primary {
  border-color: var(--primary, #4f46e5);
  background: var(--primary, #4f46e5);
  color: #fff;
}

.ta-mermaid-preview-footer button:hover:not(:disabled) {
  opacity: 0.9;
}

@media (max-width: 820px) {
  .ta-mermaid-preview-backdrop {
    padding: 8px;
  }
  .ta-mermaid-preview-dialog {
    width: calc(100vw - 16px);
    height: calc(100vh - 16px);
    min-height: 0;
  }
}
</style>
