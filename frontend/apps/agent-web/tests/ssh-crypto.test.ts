import { afterEach, describe, expect, it, vi } from "vitest";
import { encryptSshKey } from "../src/utils/ssh-crypto";

describe("ssh-crypto", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("reports a clear compatibility error when Web Crypto subtle is unavailable", async () => {
    vi.stubGlobal("crypto", {
      getRandomValues: vi.fn(),
      subtle: undefined,
    });

    await expect(
      encryptSshKey("-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----", "public-key"),
    ).rejects.toThrow("当前浏览器环境不支持 Web Crypto 加密");
  });
});
