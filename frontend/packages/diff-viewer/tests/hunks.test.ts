import { describe, expect, it } from "vitest";
import type { RunDiffFile } from "@test-agent/shared-types";
import { hunkToPromptPart, parseDiffHunks, selectAdjacentHunk } from "../src/hunks";

describe("diff hunks", () => {
  const file: RunDiffFile = {
    path: "src/App.tsx",
    status: "modified",
    additions: 3,
    deletions: 1,
    patch: [
      "--- a/src/App.tsx",
      "+++ b/src/App.tsx",
      "@@ -1,2 +1,3 @@ render",
      " const name = 'test';",
      "-old();",
      "+newFlow();",
      "+assertReady();",
      "@@ -20 +21,2 @@ footer",
      "-done();",
      "+finish();",
      "+cleanup();"
    ].join("\n")
  };

  it("parses unified patch hunk headers with file path and ranges", () => {
    expect(parseDiffHunks(file)).toEqual([
      {
        index: 0,
        filePath: "src/App.tsx",
        oldStart: 1,
        oldLines: 2,
        newStart: 1,
        newLines: 3,
        heading: "render",
        patch: " const name = 'test';\n-old();\n+newFlow();\n+assertReady();"
      },
      {
        index: 1,
        filePath: "src/App.tsx",
        oldStart: 20,
        oldLines: 1,
        newStart: 21,
        newLines: 2,
        heading: "footer",
        patch: "-done();\n+finish();\n+cleanup();"
      }
    ]);
  });

  it("wraps adjacent hunk navigation", () => {
    const hunks = parseDiffHunks(file);

    expect(selectAdjacentHunk(hunks, 0, "previous")?.index).toBe(1);
    expect(selectAdjacentHunk(hunks, 1, "next")?.index).toBe(0);
  });

  it("converts a selected hunk to a file prompt part", () => {
    const part = hunkToPromptPart(file, parseDiffHunks(file)[1]!);

    expect(part).toEqual({
      type: "file",
      path: "src/App.tsx",
      name: "App.tsx",
      source: {
        start: 21,
        end: 22,
        text: "-done();\n+finish();\n+cleanup();"
      }
    });
  });
});
