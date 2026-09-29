import { describe, expect, it, vi } from "vitest";
import { assistantMessagesAfter, getNativeV2Session, listNativeV2Messages, nativeV2SessionDirectory, patchNativeV2SessionTitle, projectedNativeV2Parts } from "./opencode-v2-native";

describe("OpenCode V2 real E2E native reader", () => {
  it("follows opaque cursors without repeating order and preserves V2 messages", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(Response.json({ data: [{ id: "msg_u", type: "user", text: "hi" }], cursor: { previous: null, next: "c2" } }))
      .mockResolvedValueOnce(Response.json({ data: [{ id: "msg_a", type: "assistant", content: [{ type: "text", text: "ok" }] }], cursor: { previous: "c1", next: null } }));
    const messages = await listNativeV2Messages("http://127.0.0.1:4096/", "ses_1", fetcher);
    expect(messages.map((message) => message.id)).toEqual(["msg_u", "msg_a"]);
    expect(String(fetcher.mock.calls[0]?.[0])).toContain("order=asc");
    expect(String(fetcher.mock.calls[1]?.[0])).toContain("cursor=c2");
    expect(String(fetcher.mock.calls[1]?.[0])).not.toContain("order=");
  });

  it("rejects a repeated cursor rather than looping indefinitely", async () => {
    const fetcher = vi.fn().mockImplementation(async () => Response.json({ data: [], cursor: { previous: null, next: "same" } }));
    await expect(listNativeV2Messages("http://127.0.0.1:4096", "ses_1", fetcher)).rejects.toThrow("repeated");
  });

  it("reads session location and updates the V2 title route", async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(Response.json({ data: { id: "ses_1", location: { directory: "/workspace" } } }))
      .mockResolvedValueOnce(Response.json({ data: { id: "ses_1" } }));
    const session = await getNativeV2Session("http://127.0.0.1:4096", "ses_1", fetcher);
    expect(nativeV2SessionDirectory(session)).toBe("/workspace");
    await patchNativeV2SessionTitle("http://127.0.0.1:4096", "ses_1", "Title", fetcher);
    expect(String(fetcher.mock.calls[1]?.[0])).toBe("http://127.0.0.1:4096/api/session/ses_1");
    expect(fetcher.mock.calls[1]?.[1]).toMatchObject({ method: "PATCH", body: JSON.stringify({ title: "Title" }) });
  });

  it("groups assistants by the V2 user turn boundary", () => {
    const messages = [
      { id: "msg_u1", type: "user" },
      { id: "msg_a1", type: "assistant" },
      { id: "msg_control", type: "compaction" },
      { id: "msg_a2", type: "assistant" },
      { id: "msg_u2", type: "user" },
      { id: "msg_a3", type: "assistant" }
    ];
    expect(assistantMessagesAfter(messages, "msg_u1").map((message) => message.id)).toEqual(["msg_a1", "msg_a2"]);
  });

  it("projects V2 text, reasoning and tool content using the Java adapter IDs", () => {
    const parts = projectedNativeV2Parts([{ id: "msg_a", type: "assistant", content: [
      { type: "text", text: "answer" },
      { type: "reasoning", text: "thought", time: { created: 1 } },
      { type: "tool", id: "call_1", name: "read", state: { status: "completed", input: {}, content: [{ type: "text", text: "ok" }] } }
    ] }], "ses_1");
    expect(parts.map((part) => part.id)).toEqual(["part_msg_a_0", "part_msg_a_1", "call_1"]);
    expect(parts[1]?.time).toMatchObject({ start: 1 });
    expect(parts[2]).toMatchObject({ callID: "call_1", tool: "read", output: "ok", state: { output: "ok" } });
  });
});
