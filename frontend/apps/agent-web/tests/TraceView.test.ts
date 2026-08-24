// @vitest-environment jsdom
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TraceCatalog, TraceRawEvent, TraceSpan } from "@test-agent/shared-types";
import TraceView from "../src/views/TraceView.vue";
import traceViewSource from "../src/views/TraceView.vue?raw";
import agentWorkbenchSource from "../src/components/AgentWorkbench.vue?raw";
import { useAuthStore } from "../src/stores/authStore";

const api = vi.hoisted(() => ({
  listTraces: vi.fn(),
  getTrace: vi.fn(),
  getTraceEvents: vi.fn(),
  getTraceSpans: vi.fn(),
  getTraceRecord: vi.fn(),
  downloadTrace: vi.fn(),
  getCurrentUser: vi.fn(),
}));

vi.mock("@test-agent/backend-api", () => ({
  createBackendApiClient: () => api,
}));

const trace: TraceCatalog = {
  traceId: "trc_0123456789abcdef0123456789abcdef",
  userId: "usr_trace_user",
  username: "测试用户",
  organization: "研发中心",
  rdDepartment: "质量部",
  department: "智能测试",
  runtimeKind: "LOCAL_CLIENT",
  source: "OPENCODE_PLUGIN",
  clientInstanceId: "lci_trace_user",
  backendProcessId: "bjp_trace_owner",
  linuxServerId: "server-trace",
  sessionId: "ses_trace",
  runId: "run_trace",
  agentId: "test-design-agent",
  status: "COMPLETED",
  archiveStatus: "INCOMPLETE",
  startedAt: "2026-08-22T08:00:00Z",
  updatedAt: "2026-08-22T08:05:00Z",
  coverageStartAt: "2026-08-22T08:00:00Z",
  completeThrough: 6,
  eventCount: 6,
  archivedBytes: 2048,
  droppedCount: 1,
  pendingChunks: 0,
  complete: false,
  redacted: true,
};

const fragmentPayload = {
  tool: "skill",
  skillName: "test-design",
  capabilityKind: "SKILL",
  status: "SUCCEEDED",
  durationMs: 18,
  args: { name: "test-design" },
  result: "完整测试设计输出",
};
const fragmentData = Buffer.from(JSON.stringify(fragmentPayload), "utf8").toString("base64");
const events: TraceRawEvent[] = [
  event(1, "SYSTEM_PROMPT", { system: ["You are a test agent"] }),
  {
    ...event(2, "ASSISTANT_STEP_METRICS", {
      recordKind: "message",
      event: { type: "message.part.updated" },
      reasoning: "分析路径",
      startedAt: "2026-08-22T08:00:01.800Z",
      durationMs: 200,
      ttftMs: 40,
      decodeMs: 160,
      tokensInput: 100,
      tokensOutput: 20,
      tokensReasoning: 8,
      tokensCacheRead: 60,
      tokensCacheWrite: 5,
      tokensTotal: 128,
      decodeTokens: 20,
      cost: 0.01,
      finishReason: "stop",
    }),
    messageId: "msg-assistant",
  },
  {
    ...event(3, "TOOL_EXECUTE_AFTER", {
      tool: "skill",
      skillName: "test-design",
      capabilityKind: "SKILL",
      fragmentedPayload: {
        fragmentGroupId: "frg_trace_skill",
        fragmentCount: 1,
        encoding: "base64-json-utf8",
        originalBytes: fragmentData.length,
      },
    }),
    callId: "call_skill_1",
  },
  event(4, "PAYLOAD_FRAGMENT", {
    fragmentGroupId: "frg_trace_skill",
    fragmentIndex: 0,
    fragmentCount: 1,
    encoding: "base64-json-utf8",
    originalType: "TOOL_EXECUTE_AFTER",
    originalLane: "TOOLS",
    dataBase64: fragmentData,
  }),
  event(5, "OPENCODE_EVENT", { event: { type: "session.idle" } }),
  event(6, "OPENCODE_EVENT", {
    event: {
      type: "message.part.delta",
      properties: { messageID: "msg-assistant", partID: "text-1", field: "text", delta: "raw fragment" },
    },
  }),
];

describe("TraceView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    api.listTraces.mockResolvedValue({ items: [trace], page: 1, size: 30, total: 1 });
    api.getTrace.mockResolvedValue(trace);
    api.getTraceEvents.mockResolvedValue({ items: events, completeThrough: 6, complete: false });
    const spans = events
      .filter((candidate) => ["SYSTEM_PROMPT", "ASSISTANT_STEP_METRICS", "TOOL_EXECUTE_AFTER"].includes(candidate.type))
      .map(span);
    api.getTraceSpans.mockResolvedValue({ items: spans, total: spans.length, completeThrough: 6 });
    api.getTraceRecord.mockResolvedValue({ items: events, completeThrough: 6, complete: false });
    api.downloadTrace.mockResolvedValue(new Blob(["gzip"]));
    api.getCurrentUser.mockResolvedValue({
      userId: "usr_admin",
      username: "admin",
      unifiedAuthId: "admin",
      roles: ["SUPER_ADMIN"],
    });
  });

  it("renders the console-embedded three-lane timeline and reconstructs fragmented Skill payload", async () => {
    const pinia = createPinia();
    setActivePinia(pinia);
    const authStore = useAuthStore();
    authStore.saveToken("trace-test-token");
    authStore.currentUser = {
      userId: "usr_admin",
      username: "admin",
      unifiedAuthId: "admin",
      roles: ["SUPER_ADMIN"],
    };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/workbench", name: "workbench", component: { template: "<div>workbench</div>" } },
        { path: "/traces", name: "traces", component: TraceView },
      ],
    });
    await router.push("/traces");
    await router.isReady();

    const view = render(TraceView, { global: { plugins: [pinia, router] } });
    await waitFor(() => expect(view.getByText("test-design-agent")).toBeTruthy());
    expect(api.listTraces).toHaveBeenCalledOnce();
    expect(view.getByText("Agent Trace")).toBeTruthy();
    expect(view.queryByLabelText("主导航")).toBeNull();
    expect(view.queryByLabelText("返回工作台")).toBeNull();
    expect(view.getAllByText("不完整").length).toBeGreaterThan(0);

    let releaseInitialRecord!: (value: { items: TraceRawEvent[]; completeThrough: number; complete: boolean }) => void;
    api.getTraceRecord.mockImplementationOnce(() => new Promise((resolve) => { releaseInitialRecord = resolve; }));
    await fireEvent.click(view.getByText("test-design-agent").closest("button")!);
    await waitFor(() => expect(view.getAllByText("test-design").length).toBeGreaterThan(0));
    expect(api.getTraceSpans).toHaveBeenCalledWith(trace.traceId, 0, 500);
    expect(api.getTraceEvents).not.toHaveBeenCalled();
    expect(view.getByText("正在读取所选事件正文…")).toBeTruthy();
    releaseInitialRecord({ items: events, completeThrough: 6, complete: false });
    await waitFor(() => expect(view.queryByText("正在读取所选事件正文…")).toBeNull());
    const laneText = Array.from(view.container.querySelectorAll(".lane-labels span"))
      .map((element) => element.textContent?.trim());
    expect(laneText).toEqual(["Input", "Model", "Tools"]);
    expect(view.container.querySelectorAll(".lane-dot")).toHaveLength(3);
    expect(view.getByText(/3 records \/ 6 raw events/)).toBeTruthy();

    await fireEvent.click(view.getByRole("button", { name: "折叠 Trace 目录" }));
    expect(view.container.querySelector(".trace-content")?.classList.contains("trace-content--catalog-collapsed")).toBe(true);
    expect(view.getByRole("button", { name: "展开 Trace 目录" })).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "展开 Trace 目录" }));
    expect(view.getByRole("button", { name: "折叠 Trace 目录" })).toBeTruthy();

    await fireEvent.click(view.getByRole("button", { name: "折叠事件检查器" }));
    expect(view.container.querySelector(".trace-content")?.classList.contains("trace-content--inspector-collapsed")).toBe(true);
    expect(view.getByRole("button", { name: "展开事件检查器" })).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "展开事件检查器" }));
    expect(view.getByRole("button", { name: "折叠事件检查器" })).toBeTruthy();

    const traceContent = view.container.querySelector<HTMLElement>(".trace-content")!;
    vi.spyOn(traceContent, "getBoundingClientRect").mockReturnValue({
      x: 0, y: 0, left: 0, top: 0, right: 1200, bottom: 600, width: 1200, height: 600, toJSON: () => ({}),
    });
    const catalogPanel = view.container.querySelector<HTMLElement>(".trace-list-panel")!;
    vi.spyOn(catalogPanel, "getBoundingClientRect").mockReturnValue({
      x: 0, y: 0, left: 0, top: 0, right: 286, bottom: 600, width: 286, height: 600, toJSON: () => ({}),
    });
    const catalogResize = view.getByRole("separator", { name: "调整 Trace 目录宽度" });
    Object.defineProperty(catalogResize, "setPointerCapture", { value: vi.fn() });
    await fireEvent.pointerDown(catalogResize, { button: 0, pointerId: 3, clientX: 286 });
    await fireEvent.pointerMove(catalogResize, { buttons: 1, pointerId: 3, clientX: 346 });
    expect(traceContent.style.getPropertyValue("--trace-catalog-width")).toContain("346px");
    await fireEvent.pointerUp(catalogResize, { button: 0, pointerId: 3, clientX: 346 });

    const inspectorPanel = view.container.querySelector<HTMLElement>(".trace-inspector")!;
    vi.spyOn(inspectorPanel, "getBoundingClientRect").mockReturnValue({
      x: 870, y: 0, left: 870, top: 0, right: 1200, bottom: 600, width: 330, height: 600, toJSON: () => ({}),
    });
    const inspectorResize = view.getByRole("separator", { name: "调整事件检查器宽度" });
    await fireEvent.keyDown(inspectorResize, { key: "ArrowLeft" });
    expect(traceContent.style.getPropertyValue("--trace-inspector-width")).toContain("346px");
    await fireEvent.dblClick(inspectorResize);
    expect(traceContent.style.getPropertyValue("--trace-inspector-width")).toBe("");

    expect(view.getByRole("button", { name: "Raw" })).toBeTruthy();
    await fireEvent.click(view.getByRole("button", { name: "Preview" }));
    expect(view.container.querySelector(".inspector-body pre")?.textContent).toContain("You are a test agent");

    const laneDomain = view.getByLabelText("轨迹总览；水平拖动选择时间范围，双击或按 Escape 清除");
    const capturePointer = vi.fn();
    Object.defineProperty(laneDomain, "setPointerCapture", { value: capturePointer });
    vi.spyOn(laneDomain, "getBoundingClientRect").mockReturnValue({
      x: 0, y: 0, left: 0, top: 0, right: 100, bottom: 50, width: 100, height: 50, toJSON: () => ({}),
    });
    await fireEvent.pointerDown(laneDomain, { button: 0, clientX: 40 });
    await fireEvent.pointerMove(laneDomain, { buttons: 1, clientX: 60 });
    await fireEvent.pointerUp(laneDomain, { button: 0, clientX: 60 });
    expect(capturePointer).toHaveBeenCalledOnce();
    expect(view.container.querySelector(".lane-selection--range")).toBeTruthy();
    expect(view.container.querySelectorAll('.lane-dot[data-in-range="true"]')).toHaveLength(1);
    expect(view.container.querySelectorAll(".event-row.outside-range")).toHaveLength(2);
    await fireEvent.keyDown(laneDomain, { key: "Escape" });
    expect(view.container.querySelector(".lane-selection--range")).toBeNull();
    await new Promise((resolve) => window.setTimeout(resolve, 0));

    const skillLaneButton = view.getByRole("button", { name: "选择 test-design 事件" });
    await fireEvent.pointerDown(skillLaneButton, { button: 0, clientX: 70 });
    await fireEvent.pointerUp(skillLaneButton, { button: 0, clientX: 70 });
    await fireEvent.click(skillLaneButton);
    expect(capturePointer).toHaveBeenCalledOnce();
    expect(skillLaneButton.getAttribute("aria-pressed")).toBe("true");
    expect(view.container.querySelector(".event-row.selected .event-card b")?.textContent).toBe("test-design");

    const modelLaneButton = view.getByRole("button", { name: "选择 ASSISTANT STEP METRICS 事件" });
    await fireEvent.mouseEnter(modelLaneButton);
    expect(view.container.querySelector(".lane-tooltip")?.textContent).toContain("TTFT 40 ms");
    expect(modelLaneButton.querySelectorAll(".lane-phase")).toHaveLength(2);

    const skillEventTitle = Array.from(view.container.querySelectorAll(".event-card b"))
      .find((element) => element.textContent === "test-design");
    await fireEvent.click(skillEventTitle!.closest("button")!);
    await fireEvent.click(view.getByRole("button", { name: "Payload" }));
    expect(view.getByText(/完整测试设计输出/)).toBeTruthy();
    expect(view.getByText(/"name": "test-design"/)).toBeTruthy();
    expect(view.getByText("已脱敏")).toBeTruthy();
    expect(view.getByText("OPENCODE_PLUGIN")).toBeTruthy();

    const metricEventTitle = Array.from(view.container.querySelectorAll(".event-card b"))
      .find((element) => element.textContent === "ASSISTANT STEP METRICS");
    await fireEvent.click(metricEventTitle!.closest("button")!);
    await fireEvent.click(view.getByRole("button", { name: "Output" }));
    expect(view.container.querySelector(".inspector-body pre")?.textContent).toContain("分析路径");
    await fireEvent.click(view.getByRole("button", { name: "Timing" }));
    expect(view.getByText("40 ms")).toBeTruthy();
    expect(view.getByText("160 ms")).toBeTruthy();
    expect(view.getByText("128")).toBeTruthy();
    expect(view.getByText("0.01")).toBeTruthy();

    const eventRows = () => view.container.querySelectorAll(".event-row").length;
    expect(eventRows()).toBe(3);
    await fireEvent.click(view.getByRole("button", { name: "Calls" }));
    expect(eventRows()).toBe(2);
    await fireEvent.click(view.getByRole("button", { name: "Calls" }));
    await fireEvent.click(view.getByRole("button", { name: "Turns" }));
    expect(eventRows()).toBe(1);

    const durationButton = view.getByRole("button", { name: "Duration" });
    const firstDot = view.container.querySelector<HTMLElement>(".lane-dot");
    const equalWidthStyle = firstDot?.getAttribute("style");
    await fireEvent.click(durationButton);
    expect(firstDot?.getAttribute("style")).not.toBe(equalWidthStyle);
  });

  it("shows aggregated in-progress assistant content for an incomplete run", async () => {
    const incompleteTrace = {
      ...trace,
      status: "ACTIVE",
      archiveStatus: "ARCHIVED",
      complete: false,
      eventCount: 4,
      droppedCount: 0,
      pendingChunks: 0,
    };
    const lifecycleTrace = {
      ...incompleteTrace,
      traceId: "trc_33333333333333333333333333333333",
      completeThrough: 3,
      eventCount: 3,
    };
    const historicalPendingTrace = {
      ...trace,
      traceId: "trc_44444444444444444444444444444444",
      complete: true,
      droppedCount: 0,
      pendingChunks: 2,
    };
    const incompleteEvents: TraceRawEvent[] = [
      event(1, "OPENCODE_EVENT", {
        event: { type: "message.updated", properties: { info: { id: "msg_pending", role: "assistant" } } },
      }),
      event(2, "OPENCODE_EVENT", {
        event: { type: "message.part.delta", properties: {
          messageID: "msg_pending", partID: "part_pending", field: "text", delta: "正在",
        } },
      }),
      event(3, "OPENCODE_EVENT", {
        event: { type: "message.part.delta", properties: {
          messageID: "msg_pending", partID: "part_pending", field: "text", delta: "处理",
        } },
      }),
      event(4, "OPENCODE_EVENT", { event: { type: "plugin.added", properties: { name: "observability" } } }),
    ];
    const lifecycleEvents: TraceRawEvent[] = [
      event(1, "OPENCODE_EVENT", { event: { type: "plugin.added", properties: { name: "one" } } }),
      event(2, "OPENCODE_EVENT", { event: { type: "plugin.added", properties: { name: "two" } } }),
      event(3, "OPENCODE_EVENT", { event: { type: "session.created", properties: { sessionID: "ses_trace" } } }),
    ].map((rawEvent) => ({ ...rawEvent, traceId: lifecycleTrace.traceId }));
    api.listTraces.mockResolvedValue({
      items: [incompleteTrace, lifecycleTrace, historicalPendingTrace], page: 1, size: 30, total: 3,
    });
    api.getTrace.mockImplementation(async (traceId: string) =>
      traceId === lifecycleTrace.traceId ? lifecycleTrace : incompleteTrace);
    api.getTraceSpans.mockResolvedValue({ items: [], total: 0, completeThrough: 0 });
    api.getTraceEvents.mockImplementation(async (traceId: string) => traceId === lifecycleTrace.traceId
      ? { items: lifecycleEvents, completeThrough: 3, complete: false }
      : { items: incompleteEvents, completeThrough: 4, complete: false });

    const pinia = createPinia();
    setActivePinia(pinia);
    const authStore = useAuthStore();
    authStore.saveToken("trace-test-token");
    authStore.currentUser = {
      userId: "usr_admin", username: "admin", unifiedAuthId: "admin", roles: ["SUPER_ADMIN"],
    };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/traces", name: "traces", component: TraceView }],
    });
    await router.push("/traces");
    await router.isReady();

    const view = render(TraceView, { global: { plugins: [pinia, router] } });
    expect(await view.findByText("待上传")).toBeTruthy();
    expect(view.getAllByText("进行中").length).toBeGreaterThan(1);
    expect(view.getByText("0/3 条已完整归档")).toBeTruthy();
    await fireEvent.click((await view.findByText(incompleteTrace.traceId)).closest("button")!);
    await waitFor(() => expect(view.getByText("正在处理")).toBeTruthy());
    expect(view.getByText(/1 records \/ 4 raw events/)).toBeTruthy();
    expect(view.container.querySelectorAll(".lane-dot--model")).toHaveLength(1);
    expect(view.getAllByText("ACTIVE").length).toBeGreaterThan(0);

    await fireEvent.click(view.getByText(lifecycleTrace.traceId).closest("button")!);
    await waitFor(() => expect(view.getByText(/2 records \/ 3 raw events/)).toBeTruthy());
    expect(view.container.querySelectorAll(".event-row")).toHaveLength(2);
    expect(view.getAllByText("plugin.added").length).toBeGreaterThan(0);
    expect(view.getAllByText("session.created").length).toBeGreaterThan(0);
  });

  it("keeps the Trace view compact and free of gradients", () => {
    expect(traceViewSource).not.toMatch(/(?:linear|radial|conic)-gradient\s*\(/);
    expect(traceViewSource).toContain(".event-row { min-height:28px;");
    expect(traceViewSource).toContain(".trace-list-item { min-height:40px;");
    expect(traceViewSource).toContain("width:100%; min-width:0; height:100%");
    expect(traceViewSource).toContain("white-space:nowrap;");
    expect(traceViewSource).not.toContain("min-width:1120px");
    expect(traceViewSource).not.toContain("min-width:1180px");
    expect(agentWorkbenchSource).not.toContain('data-testid="trace-activity-button"');
  });
});

function event(sequence: number, type: string, payload: Record<string, unknown>): TraceRawEvent {
  return {
    schemaVersion: "1.0",
    eventId: `evt_${String(sequence).padStart(40, "0")}`,
    traceId: trace.traceId,
    type,
    timestamp: `2026-08-22T08:00:0${sequence}Z`,
    globalSequence: sequence,
    sessionSequence: sequence,
    sessionId: "ses_trace",
    runId: "run_trace",
    turnId: "turn:msg-user",
    payload,
  };
}

function span(rawEvent: TraceRawEvent): TraceSpan {
  const payload = rawEvent.payload ?? {};
  const capabilityKind = typeof payload.capabilityKind === "string" ? payload.capabilityKind : null;
  const capabilityName = capabilityKind === "SKILL"
    ? String(payload.skillName ?? "")
    : String(payload.tool ?? "");
  return {
    traceId: rawEvent.traceId,
    eventId: rawEvent.eventId,
    type: rawEvent.type,
    lane: rawEvent.type.startsWith("TOOL_") ? "TOOLS"
      : ["CHAT_MESSAGE", "SYSTEM_PROMPT", "CONTEXT_MESSAGES"].includes(rawEvent.type) ? "INPUT" : "MODEL",
    recordKind: String(payload.recordKind ?? (rawEvent.type === "SYSTEM_PROMPT" ? "system" : "message")),
    occurredAt: rawEvent.timestamp,
    startedAt: typeof payload.startedAt === "string" ? payload.startedAt : null,
    globalSequence: rawEvent.globalSequence,
    sessionSequence: rawEvent.sessionSequence,
    sessionId: rawEvent.sessionId,
    runId: rawEvent.runId,
    turnId: rawEvent.turnId,
    stepId: rawEvent.stepId,
    messageId: rawEvent.messageId,
    callId: rawEvent.callId,
    parentId: rawEvent.parentId,
    capabilityKind,
    capabilityName,
    status: String(payload.status ?? "COMPLETED"),
    durationMs: Number(payload.durationMs ?? 0),
    tokensInput: Number(payload.tokensInput ?? 0),
    tokensOutput: Number(payload.tokensOutput ?? 0),
    tokensReasoning: Number(payload.tokensReasoning ?? 0),
    tokensCacheRead: Number(payload.tokensCacheRead ?? 0),
    tokensCacheWrite: Number(payload.tokensCacheWrite ?? 0),
    tokensTotal: Number(payload.tokensTotal ?? 0),
    ttftMs: Number(payload.ttftMs ?? 0),
    decodeMs: Number(payload.decodeMs ?? 0),
    decodeTokens: Number(payload.decodeTokens ?? 0),
    cost: Number(payload.cost ?? 0),
    finishReason: typeof payload.finishReason === "string" ? payload.finishReason : null,
    source: "OPENCODE_PLUGIN",
  };
}
