import { describe, expect, it } from "vitest";
import type { AgentMessage } from "@test-agent/shared-types";
import { convertAgentMessage, extractPromptFromAppendMessage } from "../src/assistant-thread";

describe("convertAgentMessage", () => {
  it("把 user 消息映射为 ThreadMessageLike", () => {
    const message: AgentMessage = {
      id: "u1",
      role: "user",
      text: "跑 checkout",
      createdAt: "2026-06-19T00:00:00.000Z"
    };
    const like = convertAgentMessage(message);
    expect(like.role).toBe("user");
    expect(like.id).toBe("u1");
    expect(like.content).toBe("跑 checkout");
  });

  it("把 assistant 消息映射为 role=assistant 时只汇总最终回答文本", () => {
    const message: AgentMessage = {
      id: "a1",
      role: "assistant",
      text: "fallback",
      parts: [
        { partId: "p1", type: "text", text: "hello" },
        { partId: "p2", type: "reasoning", text: "thinking" }
      ],
      createdAt: "2026-06-19T00:00:00.000Z"
    };
    const like = convertAgentMessage(message);
    expect(like.role).toBe("assistant");
    expect(like.content).toBe("hello");
  });

  it("把 card 消息映射为带 metadata.custom 的 assistant 消息", () => {
    const message: AgentMessage = {
      id: "c1",
      role: "card",
      cardType: "plan",
      title: "执行计划",
      payload: { steps: [] },
      createdAt: "2026-06-19T00:00:00.000Z"
    };
    const like = convertAgentMessage(message);
    expect(like.role).toBe("assistant");
    expect(like.metadata?.custom).toMatchObject({ card: true, cardType: "plan", title: "执行计划" });
  });
});

describe("extractPromptFromAppendMessage", () => {
  it("从 string content 提取 prompt", () => {
    const message = {
      parentId: null,
      sourceId: null,
      runConfig: undefined,
      role: "user" as const,
      content: "跑测试"
    };
    expect(extractPromptFromAppendMessage(message as never).prompt).toBe("跑测试");
  });

  it("从 part 数组提取文本", () => {
    const message = {
      parentId: null,
      sourceId: null,
      runConfig: undefined,
      role: "user" as const,
      content: [
        { type: "text", text: "第一行" },
        { type: "text", text: "第二行" }
      ]
    };
    expect(extractPromptFromAppendMessage(message as never).prompt).toBe("第一行\n第二行");
  });
});
