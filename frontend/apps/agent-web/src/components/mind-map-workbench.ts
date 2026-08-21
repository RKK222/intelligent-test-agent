import type { MindMapDocumentStatus, MindMapVisualDraft } from "@test-agent/editor";
import type { EditorTab } from "@test-agent/workbench-shell";

export function mindMapEditBlockReason(
  tab: EditorTab | undefined,
  status: MindMapDocumentStatus | undefined
): string | undefined {
  if (!tab) return "未选择思维导图文件";
  if (tab.progressivePreview) return "渐进式大文件预览不可编辑";
  if (tab.readonly || tab.livePreview) return "只读文件不可编辑";
  if (!status) return "正在检查思维导图格式";
  if (!status.canEdit) return status.message ?? "思维导图格式不支持编辑";
  return undefined;
}

export function createMindMapDraft(
  tab: EditorTab,
  status: MindMapDocumentStatus | undefined
): MindMapVisualDraft | undefined {
  if (mindMapEditBlockReason(tab, status)) return undefined;
  return {
    kind: "mind-map",
    baseContent: tab.content,
    content: tab.content
  };
}

export function mindMapSaveBlockedReason(tab: EditorTab | undefined): string | undefined {
  return tab?.visualDraft?.kind === "mind-map"
    ? "请先应用或取消思维导图编辑"
    : undefined;
}
