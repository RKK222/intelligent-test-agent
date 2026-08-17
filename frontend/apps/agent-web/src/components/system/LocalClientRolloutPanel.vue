<script setup lang="ts">
import { inject, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { LocalClientRolloutUser, UserManagementUser } from "@test-agent/shared-types";
import { Download, LoaderCircle, Plus, RefreshCw, Trash2, UsersRound } from "lucide-vue-next";

const api = inject<BackendApiClient>("api")!;
if (!api) throw new Error("LocalClientRolloutPanel requires backend api");

const loading = ref(true);
const error = ref("");
const rolloutUsers = ref<LocalClientRolloutUser[]>([]);
const dialogOpen = ref(false);
const candidates = ref<UserManagementUser[]>([]);
const candidatesLoading = ref(false);
const candidateError = ref("");
const selectedUserId = ref("");
const adding = ref(false);
let candidateRequest = 0;

onMounted(() => void load());

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const page = await api.listLocalClientRolloutUsers(1, 200);
    rolloutUsers.value = page.items;
  } catch (caught) {
    error.value = caught instanceof Error ? caught.message : "本地客户端灰度名单加载失败";
  } finally {
    loading.value = false;
  }
}

function openDialog() {
  selectedUserId.value = "";
  candidateError.value = "";
  dialogOpen.value = true;
  void loadCandidates();
}

/** 复用平台用户远程搜索，只显示可登录且尚未灰度的用户。 */
async function loadCandidates(keyword = "") {
  const requestId = ++candidateRequest;
  candidatesLoading.value = true;
  candidateError.value = "";
  try {
    const page = await api.listUsers({ keyword: keyword.trim(), page: 1, size: 30 });
    if (requestId !== candidateRequest) return;
    const enabledIds = new Set(rolloutUsers.value.map(user => user.userId));
    candidates.value = page.items.filter(user => user.status === "ACTIVE" && !enabledIds.has(user.userId));
  } catch (caught) {
    if (requestId !== candidateRequest) return;
    candidates.value = [];
    candidateError.value = caught instanceof Error ? caught.message : "用户目录加载失败";
  } finally {
    if (requestId === candidateRequest) candidatesLoading.value = false;
  }
}

function candidateLabel(user: UserManagementUser) {
  return `${user.username} · ${user.unifiedAuthId || "无统一认证号"} · ${user.userId}`;
}

async function addUser() {
  if (!selectedUserId.value) return;
  adding.value = true;
  candidateError.value = "";
  try {
    await api.enableLocalClientRolloutUser(selectedUserId.value);
    ElMessage.success("已开放本地客户端下载入口");
    dialogOpen.value = false;
    await load();
  } catch (caught) {
    candidateError.value = caught instanceof Error ? caught.message : "添加灰度用户失败";
  } finally {
    adding.value = false;
  }
}

async function removeUser(user: LocalClientRolloutUser) {
  try {
    await ElMessageBox.confirm(
      `确定关闭 ${user.userId} 的本地客户端下载入口吗？已安装客户端和已有 key 不会被删除。`,
      "移出客户端灰度",
      { type: "warning", confirmButtonText: "移出", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  try {
    await api.disableLocalClientRolloutUser(user.userId);
    ElMessage.success("已关闭本地客户端下载入口");
    await load();
  } catch (caught) {
    ElMessage.error(caught instanceof Error ? caught.message : "移出灰度用户失败");
  }
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
</script>

<template>
  <section class="rollout-panel" aria-labelledby="local-client-rollout-title" data-testid="local-client-rollout-panel">
    <header class="rollout-panel__header">
      <div>
        <div class="rollout-panel__eyebrow"><Download :size="15" /> LOCAL CLIENT ROLLOUT</div>
        <h2 id="local-client-rollout-title">本地客户端灰度</h2>
        <p>默认对所有用户隐藏下载入口。加入名单后，仅该 userId 可以在头像菜单中看到下载安装入口。</p>
      </div>
      <div class="rollout-panel__actions">
        <button type="button" :disabled="loading" aria-label="刷新灰度名单" @click="load">
          <RefreshCw :size="15" :class="{ spinning: loading }" />刷新
        </button>
        <button type="button" class="primary" data-testid="add-local-client-rollout-user" @click="openDialog">
          <Plus :size="15" />添加用户
        </button>
      </div>
    </header>

    <div v-if="loading" class="rollout-panel__state"><LoaderCircle class="spinning" :size="22" />正在读取灰度名单</div>
    <div v-else-if="error" class="rollout-panel__state rollout-panel__state--error">
      {{ error }}<button type="button" @click="load">重试</button>
    </div>
    <div v-else-if="!rolloutUsers.length" class="rollout-panel__state">
      <UsersRound :size="22" />当前没有灰度用户，下载入口对所有用户隐藏
    </div>
    <div v-else class="rollout-list" aria-label="本地客户端灰度用户列表">
      <article v-for="user in rolloutUsers" :key="user.userId" class="rollout-user">
        <div class="rollout-user__identity">
          <strong>{{ user.userId }}</strong>
          <span>加入时间 {{ formatTime(user.createdAt) }}</span>
          <small>最近操作人 {{ user.updatedByUserId }} · {{ formatTime(user.updatedAt) }}</small>
        </div>
        <button type="button" class="danger" :aria-label="`移出 ${user.userId}`" @click="removeUser(user)">
          <Trash2 :size="15" />移出
        </button>
      </article>
    </div>

    <el-dialog v-model="dialogOpen" title="添加本地客户端灰度用户" width="560px" append-to-body>
      <p class="rollout-dialog__hint">搜索并选择一个可登录的平台用户。保存后，该用户刷新页面即可看到下载入口。</p>
      <el-select
        v-model="selectedUserId"
        filterable
        remote
        clearable
        :remote-method="loadCandidates"
        :loading="candidatesLoading"
        placeholder="输入姓名、统一认证号或 userId"
        aria-label="选择本地客户端灰度用户"
        style="width: 100%"
      >
        <el-option
          v-for="user in candidates"
          :key="user.userId"
          :label="candidateLabel(user)"
          :value="user.userId"
        />
      </el-select>
      <p v-if="candidateError" class="rollout-dialog__error">{{ candidateError }}</p>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="adding" :disabled="!selectedUserId" @click="addUser">确认添加</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.rollout-panel {
  min-height: 100%;
  padding: 24px;
  box-sizing: border-box;
  background: #f7f8fa;
  color: #1f2937;
}
.rollout-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}
.rollout-panel__header h2 { margin: 5px 0 7px; font-size: 22px; }
.rollout-panel__header p { margin: 0; color: #667085; font-size: 13px; line-height: 1.6; }
.rollout-panel__eyebrow { display: flex; align-items: center; gap: 6px; color: #2563eb; font-size: 11px; font-weight: 700; letter-spacing: .08em; }
.rollout-panel__actions { display: flex; gap: 8px; flex-shrink: 0; }
.rollout-panel button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 34px; padding: 0 12px; border: 1px solid #d0d5dd; border-radius: 6px; background: #fff; color: #344054; cursor: pointer; }
.rollout-panel button.primary { border-color: #2563eb; background: #2563eb; color: #fff; }
.rollout-panel button.danger { border-color: #fecaca; color: #b42318; }
.rollout-panel button:disabled { cursor: not-allowed; opacity: .55; }
.rollout-panel__state { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 180px; border: 1px dashed #d0d5dd; border-radius: 10px; background: #fff; color: #667085; font-size: 13px; }
.rollout-panel__state--error { color: #b42318; }
.rollout-list { display: grid; gap: 10px; }
.rollout-user { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 18px; border: 1px solid #e4e7ec; border-radius: 10px; background: #fff; }
.rollout-user__identity { display: grid; gap: 4px; min-width: 0; }
.rollout-user__identity strong { color: #101828; font-size: 14px; word-break: break-all; }
.rollout-user__identity span, .rollout-user__identity small { color: #667085; font-size: 12px; }
.rollout-dialog__hint { margin: 0 0 14px; color: #667085; font-size: 13px; line-height: 1.6; }
.rollout-dialog__error { margin: 10px 0 0; color: #b42318; font-size: 12px; }
.spinning { animation: rollout-spin .9s linear infinite; }
@keyframes rollout-spin { to { transform: rotate(360deg); } }
@media (max-width: 720px) {
  .rollout-panel { padding: 16px; }
  .rollout-panel__header { flex-direction: column; }
  .rollout-panel__actions { width: 100%; }
  .rollout-panel__actions button { flex: 1; }
}
</style>
