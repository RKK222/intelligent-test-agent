<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { ElTour, ElTourStep } from "element-plus";

// v3 将引导锚定到应用、workspace/version、小地球和新建对话等真实操作按钮。
const GUIDE_VERSION = "v3";

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
      <template #header><div class="ta-onboarding-heading"><span>02</span><strong>应用在这里切换或加入</strong></div></template>
      <p>普通用户不能在工作区里新建应用；看不到“新建应用”是权限正常。请让管理员在“设置 → 应用管理”创建并把你加入，自己则点击“加入其他应用”选择已有应用。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="workspace-selector"]'
      placement="top-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>03</span><strong>一定要选中 workspace</strong></div></template>
      <p>顶部选中应用后还不够。点击左下角双向箭头，先悬停选择工作空间，再选择右侧版本；按钮显示“工作空间 / 版本”后，左侧文件树才会加载。只选应用时文件区保持空白是正常的。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="workspace-reference"]'
      placement="right-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>04</span><strong>用小地球引入子条目</strong></div></template>
      <p>工作区选中后，点击“工作空间”标题右侧的小地球打开外部需求页面；在页面中选择要引入的需求子条目并完成保存，返回后文件树会刷新。它不是刷新按钮，也不是新建应用入口。</p>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="new-conversation"]'
      placement="top-end"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>05</span><strong>第一条消息会建立对话</strong></div></template>
      <p>在右侧输入任务并点击发送，系统会自动创建主对话，不必先点“新建对话”。“新建对话”按钮用于主动清空当前草稿、开始另一条对话；需要引用子条目时先用小地球引入，再在输入框使用 # 选择。</p>
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
