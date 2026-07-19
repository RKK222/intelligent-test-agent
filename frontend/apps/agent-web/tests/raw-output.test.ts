import { describe, expect, it } from "vitest";
import { appendLatestRawOutputEntry, prepareRawOutputBody } from "../src/components/raw-output";

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

  it("redacts an unterminated quoted token in linear time", () => {
    const malformed = `data: contextToken:"${"\\".repeat(20_000)}ctx_unterminated_secret`;
    const startedAt = performance.now();

    const result = prepareRawOutputBody(malformed, 50_000);

    expect(result.body).not.toContain("ctx_unterminated_secret");
    expect(result.body).toContain('contextToken:"[REDACTED]');
    expect(performance.now() - startedAt).toBeLessThan(1_000);
  });
});
