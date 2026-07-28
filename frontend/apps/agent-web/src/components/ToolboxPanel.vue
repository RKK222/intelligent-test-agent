<script setup lang="ts">
import { computed, inject, onMounted, ref } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  ToolboxCatalog,
  ToolboxCategory,
  ToolboxClickResult,
  ToolboxSource,
  ToolboxTool
} from "@test-agent/shared-types";
import { ExternalLink, Flame, MousePointerClick, Search } from "lucide-vue-next";

const api = inject<BackendApiClient>("api")!;

type SourceFilter = "ALL" | ToolboxSource;
type CategoryFilter = "ALL" | ToolboxCategory;

const categoryOptions: Array<{ value: CategoryFilter; label: string }> = [
  { value: "ALL", label: "全部" },
  { value: "SECURITY", label: "安全与加密" },
  { value: "ENCODING", label: "编码与转换" },
  { value: "TEXT", label: "文本处理" },
  { value: "DATA", label: "数据处理" },
  { value: "WEB", label: "Web 工具" },
  { value: "NETWORK", label: "网络工具" },
  { value: "DEVELOPMENT", label: "开发辅助" },
  { value: "IMAGE", label: "图片处理" },
  { value: "AUDIO_VIDEO", label: "音视频" },
  { value: "PDF", label: "PDF" },
  { value: "DATE_TIME", label: "日期与时间" },
  { value: "MATH", label: "数学与测量" },
  { value: "OTHER", label: "其他" }
];

const catalog = ref<ToolboxCatalog | null>(null);
const tools = ref<ToolboxTool[]>([]);
const loading = ref(true);
const loadError = ref("");
const search = ref("");
const source = ref<SourceFilter>("ALL");
const category = ref<CategoryFilter>("ALL");
const localRecency = ref<Record<string, number>>({});
let launchSequence = 0;

const searchAndSourceTools = computed(() => {
  const query = normalize(search.value);
  return tools.value.filter((tool) => {
    if (source.value !== "ALL" && tool.source !== source.value) return false;
    if (!query) return true;
    return normalize([
      tool.nameZh,
      tool.nameEn,
      tool.descriptionZh,
      tool.sourceName,
      tool.categoryLabel,
      ...tool.keywords
    ].join(" ")).includes(query);
  });
});

const categoryCounts = computed(() => {
  // 分类数字只基于搜索词和来源：不能把已选分类带入统计，否则其它分类会被错误归零。
  const counts = new Map<CategoryFilter, number>([["ALL", searchAndSourceTools.value.length]]);
  for (const option of categoryOptions.slice(1)) counts.set(option.value, 0);
  for (const tool of searchAndSourceTools.value) {
    counts.set(tool.category, (counts.get(tool.category) ?? 0) + 1);
  }
  return counts;
});

const filteredTools = computed(() => category.value === "ALL"
  ? searchAndSourceTools.value
  : searchAndSourceTools.value.filter((tool) => tool.category === category.value));

const hotTools = computed(() => tools.value
  .filter((tool) => tool.clickCount > 0)
  .sort((left, right) => {
    if (left.clickCount !== right.clickCount) return right.clickCount - left.clickCount;
    const recency = (localRecency.value[right.toolId] ?? 0) - (localRecency.value[left.toolId] ?? 0);
    if (recency !== 0) return recency;
    return (left.hotRank ?? Number.MAX_SAFE_INTEGER) - (right.hotRank ?? Number.MAX_SAFE_INTEGER);
  })
  .slice(0, catalog.value?.hotLimit ?? 10));

onMounted(() => void loadCatalog());

async function loadCatalog() {
  loading.value = true;
  loadError.value = "";
  try {
    const response = await api.getToolboxCatalog();
    catalog.value = response;
    tools.value = response.tools;
  } catch {
    loadError.value = "工具目录暂时不可用，请稍后重试";
  } finally {
    loading.value = false;
  }
}

function onLinkClick(tool: ToolboxTool, event: MouseEvent) {
  if (event.button === 0) void reportClick(tool);
}

function onLinkAuxClick(tool: ToolboxTool, event: MouseEvent) {
  if (event.button === 1) void reportClick(tool);
}

function clearFilters() {
  search.value = "";
  source.value = "ALL";
  category.value = "ALL";
}

/** 埋点永远不 preventDefault；即使后端不可用，浏览器仍按原生链接语义打开工具。 */
async function reportClick(tool: ToolboxTool) {
  try {
    const result = await api.recordToolboxClick(tool.toolId, createClickEventId());
    applyClickResult(result);
  } catch {
    // 点击统计是旁路能力，失败不弹提示、不重试，也不干扰新标签页。
  }
}

function applyClickResult(result: ToolboxClickResult) {
  if (result.incremented) {
    launchSequence += 1;
    localRecency.value = { ...localRecency.value, [result.toolId]: launchSequence };
  }
  tools.value = tools.value.map((tool) => tool.toolId === result.toolId
    // 并发上报可能乱序返回，目录中的累计值只能前进，不能被较旧响应覆盖。
    ? { ...tool, clickCount: Math.max(tool.clickCount, result.clickCount) }
    : tool);
}

function createClickEventId(): string {
  const randomId = typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`;
  return `tbx_${randomId}`;
}

function normalize(value: string): string {
  return value.trim().toLocaleLowerCase();
}
</script>

<template>
  <section class="toolbox-panel" aria-labelledby="toolbox-title">
    <h1 id="toolbox-title" class="sr-only">工具盒子</h1>

    <div class="toolbox-controls">
      <div class="toolbox-controls__primary">
        <label class="toolbox-search">
          <Search :size="17" aria-hidden="true" />
          <span class="sr-only">搜索工具</span>
          <input v-model="search" type="search" placeholder="搜索中文、英文或关键词" />
        </label>
        <div class="toolbox-source-filter" aria-label="工具来源">
          <button type="button" :class="{ active: source === 'ALL' }" aria-label="查看全部来源" @click="source = 'ALL'">
            全部来源
          </button>
          <button type="button" :class="{ active: source === 'IT_TOOLS' }" aria-label="仅看 IT-Tools" @click="source = 'IT_TOOLS'">
            IT-Tools
          </button>
          <button type="button" :class="{ active: source === 'OMNI_TOOLS' }" aria-label="仅看 OmniTools" @click="source = 'OMNI_TOOLS'">
            OmniTools
          </button>
        </div>
        <button class="toolbox-clear-filter" type="button" @click="clearFilters">清除筛选</button>
      </div>
      <div class="toolbox-category-filter" aria-label="工具分类">
        <button
          v-for="option in categoryOptions"
          :key="option.value"
          type="button"
          :class="{ active: category === option.value }"
          :aria-pressed="category === option.value"
          :disabled="category !== option.value && (categoryCounts.get(option.value) ?? 0) === 0"
          @click="category = option.value"
        >
          {{ option.label }} {{ categoryCounts.get(option.value) ?? 0 }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="toolbox-state">正在装配离线工具目录…</div>
    <div v-else-if="loadError" class="toolbox-state toolbox-state--error" role="alert">
      <p>{{ loadError }}</p>
      <button type="button" @click="loadCatalog">重新加载</button>
    </div>
    <template v-else>
      <section class="toolbox-hot" aria-labelledby="toolbox-hot-title">
        <div class="toolbox-section-heading">
          <div>
            <p class="toolbox-section-kicker"><Flame :size="15" aria-hidden="true" /> LIVE RANKING</p>
            <h2 id="toolbox-hot-title">热门工具</h2>
          </div>
          <span>按累计点击量排序 · Top {{ catalog?.hotLimit ?? 10 }}</span>
        </div>
        <div v-if="hotTools.length > 0" class="toolbox-hot-list">
          <a
            v-for="(tool, index) in hotTools"
            :key="tool.toolId"
            :href="tool.launchPath"
            target="_blank"
            rel="noopener noreferrer"
            :data-testid="`hot-tool-${tool.toolId}`"
            @click="onLinkClick(tool, $event)"
            @auxclick="onLinkAuxClick(tool, $event)"
          >
            <span class="toolbox-hot-rank">#{{ index + 1 }}</span>
            <span class="toolbox-hot-name">{{ tool.nameZh }}</span>
            <span class="toolbox-hot-source">{{ tool.sourceName }}</span>
            <strong>{{ tool.clickCount }}</strong>
          </a>
        </div>
        <div v-else class="toolbox-hot-empty">
          <Flame :size="18" aria-hidden="true" />
          <span>还没有累计点击，打开任一工具后会生成热门排行</span>
        </div>
      </section>

      <section class="toolbox-catalog" aria-labelledby="toolbox-catalog-title">
        <div class="toolbox-section-heading toolbox-section-heading--catalog">
          <div>
            <p class="toolbox-section-kicker"><MousePointerClick :size="15" aria-hidden="true" /> DIRECT LAUNCH</p>
            <h2 id="toolbox-catalog-title">工具目录</h2>
          </div>
          <span>当前显示 {{ filteredTools.length }} / {{ tools.length }}</span>
        </div>

        <div v-if="filteredTools.length > 0" class="toolbox-grid">
          <a
            v-for="tool in filteredTools"
            :key="tool.toolId"
            class="toolbox-card"
            :href="tool.launchPath"
            target="_blank"
            rel="noopener noreferrer"
            :data-testid="`tool-link-${tool.toolId}`"
            @click="onLinkClick(tool, $event)"
            @auxclick="onLinkAuxClick(tool, $event)"
          >
            <div class="toolbox-card__topline">
              <span :class="['toolbox-source-badge', `toolbox-source-badge--${tool.source.toLowerCase()}`]">
                {{ tool.sourceName }}
              </span>
              <ExternalLink :size="16" aria-label="在新标签页打开" />
            </div>
            <div class="toolbox-card__title">
              <h3>{{ tool.nameZh }}</h3>
              <p>{{ tool.nameEn }}</p>
            </div>
            <p class="toolbox-card__description">{{ tool.descriptionZh }}</p>
            <div class="toolbox-card__footer">
              <span>{{ tool.categoryLabel }}</span>
              <span :data-testid="`tool-count-${tool.toolId}`">{{ tool.clickCount }} 次点击</span>
            </div>
          </a>
        </div>
        <div v-else class="toolbox-empty">
          <Search :size="24" aria-hidden="true" />
          <p>没有匹配的离线工具</p>
          <button type="button" @click="clearFilters">清除筛选</button>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.toolbox-panel {
  height: 100%;
  min-height: 0;
  overflow: auto;
  box-sizing: border-box;
  padding: 0 clamp(20px, 3vw, 48px) 52px;
  background: #f3f5f7;
  color: #172033;
}
.toolbox-section-kicker {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0 0 5px;
  color: #f2a93b;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
}
.toolbox-controls {
  position: sticky;
  z-index: 5;
  top: 0;
  display: grid;
  gap: 12px;
  max-width: 1480px;
  margin: 0 auto;
  padding: 14px 0 12px;
  border-bottom: 1px solid #dde2e8;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 8px 24px rgb(36 46 66 / 8%);
}
.toolbox-controls__primary { display: grid; grid-template-columns: minmax(260px, 1fr) auto auto; gap: 12px; }
.toolbox-search { display: flex; gap: 9px; align-items: center; min-height: 40px; padding: 0 13px; border: 1px solid #d8dee7; border-radius: 8px; color: #748094; background: #fff; }
.toolbox-search:focus-within { border-color: #3967a8; box-shadow: 0 0 0 3px rgb(57 103 168 / 12%); }
.toolbox-search input { width: 100%; border: 0; outline: 0; color: #172033; font: inherit; font-size: 13px; background: transparent; }
.toolbox-source-filter { display: flex; align-items: center; padding: 3px; border: 1px solid #d8dee7; border-radius: 8px; background: #f4f6f8; }
.toolbox-source-filter button { min-height: 32px; padding: 0 12px; border: 0; border-radius: 6px; color: #667085; font: inherit; font-size: 12px; background: transparent; cursor: pointer; }
.toolbox-source-filter button.active { color: #172033; background: #fff; box-shadow: 0 1px 3px rgb(16 24 40 / 12%); }
.toolbox-clear-filter,
.toolbox-category-filter button { min-height: 32px; padding: 0 12px; border: 1px solid #d8dee7; border-radius: 999px; color: #667085; font: inherit; font-size: 12px; background: #fff; cursor: pointer; }
.toolbox-clear-filter:hover,
.toolbox-category-filter button:hover:not(:disabled) { border-color: #8ea5c4; color: #172033; }
.toolbox-category-filter { display: flex; flex-wrap: wrap; gap: 8px; }
.toolbox-category-filter button.active { border-color: #31598c; color: #fff; background: #31598c; }
.toolbox-category-filter button:disabled { cursor: not-allowed; opacity: 0.48; }
.toolbox-hot,
.toolbox-catalog { max-width: 1480px; margin: 28px auto 0; }
.toolbox-section-heading { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 12px; }
.toolbox-section-heading h2 { margin: 0; color: #172033; font-size: 18px; letter-spacing: -0.015em; }
.toolbox-section-heading > span { color: #7c8798; font-size: 11px; }
.toolbox-section-heading--catalog { margin-bottom: 14px; }
.toolbox-section-kicker { color: #a76100; }
.toolbox-hot-list { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }
.toolbox-hot-empty { display: flex; gap: 8px; align-items: center; min-height: 48px; padding: 0 14px; border: 1px dashed #d9d3c5; border-radius: 9px; color: #8c7960; font-size: 12px; background: #fffcf5; }
.toolbox-hot-list a { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 8px; align-items: center; min-width: 0; padding: 11px 12px; border: 1px solid #e2ded3; border-radius: 9px; color: inherit; background: #fffcf5; text-decoration: none; transition: transform 150ms ease, border-color 150ms ease; }
.toolbox-hot-list a:hover { transform: translateY(-1px); border-color: #d39a3e; }
.toolbox-hot-rank { color: #a76100; font-size: 11px; font-weight: 800; }
.toolbox-hot-name { overflow: hidden; font-size: 12px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.toolbox-hot-source { grid-column: 2; color: #8c7960; font-size: 10px; }
.toolbox-hot-list strong { grid-row: 1 / span 2; grid-column: 3; font-variant-numeric: tabular-nums; font-size: 13px; }
.toolbox-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.toolbox-card { display: flex; min-height: 190px; flex-direction: column; box-sizing: border-box; padding: 16px; border: 1px solid #dfe4ea; border-radius: 11px; color: inherit; background: #fff; text-decoration: none; box-shadow: 0 3px 10px rgb(23 32 51 / 4%); transition: transform 160ms ease, border-color 160ms ease, box-shadow 160ms ease; }
.toolbox-card:hover,
.toolbox-card:focus-visible { transform: translateY(-2px); border-color: #8ea5c4; outline: none; box-shadow: 0 10px 22px rgb(23 32 51 / 10%); }
.toolbox-card__topline,
.toolbox-card__footer { display: flex; align-items: center; justify-content: space-between; }
.toolbox-card__topline { color: #718096; }
.toolbox-source-badge { padding: 4px 7px; border-radius: 5px; color: #31598c; font-size: 10px; font-weight: 750; background: #edf4fd; }
.toolbox-source-badge--omni_tools { color: #76511e; background: #fff3dc; }
.toolbox-card__title { margin-top: 16px; }
.toolbox-card__title h3 { margin: 0; font-size: 15px; line-height: 1.3; }
.toolbox-card__title p { margin: 4px 0 0; overflow: hidden; color: #778195; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.toolbox-card__description { display: -webkit-box; margin: 13px 0 18px; overflow: hidden; color: #4d596b; font-size: 12px; line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.toolbox-card__footer { margin-top: auto; padding-top: 11px; border-top: 1px solid #edf0f3; color: #7a8594; font-size: 10px; }
.toolbox-state,
.toolbox-empty { display: grid; min-height: 220px; place-items: center; align-content: center; gap: 10px; max-width: 1480px; margin: 28px auto 0; border: 1px dashed #cfd6df; border-radius: 12px; color: #687386; background: #fff; }
.toolbox-state--error { color: #9b3d33; }
.toolbox-state button,
.toolbox-empty button { padding: 7px 12px; border: 1px solid #cbd3de; border-radius: 6px; color: #27364d; background: #fff; cursor: pointer; }
.toolbox-empty p { margin: 0; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
@media (max-width: 1180px) {
  .toolbox-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .toolbox-hot-list { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .toolbox-panel { padding: 16px 14px 36px; }
  .toolbox-controls { padding-top: 0; }
  .toolbox-controls__primary { grid-template-columns: 1fr; }
  .toolbox-source-filter { overflow-x: auto; }
  .toolbox-clear-filter { grid-column: auto; }
  .toolbox-category-filter { flex-wrap: nowrap; overflow-x: auto; padding-bottom: 2px; }
  .toolbox-category-filter button { flex: 0 0 auto; }
  .toolbox-grid { grid-template-columns: 1fr; }
  .toolbox-hot-list { grid-template-columns: 1fr 1fr; }
}
</style>
