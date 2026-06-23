# 设置弹窗重构：Element Plus 风格实现应用/工作区/SSH 功能

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将新版设置功能（应用与工作区、个人 SSH key）移植回 `settings/` 目录的旧版 Element Plus 风格组件中。

**Architecture:** 修改旧版 `settings/SettingsDialog.vue` 的 Props 接口以匹配 `AgentWorkbench.vue` 的调用方式（`:open`/`@close`），替换菜单为两个新项。通过 `settings/SettingsMenu.vue` 定义菜单，`settings/SettingsPanel.vue` 动态渲染面板。新建 `SettingsAppWorkspacePanel.vue`（含 3 个子选项卡）和 `SettingsPersonalPanel.vue`（SSH key 管理）。API 客户端在父组件创建，通过 `provide`/`inject` 下发。AgentWorkbench.vue 改回旧路径。

**Tech Stack:** Vue 3, Element Plus, TypeScript, `@test-agent/backend-api`, `@test-agent/shared-types`

---

### Task 1: 修改 SettingsMenu.vue — 菜单项改为「应用与工作区」「个人设置」

**Files:**
- Modify: `frontend/apps/agent-web/src/components/settings/SettingsMenu.vue`

- [ ] **Step 1: 修改 SettingsMenu.vue**

将 `MenuKey` 类型从 4 项改为 2 项，更新 `items` 数组：

```vue
<script setup lang="ts">
import { Setting, User } from "@element-plus/icons-vue";

type MenuKey = "appWorkspace" | "personal";

defineProps<{
  activeKey: MenuKey;
}>();

const emit = defineEmits<{
  (e: "select", key: MenuKey): void;
}>();

const items: Array<{ key: MenuKey; label: string; icon: typeof Setting }> = [
  { key: "appWorkspace", label: "应用与工作区", icon: Setting },
  { key: "personal", label: "个人设置", icon: User }
];
</script>

<template>
  <nav class="ta-settings-menu" aria-label="设置导航">
    <ul class="ta-settings-menu-list">
      <li
        v-for="item in items"
        :key="item.key"
        :class="['ta-settings-menu-item', { 'is-active': activeKey === item.key }]"
        role="button"
        tabindex="0"
        @click="emit('select', item.key)"
        @keydown.enter="emit('select', item.key)"
        @keydown.space.prevent="emit('select', item.key)"
      >
        <el-icon class="ta-settings-menu-icon">
          <component :is="item.icon" />
        </el-icon>
        <span class="ta-settings-menu-label">{{ item.label }}</span>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
/* 保持原有样式不变 */
.ta-settings-menu {
  width: 200px;
  flex-shrink: 0;
  height: 100%;
  min-height: 0;
  padding: 12px 8px;
  border-right: 1px solid #ebeef5;
  background: #fafafa;
  border-top-left-radius: 4px;
  border-bottom-left-radius: 4px;
  box-sizing: border-box;
  overflow-y: auto;
}
.ta-settings-menu-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ta-settings-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
  padding: 0 12px;
  border-radius: 6px;
  cursor: pointer;
  color: #4c4d4f;
  font-size: 13px;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  transition: background-color 0.12s ease, color 0.12s ease;
  outline: none;
}
.ta-settings-menu-item:hover,
.ta-settings-menu-item:focus-visible {
  background: #f0f0f0;
  color: #18181b;
}
.ta-settings-menu-item.is-active {
  background: #e8f0ff;
  color: #3366ff;
  font-weight: 500;
}
.ta-settings-menu-icon { font-size: 16px; }
.ta-settings-menu-label { flex: 1; min-width: 0; }
</style>
```

- [ ] **Step 2: 验证无语法错误**

Run: `cd frontend/apps/agent-web && npx vite build 2>&1 | tail -5`
Expected: 构建成功

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/agent-web/src/components/settings/SettingsMenu.vue
git commit -m "refactor(settings): 替换菜单项为应用与工作区和个人设置"
```

---

### Task 2: 修改 SettingsDialog.vue — 更新 Props/Events 接口，注入 API 客户端

**Files:**
- Modify: `frontend/apps/agent-web/src/components/settings/SettingsDialog.vue`

- [ ] **Step 1: 重写 SettingsDialog.vue**

Props 改为 `{ open: boolean; currentUser: CurrentUser | null }`，事件改为 `(e: 'close'): void`。
创建 API 客户端实例，通过 `provide` 注入。高度调至 520px。

```vue
<script setup lang="ts">
import { provide, ref, watch } from "vue";
import { createBackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser } from "@test-agent/shared-types";
import SettingsMenu from "./SettingsMenu.vue";
import SettingsPanel from "./SettingsPanel.vue";

type MenuKey = "appWorkspace" | "personal";

const props = defineProps<{
  open: boolean;
  currentUser: CurrentUser | null;
}>();

const emit = defineEmits<{
  (e: "close"): void;
}>();

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: apiBaseUrl });
provide("api", api);

const activeKey = ref<MenuKey>("appWorkspace");

watch(
  () => props.open,
  (open) => {
    if (open) {
      activeKey.value = "appWorkspace";
    }
  }
);

function close() {
  emit("close");
}

function selectMenu(key: MenuKey) {
  activeKey.value = key;
}
</script>

<template>
  <el-dialog
    :model-value="open"
    title="设置"
    width="960px"
    align-center
    :close-on-click-modal="false"
    class="ta-settings-dialog"
    @update:model-value="(v: boolean) => { if (!v) close() }"
  >
    <div class="ta-settings-shell">
      <SettingsMenu :active-key="activeKey" @select="selectMenu" />
      <div class="ta-settings-content">
        <SettingsPanel :active-key="activeKey" :current-user="currentUser" />
      </div>
    </div>
    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.ta-settings-shell {
  display: flex;
  height: 520px;
  border-top: 1px solid #ebeef5;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
  overflow: hidden;
}
.ta-settings-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>

<style>
.el-dialog.ta-settings-dialog {
  max-height: calc(100vh - 80px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.el-dialog.ta-settings-dialog .el-dialog__body {
  padding: 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
```

- [ ] **Step 2: 验证构建**

Run: `cd frontend/apps/agent-web && npx vite build 2>&1 | tail -5`
Expected: 构建成功

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/agent-web/src/components/settings/SettingsDialog.vue
git commit -m "refactor(settings): 更新 SettingsDialog Props/Events 接口，注入 API 客户端"
```

---

### Task 3: 修改 SettingsPanel.vue — panels 字典指向新面板

**Files:**
- Modify: `frontend/apps/agent-web/src/components/settings/SettingsPanel.vue`

- [ ] **Step 1: 重写 SettingsPanel.vue**

panels 字典改为 `appWorkspace` 和 `personal` 两项。接收 `currentUser` prop 并传递给面板组件。

```vue
<script setup lang="ts">
import { computed, type Component } from "vue";
import type { CurrentUser } from "@test-agent/shared-types";
import SettingsAppWorkspacePanel from "./SettingsAppWorkspacePanel.vue";
import SettingsPersonalPanel from "./SettingsPersonalPanel.vue";

type PanelDef = { title: string; component: Component };

const props = defineProps<{
  activeKey: string;
  currentUser: CurrentUser | null;
}>();

const panels: Record<string, PanelDef> = {
  appWorkspace: { title: "应用与工作区", component: SettingsAppWorkspacePanel },
  personal: { title: "个人设置", component: SettingsPersonalPanel }
};

const current = computed<PanelDef>(() => panels[props.activeKey] ?? panels.appWorkspace);
</script>

<template>
  <div class="ta-settings-panel">
    <header class="ta-settings-panel-header">
      <h3 class="ta-settings-panel-title">{{ current.title }}</h3>
    </header>
    <div class="ta-settings-panel-body">
      <component :is="current.component" :current-user="currentUser" />
    </div>
  </div>
</template>

<style scoped>
.ta-settings-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.ta-settings-panel-header {
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 20px;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
}
.ta-settings-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
  margin: 0;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}
.ta-settings-panel-body {
  flex: 1;
  min-height: 0;
  padding: 20px 24px;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/apps/agent-web/src/components/settings/SettingsPanel.vue
git commit -m "refactor(settings): 面板路由指向新组件，传递 currentUser"
```

---

### Task 4: 创建 SettingsAppWorkspacePanel.vue — 应用与工作区面板

**Files:**
- Create: `frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue`

- [ ] **Step 1: 创建 SettingsAppWorkspacePanel.vue**

包含从新 `SettingsDialog.vue` 移植的应用管理功能。使用 `inject("api")` 获取 API 客户端。
接收 `currentUser` prop 用于权限判断。内部有 3 个子选项卡：members / repositories / workspaces。

```vue
<script setup lang="ts">
import { computed, inject, ref, watch } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  ApplicationDefinition,
  ApplicationMember,
  ApplicationWorkspaceConfig,
  CodeRepositoryConfig,
  CurrentUser,
  PlatformUserSummary
} from "@test-agent/shared-types";
import { UserPlus, Link2, Trash2 } from "@element-plus/icons-vue";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;

const appTab = ref<"members" | "repositories" | "workspaces">("members");
const loading = ref(false);
const errorMessage = ref("");

// 应用选择
const applications = ref<ApplicationDefinition[]>([]);
const selectedAppId = ref("");

// 权限
const currentRoles = computed(() => props.currentUser?.roles ?? []);
const currentRoleLabel = computed(() => (currentRoles.value.length ? currentRoles.value.join(",") : "无角色"));
const hasAppSettingsPermission = computed(() => currentRoles.value.includes("APP_ADMIN") || currentRoles.value.includes("SUPER_ADMIN"));
const selectedApp = computed(() => applications.value.find((item) => item.appId === selectedAppId.value));

// 成员管理
const members = ref<ApplicationMember[]>([]);
const userKeyword = ref("");
const users = ref<PlatformUserSummary[]>([]);
const memberUserId = ref("");

// 代码库
const repositories = ref<CodeRepositoryConfig[]>([]);
const appRepositories = ref<CodeRepositoryConfig[]>([]);
const repoGitUrl = ref("");
const repoName = ref("");
const repoStandard = ref(false);
const linkRepositoryId = ref("");
const editRepositoryId = ref("");
const editRepositoryName = ref("");
const editRepositoryStandard = ref(false);
const selectedRepositoryForApps = ref("");
const repositoryApplications = ref<ApplicationDefinition[]>([]);
const linkAppId = ref("");

// 工作空间
const workspaces = ref<ApplicationWorkspaceConfig[]>([]);
const workspaceRepositoryId = ref("");
const branches = ref<string[]>([]);
const workspaceBranch = ref("");
const directories = ref<string[]>([]);
const workspaceDirectory = ref("");
const workspaceName = ref("");

async function run(action: () => Promise<void>) {
  loading.value = true;
  errorMessage.value = "";
  try {
    await action();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "操作失败";
  } finally {
    loading.value = false;
  }
}

// 加载应用列表
async function loadApplications() {
  await run(async () => {
    applications.value = await api.listApplications(true);
    if (!selectedAppId.value || !applications.value.some((item) => item.appId === selectedAppId.value)) {
      selectedAppId.value = applications.value[0]?.appId ?? "";
    }
    if (selectedAppId.value) {
      await loadAppContext();
    }
  });
}

function clearAppContext() {
  applications.value = [];
  selectedAppId.value = "";
  members.value = [];
  users.value = [];
  repositories.value = [];
  appRepositories.value = [];
  repositoryApplications.value = [];
  workspaces.value = [];
  branches.value = [];
  directories.value = [];
}

async function loadAppContext() {
  await Promise.all([loadMembers(), loadRepositories(), loadWorkspaces()]);
}

// 成员管理
async function loadMembers() {
  if (!selectedAppId.value) { members.value = []; return; }
  members.value = await api.listApplicationMembers(selectedAppId.value);
}

async function searchUsers() {
  await run(async () => {
    const page = await api.searchUsers(userKeyword.value.trim() || undefined, 1, 20);
    users.value = page.items;
  });
}

async function addMember() {
  await run(async () => {
    await api.addApplicationMember(selectedAppId.value, memberUserId.value.trim());
    memberUserId.value = "";
    await loadMembers();
  });
}

async function removeMember(userId: string) {
  await run(async () => {
    await api.removeApplicationMember(selectedAppId.value, userId);
    await loadMembers();
  });
}

// 代码库管理
async function loadRepositories() {
  const [all, linked] = await Promise.all([
    api.listRepositories(1, 100),
    selectedAppId.value ? api.listApplicationRepositories(selectedAppId.value) : Promise.resolve([])
  ]);
  repositories.value = all.items;
  appRepositories.value = linked;
  if (!workspaceRepositoryId.value || !linked.some((item) => item.repositoryId === workspaceRepositoryId.value)) {
    workspaceRepositoryId.value = linked[0]?.repositoryId ?? "";
  }
  if (!selectedRepositoryForApps.value && all.items[0]) {
    selectedRepositoryForApps.value = all.items[0].repositoryId;
  }
  if (selectedRepositoryForApps.value) {
    repositoryApplications.value = await api.listRepositoryApplications(selectedRepositoryForApps.value);
  }
}

async function createRepository() {
  await run(async () => {
    const repository = await api.createRepository({
      gitUrl: repoGitUrl.value.trim(),
      name: repoName.value.trim(),
      standard: repoStandard.value
    });
    repoGitUrl.value = "";
    repoName.value = "";
    repoStandard.value = false;
    linkRepositoryId.value = repository.repositoryId;
    await loadRepositories();
  });
}

function startEditRepository(repository: CodeRepositoryConfig) {
  editRepositoryId.value = repository.repositoryId;
  editRepositoryName.value = repository.name;
  editRepositoryStandard.value = repository.standard;
}

async function saveRepository() {
  await run(async () => {
    await api.updateRepository(editRepositoryId.value, {
      name: editRepositoryName.value.trim(),
      standard: editRepositoryStandard.value
    });
    editRepositoryId.value = "";
    await loadRepositories();
  });
}

async function linkRepository() {
  await run(async () => {
    await api.linkApplicationRepository(selectedAppId.value, linkRepositoryId.value);
    await loadRepositories();
  });
}

async function unlinkRepository(repositoryId: string) {
  await run(async () => {
    await api.unlinkApplicationRepository(selectedAppId.value, repositoryId);
    await loadRepositories();
    await loadWorkspaces();
  });
}

async function loadRepositoryApplications() {
  if (!selectedRepositoryForApps.value) { repositoryApplications.value = []; return; }
  await run(async () => {
    repositoryApplications.value = await api.listRepositoryApplications(selectedRepositoryForApps.value);
  });
}

async function linkApplication() {
  await run(async () => {
    await api.linkRepositoryApplication(selectedRepositoryForApps.value, linkAppId.value.trim());
    linkAppId.value = "";
    await loadRepositoryApplications();
    await loadRepositories();
  });
}

async function unlinkApplication(appId: string) {
  await run(async () => {
    await api.unlinkRepositoryApplication(selectedRepositoryForApps.value, appId);
    await loadRepositoryApplications();
    await loadRepositories();
  });
}

// 工作空间管理
async function loadWorkspaces() {
  workspaces.value = selectedAppId.value ? await api.listApplicationWorkspaces(selectedAppId.value) : [];
}

async function loadBranches() {
  await run(async () => {
    branches.value = workspaceRepositoryId.value ? await api.listRepositoryBranches(workspaceRepositoryId.value) : [];
    workspaceBranch.value = branches.value[0] ?? "";
    directories.value = [];
    workspaceDirectory.value = "";
  });
}

async function loadDirectories() {
  await run(async () => {
    directories.value =
      workspaceRepositoryId.value && workspaceBranch.value
        ? await api.listRepositoryDirectories(workspaceRepositoryId.value, workspaceBranch.value)
        : [];
    workspaceDirectory.value = directories.value[0] ?? "";
  });
}

async function createWorkspace() {
  await run(async () => {
    await api.createApplicationWorkspace(selectedAppId.value, {
      repositoryId: workspaceRepositoryId.value,
      branch: workspaceBranch.value,
      directoryPath: workspaceDirectory.value,
      workspaceName: workspaceName.value.trim() || undefined
    });
    workspaceName.value = "";
    await loadWorkspaces();
  });
}

async function renameWorkspace(workspace: ApplicationWorkspaceConfig) {
  const nextName = window.prompt("工作空间名称", workspace.workspaceName);
  if (!nextName || !nextName.trim()) return;
  await run(async () => {
    await api.renameApplicationWorkspace(selectedAppId.value, workspace.workspaceId, { workspaceName: nextName.trim() });
    await loadWorkspaces();
  });
}

async function deleteWorkspace(workspaceId: string) {
  await run(async () => {
    await api.deleteApplicationWorkspace(selectedAppId.value, workspaceId);
    await loadWorkspaces();
  });
}

// 初始加载
watch(() => props.currentUser, async (user) => {
  if (user && hasAppSettingsPermission.value) {
    await loadApplications();
  } else {
    clearAppContext();
  }
}, { immediate: true });

watch(selectedAppId, async (appId) => {
  if (!appId || !hasAppSettingsPermission.value) return;
  await loadAppContext();
});
</script>

<template>
  <div class="ta-settings-app-workspace">
    <!-- 权限不足提示 -->
    <div v-if="!hasAppSettingsPermission" class="ta-permission-placeholder">
      <el-alert :title="`您当前角色[${currentRoleLabel}]无该项设置权限。`" type="warning" :closable="false" show-icon />
    </div>

    <template v-else>
      <!-- 应用选择 -->
      <div class="ta-app-selector" v-if="applications.length">
        <el-select v-model="selectedAppId" placeholder="选择应用" style="width: 320px">
          <el-option v-for="app in applications" :key="app.appId" :label="app.appName" :value="app.appId" />
        </el-select>
      </div>

      <!-- 子选项卡 -->
      <div class="ta-sub-tabs" v-if="selectedAppId">
        <el-radio-group v-model="appTab" class="ta-sub-tab-group">
          <el-radio-button value="members">应用人员管理</el-radio-button>
          <el-radio-button value="repositories">应用与代码库关联</el-radio-button>
          <el-radio-button value="workspaces">工作空间管理</el-radio-button>
        </el-radio-group>
      </div>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" />

      <!-- 成员管理 -->
      <div v-if="selectedAppId && appTab === 'members'" class="ta-panel-content">
        <div class="ta-section">
          <h4 class="ta-section-title">搜索用户</h4>
          <div class="ta-inline-form">
            <el-input v-model="userKeyword" placeholder="用户名或统一认证号" style="width: 240px" @keyup.enter="searchUsers" />
            <el-button type="primary" :disabled="loading" @click="searchUsers">搜索</el-button>
          </div>
          <div v-if="users.length" class="ta-item-list">
            <div v-for="user in users" :key="user.userId" class="ta-item-row">
              <span>{{ user.username }} · {{ user.unifiedAuthId }}</span>
              <el-button size="small" :disabled="loading" @click="memberUserId = user.userId; addMember()">
                <el-icon><UserPlus /></el-icon> 加入
              </el-button>
            </div>
          </div>
        </div>

        <div class="ta-section">
          <h4 class="ta-section-title">按 ID 新增成员</h4>
          <div class="ta-inline-form">
            <el-input v-model="memberUserId" placeholder="用户 ID" style="width: 240px" @keyup.enter="addMember" />
            <el-button type="primary" :disabled="loading || !memberUserId.trim()" @click="addMember">新增</el-button>
          </div>
          <div class="ta-item-list">
            <div v-for="member in members" :key="member.userId" class="ta-item-row">
              <div>
                <div class="ta-item-title">{{ member.username }}</div>
                <div class="ta-item-subtitle">{{ member.userId }} · {{ member.unifiedAuthId }}</div>
              </div>
              <el-button size="small" type="danger" plain :disabled="loading" @click="removeMember(member.userId)">
                <el-icon><Trash2 /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 代码库管理 -->
      <div v-if="selectedAppId && appTab === 'repositories'" class="ta-panel-content">
        <div class="ta-section">
          <h4 class="ta-section-title">新增代码库</h4>
          <div class="ta-inline-form">
            <el-input v-model="repoGitUrl" placeholder="Git URL" style="width: 240px" />
            <el-input v-model="repoName" placeholder="中文名称" style="width: 160px" />
            <el-checkbox v-model="repoStandard">标准库</el-checkbox>
            <el-button type="primary" :disabled="loading" @click="createRepository">新增</el-button>
          </div>
        </div>

        <div class="ta-section">
          <h4 class="ta-section-title">关联代码库到当前应用</h4>
          <div class="ta-inline-form">
            <el-select v-model="linkRepositoryId" placeholder="选择代码库" style="width: 240px">
              <el-option v-for="repo in repositories" :key="repo.repositoryId" :label="repo.name" :value="repo.repositoryId" />
            </el-select>
            <el-button type="primary" :disabled="loading || !linkRepositoryId" @click="linkRepository">
              <el-icon><Link2 /></el-icon> 关联
            </el-button>
          </div>
          <div class="ta-item-list">
            <div v-for="repo in appRepositories" :key="repo.repositoryId" class="ta-item-row">
              <div>
                <div class="ta-item-title">{{ repo.name }}</div>
                <div class="ta-item-subtitle">{{ repo.gitUrl }}</div>
              </div>
              <el-button size="small" type="danger" plain :disabled="loading" @click="unlinkRepository(repo.repositoryId)">解除</el-button>
            </div>
          </div>
        </div>

        <div class="ta-section">
          <h4 class="ta-section-title">代码库与应用双向关联</h4>
          <div class="ta-inline-form">
            <el-select v-model="selectedRepositoryForApps" placeholder="选择代码库" style="width: 240px" @change="loadRepositoryApplications">
              <el-option v-for="repo in repositories" :key="repo.repositoryId" :label="repo.name" :value="repo.repositoryId" />
            </el-select>
            <el-button :disabled="loading || !selectedRepositoryForApps" @click="loadRepositoryApplications">刷新</el-button>
          </div>
          <div class="ta-inline-form">
            <el-input v-model="linkAppId" placeholder="应用 ID" style="width: 240px" @keyup.enter="linkApplication" />
            <el-button type="primary" :disabled="loading || !selectedRepositoryForApps || !linkAppId.trim()" @click="linkApplication">关联应用</el-button>
          </div>
          <div class="ta-item-list">
            <div v-for="app in repositoryApplications" :key="app.appId" class="ta-item-row">
              <span>{{ app.appName }} · {{ app.appId }}</span>
              <el-button size="small" type="danger" plain :disabled="loading" @click="unlinkApplication(app.appId)">解除</el-button>
            </div>
          </div>
        </div>

        <div class="ta-section">
          <h4 class="ta-section-title">编辑代码库</h4>
          <div v-for="repo in repositories" :key="repo.repositoryId" class="ta-item-row ta-edit-item">
            <div>
              <span class="ta-item-title">{{ repo.name }}</span>
              <span v-if="repo.standard" class="ta-item-badge">标准库</span>
              <div class="ta-item-subtitle">{{ repo.gitUrl }}</div>
            </div>
            <el-button size="small" @click="startEditRepository(repo)">编辑</el-button>
          </div>
          <div v-if="editRepositoryId" class="ta-inline-form ta-edit-form">
            <el-input v-model="editRepositoryName" placeholder="名称" style="width: 200px" />
            <el-checkbox v-model="editRepositoryStandard">标准库</el-checkbox>
            <el-button type="primary" :disabled="loading" @click="saveRepository">保存</el-button>
          </div>
        </div>
      </div>

      <!-- 工作空间管理 -->
      <div v-if="selectedAppId && appTab === 'workspaces'" class="ta-panel-content">
        <div class="ta-section">
          <h4 class="ta-section-title">创建工作空间</h4>
          <div class="ta-inline-form">
            <el-select v-model="workspaceRepositoryId" placeholder="选择已关联代码库" style="width: 200px">
              <el-option v-for="repo in appRepositories" :key="repo.repositoryId" :label="repo.name" :value="repo.repositoryId" />
            </el-select>
            <el-button :disabled="loading || !workspaceRepositoryId" @click="loadBranches">加载分支</el-button>
          </div>
          <div class="ta-inline-form">
            <el-select v-model="workspaceBranch" placeholder="选择分支" style="width: 200px">
              <el-option v-for="branch in branches" :key="branch" :label="branch" :value="branch" />
            </el-select>
            <el-button :disabled="loading || !workspaceBranch" @click="loadDirectories">加载目录</el-button>
          </div>
          <div class="ta-inline-form">
            <el-select v-model="workspaceDirectory" placeholder="选择目录" style="width: 200px">
              <el-option v-for="dir in directories" :key="dir" :label="dir" :value="dir" />
            </el-select>
            <el-input v-model="workspaceName" placeholder="工作空间名称" style="width: 180px" />
            <el-button type="primary" :disabled="loading || !workspaceDirectory" @click="createWorkspace">创建</el-button>
          </div>
        </div>

        <div class="ta-section">
          <h4 class="ta-section-title">已有工作空间</h4>
          <div v-if="!workspaces.length" class="ta-empty-hint">暂无工作空间</div>
          <div v-for="ws in workspaces" :key="ws.workspaceId" class="ta-item-row">
            <div>
              <div class="ta-item-title">{{ ws.workspaceName }}</div>
              <div class="ta-item-subtitle">{{ ws.branch }} · {{ ws.directoryPath }}</div>
            </div>
            <div class="ta-item-actions">
              <el-button size="small" :disabled="loading" @click="renameWorkspace(ws)">重命名</el-button>
              <el-button size="small" type="danger" plain :disabled="loading" @click="deleteWorkspace(ws.workspaceId)">
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="selectedAppId === '' && hasAppSettingsPermission" class="ta-empty-hint">
        暂无启用应用
      </div>
    </template>
  </div>
</template>

<style scoped>
.ta-settings-app-workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.ta-panel-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.ta-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ta-section-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #18181b;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}
.ta-inline-form {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ta-item-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ta-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.ta-item-title {
  font-size: 13px;
  font-weight: 500;
  color: #18181b;
}
.ta-item-subtitle {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.ta-item-badge {
  font-size: 11px;
  color: #909399;
  margin-left: 6px;
}
.ta-item-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}
.ta-empty-hint {
  font-size: 13px;
  color: #909399;
  padding: 20px 0;
  text-align: center;
}
.ta-error {
  margin-bottom: 8px;
}
.ta-sub-tabs {
  margin-bottom: 4px;
}
.ta-permission-placeholder {
  padding: 40px 0;
}
</style>
```

- [ ] **Step 2: 验证构建**

Run: `cd frontend/apps/agent-web && npx vite build 2>&1 | tail -5`
Expected: 构建成功

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/agent-web/src/components/settings/SettingsAppWorkspacePanel.vue
git commit -m "feat(settings): 创建应用与工作区面板（成员/代码库/工作空间管理）"
```

---

### Task 5: 创建 SettingsPersonalPanel.vue — 个人 SSH key 管理面板

**Files:**
- Create: `frontend/apps/agent-web/src/components/settings/SettingsPersonalPanel.vue`

- [ ] **Step 1: 创建 SettingsPersonalPanel.vue**

从新 `SettingsDialog.vue` 移植 SSH key 管理功能。使用 `inject("api")` 和 Element Plus 组件。

```vue
<script setup lang="ts">
import { inject, onMounted, ref } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { SshKeyMetadata } from "@test-agent/shared-types";
import { KeyRound, Trash2 } from "@element-plus/icons-vue";

const props = defineProps<{
  currentUser: unknown;
}>();

const api = inject<BackendApiClient>("api")!;

const sshKeys = ref<SshKeyMetadata[]>([]);
const sshKeyName = ref("");
const sshPrivateKey = ref("");
const loading = ref(false);
const errorMessage = ref("");

async function run(action: () => Promise<void>) {
  loading.value = true;
  errorMessage.value = "";
  try {
    await action();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "操作失败";
  } finally {
    loading.value = false;
  }
}

async function loadSshKeys() {
  await run(async () => {
    sshKeys.value = await api.listPersonalSshKeys();
  });
}

async function addSshKey() {
  await run(async () => {
    await api.addPersonalSshKey({ name: sshKeyName.value.trim(), privateKey: sshPrivateKey.value });
    sshKeyName.value = "";
    sshPrivateKey.value = "";
    await loadSshKeys();
  });
}

async function deleteSshKey(sshKeyId: string) {
  await run(async () => {
    await api.deletePersonalSshKey(sshKeyId);
    await loadSshKeys();
  });
}

onMounted(() => {
  loadSshKeys();
});
</script>

<template>
  <div class="ta-personal">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" />

    <!-- 已有 SSH key 列表 -->
    <div v-if="sshKeys.length" class="ta-section">
      <h4 class="ta-section-title">已添加的 SSH Key</h4>
      <div class="ta-item-list">
        <div v-for="sshKey in sshKeys" :key="sshKey.sshKeyId" class="ta-item-row">
          <div class="ta-item-row-left">
            <el-icon><KeyRound /></el-icon>
            <div>
              <div class="ta-item-title">{{ sshKey.name }}</div>
              <div class="ta-item-subtitle">{{ sshKey.fingerprint }}</div>
            </div>
          </div>
          <el-button size="small" type="danger" plain :disabled="loading" @click="deleteSshKey(sshKey.sshKeyId)">
            <el-icon><Trash2 /></el-icon> 删除
          </el-button>
        </div>
      </div>
    </div>

    <!-- 添加 SSH key（无 key 时显示） -->
    <div v-if="sshKeys.length === 0" class="ta-section">
      <h4 class="ta-section-title">添加 SSH Key</h4>
      <el-form label-position="top" class="ta-settings-form">
        <el-form-item label="SSH Key 名称">
          <el-input v-model="sshKeyName" placeholder="例如：work" style="width: 320px" />
        </el-form-item>
        <el-form-item label="私钥内容">
          <el-input
            v-model="sshPrivateKey"
            type="textarea"
            :rows="8"
            placeholder="-----BEGIN OPENSSH PRIVATE KEY-----"
            style="width: 480px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="loading || !sshKeyName.trim() || !sshPrivateKey.trim()" @click="addSshKey">
            添加 SSH key
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.ta-personal {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 600px;
}
.ta-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ta-section-title {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #18181b;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}
.ta-item-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ta-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.ta-item-row-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ta-item-title {
  font-size: 13px;
  font-weight: 500;
  color: #18181b;
}
.ta-item-subtitle {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.ta-settings-form {
  max-width: 520px;
}
.ta-error {
  margin-bottom: 8px;
}
</style>
```

- [ ] **Step 2: 验证构建**

Run: `cd frontend/apps/agent-web && npx vite build 2>&1 | tail -5`
Expected: 构建成功

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/agent-web/src/components/settings/SettingsPersonalPanel.vue
git commit -m "feat(settings): 创建个人设置面板（SSH key 管理）"
```

---

### Task 6: 修改 AgentWorkbench.vue — 改回旧版导入路径

**Files:**
- Modify: `frontend/apps/agent-web/src/components/AgentWorkbench.vue`

- [ ] **Step 1: 修改导入路径**

将第 39 行的导入路径从 `"./SettingsDialog.vue"` 改为 `"./settings/SettingsDialog.vue"`。

```
- import SettingsDialog from "./SettingsDialog.vue";
+ import SettingsDialog from "./settings/SettingsDialog.vue";
```

- [ ] **Step 2: 验证构建**

Run: `cd frontend/apps/agent-web && npx vite build 2>&1 | tail -5`
Expected: 构建成功

- [ ] **Step 3: 提交**

```bash
git add frontend/apps/agent-web/src/components/AgentWorkbench.vue
git commit -m "fix(settings): 改回旧版 Element Plus 风格设置弹窗"
```

---

### Task 7: 运行 E2E 测试验证

**Files:**
- Test: `frontend/apps/agent-web/tests/workbench.spec.ts`

- [ ] **Step 1: 运行设置相关 E2E 测试**

Run: `cd frontend && npx playwright test apps/agent-web/tests/workbench.spec.ts --grep "settings dialog" --reporter=list`
Expected: 10 passed

- [ ] **Step 2: 更新测试**

如果测试因组件变更失败（例如 Element Plus 的 `role="button"` 行为不同），调整测试定位器。主要关注：
- `page.getByRole("button", { name: "应用与工作区" })` — 可能需要调整选择器
- `page.getByRole("button", { name: "个人设置" })`
- `page.getByLabel("应用选择")` — Element Plus 的 `el-select` 使用不同标签
- `page.getByPlaceholder("SSH key 名称")`
- `page.getByPlaceholder("-----BEGIN OPENSSH PRIVATE KEY-----")`

- [ ] **Step 3: 全部设置测试通过**

Run: `cd frontend && npx playwright test apps/agent-web/tests/workbench.spec.ts --grep "settings dialog" --reporter=list`
Expected: 全部通过
