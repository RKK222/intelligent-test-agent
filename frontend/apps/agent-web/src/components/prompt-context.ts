import type { PromptPart } from "@test-agent/shared-types";

export type EditorSelectionContext = {
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
  text: string;
};

export function buildEditorFilePromptPart(
  activeTab: { path: string; content: string } | undefined,
  selection: EditorSelectionContext | undefined
): Extract<PromptPart, { type: "file" }> | undefined {
  if (!activeTab?.path) {
    return undefined;
  }
  const selectedText = selection?.text.trim() ? selection.text : undefined;
  return {
    type: "file",
    path: activeTab.path,
    name: activeTab.path.split("/").at(-1) ?? activeTab.path,
    source: selectedText
      ? {
          start: selection?.startLineNumber,
          end: selection?.endLineNumber,
          text: selectedText.slice(0, 12000)
        }
      : { text: activeTab.content.slice(0, 12000) }
  };
}
