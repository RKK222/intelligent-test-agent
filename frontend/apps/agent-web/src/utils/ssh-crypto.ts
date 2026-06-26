/**
 * SSH 私钥客户端混合加密工具：使用 Web Crypto API 实现 RSA + AES 混合加密。
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
async function computeFingerprint(plaintext: string): Promise<string> {
  const encoded = new TextEncoder().encode(plaintext);
  const digest = await crypto.subtle.digest("SHA-256", encoded);
  return `SHA256:${bufferToUrlSafeBase64NoPad(digest)}`;
}

/** 生成临时 AES-256-GCM 密钥（可导出，便于用 RSA 加密其原始字节）。 */
async function generateAesKey(): Promise<CryptoKey> {
  return crypto.subtle.generateKey(
    { name: "AES-GCM", length: 256 },
    true,
    ["encrypt"],
  );
}

/** 用 AES-256-GCM 加密 SSH 私钥，返回密文和 nonce（均为标准 Base64）。 */
async function aesEncryptPrivateKey(
  plaintext: string,
  aesKey: CryptoKey,
): Promise<{ encryptedPrivateKey: string; nonce: string }> {
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const encoded = new TextEncoder().encode(plaintext);
  // Web Crypto AES-GCM 输出 = 密文 + 128 位 tag，与 Java GCM doFinal 期望的格式一致
  const encrypted = await crypto.subtle.encrypt(
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
  aesKeyRaw: ArrayBuffer,
  rsaPublicKey: CryptoKey,
): Promise<string> {
  const encrypted = await crypto.subtle.encrypt(
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
  const fingerprint = await computeFingerprint(normalized);

  const aesKey = await generateAesKey();
  const aesKeyRaw = await crypto.subtle.exportKey("raw", aesKey);
  const { encryptedPrivateKey, nonce } = await aesEncryptPrivateKey(normalized, aesKey);

  const rsaPublicKey = await crypto.subtle.importKey(
    "spki",
    base64ToArrayBuffer(serverPublicKeyBase64),
    { name: "RSA-OAEP", hash: "SHA-256" },
    false,
    ["encrypt"],
  );
  const encryptedAesKey = await rsaEncryptAesKey(aesKeyRaw, rsaPublicKey);

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
