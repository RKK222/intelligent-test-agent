<script lang="ts">
export type WorkbenchShellProps = {
  workspaceName: string;
  branchName: string;
  runStatus?: string;
};
</script>

<script setup lang="ts">
import { provide, ref, useSlots } from "vue";
import { DockviewVue, type DockviewReadyEvent, type VueComponent } from "dockview-vue";
import { Activity, GitBranch, PlayCircle } from "lucide-vue-next";
import { Badge, cn } from "@test-agent/ui-kit";
import { DockPanel } from "./DockPanel";

withDefaults(defineProps<WorkbenchShellProps>(), { runStatus: "IDLE" });

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
    initialWidth: 280
  });
  event.api.addPanel({
    id: "agent",
    title: "Agent",
    component: "panel",
    params: { slot: "right" },
    position: { referencePanel: "editor", direction: "right" },
    initialWidth: 420
  });
  event.api.addPanel({
    id: "run",
    title: "运行",
    component: "panel",
    params: { slot: "bottom" },
    position: { referencePanel: "editor", direction: "below" },
    initialHeight: 180
  });
}
</script>

<template>
  <div
    class="min-h-0 bg-[var(--ta-bg)] text-[var(--ta-text)]"
    :style="{ display: 'grid', gridTemplateRows: '44px minmax(0, 1fr)', height: '100vh' }"
  >
    <header class="flex items-center justify-between border-b border-[var(--ta-border)] bg-[linear-gradient(180deg,#0f1a33,#0b1220)] px-3">
      <div class="flex min-w-0 items-center gap-3">
        <div class="flex items-center gap-2">
          <span class="h-3.5 w-3.5 rounded-[4px] bg-[conic-gradient(from_0deg,#60a5fa,#22d3ee,#a78bfa,#60a5fa)] shadow-[0_0_0_2px_rgba(96,165,250,.15)]" />
          <span class="font-semibold">TestAgent IDE</span>
        </div>
        <div class="hidden min-w-0 items-center gap-2 md:flex">
          <Badge tone="info">{{ workspaceName }}</Badge>
          <span class="flex items-center gap-1 text-[12px] text-slate-400">
            <GitBranch class="h-3.5 w-3.5" />
            {{ branchName }}
          </span>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <span
          :class="cn(
            'flex items-center gap-1 rounded-full border border-[var(--ta-border)] bg-[#101b33] px-2 py-1 text-[12px] text-[var(--ta-muted)]',
            runStatus === 'RUNNING' && 'border-[#1b2d5a] text-[#8db6f5] shadow-[0_0_10px_rgba(96,165,250,.4)]',
            runStatus === 'FAILED' && 'border-[#3b0d0d] text-[#fca5a5]',
            runStatus === 'SUCCEEDED' && 'border-[#0b2a1a] text-[#86efac]'
          )"
        >
          <Activity v-if="runStatus === 'RUNNING'" class="h-3.5 w-3.5 animate-[ta-pulse_1.2s_infinite]" />
          <PlayCircle v-else class="h-3.5 w-3.5" />
          {{ runStatus }}
        </span>
      </div>
    </header>
    <main class="relative min-h-0 overflow-hidden" :style="{ height: '100%' }">
      <DockviewVue class="h-full w-full" :components="components" @ready="onReady" />
    </main>
  </div>
</template>
