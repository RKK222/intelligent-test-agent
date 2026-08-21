import { BackendApiError } from '@test-agent/backend-api'
import type { LocalClientUserUpdateRequest, UserNotification } from '@test-agent/shared-types'

export type LocalClientUpdateDirection = 'UPDATE' | 'ROLLBACK'

export type LocalClientNotificationUpdate = {
  clientInstanceId: string;
  payload: LocalClientUserUpdateRequest;
  direction: LocalClientUpdateDirection;
}

type LocalClientNotificationUpdateOptions = {
  notification: UserNotification;
  update: (clientInstanceId: string, payload: LocalClientUserUpdateRequest) => Promise<unknown>;
  refresh: () => Promise<void>;
  reportFailure: (error: unknown) => void;
}

type FailedAttempt = { error: unknown }

/**
 * 通知正文是生成更新请求时目标版本的唯一来源，避免用当前策略替换旧通知而破坏 409 冲突保护。
 */
export function parseLocalClientNotificationUpdate(notification: UserNotification): LocalClientNotificationUpdate | null {
  if (
    notification.type !== 'LOCAL_CLIENT_UPDATE_AVAILABLE'
    || notification.actionType !== 'LOCAL_CLIENT_UPDATE'
    || !notification.actionAvailable
    || !notification.actionTargetId.trim()
  ) return null

  const currentVersion = uniqueVersion(notification.body, '当前')
  const targetVersion = uniqueVersion(notification.body, '目标')
  if (!currentVersion || !targetVersion || currentVersion === targetVersion) return null

  return {
    clientInstanceId: notification.actionTargetId,
    payload: {
      notificationId: notification.notificationId,
      expectedTargetVersion: targetVersion,
    },
    direction: targetVersion > currentVersion ? 'UPDATE' : 'ROLLBACK',
  }
}

/** 仅接受正文中唯一、完整的 14 位版本，防止截断长数字或多目标正文触发动作。 */
function uniqueVersion(body: string, label: '当前' | '目标'): string | null {
  const matches = [...body.matchAll(new RegExp(`${label} (\\d{14})(?!\\d)`, 'g'))]
  return matches.length === 1 ? matches[0]![1]! : null
}

/**
 * 成功和失败都回读权威通知列表；409 失败同样必须使旧通知尽快失效。
 * 仅当更新请求和该次刷新都成功时返回 true；任一失败都会反馈并返回 false，不向点击处理器抛出异常。
 */
export async function requestLocalClientNotificationUpdate({
  notification,
  update,
  refresh,
  reportFailure,
}: LocalClientNotificationUpdateOptions): Promise<boolean> {
  const action = parseLocalClientNotificationUpdate(notification)
  if (!action) return false

  let updateFailure: FailedAttempt | null = null
  try {
    await update(action.clientInstanceId, action.payload)
  } catch (error) {
    updateFailure = { error }
  }

  const refreshFailure = await refreshUserNotifications(refresh)
  if (updateFailure && refreshFailure) {
    reportFailure(combinedUpdateAndRefreshFailure(updateFailure.error, refreshFailure.error))
    return false
  }
  if (updateFailure) {
    reportFailure(updateFailure.error)
    return false
  }
  if (refreshFailure) {
    reportFailure(refreshFailure.error)
    return false
  }
  return true
}

/** 刷新失败同样是可见操作失败，但不能让异步点击处理器留下未处理 rejection。 */
async function refreshUserNotifications(
  refresh: () => Promise<void>,
): Promise<FailedAttempt | null> {
  try {
    await refresh()
    return null
  } catch (error) {
    return { error }
  }
}

/** 双失败只反馈一次，保留 409 的安全 code/traceId，并避免把未知原始错误拼进页面反馈。 */
function combinedUpdateAndRefreshFailure(updateError: unknown, refreshError: unknown): Error {
  const updateConflict = updateError instanceof BackendApiError && updateError.status === 409
  const updateTitle = updateConflict ? '本地客户端更新冲突' : '本地客户端更新失败'
  const updateMessage = updateError instanceof BackendApiError ? updateError.message : '更新请求未完成'
  const refreshMessage = refreshError instanceof BackendApiError ? `：${refreshError.message}` : ''
  const message = `${updateTitle}：${updateMessage}；权威通知刷新失败${refreshMessage}`

  if (updateError instanceof BackendApiError) {
    return new BackendApiError(updateError.status, {
      success: false,
      code: updateError.code,
      message,
      traceId: updateError.traceId,
      details: updateError.details,
      retryable: updateError.retryable,
    })
  }
  return new Error(message)
}
