import { describe, expect, it } from "vitest";
import {
  PART_KINDS,
  PART_SPECS,
  assertPartProjection,
  evidencePath,
  findPlatformMessagePart,
  findRawPart,
  findTreeMessagePart,
  sanitizeEvidence,
  selectPartFields
} from "./opencode-parts-real-e2e";

const base = { id: "part_1", partID: "part_1", partId: "part_1", sessionID: "ses_1", messageID: "msg_1" };

const samples = {
  text: { ...base, type: "text", text: "e2e text" },
  subtask: { ...base, type: "subtask", prompt: "inspect", description: "review", agent: "explore", model: { providerID: "p", modelID: "m" }, command: "check" },
  reasoning: { ...base, type: "reasoning", text: "reason", time: { start: 1, end: 2 } },
  file: { ...base, type: "file", mime: "text/plain", filename: "marker.txt", url: "data:text/plain,marker", source: { type: "file", path: "marker.txt", text: { value: "@marker.txt", start: 0, end: 11 } } },
  tool: { ...base, type: "tool", callID: "call_1", tool: "read", state: { status: "completed", input: { filePath: "marker.txt" }, output: "marker", title: "Read", metadata: {}, time: { start: 1, end: 2 } } },
  "step-start": { ...base, type: "step-start", snapshot: "snap-start" },
  "step-finish": { ...base, type: "step-finish", reason: "stop", snapshot: "snap-end", cost: 0.1, tokens: { total: 6, input: 1, output: 2, reasoning: 3, cache: { read: 4, write: 5 } } },
  snapshot: { ...base, type: "snapshot", snapshot: "snapshot-marker" },
  patch: { ...base, type: "patch", hash: "hash-marker", files: ["marker.txt"] },
  agent: { ...base, type: "agent", name: "build", source: { value: "@build", start: 0, end: 6 } },
  retry: { ...base, type: "retry", attempt: 1, error: { name: "APIError", data: { message: "retry-marker", isRetryable: true } }, time: { created: 1 } },
  compaction: { ...base, type: "compaction", auto: true, overflow: false, tail_start_id: "part_tail" }
} as const;

describe("OpenCode 1.17.7 Part 契约", () => {
  it("严格包含官方顺序的 12 种 Part", () => {
    expect(PART_KINDS).toEqual(["text", "subtask", "reasoning", "file", "tool", "step-start", "step-finish", "snapshot", "patch", "agent", "retry", "compaction"]);
    expect(PART_SPECS).toHaveLength(12);
    expect(PART_SPECS.map((item) => item.kind)).toEqual(PART_KINDS);
  });

  it.each(PART_KINDS)("%s 定义完整原生字段和可见 UI 契约", (kind) => {
    const spec = PART_SPECS.find((item) => item.kind === kind)!;
    expect(spec.requiredFields).toContain("id");
    expect(spec.requiredFields).toContain("sessionID");
    expect(spec.requiredFields).toContain("messageID");
    expect(spec.requiredFields).toContain("type");
    expect(spec.ui.current.locators.length).toBeGreaterThan(0);
    expect(spec.ui.history.locators.length).toBeGreaterThan(0);
    expect(spec.ui.current.mustBeVisible).toBe(true);
    expect(spec.ui.history.mustBeVisible).toBe(true);
    expect(spec.ui.current.forbiddenLocator).toBe(".oc-unknown-part");
  });

  it("为适用交互提供确定 locator", () => {
    expect(PART_SPECS.find((item) => item.kind === "text")?.ui.current.interactionLocator).toBe("button[aria-label='复制']");
    for (const spec of PART_SPECS.filter((item) => item.ui.current.interaction !== "none")) {
      expect(spec.ui.current.interactionLocator).toBeTruthy();
      expect(spec.ui.history.interactionLocator).toBe(spec.ui.current.interactionLocator);
    }
  });

  it("step-start 明确要求低噪可见标记", () => {
    const spec = PART_SPECS.find((item) => item.kind === "step-start")!;
    expect(spec.ui.current.locators).toContain(".oc-step-start-marker");
    expect(spec.ui.current.semantic).toContain("低噪");
  });

  it.each(PART_KINDS)("%s 按官方关键字段选择且缺字段失败", (kind) => {
    const selected = selectPartFields(kind, samples[kind]);
    expect(selected.type).toBe(kind);
    expect(() => selectPartFields(kind, { ...samples[kind], id: undefined })).toThrow(/id/);
  });
});

describe("三层 Part 定位与投影", () => {
  it("分别从原生消息、平台消息和真实 tree shape 定位", () => {
    const part = samples.text;
    expect(findRawPart([{ info: { id: "msg_1" }, parts: [part] }], "part_1")).toBe(part);
    expect(findPlatformMessagePart({ items: [{ messageId: "msg_1", parts: [part] }] }, "part_1")).toBe(part);
    expect(findTreeMessagePart({ messagesBySessionId: { ses_1: [{ messageID: "msg_1", part }] } }, "msg_1", "part_1")).toBe(part);
  });

  it.each(PART_KINDS)("%s 对 raw→messages 和 raw→tree 独立比较", (kind) => {
    const raw = samples[kind];
    const platform = { ...raw, partID: raw.id, partId: raw.id, messageId: raw.messageID };
    expect(() => assertPartProjection(kind, raw, { items: [{ parts: [platform] }] }, { messagesBySessionId: { ses_1: [{ messageID: raw.messageID, part: platform }] } })).not.toThrow();
    expect(() => assertPartProjection(kind, raw, { items: [{ parts: [{ ...platform, type: "broken" }] }] }, { messagesBySessionId: { ses_1: [{ messageID: raw.messageID, part: platform }] } })).toThrow(/platform messages/);
    expect(() => assertPartProjection(kind, raw, { items: [{ parts: [platform] }] }, { messagesBySessionId: { ses_1: [{ messageID: raw.messageID, part: { ...platform, type: "broken" } }] } })).toThrow(/platform tree/);
  });
});

describe("证据安全", () => {
  it("递归脱敏对象和数组中的鉴权、Cookie、token、key、secret", () => {
    expect(sanitizeEvidence({ Authorization: "Bearer x", nested: [{ cookie: "c", token: "t", key: "k", secret: "s", safe: "ok" }] })).toEqual({ nested: [{ safe: "ok" }] });
  });

  it("生成逐 run/逐 kind 证据路径并拒绝目录穿越", () => {
    expect(evidencePath("run_1", "text", "raw.json")).toBe(".tmp/e2e/opencode-parts/run_1/text/raw.json");
    expect(() => evidencePath("../run", "text", "raw.json")).toThrow(/unsafe/);
    expect(() => evidencePath("run_1", "text", "../tool/raw.json")).toThrow(/unsafe/);
    expect(() => evidencePath("run_1", "unknown" as never, "raw.json")).toThrow(/kind/);
  });
});
