import { describe, expect, it } from "vitest";
import { parseMermaidDiagram, serializeMermaidDiagram } from "../src/mermaid/diagram";
import {
  cloneMermaidStateDiagram,
  flattenMermaidStateNodes,
  flattenMermaidStateScopes
} from "../src/mermaid/state/model";
import { parseMermaidState } from "../src/mermaid/state/parser";
import { serializeMermaidState } from "../src/mermaid/state/serializer";
import {
  getMermaidStateConnectionInvalidReason,
  validateMermaidState
} from "../src/mermaid/state/validator";

async function expectOfficialMermaidParse(source: string): Promise<void> {
  const css = window.CSS as { supports?: (property: string, value?: string) => boolean };
  const originalSupports = css.supports;
  if (!originalSupports) Object.defineProperty(css, "supports", { configurable: true, value: () => true });
  try {
    const mermaid = (await import("mermaid")).default;
    await expect(mermaid.parse(source)).resolves.toBeTruthy();
  } finally {
    if (originalSupports) Object.defineProperty(css, "supports", { configurable: true, value: originalSupports });
    else delete css.supports;
  }
}

const completeSource = `stateDiagram-v2
direction LR
[*] --> Idle
state "空闲" as Idle
Idle: 等待任务
Idle --> Idle: 重试
Idle --> Decision: 开始
state Decision <<choice>>
Decision --> Forker: [并行]
Decision --> Cancelled: [取消]
state Forker <<fork>>
Forker --> WorkerA: A
Forker --> WorkerB: B
WorkerA --> Joiner
WorkerB --> Joiner
state Joiner <<join>>
Joiner --> Completed
Cancelled --> [*]
Completed --> [*]
note right of Idle
  可接收任务
  支持重试
end note
style Idle fill:#abc,stroke:#123456,color:#fff
style Decision fill:#FEDCBA,stroke:#135
classDef legacy fill:rgba(1,2,3,.4)
class Idle legacy`;

describe("Mermaid State Diagram 领域层", () => {
  it("解析普通状态、说明、自循环、标签转换、Choice、Fork/Join、Note、方向和样式", async () => {
    const diagram = parseMermaidState(completeSource);
    const nodes = flattenMermaidStateNodes(diagram);
    const region = diagram.root.regions[0]!;

    expect(diagram.kind).toBe("stateDiagram");
    expect(diagram.header).toBe("stateDiagram-v2");
    expect(diagram.root.direction).toBe("LR");
    expect(nodes.map((node) => node.kind)).toEqual(expect.arrayContaining([
      "start", "end", "state", "choice", "fork", "join"
    ]));
    expect(nodes.find((node) => node.id === "Idle")).toMatchObject({
      label: "空闲",
      descriptions: ["等待任务"],
      style: { fillColor: "#AABBCC", strokeColor: "#123456", textColor: "#FFFFFF" }
    });
    expect(nodes.find((node) => node.id === "Decision")?.style).toEqual({
      fillColor: "#FEDCBA",
      strokeColor: "#113355"
    });
    expect(region.transitions).toEqual(expect.arrayContaining([
      expect.objectContaining({ source: "Idle", target: "Idle", label: "重试" }),
      expect.objectContaining({ source: "Decision", target: "Forker", label: "[并行]" })
    ]));
    expect(region.notes[0]).toMatchObject({ target: "Idle", placement: "right", text: "可接收任务\n支持重试" });
    expect(diagram.root.preservedLines).toEqual(expect.arrayContaining([
      "classDef legacy fill:rgba(1,2,3,.4)",
      "class Idle legacy"
    ]));
    expect(validateMermaidState(diagram)).toEqual([]);
    await expectOfficialMermaidParse(serializeMermaidState(diagram));
  });

  it("递归解析复合、嵌套状态和并发 Region，并保留每层方向", async () => {
    const source = `stateDiagram
[*] --> Running
state "运行中" as Running {
  direction TB
  [*] --> Frontend
  state Frontend {
    direction LR
    [*] --> Render
    Render --> [*]
  }
  Frontend --> [*]
  --
  [*] --> Backend
  Backend --> [*]
}
Running --> [*]`;
    const diagram = parseMermaidState(source);
    const running = flattenMermaidStateNodes(diagram).find((node) => node.id === "Running");
    const frontend = flattenMermaidStateNodes(diagram).find((node) => node.id === "Frontend");

    expect(running).toMatchObject({ kind: "state", label: "运行中" });
    expect(running?.childScope?.direction).toBe("TB");
    expect(running?.childScope?.regions).toHaveLength(2);
    expect(frontend?.childScope?.direction).toBe("LR");
    expect(flattenMermaidStateScopes(diagram)).toHaveLength(3);
    expect(validateMermaidState(diagram)).toEqual([]);
    await expectOfficialMermaidParse(serializeMermaidState(diagram));
  });

  it("同源同目标的多条标签转换保持独立", () => {
    const diagram = parseMermaidState(`stateDiagram-v2
[*] --> A
A --> B: success
A --> B: retry
B --> [*]`);

    expect(diagram.root.regions[0]?.transitions.filter(({ source, target }) => source === "A" && target === "B"))
      .toMatchObject([{ label: "success" }, { label: "retry" }]);
    expect(validateMermaidState(diagram)).toEqual([]);
    expect(serializeMermaidState(diagram)).toContain("A --> B: retry");
  });

  it("每次出现 [*] 都分配独立稳定 ID，并要求结束节点单入边", () => {
    const diagram = parseMermaidState(`stateDiagram-v2
[*] --> A
A --> [*]
[*] --> B
B --> [*]`);
    const pseudoNodes = flattenMermaidStateNodes(diagram).filter((node) => node.kind === "start" || node.kind === "end");

    expect(new Set(pseudoNodes.map((node) => node.id)).size).toBe(4);
    expect(pseudoNodes.map((node) => node.kind)).toEqual(["start", "end", "start", "end"]);
    expect(validateMermaidState(diagram)).toEqual([]);
  });

  it("校验跨 Region、非法自循环、空 Region、Note 目标和伪状态基数", () => {
    const diagram = parseMermaidState(`stateDiagram-v2
state Parent {
  [*] --> A
  A --> Choice
  state Choice <<choice>>
  Choice --> Choice: illegal
  --
  B --> [*]
}
note left of Choice: bad target`);
    const child = flattenMermaidStateNodes(diagram).find((node) => node.id === "Parent")?.childScope;
    if (!child) throw new Error("缺少复合状态");
    child.regions[0]!.transitions.push({
      id: "cross",
      source: "A",
      target: "B",
      label: "跨区域"
    });
    child.regions.push({ id: "empty", nodes: [], transitions: [], notes: [] });

    const codes = validateMermaidState(diagram).map((issue) => issue.code);
    expect(codes).toEqual(expect.arrayContaining([
      "cross-region-transition",
      "invalid-self-loop",
      "empty-region",
      "invalid-note-target",
      "choice-cardinality"
    ]));
    expect(() => serializeMermaidState(diagram)).toThrow("状态图校验失败");
  });

  it("连接阶段阻止跨区域和伪状态超限，但允许同端点多转换与普通状态自循环", () => {
    const diagram = parseMermaidState(`stateDiagram-v2
[*] --> A
A --> B: success
A --> B: retry
B --> [*]`);
    const region = diagram.root.regions[0]!;
    const [start, end] = ["start", "end"].map((kind) => region.nodes.find((node) => node.kind === kind)!);

    expect(getMermaidStateConnectionInvalidReason(diagram, region.id, { source: "A", target: "B" })).toBeUndefined();
    expect(getMermaidStateConnectionInvalidReason(diagram, region.id, { source: "A", target: "A" })).toBeUndefined();
    expect(getMermaidStateConnectionInvalidReason(diagram, region.id, { source: start.id, target: "B" })).toContain("一条出边");
    expect(getMermaidStateConnectionInvalidReason(diagram, region.id, { source: "B", target: start.id })).toContain("开始节点");
    expect(getMermaidStateConnectionInvalidReason(diagram, region.id, { source: end.id, target: "A" })).toContain("结束节点");
    expect(getMermaidStateConnectionInvalidReason(diagram, region.id, { source: "A", target: end.id })).toContain("一条入边");

    region.nodes.push({ id: "C", kind: "choice", label: "C", descriptions: [], position: { x: 0, y: 0 } });
    region.transitions.push({ id: "choice-in", source: "A", target: "C", label: "" });
    expect(getMermaidStateConnectionInvalidReason(diagram, region.id, { source: "B", target: "C" })).toContain("Choice");
    expect(getMermaidStateConnectionInvalidReason(diagram, region.id, { source: "C", target: "C" })).toContain("自循环");

    diagram.root.regions.push({
      id: "root-region-2",
      nodes: [{ id: "Other", kind: "state", label: "Other", descriptions: [], position: { x: 0, y: 0 } }],
      transitions: [],
      notes: []
    });
    expect(getMermaidStateConnectionInvalidReason(diagram, region.id, { source: "A", target: "Other" })).toContain("跨复合状态或并发区域");
  });

  it("未修改源码保持原头部、CRLF、注释与非结构指令，修改后仍原样保留指令", () => {
    const source = "  stateDiagram-v2\r\n%% comment\r\naccTitle: 状态图\r\n[*] --> A\r\nA --> [*]\r\nclassDef old fill:red\r\n";
    const diagram = parseMermaidState(source);
    expect(serializeMermaidState(diagram)).toBe(source);

    const state = flattenMermaidStateNodes(diagram).find((node) => node.id === "A");
    if (!state) throw new Error("缺少状态 A");
    state.label = "已修改";
    const changed = serializeMermaidState(diagram);
    expect(changed.startsWith("  stateDiagram-v2\r\n")).toBe(true);
    expect(changed).toContain("%% comment");
    expect(changed).toContain("accTitle: 状态图");
    expect(changed).toContain("classDef old fill:red");
  });

  it("按 Scope 原样保留带花括号的注释与多行可访问性说明", async () => {
    const source = `stateDiagram-v2
%% comment { keep }
accDescr {
  状态机的多行说明
}
[*] --> A
A --> [*]`;
    const diagram = parseMermaidState(source);
    flattenMermaidStateNodes(diagram).find((node) => node.id === "A")!.label = "已修改";
    const changed = serializeMermaidState(diagram);

    expect(changed).toContain("%% comment { keep }");
    expect(changed).toContain("accDescr {\n  状态机的多行说明\n}");
    expect(changed).toContain('state "已修改" as A');
    await expectOfficialMermaidParse(changed);
  });

  it("遇到无法安全映射的结构语法时拒绝构造可视化模型", () => {
    expect(() => parseMermaidState(`stateDiagram-v2
state A {
  state B:::unsupported
}`)).toThrow("无法安全映射");
  });

  it("紧凑 metadata 往返局部坐标和转换端口，拓扑变化后安全回退", () => {
    const diagram = parseMermaidState(`stateDiagram-v2
[*] --> A
A --> [*]`);
    const nodes = flattenMermaidStateNodes(diagram);
    nodes.forEach((node, index) => { node.position = { x: 45.5 + index * 80, y: 70 + index * 35.2 }; });
    diagram.root.regions[0]!.transitions[0]!.sourceHandle = "source-1";
    diagram.root.regions[0]!.transitions[0]!.targetHandle = "target-3";

    const serialized = serializeMermaidState(diagram);
    expect(serialized).toMatch(/^stateDiagram-v2\n%%@[A-Za-z0-9_-]+/);
    const reparsed = parseMermaidState(serialized);
    expect(Object.fromEntries(flattenMermaidStateNodes(reparsed).map((node) => [node.id, node.position])))
      .toEqual(Object.fromEntries(nodes.map((node) => [node.id, node.position])));
    expect(reparsed.root.regions[0]!.transitions[0]).toMatchObject({
      sourceHandle: "source-1",
      targetHandle: "target-3"
    });

    const stale = serialized.replace("A --> [*]", "A --> B\nB --> [*]");
    const staleParsed = parseMermaidState(stale);
    expect(flattenMermaidStateNodes(staleParsed).every((node) => node.position.x === 0 && node.position.y === 0)).toBe(true);
    expect(staleParsed.root.preservedLines.some((line) => line.startsWith("%%@"))).toBe(true);
  });

  it("损坏 metadata 原样保留且后续保存不制造第二个 marker", () => {
    const diagram = parseMermaidState(`stateDiagram-v2
[*] --> A
A --> [*]`);
    flattenMermaidStateNodes(diagram)[0]!.position = { x: 90, y: 80 };
    const serialized = serializeMermaidState(diagram);
    const damaged = serialized.replace(/(%%@[A-Za-z0-9_-]*)([A-Za-z0-9_-])/, (_all, prefix, last) => `${prefix}${last === "A" ? "B" : "A"}`);
    const reparsed = parseMermaidState(damaged);
    flattenMermaidStateNodes(reparsed).find((node) => node.id === "A")!.label = "Changed";
    const rewritten = serializeMermaidState(reparsed);

    expect(rewritten.split("\n").filter((line) => line.startsWith("%%@")).length).toBe(1);
    expect(rewritten).toContain("state \"Changed\" as A");
  });

  it("损坏的多行 metadata 保持原始续行顺序且不追加冲突 marker", () => {
    const diagram = parseMermaidState(`stateDiagram-v2
[*] --> A
A --> [*]`);
    const region = diagram.root.regions[0]!;
    for (let index = 0; index < 100; index += 1) {
      region.nodes.push({
        id: `Extra${index}`,
        kind: "state",
        label: `Extra${index}`,
        descriptions: [],
        position: { x: 100 + index * 17.3, y: 80 + index * 11.7 }
      });
    }
    const serialized = serializeMermaidState(diagram);
    const markerLines = serialized.split("\n").filter((line) => line.startsWith("%%@"));
    expect(markerLines.length).toBeGreaterThan(1);
    const damagedFirst = markerLines[0]!.replace(/.$/, (last) => last === "A" ? "B" : "A");
    const damaged = serialized.replace(markerLines[0]!, damagedFirst);
    const reparsed = parseMermaidState(damaged);
    flattenMermaidStateNodes(reparsed).find((node) => node.id === "A")!.label = "Changed";
    const rewrittenMarkers = serializeMermaidState(reparsed)
      .split("\n")
      .filter((line) => line.startsWith("%%@"));

    expect(rewrittenMarkers).toEqual([damagedFirst, ...markerLines.slice(1)]);
  });

  it("克隆完全隔离递归节点、Note、转换和样式", () => {
    const original = parseMermaidState(completeSource);
    const cloned = cloneMermaidStateDiagram(original);
    const clonedIdle = flattenMermaidStateNodes(cloned).find((node) => node.id === "Idle")!;
    clonedIdle.label = "Changed";
    clonedIdle.style!.fillColor = "#000000";
    cloned.root.regions[0]!.notes[0]!.text = "Changed";
    cloned.root.regions[0]!.transitions[0]!.label = "Changed";

    expect(flattenMermaidStateNodes(original).find((node) => node.id === "Idle")?.label).toBe("空闲");
    expect(original.root.regions[0]?.notes[0]?.text).toBe("可接收任务\n支持重试");
    expect(original.root.regions[0]?.transitions[0]?.label).not.toBe("Changed");
  });

  it("通用入口分发 State Diagram 且不改变其他图类型错误边界", () => {
    expect(parseMermaidDiagram("stateDiagram\n[*] --> A\nA --> [*]").kind).toBe("stateDiagram");
    expect(parseMermaidDiagram("stateDiagram-v2\n[*] --> A\nA --> [*]").kind).toBe("stateDiagram");
    expect(serializeMermaidDiagram(parseMermaidDiagram("stateDiagram-v2\n[*] --> A\nA --> [*]")))
      .toContain("stateDiagram-v2");
    expect(() => parseMermaidDiagram("classDiagram\nA <|-- B")).toThrow(
      "仅支持 flowchart、graph、sequenceDiagram 或 stateDiagram"
    );
  });
});
