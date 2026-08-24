import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render } from "@testing-library/vue";
import { defineComponent, ref } from "vue";
import { parseMindMapMarkdown } from "../../src/mind-map/markdown";

const runtimeState = vi.hoisted(() => ({
  instances: [] as Array<{
    options: Record<string, unknown>;
    handlers: Map<string, (...args: unknown[]) => void>;
    resize: ReturnType<typeof vi.fn>;
    destroy: ReturnType<typeof vi.fn>;
    execCommand(command: string, ...args: unknown[]): void;
    command: ReturnType<typeof vi.fn>;
    getData: ReturnType<typeof vi.fn>;
    renderer: { activeNodeList: Array<{ layerIndex: number; isRoot: boolean; getData(): unknown }> };
    view: { enlarge: ReturnType<typeof vi.fn>; narrow: ReturnType<typeof vi.fn>; fit: ReturnType<typeof vi.fn> };
  }>,
  load: vi.fn()
}));

vi.mock("../../src/mind-map/simple-mind-map-runtime", () => ({
  loadSimpleMindMapRuntime: runtimeState.load
}));

import MindMapCanvas from "../../src/mind-map/MindMapCanvas.vue";

let resizeCallback: ResizeObserverCallback | undefined;

function fakeNode(layerIndex: number, isRoot = false) {
  return {
    layerIndex,
    isRoot,
    getData: vi.fn(() => ({ uid: isRoot ? "root" : `n${layerIndex}` }))
  };
}

function createRuntime() {
  class FakeMindMap {
    options: Record<string, unknown>;
    handlers = new Map<string, (...args: unknown[]) => void>();
    resize = vi.fn();
    destroy = vi.fn();
    command = vi.fn();
    getData: ReturnType<typeof vi.fn>;
    renderer = { activeNodeList: [] as ReturnType<typeof fakeNode>[] };
    view = { enlarge: vi.fn(), narrow: vi.fn(), fit: vi.fn() };

    constructor(options: Record<string, unknown>) {
      this.options = options;
      this.getData = vi.fn(() => options.data);
      runtimeState.instances.push(this);
    }

    on(name: string, handler: (...args: unknown[]) => void) {
      this.handlers.set(name, handler);
    }

    off(name: string) {
      this.handlers.delete(name);
    }

    execCommand(command: string, ...args: unknown[]) {
      this.command(command, ...args);
    }
  }
  return { MindMap: FakeMindMap as never };
}

describe("MindMapCanvas", () => {
  beforeEach(() => {
    runtimeState.instances.length = 0;
    runtimeState.load.mockReset().mockResolvedValue(createRuntime());
    resizeCallback = undefined;
    vi.stubGlobal("ResizeObserver", class {
      constructor(callback: ResizeObserverCallback) {
        resizeCallback = callback;
      }
      observe() {}
      disconnect() {}
    });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("挂载时才懒加载依赖，并强制使用右向结构、单行字面文本和安全选项", async () => {
    const document = parseMindMapMarkdown("# 根\n\n- A\n").document!;
    render(MindMapCanvas, { props: { document, readonly: true, generationKey: "a.mind" } });

    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));
    expect(runtimeState.load).toHaveBeenCalledOnce();
    expect(runtimeState.instances[0]?.options).toMatchObject({
      layout: "logicalStructure",
      readonly: true,
      disabledClipboard: true,
      enableCtrlKeyNodeSelection: false,
      enableFreeDrag: false,
      openPerformance: true,
      isShowCreateChildBtnIcon: false,
      data: {
        data: { text: "根", richText: false, expand: true }
      }
    });
  });

  it("节点编号耗尽时在画布边界阻断所有第三方新增命令", async () => {
    const document = parseMindMapMarkdown(
      "# 根\n\n<!-- mm:id=root -->\n\n- A <!-- mm:id=n4294967294 -->\n"
    ).document!;
    render(MindMapCanvas, { props: { document, generationKey: "exhausted.mind" } });
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));
    const instance = runtimeState.instances[0]!;

    for (const command of [
      "INSERT_CHILD_NODE",
      "INSERT_NODE",
      "INSERT_PARENT_NODE",
      "INSERT_MULTI_CHILD_NODE",
      "INSERT_MULTI_NODE"
    ]) {
      instance.execCommand(command);
    }
    instance.execCommand("SELECT_ALL");
    instance.execCommand("SET_NODE_TEXT", "仍可编辑已有节点");

    expect(instance.command).toHaveBeenCalledTimes(1);
    expect(instance.command).toHaveBeenCalledWith("SET_NODE_TEXT", "仍可编辑已有节点");
  });

  it("按实际激活节点数预留 ID，容量只剩一个时阻断多选新增", async () => {
    const document = parseMindMapMarkdown(
      "# 根\n\n<!-- mm:id=root -->\n\n- A <!-- mm:id=n4294967292 -->\n- B <!-- mm:id=n4294967293 -->\n"
    ).document!;
    render(MindMapCanvas, { props: { document, generationKey: "last-id.mind" } });
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));
    const instance = runtimeState.instances[0]!;
    instance.renderer.activeNodeList = [fakeNode(1), fakeNode(1)];

    instance.execCommand("INSERT_CHILD_NODE");

    expect(instance.command).not.toHaveBeenCalled();
  });

  it("达到节点数上限时阻断新增，但继续允许已有节点编辑", async () => {
    const source = [
      "# 根",
      "",
      ...Array.from({ length: 1_999 }, (_, index) => `- N${index + 1}`),
      ""
    ].join("\n");
    const document = parseMindMapMarkdown(source).document!;
    render(MindMapCanvas, { props: { document, generationKey: "node-limit.mind" } });
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));
    const instance = runtimeState.instances[0]!;
    instance.renderer.activeNodeList = [fakeNode(1)];

    instance.execCommand("INSERT_NODE");
    instance.execCommand("SET_NODE_TEXT", "仍可编辑已有节点");

    expect(instance.command).toHaveBeenCalledTimes(1);
    expect(instance.command).toHaveBeenCalledWith("SET_NODE_TEXT", "仍可编辑已有节点");
  });

  it("最深层节点阻断新增子节点，普通单节点新增仍下发", async () => {
    const deepSource = [
      "# 根",
      "",
      ...Array.from({ length: 128 }, (_, depth) => `${"  ".repeat(depth)}- L${depth + 1}`),
      ""
    ].join("\n");
    const deepDocument = parseMindMapMarkdown(deepSource).document!;
    const { unmount } = render(MindMapCanvas, {
      props: { document: deepDocument, generationKey: "depth-limit.mind" }
    });
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));
    const deepInstance = runtimeState.instances[0]!;
    deepInstance.renderer.activeNodeList = [fakeNode(128)];
    deepInstance.execCommand("INSERT_CHILD_NODE");
    expect(deepInstance.command).not.toHaveBeenCalled();

    unmount();
    runtimeState.instances.length = 0;
    const normalDocument = parseMindMapMarkdown("# 根\n\n- A\n").document!;
    render(MindMapCanvas, { props: { document: normalDocument, generationKey: "normal.mind" } });
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));
    const normalInstance = runtimeState.instances[0]!;
    normalInstance.renderer.activeNodeList = [fakeNode(1)];
    normalInstance.execCommand("INSERT_CHILD_NODE");

    expect(normalInstance.command).toHaveBeenCalledOnce();
    expect(normalInstance.command).toHaveBeenCalledWith("INSERT_CHILD_NODE");
  });

  it("容器变化调用 resize，文件代次变化销毁旧实例，卸载销毁当前实例", async () => {
    const document = parseMindMapMarkdown("# 根\n\n- A\n").document!;
    const { rerender, unmount } = render(MindMapCanvas, {
      props: { document, generationKey: "a.mind" }
    });
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));

    resizeCallback?.([], {} as ResizeObserver);
    expect(runtimeState.instances[0]?.resize).toHaveBeenCalledOnce();

    await rerender({ document, generationKey: "b.mind" });
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(2));
    expect(runtimeState.instances[0]?.destroy).toHaveBeenCalledOnce();

    unmount();
    expect(runtimeState.instances[1]?.destroy).toHaveBeenCalledOnce();
  });

  it("同一路径收到外部新文档时重建画布，避免后台刷新后继续显示旧树", async () => {
    const first = parseMindMapMarkdown("# 根\n\n- A\n").document!;
    const second = parseMindMapMarkdown("# 根\n\n- B\n").document!;
    const { rerender } = render(MindMapCanvas, {
      props: { document: first, generationKey: "same.mind" }
    });
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));

    await rerender({ document: second, generationKey: "same.mind" });

    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(2));
    expect(runtimeState.instances[0]?.destroy).toHaveBeenCalledOnce();
    expect(runtimeState.instances[1]?.options.data).toMatchObject({
      children: [{ data: { text: "B" } }]
    });
  });

  it("异步依赖返回时若组件已卸载，不再创建过期画布", async () => {
    let resolveRuntime: ((value: ReturnType<typeof createRuntime>) => void) | undefined;
    runtimeState.load.mockReset().mockReturnValue(new Promise((resolve) => {
      resolveRuntime = resolve;
    }));
    const document = parseMindMapMarkdown("# 根\n\n- A\n").document!;
    const { unmount } = render(MindMapCanvas, { props: { document, generationKey: "a.mind" } });

    unmount();
    resolveRuntime?.(createRuntime());
    await Promise.resolve();

    expect(runtimeState.instances).toHaveLength(0);
  });

  it("画布 data_change 只通过白名单适配后上报领域文档", async () => {
    const document = parseMindMapMarkdown("# 根\n\n- A\n").document!;
    const { emitted } = render(MindMapCanvas, { props: { document, generationKey: "a.mind" } });
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));
    const instance = runtimeState.instances[0]!;
    instance.getData.mockReturnValue({
      data: { text: "根", uid: "root", expand: true, richText: false },
      children: [{
        data: {
          text: "更新",
          uid: "n1",
          expand: true,
          richText: false,
          hyperlink: "javascript:alert(1)"
        },
        children: []
      }]
    });

    instance.handlers.get("data_change")?.();

    expect((emitted().change as Array<[typeof document]>)[0]?.[0].root.children[0]?.text).toBe("更新");
    expect((emitted().change as Array<[typeof document]>)[0]?.[0].root.children[0]).not.toHaveProperty("hyperlink");
  });

  it("父级接回画布自身的 change 时不重复重建实例", async () => {
    const initial = parseMindMapMarkdown("# 根\n\n- A\n").document!;
    const Host = defineComponent({
      components: { MindMapCanvas },
      setup() {
        return { current: ref(initial) };
      },
      template: `<MindMapCanvas :document="current" generation-key="same.mind" @change="current = $event" />`
    });
    render(Host);
    await vi.waitFor(() => expect(runtimeState.instances).toHaveLength(1));
    const instance = runtimeState.instances[0]!;
    instance.getData.mockReturnValue({
      data: { text: "根", uid: "root", expand: true, richText: false },
      children: [{
        data: { text: "更新", uid: "n1", expand: true, richText: false },
        children: []
      }]
    });

    instance.handlers.get("data_change")?.();
    await Promise.resolve();

    expect(runtimeState.instances).toHaveLength(1);
    expect(instance.destroy).not.toHaveBeenCalled();
  });
});
