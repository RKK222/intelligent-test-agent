/**
 * 复制文本到系统剪贴板。
 *
 * HTTPS/localhost 优先使用异步 Clipboard API；企业内 HTTP 入口或浏览器拒绝权限时，
 * 回退到 Chromium 108 仍支持的受控 textarea + execCommand 路径。
 */
export async function copyTextToClipboard(text: string): Promise<boolean> {
  if (typeof window !== "undefined"
    && typeof navigator !== "undefined"
    && window.isSecureContext
    && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      // 企业浏览器可能保留 API 但拒绝权限，继续尝试兼容复制路径。
    }
  }

  if (typeof document === "undefined" || !document.body) return false;

  const activeElement = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  const textArea = document.createElement("textarea");
  textArea.value = text;
  textArea.setAttribute("readonly", "");
  textArea.setAttribute("aria-hidden", "true");
  textArea.style.position = "fixed";
  textArea.style.left = "-9999px";
  textArea.style.top = "0";
  textArea.style.opacity = "0";
  document.body.appendChild(textArea);

  try {
    textArea.focus();
    textArea.select();
    textArea.setSelectionRange(0, textArea.value.length);
    return document.execCommand("copy");
  } catch {
    return false;
  } finally {
    textArea.remove();
    activeElement?.focus();
  }
}
