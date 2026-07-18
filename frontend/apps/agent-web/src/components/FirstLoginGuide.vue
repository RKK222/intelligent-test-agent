<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { ElTour, ElTourStep } from "element-plus";

// v7 将应用管理员设置流程拆到真实的版本库入口和应用管理页签。
const GUIDE_VERSION = "v7";

type SettingsGuideTarget = "personal" | "repository" | "members" | "repositories" | "workspaces";

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
  (event: "settings-step", open: boolean, target?: SettingsGuideTarget): void;
}>();

const open = ref(false);
const current = ref(0);
const settingsPersonalTarget = ref<string | HTMLElement>('[data-onboarding="settings-personal"]');
const settingsRepositoryTarget = ref<string | HTMLElement>('[data-onboarding="settings-repository"]');
const settingsMembersTarget = ref<string | HTMLElement>('[data-onboarding="settings-app-members"]');
const settingsRepositoriesTarget = ref<string | HTMLElement>('[data-onboarding="settings-app-repositories"]');
const settingsWorkspacesTarget = ref<string | HTMLElement>('[data-onboarding="settings-app-workspaces"]');
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

function settingsGuideTargetFor(step: number): SettingsGuideTarget | undefined {
  if (step === 6) return "personal";
  if (!props.appAdmin) return undefined;
  if (step === 7) return "repository";
  if (step === 8) return "members";
  if (step === 9) return "repositories";
  if (step === 10) return "workspaces";
  return undefined;
}

function settingsTargetSelector(target: SettingsGuideTarget) {
  if (target === "personal") return '[data-onboarding="settings-personal"]';
  if (target === "repository") return '[data-onboarding="settings-repository"]';
  if (target === "members") return '[data-onboarding="settings-app-members"]';
  if (target === "repositories") return '[data-onboarding="settings-app-repositories"]';
  return '[data-onboarding="settings-app-workspaces"]';
}

function setSettingsTarget(target: SettingsGuideTarget, element: Element | null) {
  const selector = settingsTargetSelector(target);
  const value: string | HTMLElement = element instanceof HTMLElement ? element : selector;
  if (target === "personal") settingsPersonalTarget.value = value;
  else if (target === "repository") settingsRepositoryTarget.value = value;
  else if (target === "members") settingsMembersTarget.value = value;
  else if (target === "repositories") settingsRepositoriesTarget.value = value;
  else settingsWorkspacesTarget.value = value;
}

async function refreshSettingsTarget(target: SettingsGuideTarget) {
  const selector = settingsTargetSelector(target);
  // 设置弹窗和应用管理数据是异步挂载的；轮询真实 Tab，避免 Tour 在左上角生成无目标气泡。
  for (let attempt = 0; attempt < 60; attempt += 1) {
    await nextTick();
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()));
    const element = document.querySelector(selector);
    if (element) {
      setSettingsTarget(target, element);
      return;
    }
  }
  setSettingsTarget(target, null);
}

watch(current, async (step) => {
  // 设置相关步骤需要切换到对应真实菜单/页签；返回上一步或进入手册时关闭它。
  const target = settingsGuideTargetFor(step);
  if (target) emit("settings-step", true, target);
  else emit("settings-step", false);
  if (target !== undefined) await refreshSettingsTarget(target);
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
      <template #header><div class="ta-onboarding-heading"><span>08</span><strong>版本库管理</strong></div></template>
      <div class="ta-onboarding-settings-guide">
        <p>这里只登记 Git 版本库，先点击“新增”。</p>
        <ol>
          <li>选择“部署模式”：外部部署填写完整 Git URL；内部部署保留页面显示的 SSH 前缀，只填写主机、端口和仓库路径。</li>
          <li>填写“版本库名称”“版本库英文名称”，选择“版本库类型”。只有“测试工作库”能在后面的工作空间页签创建 workspace。</li>
          <li>点击“新增”。已有版本库可点“编辑”修改名称和英文名称，地址、模式和类型只读。</li>
        </ol>
      </div>
    </ElTourStep>
    <ElTourStep
      v-if="appAdmin"
      :target="settingsMembersTarget"
      placement="bottom-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>09</span><strong>应用人员管理</strong></div></template>
      <div class="ta-onboarding-settings-guide">
        <p>先确认顶部“应用选择”已选目标应用。</p>
        <ol>
          <li>在“添加成员”输入用户 ID、用户名或统一认证号。</li>
          <li>从候选下拉中选中用户，再点击“添加”；空输入不会查询。</li>
          <li>移除成员时点击“已有成员”右侧删除按钮，并在页面内确认。</li>
        </ol>
      </div>
    </ElTourStep>
    <ElTourStep
      v-if="appAdmin"
      :target="settingsRepositoriesTarget"
      placement="bottom-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>10</span><strong>应用与版本库关联</strong></div></template>
      <div class="ta-onboarding-settings-guide">
        <p>这是“应用管理”里的关联页签，不是左侧“版本库管理”入口。</p>
        <ol>
          <li>顶部“应用选择”先选目标应用。</li>
          <li>在“选择版本库”下拉框选择刚登记的版本库，点击“关联”。</li>
          <li>已关联版本库会显示在下方；误关联时点击“解除”并确认。列表为空时回到第 08 步。</li>
        </ol>
      </div>
    </ElTourStep>
    <ElTourStep
      v-if="appAdmin"
      :target="settingsWorkspacesTarget"
      placement="bottom-start"
      :prev-button-props="previousButton"
      :next-button-props="nextButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>11</span><strong>工作空间管理</strong></div></template>
      <div class="ta-onboarding-settings-guide">
        <ol>
          <li>“已关联版本库”只选择类型为“测试工作库”的版本库；下拉为空时先完成第 10 步。</li>
          <li>“分支”选择符合 <code>feature_testagent_yyyymmdd</code> 规则的分支，加载后才会出现目录树。</li>
          <li>“工作空间别名”默认是 <code>ai-test</code>，同一应用下不能重名。</li>
          <li>“目录树”只选择当前应用同名目录下的一级子目录；文件不能选，必要时可新增一级目录。</li>
          <li>点击“保存”，等待校验、保存配置、解析版本、下载代码、创建运行态工作区和完成全部成功。</li>
          <li>回工作台左下角，先选择 workspace，再选择版本；否则文件树仍为空白。</li>
        </ol>
      </div>
    </ElTourStep>
    <ElTourStep
      target='[data-onboarding="manual"]'
      placement="bottom-end"
      :prev-button-props="previousButton"
      :next-button-props="finishButton"
    >
      <template #header><div class="ta-onboarding-heading"><span>{{ appAdmin ? '12' : '08' }}</span><strong>有疑问就打开用户手册</strong></div></template>
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

.ta-onboarding-settings-guide h4 {
  margin: 0 0 3px;
  color: #334155;
  font-size: 12px;
  line-height: 1.45;
}

.ta-onboarding-settings-guide code {
  padding: 0 2px;
  border-radius: 3px;
  background: #f1f5f9;
  color: #3b5872;
  font-size: 11px;
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
