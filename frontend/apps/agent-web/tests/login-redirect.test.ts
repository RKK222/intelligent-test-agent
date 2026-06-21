import { describe, expect, it } from "vitest";
import { resolveLoginRedirect } from "../src/router";

describe("login redirect", () => {
  it("falls back to the workbench when redirect has no matching route", () => {
    expect(resolveLoginRedirect("/error")).toBe("/");
  });

  it("keeps known internal routes with query strings", () => {
    expect(resolveLoginRedirect("/s/ses_123?mode=readonly")).toBe("/s/ses_123?mode=readonly");
  });

  it("rejects external or login-loop redirects", () => {
    expect(resolveLoginRedirect("https://example.com")).toBe("/");
    expect(resolveLoginRedirect("//example.com/path")).toBe("/");
    expect(resolveLoginRedirect("/login?redirect=/error")).toBe("/");
  });
});
