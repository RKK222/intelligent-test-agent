// 适配层发送入口：替换 submit.ts 中的 client.session.promptAsync。
// 懒创建平台 workspace+session（首次），POST /api/runs 触发后端 run，注册 runId 开 RunEvent SSE。
// messageID 透传给后端 → 后端转发 opencode → message.updated 回来 id 一致，reducer 就地更新乐观用户消息。

import { request } from "./platform-client"
import { ensurePlatformSession } from "./workspace-session"
import { registerRun } from "./event-bridge"

/** opencode 发送 parts 类型（TextPartInput | FilePartInput | AgentPartInput，带 id） */
type SendPart = Record<string, unknown> & { id?: string; type: string }

export type SendPromptInput = {
  /** 前端本地 sessionID（opencode ses_X） */
  localSessionId: string
  agent: string
  model: { providerID: string; modelID: string }
  messageID?: string
  parts: SendPart[]
  variant?: string
  directory: string
}

type RunResponse = { runId: string; sessionId: string; workspaceId: string; status: string }

/** 把 opencode parts 映射为后端 PromptPartRequest[] */
function toBackendParts(parts: SendPart[]): unknown[] {
  return parts.map((part) => {
    const { id: _id, ...rest } = part
    void _id
    return rest
  })
}

/**
 * 经平台后端发送一条消息。
 * 返回 runId（供调用方追踪）；失败抛 PlatformRequestError。
 */
export async function sendPrompt(input: SendPromptInput): Promise<{ runId: string }> {
  const platformSessionId = await ensurePlatformSession(
    input.localSessionId,
    `Session ${input.localSessionId}`,
  )

  const run = await request<RunResponse>("/runs", {
    method: "POST",
    body: {
      sessionId: platformSessionId,
      messageId: input.messageID,
      agent: input.agent,
      model: `${input.model.providerID}/${input.model.modelID}`,
      variant: input.variant,
      parts: toBackendParts(input.parts),
    },
  })

  // 注册 run，开启 RunEvent SSE → 投影注入 reducer
  registerRun(run.runId, input.localSessionId, input.directory)

  return { runId: run.runId }
}
