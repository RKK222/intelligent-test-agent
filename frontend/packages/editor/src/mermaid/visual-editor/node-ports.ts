import { Position } from "@vue-flow/core";
import type { MermaidGraph } from "../model";

export const MERMAID_NODE_PORT_COUNT = 3;

const PORT_OFFSETS = [25, 50, 75] as const;

export type MermaidNodePortType = "target" | "source";

export type MermaidNodePortLayout = {
  target: Position;
  source: Position;
  offsets: typeof PORT_OFFSETS;
};

/** 端口所在边跟随图方向；无法识别的方向按 Mermaid 默认的从上到下处理。 */
export function getMermaidNodePortLayout(direction: MermaidGraph["direction"]): MermaidNodePortLayout {
  if (direction === "LR") return { target: Position.Left, source: Position.Right, offsets: PORT_OFFSETS };
  if (direction === "RL") return { target: Position.Right, source: Position.Left, offsets: PORT_OFFSETS };
  if (direction === "BT") return { target: Position.Bottom, source: Position.Top, offsets: PORT_OFFSETS };
  return { target: Position.Top, source: Position.Bottom, offsets: PORT_OFFSETS };
}

/** 超过三个端口时稳定循环复用，避免把可视化端口信息写进 Mermaid 文本。 */
export function getMermaidNodePortId(type: MermaidNodePortType, edgeIndex: number): string {
  return `${type}-${edgeIndex % MERMAID_NODE_PORT_COUNT}`;
}
