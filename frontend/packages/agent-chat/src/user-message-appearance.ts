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
  backgroundColor: "var(--ta-chat-user-bg, #E6F4FF)",
  border: "1px solid #BAE0FF"
};

const OTHER_MESSAGE_STYLE = {
  backgroundColor: "var(--ta-chat-other-user-bg, #F3E8FF)",
  border: "1px solid #DDD6FE"
};

/**
 * 消息颜色始终以当前查看者为基准：本人使用浅蓝色，共享会话中的其他用户使用低饱和浅紫色。
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
