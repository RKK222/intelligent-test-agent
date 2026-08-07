import { describe, expect, it } from "vitest";
import {
  appendLatestRawOutputEntry,
  prepareRawOutputBody,
  sortRawOutputEntriesNewestFirst,
  upsertLatestRawOutputSession
} from "../src/components/raw-output";

describe("raw output boundary", () => {
  it("keeps only the latest 2,000 raw-output entries without mutating the input", () => {
    const current = Array.from({ length: 2_000 }, (_, index) => index);
    const result = appendLatestRawOutputEntry(current, 2_000);

    expect(result).toHaveLength(2_000);
    expect(result).not.toContain(0);
    expect(result[0]).toBe(1);
    expect(result.at(-1)).toBe(2_000);
    expect(current).toEqual(Array.from({ length: 2_000 }, (_, index) => index));
  });

  it("keeps raw output for only the latest 20 conversations and refreshes recency", () => {
    const current = Object.fromEntries(
      Array.from({ length: 20 }, (_, index) => [`ses_${index + 1}`, [index + 1]])
    );

    const appended = upsertLatestRawOutputSession(current, "ses_21", [21]);

    expect(Object.keys(appended.entriesBySessionId)).toHaveLength(20);
    expect(appended.evictedSessionIds).toEqual(["ses_1"]);
    expect(appended.entriesBySessionId.ses_1).toBeUndefined();
    expect(appended.entriesBySessionId.ses_21).toEqual([21]);
    expect(current.ses_1).toEqual([1]);

    const refreshed = upsertLatestRawOutputSession(appended.entriesBySessionId, "ses_2", [200]);

    expect(refreshed.evictedSessionIds).toEqual([]);
    expect(Object.keys(refreshed.entriesBySessionId).at(-1)).toBe("ses_2");
    expect(refreshed.entriesBySessionId.ses_2).toEqual([200]);
  });

  it("sorts raw-output entries by occurred time descending without mutating the cache", () => {
    const current = [
      { id: "middle", occurredAt: "2026-07-22T08:00:01.000Z" },
      { id: "oldest", occurredAt: "2026-07-22T08:00:00.000Z" },
      { id: "newest", occurredAt: "2026-07-22T08:00:02.000Z" }
    ];

    const result = sortRawOutputEntriesNewestFirst(current);

    expect(result.map((entry) => entry.id)).toEqual(["newest", "middle", "oldest"]);
    expect(current.map((entry) => entry.id)).toEqual(["middle", "oldest", "newest"]);
  });

  it("recursively redacts context tokens from RunEvent SSE data before storing it", () => {
    const result = prepareRawOutputBody(
      JSON.stringify({
        type: "run.started",
        payload: {
          contextToken: "ctx_root_secret",
          children: [{ metadata: { ContextToken: "ctx_nested_secret", keep: "visible" } }]
        }
      }),
      10_000
    );

    expect(JSON.parse(result.body)).toEqual({
      type: "run.started",
      payload: {
        contextToken: "[REDACTED]",
        children: [{ metadata: { ContextToken: "[REDACTED]", keep: "visible" } }]
      }
    });
    expect(result.body).not.toContain("ctx_root_secret");
    expect(result.body).not.toContain("ctx_nested_secret");
    expect(result.truncated).toBeUndefined();
  });

  it("redacts context tokens when an SSE or malformed body is not valid JSON", () => {
    const sse = prepareRawOutputBody(
      'data: {"contextToken":"ctx_sse_secret","keep":"visible"}\n\n',
      10_000
    );
    const form = prepareRawOutputBody("kind=debug&ContextToken=ctx_form_secret&keep=visible", 10_000);

    expect(sse.body).toContain('"contextToken":"[REDACTED]"');
    expect(sse.body).toContain('"keep":"visible"');
    expect(sse.body).not.toContain("ctx_sse_secret");
    expect(form.body).toContain("ContextToken=[REDACTED]");
    expect(form.body).toContain("keep=visible");
    expect(form.body).not.toContain("ctx_form_secret");
  });

  it("redacts XXL tickets, cookies, tokens, passwords, secrets and session digests", () => {
    const json = prepareRawOutputBody(JSON.stringify({
      ticket: "xxl-ticket-secret",
      Cookie: "test_agent_xxl_login=cookie-secret",
      access_token: "access-secret",
      password: "mysql-secret",
      nested: { secret: "nested-secret", sessionDigest: "digest-secret", keep: "visible" }
    }), 10_000);
    const text = prepareRawOutputBody(
      "ticket=xxl-text-secret&set-cookie=cookie-text-secret&token=token-text-secret&keep=visible",
      10_000
    );

    expect(json.body).not.toMatch(/xxl-ticket-secret|cookie-secret|access-secret|mysql-secret|nested-secret|digest-secret/);
    expect(json.body).toContain('"keep":"visible"');
    expect(text.body).not.toMatch(/xxl-text-secret|cookie-text-secret|token-text-secret/);
    expect(text.body).toContain("keep=visible");
  });

  it("redacts internal model authToken fields in raw output", () => {
    const json = prepareRawOutputBody(
      JSON.stringify({ authToken: "legacy-provider-secret", tokenValue: "provider-secret", keep: "visible" }),
      10_000
    );
    const text = prepareRawOutputBody(
      "auth_token=legacy-text-secret&token-value=provider-text-secret&keep=visible",
      10_000
    );

    expect(json.body).not.toMatch(/legacy-provider-secret|provider-secret/);
    expect(text.body).not.toMatch(/legacy-text-secret|provider-text-secret/);
    expect(json.body).toContain('"keep":"visible"');
    expect(text.body).toContain("keep=visible");
  });

  it("redacts an unterminated quoted token in linear time", () => {
    const malformed = `data: contextToken:"${"\\".repeat(20_000)}ctx_unterminated_secret`;
    const startedAt = performance.now();

    const result = prepareRawOutputBody(malformed, 50_000);

    expect(result.body).not.toContain("ctx_unterminated_secret");
    expect(result.body).toContain('contextToken:"[REDACTED]');
    expect(performance.now() - startedAt).toBeLessThan(1_000);
  });
});
