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

function zoomIn() {
  const newZoom = Math.min(zoom.value * 1.25, 5);
  updateZoomCentered(newZoom);
}

function zoomOut() {
  const newZoom = Math.max(zoom.value * 0.8, 0.1);
  updateZoomCentered(newZoom);
}

function resetZoom() {
  zoom.value = 1;
  centerDiagram(1);
}

function updateZoomCentered(newZoom: number) {
  if (!viewportEl.value) {
    zoom.value = newZoom;
    return;
  }
  const rect = viewportEl.value.getBoundingClientRect();
  const centerX = rect.width / 2;
  const centerY = rect.height / 2;

  pan.value = {
    x: centerX - (centerX - pan.value.x) * (newZoom / zoom.value),
    y: centerY - (centerY - pan.value.y) * (newZoom / zoom.value)
  };
  zoom.value = newZoom;
}

function centerDiagram(targetZoom: number) {
  if (!viewportEl.value || !svgHostEl.value) return;
  const svgEl = svgHostEl.value.querySelector("svg");
  if (!svgEl) return;

  const viewportRect = viewportEl.value.getBoundingClientRect();
  const svgRect = svgEl.getBoundingClientRect();
  const currentWidth = svgRect.width / (zoom.value || 1);
  const currentHeight = svgRect.height / (zoom.value || 1);

  const renderedWidth = currentWidth * targetZoom;
  const renderedHeight = currentHeight * targetZoom;

  pan.value = {
    x: (viewportRect.width - renderedWidth) / 2,
    y: Math.max((viewportRect.height - renderedHeight) / 2, 32)
  };
}

function fitToScreen() {
  if (!viewportEl.value || !svgHostEl.value) {
    zoom.value = 1;
    pan.value = { x: 0, y: 0 };
    return;
  }
  const svgEl = svgHostEl.value.querySelector("svg");
  if (!svgEl) {
    zoom.value = 1;
    pan.value = { x: 0, y: 0 };
    return;
  }

  // 移除 Mermaid 自带的绝对 max-width 限制，让容器和 transform 完全接管缩放
  svgEl.style.maxWidth = "none";
  svgEl.style.width = "auto";
  svgEl.style.height = "auto";

  const viewportRect = viewportEl.value.getBoundingClientRect();
  if (viewportRect.width <= 0 || viewportRect.height <= 0) {
    zoom.value = 1;
    pan.value = { x: 24, y: 24 };
    return;
  }

  const svgBbox = typeof svgEl.getBBox === "function" ? svgEl.getBBox() : null;
  const rawWidth =
    (svgBbox && svgBbox.width > 0 ? svgBbox.width : 0) ||
    svgEl.clientWidth ||
    parseFloat(svgEl.getAttribute("width") || "0") ||
    parseFloat(svgEl.viewBox?.baseVal?.width?.toString() || "800") ||
    800;

  const rawHeight =
    (svgBbox && svgBbox.height > 0 ? svgBbox.height : 0) ||
    svgEl.clientHeight ||
    parseFloat(svgEl.getAttribute("height") || "0") ||
    parseFloat(svgEl.viewBox?.baseVal?.height?.toString() || "600") ||
    600;

  const paddingX = 48;
  const paddingTop = 36;
  const paddingBottom = 48;

  const availableWidth = Math.max(viewportRect.width - paddingX * 2, 100);
  const availableHeight = Math.max(viewportRect.height - paddingTop - paddingBottom, 100);

  const scaleX = availableWidth / rawWidth;
  const scaleY = availableHeight / rawHeight;
  const fitScale = Math.min(scaleX, scaleY, 1.2);

  const finalZoom = Math.max(fitScale, 0.1);
  const renderedWidth = rawWidth * finalZoom;
  const renderedHeight = rawHeight * finalZoom;

  const startX = Math.max((viewportRect.width - renderedWidth) / 2, paddingX);
  // 确保顶部始终留出足够的内边距，不会被标题栏遮挡或顶出可视区
  const startY = Math.max((viewportRect.height - renderedHeight) / 2, paddingTop);

  zoom.value = finalZoom;
  pan.value = { x: startX, y: startY };
}

function onWheel(event: WheelEvent) {
  const delta = event.deltaY < 0 ? 1.15 : 0.87;
  const newZoom = Math.min(Math.max(zoom.value * delta, 0.1), 5);

  if (viewportEl.value) {
    const rect = viewportEl.value.getBoundingClientRect();
    const mouseX = event.clientX - rect.left;
    const mouseY = event.clientY - rect.top;

    pan.value = {
      x: mouseX - (mouseX - pan.value.x) * (newZoom / zoom.value),
      y: mouseY - (mouseY - pan.value.y) * (newZoom / zoom.value)
    };
  }
  zoom.value = newZoom;
}

function onMouseDown(event: MouseEvent) {
  if (event.button !== 0) return;
  // 忽略工具栏上的点击
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

            <!-- 浮动缩放控制栏 -->
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
              <button
                type="button"
                class="ta-mermaid-toolbar-zoom-label"
                title="点击重置 100%"
                @click.stop="resetZoom"
              >
                {{ Math.round(zoom * 100) }}%
              </button>
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
                class="ta-mermaid-toolbar-btn"
                title="适应画布 (双击背景)"
                aria-label="适应画布"
                @click.stop="fitToScreen"
              >
                <Maximize2 :size="14" />
              </button>
              <button
                type="button"
                class="ta-mermaid-toolbar-btn"
                title="重置比例 (100%)"
                aria-label="重置"
                @click.stop="resetZoom"
              >
                <RotateCcw :size="14" />
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
  will-change: transform;
}

.ta-mermaid-preview-svg {
  display: inline-block;
}

.ta-mermaid-preview-svg :deep(svg) {
  display: block;
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
  width: 26px;
  height: 26px;
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

.ta-mermaid-toolbar-zoom-label {
  display: inline-block;
  min-width: 44px;
  text-align: center;
  font-size: 11px;
  font-weight: 500;
  color: #475569;
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
}

.ta-mermaid-toolbar-zoom-label:hover {
  background: #f1f5f9;
  color: #0f172a;
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
