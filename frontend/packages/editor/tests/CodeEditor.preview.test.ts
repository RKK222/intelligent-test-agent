import { describe, expect, it, vi } from "vitest";
import { render } from "@testing-library/vue";

// 屏蔽 Monaco 真实加载（jsdom 无法运行 Monaco），提供一个最小 editor 工厂桩
vi.mock("../src/monaco-env", () => {
  const fakeModel = {
    getValue: () => "",
    setValue: () => {},
    getValueInRange: () => "",
    dispose: () => {}
  };
  const fakeEditor = {
    setModel: () => {},
    onDidChangeModelContent: () => ({ dispose: () => {} }),
    onDidChangeCursorSelection: () => ({ dispose: () => {} }),
    onDidScrollChange: () => ({ dispose: () => {} }),
    getSelection: () => null,
    getModel: () => fakeModel,
    updateOptions: () => {},
    getVisibleRanges: () => [{ startLineNumber: 1, endLineNumber: 1 }],
    getTopForLineNumber: () => 0,
    setScrollTop: () => {},
    dispose: () => {}
  };
  return {
    monaco: {
      Uri: { parse: () => ({}) },
      editor: {
        getModel: () => null,
        createModel: () => fakeModel,
        create: () => fakeEditor
      }
    }
  };
});

import CodeEditor from "../src/CodeEditor.vue";

const baseProps = { content: "# hi", dirty: false, readonly: false, saving: false };

describe("CodeEditor Markdown 预览开关", () => {
  it("打开 .md 文件时展示预览按钮，且默认不预览", async () => {
    const { queryByLabelText, queryByText } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md" }
    });
    // 默认不预览：存在「预览」按钮，无「关闭预览」，且无渲染预览区
    expect(queryByLabelText("预览")).toBeTruthy();
    expect(queryByLabelText("关闭预览")).toBeNull();
    expect(queryByText("正在准备预览…")).toBeNull();
  });

  it("打开 .ts 文件时不展示预览按钮", async () => {
    const { queryByLabelText } = render(CodeEditor, {
      props: { ...baseProps, path: "src/app.ts", content: "console.log(1)" }
    });
    expect(queryByLabelText("预览")).toBeNull();
    expect(queryByLabelText("关闭预览")).toBeNull();
  });

  it("点击预览后切换为关闭预览并出现预览区", async () => {
    const { getByLabelText, findByLabelText } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md" }
    });
    const toggle = getByLabelText("预览") as HTMLButtonElement;
    await toggle.click();
    // 预览开启：按钮文案切换为「关闭预览」
    expect(await findByLabelText("关闭预览")).toBeTruthy();
  });
});
