import { defineStore } from "pinia";
import { computed, ref } from "vue";
import type { AgentInfo, CommandInfo, ModelInfo, PageResponse, ProviderInfo, Session, Workspace } from "@test-agent/shared-types";
import { usePlatformStore } from "@/stores/platform";

export const useWorkspaceStore = defineStore("workspace", () => {
  const workspaces = ref<Workspace[]>([]);
  const sessions = ref<Session[]>([]);
  const agents = ref<AgentInfo[]>([]);
  const models = ref<ModelInfo[]>([]);
  const providers = ref<ProviderInfo[]>([]);
  const commands = ref<CommandInfo[]>([]);
  const selectedWorkspaceId = ref<string>();
  const query = ref("");
  const loading = ref(false);
  const error = ref<string>();

  const selectedWorkspace = computed(() => workspaces.value.find((item) => item.workspaceId === selectedWorkspaceId.value));
  const filteredSessions = computed(() => {
    const term = query.value.trim().toLowerCase();
    if (!term) {
      return sessions.value;
    }
    return sessions.value.filter((session) => `${session.title} ${session.status}`.toLowerCase().includes(term));
  });

  async function loadHome() {
    const platform = usePlatformStore();
    loading.value = true;
    error.value = undefined;
    try {
      const [workspacePage, sessionPage, agentList, modelList, providerList] = await Promise.all([
        platform.api.listWorkspaces(1, 50),
        platform.api.listAllSessions(1, 50, query.value || undefined),
        platform.api.listAgents(selectedWorkspaceId.value),
        platform.api.listModels(selectedWorkspaceId.value),
        platform.api.listProviders(selectedWorkspaceId.value)
      ]);
      workspaces.value = pageItems(workspacePage);
      sessions.value = pageItems(sessionPage);
      agents.value = agentList;
      models.value = modelList;
      providers.value = providerList;
      selectedWorkspaceId.value ??= workspaces.value[0]?.workspaceId;
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : "工作区加载失败";
    } finally {
      loading.value = false;
    }
  }

  async function loadCommands() {
    const platform = usePlatformStore();
    try {
      commands.value = await platform.api.listCommands(selectedWorkspaceId.value);
    } catch {
      commands.value = [];
    }
  }

  function selectWorkspace(workspaceId: string) {
    selectedWorkspaceId.value = workspaceId;
  }

  return {
    workspaces,
    sessions,
    agents,
    models,
    providers,
    commands,
    selectedWorkspaceId,
    selectedWorkspace,
    filteredSessions,
    query,
    loading,
    error,
    loadHome,
    loadCommands,
    selectWorkspace
  };
});

function pageItems<T>(page: PageResponse<T>) {
  return Array.isArray(page.items) ? page.items : [];
}
