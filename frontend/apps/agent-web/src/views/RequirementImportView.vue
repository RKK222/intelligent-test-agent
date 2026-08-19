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

type ImportTreeRefreshResult = {
  type: "ITA_REQUIREMENT_IMPORT_TREE_REFRESHED";
  requestId: string;
  success: boolean;
};

const api = createBackendApiClient({
  baseUrl: import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080"
});
const context = ref<ImportContext | null>(null);
const applications = ref<RequirementImportApplication[]>([]);
const selectedApp = ref("");
const selectedAppInput = ref("");
const selectedVersion = ref("");
const selectedVersionInput = ref("");
const items = ref<RequirementImportItem[]>([]);
const selected = ref(new Set<string>());
const keyword = ref("");
const selectedOnly = ref(false);
const loadingApplications = ref(false);
const loadingItems = ref(false);
const importing = ref(false);
const errorMessage = ref("");
const result = ref<RequirementImportResult | null>(null);
const versionMenuOpen = ref(false);
const applicationMenuOpen = ref(false);
const applicationSearchActive = ref(false);
let itemRequestSequence = 0;

function applicationLabel(application: RequirementImportApplication): string {
  return `${application.appName}（${application.appShortName}）`;
}

function resolveApplication(raw: string): RequirementImportApplication | undefined {
  const normalized = raw.trim();
  return applications.value.find((application) =>
    application.appShortName === normalized
    || application.appName === normalized
    || applicationLabel(application) === normalized);
}

const versions = computed(() => {
  const now = new Date();
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(now.getFullYear(), now.getMonth() + index - 3, 1);
    return `${date.getFullYear()}年${date.getMonth() + 1}月`;
  });
});

const versionOptions = computed(() => [...new Set([
  selectedVersion.value.trim(),
  ...versions.value
].filter(Boolean))]);

const filteredApplications = computed(() => {
  const query = selectedAppInput.value.trim().toLocaleLowerCase();
  // 聚焦或主动展开时展示全量建议；一旦用户键入，即使刚好完整命中简称，也必须保持筛选结果。
  if (!applicationSearchActive.value || !query) return applications.value;
  return applications.value.filter((application) =>
    `${application.appName} ${application.appShortName}`.toLocaleLowerCase().includes(query));
});

const filteredItems = computed(() => {
  const tokens = keyword.value.trim().toLocaleLowerCase().split(/\s+/u).filter(Boolean);
  return items.value.flatMap((item) => {
    const parentText = `${item.itemNo} ${item.itemName}`.toLocaleLowerCase();
    const parentMatches = tokens.length > 0 && tokens.every((token) => parentText.includes(token));
    const children = item.children.filter((child) => {
      if (selectedOnly.value && !selected.value.has(child.itemNo)) return false;
      if (tokens.length === 0 || parentMatches) return true;
      const childText = `${parentText} ${child.itemNo} ${child.itemName}`.toLocaleLowerCase();
      return tokens.every((token) => childText.includes(token));
    });
    return children.length > 0 ? [{ ...item, children }] : [];
  });
});

const filteredNumbers = computed(() => filteredItems.value.flatMap((item) => item.children.map((child) => child.itemNo)));
const totalItemCount = computed(() => items.value.reduce((count, item) => count + item.children.length, 0));
const hasItemFilter = computed(() => keyword.value.trim().length > 0 || selectedOnly.value);
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

function clearItemFilters() {
  keyword.value = "";
  selectedOnly.value = false;
}

function selectCurrentInput(event: FocusEvent) {
  (event.target as HTMLInputElement).select();
}

async function loadApplications() {
  loadingApplications.value = true;
  errorMessage.value = "";
  const expected = context.value?.defaultAppName?.trim();
  selectedApp.value = expected ?? "";
  selectedAppInput.value = expected ?? "";
  // 前后 3 个月仅作为输入建议；TCDS 的历史或未来版本仍允许按原值查询。
  selectedVersion.value = context.value?.defaultVersion?.trim() || versions.value[3];
  selectedVersionInput.value = selectedVersion.value;
  let applicationLoadError = "";
  try {
    applications.value = await api.listRequirementImportApplications();
    const application = applications.value.find((candidate) =>
      candidate.appName === expected || candidate.appShortName === expected);
    const fallback = applications.value[0];
    // 父页面传入的应用不要求命中目录，保持旧页面原样查询 TCDS 的兼容行为。
    selectedApp.value = application?.appShortName ?? expected ?? fallback?.appShortName ?? "";
    selectedAppInput.value = application
      ? applicationLabel(application)
      : expected ?? (fallback ? applicationLabel(fallback) : "");
  } catch (error) {
    // 应用目录只是建议，加载失败时仍允许用父页面值或手工输入继续查询。
    applicationLoadError = error instanceof Error ? error.message : "TCDS 应用建议加载失败，可直接输入";
  } finally {
    loadingApplications.value = false;
  }
  // 应用目录返回后立即释放筛选控件；条目请求较慢时用户仍可切换，迟到请求由 sequence 丢弃。
  await loadItems();
  if (applicationLoadError && !errorMessage.value) errorMessage.value = applicationLoadError;
}

async function applyApplicationInput() {
  applicationMenuOpen.value = false;
  applicationSearchActive.value = false;
  const rawApplication = selectedAppInput.value.trim();
  if (!rawApplication) {
    selectedApp.value = "";
    items.value = [];
    selected.value = new Set();
    result.value = null;
    errorMessage.value = "请输入 TCDS 应用名称或简称";
    return;
  }
  const application = resolveApplication(selectedAppInput.value);
  selectedApp.value = application?.appShortName ?? rawApplication;
  selectedAppInput.value = application ? applicationLabel(application) : rawApplication;
  await loadItems();
}

async function applyVersionInput() {
  versionMenuOpen.value = false;
  const rawVersion = selectedVersionInput.value.trim();
  if (!rawVersion) {
    selectedVersion.value = "";
    selectedVersionInput.value = "";
    items.value = [];
    selected.value = new Set();
    result.value = null;
    errorMessage.value = "请输入 TCDS 版本";
    return;
  }
  selectedVersion.value = rawVersion;
  selectedVersionInput.value = rawVersion;
  await loadItems();
}

async function selectApplication(application: RequirementImportApplication) {
  selectedApp.value = application.appShortName;
  selectedAppInput.value = applicationLabel(application);
  applicationMenuOpen.value = false;
  applicationSearchActive.value = false;
  await loadItems();
}

async function selectVersion(version: string) {
  selectedVersion.value = version;
  selectedVersionInput.value = version;
  versionMenuOpen.value = false;
  await loadItems();
}

function openVersionMenu(event: FocusEvent) {
  versionMenuOpen.value = true;
  selectCurrentInput(event);
}

function openApplicationMenu(event?: FocusEvent) {
  applicationSearchActive.value = false;
  applicationMenuOpen.value = true;
  if (event) selectCurrentInput(event);
}

function filterApplicationSuggestions() {
  applicationSearchActive.value = true;
  applicationMenuOpen.value = true;
}

function toggleApplicationMenu() {
  if (applicationMenuOpen.value) {
    applicationMenuOpen.value = false;
    return;
  }
  openApplicationMenu();
}

function cancelVersionInput() {
  selectedVersionInput.value = selectedVersion.value;
  versionMenuOpen.value = false;
}

function cancelApplicationInput() {
  const application = applications.value.find((candidate) => candidate.appShortName === selectedApp.value);
  selectedAppInput.value = application ? applicationLabel(application) : selectedApp.value;
  applicationSearchActive.value = false;
  applicationMenuOpen.value = false;
}

function closeMenuAfterFocusLeaves(menu: "version" | "application", event: FocusEvent) {
  const field = event.currentTarget as HTMLElement;
  if (event.relatedTarget instanceof Node && field.contains(event.relatedTarget)) return;
  if (menu === "version") versionMenuOpen.value = false;
  else applicationMenuOpen.value = false;
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
    // 成功和部分成功都要等父工作台完成定向文件树刷新；FAILED 没有可展开的成功结果。
    if (response.status === "FAILED") importing.value = false;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "需求导入失败";
    importing.value = false;
  }
}

function receiveParentMessage(event: MessageEvent) {
  if (event.origin !== window.location.origin || event.source !== window.parent) return;
  const data = event.data as Record<string, unknown> | null;
  if (data?.type === "ITA_REQUIREMENT_IMPORT_TREE_REFRESHED") {
    const refresh = data as Partial<ImportTreeRefreshResult>;
    if (!context.value || refresh.requestId !== context.value.requestId) return;
    importing.value = false;
    if (!refresh.success) {
      errorMessage.value = "需求已写入，但文件树刷新失败；请关闭弹窗后手动刷新文件树。";
    }
    return;
  }
  if (
    data?.type !== "ITA_REQUIREMENT_IMPORT_CONTEXT"
    || typeof data.workspaceId !== "string"
    || !data.workspaceId
    || typeof data.requestId !== "string"
    || !data.requestId
  ) return;
  context.value = data as ImportContext;
  void loadApplications();
}

onMounted(() => {
  window.addEventListener("message", receiveParentMessage);
  window.parent.postMessage({ type: "ITA_REQUIREMENT_IMPORT_READY" }, window.location.origin);
});

onBeforeUnmount(() => window.removeEventListener("message", receiveParentMessage));
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
      <div class="filter-field" @focusout="closeMenuAfterFocusLeaves('version', $event)">
        <span class="filter-label">版本：</span>
        <div class="filter-control combobox">
          <input
            v-model="selectedVersionInput"
            class="filter-input"
            type="text"
            role="combobox"
            aria-label="TCDS 版本"
            aria-controls="requirement-import-version-options"
            :aria-expanded="versionMenuOpen"
            placeholder="可输入任意 TCDS 版本"
            autocomplete="off"
            :disabled="importing"
            @focus="openVersionMenu"
            @input="versionMenuOpen = true"
            @change="applyVersionInput"
            @keydown.enter.prevent="applyVersionInput"
            @keydown.esc="cancelVersionInput"
          />
          <button
            class="combobox-toggle"
            type="button"
            aria-label="展开版本快捷选项"
            :disabled="importing"
            @click="versionMenuOpen = !versionMenuOpen"
          >⌄</button>
          <ul
            v-if="versionMenuOpen"
            id="requirement-import-version-options"
            class="filter-dropdown"
            role="listbox"
            aria-label="版本快捷选项"
          >
            <li
              v-for="version in versionOptions"
              :key="version"
              role="option"
              :aria-selected="version === selectedVersion"
            >
              <button type="button" @click="selectVersion(version)">
                <span>{{ version }}</span>
                <small v-if="version === selectedVersion">当前</small>
              </button>
            </li>
          </ul>
        </div>
      </div>
      <div class="filter-field" @focusout="closeMenuAfterFocusLeaves('application', $event)">
        <span class="filter-label">应用：</span>
        <div class="filter-control combobox">
          <input
            v-model="selectedAppInput"
            class="filter-input"
            type="text"
            role="combobox"
            aria-label="TCDS 应用"
            aria-controls="requirement-import-application-options"
            :aria-expanded="applicationMenuOpen"
            placeholder="可输入或选择 TCDS 应用"
            autocomplete="off"
            :disabled="importing"
            @focus="openApplicationMenu"
            @input="filterApplicationSuggestions"
            @change="applyApplicationInput"
            @keydown.enter.prevent="applyApplicationInput"
            @keydown.esc="cancelApplicationInput"
          />
          <button
            class="combobox-toggle"
            type="button"
            aria-label="展开 TCDS 应用建议"
            :disabled="importing"
            @click="toggleApplicationMenu"
          >⌄</button>
          <ul
            v-if="applicationMenuOpen"
            id="requirement-import-application-options"
            class="filter-dropdown application-options"
            role="listbox"
            aria-label="TCDS 应用建议"
          >
            <li
              v-for="application in filteredApplications"
              :key="application.appShortName"
              role="option"
              :aria-selected="application.appShortName === selectedApp"
            >
              <button type="button" @click="selectApplication(application)">
                <span>{{ application.appName }}</span>
                <code>{{ application.appShortName }}</code>
              </button>
            </li>
            <li v-if="filteredApplications.length === 0" class="dropdown-empty">没有匹配建议，可直接输入</li>
          </ul>
        </div>
      </div>
      <label class="filter-field search">
        <span class="filter-label">子条目：</span>
        <input
          v-model="keyword"
          class="filter-control search-control"
          type="search"
          aria-label="筛选需求子条目"
          placeholder="名称或编号，空格分隔"
          @keydown.esc="keyword = ''"
        />
      </label>
    </section>

    <section class="catalog" :aria-busy="loadingApplications || loadingItems">
      <div class="catalog-toolbar">
        <div class="toolbar-actions">
          <label class="check-label select-all-toggle">
            <input
              type="checkbox"
              :checked="allFilteredSelected"
              :indeterminate.prop="someFilteredSelected"
              :disabled="filteredNumbers.length === 0 || importing"
              @change="toggleAll"
            />
            全选当前结果
          </label>
          <label class="check-label selected-only-toggle">
            <input v-model="selectedOnly" type="checkbox" aria-label="仅显示已选子条目" :disabled="importing" />
            仅看已选
          </label>
          <button v-if="hasItemFilter" class="toolbar-link" type="button" :disabled="importing" @click="clearItemFilters">
            清空筛选
          </button>
        </div>
        <span class="catalog-summary">已选 {{ selected.size }} / 100 · 显示 {{ filteredNumbers.length }} / {{ totalItemCount }}</span>
      </div>

      <p v-if="!context" class="empty">正在等待工作空间上下文…</p>
      <p v-else-if="loadingApplications || loadingItems" class="empty">正在读取 TCDS 条目…</p>
      <p v-else-if="items.length === 0" class="empty">当前应用和版本没有可导入子条目</p>
      <p v-else-if="filteredItems.length === 0" class="empty">
        {{ selectedOnly ? "没有符合当前搜索条件的已选子条目" : "没有匹配的子条目，请调整名称或编号" }}
      </p>
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

.combobox {
  position: relative;
  display: flex;
}

.filter-input,
.search-control {
  box-sizing: border-box;
  width: 100%;
  height: 28px;
  min-height: 28px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  outline: none;
  background: #fff;
  color: #606266;
  font: inherit;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.combobox .filter-input {
  padding-right: 30px;
}

.combobox-toggle {
  position: absolute;
  top: 1px;
  right: 1px;
  width: 28px;
  height: 26px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #909399;
  cursor: pointer;
  font-family: inherit;
  font-size: 16px;
  line-height: 26px;
}

.combobox-toggle:disabled {
  cursor: not-allowed;
}

.filter-dropdown {
  position: absolute;
  z-index: 8;
  top: calc(100% + 4px);
  right: 0;
  left: 0;
  max-height: min(240px, calc(100vh - 96px));
  margin: 0;
  padding: 4px 0;
  overflow: auto;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 6px 18px rgb(31 45 61 / 14%);
  list-style: none;
}

.filter-dropdown button {
  box-sizing: border-box;
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 6px 10px;
  overflow: hidden;
  border: 0;
  background: transparent;
  color: #606266;
  cursor: pointer;
  font: inherit;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-dropdown button span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.filter-dropdown button code,
.filter-dropdown button small {
  flex: 0 0 auto;
  color: #909399;
  font: inherit;
  font-size: 11px;
}

.filter-dropdown button:hover,
.filter-dropdown [aria-selected="true"] button {
  background: #ecf5ff;
  color: #409eff;
}

.dropdown-empty {
  padding: 6px 10px;
  color: #909399;
}

.search-control {
  appearance: none;
}

.filter-input:focus,
.search-control:focus {
  border-color: #409eff;
  box-shadow: 0 0 0 1px rgb(64 158 255 / 16%);
}

.filter-input:disabled,
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
  gap: 12px;
  justify-content: space-between;
  min-height: 36px;
  padding: 4px 12px;
  border-bottom: 1px solid #ebeef5;
  background: #f5f7fa;
  color: #909399;
  font-size: 12px;
}

.toolbar-actions {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
}

.selected-only-toggle {
  color: #606266;
}

.toolbar-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: #409eff;
  cursor: pointer;
  font: inherit;
}

.toolbar-link:disabled {
  color: #c0c4cc;
  cursor: not-allowed;
}

.catalog-summary {
  flex: 0 0 auto;
  white-space: nowrap;
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
