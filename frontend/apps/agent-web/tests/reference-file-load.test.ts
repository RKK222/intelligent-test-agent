import { describe, expect, it } from "vitest";
import {
  referenceFileInfo,
  referenceLocatorFromTab,
  referenceTabPath
} from "../src/components/referenceFileLoad";

describe("reference file tab identity", () => {
  it("keeps legacy document reference tab identities compatible", () => {
    const path = referenceTabPath({
      workspaceId: "wrk_1",
      referenceAlias: "docs",
      referencePath: "design/api.md",
      logicalPath: "docs/design/api.md"
    });

    expect(referenceFileInfo(path)).toEqual({
      workspaceId: "wrk_1",
      referenceAlias: "docs",
      referencePath: "design/api.md",
      logicalPath: "docs/design/api.md"
    });
  });

  it("pins automation tabs to the application repository generation used when the file was opened", () => {
    const info = referenceFileInfo(referenceTabPath({
      workspaceId: "wrk_1",
      referenceAlias: "接口自动化",
      referencePath: "cases/login.robot",
      logicalPath: "自动化代码库/接口自动化/cases/login.robot",
      kind: "AUTOMATION_REFERENCE",
      automationAppId: "app_demo",
      automationRepositoryId: "repo_auto",
      automationGeneration: 3
    }));

    expect(referenceLocatorFromTab(info)).toEqual({
      kind: "AUTOMATION_REFERENCE",
      path: "cases/login.robot",
      automationAppId: "app_demo",
      automationRepositoryId: "repo_auto",
      automationGeneration: 3
    });
  });
});
