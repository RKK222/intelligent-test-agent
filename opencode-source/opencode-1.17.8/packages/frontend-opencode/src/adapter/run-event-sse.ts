// 适配层 RunEvent SSE 读器：基于 fetch + ReadableStream 订阅 GET /api/runs/{runId}/events。
// 解析 text/event-stream：按空行分帧，提取 event:/id:/data: 字段。
// 支持 ?lastEventId= 断线续传（EventSource 无法设自定义头，用 query param）。

import { PLATFORM_API_URL, PLATFORM_API_TOKEN } from "./config"
import type { RunEventSsePayload } from "./project"

export type SseHandlers = {
  onEvent: (payload: RunEventSsePayload) => void
  onError?: (err: unknown) => void
  /** 返回 false 则停止订阅 */
  shouldContinue?: () => boolean
}

/** 订阅一个 run 的事件流，直到 shouldContinue 返回 false 或 run 终止（调用方 abort signal） */
export async function subscribeRunEvents(runId: string, signal: AbortSignal, handlers: SseHandlers): Promise<void> {
  let lastEventId = ""
  while (!signal.aborted) {
    if (handlers.shouldContinue?.() === false) return
    try {
      const url = `${PLATFORM_API_URL}/runs/${encodeURIComponent(runId)}/events${
        lastEventId ? `?lastEventId=${encodeURIComponent(lastEventId)}` : ""
      }`
      const res = await fetch(url, {
        method: "GET",
        headers: {
          Accept: "text/event-stream",
          ...(PLATFORM_API_TOKEN ? { Authorization: `Bearer ${PLATFORM_API_TOKEN}` } : {}),
          ...(lastEventId ? { "Last-Event-ID": lastEventId } : {}),
        },
        signal,
      })
      if (!res.ok || !res.body) {
        handlers.onError?.(new Error(`SSE HTTP ${res.status}`))
        if (res.status >= 400 && res.status < 500) return // 4xx 不可重试
        await delay(1000, signal)
        continue
      }
      await readStream(res.body, signal, (evt) => {
        if (evt.id) lastEventId = evt.id
        if (!evt.data) return
        try {
          const payload = JSON.parse(evt.data) as RunEventSsePayload
          handlers.onEvent(payload)
        } catch {
          // 非 JSON 帧忽略
        }
      })
    } catch (err) {
      if (signal.aborted) return
      handlers.onError?.(err)
      await delay(1000, signal)
    }
  }
}

type SseFrame = { event?: string; id?: string; data?: string }

async function readStream(
  body: ReadableStream<Uint8Array>,
  signal: AbortSignal,
  onFrame: (frame: SseFrame) => void,
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""
  while (!signal.aborted) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 按空行分帧（\n\n）
    let idx: number
    while ((idx = buffer.indexOf("\n\n")) >= 0) {
      const raw = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      onFrame(parseFrame(raw))
    }
  }
}

function parseFrame(raw: string): SseFrame {
  const frame: SseFrame = {}
  const dataLines: string[] = []
  for (const line of raw.split("\n")) {
    if (line.startsWith("event:")) frame.event = line.slice(6).trim()
    else if (line.startsWith("id:")) frame.id = line.slice(3).trim()
    else if (line.startsWith("data:")) dataLines.push(line.slice(5).replace(/^ /, ""))
  }
  if (dataLines.length) frame.data = dataLines.join("\n")
  return frame
}

function delay(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    const t = setTimeout(resolve, ms)
    signal.addEventListener(
      "abort",
      () => {
        clearTimeout(t)
        resolve()
      },
      { once: true },
    )
  })
}
