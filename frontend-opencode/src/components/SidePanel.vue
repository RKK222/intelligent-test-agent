<script setup lang="ts">
import { ref } from "vue";
import { FileDiff, FolderTree, RadioTower, TerminalSquare } from "lucide-vue-next";
import DiffReviewPanel from "@/components/DiffReviewPanel.vue";
import FileTreePanel from "@/components/FileTreePanel.vue";
import TerminalPanel from "@/components/TerminalPanel.vue";
import { useSessionStore } from "@/stores/session";
import { useTerminalStore } from "@/stores/terminal";

const session = useSessionStore();
const terminal = useTerminalStore();
const tab = ref<"review" | "files" | "terminal" | "status">("review");
const emit = defineEmits<{
  close: [];
}>();

function openTerminal() {
  if (session.activeSession?.sessionId) {
    void terminal.open(session.activeSession.sessionId, { cols: 100, rows: 30 });
  }
}
</script>

<template>
  <aside class="side-panel" aria-label="Session side panel">
    <div class="mobile-panel-header">
      <strong>Panel</strong>
      <button class="icon-text" type="button" aria-label="Close session panel" @click="emit('close')">Close</button>
    </div>
    <div class="side-tabs" role="tablist">
      <button type="button" :class="{ active: tab === 'review' }" @click="tab = 'review'"><FileDiff :size="15" />Review</button>
      <button type="button" :class="{ active: tab === 'files' }" @click="tab = 'files'"><FolderTree :size="15" />Files</button>
      <button type="button" :class="{ active: tab === 'terminal' }" @click="tab = 'terminal'; openTerminal()">
        <TerminalSquare :size="15" />Terminal
      </button>
      <button type="button" :class="{ active: tab === 'status' }" @click="tab = 'status'"><RadioTower :size="15" />Status</button>
    </div>

    <section v-if="tab === 'review'" class="panel-section">
      <DiffReviewPanel :files="session.diff?.files ?? []" />
    </section>

    <section v-else-if="tab === 'files'" class="panel-section">
      <FileTreePanel />
    </section>

    <section v-else-if="tab === 'terminal'" class="panel-section terminal-panel">
      <div class="section-label">PTY terminal</div>
      <TerminalPanel @reconnect="openTerminal" />
    </section>

    <section v-else class="panel-section">
      <div class="section-label">Runtime requests</div>
      <div class="catalog-row"><span>Permissions</span><small>{{ session.permissions.length }}</small></div>
      <div class="catalog-row"><span>Questions</span><small>{{ session.questions.length }}</small></div>
      <div class="catalog-row"><span>Run</span><small>{{ session.activeRun?.status ?? "idle" }}</small></div>
    </section>
  </aside>
</template>
