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
    <div class="side-tabs" role="tablist" aria-label="Session panel tabs">
      <button
        id="panel-tab-review"
        type="button"
        role="tab"
        aria-controls="panel-review"
        :aria-selected="tab === 'review' ? 'true' : 'false'"
        :class="{ active: tab === 'review' }"
        @click="tab = 'review'"
      >
        <FileDiff :size="15" />Review
      </button>
      <button
        id="panel-tab-files"
        type="button"
        role="tab"
        aria-controls="panel-files"
        :aria-selected="tab === 'files' ? 'true' : 'false'"
        :class="{ active: tab === 'files' }"
        @click="tab = 'files'"
      >
        <FolderTree :size="15" />Files
      </button>
      <button
        id="panel-tab-terminal"
        type="button"
        role="tab"
        aria-controls="panel-terminal"
        :aria-selected="tab === 'terminal' ? 'true' : 'false'"
        :class="{ active: tab === 'terminal' }"
        @click="tab = 'terminal'; openTerminal()"
      >
        <TerminalSquare :size="15" />Terminal
      </button>
      <button
        id="panel-tab-status"
        type="button"
        role="tab"
        aria-controls="panel-status"
        :aria-selected="tab === 'status' ? 'true' : 'false'"
        :class="{ active: tab === 'status' }"
        @click="tab = 'status'"
      >
        <RadioTower :size="15" />Status
      </button>
    </div>

    <section v-if="tab === 'review'" id="panel-review" class="panel-section" role="tabpanel" aria-label="Review" aria-labelledby="panel-tab-review">
      <DiffReviewPanel :files="session.diff?.files ?? []" />
    </section>

    <section v-else-if="tab === 'files'" id="panel-files" class="panel-section" role="tabpanel" aria-label="Files" aria-labelledby="panel-tab-files">
      <FileTreePanel />
    </section>

    <section
      v-else-if="tab === 'terminal'"
      id="panel-terminal"
      class="panel-section terminal-panel"
      role="tabpanel"
      aria-label="Terminal"
      aria-labelledby="panel-tab-terminal"
    >
      <div class="section-label">PTY terminal</div>
      <TerminalPanel @reconnect="openTerminal" />
    </section>

    <section v-else id="panel-status" class="panel-section" role="tabpanel" aria-label="Status" aria-labelledby="panel-tab-status">
      <div class="section-label">Runtime requests</div>
      <div class="catalog-row"><span>Permissions</span><small>{{ session.permissions.length }}</small></div>
      <div class="catalog-row"><span>Questions</span><small>{{ session.questions.length }}</small></div>
      <div class="catalog-row"><span>Run</span><small>{{ session.activeRun?.status ?? "idle" }}</small></div>
    </section>
  </aside>
</template>
