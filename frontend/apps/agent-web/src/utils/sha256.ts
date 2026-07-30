import forge from "node-forge";

const FORGE_HASH_CHUNK_BYTES = 64 * 1024;

/** 将摘要字节转换成稳定的小写十六进制字符串。 */
function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}

/**
 * 使用 node-forge 计算 SHA-256，兼容 HTTP 内网环境中缺少 Web Crypto subtle 的企业浏览器。
 * 分块构造 forge 的 binary string，避免对大附件执行一次超长参数展开。
 */
export function sha256BytesWithForge(bytes: Uint8Array): Uint8Array {
  const digest = forge.md.sha256.create();
  for (let offset = 0; offset < bytes.byteLength; offset += FORGE_HASH_CHUNK_BYTES) {
    const end = Math.min(offset + FORGE_HASH_CHUNK_BYTES, bytes.byteLength);
    let binary = "";
    for (let index = offset; index < end; index++) {
      binary += String.fromCharCode(bytes[index]);
    }
    digest.update(binary);
  }
  return Uint8Array.from(digest.digest().getBytes(), (char) => char.charCodeAt(0));
}

/**
 * 计算附件内容 SHA-256：优先使用浏览器原生 Web Crypto，缺失或执行失败时使用纯 JS 回退。
 */
export async function blobSha256Hex(blob: Blob): Promise<string> {
  const buffer = await blob.arrayBuffer();
  const subtle = globalThis.crypto?.subtle;
  if (subtle) {
    try {
      return bytesToHex(new Uint8Array(await subtle.digest("SHA-256", buffer)));
    } catch {
      // 某些企业浏览器会暴露 subtle 但在非安全上下文拒绝 digest，继续走兼容实现。
    }
  }
  return bytesToHex(sha256BytesWithForge(new Uint8Array(buffer)));
}
