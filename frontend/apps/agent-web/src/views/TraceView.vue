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
  ChevronLeft,
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
type TimelineRange = { left: number; width: number };
type TimelineSelection = { start: number; end: number };

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
const hoveredEventId = ref<string | null>(null);
const timelineSelection = ref<TimelineSelection | null>(null);
const actualDuration = ref(false);
const allTurnsCollapsed = ref(false);
const allCallsCollapsed = ref(false);
const catalogCollapsed = ref(false);
const inspectorCollapsed = ref(false);
const catalogWidth = ref<number | null>(null);
const inspectorWidth = ref<number | null>(null);
const resizingPanel = ref<"catalog" | "inspector" | null>(null);
const inspectorTab = ref<InspectorTab>("summary");
const eventSearch = ref("");
const collapsedParents = ref<Set<string>>(new Set());
const loadingList = ref(false);
const loadingEvents = ref(false);
const downloading = ref(false);
const error = ref<string | null>(null);
let rangeStart = 0;
let rangePointerActive = false;
let rangeDragging = false;
let suppressTimelineClick = false;
let panelResizeStartX = 0;
let panelResizeStartWidth = 0;

const traceContentStyle = computed<Record<string, string>>(() => {
  const style: Record<string, string> = {};
  // 百分比上限保证浏览器窗口变窄后仍给中间轨迹留出可用空间。
  if (catalogWidth.value !== null) {
    style["--trace-catalog-width"] = `clamp(190px, ${catalogWidth.value}px, min(420px, 32%))`;
  }
  if (inspectorWidth.value !== null) {
    style["--trace-inspector-width"] = `clamp(240px, ${inspectorWidth.value}px, min(520px, 36%))`;
  }
  return style;
});

const displayEvents = computed<DisplayEvent[]>(() => materializeEvents(rawEvents.value));
const trajectoryEvents = computed<DisplayEvent[]>(() => buildTrajectoryEvents(displayEvents.value));
const selectedEvent = computed(() =>
  trajectoryEvents.value.find((event) => event.eventId === selectedEventId.value)
    ?? displayEvents.value.find((event) => event.eventId === selectedEventId.value)
    ?? null
);
const selectedTrajectoryEvent = computed(() =>
  trajectoryEvents.value.find((event) => event.eventId === selectedEventId.value) ?? null
);
const hoveredEvent = computed(() =>
  trajectoryEvents.value.find((event) => event.eventId === hoveredEventId.value) ?? null
);
const filteredEvents = computed(() => {
  const keyword = eventSearch.value.trim().toLowerCase();
  const firstByTurn = new Map<string, string>();
  for (const event of trajectoryEvents.value) {
    if (event.turnId && !firstByTurn.has(event.turnId)) firstByTurn.set(event.turnId, event.eventId);
  }
  return trajectoryEvents.value.filter((event) => {
    if (event.parentId && collapsedParents.value.has(event.parentId)) return false;
    if (allCallsCollapsed.value && event.lane === "TOOLS") return false;
    if (allTurnsCollapsed.value && event.turnId && firstByTurn.get(event.turnId) !== event.eventId) return false;
    return !keyword || JSON.stringify(event).toLowerCase().includes(keyword);
  });
});
const completeCount = computed(() => page.value.items.filter(isEffectivelyComplete).length);
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

function startPanelResize(side: "catalog" | "inspector", event: PointerEvent) {
  if (event.button !== 0) return;
  const handle = event.currentTarget as HTMLElement;
  const content = handle.closest<HTMLElement>(".trace-content");
  const panel = content?.querySelector<HTMLElement>(side === "catalog" ? ".trace-list-panel" : ".trace-inspector");
  if (!panel) return;
  resizingPanel.value = side;
  panelResizeStartX = event.clientX;
  panelResizeStartWidth = panel.getBoundingClientRect().width;
  handle.setPointerCapture?.(event.pointerId);
  event.preventDefault();
}

function updatePanelResize(side: "catalog" | "inspector", event: PointerEvent) {
  if (resizingPanel.value !== side) return;
  const delta = event.clientX - panelResizeStartX;
  setPanelWidth(side, panelResizeStartWidth + (side === "catalog" ? delta : -delta), event.currentTarget as HTMLElement);
}

function finishPanelResize(side: "catalog" | "inspector", event: PointerEvent) {
  if (resizingPanel.value !== side) return;
  resizingPanel.value = null;
  const handle = event.currentTarget as HTMLElement;
  if (handle.hasPointerCapture?.(event.pointerId)) handle.releasePointerCapture(event.pointerId);
}

function resizePanelWithKeyboard(side: "catalog" | "inspector", event: KeyboardEvent) {
  if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
  const handle = event.currentTarget as HTMLElement;
  const content = handle.closest<HTMLElement>(".trace-content");
  const panel = content?.querySelector<HTMLElement>(side === "catalog" ? ".trace-list-panel" : ".trace-inspector");
  if (!panel) return;
  const separatorDelta = event.key === "ArrowRight" ? 16 : -16;
  setPanelWidth(side, panel.getBoundingClientRect().width + (side === "catalog" ? separatorDelta : -separatorDelta), handle);
  event.preventDefault();
}

function setPanelWidth(side: "catalog" | "inspector", requestedWidth: number, handle: HTMLElement) {
  const contentWidth = handle.closest<HTMLElement>(".trace-content")?.getBoundingClientRect().width ?? window.innerWidth;
  const minimum = side === "catalog" ? 190 : 240;
  const absoluteMaximum = side === "catalog" ? 420 : 520;
  const responsiveMaximum = contentWidth * (side === "catalog" ? 0.32 : 0.36);
  const maximum = Math.max(minimum, Math.min(absoluteMaximum, responsiveMaximum));
  const width = Math.round(Math.min(Math.max(minimum, requestedWidth), maximum));
  if (side === "catalog") catalogWidth.value = width;
  else inspectorWidth.value = width;
}

function resetPanelWidth(side: "catalog" | "inspector") {
  if (side === "catalog") catalogWidth.value = null;
  else inspectorWidth.value = null;
}

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
  hoveredEventId.value = null;
  timelineSelection.value = null;
  rawEvents.value = [];
  loadingEvents.value = true;
  error.value = null;
  try {
    const [detail, events] = await Promise.all([api.getTrace(trace.traceId), loadAllEvents(trace.traceId)]);
    selectedTrace.value = detail;
    rawEvents.value = events;
    selectedEventId.value = buildTrajectoryEvents(materializeEvents(events))[0]?.eventId ?? null;
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

/**
 * DSH 的三泳道展示语义记录，而不是传输级事件。OpenCode 的 delta、状态和生命周期原始事件
 * 仍完整保留在服务器归档/下载中；页面把它们汇入 Assistant 摘要，避免数千个分片互相覆盖。
 */
function buildTrajectoryEvents(events: DisplayEvent[]): DisplayEvent[] {
  const terminalCalls = new Set(events
    .filter((event) => event.type === "TOOL_EXECUTE_AFTER" && event.callId)
    .map((event) => event.callId as string));
  const metricMessages = new Set(events
    .filter((event) => event.type === "ASSISTANT_STEP_METRICS" && event.messageId)
    .map((event) => event.messageId as string));
  const userMessages = new Set(events
    .filter((event) => event.type === "CHAT_MESSAGE" && event.messageId)
    .map((event) => event.messageId as string));
  const messageRoles = new Map<string, string>();
  const assistantParts = new Map<string, Map<string, {
    kind: string;
    text: string;
    event: DisplayEvent;
    finalized: boolean;
  }>>();
  const pendingTools = new Map<string, DisplayEvent>();

  for (const event of events) {
    if (event.type !== "OPENCODE_EVENT" || nestedEventType(event.displayPayload) !== "message.updated") continue;
    const properties = recordValue(recordValue(event.displayPayload.event)?.properties);
    const info = recordValue(properties?.info);
    const messageId = stringValue(info?.id) ?? event.messageId;
    const role = stringValue(info?.role);
    if (messageId && role) messageRoles.set(messageId, role);
  }

  for (const event of events) {
    if (event.type !== "OPENCODE_EVENT") continue;
    const nestedType = nestedEventType(event.displayPayload);
    const properties = recordValue(recordValue(event.displayPayload.event)?.properties);
    if (nestedType === "message.part.delta") {
      const messageId = stringValue(properties?.messageID) ?? event.messageId;
      const partId = stringValue(properties?.partID);
      const field = stringValue(properties?.field);
      const delta = stringValue(properties?.delta);
      if (!messageId || !partId || !delta || !["text", "reasoning"].includes(field ?? "")) continue;
      const parts = assistantParts.get(messageId) ?? new Map();
      const current = parts.get(partId);
      if (!current?.finalized) {
        parts.set(partId, {
          kind: field as string,
          text: `${current?.text ?? ""}${delta}`,
          event,
          finalized: false,
        });
        assistantParts.set(messageId, parts);
      }
      continue;
    }
    if (nestedType !== "message.part.updated") continue;
    const part = recordValue(properties?.part);
    const messageId = stringValue(part?.messageID) ?? stringValue(properties?.messageID);
    const partId = stringValue(part?.id);
    const kind = stringValue(part?.type);
    if (kind === "tool") {
      const callId = stringValue(part?.callID) ?? event.callId ?? partId;
      if (!callId || terminalCalls.has(callId)) continue;
      const state = recordValue(part?.state);
      const status = (stringValue(state?.status) ?? "pending").toUpperCase();
      pendingTools.set(callId, {
        ...event,
        callId,
        lane: "TOOLS",
        displayPayload: {
          ...event.displayPayload,
          recordKind: "tool",
          status,
          tool: stringValue(part?.tool) ?? "tool",
          args: state?.input,
          result: state?.output,
          error: state?.error,
        },
      });
      continue;
    }
    const text = compactPreview(part?.text ?? part?.content ?? part?.reasoning);
    if (!messageId || !partId || !kind || !text || !["text", "reasoning"].includes(kind)) continue;
    const parts = assistantParts.get(messageId) ?? new Map();
    parts.set(partId, { kind, text, event, finalized: true });
    assistantParts.set(messageId, parts);
  }

  const seenSystemPrompts = new Set<string>();
  const semanticTypes = new Set([
    "CHAT_MESSAGE",
    "SYSTEM_PROMPT",
    "CONTEXT_MESSAGES",
    "COMPACTION_CONTEXT",
    "ASSISTANT_STEP_METRICS",
    "TOOL_EXECUTE_AFTER",
  ]);
  const result: DisplayEvent[] = [];
  for (const event of events) {
    if (event.type === "TOOL_EXECUTE_BEFORE") {
      if (!event.callId || terminalCalls.has(event.callId)) continue;
    } else if (!semanticTypes.has(event.type)) {
      continue;
    }
    if (event.type === "SYSTEM_PROMPT") {
      const signature = JSON.stringify(event.displayPayload.system ?? event.displayPayload);
      if (seenSystemPrompts.has(signature)) continue;
      seenSystemPrompts.add(signature);
    }
    if (event.type === "ASSISTANT_STEP_METRICS" && event.messageId) {
      const parts = [...(assistantParts.get(event.messageId)?.values() ?? [])];
      const text = parts.filter((part) => part.kind === "text").map((part) => part.text).join("\n");
      const reasoning = parts.filter((part) => part.kind === "reasoning").map((part) => part.text).join("\n");
      result.push({
        ...event,
        displayPayload: {
          ...event.displayPayload,
          ...(text ? { text } : {}),
          ...(reasoning ? { reasoning } : {}),
        },
      });
      continue;
    }
    result.push(event);
  }

  // 未闭合 Run 尚无 Step/Tool 终态：把数百个流式分片聚合成一条进行中记录，而不是显示空白或重新堆叠 delta。
  for (const [messageId, partMap] of assistantParts) {
    if (metricMessages.has(messageId) || userMessages.has(messageId) || messageRoles.get(messageId) === "user") continue;
    const parts = [...partMap.values()];
    if (parts.length === 0) continue;
    const event = parts.reduce((latest, part) =>
      part.event.globalSequence > latest.globalSequence ? part.event : latest, parts[0].event);
    const startedAt = Math.min(...parts.map((part) => Date.parse(part.event.timestamp)).filter(Number.isFinite));
    const text = parts.filter((part) => part.kind === "text").map((part) => part.text).join("\n");
    const reasoning = parts.filter((part) => part.kind === "reasoning").map((part) => part.text).join("\n");
    result.push({
      ...event,
      messageId,
      lane: "MODEL",
      displayPayload: {
        ...event.displayPayload,
        recordKind: "message",
        status: "ACTIVE",
        ...(Number.isFinite(startedAt) ? { startedAt: new Date(startedAt).toISOString() } : {}),
        durationMs: Number.isFinite(startedAt) ? Math.max(0, Date.parse(event.timestamp) - startedAt) : 0,
        ...(text ? { text } : {}),
        ...(reasoning ? { reasoning } : {}),
      },
    });
  }
  result.push(...pendingTools.values());

  // 只有生命周期/插件事件的不完整 Trace 仍应可打开；按事件类型保留最后一条，避免同类噪声淹没页面。
  if (result.length === 0) {
    const lifecycle = new Map<string, DisplayEvent>();
    for (const event of events) {
      if (event.type === "PAYLOAD_FRAGMENT") continue;
      const type = nestedEventType(event.displayPayload) ?? event.type;
      lifecycle.set(type, {
        ...event,
        lane: "INPUT",
        displayPayload: {
          ...event.displayPayload,
          recordKind: "context",
          status: "ACTIVE",
          content: type,
        },
      });
    }
    result.push(...lifecycle.values());
  }
  return result.sort((left, right) => left.globalSequence - right.globalSequence);
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
    payload.messages,
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
  const turnIds = [...new Set(trajectoryEvents.value.map((candidate) => candidate.turnId).filter(Boolean))];
  return `Turn ${turnIds.indexOf(event.turnId) + 1}`;
}

function isTurnStart(event: DisplayEvent): boolean {
  if (!event.turnId) return false;
  return trajectoryEvents.value.find((candidate) => candidate.turnId === event.turnId)?.eventId === event.eventId;
}

function hasChildren(event: DisplayEvent): boolean {
  return trajectoryEvents.value.some((candidate) => candidate.parentId === event.eventId
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

function trackRange(event: DisplayEvent): TimelineRange {
  const visible = trajectoryEvents.value;
  const index = Math.max(0, visible.findIndex((candidate) => candidate.eventId === event.eventId));
  if (!actualDuration.value) {
    const width = 100 / Math.max(1, visible.length);
    return { left: index * width, width };
  }
  const ranges = visible.map((candidate) => {
    const start = eventStartMs(candidate);
    return { start, end: start + numberValue(candidate.displayPayload.durationMs) };
  });
  const start = Math.min(...ranges.map((range) => range.start));
  const end = Math.max(...ranges.map((range) => range.end), start + 1);
  const span = Math.max(1, end - start);
  const eventStart = eventStartMs(event);
  const left = (eventStart - start) / span * 100;
  const width = numberValue(event.displayPayload.durationMs) / span * 100;
  return { left, width };
}

function trackStyle(event: DisplayEvent): Record<string, string> {
  const range = trackRange(event);
  const lane = { INPUT: 0, MODEL: 1, TOOLS: 2 }[event.lane];
  const gap = Math.max(0, range.width * 0.08);
  return {
    "--trajectory-span-left": `${range.left}%`,
    "--trajectory-span-width": `${range.width}%`,
    "--trajectory-span-gap": `min(${gap}%, 1px)`,
    "--trajectory-span-lane": String(lane),
  };
}

function selectionStyle(event: DisplayEvent): Record<string, string> {
  const range = trackRange(event);
  return {
    "--trajectory-selection-left": `${range.left}%`,
    "--trajectory-selection-width": `${Math.max(range.width, 0.16)}%`,
  };
}

function rangeSelectionStyle(selection: TimelineSelection): Record<string, string> {
  return {
    "--trajectory-selection-left": `${selection.start}%`,
    "--trajectory-selection-width": `${selection.end - selection.start}%`,
  };
}

function tooltipStyle(event: DisplayEvent): Record<string, string> {
  const range = trackRange(event);
  const anchor = Math.min(90, Math.max(10, range.left + range.width / 2));
  return { "--trajectory-tooltip-left": `${anchor}%` };
}

function assistantPhaseStyle(event: DisplayEvent): Record<string, string> {
  const duration = numberValue(event.displayPayload.durationMs);
  const ttft = numberValue(event.displayPayload.ttftMs);
  const prefill = duration > 0 ? Math.min(100, Math.max(0, ttft / duration * 100)) : 0;
  return { "--trajectory-prefill-width": `${prefill}%` };
}

function eventInTimelineSelection(event: DisplayEvent): boolean {
  const selection = timelineSelection.value;
  if (!selection) return true;
  const range = trackRange(event);
  const end = range.left + Math.max(range.width, 0.01);
  return end >= selection.start && range.left <= selection.end;
}

function startTimelineRange(event: PointerEvent) {
  if (event.button !== 0) return;
  rangePointerActive = true;
  rangeDragging = false;
  rangeStart = timelinePercent(event);
}

function updateTimelineRange(event: PointerEvent) {
  if (!rangePointerActive || (event.buttons & 1) === 0) return;
  const current = timelinePercent(event);
  if (!rangeDragging && Math.abs(current - rangeStart) < 0.5) return;
  if (!rangeDragging) {
    rangeDragging = true;
    // 普通点选不捕获指针；只有确认进入拖拽后才捕获，避免色块 click 被重定向到泳道容器。
    (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
  }
  timelineSelection.value = {
    start: Math.min(rangeStart, current),
    end: Math.max(rangeStart, current),
  };
}

async function finishTimelineRange(event: PointerEvent) {
  if (!rangePointerActive) return;
  const target = event.currentTarget as HTMLElement;
  if (target.hasPointerCapture?.(event.pointerId)) target.releasePointerCapture(event.pointerId);
  rangePointerActive = false;
  if (!rangeDragging || !timelineSelection.value) return;
  rangeDragging = false;
  // 只吞掉 pointerup 紧随产生的 click；下一轮事件循环立即恢复，避免快速点选被误伤。
  suppressTimelineClick = true;
  window.setTimeout(() => { suppressTimelineClick = false; }, 0);
  const first = trajectoryEvents.value.find(eventInTimelineSelection);
  if (!first) return;
  await nextTick();
  document.getElementById(`trace-event-${first.eventId}`)?.scrollIntoView?.({ block: "nearest" });
}

function cancelTimelineRange(event: PointerEvent) {
  const target = event.currentTarget as HTMLElement;
  if (target.hasPointerCapture?.(event.pointerId)) target.releasePointerCapture(event.pointerId);
  rangePointerActive = false;
  rangeDragging = false;
}

function timelinePercent(event: PointerEvent): number {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  if (rect.width <= 0) return 0;
  return Math.min(100, Math.max(0, (event.clientX - rect.left) / rect.width * 100));
}

function eventStartMs(event: DisplayEvent) {
  const explicit = stringValue(event.displayPayload.startedAt);
  if (!explicit && event.type === "TOOL_EXECUTE_AFTER" && event.callId) {
    const before = displayEvents.value.find((candidate) =>
      candidate.type === "TOOL_EXECUTE_BEFORE" && candidate.callId === event.callId);
    if (before) return Date.parse(before.timestamp);
  }
  const timestamp = Date.parse(explicit ?? event.timestamp);
  return Number.isFinite(timestamp) ? timestamp : 0;
}

function timelineTimeLabel(event: DisplayEvent): string {
  const start = eventStartMs(event);
  const end = start + numberValue(event.displayPayload.durationMs);
  return `${clockTime(start)} → ${clockTime(end)}`;
}

function timelineTimingLabel(event: DisplayEvent): string {
  const fields = [`Total ${eventDurationLabel(event)}`];
  const ttft = numberValue(event.displayPayload.ttftMs);
  const decode = numberValue(event.displayPayload.decodeMs);
  if (ttft) fields.push(`TTFT ${Math.round(ttft)} ms`);
  if (decode) fields.push(`Decode ${Math.round(decode)} ms`);
  return fields.join(" · ");
}

function clockTime(timestamp: number): string {
  if (!Number.isFinite(timestamp)) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit", minute: "2-digit", second: "2-digit", fractionalSecondDigits: 3, hour12: false,
  }).format(new Date(timestamp));
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

function inspectorTabLabel(tab: InspectorTab, event: DisplayEvent | null): string {
  if (tab === "summary") return "Summary";
  if (tab === "timing") return "Timing";
  if (tab === "source") return "Source";
  if (tab === "payload") return event?.lane === "TOOLS" ? "Payload" : "Raw";
  if (!event) return "Preview";
  if (event.lane === "TOOLS") return "Result";
  return eventKind(event) === "assistant" ? "Output" : "Preview";
}

function resultValue(event: DisplayEvent): unknown {
  const payload = event.displayPayload;
  if (event.lane === "TOOLS") {
    return payload.result
      ?? payload.output
      ?? payload.error
      ?? recordValue(recordValue(recordValue(payload.event)?.properties)?.part)?.state
      ?? payload.event
      ?? payload;
  }
  const kind = eventKind(event);
  if (kind === "system") return payload.system ?? payload.prompt ?? payload.content ?? payload;
  if (kind === "user") return payload.text ?? payload.content ?? payload.message ?? payload.input ?? payload;
  if (kind === "context") {
    return payload.context ?? payload.messages ?? payload.content ?? payload.system ?? payload.event ?? payload;
  }
  const output: Record<string, unknown> = {};
  for (const key of ["text", "reasoning", "content", "message", "finishReason", "cost"] as const) {
    if (payload[key] != null) output[key] = payload[key];
  }
  if (Object.keys(output).length > 0) return output;
  return payload.event ?? payload;
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

function traceStateLabel(trace: TraceCatalog) {
  if (trace.droppedCount > 0) return "不完整";
  if (trace.pendingChunks > 0) return "待上传";
  if (trace.complete) return "完整";
  if (trace.status === "ACTIVE") return "进行中";
  return "不完整";
}

function traceStateClass(trace: TraceCatalog) {
  if (trace.droppedCount > 0) return "incomplete";
  if (trace.pendingChunks > 0) return "pending";
  if (trace.complete) return "complete";
  return trace.status === "ACTIVE" ? "active" : "incomplete";
}

/** 历史版本曾出现 complete=true 但仍有待上传分片；展示完整度必须以无积压、无丢弃为准。 */
function isEffectivelyComplete(trace: TraceCatalog) {
  return trace.complete && trace.pendingChunks === 0 && trace.droppedCount === 0;
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
  if (revealRow && suppressTimelineClick) return;
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

      <section :class="['trace-content', {
        'trace-content--catalog-collapsed': catalogCollapsed,
        'trace-content--inspector-collapsed': inspectorCollapsed,
        'trace-content--resizing': resizingPanel !== null,
      }]" :style="traceContentStyle">
        <aside v-if="!catalogCollapsed" class="trace-list-panel">
          <header>
            <div><strong>Trace 目录</strong><span>{{ page.total }} 条</span></div>
            <small>ClickHouse 元数据索引</small>
            <button type="button" class="panel-collapse-button" aria-label="折叠 Trace 目录" title="折叠 Trace 目录"
              @click="catalogCollapsed = true"><ChevronLeft :size="15" /></button>
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
              <b>{{ trace.agentId && trace.agentId !== 'unknown' ? trace.agentId : '未识别 Agent' }}</b>
              <em :class="traceStateClass(trace)">
                {{ traceStateLabel(trace) }}
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
        <aside v-else class="trace-panel-rail trace-panel-rail--left">
          <button type="button" aria-label="展开 Trace 目录" title="展开 Trace 目录"
            @click="catalogCollapsed = false"><ChevronRight :size="15" /><span>Trace</span></button>
        </aside>
        <div v-if="!catalogCollapsed" class="panel-resize-handle panel-resize-handle--catalog"
          role="separator" aria-label="调整 Trace 目录宽度" aria-orientation="vertical" tabindex="0"
          :aria-valuenow="Math.round(catalogWidth ?? 286)" aria-valuemin="190" aria-valuemax="420"
          title="拖动调整 Trace 目录宽度，双击恢复默认"
          @pointerdown="startPanelResize('catalog', $event)"
          @pointermove="updatePanelResize('catalog', $event)"
          @pointerup="finishPanelResize('catalog', $event)"
          @pointercancel="finishPanelResize('catalog', $event)"
          @keydown="resizePanelWithKeyboard('catalog', $event)"
          @dblclick="resetPanelWidth('catalog')"><span /></div>

        <section class="trace-timeline-panel">
          <div v-if="!selectedTrace" class="trace-empty trace-empty--hero">
            <Waypoints :size="30" />
            <strong>选择一条 Trace 查看完整轨迹</strong>
            <span>Input / Model / Tools 三泳道会按全局序号对齐。</span>
          </div>
          <template v-else>
            <header class="timeline-header">
              <div>
                <p>{{ selectedTrace.username }} · {{ selectedTrace.agentId && selectedTrace.agentId !== 'unknown' ? selectedTrace.agentId : '未识别 Agent' }}</p>
                <h2>{{ selectedTrace.traceId }}</h2>
                <span>{{ selectedTrace.runId || '无 Run ID' }} · {{ trajectoryEvents.length }} records / {{ rawEvents.length }} raw events · 完成水位 {{ selectedTrace.completeThrough }}</span>
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
              <div class="lane-labels" aria-hidden="true"><span>Input</span><span>Model</span><span>Tools</span></div>
              <div
                class="lane-domain"
                tabindex="0"
                aria-label="轨迹总览；水平拖动选择时间范围，双击或按 Escape 清除"
                @pointerdown="startTimelineRange"
                @pointermove="updateTimelineRange"
                @pointerup="finishTimelineRange"
                @pointercancel="cancelTimelineRange"
                @dblclick.self="timelineSelection = null"
                @keydown.esc="timelineSelection = null"
              >
                <span
                  v-if="timelineSelection"
                  class="lane-selection lane-selection--range"
                  :style="rangeSelectionStyle(timelineSelection)"
                  aria-hidden="true"
                />
                <span
                  v-else-if="selectedTrajectoryEvent"
                  class="lane-selection lane-selection--event"
                  :style="selectionStyle(selectedTrajectoryEvent)"
                  aria-hidden="true"
                />
                <button
                  v-for="event in trajectoryEvents"
                  :key="event.eventId"
                  type="button"
                  :class="['lane-dot', `lane-dot--${event.lane.toLowerCase()}`, { selected: selectedEventId === event.eventId, 'outside-range': !eventInTimelineSelection(event), 'has-timing': event.lane === 'MODEL' && numberValue(event.displayPayload.durationMs) > 0 }]"
                  :style="trackStyle(event)"
                  :data-in-range="eventInTimelineSelection(event)"
                  :aria-label="`选择 ${eventTitle(event)} 事件`"
                  :aria-pressed="selectedEventId === event.eventId"
                  @mouseenter="hoveredEventId = event.eventId"
                  @mouseleave="hoveredEventId = null"
                  @focus="hoveredEventId = event.eventId"
                  @blur="hoveredEventId = null"
                  @click="selectTimelineEvent(event, true)"
                >
                  <template v-if="event.lane === 'MODEL' && numberValue(event.displayPayload.durationMs) > 0">
                    <span class="lane-phase lane-phase--prefill" :style="assistantPhaseStyle(event)" aria-hidden="true" />
                    <span class="lane-phase lane-phase--decode" aria-hidden="true" />
                  </template>
                </button>
                <div v-if="hoveredEvent" class="lane-tooltip" role="tooltip" :style="tooltipStyle(hoveredEvent)">
                  <strong>{{ eventKindLabel(hoveredEvent) }}</strong>
                  <span>{{ timelineTimeLabel(hoveredEvent) }}</span>
                  <small>{{ timelineTimingLabel(hoveredEvent) }}</small>
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
                :class="['event-row', `event-row--${eventKind(event)}`, { selected: selectedEventId === event.eventId, 'outside-range': !eventInTimelineSelection(event), 'turn-start': isTurnStart(event) }]"
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

        <aside v-if="!inspectorCollapsed" class="trace-inspector">
          <header>
            <div><strong>{{ selectedEvent ? eventTitle(selectedEvent) : '事件检查器' }}</strong><span>{{ selectedEvent?.type }}</span></div>
            <span class="inspector-header-actions">
              <button v-if="selectedEvent" type="button" aria-label="清除事件选择" title="清除事件选择"
                @click="selectedEventId = null">×</button>
              <button type="button" aria-label="折叠事件检查器" title="折叠事件检查器"
                @click="inspectorCollapsed = true"><ChevronRight :size="15" /></button>
            </span>
          </header>
          <nav aria-label="事件检查器视图">
            <button v-for="tab in (['summary', 'payload', 'result', 'timing', 'source'] as InspectorTab[])" :key="tab"
              type="button" :class="{ active: inspectorTab === tab }" @click="inspectorTab = tab">
              {{ inspectorTabLabel(tab, selectedEvent) }}
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
        <aside v-else class="trace-panel-rail trace-panel-rail--right">
          <button type="button" aria-label="展开事件检查器" title="展开事件检查器"
            @click="inspectorCollapsed = false"><ChevronLeft :size="15" /><span>详情</span></button>
        </aside>
        <div v-if="!inspectorCollapsed" class="panel-resize-handle panel-resize-handle--inspector"
          role="separator" aria-label="调整事件检查器宽度" aria-orientation="vertical" tabindex="0"
          :aria-valuenow="Math.round(inspectorWidth ?? 330)" aria-valuemin="240" aria-valuemax="520"
          title="拖动调整事件检查器宽度，双击恢复默认"
          @pointerdown="startPanelResize('inspector', $event)"
          @pointermove="updatePanelResize('inspector', $event)"
          @pointerup="finishPanelResize('inspector', $event)"
          @pointercancel="finishPanelResize('inspector', $event)"
          @keydown="resizePanelWithKeyboard('inspector', $event)"
          @dblclick="resetPanelWidth('inspector')"><span /></div>
      </section>
    </section>
  </main>
</template>

<style scoped>
.trace-shell { --ink:#202126; --muted:#777b86; --line:#e8e8ec; --panel:#fff; --fog:#f7f7f8; --input:#2fa36b; --model:#8063ad; --tools:#df851d; width:100%; min-width:0; height:100%; display:flex; overflow:hidden; color:var(--ink); background:var(--fog); font-family:Inter,"PingFang SC","Microsoft YaHei",sans-serif; }
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
.trace-content { position:relative; min-height:0; flex:1; display:grid; grid-template-columns:310px minmax(520px,1fr) 350px; overflow:hidden; }
.trace-list-panel,.trace-inspector { min-height:0; display:flex; flex-direction:column; background:#fff; }
.trace-list-panel { border-right:1px solid var(--line); overflow:auto; }
.trace-list-panel>header,.trace-inspector>header { position:sticky; top:0; z-index:2; display:flex; justify-content:space-between; padding:13px 14px; border-bottom:1px solid var(--line); background:#fff; }
.trace-list-panel>header div,.trace-inspector>header div { display:flex; flex-direction:column; gap:2px; }.trace-list-panel>header span,.trace-list-panel>header small,.trace-inspector>header span { color:var(--muted); font-size:10px; }
.trace-list-item { width:100%; padding:12px 14px; text-align:left; border:0; border-bottom:1px solid #efeff1; background:#fff; cursor:pointer; }
.trace-list-item:hover { background:#faf9fd; }.trace-list-item.selected { background:#f2effa; box-shadow:inset 3px 0 #7b5ab4; }
.trace-list-title,.trace-list-meta { display:flex; align-items:center; justify-content:space-between; gap:8px; }.trace-list-title b { font-size:12px; }.trace-list-title em { padding:2px 6px; border-radius:999px; font-size:9px; font-style:normal; }.trace-list-title em.complete { color:#147245; background:#e8f7ef; }.trace-list-title em.incomplete { color:#a8620f; background:#fff2de; }.trace-list-title em.active { color:#3567a8; background:#eaf2fc; }.trace-list-title em.pending { color:#8a6417; background:#fff6da; }
.trace-list-user,.trace-list-item code,.trace-list-meta { display:block; margin-top:5px; color:#777b85; font-size:10px; }.trace-list-item code { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#5c477f; }.trace-list-meta { display:flex; }
.trace-pagination { position:sticky; bottom:0; display:flex; align-items:center; justify-content:space-between; padding:9px; border-top:1px solid var(--line); background:#fff; font-size:10px; }.trace-pagination button { border:1px solid #dddde2; border-radius:6px; background:#fff; font-size:10px; }
.trace-timeline-panel { min-width:0; min-height:0; display:flex; flex-direction:column; overflow:hidden; background:#fcfcfd; }
.timeline-header { display:flex; align-items:center; justify-content:space-between; padding:14px 17px; border-bottom:1px solid var(--line); background:#fff; }.timeline-header p,.timeline-header h2,.timeline-header span { margin:0; }.timeline-header p { color:#6b507e; font-size:11px; font-weight:700; }.timeline-header h2 { margin:3px 0; font:600 14px ui-monospace,SFMono-Regular,monospace; }.timeline-header span { color:var(--muted); font-size:10px; }
.timeline-toolbar { display:flex; align-items:center; justify-content:space-between; padding:8px 12px; border-bottom:1px solid var(--line); background:#fff; }.timeline-modes { display:flex; gap:3px; padding:3px; border-radius:8px; background:#f0f0f2; }.timeline-modes button { padding:5px 9px; border:0; border-radius:6px; background:transparent; color:#777b85; font-size:10px; cursor:pointer; }.timeline-modes button.active { background:#fff; color:#2b2c31; box-shadow:0 1px 3px #0001; }
.event-search { display:flex; align-items:center; gap:5px; width:190px; padding:0 8px; border:1px solid #dddde2; border-radius:7px; background:#fff; }.event-search input { width:100%; height:27px; border:0; outline:0; font-size:10px; }
.lane-overview { height:50px; display:grid; grid-template-columns:48px minmax(0,1fr); padding:0; border-bottom:1px solid var(--line); background:#fafafa; }
.lane-labels { display:grid; grid-template-rows:repeat(3,14px); align-content:start; padding-top:4px; border-right:1px solid var(--line); }
.lane-labels span { display:flex; align-items:center; justify-content:flex-end; padding-right:4px; color:#858994; font-size:10px; }
.lane-domain { position:relative; height:49px; cursor:crosshair; touch-action:none; user-select:none; }
.lane-selection { position:absolute; z-index:1; top:0; bottom:0; left:var(--trajectory-selection-left); width:var(--trajectory-selection-width); min-width:3px; border-right:2px solid #477bea; border-left:2px solid #477bea; background:rgba(71,123,234,.08); pointer-events:none; }
.lane-dot { position:absolute; z-index:2; top:calc(7px + var(--trajectory-span-lane) * 14px); left:calc(var(--trajectory-span-left) + var(--trajectory-span-gap)); width:calc(var(--trajectory-span-width) - var(--trajectory-span-gap) - var(--trajectory-span-gap)); min-width:2px; height:8px; display:flex; padding:0; border:0; border-radius:1px; cursor:crosshair; }
.lane-dot::before { position:absolute; z-index:3; inset:-3px -4px; content:""; }
.lane-dot--input { background:var(--input); }.lane-dot--model { background:var(--model); }.lane-dot--tools { background:var(--tools); }
.lane-dot.has-timing { background:transparent; }.lane-phase { height:8px; pointer-events:none; }.lane-phase--prefill { width:var(--trajectory-prefill-width); flex:0 0 var(--trajectory-prefill-width); border-radius:1px 0 0 1px; background:#baa9cf; }.lane-phase--decode { min-width:0; flex:1; border-radius:0 1px 1px 0; background:var(--model); }
.lane-dot.selected { box-shadow:0 0 0 1px #fff,0 0 0 2px #477bea; }.lane-dot:hover,.lane-dot:focus-visible { z-index:4; outline:0; box-shadow:0 0 0 1px #fff,0 0 0 2px #477bea; }
.lane-dot.outside-range:not(.selected) { opacity:.18; }
.lane-tooltip { position:absolute; z-index:8; top:46px; left:var(--trajectory-tooltip-left); min-width:210px; max-width:340px; display:grid; gap:2px; padding:7px 10px; transform:translateX(-50%); border-radius:7px; background:#2d2e32; color:#fff; box-shadow:0 5px 14px rgba(0,0,0,.18); pointer-events:none; font:10px/1.35 ui-monospace,SFMono-Regular,Menlo,monospace; }.lane-tooltip strong { font-size:10px; }.lane-tooltip span,.lane-tooltip small { color:#f1f1f3; white-space:nowrap; }.lane-tooltip small { color:#d4d5d9; }
.event-list { min-height:0; overflow:auto; padding-bottom:30px; }.event-row { width:100%; min-height:54px; display:grid; grid-template-columns:90px 20px 1fr 62px; align-items:center; padding:6px 12px; text-align:left; border:0; border-bottom:1px solid #ededf0; background:#fff; cursor:pointer; }.event-row:hover { background:#fafafa; }.event-row.selected { background:#f6f3fb; }.event-row>time { color:#8a8d96; font:9px ui-monospace,SFMono-Regular,monospace; }.event-row>time small { display:block; margin-top:4px; font-size:8px; }.event-tree-control { color:#777; }.event-card { min-width:0; display:flex; flex-direction:column; gap:4px; padding:7px 10px; border-left:3px solid var(--model); border-radius:5px; background:#f2edf7; }.event-row--input .event-card { border-color:var(--input); background:#eaf7f0; }.event-row--tools .event-card { border-color:var(--tools); background:#fff4e6; }.event-card b { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; font-size:11px; }.event-card small { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; color:#777b85; font-size:9px; }.event-duration { text-align:right; color:#777b85; font:9px ui-monospace,SFMono-Regular,monospace; }
.event-row.outside-range:not(.selected) { opacity:.28; }
.trace-inspector { border-left:1px solid var(--line); }.trace-inspector nav { display:flex; overflow-x:auto; padding:0 8px; border-bottom:1px solid var(--line); }.trace-inspector nav button { padding:10px 7px 8px; border:0; border-bottom:2px solid transparent; background:transparent; color:#7b7e87; font-size:9px; cursor:pointer; }.trace-inspector nav button.active { color:#684a98; border-color:#7b5ab4; }
.inspector-body { min-height:0; flex:1; overflow:auto; padding:13px; }.inspector-body dl { display:grid; grid-template-columns:82px 1fr; gap:10px 8px; margin:0; font-size:10px; }.inspector-body dt { color:#8a8d96; }.inspector-body dd { min-width:0; margin:0; overflow-wrap:anywhere; color:#32333a; }.inspector-body pre { margin:0; white-space:pre-wrap; overflow-wrap:anywhere; font:10px/1.55 ui-monospace,SFMono-Regular,monospace; color:#34353b; }
.trace-inspector>footer { display:flex; flex-wrap:wrap; gap:6px; padding:9px 12px; border-top:1px solid var(--line); }.trace-inspector>footer span { padding:3px 7px; border-radius:999px; background:#f0f0f2; color:#6c7079; font-size:9px; }.trace-inspector>footer .safe { background:#e8f7ef; color:#147245; }.trace-inspector>footer .warning { background:#fff2de; color:#a8620f; }
.panel-collapse-button,.inspector-header-actions button,.trace-panel-rail button { border:0; background:transparent; color:#777c85; cursor:pointer; }
.panel-collapse-button { width:24px; height:24px; display:grid; flex:0 0 24px; place-items:center; margin-left:4px; padding:0; border-radius:3px; }
.panel-collapse-button:hover,.inspector-header-actions button:hover,.trace-panel-rail button:hover { background:#f0f1f3; color:#30333a; }
.inspector-header-actions { display:flex; align-items:center; gap:2px; margin-left:auto; }
.inspector-header-actions button { width:24px; height:24px; display:grid; place-items:center; padding:0; border-radius:3px; font-size:18px; }
.trace-panel-rail { min-width:0; display:flex; justify-content:center; border-color:var(--line); background:#fafafa; }
.trace-panel-rail--left { border-right:1px solid var(--line); }
.trace-panel-rail--right { border-left:1px solid var(--line); }
.trace-panel-rail button { width:100%; display:flex; flex-direction:column; align-items:center; gap:6px; padding:9px 0; font-size:9px; }
.trace-panel-rail button span { writing-mode:vertical-rl; letter-spacing:.8px; }
.panel-resize-handle { position:absolute; z-index:8; top:0; bottom:0; width:9px; touch-action:none; cursor:col-resize; outline:0; }
.panel-resize-handle--catalog { left:calc(var(--trace-catalog-width) - 4px); }
.panel-resize-handle--inspector { right:calc(var(--trace-inspector-width) - 4px); }
.panel-resize-handle span { position:absolute; top:calc(50% - 16px); left:3px; width:3px; height:32px; border-radius:2px; background:#c7c9cf; opacity:0; transition:opacity .12s ease; }
.panel-resize-handle:hover span,.panel-resize-handle:focus-visible span,.trace-content--resizing .panel-resize-handle span { opacity:1; }
.trace-content--resizing { cursor:col-resize; user-select:none; }
.trace-empty { display:flex; align-items:center; justify-content:center; min-height:120px; padding:24px; color:#898c95; font-size:11px; text-align:center; }.trace-empty--hero { height:100%; flex-direction:column; gap:8px; }.trace-empty--hero strong { color:#4b4d54; font-size:14px; }.spinning { animation:spin .8s linear infinite; }@keyframes spin { to { transform:rotate(360deg); } }
@media (max-width:1450px) { .trace-content { grid-template-columns:280px minmax(500px,1fr) 310px; }.trace-filters { grid-template-columns:repeat(5,1fr); }.trace-filters button { align-self:end; } }

/*
 * Trace 详情遵循 DeepSeek Harness 轨迹视图的信息密度与交互语义：全宽白底、
 * 32px 控制栏、50px 三泳道和按 Turn 排列的 ledger；平台筛选与权限仍保留自身契约。
 */
.trace-shell { --ink:#202126; --muted:#7d818a; --caption:#a2a7b0; --line:#e7e8eb; --line-soft:#f0f1f3; --input:#39a96b; --model:#8a6bad; --tools:#df851d; width:100%; min-width:0; height:100%; color:var(--ink); background:#fff; }
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
.trace-list-panel>header { height:30px; min-width:0; align-items:center; gap:6px; padding:0 10px; background:#f7f8f9; }
.trace-list-panel>header div { min-width:max-content; flex:0 0 auto; flex-direction:row; align-items:center; gap:6px; white-space:nowrap; }
.trace-list-panel>header div strong,.trace-list-panel>header div span { white-space:nowrap; }
.trace-list-panel>header small { min-width:0; flex:1; margin-left:auto; overflow:hidden; text-align:right; text-overflow:ellipsis; white-space:nowrap; }
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
.trace-inspector nav button.active { color:#477bea; border-color:#477bea; }
button:focus-visible,input:focus-visible,select:focus-visible { outline:1px solid #477bea; outline-offset:1px; }
@media (prefers-reduced-motion:reduce) { .spinning { animation:none; } }

/* 控制台嵌入态：系统导航已经提供入口，内部只保留“基础信息列表 → 原位展开轨迹”。 */
.trace-rail { display:none; }
.trace-header { min-height:54px; padding:7px 16px; }
.trace-content { --trace-catalog-width:clamp(232px,19vw,286px); --trace-inspector-width:clamp(280px,22vw,330px); grid-template-columns:var(--trace-catalog-width) minmax(0,1fr) var(--trace-inspector-width); }
.trace-content.trace-content--catalog-collapsed { grid-template-columns:32px minmax(0,1fr) var(--trace-inspector-width); }
.trace-content.trace-content--inspector-collapsed { grid-template-columns:var(--trace-catalog-width) minmax(0,1fr) 32px; }
.trace-content.trace-content--catalog-collapsed.trace-content--inspector-collapsed { grid-template-columns:32px minmax(0,1fr) 32px; }
.trace-list-panel { border-right:1px solid var(--line); }
.trace-list-item { min-height:40px; display:block; padding:3px 10px; }
.trace-list-title,.trace-list-meta { display:flex; }
.trace-list-title { grid-column:auto; grid-row:auto; }
.trace-list-user,.trace-list-item code,.trace-list-meta { grid-column:auto; grid-row:auto; margin-top:2px; }
.trace-list-meta { flex-direction:row; align-items:center; }
.trace-list-title em { border-radius:4px; }
.timeline-header { min-height:24px; }
.timeline-toolbar { height:28px; }
.event-row { min-height:28px; }
.event-card { gap:8px; }
.event-card b { font-size:10px; }
.event-card small { font-size:9px; }
.event-row>time small { top:0; }
@media (max-width:1380px) {
  .trace-content { --trace-catalog-width:232px; --trace-inspector-width:280px; }
  .event-row { grid-template-columns:58px 108px minmax(0,1fr) 72px 56px; }
  .event-card { gap:6px; }
  .event-card b { max-width:130px; }
}
@media (max-width:1150px) {
  .trace-content { --trace-catalog-width:218px; --trace-inspector-width:260px; }
  .event-row { grid-template-columns:54px 98px minmax(0,1fr) 54px; }
  .event-tokens { display:none; }
  .event-card b { max-width:100px; }
  .trace-inspector nav button { padding-right:5px; padding-left:5px; }
}
</style>
