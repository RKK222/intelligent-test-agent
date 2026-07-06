import { describe, expect, it } from "vitest";
import { displayTextFromUserPrompt, workspaceContextAttachmentsFromUserPrompt } from "../src/user-message-display";

describe("displayTextFromUserPrompt", () => {
  it("hides serialized workspace context from user message display", () => {
    const text = [
      "用户问题：",
      "写了什么内容",
      "",
      "以下是用户添加的工作区上下文：",
      "",
      '<context type="selection" path="a.md" lines="5-5">',
      "上下文内容",
      "</context>"
    ].join("\n");

    expect(displayTextFromUserPrompt(text)).toBe("写了什么内容");
  });

  it("keeps normal user messages unchanged", () => {
    expect(displayTextFromUserPrompt("分析 checkout 失败")).toBe("分析 checkout 失败");
  });

  it("extracts associated workspace context attachment metadata", () => {
    const text = [
      "用户问题：",
      "写了什么内容",
      "",
      "以下是用户添加的工作区上下文：",
      "",
      '<context type="selection" path="99-测试数据/Git冲突处理/冲突文件.md" lines="5-5">',
      "选区内容",
      "</context>",
      "",
      '<context type="file" path="docs/a&amp;b.md">',
      "文件内容",
      "</context>"
    ].join("\n");

    expect(workspaceContextAttachmentsFromUserPrompt(text)).toEqual([
      {
        type: "selection",
        path: "99-测试数据/Git冲突处理/冲突文件.md",
        fileName: "冲突文件.md",
        lines: "5-5"
      },
      {
        type: "file",
        path: "docs/a&b.md",
        fileName: "a&b.md",
        lines: undefined
      }
    ]);
  });
});
