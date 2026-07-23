export type SessionListDrawerRect = {
  top: number;
  left: number;
  width: number;
  height: number;
};

export type SessionListDrawerViewport = {
  width: number;
  height: number;
};

export type SessionListDrawerPlacement = {
  mode: "left" | "overlay";
  top: number;
  left: number;
  width: number;
  height: number;
};

const DRAWER_MAX_WIDTH = 400;
const DRAWER_MIN_LEFT_WIDTH = 280;
const VIEWPORT_MARGIN = 8;

/**
 * 优先把会话列表贴在对话栏左侧；空间不足时退化为视口内覆盖，避免窄屏抽屉落到屏幕外。
 */
export function resolveSessionListDrawerPlacement(
  conversationRect: SessionListDrawerRect,
  viewport: SessionListDrawerViewport
): SessionListDrawerPlacement {
  const top = Math.max(VIEWPORT_MARGIN, conversationRect.top);
  const height = Math.max(
    0,
    Math.min(conversationRect.height, viewport.height - top - VIEWPORT_MARGIN)
  );
  const availableLeftWidth = Math.max(0, conversationRect.left - VIEWPORT_MARGIN);

  if (availableLeftWidth >= DRAWER_MIN_LEFT_WIDTH) {
    const width = Math.min(DRAWER_MAX_WIDTH, availableLeftWidth);
    return {
      mode: "left",
      top,
      left: conversationRect.left - width,
      width,
      height
    };
  }

  return {
    mode: "overlay",
    top,
    left: VIEWPORT_MARGIN,
    width: Math.max(0, Math.min(DRAWER_MAX_WIDTH, viewport.width - VIEWPORT_MARGIN * 2)),
    height
  };
}

/**
 * 把详情抽屉严格放在对话栏外侧；左侧空间不足时只收窄抽屉，不回退覆盖对话。
 */
export function resolveExternalConversationDrawerPlacement(
  conversationRect: SessionListDrawerRect,
  viewport: SessionListDrawerViewport,
  maxWidth: number
): SessionListDrawerPlacement {
  const top = Math.max(VIEWPORT_MARGIN, conversationRect.top);
  const height = Math.max(
    0,
    Math.min(conversationRect.height, viewport.height - top - VIEWPORT_MARGIN)
  );
  const conversationLeft = Math.min(conversationRect.left, viewport.width - VIEWPORT_MARGIN);
  const width = Math.max(
    0,
    Math.min(Math.max(0, maxWidth), conversationLeft - VIEWPORT_MARGIN)
  );

  return {
    mode: "left",
    top,
    left: conversationLeft - width,
    width,
    height
  };
}
