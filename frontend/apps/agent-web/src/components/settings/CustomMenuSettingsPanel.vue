<script setup lang="ts">
import { computed, ref } from "vue";
import { ChevronDown, ChevronUp, Pencil, Plus, Trash2 } from "lucide-vue-next";
import { customMenuIconComponent } from "../custom-menu-icons";
import {
  CUSTOM_MENU_ICON_OPTIONS,
  CUSTOM_MENU_LIMIT,
  CUSTOM_MENU_NAME_MAX_LENGTH,
  createCustomMenuId,
  normalizeCustomMenuName,
  normalizeCustomMenuUrl,
  type CustomMenuIconKey,
  type CustomMenuItem
} from "../custom-menus";

const props = defineProps<{
  customMenus: CustomMenuItem[];
}>();

const emit = defineEmits<{
  (e: "custom-menus-change", items: CustomMenuItem[]): void;
}>();

const editingId = ref<string | null>(null);
const pendingDeleteId = ref<string | null>(null);
const draftName = ref("");
const draftIcon = ref<CustomMenuIconKey>("globe");
const draftUrl = ref("");
const formError = ref("");

const submitLabel = computed(() => editingId.value ? "保存修改" : "添加菜单");
const limitReached = computed(() => props.customMenus.length >= CUSTOM_MENU_LIMIT && !editingId.value);

function resetDraft() {
  editingId.value = null;
  draftName.value = "";
  draftIcon.value = "globe";
  draftUrl.value = "";
  formError.value = "";
}

function editMenu(item: CustomMenuItem) {
  pendingDeleteId.value = null;
  editingId.value = item.id;
  draftName.value = item.name;
  draftIcon.value = item.icon;
  draftUrl.value = item.url;
  formError.value = "";
}

function submitMenu() {
  formError.value = "";
  try {
    const name = normalizeCustomMenuName(draftName.value);
    const duplicate = props.customMenus.some((item) =>
      item.id !== editingId.value && item.name.toLocaleLowerCase() === name.toLocaleLowerCase()
    );
    if (duplicate) throw new Error("菜单名不能重复");
    const url = normalizeCustomMenuUrl(draftUrl.value, window.location.origin);
    const item: CustomMenuItem = {
      id: editingId.value ?? createCustomMenuId(),
      name,
      icon: draftIcon.value,
      url
    };
    const nextItems = editingId.value
      ? props.customMenus.map((current) => current.id === editingId.value ? item : current)
      : [...props.customMenus, item];
    if (nextItems.length > CUSTOM_MENU_LIMIT) throw new Error(`最多添加 ${CUSTOM_MENU_LIMIT} 个自定义菜单`);
    emit("custom-menus-change", nextItems);
    resetDraft();
  } catch (error) {
    formError.value = error instanceof Error ? error.message : "菜单配置不正确";
  }
}

function confirmDelete(id: string) {
  emit("custom-menus-change", props.customMenus.filter((item) => item.id !== id));
  pendingDeleteId.value = null;
  if (editingId.value === id) resetDraft();
}

function moveMenu(index: number, offset: -1 | 1) {
  const targetIndex = index + offset;
  if (targetIndex < 0 || targetIndex >= props.customMenus.length) return;
  const items = [...props.customMenus];
  const [item] = items.splice(index, 1);
  if (!item) return;
  items.splice(targetIndex, 0, item);
  emit("custom-menus-change", items);
}
</script>

<template>
  <div class="ta-custom-menu-settings">
    <p class="ta-custom-menu-settings__intro">
      添加常用内网页面或同源功能入口。菜单会显示在左侧活动栏，并在工作台内打开独立 Tab。
    </p>

    <section class="ta-custom-menu-form" aria-label="自定义菜单表单">
      <div class="ta-custom-menu-form__grid">
        <label>
          <span>菜单名</span>
          <el-input
            v-model="draftName"
            :maxlength="CUSTOM_MENU_NAME_MAX_LENGTH"
            show-word-limit
            placeholder="例如：质量看板"
            data-testid="custom-menu-name"
          />
        </label>
        <label>
          <span>图标</span>
          <el-select v-model="draftIcon" data-testid="custom-menu-icon" aria-label="选择菜单图标">
            <el-option
              v-for="option in CUSTOM_MENU_ICON_OPTIONS"
              :key="option.key"
              :label="option.label"
              :value="option.key"
            >
              <div class="ta-custom-menu-icon-option">
                <component :is="customMenuIconComponent(option.key)" aria-hidden="true" />
                <span>{{ option.label }}</span>
              </div>
            </el-option>
          </el-select>
        </label>
        <label class="ta-custom-menu-form__url">
          <span>URL</span>
          <el-input
            v-model="draftUrl"
            placeholder="https://example.com 或 /internal-page"
            data-testid="custom-menu-url"
            @keydown.enter.prevent="submitMenu"
          />
        </label>
      </div>
      <p class="ta-custom-menu-form__hint">支持 HTTP(S) 地址或以 / 开头的同源路径；不要在 URL 中填写账号、密码或临时票据。</p>
      <p v-if="formError" class="ta-custom-menu-form__error" role="alert">{{ formError }}</p>
      <div class="ta-custom-menu-form__actions">
        <el-button v-if="editingId" @click="resetDraft">取消编辑</el-button>
        <el-button type="primary" :disabled="limitReached" data-testid="custom-menu-submit" @click="submitMenu">
          <Plus v-if="!editingId" aria-hidden="true" />
          {{ submitLabel }}
        </el-button>
      </div>
    </section>

    <div class="ta-custom-menu-list__header">
      <strong>已配置菜单</strong>
      <span>{{ customMenus.length }} / {{ CUSTOM_MENU_LIMIT }}</span>
    </div>
    <div v-if="customMenus.length === 0" class="ta-custom-menu-empty">
      暂无自定义菜单，填写上方信息后即可添加。
    </div>
    <ul v-else class="ta-custom-menu-list" aria-label="已配置自定义菜单">
      <li v-for="(item, index) in customMenus" :key="item.id" class="ta-custom-menu-item">
        <component :is="customMenuIconComponent(item.icon)" class="ta-custom-menu-item__icon" aria-hidden="true" />
        <div class="ta-custom-menu-item__content">
          <strong>{{ item.name }}</strong>
          <span :title="item.url">{{ item.url }}</span>
        </div>
        <div v-if="pendingDeleteId === item.id" class="ta-custom-menu-item__confirm" role="group" :aria-label="`确认删除 ${item.name}`">
          <span>确认删除？</span>
          <button type="button" @click="confirmDelete(item.id)">删除</button>
          <button type="button" @click="pendingDeleteId = null">取消</button>
        </div>
        <div v-else class="ta-custom-menu-item__actions">
          <button type="button" :disabled="index === 0" :aria-label="`上移 ${item.name}`" @click="moveMenu(index, -1)"><ChevronUp /></button>
          <button type="button" :disabled="index === customMenus.length - 1" :aria-label="`下移 ${item.name}`" @click="moveMenu(index, 1)"><ChevronDown /></button>
          <button type="button" :aria-label="`编辑 ${item.name}`" @click="editMenu(item)"><Pencil /></button>
          <button type="button" :aria-label="`删除 ${item.name}`" @click="pendingDeleteId = item.id"><Trash2 /></button>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.ta-custom-menu-settings { display: flex; flex-direction: column; gap: 16px; }
.ta-custom-menu-settings__intro { margin: 0; color: #606266; font-size: 13px; line-height: 1.6; }
.ta-custom-menu-form { padding: 16px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fafbfc; }
.ta-custom-menu-form__grid { display: grid; grid-template-columns: minmax(180px, 1fr) 150px; gap: 14px; }
.ta-custom-menu-form label { display: flex; min-width: 0; flex-direction: column; gap: 7px; color: #303133; font-size: 13px; font-weight: 500; }
.ta-custom-menu-form__url { grid-column: 1 / -1; }
.ta-custom-menu-icon-option { display: flex; align-items: center; gap: 8px; }
.ta-custom-menu-icon-option svg { width: 16px; height: 16px; }
.ta-custom-menu-form__hint { margin: 10px 0 0; color: #909399; font-size: 12px; line-height: 1.5; }
.ta-custom-menu-form__error { margin: 8px 0 0; color: #c45656; font-size: 12px; }
.ta-custom-menu-form__actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 14px; }
.ta-custom-menu-form__actions svg { width: 15px; height: 15px; margin-right: 5px; }
.ta-custom-menu-list__header { display: flex; align-items: center; justify-content: space-between; color: #303133; font-size: 13px; }
.ta-custom-menu-list__header span { color: #909399; font-size: 12px; }
.ta-custom-menu-empty { padding: 28px 16px; border: 1px dashed #dcdfe6; border-radius: 8px; color: #909399; text-align: center; font-size: 13px; }
.ta-custom-menu-list { display: flex; flex-direction: column; gap: 8px; margin: 0; padding: 0; list-style: none; }
.ta-custom-menu-item { display: flex; min-height: 54px; align-items: center; gap: 12px; padding: 9px 10px 9px 12px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
.ta-custom-menu-item__icon { width: 18px; height: 18px; flex: 0 0 auto; color: #6d5bb6; }
.ta-custom-menu-item__content { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 4px; }
.ta-custom-menu-item__content strong { overflow: hidden; color: #303133; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.ta-custom-menu-item__content span { overflow: hidden; color: #909399; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.ta-custom-menu-item__actions { display: flex; align-items: center; gap: 2px; }
.ta-custom-menu-item__actions button,
.ta-custom-menu-item__confirm button { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border: 0; border-radius: 6px; background: transparent; color: #606266; cursor: pointer; }
.ta-custom-menu-item__actions button:hover:not(:disabled),
.ta-custom-menu-item__actions button:focus-visible { background: #f0f2f5; color: #6d5bb6; }
.ta-custom-menu-item__actions button:disabled { cursor: not-allowed; opacity: 0.32; }
.ta-custom-menu-item__actions svg { width: 15px; height: 15px; }
.ta-custom-menu-item__confirm { display: flex; align-items: center; gap: 4px; color: #c45656; font-size: 12px; }
.ta-custom-menu-item__confirm button { width: auto; padding: 0 7px; }
.ta-custom-menu-item__confirm button:first-of-type { background: #fef0f0; color: #c45656; }
@media (max-width: 720px) { .ta-custom-menu-form__grid { grid-template-columns: 1fr; } .ta-custom-menu-form__url { grid-column: auto; } }
</style>
