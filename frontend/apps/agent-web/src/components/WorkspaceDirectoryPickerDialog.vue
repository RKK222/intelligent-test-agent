<script setup lang="ts">
import type { WorkspaceDirectoryList } from "@test-agent/shared-types";
import { ChevronLeft, Folder } from "lucide-vue-next";
import { Button } from "@test-agent/ui-kit";

const props = defineProps<{
  open: boolean;
  directory: WorkspaceDirectoryList | null;
  loading: boolean;
}>();

const emit = defineEmits<{
  close: [];
  navigate: [path: string];
  select: [path: string];
}>();
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6">
      <section
        role="dialog"
        aria-modal="true"
        aria-label="选择工作区目录"
        class="flex max-h-[min(640px,calc(100vh-48px))] w-[min(620px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl"
      >
        <header class="flex h-12 shrink-0 items-center justify-between border-b border-[var(--ta-border)] px-4">
          <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">选择工作区目录</h2>
          <Button variant="ghost" size="sm" @click="emit('close')">取消</Button>
        </header>

        <div class="border-b border-[var(--ta-border)] px-4 py-3">
          <div class="truncate text-[12px] text-[var(--ta-muted)]" :title="directory?.path ?? ''">
            {{ directory?.path ?? "正在加载目录" }}
          </div>
          <div class="mt-2 flex items-center gap-2">
            <Button
              size="sm"
              variant="secondary"
              :disabled="loading || !directory?.parentPath"
              @click="directory?.parentPath && emit('navigate', directory.parentPath)"
            >
              <ChevronLeft class="mr-1 h-3.5 w-3.5" />
              返回上级
            </Button>
            <Button
              size="sm"
              :disabled="loading || !directory"
              @click="directory && emit('select', directory.path)"
            >
              选择此目录
            </Button>
          </div>
        </div>

        <div class="min-h-[240px] flex-1 overflow-auto p-2">
          <div v-if="loading" class="px-2 py-4 text-[13px] text-[var(--ta-muted)]">正在加载目录</div>
          <div v-else-if="!directory?.entries.length" class="px-2 py-4 text-[13px] text-[var(--ta-muted)]">没有可进入的子目录</div>
          <button
            v-for="entry in props.directory?.entries ?? []"
            :key="entry.path"
            type="button"
            class="flex h-9 w-full items-center gap-2 rounded px-2 text-left text-[13px] text-[var(--ta-text)] hover:bg-[var(--ta-hover)]"
            @click="emit('navigate', entry.path)"
          >
            <Folder class="h-4 w-4 shrink-0 text-[var(--ta-muted)]" />
            <span class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>
