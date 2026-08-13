import { describe, expect, it } from "vitest";
import {
  normalizePhysicalAbsolutePath,
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
});
