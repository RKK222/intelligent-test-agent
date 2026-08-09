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

/**
 * 由稳定用户标识生成低饱和浅色气泡；深色文字在所有色相上保持可读对比度。
 * 旧消息没有发送人归因时沿用原有主题样式，避免历史展示被随机改色。
 */
export function resolveUserMessageAppearance(
  attribution: UserMessageAttribution,
  currentUserId?: string | null
): UserMessageAppearance {
  const senderUserId = attribution.senderUserId?.trim();
  if (!senderUserId) {
    return { own: true, style: {} };
  }
  const own = Boolean(currentUserId?.trim()) && senderUserId === currentUserId?.trim();
  const hue = stableHue(senderUserId);
  return {
    own,
    ...(own ? {} : {
      displayName: attribution.senderUsername?.trim()
        || attribution.senderUnifiedAuthId?.trim()
        || "协作者"
    }),
    style: {
      backgroundColor: `hsl(${hue} 72% 92%)`,
      borderColor: `hsl(${hue} 48% 68%)`,
      color: "#17223b"
    }
  };
}

function stableHue(value: string): number {
  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return Math.abs(hash) % 360;
}
