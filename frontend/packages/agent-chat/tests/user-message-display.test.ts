import { describe, expect, it } from "vitest";
import {
  displayTextFromUserPrompt,
  promptPartsForUserDisplay,
  workspaceContextAttachmentsFromPromptParts,
  workspaceContextAttachmentsFromUserPrompt
} from "../src/user-message-display";

describe("displayTextFromUserPrompt", () => {
  it("keeps only user text and attachment metadata in optimistic timeline parts", () => {
    const serializedSelection = [
      "用户问题：",
      "检查实现",
      "",
      "以下是用户添加的工作区上下文：",
      "",
      '<context type="selection" path="src/UserService.java" lines="20-35">',
      "不应进入用户消息展示的选区原文",
      "</context>"
    ].join("\n");

    expect(
      promptPartsForUserDisplay([
        { type: "text", text: serializedSelection },
        {
          type: "file",
          path: "docs/api.md",
          name: "api.md",
          content: "不应进入用户消息展示的文件原文",
          url: "data:text/plain;base64,Zm9v",
          source: {
            text: "不应进入用户消息展示的 source 原文",
            start: 0,
            end: 10,
            contextType: "file"
          }
        }
      ])
    ).toEqual([
      { type: "text", text: "检查实现" },
      {
        type: "file",
        path: "src/UserService.java",
        name: "UserService.java",
        source: { contextType: "selection", startLine: 20, endLine: 35 }
      },
      {
        type: "file",
        path: "docs/api.md",
        name: "api.md",
        mimeType: undefined,
        source: { start: 0, end: 10, startLine: undefined, endLine: undefined, contextType: "file" }
      }
    ]);
  });

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

  it("extracts associated workspace context metadata from native file prompt parts", () => {
    expect(
      workspaceContextAttachmentsFromPromptParts([
        {
          type: "text",
          text: "写了什么内容"
        },
        {
          type: "file",
          path: "src/UserService.java",
          name: "UserService.java",
          content: "class UserService {}",
          source: {
            text: "class UserService {}",
            startLine: 20,
            endLine: 35,
            contextType: "selection"
          }
        },
        {
          type: "file",
          path: "docs/api.md",
          name: "api.md",
          content: "# API"
        }
      ])
    ).toEqual([
      {
        type: "selection",
        path: "src/UserService.java",
        fileName: "UserService.java",
        lines: "20-35"
      },
      {
        type: "file",
        path: "docs/api.md",
        fileName: "api.md",
        lines: undefined
      }
    ]);
  });

  it("extracts associated workspace context metadata from serialized text prompt parts", () => {
    expect(
      workspaceContextAttachmentsFromPromptParts([
        {
          type: "text",
          text: [
            "用户问题：",
            "能看到什么内容",
            "",
            "以下是用户添加的工作区上下文：",
            "",
            '<context type="selection" path="99-测试数据/Git冲突处理/冲突文件.md" lines="5-5">',
            "应用分支上的修改，用于制造合并冲突。",
            "</context>"
          ].join("\n")
        }
      ])
    ).toEqual([
      {
        type: "selection",
        path: "99-测试数据/Git冲突处理/冲突文件.md",
        fileName: "冲突文件.md",
        lines: "5-5"
      }
    ]);
  });

  it("deduplicates repeated native workspace file prompt parts", () => {
    expect(
      workspaceContextAttachmentsFromPromptParts([
        {
          type: "text",
          text: "写了什么内容"
        },
        {
          type: "file",
          path: "99-测试数据/Git冲突处理/冲突文件.md",
          name: "冲突文件.md",
          content: "内容",
          source: {
            contextType: "file"
          }
        },
        {
          type: "file",
          path: "99-测试数据/Git冲突处理/冲突文件.md",
          name: "冲突文件.md",
          content: "内容",
          source: {
            contextType: "file"
          }
        }
      ])
    ).toEqual([
      {
        type: "file",
        path: "99-测试数据/Git冲突处理/冲突文件.md",
        fileName: "冲突文件.md",
        lines: undefined
      }
    ]);
  });
});
