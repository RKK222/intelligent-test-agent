import { describe, expect, it } from "vitest";
import type { OpencodeRuntimeManagementOverview } from "@test-agent/shared-types";
import { buildRuntimeTopologyGraph } from "../src/components/settings/runtimeTopologyGraphData";

const baseOverview: OpencodeRuntimeManagementOverview = {
  generatedAt: "2026-06-24T08:00:00Z",
  summary: {
    linuxServers: 0,
    readyLinuxServers: 0,
    backendProcesses: 0,
    readyBackendProcesses: 0,
    containers: 0,
    readyContainers: 0,
    managers: 0,
    connectedManagers: 0,
    managerBackendConnections: 0,
    opencodeProcesses: 0,
    runningOpencodeProcesses: 0,
    userBindings: 0
  },
  linuxServers: [],
  backendProcesses: [],
  containers: [],
  managers: [],
  managerBackendConnections: [],
  opencodeProcesses: {
    items: [],
    page: 1,
    size: 20,
    total: 0
  }
};

describe("runtime topology graph data", () => {
  it("builds backend, manager and opencode nodes with connection edges", () => {
    const overview: OpencodeRuntimeManagementOverview = {
      ...baseOverview,
      backendProcesses: [
        {
          backendProcessId: "bjp_1234567890abcdef",
          linuxServerId: "10.8.0.12",
          listenUrl: "http://10.8.0.12:8080",
          status: "READY",
          startedAt: "2026-06-24T08:00:00Z",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_backend"
        }
      ],
      containers: [
        {
          containerId: "ctr_01",
          linuxServerId: "10.8.0.12",
          containerName: "test-agent-opencode-worker",
          portStart: 4096,
          portEnd: 4100,
          maxProcesses: 4,
          currentProcesses: 2,
          availableCapacity: 2,
          status: "READY",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_container"
        }
      ],
      managers: [
        {
          managerId: "mgr_1234567890abcdef",
          containerId: "ctr_01",
          linuxServerId: "10.8.0.12",
          protocolVersion: "opencode-manager.v1",
          connectionStatus: "CONNECTED",
          capabilities: {},
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_manager",
          managedProcesses: [
            {
              port: 4096,
              pid: 12345,
              baseUrl: "http://10.8.0.12:4096",
              ownership: "BOUND",
              username: "wr",
              processStatus: "RUNNING",
              unifiedAuthId: "BOUND-A",
              managerStatus: "PID_ALIVE",
              traceId: "trace_opencode_bound"
            },
            {
              port: 4104,
              pid: 22345,
              baseUrl: "http://10.8.0.12:4104",
              ownership: "UNBOUND",
              unifiedAuthId: "A",
              managerStatus: "PID_ALIVE",
              traceId: "trace_opencode_unbound"
            }
          ]
        }
      ],
      managerBackendConnections: [
        {
          managerId: "mgr_1234567890abcdef",
          backendProcessId: "bjp_1234567890abcdef",
          status: "CONNECTED",
          connectedAt: "2026-06-24T08:00:00Z",
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_connection"
        }
      ]
    };

    const graph = buildRuntimeTopologyGraph(overview);

    expect(graph.nodes.map((node) => [node.id, node.kind, node.label])).toEqual([
      ["backend:bjp_1234567890abcdef", "backend", "10.8.0.12"],
      ["manager:mgr_1234567890abcdef", "manager", "test-agent-opencode-worker"],
      ["opencode:mgr_1234567890abcdef:4096:0", "opencode-bound", "4096"],
      ["opencode:mgr_1234567890abcdef:4104:1", "opencode-unbound", "4104"]
    ]);
    expect(graph.edges.map((edge) => [edge.source, edge.target, edge.kind])).toEqual([
      ["backend:bjp_1234567890abcdef", "manager:mgr_1234567890abcdef", "backend-manager"],
      ["manager:mgr_1234567890abcdef", "opencode:mgr_1234567890abcdef:4096:0", "manager-opencode"],
      ["manager:mgr_1234567890abcdef", "opencode:mgr_1234567890abcdef:4104:1", "manager-opencode"]
    ]);
    const boundNode = graph.nodes.find((node) => node.id === "opencode:mgr_1234567890abcdef:4096:0");
    const unboundNode = graph.nodes.find((node) => node.id === "opencode:mgr_1234567890abcdef:4104:1");
    expect(boundNode?.subtitle).toBe("wr / RUNNING");
    expect(boundNode?.tooltip).toContain("Manager 状态: PID_ALIVE");
    expect(unboundNode?.subtitle).toBe("UCID: A / PID_ALIVE");
    expect(unboundNode?.tooltip).toContain("UCID: A");
    expect(unboundNode?.tooltip).toContain("Manager 状态: PID_ALIVE");
    expect(unboundNode?.tooltip).toContain("平台登记: 平台未登记");
    expect(unboundNode?.tooltip).toContain("健康检查: 未执行 HTTP 健康检查");
    expect(graph.nodes.find((node) => node.id === "manager:mgr_1234567890abcdef")?.tooltip).toContain("容器 ID: ctr_01");
  });

  it("keeps legacy managed process responses readable when new fields are absent", () => {
    const overview: OpencodeRuntimeManagementOverview = {
      ...baseOverview,
      managers: [
        {
          managerId: "mgr_legacy",
          containerId: "ctr_legacy",
          linuxServerId: "10.8.0.12",
          protocolVersion: "opencode-manager.v1",
          connectionStatus: "CONNECTED",
          capabilities: {},
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_manager",
          managedProcesses: [
            {
              port: 4097,
              pid: 32345,
              baseUrl: "http://10.8.0.12:4097",
              ownership: "UNBOUND"
            }
          ]
        }
      ]
    };

    const graph = buildRuntimeTopologyGraph(overview);
    const node = graph.nodes.find((candidate) => candidate.kind === "opencode-unbound");

    expect(node?.subtitle).toBe("无主 / -");
    expect(node?.tooltip).toContain("UCID: -");
    expect(node?.tooltip).toContain("Manager 状态: -");
    expect(node?.tooltip).not.toContain("undefined");
  });

  it("keeps manager nodes when old responses omit managedProcesses", () => {
    const overview: OpencodeRuntimeManagementOverview = {
      ...baseOverview,
      managers: [
        {
          managerId: "mgr_1234567890abcdef",
          containerId: "ctr_01",
          linuxServerId: "10.8.0.12",
          protocolVersion: "opencode-manager.v1",
          connectionStatus: "CONNECTED",
          capabilities: {},
          lastHeartbeatAt: "2026-06-24T08:00:00Z",
          createdAt: "2026-06-24T08:00:00Z",
          updatedAt: "2026-06-24T08:00:00Z",
          traceId: "trace_manager"
        }
      ]
    };

    const graph = buildRuntimeTopologyGraph(overview);

    expect(graph.nodes.map((node) => [node.id, node.kind])).toEqual([
      ["manager:mgr_1234567890abcdef", "manager"]
    ]);
    expect(graph.edges).toEqual([]);
  });
});
