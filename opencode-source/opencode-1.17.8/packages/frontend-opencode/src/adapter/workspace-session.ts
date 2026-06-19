// 适配层会话/工作区管理：懒创建平台 workspace+session，映射前端本地 sessionID → 平台 sessionId。
// 前端本地 sessionID 是 opencode 原生 session（ses_X，由 client.session.create 在 :4096 创建）。
// 平台后端 POST /api/runs 要求平台 sessionId（ses_），故首次发送时懒创建并缓存映射。

import { request } from "./platform-client"
import { PLATFORM_WORKSPACE_NAME, PLATFORM_WORKSPACE_ROOT_PATH } from "./config"

type WorkspaceResponse = { workspaceId: string; name: string; rootPath: string }
type SessionResponse = { sessionId: string; workspaceId: string; title: string }

let cachedWorkspaceId: string | undefined
const localToPlatformSession = new Map<string, string>()

/** 首次发送时创建平台工作区（缓存） */
async function ensureWorkspace(): Promise<string> {
  if (cachedWorkspaceId) return cachedWorkspaceId
  const ws = await request<WorkspaceResponse>("/workspaces", {
    method: "POST",
    body: { name: PLATFORM_WORKSPACE_NAME, rootPath: PLATFORM_WORKSPACE_ROOT_PATH },
  })
  cachedWorkspaceId = ws.workspaceId
  return ws.workspaceId
}

/**
 * 确保前端本地 sessionID 有对应的平台 session。
 * 首次为该 localSessionId 创建平台 session，后续复用缓存。
 * 返回平台 sessionId（ses_），供 POST /api/runs 使用。
 */
export async function ensurePlatformSession(localSessionId: string, title: string): Promise<string> {
  const cached = localToPlatformSession.get(localSessionId)
  if (cached) return cached
  const workspaceId = await ensureWorkspace()
  const session = await request<SessionResponse>("/sessions", {
    method: "POST",
    body: { workspaceId, title },
  })
  localToPlatformSession.set(localSessionId, session.sessionId)
  return session.sessionId
}
