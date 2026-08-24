import { cleanup, render, waitFor } from "@testing-library/vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { OpencodeRuntimeManagementOverview } from "@test-agent/shared-types";
import { buildRuntimeTopologyGraph } from "../src/components/settings/runtimeTopologyGraphData";
import RuntimeTopologyGraph from "../src/components/settings/RuntimeTopologyGraph.vue";

const echartsMock = vi.hoisted(() => {
  const chart = {
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
    on: vi.fn(),
    off: vi.fn(),
    getOption: vi.fn(() => ({ series: [{ zoom: 1 }] }))
  };
  return {
    chart,
    init: vi.fn(() => chart),
    use: vi.fn()
  };
});

vi.mock("echarts/core", () => ({
  init: echartsMock.init,
  use: echartsMock.use
}));
vi.mock("echarts/charts", () => ({ GraphChart: {} }));
vi.mock("echarts/components", () => ({ TooltipComponent: {} }));
vi.mock("echarts/renderers", () => ({ SVGRenderer: {} }));

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

const topologyOverview: OpencodeRuntimeManagementOverview = {
  ...baseOverview,
  managers: [
    {
      managerId: "mgr_resize_guard",
      containerId: "ctr_resize_guard",
      linuxServerId: "10.8.0.12",
      protocolVersion: "opencode-manager.v1",
      connectionStatus: "CONNECTED",
      capabilities: {},
      createdAt: "2026-06-24T08:00:00Z",
      updatedAt: "2026-06-24T08:00:00Z",
      traceId: "trace_resize_guard",
      managedProcesses: []
    }
  ]
};

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

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

describe("RuntimeTopologyGraph lifecycle", () => {
  it("skips ECharts initialization and resize while the persistent page is hidden or zero-sized", async () => {
    let width = 0;
    let height = 0;
    vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockImplementation(() => width);
    vi.spyOn(HTMLElement.prototype, "clientHeight", "get").mockImplementation(() => height);

    const view = render(RuntimeTopologyGraph, {
      props: { overview: topologyOverview, pageActive: false }
    });
    await Promise.resolve();
    expect(echartsMock.init).not.toHaveBeenCalled();

    width = 800;
    height = 360;
    await view.rerender({ overview: topologyOverview, pageActive: true });
    await waitFor(() => expect(echartsMock.init).toHaveBeenCalledTimes(1));
    expect(echartsMock.chart.setOption).toHaveBeenCalled();

    echartsMock.chart.resize.mockClear();
    width = 0;
    window.dispatchEvent(new Event("resize"));
    expect(echartsMock.chart.resize).not.toHaveBeenCalled();

    width = 800;
    height = 0;
    window.dispatchEvent(new Event("resize"));
    expect(echartsMock.chart.resize).not.toHaveBeenCalled();

    height = 360;
    window.dispatchEvent(new Event("resize"));
    expect(echartsMock.chart.resize).toHaveBeenCalledTimes(1);

    await view.rerender({ overview: topologyOverview, pageActive: false });
    window.dispatchEvent(new Event("resize"));
    expect(echartsMock.chart.resize).toHaveBeenCalledTimes(1);
  });
});
