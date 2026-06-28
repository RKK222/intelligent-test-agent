import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  AnalyticsExceptionDetail,
  AnalyticsOverview,
  AnalyticsPeaks,
  AnalyticsSatisfaction,
  AnalyticsTimeSeriesPoint,
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

const peaks: AnalyticsPeaks = {
  peakPeriods: [],
  heatmap: [],
  freshness: overview.freshness
};

function pageOf<T>(items: T[]): PageResponse<T> {
  return { items, page: 1, size: 20, total: items.length };
}

function api() {
  return {
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
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("renders stale analytics state, metrics and avoids cost wording", async () => {
    const backendApi = api();
    const view = renderPanel(backendApi);

    expect(await view.findByText("运营分析")).toBeTruthy();
    expect(await view.findByText(/可能延迟/)).toBeTruthy();
    expect(await view.findByText("活跃用户")).toBeTruthy();
    expect((await view.findAllByText("token 使用量")).length).toBeGreaterThanOrEqual(1);
    expect(await view.findByText("小时热力")).toBeTruthy();
    expect(await view.findByText("暂无数据")).toBeTruthy();
    expect(view.container.textContent ?? "").not.toMatch(/成本|费用|花费|costUsd/i);
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
      topN: 10,
      pageSize: 20
    })));
    expect(createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:test");
    view.queryClient.clear();
  });
});
