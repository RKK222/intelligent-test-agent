// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import { resolveLoginRedirect } from "../src/router";

describe("login redirect", () => {
  it("falls back to the workbench when redirect has no matching route", () => {
    expect(resolveLoginRedirect("/error")).toBe("/workbench");
  });

  it("keeps known internal routes with query strings", () => {
    expect(resolveLoginRedirect("/?source=legacy")).toBe("/workbench?source=legacy");
    expect(resolveLoginRedirect("/workbench?source=activity")).toBe("/workbench?source=activity");
    expect(resolveLoginRedirect("/s/ses_123?mode=readonly")).toBe("/s/ses_123?mode=readonly");
    expect(resolveLoginRedirect("/toolbox?source=omni-tools")).toBe("/toolbox?source=omni-tools");
    expect(resolveLoginRedirect("/toolbox/?source=it-tools")).toBe("/toolbox/?source=it-tools");
    expect(resolveLoginRedirect("/memories?tab=team")).toBe("/memories?tab=team");
    expect(resolveLoginRedirect("/system?tab=runtime")).toBe("/system?tab=runtime");
    expect(resolveLoginRedirect("/hub?kind=skill")).toBe("/hub?kind=skill");
    expect(resolveLoginRedirect("/settings?menu=personal")).toBe("/settings?menu=personal");
  });

  it("rejects disabled release feature routes by default", () => {
    expect(resolveLoginRedirect("/lobehub/launch")).toBe("/workbench");
    expect(resolveLoginRedirect("/lobehub/launch?returnUrl=https://evil.example#ticket")).toBe("/workbench");
  });

  it("keeps explicitly enabled release feature routes", () => {
    const enabledFeatures = { lobehub: true };
    expect(resolveLoginRedirect("/lobehub/launch", enabledFeatures)).toBe("/lobehub/launch");
    expect(resolveLoginRedirect("/lobehub/launch?returnUrl=https://evil.example#ticket"))
      .toBe("/workbench");
    expect(resolveLoginRedirect(
      "/lobehub/launch?returnUrl=https://evil.example#ticket",
      enabledFeatures
    )).toBe("/lobehub/launch");
  });

  it("rejects external or login-loop redirects", () => {
    expect(resolveLoginRedirect("https://example.com")).toBe("/workbench");
    expect(resolveLoginRedirect("//example.com/path")).toBe("/workbench");
    expect(resolveLoginRedirect("/login?redirect=/error")).toBe("/workbench");
  });
});
