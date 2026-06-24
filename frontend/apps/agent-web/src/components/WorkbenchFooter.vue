<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { BookmarkPlus, GitBranch, Layers, Plus, Save } from "lucide-vue-next";
import { ElDatePicker, ElDialog } from "element-plus";
import type { ApplicationWorkspaceTemplate, ApplicationWorkspaceVersion } from "@test-agent/shared-types";

type VcsBranch = { name: string; isCurrent?: boolean };

// 分组键：两级菜单中的一级菜单分类。
// - current: 当前分支（来自 vcsStatus.branch）
// - default: 仓库默认分支（来自 vcsStatus.default_branch）
// - recent: 用户最近一次手动选择的分支（来自 user_workspace_branch_preferences）
// - other: 父组件额外传入的分支列表（branches prop 中未归入前几项的）
export type BranchGroupKey = "current" | "default" | "recent" | "other";

// 透传类型：直接复用后端 VO（ApplicationWorkspaceTemplate / ApplicationWorkspaceVersion），
// 父组件负责把 versions 懒加载后回填到 template.versions 上。
export type AppWorkspaceTemplate = ApplicationWorkspaceTemplate & {
  versions?: ApplicationWorkspaceVersion[];
};
export type AppWorkspaceVersion = ApplicationWorkspaceVersion;

const props = defineProps<{
  /** 当前 VCS 分支名（来自 /vcs/status） */
  branch?: string;
  /** 仓库默认分支名（来自 /vcs/status 的 default_branch 字段） */
  defaultBranch?: string;
  /** 用户在该 (appId, workspaceId) 维度下最近一次手动选择的 VCS 分支偏好 */
  recentBranch?: string;
  /** 可选分支列表；若提供则会渲染下拉切换 */
  branches?: VcsBranch[];
  /** 是否展示分支选择（仅工作目录场景） */
  showBranch?: boolean;
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
  /** 归属当前应用的工作空间模板列表；为空则降级显示旧的 VCS 分支按钮 */
  templates?: AppWorkspaceTemplate[];
  /** 当前选中的版本 ID；用于标记菜单项高亮 */
  selectedVersionId?: string;
  /** 工作空间模板是否仍在加载 */
  loadingTemplates?: boolean;
  /** 工作空间版本是否仍在加载（按模板分组懒加载时使用） */
  loadingVersions?: boolean;
  /** 是否禁用"保存当前分支为偏好"按钮（无 appId/workspaceId/branch 时） */
  rememberDisabled?: boolean;
  /** 「+新增版本」是否正在提交中（父组件控制禁用 & 展示 loading） */
  creatingVersion?: boolean;
}>();

const emit = defineEmits<{
  (e: "change-branch", branch: string): void;
  (e: "save"): void;
  // 把当前 VCS 分支显式写入 user_workspace_branch_preferences。
  // 当前分支按钮只有 current 一项且被 disable，没办法通过 change-branch 触发持久化，
  // 因此单独提供"记住当前分支"入口，操作链路上等价于"切到当前分支后写入偏好"。
  (e: "remember-current-branch"): void;
  // 选择某工作空间下的某个版本：父组件负责切换运行态 Workspace。
  (e: "select-version", payload: { template: AppWorkspaceTemplate; version: AppWorkspaceVersion }): void;
  // 要求父组件按需懒加载某模板下的版本列表
  (e: "load-versions", templateId: string): void;
  // 「+新增版本」弹窗确认后回调：父组件负责调用 createWorkspaceVersion。
  // version 字段保留用户在前端选择的原始字符串（"yyyy年M月"），后端会校验并按需转换分支/路径。
  (e: "create-version", payload: { template: AppWorkspaceTemplate; version: string }): void;
}>();

const showBranchValue = computed(() => props.showBranch !== false);
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

const branchOptions = computed<VcsBranch[]>(() => {
  if (props.branches && props.branches.length > 0) return props.branches;
  return props.branch ? [{ name: props.branch, isCurrent: true }] : [];
});

// ===== 分支两级菜单 =====
type BranchGroup = { key: BranchGroupKey; label: string; items: VcsBranch[] };

// 依据现有数据构造一级菜单分组：
// 1) 当前分支：vcsStatus.branch；
// 2) 仓库默认分支：vcsStatus.defaultBranch（与当前分支一致时跳过，避免重复展示）；
// 3) 最近使用：用户最近一次手动选择的分支偏好（与前面两项一致时跳过）；
// 4) 其他：branches prop 里尚未归入上述三类的分支。
// 同一分支名只会出现在一个分组里；空分组不渲染对应一级菜单。
const branchGroups = computed<BranchGroup[]>(() => {
  const seen = new Set<string>();
  const groups: BranchGroup[] = [];

  const pushUnique = (group: BranchGroup, branch: VcsBranch) => {
    if (!branch?.name) return;
    if (seen.has(branch.name)) return;
    seen.add(branch.name);
    group.items.push(branch);
  };

  const current = props.branch;
  const currentGroup: BranchGroup = { key: "current", label: "当前分支", items: [] };
  if (current) pushUnique(currentGroup, { name: current, isCurrent: true });
  if (currentGroup.items.length > 0) groups.push(currentGroup);

  const defaultBranch = props.defaultBranch;
  if (defaultBranch) {
    const defaultGroup: BranchGroup = { key: "default", label: "默认分支", items: [] };
    pushUnique(defaultGroup, { name: defaultBranch });
    if (defaultGroup.items.length > 0) groups.push(defaultGroup);
  }

  const recent = props.recentBranch;
  if (recent) {
    const recentGroup: BranchGroup = { key: "recent", label: "最近使用", items: [] };
    pushUnique(recentGroup, { name: recent });
    if (recentGroup.items.length > 0) groups.push(recentGroup);
  }

  const otherGroup: BranchGroup = { key: "other", label: "其他分支", items: [] };
  for (const item of branchOptions.value) {
    if (item?.name) pushUnique(otherGroup, { name: item.name, isCurrent: item.isCurrent });
  }
  if (otherGroup.items.length > 0) groups.push(otherGroup);

  return groups;
});

// 当没有任何分支信息时（既无 current 也无 branches prop），不渲染分支下拉按钮。
// 触发按钮的可见性遵循 showBranch 控制；这里额外保证下拉有内容才允许点击。
const hasBranchMenu = computed(() => branchGroups.value.length > 0);

const templates = computed(() => props.templates ?? []);

// ===== 应用工作空间两级菜单的弹出状态 =====
// 当父组件未传 templates 或 templates 为空时，使用传统 el-dropdown 展示 VCS 分支，保持向后兼容。
const useCascadeMenu = computed(() => templates.value.length > 0);

// ===== 两级菜单弹出状态 =====
// menuOpen: 一级菜单（工作空间列表）开关；hoveredTemplateId: 当前悬停的模板，控制二级菜单（版本）显隐。
// cascadeButtonRef: 触发按钮 DOM 引用；cascadeMenuPos / cascadeSubmenuPos: 一级与二级菜单的 fixed 定位坐标。
// 菜单用 <Teleport to="body"> + position:fixed 挂到 body 末尾，避开父级 dockview 面板 overflow:hidden 裁切。
const menuOpen = ref(false);
const hoveredTemplateId = ref<string | null>(null);
const hoveredTemplateEl = ref<HTMLElement | null>(null);
const cascadeButtonRef = ref<HTMLElement | null>(null);
const cascadeMenuPos = ref<{ top: number; left: number } | null>(null);
const cascadeSubmenuPos = ref<{ top: number; left: number } | null>(null);
let cascadePosRafId: number | null = null;
let cascadeSubmenuCloseTimer: ReturnType<typeof setTimeout> | null = null;

// ===== 「+新增版本」弹窗状态 =====
// createVersionTarget: 当前弹窗操作的模板；createVersionValue: el-date-picker 选中的 yyyy年M月 字符串。
// createVersionOpen 控制 ElDialog 显隐；与两级菜单的 hover 状态解耦，避免鼠标移开后弹窗被父级 v-if 卸载。
const createVersionTarget = ref<AppWorkspaceTemplate | null>(null);
const createVersionValue = ref<string>("");
const createVersionOpen = ref(false);

function openCreateVersionDialog(template: AppWorkspaceTemplate) {
  createVersionTarget.value = template;
  createVersionValue.value = "";
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
  emit("create-version", { template: target, version: createVersionValue.value });
  createVersionOpen.value = false;
}

function cancelCreateVersion() {
  createVersionOpen.value = false;
}

// ===== 分支两级菜单弹出状态 =====
// branchMenuOpen: 一级菜单（分组）开关；hoveredBranchGroup: 当前悬停的分组，控制二级菜单（分支名）显隐。
// branchButtonRef: 触发按钮的 DOM 引用，用于 Teleport 后定位菜单的 fixed 坐标。
// branchMenuPos: 计算后的菜单坐标，{top, left}，避免被父级 dockview 面板 overflow:hidden 裁切。
const branchMenuOpen = ref(false);
const hoveredBranchGroup = ref<BranchGroupKey | null>(null);
const branchButtonRef = ref<HTMLElement | null>(null);
const branchMenuPos = ref<{ top: number; left: number } | null>(null);
let branchSubmenuCloseTimer: ReturnType<typeof setTimeout> | null = null;

function clearBranchSubmenuCloseTimer() {
  if (branchSubmenuCloseTimer !== null) {
    clearTimeout(branchSubmenuCloseTimer);
    branchSubmenuCloseTimer = null;
  }
}

// 计算并刷新菜单坐标：基于触发按钮的 getBoundingClientRect()，定位到按钮正上方。
// 使用 fixed 定位 + Teleport 把菜单挂到 body，避开 dockview 面板 overflow:hidden / 内部 stacking context 的裁切。
function updateBranchMenuPos() {
  if (!branchButtonRef.value) return;
  const rect = branchButtonRef.value.getBoundingClientRect();
  branchMenuPos.value = {
    top: rect.top - 6, // 6px 间隙
    left: rect.left
  };
}

// 菜单打开期间：滚动 / 窗口尺寸变化时同步刷新坐标，避免菜单停留在原位置与按钮错位。
// 关闭时清掉监听，避免无谓的全局事件。
let branchPosRafId: number | null = null;
function onBranchPosScrollOrResize() {
  if (!branchMenuOpen.value) return;
  if (branchPosRafId !== null) cancelAnimationFrame(branchPosRafId);
  branchPosRafId = requestAnimationFrame(() => {
    updateBranchMenuPos();
    branchPosRafId = null;
  });
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
// 防遮挡：先按 li 的 top 算 natural top；下一帧测量子菜单实际高度，若底部超出视口则向上偏移。
// 偏移量 = (naturalTop + height) - (viewportHeight - margin)；偏移后底部对齐视口底部减 margin，
// 顶部不会 < margin（因为子菜单 max-height = 100vh - 24 = viewport - 2*margin，偏移后顶至少是 margin）。
function updateCascadeSubmenuPos() {
  if (!hoveredTemplateEl.value) {
    cascadeSubmenuPos.value = null;
    return;
  }
  const liRect = hoveredTemplateEl.value.getBoundingClientRect();
  const panelEl = document.querySelector(".ta-workbench-cascade-panel") as HTMLElement | null;
  const anchorRight = panelEl ? panelEl.getBoundingClientRect().right : liRect.right;
  const naturalTop = liRect.top - 6;
  const left = anchorRight + 4;
  cascadeSubmenuPos.value = { top: naturalTop, left };
  // 渲染后再做一次"是否超出底部"的修正
  void nextTick(() => {
    if (!cascadeSubmenuPos.value) return;
    const submenuEl = document.querySelector(".ta-workbench-cascade-submenu") as HTMLElement | null;
    if (!submenuEl) return;
    const rect = submenuEl.getBoundingClientRect();
    const viewportHeight = window.innerHeight;
    const margin = 12;
    if (rect.bottom > viewportHeight - margin) {
      const overflow = rect.bottom - (viewportHeight - margin);
      const newTop = Math.max(margin, naturalTop - overflow);
      if (newTop !== cascadeSubmenuPos.value.top) {
        cascadeSubmenuPos.value = { top: newTop, left };
      }
    }
  });
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
  return props.appName ? `${props.appName} 工作空间` : "应用工作空间";
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
  // 分支菜单同样已 Teleport 到 body，用 closest() 判断。
  if (branchMenuOpen.value) {
    const target = event.target as Node | null;
    if (!target) return;
    const insideMenu = target instanceof Element ? target.closest(".ta-workbench-branch-panel") : null;
    const insideButton = target instanceof Element ? target.closest(".ta-workbench-branch-cascade") : null;
    if (!insideMenu && !insideButton) {
      closeBranchMenu();
    }
  }
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  if (menuOpen.value) {
    closeMenu();
    return;
  }
  if (branchMenuOpen.value) {
    closeBranchMenu();
  }
}

onMounted(() => {
  document.addEventListener("click", onDocumentClick);
  document.addEventListener("keydown", onDocumentKeydown);
});
onBeforeUnmount(() => {
  document.removeEventListener("click", onDocumentClick);
  document.removeEventListener("keydown", onDocumentKeydown);
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
  // 等 hoveredTemplateId 触发 v-if 渲染子菜单后再算坐标。
  void nextTick(() => updateCascadeSubmenuPos());
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

// ===== 分支两级菜单交互 =====

function toggleBranchMenu() {
  if (branchMenuOpen.value) {
    closeBranchMenu();
  } else {
    // 打开前先计算坐标，fixed 定位在 Teleport 之后才有意义。
    updateBranchMenuPos();
    branchMenuOpen.value = true;
    window.addEventListener("scroll", onBranchPosScrollOrResize, true);
    window.addEventListener("resize", onBranchPosScrollOrResize);
  }
}

function closeBranchMenu() {
  branchMenuOpen.value = false;
  hoveredBranchGroup.value = null;
  clearBranchSubmenuCloseTimer();
  window.removeEventListener("scroll", onBranchPosScrollOrResize, true);
  window.removeEventListener("resize", onBranchPosScrollOrResize);
  if (branchPosRafId !== null) {
    cancelAnimationFrame(branchPosRafId);
    branchPosRafId = null;
  }
}

function onBranchGroupEnter(groupKey: BranchGroupKey) {
  clearBranchSubmenuCloseTimer();
  hoveredBranchGroup.value = groupKey;
}

function onBranchGroupLeave() {
  // 200ms 延迟关闭二级菜单，给用户从一级菜单滑到子菜单留出时间。
  // 期间若鼠标进入子菜单，onBranchSubmenuEnter 会清掉这个 timer。
  branchSubmenuCloseTimer = setTimeout(() => {
    hoveredBranchGroup.value = null;
    branchSubmenuCloseTimer = null;
  }, 200);
}

function onBranchSubmenuEnter() {
  // 进入子菜单时清掉关闭意图，hoveredBranchGroup 保持。
  clearBranchSubmenuCloseTimer();
}

function onBranchClick(branchName: string) {
  emit("change-branch", branchName);
  closeBranchMenu();
}

onBeforeUnmount(() => {
  clearBranchSubmenuCloseTimer();
  // 卸载时如果菜单还开着，连带清掉全局事件监听
  window.removeEventListener("scroll", onBranchPosScrollOrResize, true);
  window.removeEventListener("resize", onBranchPosScrollOrResize);
  if (branchPosRafId !== null) {
    cancelAnimationFrame(branchPosRafId);
    branchPosRafId = null;
  }
  // 工作空间两级菜单的全局监听兜底
  clearCascadeSubmenuCloseTimer();
  window.removeEventListener("scroll", onCascadePosScrollOrResizeBound, true);
  window.removeEventListener("resize", onCascadePosScrollOrResizeBound);
  if (cascadePosRafId !== null) {
    cancelAnimationFrame(cascadePosRafId);
    cascadePosRafId = null;
  }
});
</script>

<template>
  <footer class="ta-workbench-footer">
    <div v-if="showBranchValue" class="ta-workbench-footer-left">
      <!--
        两级菜单：当归属应用存在工作空间模板时，展示「应用 → 工作空间 → 版本」选择器。
        一级菜单展示工作空间模板，二级菜单展示该模板下的应用版本。
        鼠标 hover 模板时触发子菜单；点击版本后由父组件切换运行态 Workspace。
      -->
      <div
        v-if="useCascadeMenu"
        class="ta-workbench-cascade"
        :class="{ 'is-open': menuOpen }"
      >
        <button
          ref="cascadeButtonRef"
          type="button"
          class="ta-workbench-footer-branch"
          :title="triggerLabel"
          :aria-expanded="menuOpen"
          aria-haspopup="menu"
          @click.stop="toggleMenu"
        >
          <Layers class="ta-workbench-footer-icon" />
          <span class="ta-workbench-footer-branch-label">{{ triggerLabel }}</span>
        </button>
        <!--
          两级菜单用 <Teleport to="body"> + position:fixed 挂到 body 末尾，
          避开 dockview 面板的 overflow:hidden / 内部 stacking context 裁切。
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
            :style="{ top: `${cascadeSubmenuPos.top}px`, left: `${cascadeSubmenuPos.left}px` }"
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
                @click="onVersionClick(hoveredTemplate, version)"
              >
                <span class="ta-workbench-cascade-submenu-item-name">{{ version.version }}</span>
                <span class="ta-workbench-cascade-submenu-item-desc">{{ version.branch }}</span>
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
      <!--
        VCS 分支两级菜单：替代原先的单级 el-dropdown。
        一级菜单按"来源"分组（当前分支 / 默认分支 / 最近使用 / 其他分支），
        二级菜单展示该组下的分支名。
        数据从 props.branch / defaultBranch / recentBranch / branches 派生；空分组不渲染。
        hover 一级菜单项展开二级菜单，点击分支名后 emit('change-branch') 由父组件持久化偏好。
        菜单用 <Teleport to="body"> + position:fixed 挂到 body，避开 dockview 面板 overflow:hidden 裁切。
      -->
      <div
        v-if="hasBranchMenu"
        class="ta-workbench-branch-cascade"
        :class="{ 'is-open': branchMenuOpen }"
      >
        <button
          ref="branchButtonRef"
          type="button"
          class="ta-workbench-footer-branch"
          :title="`当前分支：${branch ?? '—'}`"
          :aria-expanded="branchMenuOpen"
          aria-haspopup="menu"
          @click.stop="toggleBranchMenu"
        >
          <GitBranch class="ta-workbench-footer-icon" />
          <span class="ta-workbench-footer-branch-label">{{ branch ?? "选择分支" }}</span>
        </button>
        <Teleport to="body">
          <div
            v-if="branchMenuOpen && branchMenuPos"
            class="ta-workbench-branch-panel"
            role="menu"
            :style="{ top: `${branchMenuPos.top}px`, left: `${branchMenuPos.left}px` }"
            @click.stop
          >
            <ul class="ta-workbench-branch-list" role="none">
              <li
                v-for="group in branchGroups"
                :key="group.key"
                :class="[
                  'ta-workbench-branch-group',
                  hoveredBranchGroup === group.key && 'is-hovered'
                ]"
                role="menuitem"
                :aria-haspopup="true"
                @mouseenter="onBranchGroupEnter(group.key)"
                @mouseleave="onBranchGroupLeave"
              >
                <div class="ta-workbench-branch-group-main">
                  <span class="ta-workbench-branch-group-label">{{ group.label }}</span>
                  <span class="ta-workbench-branch-group-count">{{ group.items.length }}</span>
                </div>
                <span class="ta-workbench-branch-group-arrow" aria-hidden="true">›</span>
                <!--
                  二级菜单：仅在 hover 时渲染；通过 v-if 控制 DOM 数量。
                  子菜单挂载在父级 li 上，hover 从一级菜单跨到子菜单时不需要补额外的 mousemove 逻辑。
                -->
                <div
                  v-if="hoveredBranchGroup === group.key"
                  class="ta-workbench-branch-submenu"
                  role="menu"
                  @mouseenter="onBranchSubmenuEnter"
                >
                  <ul class="ta-workbench-branch-submenu-list" role="none">
                    <li
                      v-for="item in group.items"
                      :key="`${group.key}:${item.name}`"
                      :class="[
                        'ta-workbench-branch-submenu-item',
                        (item.name === branch || item.isCurrent) && 'is-current',
                        (item.name === recentBranch && group.key !== 'recent') && 'is-recent'
                      ]"
                      role="menuitem"
                      @click="onBranchClick(item.name)"
                    >
                      <span class="ta-workbench-branch-submenu-item-name">{{ item.name }}</span>
                      <span v-if="item.name === branch || item.isCurrent" class="ta-workbench-branch-submenu-item-tag">当前</span>
                      <span v-else-if="item.name === recentBranch && group.key !== 'recent'" class="ta-workbench-branch-submenu-item-tag is-recent">最近</span>
                    </li>
                  </ul>
                </div>
              </li>
            </ul>
          </div>
        </Teleport>
      </div>
      <!-- <span v-else class="ta-workbench-footer-branch is-disabled">
        <GitBranch class="ta-workbench-footer-icon" />
        <span>分支选择</span>
      </span> -->
      <!--
        单独"记住当前分支"按钮：两级菜单中"当前分支"组只有 current branch 一项，
        用户没办法通过 change-branch 路径触发 markRecentBranch 写入偏好。
        这里把"切到当前分支并写入偏好"显式化，保证重启前后数据可写入 user_workspace_branch_preferences。
      -->
      <button
        v-if="hasBranchMenu"
        type="button"
        class="ta-workbench-footer-remember"
        :disabled="rememberDisabled"
        :title="
          rememberDisabled
            ? '需要选择应用与工作区后才会写入分支偏好'
            : `把当前分支「${branch ?? ''}」写入分支偏好`
        "
        @click="emit('remember-current-branch')"
      >
        <BookmarkPlus class="ta-workbench-footer-icon" />
        <span>记住当前分支</span>
      </button>
    </div>

    <div v-if="showSave" class="ta-workbench-footer-middle">
      <span class="ta-workbench-footer-path">
        写入路径：<span class="ta-workbench-footer-path-value">{{ writePath ?? "—" }}</span>
      </span>
      <span class="ta-workbench-footer-updated">更新时间：{{ updatedLabel }}</span>
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
    时间选择器 type="month" + format="yyyy年M月" + value-format="yyyy年M月"，
    选完后 createVersionValue 直接是 "2024年1月" 这种字符串，原样透传给后端。
  -->
  <ElDialog
    v-model="createVersionOpen"
    :title="`为「${createVersionTarget?.workspaceName ?? ''}」新增版本`"
    width="420px"
    :close-on-click-modal="false"
    @close="cancelCreateVersion"
  >
    <div class="ta-workbench-create-version">
      <label class="ta-workbench-create-version-label">选择月份（格式 yyyy年M月）</label>
      <ElDatePicker
        v-model="createVersionValue"
        type="month"
        format="yyyy年M月"
        value-format="yyyy年M月"
        placeholder="请选择月份"
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
        :disabled="!createVersionValue || creatingVersion"
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
  height: 36px;
  padding: 0 12px;
  background: #fff;
  border-top: 1px solid #ddd;
  flex-shrink: 0;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
  font-size: 12px;
  color: #4b4b4b;
}

.ta-workbench-footer-left,
.ta-workbench-footer-right {
  display: flex;
  align-items: center;
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
}

.ta-workbench-footer-right {
  margin-left: auto;
}

.ta-workbench-footer-branch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 10px;
  border: 0.8px solid #dfdfdf;
  border-radius: 6px;
  background: #fff;
  color: #333;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease;
  font: inherit;
}

.ta-workbench-footer-branch:hover {
  background: #f5f5f5;
  border-color: #b5b5b5;
}

.ta-workbench-footer-branch.is-disabled {
  cursor: default;
  color: #888;
  background: #fafafa;
}

.ta-workbench-footer-remember {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 26px;
  padding: 0 10px;
  border: 0.8px dashed #b5c8ff;
  border-radius: 6px;
  background: #f3f7ff;
  color: #1d4ed8;
  cursor: pointer;
  transition: background-color 0.12s ease, border-color 0.12s ease;
  font: inherit;
}

.ta-workbench-footer-remember:hover:not(:disabled) {
  background: #e6efff;
  border-color: #6688e8;
}

.ta-workbench-footer-remember:disabled {
  cursor: default;
  color: #9aa0a6;
  background: #fafafa;
  border-color: #e1e1e1;
}

.ta-workbench-footer-branch-label {
  font-weight: 500;
}

.ta-workbench-footer-icon {
  width: 14px;
  height: 14px;
  color: #555;
}

.ta-workbench-footer-path,
.ta-workbench-footer-updated {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  font-family: "JetBrains Mono", "PingFang SC", monospace;
  font-size: 12px;
  color: #555;
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
  height: 26px;
  padding: 0 12px;
  border: none;
  border-radius: 6px;
  background: #18181b;
  color: #fff;
  font: inherit;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.12s ease, opacity 0.12s ease;
}

.ta-workbench-footer-save:hover:not(:disabled) {
  background: #000;
}

.ta-workbench-footer-save:disabled {
  background: #c0c4cc;
  cursor: not-allowed;
  opacity: 0.7;
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

/* ===== 分支两级菜单样式 ===== */
.ta-workbench-branch-cascade {
  position: relative;
}

.ta-workbench-branch-cascade.is-open .ta-workbench-footer-branch {
  background: #f5f5f5;
  border-color: #b5b5b5;
}

/*
  菜单面板：使用 position:fixed + <Teleport to="body"> 挂到 body 末尾，
  避免被父级 dockview 面板的 overflow:hidden 或内部 stacking context 裁掉；
  top/left 通过 updateBranchMenuPos() 在打开前基于按钮 getBoundingClientRect() 计算。
  transform: translateY(-100%) 让菜单底边对齐按钮上沿（按钮正上方）。
*/
.ta-workbench-branch-panel {
  position: fixed;
  min-width: 200px;
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

.ta-workbench-branch-list {
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 360px;
  overflow-y: auto;
}

.ta-workbench-branch-group {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.1s ease;
}

.ta-workbench-branch-group:hover,
.ta-workbench-branch-group.is-hovered {
  background: #f4f4f5;
}

.ta-workbench-branch-group-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.ta-workbench-branch-group-label {
  font-size: 13px;
  font-weight: 500;
  color: #18181b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ta-workbench-branch-group-count {
  font-size: 11px;
  color: #999;
  background: #f0f0f0;
  border-radius: 999px;
  padding: 0 6px;
  min-width: 18px;
  text-align: center;
}

.ta-workbench-branch-group-arrow {
  color: #b5b5b5;
  font-size: 14px;
  line-height: 1;
}

.ta-workbench-branch-submenu {
  position: absolute;
  top: -6px;
  left: calc(100% + 4px);
  min-width: 180px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 6px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  z-index: 1;
}

.ta-workbench-branch-submenu-list {
  margin: 0;
  padding: 0;
  list-style: none;
  max-height: 280px;
  overflow-y: auto;
}

.ta-workbench-branch-submenu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.1s ease;
}

.ta-workbench-branch-submenu-item:hover {
  background: #f4f4f5;
}

.ta-workbench-branch-submenu-item.is-current {
  background: #f0f5ff;
  color: #1d3fb0;
}

.ta-workbench-branch-submenu-item-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  color: #18181b;
  font-family: "JetBrains Mono", "PingFang SC", monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ta-workbench-branch-submenu-item-tag {
  font-size: 10px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 4px;
  background: #1d3fb0;
  color: #fff;
  font-weight: 500;
}

.ta-workbench-branch-submenu-item-tag.is-recent {
  background: #f3f7ff;
  color: #1d4ed8;
  border: 0.5px solid #b9c8ff;
}
</style>
