import { describe, expect, it, vi } from "vitest";
import { defineComponent, h } from "vue";
import { fireEvent, render } from "@testing-library/vue";

const editorLayout = vi.fn();
const editorCreate = vi.fn();
const mindMapRuntime = vi.hoisted(() => ({
  created: vi.fn(),
  destroyed: vi.fn()
}));

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
    layout: editorLayout,
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
    addAction: () => null,
    dispose: () => {}
  };
  const mockMonaco = {
    Uri: { parse: () => ({}), file: (p: string) => ({ path: p }) },
    KeyMod: { CtrlCmd: 1 << 11 },
    KeyCode: { KeyS: 49 },
    editor: {
      getModel: () => null,
      createModel: () => fakeModel,
      create: (_element: HTMLElement, options: unknown) => {
        editorCreate(options);
        return fakeEditor;
      }
    }
  };
  return {
    loadMonaco: () => Promise.resolve(mockMonaco),
    getMonaco: () => mockMonaco,
    monaco: mockMonaco
  };
});

vi.mock("mermaid", () => ({
  default: {
    initialize: vi.fn(),
    parse: vi.fn().mockResolvedValue(true),
    render: vi.fn().mockResolvedValue({ svg: "<svg />" })
  }
}));

vi.mock("@vue-flow/core", () => ({
  VueFlow: defineComponent({ props: ["nodes", "edges"], template: "<div data-testid='code-editor-flow-mock' />" }),
  Handle: defineComponent({ template: "<span />" }),
  BaseEdge: defineComponent({ template: "<path />" }),
  Position: { Left: "left", Right: "right", Top: "top", Bottom: "bottom" },
  MarkerType: { ArrowClosed: "arrowclosed", Arrow: "arrow" },
  ConnectionMode: { Loose: "loose" },
  getSmoothStepPath: vi.fn(() => ["M0 0 L1 1"])
}));

vi.mock("../src/mind-map/simple-mind-map-runtime", () => ({
  loadSimpleMindMapRuntime: vi.fn(async () => ({
    MindMap: class {
      view = { enlarge: vi.fn(), narrow: vi.fn(), fit: vi.fn() };
      options: Record<string, unknown>;

      constructor(options: Record<string, unknown>) {
        this.options = options;
        mindMapRuntime.created(options);
      }

      on() {}
      off() {}
      execCommand() {}
      getData() { return this.options.data; }
      setData(data: unknown) { this.options.data = data; }
      setMode() {}
      resize() {}
      destroy() { mindMapRuntime.destroyed(); }
    }
  }))
}));

import CodeEditor from "../src/CodeEditor.vue";
import { isMindMapPath } from "../src";

const baseProps = { content: "# hi", dirty: false, readonly: false, saving: false };

describe("CodeEditor Markdown 预览受控", () => {
  it("中间源码编辑区默认启用自动换行", async () => {
    editorCreate.mockClear();
    render(CodeEditor, {
      props: { ...baseProps, path: "logs/output.log" }
    });

    await vi.waitFor(() => expect(editorCreate).toHaveBeenCalled());
    expect(editorCreate.mock.calls[0]?.[0]).toMatchObject({ wordWrap: "on" });
  });

  it("无文件空态允许 app 层通过 slot 注入主页操作", async () => {
    const openManual = vi.fn();
    const { getByRole } = render(CodeEditor, {
      props: baseProps,
      slots: {
        "empty-actions": () => h("button", { onClick: openManual }, "打开用户手册")
      }
    });

    await fireEvent.click(getByRole("button", { name: "打开用户手册" }));

    expect(openManual).toHaveBeenCalledOnce();
  });

  // 预览开关在 WorkbenchFooter，CodeEditor 只负责受控渲染预览区。
  it("打开 .md 文件且 showPreview=false 时不渲染预览区", () => {
    const { queryByText } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md", showPreview: false }
    });
    expect(queryByText("正在准备预览…")).toBeNull();
  });

  it("打开 .md 文件且 previewMode='full' 时整体预览（隐藏源码编辑器）", async () => {
    const { findByText, getByTestId } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md", previewMode: "full" }
    });
    expect(await findByText("正在准备预览…")).toBeTruthy();
    const sourceEl = getByTestId("code-editor-source");
    expect(sourceEl.style.display).toBe("none");
  });

  it("打开 .md 文件且 previewMode='split' 时分屏预览（显示源码编辑器）", async () => {
    const { findByText, getByTestId } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md", previewMode: "split" }
    });
    expect(await findByText("正在准备预览…")).toBeTruthy();
    const sourceEl = getByTestId("code-editor-source");
    expect(sourceEl.style.display).not.toBe("none");
  });

  it("CodeEditor 自身不再渲染预览开关按钮", () => {
    const { queryByLabelText } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md", showPreview: false }
    });
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

  it("预览分屏打开时按源码容器实际尺寸布局 Monaco，避免原文区域空白", async () => {
    editorLayout.mockClear();
    const { findByTestId } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/README.md", showPreview: true, previewMode: "split" }
    });
    const source = await findByTestId("code-editor-source");
    Object.defineProperty(source, "clientWidth", { configurable: true, value: 960 });
    Object.defineProperty(source, "clientHeight", { configurable: true, value: 360 });

    await new Promise(resolve => requestAnimationFrame(resolve));

    expect(editorLayout).toHaveBeenLastCalledWith({ width: 960, height: 360 });
  });

  it("可视化编辑应用后把完整 Markdown 通过既有 change 事件上报", async () => {
    const content = "```mermaid\nflowchart TD\nA --> B\n```";
    const { container, emitted, findByRole } = render(CodeEditor, {
      props: { ...baseProps, content, path: "docs/README.md", previewMode: "full" }
    });
    await new Promise((resolve) => setTimeout(resolve, 350));

    await fireEvent.click(container.querySelector('[data-mermaid-mode="visual"]') as Element);
    // 全量并发时 Mermaid/Vue Flow 懒模块首次转换可能超过 Testing Library 默认 1 秒。
    await fireEvent.click(await findByRole("button", { name: "应用到 Markdown" }, { timeout: 5000 }));

    await vi.waitFor(() => expect(emitted().change).toBeTruthy());
    expect((emitted().change as Array<[string]>)[0]?.[0])
      .toMatch(/^```mermaid\nflowchart TD\n%%@[A-Za-z0-9_-]+(?:\n%%@\+[A-Za-z0-9_-]+)*$/m);
  });
});

describe("CodeEditor 独立思维导图", () => {
  const content = "# 产品冷启动\n\n- 用户分析\n  - 用户画像\n";

  it("显式识别 .mind 且不把相似路径误判为思维导图", () => {
    expect(isMindMapPath("docs/plan.mind")).toBe(true);
    expect(isMindMapPath("docs/PLAN.MIND")).toBe(true);
    expect(isMindMapPath("docs/plan.mind.md")).toBe(false);
    expect(isMindMapPath("docs/mind")).toBe(false);
  });

  it(".mind 默认按需显示只读画布且不创建 Monaco", async () => {
    editorCreate.mockClear();
    mindMapRuntime.created.mockClear();
    const { findByTestId, emitted } = render(CodeEditor, {
      props: { ...baseProps, path: "docs/plan.mind", content }
    });

    expect(await findByTestId("mind-map-canvas")).toBeTruthy();
    await vi.waitFor(() => {
      expect(mindMapRuntime.created).toHaveBeenCalledWith(expect.objectContaining({ readonly: true }));
    });
    expect(editorCreate).not.toHaveBeenCalled();
    await vi.waitFor(() => {
      expect((emitted().mindMapStatus as Array<[{ canEdit: boolean }]> | undefined)?.at(-1)?.[0].canEdit)
        .toBe(true);
    });
  });

  it("损坏元数据只做安全预览并阻止进入编辑器", async () => {
    mindMapRuntime.created.mockClear();
    const damaged = "# 根\n\n<!-- mm:id=root -->\n\n- A <!-- mm:id=n1 -->\n\n<!--mm:v2:AAAA-->\n";
    const { findByTestId, queryByRole, emitted } = render(CodeEditor, {
      props: {
        ...baseProps,
        path: "docs/damaged.mind",
        content: damaged,
        mindMapEditing: true,
        mindMapDraft: { kind: "mind-map", baseContent: damaged, content: damaged }
      }
    });

    expect(await findByTestId("mind-map-canvas")).toBeTruthy();
    expect(queryByRole("button", { name: "应用思维导图" })).toBeNull();
    await vi.waitFor(() => {
      expect(mindMapRuntime.created).toHaveBeenCalledWith(expect.objectContaining({ readonly: true }));
      expect((emitted().mindMapStatus as Array<[{ canEdit: boolean }]>).at(-1)?.[0].canEdit).toBe(false);
    });
  });

  it("切回 .mind 时从受控草稿恢复编辑内容", async () => {
    mindMapRuntime.created.mockClear();
    const draftContent = "# 已恢复草稿\n\n- A\n";
    const { findByRole } = render(CodeEditor, {
      props: {
        ...baseProps,
        path: "docs/plan.mind",
        content,
        mindMapEditing: true,
        mindMapDraft: { kind: "mind-map", baseContent: content, content: draftContent }
      }
    });

    expect(await findByRole("button", { name: "应用思维导图" })).toBeTruthy();
    await vi.waitFor(() => {
      expect(mindMapRuntime.created).toHaveBeenCalledWith(expect.objectContaining({
        readonly: false,
        data: expect.objectContaining({ data: expect.objectContaining({ text: "已恢复草稿" }) })
      }));
    });
  });

  it("应用草稿后上报规范正文并清理受控编辑状态", async () => {
    const { findByRole, emitted } = render(CodeEditor, {
      props: {
        ...baseProps,
        path: "docs/plan.mind",
        content,
        mindMapEditing: true,
        mindMapDraft: { kind: "mind-map", baseContent: content, content }
      }
    });

    await fireEvent.click(await findByRole("button", { name: "应用思维导图" }));

    await vi.waitFor(() => expect(emitted().change).toBeTruthy());
    expect((emitted().change as Array<[string]>).at(-1)?.[0]).toMatch(/<!-- mm:id=root -->[\s\S]*<!--mm:v1:[A-Za-z0-9_-]+-->/);
    expect(emitted()["update:mindMapDraft"]?.at(-1)).toEqual([undefined]);
    expect(emitted()["update:mindMapEditing"]?.at(-1)).toEqual([false]);
  });

  it("取消草稿不修改正文，只清理受控编辑状态", async () => {
    const { findByRole, emitted } = render(CodeEditor, {
      props: {
        ...baseProps,
        path: "docs/plan.mind",
        content,
        mindMapEditing: true,
        mindMapDraft: { kind: "mind-map", baseContent: content, content: "# 草稿\n\n- B\n" }
      }
    });

    await fireEvent.click(await findByRole("button", { name: "取消编辑" }));

    expect(emitted().change).toBeUndefined();
    expect(emitted()["update:mindMapDraft"]?.at(-1)).toEqual([undefined]);
    expect(emitted()["update:mindMapEditing"]?.at(-1)).toEqual([false]);
  });
});
