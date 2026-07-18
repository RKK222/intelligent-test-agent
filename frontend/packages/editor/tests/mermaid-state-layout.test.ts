import { describe, expect, it } from "vitest";
import {
  autoLayoutMermaidState,
  getMermaidStateNodeSize,
  layoutMermaidStateRegions
} from "../src/mermaid/state/layout";
import { flattenMermaidStateNodes } from "../src/mermaid/state/model";
import { parseMermaidState } from "../src/mermaid/state/parser";

describe("Mermaid State Diagram 层级布局", () => {
  it("递归为根层和复合状态的每个 Region 生成局部坐标且不修改输入", async () => {
    const diagram = parseMermaidState(`stateDiagram-v2
direction TB
[*] --> Running
state Running {
  direction LR
  [*] --> A
  A --> [*]
  --
  [*] --> B
  B --> [*]
}
Running --> [*]`);
    const laidOut = await autoLayoutMermaidState(diagram);

    expect(laidOut).not.toBe(diagram);
    expect(flattenMermaidStateNodes(diagram).every((node) => node.position.x === 0 && node.position.y === 0)).toBe(true);
    expect(flattenMermaidStateNodes(laidOut).every((node) => node.position.x >= 24 && node.position.y >= 24)).toBe(true);
    const child = flattenMermaidStateNodes(laidOut).find((node) => node.id === "Running")?.childScope;
    expect(child?.regions).toHaveLength(2);
    expect(child?.regions.every((region) => region.nodes.some((node) => node.position.x > 0))).toBe(true);
  });

  it("TB/BT 横向打包并发 Region，LR/RL 纵向打包", () => {
    const diagram = parseMermaidState(`stateDiagram-v2
state Parent {
  [*] --> A
  A --> [*]
  --
  [*] --> B
  B --> [*]
}`);
    const scope = flattenMermaidStateNodes(diagram).find((node) => node.id === "Parent")!.childScope!;
    scope.regions[0]!.nodes[0]!.position = { x: 30, y: 40 };
    scope.regions[1]!.nodes[0]!.position = { x: 30, y: 40 };

    scope.direction = "TB";
    const horizontal = layoutMermaidStateRegions(scope);
    expect(horizontal[1]!.x).toBeGreaterThan(horizontal[0]!.x + horizontal[0]!.width);
    expect(horizontal[1]!.y).toBe(horizontal[0]!.y);

    scope.direction = "LR";
    const vertical = layoutMermaidStateRegions(scope);
    expect(vertical[1]!.y).toBeGreaterThan(vertical[0]!.y + vertical[0]!.height);
    expect(vertical[1]!.x).toBe(vertical[0]!.x);
  });

  it("Fork/Join 随流向旋转，复合状态使用摘要尺寸而非 Flow 缩放", () => {
    const diagram = parseMermaidState(`stateDiagram-v2
state F <<fork>>
state J <<join>>
state Parent {
  [*] --> A
  A --> [*]
}`);
    const [fork, join, parent] = ["F", "J", "Parent"].map((id) =>
      flattenMermaidStateNodes(diagram).find((node) => node.id === id)!
    );

    expect(getMermaidStateNodeSize(fork, "TB")).toEqual({ width: 112, height: 16 });
    expect(getMermaidStateNodeSize(join, "LR")).toEqual({ width: 16, height: 112 });
    expect(getMermaidStateNodeSize(parent, "TB").height).toBeGreaterThan(60);
  });
});
