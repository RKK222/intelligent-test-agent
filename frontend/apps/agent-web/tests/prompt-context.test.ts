import { describe, expect, it } from "vitest";
import { buildEditorFilePromptPart } from "../src/components/prompt-context";

describe("prompt editor context", () => {
  const tab = {
    path: "src/spec.ts",
    content: "line 1\nline 2\nline 3"
  };

  it("uses selected editor text when a Monaco selection is active", () => {
    expect(
      buildEditorFilePromptPart(tab, {
        startLineNumber: 2,
        startColumn: 1,
        endLineNumber: 2,
        endColumn: 7,
        text: "line 2"
      })
    ).toEqual({
      type: "file",
      path: "src/spec.ts",
      name: "spec.ts",
      source: {
        start: 2,
        end: 2,
        text: "line 2",
        startLine: 2,
        endLine: 2,
        contextType: "selection"
      }
    });
  });

  it("does not fall back to the active file content when there is no selection", () => {
    expect(buildEditorFilePromptPart(tab, undefined)).toBeUndefined();
  });
});
