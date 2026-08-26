<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ExternalLink, RefreshCw } from "lucide-vue-next";
import type { CustomMenuItem } from "./custom-menus";

const props = defineProps<{
  menu: CustomMenuItem;
  pageActive: boolean;
}>();

const frameRevision = ref(0);
const loading = ref(true);
const frameKey = computed(() => `${props.menu.id}:${props.menu.url}:${frameRevision.value}`);

watch(() => props.menu.url, () => {
  loading.value = true;
});

watch(() => props.pageActive, (active) => {
  if (active) loading.value = true;
});

function reload() {
  loading.value = true;
  frameRevision.value += 1;
}

function openInNewTab() {
  window.open(props.menu.url, "_blank", "noopener,noreferrer");
}
</script>

<template>
  <div class="ta-custom-menu-page">
    <header class="ta-custom-menu-page__toolbar">
      <span class="ta-custom-menu-page__url" :title="menu.url">{{ menu.url }}</span>
      <span v-if="loading && pageActive" class="ta-custom-menu-page__status" role="status">正在加载</span>
      <button type="button" :aria-label="`刷新 ${menu.name}`" title="刷新" @click="reload"><RefreshCw /></button>
      <button type="button" :aria-label="`在新窗口打开 ${menu.name}`" title="在新窗口打开" @click="openInNewTab"><ExternalLink /></button>
    </header>
    <iframe
      v-if="pageActive"
      :key="frameKey"
      :src="menu.url"
      :title="menu.name"
      class="ta-custom-menu-page__frame"
      sandbox="allow-downloads allow-forms allow-modals allow-popups allow-same-origin allow-scripts"
      referrerpolicy="strict-origin-when-cross-origin"
      @load="loading = false"
    />
    <div v-else class="ta-custom-menu-page__paused" aria-hidden="true">页面已暂停</div>
  </div>
</template>

<style scoped>
.ta-custom-menu-page { display: flex; width: 100%; height: 100%; min-height: 0; flex-direction: column; background: var(--ta-shell-surface, #fff); }
.ta-custom-menu-page__toolbar { display: flex; height: 34px; flex: 0 0 34px; align-items: center; gap: 6px; padding: 0 8px 0 12px; border-bottom: 1px solid var(--ta-shell-border, #e5e7eb); background: var(--ta-shell-header, #f8fafc); }
.ta-custom-menu-page__url { min-width: 0; flex: 1; overflow: hidden; color: var(--ta-shell-muted, #6b7280); font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.ta-custom-menu-page__status { color: var(--ta-shell-muted, #6b7280); font-size: 11px; white-space: nowrap; }
.ta-custom-menu-page__toolbar button { display: inline-flex; width: 26px; height: 26px; align-items: center; justify-content: center; border: 0; border-radius: 6px; background: transparent; color: var(--ta-shell-muted, #6b7280); cursor: pointer; }
.ta-custom-menu-page__toolbar button:hover,
.ta-custom-menu-page__toolbar button:focus-visible { background: var(--ta-shell-hover, #f3f4f6); color: var(--ta-shell-text, #1f2937); }
.ta-custom-menu-page__toolbar svg { width: 15px; height: 15px; }
.ta-custom-menu-page__frame { width: 100%; min-height: 0; flex: 1; border: 0; background: #fff; }
.ta-custom-menu-page__paused { display: none; }
</style>
