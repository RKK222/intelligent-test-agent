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
  backgroundColor: "#B2EDDF",
  border: "none"
};

const OTHER_MESSAGE_STYLE = {
  backgroundColor: "var(--ta-chat-other-user-bg, #DED9F6)",
  border: "none"
};

/**
 * 消息颜色始终以当前查看者为基准：自己使用绿色，其他人统一使用紫色，且都不显示边框。
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
