<script setup lang="ts">
import { ref, watch } from "vue";

const form = ref({
  displayName: "MIMO 测试员",
  email: "tester@mimo.local",
  notifications: {
    runFinished: true,
    permissionAsked: true,
    questionAsked: true
  }
});

watch(
  form,
  (value) => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem("ta.settings.account", JSON.stringify(value));
    }
  },
  { deep: true }
);
</script>

<template>
  <div class="ta-account">
    <section class="ta-account-section">
      <h4 class="ta-account-section-title">基础信息</h4>
      <el-form label-position="top" class="ta-settings-form">
        <el-form-item label="显示名称">
          <el-input v-model="form.displayName" placeholder="输入显示名称" clearable />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="name@example.com" clearable />
        </el-form-item>
      </el-form>
    </section>

    <section class="ta-account-section">
      <h4 class="ta-account-section-title">通知偏好</h4>
      <ul class="ta-account-list">
        <li class="ta-account-list-item">
          <div>
            <div class="ta-account-list-title">Run 完成</div>
            <div class="ta-account-list-desc">智能体任务成功或失败时通知我</div>
          </div>
          <el-switch v-model="form.notifications.runFinished" />
        </li>
        <li class="ta-account-list-item">
          <div>
            <div class="ta-account-list-title">权限请求</div>
            <div class="ta-account-list-desc">智能体申请执行受限操作时通知我</div>
          </div>
          <el-switch v-model="form.notifications.permissionAsked" />
        </li>
        <li class="ta-account-list-item">
          <div>
            <div class="ta-account-list-title">智能体提问</div>
            <div class="ta-account-list-desc">智能体向你发起提问时通知我</div>
          </div>
          <el-switch v-model="form.notifications.questionAsked" />
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.ta-account {
  display: flex;
  flex-direction: column;
  gap: 28px;
  max-width: 640px;
}

.ta-account-section-title {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: #18181b;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}

.ta-account-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.ta-account-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid #ebeef5;
}

.ta-account-list-item:last-child {
  border-bottom: none;
}

.ta-account-list-title {
  font-size: 13px;
  font-weight: 500;
  color: #18181b;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}

.ta-account-list-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.ta-settings-form {
  max-width: 520px;
}
</style>
