<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { ElTour, ElTourStep } from "element-plus";

// v2 修正 SSH 步骤的目标元素，并扩展为普通用户可见的工作台主入口说明。
const GUIDE_VERSION = "v2";

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
    :content-style="{ width: 'min(320px, calc(100vw - 32px))' }"
    :target-area-clickable="false"
    :scroll-into-view-options="{ block: 'center', behavior: 'smooth' }"
    @close="close"
    @finish="finish"
  >
    <!-- 工作面板几乎占满视口，若把气泡锚定在面板外侧会被浏览器边缘裁切；首步改为视口居中概览。 -->
    <ElTourStep :show-arrow="false" :next-button-props="nextButton">
      <template #header><div class="ta-onboarding-heading"><span>01</span><strong>这是你的工作面板</strong></div></template>
      <p>左侧管理应用版本、个人工作区和文件，中间查看或编辑内容，右侧与智能体对话。一次任务需要先选好应用和工作区。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="application"]'
      placement="bottom-end"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>02</span><strong>选择要处理的应用</strong></div></template>
      <p>这里切换你已加入的应用；目标应用不在列表时，打开菜单并选择“加入其他应用”。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="editor-button"]'
      placement="right-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>03</span><strong>返回编辑工作台</strong></div></template>
      <p>点击代码图标可从其他页面回到文件、编辑器和对话组成的主工作台。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="workspace"]'
      placement="right-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>04</span><strong>选择工作区和文件</strong></div></template>
      <p>先选择应用版本和自己的个人工作区，再在文件树中浏览、搜索或打开文件。工作区决定智能体能够读取和修改的代码范围。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="chat"]'
      placement="left-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>05</span><strong>向智能体描述任务</strong></div></template>
      <p>在对话区发送测试、分析或编码任务，也可以带上文件上下文。运行进度和结果都会留在当前会话。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="pet"]'
      placement="right-end"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>06</span><strong>小宠物也是快捷助手</strong></div></template>
      <p>点击宠物按钮可查看 TestAgent 服务状态、初始化专属进程，或随时提问；没有主对话时会依据用户手册回答。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="settings"]'
      placement="right-end"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>07</span><strong>先在设置中配置 SSH</strong></div></template>
      <p>点击齿轮进入“个人设置”，添加你自己的 SSH 私钥。完成后才能正常访问有权限的 Git 仓库、分支和工作区文件。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="manual"]'
      placement="bottom-end"
      :prev-button-props="previousButton"
      :next-button-props="finishButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>08</span><strong>有疑问就打开用户手册</strong></div></template>
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
