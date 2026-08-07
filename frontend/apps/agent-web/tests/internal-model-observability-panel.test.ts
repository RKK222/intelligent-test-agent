import { QueryClient, VueQueryPlugin } from "@tanstack/vue-query";
import { render, waitFor } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  InternalModelCallHourlyStat,
  InternalModelCallRecord
} from "@test-agent/shared-types";
import InternalModelObservabilityPanel from "../src/components/system/InternalModelObservabilityPanel.vue";

vi.mock("echarts", () => ({
  init: vi.fn(() => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() }))
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
  outcome: "SUCCESS",
  streaming: true,
  durationMillis: 900,
  firstTokenMillis: 200,
  streamCompleteMillis: 800,
  traceId: "trace_metric_help",
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

function renderPanel() {
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
      items: [record], page: 1, size: 20, total: 1
    }),
    getInternalModelCallStats: vi.fn().mockResolvedValue(stats),
    triggerInternalModelProbe: vi.fn().mockResolvedValue({})
  } as Partial<BackendApiClient> as BackendApiClient;

  const view = render(InternalModelObservabilityPanel, {
    props: { currentUser },
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      provide: { api },
      stubs: {
        ElTooltip: {
          props: ["content", "trigger"],
          template: `<span class="metric-tooltip-stub" :data-description="content" :data-trigger="Array.isArray(trigger) ? trigger.join(',') : trigger"><slot /></span>`
        }
      }
    }
  });
  return { ...view, queryClient };
}

describe("InternalModelObservabilityPanel", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("explains every displayed metric in plain language and includes both hourly buckets in QPS", async () => {
    const view = renderPanel();

    await view.findByText("总览");
    expect(view.getByText(/默认查看最近 24 小时的用户调用/)).toBeTruthy();
    expect(await view.findByText("0.05")).toBeTruthy();

    const explainedLabels = [
      "总请求", "供应商", "成功率", "失败率", "失败数", "平均耗时", "最大耗时", "总耗时", "QPS",
      "平均首 token", "最大首 token", "平均流完成", "最大流完成", "小时趋势", "成功率构成", "失败分类",
      "按供应商请求量", "端到端耗时", "首 token", "流完成", "失败", "请求数", "耗时合计"
    ];
    for (const label of explainedLabels) {
      expect(view.getAllByRole("button", { name: `查看${label}说明` }).length).toBeGreaterThan(0);
    }

    await waitFor(() => {
      const descriptions = [...view.container.querySelectorAll<HTMLElement>("[data-description]")]
        .map((item) => item.dataset.description ?? "");
      expect(descriptions.length).toBeGreaterThanOrEqual(explainedLabels.length);
      expect([...view.container.querySelectorAll<HTMLElement>("[data-description]")]
        .every((item) => item.dataset.trigger === "hover,focus")).toBe(true);
      expect(descriptions.join(" ")).not.toMatch(/SSE|\[DONE]|SQL|chunk|sum|count/i);
      expect(descriptions.some((description) => description.includes("没有返回回答的调用不参与"))).toBe(true);
      expect(descriptions.some((description) => description.includes("不是瞬时峰值"))).toBe(true);
    });
    view.queryClient.clear();
  });
});
