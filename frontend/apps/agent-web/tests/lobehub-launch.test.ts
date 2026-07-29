// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import {
  launchLobehubInCurrentTab,
  launchLobehubInNewTab
} from "../src/components/lobehub-launch";

describe("LobeHub login handoff", () => {
  afterEach(() => {
    document.body.innerHTML = "";
    sessionStorage.clear();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it("opens a blank tab synchronously and sends the ticket only in a hidden POST form", async () => {
    let resolveIssue!: (value: {
      ticket: string;
      expiresAt: string;
      consumeUrl: string;
    }) => void;
    const issue = new Promise<{
      ticket: string;
      expiresAt: string;
      consumeUrl: string;
    }>((resolve) => { resolveIssue = resolve; });
    const popup = { close: vi.fn(), opener: window, name: "" };
    const openWindow = vi.fn((_url: string, _target: string): typeof popup => popup);
    let submitted: Record<string, string> | undefined;
    vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(function submit(this: HTMLFormElement) {
      const ticket = this.querySelector<HTMLInputElement>('input[name="ticket"]');
      submitted = {
        action: this.action,
        method: this.method,
        target: this.target,
        ticket: ticket?.value ?? ""
      };
    });

    const launch = launchLobehubInNewTab(
      { createLobehubSsoTicket: () => issue },
      { openWindow, targetName: () => "lobehub-target", document }
    );

    expect(openWindow).toHaveBeenCalledWith("about:blank", "lobehub-target");
    expect(submitted).toBeUndefined();
    resolveIssue({
      ticket: "one-time-ticket",
      expiresAt: "2026-07-30T08:00:00Z",
      consumeUrl: "http://chat.internal/api/auth/platform/consume"
    });
    await launch;

    expect(submitted).toEqual({
      action: "http://chat.internal/api/auth/platform/consume",
      method: "post",
      target: "lobehub-target",
      ticket: "one-time-ticket"
    });
    expect(String(openWindow.mock.calls[0]?.[0])).not.toContain("one-time-ticket");
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.length).toBe(0);
    expect(popup.opener).toBeNull();
  });

  it("closes the blank tab when the server returns an unsafe consume URL", async () => {
    const popup = { close: vi.fn(), opener: window, name: "" };
    const submit = vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(() => {});

    await expect(launchLobehubInNewTab(
      { createLobehubSsoTicket: async () => ({
        ticket: "one-time-ticket",
        expiresAt: "2026-07-30T08:00:00Z",
        consumeUrl: "javascript:alert(1)"
      }) },
      { openWindow: () => popup, targetName: () => "lobehub-target", document }
    )).rejects.toThrow("LobeHub 登录地址无效");

    expect(popup.close).toHaveBeenCalledOnce();
    expect(submit).not.toHaveBeenCalled();
  });

  it("uses the current tab for the fixed launch route", async () => {
    let submittedTarget = "";
    vi.spyOn(HTMLFormElement.prototype, "submit").mockImplementation(function submit(this: HTMLFormElement) {
      submittedTarget = this.target;
    });

    await launchLobehubInCurrentTab({
      createLobehubSsoTicket: async () => ({
        ticket: "route-ticket",
        expiresAt: "2026-07-30T08:00:00Z",
        consumeUrl: "http://chat.internal/api/auth/platform/consume"
      })
    }, document);

    expect(submittedTarget).toBe("_self");
  });
});
