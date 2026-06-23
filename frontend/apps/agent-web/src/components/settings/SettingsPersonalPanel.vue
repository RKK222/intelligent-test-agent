<script setup lang="ts">
import { inject, onMounted, ref } from "vue";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { CurrentUser, SshKeyMetadata } from "@test-agent/shared-types";
import { Key, Delete } from "@element-plus/icons-vue";

// SettingsPanel 统一向所有面板传入 currentUser；个人设置面板目前不依赖该字段，
// 但保留 prop 以避免 Vue 透传告警，类型与 SettingsAppWorkspacePanel 保持一致。
defineProps<{
  currentUser: CurrentUser | null;
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
