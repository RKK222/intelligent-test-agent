export type WorkspaceDownloadFile = {
  path: string;
  content: Uint8Array<ArrayBuffer>;
};

export type WorkspaceDownloadCandidate = WorkspaceDownloadFile & {
  source: "WORKSPACE" | "REFERENCE" | "AUTOMATION_REFERENCE";
  referenceAlias?: string;
};

type WorkspaceViewDownloadCompleteness = {
  truncated: boolean;
  warnings: readonly { message: string }[];
};

type WorkspaceBinaryChunk = {
  contentBase64: string;
  offset: number;
  nextOffset: number;
  size: number;
  eof: boolean;
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

/** 截断或引用告警都表示组合视图不完整，禁止继续生成看似成功的 ZIP。 */
export function assertCompleteWorkspaceViewDownload(result: WorkspaceViewDownloadCompleteness): void {
  if (result.truncated) {
    throw new Error("目录条目超过服务端返回上限，无法生成完整 ZIP");
  }
  const warning = result.warnings.find((item) => item.message.trim());
  if (warning) {
    throw new Error(`工作区引用内容不完整：${warning.message}`);
  }
}

export function decodeBase64Bytes(contentBase64: string): Uint8Array<ArrayBuffer> {
  const binary = atob(contentBase64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

/** 校验服务端字节偏移与 Base64 实际长度，避免畸形分段造成错位拼接。 */
export function decodeWorkspaceBinaryChunk(
  chunk: WorkspaceBinaryChunk,
  expectedOffset: number
): Uint8Array<ArrayBuffer> {
  if (chunk.offset !== expectedOffset
    || chunk.nextOffset < chunk.offset
    || (!chunk.eof && chunk.nextOffset === chunk.offset)
    || chunk.nextOffset > chunk.size) {
    throw new Error("文件下载分段响应无效，请重新下载");
  }
  const bytes = decodeBase64Bytes(chunk.contentBase64);
  if (bytes.length !== chunk.nextOffset - chunk.offset) {
    throw new Error("文件下载分段长度不一致，请重新下载");
  }
  return bytes;
}

export function concatWorkspaceDownloadChunks(
  chunks: readonly Uint8Array<ArrayBuffer>[],
  expectedSize: number
): Uint8Array<ArrayBuffer> {
  const content = new Uint8Array(expectedSize);
  let offset = 0;
  for (const chunk of chunks) {
    content.set(chunk, offset);
    offset += chunk.length;
  }
  if (offset !== expectedSize) {
    throw new Error("文件下载大小不一致，请重新下载");
  }
  return content;
}

function sourceArchivePath(file: WorkspaceDownloadCandidate): string {
  if (file.source === "WORKSPACE") {
    return `workspace/${file.path}`;
  }
  const alias = file.referenceAlias?.trim();
  if (!alias) {
    throw new Error("引用文件缺少来源别名，无法安全生成 ZIP");
  }
  if (file.source === "AUTOMATION_REFERENCE") {
    return `automation/${alias}/${file.path}`;
  }
  return `references/${alias}/${file.path}`;
}

/** 组合视图存在冲突时，全部文件按来源分区，确保工作区和不同引用不会写入同名 ZIP 条目。 */
export function finalizeWorkspaceDownloadFiles(
  candidates: readonly WorkspaceDownloadCandidate[],
  collisionDetected: boolean
): WorkspaceDownloadFile[] {
  const normalizedLogicalPaths = candidates.map((file) => normalizedArchivePath(file.path));
  const duplicateLogicalPath = new Set(normalizedLogicalPaths).size !== normalizedLogicalPaths.length;
  const namespaceBySource = collisionDetected || duplicateLogicalPath;
  const files = candidates.map((file, index) => ({
    path: normalizedArchivePath(namespaceBySource ? sourceArchivePath(file) : normalizedLogicalPaths[index]!),
    content: file.content
  }));
  const uniquePaths = new Set(files.map((file) => file.path));
  if (uniquePaths.size !== files.length) {
    throw new Error("工作区中仍存在无法区分的同名文件，未生成 ZIP");
  }
  return files;
}

// 下载内容来自工作区原始字节分片；ZIP 使用无压缩存储，避免引入额外运行时依赖，
// 同时保留 UTF-8 文件名和任意二进制正文，浏览器及常见解压工具均可直接打开。
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
    const contentBytes = file.content;
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

export function createWorkspaceFileBlob(content: Uint8Array<ArrayBuffer>): Blob {
  return new Blob([content], { type: "application/octet-stream" });
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
