<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { Bot, CheckCircle2, CircleDashed, FolderOpen, Pin, Server, TerminalSquare } from "lucide-vue-next";
import { useWorkspaceStore } from "@/stores/workspace";

const router = useRouter();
const workspace = useWorkspaceStore();

const activeWorkspace = computed(() => workspace.selectedWorkspace ?? workspace.workspaces[0]);

function openSession(sessionId: string, workspaceId?: string) {
  router.push(`/w/${workspaceId ?? activeWorkspace.value?.workspaceId ?? "default"}/session/${sessionId}`);
}
</script>

<template>
  <main class="home-grid">
    <aside class="workspace-rail" aria-label="Workspaces">
      <div class="rail-section">
        <div class="section-label">Projects</div>
        <button
          v-for="item in workspace.workspaces"
          :key="item.workspaceId"
          class="workspace-row"
          :class="{ active: item.workspaceId === workspace.selectedWorkspaceId }"
          type="button"
          @click="workspace.selectWorkspace(item.workspaceId)"
        >
          <FolderOpen :size="16" aria-hidden="true" />
          <span>
            <strong>{{ item.name }}</strong>
            <small>{{ item.rootPath }}</small>
          </span>
        </button>
        <div v-if="!workspace.workspaces.length" class="empty-note">No platform workspaces yet.</div>
      </div>
      <div class="rail-section status-stack">
        <div class="section-label">Runtime</div>
        <span><Server :size="14" />Backend API</span>
        <span><TerminalSquare :size="14" />RunEvent SSE</span>
        <span><CheckCircle2 :size="14" />No direct opencode browser link</span>
      </div>
    </aside>

    <section class="home-main" aria-label="Sessions">
      <div class="pane-heading">
        <div>
          <p class="eyebrow">Workspace</p>
          <h1>{{ activeWorkspace?.name ?? "opencode workspace" }}</h1>
          <p>{{ activeWorkspace?.rootPath ?? "Select or create a workspace to begin." }}</p>
        </div>
        <button class="primary-action" type="button" aria-label="Create workspace session" @click="router.push('/new-session')">
          New session
        </button>
      </div>

      <div v-if="workspace.error" class="inline-alert">{{ workspace.error }}</div>
      <div class="session-list">
        <button
          v-for="session in workspace.filteredSessions"
          :key="session.sessionId"
          class="session-row"
          type="button"
          @click="openSession(session.sessionId, session.workspaceId)"
        >
          <span class="session-status"><CircleDashed :size="15" /></span>
          <span class="session-copy">
            <strong>{{ session.title }}</strong>
            <small>{{ session.status }} · {{ session.updatedAt }}</small>
          </span>
          <Pin v-if="session.pinned" :size="14" aria-label="Pinned" />
        </button>
        <div v-if="!workspace.filteredSessions.length" class="empty-state">
          <Bot :size="28" aria-hidden="true" />
          <strong>No sessions in view</strong>
          <span>Start a new session or adjust the search query.</span>
        </div>
      </div>
    </section>

    <aside class="home-side" aria-label="Catalog">
      <section class="catalog-panel">
        <div class="section-label">Agents</div>
        <div v-for="agent in workspace.agents" :key="agent.agentId" class="catalog-row">
          <span>{{ agent.name }}</span>
          <small>{{ agent.mode ?? "chat" }}</small>
        </div>
        <div v-if="!workspace.agents.length" class="empty-note">Agent catalog loads from backend API.</div>
      </section>
      <section class="catalog-panel">
        <div class="section-label">Models</div>
        <div v-for="model in workspace.models.slice(0, 8)" :key="`${model.providerId}:${model.id}`" class="catalog-row">
          <span>{{ model.name }}</span>
          <small>{{ model.providerId ?? "provider" }}</small>
        </div>
        <div v-if="!workspace.models.length" class="empty-note">Model list follows opencode provider data.</div>
      </section>
    </aside>
  </main>
</template>
