// @vitest-environment jsdom

import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  handleAamCallback,
  normalizeAamRetryPath
} from "../src/auth/aamAuth";
import { useAuthStore } from "../src/stores/authStore";
import { getAamUrl } from "../src/utils/aamLogin";

describe("AAM callback", () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it("cleans the address bar and old auth before exchanging credentials", async () => {
    const calls: string[] = [];
    const loginByUnifiedAuth = vi.fn(async () => {
      calls.push("request");
      return {
        token: "platform-token",
        userId: "usr_001",
        username: "张三",
        unifiedAuthId: "AUTH_001"
      };
    });

    const outcome = await handleAamCallback({
      currentUrl: "https://agent.internal/workbench?tab=run&userId=AUTH_001&token=aam-secret&SSIAuth=x&SSISign=y#detail",
      replaceAddress: (path) => calls.push(`replace:${path}`),
      beginExchange: () => calls.push("clear"),
      loginByUnifiedAuth,
      savePlatformToken: (token) => calls.push(`save:${token}`)
    });

    expect(calls).toEqual([
      "replace:/workbench?tab=run#detail",
      "clear",
      "request",
      "save:platform-token"
    ]);
    expect(loginByUnifiedAuth).toHaveBeenCalledWith({
      unifiedAuthId: "AUTH_001",
      token: "aam-secret"
    });
    expect(outcome).toEqual({ kind: "success", retryPath: "/workbench?tab=run#detail" });
  });

  it("maps explicit rejection to relogin and unavailable failures to the safe error page", async () => {
    const base = {
      currentUrl: "https://agent.internal/system?userId=AUTH_001&token=aam-secret",
      replaceAddress: vi.fn(),
      beginExchange: vi.fn(),
      savePlatformToken: vi.fn()
    };

    await expect(handleAamCallback({
      ...base,
      loginByUnifiedAuth: vi.fn().mockRejectedValue({ status: 401 })
    })).resolves.toEqual({ kind: "rejected", retryPath: "/system" });

    await expect(handleAamCallback({
      ...base,
      loginByUnifiedAuth: vi.fn().mockRejectedValue({ status: 503 })
    })).resolves.toEqual({ kind: "error", reason: "unavailable", retryPath: "/system" });
  });

  it("rejects duplicate or incomplete callback parameters after removing every sensitive value", async () => {
    const loginByUnifiedAuth = vi.fn();
    const replaced: string[] = [];

    const duplicate = await handleAamCallback({
      currentUrl: "https://agent.internal/workbench?userId=A&userId=B&token=T&SSIAuth=X",
      replaceAddress: (path) => replaced.push(path),
      beginExchange: vi.fn(),
      loginByUnifiedAuth,
      savePlatformToken: vi.fn()
    });
    const missing = await handleAamCallback({
      currentUrl: "https://agent.internal/toolbox?token=T&SSISign=Y",
      replaceAddress: (path) => replaced.push(path),
      beginExchange: vi.fn(),
      loginByUnifiedAuth,
      savePlatformToken: vi.fn()
    });

    expect(duplicate).toEqual({ kind: "error", reason: "malformed", retryPath: "/workbench" });
    expect(missing).toEqual({ kind: "error", reason: "malformed", retryPath: "/toolbox" });
    expect(replaced).toEqual(["/workbench", "/toolbox"]);
    expect(loginByUnifiedAuth).not.toHaveBeenCalled();
  });

  it("accepts only same-origin sanitized retry paths and uses the onlyLogin endpoint", () => {
    expect(normalizeAamRetryPath("/workbench?tab=run&token=leak")).toBe("/workbench?tab=run");
    expect(normalizeAamRetryPath("https://evil.example/path")).toBe("/workbench");
    expect(normalizeAamRetryPath("//evil.example/path")).toBe("/workbench");
    expect(normalizeAamRetryPath("/auth/aam-error?retry=/system")).toBe("/workbench");
    expect(getAamUrl("https://agent.internal/workbench")).toMatch(
      /^http:\/\/zfw\.sdc\.cs\.icbc\/aam\/onlyLogin\/[A-Za-z0-9_-]+$/
    );
  });

  it("stores only the platform token in sessionStorage and clears it with the tab session", () => {
    const store = useAuthStore();
    sessionStorage.setItem("test-agent.auth.unifiedAuthId", "legacy-value");

    store.beginAamExchange();
    expect(sessionStorage.getItem("test-agent.auth.token")).toBeNull();
    expect(sessionStorage.getItem("test-agent.auth.unifiedAuthId")).toBeNull();
    expect(store.suppressAutoLoginRedirect).toBe(true);

    store.saveToken("platform-token");
    expect(sessionStorage.getItem("test-agent.auth.token")).toBe("platform-token");
    expect(localStorage.getItem("test-agent.auth.token")).toBeNull();
    expect(store.suppressAutoLoginRedirect).toBe(false);

    sessionStorage.clear();
    setActivePinia(createPinia());
    expect(useAuthStore().token).toBeNull();
  });
});
