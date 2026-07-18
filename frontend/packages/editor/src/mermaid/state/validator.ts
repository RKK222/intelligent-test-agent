import {
  flattenMermaidStateScopes,
  indexMermaidStateNodes,
  type MermaidStateDiagram,
  type MermaidStateNode,
  type MermaidStateRegion
} from "./model";

export type MermaidStateValidationCode =
  | "duplicate-state-id"
  | "missing-transition-endpoint"
  | "cross-scope-transition"
  | "cross-region-transition"
  | "invalid-self-loop"
  | "start-cardinality"
  | "end-cardinality"
  | "choice-cardinality"
  | "fork-cardinality"
  | "join-cardinality"
  | "empty-region"
  | "missing-note-target"
  | "invalid-note-target";

export type MermaidStateValidationIssue = {
  code: MermaidStateValidationCode;
  message: string;
  scopeId?: string;
  regionId?: string;
  nodeId?: string;
  transitionId?: string;
  noteId?: string;
};

function cardinalityMessage(node: MermaidStateNode, incoming: number, outgoing: number): MermaidStateValidationIssue | undefined {
  if (node.kind === "start" && (incoming !== 0 || outgoing !== 1)) {
    return { code: "start-cardinality", nodeId: node.id, message: "开始节点必须没有入边且恰好一条出边" };
  }
  if (node.kind === "end" && (incoming !== 1 || outgoing !== 0)) {
    return { code: "end-cardinality", nodeId: node.id, message: "结束节点必须恰好一条入边且没有出边" };
  }
  if (node.kind === "choice" && (incoming !== 1 || outgoing < 2)) {
    return { code: "choice-cardinality", nodeId: node.id, message: "Choice 必须恰好一条入边且至少两条出边" };
  }
  if (node.kind === "fork" && (incoming !== 1 || outgoing < 2)) {
    return { code: "fork-cardinality", nodeId: node.id, message: "Fork 必须恰好一条入边且至少两条出边" };
  }
  if (node.kind === "join" && (incoming < 2 || outgoing !== 1)) {
    return { code: "join-cardinality", nodeId: node.id, message: "Join 必须至少两条入边且恰好一条出边" };
  }
  return undefined;
}

/** 应用前的完整领域校验；返回全部问题，便于一次性向用户解释草稿中所有不完整处。 */
export function validateMermaidState(diagram: MermaidStateDiagram): MermaidStateValidationIssue[] {
  const issues: MermaidStateValidationIssue[] = [];
  const seenIds = new Set<string>();
  const nodeIndex = indexMermaidStateNodes(diagram);

  for (const scope of flattenMermaidStateScopes(diagram)) {
    for (const region of scope.regions) {
      if (region.nodes.length === 0) {
        issues.push({
          code: "empty-region",
          scopeId: scope.id,
          regionId: region.id,
          message: "并发区域不能为空"
        });
      }

      for (const node of region.nodes) {
        if (seenIds.has(node.id)) {
          issues.push({ code: "duplicate-state-id", nodeId: node.id, message: `状态 ID ${node.id} 重复` });
        }
        seenIds.add(node.id);
      }

      for (const transition of region.transitions) {
        const source = nodeIndex.get(transition.source);
        const target = nodeIndex.get(transition.target);
        if (!source || !target) {
          issues.push({
            code: "missing-transition-endpoint",
            transitionId: transition.id,
            message: `转换 ${transition.id} 的端点不存在`
          });
          continue;
        }
        if (source.scope.id !== scope.id || target.scope.id !== scope.id) {
          issues.push({
            code: "cross-scope-transition",
            transitionId: transition.id,
            message: "转换不能跨复合状态层级"
          });
        } else if (source.region.id !== region.id || target.region.id !== region.id) {
          issues.push({
            code: "cross-region-transition",
            transitionId: transition.id,
            message: "转换不能跨并发区域"
          });
        }
        if (
          transition.source === transition.target
          && (source.node.kind !== "state")
        ) {
          issues.push({
            code: "invalid-self-loop",
            transitionId: transition.id,
            nodeId: source.node.id,
            message: "自循环只允许普通或复合状态"
          });
        }
      }

      for (const node of region.nodes) {
        const incoming = region.transitions.filter((transition) => transition.target === node.id).length;
        const outgoing = region.transitions.filter((transition) => transition.source === node.id).length;
        const issue = cardinalityMessage(node, incoming, outgoing);
        if (issue) issues.push({ ...issue, scopeId: scope.id, regionId: region.id });
      }

      for (const note of region.notes) {
        const target = nodeIndex.get(note.target);
        if (!target) {
          issues.push({
            code: "missing-note-target",
            noteId: note.id,
            message: `Note 目标 ${note.target} 不存在`
          });
        } else if (
          target.scope.id !== scope.id
          || target.region.id !== region.id
          || target.node.kind !== "state"
        ) {
          issues.push({
            code: "invalid-note-target",
            noteId: note.id,
            nodeId: note.target,
            message: "Note 只能依附同一区域的普通或复合状态"
          });
        }
      }
    }
  }
  return issues;
}

export type MermaidStateConnection = {
  source: string | null;
  target: string | null;
  sourceHandle?: string | null;
  targetHandle?: string | null;
};

function countEdges(region: MermaidStateRegion, nodeId: string, end: "source" | "target", excludeId?: string): number {
  return region.transitions.filter((transition) => transition.id !== excludeId && transition[end] === nodeId).length;
}

/** 连接拖拽阶段只拒绝必然非法的操作；缺少最小基数由应用时完整校验。 */
export function getMermaidStateConnectionInvalidReason(
  diagram: MermaidStateDiagram,
  regionId: string,
  connection: MermaidStateConnection,
  excludeTransitionId?: string
): string | undefined {
  if (!connection.source || !connection.target) return undefined;
  const index = indexMermaidStateNodes(diagram);
  const source = index.get(connection.source);
  const target = index.get(connection.target);
  if (!source || !target) return "连接端点不存在";
  if (source.region.id !== regionId || target.region.id !== regionId) return "转换不能跨复合状态或并发区域";
  if (source.node.kind === "end") return "结束节点不能创建出边";
  if (target.node.kind === "start") return "开始节点不能接收入边";
  if (connection.source === connection.target && source.node.kind !== "state") {
    return "自循环只允许普通或复合状态";
  }
  if (source.node.kind === "start" && countEdges(source.region, source.node.id, "source", excludeTransitionId) >= 1) {
    return "开始节点只能有一条出边";
  }
  if (target.node.kind === "end" && countEdges(target.region, target.node.id, "target", excludeTransitionId) >= 1) {
    return "结束节点只能有一条入边";
  }
  if (
    (target.node.kind === "choice" || target.node.kind === "fork")
    && countEdges(target.region, target.node.id, "target", excludeTransitionId) >= 1
  ) return `${target.node.kind === "choice" ? "Choice" : "Fork"} 只能有一条入边`;
  if (source.node.kind === "join" && countEdges(source.region, source.node.id, "source", excludeTransitionId) >= 1) {
    return "Join 只能有一条出边";
  }
  if (
    connection.source === connection.target
    && connection.sourceHandle
    && connection.sourceHandle === connection.targetHandle
  ) return "不能在同一个端口上建立自循环";
  return undefined;
}

export function canConnectMermaidState(
  diagram: MermaidStateDiagram,
  regionId: string,
  connection: MermaidStateConnection,
  excludeTransitionId?: string
): boolean {
  return Boolean(connection.source && connection.target)
    && getMermaidStateConnectionInvalidReason(diagram, regionId, connection, excludeTransitionId) === undefined;
}
