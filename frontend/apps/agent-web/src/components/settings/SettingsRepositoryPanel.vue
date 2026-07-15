<script setup lang="ts">
import { computed, inject, nextTick, onMounted, ref, watch } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CodeRepositoryConfig,
  CurrentUser,
  RepositoryDeploymentOptions,
  RepositoryTypeOption
} from "@test-agent/shared-types";
import { InfoFilled } from "@element-plus/icons-vue";

const TEST_WORK_REPOSITORY_TYPE = "TEST_WORK_REPOSITORY";
const APPLICATION_CODE_REPOSITORY_TYPE = "APPLICATION_CODE_REPOSITORY";
const EXTERNAL_DEPLOYMENT_MODE = "EXTERNAL";
const INTERNAL_DEPLOYMENT_MODE = "INTERNAL";
const STANDARD_REPOSITORY_TOOLTIP = "测试工作库等价于原标准库，会按标准库分支规则创建工作空间。";
const REPOSITORY_ENGLISH_NAME_ERROR = "版本库英文名称只能使用字母、数字和连字符，长度 1 到 128，且不能以连字符开头或结尾";
const DEFAULT_REPOSITORY_TYPES: RepositoryTypeOption[] = [
  { typeCode: TEST_WORK_REPOSITORY_TYPE, typeLabel: "测试工作库" },
  { typeCode: APPLICATION_CODE_REPOSITORY_TYPE, typeLabel: "应用代码库" },
  { typeCode: "APPLICATION_ASSET_REPOSITORY", typeLabel: "应用资产库" }
];
const DEFAULT_REPOSITORY_DEPLOYMENT_OPTIONS: RepositoryDeploymentOptions = {
  defaultDeploymentMode: EXTERNAL_DEPLOYMENT_MODE,
  internalSshPrefix: "",
  options: [
    { mode: EXTERNAL_DEPLOYMENT_MODE, label: "外部部署" },
    { mode: INTERNAL_DEPLOYMENT_MODE, label: "内部部署" }
  ]
};

const props = defineProps<{
  currentUser: CurrentUser | null;
  autoOpenCreate?: boolean;
}>();

const api = inject<BackendApiClient>("api")!;

const loading = ref(false);
const errorMessage = ref("");
const createDialogVisible = ref(false);

// 权限
const currentRoles = computed(() => props.currentUser?.roles ?? []);
const currentRoleLabel = computed(() => (currentRoles.value.length ? currentRoles.value.join(",") : "无角色"));
const hasAppSettingsPermission = computed(() => currentRoles.value.includes("APP_ADMIN") || currentRoles.value.includes("SUPER_ADMIN"));

// 版本库
const repositories = ref<CodeRepositoryConfig[]>([]);
const repositoryTotal = ref(0);
const repositoryTypes = ref<RepositoryTypeOption[]>(DEFAULT_REPOSITORY_TYPES);
const repositoryDeploymentOptions = ref<RepositoryDeploymentOptions>(DEFAULT_REPOSITORY_DEPLOYMENT_OPTIONS);
const repoGitUrl = ref("");
const repoName = ref("");
const repoEnglishName = ref("");
const repoEnglishNameTouched = ref(false);
const repoDeploymentMode = ref(EXTERNAL_DEPLOYMENT_MODE);
const repoType = ref("");
const editRepositoryId = ref("");
const editRepositoryName = ref("");
const editRepositoryEnglishName = ref("");
const editRepositoryTypeLabel = ref("");
const editRepositoryDeploymentMode = ref(EXTERNAL_DEPLOYMENT_MODE);
const editRepositoryGitUrl = ref("");
const repoGitUrlInputRef = ref<{ focus: () => void } | null>(null);
const editDialogVisible = ref(false);
const editNameInputRef = ref<{ focus: () => void } | null>(null);

async function run(action: () => Promise<void>) {
  loading.value = true;
  errorMessage.value = "";
  try {
    await action();
  } catch (error) {
    if (error instanceof Error) {
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

// 版本库管理
async function loadRepositories() {
  await run(async () => {
    const [all, types, deploymentOptions] = await Promise.all([
      api.listRepositories(1, 100),
      api.listRepositoryTypes(),
      api.getRepositoryDeploymentOptions()
    ]);
    repositories.value = all.items;
    repositoryTotal.value = all.total;
    repositoryTypes.value = types.length ? types : DEFAULT_REPOSITORY_TYPES;
    repositoryDeploymentOptions.value = deploymentOptions;
    repoDeploymentMode.value = deploymentOptions.defaultDeploymentMode || EXTERNAL_DEPLOYMENT_MODE;
  });
}

function isTestWorkRepositoryType(typeCode: string) {
  return typeCode === TEST_WORK_REPOSITORY_TYPE;
}

function repositoryTypeLabel(repository: CodeRepositoryConfig) {
  if (repository.repositoryTypeLabel) return repository.repositoryTypeLabel;
  if (repository.repositoryType) {
    return repositoryTypes.value.find((item) => item.typeCode === repository.repositoryType)?.typeLabel ?? repository.repositoryType;
  }
  return repository.standard ? "测试工作库" : "应用代码库";
}

function repositoryDeploymentModeLabel(mode: string) {
  return repositoryDeploymentOptions.value.options.find((item) => item.mode === mode)?.label ?? mode;
}

function isInternalRepositoryMode(mode: string) {
  return mode === INTERNAL_DEPLOYMENT_MODE;
}

const internalSshPrefix = computed(() => {
  if (repositoryDeploymentOptions.value.internalSshPrefix) return repositoryDeploymentOptions.value.internalSshPrefix;
  return props.currentUser?.unifiedAuthId ? `ssh://${props.currentUser.unifiedAuthId}@` : "ssh://";
});

const currentCreateInternal = computed(() => isInternalRepositoryMode(repoDeploymentMode.value));

const currentDeploymentMode = computed(() => repositoryDeploymentOptions.value.defaultDeploymentMode || EXTERNAL_DEPLOYMENT_MODE);
const currentDeploymentModeLabel = computed(() => repositoryDeploymentModeLabel(currentDeploymentMode.value));
const repositoryDeploymentModeWarning = computed(() => {
  if (repoDeploymentMode.value === currentDeploymentMode.value) return "";
  return `当前部署模式为${currentDeploymentModeLabel.value}，若修改部署模式，将导致无法访问版本库。`;
});

const editRepositoryDisplayGitUrl = computed(() => {
  if (isInternalRepositoryMode(editRepositoryDeploymentMode.value)) {
    return `${internalSshPrefix.value}${editRepositoryGitUrl.value}`;
  }
  return editRepositoryGitUrl.value;
});

function normalizeRepositoryEnglishName(value: string) {
  const trimmed = value.trim();
  if (!/^[A-Za-z0-9](?:[A-Za-z0-9-]{0,126}[A-Za-z0-9])?$/.test(trimmed)) {
    errorMessage.value = REPOSITORY_ENGLISH_NAME_ERROR;
    return "";
  }
  return trimmed.toLowerCase();
}

function deriveInternalRepositoryEnglishName(gitUrl: string) {
  const value = gitUrl.trim().replace(/\/+$/, "");
  const slash = value.indexOf("/");
  const path = slash >= 0 ? value.slice(slash + 1) : value;
  const withoutGitSuffix = path.endsWith(".git") ? path.slice(0, -4) : path;
  return withoutGitSuffix.replaceAll("/", "-").toLowerCase();
}

function syncDerivedEnglishName() {
  if (currentCreateInternal.value) {
    repoEnglishName.value = deriveInternalRepositoryEnglishName(repoGitUrl.value);
  } else {
    if (repoEnglishNameTouched.value) return;
    repoEnglishName.value = "";
  }
}

function markRepositoryEnglishNameTouched(value: string) {
  repoEnglishNameTouched.value = true;
  repoEnglishName.value = value;
}

function openCreateRepositoryDialog() {
  errorMessage.value = "";
  repoDeploymentMode.value = repositoryDeploymentOptions.value.defaultDeploymentMode || EXTERNAL_DEPLOYMENT_MODE;
  repoGitUrl.value = "";
  repoName.value = "";
  repoEnglishName.value = "";
  repoEnglishNameTouched.value = false;
  repoType.value = "";
  syncDerivedEnglishName();
  createDialogVisible.value = true;
}

function closeCreateRepositoryDialog() {
  createDialogVisible.value = false;
}

async function createRepository() {
  if (!repoName.value.trim()) {
    errorMessage.value = "请输入版本库名称";
    return;
  }
  if (!repoType.value) {
    errorMessage.value = "请选择版本库类型";
    return;
  }
  const resolvedEnglishName = repoEnglishName.value || (currentCreateInternal.value ? deriveInternalRepositoryEnglishName(repoGitUrl.value) : "");
  const englishName = normalizeRepositoryEnglishName(resolvedEnglishName);
  if (!englishName) {
    errorMessage.value = REPOSITORY_ENGLISH_NAME_ERROR;
    return;
  }
  await run(async () => {
    await api.createRepository({
      gitUrl: repoGitUrl.value.trim(),
      name: repoName.value.trim(),
      englishName,
      deploymentMode: repoDeploymentMode.value,
      repositoryType: repoType.value,
      standard: isTestWorkRepositoryType(repoType.value)
    });
    repoGitUrl.value = "";
    repoName.value = "";
    repoEnglishName.value = "";
    repoEnglishNameTouched.value = false;
    repoDeploymentMode.value = repositoryDeploymentOptions.value.defaultDeploymentMode || EXTERNAL_DEPLOYMENT_MODE;
    repoType.value = "";
    createDialogVisible.value = false;
    await loadRepositories();
  });
}

function startEditRepository(repository: CodeRepositoryConfig) {
  editRepositoryId.value = repository.repositoryId;
  editRepositoryName.value = repository.name;
  editRepositoryEnglishName.value = repository.englishName ?? "";
  editRepositoryTypeLabel.value = repositoryTypeLabel(repository);
  editRepositoryDeploymentMode.value = repository.deploymentMode || EXTERNAL_DEPLOYMENT_MODE;
  editRepositoryGitUrl.value = repository.gitUrl;
  editDialogVisible.value = true;
}

function cancelEditRepository() {
  editRepositoryId.value = "";
  editRepositoryName.value = "";
  editRepositoryEnglishName.value = "";
  editRepositoryTypeLabel.value = "";
  editRepositoryDeploymentMode.value = EXTERNAL_DEPLOYMENT_MODE;
  editRepositoryGitUrl.value = "";
  editDialogVisible.value = false;
}

async function saveRepository() {
  const englishName = normalizeRepositoryEnglishName(editRepositoryEnglishName.value);
  if (!englishName) {
    errorMessage.value = REPOSITORY_ENGLISH_NAME_ERROR;
    return;
  }
  await run(async () => {
    await api.updateRepository(editRepositoryId.value, {
      name: editRepositoryName.value.trim(),
      englishName
    });
    cancelEditRepository();
    await loadRepositories();
  });
}

// 初始加载
watch(() => props.currentUser, async (user) => {
  if (user && hasAppSettingsPermission.value) {
    await loadRepositories();
  } else {
    repositories.value = [];
    repositoryTotal.value = 0;
  }
}, { immediate: true });

watch(() => props.autoOpenCreate, (val) => {
  if (val) {
    openCreateRepositoryDialog();
  }
}, { immediate: true });

watch([repoGitUrl, repoDeploymentMode], syncDerivedEnglishName);

function focusGitUrlInput() {
  // 延迟聚焦才能稳定落到 Git URL 输入框
  window.setTimeout(() => {
    repoGitUrlInputRef.value?.focus();
  }, 100);
}

function focusEditNameInput() {
  // 延迟聚焦才能稳定落到名称输入框
  window.setTimeout(() => {
    editNameInputRef.value?.focus();
  }, 100);
}
</script>

<template>
  <div class="ta-settings-repository">
    <!-- 权限不足提示 -->
    <div v-if="!hasAppSettingsPermission" class="ta-permission-placeholder">
      <el-alert :title="`您当前角色[${currentRoleLabel}]无该项设置权限。`" type="warning" :closable="false" show-icon />
    </div>

    <template v-else>
      <el-alert v-if="errorMessage && !createDialogVisible && !editDialogVisible" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" />

      <div class="ta-panel-content">
        <div class="ta-section">
          <div class="ta-section-header">
            <h4 class="ta-section-title">已有版本库</h4>
            <div class="ta-section-actions">
              <span class="ta-count-badge">共 {{ repositoryTotal }} 个版本库</span>
              <el-button :disabled="loading" @click="loadRepositories">刷新</el-button>
              <el-button type="primary" @click="openCreateRepositoryDialog">新增</el-button>
            </div>
          </div>
          <div v-for="repo in repositories" :key="repo.repositoryId" class="ta-item-row ta-edit-item">
            <div>
              <span class="ta-item-title">{{ repo.name }}</span>
              <span class="ta-item-badge">{{ repositoryTypeLabel(repo) }}</span>
              <div class="ta-item-subtitle">{{ repo.englishName || "未配置英文名" }} · {{ repo.gitUrl }}</div>
            </div>
            <el-button size="small" @click="startEditRepository(repo)">编辑</el-button>
          </div>
          <!-- 编辑版本库弹窗已在下方定义 -->
        </div>

        <!-- 新增版本库弹窗 -->
        <el-dialog
          v-model="createDialogVisible"
          title="新增版本库"
          width="540px"
          :close-on-click-modal="false"
          align-center
          @opened="focusGitUrlInput"
        >
          <el-alert v-if="errorMessage && createDialogVisible" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" style="margin-bottom: 16px;" />
          <el-form label-width="120px">
            <el-form-item label="部署模式">
              <div class="ta-deployment-mode-field">
                <el-select v-model="repoDeploymentMode" aria-label="部署模式" style="width: 160px" filterable>
                  <el-option v-for="option in repositoryDeploymentOptions.options" :key="option.mode" :label="option.label" :value="option.mode" />
                </el-select>
                <el-alert
                  v-if="repositoryDeploymentModeWarning"
                  :title="repositoryDeploymentModeWarning"
                  type="warning"
                  :closable="false"
                  show-icon
                  class="ta-deployment-mode-warning"
                />
              </div>
            </el-form-item>
            <el-form-item label="版本库地址">
              <div v-if="currentCreateInternal" class="ta-git-url-input-group">
                <span class="ta-git-url-prefix">{{ internalSshPrefix }}</span>
                <el-input ref="repoGitUrlInputRef" v-model="repoGitUrl" placeholder="scm-share.sdc.cs.icbc:29418/group/repository" />
              </div>
              <el-input v-else ref="repoGitUrlInputRef" v-model="repoGitUrl" placeholder="Git URL" />
            </el-form-item>
            <el-form-item label="版本库名称">
              <el-input v-model="repoName" placeholder="中文名称" />
            </el-form-item>
            <el-form-item label="版本库英文名称">
              <el-input :model-value="repoEnglishName" placeholder="英文名称" :disabled="currentCreateInternal" @update:model-value="markRepositoryEnglishNameTouched" />
            </el-form-item>
            <el-form-item label="版本库类型">
              <div style="display: flex; align-items: center; gap: 8px;">
                <el-select v-model="repoType" aria-label="版本库类型" placeholder="选择版本库类型" style="width: 160px" filterable>
                  <el-option v-for="type in repositoryTypes" :key="type.typeCode" :label="type.typeLabel" :value="type.typeCode" />
                </el-select>
                <el-tooltip :content="STANDARD_REPOSITORY_TOOLTIP" placement="top">
                  <el-icon class="ta-help-icon" :title="STANDARD_REPOSITORY_TOOLTIP" aria-label="标准库说明">
                    <InfoFilled />
                  </el-icon>
                </el-tooltip>
              </div>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="closeCreateRepositoryDialog">取消</el-button>
            <el-button type="primary" :disabled="loading" @click="createRepository">新增</el-button>
          </template>
        </el-dialog>

        <!-- 编辑版本库弹窗 -->
        <el-dialog
          v-model="editDialogVisible"
          title="编辑版本库"
          width="540px"
          :close-on-click-modal="false"
          align-center
          @opened="focusEditNameInput"
        >
          <el-alert v-if="errorMessage && editDialogVisible" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" style="margin-bottom: 16px;" />
          <el-form label-width="120px">
            <el-form-item label="版本库名称">
              <el-input ref="editNameInputRef" v-model="editRepositoryName" placeholder="名称" />
            </el-form-item>
            <el-form-item label="版本库地址">
              <span class="ta-readonly-field ta-readonly-url">{{ editRepositoryDisplayGitUrl }}</span>
            </el-form-item>
            <el-form-item label="版本库英文名称">
              <el-input v-model="editRepositoryEnglishName" placeholder="英文名称" />
            </el-form-item>
            <el-form-item label="部署模式">
              <span class="ta-readonly-field">{{ repositoryDeploymentModeLabel(editRepositoryDeploymentMode) }}</span>
            </el-form-item>
            <el-form-item label="版本库类型">
              <span class="ta-readonly-field">{{ editRepositoryTypeLabel }}</span>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="cancelEditRepository">取消</el-button>
            <el-button type="primary" :disabled="loading" @click="saveRepository">保存</el-button>
          </template>
        </el-dialog>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ta-settings-repository {
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
.ta-readonly-field {
  font-size: 13px;
  color: #606266;
  line-height: 32px;
}
.ta-readonly-url {
  display: inline-block;
  max-width: 100%;
  overflow-wrap: anywhere;
  line-height: 20px;
  padding: 6px 0;
}
.ta-git-url-input-group {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
}
.ta-git-url-prefix {
  flex-shrink: 0;
  max-width: 210px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 8px;
  height: 32px;
  line-height: 32px;
  font-size: 12px;
  color: #606266;
  border: 1px solid #dcdfe6;
  border-right: 0;
  border-radius: 4px 0 0 4px;
  background: #f5f7fa;
}
.ta-deployment-mode-field {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
}
.ta-deployment-mode-warning {
  width: 100%;
}
.ta-repository-create-form {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
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
.ta-help-icon {
  color: #909399;
  cursor: help;
}
.ta-error {
  margin-bottom: 8px;
}
.ta-permission-placeholder {
  padding: 40px 0;
}
</style>
