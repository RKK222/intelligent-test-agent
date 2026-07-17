import type { MermaidNodeType } from "./model";

export type MermaidNodeShapeGroup = "flowchart" | "document";

export type MermaidNodeShapeDefinition = {
  type: MermaidNodeType;
  label: string;
  group: MermaidNodeShapeGroup;
  modernShape: string;
  portCount: 8 | 12;
};

/**
 * Flowchart 图形的唯一公开目录。顺序即右侧图形库和属性下拉顺序，中文名称由用户约定；
 * modernShape 固定使用官方短名，避免同一轮廓因别名不同产生不稳定的保存结果。
 */
export const MERMAID_NODE_SHAPES = [
  { type: "stadium", label: "开始/结束", group: "flowchart", modernShape: "stadium", portCount: 8 },
  { type: "rectangle", label: "普通处理步骤", group: "flowchart", modernShape: "rect", portCount: 12 },
  { type: "rounded", label: "圆角处理节点", group: "flowchart", modernShape: "rounded", portCount: 8 },
  { type: "subroutine", label: "子程序", group: "flowchart", modernShape: "fr-rect", portCount: 12 },
  { type: "database", label: "数据库", group: "flowchart", modernShape: "cyl", portCount: 8 },
  { type: "circle", label: "连接点", group: "flowchart", modernShape: "circle", portCount: 8 },
  { type: "diamond", label: "条件判断", group: "flowchart", modernShape: "diam", portCount: 12 },
  { type: "hexagon", label: "准备步骤", group: "flowchart", modernShape: "hex", portCount: 12 },
  { type: "parallelogram", label: "输入或输出", group: "flowchart", modernShape: "lean-r", portCount: 12 },
  { type: "trapezoid", label: "人工处理", group: "flowchart", modernShape: "trap-b", portCount: 12 },
  { type: "double-circle", label: "终止节点", group: "flowchart", modernShape: "dbl-circ", portCount: 8 },
  { type: "text", label: "文本块", group: "document", modernShape: "text", portCount: 8 },
  { type: "doc", label: "文档", group: "document", modernShape: "doc", portCount: 8 },
  { type: "docs", label: "多文档", group: "document", modernShape: "docs", portCount: 8 }
] as const satisfies readonly MermaidNodeShapeDefinition[];

export type MermaidModernNodeShape = (typeof MERMAID_NODE_SHAPES)[number]["modernShape"];

export const MERMAID_MODERN_SHAPE_BY_TYPE = Object.fromEntries(
  MERMAID_NODE_SHAPES.map((definition) => [definition.type, definition.modernShape])
) as Record<MermaidNodeType, MermaidModernNodeShape>;

const NODE_SHAPE_BY_TYPE = new Map<MermaidNodeType, MermaidNodeShapeDefinition>(
  MERMAID_NODE_SHAPES.map((definition) => [definition.type, definition])
);

const NODE_TYPE_BY_MODERN_SHAPE = new Map<MermaidModernNodeShape, MermaidNodeType>(
  MERMAID_NODE_SHAPES.map((definition) => [definition.modernShape, definition.type])
);

type MermaidNodeSizeRule = {
  height: number;
  minWidth: number;
  maxWidth?: number;
  horizontalPadding?: number;
  fixedWidth?: number;
};

const NODE_SIZE_RULES: Record<MermaidNodeType, MermaidNodeSizeRule> = {
  rectangle: { minWidth: 120, maxWidth: 190, horizontalPadding: 40, height: 52 },
  rounded: { minWidth: 120, maxWidth: 190, horizontalPadding: 40, height: 52 },
  stadium: { minWidth: 120, maxWidth: 190, horizontalPadding: 48, height: 52 },
  subroutine: { minWidth: 128, maxWidth: 190, horizontalPadding: 64, height: 52 },
  database: { minWidth: 132, maxWidth: 190, horizontalPadding: 48, height: 68 },
  circle: { minWidth: 92, fixedWidth: 92, height: 92 },
  diamond: { minWidth: 150, fixedWidth: 150, height: 88 },
  hexagon: { minWidth: 140, maxWidth: 190, horizontalPadding: 64, height: 72 },
  parallelogram: { minWidth: 140, maxWidth: 190, horizontalPadding: 64, height: 64 },
  trapezoid: { minWidth: 140, maxWidth: 190, horizontalPadding: 64, height: 64 },
  "double-circle": { minWidth: 100, fixedWidth: 100, height: 100 },
  text: { minWidth: 100, maxWidth: 190, horizontalPadding: 24, height: 44 },
  doc: { minWidth: 132, maxWidth: 190, horizontalPadding: 48, height: 68 },
  docs: { minWidth: 140, maxWidth: 190, horizontalPadding: 56, height: 76 }
};

export type MermaidNodeSize = { width: number; height: number };

/** 返回图形目录定义；调用方只读使用，不能在组件内复制名称或短名表。 */
export function getMermaidNodeShape(type: MermaidNodeType): MermaidNodeShapeDefinition {
  return NODE_SHAPE_BY_TYPE.get(type) ?? NODE_SHAPE_BY_TYPE.get("rectangle")!;
}

/**
 * 画布与 ELK 共用同一包围盒估算。中文按 12px 单字宽度计算，长文本继续沿用 190px
 * 上限并由节点标签省略，避免自动布局与真实节点宽度再次分叉。
 */
export function getMermaidNodeBaseSize(node: { type: MermaidNodeType; text: string }): MermaidNodeSize {
  const rule = NODE_SIZE_RULES[node.type] ?? NODE_SIZE_RULES.rectangle;
  if (rule.fixedWidth) return { width: rule.fixedWidth, height: rule.height };
  const contentWidth = Array.from(node.text || "").length * 12 + (rule.horizontalPadding ?? 0);
  return {
    width: Math.max(rule.minWidth, Math.min(rule.maxWidth ?? rule.minWidth, contentWidth)),
    height: rule.height
  };
}

/** 实际尺寸在默认包围盒上等比缩放；统一限制范围并保留 0.1px，供画布和 ELK 共用。 */
export function getMermaidNodeSize(node: {
  type: MermaidNodeType;
  text: string;
  scale?: number;
}): MermaidNodeSize {
  const base = getMermaidNodeBaseSize(node);
  const scale = Number.isFinite(node.scale) ? Math.min(3, Math.max(0.5, node.scale ?? 1)) : 1;
  return {
    width: Math.round(base.width * scale * 10) / 10,
    height: Math.round(base.height * scale * 10) / 10
  };
}

/** 只识别本编辑器明确支持的官方短名，其他 shape 继续作为未知 Mermaid 原文保留。 */
export function findMermaidNodeTypeByModernShape(shape: string): MermaidNodeType | undefined {
  return NODE_TYPE_BY_MODERN_SHAPE.get(shape.trim().toLowerCase() as MermaidModernNodeShape);
}
