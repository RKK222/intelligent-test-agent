import { BackendApiError } from '@test-agent/backend-api'

export type RestartConfirmation = (runningCount: number | null) => Promise<boolean>

/**
 * 后端是活动 Run 的唯一权威来源：首次固定以未确认请求探测，只有明确的冲突响应才询问用户并重试。
 * 返回 null 表示用户取消，调用方不得更新进程状态或把通知标记为已处理。
 */
export async function restartOwnProcessWithConfirmation<T>(
  restart: (confirmRunning: boolean) => Promise<T>,
  requestConfirmation: RestartConfirmation,
): Promise<T | null> {
  try {
    return await restart(false)
  } catch (error) {
    if (!isRunningConfirmationConflict(error)) throw error
    const confirmed = await requestConfirmation(normalizeRunningCount(error.details.runningCount))
    if (!confirmed) return null
    return restart(true)
  }
}

function isRunningConfirmationConflict(error: unknown): error is BackendApiError {
  return error instanceof BackendApiError
    && error.status === 409
    && error.code === 'CONFLICT'
    && error.details.confirmationRequired === true
}

function normalizeRunningCount(value: unknown): number | null {
  const count = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(count) && count >= 0 ? Math.floor(count) : null
}
