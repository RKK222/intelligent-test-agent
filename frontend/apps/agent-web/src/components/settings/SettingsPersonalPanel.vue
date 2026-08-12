<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, ref } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  LocalClientCredential,
  LocalClientDirectoryEntry,
  LocalClientInstance,
  SshKeyMetadata,
  Workspace
} from "@test-agent/shared-types";
import { copyTextToClipboard } from "@test-agent/ui-kit";
import { ElMessage, ElMessageBox } from "element-plus";
import { Delete, Folder, Key, Refresh, VideoPlay, VideoPause } from "@element-plus/icons-vue";
import { encryptSshKey } from "../../utils/ssh-crypto";

// SettingsPanel 统一向所有面板传入 currentUser；个人设置面板目前不依赖该字段，
// 但保留 prop 以避免 Vue 透传告警，类型与 SettingsAppWorkspacePanel 保持一致。
defineProps<{
  currentUser: CurrentUser | null;
}>();

const emit = defineEmits<{
  (event: "workspace-catalog-changed"): void;
}>();

const api = inject<BackendApiClient>("api")!;

const sshKeys = ref<SshKeyMetadata[]>([]);
const sshKeyName = ref("");
const sshPrivateKey = ref("");
const loading = ref(false);
const errorMessage = ref("");
const localClientLoading = ref(false);
const localClientError = ref("");
const credential = ref<LocalClientCredential | null>(null);
const localClients = ref<LocalClientInstance[]>([]);
const localWorkspaces = ref<Workspace[]>([]);
const localWorkspaceClientId = ref("");
const localWorkspaceName = ref("");
const localWorkspaceRoot = ref("");
const pickerOpen = ref(false);
const pickerLoading = ref(false);
const pickerError = ref("");
const pickerPath = ref("/");
const pickerEntries = ref<LocalClientDirectoryEntry[]>([]);
let localClientRefreshTimer: ReturnType<typeof setInterval> | undefined;

const onlineLocalClients = computed(() => localClients.value.filter((client) => client.online));
const selectedLocalClient = computed(() =>
  localClients.value.find((client) => client.clientInstanceId === localWorkspaceClientId.value) ?? null
);

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
    // 先获取服务端 RSA 公钥，再在浏览器端完成混合加密后传输密文
    const { publicKey } = await api.getSshKeyPublicKey();
    const encrypted = await encryptSshKey(sshPrivateKey.value, publicKey);
    await api.addPersonalSshKey({
      name: sshKeyName.value.trim(),
      encryptedPrivateKey: encrypted.encryptedPrivateKey,
      encryptedAesKey: encrypted.encryptedAesKey,
      encryptionNonce: encrypted.encryptionNonce,
      fingerprint: encrypted.fingerprint,
    });
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

/** 状态轮询只读取掩码与实例元数据，不请求也不持有明文 client key。 */
async function loadLocalClientState(silent = false) {
  if (!silent) localClientLoading.value = true;
  localClientError.value = "";
  try {
    const [credentialView, clients, workspaces] = await Promise.all([
      api.getMyLocalClientCredential(),
      api.listMyLocalClientInstances(),
      api.listWorkspaces(1, 100)
    ]);
    credential.value = credentialView;
    localClients.value = clients;
    localWorkspaces.value = workspaces.items.filter((workspace) => workspace.runtimeKind === "LOCAL_CLIENT");
    if (!onlineLocalClients.value.some((client) => client.clientInstanceId === localWorkspaceClientId.value)) {
      localWorkspaceClientId.value = onlineLocalClients.value[0]?.clientInstanceId ?? "";
    }
  } catch (error) {
    localClientError.value = error instanceof Error ? error.message : "读取本地客户端状态失败";
  } finally {
    if (!silent) localClientLoading.value = false;
  }
}

async function createCredential() {
  await runLocalClientAction(async () => {
    credential.value = await api.createMyLocalClientCredential();
    ElMessage.success("Client key 已创建，请点击复制后写入客户端配置");
  });
}

/** 明文只存在于当前调用栈，复制结束立即清空局部引用，不写响应式状态或浏览器存储。 */
async function copyCredential() {
  await runLocalClientAction(async () => {
    let plaintext = "";
    try {
      plaintext = (await api.copyMyLocalClientCredential()).clientKey;
      if (!plaintext || !await copyTextToClipboard(plaintext)) {
        throw new Error("浏览器未允许写入剪贴板");
      }
      ElMessage.success("Client key 已复制，请立即粘贴到客户端配置");
    } finally {
      plaintext = "";
    }
  });
}

async function rotateCredential() {
  try {
    await ElMessageBox.confirm(
      "轮换后当前用户的所有本地客户端会立即断开，所有设备都需要更新新 key。",
      "确认轮换 Client key",
      { type: "warning", confirmButtonText: "确认轮换", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await runLocalClientAction(async () => {
    credential.value = await api.rotateMyLocalClientCredential();
    await loadLocalClientState(true);
    ElMessage.success("Client key 已轮换，请复制新 key 并更新所有设备");
  });
}

async function revokeCredential() {
  try {
    await ElMessageBox.confirm(
      "撤销后所有本地客户端会立即断开；已注册工作区记录保留，但离线期间不可访问。",
      "确认撤销 Client key",
      { type: "warning", confirmButtonText: "确认撤销", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await runLocalClientAction(async () => {
    await api.revokeMyLocalClientCredential();
    await loadLocalClientState(true);
    ElMessage.success("Client key 已撤销");
  });
}

async function commandLocalClient(
  client: LocalClientInstance,
  action: "START" | "RESTART" | "STOP" | "STATUS"
) {
  await runLocalClientAction(async () => {
    const result = await api.commandLocalClientOpencode(client.clientInstanceId, action);
    await loadLocalClientState(true);
    ElMessage.success(result.message || "本地 OpenCode 命令已执行");
  });
}

async function runLocalClientAction(action: () => Promise<void>) {
  localClientLoading.value = true;
  localClientError.value = "";
  try {
    await action();
  } catch (error) {
    localClientError.value = error instanceof Error ? error.message : "本地客户端操作失败";
  } finally {
    localClientLoading.value = false;
  }
}

async function openDirectoryPicker() {
  if (!selectedLocalClient.value) return;
  pickerOpen.value = true;
  pickerPath.value = localWorkspaceRoot.value.trim() || "/";
  await loadPickerDirectory(pickerPath.value);
}

async function loadPickerDirectory(path: string) {
  if (!selectedLocalClient.value) return;
  pickerLoading.value = true;
  pickerError.value = "";
  try {
    pickerEntries.value = await api.listLocalClientDirectories(
      selectedLocalClient.value.clientInstanceId,
      path
    );
    pickerPath.value = path;
  } catch (error) {
    pickerError.value = error instanceof Error ? error.message : "目录读取失败";
  } finally {
    pickerLoading.value = false;
  }
}

function parentDirectory(path: string) {
  const normalized = path.replace(/\\/g, "/").replace(/\/+$/, "");
  if (!normalized || normalized === "/") return null;
  const slash = normalized.lastIndexOf("/");
  return slash <= 0 ? "/" : normalized.slice(0, slash);
}

function selectPickerDirectory() {
  localWorkspaceRoot.value = pickerPath.value;
  if (!localWorkspaceName.value.trim()) {
    localWorkspaceName.value = pickerPath.value.replace(/\\/g, "/").split("/").filter(Boolean).pop() ?? "本地工作区";
  }
  pickerOpen.value = false;
}

async function createLocalWorkspace() {
  const client = selectedLocalClient.value;
  if (!client) return;
  await runLocalClientAction(async () => {
    await api.createLocalWorkspace({
      clientInstanceId: client.clientInstanceId,
      name: localWorkspaceName.value.trim(),
      rootPath: localWorkspaceRoot.value.trim()
    });
    localWorkspaceName.value = "";
    localWorkspaceRoot.value = "";
    await loadLocalClientState(true);
    emit("workspace-catalog-changed");
    ElMessage.success("本地工作区已注册，原目录内容未被复制或修改");
  });
}

async function deleteLocalWorkspace(workspace: Workspace) {
  try {
    await ElMessageBox.confirm(
      `只注销平台记录，不会删除本地目录：${workspace.physicalRootPath ?? workspace.rootPath}`,
      `注销 ${workspace.name}`,
      { type: "warning", confirmButtonText: "仅注销记录", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await runLocalClientAction(async () => {
    const result = await api.deleteLocalWorkspace(workspace.workspaceId);
    if (result.localDirectoryDeleted) throw new Error("服务端返回了不允许的本地目录删除状态");
    await loadLocalClientState(true);
    emit("workspace-catalog-changed");
    ElMessage.success("工作区记录已注销，本地目录未删除");
  });
}

onMounted(() => {
  loadSshKeys();
  void loadLocalClientState();
  localClientRefreshTimer = setInterval(() => void loadLocalClientState(true), 5_000);
});

onBeforeUnmount(() => {
  if (localClientRefreshTimer) clearInterval(localClientRefreshTimer);
});
</script>

<template>
  <div class="ta-personal">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="ta-error" />
    <el-alert v-if="localClientError" :title="localClientError" type="error" :closable="false" show-icon class="ta-error" />

    <section class="ta-section ta-local-client-section" aria-label="本地 OpenCode 客户端">
      <div class="ta-section-heading">
        <div>
          <h4 class="ta-section-title">本地 OpenCode 客户端</h4>
          <p class="ta-section-description">控制通道使用 HTTPS/WSS；client key 仅用于客户端认证，不参与 HTTP 制品下载。</p>
        </div>
        <el-button size="small" :loading="localClientLoading" @click="loadLocalClientState()">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <div class="ta-credential-card">
        <div>
          <div class="ta-item-title">Client key</div>
          <div class="ta-masked-key">{{ credential?.exists ? credential.maskedKey : "尚未创建" }}</div>
          <div v-if="credential?.exists" class="ta-item-subtitle">版本 {{ credential.version }} · {{ credential.status }}</div>
        </div>
        <div class="ta-row-actions">
          <el-button v-if="!credential?.exists || credential.status === 'REVOKED'" size="small" type="primary" :disabled="localClientLoading" @click="createCredential">创建</el-button>
          <template v-else>
            <el-button size="small" type="primary" :disabled="localClientLoading" @click="copyCredential">复制</el-button>
            <el-button size="small" :disabled="localClientLoading" @click="rotateCredential">轮换</el-button>
            <el-button size="small" type="danger" plain :disabled="localClientLoading" @click="revokeCredential">撤销</el-button>
          </template>
        </div>
      </div>

      <div v-if="localClients.length" class="ta-client-list">
        <article v-for="client in localClients" :key="client.clientInstanceId" class="ta-client-card">
          <div class="ta-client-main">
            <span :class="['ta-online-dot', client.online && 'is-online']" aria-hidden="true" />
            <div>
              <div class="ta-item-title">{{ client.clientName }}</div>
              <div class="ta-item-subtitle">
                {{ client.platform }} / {{ client.architecture }} · 客户端 {{ client.clientVersion }} · OpenCode {{ client.opencodeVersion }}
              </div>
              <div class="ta-item-subtitle">
                {{ client.processStatus }} · {{ client.observedRemoteAddress || '未观察到远端地址' }}<template v-if="client.opencodePort"> · 端口 {{ client.opencodePort }}</template>
              </div>
            </div>
          </div>
          <div class="ta-row-actions">
            <el-button size="small" :disabled="localClientLoading || !client.online" @click="commandLocalClient(client, 'START')"><el-icon><VideoPlay /></el-icon>启动</el-button>
            <el-button size="small" :disabled="localClientLoading || !client.online" @click="commandLocalClient(client, 'RESTART')">重启</el-button>
            <el-button size="small" :disabled="localClientLoading || !client.online" @click="commandLocalClient(client, 'STOP')"><el-icon><VideoPause /></el-icon>停止</el-button>
          </div>
        </article>
      </div>
      <el-empty v-else :image-size="56" description="暂无已认证客户端实例" />

      <div class="ta-local-workspace-form">
        <h5>注册本地工作区</h5>
        <el-select v-model="localWorkspaceClientId" placeholder="选择在线客户端" style="width: 100%">
          <el-option v-for="client in onlineLocalClients" :key="client.clientInstanceId" :label="client.clientName" :value="client.clientInstanceId" />
        </el-select>
        <el-input v-model="localWorkspaceName" maxlength="120" placeholder="工作区名称" />
        <div class="ta-path-row">
          <el-input v-model="localWorkspaceRoot" placeholder="绝对路径，例如 /Users/me/project" />
          <el-button :disabled="!selectedLocalClient" @click="openDirectoryPicker"><el-icon><Folder /></el-icon>浏览</el-button>
        </div>
        <el-button
          type="primary"
          :disabled="localClientLoading || !selectedLocalClient || !localWorkspaceName.trim() || !localWorkspaceRoot.trim()"
          @click="createLocalWorkspace"
        >注册工作区</el-button>
      </div>

      <div v-if="localWorkspaces.length" class="ta-item-list">
        <h5>已注册本地工作区</h5>
        <div v-for="workspace in localWorkspaces" :key="workspace.workspaceId" class="ta-item-row">
          <div class="ta-item-row-left">
            <el-icon><Folder /></el-icon>
            <div>
              <div class="ta-item-title">{{ workspace.name }}</div>
              <div class="ta-item-subtitle">{{ workspace.physicalRootPath ?? workspace.rootPath }}</div>
            </div>
          </div>
          <el-button size="small" type="danger" plain :disabled="localClientLoading" @click="deleteLocalWorkspace(workspace)">
            <el-icon><Delete /></el-icon> 仅注销
          </el-button>
        </div>
      </div>
    </section>

    <!-- 已有 SSH key 列表 -->
    <div v-if="sshKeys.length" class="ta-section">
      <h4 class="ta-section-title">已添加的 SSH Key</h4>
      <div class="ta-item-list">
        <div v-for="sshKey in sshKeys" :key="sshKey.sshKeyId" class="ta-item-row">
          <div class="ta-item-row-left">
            <el-icon><Key /></el-icon>
            <div>
              <div class="ta-item-title">{{ sshKey.name }}</div>
              <div class="ta-item-subtitle">{{ sshKey.fingerprint }}</div>
            </div>
          </div>
          <el-button size="small" type="danger" plain :disabled="loading" @click="deleteSshKey(sshKey.sshKeyId)">
            <el-icon><Delete /></el-icon> 删除
          </el-button>
        </div>
      </div>
    </div>

    <!-- 添加 SSH key（无 key 时显示） -->
    <div v-if="sshKeys.length === 0" class="ta-section">
      <h4 class="ta-section-title">添加 SSH Key</h4>
      <el-form label-position="top" class="ta-settings-form">
        <el-form-item label="SSH Key 名称">
          <el-input v-model="sshKeyName" placeholder="SSH key 名称" style="width: 320px" />
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

    <el-dialog v-model="pickerOpen" title="选择本地目录" width="720px" append-to-body :close-on-click-modal="false">
      <el-alert v-if="pickerError" :title="pickerError" type="error" :closable="false" show-icon class="ta-error" />
      <div class="ta-picker-toolbar">
        <el-button size="small" :disabled="!parentDirectory(pickerPath) || pickerLoading" @click="loadPickerDirectory(parentDirectory(pickerPath)!)">上一级</el-button>
        <el-input v-model="pickerPath" @keyup.enter="loadPickerDirectory(pickerPath)" />
        <el-button size="small" :loading="pickerLoading" @click="loadPickerDirectory(pickerPath)">转到</el-button>
      </div>
      <div v-loading="pickerLoading" class="ta-picker-list">
        <button
          v-for="entry in pickerEntries"
          :key="entry.absolutePath"
          type="button"
          :disabled="!entry.directory || entry.symbolicLink || !entry.readable"
          @dblclick="loadPickerDirectory(entry.absolutePath)"
          @click="entry.directory && !entry.symbolicLink && entry.readable && loadPickerDirectory(entry.absolutePath)"
        >
          <el-icon><Folder /></el-icon>
          <span>{{ entry.name }}</span>
          <small v-if="entry.symbolicLink">符号链接不可选</small>
        </button>
      </div>
      <template #footer>
        <el-button @click="pickerOpen = false">取消</el-button>
        <el-button type="primary" :disabled="pickerLoading" @click="selectPickerDirectory">使用当前目录</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ta-personal {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 760px;
}
.ta-section-heading,
.ta-credential-card,
.ta-client-card,
.ta-client-main,
.ta-row-actions,
.ta-path-row,
.ta-picker-toolbar {
  display: flex;
  align-items: center;
}
.ta-section-heading,
.ta-credential-card,
.ta-client-card { justify-content: space-between; gap: 16px; }
.ta-section-description { margin: 4px 0 0; color: #777; font-size: 12px; line-height: 1.5; }
.ta-local-client-section { padding-bottom: 18px; border-bottom: 1px solid #ebeef5; }
.ta-credential-card,
.ta-client-card,
.ta-local-workspace-form { padding: 12px; border: 1px solid #ebeef5; border-radius: 8px; background: #fff; }
.ta-masked-key { margin-top: 4px; font: 12px ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; color: #4c4d4f; }
.ta-row-actions { flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.ta-row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.ta-client-list { display: flex; flex-direction: column; gap: 8px; }
.ta-client-main { align-items: flex-start; gap: 9px; min-width: 0; }
.ta-online-dot { width: 8px; height: 8px; margin-top: 5px; border-radius: 50%; background: #b4b4b4; box-shadow: 0 0 0 3px #f2f2f2; }
.ta-online-dot.is-online { background: #2f9e61; box-shadow: 0 0 0 3px #e5f6ed; }
.ta-local-workspace-form { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.ta-local-workspace-form h5,
.ta-item-list h5 { grid-column: 1 / -1; margin: 0; font-size: 13px; color: #303133; }
.ta-path-row { grid-column: 1 / -1; gap: 8px; }
.ta-local-workspace-form > .el-button { justify-self: start; }
.ta-picker-toolbar { gap: 8px; }
.ta-picker-list { min-height: 280px; max-height: 420px; margin-top: 12px; overflow: auto; border: 1px solid #ebeef5; border-radius: 8px; }
.ta-picker-list button { width: 100%; display: grid; grid-template-columns: 20px 1fr auto; align-items: center; gap: 8px; padding: 9px 12px; border: 0; border-bottom: 1px solid #f2f3f5; background: #fff; color: #303133; text-align: left; cursor: pointer; }
.ta-picker-list button:hover:not(:disabled) { background: #f5f7fa; }
.ta-picker-list button:disabled { color: #b6b8bc; cursor: not-allowed; }
.ta-picker-list small { color: #a66; }
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
