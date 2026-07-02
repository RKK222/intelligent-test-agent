<script setup lang="ts">
import { computed, inject, nextTick, onMounted, ref, watch } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CodeRepositoryConfig,
  CurrentUser,
  RepositoryTypeOption
} from "@test-agent/shared-types";
import { InfoFilled } from "@element-plus/icons-vue";

const TEST_WORK_REPOSITORY_TYPE = "TEST_WORK_REPOSITORY";
const APPLICATION_CODE_REPOSITORY_TYPE = "APPLICATION_CODE_REPOSITORY";
const STANDARD_REPOSITORY_TOOLTIP = "测试工作库等价于原标准库，会按标准库分支规则创建工作空间。";
const DEFAULT_REPOSITORY_TYPES: RepositoryTypeOption[] = [
  { typeCode: TEST_WORK_REPOSITORY_TYPE, typeLabel: "测试工作库" },
  { typeCode: APPLICATION_CODE_REPOSITORY_TYPE, typeLabel: "应用代码库" },
  { typeCode: "APPLICATION_ASSET_REPOSITORY", typeLabel: "应用资产库" }
];

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
const repoGitUrl = ref("");
const repoName = ref("");
const repoEnglishName = ref("");
const repoType = ref(APPLICATION_CODE_REPOSITORY_TYPE);
const editRepositoryId = ref("");
const editRepositoryName = ref("");
const editRepositoryEnglishName = ref("");
const editRepositoryTypeLabel = ref("");
const repoGitUrlInputRef = ref<{ focus: () => void } | null>(null);

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
    const [all, types] = await Promise.all([
      api.listRepositories(1, 100),
      api.listRepositoryTypes()
    ]);
    repositories.value = all.items;
    repositoryTotal.value = all.total;
    repositoryTypes.value = types.length ? types : DEFAULT_REPOSITORY_TYPES;
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

function normalizeRepositoryEnglishName(value: string) {
  const trimmed = value.trim();
  if (!/^[A-Za-z]{1,29}$/.test(trimmed)) {
    errorMessage.value = "版本库英文名称只能输入 1 到 29 位英文字母";
    return "";
  }
  return trimmed.toLowerCase();
}

async function createRepository() {
  const englishName = normalizeRepositoryEnglishName(repoEnglishName.value);
  if (!englishName) {
    errorMessage.value = "版本库英文名称只能输入 1 到 29 位英文字母";
    return;
  }
  await run(async () => {
    await api.createRepository({
      gitUrl: repoGitUrl.value.trim(),
      name: repoName.value.trim(),
      englishName,
      repositoryType: repoType.value,
      standard: isTestWorkRepositoryType(repoType.value)
    });
    repoGitUrl.value = "";
    repoName.value = "";
    repoEnglishName.value = "";
    repoType.value = APPLICATION_CODE_REPOSITORY_TYPE;
    createDialogVisible.value = false;
    await loadRepositories();
  });
}

function startEditRepository(repository: CodeRepositoryConfig) {
  editRepositoryId.value = repository.repositoryId;
  editRepositoryName.value = repository.name;
  editRepositoryEnglishName.value = repository.englishName ?? "";
  editRepositoryTypeLabel.value = repositoryTypeLabel(repository);
}

function cancelEditRepository() {
  editRepositoryId.value = "";
  editRepositoryName.value = "";
  editRepositoryEnglishName.value = "";
  editRepositoryTypeLabel.value = "";
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
    createDialogVisible.value = true;
  }
}, { immediate: true });

function focusGitUrlInput() {
  // 延迟聚焦才能稳定落到 Git URL 输入框
  window.setTimeout(() => {
    repoGitUrlInputRef.value?.focus();
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
      <el-alert v-if="errorMessage && !createDialogVisible" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" />

      <div class="ta-panel-content">
        <div class="ta-section">
          <div class="ta-section-header">
            <h4 class="ta-section-title">已有版本库</h4>
            <div class="ta-section-actions">
              <span class="ta-count-badge">共 {{ repositoryTotal }} 个版本库</span>
              <el-button :disabled="loading" @click="loadRepositories">刷新</el-button>
              <el-button type="primary" @click="createDialogVisible = true">新增</el-button>
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
          <div v-if="editRepositoryId" class="ta-inline-form ta-edit-form">
            <label class="ta-form-field">
              <span class="ta-form-label">版本库名称</span>
              <el-input v-model="editRepositoryName" placeholder="名称" style="width: 240px" />
            </label>
            <label class="ta-form-field">
              <span class="ta-form-label">版本库英文名称</span>
              <el-input v-model="editRepositoryEnglishName" placeholder="英文名称" style="width: 180px" />
            </label>
            <span class="ta-readonly-field">类型：{{ editRepositoryTypeLabel }}</span>
            <el-button type="primary" :disabled="loading" @click="saveRepository">保存</el-button>
            <el-button :disabled="loading" @click="cancelEditRepository">取消</el-button>
          </div>
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
          <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" style="margin-bottom: 16px;" />
          <el-form label-width="120px">
            <el-form-item label="版本库地址">
              <el-input ref="repoGitUrlInputRef" v-model="repoGitUrl" placeholder="Git URL" />
            </el-form-item>
            <el-form-item label="版本库名称">
              <el-input v-model="repoName" placeholder="中文名称" />
            </el-form-item>
            <el-form-item label="版本库英文名称">
              <el-input v-model="repoEnglishName" placeholder="英文名称" />
            </el-form-item>
            <el-form-item label="版本库类型">
              <div style="display: flex; align-items: center; gap: 8px;">
                <el-select v-model="repoType" aria-label="版本库类型" style="width: 160px">
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
            <el-button @click="createDialogVisible = false">取消</el-button>
            <el-button type="primary" :disabled="loading" @click="createRepository">新增</el-button>
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
