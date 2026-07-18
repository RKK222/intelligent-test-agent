import { describe, expect, it } from "vitest";
import {
  isReferenceFilePath,
  referenceFileInfo,
  referenceTabPath
} from "../src/components/referenceFileLoad";
import * as referenceFileLoad from "../src/components/referenceFileLoad";

describe("reference file tab identity", () => {
  it("round trips workspace, alias, reference path and logical path independently", () => {
    const tabPath = referenceTabPath({
      workspaceId: "wrk:personal/1",
      referenceAlias: "requirements:zh",
      referencePath: "资产/guide:api.md",
      logicalPath: "docs/guide:api.md"
    });

    expect(isReferenceFilePath(tabPath)).toBe(true);
    expect(referenceFileInfo(tabPath)).toEqual({
      workspaceId: "wrk:personal/1",
      referenceAlias: "requirements:zh",
      referencePath: "资产/guide:api.md",
      logicalPath: "docs/guide:api.md"
    });
    expect(referenceTabPath({
      workspaceId: "wrk:personal/1",
      referenceAlias: "legacy",
      referencePath: "资产/guide:api.md",
      logicalPath: "docs/guide:api.md"
    })).not.toBe(tabPath);
  });

  it("keeps a retried empty error tab in error state when the reference read fails again", () => {
    const failurePatch = (referenceFileLoad as typeof referenceFileLoad & {
      referenceReadFailurePatch?: (hasLoadedSnapshot: boolean, message: string) => unknown;
    }).referenceReadFailurePatch;

    expect(failurePatch?.(false, "引用服务暂不可用")).toEqual({
      readonly: true,
      loadState: "error",
      loadError: "引用服务暂不可用",
      hasLoadedSnapshot: false
    });
    expect(failurePatch?.(true, "引用服务暂不可用")).toEqual({
      readonly: true,
      loadState: "loaded",
      loadError: "引用服务暂不可用",
      hasLoadedSnapshot: true
    });
  });
});
