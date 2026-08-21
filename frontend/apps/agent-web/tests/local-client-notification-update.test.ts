import { BackendApiError } from '@test-agent/backend-api'
import type { UserNotification } from '@test-agent/shared-types'
import { describe, expect, it, vi } from 'vitest'
import { requestLocalClientNotificationUpdate } from '../src/components/local-client-notification-update'

const notification: UserNotification = {
  notificationId: 'ntf_local_update',
  type: 'LOCAL_CLIENT_UPDATE_AVAILABLE',
  actorUserId: null,
  title: '本地客户端有可用版本',
  body: '开发机 · 当前 20260819183000 → 目标 20260820183000',
  actionType: 'LOCAL_CLIENT_UPDATE',
  actionTargetId: 'local_client_1',
  status: 'ACTIVE',
  invalidationReason: null,
  actionAvailable: true,
  unread: true,
  expiresAt: null,
  readAt: null,
  createdAt: '2026-08-20T09:00:00Z',
  updatedAt: '2026-08-20T09:00:00Z',
}

describe('requestLocalClientNotificationUpdate', () => {
  it('submits the notification target version to its controlled client instance and refreshes on success', async () => {
    const update = vi.fn().mockResolvedValue({ rolloutId: 'rollout_1' })
    const refresh = vi.fn().mockResolvedValue(undefined)
    const reportFailure = vi.fn()

    await expect(requestLocalClientNotificationUpdate({ notification, update, refresh, reportFailure })).resolves.toBe(true)

    expect(update).toHaveBeenCalledWith('local_client_1', {
      notificationId: 'ntf_local_update',
      expectedTargetVersion: '20260820183000',
    })
    expect(refresh).toHaveBeenCalledOnce()
    expect(reportFailure).not.toHaveBeenCalled()
  })

  it('returns false and reports the refresh error when a successful update cannot refresh notifications', async () => {
    const refreshFailure = new Error('通知刷新失败')
    const update = vi.fn().mockResolvedValue({ rolloutId: 'rollout_1' })
    const refresh = vi.fn().mockRejectedValue(refreshFailure)
    const reportFailure = vi.fn()

    await expect(requestLocalClientNotificationUpdate({ notification, update, refresh, reportFailure })).resolves.toBe(false)

    expect(update).toHaveBeenCalledOnce()
    expect(refresh).toHaveBeenCalledOnce()
    expect(reportFailure).toHaveBeenCalledWith(refreshFailure)
  })

  it('reports a 409 update conflict and refreshes the authoritative notification list', async () => {
    const conflict = new BackendApiError(409, {
      success: false,
      code: 'CONFLICT',
      message: '目标版本已变化',
      traceId: 'trace_local_update',
    })
    const update = vi.fn().mockRejectedValue(conflict)
    const refresh = vi.fn().mockResolvedValue(undefined)
    const reportFailure = vi.fn()

    await expect(requestLocalClientNotificationUpdate({ notification, update, refresh, reportFailure })).resolves.toBe(false)

    expect(reportFailure).toHaveBeenCalledWith(conflict)
    expect(refresh).toHaveBeenCalledOnce()
  })

  it('reports one visible failure that retains a 409 conflict when authority refresh also fails', async () => {
    const conflict = new BackendApiError(409, {
      success: false,
      code: 'CONFLICT',
      message: '目标版本已变化',
      traceId: 'trace_local_update',
    })
    const refreshFailure = new Error('通知刷新失败')
    const update = vi.fn().mockRejectedValue(conflict)
    const refresh = vi.fn().mockRejectedValue(refreshFailure)
    const reportFailure = vi.fn()

    await expect(requestLocalClientNotificationUpdate({ notification, update, refresh, reportFailure })).resolves.toBe(false)

    expect(refresh).toHaveBeenCalledOnce()
    expect(reportFailure).toHaveBeenCalledTimes(1)
    const visibleFailure = reportFailure.mock.calls[0]![0]
    expect(visibleFailure).toBeInstanceOf(Error)
    expect((visibleFailure as Error).message).toContain('本地客户端更新冲突')
    expect((visibleFailure as Error).message).toContain('目标版本已变化')
    expect((visibleFailure as Error).message).toContain('权威通知刷新失败')
  })

  it.each([
    ['action is unavailable', { actionAvailable: false }],
    ['client instance id is empty', { actionTargetId: '  ' }],
    ['current and target versions are the same', { body: '开发机 · 当前 20260820183000 → 目标 20260820183000' }],
    ['target version is missing', { body: '开发机 · 当前 20260819183000' }],
    ['target version is not a complete 14-digit value', { body: '开发机 · 当前 20260819183000 → 目标 2026082018300' }],
  ])('fails closed when %s', async (_reason, overrides) => {
    const update = vi.fn().mockResolvedValue({ rolloutId: 'rollout_1' })
    const refresh = vi.fn().mockResolvedValue(undefined)
    const reportFailure = vi.fn()

    await expect(requestLocalClientNotificationUpdate({
      notification: { ...notification, ...overrides },
      update,
      refresh,
      reportFailure,
    })).resolves.toBe(false)

    expect(update).not.toHaveBeenCalled()
    expect(refresh).not.toHaveBeenCalled()
    expect(reportFailure).not.toHaveBeenCalled()
  })
})
