<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { createBackendApiClient } from "@test-agent/backend-api";
import type {
  PageResponse,
  TraceCatalog,
  TraceQueryParams,
  TraceRawEvent,
} from "@test-agent/shared-types";
import {
  Activity,
  ArrowLeft,
  ChevronDown,
  ChevronRight,
  Download,
  RefreshCw,
  Search,
  ShieldCheck,
} from "lucide-vue-next";
import logoUrl from "../assets/figma/logo.png";
import { useAuthStore } from "../stores/authStore";

type InspectorTab = "summary" | "payload" | "result" | "timing" | "source";
type TimelineMode = "duration" | "turns" | "calls";
type TraceLane = "INPUT" | "MODEL" | "TOOLS";
type DisplayEvent = TraceRawEvent & {
  displayPayload: Record<string, unknown>;
  lane: TraceLane;
};

const API_BASE_URL = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: API_BASE_URL });
const router = useRouter();
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
const timelineMode = ref<TimelineMode>("duration");
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
  return displayEvents.value.filter((event) => {
    if (event.parentId && collapsedParents.value.has(event.parentId)) return false;
    return !keyword || JSON.stringify(event).toLowerCase().includes(keyword);
  });
});
const laneCounts = computed(() => ({
  INPUT: displayEvents.value.filter((event) => event.lane === "INPUT").length,
  MODEL: displayEvents.value.filter((event) => event.lane === "MODEL").length,
  TOOLS: displayEvents.value.filter((event) => event.lane === "TOOLS").length,
}));
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
const maxSequence = computed(() => Math.max(1, ...displayEvents.value.map((event) => event.globalSequence)));
const minSequence = computed(() => Math.min(...displayEvents.value.map((event) => event.globalSequence), 1));

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
  for (let pageIndex = 0; pageIndex < 20; pageIndex += 1) {
    const response = await api.getTraceEvents(traceId, afterSequence, 500);
    result.push(...response.items);
    if (response.items.length < 500) break;
    const next = Math.max(...response.items.map((event) => event.globalSequence));
    if (next <= afterSequence) break;
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

function eventSubtitle(event: DisplayEvent): string {
  return [event.type, event.callId, stringValue(event.displayPayload.status)].filter(Boolean).join(" · ");
}

function hasChildren(event: DisplayEvent): boolean {
  return displayEvents.value.some((candidate) => candidate.parentId === event.eventId
    || (event.callId && candidate.parentId === event.callId));
}

function toggleChildren(event: DisplayEvent) {
  const next = new Set(collapsedParents.value);
  const keys = [event.eventId, event.callId].filter((value): value is string => Boolean(value));
  if (keys.some((key) => next.has(key))) keys.forEach((key) => next.delete(key));
  else keys.forEach((key) => next.add(key));
  collapsedParents.value = next;
}

function isCollapsed(event: DisplayEvent): boolean {
  return collapsedParents.value.has(event.eventId)
    || Boolean(event.callId && collapsedParents.value.has(event.callId));
}

function trackStyle(event: DisplayEvent) {
  const range = Math.max(1, maxSequence.value - minSequence.value);
  const left = ((event.globalSequence - minSequence.value) / range) * 88;
  const duration = numberValue(event.displayPayload.durationMs);
  const width = duration > 0 ? Math.min(18, Math.max(2.5, Math.log10(duration + 10) * 2.2)) : 2.5;
  return { left: `${left}%`, width: `${width}%` };
}

function summaryRows(event: DisplayEvent): Array<[string, string]> {
  return [
    ["类型", event.type],
    ["泳道", event.lane],
    ["状态", stringValue(event.displayPayload.status) ?? "COMPLETED"],
    ["Agent", stringValue(event.displayPayload.agentName) ?? "—"],
    ["Skill", stringValue(event.displayPayload.skillName) ?? "—"],
    ["Tool", stringValue(event.displayPayload.tool) ?? "—"],
    ["Call ID", event.callId ?? "—"],
    ["父级", event.parentId ?? "—"],
  ];
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

function base64Bytes(value: string): Uint8Array {
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
}
</script>

<template>
  <main class="trace-shell">
    <aside class="trace-rail" aria-label="主导航">
      <img class="trace-logo" :src="logoUrl" alt="TestAgent" />
      <button type="button" title="返回工作台" aria-label="返回工作台" @click="router.push({ name: 'workbench' })">
        <ArrowLeft :size="19" />
        <span>工作台</span>
      </button>
      <button type="button" class="active" title="Trace" aria-current="page">
        <Activity :size="19" />
        <span>Trace</span>
      </button>
      <div class="trace-rail-spacer" />
      <div class="trace-admin-mark" title="仅超级管理员可访问">
        <ShieldCheck :size="17" />
        <span>超管</span>
      </div>
    </aside>

    <section class="trace-workspace">
      <header class="trace-header">
        <div>
          <p class="trace-eyebrow">OPENCODE OBSERVABILITY</p>
          <h1>Agent Trace</h1>
          <p>集中查看服务端与本地端 Agent 全轨迹；正文读取不依赖客户端在线。</p>
        </div>
        <div class="trace-header-actions">
          <span class="trace-user">{{ authStore.currentUser?.username }}</span>
          <button type="button" :disabled="loadingList" @click="loadTraces(1)">
            <RefreshCw :size="15" :class="{ spinning: loadingList }" /> 刷新
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
            <Activity :size="30" />
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
              <button type="button" :disabled="downloading" @click="downloadSelectedTrace">
                <Download :size="15" /> {{ downloading ? '下载中' : 'Session log' }}
              </button>
            </header>

            <div class="timeline-toolbar">
              <div class="timeline-modes" role="tablist" aria-label="Trace 时间线模式">
                <button v-for="mode in (['duration', 'turns', 'calls'] as TimelineMode[])" :key="mode"
                  type="button" :class="{ active: timelineMode === mode }" @click="timelineMode = mode">
                  {{ mode === 'duration' ? 'Duration' : mode === 'turns' ? 'Turns' : 'Calls' }}
                </button>
              </div>
              <label class="event-search"><Search :size="14" /><input v-model="eventSearch" placeholder="搜索轨迹正文" /></label>
            </div>

            <div class="lane-overview" aria-label="三泳道总览">
              <div v-for="lane in (['INPUT', 'MODEL', 'TOOLS'] as TraceLane[])" :key="lane" class="lane-track">
                <span>{{ lane === 'TOOLS' ? 'Tools' : lane === 'MODEL' ? 'Model' : 'Input' }} <b>{{ laneCounts[lane] }}</b></span>
                <div>
                  <i v-for="event in displayEvents.filter((item) => item.lane === lane)" :key="event.eventId"
                    :class="`lane-dot lane-dot--${lane.toLowerCase()}`" :style="trackStyle(event)"
                    :title="eventTitle(event)" @click="selectedEventId = event.eventId" />
                </div>
              </div>
            </div>

            <div v-if="loadingEvents" class="trace-empty">正在从归档服务器读取正文…</div>
            <div v-else class="event-list">
              <button
                v-for="event in filteredEvents"
                :key="event.eventId"
                type="button"
                :class="['event-row', `event-row--${event.lane.toLowerCase()}`, { selected: selectedEventId === event.eventId }]"
                @click="selectedEventId = event.eventId"
              >
                <time>#{{ event.globalSequence }}<small>{{ formatTime(event.timestamp) }}</small></time>
                <span class="event-tree-control" @click.stop="hasChildren(event) && toggleChildren(event)">
                  <component :is="isCollapsed(event) ? ChevronRight : ChevronDown" v-if="hasChildren(event)" :size="14" />
                </span>
                <span class="event-card">
                  <b>{{ eventTitle(event) }}</b>
                  <small>{{ eventSubtitle(event) }}</small>
                </span>
                <span class="event-duration">
                  {{ numberValue(event.displayPayload.durationMs) ? `${numberValue(event.displayPayload.durationMs)} ms` : '—' }}
                </span>
              </button>
            </div>
          </template>
        </section>

        <aside class="trace-inspector">
          <header>
            <div><strong>{{ selectedEvent ? eventTitle(selectedEvent) : '事件检查器' }}</strong><span>{{ selectedEvent?.type }}</span></div>
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
              <dt>耗时</dt><dd>{{ numberValue(selectedEvent.displayPayload.durationMs) }} ms</dd>
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
.lane-overview { padding:9px 12px; border-bottom:1px solid var(--line); background:#fff; }.lane-track { display:grid; grid-template-columns:68px 1fr; align-items:center; min-height:25px; }.lane-track>span { color:#858994; font-size:9px; }.lane-track>span b { float:right; margin-right:8px; }.lane-track>div { position:relative; height:11px; border-left:1px solid #dedee3; background:repeating-linear-gradient(90deg,#f5f5f6 0,#f5f5f6 1px,transparent 1px,transparent 10%); }.lane-dot { position:absolute; top:2px; height:7px; min-width:3px; border-radius:2px; cursor:pointer; }.lane-dot--input { background:var(--input); }.lane-dot--model { background:var(--model); }.lane-dot--tools { background:var(--tools); }
.event-list { min-height:0; overflow:auto; padding-bottom:30px; }.event-row { width:100%; min-height:54px; display:grid; grid-template-columns:90px 20px 1fr 62px; align-items:center; padding:6px 12px; text-align:left; border:0; border-bottom:1px solid #ededf0; background:#fff; cursor:pointer; }.event-row:hover { background:#fafafa; }.event-row.selected { background:#f6f3fb; }.event-row>time { color:#8a8d96; font:9px ui-monospace,SFMono-Regular,monospace; }.event-row>time small { display:block; margin-top:4px; font-size:8px; }.event-tree-control { color:#777; }.event-card { min-width:0; display:flex; flex-direction:column; gap:4px; padding:7px 10px; border-left:3px solid var(--model); border-radius:5px; background:#f2edf7; }.event-row--input .event-card { border-color:var(--input); background:#eaf7f0; }.event-row--tools .event-card { border-color:var(--tools); background:#fff4e6; }.event-card b { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:11px; }.event-card small { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#777b85; font-size:9px; }.event-duration { text-align:right; color:#777b85; font:9px ui-monospace,SFMono-Regular,monospace; }
.trace-inspector { border-left:1px solid var(--line); }.trace-inspector nav { display:flex; overflow-x:auto; padding:0 8px; border-bottom:1px solid var(--line); }.trace-inspector nav button { padding:10px 7px 8px; border:0; border-bottom:2px solid transparent; background:transparent; color:#7b7e87; font-size:9px; cursor:pointer; }.trace-inspector nav button.active { color:#684a98; border-color:#7b5ab4; }
.inspector-body { min-height:0; flex:1; overflow:auto; padding:13px; }.inspector-body dl { display:grid; grid-template-columns:82px 1fr; gap:10px 8px; margin:0; font-size:10px; }.inspector-body dt { color:#8a8d96; }.inspector-body dd { min-width:0; margin:0; overflow-wrap:anywhere; color:#32333a; }.inspector-body pre { margin:0; white-space:pre-wrap; overflow-wrap:anywhere; font:10px/1.55 ui-monospace,SFMono-Regular,monospace; color:#34353b; }
.trace-inspector>footer { display:flex; flex-wrap:wrap; gap:6px; padding:9px 12px; border-top:1px solid var(--line); }.trace-inspector>footer span { padding:3px 7px; border-radius:999px; background:#f0f0f2; color:#6c7079; font-size:9px; }.trace-inspector>footer .safe { background:#e8f7ef; color:#147245; }.trace-inspector>footer .warning { background:#fff2de; color:#a8620f; }
.trace-empty { display:flex; align-items:center; justify-content:center; min-height:120px; padding:24px; color:#898c95; font-size:11px; text-align:center; }.trace-empty--hero { height:100%; flex-direction:column; gap:8px; }.trace-empty--hero strong { color:#4b4d54; font-size:14px; }.spinning { animation:spin .8s linear infinite; }@keyframes spin { to { transform:rotate(360deg); } }
@media (max-width:1450px) { .trace-content { grid-template-columns:280px minmax(500px,1fr) 310px; }.trace-filters { grid-template-columns:repeat(5,1fr); }.trace-filters button { align-self:end; } }
</style>
