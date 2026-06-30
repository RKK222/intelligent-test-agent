<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, ref, watch } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  ApplicationDefinition,
  ApplicationMember,
  ApplicationWorkspaceConfig,
  CodeRepositoryConfig,
  CurrentUser,
  PlatformUserSummary,
  WorkspaceCreateOperation
} from "@test-agent/shared-types";
import { CirclePlus, Delete, InfoFilled, Link } from "@element-plus/icons-vue";

const ADD_REPOSITORY_OPTION_VALUE = "__create_repository__";
const STANDARD_REPOSITORY_TOOLTIP = "标准代码库是指测试自己去git申请，专门用于测试智能体的版本库。";

type PendingDangerAction =
  | { type: "remove-member"; member: ApplicationMember }
  | { type: "unlink-repository"; repository: CodeRepositoryConfig }
  | { type: "delete-workspace"; workspace: ApplicationWorkspaceConfig };

const props = defineProps<{
  currentUser: CurrentUser | null;
}>();

const api = inject<BackendApiClient>("api")!;

const appTab = ref<"members" | "repositoryManagement" | "repositories" | "workspaces">("members");
const loading = ref(false);
const errorMessage = ref("");
const pendingDangerAction = ref<PendingDangerAction | null>(null);

// 应用选择
const applications = ref<ApplicationDefinition[]>([]);
const selectedAppId = ref("");

// 权限
const currentRoles = computed(() => props.currentUser?.roles ?? []);
const currentRoleLabel = computed(() => (currentRoles.value.length ? currentRoles.value.join(",") : "无角色"));
const hasAppSettingsPermission = computed(() => currentRoles.value.includes("APP_ADMIN") || currentRoles.value.includes("SUPER_ADMIN"));
const selectedApp = computed(() => applications.value.find((item) => item.appId === selectedAppId.value));
const pendingDangerTitle = computed(() => {
  if (!pendingDangerAction.value) return "";
  if (pendingDangerAction.value.type === "remove-member") return "确认移除成员";
  if (pendingDangerAction.value.type === "delete-workspace") return "确认删除工作空间";
  return "确认解除关联";
});
const pendingDangerMessage = computed(() => {
  const action = pendingDangerAction.value;
  if (!action) return "";
  if (action.type === "remove-member") {
    return `确认移除成员[${action.member.username}]吗？`;
  }
  if (action.type === "delete-workspace") {
    return `确认删除工作空间[${action.workspace.workspaceName}]吗？删除后数据将无法恢复。`;
  }
  return `确认解除版本库[${action.repository.name}]与当前应用的关联吗？`;
});
const pendingDangerConfirmText = computed(() => {
  if (!pendingDangerAction.value) return "确认";
  if (pendingDangerAction.value.type === "remove-member") return "确认移除";
  if (pendingDangerAction.value.type === "delete-workspace") return "确认删除";
  return "确认解除";
});

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
const repoEnglishName = ref("");
const repoStandard = ref(false);
const linkRepositoryId = ref("");
const lastLinkRepositoryId = ref("");
const editRepositoryId = ref("");
const editRepositoryName = ref("");
const editRepositoryEnglishName = ref("");
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
const workspaceVersion = ref("");
const workspaceCreateOperation = ref<WorkspaceCreateOperation | null>(null);
let workspaceCreatePollTimer: number | undefined;
const loadingBranches = ref(false);
const loadingDirectories = ref(false);

const selectedWorkspaceRepository = computed(() => appRepositories.value.find((item) => item.repositoryId === workspaceRepositoryId.value) ?? null);
const requiresWorkspaceVersion = computed(() => selectedWorkspaceRepository.value != null && !selectedWorkspaceRepository.value.standard);
const workspaceCreateSteps = computed(() => workspaceCreateOperation.value?.steps ?? []);
const customBranchError = ref("");

/**
 * 校验分支名是否符合标准库格式：feature_testagent_yyyyMMdd
 */
function isValidStandardBranch(branch: string): boolean {
  // 正则匹配：feature_testagent_ + 8位数字
  const pattern = /^feature_testagent_\d{8}$/;
  if (!pattern.test(branch)) return false;

  // 提取并校验日期有效性
  const dateStr = branch.slice(-8);
  const year = parseInt(dateStr.slice(0, 4), 10);
  const month = parseInt(dateStr.slice(4, 6), 10);
  const day = parseInt(dateStr.slice(6, 8), 10);

  // 范围校验
  if (month < 1 || month > 12 || day < 1 || day > 31) return false;

  // 日期对象校验（自动处理2月30日等）
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year &&
         date.getMonth() === month - 1 &&
         date.getDate() === day;
}

/**
 * 判断分支是否应该被禁用
 */
function isBranchDisabled(branch: string): boolean {
  if (!selectedWorkspaceRepository.value?.standard) return false;
  return !isValidStandardBranch(branch);
}

/**
 * 处理分支变更事件（包括手动输入）
 */
function handleBranchChange(branch: string) {
  customBranchError.value = "";

  if (selectedWorkspaceRepository.value?.standard && branch) {
    if (!isValidStandardBranch(branch)) {
      customBranchError.value = "标准库只能使用 feature_testagent_yyyyMMdd 格式的分支";
    }
  }
}

/**
 * 排序后的分支列表：符合格式的排在前面，不符合格式的排在后面
 */
const sortedBranches = computed(() => {
  if (!selectedWorkspaceRepository.value?.standard) {
    // 非标准库：按原始顺序返回
    return branches.value;
  }
  // 标准库：符合格式的排前面，不符合的排后面
  const validBranches: string[] = [];
  const invalidBranches: string[] = [];

  branches.value.forEach(branch => {
    if (isValidStandardBranch(branch)) {
      validBranches.push(branch);
    } else {
      invalidBranches.push(branch);
    }
  });

  // 符合格式的分支按日期倒序排序（最新的在前）
  validBranches.sort((a, b) => {
    const dateA = a.slice(-8); // 提取日期 yyyyMMdd
    const dateB = b.slice(-8);
    return dateB.localeCompare(dateA); // 倒序：B 在前，A 在后
  });

  // 不符合格式的分支按字母顺序排序
  invalidBranches.sort();

  return [...validBranches, ...invalidBranches];
});

async function run(action: () => Promise<void>) {
  loading.value = true;
  errorMessage.value = "";
  try {
    await action();
  } catch (error) {
    if (error instanceof Error) {
      // 将 fetch 错误转换为更友好的提示
      if (error.message.includes("fetch") || error.message.includes("Failed to fetch")) {
        errorMessage.value = "网络请求失败，请检查网络连接或刷新页面重试";
      } else if (error.message.includes("403") || error.message.includes("Forbidden")) {
        errorMessage.value = "权限不足，请确认已登录且有应用管理员权限";
      } else if (error.message.includes("401") || error.message.includes("Unauthorized")) {
        errorMessage.value = "未登录或登录已过期，请刷新页面重新登录";
      } else {
        errorMessage.value = error.message;
      }
    } else {
      errorMessage.value = "操作失败";
    }
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
  pendingDangerAction.value = null;
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
function fetchUserSuggestions(keyword: string, callback: (items: any[]) => void) {
  const trimmed = keyword.trim();
  if (!trimmed) {
    callback([]);
    return;
  }
  void (async () => {
    try {
      const page = await api.searchUsers(trimmed, 1, 20);
      callback(page.items);
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : "搜索用户失败";
      callback([]);
    }
  })();
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

async function removeMember(member: ApplicationMember) {
  pendingDangerAction.value = { type: "remove-member", member };
}

function cancelDangerAction() {
  if (loading.value) return;
  pendingDangerAction.value = null;
}

// 破坏性操作必须使用页面内确认框，避免浏览器原生模态框打断设置面板体验。
async function confirmDangerAction() {
  const action = pendingDangerAction.value;
  if (!action) return;
  pendingDangerAction.value = null;
  if (action.type === "remove-member") {
    await run(async () => {
      await api.removeApplicationMember(selectedAppId.value, action.member.userId);
      await loadMembers();
    });
    return;
  }
  if (action.type === "delete-workspace") {
    await run(async () => {
      await api.deleteApplicationWorkspace(selectedAppId.value, action.workspace.workspaceId);
      await loadWorkspaces();
    });
    return;
  }
  await run(async () => {
    await api.unlinkApplicationRepository(selectedAppId.value, action.repository.repositoryId);
    await loadRepositories();
    await loadWorkspaces();
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
  const englishName = normalizeRepositoryEnglishName(repoEnglishName.value);
  if (!englishName) {
    errorMessage.value = "版本库英文名称只能输入 1 到 29 位英文字母";
    return;
  }
  await run(async () => {
    const repository = await api.createRepository({
      gitUrl: repoGitUrl.value.trim(),
      name: repoName.value.trim(),
      englishName,
      standard: repoStandard.value
    });
    repoGitUrl.value = "";
    repoName.value = "";
    repoEnglishName.value = "";
    repoStandard.value = false;
    linkRepositoryId.value = repository.repositoryId;
    lastLinkRepositoryId.value = repository.repositoryId;
    await loadRepositories();
  });
}

function startEditRepository(repository: CodeRepositoryConfig) {
  editRepositoryId.value = repository.repositoryId;
  editRepositoryName.value = repository.name;
  editRepositoryEnglishName.value = repository.englishName ?? "";
  editRepositoryStandard.value = repository.standard;
}

function cancelEditRepository() {
  editRepositoryId.value = "";
  editRepositoryName.value = "";
  editRepositoryEnglishName.value = "";
  editRepositoryStandard.value = false;
}

async function saveRepository() {
  const englishName = normalizeRepositoryEnglishName(editRepositoryEnglishName.value);
  if (!englishName) {
    errorMessage.value = "版本库英文名称只能输入 1 到 29 位英文字母";
    return;
  }
  await run(async () => {
    await api.updateRepository(editRepositoryId.value, {
      name: editRepositoryName.value.trim(),
      englishName,
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

async function unlinkRepository(repository: CodeRepositoryConfig) {
  pendingDangerAction.value = { type: "unlink-repository", repository };
}

// 工作空间管理
async function loadWorkspaces() {
  workspaces.value = selectedAppId.value ? await api.listApplicationWorkspaces(selectedAppId.value) : [];
}

async function loadBranches() {
  loadingBranches.value = true;
  await run(async () => {
    branches.value = workspaceRepositoryId.value ? await api.listRepositoryBranches(workspaceRepositoryId.value) : [];

    // 智能选择默认分支（基于排序后的列表）
    if (branches.value.length > 0) {
      if (selectedWorkspaceRepository.value?.standard) {
        // 标准库：先排序，再选择第一个（已按日期倒序，最新的在前）
        const sortedValid = branches.value
          .filter(b => isValidStandardBranch(b))
          .sort((a, b) => {
            const dateA = a.slice(-8);
            const dateB = b.slice(-8);
            return dateB.localeCompare(dateA);
          });
        workspaceBranch.value = sortedValid[0] ?? "";
      } else {
        // 非标准库：选择第一条
        workspaceBranch.value = branches.value[0];
      }
    } else {
      workspaceBranch.value = "";
    }

    directories.value = [];
    workspaceDirectory.value = "";
    customBranchError.value = "";
  }).finally(() => {
    loadingBranches.value = false;
  });
}

async function loadDirectories() {
  loadingDirectories.value = true;
  await run(async () => {
    directories.value =
      workspaceRepositoryId.value && workspaceBranch.value
        ? await api.listRepositoryDirectories(workspaceRepositoryId.value, workspaceBranch.value)
        : [];
    workspaceDirectory.value = directories.value[0] ?? "";
  }).finally(() => {
    loadingDirectories.value = false;
  });
}

async function createWorkspace() {
  if (requiresWorkspaceVersion.value && !/^\d{8}$/.test(workspaceVersion.value ?? '')) {
    errorMessage.value = "非标准库版本必须选择日期";
    return;
  }
  const operationId = createWorkspaceOperationId();
  startWorkspaceCreatePolling(operationId);
  await run(async () => {
    // POST 失败时停止轮询（此时 operation 未创建或后端校验未通过），
    // POST 成功后由 refreshWorkspaceCreateOperation 在终态时停止，或组件卸载后清理。
    try {
      await api.createApplicationWorkspace(selectedAppId.value, {
        repositoryId: workspaceRepositoryId.value,
        branch: workspaceBranch.value,
        directoryPath: workspaceDirectory.value,
        workspaceName: workspaceName.value.trim() || undefined,
        version: requiresWorkspaceVersion.value ? (workspaceVersion.value ?? '').trim() || undefined : undefined,
        operationId
      });
    } catch (error) {
      stopWorkspaceCreatePolling();
      throw error;
    }
    await refreshWorkspaceCreateOperation(operationId);
    workspaceName.value = "";
    workspaceVersion.value = "";
    await loadWorkspaces();
  });
}

function normalizeRepositoryEnglishName(value: string) {
  const trimmed = value.trim();
  if (!/^[A-Za-z]{1,29}$/.test(trimmed)) {
    errorMessage.value = "版本库英文名称只能输入 1 到 29 位英文字母";
    return "";
  }
  return trimmed.toLowerCase();
}

function createWorkspaceOperationId() {
  const raw = typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID().replaceAll("-", "")
    : `${Date.now()}${Math.random().toString(16).slice(2)}`;
  return `wco_${raw.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 64)}`;
}

function startWorkspaceCreatePolling(operationId: string) {
  stopWorkspaceCreatePolling();
  workspaceCreateOperation.value = {
    operationId,
    status: "RUNNING",
    currentStep: "VALIDATING_INPUT",
    steps: [
      { code: "VALIDATING_INPUT", name: "校验参数", status: "RUNNING" },
      { code: "SAVING_TEMPLATE", name: "保存工作空间配置", status: "PENDING" },
      { code: "RESOLVING_VERSION", name: "解析版本和分支", status: "PENDING" },
      { code: "PREPARING_REPOSITORY", name: "下载代码并切换分支", status: "PENDING" },
      { code: "CREATING_RUNTIME_WORKSPACE", name: "创建运行态工作区", status: "PENDING" },
      { code: "COMPLETED", name: "完成", status: "PENDING" }
    ]
  };
  void refreshWorkspaceCreateOperation(operationId);
  workspaceCreatePollTimer = window.setInterval(() => {
    void refreshWorkspaceCreateOperation(operationId);
  }, 1200);
}

function stopWorkspaceCreatePolling() {
  if (workspaceCreatePollTimer !== undefined) {
    window.clearInterval(workspaceCreatePollTimer);
    workspaceCreatePollTimer = undefined;
  }
}

async function refreshWorkspaceCreateOperation(operationId: string) {
  try {
    const operation = await api.getWorkspaceCreateOperation(operationId);
    workspaceCreateOperation.value = operation;
    if (operation.status === "SUCCEEDED" || operation.status === "FAILED") {
      stopWorkspaceCreatePolling();
      if (operation.status === "SUCCEEDED") {
        // 操作完成后刷新已有工作空间列表，确保刚创建的工作空间可见
        await loadWorkspaces();
      }
    }
  } catch {
    // 创建请求刚发出时后端可能尚未写入 operation，下一轮轮询继续读取。
  }
}

async function renameWorkspace(workspace: ApplicationWorkspaceConfig) {
  const nextName = window.prompt("工作空间名称", workspace.workspaceName);
  if (!nextName || !nextName.trim()) return;
  await run(async () => {
    await api.renameApplicationWorkspace(selectedAppId.value, workspace.workspaceId, { workspaceName: nextName.trim() });
    await loadWorkspaces();
  });
}

/**
 * 删除工作空间：先弹出页面内二次确认框，用户确认后再执行删除。
 * 使用 pendingDangerAction 模式，与移除成员、解除关联版本库保持一致。
 */
function confirmDeleteWorkspace(workspace: ApplicationWorkspaceConfig) {
  pendingDangerAction.value = { type: "delete-workspace", workspace };
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
  pendingDangerAction.value = null;
  await loadAppContext();
});

watch(workspaceRepositoryId, () => {
  branches.value = [];
  workspaceBranch.value = "";
  directories.value = [];
  workspaceDirectory.value = "";
  customBranchError.value = "";
});

watch(workspaceBranch, () => {
  directories.value = [];
  workspaceDirectory.value = "";
});

const directorySearchQuery = ref("");

function handleDirectoryFilter(val: string) {
  directorySearchQuery.value = val;
}

function handleDirectoryVisibleChange(visible: boolean) {
  if (!visible) {
    directorySearchQuery.value = "";
  }
}

const filteredDirectories = computed(() => {
  const query = directorySearchQuery.value.trim().toLowerCase();
  return directories.value.filter(dir => {
    const isHidden = dir.split(/[/\\]/).some(segment => segment.startsWith('.'));
    if (isHidden) {
      if (!query) {
        return false;
      }
      return dir.toLowerCase().includes(query);
    }
    if (!query) {
      return true;
    }
    return dir.toLowerCase().includes(query);
  });
});

onBeforeUnmount(() => {
  stopWorkspaceCreatePolling();
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
              @select="(item: any) => onUserSelected(item)"
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
              <el-button size="small" type="danger" plain aria-label="移除成员" :disabled="loading" @click="removeMember(member)">
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
              <el-button size="small" type="danger" plain :disabled="loading" @click="unlinkRepository(repo)">解除</el-button>
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
              <div class="ta-item-subtitle">{{ repo.englishName || "未配置英文名" }} · {{ repo.gitUrl }}</div>
            </div>
            <el-button size="small" @click="startEditRepository(repo)">编辑</el-button>
          </div>
          <div v-if="editRepositoryId" class="ta-inline-form ta-edit-form">
            <label class="ta-form-field">
              <span class="ta-form-label">版本库名称</span>
              <el-input v-model="editRepositoryName" placeholder="名称" style="width: 240px" />
            </label>
            <label class="ta-form-field">
              <span class="ta-form-label">版本库英文名称</span>
              <el-input v-model="editRepositoryEnglishName" placeholder="英文名称" style="width: 180px" />
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
              <label class="ta-form-field">
                <span class="ta-form-label">版本库英文名称</span>
                <el-input v-model="repoEnglishName" placeholder="英文名称" style="width: 180px" />
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
          <div class="ta-workspace-create-steps">
            <div class="ta-workspace-step" :class="{ 'is-completed': branches.length > 0, 'is-active': branches.length === 0 }">
              <div class="ta-workspace-step-heading">
                <span class="ta-workspace-step-index">1</span>
                <span class="ta-workspace-step-title">刷新分支</span>
              </div>
              <div class="ta-workspace-step-controls">
                <div class="ta-workspace-step-inputs">
                  <label class="ta-form-field" style="width: 320px">
                    <span class="ta-form-label">已关联版本库</span>
                    <el-select v-model="workspaceRepositoryId" placeholder="选择已关联版本库" style="width: 100%">
                      <el-option v-for="repo in appRepositories" :key="repo.repositoryId" :label="formatRepositoryOption(repo)" :value="repo.repositoryId" />
                    </el-select>
                  </label>
                </div>
                <el-button :disabled="loading || !workspaceRepositoryId" :loading="loadingBranches" @click="loadBranches">刷新分支</el-button>
              </div>
              <div v-if="loadingBranches" class="ta-workspace-step-progress">
                <el-progress :percentage="100" :indeterminate="true" :duration="1" :show-text="false" :stroke-width="2" style="width: 100%" />
              </div>
            </div>

            <div class="ta-workspace-step" :class="{ 'is-disabled': branches.length === 0, 'is-completed': branches.length > 0 && directories.length > 0, 'is-active': branches.length > 0 && directories.length === 0 }">
              <div class="ta-workspace-step-heading">
                <span class="ta-workspace-step-index">2</span>
                <span class="ta-workspace-step-title">加载目录</span>
              </div>
              <div class="ta-workspace-step-controls">
                <div class="ta-workspace-step-inputs">
                  <label class="ta-form-field" style="width: 240px">
                    <span class="ta-form-label">分支</span>
                    <el-select
                      v-model="workspaceBranch"
                      filterable
                      allow-create
                      default-first-option
                      placeholder="选择或输入分支"
                      style="width: 100%"
                      @change="handleBranchChange"
                    >
                      <el-option
                        v-for="branch in sortedBranches"
                        :key="branch"
                        :label="branch"
                        :value="branch"
                        :disabled="isBranchDisabled(branch)"
                      />
                    </el-select>
                    <!-- 标准库分支格式错误提示 -->
                    <div v-if="customBranchError" class="ta-branch-error">
                      {{ customBranchError }}
                    </div>
                  </label>
                </div>
                <el-button :disabled="loading || !workspaceBranch || !!customBranchError" :loading="loadingDirectories" @click="loadDirectories">加载目录</el-button>
              </div>
              <div v-if="loadingDirectories" class="ta-workspace-step-progress">
                <el-progress :percentage="100" :indeterminate="true" :duration="1" :show-text="false" :stroke-width="2" style="width: 100%" />
              </div>
            </div>

            <div class="ta-workspace-step" :class="{ 'is-disabled': directories.length === 0, 'is-active': directories.length > 0 }">
              <div class="ta-workspace-step-heading">
                <span class="ta-workspace-step-index">3</span>
                <span class="ta-workspace-step-title">创建工作空间</span>
              </div>
              <div class="ta-workspace-step-controls">
                <div class="ta-workspace-step-inputs">
                  <label class="ta-form-field" style="width: 240px">
                    <span class="ta-form-label">目录</span>
                    <el-select
                      v-model="workspaceDirectory"
                      filterable
                      allow-create
                      default-first-option
                      :filter-method="handleDirectoryFilter"
                      @visible-change="handleDirectoryVisibleChange"
                      placeholder="选择或输入目录"
                      style="width: 100%"
                    >
                      <el-option v-for="dir in filteredDirectories" :key="dir" :label="dir" :value="dir" />
                    </el-select>
                  </label>
                  <label class="ta-form-field" style="width: 180px">
                    <span class="ta-form-label">工作空间名称</span>
                    <el-input v-model="workspaceName" placeholder="工作空间名称" style="width: 100%" />
                  </label>
                  <label v-if="requiresWorkspaceVersion" class="ta-form-field" style="width: 160px">
                    <span class="ta-form-label">非标准库版本</span>
                    <el-date-picker
                      v-model="workspaceVersion"
                      type="date"
                      value-format="YYYYMMDD"
                      format="YYYYMMDD"
                      placeholder="选择日期"
                      style="width: 100%"
                    />
                  </label>
                </div>
                <el-button type="primary" :disabled="loading || !workspaceDirectory" @click="createWorkspace">创建</el-button>
              </div>
            </div>
          </div>
          <div v-if="workspaceCreateOperation" class="ta-workspace-progress">
            <div
              v-for="step in workspaceCreateSteps"
              :key="step.code"
              class="ta-workspace-progress-step"
              :class="`is-${step.status.toLowerCase()}`"
            >
              <span class="ta-progress-dot" />
              <span>{{ step.name }}</span>
              <span class="ta-progress-status">{{ step.status }}</span>
            </div>
            <div v-if="workspaceCreateOperation.status === 'FAILED'" class="ta-workspace-progress-error">
              {{ workspaceCreateOperation.errorMessage || "创建工作空间失败" }}
            </div>
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
              <el-button size="small" type="danger" plain :disabled="loading" @click="confirmDeleteWorkspace(ws)">
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="selectedAppId === '' && hasAppSettingsPermission" class="ta-empty-hint">
        暂无启用应用
      </div>

      <div v-if="pendingDangerAction" class="ta-danger-confirm-mask" @click.self="cancelDangerAction">
        <div class="ta-danger-confirm" role="dialog" aria-modal="true" aria-labelledby="ta-danger-confirm-title">
          <h4 id="ta-danger-confirm-title" class="ta-danger-confirm-title">{{ pendingDangerTitle }}</h4>
          <p class="ta-danger-confirm-message">{{ pendingDangerMessage }}</p>
          <div class="ta-danger-confirm-actions">
            <el-button :disabled="loading" @click="cancelDangerAction">取消</el-button>
            <el-button type="danger" :disabled="loading" @click="confirmDangerAction">{{ pendingDangerConfirmText }}</el-button>
          </div>
        </div>
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
.ta-workspace-create-steps {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.03);
  padding: 8px 0;
  position: relative;
}
.ta-workspace-step {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  align-items: flex-start;
  gap: 16px;
  padding: 12px 24px;
  position: relative;
  transition: all 0.25s ease;
}
.ta-workspace-step:not(:last-child) {
  border-bottom: 1px solid #f0f2f5;
}
.ta-workspace-step:hover {
  background: #fbfcfe;
}
.ta-workspace-step-progress {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 100%;
  line-height: 1;
}
.ta-workspace-step-progress :deep(.el-progress) {
  margin: 0;
}
.ta-workspace-step-progress :deep(.el-progress-bar) {
  margin: 0;
  padding: 0;
}
.ta-workspace-step-progress :deep(.el-progress-bar__outer) {
  border-radius: 0;
  background-color: transparent !important;
}
.ta-workspace-step-progress :deep(.el-progress-bar__inner) {
  border-radius: 0;
}
.ta-workspace-step-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  position: relative;
  z-index: 2;
  height: 32px;
  white-space: nowrap;
}
.ta-workspace-step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #e4e7ed;
  color: #909399;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  position: relative;
  z-index: 2;
  box-shadow: 0 0 0 4px #ffffff;
  transition: all 0.25s ease;
}
.ta-workspace-step-title {
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  transition: all 0.25s ease;
}
.ta-workspace-step-controls {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  min-width: 0;
  width: 100%;
}
.ta-workspace-step-inputs {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
  min-width: 0;
  flex-grow: 1;
}
.ta-workspace-step .ta-form-field {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}

/* Step states styling */
.ta-workspace-step.is-disabled {
  opacity: 0.55;
}
.ta-workspace-step.is-disabled .ta-workspace-step-controls {
  pointer-events: none;
}
.ta-workspace-step.is-active .ta-workspace-step-index {
  background: #3366ff;
  color: #ffffff;
  box-shadow: 0 0 0 4px rgba(51, 102, 255, 0.15), 0 0 0 8px #ffffff;
}
.ta-workspace-step.is-active .ta-workspace-step-title {
  color: #18181b;
}
.ta-workspace-step.is-completed .ta-workspace-step-index {
  background: #19a15f;
  color: #ffffff;
  box-shadow: 0 0 0 4px rgba(25, 161, 95, 0.15), 0 0 0 8px #ffffff;
}
.ta-workspace-step.is-completed .ta-workspace-step-title {
  color: #19a15f;
}

.ta-workspace-progress {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
  margin-top: 16px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.02);
}
.ta-workspace-progress-step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  font-size: 12px;
  color: #606266;
}
.ta-progress-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #dcdfe6;
}

@keyframes ta-progress-pulse {
  0% {
    transform: scale(0.9);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.25);
    opacity: 1;
  }
  100% {
    transform: scale(0.9);
    opacity: 0.6;
  }
}
.ta-workspace-progress-step.is-running .ta-progress-dot {
  background: #3366ff;
  animation: ta-progress-pulse 1.5s infinite ease-in-out;
  box-shadow: 0 0 0 3px rgba(51, 102, 255, 0.2);
}
.ta-workspace-progress-step.is-succeeded .ta-progress-dot {
  background: #19a15f;
}
.ta-workspace-progress-step.is-failed .ta-progress-dot {
  background: #d93025;
}
.ta-progress-status {
  color: #909399;
}
.ta-workspace-progress-error {
  width: 100%;
  color: #d93025;
  font-size: 12px;
}
@media (max-width: 720px) {
  .ta-workspace-step {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }
  .ta-workspace-create-steps::before {
    display: none;
  }
  .ta-workspace-step-index {
    box-shadow: none !important;
  }
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
.ta-danger-confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgb(15 23 42 / 35%);
}
.ta-danger-confirm {
  width: min(360px, 100%);
  padding: 18px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 48px rgb(15 23 42 / 18%);
}
.ta-danger-confirm-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #18181b;
}
.ta-danger-confirm-message {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #606266;
  overflow-wrap: anywhere;
}
.ta-danger-confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}
.ta-branch-error {
  margin-top: 4px;
  font-size: 12px;
  color: #f56c6c;
  line-height: 1.4;
}
</style>
