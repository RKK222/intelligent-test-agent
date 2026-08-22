import { describe, expect, it } from "vitest";
import {
  automationReferencePatches,
  normalizeRepositoryList
} from "../src/components/automation-reference-config-reconciliation";

const repository = {
  repositoryId: "repo_auto",
  currentConfiguration: {
    generation: 2,
    alias: "automation-auto",
    logicalPath: "{env:OPENCODE_REFERENCES_DIR}/automation/app/auto/2/tests",
    directoryName: "tests",
    description: "只读自动化引用",
    status: "READY"
  }
};

describe("automation reference workspace reconciliation contract", () => {
  it("accepts both the current array payload and the rolling-upgrade data wrapper", () => {
    expect(normalizeRepositoryList([repository])).toEqual([repository]);
    expect(normalizeRepositoryList({ data: [repository] })).toEqual([repository]);
    expect(automationReferencePatches("app_demo", { data: [repository] })).toHaveLength(1);
  });

  it("reports an invalid payload instead of crashing on flatMap", () => {
    expect(() => normalizeRepositoryList({ repositories: [] }))
      .toThrow("自动化代码库列表响应格式无效");
  });
});
