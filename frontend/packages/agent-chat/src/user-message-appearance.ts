export type UserMessageAttribution = {
  senderUserId?: string | null;
  senderUnifiedAuthId?: string | null;
  senderUsername?: string | null;
};

export type UserMessageAppearance = {
  own: boolean;
  displayName?: string;
  style: Record<string, string>;
};

const OWN_MESSAGE_STYLE = {
  backgroundColor: "#EAF3FD",
  border: "none"
};

const OTHER_MESSAGE_STYLE = {
  backgroundColor: "var(--ta-chat-other-user-bg, #EAF3FD)",
  border: "none"
};

/**
 * 本人及共享会话中的其他用户消息统一使用浅蓝色，发送人归因只控制姓名和操作权限。
 * 旧消息没有发送人归因时按自己发送处理，保持历史数据兼容。
 */
export function resolveUserMessageAppearance(
  attribution: UserMessageAttribution,
  currentUserId?: string | null
): UserMessageAppearance {
  const senderUserId = attribution.senderUserId?.trim();
  if (!senderUserId) {
    return { own: true, style: OWN_MESSAGE_STYLE };
  }
  const own = Boolean(currentUserId?.trim()) && senderUserId === currentUserId?.trim();
  return {
    own,
    ...(own ? {} : {
      displayName: attribution.senderUsername?.trim()
        || attribution.senderUnifiedAuthId?.trim()
        || "协作者"
    }),
    style: own ? OWN_MESSAGE_STYLE : OTHER_MESSAGE_STYLE
  };
}
