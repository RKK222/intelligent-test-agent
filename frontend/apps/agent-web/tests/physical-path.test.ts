import { describe, expect, it } from "vitest";
import {
  normalizePhysicalAbsolutePath,
  resolvePhysicalFilePath,
  workspacePhysicalRootPath
} from "../src/components/physical-path";

describe("physical workspace paths", () => {
  it("prefers the explicit physical API field and only falls back to an absolute legacy root", () => {
    expect(workspacePhysicalRootPath({
      rootPath: "/legacy/data/workspace",
      physicalRootPath: "/data/workspace"
    })).toBe("/data/workspace");
    expect(workspacePhysicalRootPath({ rootPath: "C:\\data\\workspace" }))
      .toBe("C:/data/workspace");
  });

  it("rejects logical and relative workspace roots", () => {
    expect(normalizePhysicalAbsolutePath("personalworktree:20260806/usr/demo")).toBeUndefined();
    expect(workspacePhysicalRootPath({ rootPath: "workspaces/demo" })).toBeUndefined();
  });

  it("joins an ordinary relative file only beneath a physical root", () => {
    expect(resolvePhysicalFilePath({
      workspaceRootPath: "/data/workspace",
      filePath: "src/main.ts"
    })).toBe("/data/workspace/src/main.ts");
  });

  it("fails closed for relative explicit paths, internal routes and traversal", () => {
    expect(resolvePhysicalFilePath({
      explicitPath: "agents/review.md",
      workspaceRootPath: "/data/workspace",
      filePath: "src/main.ts"
    })).toBeUndefined();
    expect(resolvePhysicalFilePath({
      workspaceRootPath: "/data/workspace",
      filePath: "agent-workspace:wrk_1:::agents%2Freview.md"
    })).toBeUndefined();
    expect(resolvePhysicalFilePath({
      workspaceRootPath: "/data/workspace",
      filePath: "../outside.txt"
    })).toBeUndefined();
    expect(resolvePhysicalFilePath({
      workspaceRootPath: "/data/workspace",
      filePath: "src/.."
    })).toBeUndefined();
    expect(normalizePhysicalAbsolutePath("/data/workspace/../outside.txt")).toBeUndefined();
  });
});
