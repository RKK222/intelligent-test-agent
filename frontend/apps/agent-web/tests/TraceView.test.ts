// @vitest-environment jsdom
import { createPinia, setActivePinia } from "pinia";
import { createMemoryHistory, createRouter } from "vue-router";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TraceCatalog, TraceRawEvent } from "@test-agent/shared-types";
import TraceView from "../src/views/TraceView.vue";
import traceViewSource from "../src/views/TraceView.vue?raw";
import agentWorkbenchSource from "../src/components/AgentWorkbench.vue?raw";
import { useAuthStore } from "../src/stores/authStore";

const api = vi.hoisted(() => ({
  listTraces: vi.fn(),
  getTrace: vi.fn(),
  getTraceEvents: vi.fn(),
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
  completeThrough: 5,
  eventCount: 5,
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
  event(2, "ASSISTANT_STEP_METRICS", {
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
];

describe("TraceView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    api.listTraces.mockResolvedValue({ items: [trace], page: 1, size: 30, total: 1 });
    api.getTrace.mockResolvedValue(trace);
    api.getTraceEvents.mockResolvedValue({ items: events, completeThrough: 5, complete: false });
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

    await fireEvent.click(view.getByText("test-design-agent").closest("button")!);
    await waitFor(() => expect(view.getAllByText("test-design").length).toBeGreaterThan(0));
    expect(api.getTraceEvents).toHaveBeenCalledWith(trace.traceId, 0, 500);
    const laneText = Array.from(view.container.querySelectorAll(".lane-track"))
      .map((element) => element.textContent?.replace(/\s+/g, " ").trim());
    expect(laneText).toEqual(["Input", "Model", "Tools"]);

    const skillLaneButton = view.getByRole("button", { name: "选择 test-design 事件" });
    await fireEvent.click(skillLaneButton);
    expect(skillLaneButton.getAttribute("aria-pressed")).toBe("true");
    expect(view.container.querySelector(".event-row.selected .event-card b")?.textContent).toBe("test-design");

    const skillEventTitle = Array.from(view.container.querySelectorAll(".event-card b"))
      .find((element) => element.textContent === "test-design");
    await fireEvent.click(skillEventTitle!.closest("button")!);
    await fireEvent.click(view.getByRole("button", { name: "Payload" }));
    expect(view.getByText(/完整测试设计输出/)).toBeTruthy();
    expect(view.getByText(/"name": "test-design"/)).toBeTruthy();
    expect(view.getByText("已脱敏")).toBeTruthy();
    expect(view.getByText("OPENCODE_PLUGIN")).toBeTruthy();

    const metricEventTitle = Array.from(view.container.querySelectorAll(".event-card b"))
      .find((element) => element.textContent === "message.part.updated");
    await fireEvent.click(metricEventTitle!.closest("button")!);
    await fireEvent.click(view.getByRole("button", { name: "Timing" }));
    expect(view.getByText("40 ms")).toBeTruthy();
    expect(view.getByText("160 ms")).toBeTruthy();
    expect(view.getByText("128")).toBeTruthy();
    expect(view.getByText("0.01")).toBeTruthy();

    const eventRows = () => view.container.querySelectorAll(".event-row").length;
    expect(eventRows()).toBe(4);
    await fireEvent.click(view.getByRole("button", { name: "Calls" }));
    expect(eventRows()).toBe(3);
    await fireEvent.click(view.getByRole("button", { name: "Calls" }));
    await fireEvent.click(view.getByRole("button", { name: "Turns" }));
    expect(eventRows()).toBe(1);

    const durationButton = view.getByRole("button", { name: "Duration" });
    const firstDot = view.container.querySelector<HTMLElement>(".lane-dot");
    const equalWidthStyle = firstDot?.getAttribute("style");
    await fireEvent.click(durationButton);
    expect(firstDot?.getAttribute("style")).not.toBe(equalWidthStyle);
  });

  it("keeps the Trace view compact and free of gradients", () => {
    expect(traceViewSource).not.toMatch(/(?:linear|radial|conic)-gradient\s*\(/);
    expect(traceViewSource).toContain(".event-row { min-height:28px;");
    expect(traceViewSource).toContain(".trace-list-item { min-height:40px;");
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
