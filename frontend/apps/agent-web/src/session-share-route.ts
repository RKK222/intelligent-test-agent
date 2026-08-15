import type { SessionShareAccess } from "@test-agent/shared-types";

type ShareAccessReader = { getSessionShareAccess: () => Promise<SessionShareAccess> };

export type SessionShareEntry =
  | { kind: "transcript"; sessionId: string }
  | { kind: "shared"; access: SessionShareAccess }
  | { kind: "owner"; sessionId: string }
  | { kind: "invalid"; error: unknown };

/** Session ID 直接进入所属人只读原文；其余 ID 才按唯一 shareId 解析分享工作台。 */
export async function resolveSessionShareEntry(
  pathId: string,
  sharedApi: ShareAccessReader
): Promise<SessionShareEntry> {
  if (pathId.startsWith("ses_")) {
    return { kind: "transcript", sessionId: pathId };
  }
  try {
    const access = await sharedApi.getSessionShareAccess();
    return access.ownerAccess
      ? { kind: "owner", sessionId: access.sessionId }
      : { kind: "shared", access };
  } catch (shareError) {
    return { kind: "invalid", error: shareError };
  }
}
