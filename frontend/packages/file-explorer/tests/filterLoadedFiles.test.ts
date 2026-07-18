import { describe, expect, it } from "vitest";
import { filterLoadedFiles } from "../src";
import type { WorkspaceViewEntry } from "@test-agent/shared-types";

describe("filterLoadedFiles", () => {
  it("filters only loaded file entries by file name or path", () => {
    const result = filterLoadedFiles(
      {
        "": [
          { type: "directory", path: "tests", name: "tests" },
          { type: "file", path: "package.json", name: "package.json" }
        ],
        tests: [{ type: "file", path: "tests/checkout.spec.ts", name: "checkout.spec.ts" }]
      },
      "checkout"
    );

    expect(result).toEqual([{ type: "file", path: "tests/checkout.spec.ts", name: "checkout.spec.ts" }]);
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
});
