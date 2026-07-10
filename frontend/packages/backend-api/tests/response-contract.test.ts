import { describe, expect, it } from "vitest";
import type {
  Run,
  SessionMessage,
  SessionTreeMessagesResponse
} from "@test-agent/shared-types";

describe("Redis summary response contracts", () => {
  it("accepts the optional Run storage metadata", () => {
    const run = {
      runId: "run_1",
      sessionId: "ses_1",
      workspaceId: "wrk_1",
      status: "SUCCEEDED",
      createdAt: "2026-07-10T00:00:00Z",
      updatedAt: "2026-07-10T00:01:00Z",
      storageMode: "REDIS_SUMMARY",
      clientRequestId: "req_1",
      detailsAvailableUntil: "2026-07-11T00:00:00Z"
    } satisfies Run;

    expect(run.storageMode).toBe("REDIS_SUMMARY");
  });

  it("accepts optional terminal summary metadata on messages", () => {
    const message = {
      messageId: "msg_1",
      sessionId: "ses_1",
      role: "ASSISTANT",
      content: "回答摘要",
      createdAt: "2026-07-10T00:01:00Z",
      contentKind: "SUMMARY",
      summaryStatus: "PARTIAL",
      summaryVersion: 1
    } satisfies SessionMessage;

    expect(message.summaryStatus).toBe("PARTIAL");
  });

  it("accepts full and summary history representation metadata", () => {
    const history = {
      sessionId: "ses_1",
      sessions: [],
      messagesBySessionId: {},
      childSessionIdByTaskPartId: {},
      events: [],
      historyRepresentation: "SUMMARY",
      replayAvailable: false,
      detailsAvailableUntil: "2026-07-11T00:00:00Z"
    } satisfies SessionTreeMessagesResponse;

    expect(history.replayAvailable).toBe(false);
  });
});
