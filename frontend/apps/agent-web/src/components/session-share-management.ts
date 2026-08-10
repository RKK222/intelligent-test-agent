import type { SessionCollaborationShare, SessionShareMember } from '@test-agent/shared-types'

export const SESSION_SHARE_MAX_MEMBERS = 50
export const SESSION_SHARE_MAX_DAYS = 7

/** 有效期始终以本次操作时刻重新计算，避免续期突破后端允许的七天窗口。 */
export function sessionShareExpiryAt(now: Date, days: number): string {
  const safeDays = Math.min(SESSION_SHARE_MAX_DAYS, Math.max(1, Math.trunc(days)))
  return new Date(now.getTime() + safeDays * 24 * 60 * 60 * 1000).toISOString()
}

export function activeSessionShareMembers(members: SessionShareMember[]) {
  return members
    .filter((member) => member.status === 'ACTIVE')
    .map((member) => ({
      userId: member.userId,
      unifiedAuthId: member.unifiedAuthId,
      username: member.username,
      canChat: member.canChat,
    }))
}

export function sessionShareDraftError(members: Array<{ userId: string }>): string | null {
  const unique = new Set(members.map((member) => member.userId.trim()).filter(Boolean))
  if (unique.size !== members.length) return '不能重复添加同一用户'
  if (unique.size > SESSION_SHARE_MAX_MEMBERS) return `每个会话最多分享给 ${SESSION_SHARE_MAX_MEMBERS} 人`
  if (unique.size === 0) return '请至少添加一名被分享人'
  return null
}

export function absoluteSessionShareLink(sharePath: string, origin?: string): string {
  const normalizedPath = sharePath.startsWith('/') ? sharePath : `/${sharePath}`
  const resolvedOrigin = origin ?? (typeof window === 'undefined' ? '' : window.location.origin)
  return `${resolvedOrigin.replace(/\/$/, '')}${normalizedPath}`
}

/** 所属人从普通工作台进入时，也必须遵守协作会话禁止 busy follow-up 的约束。 */
export function sessionCollaborationShareIsActive(
  share: Pick<SessionCollaborationShare, 'status' | 'expiresAt'> | null | undefined,
  now = new Date()
): boolean {
  if (share?.status !== 'ACTIVE') return false
  const expiresAt = Date.parse(share.expiresAt)
  return Number.isFinite(expiresAt) && expiresAt > now.getTime()
}
