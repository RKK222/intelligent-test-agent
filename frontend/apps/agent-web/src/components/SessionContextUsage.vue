<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useId, watch, type CSSProperties } from "vue";
import { X } from "lucide-vue-next";
import type { AgentMessage, MessageScope, ModelInfo, ProviderInfo } from "@test-agent/shared-types";
import {
  buildSessionContextSummary,
  estimateSessionContextBreakdown,
  type SessionContextBreakdownItem
} from "./session-context-metrics";
import { resolveExternalConversationDrawerPlacement } from "./session-list-drawer";

const props = defineProps<{
  sessionId: string;
  sessionTitle?: string;
  messages: AgentMessage[];
  messageScopesById?: Record<string, MessageScope>;
  selectedProvider?: string;
  selectedModel?: string;
  models?: ModelInfo[];
  providers?: ProviderInfo[];
  panelVisible?: boolean;
  historyDrawerOpen?: boolean;
}>();
const emit = defineEmits<{
  "detail-open": [];
}>();

const triggerRef = ref<HTMLButtonElement>();
const closeRef = ref<HTMLButtonElement>();
const detailOpen = ref(false);
const tooltipOpen = ref(false);
const tooltipId = useId();
const dialogId = useId();
const tooltipVisible = computed(() => tooltipOpen.value && !detailOpen.value);
const drawerPlacement = ref<ReturnType<typeof resolveExternalConversationDrawerPlacement> | null>(null);
const drawerPanelStyle = computed<CSSProperties>(() => {
  const placement = drawerPlacement.value;
  if (!placement) return {};
  return {
    top: `${placement.top}px`,
    left: `${placement.left}px`,
    width: `${placement.width}px`,
    height: `${placement.height}px`
  };
});
let drawerResizeObserver: ResizeObserver | null = null;
let drawerPlacementFrame: number | null = null;

const summary = computed(() => buildSessionContextSummary({
  messages: props.messages,
  messageScopesById: props.messageScopesById,
  rootSessionId: props.sessionId,
  selectedProvider: props.selectedProvider,
  selectedModel: props.selectedModel,
  models: props.models,
  providers: props.providers
}));

const ringRadius = 6;
const ringCircumference = 2 * Math.PI * ringRadius;
const ringOffset = computed(() => ringCircumference * (1 - summary.value.ringPercent / 100));

let cachedBreakdownKey = "";
let cachedBreakdown: SessionContextBreakdownItem[] = [];
const breakdown = computed(() => {
  if (!detailOpen.value) return [];
  const value = summary.value;
  // 流式正文频繁变化时，只在影响统计口径的最近用量、input 或根消息数变化后重算正文拆分。
  const cacheKey = `${props.sessionId}:${value.usageMessageId ?? "none"}:${value.inputTokens}:${value.messageCount}`;
  if (cacheKey === cachedBreakdownKey) return cachedBreakdown;
  cachedBreakdownKey = cacheKey;
  cachedBreakdown = estimateSessionContextBreakdown(props.messages, value.inputTokens, {
    messageScopesById: props.messageScopesById,
    rootSessionId: props.sessionId
  });
  return cachedBreakdown;
});

function formatNumber(value: number | undefined): string {
  return value === undefined ? "—" : new Intl.NumberFormat("zh-CN").format(value);
}

function openDetail() {
  tooltipOpen.value = false;
  const placement = measureDrawerPlacement();
  if (!placement || placement.width <= 0) return;
  emit("detail-open");
  detailOpen.value = true;
  nextTick(() => {
    measureDrawerPlacement();
    closeRef.value?.focus();
  });
}

function toggleDetail() {
  if (detailOpen.value) {
    tooltipOpen.value = false;
    closeDetail();
    return;
  }
  openDetail();
}

function closeDetail(restoreFocus = true) {
  if (!detailOpen.value) return;
  detailOpen.value = false;
  if (drawerPlacementFrame !== null && typeof window !== "undefined") {
    window.cancelAnimationFrame(drawerPlacementFrame);
    drawerPlacementFrame = null;
  }
  if (restoreFocus) nextTick(() => triggerRef.value?.focus());
}

// 与会话列表一致，按对话栏实测矩形定位到 body；宽度不足时只压缩外侧抽屉，绝不覆盖对话。
function measureDrawerPlacement() {
  if (typeof window === "undefined") return null;
  const root = triggerRef.value?.closest(".figma-chat-root") as HTMLElement | null;
  if (!root) return null;
  const rect = root.getBoundingClientRect();
  const placement = resolveExternalConversationDrawerPlacement(
    { top: rect.top, left: rect.left, width: rect.width, height: rect.height },
    { width: window.innerWidth, height: window.innerHeight },
    420
  );
  drawerPlacement.value = placement;
  return placement;
}

// 仅在详情可见时按帧合并滚动和尺寸事件，避免消息滚动期间反复触发布局读取。
function scheduleDrawerPlacement() {
  if (!detailOpen.value || typeof window === "undefined" || drawerPlacementFrame !== null) return;
  drawerPlacementFrame = window.requestAnimationFrame(() => {
    drawerPlacementFrame = null;
    const placement = measureDrawerPlacement();
    if (!placement || placement.width <= 0) closeDetail(false);
  });
}

function handleEscape(event: KeyboardEvent) {
  if (event.key === "Escape" && detailOpen.value) {
    event.preventDefault();
    closeDetail();
    return;
  }
  if (event.key === "Escape" && tooltipOpen.value) {
    event.preventDefault();
    tooltipOpen.value = false;
  }
}

watch(() => props.sessionId, () => closeDetail(false));
watch(() => props.panelVisible, (visible) => {
  if (visible === false) closeDetail(false);
});
watch(() => props.historyDrawerOpen, (open) => {
  if (open) closeDetail(false);
});
onMounted(() => {
  window.addEventListener("keydown", handleEscape);
  window.addEventListener("resize", scheduleDrawerPlacement);
  window.addEventListener("scroll", scheduleDrawerPlacement, true);
  const root = triggerRef.value?.closest(".figma-chat-root") as HTMLElement | null;
  if (root && typeof ResizeObserver !== "undefined") {
    drawerResizeObserver = new ResizeObserver(scheduleDrawerPlacement);
    drawerResizeObserver.observe(root);
  }
});
onBeforeUnmount(() => {
  window.removeEventListener("keydown", handleEscape);
  window.removeEventListener("resize", scheduleDrawerPlacement);
  window.removeEventListener("scroll", scheduleDrawerPlacement, true);
  if (drawerPlacementFrame !== null) window.cancelAnimationFrame(drawerPlacementFrame);
  drawerPlacementFrame = null;
  drawerResizeObserver?.disconnect();
  drawerResizeObserver = null;
});
</script>

<template>
  <div class="session-context-usage">
    <button
      ref="triggerRef"
      type="button"
      class="session-context-trigger"
      aria-label="查看会话上下文"
      :aria-expanded="detailOpen"
      aria-haspopup="dialog"
      :aria-controls="dialogId"
      :aria-describedby="tooltipVisible ? tooltipId : undefined"
      @click="toggleDetail"
      @mouseenter="tooltipOpen = true"
      @mouseleave="tooltipOpen = false"
      @focus="tooltipOpen = true"
      @blur="tooltipOpen = false"
    >
      <svg
        class="session-context-ring"
        data-testid="context-ring"
        :data-usage-percent="summary.usagePercent ?? ''"
        width="16"
        height="16"
        viewBox="0 0 16 16"
        aria-hidden="true"
      >
        <circle class="session-context-ring-track" cx="8" cy="8" :r="ringRadius" />
        <circle
          v-if="summary.usagePercent !== undefined"
          class="session-context-ring-value"
          cx="8"
          cy="8"
          :r="ringRadius"
          :stroke-dasharray="ringCircumference"
          :stroke-dashoffset="ringOffset"
        />
      </svg>
    </button>

    <div v-if="tooltipVisible" :id="tooltipId" class="session-context-tooltip" role="tooltip">
      <div><span>使用率</span><strong>{{ summary.usagePercent === undefined ? '—' : `${summary.usagePercent}%` }}</strong></div>
      <div><span>总上下文</span><strong>{{ formatNumber(summary.contextLimit) }}</strong></div>
      <div><span>已使用</span><strong>{{ formatNumber(summary.totalTokens) }}</strong></div>
    </div>

    <Teleport to="body">
      <section
        v-if="detailOpen && drawerPlacement && drawerPlacement.width > 0"
        class="session-context-panel session-context-external-drawer"
        data-side="left"
        data-placement="left-of-conversation"
        :id="dialogId"
        :style="drawerPanelStyle"
        role="dialog"
        aria-label="会话上下文"
        aria-modal="false"
      >
      <header class="session-context-panel-header">
        <div>
          <p class="session-context-eyebrow">CONTEXT</p>
          <h3>会话上下文</h3>
        </div>
        <button ref="closeRef" type="button" class="session-context-close" aria-label="关闭上下文详情" @click="closeDetail()">
          <X aria-hidden="true" />
        </button>
      </header>

      <div class="session-context-panel-body">
        <section class="session-context-identity">
          <p class="session-context-section-label">当前会话</p>
          <h4>{{ sessionTitle || '未命名会话' }}</h4>
          <dl class="session-context-meta-grid">
            <div><dt>供应商</dt><dd>{{ summary.providerName }}</dd></div>
            <div><dt>模型</dt><dd>{{ summary.modelName }}</dd></div>
            <div><dt>消息数</dt><dd>{{ summary.messageCount }} 条</dd></div>
            <div><dt>上下文限制</dt><dd data-testid="context-limit">{{ formatNumber(summary.contextLimit) }}</dd></div>
          </dl>
        </section>

        <section class="session-context-meter-card">
          <div class="session-context-meter-heading">
            <div>
              <p class="session-context-section-label">上下文使用率</p>
              <strong>{{ summary.usagePercent === undefined ? '—' : `${summary.usagePercent}%` }}</strong>
            </div>
            <span>{{ formatNumber(summary.totalTokens) }} / {{ formatNumber(summary.contextLimit) }}</span>
          </div>
          <div class="session-context-meter-track" aria-hidden="true">
            <span :style="{ width: `${summary.ringPercent}%` }" />
          </div>
        </section>

        <dl class="session-context-token-grid">
          <div><dt>总 Token</dt><dd>{{ formatNumber(summary.totalTokens) }}</dd></div>
          <div><dt>输入 Token</dt><dd>{{ formatNumber(summary.inputTokens) }}</dd></div>
          <div><dt>输出 Token</dt><dd>{{ formatNumber(summary.outputTokens) }}</dd></div>
        </dl>

        <section class="session-context-breakdown-section">
          <div class="session-context-breakdown-heading">
            <div>
              <p class="session-context-section-label">上下文拆分</p>
              <h4>最近一次输入估算</h4>
            </div>
            <span>按可见消息校准</span>
          </div>

          <template v-if="breakdown.length">
            <div class="session-context-breakdown" data-testid="context-breakdown" aria-label="上下文拆分图表">
              <span
                v-for="item in breakdown"
                :key="item.key"
                :style="{ width: `${item.percentage}%`, backgroundColor: item.color }"
                :title="`${item.label} ${formatNumber(item.tokens)}`"
              />
            </div>
            <div class="session-context-breakdown-legend">
              <div v-for="item in breakdown" :key="item.key" data-testid="context-breakdown-item">
                <span class="session-context-legend-dot" :style="{ backgroundColor: item.color }" />
                <span>{{ item.label }}</span>
                <strong>{{ formatNumber(item.tokens) }}</strong>
              </div>
            </div>
          </template>
          <div v-else class="session-context-breakdown-empty" data-testid="context-breakdown-empty">
            暂无可用的上下文拆分
          </div>
        </section>
      </div>
      </section>
    </Teleport>
  </div>
</template>

<style scoped>
.session-context-usage {
  display: contents;
}

.session-context-trigger {
  width: 24px;
  height: 24px;
  margin-right: 8px;
  padding: 4px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 5px;
  color: #a40dbc;
  background: transparent;
  cursor: pointer;
}

.session-context-trigger:hover,
.session-context-trigger:focus-visible {
  background: rgba(164, 13, 188, 0.08);
  outline: none;
}

.session-context-ring {
  display: block;
  overflow: visible;
}

.session-context-ring-track,
.session-context-ring-value {
  fill: none;
  stroke-width: 2;
}

.session-context-ring-track {
  stroke: #dedee5;
}

.session-context-ring-value {
  stroke: currentColor;
  stroke-linecap: round;
  transform: rotate(-90deg);
  transform-origin: 8px 8px;
  transition: stroke-dashoffset 180ms ease;
}

.session-context-tooltip {
  position: absolute;
  left: 10px;
  bottom: 34px;
  z-index: 42;
  width: 190px;
  padding: 9px 11px;
  border: 1px solid #dedee4;
  border-radius: 7px;
  background: #18181b;
  color: #f4f4f5;
  box-shadow: 0 10px 28px rgba(15, 15, 18, 0.18);
  font-family: var(--font-sans);
  font-size: 11px;
}

.session-context-tooltip div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  line-height: 20px;
}

.session-context-tooltip span {
  color: #a1a1aa;
}

.session-context-tooltip strong {
  font-family: 'JetBrains Mono', 'SFMono-Regular', monospace;
  font-weight: 500;
}

.session-context-panel {
  position: fixed;
  z-index: 1200;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  border: 1px solid #dedee4;
  border-radius: 10px 0 0 10px;
  color: #27272a;
  background: #fafafa;
  box-shadow: -8px 0 28px rgba(15, 23, 42, 0.14);
  font-family: var(--font-sans);
  animation: session-context-drawer-enter 180ms cubic-bezier(0.2, 0.75, 0.2, 1);
}

.session-context-panel-header {
  height: 48px;
  flex: 0 0 auto;
  padding: 0 16px 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e4e7;
  background: #fff;
}

.session-context-panel-header h3,
.session-context-identity h4,
.session-context-breakdown-heading h4 {
  margin: 0;
}

.session-context-panel-header h3 {
  font-size: 13px;
  font-weight: 650;
  line-height: 17px;
}

.session-context-eyebrow,
.session-context-section-label {
  margin: 0;
  color: #8b8b96;
  font-family: 'JetBrains Mono', 'SFMono-Regular', monospace;
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.session-context-close {
  width: 28px;
  height: 28px;
  padding: 6px;
  display: inline-flex;
  border: 0;
  border-radius: 6px;
  color: #71717a;
  background: transparent;
  cursor: pointer;
}

.session-context-close svg {
  width: 16px;
  height: 16px;
}

.session-context-close:hover,
.session-context-close:focus-visible {
  color: #27272a;
  background: #f1f1f3;
  outline: none;
}

.session-context-panel-body {
  min-height: 0;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.session-context-identity,
.session-context-meter-card,
.session-context-breakdown-section {
  border: 1px solid #e4e4e7;
  border-radius: 9px;
  background: #fff;
}

.session-context-identity {
  padding: 15px;
}

.session-context-identity h4 {
  margin-top: 5px;
  overflow: hidden;
  color: #18181b;
  font-size: 15px;
  font-weight: 650;
  line-height: 21px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-context-meta-grid,
.session-context-token-grid {
  margin: 14px 0 0;
  display: grid;
  gap: 1px;
  overflow: hidden;
  border: 1px solid #ececef;
  border-radius: 7px;
  background: #ececef;
}

.session-context-meta-grid {
  grid-template-columns: repeat(auto-fit, minmax(min(140px, 100%), 1fr));
}

.session-context-token-grid {
  grid-template-columns: repeat(auto-fit, minmax(min(110px, 100%), 1fr));
}

.session-context-meta-grid div,
.session-context-token-grid div {
  min-width: 0;
  padding: 10px 11px;
  background: #fafafa;
}

.session-context-meta-grid dt,
.session-context-token-grid dt {
  color: #8b8b96;
  font-size: 10px;
  line-height: 14px;
}

.session-context-meta-grid dd,
.session-context-token-grid dd {
  margin: 3px 0 0;
  overflow: hidden;
  color: #3f3f46;
  font-family: 'JetBrains Mono', 'SFMono-Regular', monospace;
  font-size: 11px;
  font-weight: 550;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-context-meter-card {
  padding: 14px 15px 15px;
}

.session-context-meter-heading,
.session-context-breakdown-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.session-context-meter-heading strong {
  display: block;
  margin-top: 3px;
  color: #18181b;
  font-family: 'JetBrains Mono', 'SFMono-Regular', monospace;
  font-size: 24px;
  font-weight: 600;
  line-height: 28px;
}

.session-context-meter-heading > span,
.session-context-breakdown-heading > span {
  color: #8b8b96;
  font-family: 'JetBrains Mono', 'SFMono-Regular', monospace;
  font-size: 9px;
}

.session-context-meter-track {
  height: 4px;
  margin-top: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: #ececf0;
}

.session-context-meter-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #a40dbc;
  transition: width 180ms ease;
}

.session-context-token-grid {
  margin-top: 0;
}

.session-context-breakdown-section {
  padding: 15px;
}

.session-context-breakdown-heading h4 {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 620;
}

.session-context-breakdown {
  height: 10px;
  margin-top: 16px;
  display: flex;
  overflow: hidden;
  border-radius: 3px;
  background: #ececf0;
}

.session-context-breakdown > span {
  min-width: 0;
  height: 100%;
}

.session-context-breakdown-legend {
  margin-top: 13px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(140px, 100%), 1fr));
  gap: 8px 16px;
}

.session-context-breakdown-legend > div {
  min-width: 0;
  display: grid;
  grid-template-columns: 7px 1fr auto;
  align-items: center;
  gap: 6px;
  color: #71717a;
  font-size: 10px;
}

.session-context-breakdown-legend strong {
  color: #3f3f46;
  font-family: 'JetBrains Mono', 'SFMono-Regular', monospace;
  font-weight: 500;
}

.session-context-legend-dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;
}

.session-context-breakdown-empty {
  margin-top: 14px;
  padding: 28px 12px;
  border: 1px dashed #d8d8df;
  border-radius: 7px;
  color: #8b8b96;
  text-align: center;
  font-size: 11px;
}

@keyframes session-context-drawer-enter {
  from { opacity: 0.72; transform: translateX(12px); }
  to { opacity: 1; transform: translateX(0); }
}

@media (prefers-reduced-motion: reduce) {
  .session-context-panel,
  .session-context-ring-value,
  .session-context-meter-track span {
    animation: none;
    transition: none;
  }
}
</style>
