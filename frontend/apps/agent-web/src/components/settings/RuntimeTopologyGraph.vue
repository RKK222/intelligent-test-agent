<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { OpencodeRuntimeManagementOverview } from "@test-agent/shared-types";
import type { EChartsType } from "echarts/core";
import {
  buildRuntimeTopologyGraph,
  type RuntimeTopologyEdge,
  type RuntimeTopologyNode,
  type RuntimeTopologyNodeKind
} from "./runtimeTopologyGraphData";
import linuxSvgUrl from "../../assets/figma/Linux.svg";
import dockerSvgUrl from "../../assets/figma/Docker.svg";
import opencodeLogoSvgUrl from "../../assets/figma/opencode-logo.svg";
import unknownSvgUrl from "../../assets/figma/unknown.svg";

const props = defineProps<{
  overview?: OpencodeRuntimeManagementOverview | null;
}>();

const chartEl = ref<HTMLDivElement | null>(null);
type RuntimeTopologyChartClick = {
  dataType?: string;
  data?: { id?: string };
};
let chart: EChartsType | null = null;

const graph = computed(() => buildRuntimeTopologyGraph(props.overview));
const hasTopology = computed(() => graph.value.nodes.length > 0);
const selectedNodeId = ref<string | null>(null);
const legendItems = [
  { kind: "backend", label: "Java 进程" },
  { kind: "manager", label: "Manager" },
  { kind: "opencode-bound", label: "有主 opencode" },
  { kind: "opencode-unbound", label: "无主 opencode" }
] as const;

function getStatusRichKey(status?: string | null) {
  if (!status) return "status_pending";
  const s = status.toUpperCase();
  if (["READY", "RUNNING", "CONNECTED", "ONLINE"].includes(s)) {
    return "status_online";
  }
  if (["UNHEALTHY", "DISCONNECTED", "ERROR", "OFFLINE", "FAIL", "FAILED"].includes(s)) {
    return "status_offline";
  }
  return "status_pending";
}

const ICON_LINUX = "image://" + linuxSvgUrl;
const ICON_DOCKER = "image://" + dockerSvgUrl;
const ICON_OPENCODE = "image://" + opencodeLogoSvgUrl;
const ICON_UNKNOWN = "image://" + unknownSvgUrl;

function nodeSymbol(kind: RuntimeTopologyNodeKind): string {
  if (kind === "backend") return ICON_LINUX;
  if (kind === "manager") return ICON_DOCKER;
  if (kind === "opencode-bound") return ICON_OPENCODE;
  return ICON_UNKNOWN;
}

function legendIconUrl(kind: string): string {
  if (kind === "backend") return linuxSvgUrl;
  if (kind === "manager") return dockerSvgUrl;
  if (kind === "opencode-bound") return opencodeLogoSvgUrl;
  return unknownSvgUrl;
}

function chartOption() {
  return {
    animation: false,
    tooltip: {
      formatter: (params: { data?: { tooltip?: string; label?: string } }) =>
        (params.data?.tooltip ?? params.data?.label ?? "").replace(/\n/g, "<br/>")
    },
    series: [
      {
        type: "graph",
        layout: "none",
        roam: true,
        draggable: true,
        emphasis: { focus: "adjacency" },
        edgeSymbol: ["none", "arrow"],
        edgeSymbolSize: 8,
        lineStyle: { color: "#94a3b8", width: 1.4, curveness: 0 }, // 直线连接，更符合机房布线风格
        data: graph.value.nodes.map((node) => ({
          id: node.id,
          name: node.id,
          value: node.label,
          x: node.x,
          y: node.y,
          symbol: nodeSymbol(node.kind), // 节点使用具体品牌 SVG Path 图标
          symbolSize: symbolSize(node.kind),
          itemStyle: {
            color: nodeColor(node.kind),
            borderColor: nodeBorderColor(node.kind),
            borderWidth: 1.2,
            opacity: isNodeDimmed(node) ? 0.28 : 1
          },
          label: {
            show: true,
            position: "right", // 网络拓扑图文字排布于图标右侧 (图标在文字前面)
            distance: 8,
            formatter: (params: any) => {
              const nodeData = graph.value.nodes.find((n) => n.id === params.data.id);
              if (!nodeData) return "";
              // 长 ID 自动缩短展示以防文字溢出
              const shortLabel = nodeData.label.length > 20
                ? `${nodeData.label.slice(0, 10)}...${nodeData.label.slice(-6)}`
                : nodeData.label;
              const statusKey = getStatusRichKey(nodeData.status);
              if (nodeData.kind === "backend" || nodeData.kind === "manager") {
                return `{${statusKey}|●} {title|${shortLabel}}`;
              }
              return `{${statusKey}|●} {title|${shortLabel}}\n{subtitle|${nodeData.subtitle}}`;
            },
            rich: {
              status_online: {
                color: "#22c55e",
                fontSize: 10,
                fontWeight: "bold",
                padding: [0, 4, 0, 0]
              },
              status_offline: {
                color: "#ef4444",
                fontSize: 10,
                fontWeight: "bold",
                padding: [0, 4, 0, 0]
              },
              status_pending: {
                color: "#f59e0b",
                fontSize: 10,
                fontWeight: "bold",
                padding: [0, 4, 0, 0]
              },
              title: {
                color: "#1f2937",
                fontWeight: 600,
                fontSize: 11,
                fontFamily: "Geist Mono, monospace",
                lineHeight: 16
              },
              subtitle: {
                color: "#4b5563",
                fontSize: 9,
                padding: [2, 0, 0, 0]
              }
            }
          },
          tooltip: node.tooltip
        })),
        links: graph.value.edges.map((edge) => ({
          source: edge.source,
          target: edge.target,
          value: edge.label,
          lineStyle: {
            color: edge.kind === "backend-manager" ? "#60a5fa" : "#a3a3a3",
            opacity: isEdgeDimmed(edge) ? 0.18 : 1,
            width: isEdgeDimmed(edge) ? 1 : (edge.kind === "backend-manager" ? 2.4 : 1.8)
          },
          tooltip: `${edge.kind === "backend-manager" ? "后端连接" : "进程管理"}\n${edge.label}`
        }))
      }
    ]
  };
}

async function ensureChart() {
  if (!chartEl.value || chart || !hasTopology.value) {
    return;
  }
  try {
    const [{ use, init }, { GraphChart }, { TooltipComponent }, { SVGRenderer }] = await Promise.all([
      import("echarts/core"),
      import("echarts/charts"),
      import("echarts/components"),
      import("echarts/renderers")
    ]);
    use([GraphChart, TooltipComponent, SVGRenderer]);
    const instance = init(chartEl.value);
    instance.on("click", handleChartClick);
    chart = instance;
  } catch {
    chart = null;
  }
}

async function renderChart() {
  await nextTick();
  if (!hasTopology.value) {
    chart?.dispose();
    chart = null;
    return;
  }
  await ensureChart();
  chart?.setOption(chartOption(), true);
}

onMounted(() => {
  void renderChart();
  window.addEventListener("resize", resizeChart);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeChart);
  chart?.off("click", handleChartClick);
  chart?.dispose();
  chart = null;
});

watch(graph, () => {
  if (selectedNodeId.value && !graph.value.nodes.some((node) => node.id === selectedNodeId.value)) {
    selectedNodeId.value = null;
  }
  void renderChart();
}, { deep: true });

function resizeChart() {
  chart?.resize();
}

function handleChartClick(params: unknown) {
  const event = params as RuntimeTopologyChartClick;
  if (event.dataType !== "node" || !event.data?.id) {
    selectedNodeId.value = null;
    void renderChart();
    return;
  }
  selectedNodeId.value = selectedNodeId.value === event.data.id ? null : event.data.id;
  void renderChart();
}

function isNodeDimmed(node: RuntimeTopologyNode) {
  const selected = selectedNodeId.value;
  if (!selected || node.id === selected) {
    return false;
  }
  return !graph.value.edges.some((edge) => isAdjacentEdge(edge, node.id, selected));
}

function isEdgeDimmed(edge: RuntimeTopologyEdge) {
  const selected = selectedNodeId.value;
  return Boolean(selected && edge.source !== selected && edge.target !== selected);
}

function isAdjacentEdge(edge: RuntimeTopologyEdge, nodeId: string, selected: string) {
  return (edge.source === selected && edge.target === nodeId) || (edge.target === selected && edge.source === nodeId);
}

function symbolSize(kind: RuntimeTopologyNodeKind): number {
  return 24;
}

function nodeColor(kind: RuntimeTopologyNodeKind) {
  if (kind === "backend") return "#1f2937"; // Linux 填充色 (深色/石墨灰)
  if (kind === "manager") return "#0db7ed"; // Docker 填充色 (品牌青蓝)
  if (kind === "opencode-bound") return "#f59e0b"; // opencode 填充色 (有主琥珀黄)
  return "#ef4444"; // opencode 填充色 (无主异常红)
}

function nodeBorderColor(kind: RuntimeTopologyNodeKind) {
  if (kind === "backend") return "#111827";
  if (kind === "manager") return "#0284c7";
  if (kind === "opencode-bound") return "#d97706";
  return "#dc2626";
}

function handleZoomIn() {
  if (!chart) return;
  const option = chart.getOption() as any;
  const currentZoom = option.series[0].zoom ?? 1;
  chart.setOption({
    series: [{
      zoom: currentZoom * 1.2
    }]
  });
}

function handleZoomOut() {
  if (!chart) return;
  const option = chart.getOption() as any;
  const currentZoom = option.series[0].zoom ?? 1;
  chart.setOption({
    series: [{
      zoom: currentZoom / 1.2
    }]
  });
}

function handleZoomReset() {
  if (!chart) return;
  chart.setOption({
    series: [{
      zoom: 1,
      center: null
    }]
  });
}
</script>

<template>
  <div class="ta-runtime-topology">
    <div class="ta-runtime-topology-meta">
      <span v-for="item in legendItems" :key="item.kind">
        <img :src="legendIconUrl(item.kind)" class="legend-icon" alt="" />
        {{ item.label }}
      </span>
      <span>{{ graph.nodes.length }} 节点 / {{ graph.edges.length }} 连线</span>
    </div>
    <div v-if="!hasTopology" class="ta-runtime-topology-empty">暂无拓扑关系</div>
    <template v-else>
      <div ref="chartEl" class="ta-runtime-topology-canvas" aria-label="Java、Manager 与 TestAgent server 网络拓扑图" role="img"></div>
      <div class="ta-runtime-topology-zoom-controls">
        <button @click="handleZoomIn" title="放大" aria-label="放大">+</button>
        <button @click="handleZoomOut" title="缩小" aria-label="缩小">-</button>
        <button @click="handleZoomReset" title="重置" aria-label="重置">⟲</button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ta-runtime-topology {
  position: relative;
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.ta-runtime-topology-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 15px;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  color: #606266;
  font-size: 12px;
  background: #fafafa;
}
.ta-runtime-topology-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.ta-runtime-topology-meta .legend-icon {
  width: 16px;
  height: 16px;
  object-fit: contain;
}
.ta-runtime-topology-canvas,
.ta-runtime-topology-empty {
  height: 400px;
}
.ta-runtime-topology-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 12px;
}
.ta-runtime-topology-zoom-controls {
  position: absolute;
  bottom: 15px;
  right: 15px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  z-index: 10;
}
.ta-runtime-topology-zoom-controls button {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  background: rgba(255, 255, 255, 0.9);
  color: #606266;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  transition: all 0.2s;
  outline: none;
  user-select: none;
}
.ta-runtime-topology-zoom-controls button:hover {
  background: #f5f7fa;
  color: #409eff;
  border-color: #c6e2ff;
}
.ta-runtime-topology-zoom-controls button:active {
  background: #ebeeef;
}
</style>
