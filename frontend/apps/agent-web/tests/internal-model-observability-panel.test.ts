import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { cleanup, fireEvent, render, waitFor } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  InternalModelCallHourlyStat,
  InternalModelCallRecord
} from "@test-agent/shared-types";
import InternalModelObservabilityPanel from "../src/components/system/InternalModelObservabilityPanel.vue";

const chartOptions = vi.hoisted(() => [] as Array<{
  xAxis?: { data?: unknown };
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
  model: "Qwen3.6-27B",
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
    model: "Qwen3.6-27B",
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
    model: "Qwen3.6-27B",
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
  },
  {
    statHour: "2026-08-07T10:00:00Z",
    providerId: "vendor-b",
    model: "DeepSeek-V4-Flash",
    endpoint: "/chat/completions",
    source: "USER_CALL",
    outcome: "SUCCESS",
    requestCount: 180,
    durationMillisSum: 270_000,
    durationMillisMax: 3_000,
    firstTokenMillisSum: 90_000,
    firstTokenMillisMax: 900,
    firstTokenCount: 180,
    streamCompleteMillisSum: 216_000,
    streamCompleteMillisMax: 2_400,
    streamCompleteCount: 180
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
    listInternalModelCallRecords: vi.fn().mockImplementation((params: { ucid?: string | null; page?: number; size?: number }) =>
      Promise.resolve({
        items: !params.ucid || record.ucid?.toLowerCase().includes(params.ucid.toLowerCase()) ? [record] : [],
        page: params.page ?? 1,
        size: params.size ?? 20,
        total: params.ucid ? 1 : recordsTotal
      })),
    getInternalModelCallStats: vi.fn().mockResolvedValue(stats),
    getInternalModelTtftDistribution: vi.fn().mockImplementation((params: { providerId?: string | null }) =>
      Promise.resolve(params.providerId === "vendor-b" ? {
        sampleCount: 4,
        averageMillis: 700,
        minimumMillis: 500,
        firstQuartileMillis: 600,
        medianMillis: 700,
        thirdQuartileMillis: 800,
        maximumMillis: 900
      } : {
        sampleCount: 4,
        averageMillis: 250,
        minimumMillis: 100,
        firstQuartileMillis: 175,
        medianMillis: 250,
        thirdQuartileMillis: 325,
        maximumMillis: 400
      })),
    getInternalModelItlDistribution: vi.fn().mockImplementation((params: { providerId?: string | null }) => {
      if (!params.providerId) {
        return Promise.resolve({
          sampleCount: 8,
          averageMillis: 95,
          minimumMillis: 20,
          firstQuartileMillis: 50,
          medianMillis: 90,
          thirdQuartileMillis: 140,
          maximumMillis: 180
        });
      }
      return Promise.resolve(params.providerId === "vendor-b" ? {
        sampleCount: 4,
        averageMillis: 140,
        minimumMillis: 100,
        firstQuartileMillis: 120,
        medianMillis: 140,
        thirdQuartileMillis: 160,
        maximumMillis: 180
      } : {
        sampleCount: 4,
        averageMillis: 50,
        minimumMillis: 20,
        firstQuartileMillis: 35,
        medianMillis: 50,
        thirdQuartileMillis: 65,
        maximumMillis: 80
      });
    }),
    getInternalModelTpsDistribution: vi.fn().mockImplementation((params: { providerId?: string | null }) => {
      if (!params.providerId) {
        return Promise.resolve({
          sampleCount: 8,
          averageTokensPerSecond: 88,
          minimumTokensPerSecond: 40,
          firstQuartileTokensPerSecond: 55,
          medianTokensPerSecond: 80,
          thirdQuartileTokensPerSecond: 110,
          maximumTokensPerSecond: 140
        });
      }
      return Promise.resolve(params.providerId === "vendor-b" ? {
        sampleCount: 4,
        averageTokensPerSecond: 100,
        minimumTokensPerSecond: 80,
        firstQuartileTokensPerSecond: 90,
        medianTokensPerSecond: 100,
        thirdQuartileTokensPerSecond: 110,
        maximumTokensPerSecond: 120
      } : {
        sampleCount: 4,
        averageTokensPerSecond: 60,
        minimumTokensPerSecond: 40,
        firstQuartileTokensPerSecond: 50,
        medianTokensPerSecond: 60,
        thirdQuartileTokensPerSecond: 70,
        maximumTokensPerSecond: 80
      });
    }),
    triggerInternalModelProbe: vi.fn().mockResolvedValue({})
  } as Partial<BackendApiClient> as BackendApiClient;

  const view = render(InternalModelObservabilityPanel, {
    props: { currentUser },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      provide: { api },
      stubs: {
        ElPopover: {
          template: `<div><slot name="reference" /><slot /></div>`
        },
        ElDatePicker: {
          emits: ["update:modelValue", "change"],
          template: `
            <div>
              <button type="button" data-testid="partial-time" @click="$emit('update:modelValue', ['2026-08-01 00:00:00', '']); $emit('change', ['2026-08-01 00:00:00', ''])">只选开始时间</button>
              <button type="button" data-testid="complete-time" @click="$emit('update:modelValue', ['2026-08-01 00:00:00', '2026-08-01 02:00:00']); $emit('change', ['2026-08-01 00:00:00', '2026-08-01 02:00:00'])">选完整时间</button>
            </div>
          `
        },
        ElSelect: {
          props: ["modelValue", "placeholder"],
          emits: ["update:modelValue", "change"],
          template: `<select :aria-label="placeholder" :value="modelValue" @change="$emit('update:modelValue', $event.target.value); $emit('change', $event.target.value)"><option value=""></option><slot /></select>`
        },
        ElOption: {
          props: ["label", "value"],
          template: `<option :value="value">{{ label }}</option>`
        },
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
    cleanup();
    chartOptions.length = 0;
    vi.restoreAllMocks();
  });

  it("uses standard metric names, plain-language help, grouped outcomes, and the user id", async () => {
    vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-08-07T10:30:00Z"));
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(800);
    const view = renderPanel();

    await view.findByText("Overview");
    expect(view.getByText(/当前 24 小时时间段/)).toBeTruthy();
    expect(view.getByText(/ITL \/ TPOT 使用毫秒（ms）/)).toBeTruthy();
    expect(await view.findByText("0.0064")).toBeTruthy();
    expect((await view.findAllByText("user-10086")).length).toBeGreaterThan(0);
    expect(view.getAllByText("上游服务异常").length).toBeGreaterThan(0);
    expect(view.getByText("上游 HTTP 错误")).toBeTruthy();
    expect(await view.findByText("中间 50%：0.175s–0.325s")).toBeTruthy();
    expect(view.getByText("中位数：0.25s")).toBeTruthy();
    expect(view.getByText("中位数：50ms")).toBeTruthy();
    expect(view.getByText("中位数：140ms")).toBeTruthy();
    expect(view.getByText("中位数：60 tokens/s")).toBeTruthy();
    expect(view.getByText("中位数：100 tokens/s")).toBeTruthy();
    expect(view.getAllByText("样本：4 次")).toHaveLength(6);
    expect(view.getByText("95ms")).toBeTruthy();
    expect(view.getByText("180ms")).toBeTruthy();
    expect(view.getByText("88 tokens/s")).toBeTruthy();
    expect(view.getByText("公开性能参考（方向性对标）")).toBeTruthy();
    expect(view.getByText("Qwen3.6 27B Reasoning")).toBeTruthy();
    expect(view.getByText("DeepSeek V4 Flash Reasoning Max")).toBeTruthy();
    expect(view.container.querySelectorAll(".ta-imob-chart-box")).toHaveLength(3);
    const comparison = view.container.querySelector(".ta-imob-chart-comparison");
    expect(comparison?.querySelector(".ta-imob-chart-stack")).toBeTruthy();
    expect(comparison?.querySelectorAll(".ta-imob-latency-box-stack > .ta-imob-box-card")).toHaveLength(3);
    expect(comparison?.querySelectorAll(".ta-imob-chart-stack > .ta-imob-chart-card")).toHaveLength(2);

    await waitFor(() => {
      const ttftOption = [...chartOptions].reverse().find((option) =>
        !Array.isArray(option.yAxis) && option.yAxis?.name === "TTFT (s)"
      );
      const itlOption = [...chartOptions].reverse().find((option) =>
        !Array.isArray(option.yAxis) && option.yAxis?.name === "ITL / TPOT (ms)"
      );
      const tpsOption = [...chartOptions].reverse().find((option) =>
        !Array.isArray(option.yAxis) && option.yAxis?.name === "Output TPS (tokens/s)"
      );
      expect(ttftOption?.xAxis?.data).toEqual(["local-mock", "vendor-b"]);
      expect(ttftOption?.series?.[0]?.data).toEqual([
        [0.1, 0.175, 0.25, 0.325, 0.4],
        [0.5, 0.6, 0.7, 0.8, 0.9]
      ]);
      expect(itlOption?.xAxis?.data).toEqual(["local-mock", "vendor-b"]);
      expect(itlOption?.series?.[0]?.data).toEqual([
        [20, 35, 50, 65, 80],
        [100, 120, 140, 160, 180]
      ]);
      expect(tpsOption?.xAxis?.data).toEqual(["local-mock", "vendor-b"]);
      expect(tpsOption?.series?.[0]?.data).toEqual([
        [40, 50, 60, 70, 80],
        [80, 90, 100, 110, 120]
      ]);
    });

    expect(view.api.getInternalModelItlDistribution).toHaveBeenCalledWith(expect.objectContaining({
      providerId: null,
      source: "USER_CALL"
    }));
    expect(view.api.getInternalModelItlDistribution).toHaveBeenCalledWith(expect.objectContaining({
      providerId: "local-mock"
    }));
    expect(view.api.getInternalModelItlDistribution).toHaveBeenCalledWith(expect.objectContaining({
      providerId: "vendor-b"
    }));
    expect(view.api.getInternalModelTpsDistribution).toHaveBeenCalledWith(expect.objectContaining({
      providerId: null,
      source: "USER_CALL"
    }));

    const explainedLabels = [
      "REQ", "Providers", "SR", "FR", "Failures",
      "Avg E2E", "Max E2E", "Total Duration", "RPS",
      "Avg TTFT", "Max TTFT", "Avg ITL / TPOT", "Max ITL / TPOT", "Avg Output TPS", "P50 Output TPS",
      "Avg SCT", "Max SCT",
      "请求数与成功率趋势", "TTFT 厂商对比（箱线图）", "ITL / TPOT 厂商对比（箱线图）", "Output TPS 厂商对比（箱线图）",
      "调用结果分布", "失败原因分类", "供应商请求量对比",
      "E2E Latency", "TTFT", "ITL / TPOT", "Output TPS", "SCT"
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
    // 当前页只有 1 条，但全量 Overview 仍来自后端聚合的 540 次调用。
    expect(view.getByText("540")).toBeTruthy();

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

  it("waits for a complete custom range and applies the user filter on the server", async () => {
    vi.spyOn(Date, "now").mockReturnValue(Date.parse("2026-08-07T10:30:00Z"));
    const view = renderPanel(41);

    await waitFor(() => expect(view.getByTestId("records-pagination").textContent).toContain("total=41"));
    const initialCalls = vi.mocked(view.api.listInternalModelCallRecords).mock.calls.length;

    await fireEvent.click(view.getByTestId("partial-time"));
    await Promise.resolve();
    expect(vi.mocked(view.api.listInternalModelCallRecords).mock.calls).toHaveLength(initialCalls);

    await fireEvent.click(view.getByTestId("complete-time"));
    const expectedFrom = new Date("2026-08-01T00:00:00").toISOString();
    const expectedTo = new Date("2026-08-01T02:00:00").toISOString();
    await waitFor(() => {
      expect(view.api.listInternalModelCallRecords).toHaveBeenCalledWith(expect.objectContaining({
        from: expectedFrom,
        to: expectedTo,
        page: 1
      }));
    });

    await fireEvent.update(view.getByRole("listbox", { name: "按用户" }), "user-10086");
    await waitFor(() => {
      expect(view.api.listInternalModelCallRecords).toHaveBeenCalledWith(expect.objectContaining({
        ucid: "user-10086",
        page: 1
      }));
      expect(view.getByTestId("records-pagination").textContent).toContain("total=1");
      expect(view.getAllByText("user-10086").length).toBeGreaterThan(0);
    });
    view.queryClient.clear();
  });
});
