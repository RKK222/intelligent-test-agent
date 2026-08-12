<script setup lang="ts">
import { AlertTriangle, GitBranch, UsersRound } from "lucide-vue-next";

defineProps<{
  open: boolean;
  opening?: boolean;
}>();

const emit = defineEmits<{
  decline: [];
  start: [];
}>();
</script>

<template>
  <el-dialog
    :model-value="open"
    width="min(520px, calc(100vw - 32px))"
    class="experience-workspace-dialog"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :destroy-on-close="true"
    append-to-body
  >
    <template #header>
      <div class="experience-dialog-heading">
        <span class="experience-dialog-kicker">平台体验</span>
        <h2>现在体验平台功能吗？</h2>
        <p>无需所属应用，直接打开体验工作区浏览文件、编辑内容并与 TestAgent 对话。</p>
      </div>
    </template>

    <div class="experience-shared-strip" role="note" aria-label="体验工作区共享说明">
      <UsersRound class="experience-shared-icon" :stroke-width="1.7" aria-hidden="true" />
      <div>
        <strong>这是同一台服务器上的多人共享目录</strong>
        <span>其他体验用户可能同时修改相同文件，本地 Git 变更仅供查看。</span>
      </div>
    </div>

    <div class="experience-dialog-facts">
      <div>
        <GitBranch :stroke-width="1.6" aria-hidden="true" />
        <span>平台不会创建远端仓库，也不会自动重置或清理目录。</span>
      </div>
      <div class="is-warning">
        <AlertTriangle :stroke-width="1.7" aria-hidden="true" />
        <strong>请勿存放密码、密钥、客户数据等敏感信息。</strong>
      </div>
    </div>

    <template #footer>
      <div class="experience-dialog-actions">
        <el-button :disabled="opening" @click="emit('decline')">暂不体验</el-button>
        <el-button type="primary" :loading="opening" @click="emit('start')">开始体验</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.experience-dialog-heading {
  padding-right: 8px;
}

.experience-dialog-kicker {
  display: inline-flex;
  margin-bottom: 8px;
  padding: 3px 9px;
  border-radius: 999px;
  background: #eef4ff;
  color: #315ea8;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.04em;
}

.experience-dialog-heading h2 {
  margin: 0;
  color: #202733;
  font-size: 21px;
  font-weight: 680;
  line-height: 1.35;
}

.experience-dialog-heading p {
  margin: 8px 0 0;
  color: #687182;
  font-size: 14px;
  line-height: 1.65;
}

.experience-shared-strip {
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 12px;
  align-items: start;
  padding: 14px 15px;
  border: 1px solid #bfd0ec;
  border-radius: 10px;
  background: linear-gradient(135deg, #f4f8ff 0%, #f8fbff 100%);
}

.experience-shared-icon {
  width: 25px;
  height: 25px;
  margin-top: 1px;
  color: #315ea8;
}

.experience-shared-strip strong,
.experience-shared-strip span {
  display: block;
}

.experience-shared-strip strong {
  color: #253d64;
  font-size: 14px;
}

.experience-shared-strip span {
  margin-top: 4px;
  color: #5f6f87;
  font-size: 13px;
  line-height: 1.55;
}

.experience-dialog-facts {
  display: grid;
  gap: 9px;
  margin-top: 14px;
}

.experience-dialog-facts > div {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 8px;
  align-items: start;
  color: #636c7a;
  font-size: 13px;
  line-height: 1.55;
}

.experience-dialog-facts svg {
  width: 16px;
  height: 16px;
  margin-top: 2px;
}

.experience-dialog-facts .is-warning {
  color: #8a5a13;
}

.experience-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
