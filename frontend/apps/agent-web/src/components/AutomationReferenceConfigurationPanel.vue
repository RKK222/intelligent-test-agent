<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch } from "vue";
import {
  Check,
  ChevronDown,
  ChevronRight,
  Copy,
  File,
  Folder,
  FolderGit2,
  GitBranch,
  RefreshCw
} from "lucide-vue-next";
import { Button, copyTextToClipboard, Input, Spinner } from "@test-agent/ui-kit";
import { BackendApiError, type AutomationVersionSynchronization, type BackendApiClient } from "@test-agent/backend-api";
import type {
  ApplicationWorkspaceTemplate,
  ApplicationWorkspaceVersion,
  CodeRepositoryConfig,
  RepositoryTreeNode,
  WorkspaceCreateOperation
} from "@test-agent/shared-types";
import RepositoryOperationProgressDialog from "./RepositoryOperationProgressDialog.vue";

const props = defineProps<{
  open: boolean;
  appId: string;
  canManage: boolean;
}>();

const emit = defineEmits<{
  changed: [];
  "operation-state": [state: { open: boolean; canClose: boolean }];
}>();

const api = inject<BackendApiClient>("api")!;
const AUTOMATION_REPOSITORY_TYPE = "AUTOMATION_CODE_REPOSITORY";
const OPERATION_POLL_INTERVAL_MS = 1_000;

interface Notice {
  message: string;
  traceId?: string;
}

interface VisibleTreeNode {
  name: string;
  path: string;
  directory: boolean;
  depth: number;
}

export type AutomationRepository = CodeRepositoryConfig & {
  branch?: string;
  repositoryPath?: string | null;
  targetCommitHash?: string | null;
  status?: string;
  targetServerCount?: number;
  readyServerCount?: number;
  servers?: Array<{
    linuxServerId: string;
    serverName?: string;
    status?: string;
    online?: boolean;
    currentBranch?: string;
    currentCommitHash?: string;
    error?: string;
    syncedAt?: string;
    verifiedAt?: string;
  }>;
};

const repositories = ref<AutomationRepository[]>([]);
const templates = ref<ApplicationWorkspaceTemplate[]>([]);
const versionsByTemplate = ref<Record<string, ApplicationWorkspaceVersion[]>>({});
const synchronizationsByVersion = ref<Record<string, AutomationVersionSynchronization>>({});
const loading = ref(false);
const saving = ref(false);
const syncLoading = ref(false);
const errorMessage = ref("");
const selectedRepositoryId = ref<string | null>(null);

const branchPopoverRepositoryId = ref<string | null>(null);
const branchPopoverMode = ref<"initialize" | "switch" | null>(null);
const branches = ref<string[]>([]);
const selectedBranch = ref("");
const branchesLoading = ref(false);
const branchError = ref<Notice | null>(null);

const treeByParent = ref<Record<string, Array<{ name: string; path: string; directory: boolean }>>>({});
const treeLoading = ref(false);
const treeErrors = ref<Record<string, Notice>>({});
const expandedPaths = ref<Set<string>>(new Set());
const selectedFolderPath = ref<string | null>(null);

const branchesByRepository = ref<Record<string, string[]>>({});
const treeByBranchKey = ref<Record<string, Record<string, Array<{ name: string; path: string; directory: boolean }>>>>({});

const referenceName = ref("");
const versionDate = ref("");
const formBranch = ref("");

const operation = ref<WorkspaceCreateOperation | null>(null);
const synchronization = ref<AutomationVersionSynchronization | null>(null);
const synchronizationRequest = ref<{
  template: ApplicationWorkspaceTemplate;
  version: ApplicationWorkspaceVersion;
  requestState: "REQUESTING" | "ACCEPTED" | "FAILED";
  error: { message: string; traceId?: string } | null;
  pollingError: { message: string; traceId?: string } | null;
} | null>(null);

let catalogGeneration = 0;
let branchGeneration = 0;
let treeGeneration = 0;
let operationTimer: number | undefined;
let synchronizationTimer: number | undefined;

const selectedRepository = computed(() =>
  repositories.value.find((r) => r.repositoryId === selectedRepositoryId.value) ?? null
);

const activeTemplate = computed(() => {
  if (!selectedRepositoryId.value || !selectedFolderPath.value) return null;
  return templates.value.find(
    (t) => t.repositoryId === selectedRepositoryId.value && t.directoryPath === selectedFolderPath.value
  ) ?? null;
});

const selectedVersions = computed(() =>
  activeTemplate.value ? versionsByTemplate.value[activeTemplate.value.workspaceId] ?? [] : []
);

const activeVersion = computed(() => activeTemplate.value?.activeVersion ?? null);

const currentSynchronization = computed(() => {
  if (activeVersion.value) {
    return synchronizationsByVersion.value[activeVersion.value.versionId] ?? null;
  }
  return synchronization.value;
});

const targetCommitHash = computed(() =>
  activeVersion.value?.targetCommitHash
  || currentSynchronization.value?.targetCommitHash
  || selectedRepository.value?.targetCommitHash
  || null
);

const visibleTreeNodes = computed<VisibleTreeNode[]>(() => {
  const result: VisibleTreeNode[] = [];
  const append = (parentPath: string, depth: number) => {
    for (const node of treeByParent.value[parentPath] ?? []) {
      result.push({ ...node, depth });
      if (node.directory && expandedPaths.value.has(node.path)) {
        append(node.path, depth + 1);
      }
    }
  };
  append("", 0);
  return result;
});

const canCreateReference = computed(() =>
  props.canManage
  && !saving.value
  && Boolean(selectedRepositoryId.value && (formBranch.value || selectedBranch.value) && selectedFolderPath.value && referenceName.value.trim())
  && /^\d{8}$/.test(versionDate.value)
);

const canCreateVersion = computed(() =>
  props.canManage
  && !saving.value
  && Boolean(activeTemplate.value && (formBranch.value || selectedBranch.value))
  && /^\d{8}$/.test(versionDate.value)
);

const synchronizationTarget = computed(() => {
  const request = synchronizationRequest.value;
  if (!request) return null;
  const current = synchronization.value;
  const repository = repositories.value.find((item) => item.repositoryId === request.version.repositoryId);
  return {
    name: current?.repositoryName || repository?.name || "自动化代码库",
    englishName: repository?.englishName || null,
    branch: current?.branch || request.version.branch,
    targetCommitHash: current?.targetCommitHash || request.version.targetCommitHash || null,
    status: current?.status || "SYNCHRONIZING",
    targetServerCount: current?.targetServerCount || current?.servers?.length || 0,
    readyServerCount: current?.readyServerCount || 0,
    servers: current?.servers || [],
    traceId: current?.traceId,
    message: current?.message
  };
});

const acceptedSynchronizationTarget = computed(() =>
  synchronizationRequest.value?.requestState === "ACCEPTED" ? synchronizationTarget.value : null
);

const synchronizationCanClose = computed(() => {
  const request = synchronizationRequest.value;
  if (!request) return false;
  return request.requestState === "FAILED" || ["READY", "FAILED"].includes(synchronization.value?.status || "");
});

const synchronizationCanRetry = computed(() => {
  const request = synchronizationRequest.value;
  return Boolean(
    request && request.requestState !== "REQUESTING"
    && (request.requestState === "FAILED" || synchronization.value?.status === "FAILED")
  );
});

function todayVersion(): string {
  const now = new Date();
  const year = String(now.getFullYear());
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}${month}${day}`;
}

function shortCommit(hash?: string | null): string {
  return hash && hash.length >= 7 ? hash.slice(0, 7) : hash || "—";
}

function formattedTime(iso?: string | null): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function readyServerText(repo: AutomationRepository): string {
  const repoTemplates = templates.value.filter((t) => t.repositoryId === repo.repositoryId);
  for (const t of repoTemplates) {
    if (t.activeVersion && synchronizationsByVersion.value[t.activeVersion.versionId]) {
      const sync = synchronizationsByVersion.value[t.activeVersion.versionId];
      const target = sync.targetServerCount || sync.servers?.length || 0;
      return `${sync.readyServerCount}/${target} 台就绪`;
    }
  }
  const sync = currentSynchronization.value;
  if (sync && (sync.targetServerCount || sync.servers?.length)) {
    const target = sync.targetServerCount || sync.servers?.length || 0;
    return `${sync.readyServerCount}/${target} 台就绪`;
  }
  return `${repo.readyServerCount || 0}/${repo.targetServerCount || 0} 台就绪`;
}

function copyCommit(hash?: string | null) {
  if (hash) copyTextToClipboard(hash);
}

function notice(error: unknown, fallback: string): Notice {
  if (error instanceof BackendApiError) {
    return { message: error.message || fallback, traceId: error.traceId || undefined };
  }
  return { message: error instanceof Error ? error.message : fallback };
}

function buildTreeByParent(nodes: RepositoryTreeNode[]) {
  const map: Record<string, Array<{ name: string; path: string; directory: boolean }>> = {};
  const traverse = (items: RepositoryTreeNode[], parentPath = "") => {
    const list = items.map((node) => ({
      name: node.name,
      path: node.path,
      directory: node.type === "directory"
    })).sort((a, b) => {
      if (a.directory !== b.directory) return a.directory ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
    map[parentPath] = list;
    for (const node of items) {
      if (node.type === "directory" && node.children && node.children.length > 0) {
        traverse(node.children, node.path);
      }
    }
  };
  traverse(nodes, "");
  return map;
}

async function loadCatalog() {
  const generation = ++catalogGeneration;
  loading.value = true;
  errorMessage.value = "";
  try {
    const [nextTemplates, nextRepositories, configuredWorkspaces] = await Promise.all([
      api.listWorkspaceTemplates(props.appId),
      props.canManage ? api.listApplicationRepositories(props.appId) : Promise.resolve([]),
      props.canManage ? api.listApplicationWorkspaces(props.appId) : Promise.resolve([])
    ]);
    if (!props.open || generation !== catalogGeneration) return;

    const automationRepositories = nextRepositories.filter((r) => r.repositoryType === AUTOMATION_REPOSITORY_TYPE);
    const automationRepositoryIds = new Set(automationRepositories.map((r) => r.repositoryId));

    const templateById = new Map<string, ApplicationWorkspaceTemplate>();
    for (const t of nextTemplates) {
      if (t.repositoryType === AUTOMATION_REPOSITORY_TYPE) {
        templateById.set(t.workspaceId, t);
      }
    }

    if (props.canManage) {
      for (const workspace of configuredWorkspaces) {
        if (!automationRepositoryIds.has(workspace.repositoryId) || templateById.has(workspace.workspaceId)) continue;
        templateById.set(workspace.workspaceId, {
          ...workspace,
          standard: false,
          repositoryType: AUTOMATION_REPOSITORY_TYPE,
          activeVersion: null
        });
      }
    }

    const automationTemplates = [...templateById.values()];
    const versionEntries = await Promise.all(automationTemplates.map(async (t) => [
      t.workspaceId,
      await api.listWorkspaceVersions(props.appId, t.workspaceId)
    ] as const));

    if (!props.open || generation !== catalogGeneration) return;

    templates.value = automationTemplates;
    repositories.value = automationRepositories.length > 0
      ? automationRepositories
      : automationTemplates.map((t) => ({
          repositoryId: t.repositoryId,
          name: t.workspaceName,
          englishName: t.workspaceName,
          repositoryType: AUTOMATION_REPOSITORY_TYPE,
          gitUrl: t.directoryPath,
          standard: false,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          branch: t.branch || "main",
          status: "READY",
          targetServerCount: 0,
          readyServerCount: 0,
          servers: []
        } as AutomationRepository));

    versionsByTemplate.value = Object.fromEntries(versionEntries);

    if (props.canManage) {
      const statusEntries = await Promise.all(versionEntries.flatMap(([templateId, versions]) =>
        versions.map(async (v) => {
          try {
            return [
              v.versionId,
              await api.getAutomationWorkspaceVersionSynchronizationStatus(props.appId, templateId, v.versionId)
            ] as const;
          } catch {
            return null;
          }
        })));
      if (!props.open || generation !== catalogGeneration) return;
      synchronizationsByVersion.value = Object.fromEntries(statusEntries.filter((e) => e !== null));
    }

    const currentRepo = repositories.value.find((r) => r.repositoryId === selectedRepositoryId.value)
      || repositories.value[0]
      || null;

    if (currentRepo) {
      await selectRepository(currentRepo);
    }
  } catch (error) {
    if (generation === catalogGeneration) {
      errorMessage.value = error instanceof Error ? error.message : "加载自动化引用配置失败";
    }
  } finally {
    if (generation === catalogGeneration) loading.value = false;
  }
}

async function selectRepository(repository: AutomationRepository, autoSync = false) {
  selectedRepositoryId.value = repository.repositoryId;
  closeBranchPopover();

  const existing = templates.value.find((t) => t.repositoryId === repository.repositoryId);
  if (existing) {
    selectedFolderPath.value = existing.directoryPath;
    referenceName.value = existing.workspaceName;
  } else {
    selectedFolderPath.value = null;
    referenceName.value = repository.name;
  }
  versionDate.value = todayVersion();

  if (props.canManage) {
    await loadBranches(repository.repositoryId, repository.branch || existing?.branch || "main");
  }

  if (autoSync && props.canManage && existing) {
    const versionList = versionsByTemplate.value[existing.workspaceId] ?? [];
    const targetVersion = (existing.activeVersion
      ? versionList.find((v) => v.versionId === existing.activeVersion?.versionId) || existing.activeVersion
      : versionList[0]) as ApplicationWorkspaceVersion | null;
    if (targetVersion) {
      void synchronizeVersion(existing, targetVersion);
    }
  }
}

async function loadBranches(repositoryId: string, preferredBranch = "", force = false) {
  const isForce = force === true;
  const generation = ++branchGeneration;
  const cached = branchesByRepository.value[repositoryId];

  if (!isForce && cached) {
    branches.value = cached;
    selectedBranch.value = cached.includes(preferredBranch) ? preferredBranch : cached[0] ?? "";
    formBranch.value = selectedBranch.value;
    branchesLoading.value = false;
    if (selectedBranch.value) await loadTree(isForce);
    return;
  }

  branchesLoading.value = true;
  branches.value = [];
  selectedBranch.value = "";
  formBranch.value = "";
  try {
    const result = await api.listRepositoryBranches(repositoryId);
    if (generation !== branchGeneration || repositoryId !== selectedRepositoryId.value) return;
    branchesByRepository.value = { ...branchesByRepository.value, [repositoryId]: result };
    branches.value = result;
    selectedBranch.value = result.includes(preferredBranch) ? preferredBranch : result[0] ?? "";
    formBranch.value = selectedBranch.value;
    if (selectedBranch.value) await loadTree(isForce);
  } catch (error) {
    if (generation === branchGeneration) {
      branchError.value = notice(error, "加载分支失败");
    }
  } finally {
    if (generation === branchGeneration) branchesLoading.value = false;
  }
}

async function loadTree(force = false) {
  const isForce = force === true;
  if (!selectedRepositoryId.value || !selectedBranch.value) return;
  const repositoryId = selectedRepositoryId.value;
  const branch = selectedBranch.value;
  const cacheKey = `${props.appId}:${repositoryId}:${branch}`;
  const generation = ++treeGeneration;
  const cached = treeByBranchKey.value[cacheKey];

  if (!isForce && cached) {
    treeByParent.value = cached;
    treeLoading.value = false;
    autoExpandAndSelect();
    return;
  }

  treeLoading.value = true;
  treeByParent.value = {};
  treeErrors.value = {};
  try {
    const response = await api.getRepositoryTree(props.appId, repositoryId, branch);
    if (generation !== treeGeneration || repositoryId !== selectedRepositoryId.value || branch !== selectedBranch.value) return;
    const treeMap = buildTreeByParent(response.nodes);
    treeByBranchKey.value = { ...treeByBranchKey.value, [cacheKey]: treeMap };
    treeByParent.value = treeMap;
    autoExpandAndSelect();
  } catch (error) {
    if (generation === treeGeneration) {
      treeErrors.value = { "": notice(error, "加载自动化代码库目录失败") };
    }
  } finally {
    if (generation === treeGeneration) treeLoading.value = false;
  }
}

function autoExpandAndSelect() {
  const rootNodes = treeByParent.value[""] ?? [];
  const expandSet = new Set<string>();
  for (const node of rootNodes) {
    if (node.directory) expandSet.add(node.path);
  }
  expandedPaths.value = expandSet;

  if (selectedFolderPath.value) {
    return;
  }
  const repoTemplates = templates.value.filter((t) => t.repositoryId === selectedRepositoryId.value);
  if (repoTemplates.length > 0) {
    const firstTemplate = repoTemplates[0]!;
    selectedFolderPath.value = firstTemplate.directoryPath;
    referenceName.value = firstTemplate.workspaceName;
    versionDate.value = todayVersion();
    formBranch.value = selectedBranch.value;
    return;
  }

  if (rootNodes.length > 0) {
    const firstDir = rootNodes.find((n) => n.directory);
    if (firstDir) selectFolder(firstDir);
  }
}

function toggleDirectory(node: VisibleTreeNode) {
  const set = new Set(expandedPaths.value);
  if (set.has(node.path)) {
    set.delete(node.path);
  } else {
    set.add(node.path);
  }
  expandedPaths.value = set;
}

function selectFolder(node: { name: string; path: string; directory: boolean }) {
  if (!node.directory) return;
  selectedFolderPath.value = node.path;
  const existing = templates.value.find(
    (t) => t.repositoryId === selectedRepositoryId.value && t.directoryPath === node.path
  );
  if (existing) {
    referenceName.value = existing.workspaceName;
  } else {
    const parts = node.path.split("/").filter(Boolean);
    referenceName.value = parts[parts.length - 1] || "自动化目录";
  }
  versionDate.value = todayVersion();
  formBranch.value = selectedBranch.value;
}

function openBranchPopover(repository: AutomationRepository, mode: "initialize" | "switch") {
  branchPopoverRepositoryId.value = repository.repositoryId;
  branchPopoverMode.value = mode;
  branchError.value = null;
}

function closeBranchPopover() {
  branchPopoverRepositoryId.value = null;
  branchPopoverMode.value = null;
  branchError.value = null;
}

async function confirmBranchPopover() {
  if (!selectedBranch.value) return;
  closeBranchPopover();
  await loadTree(true);
}

async function verifyPointers() {
  if (!selectedRepositoryId.value) return;
  const template = activeTemplate.value;
  const ver = (activeVersion.value || (template ? versionsByTemplate.value[template.workspaceId]?.[0] : null)) as ApplicationWorkspaceVersion | null;
  if (template && ver) {
    void synchronizeVersion(template, ver);
    return;
  }
  syncLoading.value = true;
  try {
    await loadCatalog();
  } finally {
    syncLoading.value = false;
  }
}

function createOperationId() {
  const random = typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID().replaceAll("-", "")
    : `${Date.now()}${Math.random().toString(16).slice(2)}`;
  return `wco_${random.replace(/[^A-Za-z0-9_-]/g, "").slice(0, 64)}`;
}

function clearOperationPoll() {
  if (operationTimer !== undefined) window.clearTimeout(operationTimer);
  operationTimer = undefined;
}

function clearSynchronizationPoll() {
  if (synchronizationTimer !== undefined) window.clearTimeout(synchronizationTimer);
  synchronizationTimer = undefined;
}

async function pollSynchronization(templateId: string, versionId: string) {
  clearSynchronizationPoll();
  try {
    const next = await api.getAutomationWorkspaceVersionSynchronizationStatus(props.appId, templateId, versionId);
    const request = synchronizationRequest.value;
    if (!props.open || !request || request.version.versionId !== versionId) return;
    synchronization.value = next;
    synchronizationsByVersion.value = { ...synchronizationsByVersion.value, [versionId]: next };
    synchronizationRequest.value = { ...request, pollingError: null };
    if (["READY", "FAILED"].includes(next.status)) {
      saving.value = false;
      emit("changed");
      return;
    }
  } catch (error) {
    const request = synchronizationRequest.value;
    if (!props.open || !request || request.version.versionId !== versionId) return;
    synchronizationRequest.value = {
      ...request,
      pollingError: notice(error, "读取自动化代码库同步状态失败")
    };
  }
  if (!props.open || synchronizationRequest.value?.version.versionId !== versionId) return;
  synchronizationTimer = window.setTimeout(
    () => void pollSynchronization(templateId, versionId),
    OPERATION_POLL_INTERVAL_MS
  );
}

async function synchronizeVersion(template: ApplicationWorkspaceTemplate, version: ApplicationWorkspaceVersion) {
  if (!props.canManage) return;
  clearSynchronizationPoll();
  saving.value = true;
  synchronization.value = null;
  synchronizationRequest.value = {
    template,
    version,
    requestState: "REQUESTING",
    error: null,
    pollingError: null
  };
  try {
    const response = await api.synchronizeAutomationWorkspaceVersion(props.appId, template.workspaceId, version.versionId);
    const request = synchronizationRequest.value;
    if (!props.open || !request || request.version.versionId !== version.versionId) return;
    synchronization.value = response;
    synchronizationsByVersion.value = { ...synchronizationsByVersion.value, [version.versionId]: response };
    synchronizationRequest.value = {
      ...request,
      requestState: "ACCEPTED",
      error: null
    };
    await pollSynchronization(template.workspaceId, version.versionId);
  } catch (error) {
    const request = synchronizationRequest.value;
    if (!props.open || !request || request.version.versionId !== version.versionId) return;
    saving.value = false;
    synchronizationRequest.value = {
      ...request,
      requestState: "FAILED",
      error: notice(error, "创建自动化代码库同步任务失败")
    };
  }
}

function retrySynchronization() {
  const request = synchronizationRequest.value;
  if (request && synchronizationCanRetry.value) void synchronizeVersion(request.template, request.version);
}

function closeSynchronization() {
  if (!synchronizationCanClose.value) return;
  clearSynchronizationPoll();
  synchronizationRequest.value = null;
  synchronization.value = null;
}

async function pollOperation(operationId: string) {
  clearOperationPoll();
  try {
    const next = await api.getWorkspaceCreateOperation(operationId);
    if (!props.open || !saving.value) return;
    operation.value = next;
    if (next.status === "SUCCEEDED") {
      saving.value = false;
      await loadCatalog();
      emit("changed");
      return;
    }
    if (next.status === "FAILED") {
      saving.value = false;
      errorMessage.value = next.errorMessage || "创建自动化目录引用失败";
      return;
    }
  } catch {
  }
  if (!props.open || !saving.value) return;
  operationTimer = window.setTimeout(() => void pollOperation(operationId), OPERATION_POLL_INTERVAL_MS);
}

async function createReference() {
  if (!canCreateReference.value || !selectedRepositoryId.value || !selectedFolderPath.value) return;
  saving.value = true;
  errorMessage.value = "";
  const operationId = createOperationId();
  operation.value = {
    operationId,
    status: "RUNNING",
    currentStep: "VALIDATING_INPUT",
    steps: []
  };
  try {
    await api.createApplicationWorkspace(props.appId, {
      repositoryId: selectedRepositoryId.value,
      branch: formBranch.value || selectedBranch.value,
      directoryPath: selectedFolderPath.value,
      workspaceName: referenceName.value.trim(),
      version: versionDate.value,
      operationId
    });
    await pollOperation(operationId);
  } catch (error) {
    saving.value = false;
    errorMessage.value = error instanceof Error ? error.message : "创建自动化目录引用失败";
  }
}

async function createVersion() {
  const template = activeTemplate.value;
  if (!template || !canCreateVersion.value) return;
  saving.value = true;
  errorMessage.value = "";
  try {
    await api.createWorkspaceVersion(props.appId, template.workspaceId, {
      version: versionDate.value,
      branch: formBranch.value || selectedBranch.value
    });
    await loadCatalog();
    emit("changed");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "新增自动化引用版本失败";
  } finally {
    saving.value = false;
  }
}

async function activateVersion(template: ApplicationWorkspaceTemplate, version: ApplicationWorkspaceVersion) {
  if (!props.canManage || saving.value) return;
  saving.value = true;
  errorMessage.value = "";
  try {
    await api.activateAutomationWorkspaceVersion(props.appId, template.workspaceId, version.versionId);
    await loadCatalog();
    emit("changed");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "切换自动化引用版本失败";
  } finally {
    saving.value = false;
  }
}

async function toggleReference(template: ApplicationWorkspaceTemplate) {
  if (!props.canManage || saving.value) return;
  saving.value = true;
  errorMessage.value = "";
  try {
    await api.updateApplicationWorkspace(props.appId, template.workspaceId, { enabled: template.enabled === false });
    await loadCatalog();
    emit("changed");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "更新自动化引用状态失败";
  } finally {
    saving.value = false;
  }
}

function retryCatalog() {
  branchesByRepository.value = {};
  treeByBranchKey.value = {};
  void loadCatalog();
}

watch(
  () => [props.open, props.appId, props.canManage] as const,
  ([open, appId], prevValues) => {
    const prevAppId = prevValues?.[1];
    catalogGeneration++;
    branchGeneration++;
    treeGeneration++;
    clearOperationPoll();
    clearSynchronizationPoll();
    if (prevAppId !== undefined && appId !== prevAppId) {
      branchesByRepository.value = {};
      treeByBranchKey.value = {};
    }
    if (open && props.appId) void loadCatalog();
  },
  { immediate: true }
);

watch(
  () => [Boolean(synchronizationRequest.value), synchronizationCanClose.value] as const,
  ([open, canClose]) => emit("operation-state", { open, canClose }),
  { immediate: true }
);

onBeforeUnmount(() => {
  catalogGeneration++;
  branchGeneration++;
  treeGeneration++;
  clearOperationPoll();
  clearSynchronizationPoll();
  emit("operation-state", { open: false, canClose: true });
});
</script>

<template>
  <div class="reference-automation-layout">
    <aside class="reference-repository-column" aria-label="自动化代码库">
      <div class="reference-column-heading">
        <span>自动化代码库</span>
        <Spinner v-if="loading" class="h-3.5 w-3.5" />
      </div>

      <div v-if="errorMessage" class="reference-state is-error" role="alert">
        <span>{{ errorMessage }}</span>
        <button
          type="button"
          class="reference-inline-action"
          aria-label="重试加载自动化代码库"
          :disabled="loading"
          @click="retryCatalog"
        >
          重试
        </button>
      </div>
      <div v-else-if="loading && !repositories.length" class="reference-state" role="status">正在加载自动化引用…</div>
      <div v-else-if="repositories.length === 0" class="reference-state">当前应用未关联自动化代码库。</div>
      <div v-else class="reference-repository-list">
        <article
          v-for="repository in repositories"
          :key="repository.repositoryId"
          class="reference-repository-card"
          :class="{ 'is-selected': selectedRepositoryId === repository.repositoryId }"
        >
          <button
            type="button"
            class="reference-repository-main"
            :aria-label="`选择${repository.name}`"
            :aria-pressed="selectedRepositoryId === repository.repositoryId"
            :disabled="saving"
            @click="selectRepository(repository, true)"
          >
            <FolderGit2 class="h-4 w-4 shrink-0" />
            <span class="min-w-0">
              <strong>{{ repository.name }}</strong>
              <small :title="repository.gitUrl">{{ repository.gitUrl }}</small>
            </span>
            <span class="reference-status">{{ activeVersion?.version || repository.status || "READY" }}</span>
          </button>
          <div class="reference-repository-meta">
            <span>{{ readyServerText(repository) }}</span>
            <button
              v-if="canManage"
              type="button"
              class="reference-inline-action"
              :aria-label="`切换${repository.name}分支`"
              :disabled="saving"
              @click="openBranchPopover(repository, 'switch')"
            >
              切换分支
            </button>
          </div>

          <div
            v-if="branchPopoverRepositoryId === repository.repositoryId"
            class="reference-branch-popover"
            role="dialog"
            :aria-label="`切换${repository.name}分支`"
          >
            <div class="reference-branch-title">
              <GitBranch class="h-3.5 w-3.5" />
              选择目标分支
            </div>
            <div v-if="branchesLoading" class="reference-compact-state">
              <Spinner class="h-3.5 w-3.5 inline-block mr-1" /> 正在加载分支…
            </div>
            <div v-else-if="branchError" class="reference-compact-state is-error">
              {{ branchError.message }}
            </div>
            <template v-else>
              <select
                v-model="selectedBranch"
                aria-label="目标分支"
                class="reference-select"
                @change="() => loadTree()"
              >
                <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
              </select>
              <div class="reference-popover-actions">
                <Button size="sm" variant="ghost" @click="closeBranchPopover">取消</Button>
                <Button
                  size="sm"
                  :disabled="!selectedBranch || saving"
                  @click="confirmBranchPopover"
                >
                  确认
                </Button>
              </div>
            </template>
          </div>
        </article>
      </div>
    </aside>

    <main class="reference-configuration-column">
      <div v-if="!selectedRepository" class="reference-state is-centered">
        选择一个已关联自动化代码库开始同步与配置。
      </div>
      <template v-else>
        <div class="reference-selected-heading">
          <div>
            <strong>{{ selectedRepository.name }}</strong>
            <span>{{ activeTemplate ? `${selectedRepository.name} · ${activeTemplate.directoryPath}` : (selectedFolderPath ? `${selectedRepository.name} · ${selectedFolderPath}` : (selectedBranch || selectedRepository.branch || "main")) }}</span>
          </div>
          <div class="reference-selected-actions">
            <div
              class="reference-repository-path"
              :title="selectedRepository.repositoryPath || undefined"
            >
              <span>服务器路径</span>
              <code>{{ selectedRepository.repositoryPath || selectedRepository.gitUrl || "服务器路径暂不可用" }}</code>
            </div>
            <Button
              size="sm"
              variant="ghost"
              :aria-label="`刷新${selectedRepository.name} Git 指针`"
              :disabled="saving || syncLoading"
              @click="verifyPointers"
            >
              <RefreshCw class="h-3.5 w-3.5" :class="{ 'animate-spin': syncLoading }" />
              刷新 Git 指针
            </Button>
          </div>
        </div>

        <section class="reference-pointer-panel" aria-label="服务器 Git 指针">
          <div class="reference-pointer-target">
            <span>目标 Git 指针</span>
            <strong>{{ selectedBranch || selectedRepository.branch || "—" }}</strong>
            <code :title="targetCommitHash || undefined">{{ shortCommit(targetCommitHash) }}</code>
            <button
              v-if="targetCommitHash"
              type="button"
              class="reference-copy-action"
              aria-label="复制目标 Git HEAD"
              @click="copyCommit(targetCommitHash)"
            >
              <Copy class="h-3 w-3" />
            </button>
            <span v-if="activeVersion" class="reference-pointer-status ml-auto">
              当前版本：<code>{{ activeVersion.version }}</code>
            </span>
          </div>

          <div v-if="!selectedRepository.servers || selectedRepository.servers.length === 0" class="reference-compact-state">
            暂无服务器副本信息。
          </div>
          <div v-else class="reference-pointer-table-wrap">
            <table class="reference-pointer-table">
              <thead>
                <tr>
                  <th>服务器</th>
                  <th>状态</th>
                  <th>实际分支</th>
                  <th>实际 HEAD</th>
                  <th>目标</th>
                  <th>最近同步 / 核验</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="server in selectedRepository.servers" :key="server.linuxServerId">
                  <td>
                    <strong>{{ server.linuxServerId }}</strong>
                    <small :class="server.online === true ? 'is-online' : 'is-offline'">
                      {{ server.online === true ? "在线" : "离线 · 非实时" }}
                    </small>
                  </td>
                  <td>
                    <span class="reference-pointer-status">{{ server.status }}</span>
                  </td>
                  <td><code>{{ server.currentBranch || "—" }}</code></td>
                  <td>
                    <span class="reference-commit-cell">
                      <code>{{ shortCommit(server.currentCommitHash) }}</code>
                      <button
                        v-if="server.currentCommitHash"
                        type="button"
                        class="reference-copy-action"
                        :aria-label="`复制 ${server.linuxServerId} Git HEAD`"
                        @click="copyCommit(server.currentCommitHash)"
                      >
                        <Copy class="h-3 w-3" />
                      </button>
                    </span>
                  </td>
                  <td>
                    <span class="reference-pointer-match is-match">
                      <Check class="h-3 w-3" /> 一致
                    </span>
                  </td>
                  <td>
                    <small>同步 <time>{{ formattedTime(server.syncedAt) }}</time></small>
                    <small>核验 <time>{{ formattedTime(server.verifiedAt) }}</time></small>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <div class="reference-ready-layout">
          <section class="reference-tree-panel" aria-label="引用目录树">
            <div class="reference-panel-title">
              <span>目录</span>
              <Spinner v-if="treeLoading" class="h-3 w-3" />
            </div>

            <div v-if="treeLoading" class="reference-compact-state is-centered" role="status">
              <Spinner class="h-4 w-4" />
              <span>正在读取目录…</span>
            </div>
            <div v-else-if="treeErrors['']" class="reference-compact-state is-error">
              {{ treeErrors[""]?.message }}
              <button type="button" class="reference-inline-action" @click="loadTree(true)">重试</button>
            </div>
            <div v-else-if="visibleTreeNodes.length === 0" class="reference-compact-state">
              当前分支没有可选择的目录。
            </div>
            <div v-else class="reference-tree" role="list">
              <div
                v-for="node in visibleTreeNodes"
                :key="node.path"
                class="reference-tree-node"
                role="listitem"
              >
                <div
                  class="reference-tree-row"
                  :class="{
                    'is-reference-selectable': node.directory,
                    'is-selected': selectedFolderPath === node.path
                  }"
                  :style="{ paddingLeft: `${8 + node.depth * 16}px` }"
                >
                  <button
                    v-if="node.directory"
                    type="button"
                    class="reference-tree-toggle"
                    :aria-label="`${expandedPaths.has(node.path) ? '收起' : '展开'} ${node.name}`"
                    :aria-expanded="expandedPaths.has(node.path)"
                    @click="toggleDirectory(node)"
                  >
                    <ChevronDown v-if="expandedPaths.has(node.path)" class="h-3.5 w-3.5" />
                    <ChevronRight v-else class="h-3.5 w-3.5" />
                  </button>
                  <span v-else class="reference-tree-spacer" />
                  <Folder v-if="node.directory" class="reference-tree-icon" />
                  <File v-else class="reference-tree-icon" />
                  <button
                    v-if="node.directory"
                    type="button"
                    class="reference-tree-name"
                    data-reference-selectable="true"
                    :aria-label="`选择目录 ${node.path}`"
                    :disabled="saving"
                    @click="selectFolder(node)"
                  >
                    {{ node.name }}
                  </button>
                  <span v-else class="reference-tree-name">{{ node.name }}</span>
                </div>
              </div>
            </div>
          </section>

          <section class="reference-form-panel" aria-label="自动化引用配置">
            <div class="reference-panel-title">
              <span>配置</span>
              <span v-if="activeTemplate" class="reference-status">
                {{ activeTemplate.enabled === false ? "已停用" : "已启用" }}
              </span>
            </div>

            <form class="reference-form" @submit.prevent="activeTemplate ? createVersion() : createReference()">
              <label>
                <span>版本库（repository）</span>
                <Input :model-value="selectedRepository.name" readonly aria-label="自动化版本库" />
              </label>

              <label>
                <span class="automation-field-label">
                  <span>分支（branch）</span>
                  <span v-if="branchesLoading" class="automation-inline-loading" role="status">
                    <Spinner class="h-3 w-3" />
                    <small>拉取分支中…</small>
                  </span>
                </span>
                <select
                  v-model="formBranch"
                  class="reference-select"
                  aria-label="自动化引用分支"
                  :disabled="branchesLoading || saving"
                  @change="() => { selectedBranch = formBranch; loadTree(); }"
                >
                  <option v-if="branchesLoading && !branches.length" value="" disabled>正在拉取分支…</option>
                  <option v-for="branch in branches" :key="branch" :value="branch">{{ branch }}</option>
                </select>
              </label>

              <label>
                <span>引用目录（path） <b aria-hidden="true">*</b></span>
                <Input :model-value="selectedFolderPath || ''" readonly :placeholder="treeLoading ? '正在读取目录…' : '请在左侧选择目录'" aria-label="引用目录" />
              </label>

              <label>
                <span>引用名称（workspace-name） <b aria-hidden="true">*</b></span>
                <Input
                  v-model="referenceName"
                  aria-label="自动化引用名称"
                  placeholder="例如 接口自动化"
                  :disabled="saving || !canManage"
                />
              </label>

              <label>
                <span>版本日期（version） <b aria-hidden="true">*</b></span>
                <Input
                  v-model="versionDate"
                  aria-label="自动化引用版本日期"
                  placeholder="YYYYMMDD"
                  :disabled="saving || !canManage"
                />
              </label>

              <div v-if="activeTemplate" class="automation-version-history">
                <div class="automation-history-header">
                  <span>版本历史（{{ selectedVersions.length }}）</span>
                  <Button
                    v-if="canManage"
                    size="sm"
                    variant="ghost"
                    class="reference-inline-action"
                    :disabled="saving"
                    @click="toggleReference(activeTemplate)"
                  >
                    {{ activeTemplate.enabled === false ? "启用引用" : "停用引用" }}
                  </Button>
                </div>
                <div class="reference-pointer-table-wrap">
                  <table class="reference-pointer-table">
                    <thead>
                      <tr>
                        <th>版本号</th>
                        <th>分支</th>
                        <th>状态</th>
                        <th>时间</th>
                        <th v-if="canManage" style="text-align: right;">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="ver in selectedVersions" :key="ver.versionId">
                        <td><strong>{{ ver.version }}</strong></td>
                        <td><code>{{ ver.branch }}</code></td>
                        <td>
                          <span v-if="activeVersion?.versionId === ver.versionId" class="reference-pointer-match is-match">
                            <Check class="h-3 w-3" /> 当前版本
                          </span>
                          <span v-else class="reference-pointer-status">{{ ver.status === "ACTIVE" ? "可用" : ver.status }}</span>
                        </td>
                        <td><time>{{ formattedTime(ver.createdAt) }}</time></td>
                        <td v-if="canManage" style="text-align: right;">
                          <div class="automation-version-actions">
                            <Button
                              size="sm"
                              variant="ghost"
                              class="reference-inline-action"
                              :aria-label="`同步自动化版本 ${ver.version}`"
                              :disabled="saving"
                              @click="synchronizeVersion(activeTemplate, ver)"
                            >
                              同步
                            </Button>
                            <Button
                              v-if="activeVersion?.versionId !== ver.versionId"
                              size="sm"
                              variant="ghost"
                              class="reference-inline-action"
                              :disabled="saving || ver.status !== 'ACTIVE'"
                              @click="activateVersion(activeTemplate, ver)"
                            >
                              设为当前版本
                            </Button>
                          </div>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <div v-if="operation" class="reference-form-notice is-loading" role="status">
                <Spinner v-if="operation.status === 'RUNNING'" class="h-3.5 w-3.5 shrink-0" />
                <span>{{ operation.status === "RUNNING" ? "正在初始化共享只读副本…" : operation.status }}</span>
              </div>

              <div v-if="canManage" class="reference-form-actions">
                <Button
                  v-if="!activeTemplate"
                  type="button"
                  aria-label="保存自动化目录引用"
                  :disabled="!canCreateReference"
                  @click="createReference"
                >
                  <Spinner v-if="saving" class="h-3.5 w-3.5 mr-1.5" />
                  {{ saving ? "保存中…" : "保存目录引用" }}
                </Button>
                <Button
                  v-else
                  type="button"
                  aria-label="新增版本"
                  :disabled="!canCreateVersion"
                  @click="createVersion"
                >
                  <Spinner v-if="saving" class="h-3.5 w-3.5 mr-1.5" />
                  {{ saving ? "创建中…" : "新增版本" }}
                </Button>
              </div>
            </form>
          </section>
        </div>
      </template>
    </main>

    <RepositoryOperationProgressDialog
      v-if="synchronizationRequest"
      :open="true"
      operation="SYNCHRONIZE"
      :request-state="synchronizationRequest.requestState"
      :target="synchronizationTarget"
      :accepted-target="acceptedSynchronizationTarget"
      :error="synchronizationRequest.error"
      :polling-error="synchronizationRequest.pollingError"
      :can-close="synchronizationCanClose"
      :can-retry="synchronizationCanRetry"
      resource-label="自动化代码库"
      @close="closeSynchronization"
      @retry="retrySynchronization"
    />
  </div>
</template>

<style scoped>
.reference-automation-layout {
  display: grid;
  grid-template-columns: minmax(290px, 34%) minmax(0, 1fr);
  min-height: 0;
  height: 100%;
}

.reference-repository-column,
.reference-configuration-column {
  min-height: 0;
  overflow: auto;
}

.reference-repository-column {
  border-right: 1px solid var(--ta-border);
  background: var(--ta-panel-2);
}

.reference-column-heading,
.reference-selected-heading,
.reference-panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ta-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.reference-column-heading {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 34px;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 12px;
  background: var(--ta-panel-2);
}

.reference-repository-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
}

.reference-repository-card {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--ta-border);
  border-radius: 7px;
  background: var(--ta-surface);
}

.reference-repository-card.is-selected {
  border-color: var(--ta-border-strong);
  box-shadow: inset 3px 0 0 var(--ta-ink);
}

.reference-repository-main {
  display: grid;
  width: 100%;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 0;
  padding: 9px 10px 6px;
  background: transparent;
  color: var(--ta-text);
  text-align: left;
  cursor: pointer;
}

.reference-repository-main:hover {
  background: var(--ta-hover);
}

.reference-repository-main strong,
.reference-repository-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-repository-main strong {
  font-size: 12px;
  font-weight: 600;
}

.reference-repository-main small {
  margin-top: 3px;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-status {
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-repository-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 10px 7px 34px;
  color: var(--ta-muted);
  font-size: 10px;
}

.reference-inline-action {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--ta-text);
  font-size: 11px;
  font-weight: 600;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

.automation-version-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.reference-branch-popover {
  margin: 0 8px 8px;
  border: 1px solid var(--ta-border-strong);
  border-radius: 6px;
  padding: 8px;
  background: var(--ta-panel);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
}

.reference-branch-title {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 7px;
  font-size: 11px;
  font-weight: 600;
}

.reference-popover-actions,
.reference-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 8px;
}

.reference-configuration-column {
  display: flex;
  flex-direction: column;
  background: var(--ta-panel);
}

.reference-selected-heading {
  min-height: 44px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 14px;
  background: var(--ta-panel-2);
  color: var(--ta-text);
  letter-spacing: normal;
  text-transform: none;
}

.reference-selected-heading strong,
.reference-selected-heading span {
  display: block;
}

.reference-selected-heading strong {
  font-size: 12px;
}

.reference-selected-heading span {
  margin-top: 2px;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
  font-weight: 400;
}

.reference-selected-actions,
.reference-commit-cell,
.reference-pointer-match,
.reference-pointer-target {
  display: flex;
  align-items: center;
}

.reference-selected-actions {
  min-width: 0;
  max-width: 72%;
  gap: 6px;
}

.reference-repository-path {
  display: flex;
  min-width: 0;
  max-width: 430px;
  align-items: center;
  gap: 6px;
  border-right: 1px solid var(--ta-border);
  padding-right: 10px;
}

.reference-repository-path span {
  flex-shrink: 0;
  margin: 0;
  color: var(--ta-muted);
  font-family: inherit;
  font-size: 10px;
}

.reference-repository-path code {
  min-width: 0;
  overflow: hidden;
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-pointer-panel {
  flex-shrink: 0;
  border-bottom: 1px solid var(--ta-border);
  background: var(--ta-surface);
}

.reference-pointer-target {
  min-height: 34px;
  gap: 8px;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 12px;
  color: var(--ta-muted);
  font-size: 10px;
}

.reference-pointer-target strong,
.reference-pointer-target code,
.reference-pointer-table code,
.reference-pointer-table time {
  color: var(--ta-text);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-pointer-table-wrap {
  max-height: 148px;
  overflow: auto;
}

.reference-pointer-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 10px;
  text-align: left;
}

.reference-pointer-table th,
.reference-pointer-table td {
  border-bottom: 1px solid var(--ta-border);
  padding: 5px 8px;
  vertical-align: top;
  white-space: nowrap;
}

.reference-pointer-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--ta-panel-2);
  color: var(--ta-muted);
  font-weight: 600;
}

.reference-pointer-table td:first-child strong,
.reference-pointer-table td:first-child small,
.reference-pointer-table td:last-child small {
  display: block;
}

.reference-pointer-table small {
  margin-top: 2px;
  color: var(--ta-muted);
  font-size: 9px;
}

.reference-pointer-table small.is-online,
.reference-pointer-match.is-match {
  color: var(--ta-ok);
}

.reference-pointer-table small.is-offline {
  color: var(--ta-muted);
}

.reference-pointer-status {
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
}

.reference-commit-cell,
.reference-pointer-match {
  gap: 3px;
}

.reference-copy-action {
  display: inline-grid;
  width: 18px;
  height: 18px;
  place-items: center;
  border: 0;
  border-radius: 3px;
  padding: 0;
  background: transparent;
  color: var(--ta-muted);
  cursor: pointer;
}

.reference-copy-action:hover,
.reference-copy-action:focus-visible {
  background: var(--ta-hover);
  color: var(--ta-text);
}

.reference-ready-layout {
  display: grid;
  min-height: 0;
  flex: 1;
  grid-template-columns: minmax(210px, 42%) minmax(280px, 1fr);
}

.reference-tree-panel,
.reference-form-panel {
  min-height: 0;
  overflow: auto;
}

.reference-tree-panel {
  border-right: 1px solid var(--ta-border);
  background: var(--ta-tree-bg);
  font-family: var(--ta-tree-font-family);
  font-size: var(--ta-tree-font-size);
}

.reference-form-panel {
  background: var(--ta-panel-2);
}

.reference-panel-title {
  position: sticky;
  top: 0;
  z-index: 1;
  height: 32px;
  border-bottom: 1px solid var(--ta-border);
  padding: 0 10px;
  background: inherit;
}

.reference-tree {
  padding: 5px 0;
}

.reference-tree-row {
  display: flex;
  height: var(--ta-tree-row-height);
  align-items: center;
  gap: 4px;
  padding-right: 8px;
  color: var(--ta-tree-text);
}

.reference-tree-row:hover,
.reference-tree-row.is-selected {
  background: var(--ta-tree-hover);
}

.reference-tree-toggle {
  display: inline-grid;
  width: 16px;
  height: 18px;
  flex-shrink: 0;
  place-items: center;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--ta-tree-muted);
  cursor: pointer;
}

.reference-tree-spacer {
  width: 16px;
  flex-shrink: 0;
}

.reference-tree-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: var(--ta-tree-muted);
}

.reference-tree-name {
  min-width: 0;
  overflow: hidden;
  border: 0;
  padding: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  line-height: var(--ta-tree-row-height);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reference-tree-row.is-reference-selectable .reference-tree-icon,
.reference-tree-row.is-reference-selectable .reference-tree-name {
  color: var(--ta-ink);
  font-weight: 600;
  cursor: pointer;
}

.reference-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.reference-form label > span {
  display: block;
  margin-bottom: 5px;
  color: var(--ta-muted);
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-form label b {
  color: var(--ta-error);
}

.automation-field-label {
  display: flex !important;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
}

.automation-inline-loading {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ta-muted);
  font-family: var(--font-sans, -apple-system, sans-serif);
  font-size: 10px;
  font-weight: 400;
}

.automation-inline-loading small {
  color: var(--ta-muted);
  font-size: 10px;
}

.reference-select {
  width: 100%;
  height: 32px;
  border: 1px solid var(--ta-border);
  border-radius: 5px;
  padding: 0 8px;
  outline: none;
  background: var(--ta-surface);
  color: var(--ta-text);
  font-size: 12px;
}

.reference-select:focus {
  border-color: var(--ta-border-strong);
}

.automation-version-history {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border-top: 1px solid var(--ta-border);
  padding-top: 10px;
}

.automation-history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ta-muted);
  font-size: 11px;
  font-weight: 600;
}

.reference-state,
.reference-compact-state {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 12px;
  color: var(--ta-muted);
  font-size: 12px;
}

.reference-state.is-centered,
.reference-compact-state.is-centered {
  min-height: 120px;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 8px;
}

.reference-state.is-error,
.reference-compact-state.is-error,
.reference-form-notice.is-error {
  color: var(--ta-error);
}

.reference-state code,
.reference-compact-state code,
.reference-form-notice code {
  font-family: "Geist Mono", monospace;
  font-size: 10px;
}

.reference-form-notice {
  display: flex;
  flex-direction: column;
  gap: 3px;
  border: 1px solid var(--ta-border);
  border-radius: 5px;
  padding: 7px 8px;
  color: var(--ta-muted);
  font-size: 11px;
}

.reference-form-notice.is-loading {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  border-color: var(--ta-cyan);
  background: rgba(79, 111, 122, 0.06);
  color: var(--ta-cyan);
}

.reference-form-notice.is-success {
  color: var(--ta-ok);
}

@media (max-width: 780px) {
  .reference-automation-layout {
    grid-template-columns: 1fr;
  }
  .reference-repository-column {
    max-height: 38vh;
    border-right: 0;
    border-bottom: 1px solid var(--ta-border);
  }
  .reference-ready-layout {
    grid-template-columns: 1fr;
  }
  .reference-tree-panel {
    min-height: 180px;
    border-right: 0;
    border-bottom: 1px solid var(--ta-border);
  }
}
</style>
