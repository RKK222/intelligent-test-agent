export const SUPPORT_ACCESS_SHIFT_WINDOW_MS = 2_000;

export type TripleKeyShortcut = {
  handleKeydown: (event: Pick<KeyboardEvent, "key" | "repeat"> & Partial<Pick<KeyboardEvent, "code">>) => boolean;
  reset: () => void;
};

export type SupportAccessShortcut = TripleKeyShortcut;

/**
 * 只识别短时间内连续三次独立修饰键按下；其它按键会打断序列，长按产生的 repeat 不计数。
 * 该手势只负责请求展示入口，实际角色校验仍由调用方及后端完成。
 */
export function createTripleKeyShortcut(
  triggerKey: "Shift" | "Control",
  now: () => number = () => Date.now(),
  windowMs = SUPPORT_ACCESS_SHIFT_WINDOW_MS
): TripleKeyShortcut {
  let pressCount = 0;
  let firstPressAt = 0;

  function reset() {
    pressCount = 0;
    firstPressAt = 0;
  }

  function handleKeydown(event: Pick<KeyboardEvent, "key" | "repeat"> & Partial<Pick<KeyboardEvent, "code">>) {
    const keyVariants = [triggerKey, `${triggerKey}Left`, `${triggerKey}Right`];
    const isTriggerKey = keyVariants.includes(event.key) || keyVariants.includes(event.code ?? "");
    if (!isTriggerKey) {
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

/** 排查入口继续使用既有的三次 Shift 手势，保持其调用方接口兼容。 */
export function createSupportAccessShortcut(
  now: () => number = () => Date.now(),
  windowMs = SUPPORT_ACCESS_SHIFT_WINDOW_MS
): SupportAccessShortcut {
  return createTripleKeyShortcut("Shift", now, windowMs);
}
