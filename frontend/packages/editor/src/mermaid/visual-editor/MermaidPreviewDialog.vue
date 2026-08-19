<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { Maximize2, RotateCcw, ZoomIn, ZoomOut } from "lucide-vue-next";
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

const viewportEl = ref<HTMLElement | null>(null);
const svgHostEl = ref<HTMLElement | null>(null);

const zoom = ref(1);
const pan = ref({ x: 0, y: 0 });
const isDragging = ref(false);
const dragStart = ref({ x: 0, y: 0 });

let renderSequence = 0;

function getSvgNaturalSize(): { width: number; height: number } {
  if (!svgHostEl.value) return { width: 800, height: 600 };
  const svgEl = svgHostEl.value.querySelector("svg");
  if (!svgEl) return { width: 800, height: 600 };

  const viewBoxAttr = svgEl.getAttribute("viewBox");
  if (viewBoxAttr) {
    const parts = viewBoxAttr.trim().split(/[\s,]+/).map(Number);
    if (parts.length === 4 && parts[2] > 0 && parts[3] > 0) {
      return { width: parts[2], height: parts[3] };
    }
  }

  const widthAttr = parseFloat(svgEl.getAttribute("width") || "0");
  const heightAttr = parseFloat(svgEl.getAttribute("height") || "0");
  if (widthAttr > 0 && heightAttr > 0) {
    return { width: widthAttr, height: heightAttr };
  }

  const clientRect = svgEl.getBoundingClientRect();
  if (clientRect.width > 0 && clientRect.height > 0) {
    return { width: clientRect.width, height: clientRect.height };
  }

  return { width: 800, height: 600 };
}

/**
 * 还原到 100% 原始比例并居中显示
 */
function resetTo100Center() {
  if (!viewportEl.value) {
    zoom.value = 1;
    pan.value = { x: 36, y: 36 };
    return;
  }
  const naturalSize = getSvgNaturalSize();
  const vWidth = viewportEl.value.clientWidth || 1000;
  const vHeight = viewportEl.value.clientHeight || 600;

  zoom.value = 1;
  pan.value = {
    x: (vWidth - naturalSize.width) / 2,
    y: Math.max((vHeight - naturalSize.height) / 2, 36)
  };
}

/**
 * 适应画布：根据视口大小按最佳比例完整居中显示
 */
function fitToScreen() {
  if (!viewportEl.value) {
    resetTo100Center();
    return;
  }
  const naturalSize = getSvgNaturalSize();
  const vWidth = viewportEl.value.clientWidth || 1000;
  const vHeight = viewportEl.value.clientHeight || 600;

  if (vWidth <= 0 || vHeight <= 0 || naturalSize.width <= 0 || naturalSize.height <= 0) {
    resetTo100Center();
    return;
  }

  const paddingX = 48;
  const paddingTop = 36;
  const paddingBottom = 48;

  const availW = Math.max(vWidth - paddingX * 2, 100);
  const availH = Math.max(vHeight - paddingTop - paddingBottom, 100);

  const scaleX = availW / naturalSize.width;
  const scaleY = availH / naturalSize.height;
  const fitZoom = Math.min(scaleX, scaleY, 1.2); // 最大不超过 120%

  zoom.value = Math.max(fitZoom, 0.05);

  const renderedW = naturalSize.width * zoom.value;
  const renderedH = naturalSize.height * zoom.value;

  pan.value = {
    x: (vWidth - renderedW) / 2,
    y: Math.max((vHeight - renderedH) / 2, paddingTop)
  };
}

function zoomIn() {
  if (!viewportEl.value) {
    zoom.value = Math.min(zoom.value * 1.25, 8);
    return;
  }
  const rect = viewportEl.value.getBoundingClientRect();
  const centerX = rect.width / 2;
  const centerY = rect.height / 2;
  const newZoom = Math.min(zoom.value * 1.25, 8);

  pan.value = {
    x: centerX - (centerX - pan.value.x) * (newZoom / zoom.value),
    y: centerY - (centerY - pan.value.y) * (newZoom / zoom.value)
  };
  zoom.value = newZoom;
}

function zoomOut() {
  if (!viewportEl.value) {
    zoom.value = Math.max(zoom.value * 0.8, 0.05);
    return;
  }
  const rect = viewportEl.value.getBoundingClientRect();
  const centerX = rect.width / 2;
  const centerY = rect.height / 2;
  const newZoom = Math.max(zoom.value * 0.8, 0.05);

  pan.value = {
    x: centerX - (centerX - pan.value.x) * (newZoom / zoom.value),
    y: centerY - (centerY - pan.value.y) * (newZoom / zoom.value)
  };
  zoom.value = newZoom;
}

function onWheel(event: WheelEvent) {
  if (!viewportEl.value) return;

  const rect = viewportEl.value.getBoundingClientRect();
  const mouseX = event.clientX - rect.left;
  const mouseY = event.clientY - rect.top;

  const delta = event.deltaY < 0 ? 1.15 : 0.87;
  const newZoom = Math.min(Math.max(zoom.value * delta, 0.05), 8);

  pan.value = {
    x: mouseX - (mouseX - pan.value.x) * (newZoom / zoom.value),
    y: mouseY - (mouseY - pan.value.y) * (newZoom / zoom.value)
  };
  zoom.value = newZoom;
}

function onMouseDown(event: MouseEvent) {
  if (event.button !== 0) return;
  const target = event.target as HTMLElement | null;
  if (target?.closest(".ta-mermaid-preview-toolbar")) return;

  isDragging.value = true;
  dragStart.value = {
    x: event.clientX - pan.value.x,
    y: event.clientY - pan.value.y
  };
}

function onMouseMove(event: MouseEvent) {
  if (!isDragging.value) return;
  pan.value = {
    x: event.clientX - dragStart.value.x,
    y: event.clientY - dragStart.value.y
  };
}

function onMouseUp() {
  isDragging.value = false;
}

function normalizeSvgElement() {
  if (!svgHostEl.value) return;
  const svgEl = svgHostEl.value.querySelector("svg");
  if (!svgEl) return;

  const naturalSize = getSvgNaturalSize();
  svgEl.style.maxWidth = "none";
  svgEl.style.width = `${naturalSize.width}px`;
  svgEl.style.height = `${naturalSize.height}px`;
  svgEl.style.display = "block";
  svgEl.style.shapeRendering = "geometricPrecision";
  svgEl.style.textRendering = "geometricPrecision";
}

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
      await nextTick();
      normalizeSvgElement();
      fitToScreen();
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

onMounted(() => {
  window.addEventListener("keydown", onKeydown);
  window.addEventListener("mousemove", onMouseMove);
  window.addEventListener("mouseup", onMouseUp);
});

onBeforeUnmount(() => {
  window.removeEventListener("keydown", onKeydown);
  window.removeEventListener("mousemove", onMouseMove);
  window.removeEventListener("mouseup", onMouseUp);
});
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
            <p>Mermaid 图表渲染预览（支持滚轮缩放与鼠标拖拽平移）</p>
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
          <div
            v-else
            ref="viewportEl"
            class="ta-mermaid-preview-viewport"
            :class="{ 'is-dragging': isDragging }"
            @wheel.prevent="onWheel"
            @mousedown="onMouseDown"
            @dblclick="fitToScreen"
          >
            <div
              class="ta-mermaid-preview-canvas"
              :style="{
                transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
                transformOrigin: '0 0'
              }"
            >
              <div ref="svgHostEl" class="ta-mermaid-preview-svg" v-html="svgContent" />
            </div>

            <!-- 浮动缩放与复原控制栏 -->
            <div class="ta-mermaid-preview-toolbar">
              <button
                type="button"
                class="ta-mermaid-toolbar-btn"
                title="缩小 (向下滚轮)"
                aria-label="缩小"
                @click.stop="zoomOut"
              >
                <ZoomOut :size="14" />
              </button>
              <span class="ta-mermaid-toolbar-zoom-label">
                {{ Math.round(zoom * 100) }}%
              </span>
              <button
                type="button"
                class="ta-mermaid-toolbar-btn"
                title="放大 (向上滚轮)"
                aria-label="放大"
                @click.stop="zoomIn"
              >
                <ZoomIn :size="14" />
              </button>
              <span class="ta-mermaid-toolbar-divider"></span>
              <button
                type="button"
                class="ta-mermaid-toolbar-btn ta-mermaid-toolbar-btn-text"
                title="适应画布 (双击背景)"
                aria-label="适应画布"
                @click.stop="fitToScreen"
              >
                <Maximize2 :size="13" />
                <span>适应</span>
              </button>
              <button
                type="button"
                class="ta-mermaid-toolbar-btn ta-mermaid-toolbar-btn-text ta-mermaid-toolbar-btn-reset"
                title="还原到 100% 比例并居中"
                aria-label="复原"
                @click.stop="resetTo100Center"
              >
                <RotateCcw :size="13" />
                <span>复原 (100%)</span>
              </button>
            </div>
          </div>
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
  width: min(1200px, calc(100vw - 48px));
  height: min(800px, calc(100vh - 48px));
  min-height: 520px;
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
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  background: #f8fafc;
}

.ta-mermaid-preview-viewport {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  cursor: grab;
  user-select: none;
  background-image: radial-gradient(rgba(148, 163, 184, 0.22) 1px, transparent 1px);
  background-size: 20px 20px;
}

.ta-mermaid-preview-viewport.is-dragging {
  cursor: grabbing;
}

.ta-mermaid-preview-canvas {
  position: absolute;
  top: 0;
  left: 0;
  user-select: none;
  pointer-events: none;
}

.ta-mermaid-preview-svg {
  display: inline-block;
}

.ta-mermaid-preview-svg :deep(svg) {
  display: block;
  max-width: none !important;
  height: auto;
  shape-rendering: geometricPrecision;
  text-rendering: geometricPrecision;
}

.ta-mermaid-preview-toolbar {
  position: absolute;
  bottom: 16px;
  right: 16px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(8px);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.ta-mermaid-toolbar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  padding: 0 6px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #475569;
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
}

.ta-mermaid-toolbar-btn:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.ta-mermaid-toolbar-btn-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 500;
  padding: 0 8px;
}

.ta-mermaid-toolbar-btn-reset {
  color: #4f46e5;
}

.ta-mermaid-toolbar-btn-reset:hover {
  background: #eef2ff;
  color: #4338ca;
}

.ta-mermaid-toolbar-zoom-label {
  display: inline-block;
  min-width: 42px;
  text-align: center;
  font-size: 11px;
  font-weight: 600;
  color: #334155;
  padding: 2px 4px;
}

.ta-mermaid-toolbar-divider {
  width: 1px;
  height: 14px;
  background: #e2e8f0;
  margin: 0 2px;
}

.ta-mermaid-preview-loading {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
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

.ta-mermaid-error {
  margin: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  max-width: 640px;
  width: 90%;
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
