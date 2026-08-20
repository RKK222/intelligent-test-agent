import { describe, expect, it } from "vitest";
import {
  createMindMapDraft,
  mindMapEditBlockReason,
  mindMapSaveBlockedReason
} from "../src/components/mind-map-workbench";

const content = "# 根\n\n- A\n";
const tab = {
  id: "file:docs/plan.mind",
  path: "docs/plan.mind",
  title: "plan.mind",
  content,
  savedContent: content
};

describe("mind map workbench policy", () => {
  it("为合法可写文件创建纯文本可恢复草稿", () => {
    expect(mindMapEditBlockReason(tab, { canEdit: true, issues: [] })).toBeUndefined();
    expect(createMindMapDraft(tab, { canEdit: true, issues: [] })).toEqual({
      kind: "mind-map",
      baseContent: content,
      content
    });
  });

  it("阻断只读、渐进预览和损坏元数据", () => {
    expect(mindMapEditBlockReason({ ...tab, readonly: true }, { canEdit: true, issues: [] }))
      .toContain("只读");
    expect(mindMapEditBlockReason({
      ...tab,
      progressivePreview: {
        size: 6_000_000,
        warningThresholdBytes: 5_000_000,
        loadedBytes: 1024,
        nextOffset: 1024,
        lastModifiedMillis: 1,
        eof: false,
        loading: false,
        loadingAll: false
      }
    }, { canEdit: true, issues: [] })).toContain("渐进");
    expect(mindMapEditBlockReason(tab, {
      canEdit: false,
      message: "第 7 行：元数据校验失败",
      issues: []
    })).toBe("第 7 行：元数据校验失败");
  });

  it("待应用草稿统一阻断保存", () => {
    expect(mindMapSaveBlockedReason(tab)).toBeUndefined();
    expect(mindMapSaveBlockedReason({
      ...tab,
      visualDraft: { kind: "mind-map" as const, baseContent: content, content }
    })).toBe("请先应用或取消思维导图编辑");
  });
});
