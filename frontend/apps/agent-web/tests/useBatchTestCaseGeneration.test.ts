import { describe, expect, it, vi } from "vitest";
import { BackendApiError } from "@test-agent/backend-api";
import { useBatchTestCaseGeneration } from "../src/components/useBatchTestCaseGeneration";

const references = [
  {
    id: "spec/需求一/01-需求/登录",
    requirementName: "需求一",
    subitemName: "登录",
    filePaths: ["spec/需求一/01-需求/登录/需求.md"]
  },
  {
    id: "spec/需求一/01-需求/支付",
    requirementName: "需求一",
    subitemName: "支付",
    filePaths: ["spec/需求一/01-需求/支付/需求.md"]
  }
];

describe("useBatchTestCaseGeneration", () => {
  it("continues other items when context loading fails before session creation", async () => {
    const api = {
      readFile: vi.fn(async (_workspaceId: string, path: string) => {
        if (path.includes("登录")) throw new Error("read failed");
        return { content: "支付需求正文" };
      }),
      createBatchItemSession: vi.fn(async (_workspaceId: string, _title: string, _batchContext: { batchId: string; itemRequestId: string }) => ({ sessionId: "ses_pay" })),
      startRun: vi.fn(async (_payload: Record<string, unknown>) => ({ runId: "run_pay" })),
      createNightExecutionTask: vi.fn()
    };
    const generation = useBatchTestCaseGeneration({
      api: api as any,
      conversationContexts: {
        get: vi.fn(async () => ({ contextToken: "ctx", contextVersion: 1, expiresAt: "2026-08-07T13:00:00Z" })),
        invalidate: vi.fn(),
        clear: vi.fn()
      },
      references: () => references,
      workspaceId: () => "wrk_batch",
      agent: () => "build",
      model: () => "provider/model",
      mode: () => "build",
      nightSlots: () => null
    });

    const result = await generation.execute({
      referenceIds: references.map((item) => item.id),
      requirement: "请生成子条目测试案例。",
      executionMode: "immediate"
    });

    expect(result).toEqual({ total: 2, succeeded: 1, failed: 1, capacityConflict: false });
    expect(api.createBatchItemSession).toHaveBeenCalledTimes(1);
    expect(generation.itemStates.value[references[0]!.id]?.errorCode).toBe("FILE_READ_FAILED");
    expect(generation.itemStates.value[references[1]!.id]).toEqual(expect.objectContaining({
      status: "succeeded",
      sessionId: "ses_pay",
      runId: "run_pay"
    }));
  });

  it("reuses batch item and run request ids when retrying a failed run start", async () => {
    const api = {
      readFile: vi.fn(async () => ({ content: "需求正文" })),
      createBatchItemSession: vi.fn(async (_workspaceId: string, _title: string, _batchContext: { batchId: string; itemRequestId: string }) => ({ sessionId: "ses_login" })),
      startRun: vi.fn<(payload: Record<string, unknown>) => Promise<{ runId: string }>>()
        .mockRejectedValueOnce(new Error("temporary"))
        .mockResolvedValueOnce({ runId: "run_login" }),
      createNightExecutionTask: vi.fn()
    };
    const generation = useBatchTestCaseGeneration({
      api: api as any,
      conversationContexts: {
        get: vi.fn(async () => ({ contextToken: "ctx", contextVersion: 1, expiresAt: "2026-08-07T13:00:00Z" })),
        invalidate: vi.fn(),
        clear: vi.fn()
      },
      references: () => references,
      workspaceId: () => "wrk_batch",
      agent: () => "build",
      model: () => undefined,
      mode: () => "build",
      nightSlots: () => null
    });
    const request = {
      referenceIds: [references[0]!.id],
      requirement: "请生成子条目测试案例。",
      executionMode: "immediate" as const
    };

    await generation.execute(request);
    await generation.execute({ ...request, retry: true });

    const firstContext = api.createBatchItemSession.mock.calls[0]![2];
    const retryContext = api.createBatchItemSession.mock.calls[1]![2];
    expect(retryContext).toEqual(firstContext);
    expect(api.startRun.mock.calls[0]![0].clientRequestId)
      .toBe(api.startRun.mock.calls[1]![0].clientRequestId);
    expect(generation.itemStates.value[references[0]!.id]?.status).toBe("succeeded");
  });

  it("does not misclassify an ordinary conflict as a night capacity conflict", async () => {
    const api = {
      readFile: vi.fn(async () => ({ content: "需求正文" })),
      createBatchItemSession: vi.fn(async () => {
        throw new BackendApiError(409, {
          success: false,
          code: "CONFLICT",
          message: "批量会话归因写入失败",
          traceId: "trace_batch_conflict",
          details: { itemRequestId: "item_conflict" }
        });
      }),
      startRun: vi.fn(),
      createNightExecutionTask: vi.fn()
    };
    const generation = useBatchTestCaseGeneration({
      api: api as any,
      conversationContexts: {
        get: vi.fn(),
        invalidate: vi.fn(),
        clear: vi.fn()
      },
      references: () => references,
      workspaceId: () => "wrk_batch",
      agent: () => "build",
      model: () => undefined,
      mode: () => "build",
      nightSlots: () => null
    });

    const result = await generation.execute({
      referenceIds: [references[0]!.id],
      requirement: "请生成子条目测试案例。",
      executionMode: "immediate"
    });

    expect(result.capacityConflict).toBe(false);
    expect(generation.itemStates.value[references[0]!.id]?.errorCode).toBe("CONFLICT");
  });
});
