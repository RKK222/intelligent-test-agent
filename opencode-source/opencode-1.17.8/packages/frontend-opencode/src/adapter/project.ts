// 适配层事件投影：把平台 RunEvent 投影为 opencode 原生 Event 形态，喂给现有 reducer（不改 reducer）。
//
// 后端 OpencodeRunEventMapper 把 opencode 的 properties 原样放入 payload，并加 rawType（opencode 原始 type）
// 与 rawEventId。故投影几乎直通：type=rawType、id=rawEventId、properties=payload 去掉 rawType/rawEventId/rawPayload。
//
// 唯一必要变换：sessionID 重写。后端在 :4096 服务端自建 opencode session（ses_remote_Y），
// 与前端本地 sessionID（ses_X）不同；reducer 按 sessionID 存消息，必须重写为 ses_X，回复才落到活动视图。

import type { Event } from "@opencode-ai/sdk/v2/client"

/** 平台 RunEvent SSE payload（RunEventSsePayload） */
export type RunEventSsePayload = {
  eventId: string
  runId: string
  seq: number
  type: string
  traceId?: string
  occurredAt?: string
  payload: Record<string, unknown>
}

const PLATFORM_META_KEYS = new Set(["rawType", "rawEventId", "rawPayload"])

/**
 * 将一个 RunEvent 投影为 opencode Event。
 * @param sse  平台 SSE payload
 * @param localSessionId  前端本地 sessionID（ses_X），用于重写 payload.sessionID
 */
export function projectRunEvent(sse: RunEventSsePayload, localSessionId: string): Event | undefined {
  const payload = sse.payload ?? {}
  const rawType = payload["rawType"] as string | undefined
  // 无 rawType 的是平台合成事件（run.created/started、agent/model-switched 等），
  // 非 opencode 原生形态，跳过以免向 reducer 喂入错误结构。
  if (!rawType) return undefined
  const rawEventId = (payload["rawEventId"] as string | undefined) ?? sse.eventId

  // 重建 properties：去掉平台元字段，保留 opencode 原始 properties 字段
  const properties: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(payload)) {
    if (PLATFORM_META_KEYS.has(k)) continue
    properties[k] = v
  }

  // 核心变换：sessionID 重写为前端本地 sessionID
  if ("sessionID" in properties) {
    properties["sessionID"] = localSessionId
  }

  return {
    id: rawEventId,
    type: rawType as Event["type"],
    properties: properties as any,
  } as Event
}
