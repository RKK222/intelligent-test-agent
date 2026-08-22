<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import { createBackendApiClient } from "@test-agent/backend-api";
import type {
  PageResponse,
  TraceCatalog,
  TraceQueryParams,
  TraceRawEvent,
} from "@test-agent/shared-types";
import {
  ChevronDown,
  ChevronRight,
  Download,
  RefreshCw,
  Search,
  ShieldCheck,
  Waypoints,
} from "lucide-vue-next";
import { useAuthStore } from "../stores/authStore";

type InspectorTab = "summary" | "payload" | "result" | "timing" | "source";
type TraceLane = "INPUT" | "MODEL" | "TOOLS";
type DisplayEvent = TraceRawEvent & {
  displayPayload: Record<string, unknown>;
  lane: TraceLane;
};

const API_BASE_URL = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: API_BASE_URL });
const authStore = useAuthStore();

const now = Date.now();
const filters = ref({
  startTime: localDateTime(now - 7 * 86_400_000),
  endTime: localDateTime(now),
  user: "",
  organization: "",
  agentId: "",
  skill: "",
  tool: "",
  status: "",
  traceId: "",
  runId: "",
});
const page = ref<PageResponse<TraceCatalog>>({ items: [], page: 1, size: 30, total: 0 });
const selectedTrace = ref<TraceCatalog | null>(null);
const rawEvents = ref<TraceRawEvent[]>([]);
const selectedEventId = ref<string | null>(null);
const actualDuration = ref(false);
const allTurnsCollapsed = ref(false);
const allCallsCollapsed = ref(false);
const inspectorTab = ref<InspectorTab>("summary");
const eventSearch = ref("");
const collapsedParents = ref<Set<string>>(new Set());
const loadingList = ref(false);
const loadingEvents = ref(false);
const downloading = ref(false);
const error = ref<string | null>(null);

const displayEvents = computed<DisplayEvent[]>(() => materializeEvents(rawEvents.value));
const selectedEvent = computed(() =>
  displayEvents.value.find((event) => event.eventId === selectedEventId.value) ?? null
);
const filteredEvents = computed(() => {
  const keyword = eventSearch.value.trim().toLowerCase();
  const firstByTurn = new Map<string, string>();
  for (const event of displayEvents.value) {
    if (event.turnId && !firstByTurn.has(event.turnId)) firstByTurn.set(event.turnId, event.eventId);
  }
  return displayEvents.value.filter((event) => {
    if (event.parentId && collapsedParents.value.has(event.parentId)) return false;
    if (allCallsCollapsed.value && event.lane === "TOOLS") return false;
    if (allTurnsCollapsed.value && event.turnId && firstByTurn.get(event.turnId) !== event.eventId) return false;
    return !keyword || JSON.stringify(event).toLowerCase().includes(keyword);
  });
});
const completeCount = computed(() => page.value.items.filter((trace) => trace.complete).length);
const rolloutCompleteness = computed(() => page.value.items.length
  ? Math.round((completeCount.value / page.value.items.length) * 100)
  : 0);
const pendingChunks = computed(() => page.value.items.reduce((total, trace) => total + trace.pendingChunks, 0));
const droppedEvents = computed(() => page.value.items.reduce((total, trace) => total + trace.droppedCount, 0));
const coverageStartAt = computed(() => page.value.items
  .map((trace) => trace.coverageStartAt)
  .filter(Boolean)
  .sort()[0] ?? null);

onMounted(async () => {
  if (!authStore.currentUser) await authStore.fetchCurrentUser(api);
  await loadTraces(1);
});

async function loadTraces(targetPage = page.value.page) {
  loadingList.value = true;
  error.value = null;
  try {
    const params: TraceQueryParams = {
      startTime: toInstant(filters.value.startTime),
      endTime: toInstant(filters.value.endTime),
      user: clean(filters.value.user),
      organization: clean(filters.value.organization),
      agentId: clean(filters.value.agentId),
      skill: clean(filters.value.skill),
      tool: clean(filters.value.tool),
      status: clean(filters.value.status),
      traceId: clean(filters.value.traceId),
      runId: clean(filters.value.runId),
      page: targetPage,
      pageSize: page.value.size,
    };
    page.value = await api.listTraces(params);
    if (selectedTrace.value) {
      const current = page.value.items.find((trace) => trace.traceId === selectedTrace.value?.traceId);
      if (current) selectedTrace.value = current;
    }
  } catch (cause) {
    error.value = messageOf(cause, "Trace 目录暂不可用");
  } finally {
    loadingList.value = false;
  }
}

async function openTrace(trace: TraceCatalog) {
  selectedTrace.value = trace;
  selectedEventId.value = null;
  rawEvents.value = [];
  loadingEvents.value = true;
  error.value = null;
  try {
    const [detail, events] = await Promise.all([api.getTrace(trace.traceId), loadAllEvents(trace.traceId)]);
    selectedTrace.value = detail;
    rawEvents.value = events;
    selectedEventId.value = materializeEvents(events)[0]?.eventId ?? null;
  } catch (cause) {
    error.value = messageOf(cause, "Trace 正文暂不可用");
  } finally {
    loadingEvents.value = false;
  }
}

async function loadAllEvents(traceId: string): Promise<TraceRawEvent[]> {
  const result: TraceRawEvent[] = [];
  let afterSequence = 0;
  for (;;) {
    const response = await api.getTraceEvents(traceId, afterSequence, 500);
    result.push(...response.items);
    if (response.items.length === 0) break;
    const next = Math.max(...response.items.map((event) => event.globalSequence));
    if (next <= afterSequence || next >= response.completeThrough || response.items.length < 500) break;
    afterSequence = next;
  }
  return result;
}

async function downloadSelectedTrace() {
  if (!selectedTrace.value || downloading.value) return;
  downloading.value = true;
  try {
    const blob = await api.downloadTrace(selectedTrace.value.traceId);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${selectedTrace.value.traceId}.ndjson.gz`;
    anchor.click();
    URL.revokeObjectURL(url);
  } catch (cause) {
    error.value = messageOf(cause, "Trace 下载失败");
  } finally {
    downloading.value = false;
  }
}

function materializeEvents(events: TraceRawEvent[]): DisplayEvent[] {
  const fragments = new Map<string, TraceRawEvent[]>();
  for (const event of events) {
    if (event.type !== "PAYLOAD_FRAGMENT") continue;
    const groupId = stringValue(event.payload?.fragmentGroupId);
    if (groupId) fragments.set(groupId, [...(fragments.get(groupId) ?? []), event]);
  }
  return events
    .filter((event) => event.type !== "PAYLOAD_FRAGMENT")
    .map((event) => {
      const payload = (event.payload ?? {}) as Record<string, unknown>;
      const fragmentInfo = recordValue(payload.fragmentedPayload);
      const groupId = stringValue(fragmentInfo?.fragmentGroupId);
      let displayPayload = payload;
      if (groupId) {
        try {
          const bytes = (fragments.get(groupId) ?? [])
            .sort((left, right) => numberValue(left.payload?.fragmentIndex) - numberValue(right.payload?.fragmentIndex))
            .flatMap((fragment) => Array.from(base64Bytes(stringValue(fragment.payload?.dataBase64) ?? "")));
          displayPayload = JSON.parse(new TextDecoder().decode(new Uint8Array(bytes))) as Record<string, unknown>;
        } catch {
          displayPayload = payload;
        }
      }
      return { ...event, displayPayload, lane: laneOf(event) };
    });
}

function laneOf(event: TraceRawEvent): TraceLane {
  if (event.type.startsWith("TOOL_")) return "TOOLS";
  if (["CHAT_MESSAGE", "SYSTEM_PROMPT", "CONTEXT_MESSAGES"].includes(event.type)) return "INPUT";
  return "MODEL";
}

function eventTitle(event: DisplayEvent): string {
  const payload = event.displayPayload;
  return stringValue(payload.skillName)
    ?? stringValue(payload.agentName)
    ?? stringValue(payload.tool)
    ?? nestedEventType(payload)
    ?? event.type.replaceAll("_", " ");
}

function eventKind(event: DisplayEvent): "system" | "user" | "context" | "assistant" | "tool" | "subtool" {
  const kind = stringValue(event.displayPayload.recordKind) ?? recordKindOf(event);
  if (kind === "system" || kind === "user" || kind === "context" || kind === "tool" || kind === "subtool") return kind;
  return "assistant";
}

function eventKindLabel(event: DisplayEvent): string {
  return {
    system: "SYSTEM",
    user: "USER",
    context: "CONTEXT",
    assistant: "ASSISTANT",
    tool: "TOOL",
    subtool: "SUBTOOL",
  }[eventKind(event)];
}

function eventPreview(event: DisplayEvent): string {
  const payload = event.displayPayload;
  const candidates: unknown[] = [
    payload.text,
    payload.reasoning,
    payload.content,
    payload.input,
    payload.message,
    payload.system,
    payload.context,
    payload.args,
    payload.result,
    payload.output,
    payload.error,
  ];
  for (const candidate of candidates) {
    const preview = compactPreview(candidate);
    if (preview) return preview;
  }
  return eventTitle(event);
}

function compactPreview(value: unknown): string | null {
  if (typeof value === "string" && value.trim()) return value.replace(/\s+/g, " ").trim();
  if (Array.isArray(value) && value.length) return compactPreview(value[0]) ?? JSON.stringify(value);
  if (value && typeof value === "object") {
    const record = value as Record<string, unknown>;
    return compactPreview(record.text ?? record.content ?? record.title) ?? JSON.stringify(value);
  }
  return null;
}

function eventDurationLabel(event: DisplayEvent): string {
  const duration = numberValue(event.displayPayload.durationMs);
  if (!duration) return "—";
  if (duration < 1000) return `${Math.round(duration)} ms`;
  return `${(duration / 1000).toFixed(duration < 10_000 ? 2 : 1)} s`;
}

function eventTokenLabel(event: DisplayEvent): string {
  const input = numberValue(event.displayPayload.tokensInput);
  const output = numberValue(event.displayPayload.tokensOutput);
  return input || output ? `${input} / ${output}` : "—";
}

function turnLabel(event: DisplayEvent): string {
  if (!event.turnId) return "";
  const turnIds = [...new Set(displayEvents.value.map((candidate) => candidate.turnId).filter(Boolean))];
  return `Turn ${turnIds.indexOf(event.turnId) + 1}`;
}

function isTurnStart(event: DisplayEvent): boolean {
  if (!event.turnId) return false;
  return displayEvents.value.find((candidate) => candidate.turnId === event.turnId)?.eventId === event.eventId;
}

function hasChildren(event: DisplayEvent): boolean {
  return displayEvents.value.some((candidate) => candidate.parentId === event.eventId
    || (event.callId && candidate.parentId === event.callId)
    || candidate.parentId === event.sessionId);
}

function toggleChildren(event: DisplayEvent) {
  const next = new Set(collapsedParents.value);
  const keys = [event.eventId, event.callId, event.sessionId].filter((value): value is string => Boolean(value));
  if (keys.some((key) => next.has(key))) keys.forEach((key) => next.delete(key));
  else keys.forEach((key) => next.add(key));
  collapsedParents.value = next;
}

function isCollapsed(event: DisplayEvent): boolean {
  return collapsedParents.value.has(event.eventId)
    || Boolean(event.callId && collapsedParents.value.has(event.callId))
    || collapsedParents.value.has(event.sessionId);
}

function trackStyle(event: DisplayEvent) {
  const visible = displayEvents.value;
  const index = Math.max(0, visible.findIndex((candidate) => candidate.eventId === event.eventId));
  if (!actualDuration.value) {
    const width = Math.max(1.2, Math.min(8, 88 / Math.max(1, visible.length)));
    return { left: `${index / Math.max(1, visible.length) * 88}%`, width: `${width}%` };
  }
  const ranges = visible.map((candidate) => {
    const start = eventStartMs(candidate);
    return { start, end: start + numberValue(candidate.displayPayload.durationMs) };
  });
  const start = Math.min(...ranges.map((range) => range.start));
  const end = Math.max(...ranges.map((range) => range.end), start + 1);
  const span = Math.max(1, end - start);
  const eventStart = eventStartMs(event);
  const left = (eventStart - start) / span * 88;
  const width = Math.max(1.2, numberValue(event.displayPayload.durationMs) / span * 88);
  return { left: `${left}%`, width: `${width}%` };
}

function eventStartMs(event: DisplayEvent) {
  const explicit = stringValue(event.displayPayload.startedAt);
  const timestamp = Date.parse(explicit ?? event.timestamp);
  return Number.isFinite(timestamp) ? timestamp : 0;
}

function summaryRows(event: DisplayEvent): Array<[string, string]> {
  return [
    ["类型", event.type],
    ["DSH 记录类型", stringValue(event.displayPayload.recordKind) ?? recordKindOf(event)],
    ["泳道", event.lane],
    ["状态", stringValue(event.displayPayload.status) ?? "COMPLETED"],
    ["Agent", stringValue(event.displayPayload.agentName) ?? "—"],
    ["Skill", stringValue(event.displayPayload.skillName) ?? "—"],
    ["Tool", stringValue(event.displayPayload.tool) ?? "—"],
    ["Call ID", event.callId ?? "—"],
    ["父级", event.parentId ?? "—"],
    ["Turn", event.turnId ?? "—"],
    ["Step", event.stepId ?? "—"],
  ];
}

function recordKindOf(event: DisplayEvent) {
  if (event.type === "SYSTEM_PROMPT") return "system";
  if (event.type === "CHAT_MESSAGE") return "user";
  if (event.type === "CONTEXT_MESSAGES") return "context";
  if (event.lane === "TOOLS") return event.parentId ? "subtool" : "tool";
  return nestedPartType(event.displayPayload) === "compaction" ? "compacted" : "message";
}

function resultValue(event: DisplayEvent): unknown {
  return event.displayPayload.result
    ?? event.displayPayload.output
    ?? event.displayPayload.error
    ?? "该事件没有独立结果字段";
}

function sourceValue(event: DisplayEvent) {
  return {
    schemaVersion: event.schemaVersion,
    eventId: event.eventId,
    traceId: event.traceId,
    sessionId: event.sessionId,
    runId: event.runId,
    turnId: event.turnId,
    stepId: event.stepId,
    messageId: event.messageId,
    callId: event.callId,
    globalSequence: event.globalSequence,
    sessionSequence: event.sessionSequence,
    source: "OPENCODE_PLUGIN",
  };
}

function formatJson(value: unknown) {
  return JSON.stringify(value, null, 2);
}

function formatTime(value?: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MiB`;
}

function localDateTime(timestamp: number) {
  const date = new Date(timestamp - new Date(timestamp).getTimezoneOffset() * 60_000);
  return date.toISOString().slice(0, 16);
}

function toInstant(value: string): string | undefined {
  if (!value) return undefined;
  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) ? new Date(timestamp).toISOString() : undefined;
}

function clean(value: string): string | undefined {
  return value.trim() || undefined;
}

function messageOf(cause: unknown, fallback: string) {
  return cause instanceof Error && cause.message ? cause.message : fallback;
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function stringValue(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

function numberValue(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : Number(value) || 0;
}

function nestedEventType(payload: Record<string, unknown>) {
  return stringValue(recordValue(payload.event)?.type);
}

function nestedPartType(payload: Record<string, unknown>) {
  return stringValue(recordValue(recordValue(recordValue(payload.event)?.properties)?.part)?.type);
}

function base64Bytes(value: string): Uint8Array {
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}

/**
 * 三泳道条带和明细行复用同一个选择动作；从总览选择时同步把对应明细滚入视口，
 * 让紧凑轨迹产生明确反馈，而不是只更新屏幕外的检查器。
 */
async function selectTimelineEvent(event: DisplayEvent, revealRow = false) {
  selectedEventId.value = event.eventId;
  inspectorTab.value = "summary";
  if (!revealRow) return;
  await nextTick();
  document.getElementById(`trace-event-${event.eventId}`)?.scrollIntoView?.({ block: "nearest" });
}
</script>

<template>
  <main class="trace-shell">
    <section class="trace-workspace">
      <header class="trace-header">
        <div>
          <h1>Agent Trace</h1>
          <p>服务端与本地端 OpenCode 1.18.4 集中轨迹</p>
        </div>
        <div class="trace-header-actions">
          <span class="trace-user"><ShieldCheck :size="14" /> {{ authStore.currentUser?.username }}</span>
          <button type="button" :disabled="loadingList" @click="loadTraces(1)">
            <RefreshCw :size="15" :class="{ spinning: loadingList }" /> 刷新
          </button>
          <button v-if="selectedTrace" type="button" :disabled="downloading" @click="downloadSelectedTrace">
            <Download :size="15" /> {{ downloading ? '下载中' : 'Session log' }}
          </button>
        </div>
      </header>

      <section class="trace-status-strip" aria-label="Trace 归档状态">
        <article>
          <span>覆盖起点</span>
          <strong>{{ formatTime(coverageStartAt) }}</strong>
          <small>启用插件后开始，不回填历史</small>
        </article>
        <article>
          <span>当前页完整度</span>
          <strong>{{ rolloutCompleteness }}%</strong>
          <small>{{ completeCount }}/{{ page.items.length }} 条已完整归档</small>
        </article>
        <article :class="{ warning: pendingChunks > 0 }">
          <span>上传积压</span>
          <strong>{{ pendingChunks }}</strong>
          <small>服务器已知待确认分片</small>
        </article>
        <article :class="{ danger: droppedEvents > 0 }">
          <span>丢弃事件</span>
          <strong>{{ droppedEvents }}</strong>
          <small>非零 Trace 会标记为 INCOMPLETE</small>
        </article>
      </section>

      <form class="trace-filters" @submit.prevent="loadTraces(1)">
        <label>开始时间<input v-model="filters.startTime" type="datetime-local" /></label>
        <label>结束时间<input v-model="filters.endTime" type="datetime-local" /></label>
        <label>用户<input v-model="filters.user" placeholder="姓名 / 用户 ID" /></label>
        <label>组织<input v-model="filters.organization" placeholder="组织" /></label>
        <label>Agent<input v-model="filters.agentId" placeholder="Agent" /></label>
        <label>Skill<input v-model="filters.skill" placeholder="test-design" /></label>
        <label>Tool<input v-model="filters.tool" placeholder="bash" /></label>
        <label>状态
          <select v-model="filters.status">
            <option value="">全部</option>
            <option value="COMPLETED">已完成</option>
            <option value="ACTIVE">进行中</option>
            <option value="INCOMPLETE">不完整</option>
          </select>
        </label>
        <label>Trace ID<input v-model="filters.traceId" placeholder="trc_…" /></label>
        <label>Run ID<input v-model="filters.runId" placeholder="run_…" /></label>
        <button type="submit"><Search :size="15" /> 查询</button>
      </form>

      <p v-if="error" class="trace-error" role="alert">{{ error }}</p>

      <section class="trace-content">
        <aside class="trace-list-panel">
          <header>
            <div><strong>Trace 目录</strong><span>{{ page.total }} 条</span></div>
            <small>ClickHouse 元数据索引</small>
          </header>
          <div v-if="loadingList" class="trace-empty">正在读取目录…</div>
          <div v-else-if="page.items.length === 0" class="trace-empty">当前筛选范围暂无 Trace</div>
          <button
            v-for="trace in page.items"
            :key="trace.traceId"
            type="button"
            :class="['trace-list-item', { selected: selectedTrace?.traceId === trace.traceId }]"
            @click="openTrace(trace)"
          >
            <span class="trace-list-title">
              <b>{{ trace.agentId || 'opencode' }}</b>
              <em :class="trace.complete ? 'complete' : 'incomplete'">
                {{ trace.complete ? '完整' : '不完整' }}
              </em>
            </span>
            <span class="trace-list-user">{{ trace.username }} · {{ trace.runtimeKind }}</span>
            <code>{{ trace.traceId }}</code>
            <span class="trace-list-meta">
              <time>{{ formatTime(trace.startedAt) }}</time>
              <span>{{ trace.eventCount }} events · {{ formatBytes(trace.archivedBytes) }}</span>
            </span>
          </button>
          <footer class="trace-pagination">
            <button type="button" :disabled="page.page <= 1" @click="loadTraces(page.page - 1)">上一页</button>
            <span>{{ page.page }} / {{ Math.max(1, Math.ceil(page.total / page.size)) }}</span>
            <button type="button" :disabled="page.page * page.size >= page.total" @click="loadTraces(page.page + 1)">下一页</button>
          </footer>
        </aside>

        <section class="trace-timeline-panel">
          <div v-if="!selectedTrace" class="trace-empty trace-empty--hero">
            <Waypoints :size="30" />
            <strong>选择一条 Trace 查看完整轨迹</strong>
            <span>Input / Model / Tools 三泳道会按全局序号对齐。</span>
          </div>
          <template v-else>
            <header class="timeline-header">
              <div>
                <p>{{ selectedTrace.username }} · {{ selectedTrace.agentId || 'opencode' }}</p>
                <h2>{{ selectedTrace.traceId }}</h2>
                <span>{{ selectedTrace.runId || '无 Run ID' }} · 完成水位 {{ selectedTrace.completeThrough }}</span>
              </div>
            </header>

            <div class="timeline-toolbar">
              <div class="timeline-modes" role="toolbar" aria-label="Trace 轨迹控制">
                <button type="button" :class="{ active: actualDuration }" :aria-pressed="actualDuration"
                  title="在等宽块与真实耗时块之间切换" @click="actualDuration = !actualDuration">
                  <svg viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="5.25"/><path d="M8 4.75V8l2.25 1.5"/></svg>
                  Duration
                </button>
                <button type="button" :class="{ active: allTurnsCollapsed }" :aria-pressed="allTurnsCollapsed"
                  title="折叠或展开全部 Turn" @click="allTurnsCollapsed = !allTurnsCollapsed">
                  <span aria-hidden="true">{{ allTurnsCollapsed ? '⊞' : '⊟' }}</span> Turns
                </button>
                <button type="button" :class="{ active: allCallsCollapsed }" :aria-pressed="allCallsCollapsed"
                  title="折叠或展开 Assistant 下的 Tool Call" @click="allCallsCollapsed = !allCallsCollapsed">
                  <span aria-hidden="true">{{ allCallsCollapsed ? '⊞' : '⊟' }}</span> Calls
                </button>
              </div>
              <label class="event-search"><Search :size="14" /><input v-model="eventSearch" type="search" placeholder="搜索" /></label>
            </div>

            <div class="lane-overview" aria-label="三泳道总览">
              <div v-for="lane in (['INPUT', 'MODEL', 'TOOLS'] as TraceLane[])" :key="lane" class="lane-track">
                <span>{{ lane === 'TOOLS' ? 'Tools' : lane === 'MODEL' ? 'Model' : 'Input' }}</span>
                <div>
                  <button
                    v-for="event in displayEvents.filter((item) => item.lane === lane)"
                    :key="event.eventId"
                    type="button"
                    :class="['lane-dot', `lane-dot--${lane.toLowerCase()}`, { selected: selectedEventId === event.eventId }]"
                    :style="trackStyle(event)"
                    :title="eventTitle(event)"
                    :aria-label="`选择 ${eventTitle(event)} 事件`"
                    :aria-pressed="selectedEventId === event.eventId"
                    @click="selectTimelineEvent(event, true)"
                  />
                </div>
              </div>
            </div>

            <div v-if="loadingEvents" class="trace-empty">正在从归档服务器读取正文…</div>
            <div v-else class="event-list">
              <button
                v-for="event in filteredEvents"
                :key="event.eventId"
                :id="`trace-event-${event.eventId}`"
                type="button"
                :class="['event-row', `event-row--${eventKind(event)}`, { selected: selectedEventId === event.eventId, 'turn-start': isTurnStart(event) }]"
                :aria-pressed="selectedEventId === event.eventId"
                @click="selectTimelineEvent(event)"
              >
                <time>
                  <small v-if="isTurnStart(event)">{{ turnLabel(event) }}</small>
                  #{{ event.globalSequence }}
                </time>
                <span class="event-kind">
                  <span class="event-tree-control" @click.stop="hasChildren(event) && toggleChildren(event)">
                    <component :is="isCollapsed(event) ? ChevronRight : ChevronDown" v-if="hasChildren(event)" :size="13" />
                  </span>
                  <b>{{ eventKindLabel(event) }}</b>
                </span>
                <span class="event-card" :title="eventPreview(event)">
                  <b>{{ eventTitle(event) }}</b><small>{{ eventPreview(event) }}</small>
                </span>
                <span class="event-tokens">{{ eventTokenLabel(event) }}</span>
                <span class="event-duration">{{ eventDurationLabel(event) }}</span>
              </button>
            </div>
          </template>
        </section>

        <aside class="trace-inspector">
          <header>
            <div><strong>{{ selectedEvent ? eventTitle(selectedEvent) : '事件检查器' }}</strong><span>{{ selectedEvent?.type }}</span></div>
            <button v-if="selectedEvent" type="button" aria-label="关闭事件检查器" @click="selectedEventId = null">×</button>
          </header>
          <nav aria-label="事件检查器视图">
            <button v-for="tab in (['summary', 'payload', 'result', 'timing', 'source'] as InspectorTab[])" :key="tab"
              type="button" :class="{ active: inspectorTab === tab }" @click="inspectorTab = tab">
              {{ { summary: 'Summary', payload: 'Payload', result: 'Result', timing: 'Timing', source: 'Source' }[tab] }}
            </button>
          </nav>
          <div v-if="!selectedEvent" class="trace-empty">选择事件查看参数、结果与来源</div>
          <div v-else class="inspector-body">
            <dl v-if="inspectorTab === 'summary'">
              <template v-for="row in summaryRows(selectedEvent)" :key="row[0]">
                <dt>{{ row[0] }}</dt><dd>{{ row[1] }}</dd>
              </template>
            </dl>
            <pre v-else-if="inspectorTab === 'payload'">{{ formatJson(selectedEvent.displayPayload) }}</pre>
            <pre v-else-if="inspectorTab === 'result'">{{ formatJson(resultValue(selectedEvent)) }}</pre>
            <dl v-else-if="inspectorTab === 'timing'">
              <dt>发生时间</dt><dd>{{ selectedEvent.timestamp }}</dd>
              <dt>开始时间</dt><dd>{{ stringValue(selectedEvent.displayPayload.startedAt) ?? '—' }}</dd>
              <dt>耗时</dt><dd>{{ selectedEvent.displayPayload.durationMs == null ? '—' : `${numberValue(selectedEvent.displayPayload.durationMs)} ms` }}</dd>
              <dt>计时有效</dt><dd>{{ selectedEvent.displayPayload.timingRecorded === false ? '否（中断 Step）' : '是' }}</dd>
              <dt>TTFT</dt><dd>{{ selectedEvent.displayPayload.ttftMs == null ? '—' : `${numberValue(selectedEvent.displayPayload.ttftMs)} ms` }}</dd>
              <dt>Decode</dt><dd>{{ selectedEvent.displayPayload.decodeMs == null ? '—' : `${numberValue(selectedEvent.displayPayload.decodeMs)} ms` }}</dd>
              <dt>Input tokens</dt><dd>{{ numberValue(selectedEvent.displayPayload.tokensInput) }}</dd>
              <dt>Output tokens</dt><dd>{{ numberValue(selectedEvent.displayPayload.tokensOutput) }}</dd>
              <dt>Reasoning tokens</dt><dd>{{ numberValue(selectedEvent.displayPayload.tokensReasoning) }}</dd>
              <dt>Cache read/write</dt><dd>{{ numberValue(selectedEvent.displayPayload.tokensCacheRead) }} / {{ numberValue(selectedEvent.displayPayload.tokensCacheWrite) }}</dd>
              <dt>Total tokens</dt><dd>{{ numberValue(selectedEvent.displayPayload.tokensTotal) }}</dd>
              <dt>Decode tokens</dt><dd>{{ numberValue(selectedEvent.displayPayload.decodeTokens) }}</dd>
              <dt>Step cost</dt><dd>{{ selectedEvent.displayPayload.cost == null ? '—' : numberValue(selectedEvent.displayPayload.cost) }}</dd>
              <dt>Finish reason</dt><dd>{{ stringValue(selectedEvent.displayPayload.finishReason) ?? '—' }}</dd>
              <dt>全局序号</dt><dd>{{ selectedEvent.globalSequence }}</dd>
              <dt>Session 序号</dt><dd>{{ selectedEvent.sessionSequence }}</dd>
            </dl>
            <pre v-else>{{ formatJson(sourceValue(selectedEvent)) }}</pre>
          </div>
          <footer v-if="selectedTrace">
            <span :class="selectedTrace.redacted ? 'safe' : 'warning'">{{ selectedTrace.redacted ? '已脱敏' : '脱敏未知' }}</span>
            <span>{{ selectedTrace.source }}</span>
            <span>{{ selectedTrace.archiveStatus }}</span>
          </footer>
        </aside>
      </section>
    </section>
  </main>
</template>

<style scoped>
.trace-shell { --ink:#202126; --muted:#777b86; --line:#e8e8ec; --panel:#fff; --fog:#f7f7f8; --input:#2fa36b; --model:#8063ad; --tools:#df851d; display:flex; min-width:1180px; height:100vh; overflow:hidden; color:var(--ink); background:var(--fog); font-family:Inter,"PingFang SC","Microsoft YaHei",sans-serif; }
.trace-rail { width:72px; flex:0 0 72px; display:flex; flex-direction:column; align-items:center; gap:12px; padding:14px 7px 12px; border-right:1px solid var(--line); background:#fff; }
.trace-logo { width:34px; height:34px; object-fit:contain; margin-bottom:10px; }
.trace-rail button { width:56px; min-height:50px; display:flex; flex-direction:column; align-items:center; justify-content:center; gap:3px; border:0; border-radius:12px; background:transparent; color:#707580; font-size:10px; cursor:pointer; }
.trace-rail button:hover { background:#f3f1fb; color:#6548a2; }
.trace-rail button.active { color:#6548a2; background:#eee9fb; box-shadow:inset 3px 0 #7c5ce5; }
.trace-rail-spacer { flex:1; }
.trace-admin-mark { display:flex; flex-direction:column; align-items:center; gap:3px; color:#9699a2; font-size:10px; }
.trace-workspace { min-width:0; flex:1; display:flex; flex-direction:column; }
.trace-header { min-height:88px; display:flex; align-items:center; justify-content:space-between; padding:15px 24px; border-bottom:1px solid var(--line); background:rgba(255,255,255,.94); }
.trace-header h1 { margin:1px 0 2px; font-size:24px; letter-spacing:-.5px; }
.trace-header p { margin:0; color:var(--muted); font-size:12px; }
.trace-header .trace-eyebrow { color:#7458ae; font-size:10px; font-weight:800; letter-spacing:1.4px; }
.trace-header-actions { display:flex; align-items:center; gap:10px; }
.trace-header-actions button,.timeline-header button { display:flex; align-items:center; gap:6px; height:34px; padding:0 13px; border:1px solid #dddde4; border-radius:9px; background:#fff; cursor:pointer; }
.trace-user { padding-right:10px; color:#666b75; font-size:12px; }
.trace-status-strip { display:grid; grid-template-columns:repeat(4,minmax(180px,1fr)); border-bottom:1px solid var(--line); background:#fff; }
.trace-status-strip article { min-height:72px; padding:11px 18px; border-right:1px solid var(--line); }
.trace-status-strip span,.trace-status-strip small { display:block; color:var(--muted); font-size:10px; }
.trace-status-strip strong { display:block; margin:4px 0 2px; font-size:18px; font-variant-numeric:tabular-nums; }
.trace-status-strip .warning strong { color:#a96713; }.trace-status-strip .danger strong { color:#bd3d42; }
.trace-filters { display:grid; grid-template-columns:repeat(10,minmax(92px,1fr)) auto; align-items:end; gap:7px; padding:10px 14px; border-bottom:1px solid var(--line); background:#fafafa; }
.trace-filters label { display:flex; flex-direction:column; gap:4px; color:#777b84; font-size:9px; font-weight:700; }
.trace-filters input,.trace-filters select { min-width:0; height:30px; padding:0 8px; border:1px solid #dddde2; border-radius:7px; background:#fff; color:#313238; font-size:11px; }
.trace-filters button { height:30px; display:flex; align-items:center; gap:5px; padding:0 12px; border:0; border-radius:7px; background:#27282d; color:#fff; cursor:pointer; }
.trace-error { margin:0; padding:7px 14px; background:#fff1f1; color:#a82930; font-size:11px; }
.trace-content { min-height:0; flex:1; display:grid; grid-template-columns:310px minmax(520px,1fr) 350px; overflow:hidden; }
.trace-list-panel,.trace-inspector { min-height:0; display:flex; flex-direction:column; background:#fff; }
.trace-list-panel { border-right:1px solid var(--line); overflow:auto; }
.trace-list-panel>header,.trace-inspector>header { position:sticky; top:0; z-index:2; display:flex; justify-content:space-between; padding:13px 14px; border-bottom:1px solid var(--line); background:#fff; }
.trace-list-panel>header div,.trace-inspector>header div { display:flex; flex-direction:column; gap:2px; }.trace-list-panel>header span,.trace-list-panel>header small,.trace-inspector>header span { color:var(--muted); font-size:10px; }
.trace-list-item { width:100%; padding:12px 14px; text-align:left; border:0; border-bottom:1px solid #efeff1; background:#fff; cursor:pointer; }
.trace-list-item:hover { background:#faf9fd; }.trace-list-item.selected { background:#f2effa; box-shadow:inset 3px 0 #7b5ab4; }
.trace-list-title,.trace-list-meta { display:flex; align-items:center; justify-content:space-between; gap:8px; }.trace-list-title b { font-size:12px; }.trace-list-title em { padding:2px 6px; border-radius:999px; font-size:9px; font-style:normal; }.trace-list-title em.complete { color:#147245; background:#e8f7ef; }.trace-list-title em.incomplete { color:#a8620f; background:#fff2de; }
.trace-list-user,.trace-list-item code,.trace-list-meta { display:block; margin-top:5px; color:#777b85; font-size:10px; }.trace-list-item code { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#5c477f; }.trace-list-meta { display:flex; }
.trace-pagination { position:sticky; bottom:0; display:flex; align-items:center; justify-content:space-between; padding:9px; border-top:1px solid var(--line); background:#fff; font-size:10px; }.trace-pagination button { border:1px solid #dddde2; border-radius:6px; background:#fff; font-size:10px; }
.trace-timeline-panel { min-width:0; min-height:0; display:flex; flex-direction:column; overflow:hidden; background:#fcfcfd; }
.timeline-header { display:flex; align-items:center; justify-content:space-between; padding:14px 17px; border-bottom:1px solid var(--line); background:#fff; }.timeline-header p,.timeline-header h2,.timeline-header span { margin:0; }.timeline-header p { color:#6b507e; font-size:11px; font-weight:700; }.timeline-header h2 { margin:3px 0; font:600 14px ui-monospace,SFMono-Regular,monospace; }.timeline-header span { color:var(--muted); font-size:10px; }
.timeline-toolbar { display:flex; align-items:center; justify-content:space-between; padding:8px 12px; border-bottom:1px solid var(--line); background:#fff; }.timeline-modes { display:flex; gap:3px; padding:3px; border-radius:8px; background:#f0f0f2; }.timeline-modes button { padding:5px 9px; border:0; border-radius:6px; background:transparent; color:#777b85; font-size:10px; cursor:pointer; }.timeline-modes button.active { background:#fff; color:#2b2c31; box-shadow:0 1px 3px #0001; }
.event-search { display:flex; align-items:center; gap:5px; width:190px; padding:0 8px; border:1px solid #dddde2; border-radius:7px; background:#fff; }.event-search input { width:100%; height:27px; border:0; outline:0; font-size:10px; }
.lane-overview { padding:9px 12px; border-bottom:1px solid var(--line); background:#fff; }.lane-track { display:grid; grid-template-columns:68px 1fr; align-items:center; min-height:25px; }.lane-track>span { color:#858994; font-size:9px; }.lane-track>span b { float:right; margin-right:8px; }.lane-track>div { position:relative; height:11px; border-left:1px solid #dedee3; background:transparent; }.lane-dot { position:absolute; top:0; height:14px; min-width:6px; padding:0; border:0; border-radius:0; background:transparent; cursor:pointer; }.lane-dot::after { position:absolute; inset:3px 0; border-radius:2px; background:var(--lane-color); content:""; }.lane-dot--input { --lane-color:var(--input); }.lane-dot--model { --lane-color:var(--model); }.lane-dot--tools { --lane-color:var(--tools); }.lane-dot.selected::after { box-shadow:0 0 0 1px #fff,0 0 0 2px #477bea; }
.event-list { min-height:0; overflow:auto; padding-bottom:30px; }.event-row { width:100%; min-height:54px; display:grid; grid-template-columns:90px 20px 1fr 62px; align-items:center; padding:6px 12px; text-align:left; border:0; border-bottom:1px solid #ededf0; background:#fff; cursor:pointer; }.event-row:hover { background:#fafafa; }.event-row.selected { background:#f6f3fb; }.event-row>time { color:#8a8d96; font:9px ui-monospace,SFMono-Regular,monospace; }.event-row>time small { display:block; margin-top:4px; font-size:8px; }.event-tree-control { color:#777; }.event-card { min-width:0; display:flex; flex-direction:column; gap:4px; padding:7px 10px; border-left:3px solid var(--model); border-radius:5px; background:#f2edf7; }.event-row--input .event-card { border-color:var(--input); background:#eaf7f0; }.event-row--tools .event-card { border-color:var(--tools); background:#fff4e6; }.event-card b { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:11px; }.event-card small { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#777b85; font-size:9px; }.event-duration { text-align:right; color:#777b85; font:9px ui-monospace,SFMono-Regular,monospace; }
.trace-inspector { border-left:1px solid var(--line); }.trace-inspector nav { display:flex; overflow-x:auto; padding:0 8px; border-bottom:1px solid var(--line); }.trace-inspector nav button { padding:10px 7px 8px; border:0; border-bottom:2px solid transparent; background:transparent; color:#7b7e87; font-size:9px; cursor:pointer; }.trace-inspector nav button.active { color:#684a98; border-color:#7b5ab4; }
.inspector-body { min-height:0; flex:1; overflow:auto; padding:13px; }.inspector-body dl { display:grid; grid-template-columns:82px 1fr; gap:10px 8px; margin:0; font-size:10px; }.inspector-body dt { color:#8a8d96; }.inspector-body dd { min-width:0; margin:0; overflow-wrap:anywhere; color:#32333a; }.inspector-body pre { margin:0; white-space:pre-wrap; overflow-wrap:anywhere; font:10px/1.55 ui-monospace,SFMono-Regular,monospace; color:#34353b; }
.trace-inspector>footer { display:flex; flex-wrap:wrap; gap:6px; padding:9px 12px; border-top:1px solid var(--line); }.trace-inspector>footer span { padding:3px 7px; border-radius:999px; background:#f0f0f2; color:#6c7079; font-size:9px; }.trace-inspector>footer .safe { background:#e8f7ef; color:#147245; }.trace-inspector>footer .warning { background:#fff2de; color:#a8620f; }
.trace-empty { display:flex; align-items:center; justify-content:center; min-height:120px; padding:24px; color:#898c95; font-size:11px; text-align:center; }.trace-empty--hero { height:100%; flex-direction:column; gap:8px; }.trace-empty--hero strong { color:#4b4d54; font-size:14px; }.spinning { animation:spin .8s linear infinite; }@keyframes spin { to { transform:rotate(360deg); } }
@media (max-width:1450px) { .trace-content { grid-template-columns:280px minmax(500px,1fr) 310px; }.trace-filters { grid-template-columns:repeat(5,1fr); }.trace-filters button { align-self:end; } }

/*
 * Trace 详情遵循 DeepSeek Harness 轨迹视图的信息密度与交互语义：全宽白底、
 * 32px 控制栏、50px 三泳道和按 Turn 排列的 ledger；平台筛选与权限仍保留自身契约。
 */
.trace-shell { --ink:#202126; --muted:#7d818a; --caption:#a2a7b0; --line:#e7e8eb; --line-soft:#f0f1f3; --input:#39a96b; --model:#8a6bad; --tools:#df851d; min-width:1120px; color:var(--ink); background:#fff; }
.trace-rail { width:58px; flex-basis:58px; gap:14px; padding:13px 0; background:#fbfbfc; }
.trace-logo { width:32px; height:32px; margin-bottom:12px; }
.trace-rail button { width:38px; min-height:38px; height:38px; border-radius:7px; }
.trace-rail button.active { color:#202126; background:#edeef1; box-shadow:inset 2px 0 #477bea; }
.trace-admin-mark { color:#9a9ea7; }
.trace-header { min-height:64px; padding:10px 24px; background:#fff; }
.trace-header h1 { margin:0; font-size:18px; font-weight:650; letter-spacing:-.25px; }
.trace-header p { margin:4px 0 0; font-size:11px; }
.trace-header-actions button { height:32px; border-radius:17px; }
.trace-user { display:flex; align-items:center; gap:5px; padding-right:0; font-size:11px; }
.trace-tabs { height:42px; display:flex; align-items:flex-end; gap:28px; padding:0 24px; border-bottom:1px solid var(--line); background:#fff; }
.trace-tabs button { position:relative; height:42px; padding:0; border:0; background:transparent; color:#888c95; font-size:12px; cursor:pointer; }
.trace-tabs button.active { color:#477bea; }
.trace-tabs button.active::after { position:absolute; right:0; bottom:-1px; left:0; height:2px; background:#477bea; content:""; }
.trace-tabs button:disabled { color:#c4c7cd; cursor:not-allowed; }
.trace-status-strip { display:flex; min-height:34px; background:#fafafa; }
.trace-status-strip article { min-height:34px; display:flex; align-items:center; gap:5px; padding:0 16px; }
.trace-status-strip article span,.trace-status-strip article strong { display:inline; margin:0; font-size:10px; }
.trace-status-strip article strong { color:#3e4147; font-weight:600; }
.trace-status-strip article small { display:none; }
.trace-filters { padding:9px 12px; gap:6px; background:#fff; }
.trace-filters label { gap:3px; font-size:9px; font-weight:400; }
.trace-filters input,.trace-filters select { height:27px; padding:0 7px; border-radius:4px; background:#fafafa; font-size:10px; }
.trace-filters input:focus,.trace-filters select:focus { border-color:#477bea; outline:0; background:#fff; }
.trace-filters button { height:27px; border-radius:4px; background:#2d2f34; }
.trace-content--catalog { display:block; }
.trace-content--catalog .trace-list-panel { height:100%; border-right:0; }
.trace-list-panel>header { height:30px; align-items:center; padding:0 14px; background:#f7f8f9; }
.trace-list-panel>header div { flex-direction:row; align-items:center; gap:6px; }
.trace-list-panel>header small { margin-left:auto; }
.trace-list-item { min-height:58px; display:grid; grid-template-columns:minmax(150px,.8fr) minmax(250px,1.3fr) minmax(140px,.7fr) minmax(260px,1.2fr); align-items:center; column-gap:18px; padding:0 16px; }
.trace-list-item:hover { background:#f8f9fa; }
.trace-list-item.selected { background:#f3f6fc; box-shadow:inset 2px 0 #477bea; }
.trace-list-title { grid-column:1; grid-row:1; }
.trace-list-user { grid-column:2; grid-row:1; margin:0; font-size:10px; }
.trace-list-item code { grid-column:4; grid-row:1; margin:0; font-size:10px; }
.trace-list-meta { grid-column:3; grid-row:1; margin:0; flex-direction:column; align-items:flex-start; font-size:9px; }
.trace-pagination { height:36px; justify-content:center; gap:20px; padding:0; }
.trace-content--trajectory { grid-template-columns:minmax(560px,1fr) 360px; background:#fff; }
.timeline-header { min-height:28px; padding:0 10px; background:#fafafa; }
.timeline-header>div { width:100%; display:flex; align-items:center; gap:18px; overflow:hidden; }
.timeline-header p { display:none; }
.timeline-header h2 { max-width:360px; margin:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:9px; font-weight:500; }
.timeline-header span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:9px; }
.timeline-toolbar { height:32px; padding:0 7px; }
.timeline-modes { gap:2px; padding:0; border-radius:0; background:transparent; }
.timeline-modes button { height:22px; display:inline-flex; align-items:center; gap:4px; padding:0 7px; border-radius:3px; font-size:11px; }
.timeline-modes button.active { background:#f0f1f3; box-shadow:none; }
.timeline-modes svg { width:12px; height:12px; fill:none; stroke:currentColor; stroke-width:1.25; stroke-linecap:round; stroke-linejoin:round; }
.timeline-modes span { font:14px/1 ui-monospace,SFMono-Regular,Menlo,monospace; }
.event-search { width:176px; height:23px; border-radius:4px; background:#fafafa; }
.event-search:focus-within { border-color:#477bea; background:#fff; }
.event-search input { height:21px; font-size:10px; }
.lane-overview { height:50px; display:flex; flex-direction:column; padding:0; background:#fafafa; }
.lane-track { min-height:0; height:14px; display:grid; grid-template-columns:48px 1fr; }
.lane-track:first-child { margin-top:4px; }
.lane-track>span { display:flex; align-items:center; justify-content:flex-end; padding-right:4px; border-right:1px solid var(--line); font-size:10px; }
.lane-track>div { height:14px; border-left:0; background:transparent; }
.lane-dot { top:0; height:14px; min-width:6px; opacity:.86; }
.lane-dot::after { inset:3px 0; border-radius:1px; }
.lane-dot:hover,.lane-dot:focus-visible { opacity:1; outline:0; }
.lane-dot:hover::after,.lane-dot:focus-visible::after { box-shadow:0 0 0 1px #fff,0 0 0 2px #477bea; }
.event-list { flex:1; padding:0; background:#fff; }
.event-row { min-height:32px; grid-template-columns:66px 118px minmax(220px,1fr) 92px 66px; padding:0 8px; border-bottom:1px solid var(--line-soft); }
.event-row:hover { background:#fafafa; }
.event-row.selected { background:#f3f6fc; box-shadow:inset 2px 0 #477bea; }
.event-row.turn-start { border-top:1px solid #dfe1e5; }
.event-row>time { position:relative; align-self:stretch; display:flex; align-items:center; padding-left:4px; border-right:1px solid #e1e3e7; font-size:9px; }
.event-row>time small { position:absolute; top:2px; left:0; margin:0; color:#858a93; font-size:8px; }
.event-row.turn-start>time::after { position:absolute; top:5px; right:-3px; width:5px; height:5px; border-radius:50%; background:#aeb3bc; box-shadow:0 0 0 2px #fff; content:""; }
.event-kind { min-width:0; display:flex; align-items:center; gap:4px; padding-left:10px; }
.event-tree-control { width:14px; flex:0 0 14px; display:grid; place-items:center; }
.event-kind>b { max-width:82px; padding:2px 5px; overflow:hidden; border-radius:4px; text-overflow:ellipsis; white-space:nowrap; font-size:9px; letter-spacing:.2px; }
.event-row--system .event-kind>b { color:#62666e; background:#eef0f2; }
.event-row--user .event-kind>b,.event-row--context .event-kind>b { color:#29935c; background:#e6f7ed; }
.event-row--assistant .event-kind>b { color:#8261a9; background:#efe7f5; }
.event-row--tool .event-kind>b,.event-row--subtool .event-kind>b { color:#d47a12; background:#fff1df; }
.event-row--subtool .event-kind { padding-left:18px; }
.event-card { min-width:0; flex-direction:row; align-items:baseline; gap:12px; padding:0; border:0; border-radius:0; background:transparent; }
.event-row--input .event-card,.event-row--tools .event-card { border:0; background:transparent; }
.event-card b { flex:0 0 auto; max-width:180px; font-size:11px; font-weight:520; }
.event-card small { font:10px ui-monospace,SFMono-Regular,Menlo,monospace; color:#646974; }
.event-tokens,.event-duration { color:#858991; font:9px ui-monospace,SFMono-Regular,Menlo,monospace; }
.trace-inspector>header { min-height:42px; align-items:center; }
.trace-inspector>header button { margin-left:auto; border:0; background:transparent; color:#777c85; font-size:20px; cursor:pointer; }
.trace-inspector nav button.active { color:#477bea; border-color:#477bea; }
button:focus-visible,input:focus-visible,select:focus-visible { outline:1px solid #477bea; outline-offset:1px; }
@media (prefers-reduced-motion:reduce) { .spinning { animation:none; } }

/* 控制台嵌入态：系统导航已经提供入口，内部只保留“基础信息列表 → 原位展开轨迹”。 */
.trace-rail { display:none; }
.trace-header { min-height:54px; padding:7px 16px; }
.trace-content { grid-template-columns:286px minmax(520px,1fr) 330px; }
.trace-list-panel { border-right:1px solid var(--line); }
.trace-list-item { min-height:40px; display:block; padding:3px 10px; }
.trace-list-title,.trace-list-meta { display:flex; }
.trace-list-title { grid-column:auto; grid-row:auto; }
.trace-list-user,.trace-list-item code,.trace-list-meta { grid-column:auto; grid-row:auto; margin-top:2px; }
.trace-list-meta { flex-direction:row; align-items:center; }
.trace-list-title em { border-radius:4px; }
.timeline-header { min-height:24px; }
.timeline-toolbar { height:28px; }
.lane-overview { height:44px; }
.lane-track { height:13px; }
.event-row { min-height:28px; }
.event-card { gap:8px; }
.event-card b { font-size:10px; }
.event-card small { font-size:9px; }
.event-row>time small { top:0; }
@media (max-width:1380px) { .trace-content { grid-template-columns:260px minmax(500px,1fr) 300px; } }
</style>
