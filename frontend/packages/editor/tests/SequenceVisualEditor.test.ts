import { defineComponent } from "vue";
import { fireEvent, render } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { flattenMermaidSequenceStatements, type MermaidSequenceDiagram } from "../src/mermaid/sequence/model";
import { parseMermaidSequence } from "../src/mermaid/sequence/parser";

vi.mock("@vue-flow/core", () => ({
  VueFlow: defineComponent({
    props: ["nodes", "edges"],
    emits: ["init"],
    mounted() {
      this.$emit("init", { fitView: vi.fn() });
    },
    template: `<div data-testid="sequence-flow-mock" :data-node-count="nodes.length" :data-edge-count="edges.length">
      <slot name="node-sequence-scene" v-bind="nodes[0]" />
    </div>`
  })
}));

import SequenceVisualEditor from "../src/mermaid/sequence/visual-editor/SequenceVisualEditor.vue";

function sequence(): MermaidSequenceDiagram {
  return parseMermaidSequence(`sequenceDiagram
actor U as 用户
participant S as 服务
U->>+S: 请求
alt 成功
  S-->>U: 响应
else 失败
  Note over U,S: 错误说明
end`);
}

function updates(emitted: ReturnType<typeof render>["emitted"]): MermaidSequenceDiagram[] {
  return ((emitted()["update:modelValue"] ?? []) as Array<[MermaidSequenceDiagram]>).map(([diagram]) => diagram);
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("SequenceVisualEditor 专用时序场景", () => {
  it("Vue Flow 只承载一个场景节点，右侧使用元素/结构/属性三标签", () => {
    const { getByTestId, getByRole } = render(SequenceVisualEditor, { props: { modelValue: sequence() } });

    expect(getByTestId("sequence-flow-mock").getAttribute("data-node-count")).toBe("1");
    expect(getByTestId("sequence-flow-mock").getAttribute("data-edge-count")).toBe("0");
    expect(getByRole("tab", { name: "元素" })).toBeTruthy();
    expect(getByRole("tab", { name: "结构" })).toBeTruthy();
    expect(getByRole("tab", { name: "属性" })).toBeTruthy();
  });

  it("元素库创建 8 类参与者、消息、Note 和组合片段", async () => {
    const { getByRole, emitted, rerender } = render(SequenceVisualEditor, { props: { modelValue: sequence() } });

    await fireEvent.click(getByRole("button", { name: "新增 control" }));
    const participantUpdate = updates(emitted).at(-1);
    expect(participantUpdate?.participants.at(-1)).toMatchObject({ type: "control", text: "新控制器" });
    if (participantUpdate) await rerender({ modelValue: participantUpdate });
    await fireEvent.click(getByRole("tab", { name: "元素" }));

    await fireEvent.click(getByRole("button", { name: "新增消息" }));
    const messageUpdate = updates(emitted).at(-1);
    expect(messageUpdate?.messages.at(-1)).toMatchObject({ kind: "message", arrow: "->>" });
    if (messageUpdate) await rerender({ modelValue: messageUpdate });
    await fireEvent.click(getByRole("tab", { name: "元素" }));

    await fireEvent.click(getByRole("button", { name: "新增 Note" }));
    const noteUpdate = updates(emitted).at(-1);
    expect(flattenMermaidSequenceStatements(noteUpdate?.statements ?? []).at(-1)?.kind).toBe("note");
    if (noteUpdate) await rerender({ modelValue: noteUpdate });
    await fireEvent.click(getByRole("tab", { name: "元素" }));

    await fireEvent.click(getByRole("button", { name: "新增 alt 片段" }));
    const blockUpdate = updates(emitted).at(-1);
    expect(blockUpdate?.statements.at(-1)).toMatchObject({ kind: "block", blockType: "alt" });
  });

  it("画布选择自动切到属性，并可编辑消息全部箭头、端点和多行文本", async () => {
    const { getByLabelText, getByRole, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: sequence() }
    });

    await fireEvent.click(getByLabelText("选择消息 请求"));
    expect(getByRole("tab", { name: "属性" }).getAttribute("aria-selected")).toBe("true");
    const arrow = getByLabelText("消息箭头") as HTMLSelectElement;
    expect([...arrow.options].map((option) => option.value)).toEqual([
      "->", "-->", "->>", "-->>", "<<->>", "<<-->>", "-x", "--x", "-)", "--)"
    ]);

    await fireEvent.update(getByLabelText("消息文本"), "第一行\n第二行");
    await fireEvent.update(getByLabelText("消息箭头"), "-)");
    await fireEvent.update(getByLabelText("消息目标"), "U");
    const changed = updates(emitted);
    expect(changed.some((diagram) => diagram.messages[0]?.text === "第一行\n第二行")).toBe(true);
    expect(changed.some((diagram) => diagram.messages[0]?.arrow === "-)")).toBe(true);
    expect(changed.some((diagram) => diagram.messages[0]?.target === "U")).toBe(true);
  });

  it("Note、分支和 box 属性保持 Mermaid 语义有效", async () => {
    const { getByLabelText, getByRole, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: sequence() }
    });

    await fireEvent.click(getByLabelText("选择 Note 错误说明"));
    await fireEvent.update(getByLabelText("Note 位置"), "right");
    const noteChanged = updates(emitted).at(-1);
    const note = flattenMermaidSequenceStatements(noteChanged?.statements ?? []).find((statement) => statement.kind === "note");
    expect(note).toMatchObject({ kind: "note", placement: "right", participants: ["U"] });

    await fireEvent.click(getByLabelText("选择 alt 片段"));
    await fireEvent.update(getByLabelText("片段分支标题 else"), "异常");
    const branchChanged = updates(emitted).at(-1)?.statements.find((statement) => statement.kind === "block");
    expect(branchChanged?.kind === "block" && branchChanged.branches[1]?.label).toBe("异常");

    await fireEvent.click(getByRole("tab", { name: "元素" }));
    await fireEvent.click(getByRole("button", { name: "新增 box 分组" }));
    await fireEvent.update(getByLabelText("box 标题"), "基础设施");
    await fireEvent.update(getByLabelText("box 颜色"), "#dbeafe");
    expect(updates(emitted).at(-1)?.groups.at(-1)).toMatchObject({ label: "基础设施", color: "#dbeafe" });
  });

  it("双击画布元素可就地编辑文本，Ctrl+Enter 提交到递归模型", async () => {
    const { getByLabelText, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: sequence() }
    });

    await fireEvent.dblClick(getByLabelText("选择消息 请求"));
    const inline = getByLabelText("就地编辑文本");
    await fireEvent.update(inline, "就地修改");
    await fireEvent.keyDown(inline, { key: "Enter", ctrlKey: true });

    expect(updates(emitted).at(-1)?.messages[0]?.text).toBe("就地修改");
  });

  it("画布支持生命线拖建消息、端点重绑和参与者横向拖序", async () => {
    const { getByLabelText, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: sequence() }
    });
    const scene = getByLabelText("Sequence diagram 结构化场景");

    await fireEvent.pointerDown(getByLabelText("从 用户 生命线创建消息"), { button: 0, clientX: 124, clientY: 150 });
    await fireEvent.pointerUp(scene, { clientX: 314, clientY: 170 });
    expect(updates(emitted).at(-1)?.messages.at(-1)).toMatchObject({ source: "U", target: "S" });

    await fireEvent.click(getByLabelText("选择消息 请求"));
    await fireEvent.pointerDown(getByLabelText("拖动消息来源 请求"), { button: 0, clientX: 124, clientY: 150 });
    await fireEvent.pointerUp(scene, { clientX: 314, clientY: 150 });
    expect(updates(emitted).at(-1)?.messages[0]).toMatchObject({ source: "S", target: "S" });

    await fireEvent.pointerDown(getByLabelText("选择参与者 用户"), { button: 0, clientX: 124, clientY: 50 });
    await fireEvent.pointerUp(scene, { clientX: 314, clientY: 50 });
    expect(updates(emitted).at(-1)?.participants.map((participant) => participant.id)).toEqual(["S", "U"]);
  });

  it("元素库可拖放到画布创建，显式停用可从画布选中", async () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
activate A
A->>B: call
deactivate A`);
    const { getByLabelText, getByRole, emitted } = render(SequenceVisualEditor, { props: { modelValue: diagram } });
    const transfer = {
      value: "",
      effectAllowed: "none",
      setData(_format: string, value: string) { this.value = value; },
      getData() { return this.value; }
    };

    await fireEvent.dragStart(getByRole("button", { name: "新增 Note" }), { dataTransfer: transfer });
    await fireEvent.drop(getByLabelText("Sequence diagram 可视化画布"), { dataTransfer: transfer });
    expect(flattenMermaidSequenceStatements(updates(emitted).at(-1)?.statements ?? []).at(-1)?.kind).toBe("note");

    await fireEvent.click(getByLabelText("选择 deactivate A"));
    expect(getByLabelText("激活状态")).toBeTruthy();
  });

  it("结构树表达嵌套分支，支持键盘排序并锁定高级语句", async () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
A->>B: before
A()B: locked
alt branch
  A->>B: nested
else fallback
end
A->>B: after`);
    const { getByRole, getByText, getByLabelText, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: diagram }
    });
    await fireEvent.click(getByRole("tab", { name: "结构" }));

    expect(getByText("alt · branch")).toBeTruthy();
    expect(getByText("else · fallback")).toBeTruthy();
    expect(getByText("中央连接创建与结构化编辑", { exact: false, selector: "span" })).toBeTruthy();
    expect(getByLabelText("移动 locked").getAttribute("aria-disabled")).toBe("true");

    await fireEvent.keyDown(getByLabelText("选择 after"), { key: "ArrowUp", altKey: true });
    expect(updates(emitted).at(-1)?.statements.at(-1)).toMatchObject({ kind: "block" });
  });

  it("可选择嵌套空分支作为元素插入位置", async () => {
    const { getByRole, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: sequence() }
    });

    await fireEvent.click(getByRole("tab", { name: "结构" }));
    await fireEvent.click(getByRole("button", { name: "选择 else · 失败 作为插入位置" }));
    expect(getByRole("tab", { name: "元素" }).getAttribute("aria-selected")).toBe("true");
    await fireEvent.click(getByRole("button", { name: "新增消息" }));

    const changed = updates(emitted).at(-1);
    const block = changed?.statements.find((statement) => statement.kind === "block");
    expect(block?.kind === "block" && block.branches[1]?.statements.at(-1)).toMatchObject({
      kind: "message",
      text: "新消息"
    });
  });

  it("空分支接收结构树拖放并执行跨分支移动", async () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
alt 主流程
else 备用
end
A->>B: 待移动`);
    const messageId = diagram.messages[0]?.id;
    if (!messageId) throw new Error("缺少待移动消息");
    const { getByRole, getAllByText, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: diagram }
    });

    await fireEvent.click(getByRole("tab", { name: "结构" }));
    await fireEvent.drop(getAllByText("空分支")[1]!, {
      dataTransfer: {
        getData: (format: string) => format === "application/x-sequence-statement" ? messageId : ""
      }
    });

    const changed = updates(emitted).at(-1);
    const block = changed?.statements[0];
    expect(changed?.statements).toHaveLength(1);
    expect(block?.kind === "block" && block.branches[1]?.statements[0]).toMatchObject({ id: messageId });
  });

  it("删除被引用参与者先展示影响数，确认后原子级联并保留空分支", async () => {
    const confirm = vi.fn(() => true);
    vi.stubGlobal("confirm", confirm);
    const { getByLabelText, getByRole, emitted } = render(SequenceVisualEditor, {
      props: { modelValue: sequence() }
    });

    await fireEvent.click(getByLabelText("选择参与者 服务"));
    await fireEvent.click(getByRole("button", { name: "删除参与者" }));

    expect(confirm).toHaveBeenCalledWith(expect.stringContaining("3"));
    const deleted = updates(emitted).at(-1);
    expect(deleted?.participants.map((participant) => participant.id)).toEqual(["U"]);
    const block = deleted?.statements.find((statement) => statement.kind === "block");
    expect(block?.kind === "block" && block.branches[0]?.statements).toEqual([]);
    expect(block?.kind === "block" && block.branches[1]?.statements[0]).toMatchObject({
      kind: "note",
      participants: ["U"]
    });
  });

  it("属性栏支持参与者重命名、8 类类型、autonumber 和生命周期", async () => {
    const { getByLabelText, getByRole, emitted, rerender } = render(SequenceVisualEditor, {
      props: { modelValue: sequence() }
    });
    await fireEvent.click(getByLabelText("选择参与者 用户"));
    await fireEvent.update(getByLabelText("参与者 ID"), "Client");
    await fireEvent.update(getByLabelText("参与者类型"), "boundary");
    const renamed = updates(emitted).find((diagram) => diagram.participants.some((participant) => participant.id === "Client"));
    expect(renamed).toBeTruthy();
    expect(updates(emitted).some((diagram) => diagram.participants[0]?.type === "boundary")).toBe(true);
    if (renamed) await rerender({ modelValue: renamed });

    await fireEvent.click(getByRole("tab", { name: "元素" }));
    await fireEvent.click(getByRole("checkbox", { name: "自动编号" }));
    expect(updates(emitted).at(-1)?.autonumber).toMatchObject({ enabled: true });

    await fireEvent.click(getByRole("button", { name: "新增 create 生命周期" }));
    const lifecycle = updates(emitted).at(-1);
    expect(lifecycle?.messages.at(-1)?.create).toBeTruthy();
  });

  it("destroy 生命周期取消后重新启用仍保留目标端绑定", async () => {
    const diagram = parseMermaidSequence(`sequenceDiagram
participant A
participant B
destroy B
A->>B: bye`);
    const { getByLabelText, emitted } = render(SequenceVisualEditor, { props: { modelValue: diagram } });

    await fireEvent.click(getByLabelText("选择消息 bye"));
    await fireEvent.click(getByLabelText("destroy 生命周期"));
    await fireEvent.click(getByLabelText("destroy 生命周期"));

    expect(updates(emitted).at(-1)?.messages[0]?.destroy).toEqual({ participantId: "B" });
  });
});
