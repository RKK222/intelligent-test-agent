<script setup lang="ts">
import { computed, inject, nextTick, ref, watch } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  ApplicationDefinition,
  ApplicationMember,
  ApplicationWorkspaceConfig,
  CodeRepositoryConfig,
  CurrentUser,
  PlatformUserSummary
} from "@test-agent/shared-types";
import { CirclePlus, Delete, InfoFilled, Link } from "@element-plus/icons-vue";

const ADD_REPOSITORY_OPTION_VALUE = "__create_repository__";
const STANDARD_REPOSITORY_TOOLTIP = "标准代码库是指测试自己去git申请，专门用于测试智能体的版本库。";

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;

const appTab = ref<"members" | "repositoryManagement" | "repositories" | "workspaces">("members");
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
// el-autocomplete 双向绑定的输入关键字；触发 fetchUserSuggestions 后异步拉取候选用户
const userKeyword = ref("");
// 当前从下拉中选中的候选用户；为空时主按钮显示"搜索"，非空时显示"添加"
const selectedUser = ref<PlatformUserSummary | null>(null);

// 版本库
const repositories = ref<CodeRepositoryConfig[]>([]);
const repositoryTotal = ref(0);
const appRepositories = ref<CodeRepositoryConfig[]>([]);
const repoGitUrl = ref("");
const repoName = ref("");
const repoStandard = ref(false);
const linkRepositoryId = ref("");
const lastLinkRepositoryId = ref("");
const editRepositoryId = ref("");
const editRepositoryName = ref("");
const editRepositoryStandard = ref(false);
const repositoryCreateSectionRef = ref<HTMLElement | null>(null);
const repoGitUrlInputRef = ref<{ focus: () => void } | null>(null);

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
  selectedUser.value = null;
  userKeyword.value = "";
  repositories.value = [];
  repositoryTotal.value = 0;
  appRepositories.value = [];
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

/**
 * el-autocomplete 异步拉取候选用户（懒加载）。
 * - Element Plus 自带 300ms 防抖。
 * - keyword 为空时直接返回空下拉，不打后端，避免用户表数据多时聚焦/初始进入就全量拉取导致慢。
 * - 后端 LIKE 匹配 userId / unifiedAuthId / username 任一字段（不区分大小写）。
 */
async function fetchUserSuggestions(keyword: string, callback: (items: PlatformUserSummary[]) => void) {
  const trimmed = keyword.trim();
  if (!trimmed) {
    callback([]);
    return;
  }
  try {
    const page = await api.searchUsers(trimmed, 1, 20);
    callback(page.items);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "搜索用户失败";
    callback([]);
  }
}

// 显式"搜索"按钮：懒加载策略下空输入不查库；el-autocomplete 已自带 300ms 防抖自动拉取候选。
// 按钮在输入精确 userId 且只有 1 条命中时直接落库到 selectedUser，避免再去下拉里挑。
async function searchUsers() {
  const trimmed = userKeyword.value.trim();
  if (!trimmed) return;
  await run(async () => {
    const page = await api.searchUsers(trimmed, 1, 20);
    if (page.items.length === 1) {
      onUserSelected(page.items[0]);
    } else {
      selectedUser.value = null;
    }
  });
}

// el-autocomplete 选中候选时落库到 selectedUser，按钮由"搜索"切换为"添加"。
function onUserSelected(user: PlatformUserSummary) {
  selectedUser.value = user;
}

// 直接添加已选中的用户；清空选中态并刷新成员列表。
async function addSelectedMember() {
  const user = selectedUser.value;
  if (!user) return;
  await run(async () => {
    await api.addApplicationMember(selectedAppId.value, user.userId);
    selectedUser.value = null;
    userKeyword.value = "";
    await loadMembers();
  });
}

async function removeMember(userId: string) {
  await run(async () => {
    await api.removeApplicationMember(selectedAppId.value, userId);
    await loadMembers();
  });
}

// 版本库管理
async function loadRepositories() {
  const [all, linked] = await Promise.all([
    api.listRepositories(1, 100),
    selectedAppId.value ? api.listApplicationRepositories(selectedAppId.value) : Promise.resolve([])
  ]);
  repositories.value = all.items;
  repositoryTotal.value = all.total;
  appRepositories.value = linked;
  if (!workspaceRepositoryId.value || !linked.some((item) => item.repositoryId === workspaceRepositoryId.value)) {
    workspaceRepositoryId.value = linked[0]?.repositoryId ?? "";
  }
}

function formatRepositoryOption(repository: CodeRepositoryConfig) {
  return `${repository.name}(${repository.gitUrl})`;
}

// 下拉中的“添加版本库”只作为入口，不改变当前待关联版本库选择。
async function openRepositoryCreateSection() {
  await nextTick();
  repositoryCreateSectionRef.value?.scrollIntoView?.({ behavior: "smooth", block: "start" });
  // Element Plus 下拉关闭会回收焦点，延迟聚焦才能稳定落到 Git URL 输入框。
  window.setTimeout(() => {
    repoGitUrlInputRef.value?.focus();
  }, 80);
}

function handleLinkRepositoryChange(repositoryId: string) {
  if (repositoryId === ADD_REPOSITORY_OPTION_VALUE) {
    linkRepositoryId.value = lastLinkRepositoryId.value;
    appTab.value = "repositoryManagement";
    void openRepositoryCreateSection();
    return;
  }
  lastLinkRepositoryId.value = repositoryId;
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
    lastLinkRepositoryId.value = repository.repositoryId;
    await loadRepositories();
  });
}

function startEditRepository(repository: CodeRepositoryConfig) {
  editRepositoryId.value = repository.repositoryId;
  editRepositoryName.value = repository.name;
  editRepositoryStandard.value = repository.standard;
}

function cancelEditRepository() {
  editRepositoryId.value = "";
  editRepositoryName.value = "";
  editRepositoryStandard.value = false;
}

async function saveRepository() {
  await run(async () => {
    await api.updateRepository(editRepositoryId.value, {
      name: editRepositoryName.value.trim(),
      standard: editRepositoryStandard.value
    });
    cancelEditRepository();
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
        <el-select v-model="selectedAppId" placeholder="选择应用" aria-label="应用选择" style="width: 320px">
          <el-option v-for="app in applications" :key="app.appId" :label="app.appName" :value="app.appId" />
        </el-select>
      </div>

      <!-- 子选项卡 -->
      <div class="ta-sub-tabs" v-if="selectedAppId">
        <el-radio-group v-model="appTab" class="ta-sub-tab-group">
          <el-radio-button value="members">应用人员管理</el-radio-button>
          <el-radio-button value="repositoryManagement">版本库管理</el-radio-button>
          <el-radio-button value="repositories">应用与版本库关联</el-radio-button>
          <el-radio-button value="workspaces">工作空间管理</el-radio-button>
        </el-radio-group>
      </div>

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" />

      <!-- 成员管理 -->
      <div v-if="selectedAppId && appTab === 'members'" class="ta-panel-content">
        <div class="ta-section">
          <h4 class="ta-section-title">添加成员</h4>
          <div class="ta-inline-form">
            <!--
              el-autocomplete：懒加载搜索。trigger-on-focus=false，初始聚焦/空输入不打后端；
              仅当用户键入内容时（300ms 防抖）才异步拉取候选用户。
              选中后主按钮文案从"搜索"切换为"添加"，再点击即把该用户加入当前应用。
              "搜索"按钮在空输入时禁用，作为精确 userId 单条命中场景的兜底。
            -->
            <el-autocomplete
              v-model="userKeyword"
              :fetch-suggestions="fetchUserSuggestions"
              :trigger-on-focus="false"
              placeholder="输入用户ID、用户名或统一认证号（懒加载搜索）"
              value-key="username"
              style="width: 280px"
              clearable
              @select="onUserSelected"
            >
              <template #default="{ item }">
                <div class="ta-user-suggestion">
                  <span>{{ item.userId }} · {{ item.username }}</span>
                </div>
              </template>
            </el-autocomplete>
            <el-button v-if="!selectedUser" :disabled="loading || !userKeyword.trim()" @click="searchUsers">搜索</el-button>
            <el-button v-else type="primary" :disabled="loading" @click="addSelectedMember">
              <el-icon><CirclePlus /></el-icon>
              添加
            </el-button>
          </div>
        </div>

        <div class="ta-section">
          <h4 class="ta-section-title">已有成员</h4>
          <div class="ta-item-list">
            <div v-for="member in members" :key="member.userId" class="ta-item-row">
              <div>
                <div class="ta-item-title">{{ member.username }}</div>
                <div class="ta-item-subtitle">{{ member.userId }} · {{ member.unifiedAuthId }}</div>
              </div>
              <el-button size="small" type="danger" plain :disabled="loading" @click="removeMember(member.userId)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 版本库关联 -->
      <div v-if="selectedAppId && appTab === 'repositories'" class="ta-panel-content">
        <div class="ta-section">
          <div class="ta-section-title-row">
            <h4 class="ta-section-title">按应用关联版本库</h4>
            <span v-if="selectedApp" class="ta-section-title-app">{{ selectedApp.appName }}</span>
          </div>
          <div class="ta-inline-form">
            <el-select v-model="linkRepositoryId" placeholder="选择版本库" style="width: 360px" @change="handleLinkRepositoryChange">
              <el-option v-for="repo in repositories" :key="repo.repositoryId" :label="formatRepositoryOption(repo)" :value="repo.repositoryId" />
              <el-option :label="'添加版本库'" :value="ADD_REPOSITORY_OPTION_VALUE" />
            </el-select>
            <el-button type="primary" :disabled="loading || !linkRepositoryId" @click="linkRepository">
              <el-icon><Link /></el-icon> 关联
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

      </div>

      <!-- 版本库管理 -->
      <div v-if="selectedAppId && appTab === 'repositoryManagement'" class="ta-panel-content">
        <div class="ta-section">
          <div class="ta-section-header">
            <h4 class="ta-section-title">已有版本库</h4>
            <div class="ta-section-actions">
              <span class="ta-count-badge">共 {{ repositoryTotal }} 个版本库</span>
              <el-button :disabled="loading" @click="loadRepositories">刷新</el-button>
            </div>
          </div>
          <div v-for="repo in repositories" :key="repo.repositoryId" class="ta-item-row ta-edit-item">
            <div>
              <span class="ta-item-title">{{ repo.name }}</span>
              <span v-if="repo.standard" class="ta-item-badge">标准库</span>
              <div class="ta-item-subtitle">{{ repo.gitUrl }}</div>
            </div>
            <el-button size="small" @click="startEditRepository(repo)">编辑</el-button>
          </div>
          <div v-if="editRepositoryId" class="ta-inline-form ta-edit-form">
            <label class="ta-form-field">
              <span class="ta-form-label">版本库名称</span>
              <el-input v-model="editRepositoryName" placeholder="名称" style="width: 240px" />
            </label>
            <el-checkbox v-model="editRepositoryStandard">标准库</el-checkbox>
            <el-tooltip :content="STANDARD_REPOSITORY_TOOLTIP" placement="top">
              <el-icon class="ta-help-icon" :title="STANDARD_REPOSITORY_TOOLTIP" aria-label="标准库说明">
                <InfoFilled />
              </el-icon>
            </el-tooltip>
            <el-button type="primary" :disabled="loading" @click="saveRepository">保存</el-button>
            <el-button :disabled="loading" @click="cancelEditRepository">取消</el-button>
          </div>
        </div>

        <div ref="repositoryCreateSectionRef" class="ta-section">
          <h4 class="ta-section-title">新增版本库</h4>
          <div class="ta-repository-create-form">
            <label class="ta-form-field">
              <span class="ta-form-label">版本库地址</span>
              <el-input ref="repoGitUrlInputRef" v-model="repoGitUrl" placeholder="Git URL" style="width: 300px" />
            </label>
            <div class="ta-inline-form ta-repository-create-name-row">
              <label class="ta-form-field">
                <span class="ta-form-label">版本库名称</span>
                <el-input v-model="repoName" placeholder="中文名称" style="width: 200px" />
              </label>
              <el-checkbox v-model="repoStandard">标准库</el-checkbox>
              <el-tooltip :content="STANDARD_REPOSITORY_TOOLTIP" placement="top">
                <el-icon class="ta-help-icon" :title="STANDARD_REPOSITORY_TOOLTIP" aria-label="标准库说明">
                  <InfoFilled />
                </el-icon>
              </el-tooltip>
              <el-button type="primary" :disabled="loading" @click="createRepository">新增</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 工作空间管理 -->
      <div v-if="selectedAppId && appTab === 'workspaces'" class="ta-panel-content">
        <div class="ta-section">
          <h4 class="ta-section-title">创建工作空间</h4>
          <div class="ta-inline-form">
            <el-select v-model="workspaceRepositoryId" placeholder="选择已关联版本库" style="width: 360px">
              <el-option v-for="repo in appRepositories" :key="repo.repositoryId" :label="formatRepositoryOption(repo)" :value="repo.repositoryId" />
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
.ta-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.ta-section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.ta-count-badge {
  font-size: 12px;
  color: #606266;
}
.ta-inline-form {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ta-form-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.ta-form-label {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 500;
  color: #606266;
  line-height: 1;
}
.ta-repository-create-form {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.ta-section-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.ta-section-title-app {
  display: inline-flex;
  align-items: center;
  max-width: 240px;
  min-width: 0;
  height: 20px;
  padding: 0 8px;
  border-radius: 6px;
  background: #f0f6ff;
  color: #3366ff;
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
.ta-help-icon {
  color: #909399;
  cursor: help;
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
/* el-autocomplete 下拉项布局：单行展示 userId · userName */
.ta-user-suggestion {
  display: flex;
  align-items: center;
  line-height: 1.4;
  white-space: nowrap;
  font-size: 13px;
  color: #18181b;
}
</style>
