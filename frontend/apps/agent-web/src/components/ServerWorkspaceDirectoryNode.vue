<script setup lang="ts">
import { ref, inject } from "vue";
import type { WorkspaceBackendServer, WorkspaceDirectoryEntry } from "@test-agent/shared-types";
import { ChevronRight, Folder, Loader2 } from "lucide-vue-next";
import type { BackendApiClient } from "@test-agent/backend-api";

defineOptions({ name: "ServerWorkspaceDirectoryNode" });

const props = defineProps<{
  entry: WorkspaceDirectoryEntry;
  level: number;
  disabled: boolean;
  selectedServer?: WorkspaceBackendServer;
}>();

const emit = defineEmits<{
  navigate: [path: string];
}>();

const api = inject<BackendApiClient>("api")!;
const expanded = ref(false);
const loading = ref(false);
const children = ref<WorkspaceDirectoryEntry[]>([]);
const hasLoaded = ref(false);

async function toggleExpand() {
  if (props.disabled) return;
  expanded.value = !expanded.value;
  if (expanded.value && !hasLoaded.value && props.selectedServer) {
    loading.value = true;
    try {
      const res = await api.listServerWorkspaceDirectories(props.selectedServer, props.entry.path);
      children.value = res.entries;
      hasLoaded.value = true;
    } catch (err) {
      console.error("Failed to load subdirectories", err);
      expanded.value = false;
    } finally {
      loading.value = false;
    }
  }
}
</script>

<template>
  <div class="flex flex-col select-none text-[12px] text-gray-700 w-full">
    <!-- Folder Row -->
    <div
      :class="[
        'flex w-full items-center px-3 py-1 hover:bg-[#e8f2ff] focus-within:bg-[#e8f2ff] transition-colors duration-150',
        disabled && 'opacity-60 cursor-not-allowed'
      ]"
    >
      <!-- Indent spacer -->
      <div :style="{ width: `${level * 16}px` }" class="shrink-0"></div>

      <!-- Expand Button (chevron right) -->
      <button
        type="button"
        class="flex items-center justify-center w-5 h-5 rounded hover:bg-gray-200/50 text-gray-400 shrink-0 focus:outline-none focus:ring-1 focus:ring-blue-400"
        :disabled="disabled"
        @click.stop="toggleExpand"
      >
        <ChevronRight
          class="h-3.5 w-3.5 transition-transform duration-150"
          :class="{ 'rotate-90': expanded }"
        />
      </button>

      <!-- Folder Button (clicking text enters the folder) -->
      <button
        type="button"
        class="flex flex-1 items-center gap-1.5 min-w-0 text-left py-0.5 focus:outline-none focus:underline"
        :disabled="disabled"
        @click="emit('navigate', entry.path)"
      >
        <Folder class="h-4 w-4 shrink-0 text-[#3b82f6] fill-[#3b82f6]/10" />
        <span class="min-w-0 flex-1 truncate text-gray-900 font-medium">{{ entry.name }}</span>
      </button>
    </div>

    <!-- Expanded Subfolders -->
    <div v-if="expanded" class="flex flex-col w-full">
      <!-- Loading indicator -->
      <div v-if="loading" class="flex items-center gap-2 py-1 text-gray-400 font-normal shrink-0">
        <div :style="{ width: `${(level + 1) * 16 + 20}px` }" class="shrink-0"></div>
        <Loader2 class="h-3.5 w-3.5 animate-spin text-[#3b82f6]" />
        <span class="text-[11px]">正在加载...</span>
      </div>
      
      <!-- Empty child state -->
      <div v-else-if="children.length === 0" class="flex items-center gap-2 py-1 text-gray-400 font-normal shrink-0">
        <div :style="{ width: `${(level + 1) * 16 + 20}px` }" class="shrink-0"></div>
        <span class="text-[11px] italic">无子文件夹</span>
      </div>

      <!-- Recursive child list -->
      <template v-else>
        <ServerWorkspaceDirectoryNode
          v-for="child in children"
          :key="child.path"
          :entry="child"
          :level="level + 1"
          :disabled="disabled"
          :selected-server="selectedServer"
          @navigate="(path) => emit('navigate', path)"
        />
      </template>
    </div>
  </div>
</template>
