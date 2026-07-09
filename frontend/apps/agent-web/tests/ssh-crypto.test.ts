import { afterEach, describe, expect, it, vi } from "vitest";
import forge from "node-forge";
import { encryptSshKey } from "../src/utils/ssh-crypto";

describe("ssh-crypto", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("falls back to pure JavaScript encryption when Web Crypto subtle is unavailable", async () => {
    const keyPair = forge.pki.rsa.generateKeyPair({ bits: 2048, e: 0x10001 });
    const publicKeyBase64 = forge.util.encode64(
      forge.asn1.toDer(forge.pki.publicKeyToAsn1(keyPair.publicKey)).getBytes(),
    );
    vi.stubGlobal("crypto", {
      getRandomValues: vi.fn((bytes: Uint8Array) => {
        for (let i = 0; i < bytes.length; i++) {
          bytes[i] = (i * 17 + 3) & 0xff;
        }
        return bytes;
      }),
      subtle: undefined,
    });

    const plaintext = "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----";
    const encrypted = await encryptSshKey(plaintext, publicKeyBase64);
    const aesKeyBytes = keyPair.privateKey.decrypt(forge.util.decode64(encrypted.encryptedAesKey), "RSA-OAEP", {
      md: forge.md.sha256.create(),
      mgf1: {
        md: forge.md.sha256.create(),
      },
    });
    const encryptedPrivateKeyBytes = forge.util.decode64(encrypted.encryptedPrivateKey);
    const tag = encryptedPrivateKeyBytes.slice(encryptedPrivateKeyBytes.length - 16);
    const ciphertext = encryptedPrivateKeyBytes.slice(0, encryptedPrivateKeyBytes.length - 16);
    const decipher = forge.cipher.createDecipher("AES-GCM", aesKeyBytes);
    decipher.start({
      iv: forge.util.decode64(encrypted.encryptionNonce),
      tag: forge.util.createBuffer(tag),
      tagLength: 128,
    });
    decipher.update(forge.util.createBuffer(ciphertext));

    expect(decipher.finish()).toBe(true);
    expect(decipher.output.toString()).toBe(`${plaintext}\n`);
    expect(encrypted.fingerprint).toMatch(/^SHA256:[A-Za-z0-9_-]+$/);
  });

  it("reports a clear compatibility error when secure random is unavailable", async () => {
    vi.stubGlobal("crypto", {
      subtle: undefined,
    });

    await expect(
      encryptSshKey("-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----", "public-key"),
    ).rejects.toThrow("当前浏览器环境不支持安全随机数生成");
  });
});
