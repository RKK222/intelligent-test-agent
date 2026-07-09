import forge from "node-forge";

/**
 * SSH 私钥客户端混合加密工具：优先使用 Web Crypto API，不可用时使用纯 JS 加密回退。
 *
 * 加密流程：
 * 1. 生成临时 AES-256-GCM 密钥
 * 2. AES-GCM 加密 SSH 私钥（12 字节 nonce，128 位 tag）
 * 3. 用服务端 RSA 公钥（RSA-OAEP/SHA-256）加密临时 AES 密钥
 * 4. 计算规范化后私钥的 SHA-256 指纹（SHA256:<url-safe-base64-no-padding>）
 *
 * 与后端 SshKeyCryptoService 的格式约定：
 * - encryptedPrivateKey / encryptedAesKey / nonce：标准 Base64（带 padding）
 * - fingerprint：URL-safe Base64 不带 padding，前缀 SHA256:
 * - 私钥规范化逻辑与后端 normalizePrivateKey 一致
 */

/** 标准 Base64 编码（带 padding），与后端 Base64.getEncoder() 对齐。 */
function bufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  return bytesToBase64(bytes);
}

/** Uint8Array 转标准 Base64。 */
function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

/** URL-safe Base64 不带 padding，与后端 Base64.getUrlEncoder().withoutPadding() 对齐。 */
function bufferToUrlSafeBase64NoPad(buffer: ArrayBuffer): string {
  return bufferToBase64(buffer)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

type RequiredSshCrypto = Crypto & { subtle: SubtleCrypto };

/** 返回浏览器安全随机数源。没有安全随机数时不能生成 SSH key 加密密钥。 */
function requireRandomValues(): Crypto {
  const webCrypto = globalThis.crypto;
  if (!webCrypto || typeof webCrypto.getRandomValues !== "function") {
    throw new Error("当前浏览器环境不支持安全随机数生成，无法安全保存 SSH key。请升级浏览器后重试。");
  }
  return webCrypto;
}

/** Web Crypto 在 HTTP 内网下可能缺失 subtle，存在时优先使用浏览器原生实现。 */
function optionalWebCrypto(): RequiredSshCrypto | undefined {
  const webCrypto = globalThis.crypto;
  if (!webCrypto || !webCrypto.subtle || typeof webCrypto.getRandomValues !== "function") {
    return undefined;
  }
  return webCrypto as RequiredSshCrypto;
}

/**
 * 规范化 SSH 私钥，逻辑与后端 ConfigurationManagementApplicationService.normalizePrivateKey 一致：
 * CRLF/CR → LF，去除首尾空白，校验格式，末尾补一个换行。
 */
export function normalizePrivateKey(privateKey: string): string {
  const value = privateKey
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .trim();
  if (!value.startsWith("-----BEGIN ") || !value.includes(" PRIVATE KEY-----")) {
    throw new Error("SSH 私钥格式无效");
  }
  return value + "\n";
}

/** 计算 SHA-256 指纹，格式 SHA256:<url-safe-base64-no-padding>，与后端 fingerprint 对齐。 */
async function computeFingerprint(cryptoApi: RequiredSshCrypto, plaintext: string): Promise<string> {
  const encoded = new TextEncoder().encode(plaintext);
  const digest = await cryptoApi.subtle.digest("SHA-256", encoded);
  return `SHA256:${bufferToUrlSafeBase64NoPad(digest)}`;
}

/** 生成临时 AES-256-GCM 密钥（可导出，便于用 RSA 加密其原始字节）。 */
async function generateAesKey(cryptoApi: RequiredSshCrypto): Promise<CryptoKey> {
  return cryptoApi.subtle.generateKey(
    { name: "AES-GCM", length: 256 },
    true,
    ["encrypt"],
  );
}

/** 用 AES-256-GCM 加密 SSH 私钥，返回密文和 nonce（均为标准 Base64）。 */
async function aesEncryptPrivateKey(
  cryptoApi: RequiredSshCrypto,
  plaintext: string,
  aesKey: CryptoKey,
): Promise<{ encryptedPrivateKey: string; nonce: string }> {
  const nonce = cryptoApi.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(plaintext);
  // Web Crypto AES-GCM 输出 = 密文 + 128 位 tag，与 Java GCM doFinal 期望的格式一致
  const encrypted = await cryptoApi.subtle.encrypt(
    { name: "AES-GCM", iv: nonce, tagLength: 128 },
    aesKey,
    encoded,
  );
  return {
    encryptedPrivateKey: bufferToBase64(encrypted),
    nonce: bufferToBase64(nonce.buffer),
  };
}

/** 用服务端 RSA 公钥（RSA-OAEP/SHA-256）加密临时 AES 密钥的原始字节。 */
async function rsaEncryptAesKey(
  cryptoApi: RequiredSshCrypto,
  aesKeyRaw: ArrayBuffer,
  rsaPublicKey: CryptoKey,
): Promise<string> {
  const encrypted = await cryptoApi.subtle.encrypt(
    { name: "RSA-OAEP" },
    rsaPublicKey,
    aesKeyRaw,
  );
  return bufferToBase64(encrypted);
}

/**
 * 完整的混合加密入口：规范化私钥 → 计算指纹 → AES 加密私钥 → RSA 加密 AES 密钥。
 *
 * @param privateKey             原始 SSH 私钥文本
 * @param serverPublicKeyBase64  服务端 RSA 公钥（SPKI Base64）
 * @returns 前端加密后的 payload，可直接提交后端
 */
export async function encryptSshKey(
  privateKey: string,
  serverPublicKeyBase64: string,
): Promise<{
  encryptedPrivateKey: string;
  encryptedAesKey: string;
  encryptionNonce: string;
  fingerprint: string;
}> {
  const normalized = normalizePrivateKey(privateKey);
  const cryptoApi = optionalWebCrypto();
  if (!cryptoApi) {
    return encryptSshKeyWithForge(requireRandomValues(), normalized, serverPublicKeyBase64);
  }
  const fingerprint = await computeFingerprint(cryptoApi, normalized);

  const aesKey = await generateAesKey(cryptoApi);
  const aesKeyRaw = await cryptoApi.subtle.exportKey("raw", aesKey);
  const { encryptedPrivateKey, nonce } = await aesEncryptPrivateKey(cryptoApi, normalized, aesKey);

  const rsaPublicKey = await cryptoApi.subtle.importKey(
    "spki",
    base64ToArrayBuffer(serverPublicKeyBase64),
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"],
  );
  const encryptedAesKey = await rsaEncryptAesKey(cryptoApi, aesKeyRaw, rsaPublicKey);

  return {
    encryptedPrivateKey,
    encryptedAesKey,
    encryptionNonce: nonce,
    fingerprint,
  };
}

/** 标准 Base64 字符串转 ArrayBuffer。 */
function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

/** 用浏览器安全随机数生成指定长度字节。 */
function randomBytes(cryptoApi: Crypto, length: number): Uint8Array {
  return cryptoApi.getRandomValues(new Uint8Array(length));
}

/** forge 使用 binary string 表示原始字节，这里只做字节级转换，不做字符编码转换。 */
function bytesToBinaryString(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return binary;
}

/** 纯 JS SHA-256 指纹，用于 HTTP 内网缺少 Web Crypto subtle 的企业浏览器。 */
function computeFingerprintWithForge(plaintext: string): string {
  const digest = forge.md.sha256.create();
  digest.update(plaintext, "utf8");
  const bytes = Uint8Array.from(digest.digest().getBytes(), (char) => char.charCodeAt(0));
  return `SHA256:${bytesToBase64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")}`;
}

/** 纯 JS AES-256-GCM 加密，输出格式仍为 Java GCM doFinal 兼容的 密文+tag。 */
function aesEncryptPrivateKeyWithForge(
  randomSource: Crypto,
  plaintext: string,
  aesKeyBytes: Uint8Array,
): { encryptedPrivateKey: string; nonce: string } {
  const nonceBytes = randomBytes(randomSource, 12);
  const cipher = forge.cipher.createCipher("AES-GCM", bytesToBinaryString(aesKeyBytes));
  cipher.start({ iv: bytesToBinaryString(nonceBytes), tagLength: 128 });
  cipher.update(forge.util.createBuffer(plaintext, "utf8"));
  const success = cipher.finish();
  if (!success) {
    throw new Error("SSH key 加密失败");
  }
  const encryptedBytes = cipher.output.getBytes() + cipher.mode.tag.getBytes();
  return {
    encryptedPrivateKey: forge.util.encode64(encryptedBytes),
    nonce: bytesToBase64(nonceBytes),
  };
}

/** 纯 JS RSA-OAEP/SHA-256 加密 AES 密钥，MGF1 也显式使用 SHA-256，与后端参数一致。 */
function rsaEncryptAesKeyWithForge(aesKeyBytes: Uint8Array, serverPublicKeyBase64: string): string {
  const publicKeyAsn1 = forge.asn1.fromDer(forge.util.decode64(serverPublicKeyBase64));
  const publicKey = forge.pki.publicKeyFromAsn1(publicKeyAsn1);
  const encrypted = publicKey.encrypt(bytesToBinaryString(aesKeyBytes), "RSA-OAEP", {
    md: forge.md.sha256.create(),
    mgf1: {
      md: forge.md.sha256.create(),
    },
  });
  return forge.util.encode64(encrypted);
}

/**
 * HTTP 内网或浏览器策略禁用 Web Crypto subtle 时的回退实现。
 * 仍然在浏览器端完成 AES-GCM + RSA-OAEP/SHA-256，不向后端提交明文私钥。
 */
function encryptSshKeyWithForge(
  randomSource: Crypto,
  normalized: string,
  serverPublicKeyBase64: string,
): {
  encryptedPrivateKey: string;
  encryptedAesKey: string;
  encryptionNonce: string;
  fingerprint: string;
} {
  const fingerprint = computeFingerprintWithForge(normalized);
  const aesKeyBytes = randomBytes(randomSource, 32);
  const { encryptedPrivateKey, nonce } = aesEncryptPrivateKeyWithForge(randomSource, normalized, aesKeyBytes);
  const encryptedAesKey = rsaEncryptAesKeyWithForge(aesKeyBytes, serverPublicKeyBase64);
  return {
    encryptedPrivateKey,
    encryptedAesKey,
    encryptionNonce: nonce,
    fingerprint,
  };
}
