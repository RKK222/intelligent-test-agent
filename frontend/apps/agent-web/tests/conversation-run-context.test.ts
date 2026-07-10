import { describe, expect, it, vi } from "vitest";
import {
  createConversationRunContextCache,
  startRunWithConversationContext
} from "../src/components/conversation-run-context";

describe("conversation run context", () => {
  it("loads one context per session and reuses the same in-flight request", async () => {
    const loader = vi.fn(async (sessionId: string) => ({
      contextToken: `ctx_${sessionId}`,
      contextVersion: 1,
      expiresAt: "2026-07-11T08:00:00Z"
    }));
    const cache = createConversationRunContextCache(loader);

    const [first, second] = await Promise.all([cache.get("ses_1"), cache.get("ses_1")]);
    const third = await cache.get("ses_1");

    expect(first).toEqual(second);
    expect(third).toEqual(first);
    expect(loader).toHaveBeenCalledTimes(1);
  });

  it.each(["CONVERSATION_CONTEXT_REQUIRED", "CONVERSATION_CONTEXT_EXPIRED"])(
    "refreshes %s once and retries with the same client request id",
    async (code) => {
      const loader = vi
        .fn()
        .mockResolvedValueOnce({ contextToken: "ctx_old", contextVersion: 1, expiresAt: "2026-07-11T08:00:00Z" })
        .mockResolvedValueOnce({ contextToken: "ctx_new", contextVersion: 2, expiresAt: "2026-07-11T09:00:00Z" });
      const cache = createConversationRunContextCache(loader);
      const sent: Array<Record<string, unknown>> = [];
      const startRun = vi.fn(async (payload: Record<string, unknown>) => {
        sent.push(payload);
        if (sent.length === 1) {
          throw { code };
        }
        return { runId: "run_1" };
      });

      await expect(
        startRunWithConversationContext({
          cache,
          payload: { sessionId: "ses_1", prompt: "hello" },
          startRun,
          clientRequestIdFactory: () => "req_stable"
        })
      ).resolves.toEqual({ runId: "run_1" });

      expect(loader).toHaveBeenCalledTimes(2);
      expect(sent).toEqual([
        { sessionId: "ses_1", prompt: "hello", contextToken: "ctx_old", clientRequestId: "req_stable" },
        { sessionId: "ses_1", prompt: "hello", contextToken: "ctx_new", clientRequestId: "req_stable" }
      ]);
    }
  );

  it("does not retry a context error more than once", async () => {
    const loader = vi
      .fn()
      .mockResolvedValueOnce({ contextToken: "ctx_old", contextVersion: 1, expiresAt: "2026-07-11T08:00:00Z" })
      .mockResolvedValueOnce({ contextToken: "ctx_new", contextVersion: 2, expiresAt: "2026-07-11T09:00:00Z" });
    const cache = createConversationRunContextCache(loader);
    const startRun = vi.fn().mockRejectedValue({ code: "CONVERSATION_CONTEXT_EXPIRED" });

    await expect(
      startRunWithConversationContext({
        cache,
        payload: { sessionId: "ses_1", prompt: "hello" },
        startRun,
        clientRequestIdFactory: () => "req_stable"
      })
    ).rejects.toMatchObject({ code: "CONVERSATION_CONTEXT_EXPIRED" });

    expect(loader).toHaveBeenCalledTimes(2);
    expect(startRun).toHaveBeenCalledTimes(2);
    expect(startRun.mock.calls.map(([payload]) => payload.clientRequestId)).toEqual(["req_stable", "req_stable"]);
  });

  it("invalidates a second rejected context so the next operation signs a fresh one", async () => {
    const loader = vi
      .fn()
      .mockResolvedValueOnce({ contextToken: "ctx_old", contextVersion: 1, expiresAt: "2026-07-11T08:00:00Z" })
      .mockResolvedValueOnce({ contextToken: "ctx_refreshed", contextVersion: 2, expiresAt: "2026-07-11T09:00:00Z" })
      .mockResolvedValueOnce({ contextToken: "ctx_next_operation", contextVersion: 3, expiresAt: "2026-07-11T10:00:00Z" });
    const cache = createConversationRunContextCache(loader);
    const sent: Array<Record<string, unknown>> = [];
    const startRun = vi.fn(async (payload: Record<string, unknown>) => {
      sent.push(payload);
      if (payload.contextToken !== "ctx_next_operation") {
        throw { code: "CONVERSATION_CONTEXT_EXPIRED" };
      }
      return { runId: "run_2" };
    });
    const clientRequestIdFactory = vi
      .fn()
      .mockReturnValueOnce("req_first_operation")
      .mockReturnValueOnce("req_second_operation");

    await expect(
      startRunWithConversationContext({
        cache,
        payload: { sessionId: "ses_1", prompt: "first" },
        startRun,
        clientRequestIdFactory
      })
    ).rejects.toMatchObject({ code: "CONVERSATION_CONTEXT_EXPIRED" });

    await expect(
      startRunWithConversationContext({
        cache,
        payload: { sessionId: "ses_1", prompt: "second" },
        startRun,
        clientRequestIdFactory
      })
    ).resolves.toEqual({ runId: "run_2" });

    expect(loader).toHaveBeenCalledTimes(3);
    expect(sent.map((payload) => payload.contextToken)).toEqual([
      "ctx_old",
      "ctx_refreshed",
      "ctx_next_operation"
    ]);
    expect(sent.map((payload) => payload.clientRequestId)).toEqual([
      "req_first_operation",
      "req_first_operation",
      "req_second_operation"
    ]);
  });
});
