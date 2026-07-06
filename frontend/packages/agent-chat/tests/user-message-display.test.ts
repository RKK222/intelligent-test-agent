import { describe, expect, it } from "vitest";
import { displayTextFromUserPrompt } from "../src/user-message-display";

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
});
