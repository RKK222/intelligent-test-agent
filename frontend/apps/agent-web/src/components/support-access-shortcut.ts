export const SUPPORT_ACCESS_SHIFT_WINDOW_MS = 2_000;

export type SupportAccessShortcut = {
  handleKeydown: (event: Pick<KeyboardEvent, "key" | "repeat"> & Partial<Pick<KeyboardEvent, "code">>) => boolean;
  reset: () => void;
};

/**
 * 只识别短时间内连续三次独立 Shift 按下；其它按键会打断序列，长按产生的 repeat 不计数。
 * 该手势只负责请求展示入口，实际角色校验和排查授权仍由调用方及后端完成。
 */
export function createSupportAccessShortcut(
  now: () => number = () => Date.now(),
  windowMs = SUPPORT_ACCESS_SHIFT_WINDOW_MS
): SupportAccessShortcut {
  let pressCount = 0;
  let firstPressAt = 0;

  function reset() {
    pressCount = 0;
    firstPressAt = 0;
  }

  function handleKeydown(event: Pick<KeyboardEvent, "key" | "repeat"> & Partial<Pick<KeyboardEvent, "code">>) {
    const isShift = event.key === "Shift"
      || event.key === "ShiftLeft"
      || event.key === "ShiftRight"
      || event.code === "ShiftLeft"
      || event.code === "ShiftRight";
    if (!isShift) {
      reset();
      return false;
    }
    if (event.repeat) return false;

    const pressedAt = now();
    if (pressCount === 0 || pressedAt - firstPressAt > windowMs) {
      pressCount = 1;
      firstPressAt = pressedAt;
      return false;
    }

    pressCount += 1;
    if (pressCount < 3) return false;
    reset();
    return true;
  }

  return { handleKeydown, reset };
}
