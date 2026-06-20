<script lang="ts">
export type WorkbenchShellProps = {
  workspaceName: string;
  branchName: string;
  runStatus?: string;
  bottomOpen?: boolean;
  bottomHeight?: number;
};
</script>

<script setup lang="ts">
import { provide, ref, useSlots } from "vue";
import { DockviewVue, type DockviewReadyEvent, type VueComponent } from "dockview-vue";
import { Hexagon, UserRound } from "lucide-vue-next";
import { Badge, cn } from "@test-agent/ui-kit";
import { DockPanel } from "./DockPanel";

withDefaults(defineProps<WorkbenchShellProps>(), { runStatus: "IDLE", bottomOpen: false, bottomHeight: 180 });

// 把 WorkbenchShell 的插槽通过 provide 透传给 DockPanel，使其按 slot 渲染对应内容
const slots = useSlots();
provide("workbench-slots", slots);

const components = { panel: DockPanel } as unknown as Record<string, VueComponent>;
const initialized = ref(false);

function onReady(event: DockviewReadyEvent) {
  if (initialized.value) {
    return;
  }
  initialized.value = true;
  event.api.addPanel({ id: "editor", title: "编辑器", component: "panel", params: { slot: "center" } });
  event.api.addPanel({
    id: "workspace",
    title: "工作空间",
    component: "panel",
    params: { slot: "left" },
    position: { referencePanel: "editor", direction: "left" },
    initialWidth: 196
  });
  event.api.addPanel({
    id: "agent",
    title: "Agent",
    component: "panel",
    params: { slot: "right" },
    position: { referencePanel: "editor", direction: "right" },
    initialWidth: 245
  });
}
</script>

<template>
  <div
    class="min-h-0 bg-[var(--ta-bg)] text-[var(--ta-text)]"
    :style="{ display: 'grid', gridTemplateRows: '40px minmax(0, 1fr)', height: '100vh' }"
  >
    <header class="flex items-center justify-between border-b border-[var(--ta-border)] bg-[var(--ta-chrome)] px-4">
      <div class="flex min-w-0 items-center gap-3">
        <div class="flex items-center gap-2" aria-label="TestAgent IDE">
          <Hexagon class="h-[18px] w-[18px] text-[var(--ta-text)]" stroke-width="1.8" />
          <span class="text-[14px] font-semibold leading-5 tracking-[0.01em]">TestAgent IDE</span>
        </div>
        <div class="hidden min-w-0 items-center gap-2 md:flex" aria-label="工作区状态">
          <Badge tone="info">{{ workspaceName }}</Badge>
          <span class="text-[12px] text-[var(--ta-muted)]">{{ branchName }}</span>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <span
          :class="cn(
            'hidden items-center rounded-full border border-[var(--ta-border)] bg-[var(--ta-control)] px-2 py-0.5 text-[11px] text-[var(--ta-muted)] md:flex',
            runStatus === 'RUNNING' && 'border-[var(--ta-accent)] text-[var(--ta-accent)]',
            runStatus === 'FAILED' && 'border-[var(--ta-error)] text-[var(--ta-error)]',
            runStatus === 'SUCCEEDED' && 'border-[var(--ta-ok)] text-[var(--ta-ok)]'
          )"
        >
          {{ runStatus }}
        </span>
        <span class="flex h-7 w-7 items-center justify-center rounded-full border border-[var(--ta-border)] bg-[var(--ta-control-strong)] text-[var(--ta-muted)]">
          <UserRound class="h-3.5 w-3.5" />
        </span>
      </div>
    </header>
    <main class="flex min-h-0 overflow-hidden" :style="{ height: '100%' }">
      <aside class="w-12 shrink-0 min-h-0 border-r border-[var(--ta-border)] bg-[var(--ta-chrome)]">
        <slot name="activity" />
      </aside>
      <section class="relative min-w-0 flex-1 overflow-hidden">
        <DockviewVue class="h-full w-full" :components="components" @ready="onReady" />
        <div
          v-if="bottomOpen"
          class="absolute inset-x-0 bottom-0 z-20 border-t border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-[0_-12px_28px_rgba(17,24,39,0.08)]"
          :style="{ height: `${bottomHeight}px` }"
          role="region"
          aria-label="运行与终端"
        >
          <slot name="bottom" />
        </div>
      </section>
    </main>
  </div>
</template>
