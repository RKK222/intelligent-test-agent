import { BackendApiError } from '@test-agent/backend-api'
import { describe, expect, it, vi } from 'vitest'
import { restartOwnProcessWithConfirmation } from '../src/components/process-restart'

function runningConflict(runningCount: unknown) {
  return new BackendApiError(409, {
    success: false,
    code: 'CONFLICT',
    message: '存在运行中的任务',
    traceId: 'trace_restart',
    details: { confirmationRequired: true, runningCount },
  })
}

describe('restartOwnProcessWithConfirmation', () => {
  it('retries the same restart mutation with confirmation after the backend reports active runs', async () => {
    const restarted = { status: 'READY' }
    const restart = vi.fn()
      .mockRejectedValueOnce(runningConflict(2))
      .mockResolvedValueOnce(restarted)
    const confirm = vi.fn().mockResolvedValue(true)

    await expect(restartOwnProcessWithConfirmation(restart, confirm)).resolves.toBe(restarted)
    expect(restart.mock.calls).toEqual([[false], [true]])
    expect(confirm).toHaveBeenCalledWith(2)
  })

  it('does not send a confirmed restart when the user cancels', async () => {
    const restart = vi.fn().mockRejectedValueOnce(runningConflict(3))
    const confirm = vi.fn().mockResolvedValue(false)

    await expect(restartOwnProcessWithConfirmation(restart, confirm)).resolves.toBeNull()
    expect(restart).toHaveBeenCalledOnce()
    expect(restart).toHaveBeenCalledWith(false)
  })

  it('fails closed for non-confirmation errors', async () => {
    const failure = new BackendApiError(500, {
      success: false,
      code: 'INTERNAL_ERROR',
      message: '重启失败',
      traceId: 'trace_restart',
    })
    const restart = vi.fn().mockRejectedValueOnce(failure)
    const confirm = vi.fn()

    await expect(restartOwnProcessWithConfirmation(restart, confirm)).rejects.toBe(failure)
    expect(confirm).not.toHaveBeenCalled()
  })
})
