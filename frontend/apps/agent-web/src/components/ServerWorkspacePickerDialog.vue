<script setup lang="ts">
import { computed } from "vue";
import type { WorkspaceBackendServer, WorkspaceDirectoryList } from "@test-agent/shared-types";
import { AlertTriangle, ChevronLeft, Folder, Server } from "lucide-vue-next";
import { Button } from "@test-agent/ui-kit";

const props = defineProps<{
  open: boolean;
  servers: WorkspaceBackendServer[];
  selectedServerId?: string;
  directory: WorkspaceDirectoryList | null;
  loading: boolean;
  currentAgentLinuxServerId?: string;
}>();

const emit = defineEmits<{
  close: [];
  selectServer: [server: WorkspaceBackendServer];
  navigate: [path: string];
  select: [payload: { server: WorkspaceBackendServer; path: string }];
}>();

const selectedServer = computed(() => props.servers.find((server) => server.linuxServerId === props.selectedServerId));
const serverMismatch = computed(
  () => Boolean(selectedServer.value && props.currentAgentLinuxServerId && selectedServer.value.linuxServerId !== props.currentAgentLinuxServerId)
);
const disabledReason = computed(() => (serverMismatch.value ? "工作空间与 agent 不在同一服务器" : ""));
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[1000] flex items-center justify-center bg-black/35 px-4 py-6">
      <section
        role="dialog"
        aria-modal="true"
        aria-label="选择服务器工作空间"
        class="flex max-h-[min(700px,calc(100vh-48px))] w-[min(880px,calc(100vw-24px))] flex-col rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-xl"
      >
        <header class="flex h-12 shrink-0 items-center justify-between border-b border-[var(--ta-border)] px-4">
          <h2 class="text-[14px] font-semibold text-[var(--ta-text)]">选择服务器工作空间</h2>
          <Button variant="ghost" size="sm" @click="emit('close')">取消</Button>
        </header>

        <div class="grid min-h-0 flex-1 grid-cols-[260px_minmax(0,1fr)]">
          <aside class="min-h-0 border-r border-[var(--ta-border)] p-2">
            <div class="px-2 pb-2 text-[12px] text-[var(--ta-muted)]">后端服务器</div>
            <button
              v-for="server in servers"
              :key="server.linuxServerId"
              type="button"
              :class="[
                'flex h-12 w-full items-center gap-2 rounded px-2 text-left text-[13px] hover:bg-[var(--ta-hover)]',
                server.linuxServerId === selectedServerId && 'bg-[var(--ta-hover)]'
              ]"
              @click="emit('selectServer', server)"
            >
              <Server class="h-4 w-4 shrink-0 text-[var(--ta-muted)]" />
              <span class="min-w-0 flex-1">
                <span class="block truncate text-[var(--ta-text)]">{{ server.name || server.linuxServerId }}</span>
                <span class="block truncate text-[11px] text-[var(--ta-muted)]">{{ server.baseUrl }}</span>
              </span>
            </button>
            <div v-if="!servers.length" class="px-2 py-4 text-[13px] text-[var(--ta-muted)]">暂无可用后端服务器</div>
          </aside>

          <main class="min-h-0 p-3">
            <div class="mb-3 rounded border border-[var(--ta-border)] bg-[var(--ta-surface)] px-3 py-2">
              <div class="truncate text-[12px] text-[var(--ta-muted)]" :title="directory?.path ?? selectedServer?.defaultDirectory ?? ''">
                {{ directory?.path ?? selectedServer?.defaultDirectory ?? "请选择服务器" }}
              </div>
              <div v-if="disabledReason" class="mt-2 flex items-center gap-2 text-[12px] text-red-600">
                <AlertTriangle class="h-3.5 w-3.5" />
                <span>{{ disabledReason }}</span>
              </div>
              <div class="mt-2 flex items-center gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  :disabled="loading || serverMismatch || !directory?.parentPath"
                  @click="directory?.parentPath && emit('navigate', directory.parentPath)"
                >
                  <ChevronLeft class="mr-1 h-3.5 w-3.5" />
                  返回上级
                </Button>
                <Button
                  size="sm"
                  :disabled="loading || serverMismatch || !directory || !selectedServer"
                  @click="directory && selectedServer && emit('select', { server: selectedServer, path: directory.path })"
                >
                  选择此目录
                </Button>
              </div>
            </div>

            <div class="min-h-[280px] overflow-auto rounded border border-[var(--ta-border)] p-2">
              <div v-if="loading" class="px-2 py-4 text-[13px] text-[var(--ta-muted)]">正在加载目录</div>
              <div v-else-if="serverMismatch" class="px-2 py-4 text-[13px] text-[var(--ta-muted)]">请选择与当前 agent 相同的服务器后继续。</div>
              <div v-else-if="!directory?.entries.length" class="px-2 py-4 text-[13px] text-[var(--ta-muted)]">没有可进入的子目录</div>
              <button
                v-for="entry in directory?.entries ?? []"
                :key="entry.path"
                type="button"
                class="flex h-9 w-full items-center gap-2 rounded px-2 text-left text-[13px] text-[var(--ta-text)] hover:bg-[var(--ta-hover)] disabled:cursor-not-allowed disabled:opacity-60"
                :disabled="serverMismatch"
                @click="emit('navigate', entry.path)"
              >
                <Folder class="h-4 w-4 shrink-0 text-[var(--ta-muted)]" />
                <span class="min-w-0 flex-1 truncate">{{ entry.name }}</span>
              </button>
            </div>
          </main>
        </div>
      </section>
    </div>
  </Teleport>
</template>
