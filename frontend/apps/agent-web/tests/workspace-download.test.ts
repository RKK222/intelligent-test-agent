import { describe, expect, it } from "vitest";
import {
  assertCompleteWorkspaceViewDownload,
  concatWorkspaceDownloadChunks,
  decodeBase64Bytes,
  decodeWorkspaceBinaryChunk,
  finalizeWorkspaceDownloadFiles,
  createWorkspaceFileBlob,
  createZipBlob,
  formatDownloadTimestamp
} from "../src/components/workspace-download";

describe("workspace-download", () => {
  it("formats download timestamps in Beijing time", () => {
    expect(formatDownloadTimestamp(new Date("2026-07-31T16:05:09.000Z"))).toBe("20260801-000509");
  });

  it("creates a UTF-8 ZIP containing every workspace file", async () => {
    const encoder = new TextEncoder();
    const blob = createZipBlob([
      { path: "src/你好.ts", content: encoder.encode("export const answer = 42;") },
      { path: "README.md", content: encoder.encode("hello") }
    ]);
    const bytes = new Uint8Array(await blob.arrayBuffer());
    const text = new TextDecoder().decode(bytes);

    expect(blob.type).toBe("application/zip");
    expect(text).toContain("src/你好.ts");
    expect(text).toContain("export const answer = 42;");
    expect(text).toContain("README.md");
    expect(text).toContain("hello");
    expect(bytes[0]).toBe(0x50);
    expect(bytes[1]).toBe(0x4b);
    expect(bytes[2]).toBe(0x03);
    expect(bytes[3]).toBe(0x04);
    expect(new DataView(bytes.buffer).getUint16(6, true)).toBe(0x0800);
  });

  it("keeps single-file downloads as UTF-8 bytes", async () => {
    const blob = createWorkspaceFileBlob(new TextEncoder().encode("中文内容"));
    expect(blob.type).toBe("application/octet-stream");
    await expect(blob.text()).resolves.toBe("中文内容");
  });

  it("keeps arbitrary binary bytes without UTF-8 decoding", async () => {
    const bytes = decodeBase64Bytes("AP+AQA==");
    const blob = createWorkspaceFileBlob(bytes);

    expect([...new Uint8Array(await blob.arrayBuffer())]).toEqual([0, 255, 128, 64]);
  });

  it("validates and joins binary download chunks by byte offset", () => {
    const first = decodeWorkspaceBinaryChunk({
      contentBase64: "AP8=",
      offset: 0,
      nextOffset: 2,
      size: 4,
      eof: false
    }, 0);
    const second = decodeWorkspaceBinaryChunk({
      contentBase64: "gEA=",
      offset: 2,
      nextOffset: 4,
      size: 4,
      eof: true
    }, 2);

    expect([...concatWorkspaceDownloadChunks([first, second], 4)]).toEqual([0, 255, 128, 64]);
    expect(() => decodeWorkspaceBinaryChunk({
      contentBase64: "AA==",
      offset: 1,
      nextOffset: 2,
      size: 2,
      eof: true
    }, 0)).toThrow("分段响应无效");
  });

  it("stores arbitrary binary bytes unchanged inside ZIP", async () => {
    const content = new Uint8Array([0, 255, 128, 64]);
    const bytes = new Uint8Array(await createZipBlob([{ path: "asset.bin", content }]).arrayBuffer());
    const view = new DataView(bytes.buffer);
    const nameLength = view.getUint16(26, true);
    const extraLength = view.getUint16(28, true);
    const contentOffset = 30 + nameLength + extraLength;

    expect([...bytes.slice(contentOffset, contentOffset + content.length)]).toEqual([...content]);
  });

  it("rejects truncated or warning workspace views before packaging", () => {
    expect(() => assertCompleteWorkspaceViewDownload({ truncated: true, warnings: [] }))
      .toThrow("目录条目超过服务端返回上限");
    expect(() => assertCompleteWorkspaceViewDownload({
      truncated: false,
      warnings: [{ message: "引用副本不可用" }]
    })).toThrow("引用副本不可用");
  });

  it("namespaces every source when the composite view contains collisions", () => {
    const content = new Uint8Array([1]);
    expect(finalizeWorkspaceDownloadFiles([
      { path: "shared/config.json", content, source: "WORKSPACE" },
      { path: "shared/config.json", content, source: "REFERENCE", referenceAlias: "alpha" },
      { path: "shared/config.json", content, source: "REFERENCE", referenceAlias: "zeta" }
    ], true).map((file) => file.path)).toEqual([
      "workspace/shared/config.json",
      "references/alpha/shared/config.json",
      "references/zeta/shared/config.json"
    ]);
  });
});
