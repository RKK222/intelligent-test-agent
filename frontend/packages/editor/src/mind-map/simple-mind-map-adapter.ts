import { normalizeMindMapColor } from "./compact-metadata";
import {
  MIND_MAP_MAX_DEPTH,
  MIND_MAP_MAX_NODES,
  MIND_MAP_SHAPES,
  isMindMapNodeId,
  type MindMapDocument,
  type MindMapNode,
  type MindMapNodeStyle,
  type MindMapShape
} from "./model";

export interface SimpleMindMapDataValue {
  text: string;
  uid: string;
  expand: boolean;
  /** 读取第三方回包时可能不是 false；适配器不会信任或执行该值。 */
  richText: boolean;
  color?: string;
  fillColor?: string;
  borderColor?: string;
  borderWidth?: number;
  shape?: MindMapShape;
  fontSize?: number;
  fontWeight?: "bold";
  lineColor?: string;
  lineWidth?: number;
  lineDasharray?: "6,4";
}

export interface SimpleMindMapTreeData {
  data: SimpleMindMapDataValue & Record<string, unknown>;
  children: SimpleMindMapTreeData[];
}

function safeStyleToCanvas(style: MindMapNodeStyle | undefined): Partial<SimpleMindMapDataValue> {
  if (!style) return {};
  return {
    ...(style.textColor === undefined ? {} : { color: normalizeMindMapColor(style.textColor) }),
    ...(style.fillColor === undefined ? {} : { fillColor: normalizeMindMapColor(style.fillColor) }),
    ...(style.borderColor === undefined ? {} : { borderColor: normalizeMindMapColor(style.borderColor) }),
    ...(style.borderWidth === undefined ? {} : { borderWidth: style.borderWidth }),
    ...(style.shape === undefined ? {} : { shape: style.shape }),
    ...(style.fontSize === undefined ? {} : { fontSize: style.fontSize }),
    ...(style.bold === true ? { fontWeight: "bold" as const } : {}),
    ...(style.lineColor === undefined ? {} : { lineColor: normalizeMindMapColor(style.lineColor) }),
    ...(style.lineWidth === undefined ? {} : { lineWidth: style.lineWidth }),
    ...(style.lineDash === "dashed" ? { lineDasharray: "6,4" as const } : {})
  };
}

/** 适配器显式构造白名单对象，领域节点上即使混入未知字段也不会进入第三方画布。 */
export function toSimpleMindMapData(document: MindMapDocument): SimpleMindMapTreeData {
  let draftIndex = 0;
  const usedCanvasUids = new Set<string>();
  const nextDraftUid = (): string => {
    let uid: string;
    do {
      draftIndex += 1;
      uid = `mm-draft-${draftIndex}`;
    } while (usedCanvasUids.has(uid));
    usedCanvasUids.add(uid);
    return uid;
  };
  // 损坏元数据仍可进入只读安全预览；因此这里不能信任领域节点携带的 ID，
  // 必须保证交给第三方画布的 uid 合法且唯一，避免节点被错误合并或覆盖。
  const canvasUid = (node: MindMapNode, isRoot: boolean): string => {
    if (isRoot) {
      usedCanvasUids.add("root");
      return "root";
    }
    if (node.id && node.id !== "root" && isMindMapNodeId(node.id) && !usedCanvasUids.has(node.id)) {
      usedCanvasUids.add(node.id);
      return node.id;
    }
    return nextDraftUid();
  };
  const convert = (node: MindMapNode, isRoot: boolean): SimpleMindMapTreeData => {
    return {
      data: {
        text: node.text,
        uid: canvasUid(node, isRoot),
        expand: isRoot ? true : node.collapsed !== true,
        richText: false,
        ...safeStyleToCanvas(node.style)
      },
      children: node.children.map((child) => convert(child, false))
    };
  };
  return convert(document.root, true);
}

function optionalColor(data: Record<string, unknown>, key: string): string | undefined {
  const value = data[key];
  return typeof value === "string" ? normalizeMindMapColor(value) : undefined;
}

function optionalInteger(data: Record<string, unknown>, key: string): number | undefined {
  const value = data[key];
  return typeof value === "number" && Number.isInteger(value) ? value : undefined;
}

/** 从第三方数据只读取已知样式；图片、链接、图标、HTML、备注、标签和概要一律忽略。 */
export function styleFromSimpleMindMapData(data: Record<string, unknown>): MindMapNodeStyle {
  const style: MindMapNodeStyle = {};
  const textColor = optionalColor(data, "color");
  const fillColor = optionalColor(data, "fillColor");
  const borderColor = optionalColor(data, "borderColor");
  const lineColor = optionalColor(data, "lineColor");
  const borderWidth = optionalInteger(data, "borderWidth");
  const fontSize = optionalInteger(data, "fontSize");
  const lineWidth = optionalInteger(data, "lineWidth");
  if (textColor) style.textColor = textColor;
  if (fillColor) style.fillColor = fillColor;
  if (borderColor) style.borderColor = borderColor;
  if (lineColor) style.lineColor = lineColor;
  if (borderWidth !== undefined) style.borderWidth = borderWidth;
  if (fontSize !== undefined) style.fontSize = fontSize;
  if (lineWidth !== undefined) style.lineWidth = lineWidth;
  if (typeof data.shape === "string" && MIND_MAP_SHAPES.includes(data.shape as MindMapShape)) {
    style.shape = data.shape as MindMapShape;
  }
  if (data.fontWeight === "bold" || data.fontWeight === 700 || data.fontWeight === "700") {
    style.bold = true;
  }
  if (data.lineDasharray === "6,4") style.lineDash = "dashed";
  return style;
}

function cloneUnknownFields(node: MindMapNode | undefined) {
  return node?.unknownFields?.map((field) => ({ type: field.type, value: field.value.slice() }));
}

export function fromSimpleMindMapData(
  data: SimpleMindMapTreeData,
  template: MindMapDocument
): MindMapDocument {
  const templateById = new Map<string, MindMapNode>();
  const templateStack = [template.root];
  while (templateStack.length > 0) {
    const node = templateStack.pop()!;
    if (node.id) templateById.set(node.id, node);
    templateStack.push(...node.children);
  }
  let count = 0;
  const convert = (value: SimpleMindMapTreeData, depth: number, isRoot: boolean): MindMapNode => {
    count += 1;
    if (count > MIND_MAP_MAX_NODES) throw new Error(`思维导图最多 ${MIND_MAP_MAX_NODES} 个节点`);
    if (depth > MIND_MAP_MAX_DEPTH) throw new Error(`思维导图最多 ${MIND_MAP_MAX_DEPTH} 层`);
    if (!value || typeof value !== "object" || !value.data || !Array.isArray(value.children)) {
      throw new Error("SimpleMindMap 返回了无法识别的节点结构");
    }
    const text = value.data.text;
    if (typeof text !== "string") throw new Error("SimpleMindMap 节点文字非法");
    const rawId = typeof value.data.uid === "string" ? value.data.uid : undefined;
    const id = rawId && isMindMapNodeId(rawId)
      && (isRoot ? rawId === "root" : rawId !== "root")
      ? rawId
      : undefined;
    const style = styleFromSimpleMindMapData(value.data);
    return {
      id,
      text,
      collapsed: isRoot ? false : value.data.expand === false,
      style: Object.keys(style).length > 0 ? style : undefined,
      unknownFields: cloneUnknownFields(id ? templateById.get(id) : undefined),
      children: value.children.map((child) => convert(child, depth + 1, false))
    };
  };
  return {
    root: convert(data, 0, true),
    nextId: template.nextId,
    lineEnding: template.lineEnding,
    metadataPresent: template.metadataPresent,
    unknownMetadataFields: template.unknownMetadataFields.map((field) => ({
      type: field.type,
      value: field.value.slice()
    }))
  };
}
