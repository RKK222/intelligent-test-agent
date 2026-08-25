import type { LoginResponse, UnifiedAuthLoginRequest } from "@test-agent/shared-types";

const SENSITIVE_CALLBACK_PARAMETERS = ["userId", "token", "SSIAuth", "SSISign"] as const;
const DEFAULT_RETRY_PATH = "/workbench";
const RETRY_BASE_URL = "https://test-agent.invalid";

type AamCallbackDependencies = {
  currentUrl: string;
  replaceAddress: (sanitizedPath: string) => void;
  beginExchange: () => void;
  loginByUnifiedAuth: (payload: UnifiedAuthLoginRequest) => Promise<LoginResponse>;
  savePlatformToken: (token: string) => void;
};

export type AamCallbackOutcome =
  | { kind: "none" }
  | { kind: "success"; retryPath: string }
  | { kind: "rejected"; retryPath: string }
  | { kind: "error"; reason: "malformed" | "unavailable"; retryPath: string };

/**
 * 清理 AAM 回调地址并兑换平台会话。地址栏清理和旧会话清理都发生在网络请求之前。
 */
export async function handleAamCallback(
  dependencies: AamCallbackDependencies
): Promise<AamCallbackOutcome> {
  const parsed = inspectCallback(dependencies.currentUrl);
  if (parsed.kind === "none") {
    return parsed;
  }

  dependencies.replaceAddress(parsed.retryPath);
  dependencies.beginExchange();
  if (parsed.kind === "malformed") {
    return { kind: "error", reason: "malformed", retryPath: parsed.retryPath };
  }

  try {
    const login = await dependencies.loginByUnifiedAuth({
      unifiedAuthId: parsed.userId,
      token: parsed.token
    });
    if (!login.token || login.token.trim().length === 0) {
      return { kind: "error", reason: "unavailable", retryPath: parsed.retryPath };
    }
    dependencies.savePlatformToken(login.token);
    return { kind: "success", retryPath: parsed.retryPath };
  } catch (error) {
    if (statusOf(error) === 401) {
      return { kind: "rejected", retryPath: parsed.retryPath };
    }
    return { kind: "error", reason: "unavailable", retryPath: parsed.retryPath };
  }
}

/** 对错误页 query 做同源、去敏和防循环校验。 */
export function normalizeAamRetryPath(rawRetry: unknown): string {
  if (typeof rawRetry !== "string") {
    return DEFAULT_RETRY_PATH;
  }
  const trimmed = rawRetry.trim();
  if (!trimmed.startsWith("/") || trimmed.startsWith("//")) {
    return DEFAULT_RETRY_PATH;
  }
  try {
    const target = new URL(trimmed, RETRY_BASE_URL);
    if (target.origin !== RETRY_BASE_URL
      || target.pathname === "/auth/aam-error"
      || target.pathname === "/985211") {
      return DEFAULT_RETRY_PATH;
    }
    removeSensitiveParameters(target);
    return pathOf(target);
  } catch {
    return DEFAULT_RETRY_PATH;
  }
}

function inspectCallback(rawUrl: string):
  | { kind: "none" }
  | { kind: "malformed"; retryPath: string }
  | { kind: "valid"; retryPath: string; userId: string; token: string } {
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    return { kind: "none" };
  }
  const hasCallbackParameters = SENSITIVE_CALLBACK_PARAMETERS.some((name) => url.searchParams.has(name));
  if (!hasCallbackParameters) {
    return { kind: "none" };
  }

  const userIds = url.searchParams.getAll("userId");
  const tokens = url.searchParams.getAll("token");
  removeSensitiveParameters(url);
  const retryPath = normalizeAamRetryPath(pathOf(url));
  if (userIds.length !== 1
    || tokens.length !== 1
    || userIds[0].trim().length === 0
    || tokens[0].trim().length === 0) {
    return { kind: "malformed", retryPath };
  }
  return {
    kind: "valid",
    retryPath,
    userId: userIds[0].trim(),
    token: tokens[0].trim()
  };
}

function removeSensitiveParameters(url: URL) {
  for (const name of SENSITIVE_CALLBACK_PARAMETERS) {
    url.searchParams.delete(name);
  }
}

function pathOf(url: URL): string {
  return `${url.pathname}${url.search}${url.hash}`;
}

function statusOf(error: unknown): number | undefined {
  if (!error || typeof error !== "object") {
    return undefined;
  }
  const status = (error as { status?: unknown }).status;
  return typeof status === "number" ? status : undefined;
}
