import type {
  OpencodeRuntimeManagementOverview,
  OpencodeRuntimeManagedProcess,
  OpencodeRuntimeManager
} from "@test-agent/shared-types";

export type RuntimeTopologyNodeKind = "backend" | "manager" | "opencode-bound" | "opencode-unbound";

export type RuntimeTopologyEdgeKind = "backend-manager" | "manager-opencode";

export type RuntimeTopologyNode = {
  id: string;
  kind: RuntimeTopologyNodeKind;
  label: string;
  subtitle: string;
  status?: string | null;
  tooltip: string;
  x: number;
  y: number;
};

export type RuntimeTopologyEdge = {
  source: string;
  target: string;
  kind: RuntimeTopologyEdgeKind;
  label: string;
  status?: string | null;
};

export type RuntimeTopologyGraph = {
  nodes: RuntimeTopologyNode[];
  edges: RuntimeTopologyEdge[];
};

const X_GAP = 260; // 节点水平间距（适应 220px-240px 宽的卡片）
const OPENCODE_GAP = 160; // opencode 节点水平间距（适应 140px 宽的卡片）

/**
 * 将运行管理 overview 派生为前端网络拓扑图节点和边；只做展示态转换，不改变后端 wire shape。
 * 优化为上下结构的机房网络拓扑：
 * - 第一层 (Top, Y=60): Java 进程
 * - 第二层 (Middle, Y=180): Manager 进程
 * - 第三层 (Bottom, Y=300): opencode server 进程，水平分布在所属 Manager 下方
 */
export function buildRuntimeTopologyGraph(overview?: OpencodeRuntimeManagementOverview | null): RuntimeTopologyGraph {
  if (!overview) {
    return { nodes: [], edges: [] };
  }
  const nodes: RuntimeTopologyNode[] = [];
  const edges: RuntimeTopologyEdge[] = [];
  const nodeIds = new Set<string>();

  const backends = overview.backendProcesses ?? [];
  const managers = overview.managers ?? [];
  const containersById = new Map((overview.containers ?? []).map((container) => [container.containerId, container]));

  // 1. 第一层：Java 进程 (Top, Y=60)，水平居中分布
  const backendCount = backends.length;
  const backendStartX = 400 - ((backendCount - 1) * X_GAP) / 2;

  for (const [index, backend] of backends.entries()) {
    const x = backendStartX + index * X_GAP;
    appendNode(nodes, nodeIds, {
      id: backendNodeId(backend.backendProcessId),
      kind: "backend",
      label: backend.linuxServerId,
      subtitle: `${backend.linuxServerId} / ${backend.status}`,
      status: backend.status,
      tooltip: [
        `Java 进程: ${backend.backendProcessId}`,
        `服务器: ${backend.linuxServerId}`,
        `状态: ${backend.status}`,
        `监听: ${backend.listenUrl}`,
        `心跳: ${backend.lastHeartbeatAt ?? "-"}`
      ].join("\n"),
      x,
      y: 50
    });
  }

  // 2. 第二层：Manager (Middle, Y=180)，水平居中分布
  const managerCount = managers.length;
  const managerStartX = 400 - ((managerCount - 1) * X_GAP) / 2;
  const managerXMap = new Map<string, number>();

  for (const [index, manager] of managers.entries()) {
    const x = managerStartX + index * X_GAP;
    const container = containersById.get(manager.containerId);
    const containerName = container?.containerName || manager.containerId || manager.managerId;
    managerXMap.set(manager.managerId, x);
    appendNode(nodes, nodeIds, {
      id: managerNodeId(manager.managerId),
      kind: "manager",
      label: containerName,
      subtitle: `${manager.containerId} / ${manager.connectionStatus}`,
      status: manager.connectionStatus,
      tooltip: [
        `管理进程: ${manager.managerId}`,
        `容器名称: ${containerName}`,
        `容器 ID: ${manager.containerId}`,
        `服务器: ${manager.linuxServerId}`,
        `状态: ${manager.connectionStatus}`,
        `协议: ${manager.protocolVersion}`,
        `心跳: ${manager.lastHeartbeatAt ?? "-"}`
      ].join("\n"),
      x,
      y: 140
    });
  }

  // 3. 构建 Java 进程与 Manager 之间的连接边
  for (const connection of overview.managerBackendConnections ?? []) {
    const source = backendNodeId(connection.backendProcessId);
    const target = managerNodeId(connection.managerId);
    if (!nodeIds.has(source) || !nodeIds.has(target)) {
      continue;
    }
    edges.push({
      source,
      target,
      kind: "backend-manager",
      label: connection.status,
      status: connection.status
    });
  }

  // 4. 第三层：opencode server (Bottom, Y=300)，水平居中分布在对应 Manager 下方
  for (const manager of managers) {
    const managedProcesses = manager.managedProcesses ?? [];
    const managerX = managerXMap.get(manager.managerId) ?? 400;
    const processCount = managedProcesses.length;
    const startX = managerX - ((processCount - 1) * OPENCODE_GAP) / 2;

    for (const [index, process] of managedProcesses.entries()) {
      const processX = startX + index * OPENCODE_GAP;
      const bound = process.ownership === "BOUND";
      const userOwner = bound ? (process.username || process.userId || "-") : "-";
      const owner = bound
        ? userOwner
        : process.unifiedAuthId
          ? `UCID: ${process.unifiedAuthId}`
          : "无主";
      // 无平台进程记录时仅展示 manager 事实，避免把未执行的 HTTP 检查误报为异常。
      const platformStatus = process.processStatus ?? process.managerStatus ?? "-";
      const platformRegistration = process.processId || "平台未登记";
      const health = process.processId
        ? (process.healthMessage || process.processStatus || "-")
        : "未执行 HTTP 健康检查";

      const processNode: RuntimeTopologyNode = {
        id: `opencode:${manager.managerId}:${process.port}:${index}`,
        kind: bound ? "opencode-bound" : "opencode-unbound",
        label: String(process.port),
        subtitle: `${owner} / ${platformStatus}`,
        status: process.processStatus ?? process.managerStatus,
        tooltip: [
          `TestAgent server: ${process.port}`,
          `归属: ${bound ? "有主" : "无主"}`,
          `用户: ${userOwner}`,
          `UCID: ${process.unifiedAuthId ?? "-"}`,
          `Manager 状态: ${process.managerStatus ?? "-"}`,
          `平台登记: ${platformRegistration}`,
          `平台状态: ${process.processStatus ?? "-"}`,
          `健康检查: ${health}`,
          `PID: ${process.pid ?? "-"}`,
          `baseUrl: ${process.baseUrl ?? "-"}`
        ].join("\n"),
        x: processX,
        y: 230
      };

      appendNode(nodes, nodeIds, processNode);
      edges.push({
        source: managerNodeId(manager.managerId),
        target: processNode.id,
        kind: "manager-opencode",
        label: "manages",
        status: process.processStatus ?? process.managerStatus
      });
    }
  }

  return { nodes, edges };
}

function appendNode(nodes: RuntimeTopologyNode[], nodeIds: Set<string>, node: RuntimeTopologyNode) {
  if (nodeIds.has(node.id)) {
    return;
  }
  nodeIds.add(node.id);
  nodes.push(node);
}

function backendNodeId(backendProcessId: string) {
  return `backend:${backendProcessId}`;
}

function managerNodeId(managerId: string) {
  return `manager:${managerId}`;
}
