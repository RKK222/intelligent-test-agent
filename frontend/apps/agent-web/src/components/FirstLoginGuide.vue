<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { ElTour, ElTourStep } from "element-plus";

const GUIDE_VERSION = "v1";

const props = defineProps<{
  userId?: string | null;
}>();

const emit = defineEmits<{
  (event: "prepare"): void;
  (event: "finish"): void;
}>();

const open = ref(false);
const current = ref(0);
let scheduledUserId: string | null = null;

const previousButton = { children: "上一步" };
const nextButton = { children: "下一步" };
const finishButton = { children: "打开用户手册" };

function storageKey(userId: string) {
  return `test-agent.onboarding.${GUIDE_VERSION}:${userId}`;
}

function hasSeen(userId: string) {
  try {
    return window.localStorage.getItem(storageKey(userId)) === "seen";
  } catch {
    return false;
  }
}

function markSeen() {
  const userId = props.userId?.trim();
  if (!userId) return;
  try {
    window.localStorage.setItem(storageKey(userId), "seen");
  } catch {
    // 浏览器禁用存储时仍允许本次引导正常完成，下次登录可能再次展示。
  }
}

async function show() {
  emit("prepare");
  await nextTick();
  current.value = 0;
  window.requestAnimationFrame(() => {
    open.value = true;
  });
}

function close() {
  markSeen();
  open.value = false;
}

function finish() {
  markSeen();
  open.value = false;
  emit("finish");
}

function restart() {
  void show();
}

watch(
  () => props.userId?.trim() || null,
  (userId) => {
    if (!userId || userId === scheduledUserId || hasSeen(userId)) return;
    scheduledUserId = userId;
    void show();
  },
  { immediate: true }
);

defineExpose({ restart });
</script>

<template>
  <ElTour
    v-model="open"
    v-model:current="current"
    class="ta-onboarding-tour"
    :mask="{ color: 'rgba(13, 24, 38, 0.68)' }"
    :gap="{ offset: 8, radius: 10 }"
    :content-style="{ width: '320px' }"
    :target-area-clickable="false"
    :scroll-into-view-options="{ block: 'center', behavior: 'smooth' }"
    @close="close"
    @finish="finish"
  >
    <ElTourStep
      target='[data-onboarding="application"]'
      placement="bottom-end"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>01</span><strong>先选择应用</strong></div></template>
      <p>先确认账号角色。超级管理员可新建应用，应用管理员可配置成员、版本库和工作空间；普通用户可加入已授权应用。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="workspace"]'
      placement="right-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>02</span><strong>在工作区中操作文件</strong></div></template>
      <p>首次使用先在“设置 → 个人设置”配置 SSH，再由管理员准备应用工作空间，最后选中自己的个人工作区并初始化 TestAgent 服务。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="chat"]'
      placement="left-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>03</span><strong>向智能体描述任务</strong></div></template>
      <p>在对话区发送测试、分析或编码任务，也可以带上文件上下文。运行进度和结果都会留在当前会话。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="manual"]'
      placement="bottom-end"
      :prev-button-props="previousButton"
      :next-button-props="finishButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>04</span><strong>有疑问就打开用户手册</strong></div></template>
      <p>手册提供操作说明和全文检索，还能直接问小宠物。即使没有建立主对话，小宠物也会依据手册回答。</p>
    </ElTourStep>
  </ElTour>
</template>

<style scoped>
.ta-onboarding-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #182534;
}

.ta-onboarding-heading span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 24px;
  border-radius: 7px;
  background: #315b75;
  color: #fff;
  font: 700 11px/1 ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: 0.06em;
}

.ta-onboarding-heading strong {
  font-size: 15px;
}

p {
  margin: 2px 0 0;
  color: #526171;
  font-size: 13px;
  line-height: 1.65;
}

@media (prefers-reduced-motion: reduce) {
  :global(.el-tour__content) {
    transition: none !important;
  }
}
</style>
