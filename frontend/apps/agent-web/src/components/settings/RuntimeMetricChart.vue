<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { formatMetricSampleTime } from "./runtimeMetricFormatting";

type MetricSeries = {
  name: string;
  field: string;
};

const props = defineProps<{
  title: string;
  samples: Record<string, unknown>[];
  series: MetricSeries[];
}>();

const chartEl = ref<HTMLDivElement | null>(null);
let chart: { setOption: (option: unknown, replace?: boolean) => void; resize: () => void; dispose: () => void } | null = null;

const hasSeriesSamples = computed(() =>
  props.samples.some((sample) => props.series.some((item) => valueOf(sample, item.field) !== null))
);

function valueOf(sample: Record<string, unknown>, field: string) {
  const value = sample[field];
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function chartOption() {
  return {
    animation: false,
    tooltip: { trigger: "axis" },
    legend: { top: 0, right: 8, textStyle: { fontSize: 11 } },
    grid: { top: 28, left: 44, right: 16, bottom: 28 },
    xAxis: {
      type: "category",
      boundaryGap: false,
      data: props.samples.map((sample) => formatMetricSampleTime(sample.sampledAt))
    },
    yAxis: { type: "value", scale: true },
    series: props.series.map((item) => ({
      name: item.name,
      type: "line",
      showSymbol: false,
      connectNulls: false,
      data: props.samples.map((sample) => valueOf(sample, item.field))
    }))
  };
}

async function ensureChart() {
  if (!chartEl.value || chart) {
    return;
  }
  try {
    const [{ use, init }, { LineChart }, { GridComponent, LegendComponent, TooltipComponent }, { CanvasRenderer }] =
      await Promise.all([
        import("echarts/core"),
        import("echarts/charts"),
        import("echarts/components"),
        import("echarts/renderers")
      ]);
    use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);
    chart = init(chartEl.value);
  } catch {
    chart = null;
  }
}

async function renderChart() {
  await nextTick();
  await ensureChart();
  chart?.setOption(chartOption(), true);
}

onMounted(() => {
  void renderChart();
  window.addEventListener("resize", resizeChart);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeChart);
  chart?.dispose();
  chart = null;
});

watch(() => [props.samples, props.series], () => {
  void renderChart();
}, { deep: true });

function resizeChart() {
  chart?.resize();
}
</script>

<template>
  <div class="ta-runtime-chart">
    <h6>{{ title }}</h6>
    <div v-if="!props.samples.length" class="ta-runtime-chart-empty">暂无监控数据</div>
    <div v-else-if="!hasSeriesSamples" class="ta-runtime-chart-empty">暂无该类采样</div>
    <div ref="chartEl" class="ta-runtime-chart-canvas" aria-hidden="true" />
  </div>
</template>

<style scoped>
.ta-runtime-chart {
  min-width: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.ta-runtime-chart h6 {
  margin: 0;
  padding: 8px 10px;
  border-bottom: 1px solid #ebeef5;
  color: #303133;
  font-size: 12px;
  font-weight: 650;
  background: #fafafa;
}
.ta-runtime-chart-canvas,
.ta-runtime-chart-empty {
  height: 180px;
}
.ta-runtime-chart-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 12px;
}
.ta-runtime-chart-empty + .ta-runtime-chart-canvas {
  display: none;
}
</style>
