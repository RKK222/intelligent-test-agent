<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { ElTour, ElTourStep } from "element-plus";

// v6 将设置流程拆成 SSH、应用与版本库、应用工作区三个独立步骤。
const GUIDE_VERSION = "v6";

const props = withDefaults(defineProps<{
  userId?: string | null;
  appAdmin?: boolean;
}>(), {
  appAdmin: false
});

const emit = defineEmits<{
  (event: "prepare"): void;
  (event: "dismiss"): void;
  (event: "finish"): void;
  (event: "settings-step", open: boolean): void;
}>();

const open = ref(false);
const current = ref(0);
const settingsPersonalTarget = ref<string | HTMLElement>('[data-onboarding="settings-personal"]');
const settingsRepositoryTarget = ref<string | HTMLElement>('[data-onboarding="settings-repository"]');
const settingsWorkspaceTarget = ref<string | HTMLElement>('[data-onboarding="settings-app-workspace"]');
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
  emit("settings-step", false);
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
  emit("settings-step", false);
  emit("dismiss");
}

function finish() {
  markSeen();
  open.value = false;
  emit("settings-step", false);
  emit("finish");
}

function restart() {
  void show();
}

async function refreshSettingsTargets() {
  await nextTick();
  await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
  // 设置弹窗是异步挂载的；拿到真实 DOM 后再替换 selector，避免 Tour 在左上角生成无目标气泡。
  settingsPersonalTarget.value = document.querySelector('[data-onboarding="settings-personal"]') ?? '[data-onboarding="settings-personal"]';
  settingsRepositoryTarget.value = document.querySelector('[data-onboarding="settings-repository"]') ?? '[data-onboarding="settings-repository"]';
  settingsWorkspaceTarget.value = document.querySelector('[data-onboarding="settings-app-workspace"]') ?? '[data-onboarding="settings-app-workspace"]';
}

watch(current, async (step) => {
  // 设置相关步骤需要让用户看到设置弹窗中的真实导航；返回上一步或进入手册时关闭它。
  const lastSettingsStep = props.appAdmin ? 8 : 6;
  const settingsStep = step >= 6 && step <= lastSettingsStep;
  emit("settings-step", settingsStep);
  if (settingsStep) await refreshSettingsTargets();
});

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
    :z-index="3000"
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
      :target="settingsPersonalTarget"
      placement="right"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>07</span><strong>SSH 配置</strong></div></template>
      <div class="ta-onboarding-settings-guide">
        <p>面板已自动打开，点击“个人设置”。填写 SSH Key 名称，粘贴私钥内容，点击“添加 SSH key”；不再使用时点击对应 Key 的“删除”。</p>
      </div>
    </ElTourStep>
    <ElTourStep
      v-if="appAdmin"
      :target="settingsRepositoryTarget"
      placement="right"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>08</span><strong>应用与版本库配置</strong></div></template>
      <div class="ta-onboarding-settings-guide">
        <p>点击“版本库管理”→“新增”，填写 Git 地址、名称、部署模式和版本库类型。然后点击“应用管理”，先选目标应用，在“应用人员管理”添加成员，再到“应用与版本库关联”点击“关联”。</p>
      </div>
    </ElTourStep>
    <ElTourStep
      v-if="appAdmin"
      :target="settingsWorkspaceTarget"
      placement="right"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>09</span><strong>应用工作区配置</strong></div></template>
      <div class="ta-onboarding-settings-guide">
        <p>点击“应用管理”并选应用，进入“工作空间管理”，选择测试工作库、分支、工作空间别名和目录后点击“保存”。保存成功后，普通用户回到工作台左下角选择 workspace/version。</p>
      </div>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="manual"]'
      placement="bottom-end"
      :prev-button-props="previousButton"
      :next-button-props="finishButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>{{ appAdmin ? '10' : '08' }}</span><strong>有疑问就打开用户手册</strong></div></template>
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

.ta-onboarding-settings-guide ol {
  margin: 4px 0 0;
  padding-left: 18px;
  color: #526171;
  font-size: 12px;
  line-height: 1.5;
}

.ta-onboarding-settings-guide {
  max-height: min(42vh, 260px);
  overflow-y: auto;
  padding-right: 2px;
}

.ta-onboarding-settings-guide li + li {
  margin-top: 3px;
}

@media (prefers-reduced-motion: reduce) {
  :global(.el-tour__content) {
    transition: none !important;
  }
}
</style>
