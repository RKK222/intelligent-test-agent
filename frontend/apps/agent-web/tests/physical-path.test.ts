import { describe, expect, it } from "vitest";
import {
  normalizePhysicalAbsolutePath,
  normalizeWorkspaceRelativeEntries,
  normalizeWorkspaceRelativePath,
  workspacePhysicalRootPath
} from "../src/components/physical-path";

describe("physical workspace paths", () => {
  it("uses only the explicit physical API field and never falls back to rootPath", () => {
    expect(workspacePhysicalRootPath({
      rootPath: "/legacy/data/workspace",
      physicalRootPath: "/data/workspace"
    })).toBe("/data/workspace");
    expect(workspacePhysicalRootPath({ rootPath: "C:\\data\\workspace" })).toBeUndefined();
  });

  it("rejects logical and relative workspace roots", () => {
    expect(normalizePhysicalAbsolutePath("personalworktree:20260806/usr/demo")).toBeUndefined();
    expect(workspacePhysicalRootPath({ rootPath: "workspaces/demo" })).toBeUndefined();
  });

  it("fails closed for traversal", () => {
    expect(normalizePhysicalAbsolutePath("/data/workspace/../outside.txt")).toBeUndefined();
  });

  it("keeps relative tool paths but rejects unresolvable host paths", () => {
    expect(normalizeWorkspaceRelativePath("a/src/main.ts")).toBe("src/main.ts");
    expect(normalizeWorkspaceRelativePath("./src\\main.ts")).toBe("src/main.ts");
    expect(normalizeWorkspaceRelativePath("/data/workspace/src/main.ts")).toBeUndefined();
    expect(normalizeWorkspaceRelativePath("C:\\data\\workspace\\src\\main.ts")).toBeUndefined();
    expect(normalizeWorkspaceRelativePath("workspace:wrk_1/src/main.ts")).toBeUndefined();
    expect(normalizeWorkspaceRelativePath("../outside.ts")).toBeUndefined();
  });

  it("strips an absolute tool path only when an explicit trusted root is available", () => {
    expect(normalizeWorkspaceRelativePath(
      "/data/workspace/src/main.ts",
      "/data/workspace"
    )).toBe("src/main.ts");
    expect(normalizeWorkspaceRelativePath(
      "/data/other/src/main.ts",
      "/data/workspace"
    )).toBeUndefined();
  });

  it("drops invalid diff entries instead of falling back to host paths", () => {
    expect(normalizeWorkspaceRelativeEntries([
      { path: "src/valid.ts", additions: 1 },
      { path: "/host/private/secret.ts", additions: 2 },
      { path: "../outside.ts", additions: 3 }
    ])).toEqual([{ path: "src/valid.ts", additions: 1 }]);
  });
});
