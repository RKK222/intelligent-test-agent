<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch } from "vue";
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
const props = defineProps<{
  currentUser: CurrentUser | null;
  pageActive: boolean;
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
const plaintextDialogOpen = ref(false);
const plaintextClientKey = ref("");
const localClients = ref<LocalClientInstance[]>([]);
const localWorkspaces = ref<Workspace[]>([]);
const localWorkspaceClientId = ref("");
const localWorkspaceName = ref("");
const localWorkspaceRoot = ref("");
const pickerOpen = ref(false);
const pickerLoading = ref(false);
const pickerError = ref("");
const pickerPath = ref("/");
const pickerSelectedPath = ref("");
const pickerEntries = ref<LocalClientDirectoryEntry[]>([]);
let localClientRefreshTimer: ReturnType<typeof setInterval> | undefined;
let localClientRequestEpoch = 0;

type LocalClientRequestContext = {
  ownerUserId: string | null;
  pageActive: boolean;
  requestEpoch: number;
};

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
  if (!props.pageActive) return;
  const requestContext = captureLocalClientRequestContext();
  if (!silent) localClientLoading.value = true;
  localClientError.value = "";
  try {
    const [credentialView, clients, workspaces] = await Promise.all([
      api.getMyLocalClientCredential(),
      api.listMyLocalClientInstances(),
      api.listWorkspaces(1, 100)
    ]);
    if (!isLocalClientRequestCurrent(requestContext)) return;
    credential.value = credentialView;
    localClients.value = clients;
    localWorkspaces.value = workspaces.items.filter((workspace) => workspace.runtimeKind === "LOCAL_CLIENT");
    if (!onlineLocalClients.value.some((client) => client.clientInstanceId === localWorkspaceClientId.value)) {
      localWorkspaceClientId.value = onlineLocalClients.value[0]?.clientInstanceId ?? "";
    }
  } catch (error) {
    if (isLocalClientRequestCurrent(requestContext)) {
      localClientError.value = error instanceof Error ? error.message : "读取本地客户端状态失败";
    }
  } finally {
    if (!silent && isLocalClientRequestCurrent(requestContext)) localClientLoading.value = false;
  }
}

async function createCredential() {
  const requestContext = captureLocalClientRequestContext();
  await runLocalClientAction(async () => {
    if (!isLocalClientRequestCurrent(requestContext)) return;
    const created = await api.createMyLocalClientCredential();
    if (!isLocalClientRequestCurrent(requestContext)) return;
    credential.value = created;
    if (created.revealAvailable === true) {
      await consumeCredentialReveal(requestContext);
      return;
    }
    ElMessage.success("Client key 已创建");
  });
}

/**
 * 消费当前凭据版本唯一一次明文，并仅放入本组件的一次性对话框。
 * 请求期间页面失活或用户切换时丢弃迟到结果，不能把前一用户的 Key 带入新页面。
 */
async function consumeCredentialReveal(requestContext = captureLocalClientRequestContext()) {
  if (!isLocalClientRequestCurrent(requestContext)) return;
  try {
    const plaintext = (await api.copyMyLocalClientCredential()).clientKey;
    if (!plaintext) throw new Error("Client key 显示失败");
    if (!isLocalClientRequestCurrent(requestContext)) return;
    plaintextClientKey.value = plaintext;
    plaintextDialogOpen.value = true;
    if (credential.value) credential.value = { ...credential.value, revealAvailable: false };
    await loadLocalClientState(true);
  } catch (error) {
    if (isLocalClientRequestCurrent(requestContext)) await loadLocalClientState(true);
    throw error;
  }
}

async function showCredential() {
  const requestContext = captureLocalClientRequestContext();
  await runLocalClientAction(async () => {
    if (!isLocalClientRequestCurrent(requestContext)) return;
    if (credential.value?.revealAvailable !== true) return;
    await consumeCredentialReveal(requestContext);
  });
}

/** 显式复制只读取当前一次性对话框内存，不重新请求服务端。 */
async function copyPlaintextCredential() {
  const plaintext = plaintextClientKey.value;
  if (!plaintext) return;
  try {
    if (!await copyTextToClipboard(plaintext)) throw new Error("copy rejected");
    ElMessage.success("Client key 已复制，请立即粘贴到客户端配置");
  } catch {
    localClientError.value = "复制 Client key 失败，请手动选择并复制";
  }
}

function clearCredentialPlaintext() {
  plaintextDialogOpen.value = false;
  plaintextClientKey.value = "";
}

/**
 * 密钥请求必须绑定发起时的身份、页面活跃态和代际；任一边界变化后，
 * 迟到响应只能丢弃，不能继续消费或重新写入明文。
 */
function captureLocalClientRequestContext(): LocalClientRequestContext {
  return {
    ownerUserId: props.currentUser?.userId ?? null,
    pageActive: props.pageActive,
    requestEpoch: localClientRequestEpoch
  };
}

function isLocalClientRequestCurrent(requestContext: LocalClientRequestContext) {
  return requestContext.pageActive
    && props.pageActive
    && requestContext.requestEpoch === localClientRequestEpoch
    && requestContext.ownerUserId === (props.currentUser?.userId ?? null);
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
  const requestContext = captureLocalClientRequestContext();
  await runLocalClientAction(async () => {
    if (!isLocalClientRequestCurrent(requestContext)) return;
    const rotated = await api.rotateMyLocalClientCredential();
    if (!isLocalClientRequestCurrent(requestContext)) return;
    credential.value = rotated;
    if (rotated.revealAvailable === true) {
      await consumeCredentialReveal(requestContext);
      return;
    }
    await loadLocalClientState(true);
    ElMessage.success("Client key 已轮换");
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

async function openWebDirectoryPicker() {
  if (!selectedLocalClient.value) return;
  pickerOpen.value = true;
  pickerSelectedPath.value = "";
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
    pickerSelectedPath.value = "";
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
  applyLocalWorkspaceRoot(pickerSelectedPath.value || pickerPath.value);
  pickerSelectedPath.value = "";
  pickerOpen.value = false;
}

function selectPickerEntry(entry: LocalClientDirectoryEntry) {
  if (!entry.directory || entry.symbolicLink || !entry.readable) return;
  pickerSelectedPath.value = entry.absolutePath;
}

function applyLocalWorkspaceRoot(path: string) {
  localWorkspaceRoot.value = path;
  if (!localWorkspaceName.value.trim()) {
    localWorkspaceName.value = path.replace(/\\/g, "/").split("/").filter(Boolean).pop() ?? "本地工作区";
  }
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

watch(
  [() => props.pageActive, () => props.currentUser?.userId ?? null],
  ([active]) => {
    localClientRequestEpoch += 1;
    stopLocalClientPolling();
    clearCredentialPlaintext();
    if (!active) return;
    void loadSshKeys();
    void loadLocalClientState();
    localClientRefreshTimer = setInterval(() => void loadLocalClientState(true), 5_000);
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  localClientRequestEpoch += 1;
  stopLocalClientPolling();
  clearCredentialPlaintext();
});

function stopLocalClientPolling() {
  if (!localClientRefreshTimer) return;
  clearInterval(localClientRefreshTimer);
  localClientRefreshTimer = undefined;
}

function localClientDirectionLabel(direction?: string | null) {
  if (direction === "ROLLBACK") return "回退";
  if (direction === "UPDATE") return "更新";
  if (direction === "SAME") return "已一致";
  return "策略未知";
}

function formatLocalClientTime(value?: string | null) {
  if (!value) return "时间未知";
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false
  }).format(new Date(timestamp));
}
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
            <el-button v-if="credential.revealAvailable === true" size="small" type="primary" :disabled="localClientLoading" @click="showCredential">显示 Client key</el-button>
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
              <div v-if="client.selfUpdateSupported === true" class="ta-client-update-state">
                <div class="ta-client-update-heading">
                  <span class="ta-update-support is-supported">支持静默自更新</span>
                  <span :class="['ta-update-direction', `is-${String(client.updateDirection || 'unknown').toLowerCase()}`]">
                    {{ localClientDirectionLabel(client.updateDirection) }}
                  </span>
                </div>
                <div v-if="client.targetClientVersion" class="ta-version-transition">
                  {{ client.clientVersion }} → {{ client.targetClientVersion }}
                </div>
                <div v-else class="ta-item-subtitle">平台尚未设置目标版本</div>
                <div v-if="client.lastUpdateStatus" class="ta-item-subtitle">
                  最近结果 {{ client.lastUpdateStatus }} · {{ formatLocalClientTime(client.lastUpdateAt) }}
                </div>
              </div>
              <div v-else class="ta-client-update-state is-legacy">
                <span class="ta-update-support">不支持自更新，请安装新版 DEB</span>
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
        <h5>网页兜底注册本地工作区</h5>
        <p class="ta-item-subtitle">默认请在客户端托盘点击“选择并注册工作区…”，无需在网页填写。</p>
        <el-select v-model="localWorkspaceClientId" placeholder="选择在线客户端" style="width: 100%">
          <el-option v-for="client in onlineLocalClients" :key="client.clientInstanceId" :label="client.clientName" :value="client.clientInstanceId" />
        </el-select>
        <el-input v-model="localWorkspaceName" maxlength="120" placeholder="工作区名称" />
        <div class="ta-path-row">
          <el-input v-model="localWorkspaceRoot" placeholder="绝对路径，例如 /Users/me/project" />
          <el-button :disabled="!selectedLocalClient" @click="openWebDirectoryPicker">网页浏览</el-button>
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

    <el-dialog
      v-model="plaintextDialogOpen"
      title="一次性 Client key"
      width="560px"
      append-to-body
      :close-on-click-modal="false"
      :destroy-on-close="true"
      @close="clearCredentialPlaintext"
      @closed="clearCredentialPlaintext"
    >
      <el-alert
        title="关闭后不能再次查看；如遗失，请轮换生成新 Key。"
        type="warning"
        :closable="false"
        show-icon
      />
      <code class="ta-plaintext-client-key">{{ plaintextClientKey }}</code>
      <template #footer>
        <el-button :disabled="!plaintextClientKey" @click="copyPlaintextCredential">复制到剪贴板</el-button>
        <el-button type="primary" @click="clearCredentialPlaintext">关闭</el-button>
      </template>
    </el-dialog>

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
          :class="{ 'is-selected': pickerSelectedPath === entry.absolutePath }"
          :aria-pressed="pickerSelectedPath === entry.absolutePath"
          :disabled="!entry.directory || entry.symbolicLink || !entry.readable"
          @dblclick="loadPickerDirectory(entry.absolutePath)"
          @click="selectPickerEntry(entry)"
        >
          <el-icon><Folder /></el-icon>
          <span>{{ entry.name }}</span>
          <small v-if="entry.symbolicLink">符号链接不可选</small>
        </button>
      </div>
      <div class="ta-picker-selection">
        {{ pickerSelectedPath ? `已选择：${pickerSelectedPath}` : `当前目录：${pickerPath}` }}
      </div>
      <template #footer>
        <el-button @click="pickerOpen = false">取消</el-button>
        <el-button type="primary" :disabled="pickerLoading" @click="selectPickerDirectory">
          {{ pickerSelectedPath ? '使用所选目录' : '使用当前目录' }}
        </el-button>
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
.ta-plaintext-client-key { display: block; margin-top: 14px; padding: 12px; overflow-wrap: anywhere; border: 1px solid #e4e7ed; border-radius: 6px; background: #f7f8fa; color: #303133; user-select: all; }
.ta-row-actions { flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.ta-row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.ta-client-list { display: flex; flex-direction: column; gap: 8px; }
.ta-client-main { align-items: flex-start; gap: 9px; min-width: 0; }
.ta-online-dot { width: 8px; height: 8px; margin-top: 5px; border-radius: 50%; background: #b4b4b4; box-shadow: 0 0 0 3px #f2f2f2; }
.ta-online-dot.is-online { background: #2f9e61; box-shadow: 0 0 0 3px #e5f6ed; }
.ta-client-update-state { margin-top: 7px; padding: 7px 8px; border: 1px solid #e4e7ed; border-radius: 6px; background: #fafbfc; }
.ta-client-update-state.is-legacy { border-color: #ead8b5; background: #fffaf0; color: #946015; }
.ta-client-update-heading { display: flex; align-items: center; gap: 7px; }
.ta-update-support,
.ta-update-direction { display: inline-flex; align-items: center; border-radius: 999px; padding: 2px 7px; font-size: 10px; font-weight: 600; }
.ta-update-support { background: #f4eee3; color: #946015; }
.ta-update-support.is-supported { background: #e5f6ed; color: #2f7a51; }
.ta-update-direction { background: #f2f3f5; color: #606266; }
.ta-update-direction.is-update { background: #e5f6ed; color: #2f7a51; }
.ta-update-direction.is-rollback { background: #fff1dc; color: #9a5d16; }
.ta-version-transition { margin-top: 5px; color: #991b1b; font: 11px "Geist Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
.ta-local-workspace-form { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.ta-local-workspace-form h5,
.ta-item-list h5 { grid-column: 1 / -1; margin: 0; font-size: 13px; color: #303133; }
.ta-path-row { grid-column: 1 / -1; gap: 8px; }
.ta-local-workspace-form > .el-button { justify-self: start; }
.ta-picker-toolbar { gap: 8px; }
.ta-picker-list { min-height: 280px; max-height: 420px; margin-top: 12px; overflow: auto; border: 1px solid #ebeef5; border-radius: 8px; }
.ta-picker-list button { width: 100%; display: grid; grid-template-columns: 20px 1fr auto; align-items: center; gap: 8px; padding: 9px 12px; border: 0; border-bottom: 1px solid #f2f3f5; background: #fff; color: #303133; text-align: left; cursor: pointer; }
.ta-picker-list button:hover:not(:disabled) { background: #f5f7fa; }
.ta-picker-list button.is-selected { background: #ecf5ff; color: #2563eb; box-shadow: inset 3px 0 #409eff; }
.ta-picker-list button:disabled { color: #b6b8bc; cursor: not-allowed; }
.ta-picker-list small { color: #a66; }
.ta-picker-selection { margin-top: 8px; color: #606266; font-size: 12px; overflow-wrap: anywhere; }
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
