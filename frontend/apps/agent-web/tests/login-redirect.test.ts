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
    expect(resolveLoginRedirect("/memories?tab=team")).toBe("/memories?tab=team");
  });

  it("rejects disabled release feature routes by default", () => {
    expect(resolveLoginRedirect("/lobehub/launch")).toBe("/");
    expect(resolveLoginRedirect("/workflow-chat")).toBe("/");
    expect(resolveLoginRedirect("/lobehub/launch?returnUrl=https://evil.example#ticket")).toBe("/");
  });

  it("keeps explicitly enabled release feature routes", () => {
    const enabledFeatures = { lobehub: true, workflow: true };
    expect(resolveLoginRedirect("/lobehub/launch", enabledFeatures)).toBe("/lobehub/launch");
    expect(resolveLoginRedirect("/workflow-chat", enabledFeatures)).toBe("/workflow-chat");
    expect(resolveLoginRedirect("/lobehub/launch?returnUrl=https://evil.example#ticket"))
      .toBe("/");
    expect(resolveLoginRedirect(
      "/lobehub/launch?returnUrl=https://evil.example#ticket",
      enabledFeatures
    )).toBe("/lobehub/launch");
  });

  it("rejects external or login-loop redirects", () => {
    expect(resolveLoginRedirect("https://example.com")).toBe("/");
    expect(resolveLoginRedirect("//example.com/path")).toBe("/");
    expect(resolveLoginRedirect("/login?redirect=/error")).toBe("/");
  });
});
