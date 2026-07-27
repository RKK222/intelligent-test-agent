// eslint-disable-next-line no-restricted-imports
import { useClipboard } from '@vueuse/core';
import { useMessage } from 'naive-ui';
import { toValue, type MaybeRefOrGetter } from 'vue';

/** HTTP 内网入口没有安全上下文时，使用临时 textarea 保留复制能力。 */
export function copyTextWithHttpFallback(content: string): boolean {
  const textarea = document.createElement('textarea');
  textarea.value = content;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  const copied = document.execCommand('copy');
  textarea.remove();
  return copied;
}

export function useCopy({ source, text = 'Copied to the clipboard', createToast = true }: { source?: MaybeRefOrGetter<string>; text?: string; createToast?: boolean } = {}) {
  const { copy, copied, ...rest } = useClipboard({
    source,
    legacy: true,
  });

  const message = useMessage();

  return {
    ...rest,
    isJustCopied: copied,
    async copy(content?: string, { notificationMessage }: { notificationMessage?: string } = {}) {
      const value = source ? toValue(source) : (content ?? '');
      if (window.isSecureContext && navigator.clipboard) {
        try {
          await copy(value);
        }
        catch {
          if (!copyTextWithHttpFallback(value)) throw new Error('Clipboard write failed');
        }
      }
      else {
        if (!copyTextWithHttpFallback(value)) throw new Error('Clipboard write failed');
      }

      if (createToast) {
        message.success(notificationMessage ?? text);
      }
    },
  };
}
