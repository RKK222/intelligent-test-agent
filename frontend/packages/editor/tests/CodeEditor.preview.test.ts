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
    addCommand: () => null,
    dispose: () => {}
  };
  return {
    monaco: {
      Uri: { parse: () => ({}) },
      KeyMod: { CtrlCmd: 1 << 11 },
      KeyCode: { KeyS: 49 },
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

describe("CodeEditor Markdown 预览受控", () => {
  // 预览开关已上提到 FigmaEditorArea tab 表头，CodeEditor 只负责受控渲染预览区。
  it("打开 .md 文件且 showPreview=false 时不渲染预览区", () => {
    const { queryByText } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md", showPreview: false }
    });
    expect(queryByText("正在准备预览…")).toBeNull();
  });

  it("打开 .md 文件且 showPreview=true 时进入预览加载态", async () => {
    const { findByText } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md", showPreview: true }
    });
    // 预览组件首次渲染会显示「正在准备预览…」占位（markdown-it 懒加载中）
    expect(await findByText("正在准备预览…")).toBeTruthy();
  });

  it("CodeEditor 自身不再渲染预览开关按钮", () => {
    const { queryByLabelText } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md", showPreview: false }
    });
    // tab 表头按钮上提到 FigmaEditorArea，这里只断言 CodeEditor 不再自带入口
    expect(queryByLabelText("预览")).toBeNull();
    expect(queryByLabelText("关闭预览")).toBeNull();
  });

  it("showPreview 由 true 切到 false 时预览区消失", async () => {
    const { rerender, queryByText } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md", showPreview: true }
    });
    expect(queryByText("正在准备预览…")).toBeTruthy();
    await rerender({ ...baseProps, path: "docs/README.md", showPreview: false });
    expect(queryByText("正在准备预览…")).toBeNull();
  });
});
