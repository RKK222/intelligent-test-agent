import { afterEach, describe, expect, it, vi } from 'vitest';
import { canWriteBinaryClipboard, copyFileToClipboard } from './clipboard';

const originalSecureContext = Object.getOwnPropertyDescriptor(
  window,
  'isSecureContext'
);
const originalClipboard = Object.getOwnPropertyDescriptor(
  navigator,
  'clipboard'
);

afterEach(() => {
  vi.unstubAllGlobals();
  if (originalSecureContext) {
    Object.defineProperty(window, 'isSecureContext', originalSecureContext);
  } else {
    delete (window as { isSecureContext?: boolean }).isSecureContext;
  }
  if (originalClipboard) {
    Object.defineProperty(navigator, 'clipboard', originalClipboard);
  } else {
    delete (navigator as { clipboard?: Clipboard }).clipboard;
  }
});

describe('binary clipboard capability', () => {
  it('is disabled for the enterprise HTTP baseline', async () => {
    Object.defineProperty(window, 'isSecureContext', {
      configurable: true,
      value: false
    });
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: undefined
    });
    vi.stubGlobal('ClipboardItem', undefined);

    expect(canWriteBinaryClipboard()).toBe(false);
    await expect(
      copyFileToClipboard(new File(['x'], 'x.png', { type: 'image/png' }))
    ).rejects.toThrow('Binary clipboard is unavailable');
  });

  it('writes a file only when the secure binary Clipboard API is complete', async () => {
    const write = vi.fn().mockResolvedValue(undefined);
    class ClipboardItemStub {
      constructor(readonly items: Record<string, Blob>) {}
    }
    Object.defineProperty(window, 'isSecureContext', {
      configurable: true,
      value: true
    });
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { write }
    });
    vi.stubGlobal('ClipboardItem', ClipboardItemStub);

    const file = new File(['x'], 'x.png', { type: 'image/png' });
    expect(canWriteBinaryClipboard()).toBe(true);
    await copyFileToClipboard(file);
    expect(write).toHaveBeenCalledOnce();
  });
});
