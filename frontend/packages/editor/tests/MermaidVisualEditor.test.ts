/// <reference types="vite/client" />
import { defineComponent, ref } from "vue";
import { fireEvent, render } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type { EdgeProps } from "@vue-flow/core";
import type { MermaidGraph } from "../src/mermaid/model";
import {
  appendMermaidEdge,
  applyVueFlowPositions,
  canAppendMermaidEdge,
  updateMermaidEdge,
  toVueFlowEdges,
  toVueFlowNodes
} from "../src/mermaid/visual-editor/vue-flow-adapter";

vi.mock("@vue-flow/core", () => ({
  VueFlow: defineComponent({
    name: "VueFlow",
    props: ["nodes", "edges", "nodesConnectable", "connectionMode", "connectOnClick"],
    emits: ["nodeDragStop", "connect", "nodeClick", "edgeClick", "quick-connect-test", "paneClick"],
    setup(_, { expose }) {
      expose({
        screenToFlowCoordinate: ({ x, y }: { x: number; y: number }) => ({ x: x - 100, y: y - 50 })
      });
    },
    template: `<div data-testid="vue-flow-mock">
      <slot name="node-mermaid" v-for="node in (nodes || [])" :key="node.id" :id="node.id" :data="node.data" />
      <button data-testid="mock-drag" @click="$emit('nodeDragStop', { node: { id: 'A', position: { x: 480, y: 260 } } })">drag</button>
      <button data-testid="mock-connect" @click="$emit('connect', { source: 'B', target: 'A' })">connect</button>
      <button data-testid="mock-select" @click="$emit('nodeClick', { node: { id: 'A' } })">select</button>
      <button data-testid="mock-edge-click" @click="$emit('edgeClick', { edge: { id: 'edge-1' } })">edge-click</button>
      <button data-testid="mock-pane-click" @click="$emit('paneClick')">pane-click</button>
      <button data-testid="mock-quick-connect" @click="$emit('quick-connect-test', { portId: 'target-5', position: 'right', shapeType: 'diamond' })">quick-connect</button>
    </div>`
  }),
  Handle: defineComponent({
    props: ["id", "type", "position", "style", "connectable"],
    template: `<span
      data-testid="handle"
      :data-handle-id="id"
      :data-handle-type="type"
      :data-position="position"
      :data-connectable="String(connectable)"
      :style="style"
    />`
  }),
  BaseEdge: defineComponent({
    name: "BaseEdge",
    props: ["path", "style", "markerEnd", "markerStart", "interactionWidth"],
    template: `<path :d="path" data-testid="base-edge" :style="style" />`
  }),
  Position: { Left: "left", Right: "right", Top: "top", Bottom: "bottom" },
  MarkerType: { ArrowClosed: "arrowclosed" },
  ConnectionMode: { Loose: "loose" },
  getSmoothStepPath: vi.fn(() => ["M0 0 L1 1"])
}));

import MermaidVisualEditor from "../src/mermaid/visual-editor/MermaidVisualEditor.vue";
import MermaidFlowNode from "../src/mermaid/visual-editor/MermaidFlowNode.vue";
import MermaidFlowEdge from "../src/mermaid/visual-editor/MermaidFlowEdge.vue";
import flowNodeSource from "../src/mermaid/visual-editor/MermaidFlowNode.vue?raw";
import visualEditorSource from "../src/mermaid/visual-editor/MermaidVisualEditor.vue?raw";

const graph = (): MermaidGraph => ({
  kind: "flowchart",
  direction: "LR",
  nodes: [
    { id: "A", text: "开始", type: "rectangle", position: { x: 80, y: 70 } },
    { id: "B", text: "结束", type: "diamond", position: { x: 300, y: 70 } }
  ],
  edges: [{ id: "edge-1", source: "A", target: "B", label: "下一步", relation: "arrow" }],
  preservedLines: ["classDef important fill:red"]
});

describe("Mermaid Vue Flow 适配", () => {
  it("把领域节点和边映射为 Vue Flow 元素", () => {
    expect(toVueFlowNodes(graph())).toMatchObject([
      { id: "A", type: "mermaid", position: { x: 80, y: 70 }, data: { text: "开始", nodeType: "rectangle" } },
      { id: "B", type: "mermaid", position: { x: 300, y: 70 }, data: { text: "结束", nodeType: "diamond" } }
    ]);
    expect(toVueFlowEdges(graph())).toMatchObject([
      { id: "edge-1", source: "A", target: "B", label: "下一步", markerEnd: "arrowclosed" }
    ]);
  });

  it("把同一节点的多条入边和出边依次分配到三个端口", () => {
    const multiEdgeGraph = graph();
    multiEdgeGraph.edges = Array.from({ length: 4 }, (_, index) => ({
      id: `edge-${index + 1}`,
      source: "A",
      target: "B",
      label: "",
      relation: "arrow" as const
    }));

    const edges = toVueFlowEdges(multiEdgeGraph);

    expect(edges.map((edge) => edge.sourceHandle)).toEqual(["source-0", "source-1", "source-2", "source-0"]);
    expect(edges.map((edge) => edge.targetHandle)).toEqual(["target-0", "target-1", "target-2", "target-0"]);
  });

  it("只把 Vue Flow 拖拽坐标回写到领域模型副本", () => {
    const original = graph();
    const updated = applyVueFlowPositions(original, [{ id: "A", position: { x: 480, y: 260 } }]);

    expect(updated.nodes[0]?.position).toEqual({ x: 480, y: 260 });
    expect(original.nodes[0]?.position).toEqual({ x: 80, y: 70 });
  });

  it("通过 Handle 连接创建不重复的领域边", () => {
    const updated = appendMermaidEdge(graph(), {
      source: "B",
      target: "A",
      sourceHandle: "target-2",
      targetHandle: "source-1"
    });

    expect(updated.edges.at(-1)).toEqual({
      id: "edge-2",
      source: "B",
      target: "A",
      sourceHandle: "target-2",
      targetHandle: "source-1",
      label: "",
      relation: "arrow"
    });
    expect(appendMermaidEdge(updated, {
      source: "B",
      target: "A",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    })).toEqual(updated);
  });

  it("固定端口优先于旧边的自动分配", () => {
    const fixed = graph();
    Object.assign(fixed.edges[0]!, { sourceHandle: "target-2", targetHandle: "source-1" });

    expect(toVueFlowEdges(fixed)[0]).toMatchObject({
      sourceHandle: "target-2",
      targetHandle: "source-1"
    });
  });

  it("拒绝重复有向边和同节点同端口，允许不同端口自环", () => {
    const base = graph();
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "B",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    })).toBe(false);
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "A",
      sourceHandle: "source-0",
      targetHandle: "source-0"
    })).toBe(false);
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "A",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    })).toBe(true);
  });

  it("重连时排除自身后允许在同节点对上换端口", () => {
    const base = graph(); // 已有 A->B edge-1
    // 排除 edge-1 后，A->B 换端口不再判为重复
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "B",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    }, "edge-1")).toBe(true);
    // 不排除时仍是重复
    expect(canAppendMermaidEdge(base, {
      source: "A",
      target: "B",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    })).toBe(false);
    // 排除自身后仍拒绝其它重复边
    const dup = graph();
    dup.edges.push({ id: "edge-2", source: "A", target: "B", label: "", relation: "arrow" });
    expect(canAppendMermaidEdge(dup, {
      source: "A",
      target: "B",
      sourceHandle: "source-0",
      targetHandle: "target-0"
    }, "edge-1")).toBe(false);
  });

  it("updateMermaidEdge 只更新被拖动的一端", () => {
    const base = graph(); // edge-1: A -> B，无显式端口
    const reconnectedTarget = updateMermaidEdge(base, "edge-1", "target", {
      source: "A", target: "B", sourceHandle: "source-0", targetHandle: "target-2"
    });
    expect(reconnectedTarget.edges[0]).toMatchObject({ source: "A", target: "B", targetHandle: "target-2" });
    // source 端保持不变（原边无 sourceHandle）
    expect(reconnectedTarget.edges[0]?.sourceHandle).toBeUndefined();

    const reconnectedSource = updateMermaidEdge(base, "edge-1", "source", {
      source: "B", target: "B", sourceHandle: "source-1", targetHandle: "target-0"
    });
    expect(reconnectedSource.edges[0]).toMatchObject({ source: "B", sourceHandle: "source-1", target: "B" });
    // target 端保持不变
    expect(reconnectedSource.edges[0]?.targetHandle).toBeUndefined();
    // 原图未被修改
    expect(base.edges[0]?.targetHandle).toBeUndefined();
  });
});

describe("MermaidFlowNode", () => {
  it.each([
    ["TD"] as const,
    ["TB"] as const,
    ["BT"] as const,
    ["LR"] as const,
    ["RL"] as const
  ])("在 %s 方向渲染矩形多连接点", (direction_tuple) => {
    const direction = direction_tuple[0] as MermaidGraph["direction"];
    const { getAllByTestId } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "开始", nodeType: "rectangle", direction }
      }
    });

    const handles = getAllByTestId("handle") as HTMLElement[];
    const targetHandles = handles.filter((handle) => handle.dataset.handleId?.startsWith("target-"));
    const sourceHandles = handles.filter((handle) => handle.dataset.handleId?.startsWith("source-"));

    // 矩形: 4顶点 + 每边2个 = 12个端口，target-0~5, source-0~5
    expect(targetHandles).toHaveLength(6);
    expect(sourceHandles).toHaveLength(6);
    expect(targetHandles.map((handle) => handle.dataset.handleId)).toEqual(
      ["target-0", "target-1", "target-2", "target-3", "target-4", "target-5"]
    );
    expect(sourceHandles.map((handle) => handle.dataset.handleId)).toEqual(
      ["source-0", "source-1", "source-2", "source-3", "source-4", "source-5"]
    );
    expect(handles.every((handle) => handle.dataset.handleType === "source")).toBe(true);
    expect(handles.every((handle) => handle.dataset.connectable === "false")).toBe(true);
  });

  it.each([
    ["TD"] as const,
    ["BT"] as const,
    ["LR"] as const,
    ["RL"] as const
  ])("让 %s 方向判断节点渲染12个端口", (direction_tuple) => {
    const direction = direction_tuple[0] as MermaidGraph["direction"];
    const { getAllByTestId } = render(MermaidFlowNode, {
      props: {
        id: "D",
        data: { text: "判断", nodeType: "diamond", direction }
      }
    });

    const handles = getAllByTestId("handle") as HTMLElement[];
    const targetHandles = handles.filter((handle) => handle.dataset.handleId?.startsWith("target-"));
    const sourceHandles = handles.filter((handle) => handle.dataset.handleId?.startsWith("source-"));

    // 菱形：4顶点 + 每条斜边上2个 = 12个端口，target-0~5, source-0~5
    expect(targetHandles).toHaveLength(6);
    expect(sourceHandles).toHaveLength(6);
    expect(handles.every((handle) => handle.dataset.handleType === "source")).toBe(true);
  });

  it("用水平多边形绘制判断节点和图形库预览", () => {
    expect(flowNodeSource).not.toContain("rotate(45deg)");
    expect(flowNodeSource).not.toContain("rotate(-45deg)");
    expect(flowNodeSource).toContain("width: 150px");
    expect(flowNodeSource).toContain("height: 88px");
    expect(flowNodeSource).toContain("polygon(50% 0, 100% 50%, 50% 100%, 0 50%)");

    expect(visualEditorSource).not.toContain("rotate(45deg)");
    expect(visualEditorSource).toContain("width: 72px");
    expect(visualEditorSource).toContain("height: 38px");
    expect(visualEditorSource).toContain("polygon(50% 0, 100% 50%, 50% 100%, 0 50%)");
  });

  it("通用端口默认隐藏并在节点悬浮时显示", () => {
    expect(flowNodeSource).toContain("opacity: 0");
    expect(flowNodeSource).toContain(".ta-mermaid-flow-node:hover");
  });

  it("快捷箭头按方向选中该边上的连接点作为起始点", async () => {
    const { container, emitted } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "开始", nodeType: "rectangle", direction: "LR" }
      }
    });
    // 鼠标悬浮节点才显示快捷箭头（与是否选中无关）
    await fireEvent.mouseEnter(container.querySelector("[data-mermaid-node-id]")!);
        data: { text: "开始", nodeType: "rectangle", direction: "LR" },
        selected: true
      }
    });

    // 矩形四条边都没有正好位于 50% 的端口，旧实现会退化到左上角 target-0；
    // 现在应取各边上最接近中点的端口，使起始点落在箭头所在边上。
    const cases = [
      { cls: "is-top", dir: "top", expectedPort: "target-2" },
      { cls: "is-bottom", dir: "bottom", expectedPort: "target-3" },
      { cls: "is-left", dir: "left", expectedPort: "target-4" },
      { cls: "is-right", dir: "right", expectedPort: "target-5" }
    ];
    for (const item of cases) {
      const button = container.querySelector<HTMLElement>(
        `.ta-mermaid-quick-connector-wrapper.${item.cls} .ta-mermaid-quick-menu button`
      );
      expect(button).toBeTruthy();
      await fireEvent.click(button!);
    }

    const payloads = emitted().quickConnect as Array<
      [{ portId: string; position: string; shapeType: string }]
    >;
    expect(payloads).toHaveLength(4);
    const portByDir = new Map(payloads.map(([payload]) => [payload.position, payload.portId]));
    expect(portByDir.get("top")).toBe("target-2");
    expect(portByDir.get("bottom")).toBe("target-3");
    expect(portByDir.get("left")).toBe("target-4");
    expect(portByDir.get("right")).toBe("target-5");
  });

  it("点击快捷箭头不会误触发端口连线拖拽", async () => {
    const { container, emitted } = render(MermaidFlowNode, {
      props: { id: "A", data: { text: "开始", nodeType: "rectangle", direction: "LR" } }
    });
    const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
    await fireEvent.mouseEnter(root);

    const button = container.querySelector<HTMLElement>(".ta-mermaid-quick-menu button")!;
    // pointerdown 冒泡到箭头时被 @pointerdown.stop 拦截，不会发起端口连线拖拽
    await fireEvent.pointerDown(button);
    expect(emitted().connectionStart ?? []).toHaveLength(0);

    // click 仍能正常触发快捷建连
    await fireEvent.click(button);
    expect(emitted().quickConnect).toHaveLength(1);
  });

  it("节点根元素在 18px 内命中任一端口并发起拖线", () => {
    const { container, getAllByTestId, emitted } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "开始", nodeType: "rectangle", direction: "LR" }
      }
    });
    const root = container.querySelector<HTMLElement>("[data-mermaid-node-id]")!;
    const handles = getAllByTestId("handle") as HTMLElement[];
    // 矩形现有 12 个端口
    expect(handles).toHaveLength(12);
    handles.forEach((handle, index) => {
      Object.defineProperty(handle, "getBoundingClientRect", {
        configurable: true,
        value: () => ({ left: index * 30, top: 0, right: index * 30 + 16, bottom: 16, width: 16, height: 16 })
      });
      const event = new MouseEvent("pointerdown", {
        bubbles: true,
        cancelable: true,
        button: 0,
        clientX: index * 30 + 8,
        clientY: 8
      });
      Object.defineProperty(event, "pointerId", { value: index + 1 });
      root.dispatchEvent(event);
    });

    expect(emitted().connectionStart).toHaveLength(12);
    const starts = emitted().connectionStart as Array<[{ handleId: string }]>;
    // 12 个端口依次触发 connectionStart
    expect(starts.map(([start]) => start.handleId)).toHaveLength(12);

    const mouseDown = new MouseEvent("mousedown", {
      bubbles: true,
      cancelable: true,
      button: 0,
      clientX: 8,
      clientY: 8
    });
    root.dispatchEvent(mouseDown);
    expect(mouseDown.defaultPrevented).toBe(true);
  });

  it("拖线时区分起点、当前目标和有效或无效吸附状态", () => {
    const { container } = render(MermaidFlowNode, {
      props: {
        id: "B",
        data: { text: "结束", nodeType: "rectangle", direction: "LR" },
        connectionSourceHandleId: "source-0",
        isConnectionTarget: true,
        snappedHandleId: "target-1",
        connectionStatus: "invalid"
      }
    });

    expect(container.querySelector(".is-connection-source")).toBeTruthy();
    expect(container.querySelector(".is-connection-target")).toBeTruthy();
    expect(container.querySelector(".is-snapped-invalid")).toBeTruthy();
  });
});

describe("MermaidFlowEdge", () => {
  const edgeProps = (selected = false) =>
    ({
      id: "edge-1",
      source: "A",
      target: "B",
      sourceX: 10,
      sourceY: 20,
      sourcePosition: "right",
      targetX: 100,
      targetY: 20,
      targetPosition: "left",
      sourceHandleId: "source-0",
      targetHandleId: "target-1",
      markerEnd: "url(#ta-mermaid-preview-arrow)",
      markerStart: "",
      style: {},
      data: {},
      events: {},
      selected
    }) as unknown as EdgeProps;

  it("未选中不显示端点圆圈，选中后显示两个", () => {
    const idle = render(MermaidFlowEdge, { props: edgeProps(false) });
    expect(idle.container.querySelectorAll(".ta-mermaid-edge-handle")).toHaveLength(0);

    const active = render(MermaidFlowEdge, { props: edgeProps(true) });
    expect(active.container.querySelectorAll(".ta-mermaid-edge-handle")).toHaveLength(2);
  });

  it("有文字时在边中点渲染标签，无文字不渲染", () => {
    const withLabel = render(MermaidFlowEdge, {
      props: { ...edgeProps(true), label: "下一步" } as unknown as EdgeProps
    });
    const labelEl = withLabel.container.querySelector(".ta-mermaid-edge-label");
    expect(labelEl).toBeTruthy();
    expect(labelEl?.textContent).toContain("下一步");

    const withoutLabel = render(MermaidFlowEdge, { props: edgeProps(true) });
    expect(withoutLabel.container.querySelector(".ta-mermaid-edge-label")).toBeNull();
  });

  it("按下端点圆圈发出重连起点（带固定端信息）", async () => {
    const { container, emitted } = render(MermaidFlowEdge, { props: edgeProps(true) });
    const handles = container.querySelectorAll<HTMLElement>(".ta-mermaid-edge-handle");
    // 第一个圆圈在 sourceX（source 端），第二个在 targetX（target 端）
    await fireEvent.pointerDown(handles[1]!); // 拖动 target 端 -> 固定 source
    let calls = emitted().reconnectStart as Array<[Record<string, unknown>]> | undefined;
    expect(calls!.at(-1)![0]).toMatchObject({
      edgeId: "edge-1",
      end: "target",
      fixedNodeId: "A",
      fixedHandleId: "source-0",
      fixedPosition: "right"
    });

    await fireEvent.pointerDown(handles[0]!); // 拖动 source 端 -> 固定 target
    calls = emitted().reconnectStart as Array<[Record<string, unknown>]> | undefined;
    expect(calls!.at(-1)![0]).toMatchObject({
      edgeId: "edge-1",
      end: "source",
      fixedNodeId: "B",
      fixedHandleId: "target-1",
      fixedPosition: "left"
    });
  });
});

describe("MermaidVisualEditor", () => {
  it("用五种节点图形库替代顶部新增按钮和连线面板", () => {
    const { getByRole, queryByRole } = render(MermaidVisualEditor, { props: { modelValue: graph() } });

    for (const label of ["矩形", "圆角", "胶囊", "判断", "圆形"]) {
      expect(getByRole("button", { name: `添加${label}节点` })).toBeTruthy();
    }
    expect(queryByRole("button", { name: "新增节点" })).toBeNull();
    expect(queryByRole("button", { name: "新增连线" })).toBeNull();
    expect(queryByRole("heading", { name: "连线" })).toBeNull();
  });

  it("点击图形库按钮创建对应类型节点", async () => {
    const { getByRole, emitted } = render(MermaidVisualEditor, { props: { modelValue: graph() } });

    await fireEvent.click(getByRole("button", { name: "添加判断节点" }));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].nodes.at(-1)).toMatchObject({
      id: "N3",
      text: "新节点",
      type: "diamond"
    });
  });

  it("创建节点后在图形库下方立即显示当前节点属性", async () => {
    const EditorHost = defineComponent({
      components: { MermaidVisualEditor },
      setup() {
        return { model: ref(graph()) };
      },
      template: `<MermaidVisualEditor v-model="model" />`
    });
    const { getByLabelText, getByRole } = render(EditorHost);

    await fireEvent.click(getByRole("button", { name: "添加圆角节点" }));

    expect((getByLabelText("节点 ID") as HTMLInputElement).value).toBe("N3");
    expect((getByLabelText("节点名称") as HTMLInputElement).value).toBe("新节点");
    expect((getByLabelText("节点类型") as HTMLSelectElement).value).toBe("rounded");
  });

  it("把节点类型拖到画布落点后创建节点", async () => {
    const { getByLabelText, emitted } = render(MermaidVisualEditor, { props: { modelValue: graph() } });
    const dataTransfer = {
      dropEffect: "none",
      effectAllowed: "all",
      getData: vi.fn(() => "circle"),
      setData: vi.fn()
    };

    const dropEvent = new Event("drop", { bubbles: true, cancelable: true });
    Object.defineProperties(dropEvent, {
      clientX: { value: 420 },
      clientY: { value: 260 },
      dataTransfer: { value: dataTransfer }
    });
    fireEvent(getByLabelText("Mermaid 可视化画布"), dropEvent);

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].nodes.at(-1)).toMatchObject({
      id: "N3",
      text: "新节点",
      type: "circle",
      position: { x: 320, y: 210 }
    });
  });

  it("接收 Vue Flow 拖拽结束事件并上报新坐标", async () => {
    const { getByTestId, emitted } = render(MermaidVisualEditor, { props: { modelValue: graph() } });

    await fireEvent.click(getByTestId("mock-drag"));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].nodes[0]?.position).toEqual({ x: 480, y: 260 });
  });

  it("关闭 Vue Flow 原生连接并使用不拦截鼠标的临时 SVG", () => {
    expect(visualEditorSource).toContain(":nodes-connectable=\"false\"");
    expect(visualEditorSource).toContain(":connect-on-click=\"false\"");
    expect(visualEditorSource).toContain(":connection-mode=\"ConnectionMode.Loose\"");
    expect(visualEditorSource).not.toContain("@connect=\"onConnect\"");
    expect(visualEditorSource).toContain("ta-mermaid-connection-preview");
    expect(visualEditorSource).toContain("pointer-events: none");
  });

  it("可选择节点并修改名称、类型和删除关联边", async () => {
    const { getByTestId, getByLabelText, getByRole, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    await fireEvent.click(getByTestId("mock-select"));
    await fireEvent.update(getByLabelText("节点名称"), "准备");
    await fireEvent.update(getByLabelText("节点类型"), "rounded");
    await fireEvent.click(getByRole("button", { name: "删除节点" }));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.some(([value]) => value.nodes[0]?.text === "准备")).toBe(true);
    expect(updates.some(([value]) => value.nodes[0]?.type === "rounded")).toBe(true);
    expect(updates.at(-1)?.[0].nodes.map((node) => node.id)).toEqual(["B"]);
    expect(updates.at(-1)?.[0].edges).toEqual([]);
  });

  it("支持通过连接点快捷创建并连接新节点", async () => {
    const { getByTestId, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    // 选中节点 A
    await fireEvent.click(getByTestId("mock-select"));

    // 触发快捷连接，朝右侧新建一个 diamond 节点
    await fireEvent.click(getByTestId("mock-quick-connect"));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    const lastUpdate = updates.at(-1)?.[0];
    expect(lastUpdate).toBeTruthy();

    // 校验新建的节点 N3 类型为 diamond，且坐标 x += 190
    expect(lastUpdate!.nodes.at(-1)).toMatchObject({
      id: "N3",
      text: "新节点",
      type: "diamond",
      position: { x: 270, y: 70 } // A: x: 80, y: 70 => 80+190=270, 70
    });

    // 校验新建的边连接：起始点固定为被选中节点 A（即使传入的 portId 为 target-* 也朝外），
    // 起点端口落在矩形右侧（target-5），目标端口落在新建 diamond 朝向起点的左侧（source-1）。
    expect(lastUpdate!.edges.at(-1)).toMatchObject({
      source: "A",
      target: "N3",
      sourceHandle: "target-5",
      targetHandle: "source-1"
    });
  });

  it("快捷建连的箭头方向始终从被选中节点指向新节点", async () => {
    // 即便快捷箭头选中的端口是 target-*，起始点也必须是被选中节点，箭头朝外指向新节点。
    const { getByTestId, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    await fireEvent.click(getByTestId("mock-select"));
    await fireEvent.click(getByTestId("mock-quick-connect"));

    const lastUpdate = (emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0];
    const edge = lastUpdate!.edges.at(-1);
    expect(edge).toMatchObject({ source: "A", target: "N3" });
    // 起点端口随被选中节点形状取右侧中点端口；目标端口随新节点形状取左侧端口。
    expect(edge!.sourceHandle).toBe("target-5");
    expect(edge!.targetHandle).toBe("source-1");
  });

  it("鼠标悬浮节点显示四向快捷箭头，离开后隐藏（与选中无关）", async () => {
    const { container } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });
    const arrowsOf = (nodeId: string) =>
      container.querySelectorAll(`[data-mermaid-node-id="${nodeId}"] .ta-mermaid-quick-connector-wrapper`).length;
    const node = (id: string) => container.querySelector<HTMLElement>(`[data-mermaid-node-id="${id}"]`)!;

    // 未悬浮、也未选中：不显示箭头
    expect(arrowsOf("A")).toBe(0);

    // 悬浮 A：四周出现 4 个半透明快捷箭头
    await fireEvent.mouseEnter(node("A"));
    expect(arrowsOf("A")).toBe(4);

    // 离开 A：箭头隐藏
    await fireEvent.mouseLeave(node("A"));
    expect(arrowsOf("A")).toBe(0);
  });

  it("点击空白画布取消选中节点", async () => {
    const { getByTestId, queryByText } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    await fireEvent.click(getByTestId("mock-select")); // 选中 A，属性面板显示 A
    expect(queryByText("选择画布中的节点或连线后编辑。")).toBeNull();

    await fireEvent.click(getByTestId("mock-pane-click")); // 点击空白画布取消选中
    expect(queryByText("选择画布中的节点或连线后编辑。")).toBeTruthy();
  });

  it("选中连线后可新增、修改并删除连线文字", async () => {
    const { getByTestId, getByLabelText, emitted } = render(MermaidVisualEditor, {
      props: { modelValue: graph() }
    });

    // 选中 edge-1（A->B）
    await fireEvent.click(getByTestId("mock-edge-click"));

    // 新增文字
    await fireEvent.update(getByLabelText("连线文字"), "是");
    expect((emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0].edges[0]?.label).toBe("是");

    // 修改文字
    await fireEvent.update(getByLabelText("连线文字"), "下一步");
    expect((emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0].edges[0]?.label).toBe("下一步");

    // 删除文字（清空）
    await fireEvent.update(getByLabelText("连线文字"), "");
    expect((emitted()["update:modelValue"] as Array<[MermaidGraph]>).at(-1)?.[0].edges[0]?.label).toBe("");
  });
});
