/** 在 HTTPS 优先使用 Clipboard API，HTTP 内网入口回退到同步 selection copy。 */
export async function copyTextWithHttpFallback(value: string): Promise<void> {
  if (window.isSecureContext && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(value);
      return;
    } catch {
      // 企业浏览器策略也可能拒绝 Clipboard API，继续尝试兼容路径。
    }
  }
  const textarea = document.createElement('textarea');
  textarea.value = value;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  const copied = document.execCommand('copy');
  textarea.remove();
  if (!copied) throw new Error('Clipboard write failed');
}

/** 二进制剪贴板没有可靠的 HTTP 降级，只在浏览器明确支持时展示对应操作。 */
export function canWriteBinaryClipboard(): boolean {
  return Boolean(
    window.isSecureContext &&
      typeof ClipboardItem !== 'undefined' &&
      navigator.clipboard?.write
  );
}

export async function copyFileToClipboard(value: File): Promise<void> {
  if (!canWriteBinaryClipboard()) {
    throw new Error('Binary clipboard is unavailable');
  }
  const blob = new Blob([value], { type: value.type });
  await navigator.clipboard.write([new ClipboardItem({ [value.type]: blob })]);
}
