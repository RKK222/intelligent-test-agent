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
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "TCDS 应用加载失败";
    return;
  } finally {
    loadingApplications.value = false;
  }
  // 应用目录返回后立即释放筛选控件；条目请求较慢时用户仍可切换，迟到请求由 sequence 丢弃。
  await loadItems();
}

async function loadItems() {
  selected.value = new Set();
  result.value = null;
  errorMessage.value = "";
  const sequence = ++itemRequestSequence;
  if (!context.value || !selectedApp.value || !selectedVersion.value) {
    items.value = [];
    return;
  }
  loadingItems.value = true;
  try {
    const loaded = await api.listWorkspaceRequirementImportItems(
      context.value.workspaceId,
      selectedApp.value,
      selectedVersion.value
    );
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
    <Transition name="import-mask">
      <div
        v-if="importing"
        class="importing-mask"
        role="status"
        aria-live="polite"
        aria-label="正在生成需求目录和文档"
      >
        <div class="importing-card">
          <span class="importing-spinner" aria-hidden="true" />
          <strong>正在生成需求目录和文档</strong>
          <span>请保持当前窗口打开，完成后会自动刷新文件树。</span>
        </div>
      </div>
    </Transition>

    <section class="filters" aria-label="需求筛选">
      <label class="filter-field">
        <span class="filter-label">版本：</span>
        <el-select
          v-model="selectedVersion"
          class="filter-control"
          aria-label="TCDS 版本"
          filterable
          :disabled="importing"
          @change="loadItems"
        >
          <el-option v-for="version in versions" :key="version" :label="version" :value="version" />
        </el-select>
      </label>
      <label class="filter-field">
        <span class="filter-label">应用：</span>
        <el-select
          v-model="selectedApp"
          class="filter-control"
          aria-label="TCDS 应用"
          placeholder="请选择应用"
          filterable
          :loading="loadingApplications"
          :disabled="importing || applications.length === 0"
          @change="loadItems"
        >
          <el-option
            v-for="application in applications"
            :key="application.appShortName"
            :label="`${application.appName}（${application.appShortName}）`"
            :value="application.appShortName"
          />
        </el-select>
      </label>
      <label class="filter-field search">
        <span class="filter-label">条目信息：</span>
        <input v-model="keyword" class="filter-control search-control" type="search" placeholder="按名字/ID 搜索" />
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
        <span>已选 {{ selected.size }} / 100，当前 {{ filteredNumbers.length }} 个可选子项</span>
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
          <span class="item-name">{{ item.itemName }}</span>
          <span v-if="item.imported !== null && item.imported !== undefined" class="import-status" :class="{ imported: item.imported }">
            {{ item.imported ? "已导入" : "未导入" }}
          </span>
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
            <span class="item-name">{{ child.itemName }}</span>
            <span v-if="child.imported !== null && child.imported !== undefined" class="import-status" :class="{ imported: child.imported }">
              {{ child.imported ? "已导入" : "未导入" }}
            </span>
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
      <button :disabled="!context || selected.size === 0 || selected.size > 100 || importing" @click="submitImport">
        {{ importing ? "正在生成…" : "生成" }}
      </button>
    </footer>
  </main>
</template>

<style scoped>
.requirement-import-page {
  position: relative;
  box-sizing: border-box;
  display: flex;
  height: 100vh;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  padding: 10px;
  color: #606266;
  background: #fff;
  font: 14px/1.5 "PingFang SC", "Microsoft YaHei", Arial, sans-serif;
}

.importing-mask {
  position: absolute;
  z-index: 20;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(245 247 250 / 82%);
  backdrop-filter: blur(2px);
}

.importing-card {
  display: grid;
  min-width: min(320px, calc(100vw - 48px));
  justify-items: center;
  gap: 8px;
  padding: 22px 26px;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 32px rgb(31 45 61 / 14%);
  color: #606266;
  text-align: center;
}

.importing-card strong {
  color: #303133;
  font-size: 15px;
}

.importing-card span:last-child {
  color: #909399;
  font-size: 12px;
}

.importing-spinner {
  width: 30px;
  height: 30px;
  box-sizing: border-box;
  border: 3px solid #d9ecff;
  border-top-color: #409eff;
  border-radius: 50%;
  animation: requirement-import-spin 0.8s linear infinite;
}

.import-mask-enter-active,
.import-mask-leave-active {
  transition: opacity 0.18s ease;
}

.import-mask-enter-from,
.import-mask-leave-to {
  opacity: 0;
}

@keyframes requirement-import-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .importing-spinner {
    animation-duration: 1.6s;
  }

  .import-mask-enter-active,
  .import-mask-leave-active {
    transition: none;
  }
}

.filters,
.catalog-toolbar,
footer {
  display: flex;
  align-items: center;
}

.filters {
  flex-shrink: 0;
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(240px, 1.4fr) minmax(180px, 1fr);
  gap: 18px;
  margin-bottom: 8px;
}

.filter-field {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  color: #606266;
  font-size: 12px;
  white-space: nowrap;
}

.filter-label {
  flex: 0 0 auto;
}

.filter-control {
  min-width: 0;
  flex: 1;
}

.filter-field :deep(.el-select) {
  width: 100%;
}

.filter-field :deep(.el-select__wrapper),
.search-control {
  box-sizing: border-box;
  min-height: 28px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  outline: none;
  background: #fff;
  color: #606266;
  font: inherit;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-control {
  width: 100%;
  height: 28px;
  padding: 0 10px;
}

.search-control:focus {
  border-color: #409eff;
}

.search-control:disabled {
  cursor: not-allowed;
  background: #f5f7fa;
  color: #c0c4cc;
}

.catalog {
  box-sizing: border-box;
  width: 100%;
  min-height: 0;
  min-width: 0;
  flex: 1;
  overflow: auto;
  border: 1px solid #ebeef5;
  background: #fff;
}

.catalog-toolbar {
  box-sizing: border-box;
  width: 100%;
  position: sticky;
  top: 0;
  z-index: 1;
  justify-content: space-between;
  min-height: 32px;
  padding: 0 12px;
  border-bottom: 1px solid #ebeef5;
  background: #f5f7fa;
  color: #909399;
  font-size: 12px;
}

.check-label {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.check-label input[type="checkbox"] {
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
  margin: 0;
  accent-color: #409eff;
}

.check-label:has(input:disabled) {
  cursor: not-allowed;
}

.parent-item {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  border-bottom: 1px solid #ebeef5;
}

.parent-item:last-child {
  border-bottom: 0;
}

.parent-row {
  box-sizing: border-box;
  width: 100%;
  min-height: 34px;
  padding: 0 12px;
  color: #303133;
}

.parent-row strong,
.child-row code {
  flex: 0 0 auto;
  color: inherit;
  font: inherit;
}

.parent-row .item-name,
.child-row .item-name {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.import-status {
  flex: 0 0 auto;
  padding: 1px 6px;
  border: 1px solid #f3d19e;
  border-radius: 3px;
  background: #fdf6ec;
  color: #b88230;
  font-size: 12px;
  line-height: 18px;
}

.import-status.imported {
  border-color: #b3e19d;
  background: #f0f9eb;
  color: #529b2e;
}

.children {
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  border-top: 1px solid #ebeef5;
}

.child-row {
  box-sizing: border-box;
  width: 100%;
  min-height: 34px;
  padding: 0 12px 0 40px;
  border-bottom: 1px solid #f2f3f5;
  background: #fff;
}

.child-row:last-child {
  border-bottom: 0;
}

.child-row:hover {
  background: #f5f7fa;
}

.empty {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 20px;
  color: #909399;
  text-align: center;
}

.notice {
  max-height: 92px;
  flex-shrink: 0;
  overflow: auto;
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px solid #b3d8ff;
  background: #ecf5ff;
  color: #409eff;
}

.notice.error,
.notice.failed {
  border-color: #fbc4c4;
  background: #fef0f0;
  color: #f56c6c;
}

.notice.partial {
  border-color: #f5dab1;
  background: #fdf6ec;
  color: #e6a23c;
}

.notice.succeeded {
  border-color: #c2e7b0;
  background: #f0f9eb;
  color: #67c23a;
}

.notice ul {
  margin: 6px 0 0;
  padding-left: 20px;
}

footer {
  flex-shrink: 0;
  justify-content: flex-end;
  margin-top: 8px;
}

button {
  min-width: 64px;
  height: 28px;
  padding: 0 15px;
  border: 1px solid #409eff;
  border-radius: 4px;
  background: #409eff;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}

button:hover:not(:disabled) {
  border-color: #66b1ff;
  background: #66b1ff;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

@media (max-width: 760px) {
  .requirement-import-page {
    height: auto;
    min-height: 100vh;
    overflow: auto;
  }

  .filters {
    align-items: stretch;
    grid-template-columns: minmax(0, 1fr);
    gap: 6px;
  }

  .filter-field {
    width: 100%;
  }

  .filter-field {
    justify-content: space-between;
  }

  .catalog {
    min-height: 300px;
  }

  .catalog-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
    padding: 6px 12px;
  }
}
</style>
