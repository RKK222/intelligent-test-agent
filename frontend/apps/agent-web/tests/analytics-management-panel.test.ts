// @vitest-environment jsdom

import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  AnalyticsCapabilities,
  AnalyticsExceptionDetail,
  AnalyticsFilterOptions,
  AnalyticsFunnel,
  AnalyticsHourlyHeatmap,
  AnalyticsOverview,
  AnalyticsPeaks,
  AnalyticsSatisfaction,
  AnalyticsTimeSeriesPoint,
  AnalyticsTokenOperations,
  AnalyticsUserUsageRow,
  PageResponse
} from "@test-agent/shared-types";
import AnalyticsManagementPanel from "../src/components/system/AnalyticsManagementPanel.vue";

function queryClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

const overview: AnalyticsOverview = {
  registeredUsers: 10,
  enabledUsers: 9,
  loginUsers: 4,
  activeUsers: 3,
  validUsers: 2,
  deepUsers: 1,
  activeRate: 0.3333,
  loginToActiveRate: 0.75,
  activeToValidRate: 0.6667,
  validToDeepRate: 0.5,
  sessionCount: 5,
  activeSessionCount: 4,
  emptySessionCount: 1,
  continuousSessionCount: 2,
  userMessageCount: 12,
  assistantMessageCount: 10,
  runCount: 6,
  runsPerUser: 2,
  messagesPerUser: 4,
  messagesPerSession: 3,
  continuousConversationRate: 0.5,
  validInteractionCount: 5,
  sustainedUsers: 2,
  succeededRuns: 4,
  failedRuns: 1,
  cancelledRuns: 1,
  activeTerminations: 1,
  successRate: 0.6667,
  failureRate: 0.1667,
  cancellationRate: 0.1667,
  averageDurationMs: 1200,
  p95DurationMs: 2500,
  positiveFeedbackCount: 3,
  negativeFeedbackCount: 1,
  satisfactionRate: 0.75,
  feedbackCoverageRate: 0.4,
  diffProposedCount: 4,
  diffAcceptedCount: 3,
  diffRejectedCount: 1,
  diffAcceptanceRate: 0.75,
  diffRejectionRate: 0.25,
  inputTokens: 100,
  outputTokens: 80,
  reasoningTokens: 20,
  totalTokens: 200,
  tokensPerUser: 66.67,
  tokensPerRun: 33.33,
  freshness: {
    generatedAt: "2026-06-28T00:00:00Z",
    status: "STALE",
    message: "延迟"
  }
};

const trend: AnalyticsTimeSeriesPoint[] = [{
  bucketStart: "2026-06-28T00:00:00Z",
  loginUsers: 2,
  activeUsers: 2,
  sessionCount: 2,
  activeSessionCount: 2,
  userMessageCount: 5,
  assistantMessageCount: 4,
  runCount: 3,
  succeededRuns: 2,
  failedRuns: 1,
  cancelledRuns: 0,
  positiveFeedbackCount: 1,
  negativeFeedbackCount: 0,
  diffAcceptedCount: 1,
  diffRejectedCount: 0,
  totalTokens: 80,
  satisfactionRate: 1,
  diffAcceptanceRate: 1,
  cancellationRate: 0
}];

const completeHeatmap = Array.from({ length: 168 }, (_, index) => ({
  dayOfWeek: Math.floor(index / 24) + 1,
  hourOfDay: index % 24,
  activeUsers: 0,
  runCount: 0,
  userMessageCount: 0
}));

const peaks: AnalyticsPeaks = {
  peakPeriods: [],
  heatmap: completeHeatmap,
  freshness: overview.freshness
};

const filterOptions: AnalyticsFilterOptions = {
  organizations: [{ value: "总行", label: "总行" }],
  rdDepartments: [{ value: "研发一部", label: "研发一部" }],
  departments: [{ value: "平台处", label: "平台处" }],
  freshness: overview.freshness
};

const funnel: AnalyticsFunnel = {
  totalUsers: 10,
  activeUsers: 6,
  deepUsers: 2,
  activeRate: 0.6,
  deepRate: 1 / 3,
  activeDefinition: "活跃用户：所选时间内至少发送 1 条用户消息",
  deepDefinition: "深度用户：活跃用户中，至少 2 个自然日有使用且累计至少 5 条用户消息",
  freshness: overview.freshness
};

const hourlyHeatmap: AnalyticsHourlyHeatmap = {
  metric: "USER_MESSAGES",
  dates: ["2026-06-28", "2026-06-29"],
  points: Array.from({ length: 48 }, (_, index) => ({
    date: index < 24 ? "2026-06-28" : "2026-06-29",
    hourOfDay: index % 24,
    value: index === 8 ? 12 : 0
  })),
  freshness: overview.freshness
};

const tokenOperations: AnalyticsTokenOperations = {
  totalTokens: 240,
  primaryTokens: 200,
  cacheReadTokens: 30,
  cacheWriteTokens: 10,
  tokenUsers: 2,
  activeUsers: 3,
  tokenUserRate: 2 / 3,
  tokenActivePersonDays: 3,
  dailyTokensPerUser: 66.67,
  repeatTokenUsers: 1,
  repeatTokenUserRate: 0.5,
  daily: [],
  users: [],
  freshness: overview.freshness
};

const capabilities: AnalyticsCapabilities = {
  activeUsers: 3,
  rows: [{
    type: "TOOL",
    name: "bash",
    invocationCount: 12,
    userCount: 2,
    usageRate: 2 / 3,
    succeededCount: 10,
    failedCount: 1,
    cancelledCount: 0,
    incompleteCount: 1
  }],
  freshness: overview.freshness
};

function pageOf<T>(items: T[]): PageResponse<T> {
  return { items, page: 1, size: 20, total: items.length };
}

function api() {
  return {
    getAnalyticsFilterOptions: vi.fn().mockResolvedValue(filterOptions),
    getAnalyticsFunnel: vi.fn().mockResolvedValue(funnel),
    getAnalyticsHourlyHeatmap: vi.fn().mockResolvedValue(hourlyHeatmap),
    getAnalyticsTokenOperations: vi.fn().mockResolvedValue(tokenOperations),
    getAnalyticsCapabilities: vi.fn().mockResolvedValue(capabilities),
    getAnalyticsOverview: vi.fn().mockResolvedValue(overview),
    getAnalyticsTimeseries: vi.fn().mockResolvedValue(trend),
    getAnalyticsPeaks: vi.fn().mockResolvedValue(peaks),
    getAnalyticsUsers: vi.fn().mockResolvedValue(pageOf<AnalyticsUserUsageRow>([])),
    getAnalyticsOrganizations: vi.fn().mockResolvedValue([]),
    getAnalyticsSatisfaction: vi.fn().mockResolvedValue({
      positiveFeedbackCount: 3,
      negativeFeedbackCount: 1,
      satisfactionRate: 0.75,
      feedbackCoverageRate: 0.4,
      negativeReasonCounts: { WRONG_ANSWER: 1 },
      feedbackDetails: pageOf([]),
      freshness: overview.freshness
    } satisfies AnalyticsSatisfaction),
    getAnalyticsExceptions: vi.fn().mockResolvedValue(pageOf<AnalyticsExceptionDetail>([])),
    exportAnalyticsCsv: vi.fn().mockResolvedValue(new Blob(["metric,value\n"], { type: "text/csv" }))
  } as Partial<BackendApiClient> as BackendApiClient;
}

function renderPanel(backendApi: BackendApiClient) {
  const client = queryClient();
  const view = render(AnalyticsManagementPanel, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient: client }]],
      provide: { api: backendApi }
    }
  });
  return { ...view, queryClient: client };
}

describe("analytics management panel", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("renders stale analytics state, metrics and avoids cost wording", async () => {
    const backendApi = api();
    const view = renderPanel(backendApi);

    expect(await view.findByText("运营分析")).toBeTruthy();
    expect(await view.findByText(/可能延迟/)).toBeTruthy();
    expect(await view.findByText("用户使用漏斗")).toBeTruthy();
    expect(await view.findByText(funnel.activeDefinition)).toBeTruthy();
    const funnelStages = view.container.querySelectorAll(".ta-funnel-stage");
    expect(funnelStages).toHaveLength(3);
    expect((funnelStages[0] as HTMLElement).style.width).toBe("");
    expect((funnelStages[1] as HTMLElement).style.width).toBe("");
    expect((funnelStages[2] as HTMLElement).style.width).toBe("");
    expect((await view.findAllByText("主 Token 使用量")).length).toBeGreaterThanOrEqual(1);
    expect(await view.findByText("小时热力")).toBeTruthy();
    expect(await view.findByText("2 天 × 24 小时")).toBeTruthy();
    expect(view.container.querySelectorAll(".ta-heatmap-cell")).toHaveLength(48);
    expect(view.queryByLabelText("agent")).toBeNull();
    expect(view.queryByLabelText("model")).toBeNull();
    expect(view.queryByLabelText("workspace")).toBeNull();
    expect(view.container.textContent ?? "").not.toMatch(/成本|费用|花费|costUsd/i);
    view.queryClient.clear();
  });

  it("renders the selected date by hour heatmap grid", async () => {
    const backendApi = api();
    vi.mocked(backendApi.getAnalyticsTimeseries).mockResolvedValue([
      trend[0]!,
      { ...trend[0]!, bucketStart: "2026-06-29T00:00:00Z", runCount: 5 }
    ]);
    vi.mocked(backendApi.getAnalyticsHourlyHeatmap).mockResolvedValue(hourlyHeatmap);
    const view = renderPanel(backendApi);

    await waitFor(() => expect(view.container.querySelectorAll(".ta-trend-item")).toHaveLength(2));
    expect(view.container.querySelector<HTMLElement>(".ta-trend")?.style.getPropertyValue("--ta-trend-columns")).toBe("2");
    expect(view.container.querySelectorAll(".ta-heatmap-cell")).toHaveLength(48);
    view.queryClient.clear();
  });

  it("shows token adoption and capability usage as dedicated operations views", async () => {
    const backendApi = api();
    const view = renderPanel(backendApi);

    await fireEvent.click(await view.findByRole("button", { name: "Token 运营" }));
    expect(await view.findByText("日人均 Token")).toBeTruthy();
    expect(await view.findByText("66.67")).toBeTruthy();
    expect(await view.findByText("240")).toBeTruthy();

    await fireEvent.click(view.getByRole("button", { name: "能力使用" }));
    expect(await view.findByText("bash")).toBeTruthy();
    expect(await view.findByText("66.7%")).toBeTruthy();
    view.queryClient.clear();
  });

  it("defaults to thirty days and supports the ninety-day heatmap limit", async () => {
    const backendApi = api();
    const view = renderPanel(backendApi);

    await waitFor(() => expect(backendApi.getAnalyticsTimeseries).toHaveBeenCalled());
    const initialParams = vi.mocked(backendApi.getAnalyticsTimeseries).mock.calls[0]?.[0];
    expect(new Date(initialParams?.endTime ?? 0).getTime() - new Date(initialParams?.startTime ?? 0).getTime())
      .toBe(30 * 24 * 60 * 60 * 1000);

    await fireEvent.update(view.getByLabelText("快速范围"), "90");
    await waitFor(() => {
      const calls = vi.mocked(backendApi.getAnalyticsTimeseries).mock.calls;
      const latest = calls.at(-1)?.[0];
      expect(new Date(latest?.endTime ?? 0).getTime() - new Date(latest?.startTime ?? 0).getTime())
        .toBe(90 * 24 * 60 * 60 * 1000);
    });
    view.queryClient.clear();
  });

  it("keeps long-range analysis usable without requesting an unsupported heatmap", async () => {
    const backendApi = api();
    const view = renderPanel(backendApi);
    await view.findByText("小时热力");
    vi.mocked(backendApi.getAnalyticsHourlyHeatmap).mockClear();

    await fireEvent.update(view.getByLabelText("开始"), "2026-01-01T00:00");
    await fireEvent.update(view.getByLabelText("结束"), "2026-06-01T00:00");

    expect(await view.findByText("小时热力图最多支持 90 天")).toBeTruthy();
    expect(backendApi.getAnalyticsHourlyHeatmap).not.toHaveBeenCalled();
    await waitFor(() => expect(backendApi.getAnalyticsTimeseries).toHaveBeenCalled());
    view.queryClient.clear();
  });

  it("exports csv with the current overview filters", async () => {
    const backendApi = api();
    const createObjectURL = vi.fn(() => "blob:test");
    const revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
    Object.defineProperty(URL, "createObjectURL", { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: revokeObjectURL });
    const view = renderPanel(backendApi);

    await view.findByText("运营分析");
    await fireEvent.click(view.getByRole("button", { name: /导出 CSV/ }));

    await waitFor(() => expect(backendApi.exportAnalyticsCsv).toHaveBeenCalledWith("overview", expect.objectContaining({
      granularity: "day",
      topN: 20,
      pageSize: 20
    })));
    expect(createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:test");
    view.queryClient.clear();
  });
});
