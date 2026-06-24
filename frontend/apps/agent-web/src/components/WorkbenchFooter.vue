<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { GitBranch, Layers, Save } from "lucide-vue-next";
import type { ApplicationWorkspaceTemplate, ApplicationWorkspaceVersion } from "@test-agent/shared-types";

type VcsBranch = { name: string; isCurrent?: boolean };

// 透传类型：直接复用后端 VO（ApplicationWorkspaceTemplate / ApplicationWorkspaceVersion），
// 父组件负责把 versions 懒加载后回填到 template.versions 上。
export type AppWorkspaceTemplate = ApplicationWorkspaceTemplate & {
  versions?: ApplicationWorkspaceVersion[];
};
export type AppWorkspaceVersion = ApplicationWorkspaceVersion;

const props = defineProps<{
  /** 当前 VCS 分支名（来自 /vcs/status） */
  branch?: string;
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
}>();

const emit = defineEmits<{
  (e: "change-branch", branch: string): void;
  (e: "save"): void;
  // 选择某工作空间下的某个版本：父组件负责切换运行态 Workspace。
  (e: "select-version", payload: { template: AppWorkspaceTemplate; version: AppWorkspaceVersion }): void;
  // 要求父组件按需懒加载某模板下的版本列表
  (e: "load-versions", templateId: string): void;
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

const templates = computed(() => props.templates ?? []);
// 当父组件未传 templates 或 templates 为空时，使用传统 el-dropdown 展示 VCS 分支，保持向后兼容。
const useCascadeMenu = computed(() => templates.value.length > 0);

// ===== 两级菜单弹出状态 =====
// menuOpen: 一级菜单（工作空间列表）开关；hoveredTemplateId: 当前悬停的模板，控制二级菜单（版本）显隐。
const menuOpen = ref(false);
const hoveredTemplateId = ref<string | null>(null);
const menuRootRef = ref<HTMLElement | null>(null);

const selectedTemplate = computed(() =>
  templates.value.find((template) => template.versions?.some((version) => version.versionId === props.selectedVersionId))
);
const selectedVersion = computed(() =>
  selectedTemplate.value?.versions?.find((version) => version.versionId === props.selectedVersionId)
);

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
    menuOpen.value = true;
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
}

function onDocumentClick(event: MouseEvent) {
  if (!menuOpen.value) return;
  const target = event.target as Node | null;
  if (menuRootRef.value && target && !menuRootRef.value.contains(target)) {
    closeMenu();
  }
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === "Escape" && menuOpen.value) {
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
});

function onTemplateEnter(template: AppWorkspaceTemplate) {
  hoveredTemplateId.value = template.workspaceId;
  if (!template.versions) {
    // 版本未加载：通知父组件按需拉取；hover 状态保留，loading 完成后会渲染子菜单
    emit("load-versions", template.workspaceId);
  }
}

function onTemplateLeave() {
  // 200ms 延迟关闭，给用户从一级菜单滑到二级菜单留出时间；如在 200ms 内鼠标进入子菜单则取消关闭
  const hovered = hoveredTemplateId.value;
  if (!hovered) return;
  setTimeout(() => {
    if (hoveredTemplateId.value === hovered) {
      hoveredTemplateId.value = null;
    }
  }, 200);
}

function keepSubmenuOpen() {
  // 鼠标进入子菜单：清掉上面 setTimeout 的关闭意图（通过重置 hoveredTemplateId 实现）
  // 由于子菜单渲染条件依赖 hoveredTemplateId === templateId，hover 在模板上时已经置上，这里不需要额外动作
}

function onVersionClick(template: AppWorkspaceTemplate, version: AppWorkspaceVersion) {
  emit("select-version", { template, version });
  closeMenu();
}
</script>

<template>
  <footer class="ta-workbench-footer">
    <div v-if="showBranchValue" class="ta-workbench-footer-left">
      <!--
        两级菜单：当归属应用存在工作空间模板时，替代原来的 VCS 分支按钮。
        一级菜单展示工作空间模板，二级菜单展示该模板下的应用版本。
        鼠标 hover 模板时触发子菜单；点击版本后由父组件切换运行态 Workspace。
      -->
      <div
        v-if="useCascadeMenu"
        ref="menuRootRef"
        class="ta-workbench-cascade"
        :class="{ 'is-open': menuOpen }"
      >
        <button
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
        <div v-if="menuOpen" class="ta-workbench-cascade-panel" role="menu" @click.stop>
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
              @mouseenter="onTemplateEnter(template)"
              @mouseleave="onTemplateLeave"
            >
              <div class="ta-workbench-cascade-item-main">
                <span class="ta-workbench-cascade-item-name">{{ template.workspaceName }}</span>
                <span class="ta-workbench-cascade-item-desc">{{ template.directoryPath }} · {{ template.branch }}</span>
              </div>
              <span class="ta-workbench-cascade-item-arrow" aria-hidden="true">›</span>
              <!--
                子菜单（版本列表）：仅在 hover 或加载完成时渲染；通过 v-if 保证不渲染多余 DOM。
                子菜单挂载在父级 li 上，方便 hover 状态在跨元素间自然转移。
              -->
              <div
                v-if="hoveredTemplateId === template.workspaceId"
                class="ta-workbench-cascade-submenu"
                role="menu"
                @mouseenter="keepSubmenuOpen"
              >
                <div class="ta-workbench-cascade-submenu-header">版本</div>
                <div v-if="!template.versions && loadingVersions" class="ta-workbench-cascade-submenu-loading">加载中…</div>
                <div
                  v-else-if="!template.versions || template.versions.length === 0"
                  class="ta-workbench-cascade-submenu-empty"
                >
                  暂无版本
                </div>
                <ul v-else class="ta-workbench-cascade-submenu-list" role="none">
                  <li
                    v-for="version in template.versions"
                    :key="version.versionId"
                    :class="['ta-workbench-cascade-submenu-item', version.versionId === selectedVersionId && 'is-selected']"
                    role="menuitem"
                    @click="onVersionClick(template, version)"
                  >
                    <span class="ta-workbench-cascade-submenu-item-name">{{ version.version }}</span>
                    <span class="ta-workbench-cascade-submenu-item-desc">{{ version.branch }}</span>
                  </li>
                </ul>
              </div>
            </li>
          </ul>
        </div>
      </div>
      <!--
        向后兼容：未配置应用工作空间模板时回退到原 VCS 分支选择，保持现状可用。
      -->
      <el-dropdown
        v-else-if="branchOptions.length > 0"
        trigger="click"
        @command="(name: string) => emit('change-branch', name)"
      >
        <button type="button" class="ta-workbench-footer-branch" :title="`当前分支：${branch ?? '—'}`">
          <GitBranch class="ta-workbench-footer-icon" />
          <span class="ta-workbench-footer-branch-label">{{ branch ?? "选择分支" }}</span>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="item in branchOptions"
              :key="item.name"
              :command="item.name"
              :disabled="item.name === branch"
            >
              {{ item.name }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <span v-else class="ta-workbench-footer-branch is-disabled">
        <GitBranch class="ta-workbench-footer-icon" />
        <span>分支选择</span>
      </span>
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

.ta-workbench-cascade-panel {
  position: absolute;
  left: 0;
  bottom: calc(100% + 6px);
  min-width: 280px;
  max-width: 360px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 6px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.14);
  z-index: 60;
  font-family: "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
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
  max-height: 360px;
  overflow-y: auto;
}

.ta-workbench-cascade-item {
  position: relative;
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

.ta-workbench-cascade-submenu {
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
  max-height: 280px;
  overflow-y: auto;
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
</style>
