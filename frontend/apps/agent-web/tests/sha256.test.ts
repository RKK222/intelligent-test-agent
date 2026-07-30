import { afterEach, describe, expect, it, vi } from "vitest";
import { blobSha256Hex } from "../src/utils/sha256";

const HELLO_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

describe("blobSha256Hex", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("uses Web Crypto when SHA-256 digest is available", async () => {
    const digest = vi.fn(async () => new Uint8Array([
      0x2c, 0xf2, 0x4d, 0xba, 0x5f, 0xb0, 0xa3, 0x0e,
      0x26, 0xe8, 0x3b, 0x2a, 0xc5, 0xb9, 0xe2, 0x9e,
      0x1b, 0x16, 0x1e, 0x5c, 0x1f, 0xa7, 0x42, 0x5e,
      0x73, 0x04, 0x33, 0x62, 0x93, 0x8b, 0x98, 0x24,
    ]).buffer);
    vi.stubGlobal("crypto", { subtle: { digest } });

    await expect(blobSha256Hex(new Blob(["hello"]))).resolves.toBe(HELLO_SHA256);
    expect(digest).toHaveBeenCalledWith("SHA-256", expect.any(ArrayBuffer));
  });

  it("falls back to node-forge when Web Crypto subtle is unavailable", async () => {
    vi.stubGlobal("crypto", { subtle: undefined });

    await expect(blobSha256Hex(new Blob(["hello"]))).resolves.toBe(HELLO_SHA256);
  });

  it("keeps the fallback digest stable across the 64 KiB chunk boundary", async () => {
    const bytes = new Uint8Array(64 * 1024 + 3);
    for (let index = 0; index < bytes.byteLength; index++) {
      bytes[index] = (index * 31 + 7) & 0xff;
    }
    vi.stubGlobal("crypto", { subtle: undefined });

    await expect(blobSha256Hex(new Blob([bytes]))).resolves.toBe(
      "cccb9585bc28e55ce555dc6800920a513bc2a70fa48c2fab9a8a899e72e092d5",
    );
  });

  it("falls back to node-forge when an exposed subtle implementation rejects digest", async () => {
    vi.stubGlobal("crypto", {
      subtle: { digest: vi.fn().mockRejectedValue(new Error("operation unavailable")) },
    });

    await expect(blobSha256Hex(new Blob(["hello"]))).resolves.toBe(HELLO_SHA256);
  });
});
