import { describe, expect, it, vi } from "vitest";
import { render, waitFor } from "@testing-library/vue";
import { defineComponent } from "vue";

type FakeUri = { path: string; toString: () => string };
type FakeModel = {
  uri: FakeUri;
  value: string;
  getValue: () => string;
  setValue: ReturnType<typeof vi.fn>;
  getValueInRange: () => string;
  dispose: () => void;
};

const models = new Map<string, FakeModel>();
let activeModel: FakeModel | null = null;
let monacoLoadGate: Promise<void> = Promise.resolve();
let monacoLoadCalls = 0;

vi.mock("../src/monaco-env", () => {
  const uri = (path: string): FakeUri => ({ path, toString: () => `file://${path}` });
  const fakeEditor = {
    setModel: (next: FakeModel) => {
      activeModel = next;
    },
    layout: vi.fn(),
    onDidChangeModelContent: () => ({ dispose: () => {} }),
    onDidChangeCursorSelection: () => ({ dispose: () => {} }),
    onDidScrollChange: () => ({ dispose: () => {} }),
    getSelection: () => null,
    getModel: () => activeModel,
    updateOptions: () => {},
    getVisibleRanges: () => [{ startLineNumber: 1, endLineNumber: 1 }],
    getTopForLineNumber: () => 0,
    setScrollTop: () => {},
    addCommand: () => null,
    addAction: () => null,
    dispose: () => {}
  };
  const mockMonaco = {
    Uri: { parse: uri, file: uri },
    KeyMod: { CtrlCmd: 1 << 11 },
    KeyCode: { KeyS: 49 },
    editor: {
      getModel: (modelUri: FakeUri) => models.get(modelUri.path) ?? null,
      createModel: (content: string, _language: string, modelUri: FakeUri) => {
        const model: FakeModel = {
          uri: modelUri,
          value: content,
          getValue() {
            return this.value;
          },
          setValue: vi.fn((next: string) => {
            model.value = next;
          }),
          getValueInRange: () => "",
          dispose: () => {}
        };
        models.set(modelUri.path, model);
        return model;
      },
      create: (_container: HTMLElement, options: { model: FakeModel }) => {
        activeModel = options.model;
        return fakeEditor;
      }
    }
  };
  return {
    loadMonaco: async () => {
      monacoLoadCalls += 1;
      await monacoLoadGate;
      return mockMonaco;
    },
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
  VueFlow: defineComponent({ template: "<div />" }),
  Handle: defineComponent({ template: "<span />" }),
  BaseEdge: defineComponent({ template: "<path />" }),
  Position: { Left: "left", Right: "right", Top: "top", Bottom: "bottom" },
  MarkerType: { ArrowClosed: "arrowclosed", Arrow: "arrow" },
  ConnectionMode: { Loose: "loose" },
  getSmoothStepPath: vi.fn(() => ["M0 0 L1 1"])
}));

import CodeEditor from "../src/CodeEditor.vue";

describe("CodeEditor Monaco 模型隔离", () => {
  it("路径和内容同 tick 切换时不把新内容写入旧路径模型", async () => {
    models.clear();
    activeModel = null;
    const { rerender } = render(CodeEditor, {
      props: { path: "docs/a.md", content: "A", dirty: false }
    });
    await waitFor(() => expect(models.get("docs/a.md")).toBeTruthy());
    const oldModel = models.get("docs/a.md")!;

    await rerender({ path: "docs/b.md", content: "B", dirty: false });

    await waitFor(() => expect(activeModel?.uri.path).toBe("docs/b.md"));
    expect(models.get("docs/b.md")?.getValue()).toBe("B");
    expect(oldModel.getValue()).toBe("A");
    expect(oldModel.setValue).not.toHaveBeenCalledWith("B");
  });

  it("Agent 编码路径同 tick 切换时保持各自模型内容", async () => {
    models.clear();
    activeModel = null;
    monacoLoadGate = Promise.resolve();
    const publicPath = "agent-public:::agents%2Ftest-design-agent.md";
    const workspacePath = "agent-workspace:::agents%2Ftest-design-case-generation.md";
    const { rerender } = render(CodeEditor, {
      props: { path: publicPath, content: "public Agent", dirty: false }
    });
    await waitFor(() => expect(models.get(publicPath)).toBeTruthy());
    const publicModel = models.get(publicPath)!;

    await rerender({ path: workspacePath, content: "workspace Agent", dirty: false });

    await waitFor(() => expect(activeModel?.uri.path).toBe(workspacePath));
    expect(models.get(workspacePath)?.getValue()).toBe("workspace Agent");
    expect(publicModel.getValue()).toBe("public Agent");
    expect(publicModel.setValue).not.toHaveBeenCalledWith("workspace Agent");
  });

  it("Monaco 异步初始化期间快速切换 Agent 文件只挂载最新路径", async () => {
    models.clear();
    activeModel = null;
    monacoLoadCalls = 0;
    let releaseMonaco!: () => void;
    monacoLoadGate = new Promise<void>((resolve) => {
      releaseMonaco = resolve;
    });
    const firstPath = "agent-public:::agents%2Fslow-agent.md";
    const latestPath = "agent-public:::agents%2Flatest-agent.md";
    const { rerender } = render(CodeEditor, {
      props: { path: firstPath, content: "stale Agent", dirty: false }
    });
    await waitFor(() => expect(monacoLoadCalls).toBeGreaterThan(0));

    await rerender({ path: latestPath, content: "latest Agent", dirty: false });
    releaseMonaco();

    await waitFor(() => expect(activeModel?.uri.path).toBe(latestPath));
    expect(models.has(firstPath)).toBe(false);
    expect(models.get(latestPath)?.getValue()).toBe("latest Agent");
    monacoLoadGate = Promise.resolve();
  });
});
