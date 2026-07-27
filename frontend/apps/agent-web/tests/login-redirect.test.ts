// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import { resolveLoginRedirect } from "../src/router";

describe("login redirect", () => {
  it("falls back to the workbench when redirect has no matching route", () => {
    expect(resolveLoginRedirect("/error")).toBe("/");
  });

  it("keeps known internal routes with query strings", () => {
    expect(resolveLoginRedirect("/s/ses_123?mode=readonly")).toBe("/s/ses_123?mode=readonly");
    expect(resolveLoginRedirect("/toolbox?source=omni-tools")).toBe("/toolbox?source=omni-tools");
    expect(resolveLoginRedirect("/toolbox/?source=it-tools")).toBe("/toolbox/?source=it-tools");
  });

  it("rejects external or login-loop redirects", () => {
    expect(resolveLoginRedirect("https://example.com")).toBe("/");
    expect(resolveLoginRedirect("//example.com/path")).toBe("/");
    expect(resolveLoginRedirect("/login?redirect=/error")).toBe("/");
  });
});
