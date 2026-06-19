// 适配层事件桥：单例，管理每个活动 run 的 RunEvent SSE 订阅。
// sendPrompt 注册 runId → {localSessionId, directory}，开 SSE，逐事件 project 后调 dispatch 注入 reducer。
// run 终止（succeeded/failed/cancelled）时关闭该 SSE。
// dispatch 由 server-sdk.tsx 的 pushExternalEvent 注入（setDispatch）。

import type { Event } from "@opencode-ai/sdk/v2/client"
import { subscribeRunEvents } from "./run-event-sse"
import { projectRunEvent, type RunEventSsePayload } from "./project"

/** 终止事件类型（run 结束后关闭该 run 的 SSE） */
const TERMINAL_RUN_TYPES = new Set(["run.succeeded", "run.failed", "run.cancelled"])

type DispatchFn = (directory: string, event: Event) => void

type RunRegistration = {
  localSessionId: string
  directory: string
  controller: AbortController
}

const runs = new Map<string, RunRegistration>()
let dispatch: DispatchFn | undefined

/** 注入派发函数（由 server-sdk.tsx 在创建 context 时调用） */
export function setDispatch(fn: DispatchFn): void {
  dispatch = fn
}

/** 注册一个 run 并开始订阅其 RunEvent SSE */
export function registerRun(runId: string, localSessionId: string, directory: string): void {
  // 若已注册同 run，先停旧订阅
  unregisterRun(runId)
  const controller = new AbortController()
  runs.set(runId, { localSessionId, directory, controller })
  void consumeRun(runId, controller)
}

/** 主动停止一个 run 的订阅 */
export function unregisterRun(runId: string): void {
  const reg = runs.get(runId)
  if (!reg) return
  reg.controller.abort()
  runs.delete(runId)
}

async function consumeRun(runId: string, controller: AbortController): Promise<void> {
  const reg = runs.get(runId)
  if (!reg) return
  await subscribeRunEvents(runId, controller.signal, {
    onEvent: (sse: RunEventSsePayload) => {
      const current = runs.get(runId)
      if (!current || !dispatch) return
      const event = projectRunEvent(sse, current.localSessionId)
      if (globalThis.__adapterLog) globalThis.__adapterLog.push({type:sse.type, rawType:(sse.payload||{}).rawType, sessionID:(sse.payload||{}).sessionID, projected:!!event, dir:current.directory})
      if (!event) return
      try {
        if (globalThis.__adapterLog) globalThis.__adapterLog.push({dispatched:event.type})
        dispatch(current.directory, event)
      } catch {
        // 注入异常不应中断流
      }
      if (TERMINAL_RUN_TYPES.has(sse.type)) {
        // run 结束：保留注册以便后续重连已无意义，停止订阅
        unregisterRun(runId)
      }
    },
    onError: (err) => {
      if (!controller.signal.aborted) console.error("[adapter] run event SSE error", { runId, err })
    },
  })
}
