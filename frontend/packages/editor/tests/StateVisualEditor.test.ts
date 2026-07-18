import { defineComponent, h } from "vue";
import { fireEvent, render, within } from "@testing-library/vue";
import { describe, expect, it, vi } from "vitest";
import type { MermaidStateDiagram } from "../src/mermaid/state/model";
import { flattenMermaidStateNodes } from "../src/mermaid/state/model";
import { parseMermaidState } from "../src/mermaid/state/parser";

vi.mock("@vue-flow/core", () => {
  const Position = { Top: "top", Right: "right", Bottom: "bottom", Left: "left" };
  return {
    Position,
    MarkerType: { ArrowClosed: "arrowclosed" },
    ConnectionMode: { Loose: "loose" },
    Handle: defineComponent({
      inheritAttrs: false,
      props: ["id", "type", "position"],
      setup(props, { attrs }) {
        return () => h("span", { ...attrs, "data-handle-id": props.id });
      }
    }),
    BaseEdge: defineComponent({ template: "<path data-testid='state-base-edge' />" }),
    getSmoothStepPath: () => ["M 0 0 L 10 10"],
    VueFlow: defineComponent({
      props: ["nodes", "edges"],
      emits: ["init", "nodeDragStop", "nodeClick", "edgeClick"],
      setup(props, { emit, slots }) {
        emit("init", { fitView: vi.fn(), screenToFlowCoordinate: (point: unknown) => point });
        return () => h("div", {
          "data-testid": "state-flow-mock",
          "data-node-count": props.nodes.length,
          "data-edge-count": props.edges.length
        }, [
          ...props.nodes.map((node: Record<string, unknown>) => slots[`node-${node.type}`]?.({ ...node, selected: false })),
          ...props.edges.map((edge: Record<string, unknown>) => slots[`edge-${edge.type}`]?.({
            ...edge,
            sourceX: 20,
            sourceY: 20,
            targetX: 180,
            targetY: 90,
            sourcePosition: Position.Right,
            targetPosition: Position.Left,
            selected: false
          }))
        ]);
      }
    })
  };
});

import StateVisualEditor from "../src/mermaid/state/visual-editor/StateVisualEditor.vue";
import StateNode from "../src/mermaid/state/visual-editor/StateNode.vue";
import StateTransitionEdge from "../src/mermaid/state/visual-editor/StateTransitionEdge.vue";

function stateDiagram(): MermaidStateDiagram {
  return parseMermaidState(`stateDiagram-v2
direction TB
[*] --> Idle
state "空闲" as Idle
Idle: 等待任务
Idle --> Running: 启动
state Running {
  direction LR
  [*] --> Frontend
  Frontend --> [*]
  --
  [*] --> Backend
  Backend --> [*]
}
Running --> [*]
note right of Idle: 可以启动`);
}

function updates(emitted: ReturnType<typeof render>["emitted"]): MermaidStateDiagram[] {
  return ((emitted()["update:modelValue"] ?? []) as Array<[MermaidStateDiagram]>).map(([diagram]) => diagram);
}

describe("StateVisualEditor 概览与聚焦编辑", () => {
  it("根概览展示 Region、全部状态和分组元素库", () => {
    const { getByTestId, getByRole, getByText } = render(StateVisualEditor, {
      props: { modelValue: stateDiagram() }
    });

    expect(Number(getByTestId("state-flow-mock").getAttribute("data-node-count"))).toBeGreaterThanOrEqual(6);
    expect(getByRole("button", { name: "根状态图" })).toBeTruthy();
    expect(getByText("状态")).toBeTruthy();
    expect(getByText("伪状态")).toBeTruthy();
    expect(getByText("说明" )).toBeTruthy();
    for (const label of ["新增普通状态", "新增复合状态", "新增开始", "新增结束", "新增 Choice", "新增 Fork", "新增 Join", "新增 Note"]) {
      expect(getByRole("button", { name: label })).toBeTruthy();
    }
  });

  it("双击复合状态进入聚焦画布，同时展示两个并发 Region 并通过面包屑返回", async () => {
    const { getByLabelText, getByRole, getByTestId } = render(StateVisualEditor, {
      props: { modelValue: stateDiagram() }
    });

    await fireEvent.dblClick(getByLabelText("复合状态 Running，双击进入"));
    expect(getByRole("button", { name: "Running" }).getAttribute("aria-current")).toBe("page");
    expect(within(getByTestId("state-flow-mock")).getAllByText(/并发区域/)).toHaveLength(2);
    expect(getByLabelText("状态 Frontend")).toBeTruthy();
    expect(getByLabelText("状态 Backend")).toBeTruthy();

    await fireEvent.click(getByRole("button", { name: "根状态图" }));
    expect(getByLabelText("复合状态 Running，双击进入")).toBeTruthy();
  });

  it("元素库点击创建所有节点类型、Note 和并发 Region", async () => {
    const view = render(StateVisualEditor, { props: { modelValue: stateDiagram() } });
    const labels = ["新增普通状态", "新增复合状态", "新增开始", "新增结束", "新增 Choice", "新增 Fork", "新增 Join"];
    let current = stateDiagram();
    await view.rerender({ modelValue: current });
    for (const label of labels) {
      await fireEvent.click(view.getByRole("button", { name: label }));
      current = updates(view.emitted).at(-1)!;
      await view.rerender({ modelValue: current });
    }
    expect(flattenMermaidStateNodes(current).map((node) => node.kind)).toEqual(expect.arrayContaining([
      "state", "start", "end", "choice", "fork", "join"
    ]));
    expect(flattenMermaidStateNodes(current).some((node) => Boolean(node.childScope))).toBe(true);

    await fireEvent.click(view.getByRole("button", { name: "新增 Note" }));
    current = updates(view.emitted).at(-1)!;
    await view.rerender({ modelValue: current });
    expect(current.root.regions[0]?.notes.at(-1)).toMatchObject({ placement: "right" });

    await fireEvent.click(view.getByRole("button", { name: "新增并发区域" }));
    expect(updates(view.emitted).at(-1)?.root.regions).toHaveLength(2);
  });

  it("属性栏编辑状态 ID、名称、多行说明和允许的三类颜色", async () => {
    const view = render(StateVisualEditor, { props: { modelValue: stateDiagram() } });
    await fireEvent.click(view.getByLabelText("状态 Idle"));
    const panel = view.getByRole("region", { name: "状态属性" });

    await fireEvent.update(within(panel).getByLabelText("状态 ID"), "Ready");
    await fireEvent.update(within(panel).getByLabelText("状态名称"), "就绪");
    await fireEvent.update(within(panel).getByLabelText("状态说明"), "第一行\n第二行");
    await fireEvent.update(within(panel).getByLabelText("文字颜色"), "#123456");
    await fireEvent.update(within(panel).getByLabelText("填充颜色"), "#abcdef");
    await fireEvent.update(within(panel).getByLabelText("边框颜色"), "#654321");

    const changed = updates(view.emitted).at(-1)!;
    const ready = flattenMermaidStateNodes(changed).find((node) => node.id === "Ready");
    expect(ready).toMatchObject({
      label: "就绪",
      descriptions: ["第一行", "第二行"],
      style: { textColor: "#123456", fillColor: "#ABCDEF", strokeColor: "#654321" }
    });
    expect(changed.root.regions[0]?.transitions.some((transition) => transition.source === "Ready")).toBe(true);
    expect(changed.root.regions[0]?.notes[0]?.target).toBe("Ready");
  });

  it("方向和 Note 属性可编辑，Note 不暴露颜色控件", async () => {
    const view = render(StateVisualEditor, { props: { modelValue: stateDiagram() } });
    await fireEvent.update(view.getByLabelText("当前层级方向"), "RL");
    expect(updates(view.emitted).at(-1)?.root.direction).toBe("RL");

    await fireEvent.click(view.getByLabelText("Note 可以启动"));
    const panel = view.getByRole("region", { name: "Note 属性" });
    await fireEvent.update(within(panel).getByLabelText("Note 位置"), "left");
    await fireEvent.update(within(panel).getByLabelText("Note 内容"), "第一行\n第二行");
    expect(updates(view.emitted).at(-1)?.root.regions[0]?.notes[0]).toMatchObject({
      placement: "left",
      text: "第一行\n第二行"
    });
    expect(within(panel).queryByLabelText("填充颜色")).toBeNull();
  });

  it("Choice/Fork/Join 只提供填充与边框，开始/结束不提供颜色", async () => {
    const diagram = stateDiagram();
    diagram.root.regions[0]!.nodes.push(
      { id: "C", kind: "choice", label: "C", descriptions: [], position: { x: 0, y: 0 } },
      { id: "S", kind: "start", label: "[*]", descriptions: [], position: { x: 0, y: 0 } }
    );
    const view = render(StateVisualEditor, { props: { modelValue: diagram } });

    await fireEvent.click(view.getByLabelText("Choice C"));
    expect(view.getByLabelText("填充颜色")).toBeTruthy();
    expect(view.getByLabelText("边框颜色")).toBeTruthy();
    expect(view.queryByLabelText("文字颜色")).toBeNull();

    await fireEvent.click(view.getAllByLabelText("开始节点").at(-1)!);
    expect(view.queryByLabelText("填充颜色")).toBeNull();
    expect(view.queryByLabelText("边框颜色")).toBeNull();
  });

  it("转换标签可就地编辑，且属性区不提供转换颜色", async () => {
    const view = render(StateVisualEditor, { props: { modelValue: stateDiagram() } });
    await fireEvent.dblClick(view.getByLabelText("转换 Idle 到 Running：启动"));
    const editor = document.body.querySelector(".ta-state-inline-editor") as HTMLElement;
    expect(editor).toBeTruthy();
    await fireEvent.update(within(editor).getByLabelText("转换标签"), "执行");
    await fireEvent.keyDown(within(editor).getByLabelText("转换标签"), { key: "Enter", ctrlKey: true });
    expect(updates(view.emitted).at(-1)?.root.regions[0]?.transitions.find((transition) => transition.source === "Idle" && transition.target === "Running")?.label)
      .toBe("执行");
    expect(view.queryByLabelText("转换线颜色")).toBeNull();
  });

  it("元素库支持拖放创建并保持当前聚焦 Region", async () => {
    const view = render(StateVisualEditor, { props: { modelValue: stateDiagram() } });
    const beforeChoiceCount = flattenMermaidStateNodes(stateDiagram()).filter((node) => node.kind === "choice").length;
    const transfer = {
      value: "",
      effectAllowed: "none",
      setData(_format: string, value: string) { this.value = value; },
      getData() { return this.value; }
    };
    await fireEvent.dragStart(view.getByRole("button", { name: "新增 Choice" }), { dataTransfer: transfer });
    const dropEvent = new Event("drop", { bubbles: true, cancelable: true });
    Object.defineProperties(dropEvent, {
      clientX: { value: 220 },
      clientY: { value: 180 },
      dataTransfer: { value: transfer }
    });
    fireEvent(view.getByLabelText("State diagram 可视化画布"), dropEvent);
    const changed = updates(view.emitted).at(-1)!;
    expect(flattenMermaidStateNodes(changed).filter((node) => node.kind === "choice")).toHaveLength(beforeChoiceCount + 1);
    expect(changed.root.regions[0]?.nodes.at(-1)).toMatchObject({
      kind: "choice",
      position: { x: 220, y: 180 }
    });
  });

  it("状态连接点发出屏幕坐标起线请求", () => {
    const view = render(StateNode, {
      props: {
        id: "A",
        data: {
          node: { id: "A", kind: "state", label: "A", descriptions: [], position: { x: 0, y: 0 } },
          direction: "LR"
        }
      }
    });
    const handle = view.container.querySelector<HTMLElement>('[data-mermaid-handle="source-1"]')!;
    Object.defineProperty(handle, "getBoundingClientRect", {
      configurable: true,
      value: () => ({ left: 100, top: 50, right: 110, bottom: 60, width: 10, height: 10 })
    });
    const event = new MouseEvent("pointerdown", { bubbles: true, cancelable: true, button: 0 });
    Object.defineProperty(event, "pointerId", { value: 9 });
    handle.dispatchEvent(event);

    const calls = view.emitted().connectionStart as Array<[Record<string, unknown>]>;
    expect(calls[0]?.[0]).toMatchObject({
      pointerId: 9,
      nodeId: "A",
      handleId: "source-1",
      position: "right",
      point: { x: 105, y: 55 }
    });
  });

  it("选中转换可分别发出起点和终点重连请求", async () => {
    const view = render(StateTransitionEdge, {
      props: {
        id: "transition-1",
        source: "A",
        target: "B",
        sourceX: 10,
        sourceY: 20,
        targetX: 180,
        targetY: 90,
        sourcePosition: "right" as never,
        targetPosition: "left" as never,
        sourceHandleId: "source-1",
        targetHandleId: "target-3",
        selected: true,
        data: { transitionId: "transition-1" }
      }
    });

    await fireEvent.pointerDown(view.getByLabelText("拖动状态转换终点重连"), { button: 0, pointerId: 12 });
    await fireEvent.pointerDown(view.getByLabelText("拖动状态转换起点重连"), { button: 0, pointerId: 13 });
    const calls = view.emitted().reconnectStart as Array<[Record<string, unknown>]>;
    expect(calls[0]?.[0]).toMatchObject({
      transitionId: "transition-1",
      end: "target",
      fixedNodeId: "A",
      fixedHandleId: "source-1",
      fixedPosition: "right"
    });
    expect(calls[1]?.[0]).toMatchObject({
      transitionId: "transition-1",
      end: "source",
      fixedNodeId: "B",
      fixedHandleId: "target-3",
      fixedPosition: "left"
    });
  });
});
