import { describe, expect, it } from 'vitest'
import {
  SESSION_SHARE_MAX_MEMBERS,
  absoluteSessionShareLink,
  sessionCollaborationShareIsActive,
  sessionShareDraftError,
  sessionShareExpiryAt,
} from '../src/components/session-share-management'

describe('session share management', () => {
  it('caps a requested expiry at seven days from the operation time', () => {
    const now = new Date('2026-08-09T08:00:00.000Z')
    expect(sessionShareExpiryAt(now, 30)).toBe('2026-08-16T08:00:00.000Z')
  })

  it('rejects duplicate and over-limit memberships', () => {
    expect(sessionShareDraftError([{ userId: 'u1' }, { userId: 'u1' }])).toContain('重复')
    expect(sessionShareDraftError(Array.from({ length: SESSION_SHARE_MAX_MEMBERS + 1 }, (_, index) => ({ userId: `u${index}` })))).toContain('50')
  })

  it('builds the stable public route from the platform share path', () => {
    expect(absoluteSessionShareLink('/s/share-id', 'https://agent.example/')).toBe('https://agent.example/s/share-id')
  })

  it('only treats a non-expired active share as collaborative send mode', () => {
    const active = { status: 'ACTIVE', expiresAt: '2026-08-10T08:00:00.000Z' }
    const now = new Date('2026-08-09T08:00:00.000Z')

    expect(sessionCollaborationShareIsActive(active, now)).toBe(true)
    expect(sessionCollaborationShareIsActive({ ...active, status: 'REVOKED' }, now)).toBe(false)
    expect(sessionCollaborationShareIsActive(active, new Date(active.expiresAt))).toBe(false)
    expect(sessionCollaborationShareIsActive(null, now)).toBe(false)
  })
})
