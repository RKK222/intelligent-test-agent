import { describe, expect, it } from "vitest";
import {
  createWorkspaceFileBlob,
  createZipBlob,
  formatDownloadTimestamp
} from "../src/components/workspace-download";

describe("workspace-download", () => {
  it("formats download timestamps in Beijing time", () => {
    expect(formatDownloadTimestamp(new Date("2026-07-31T16:05:09.000Z"))).toBe("20260801-000509");
  });

  it("creates a UTF-8 ZIP containing every workspace file", async () => {
    const blob = createZipBlob([
      { path: "src/你好.ts", content: "export const answer = 42;" },
      { path: "README.md", content: "hello" }
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
  });

  it("keeps single-file downloads as UTF-8 bytes", async () => {
    const blob = createWorkspaceFileBlob("中文内容");
    expect(blob.type).toBe("application/octet-stream");
    await expect(blob.text()).resolves.toBe("中文内容");
  });
});
