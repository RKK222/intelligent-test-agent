import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { fireEvent, render, waitFor } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  InternalModelCallHourlyStat,
  InternalModelCallRecord
} from "@test-agent/shared-types";
import InternalModelObservabilityPanel from "../src/components/system/InternalModelObservabilityPanel.vue";

const chartOptions = vi.hoisted(() => [] as Array<{
  yAxis?: { name?: string } | Array<{ name?: string }>;
  series?: Array<{ type?: string; data?: unknown }>;
}>);

vi.mock("echarts", () => ({
  init: vi.fn((element: HTMLElement) => ({
    setOption: vi.fn((option) => chartOptions.push(option)),
    resize: vi.fn(),
    dispose: vi.fn(),
    isDisposed: vi.fn(() => false),
    getDom: vi.fn(() => element)
  }))
}));

const currentUser: CurrentUser = {
  userId: "usr_admin",
  username: "admin",
  unifiedAuthId: "AUTH_1",
  roles: ["SUPER_ADMIN"]
};

const record: InternalModelCallRecord = {
  id: 1,
  providerId: "local-mock",
  model: "mock-model",
  endpoint: "/chat/completions",
  source: "USER_CALL",
  outcome: "UPSTREAM_HTTP_ERROR",
  httpStatus: 502,
  streaming: true,
  durationMillis: 900,
  firstTokenMillis: 200,
  lastTokenMillis: 700,
  streamCompleteMillis: 800,
  outputTokenCount: 11,
  traceId: "trace_metric_help",
  ucid: "user-10086",
  startedAt: "2026-08-07T09:10:00Z"
};

const stats: InternalModelCallHourlyStat[] = [
  {
    statHour: "2026-08-07T09:00:00Z",
    providerId: "local-mock",
    model: "mock-model",
    endpoint: "/chat/completions",
    source: "USER_CALL",
    outcome: "SUCCESS",
    requestCount: 180,
    durationMillisSum: 180_000,
    durationMillisMax: 2_000,
    firstTokenMillisSum: 36_000,
    firstTokenMillisMax: 400,
    firstTokenCount: 180,
    streamCompleteMillisSum: 144_000,
    streamCompleteMillisMax: 1_600,
    streamCompleteCount: 180
  },
  {
    statHour: "2026-08-07T10:00:00Z",
    providerId: "local-mock",
    model: "mock-model",
    endpoint: "/chat/completions",
    source: "USER_CALL",
    outcome: "UPSTREAM_HTTP_ERROR",
    requestCount: 180,
    durationMillisSum: 90_000,
    durationMillisMax: 1_000,
    firstTokenMillisSum: 0,
    firstTokenMillisMax: 0,
    firstTokenCount: 0,
    streamCompleteMillisSum: 0,
    streamCompleteMillisMax: 0,
    streamCompleteCount: 0
  }
];

function renderPanel(recordsTotal = 1) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });
  const api = {
    getInternalModelProbeStatus: vi.fn().mockResolvedValue([{
      providerId: "local-mock",
      lastOutcome: "SUCCESS",
      lastProbedAt: "2026-08-07T09:00:00Z",
      consecutiveFailures: 0,
      traceId: "trace_probe"
    }]),
    listInternalModelCallRecords: vi.fn().mockResolvedValue({
      items: [record], page: 1, size: 20, total: recordsTotal
    }),
    getInternalModelCallStats: vi.fn().mockResolvedValue(stats),
    getInternalModelTtftDistribution: vi.fn().mockResolvedValue({
      sampleCount: 4,
      minimumMillis: 100,
      firstQuartileMillis: 175,
      medianMillis: 250,
      thirdQuartileMillis: 325,
      maximumMillis: 400
    }),
    getInternalModelItlDistribution: vi.fn().mockResolvedValue({
      sampleCount: 4,
      minimumMillis: 20,
      firstQuartileMillis: 35,
      medianMillis: 50,
      thirdQuartileMillis: 65,
      maximumMillis: 80
    }),
    triggerInternalModelProbe: vi.fn().mockResolvedValue({})
  } as Partial<BackendApiClient> as BackendApiClient;

  const view = render(InternalModelObservabilityPanel, {
    props: { currentUser },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      provide: { api },
      stubs: {
        ElPagination: {
          props: ["currentPage", "pageSize", "pageSizes", "total"],
          emits: ["current-change", "size-change"],
          template: `
            <div data-testid="records-pagination">
              <span>total={{ total }}</span>
              <span>page={{ currentPage }}</span>
              <span>size={{ pageSize }}</span>
              <span>options={{ pageSizes.join(',') }}</span>
              <button type="button" @click="$emit('current-change', 2)">下一页</button>
              <button type="button" @click="$emit('size-change', 50)">每页 50 条</button>
            </div>
          `
        },
        ElTooltip: {
          props: ["content", "trigger"],
          template: `<span class="metric-tooltip-stub" :data-description="content" :data-trigger="Array.isArray(trigger) ? trigger.join(',') : trigger"><slot /></span>`
        }
      }
    }
  });
  return { ...view, api, queryClient };
}

describe("InternalModelObservabilityPanel", () => {
  afterEach(() => {
    chartOptions.length = 0;
    vi.restoreAllMocks();
  });

  it("uses standard metric names, plain-language help, grouped outcomes, and the user id", async () => {
    vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-08-07T10:30:00Z"));
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(800);
    const view = renderPanel();

    await view.findByText("Overview");
    expect(view.getByText(/当前 24 小时时间段/)).toBeTruthy();
    expect(view.getByText(/所有时长统一使用秒（s）/)).toBeTruthy();
    expect(await view.findByText("0.0043")).toBeTruthy();
    expect(await view.findByText("user-10086")).toBeTruthy();
    expect(view.getAllByText("上游服务异常").length).toBeGreaterThan(0);
    expect(view.getByText("上游 HTTP 错误")).toBeTruthy();
    expect(await view.findByText("中间 50%：0.175s–0.325s")).toBeTruthy();
    expect(view.getByText("中位数：0.25s")).toBeTruthy();
    expect(view.getByText("中位数：0.05s")).toBeTruthy();
    expect(view.getAllByText("0.05s").length).toBeGreaterThan(0);
    expect(view.getAllByText("样本：4 次")).toHaveLength(2);
    expect(view.container.textContent).not.toMatch(/\d(?:\.\d+)?ms\b/);
    expect(view.container.querySelectorAll(".ta-imob-chart-box")).toHaveLength(2);
    const comparison = view.container.querySelector(".ta-imob-chart-comparison");
    expect(comparison?.querySelector(".ta-imob-chart-stack")).toBeTruthy();
    expect(comparison?.querySelectorAll(".ta-imob-latency-box-stack > .ta-imob-box-card")).toHaveLength(2);
    expect(comparison?.querySelectorAll(".ta-imob-chart-stack > .ta-imob-chart-card")).toHaveLength(2);

    await waitFor(() => {
      const ttftOption = [...chartOptions].reverse().find((option) =>
        !Array.isArray(option.yAxis) && option.yAxis?.name === "TTFT (s)"
      );
      const itlOption = [...chartOptions].reverse().find((option) =>
        !Array.isArray(option.yAxis) && option.yAxis?.name === "ITL / TPOT (s)"
      );
      expect(ttftOption?.series?.[0]?.data).toEqual([[0.1, 0.175, 0.25, 0.325, 0.4]]);
      expect(itlOption?.series?.[0]?.data).toEqual([[0.02, 0.035, 0.05, 0.065, 0.08]]);
    });

    const explainedLabels = [
      "REQ", "Providers", "SR", "FR", "Failures",
      "Avg E2E", "Max E2E", "Total Duration", "RPS",
      "Avg TTFT", "Max TTFT",
      "Avg SCT", "Max SCT",
      "请求数与成功率趋势", "TTFT 分布（箱线图）", "ITL / TPOT 分布（箱线图）",
      "调用结果分布", "失败原因分类", "供应商请求量对比",
      "E2E Latency", "TTFT", "ITL / TPOT", "SCT"
    ];
    for (const label of explainedLabels) {
      expect(view.getAllByRole("button", { name: `查看${label}说明` }).length).toBeGreaterThan(0);
    }

    await waitFor(() => {
      const tooltips = [...view.container.querySelectorAll<HTMLElement>("[data-description]")]
        .filter((item) => item.querySelector(".ta-metric-help-button"));
      const descriptions = tooltips.map((item) => item.dataset.description ?? "");
      expect(descriptions.length).toBeGreaterThanOrEqual(explainedLabels.length);
      expect(tooltips.every((item) => item.dataset.trigger === "hover,focus")).toBe(true);
      expect(descriptions.join(" ")).not.toMatch(/SSE|\[DONE]|SQL|chunk|sum|count/i);
      expect(descriptions.some((description) => description.includes("没有返回实际回答的调用不参与"))).toBe(true);
      expect(descriptions.some((description) => description.includes("不是瞬时峰值"))).toBe(true);
    });
    view.queryClient.clear();
  });

  it("uses the server total for pagination and reloads after page or size changes", async () => {
    vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-08-07T10:30:00Z"));
    const view = renderPanel(41);

    await waitFor(() => {
      expect(view.getByTestId("records-pagination").textContent).toContain("total=41");
      expect(view.getByTestId("records-pagination").textContent).toContain("options=20,50,100");
      expect(view.api.listInternalModelCallRecords).toHaveBeenCalledWith(expect.objectContaining({
        page: 1,
        size: 20
      }));
    });

    await fireEvent.click(view.getByRole("button", { name: "下一页" }));
    await waitFor(() => {
      expect(view.api.listInternalModelCallRecords).toHaveBeenCalledWith(expect.objectContaining({
        page: 2,
        size: 20
      }));
    });

    await fireEvent.click(view.getByRole("button", { name: "每页 50 条" }));
    await waitFor(() => {
      expect(view.api.listInternalModelCallRecords).toHaveBeenCalledWith(expect.objectContaining({
        page: 1,
        size: 50
      }));
    });
    expect(view.getByTestId("records-pagination").textContent).toContain("page=1");
    expect(view.getByTestId("records-pagination").textContent).toContain("size=50");
    view.queryClient.clear();
  });
});
