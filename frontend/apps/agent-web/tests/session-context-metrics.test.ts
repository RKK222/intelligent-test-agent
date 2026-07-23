import { describe, expect, it } from "vitest";
import type { AgentMessage, MessageScope, ModelInfo, ProviderInfo } from "@test-agent/shared-types";
import {
  buildSessionContextSummary,
  calculateSessionContextUsageLevel,
  estimateSessionContextBreakdown
} from "../src/components/session-context-metrics";

const models: ModelInfo[] = [
  { id: "claude-sonnet", providerId: "anthropic", name: "Claude Sonnet", contextLimit: 200_000 },
  { id: "gpt-5", providerId: "openai", name: "GPT-5", contextLimit: 400_000 },
  { id: "mystery", providerId: "local", name: "Mystery" }
];

const providers: ProviderInfo[] = [
  { providerId: "anthropic", name: "Anthropic" },
  { providerId: "openai", name: "OpenAI" },
  { providerId: "local", name: "Local" }
];

function assistant(
  id: string,
  tokens?: Extract<AgentMessage, { role: "assistant" }>["tokens"],
  model?: Extract<AgentMessage, { role: "assistant" }>["model"]
): Extract<AgentMessage, { role: "assistant" }> {
  return {
    id,
    messageId: id,
    role: "assistant",
    text: `assistant ${id}`,
    createdAt: "2026-07-23T08:00:00Z",
    tokens,
    model
  };
}

describe("session context metrics", () => {
  it("classifies unknown, normal, warning and danger usage at the exact boundaries", () => {
    expect(calculateSessionContextUsageLevel(undefined)).toBe("unknown");
    expect(calculateSessionContextUsageLevel(59)).toBe("normal");
    expect(calculateSessionContextUsageLevel(60)).toBe("warning");
    expect(calculateSessionContextUsageLevel(79)).toBe("warning");
    expect(calculateSessionContextUsageLevel(80)).toBe("danger");
    expect(calculateSessionContextUsageLevel(101)).toBe("danger");
  });

  it("uses the latest valid root assistant usage and includes reasoning and cache tokens", () => {
    const messages: AgentMessage[] = [
      { id: "user_1", messageId: "user_1", role: "user", text: "hello", createdAt: "2026-07-23T07:59:00Z" },
      assistant("assistant_old", { input: 10, output: 20 }, { id: "claude-sonnet", providerId: "anthropic" }),
      assistant("assistant_child", { input: 99_000, output: 1_000 }, { id: "gpt-5", providerId: "openai" }),
      assistant("assistant_latest", {
        input: 90_000,
        output: 8_000,
        reasoning: 1_000,
        cacheRead: 500,
        cacheWrite: 500
      }, { id: "claude-sonnet", providerId: "anthropic" }),
      assistant("assistant_empty", { input: 0, output: 0 })
    ];
    const scopes: Record<string, MessageScope> = {
      assistant_child: { sessionId: "ses_child", rootSessionId: "ses_root", isChildSession: true }
    };

    const summary = buildSessionContextSummary({
      messages,
      messageScopesById: scopes,
      rootSessionId: "ses_root",
      selectedProvider: "anthropic",
      selectedModel: "anthropic/claude-sonnet",
      models,
      providers
    });

    expect(summary).toMatchObject({
      providerId: "anthropic",
      providerName: "Anthropic",
      modelId: "claude-sonnet",
      modelName: "Claude Sonnet",
      messageCount: 4,
      contextLimit: 200_000,
      inputTokens: 90_000,
      outputTokens: 8_000,
      totalTokens: 100_000,
      usagePercent: 50,
      ringPercent: 50,
      usageMessageId: "assistant_latest"
    });
  });

  it("recalculates the limit from the selected model and falls back to the assistant model", () => {
    const messages = [assistant("assistant_1", { input: 100_000, output: 2_000 }, { id: "claude-sonnet", providerId: "anthropic" })];

    expect(buildSessionContextSummary({
      messages,
      selectedProvider: "openai",
      selectedModel: "openai/gpt-5",
      models,
      providers
    })).toMatchObject({ contextLimit: 400_000, providerName: "OpenAI", modelName: "GPT-5", usagePercent: 26 });

    expect(buildSessionContextSummary({ messages, models, providers })).toMatchObject({
      contextLimit: 200_000,
      providerName: "Anthropic",
      modelName: "Claude Sonnet"
    });
  });

  it("uses models returned only inside the provider catalog", () => {
    const summary = buildSessionContextSummary({
      messages: [assistant("assistant_1", { input: 50_000, output: 5_000 })],
      selectedProvider: "anthropic",
      selectedModel: "anthropic/claude-provider-only",
      models: [],
      providers: [{
        providerId: "anthropic",
        name: "Anthropic",
        models: [{ id: "claude-provider-only", name: "Claude Provider Only", contextLimit: 100_000 }]
      }]
    });

    expect(summary).toMatchObject({
      providerName: "Anthropic",
      modelName: "Claude Provider Only",
      contextLimit: 100_000,
      usagePercent: 55
    });
  });

  it("keeps an unknown limit empty and clamps only the rendered ring above 100 percent", () => {
    const unknown = buildSessionContextSummary({
      messages: [assistant("assistant_1", { input: 12, output: 2 })],
      selectedProvider: "local",
      selectedModel: "local/mystery",
      models,
      providers
    });
    expect(unknown.contextLimit).toBeUndefined();
    expect(unknown.usagePercent).toBeUndefined();
    expect(unknown.ringPercent).toBe(0);

    const overflowing = buildSessionContextSummary({
      messages: [assistant("assistant_2", { input: 210_000, output: 10_000 })],
      selectedProvider: "anthropic",
      selectedModel: "anthropic/claude-sonnet",
      models,
      providers
    });
    expect(overflowing.usagePercent).toBe(110);
    expect(overflowing.ringPercent).toBe(100);
  });

  it("calibrates the five-category breakdown to the latest input token count", () => {
    const messages: AgentMessage[] = [
      {
        id: "user_1",
        messageId: "user_1",
        role: "user",
        text: "U".repeat(120),
        parts: [{ type: "file", name: "case.md", content: "F".repeat(80) }],
        createdAt: "2026-07-23T07:59:00Z"
      },
      {
        ...assistant("assistant_1", { input: 180, output: 20 }),
        text: "",
        parts: [
          { partId: "text_1", type: "text", text: "A".repeat(80) },
          { partId: "reason_1", type: "reasoning", text: "R".repeat(40) },
          { partId: "tool_1", type: "tool", toolName: "read", status: "completed", input: { path: "a.ts" }, output: "T".repeat(80) }
        ]
      }
    ];

    const breakdown = estimateSessionContextBreakdown(messages, 180);

    expect(breakdown.map((item) => item.key)).toEqual(["system", "user", "assistant", "tool", "other"]);
    expect(breakdown.reduce((sum, item) => sum + item.tokens, 0)).toBe(180);
    expect(breakdown.find((item) => item.key === "user")?.tokens).toBeGreaterThan(0);
    expect(breakdown.find((item) => item.key === "assistant")?.tokens).toBeGreaterThan(0);
    expect(breakdown.find((item) => item.key === "tool")?.tokens).toBeGreaterThan(0);
    expect(breakdown.find((item) => item.key === "other")?.tokens).toBeGreaterThan(0);
  });

  it("returns an empty breakdown when there is no input usage", () => {
    expect(estimateSessionContextBreakdown([], 0)).toEqual([]);
  });
});
