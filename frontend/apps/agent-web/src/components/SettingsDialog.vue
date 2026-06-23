<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { createBackendApiClient } from "@test-agent/backend-api";
import type {
  ApplicationDefinition,
  ApplicationMember,
  ApplicationWorkspaceConfig,
  CodeRepositoryConfig,
  CurrentUser,
  PlatformUserSummary,
  SshKeyMetadata
} from "@test-agent/shared-types";
import { Button, Input, Textarea } from "@test-agent/ui-kit";
import { KeyRound, Link2, Settings, Trash2, UserPlus, X } from "lucide-vue-next";

const props = defineProps<{
  open: boolean;
  currentUser: CurrentUser | null;
}>();

const emit = defineEmits<{
  (event: "close"): void;
}>();

const apiBaseUrl = import.meta.env.VITE_TEST_AGENT_API_BASE_URL ?? "http://127.0.0.1:8080";
const api = createBackendApiClient({ baseUrl: apiBaseUrl });

const activeMenu = ref<"apps" | "personal">("personal");
const appTab = ref<"members" | "repositories" | "workspaces">("members");
const loading = ref(false);
const errorMessage = ref("");

const applications = ref<ApplicationDefinition[]>([]);
const selectedAppId = ref("");
const members = ref<ApplicationMember[]>([]);
const userKeyword = ref("");
const users = ref<PlatformUserSummary[]>([]);
const memberUserId = ref("");

const repositories = ref<CodeRepositoryConfig[]>([]);
const appRepositories = ref<CodeRepositoryConfig[]>([]);
const repoGitUrl = ref("");
const repoName = ref("");
const repoStandard = ref(false);
const linkRepositoryId = ref("");
const editRepositoryId = ref("");
const editRepositoryName = ref("");
const editRepositoryStandard = ref(false);
const selectedRepositoryForApps = ref("");
const repositoryApplications = ref<ApplicationDefinition[]>([]);
const linkAppId = ref("");

const workspaces = ref<ApplicationWorkspaceConfig[]>([]);
const workspaceRepositoryId = ref("");
const branches = ref<string[]>([]);
const workspaceBranch = ref("");
const directories = ref<string[]>([]);
const workspaceDirectory = ref("");
const workspaceName = ref("");

const sshKeys = ref<SshKeyMetadata[]>([]);
const sshKeyName = ref("");
const sshPrivateKey = ref("");

const currentRoles = computed(() => props.currentUser?.roles ?? []);
const currentRoleLabel = computed(() => (currentRoles.value.length ? currentRoles.value.join(",") : "无角色"));
const hasAppSettingsPermission = computed(() => currentRoles.value.includes("APP_ADMIN") || currentRoles.value.includes("SUPER_ADMIN"));
const selectedApp = computed(() => applications.value.find((item) => item.appId === selectedAppId.value));

watch(
  () => props.open,
  async (open) => {
    if (!open) {
      return;
    }
    errorMessage.value = "";
    activeMenu.value = "apps";
    await loadSshKeys();
    if (hasAppSettingsPermission.value) {
      await loadApplications();
    } else {
      clearAppContext();
    }
  }
);

watch(selectedAppId, async (appId) => {
  if (!props.open || !appId || !hasAppSettingsPermission.value) {
    return;
  }
  await loadAppContext();
});

async function run(action: () => Promise<void>) {
  loading.value = true;
  errorMessage.value = "";
  try {
    await action();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "操作失败";
  } finally {
    loading.value = false;
  }
}

async function loadApplications() {
  await run(async () => {
    applications.value = await api.listApplications(true);
    if (!selectedAppId.value || !applications.value.some((item) => item.appId === selectedAppId.value)) {
      selectedAppId.value = applications.value[0]?.appId ?? "";
    }
    if (selectedAppId.value) {
      await loadAppContext();
    }
  });
}

function clearAppContext() {
  applications.value = [];
  selectedAppId.value = "";
  members.value = [];
  users.value = [];
  repositories.value = [];
  appRepositories.value = [];
  repositoryApplications.value = [];
  workspaces.value = [];
  branches.value = [];
  directories.value = [];
}

async function loadAppContext() {
  await Promise.all([loadMembers(), loadRepositories(), loadWorkspaces()]);
}

async function loadMembers() {
  if (!selectedAppId.value) {
    members.value = [];
    return;
  }
  members.value = await api.listApplicationMembers(selectedAppId.value);
}

async function searchUsers() {
  await run(async () => {
    const page = await api.searchUsers(userKeyword.value.trim() || undefined, 1, 20);
    users.value = page.items;
  });
}

async function addMember() {
  await run(async () => {
    await api.addApplicationMember(selectedAppId.value, memberUserId.value.trim());
    memberUserId.value = "";
    await loadMembers();
  });
}

async function removeMember(userId: string) {
  await run(async () => {
    await api.removeApplicationMember(selectedAppId.value, userId);
    await loadMembers();
  });
}

async function loadRepositories() {
  const [all, linked] = await Promise.all([
    api.listRepositories(1, 100),
    selectedAppId.value ? api.listApplicationRepositories(selectedAppId.value) : Promise.resolve([])
  ]);
  repositories.value = all.items;
  appRepositories.value = linked;
  if (!workspaceRepositoryId.value || !linked.some((item) => item.repositoryId === workspaceRepositoryId.value)) {
    workspaceRepositoryId.value = linked[0]?.repositoryId ?? "";
  }
  if (!selectedRepositoryForApps.value && all.items[0]) {
    selectedRepositoryForApps.value = all.items[0].repositoryId;
  }
  if (selectedRepositoryForApps.value) {
    repositoryApplications.value = await api.listRepositoryApplications(selectedRepositoryForApps.value);
  }
}

async function createRepository() {
  await run(async () => {
    const repository = await api.createRepository({
      gitUrl: repoGitUrl.value.trim(),
      name: repoName.value.trim(),
      standard: repoStandard.value
    });
    repoGitUrl.value = "";
    repoName.value = "";
    repoStandard.value = false;
    linkRepositoryId.value = repository.repositoryId;
    await loadRepositories();
  });
}

function startEditRepository(repository: CodeRepositoryConfig) {
  editRepositoryId.value = repository.repositoryId;
  editRepositoryName.value = repository.name;
  editRepositoryStandard.value = repository.standard;
}

async function saveRepository() {
  await run(async () => {
    await api.updateRepository(editRepositoryId.value, {
      name: editRepositoryName.value.trim(),
      standard: editRepositoryStandard.value
    });
    editRepositoryId.value = "";
    await loadRepositories();
  });
}

async function linkRepository() {
  await run(async () => {
    await api.linkApplicationRepository(selectedAppId.value, linkRepositoryId.value);
    await loadRepositories();
  });
}

async function unlinkRepository(repositoryId: string) {
  await run(async () => {
    await api.unlinkApplicationRepository(selectedAppId.value, repositoryId);
    await loadRepositories();
    await loadWorkspaces();
  });
}

async function loadRepositoryApplications() {
  if (!selectedRepositoryForApps.value) {
    repositoryApplications.value = [];
    return;
  }
  await run(async () => {
    repositoryApplications.value = await api.listRepositoryApplications(selectedRepositoryForApps.value);
  });
}

async function linkApplication() {
  await run(async () => {
    await api.linkRepositoryApplication(selectedRepositoryForApps.value, linkAppId.value.trim());
    linkAppId.value = "";
    await loadRepositoryApplications();
    await loadRepositories();
  });
}

async function unlinkApplication(appId: string) {
  await run(async () => {
    await api.unlinkRepositoryApplication(selectedRepositoryForApps.value, appId);
    await loadRepositoryApplications();
    await loadRepositories();
  });
}

async function loadWorkspaces() {
  workspaces.value = selectedAppId.value ? await api.listApplicationWorkspaces(selectedAppId.value) : [];
}

async function loadBranches() {
  await run(async () => {
    branches.value = workspaceRepositoryId.value ? await api.listRepositoryBranches(workspaceRepositoryId.value) : [];
    workspaceBranch.value = branches.value[0] ?? "";
    directories.value = [];
    workspaceDirectory.value = "";
  });
}

async function loadDirectories() {
  await run(async () => {
    directories.value =
      workspaceRepositoryId.value && workspaceBranch.value
        ? await api.listRepositoryDirectories(workspaceRepositoryId.value, workspaceBranch.value)
        : [];
    workspaceDirectory.value = directories.value[0] ?? "";
  });
}

async function createWorkspace() {
  await run(async () => {
    await api.createApplicationWorkspace(selectedAppId.value, {
      repositoryId: workspaceRepositoryId.value,
      branch: workspaceBranch.value,
      directoryPath: workspaceDirectory.value,
      workspaceName: workspaceName.value.trim() || undefined
    });
    workspaceName.value = "";
    await loadWorkspaces();
  });
}

async function renameWorkspace(workspace: ApplicationWorkspaceConfig) {
  const nextName = window.prompt("工作空间名称", workspace.workspaceName);
  if (!nextName || !nextName.trim()) {
    return;
  }
  await run(async () => {
    await api.renameApplicationWorkspace(selectedAppId.value, workspace.workspaceId, { workspaceName: nextName.trim() });
    await loadWorkspaces();
  });
}

async function deleteWorkspace(workspaceId: string) {
  await run(async () => {
    await api.deleteApplicationWorkspace(selectedAppId.value, workspaceId);
    await loadWorkspaces();
  });
}

async function loadSshKeys() {
  await run(async () => {
    sshKeys.value = await api.listPersonalSshKeys();
  });
}

async function addSshKey() {
  await run(async () => {
    await api.addPersonalSshKey({ name: sshKeyName.value.trim(), privateKey: sshPrivateKey.value });
    sshKeyName.value = "";
    sshPrivateKey.value = "";
    await loadSshKeys();
  });
}

async function deleteSshKey(sshKeyId: string) {
  await run(async () => {
    await api.deletePersonalSshKey(sshKeyId);
    await loadSshKeys();
  });
}
</script>

<template>
  <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4" role="dialog" aria-modal="true">
    <div class="flex h-[min(760px,calc(100vh-32px))] w-[min(1080px,calc(100vw-32px))] overflow-hidden rounded-lg border border-[var(--ta-border)] bg-[var(--ta-panel)] shadow-2xl">
      <aside class="flex w-52 shrink-0 flex-col border-r border-[var(--ta-border)] bg-[var(--ta-panel-2)]">
        <div class="flex h-14 items-center justify-between border-b border-[var(--ta-border)] px-4">
          <div class="flex items-center gap-2 text-sm font-semibold text-[var(--ta-text)]">
            <Settings class="h-4 w-4" />
            设置
          </div>
          <button class="ta-activity-button h-8 w-8" type="button" aria-label="关闭设置" title="关闭设置" @click="emit('close')">
            <X class="h-4 w-4" />
          </button>
        </div>
        <nav class="flex flex-1 flex-col gap-1 p-3" aria-label="设置菜单">
          <button
            type="button"
            :class="['rounded-md px-3 py-2 text-left text-sm', activeMenu === 'apps' ? 'bg-[var(--ta-hover)] text-[var(--ta-ink)]' : 'text-[var(--ta-subtle)] hover:bg-[var(--ta-hover)] hover:text-[var(--ta-text)]']"
            @click="activeMenu = 'apps'"
          >
            应用与工作区
          </button>
          <button
            type="button"
            :class="['rounded-md px-3 py-2 text-left text-sm', activeMenu === 'personal' ? 'bg-[var(--ta-hover)] text-[var(--ta-ink)]' : 'text-[var(--ta-subtle)] hover:bg-[var(--ta-hover)] hover:text-[var(--ta-text)]']"
            @click="activeMenu = 'personal'"
          >
            个人设置
          </button>
        </nav>
      </aside>

      <main class="flex min-w-0 flex-1 flex-col">
        <header class="flex h-14 shrink-0 items-center justify-between border-b border-[var(--ta-border)] px-5">
          <div class="min-w-0">
            <div class="truncate text-sm font-semibold text-[var(--ta-text)]">
              {{ activeMenu === "apps" ? "应用与工作区" : "SSH key 管理" }}
            </div>
            <div v-if="activeMenu === 'apps' && hasAppSettingsPermission" class="truncate text-xs text-[var(--ta-muted)]">
              {{ selectedApp?.appName ?? "未选择应用" }}
            </div>
          </div>
          <select
            v-if="activeMenu === 'apps' && hasAppSettingsPermission"
            v-model="selectedAppId"
            aria-label="应用选择"
            class="h-9 w-64 rounded-md border border-[var(--ta-border)] bg-[var(--ta-panel-2)] px-3 text-sm text-[var(--ta-text)]"
          >
            <option v-for="app in applications" :key="app.appId" :value="app.appId">{{ app.appName }}</option>
          </select>
        </header>

        <div v-if="errorMessage" class="border-b border-red-200 bg-red-50 px-5 py-2 text-sm text-red-700">
          {{ errorMessage }}
        </div>

        <section v-if="activeMenu === 'apps'" class="flex min-h-0 flex-1 flex-col">
          <div v-if="!hasAppSettingsPermission" class="flex min-h-0 flex-1 items-center justify-center p-6">
            <div class="rounded-md border border-[var(--ta-border)] bg-[var(--ta-panel-2)] px-5 py-4 text-sm text-[var(--ta-muted)]">
              您当前角色[{{ currentRoleLabel }}]无该项设置权限。
            </div>
          </div>

          <template v-else>
          <div class="flex h-11 shrink-0 items-center gap-1 border-b border-[var(--ta-border)] px-4">
            <button
              type="button"
              :class="['rounded-md px-3 py-1.5 text-sm', appTab === 'members' ? 'bg-[var(--ta-hover)] text-[var(--ta-ink)]' : 'text-[var(--ta-muted)] hover:text-[var(--ta-text)]']"
              @click="appTab = 'members'"
            >
              应用人员管理
            </button>
            <button
              type="button"
              :class="['rounded-md px-3 py-1.5 text-sm', appTab === 'repositories' ? 'bg-[var(--ta-hover)] text-[var(--ta-ink)]' : 'text-[var(--ta-muted)] hover:text-[var(--ta-text)]']"
              @click="appTab = 'repositories'"
            >
              应用与代码库关联
            </button>
            <button
              type="button"
              :class="['rounded-md px-3 py-1.5 text-sm', appTab === 'workspaces' ? 'bg-[var(--ta-hover)] text-[var(--ta-ink)]' : 'text-[var(--ta-muted)] hover:text-[var(--ta-text)]']"
              @click="appTab = 'workspaces'"
            >
              工作空间管理
            </button>
          </div>

          <div class="min-h-0 flex-1 overflow-auto p-5">
            <div v-if="!selectedAppId" class="text-sm text-[var(--ta-muted)]">暂无启用应用</div>

            <div v-else-if="appTab === 'members'" class="grid gap-5">
              <div class="grid grid-cols-[1fr_auto] gap-2">
                <Input v-model="userKeyword" placeholder="用户名或统一认证号" @keyup.enter="searchUsers" />
                <Button type="button" variant="secondary" :disabled="loading" @click="searchUsers">搜索用户</Button>
              </div>
              <div v-if="users.length" class="grid gap-2">
                <div v-for="user in users" :key="user.userId" class="flex items-center justify-between rounded-md border border-[var(--ta-border)] px-3 py-2 text-sm">
                  <span class="min-w-0 truncate">{{ user.username }} · {{ user.unifiedAuthId }}</span>
                  <Button type="button" size="sm" variant="secondary" :disabled="loading" @click="memberUserId = user.userId; addMember()">
                    <UserPlus class="mr-1 h-3.5 w-3.5" />加入
                  </Button>
                </div>
              </div>
              <div class="grid grid-cols-[1fr_auto] gap-2">
                <Input v-model="memberUserId" placeholder="用户 ID" @keyup.enter="addMember" />
                <Button type="button" :disabled="loading || !memberUserId.trim()" @click="addMember">新增成员</Button>
              </div>
              <div class="grid gap-2">
                <div v-for="member in members" :key="member.userId" class="flex items-center justify-between rounded-md border border-[var(--ta-border)] px-3 py-2 text-sm">
                  <div class="min-w-0">
                    <div class="truncate font-medium text-[var(--ta-text)]">{{ member.username }}</div>
                    <div class="truncate text-xs text-[var(--ta-muted)]">{{ member.userId }} · {{ member.unifiedAuthId }}</div>
                  </div>
                  <Button type="button" size="sm" variant="ghost" :disabled="loading" @click="removeMember(member.userId)">
                    <Trash2 class="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
            </div>

            <div v-else-if="appTab === 'repositories'" class="grid gap-6">
              <div class="grid grid-cols-[1.4fr_1fr_auto_auto] gap-2">
                <Input v-model="repoGitUrl" placeholder="Git URL" />
                <Input v-model="repoName" placeholder="中文名称" />
                <label class="flex items-center gap-2 rounded-md border border-[var(--ta-border)] px-3 text-sm text-[var(--ta-text)]">
                  <input v-model="repoStandard" type="checkbox" />
                  标准库
                </label>
                <Button type="button" :disabled="loading" @click="createRepository">新增代码库</Button>
              </div>

              <div class="grid grid-cols-[1fr_auto] gap-2">
                <select v-model="linkRepositoryId" class="h-9 rounded-md border border-[var(--ta-border)] bg-[var(--ta-panel-2)] px-3 text-sm">
                  <option value="">选择代码库</option>
                  <option v-for="repo in repositories" :key="repo.repositoryId" :value="repo.repositoryId">{{ repo.name }}</option>
                </select>
                <Button type="button" :disabled="loading || !linkRepositoryId" @click="linkRepository">
                  <Link2 class="mr-1 h-3.5 w-3.5" />关联到当前应用
                </Button>
              </div>

              <div class="grid gap-2">
                <div v-for="repo in appRepositories" :key="repo.repositoryId" class="rounded-md border border-[var(--ta-border)] p-3 text-sm">
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0">
                      <div class="truncate font-medium text-[var(--ta-text)]">{{ repo.name }}</div>
                      <div class="truncate text-xs text-[var(--ta-muted)]">{{ repo.gitUrl }}</div>
                    </div>
                    <Button type="button" size="sm" variant="ghost" :disabled="loading" @click="unlinkRepository(repo.repositoryId)">解除</Button>
                  </div>
                </div>
              </div>

              <div class="grid gap-3 border-t border-[var(--ta-border)] pt-5">
                <div class="grid grid-cols-[1fr_auto] gap-2">
                  <select v-model="selectedRepositoryForApps" class="h-9 rounded-md border border-[var(--ta-border)] bg-[var(--ta-panel-2)] px-3 text-sm" @change="loadRepositoryApplications">
                    <option value="">选择代码库</option>
                    <option v-for="repo in repositories" :key="repo.repositoryId" :value="repo.repositoryId">{{ repo.name }}</option>
                  </select>
                  <Button type="button" variant="secondary" :disabled="loading || !selectedRepositoryForApps" @click="loadRepositoryApplications">刷新关联应用</Button>
                </div>
                <div class="grid grid-cols-[1fr_auto] gap-2">
                  <Input v-model="linkAppId" placeholder="应用 ID" @keyup.enter="linkApplication" />
                  <Button type="button" :disabled="loading || !selectedRepositoryForApps || !linkAppId.trim()" @click="linkApplication">关联应用</Button>
                </div>
                <div class="grid gap-2">
                  <div v-for="app in repositoryApplications" :key="app.appId" class="flex items-center justify-between rounded-md border border-[var(--ta-border)] px-3 py-2 text-sm">
                    <span>{{ app.appName }} · {{ app.appId }}</span>
                    <Button type="button" size="sm" variant="ghost" :disabled="loading" @click="unlinkApplication(app.appId)">解除</Button>
                  </div>
                </div>
              </div>

              <div class="grid gap-2 border-t border-[var(--ta-border)] pt-5">
                <div v-for="repo in repositories" :key="repo.repositoryId" class="grid gap-2 rounded-md border border-[var(--ta-border)] p-3 text-sm">
                  <div class="flex items-center justify-between gap-3">
                    <div class="min-w-0">
                      <div class="truncate font-medium">{{ repo.name }} <span v-if="repo.standard" class="text-xs text-[var(--ta-muted)]">标准库</span></div>
                      <div class="truncate text-xs text-[var(--ta-muted)]">{{ repo.gitUrl }}</div>
                    </div>
                    <Button type="button" size="sm" variant="secondary" @click="startEditRepository(repo)">编辑</Button>
                  </div>
                  <div v-if="editRepositoryId === repo.repositoryId" class="grid grid-cols-[1fr_auto_auto] gap-2">
                    <Input v-model="editRepositoryName" />
                    <label class="flex items-center gap-2 rounded-md border border-[var(--ta-border)] px-3 text-sm">
                      <input v-model="editRepositoryStandard" type="checkbox" />
                      标准库
                    </label>
                    <Button type="button" :disabled="loading" @click="saveRepository">保存</Button>
                  </div>
                </div>
              </div>
            </div>

            <div v-else class="grid gap-5">
              <div class="grid grid-cols-[1fr_auto_1fr_auto] gap-2">
                <select v-model="workspaceRepositoryId" class="h-9 rounded-md border border-[var(--ta-border)] bg-[var(--ta-panel-2)] px-3 text-sm">
                  <option value="">选择已关联代码库</option>
                  <option v-for="repo in appRepositories" :key="repo.repositoryId" :value="repo.repositoryId">{{ repo.name }}</option>
                </select>
                <Button type="button" variant="secondary" :disabled="loading || !workspaceRepositoryId" @click="loadBranches">加载分支</Button>
                <select v-model="workspaceBranch" class="h-9 rounded-md border border-[var(--ta-border)] bg-[var(--ta-panel-2)] px-3 text-sm">
                  <option value="">选择分支</option>
                  <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
                </select>
                <Button type="button" variant="secondary" :disabled="loading || !workspaceBranch" @click="loadDirectories">加载目录</Button>
              </div>
              <div class="grid grid-cols-[1fr_1fr_auto] gap-2">
                <select v-model="workspaceDirectory" class="h-9 rounded-md border border-[var(--ta-border)] bg-[var(--ta-panel-2)] px-3 text-sm">
                  <option value="">选择目录</option>
                  <option v-for="directory in directories" :key="directory" :value="directory">{{ directory }}</option>
                </select>
                <Input v-model="workspaceName" placeholder="工作空间名称" />
                <Button type="button" :disabled="loading || !workspaceDirectory" @click="createWorkspace">创建工作空间</Button>
              </div>
              <div class="grid gap-2">
                <div v-for="workspace in workspaces" :key="workspace.workspaceId" class="flex items-center justify-between gap-3 rounded-md border border-[var(--ta-border)] px-3 py-2 text-sm">
                  <div class="min-w-0">
                    <div class="truncate font-medium text-[var(--ta-text)]">{{ workspace.workspaceName }}</div>
                    <div class="truncate text-xs text-[var(--ta-muted)]">{{ workspace.branch }} · {{ workspace.directoryPath }}</div>
                  </div>
                  <div class="flex shrink-0 items-center gap-2">
                    <Button type="button" size="sm" variant="secondary" :disabled="loading" @click="renameWorkspace(workspace)">重命名</Button>
                    <Button type="button" size="sm" variant="ghost" :disabled="loading" @click="deleteWorkspace(workspace.workspaceId)">
                      <Trash2 class="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              </div>
            </div>
          </div>
          </template>
        </section>

        <section v-else class="min-h-0 flex-1 overflow-auto p-5">
          <div class="grid gap-4">
            <div v-for="sshKey in sshKeys" :key="sshKey.sshKeyId" class="flex items-center justify-between gap-3 rounded-md border border-[var(--ta-border)] px-3 py-2 text-sm">
              <div class="min-w-0">
                <div class="flex items-center gap-2 font-medium text-[var(--ta-text)]">
                  <KeyRound class="h-4 w-4" />
                  {{ sshKey.name }}
                </div>
                <div class="truncate text-xs text-[var(--ta-muted)]">{{ sshKey.fingerprint }}</div>
              </div>
              <Button type="button" size="sm" variant="ghost" :disabled="loading" @click="deleteSshKey(sshKey.sshKeyId)">
                <Trash2 class="h-3.5 w-3.5" />
              </Button>
            </div>

            <div v-if="sshKeys.length === 0" class="grid gap-3">
              <Input v-model="sshKeyName" placeholder="SSH key 名称" />
              <Textarea v-model="sshPrivateKey" class="min-h-48 font-mono text-xs" placeholder="-----BEGIN OPENSSH PRIVATE KEY-----" />
              <div class="flex justify-end">
                <Button type="button" :disabled="loading || !sshKeyName.trim() || !sshPrivateKey.trim()" @click="addSshKey">添加 SSH key</Button>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>
