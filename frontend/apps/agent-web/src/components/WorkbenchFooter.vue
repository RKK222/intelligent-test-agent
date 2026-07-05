<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ArrowLeftRight, Plus, Save, ServerCog } from "lucide-vue-next";
import { ElDatePicker, ElDialog } from "element-plus";
import type { ApplicationWorkspaceTemplate, ApplicationWorkspaceVersion } from "@test-agent/shared-types";
import type { BackendApiClient } from "@test-agent/backend-api";

// 透传类型：直接复用后端 VO（ApplicationWorkspaceTemplate / ApplicationWorkspaceVersion），
// 父组件负责把 versions 懒加载后回填到 template.versions 上。
export type AppWorkspaceTemplate = ApplicationWorkspaceTemplate & {
  versions?: ApplicationWorkspaceVersion[];
  standard?: boolean;
};
export type AppWorkspaceVersion = ApplicationWorkspaceVersion;

const props = defineProps<{
  /** 写入路径（编辑器模式显示） */
  writePath?: string;
  /** 最近一次更新时间（秒或 ISO 字符串均可） */
  updatedAt?: string | number;
  /** 是否存在未保存改动 */
  dirty?: boolean;
  /** 是否只读 */
  readonly?: boolean;
  /** 是否正在保存 */
  saving?: boolean;
  /** 是否展示保存按钮（仅编辑器场景） */
  showSave?: boolean;
  /** 当前应用名（用于菜单首行提示与按钮文案） */
  appName?: string;
  /** 归属当前应用的工作空间模板列表；为空则不展示两级菜单 */
  templates?: AppWorkspaceTemplate[];
  /** 当前选中的版本 ID；用于标记菜单项高亮 */
  selectedVersionId?: string;
  /** 当前默认个人 worktree 分支；用于底部入口直接展示用户实际改动分支 */
  personalWorkspaceBranch?: string;
  /** 工作空间模板是否仍在加载 */
  loadingTemplates?: boolean;
  /** 工作空间版本是否仍在加载（按模板分组懒加载时使用） */
  loadingVersions?: boolean;
  /** 「+新增版本」是否正在提交中（父组件控制禁用 & 展示 loading） */
  creatingVersion?: boolean;
  /** 是否显示超级管理员服务器工作空间切换入口 */
  showServerWorkspaceSwitch?: boolean;
  /** 服务器工作空间切换入口是否禁用 */
  serverWorkspaceSwitchDisabled?: boolean;
}>();

const emit = defineEmits<{
  (e: "save"): void;
  // 选择某工作空间下的某个版本：父组件负责切换运行态 Workspace。
  (e: "select-version", payload: { template: AppWorkspaceTemplate; version: AppWorkspaceVersion }): void;
  // 要求父组件按需懒加载某模板下的版本列表
  (e: "load-versions", templateId: string): void;
  // 「+新增版本」弹窗确认后回调：父组件负责调用 createWorkspaceVersion。
  // version 字段为 yyyyMMdd 格式（日期选择器结果），非标准库同时传递 branch 分支名。
  (e: "create-version", payload: { template: AppWorkspaceTemplate; version: string; branch?: string }): void;
  // 超级管理员打开跨服务器工作空间选择器。
  (e: "open-server-workspace-picker"): void;
}>();

const updatedLabel = computed(() => {
  if (props.updatedAt === undefined || props.updatedAt === null || props.updatedAt === "") return "—";
  const value = typeof props.updatedAt === "number" ? props.updatedAt * 1000 : Date.parse(props.updatedAt);
  if (Number.isNaN(value)) return String(props.updatedAt);
  const diff = Date.now() - value;
  if (diff < 0) return new Date(value).toLocaleString("zh-CN", { hour12: false });
  const seconds = Math.floor(diff / 1000);
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(value).toLocaleDateString("zh-CN");
});

const templates = computed(() => props.templates ?? []);

// ===== 应用工作空间两级菜单的弹出状态 =====
// 模板列表尚未加载或为空时仍展示入口，用于直接暴露当前个人 worktree 分支；
// 点击后菜单会展示加载/空态，不影响用户识别当前实际改动分支。
const useCascadeMenu = computed(() =>
  templates.value.length > 0 ||
  Boolean(props.appName) ||
  Boolean(props.personalWorkspaceBranch) ||
  Boolean(props.loadingTemplates)
);

// ===== 两级菜单弹出状态 =====
// menuOpen: 一级菜单（工作空间列表）开关；hoveredTemplateId: 当前悬停的模板，控制二级菜单（版本）显隐。
// cascadeButtonRef: 触发按钮 DOM 引用；cascadeMenuPos / cascadeSubmenuPos: 一级与二级菜单的 fixed 定位坐标。
// 菜单用 <Teleport to="body"> + position:fixed 挂到 body 末尾，避开父级 dockview 面板 overflow:hidden 裁切。
const menuOpen = ref(false);
const hoveredTemplateId = ref<string | null>(null);
const hoveredTemplateEl = ref<HTMLElement | null>(null);
const cascadeButtonRef = ref<HTMLElement | null>(null);
const cascadeMenuPos = ref<{ top: number; left: number } | null>(null);
const cascadeSubmenuPos = ref<{ top: number; left: number; maxHeight: number } | null>(null);
let cascadePosRafId: number | null = null;
let cascadeSubmenuCloseTimer: ReturnType<typeof setTimeout> | null = null;

// ===== 「+新增版本」弹窗状态 =====
// createVersionTarget: 当前弹窗操作的模板；createVersionValue: el-date-picker 选中的 yyyyMMdd 字符串。
// createVersionOpen 控制 ElDialog 显隐；与两级菜单的 hover 状态解耦，避免鼠标移开后弹窗被父级 v-if 卸载。
const api = inject<BackendApiClient>("api")!;
const createVersionTarget = ref<AppWorkspaceTemplate | null>(null);
const createVersionValue = ref<string>("");
const createVersionOpen = ref(false);
const createVersionBranch = ref<string>("");
const createVersionBranches = ref<string[]>([]);
const createVersionLoadingBranches = ref(false);

function openCreateVersionDialog(template: AppWorkspaceTemplate) {
  createVersionTarget.value = template;
  createVersionValue.value = "";
  createVersionBranch.value = "";
  createVersionBranches.value = [];
  // 如果是非标准库，加载分支列表
  if (template.standard === false && template.repositoryId) {
    createVersionLoadingBranches.value = true;
    api.listRepositoryBranches(template.repositoryId).then((branches: string[]) => {
      createVersionBranches.value = branches;
      createVersionBranch.value = branches[0] ?? "";
    }).finally(() => {
      createVersionLoadingBranches.value = false;
    });
  }
  // 关闭两级菜单，避免弹窗被外层 click outside 监听立即关掉。
  closeMenu();
  // 下一帧再开 dialog：保证前一次 closeMenu() 触发的 v-if 卸载先完成，避免和 dialog 共存出现 stacking 问题。
  void nextTick(() => {
    createVersionOpen.value = true;
  });
}

function confirmCreateVersion() {
  const target = createVersionTarget.value;
  if (!target || !createVersionValue.value) return;
  // value-format 是 "YYYYMMDD"，直接使用日期字符串作为版本号（yyyyMMdd）。
  // 非标准库需要同时传递分支。
  const version = createVersionValue.value.replaceAll("-", "");
  const isNonStandard = target.standard === false;
  emit("create-version", {
    template: target,
    version,
    branch: isNonStandard ? createVersionBranch.value || undefined : undefined
  });
  createVersionOpen.value = false;
}

function cancelCreateVersion() {
  createVersionOpen.value = false;
}

// 计算一级菜单位置：固定在触发按钮正上方，6px 间隙。
// 必须在 menuOpen 置 true 前调用（因为 fixed 定位依赖 cascadeMenuPos 的存在）。
function updateCascadeMenuPos() {
  if (!cascadeButtonRef.value) return;
  const rect = cascadeButtonRef.value.getBoundingClientRect();
  cascadeMenuPos.value = {
    top: rect.top - 6,
    left: rect.left
  };
}

// 计算二级菜单位置：固定在当前 hover 的模板行右侧，让版本子菜单自然越过一级菜单的边界。
// 关键：用一级菜单面板的右边缘 + 4px 间隙作为子菜单的 left，而不是 li 的右边缘。
// 原因：li 在面板内（面板有 padding），li 的 right < 面板的 right，按 li 算会让子菜单起点仍落在面板里。
// 防遮挡策略（两段）：
// 1) top：让子菜单垂直对齐到 li 顶部，但不低于 margin（避免子菜单顶部超出视口顶部）。
//    当 li 靠近视口底部时，top 会被拉低到一个能放下"合理高度"的位置。
// 2) maxHeight：保证子菜单底部不超出 viewportHeight - margin。
//    max-height 会让内容超出时出现纵向滚动条；用户要求"不能超过底部"，max-height 满足这一点。
//    之前用 Math.max(120, ...) 给子菜单硬保底 120px 高度，结果在视口底部时反而让子菜单超出底部，
//    所以改成"严格按可用空间计算"，最多占满从 top 到 viewportHeight - margin 的距离。
function updateCascadeSubmenuPos() {
  if (!hoveredTemplateEl.value) {
    cascadeSubmenuPos.value = null;
    return;
  }
  const liRect = hoveredTemplateEl.value.getBoundingClientRect();
  const panelEl = document.querySelector(".ta-workbench-cascade-panel") as HTMLElement | null;
  const anchorRight = panelEl ? panelEl.getBoundingClientRect().right : liRect.right;
  const viewportHeight = window.innerHeight;
  const margin = 12;
  // naturalTop：先按 li 顶部对齐；若 li 底部太靠近视口底，把 top 拉低。
  // 用 liRect.bottom + 6 作为"再低就会出底"的边界：top = viewportHeight - margin - (估算高度)。
  // 估算高度 = liRect.bottom - naturalTop + 兜底（这里粗略取 min(available, 200) 让顶部尽量贴近 li）。
  const liTop = liRect.top - 6;
  const availableBelow = viewportHeight - margin - liTop;
  // 若可用空间不足 200px，把 top 抬到"让子菜单能放下 200px"的位置；否则保持与 li 顶部对齐。
  const preferredHeight = 200;
  const naturalTop =
    availableBelow >= preferredHeight
      ? Math.max(margin, liTop)
      : Math.max(margin, viewportHeight - margin - preferredHeight);
  const maxHeight = Math.max(80, viewportHeight - naturalTop - margin);
  cascadeSubmenuPos.value = { top: naturalTop, left: anchorRight + 4, maxHeight };
}

// 菜单打开期间：滚动 / 窗口尺寸变化时同步刷新一二级菜单位置。
let onCascadePosScrollOrResizeBound = () => {
  if (!menuOpen.value) return;
  if (cascadePosRafId !== null) cancelAnimationFrame(cascadePosRafId);
  cascadePosRafId = requestAnimationFrame(() => {
    updateCascadeMenuPos();
    updateCascadeSubmenuPos();
    cascadePosRafId = null;
  });
};

function clearCascadeSubmenuCloseTimer() {
  if (cascadeSubmenuCloseTimer !== null) {
    clearTimeout(cascadeSubmenuCloseTimer);
    cascadeSubmenuCloseTimer = null;
  }
}

// 二级菜单的"延迟关闭"：用户从一级菜单 li 移到二级菜单的中间地带（gap）会触发 mouseleave，
// 用 200ms 延迟给鼠标进入二级菜单留出窗口；进入二级菜单时清掉定时器（保持打开）。
function scheduleCascadeSubmenuClose() {
  clearCascadeSubmenuCloseTimer();
  const hovered = hoveredTemplateId.value;
  if (!hovered) return;
  cascadeSubmenuCloseTimer = setTimeout(() => {
    if (hoveredTemplateId.value === hovered) {
      hoveredTemplateId.value = null;
      hoveredTemplateEl.value = null;
      cascadeSubmenuPos.value = null;
    }
    cascadeSubmenuCloseTimer = null;
  }, 200);
}

const selectedTemplate = computed(() =>
  templates.value.find((template) => template.versions?.some((version) => version.versionId === props.selectedVersionId))
);
const selectedVersion = computed(() =>
  selectedTemplate.value?.versions?.find((version) => version.versionId === props.selectedVersionId)
);

// 当前 hover 的模板：二级菜单用它展示版本列表、提示文案和「+新增版本」入口。
// 从 hoveredTemplateId 反查 templates，避免每次 hover 重新构造对象。
const hoveredTemplate = computed<AppWorkspaceTemplate | null>(() => {
  if (!hoveredTemplateId.value) return null;
  return templates.value.find((template) => template.workspaceId === hoveredTemplateId.value) ?? null;
});

const triggerLabel = computed(() => {
  if (selectedVersion.value && selectedTemplate.value) {
    return `${selectedTemplate.value.workspaceName} / ${selectedVersion.value.version}`;
  }
  return "切换工作空间";
});

const triggerTitle = computed(() => {
  if (!props.personalWorkspaceBranch) {
    return triggerLabel.value;
  }
  return `${triggerLabel.value} / ${props.personalWorkspaceBranch}`;
});

function toggleMenu() {
  if (menuOpen.value) {
    closeMenu();
  } else {
    // 打开前先算 fixed 坐标；菜单用 Teleport 挂到 body，定位不能依赖父级 stacking context。
    updateCascadeMenuPos();
    menuOpen.value = true;
    window.addEventListener("scroll", onCascadePosScrollOrResizeBound, true);
    window.addEventListener("resize", onCascadePosScrollOrResizeBound);
    // 首次打开时若所有模板都未加载版本，触发一次懒加载
    const firstUnloaded = templates.value.find((template) => !template.versions);
    if (firstUnloaded) {
      emit("load-versions", firstUnloaded.workspaceId);
    }
  }
}

function closeMenu() {
  menuOpen.value = false;
  hoveredTemplateId.value = null;
  hoveredTemplateEl.value = null;
  cascadeSubmenuPos.value = null;
  clearCascadeSubmenuCloseTimer();
  window.removeEventListener("scroll", onCascadePosScrollOrResizeBound, true);
  window.removeEventListener("resize", onCascadePosScrollOrResizeBound);
  if (cascadePosRafId !== null) {
    cancelAnimationFrame(cascadePosRafId);
    cascadePosRafId = null;
  }
}

function onDocumentClick(event: MouseEvent) {
  // 菜单已 Teleport 到 body，无法再用 contains()；改用 closest() 找最近的菜单/按钮容器。
  if (menuOpen.value) {
    const target = event.target as Node | null;
    if (!target) return;
    const insidePanel = target instanceof Element ? target.closest(".ta-workbench-cascade-panel") : null;
    const insideSubmenu = target instanceof Element ? target.closest(".ta-workbench-cascade-submenu") : null;
    const insideButton = target instanceof Element ? target.closest(".ta-workbench-cascade") : null;
    if (!insidePanel && !insideSubmenu && !insideButton) {
      closeMenu();
    }
  }
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  if (menuOpen.value) {
    closeMenu();
  }
}

onMounted(() => {
  document.addEventListener("click", onDocumentClick);
  document.addEventListener("keydown", onDocumentKeydown);
});
onBeforeUnmount(() => {
  document.removeEventListener("click", onDocumentClick);
  document.removeEventListener("keydown", onDocumentKeydown);
  // 工作空间两级菜单的全局监听兜底
  clearCascadeSubmenuCloseTimer();
  window.removeEventListener("scroll", onCascadePosScrollOrResizeBound, true);
  window.removeEventListener("resize", onCascadePosScrollOrResizeBound);
  if (cascadePosRafId !== null) {
    cancelAnimationFrame(cascadePosRafId);
    cascadePosRafId = null;
  }
});

function onTemplateEnter(template: AppWorkspaceTemplate, event: MouseEvent) {
  hoveredTemplateId.value = template.workspaceId;
  // 记录当前 hover 行的 DOM 引用，用于子菜单 fixed 定位 + scroll/resize 期间重算。
  hoveredTemplateEl.value = event.currentTarget as HTMLElement;
  clearCascadeSubmenuCloseTimer();
  if (!template.versions) {
    // 版本未加载：通知父组件按需拉取；hover 状态保留，loading 完成后会渲染子菜单
    emit("load-versions", template.workspaceId);
  }
  // 同步计算子菜单位置（不需要 nextTick）：
  // 位置只依赖 li 的 getBoundingClientRect() 和 viewport 高度，DOM 渲染前就能确定。
  // max-height 由 CSS 变量动态设置，不依赖实际内容高度。
  updateCascadeSubmenuPos();
}

function onTemplateLeave() {
  // 200ms 延迟关闭，给用户从一级菜单滑到二级菜单留出时间；如在 200ms 内鼠标进入子菜单则取消关闭
  scheduleCascadeSubmenuClose();
}

function onCascadeSubmenuEnter() {
  // 进入子菜单：清掉 setTimeout 的关闭意图，hoveredTemplateId 保持，子菜单继续显示。
  clearCascadeSubmenuCloseTimer();
}

function onCascadeSubmenuLeave() {
  // 离开子菜单：同样走延迟关闭，方便用户从子菜单滑到其它位置时被误关。
  scheduleCascadeSubmenuClose();
}

function onVersionClick(template: AppWorkspaceTemplate, version: AppWorkspaceVersion) {
  emit("select-version", { template, version });
  closeMenu();
}
</script>

<template>
  <footer class="ta-workbench-footer">
    <div class="ta-workbench-footer-left">
      <!--
        两级菜单：当归属应用存在工作空间模板时，展示「应用 → 工作空间 → 版本」选择器。
        一级菜单展示工作空间模板，二级菜单展示该模板下的应用版本。
        鼠标 hover 模板时触发子菜单；点击版本后由父组件切换运行态 Workspace。
      -->
      <div
        v-if="!showSave && useCascadeMenu"
        class="ta-workbench-cascade"
        :class="{ 'is-open': menuOpen }"
      >
        <button
          ref="cascadeButtonRef"
          type="button"
          class="ta-workbench-footer-branch"
          :title="triggerTitle"
          :aria-expanded="menuOpen"
          aria-haspopup="menu"
          @click.stop="toggleMenu"
        >
          <ArrowLeftRight class="ta-workbench-footer-icon" />
        </button>
        <!--
          两级菜单用 <Teleport to="body"> + position:fixed 挂到 body 末尾，
          避开 dockview 面板 of overflow:hidden / 内部 stacking context 裁切。
          一级菜单 fixed 定位到触发按钮正上方；二级菜单 fixed 定位到当前 hover 行右侧，
          不再嵌在一级菜单 li 内，所以可以越过一级菜单的边界显示。
        -->
        <Teleport to="body">
          <div
            v-if="menuOpen && cascadeMenuPos"
            class="ta-workbench-cascade-panel"
            role="menu"
            :style="{ top: `${cascadeMenuPos.top}px`, left: `${cascadeMenuPos.left}px` }"
            @click.stop
          >
            <div class="ta-workbench-cascade-header">
              <span>应用：{{ appName || "—" }}</span>
              <span v-if="loadingTemplates" class="ta-workbench-cascade-loading">加载中…</span>
            </div>
            <ul class="ta-workbench-cascade-list" role="none">
              <li
                v-for="template in templates"
                :key="template.workspaceId"
                :class="[
                  'ta-workbench-cascade-item',
                  hoveredTemplateId === template.workspaceId && 'is-hovered',
                  template.versions?.some((v) => v.versionId === selectedVersionId) && 'is-selected'
                ]"
                role="menuitem"
                :aria-haspopup="true"
                @mouseenter="onTemplateEnter(template, $event)"
                @mouseleave="onTemplateLeave"
              >
                <div class="ta-workbench-cascade-item-main">
                  <span class="ta-workbench-cascade-item-name">{{ template.workspaceName }}</span>
                </div>
                <span class="ta-workbench-cascade-item-arrow" aria-hidden="true">›</span>
              </li>
            </ul>
          </div>
        </Teleport>
        <!--
          二级菜单独立 Teleport：与一级菜单共享 body 容器但用更高的 z-index，
          并通过 cascadeSubmenuPos 固定到 hover 行右侧，可越过一级菜单边界。
          单实例：每次 hover 切换模板时，hoveredTemplate / cascadeSubmenuPos 同步更新，
          避免给每个模板都挂一个子菜单造成 DOM 冗余。
        -->
        <Teleport to="body">
          <div
            v-if="hoveredTemplate && cascadeSubmenuPos"
            class="ta-workbench-cascade-submenu"
            role="menu"
            :style="{
              top: `${cascadeSubmenuPos.top}px`,
              left: `${cascadeSubmenuPos.left}px`,
              maxHeight: `${cascadeSubmenuPos.maxHeight}px`
            }"
            @mouseenter="onCascadeSubmenuEnter"
            @mouseleave="onCascadeSubmenuLeave"
          >
            <div class="ta-workbench-cascade-submenu-header">版本 · {{ hoveredTemplate.workspaceName }}</div>
            <div v-if="!hoveredTemplate.versions && loadingVersions" class="ta-workbench-cascade-submenu-loading">加载中…</div>
            <div
              v-else-if="!hoveredTemplate.versions || hoveredTemplate.versions.length === 0"
              class="ta-workbench-cascade-submenu-empty"
            >
              暂无版本
            </div>
            <ul v-else class="ta-workbench-cascade-submenu-list" role="none">
              <li
                v-for="version in hoveredTemplate.versions"
                :key="version.versionId"
                :class="['ta-workbench-cascade-submenu-item', version.versionId === selectedVersionId && 'is-selected']"
                role="menuitem"
                :title="version.versionId === selectedVersionId && personalWorkspaceBranch ? `当前 worktree：${personalWorkspaceBranch}` : version.branch"
                @click="onVersionClick(hoveredTemplate, version)"
              >
                <span class="ta-workbench-cascade-submenu-item-name">{{ version.version }}</span>
                <span class="ta-workbench-cascade-submenu-item-desc">{{ version.branch }}</span>
                <span
                  v-if="version.versionId === selectedVersionId && personalWorkspaceBranch"
                  class="ta-workbench-cascade-submenu-worktree"
                >
                  worktree: {{ personalWorkspaceBranch }}
                </span>
              </li>
            </ul>
            <!--
              底部固定「+新增版本」：与是否有版本、是否加载完成解耦。
              没版本时在「暂无版本」下面；有版本时在 ul 列表下方。
            -->
            <div
              class="ta-workbench-cascade-submenu-create"
              role="menuitem"
              :title="`为「${hoveredTemplate.workspaceName}」新增版本`"
              @click.stop="openCreateVersionDialog(hoveredTemplate)"
            >
              <Plus class="ta-workbench-cascade-submenu-item-icon" />
              <span>新增版本</span>
            </div>
          </div>
        </Teleport>
      </div>
      <button
        v-if="!showSave && showServerWorkspaceSwitch"
        type="button"
        class="ta-workbench-server-switch"
        :disabled="serverWorkspaceSwitchDisabled"
        title="切换服务器工作空间"
        aria-label="切换服务器工作空间"
        @click="emit('open-server-workspace-picker')"
      >
        <ServerCog class="ta-workbench-footer-icon" />
      </button>
      <template v-else-if="showSave">
        <span class="ta-workbench-footer-path">
          写入路径：<span class="ta-workbench-footer-path-value">{{ writePath ?? "—" }}</span>
        </span>
        <span class="ta-workbench-footer-separator">|</span>
        <span class="ta-workbench-footer-updated">更新时间：{{ updatedLabel }}</span>
      </template>
    </div>

    <div v-if="showSave" class="ta-workbench-footer-right">
      <button
        type="button"
        class="ta-workbench-footer-save"
        :disabled="!dirty || readonly || saving"
        :title="readonly ? '只读文件不可保存' : saving ? '保存中…' : '保存 (Ctrl+S)'"
        @click="emit('save')"
      >
        <Save class="ta-workbench-footer-save-icon" />
        <span>保存</span>
      </button>
    </div>
  </footer>
  <!--
    「+新增版本」弹窗：使用 el-dialog 居中显示，与两级菜单 hover 状态解耦。
    时间选择器 type="date" + format="YYYY-MM-DD" + value-format="YYYYMMDD"：
    标准库直接选日期，版本号为 yyyyMMdd。
    非标准库：先选分支，再选日期，版本号为 yyyyMMdd，分支名一并传给后端。
  -->
  <ElDialog
    v-model="createVersionOpen"
    :title="`为「${createVersionTarget?.workspaceName ?? ''}」新增版本`"
    width="420px"
    :close-on-click-modal="false"
    @close="cancelCreateVersion"
  >
    <div class="ta-workbench-create-version">
      <!-- 非标准库：先选分支 -->
      <template v-if="createVersionTarget?.standard === false">
        <label class="ta-workbench-create-version-label">选择分支</label>
        <el-select
          v-model="createVersionBranch"
          :loading="createVersionLoadingBranches"
          placeholder="请先选择分支"
          style="width: 100%"
        >
          <el-option
            v-for="branch in createVersionBranches"
            :key="branch"
            :label="branch"
            :value="branch"
          />
        </el-select>
      </template>
      <label class="ta-workbench-create-version-label">选择日期（格式 yyyyMMdd）</label>
      <ElDatePicker
        v-model="createVersionValue"
        type="date"
        format="YYYY-MM-DD"
        value-format="YYYYMMDD"
        placeholder="请选择日期"
        style="width: 100%"
      />
      <p class="ta-workbench-create-version-hint">提交后会在远端创建对应的工作空间版本。</p>
    </div>
    <template #footer>
      <button
        type="button"
        class="ta-workbench-create-version-cancel"
        :disabled="creatingVersion"
        @click="cancelCreateVersion"
      >
        取消
      </button>
      <button
        type="button"
        class="ta-workbench-create-version-confirm"
        :disabled="!createVersionValue || creatingVersion || (createVersionTarget?.standard === false && !createVersionBranch)"
        @click="confirmCreateVersion"
      >
        {{ creatingVersion ? "创建中…" : "确定" }}
      </button>
    </template>
  </ElDialog>
</template>

<style scoped>
.ta-workbench-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 30px;
  padding: 0 12px;
  background: #fff;
  border-top: 1px solid #eaeaea;
  flex-shrink: 0;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  font-size: 12px;
  color: #4b4b4b;
}

.ta-workbench-footer-left,
.ta-workbench-footer-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.ta-workbench-footer-middle {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
  white-space: nowrap;
  background: #f4f4f5;
  border-radius: 12px;
  padding: 4px 14px;
  height: 28px;
  border: 1px solid #e4e4e7;
}

.ta-workbench-footer-right {
  margin-left: auto;
}

.ta-workbench-footer-branch {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 0.8px solid #dfdfdf;
  border-radius: 6px;
  background: #fff;
  color: #333;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.ta-workbench-footer-branch:hover {
  background: #f5f5f5;
  border-color: #b5b5b5;
}

.ta-workbench-footer-branch-label {
  font-weight: 500;
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ta-workbench-footer-branch-ref {
  flex: 1 1 auto;
  min-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #71717a;
  font-size: 12px;
}

.ta-workbench-footer-branch-ref::before {
  content: "/";
  margin-right: 6px;
  color: #a1a1aa;
}

.ta-workbench-cascade-submenu-worktree {
  display: block;
  max-width: 100%;
  margin-top: 3px;
  overflow: hidden;
  color: #2563eb;
  font-family: "JetBrains Mono", "PingFang SC", monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ta-workbench-footer-icon {
  width: 14px;
  height: 14px;
  color: #555;
}

.ta-workbench-server-switch {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 0.8px solid #dfdfdf;
  border-radius: 6px;
  background: #fff;
  color: #333;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease, opacity 0.12s ease;
}

.ta-workbench-server-switch:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #b5b5b5;
}

.ta-workbench-server-switch:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.ta-workbench-footer-path,
.ta-workbench-footer-updated {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  font-family: "JetBrains Mono", "PingFang SC", monospace;
  font-size: 11px;
  color: #555;
}

.ta-workbench-footer-separator {
  color: #dfdfdf;
  margin: 0 4px;
  user-select: none;
}

.ta-workbench-footer-path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ta-workbench-footer-path-value {
  color: #18181b;
  font-weight: 500;
}

.ta-workbench-footer-updated {
  flex-shrink: 0;
  color: #888;
}

.ta-workbench-footer-save {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 16px;
  border: none;
  border-radius: 12px;
  background: #eeeeee;
  color: #333333;
  font: inherit;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
}

.ta-workbench-footer-save:hover:not(:disabled) {
  background: #e2e2e5;
  color: #111111;
}

.ta-workbench-footer-save:disabled {
  background: #f4f4f5;
  color: #a1a1aa;
  cursor: not-allowed;
}

.ta-workbench-footer-save-icon {
  width: 14px;
  height: 14px;
}

/* ===== 两级菜单样式 ===== */
.ta-workbench-cascade {
  position: relative;
}

.ta-workbench-cascade.is-open .ta-workbench-footer-branch {
  background: #f5f5f5;
  border-color: #b5b5b5;
}

/*
  一级菜单面板：使用 position:fixed + <Teleport to="body"> 挂到 body 末尾，
  避免被父级 dockview 面板的 overflow:hidden 或内部 stacking context 裁掉；
  top/left 通过 updateCascadeMenuPos() 在打开前基于按钮 getBoundingClientRect() 计算。
  transform: translateY(-100%) 让菜单底边对齐按钮上沿（按钮正上方）。
  不再设 max-height: 360px，让面板自然延展；改用 viewport-relative 兜底。
*/
.ta-workbench-cascade-panel {
  position: fixed;
  left: 0;
  min-width: 280px;
  max-width: 480px;
  max-height: calc(100vh - 24px);
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 6px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  z-index: 9999;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  transform: translateY(-100%);
}

.ta-workbench-cascade-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px 8px;
  font-size: 11px;
  color: #888;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 4px;
}

.ta-workbench-cascade-loading,
.ta-workbench-cascade-submenu-loading,
.ta-workbench-cascade-submenu-empty {
  font-size: 11px;
  color: #999;
  padding: 12px 8px;
  text-align: center;
}

.ta-workbench-cascade-list {
  margin: 0;
  padding: 0;
  list-style: none;
  /* 不再设 max-height，让子菜单自然延展；外层 panel 兜底 */
}

.ta-workbench-cascade-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.1s ease;
}

.ta-workbench-cascade-item:hover,
.ta-workbench-cascade-item.is-hovered {
  background: #f4f4f5;
}

.ta-workbench-cascade-item.is-selected {
  background: #f0f5ff;
  color: #1d3fb0;
}

.ta-workbench-cascade-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ta-workbench-cascade-item-name {
  font-size: 13px;
  font-weight: 500;
  color: #18181b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ta-workbench-cascade-item-desc {
  font-size: 11px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ta-workbench-cascade-item-arrow {
  color: #b5b5b5;
  font-size: 14px;
  line-height: 1;
}

/*
  二级菜单：position:fixed 让它可以越过一级菜单的边界显示在右侧，
  top/left 通过 updateCascadeSubmenuPos() 基于当前 hover 行的 getBoundingClientRect() 计算。
  z-index 比一级菜单高，避免在两个面板边缘被一级的 box-shadow 盖住。
  单独 Teleport 后，菜单天然不参与一级菜单的 layout，因此可以设置更大的 max-width / max-height。
*/
.ta-workbench-cascade-submenu {
  position: fixed;
  min-width: 200px;
  max-width: 320px;
  max-height: calc(100vh - 24px);
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 6px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  z-index: 10000;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}

.ta-workbench-cascade-submenu-header {
  padding: 4px 8px 6px;
  font-size: 11px;
  color: #888;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 4px;
}

.ta-workbench-cascade-submenu-list {
  margin: 0;
  padding: 0;
  list-style: none;
  /* 不再设 max-height，让子菜单级 max-height 兜底 */
}

.ta-workbench-cascade-submenu-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.1s ease;
}

.ta-workbench-cascade-submenu-item:hover {
  background: #f4f4f5;
}

.ta-workbench-cascade-submenu-item.is-selected {
  background: #f0f5ff;
  color: #1d3fb0;
}

.ta-workbench-cascade-submenu-item-name {
  font-size: 13px;
  font-weight: 500;
  color: #18181b;
}

.ta-workbench-cascade-submenu-item-desc {
  font-size: 11px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/*
  子菜单底部「+新增版本」按钮：与子菜单上下边距隔开，虚线框 + 强调色 + Plus 图标。
  hover 时高亮，点击后由父组件弹 el-dialog 选 yyyy年M月。
*/
.ta-workbench-cascade-submenu-create {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding: 6px 8px;
  border-top: 1px dashed #e4e4e7;
  border-radius: 6px;
  cursor: pointer;
  color: #1d4ed8;
  font-size: 12px;
  transition: background-color 0.1s ease;
}

.ta-workbench-cascade-submenu-create:hover {
  background: #eef3ff;
}

.ta-workbench-cascade-submenu-item-icon {
  width: 12px;
  height: 12px;
  color: inherit;
}

/*
  「+新增版本」弹窗内容：label + 时间选择器 + hint。
  按钮用 plain 风格，避免与 el-button 默认 primary 撞色。
*/
.ta-workbench-create-version {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ta-workbench-create-version-label {
  font-size: 12px;
  color: #555;
}

.ta-workbench-create-version-hint {
  font-size: 11px;
  color: #999;
  margin: 0;
}

.ta-workbench-create-version-cancel,
.ta-workbench-create-version-confirm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 14px;
  border-radius: 6px;
  border: 0.8px solid #dfdfdf;
  background: #fff;
  color: #333;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.ta-workbench-create-version-confirm {
  background: #18181b;
  border-color: #18181b;
  color: #fff;
  margin-left: 8px;
}

.ta-workbench-create-version-cancel:hover:not(:disabled) {
  background: #f5f5f5;
  border-color: #b5b5b5;
}

.ta-workbench-create-version-confirm:hover:not(:disabled) {
  background: #000;
}

.ta-workbench-create-version-cancel:disabled,
.ta-workbench-create-version-confirm:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
</style>
