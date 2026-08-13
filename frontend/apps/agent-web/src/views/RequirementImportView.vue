<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { createBackendApiClient } from "@test-agent/backend-api";
import type {
  RequirementImportApplication,
  RequirementImportItem,
  RequirementImportResult
} from "@test-agent/shared-types";

type ImportContext = {
  type: "ITA_REQUIREMENT_IMPORT_CONTEXT";
  workspaceId: string;
  defaultAppName?: string;
  defaultVersion?: string;
  requestId: string;
};

const api = createBackendApiClient({
  baseUrl: import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080"
});
const context = ref<ImportContext | null>(null);
const applications = ref<RequirementImportApplication[]>([]);
const selectedApp = ref("");
const selectedVersion = ref("");
const items = ref<RequirementImportItem[]>([]);
const selected = ref(new Set<string>());
const keyword = ref("");
const loadingApplications = ref(false);
const loadingItems = ref(false);
const importing = ref(false);
const errorMessage = ref("");
const result = ref<RequirementImportResult | null>(null);
let itemRequestSequence = 0;

const versions = computed(() => {
  const now = new Date();
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(now.getFullYear(), now.getMonth() + index - 3, 1);
    return `${date.getFullYear()}年${date.getMonth() + 1}月`;
  });
});

const filteredItems = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase();
  if (!query) return items.value;
  return items.value.flatMap((item) => {
    const parentMatches = `${item.itemNo} ${item.itemName}`.toLocaleLowerCase().includes(query);
    const children = parentMatches
      ? item.children
      : item.children.filter((child) => `${child.itemNo} ${child.itemName}`.toLocaleLowerCase().includes(query));
    return children.length > 0 ? [{ ...item, children }] : [];
  });
});

const filteredNumbers = computed(() => filteredItems.value.flatMap((item) => item.children.map((child) => child.itemNo)));
const allFilteredSelected = computed(() => filteredNumbers.value.length > 0
  && filteredNumbers.value.every((number) => selected.value.has(number)));
const someFilteredSelected = computed(() => filteredNumbers.value.some((number) => selected.value.has(number))
  && !allFilteredSelected.value);

function parentState(item: RequirementImportItem) {
  const count = item.children.filter((child) => selected.value.has(child.itemNo)).length;
  return { checked: item.children.length > 0 && count === item.children.length, indeterminate: count > 0 && count < item.children.length };
}

function replaceSelection(numbers: string[], checked: boolean) {
  const next = new Set(selected.value);
  numbers.forEach((number) => checked ? next.add(number) : next.delete(number));
  selected.value = next;
}

function toggleAll(event: Event) {
  replaceSelection(filteredNumbers.value, (event.target as HTMLInputElement).checked);
}

function toggleParent(item: RequirementImportItem, event: Event) {
  replaceSelection(item.children.map((child) => child.itemNo), (event.target as HTMLInputElement).checked);
}

function toggleChild(number: string, event: Event) {
  replaceSelection([number], (event.target as HTMLInputElement).checked);
}

async function loadApplications() {
  loadingApplications.value = true;
  errorMessage.value = "";
  try {
    applications.value = await api.listRequirementImportApplications();
    const expected = context.value?.defaultAppName?.trim();
    selectedApp.value = applications.value.find((application) =>
      application.appName === expected || application.appShortName === expected)?.appShortName
      ?? applications.value[0]?.appShortName
      ?? "";
    selectedVersion.value = versions.value.includes(context.value?.defaultVersion ?? "")
      ? context.value!.defaultVersion!
      : versions.value[3];
    await loadItems();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "TCDS 应用加载失败";
  } finally {
    loadingApplications.value = false;
  }
}

async function loadItems() {
  selected.value = new Set();
  result.value = null;
  errorMessage.value = "";
  const sequence = ++itemRequestSequence;
  if (!selectedApp.value || !selectedVersion.value) {
    items.value = [];
    return;
  }
  loadingItems.value = true;
  try {
    const loaded = await api.listRequirementImportItems(selectedApp.value, selectedVersion.value);
    if (sequence === itemRequestSequence) items.value = loaded;
  } catch (error) {
    if (sequence === itemRequestSequence) {
      items.value = [];
      errorMessage.value = error instanceof Error ? error.message : "TCDS 条目加载失败";
    }
  } finally {
    if (sequence === itemRequestSequence) loadingItems.value = false;
  }
}

async function submitImport() {
  if (!context.value || selected.value.size === 0 || importing.value) return;
  importing.value = true;
  errorMessage.value = "";
  result.value = null;
  try {
    const response = await api.importWorkspaceRequirements({
      workspaceId: context.value.workspaceId,
      appShortName: selectedApp.value,
      editionId: selectedVersion.value,
      selectedSubItemNos: [...selected.value],
      requestId: context.value.requestId
    });
    result.value = response;
    window.parent.postMessage({
      type: "ITA_REQUIREMENT_IMPORT_COMPLETE",
      requestId: context.value.requestId,
      result: response
    }, window.location.origin);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "需求导入失败";
  } finally {
    importing.value = false;
  }
}

function receiveContext(event: MessageEvent) {
  if (event.origin !== window.location.origin || event.source !== window.parent) return;
  const data = event.data as Partial<ImportContext> | null;
  if (data?.type !== "ITA_REQUIREMENT_IMPORT_CONTEXT" || !data.workspaceId || !data.requestId) return;
  context.value = data as ImportContext;
  void loadApplications();
}

onMounted(() => {
  window.addEventListener("message", receiveContext);
  window.parent.postMessage({ type: "ITA_REQUIREMENT_IMPORT_READY" }, window.location.origin);
});

onBeforeUnmount(() => window.removeEventListener("message", receiveContext));
</script>

<template>
  <main class="requirement-import-page">
    <header>
      <div>
        <p class="eyebrow">TCDS · 内部需求</p>
        <h1>导入到测试工作空间</h1>
      </div>
      <span class="selection-count">已选 {{ selected.size }} / 100</span>
    </header>

    <section class="filters">
      <label>应用
        <select v-model="selectedApp" :disabled="loadingApplications || importing" @change="loadItems">
          <option v-for="application in applications" :key="application.appShortName" :value="application.appShortName">
            {{ application.appName }}（{{ application.appShortName }}）
          </option>
        </select>
      </label>
      <label>版本
        <select v-model="selectedVersion" :disabled="loadingApplications || importing" @change="loadItems">
          <option v-for="version in versions" :key="version" :value="version">{{ version }}</option>
        </select>
      </label>
      <label class="search">筛选
        <input v-model="keyword" type="search" placeholder="输入父项、子项编号或名称" />
      </label>
    </section>

    <section class="catalog" :aria-busy="loadingApplications || loadingItems">
      <div class="catalog-toolbar">
        <label class="check-label">
          <input
            type="checkbox"
            :checked="allFilteredSelected"
            :indeterminate.prop="someFilteredSelected"
            :disabled="filteredNumbers.length === 0 || importing"
            @change="toggleAll"
          />
          全选当前筛选结果
        </label>
        <span>{{ filteredNumbers.length }} 个可选子项</span>
      </div>

      <p v-if="!context" class="empty">正在等待工作空间上下文…</p>
      <p v-else-if="loadingApplications || loadingItems" class="empty">正在读取 TCDS 授权目录…</p>
      <p v-else-if="filteredItems.length === 0" class="empty">当前条件下没有可导入条目</p>
      <article v-for="item in filteredItems" v-else :key="item.itemNo" class="parent-item">
        <label class="parent-row check-label">
          <input
            type="checkbox"
            :checked="parentState(item).checked"
            :indeterminate.prop="parentState(item).indeterminate"
            :disabled="importing"
            @change="toggleParent(item, $event)"
          />
          <strong>{{ item.itemNo }}</strong>
          <span>{{ item.itemName }}</span>
        </label>
        <div class="children">
          <label v-for="child in item.children" :key="child.itemNo" class="child-row check-label">
            <input
              type="checkbox"
              :checked="selected.has(child.itemNo)"
              :disabled="importing"
              @change="toggleChild(child.itemNo, $event)"
            />
            <code>{{ child.itemNo }}</code>
            <span>{{ child.itemName }}</span>
          </label>
        </div>
      </article>
    </section>

    <div v-if="errorMessage" class="notice error">{{ errorMessage }}</div>
    <div v-if="result" class="notice" :class="result.status.toLowerCase()">
      {{ result.status === "SUCCEEDED" ? "导入成功" : result.status === "PARTIAL" ? "部分文档导入失败，可重试" : "导入失败" }}：
      新增 {{ result.importedFiles }}，覆盖 {{ result.overwrittenFiles }}，失败 {{ result.failedFiles }}。
      <ul v-if="result.failures.length">
        <li v-for="failure in result.failures" :key="`${failure.fileName}:${failure.code}`">
          {{ failure.fileName }}：{{ failure.message }}
        </li>
      </ul>
    </div>

    <footer>
      <span>目录将写入当前工作空间的 <code>spec/</code>，不会删除其它文件。</span>
      <button :disabled="!context || selected.size === 0 || selected.size > 100 || importing" @click="submitImport">
        {{ importing ? "正在导入…" : `导入 ${selected.size} 个子项` }}
      </button>
    </footer>
  </main>
</template>

<style scoped>
.requirement-import-page { min-height: 100vh; box-sizing: border-box; padding: 28px; color: #18181b; background: #f7f7f5; font: 14px/1.5 Inter, "PingFang SC", sans-serif; }
header, .filters, .catalog-toolbar, footer { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
h1 { margin: 2px 0 0; font-size: 24px; letter-spacing: -.02em; }
.eyebrow { margin: 0; color: #8a1c2c; font-size: 11px; font-weight: 700; letter-spacing: .12em; }
.selection-count { padding: 6px 11px; border-radius: 999px; background: #fff; border: 1px solid #ddd; }
.filters { margin: 22px 0 14px; align-items: end; }
.filters label { display: grid; gap: 6px; color: #52525b; font-size: 12px; font-weight: 600; }
.filters .search { flex: 1; }
select, input[type="search"] { height: 38px; min-width: 190px; padding: 0 11px; border: 1px solid #d4d4d8; border-radius: 8px; background: white; color: #18181b; }
input[type="search"] { width: 100%; box-sizing: border-box; }
.catalog { min-height: 330px; max-height: calc(100vh - 310px); overflow: auto; border: 1px solid #ddd; border-radius: 12px; background: #fff; }
.catalog-toolbar { position: sticky; top: 0; z-index: 1; padding: 12px 16px; border-bottom: 1px solid #e4e4e7; background: rgba(255,255,255,.96); color: #71717a; }
.check-label { display: flex; align-items: center; gap: 9px; cursor: pointer; }
.parent-item + .parent-item { border-top: 1px solid #eee; }
.parent-row { padding: 13px 16px; background: #fafafa; }
.children { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1px; background: #eee; }
.child-row { min-height: 42px; padding: 8px 16px 8px 42px; background: #fff; }
code { color: #7f1d2d; font: 12px ui-monospace, SFMono-Regular, monospace; }
.empty { padding: 90px 20px; text-align: center; color: #71717a; }
.notice { margin-top: 12px; padding: 11px 14px; border-radius: 9px; border: 1px solid #bfdbfe; background: #eff6ff; }
.notice.error, .notice.failed { border-color: #fecaca; background: #fef2f2; color: #991b1b; }
.notice.partial { border-color: #fde68a; background: #fffbeb; color: #92400e; }
.notice.succeeded { border-color: #bbf7d0; background: #f0fdf4; color: #166534; }
.notice ul { margin: 7px 0 0; padding-left: 20px; }
footer { margin-top: 16px; color: #71717a; }
button { min-width: 150px; height: 40px; border: 0; border-radius: 9px; background: #7f1d2d; color: white; font-weight: 700; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .45; }
@media (max-width: 760px) { .filters { align-items: stretch; flex-direction: column; } .filters label, select { width: 100%; } .children { grid-template-columns: 1fr; } }
</style>
