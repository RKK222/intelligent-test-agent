import type { Session, SessionShareAccess } from "@test-agent/shared-types";

type ShareAccessReader = { getSessionShareAccess: () => Promise<SessionShareAccess> };
type SessionReader = { getSession: (sessionId: string) => Promise<Session> };

export type SessionShareEntry =
  | { kind: "shared"; access: SessionShareAccess }
  | { kind: "owner"; sessionId: string }
  | { kind: "invalid"; error: unknown };

/** 先按唯一 shareId 解析；失败时仅为兼容旧 /s/{sessionId} 尝试所属人会话。 */
export async function resolveSessionShareEntry(
  pathId: string,
  sharedApi: ShareAccessReader,
  ordinaryApi: SessionReader
): Promise<SessionShareEntry> {
  try {
    const access = await sharedApi.getSessionShareAccess();
    return access.ownerAccess
      ? { kind: "owner", sessionId: access.sessionId }
      : { kind: "shared", access };
  } catch (shareError) {
    try {
      const legacySession = await ordinaryApi.getSession(pathId);
      return { kind: "owner", sessionId: legacySession.sessionId };
    } catch {
      return { kind: "invalid", error: shareError };
    }
  }
}
