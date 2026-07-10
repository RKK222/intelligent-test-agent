import type { ConversationRunContext } from "@test-agent/shared-types";
import type { StartRunPayload } from "@test-agent/backend-api";

export type ConversationRunContextCache = {
  get: (sessionId: string) => Promise<ConversationRunContext>;
  invalidate: (sessionId: string) => void;
  clear: () => void;
};

/**
 * 会话上下文只保存在当前页面内存中；Promise 级缓存同时避免切换会话与立即发送并发触发两次查询。
 */
export function createConversationRunContextCache(
  loader: (sessionId: string) => Promise<ConversationRunContext>
): ConversationRunContextCache {
  const contexts = new Map<string, Promise<ConversationRunContext>>();

  return {
    get(sessionId) {
      const existing = contexts.get(sessionId);
      if (existing) {
        return existing;
      }
      const pending = loader(sessionId).catch((error) => {
        // 失败结果不能驻留缓存，允许用户下一次显式发送时重新获取。
        if (contexts.get(sessionId) === pending) {
          contexts.delete(sessionId);
        }
        throw error;
      });
      contexts.set(sessionId, pending);
      return pending;
    },
    invalidate(sessionId) {
      contexts.delete(sessionId);
    },
    clear() {
      contexts.clear();
    }
  };
}

type StartRunWithConversationContextOptions<TResult> = {
  cache: ConversationRunContextCache;
  payload: StartRunPayload;
  startRun: (payload: StartRunPayload) => Promise<TResult>;
  clientRequestId?: string;
  clientRequestIdFactory?: () => string;
  assertCurrent?: () => void;
};

/**
 * 上下文失效只允许刷新并重试一次；两次请求始终复用同一个 clientRequestId，避免后端重复创建 Run。
 */
export async function startRunWithConversationContext<TResult>(
  options: StartRunWithConversationContextOptions<TResult>
): Promise<TResult> {
  const sessionId = options.payload.sessionId;
  const clientRequestId = options.clientRequestId
    ?? (options.clientRequestIdFactory ?? createClientRequestId)();
  options.assertCurrent?.();
  const firstContext = await options.cache.get(sessionId);
  options.assertCurrent?.();

  try {
    return await options.startRun(withContext(options.payload, firstContext, clientRequestId));
  } catch (error) {
    if (!isConversationContextRefreshError(error)) {
      throw error;
    }
  }

  options.assertCurrent?.();
  options.cache.invalidate(sessionId);
  const refreshedContext = await options.cache.get(sessionId);
  options.assertCurrent?.();
  try {
    return await options.startRun(withContext(options.payload, refreshedContext, clientRequestId));
  } catch (error) {
    // 重试仍被服务端判定失效时，不能让该 token 污染下一次用户操作。
    if (isConversationContextRefreshError(error)) {
      options.cache.invalidate(sessionId);
    }
    throw error;
  }
}

function withContext(
  payload: StartRunPayload,
  context: ConversationRunContext,
  clientRequestId: string
): StartRunPayload {
  return {
    ...payload,
    contextToken: context.contextToken,
    clientRequestId
  };
}

function isConversationContextRefreshError(error: unknown): boolean {
  if (!error || typeof error !== "object") {
    return false;
  }
  const code = (error as { code?: unknown }).code;
  return code === "CONVERSATION_CONTEXT_REQUIRED" || code === "CONVERSATION_CONTEXT_EXPIRED";
}

export function createClientRequestId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `req_${crypto.randomUUID().replaceAll("-", "")}`;
  }
  return `req_${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
}
