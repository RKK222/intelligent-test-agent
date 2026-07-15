/// <reference types="vite/client" />
import { defineComponent, ref } from "vue";
import { fireEvent, render } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type { MermaidGraph } from "../src/mermaid/model";
import {
  appendMermaidEdge,
  applyVueFlowPositions,
  toVueFlowEdges,
  toVueFlowNodes
} from "../src/mermaid/visual-editor/vue-flow-adapter";

vi.mock("@vue-flow/core", () => ({
  VueFlow: defineComponent({
    name: "VueFlow",
    props: ["nodes", "edges"],
    emits: ["nodeDragStop", "connect", "nodeClick"],
    setup(_, { expose }) {
      expose({
        screenToFlowCoordinate: ({ x, y }: { x: number; y: number }) => ({ x: x - 100, y: y - 50 })
      });
    },
    template: `<div data-testid="vue-flow-mock">
      <button data-testid="mock-drag" @click="$emit('nodeDragStop', { node: { id: 'A', position: { x: 480, y: 260 } } })">drag</button>
      <button data-testid="mock-connect" @click="$emit('connect', { source: 'B', target: 'A' })">connect</button>
      <button data-testid="mock-select" @click="$emit('nodeClick', { node: { id: 'A' } })">select</button>
    </div>`
  }),
  Handle: defineComponent({
    props: ["id", "type", "position", "style"],
    template: `<span
      data-testid="handle"
      :data-handle-id="id"
      :data-handle-type="type"
      :data-position="position"
      :style="style"
    />`
  }),
  Position: { Left: "left", Right: "right", Top: "top", Bottom: "bottom" },
  MarkerType: { ArrowClosed: "arrowclosed" }
}));

import MermaidVisualEditor from "../src/mermaid/visual-editor/MermaidVisualEditor.vue";
import MermaidFlowNode from "../src/mermaid/visual-editor/MermaidFlowNode.vue";
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
    const updated = appendMermaidEdge(graph(), { source: "B", target: "A" });

    expect(updated.edges.at(-1)).toEqual({
      id: "edge-2",
      source: "B",
      target: "A",
      label: "",
      relation: "arrow"
    });
    expect(appendMermaidEdge(updated, { source: "B", target: "A" })).toEqual(updated);
  });
});

describe("MermaidFlowNode", () => {
  it.each([
    ["TD", "top", "bottom", "left"],
    ["TB", "top", "bottom", "left"],
    ["BT", "bottom", "top", "left"],
    ["LR", "left", "right", "top"],
    ["RL", "right", "left", "top"]
  ] as const)("在 %s 方向渲染三入三出端口", (direction, targetPosition, sourcePosition, offsetProperty) => {
    const { getAllByTestId } = render(MermaidFlowNode, {
      props: {
        id: "A",
        data: { text: "开始", nodeType: "rectangle", direction }
      }
    });

    const handles = getAllByTestId("handle") as HTMLElement[];
    const targetHandles = handles.filter((handle) => handle.dataset.handleType === "target");
    const sourceHandles = handles.filter((handle) => handle.dataset.handleType === "source");

    expect(targetHandles.map((handle) => handle.dataset.handleId)).toEqual(["target-0", "target-1", "target-2"]);
    expect(sourceHandles.map((handle) => handle.dataset.handleId)).toEqual(["source-0", "source-1", "source-2"]);
    expect(targetHandles.map((handle) => handle.dataset.position)).toEqual(Array(3).fill(targetPosition));
    expect(sourceHandles.map((handle) => handle.dataset.position)).toEqual(Array(3).fill(sourcePosition));
    expect(targetHandles.map((handle) => handle.style.getPropertyValue(offsetProperty))).toEqual(["25%", "50%", "75%"]);
    expect(sourceHandles.map((handle) => handle.style.getPropertyValue(offsetProperty))).toEqual(["25%", "50%", "75%"]);
  });

  it.each([
    ["TD", "top", "bottom"],
    ["BT", "bottom", "top"],
    ["LR", "left", "right"],
    ["RL", "right", "left"]
  ] as const)("让 %s 方向判断节点的端口贴合菱形轮廓", (direction, targetInsetProperty, sourceInsetProperty) => {
    const { getAllByTestId } = render(MermaidFlowNode, {
      props: {
        id: "D",
        data: { text: "判断", nodeType: "diamond", direction }
      }
    });

    const handles = getAllByTestId("handle") as HTMLElement[];
    const targetHandles = handles.filter((handle) => handle.dataset.handleType === "target");
    const sourceHandles = handles.filter((handle) => handle.dataset.handleType === "source");

    expect(targetHandles.map((handle) => handle.style.getPropertyValue(targetInsetProperty))).toEqual(["25%", "0%", "25%"]);
    expect(sourceHandles.map((handle) => handle.style.getPropertyValue(sourceInsetProperty))).toEqual(["25%", "0%", "25%"]);
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

  it("接收 Vue Flow connect 事件并新增连线", async () => {
    const { getByTestId, emitted } = render(MermaidVisualEditor, { props: { modelValue: graph() } });

    await fireEvent.click(getByTestId("mock-connect"));

    const updates = emitted()["update:modelValue"] as Array<[MermaidGraph]>;
    expect(updates.at(-1)?.[0].edges).toHaveLength(2);
    expect(updates.at(-1)?.[0].edges.at(-1)).toMatchObject({ source: "B", target: "A" });
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
});
