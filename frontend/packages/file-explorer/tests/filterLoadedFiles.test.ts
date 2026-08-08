import { describe, expect, it } from "vitest";
import { filterLoadedFiles } from "../src";
import type { WorkspaceViewEntry } from "@test-agent/shared-types";

describe("filterLoadedFiles", () => {
  it("filters loaded files by file name instead of parent directory path", () => {
    const result = filterLoadedFiles(
      {
        "": [{ type: "directory", path: "需求资料", name: "需求资料" }],
        需求资料: [
          { type: "file", path: "需求资料/贷款申请.md", name: "贷款申请.md" },
          { type: "file", path: "需求资料/需求说明.md", name: "需求说明.md" }
        ]
      },
      "  需求  "
    );

    expect(result).toEqual([
      { type: "file", path: "需求资料/需求说明.md", name: "需求说明.md" }
    ]);
  });

  it("keeps local fallback search physical-workspace-only", () => {
    const result = filterLoadedFiles(
      {
        "": [
          { type: "file", path: "README.md", name: "README.md" },
          ({
            id: "reference:requirements:only",
            type: "file",
            path: "reference-only.md",
            name: "reference-only.md",
            locator: { kind: "REFERENCE", path: "reference-only.md", referenceAlias: "requirements" },
            source: "REFERENCE",
            merged: true,
            collision: false,
            readonly: true,
            referenceAliases: ["requirements"]
          } as WorkspaceViewEntry)
        ]
      },
      "reference-only"
    );

    expect(result).toEqual([]);
  });

  it("matches file names case-insensitively and ignores whitespace-only keywords", () => {
    const entriesByDirectory = {
      "": [{ type: "file" as const, path: "AgentConfig.vue", name: "AgentConfig.vue" }]
    };

    expect(filterLoadedFiles(entriesByDirectory, " CONFIG ")).toEqual([
      { type: "file", path: "AgentConfig.vue", name: "AgentConfig.vue" }
    ]);
    expect(filterLoadedFiles(entriesByDirectory, "   ")).toEqual([]);
  });
});
