export type WorkspaceDownloadFile = {
  path: string;
  content: string;
};

const ZIP_LOCAL_FILE_HEADER_SIZE = 30;
const ZIP_CENTRAL_DIRECTORY_HEADER_SIZE = 46;
const ZIP_END_OF_CENTRAL_DIRECTORY_SIZE = 22;
const ZIP_UTF8_FLAG = 0x0800;

function writeUint16(view: DataView, offset: number, value: number) {
  view.setUint16(offset, value, true);
}

function writeUint32(view: DataView, offset: number, value: number) {
  view.setUint32(offset, value >>> 0, true);
}

function normalizedArchivePath(path: string): string {
  const segments = path
    .replaceAll("\\", "/")
    .split("/")
    .filter((segment) => segment && segment !== "." && segment !== "..");
  return segments.join("/") || "file";
}

// 下载内容来自工作区文本读取接口；ZIP 使用无压缩存储，避免引入额外运行时依赖，
// 同时保留 UTF-8 文件名和正文，浏览器及常见解压工具均可直接打开。
function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

export function createZipBlob(files: readonly WorkspaceDownloadFile[]): Blob {
  const encoder = new TextEncoder();
  const parts: BlobPart[] = [];
  const centralDirectory: ArrayBuffer[] = [];
  let localOffset = 0;

  for (const file of files) {
    const nameBytes = encoder.encode(normalizedArchivePath(file.path));
    const contentBytes = encoder.encode(file.content);
    const checksum = crc32(contentBytes);
    const localHeader = new ArrayBuffer(ZIP_LOCAL_FILE_HEADER_SIZE + nameBytes.length);
    const localView = new DataView(localHeader);
    writeUint32(localView, 0, 0x04034b50);
    writeUint16(localView, 4, 20);
    writeUint16(localView, 6, ZIP_UTF8_FLAG);
    writeUint16(localView, 8, 0);
    writeUint32(localView, 14, checksum);
    writeUint32(localView, 18, contentBytes.length);
    writeUint32(localView, 22, contentBytes.length);
    writeUint16(localView, 26, nameBytes.length);
    new Uint8Array(localHeader, ZIP_LOCAL_FILE_HEADER_SIZE).set(nameBytes);
    parts.push(new Uint8Array(localHeader), contentBytes);

    const centralHeader = new ArrayBuffer(ZIP_CENTRAL_DIRECTORY_HEADER_SIZE + nameBytes.length);
    const centralView = new DataView(centralHeader);
    writeUint32(centralView, 0, 0x02014b50);
    writeUint16(centralView, 4, 20);
    writeUint16(centralView, 6, 20);
    writeUint16(centralView, 8, ZIP_UTF8_FLAG);
    writeUint16(centralView, 10, 0);
    writeUint32(centralView, 16, checksum);
    writeUint32(centralView, 20, contentBytes.length);
    writeUint32(centralView, 24, contentBytes.length);
    writeUint16(centralView, 28, nameBytes.length);
    writeUint32(centralView, 42, localOffset);
    new Uint8Array(centralHeader, ZIP_CENTRAL_DIRECTORY_HEADER_SIZE).set(nameBytes);
    centralDirectory.push(centralHeader);

    localOffset += localHeader.byteLength + contentBytes.byteLength;
  }

  const centralDirectoryOffset = localOffset;
  const centralDirectorySize = centralDirectory.reduce((size, entry) => size + entry.byteLength, 0);
  parts.push(...centralDirectory);

  const endOfDirectory = new ArrayBuffer(ZIP_END_OF_CENTRAL_DIRECTORY_SIZE);
  const endView = new DataView(endOfDirectory);
  writeUint32(endView, 0, 0x06054b50);
  writeUint16(endView, 8, files.length);
  writeUint16(endView, 10, files.length);
  writeUint32(endView, 12, centralDirectorySize);
  writeUint32(endView, 16, centralDirectoryOffset);
  parts.push(new Uint8Array(endOfDirectory));

  return new Blob(parts, { type: "application/zip" });
}

export function createWorkspaceFileBlob(content: string): Blob {
  return new Blob([new TextEncoder().encode(content)], { type: "application/octet-stream" });
}

// 统一按中国时区生成可读时间戳，避免客户端机器时区不同导致同一批下载名称不一致。
export function formatDownloadTimestamp(now: Date = new Date()): string {
  const chinaTime = new Date(now.getTime() + 8 * 60 * 60 * 1000);
  const year = chinaTime.getUTCFullYear();
  const month = String(chinaTime.getUTCMonth() + 1).padStart(2, "0");
  const day = String(chinaTime.getUTCDate()).padStart(2, "0");
  const hours = String(chinaTime.getUTCHours()).padStart(2, "0");
  const minutes = String(chinaTime.getUTCMinutes()).padStart(2, "0");
  const seconds = String(chinaTime.getUTCSeconds()).padStart(2, "0");
  return `${year}${month}${day}-${hours}${minutes}${seconds}`;
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.style.display = "none";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}
