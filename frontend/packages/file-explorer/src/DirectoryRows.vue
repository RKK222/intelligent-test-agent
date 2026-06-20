<script lang="ts">
import type { FileTreeEntry } from "@test-agent/shared-types";

export type DirectoryRowsProps = {
  directory: string;
  entriesByDirectory: Record<string, FileTreeEntry[]>;
  expandedDirectories: Set<string>;
  activePath?: string;
  loadingPath?: string | null;
  depth?: number;
};
</script>

<script setup lang="ts">
import { computed } from "vue";
import { ChevronRight, FileText, Folder } from "lucide-vue-next";
import { cn } from "@test-agent/ui-kit";

const props = withDefaults(defineProps<DirectoryRowsProps>(), { depth: 0 });
const emit = defineEmits<{
  toggleDirectory: [path: string];
  openFile: [path: string];
}>();

const entries = computed(() => props.entriesByDirectory[props.directory] ?? []);

function onRowClick(entry: FileTreeEntry) {
  if (entry.type === "directory") {
    emit("toggleDirectory", entry.path);
  } else {
    emit("openFile", entry.path);
  }
}
</script>

<template>
  <div>
    <div v-for="entry in entries" :key="entry.path">
      <button
        type="button"
        :class="cn(
          'flex h-7 w-full items-center gap-1 rounded px-1 text-left text-[14px] leading-5 text-[var(--ta-subtle)] hover:bg-[var(--ta-hover)]',
          activePath === entry.path && 'bg-[var(--ta-active)] text-[var(--ta-ink)]'
        )"
        :style="{ paddingLeft: depth * 14 + 4 + 'px' }"
        @click="onRowClick(entry)"
      >
        <template v-if="entry.type === 'directory'">
          <ChevronRight :class="cn('h-3.5 w-3.5 text-[var(--ta-muted)] transition', expandedDirectories.has(entry.path) && 'rotate-90')" />
          <Folder class="h-4 w-4 text-[var(--ta-muted)]" />
        </template>
        <template v-else>
          <span class="w-3.5" />
          <FileText class="h-4 w-4 text-[var(--ta-muted)]" />
        </template>
        <span class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
        <span v-if="loadingPath === entry.path" class="text-[10px] text-[var(--ta-muted)]">...</span>
      </button>
      <DirectoryRows
        v-if="entry.type === 'directory' && expandedDirectories.has(entry.path)"
        :directory="entry.path"
        :entries-by-directory="entriesByDirectory"
        :expanded-directories="expandedDirectories"
        :active-path="activePath"
        :loading-path="loadingPath"
        :depth="depth + 1"
        @toggle-directory="emit('toggleDirectory', $event)"
        @open-file="emit('openFile', $event)"
      />
    </div>
  </div>
</template>
