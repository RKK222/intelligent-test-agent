<script setup lang="ts">
import { computed, inject, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  MemoryEvidenceView,
  MemorySkillProposalView,
  MemoryStatus,
  MemoryView
} from "@test-agent/shared-types";
import {
  Archive,
  BookOpenCheck,
  BrainCircuit,
  Check,
  ChevronRight,
  CircleAlert,
  Clock3,
  FileClock,
  FileSearch2,
  Pause,
  Pencil,
  Plus,
  RefreshCw,
  Send,
  ShieldCheck,
  Sparkles,
  UsersRound,
  X
} from "lucide-vue-next";

const props = defineProps<{
  selectedAppId?: string;
  canManageTeam: boolean;
}>();

const emit = defineEmits<{
  openSkillHub: [];
}>();

const api = inject<BackendApiClient>("api")!;
if (!api) throw new Error("MemoryCenter requires backend api");

type MemoryTab = "personal" | "team" | "skills";
type MemoryEditorMode = "personal" | "team" | "edit";

const STATUS_LABELS: Record<MemoryStatus, string> = {
  CANDIDATE: "待审核",
  PENDING_CONFIRMATION: "待确认",
  ACTIVE: "已生效",
  PAUSED: "已暂停",
  CONFLICTED: "有冲突",
  REJECTED: "已拒绝",
  ARCHIVED: "已归档",
  SUPERSEDED: "已被替代"
};

const tab = ref<MemoryTab>("personal");
const available = ref<boolean | null>(null);
const loading = ref(false);
const actionLoading = ref(false);
const loadError = ref("");
const personalMemories = ref<MemoryView[]>([]);
const teamMemories = ref<MemoryView[]>([]);
const skillProposals = ref<MemorySkillProposalView[]>([]);
const selectedMemory = ref<MemoryView | null>(null);
const evidence = ref<MemoryEvidenceView[]>([]);
const evidenceLoading = ref(false);
const detailOpen = ref(false);

const editorOpen = ref(false);
const editorMode = ref<MemoryEditorMode>("personal");
const editingMemory = ref<MemoryView | null>(null);
const editorContent = ref("");
const personalApplicationScope = ref(false);

const skillEditorOpen = ref(false);
const editingProposal = ref<MemorySkillProposalView | null>(null);
const skillTitle = ref("");
const skillDraft = ref("");

const visibleMemories = computed(() => tab.value === "personal" ? personalMemories.value : teamMemories.value);
const activeCount = computed(() => visibleMemories.value.filter((item) => item.status === "ACTIVE").length);
const attentionCount = computed(() => visibleMemories.value.filter((item) =>
  item.status === "PENDING_CONFIRMATION" || item.status === "CONFLICTED" || item.status === "CANDIDATE"
).length);
const appRequired = computed(() => tab.value !== "personal" && !props.selectedAppId);
const editorTitle = computed(() => editorMode.value === "edit"
  ? "编辑记忆"
  : editorMode.value === "team"
    ? "提交团队记忆"
    : "添加个人记忆");

onMounted(() => void initialize());
watch(() => props.selectedAppId, () => {
  if (tab.value !== "personal") void loadCurrentTab();
});
watch(tab, () => void loadCurrentTab());

async function initialize() {
  try {
    available.value = (await api.getQaMemoryAvailability()).enabled;
  } catch {
    available.value = false;
  }
  if (available.value) await loadCurrentTab();
}

async function loadCurrentTab() {
  if (available.value !== true) return;
  if ((tab.value === "team" || tab.value === "skills") && !props.selectedAppId) {
    loadError.value = "请先选择一个 Application";
    return;
  }
  loading.value = true;
  loadError.value = "";
  try {
    if (tab.value === "personal") {
      personalMemories.value = (await api.listPersonalMemories({ page: 1, size: 100 })).items;
    } else if (tab.value === "team") {
      teamMemories.value = (await api.listTeamMemories({
        applicationId: props.selectedAppId,
        page: 1,
        size: 100
      })).items;
    } else {
      skillProposals.value = (await api.listMemorySkillProposals({
        applicationId: props.selectedAppId,
        page: 1,
        size: 100
      })).items;
    }
  } catch {
    loadError.value = "记忆数据暂时不可用，请稍后重试";
  } finally {
    loading.value = false;
  }
}

function openCreate(mode: Exclude<MemoryEditorMode, "edit">) {
  if (mode === "team" && !props.selectedAppId) {
    ElMessage.warning("请先选择一个 Application");
    return;
  }
  editorMode.value = mode;
  editingMemory.value = null;
  editorContent.value = "";
  personalApplicationScope.value = mode === "personal" && Boolean(props.selectedAppId);
  editorOpen.value = true;
}

function openEdit(memory: MemoryView) {
  // displaySummary 只是服务降级时的治理摘要，绝不能作为完整正文回写覆盖 Mem0。
  if (!memory.contentAvailable) {
    ElMessage.warning("记忆正文暂时不可用，请刷新后再编辑");
    return;
  }
  editorMode.value = "edit";
  editingMemory.value = memory;
  editorContent.value = memory.content;
  personalApplicationScope.value = memory.scope === "PERSONAL_APPLICATION";
  editorOpen.value = true;
}

async function saveMemory() {
  const content = editorContent.value.trim();
  if (!content) {
    ElMessage.warning("请填写需要长期复用的信息或偏好");
    return;
  }
  if (editorMode.value === "personal" && personalApplicationScope.value && !props.selectedAppId) {
    ElMessage.warning("应用范围记忆需要先选择 Application");
    return;
  }
  actionLoading.value = true;
  try {
    if (editorMode.value === "edit" && editingMemory.value) {
      await api.updateQaMemory(editingMemory.value.memoryId, {
        content,
        expectedVersion: editingMemory.value.version
      });
    } else if (editorMode.value === "team") {
      await api.createTeamMemoryProposal({ applicationId: props.selectedAppId!, content });
    } else {
      await api.createPersonalMemory({
        scope: personalApplicationScope.value ? "PERSONAL_APPLICATION" : "PERSONAL_GLOBAL",
        applicationId: personalApplicationScope.value ? props.selectedAppId : null,
        content
      });
    }
    editorOpen.value = false;
    ElMessage.success(editorMode.value === "team" ? "团队记忆已提交审核" : "记忆已保存");
    await loadCurrentTab();
  } catch (error) {
    showActionError(error, "保存失败");
  } finally {
    actionLoading.value = false;
  }
}

async function openDetails(memory: MemoryView) {
  selectedMemory.value = memory;
  evidence.value = [];
  detailOpen.value = true;
  evidenceLoading.value = true;
  try {
    evidence.value = await api.listQaMemoryEvidence(memory.memoryId);
  } catch {
    ElMessage.warning("证据暂时无法加载");
  } finally {
    evidenceLoading.value = false;
  }
}

async function pauseMemory(memory: MemoryView) {
  await runMemoryAction(
    () => api.pausePersonalMemory(memory.memoryId, memory.version),
    "记忆已暂停"
  );
}

async function promoteGlobal(memory: MemoryView) {
  await runMemoryAction(
    () => api.promotePersonalMemoryGlobal(memory.memoryId, memory.version),
    "已提升为个人全局记忆"
  );
}

async function archiveMemory(memory: MemoryView) {
  try {
    await ElMessageBox.confirm("归档后不会再注入后续任务，仍保留审计记录。", "归档记忆", {
      confirmButtonText: "归档",
      cancelButtonText: "取消",
      type: "warning"
    });
  } catch {
    return;
  }
  await runMemoryAction(() => api.archiveQaMemory(memory.memoryId, memory.version), "记忆已归档");
}

async function reviewTeam(memory: MemoryView, decision: "APPROVE" | "REJECT") {
  if (!props.canManageTeam) return;
  let comment = "";
  if (decision === "REJECT") {
    try {
      const result = await ElMessageBox.prompt("请说明拒绝原因，便于提案人修订。", "拒绝团队候选", {
        inputType: "textarea",
        inputValidator: (value) => Boolean(value?.trim()) || "请填写原因"
      });
      comment = result.value.trim();
    } catch {
      return;
    }
  }
  await runMemoryAction(
    () => api.reviewTeamMemory(memory.memoryId, { decision, comment, expectedVersion: memory.version }),
    decision === "APPROVE" ? "团队记忆已批准" : "团队候选已拒绝"
  );
}

async function runMemoryAction(action: () => Promise<unknown>, success: string) {
  actionLoading.value = true;
  try {
    await action();
    ElMessage.success(success);
    detailOpen.value = false;
    await loadCurrentTab();
  } catch (error) {
    showActionError(error, "操作失败");
  } finally {
    actionLoading.value = false;
  }
}

async function proposeSkill(memory: MemoryView) {
  const applicationId = memory.applicationId ?? props.selectedAppId;
  if (!applicationId) {
    ElMessage.warning("沉淀为 Skill 前需要选择目标 Application");
    return;
  }
  try {
    const result = await ElMessageBox.prompt("提案只引用派生记忆，不会复制原始聊天。", "发起 Skill 提案", {
      inputValue: memory.displaySummary.slice(0, 80),
      inputPlaceholder: "Skill 标题",
      inputValidator: (value) => Boolean(value?.trim()) || "请填写标题"
    });
    actionLoading.value = true;
    await api.createMemorySkillProposal({ memoryId: memory.memoryId, applicationId, title: result.value.trim() });
    ElMessage.success("Skill 提案已提交");
    detailOpen.value = false;
    tab.value = "skills";
  } catch (error) {
    if (isCancel(error)) return;
    showActionError(error, "提案提交失败");
  } finally {
    actionLoading.value = false;
  }
}

async function reviewSkill(proposal: MemorySkillProposalView, decision: "APPROVE" | "REJECT") {
  if (!props.canManageTeam) return;
  actionLoading.value = true;
  try {
    await api.reviewMemorySkillProposal(proposal.proposalId, decision, proposal.version);
    ElMessage.success(decision === "APPROVE" ? "已生成可编辑 SKILL.md 草稿" : "Skill 提案已拒绝");
    await loadCurrentTab();
  } catch (error) {
    showActionError(error, "审核失败");
  } finally {
    actionLoading.value = false;
  }
}

function openSkillEditor(proposal: MemorySkillProposalView) {
  editingProposal.value = proposal;
  skillTitle.value = proposal.title;
  skillDraft.value = proposal.skillMdDraft;
  skillEditorOpen.value = true;
}

async function saveSkillDraft() {
  if (!editingProposal.value || !skillTitle.value.trim() || !skillDraft.value.trim()) {
    ElMessage.warning("标题和 SKILL.md 草稿不能为空");
    return;
  }
  actionLoading.value = true;
  try {
    await api.updateMemorySkillProposal(editingProposal.value.proposalId, {
      title: skillTitle.value.trim(),
      skillMdDraft: skillDraft.value,
      expectedVersion: editingProposal.value.version
    });
    skillEditorOpen.value = false;
    ElMessage.success("草稿已保存");
    await loadCurrentTab();
  } catch (error) {
    showActionError(error, "草稿保存失败");
  } finally {
    actionLoading.value = false;
  }
}

async function linkPublishedSkill(proposal: MemorySkillProposalView) {
  try {
    const result = await ElMessageBox.prompt("填写当前 Application 中已发布 Skill 的资产 ID。", "关联已发布 Skill", {
      inputPlaceholder: "assetId",
      inputValidator: (value) => Boolean(value?.trim()) || "请填写资产 ID"
    });
    actionLoading.value = true;
    await api.linkPublishedMemorySkill(proposal.proposalId, result.value.trim(), proposal.version);
    ElMessage.success("已关联发布资产");
    await loadCurrentTab();
  } catch (error) {
    if (isCancel(error)) return;
    showActionError(error, "关联失败");
  } finally {
    actionLoading.value = false;
  }
}

async function archiveSkill(proposal: MemorySkillProposalView) {
  try {
    await ElMessageBox.confirm("归档仅结束提案，不会撤回已经发布的 Skill。", "归档 Skill 提案");
  } catch {
    return;
  }
  actionLoading.value = true;
  try {
    await api.archiveMemorySkillProposal(proposal.proposalId, proposal.version);
    ElMessage.success("提案已归档");
    await loadCurrentTab();
  } catch (error) {
    showActionError(error, "归档失败");
  } finally {
    actionLoading.value = false;
  }
}

async function proposePersonalToTeam(memory: MemoryView) {
  if (!props.selectedAppId) {
    ElMessage.warning("请先选择一个 Application");
    return;
  }
  if (!memory.contentAvailable) {
    ElMessage.warning("记忆正文暂时不可用，请刷新后再提交团队候选");
    return;
  }
  try {
    await ElMessageBox.confirm(
      "将以当前正文提交团队候选，并仅复制来源 Session/Run 引用和摘要；原始对话不会进入记忆库。",
      "提交为团队记忆"
    );
  } catch {
    return;
  }
  actionLoading.value = true;
  try {
    await api.createTeamMemoryProposal({
      applicationId: props.selectedAppId,
      content: memory.content,
      sourceMemoryId: memory.memoryId
    });
    ElMessage.success("已提交团队候选，等待 APP_ADMIN 审核");
    detailOpen.value = false;
    tab.value = "team";
  } catch (error) {
    showActionError(error, "团队候选提交失败");
  } finally {
    actionLoading.value = false;
  }
}

function statusClass(status: MemoryStatus) {
  if (status === "CONFLICTED") return "conflict";
  if (status === "CANDIDATE" || status === "PENDING_CONFIRMATION") return "candidate";
  if (status === "ACTIVE") return "active";
  return "muted";
}

function scopeLabel(memory: MemoryView) {
  if (memory.scope === "TEAM_APPLICATION") return "团队 · 当前应用";
  if (memory.scope === "PERSONAL_APPLICATION") return "个人 · 指定应用";
  return "个人 · 全局";
}

function formatTime(value?: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function sessionHref(sessionId: string) {
  return `/s/${encodeURIComponent(sessionId)}`;
}

function isCancel(error: unknown) {
  return error === "cancel" || error === "close" || (error instanceof Error && /cancel|close/i.test(error.message));
}

function showActionError(error: unknown, fallback: string) {
  const message = error instanceof Error && error.message ? error.message : fallback;
  ElMessage.error(message);
}
</script>

<template>
  <section class="memory-center" aria-labelledby="memory-center-title" data-testid="memory-center">
    <header class="memory-hero">
      <div>
        <div class="memory-eyebrow"><BrainCircuit :size="15" /> MEMORY</div>
        <h1 id="memory-center-title">长期记忆</h1>
        <p>Mem0 从对话中原生学习通用信息和偏好；原始聊天只保留在平台会话中，团队记忆始终走人工审核。</p>
      </div>
      <button class="memory-refresh" type="button" :disabled="loading" aria-label="刷新记忆" @click="loadCurrentTab">
        <RefreshCw :size="16" :class="{ spinning: loading }" />
        刷新
      </button>
    </header>

    <div v-if="available === null" class="memory-state"><RefreshCw class="spinning" :size="20" />正在检查记忆能力</div>
    <div v-else-if="available === false" class="memory-state memory-state--quiet" data-testid="memory-unavailable">
      <BrainCircuit :size="28" />
      <strong>记忆能力尚未对当前账号开放</strong>
      <span>默认白名单为空，不影响现有对话；管理员开放后这里会自动可用。</span>
    </div>

    <template v-else>
      <nav class="memory-tabs" aria-label="记忆分类">
        <button type="button" :class="{ active: tab === 'personal' }" :aria-selected="tab === 'personal'" data-testid="memory-tab-personal" @click="tab = 'personal'">
          <BrainCircuit :size="17" />我的记忆
        </button>
        <button type="button" :class="{ active: tab === 'team' }" :aria-selected="tab === 'team'" data-testid="memory-tab-team" @click="tab = 'team'">
          <UsersRound :size="17" />团队记忆
        </button>
        <button type="button" :class="{ active: tab === 'skills' }" :aria-selected="tab === 'skills'" data-testid="memory-tab-skills" @click="tab = 'skills'">
          <Sparkles :size="17" />Skill 提案
        </button>
      </nav>

      <div v-if="tab !== 'skills'" class="memory-toolbar">
        <div class="memory-metrics" aria-live="polite">
          <span><strong>{{ visibleMemories.length }}</strong> 条记录</span>
          <span><strong>{{ activeCount }}</strong> 已生效</span>
          <span v-if="attentionCount"><strong>{{ attentionCount }}</strong> 待处理</span>
        </div>
        <button v-if="tab === 'personal'" class="memory-primary" type="button" data-testid="add-personal-memory" @click="openCreate('personal')">
          <Plus :size="16" />添加个人记忆
        </button>
        <button v-else class="memory-primary memory-primary--team" type="button" :disabled="!selectedAppId" data-testid="add-team-memory" @click="openCreate('team')">
          <Send :size="16" />提交团队记忆
        </button>
      </div>

      <div v-if="appRequired" class="memory-state memory-state--quiet">
        <FileSearch2 :size="26" />
        <strong>先选择 Application</strong>
        <span>团队记忆和 Skill 提案始终以 Application 为边界。</span>
      </div>
      <div v-else-if="loadError" class="memory-state memory-state--error">
        <CircleAlert :size="22" />{{ loadError }}
        <button type="button" @click="loadCurrentTab">重试</button>
      </div>
      <div v-else-if="loading" class="memory-state"><RefreshCw class="spinning" :size="20" />正在加载</div>

      <div v-else-if="tab !== 'skills' && visibleMemories.length" class="memory-grid" data-testid="memory-list">
        <article
          v-for="memory in visibleMemories"
          :key="memory.memoryId"
          :class="['memory-card', memory.scope === 'TEAM_APPLICATION' ? 'memory-card--team' : 'memory-card--personal']"
          :data-testid="`memory-card-${memory.memoryId}`"
        >
          <button class="memory-card__main" type="button" @click="openDetails(memory)">
            <span class="memory-card__topline">
              <span class="memory-scope">{{ scopeLabel(memory) }}</span>
              <span :class="['memory-status', statusClass(memory.status)]">{{ STATUS_LABELS[memory.status] }}</span>
            </span>
            <strong>{{ memory.displaySummary }}</strong>
            <span class="memory-card__meta">
              版本 {{ memory.version }} · 更新于 {{ formatTime(memory.updatedAt) }}
            </span>
          </button>
          <div class="memory-card__actions">
            <button
              type="button"
              aria-label="编辑记忆"
              :disabled="!memory.contentAvailable"
              :title="memory.contentAvailable ? undefined : '正文暂时不可用，请刷新后再编辑'"
              @click="openEdit(memory)"
            ><Pencil :size="15" />编辑</button>
            <button type="button" @click="openDetails(memory)">查看证据<ChevronRight :size="15" /></button>
          </div>
        </article>
      </div>
      <div v-else-if="tab !== 'skills'" class="memory-state memory-state--quiet">
        <BrainCircuit :size="28" />
        <strong>{{ tab === "personal" ? "还没有个人记忆" : "当前应用还没有团队记忆" }}</strong>
        <span>{{ tab === "personal" ? "可以手工添加，或在对话完成后由 Mem0 原生学习。" : "所有成员只能提交，APP_ADMIN 审核后才会对团队生效。" }}</span>
      </div>

      <div v-else-if="skillProposals.length" class="skill-proposal-list" data-testid="skill-proposal-list">
        <article v-for="proposal in skillProposals" :key="proposal.proposalId" class="skill-proposal-card">
          <div class="skill-proposal-card__icon"><BookOpenCheck :size="20" /></div>
          <div class="skill-proposal-card__body">
            <div class="skill-proposal-card__topline">
              <strong>{{ proposal.title }}</strong>
              <span :class="['skill-status', `skill-status--${proposal.status.toLowerCase()}`]">{{ proposal.status }}</span>
            </div>
            <p>来源记忆 {{ proposal.memoryId }} · 更新于 {{ formatTime(proposal.updatedAt) }}</p>
            <div class="skill-proposal-card__actions">
              <template v-if="proposal.status === 'PENDING_REVIEW' && canManageTeam">
                <button class="approve" type="button" :disabled="actionLoading" @click="reviewSkill(proposal, 'APPROVE')"><Check :size="15" />批准并生成草稿</button>
                <button type="button" :disabled="actionLoading" @click="reviewSkill(proposal, 'REJECT')"><X :size="15" />拒绝</button>
              </template>
              <template v-if="proposal.status === 'DRAFT'">
                <button type="button" @click="openSkillEditor(proposal)"><Pencil :size="15" />编辑 SKILL.md</button>
                <button type="button" @click="emit('openSkillHub')"><Sparkles :size="15" />前往 Skill Hub 发布</button>
                <button v-if="canManageTeam" type="button" @click="linkPublishedSkill(proposal)"><ShieldCheck :size="15" />关联已发布资产</button>
              </template>
              <button v-if="proposal.status !== 'ARCHIVED'" type="button" @click="archiveSkill(proposal)"><Archive :size="15" />归档</button>
            </div>
          </div>
        </article>
      </div>
      <div v-else-if="tab === 'skills'" class="memory-state memory-state--quiet">
        <Sparkles :size="28" />
        <strong>当前应用还没有 Skill 提案</strong>
        <span>从已生效的个人或团队记忆详情中发起；审核后才生成可编辑草稿。</span>
        <button type="button" @click="emit('openSkillHub')">打开 Skill Hub</button>
      </div>
    </template>

    <el-dialog v-model="editorOpen" class="memory-editor-dialog" :title="editorTitle" width="min(620px, calc(100vw - 32px))" append-to-body>
      <form class="memory-editor" @submit.prevent="saveMemory">
        <label>
          <span>长期信息或偏好</span>
          <textarea v-model="editorContent" rows="5" maxlength="2000" placeholder="例如：回答时优先使用中文，并先给结论再说明依据。" />
          <small>{{ editorContent.length }}/2000；原始对话不会复制到记忆库</small>
        </label>
        <fieldset v-if="editorMode === 'personal'">
          <legend>适用范围</legend>
          <label class="memory-radio"><input v-model="personalApplicationScope" type="radio" :value="false" />所有应用</label>
          <label class="memory-radio"><input v-model="personalApplicationScope" type="radio" :value="true" :disabled="!selectedAppId" />当前应用</label>
        </fieldset>
        <p v-if="editorMode === 'team'" class="memory-editor__notice">团队记忆提交后保持待审核状态，即使 APP_ADMIN 提交也不能绕过审核。</p>
      </form>
      <template #footer>
        <button class="memory-secondary" type="button" @click="editorOpen = false">取消</button>
        <button class="memory-primary" type="button" :disabled="actionLoading" @click="saveMemory">{{ actionLoading ? "保存中" : "保存" }}</button>
      </template>
    </el-dialog>

    <el-dialog v-model="skillEditorOpen" class="memory-editor-dialog" title="编辑 Skill 草稿" width="min(780px, calc(100vw - 32px))" append-to-body>
      <form class="memory-editor" @submit.prevent="saveSkillDraft">
        <label><span>标题</span><input v-model="skillTitle" maxlength="200" /></label>
        <label><span>SKILL.md</span><textarea v-model="skillDraft" class="skill-draft" rows="18" maxlength="100000" spellcheck="false" /></label>
        <p class="memory-editor__notice">保存草稿不会自动提交、发布或写入工作区；请在 Skill Hub 中继续既有发布流程。</p>
      </form>
      <template #footer>
        <button class="memory-secondary" type="button" @click="skillEditorOpen = false">取消</button>
        <button class="memory-primary" type="button" :disabled="actionLoading" @click="saveSkillDraft">保存草稿</button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailOpen" class="memory-detail-drawer" size="min(520px, 100vw)" append-to-body data-testid="memory-detail-drawer">
      <template #header>
        <div class="memory-detail__header">
          <span>{{ selectedMemory ? scopeLabel(selectedMemory) : "记忆详情" }}</span>
          <strong>{{ selectedMemory?.displaySummary }}</strong>
        </div>
      </template>
      <div v-if="selectedMemory" class="memory-detail">
        <div class="memory-detail__summary">
          <span :class="['memory-status', statusClass(selectedMemory.status)]">{{ STATUS_LABELS[selectedMemory.status] }}</span>
          <p>{{ selectedMemory.contentAvailable ? selectedMemory.content : selectedMemory.displaySummary }}</p>
          <small v-if="!selectedMemory.contentAvailable">派生正文服务暂时不可用，当前展示治理摘要。</small>
        </div>

        <h2>证据链</h2>
        <div class="evidence-rail" data-testid="memory-evidence-rail">
          <article v-for="item in evidence" :key="item.evidenceId" class="evidence-node evidence-node--observation">
            <span class="evidence-node__dot"><FileSearch2 :size="14" /></span>
            <div>
              <strong>{{ item.sessionTitle || "未命名对话" }}</strong>
              <p>{{ item.summary }}</p>
              <small>会话 ID {{ item.sessionId || "—" }} · Run ID {{ item.runId || "—" }} · {{ formatTime(item.observedAt) }}</small>
              <a v-if="item.transcriptAvailable && item.sessionId" class="evidence-session-link" :href="sessionHref(item.sessionId)">打开原始对话</a>
              <span v-else class="evidence-session-unavailable">仅会话所有者可打开原始对话</span>
            </div>
          </article>
          <article v-if="evidenceLoading" class="evidence-node"><span class="evidence-node__dot"><RefreshCw class="spinning" :size="14" /></span><div><strong>读取证据</strong></div></article>
          <article v-else-if="!evidence.length" class="evidence-node"><span class="evidence-node__dot"><FileClock :size="14" /></span><div><strong>暂无可展示证据</strong><p>手工添加的记忆可以直接成为事实，不依赖原始聊天副本。</p></div></article>
          <article v-if="selectedMemory.confirmedAt" class="evidence-node evidence-node--confirmation">
            <span class="evidence-node__dot"><Check :size="14" /></span><div><strong>生效</strong><p>这条记忆已进入可检索状态。</p><small>{{ formatTime(selectedMemory.confirmedAt) }}</small></div>
          </article>
          <article class="evidence-node evidence-node--usage">
            <span class="evidence-node__dot"><BrainCircuit :size="14" /></span><div><strong>使用</strong><p>只有实际注入 Run 的记忆才会记账，并在对应对话结果显示“参考了 N 条记忆”。</p></div>
          </article>
          <article class="evidence-node evidence-node--change">
            <span class="evidence-node__dot"><Clock3 :size="14" /></span><div><strong>最近变更</strong><p>版本 {{ selectedMemory.version }} · {{ STATUS_LABELS[selectedMemory.status] }}</p><small>{{ formatTime(selectedMemory.updatedAt) }}</small></div>
          </article>
        </div>

        <div class="memory-detail__actions">
          <button v-if="selectedMemory.scope === 'PERSONAL_APPLICATION'" type="button" :disabled="actionLoading" @click="promoteGlobal(selectedMemory)"><BrainCircuit :size="15" />提升为个人全局</button>
          <button
            v-if="selectedMemory.scope !== 'TEAM_APPLICATION' && selectedMemory.status === 'ACTIVE' && selectedAppId"
            type="button"
            :disabled="actionLoading || !selectedMemory.contentAvailable"
            :title="selectedMemory.contentAvailable ? undefined : '正文暂时不可用，请刷新后再提交'"
            @click="proposePersonalToTeam(selectedMemory)"
          ><UsersRound :size="15" />提交为团队记忆</button>
          <button v-if="selectedMemory.scope !== 'TEAM_APPLICATION' && selectedMemory.status === 'ACTIVE'" type="button" :disabled="actionLoading" @click="pauseMemory(selectedMemory)"><Pause :size="15" />暂停使用</button>
          <template v-if="selectedMemory.scope === 'TEAM_APPLICATION' && canManageTeam && (selectedMemory.status === 'CANDIDATE' || selectedMemory.status === 'PENDING_CONFIRMATION' || selectedMemory.status === 'CONFLICTED')">
            <button class="memory-primary memory-primary--team" type="button" :disabled="actionLoading" @click="reviewTeam(selectedMemory, 'APPROVE')"><Check :size="15" />批准</button>
            <button type="button" :disabled="actionLoading" @click="reviewTeam(selectedMemory, 'REJECT')"><X :size="15" />拒绝</button>
          </template>
          <button v-if="selectedMemory.status === 'ACTIVE'" type="button" @click="proposeSkill(selectedMemory)"><Sparkles :size="15" />沉淀为 Skill</button>
          <button v-if="!['ARCHIVED', 'SUPERSEDED'].includes(selectedMemory.status)" type="button" @click="archiveMemory(selectedMemory)"><Archive :size="15" />归档</button>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.memory-center {
  --memory-personal: #4f6bed;
  --memory-team: #0f8f88;
  --memory-candidate: #b7791f;
  --memory-conflict: #c2414b;
  min-height: 100%;
  padding: clamp(18px, 3vw, 36px);
  overflow: auto;
  background: var(--ta-shell-canvas, #f7f9fc);
  color: var(--ta-shell-text, #333a48);
}
.memory-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  max-width: 1180px;
  margin: 0 auto 22px;
}
.memory-hero h1 { margin: 5px 0 7px; font-size: clamp(24px, 3vw, 34px); letter-spacing: -0.035em; color: var(--ta-shell-header-text, #111827); }
.memory-hero p { max-width: 720px; margin: 0; color: var(--ta-shell-muted, #6b7280); font-size: 13px; line-height: 1.7; }
.memory-eyebrow { display: flex; align-items: center; gap: 7px; color: var(--memory-personal); font: 700 11px/1.2 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .12em; }
.memory-refresh,
.memory-primary,
.memory-secondary,
.memory-state button,
.memory-card__actions button,
.skill-proposal-card__actions button,
.memory-detail__actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid var(--ta-shell-border, #e5e7eb);
  border-radius: 7px;
  background: var(--ta-shell-surface, #fff);
  color: var(--ta-shell-text, #374151);
  font-size: 12px;
  cursor: pointer;
}
button:focus-visible, input:focus-visible, textarea:focus-visible { outline: 2px solid var(--memory-personal); outline-offset: 2px; }
button:disabled { cursor: not-allowed; opacity: .5; }
.memory-refresh { align-self: flex-start; }
.memory-refresh:hover,
.memory-secondary:hover,
.memory-card__actions button:hover,
.skill-proposal-card__actions button:hover,
.memory-detail__actions button:hover { background: var(--ta-shell-hover, #f3f4f6); }
.memory-primary { border-color: var(--memory-personal); background: var(--memory-personal); color: #fff; font-weight: 650; }
.memory-primary--team { border-color: var(--memory-team); background: var(--memory-team); }
.memory-tabs {
  display: flex;
  gap: 4px;
  max-width: 1180px;
  margin: 0 auto;
  padding: 5px;
  border: 1px solid var(--ta-shell-border, #e5e7eb);
  border-radius: 10px;
  background: color-mix(in srgb, var(--ta-shell-surface, #fff) 92%, transparent);
}
.memory-tabs button { display: flex; align-items: center; justify-content: center; gap: 7px; flex: 1; min-height: 40px; border: 0; border-radius: 7px; background: transparent; color: var(--ta-shell-muted, #6b7280); cursor: pointer; font-weight: 600; }
.memory-tabs button:hover { background: var(--ta-shell-hover, #f3f4f6); }
.memory-tabs button.active { color: var(--ta-shell-header-text, #111827); background: var(--ta-shell-surface, #fff); box-shadow: 0 1px 5px rgba(15, 23, 42, .08); }
.memory-tabs button:nth-child(1).active svg { color: var(--memory-personal); }
.memory-tabs button:nth-child(2).active svg { color: var(--memory-team); }
.memory-tabs button:nth-child(3).active svg { color: var(--memory-candidate); }
.memory-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; max-width: 1180px; margin: 18px auto 12px; }
.memory-metrics { display: flex; gap: 16px; color: var(--ta-shell-muted, #6b7280); font-size: 12px; }
.memory-metrics strong { color: var(--ta-shell-header-text, #111827); font-size: 14px; }
.memory-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr)); gap: 12px; max-width: 1180px; margin: 0 auto; }
.memory-card { position: relative; overflow: hidden; border: 1px solid var(--ta-shell-border, #e5e7eb); border-radius: 10px; background: color-mix(in srgb, var(--ta-shell-surface, #fff) 96%, transparent); box-shadow: var(--ta-shell-shadow, 0 1px 2px rgba(15,23,42,.04)); }
.memory-card::before { position: absolute; inset: 0 auto 0 0; width: 3px; content: ""; background: var(--memory-personal); }
.memory-card--team::before { background: var(--memory-team); }
.memory-card__main { display: flex; flex-direction: column; align-items: stretch; gap: 11px; width: 100%; min-height: 170px; padding: 17px 18px 14px 20px; border: 0; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.memory-card__main:hover { background: var(--ta-shell-hover, rgba(22,119,255,.04)); }
.memory-card__topline { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.memory-scope { color: var(--ta-shell-muted, #6b7280); font-size: 11px; }
.memory-status, .skill-status { display: inline-flex; align-items: center; border-radius: 999px; padding: 3px 8px; background: var(--ta-shell-hover, #f3f4f6); color: var(--ta-shell-muted, #6b7280); font-size: 10px; font-weight: 700; }
.memory-status.active { background: color-mix(in srgb, var(--memory-team) 12%, transparent); color: var(--memory-team); }
.memory-status.candidate { background: color-mix(in srgb, var(--memory-candidate) 14%, transparent); color: var(--memory-candidate); }
.memory-status.conflict { background: color-mix(in srgb, var(--memory-conflict) 12%, transparent); color: var(--memory-conflict); }
.memory-card__main > strong { min-height: 42px; color: var(--ta-shell-header-text, #111827); font-size: 14px; line-height: 1.55; }
.memory-card__meta { margin-top: auto; color: var(--ta-shell-muted, #6b7280); font-size: 11px; }
.memory-card__actions { display: flex; justify-content: space-between; padding: 8px 10px; border-top: 1px solid var(--ta-shell-border, #e5e7eb); }
.memory-card__actions button { min-height: 28px; padding: 0 8px; border: 0; background: transparent; }
.memory-state { display: flex; align-items: center; justify-content: center; gap: 9px; min-height: 220px; color: var(--ta-shell-muted, #6b7280); }
.memory-state--quiet { flex-direction: column; max-width: 1180px; min-height: 300px; margin: 18px auto 0; border: 1px dashed var(--ta-shell-border-strong, #d1d5db); border-radius: 10px; background: color-mix(in srgb, var(--ta-shell-surface, #fff) 72%, transparent); text-align: center; }
.memory-state--quiet strong { color: var(--ta-shell-header-text, #111827); font-size: 15px; }
.memory-state--quiet span { max-width: 520px; font-size: 12px; line-height: 1.6; }
.memory-state--error { color: var(--memory-conflict); }
.skill-proposal-list { display: grid; gap: 10px; max-width: 1180px; margin: 18px auto; }
.skill-proposal-card { display: flex; gap: 14px; padding: 16px; border: 1px solid var(--ta-shell-border, #e5e7eb); border-radius: 10px; background: var(--ta-shell-surface, #fff); }
.skill-proposal-card__icon { display: grid; place-items: center; width: 40px; height: 40px; flex: 0 0 auto; border-radius: 9px; background: color-mix(in srgb, var(--memory-candidate) 12%, transparent); color: var(--memory-candidate); }
.skill-proposal-card__body { flex: 1; min-width: 0; }
.skill-proposal-card__topline { display: flex; justify-content: space-between; align-items: center; gap: 12px; color: var(--ta-shell-header-text, #111827); }
.skill-proposal-card p { color: var(--ta-shell-muted, #6b7280); font-size: 11px; }
.skill-proposal-card__actions { display: flex; flex-wrap: wrap; gap: 7px; }
.skill-proposal-card__actions .approve { border-color: var(--memory-team); color: var(--memory-team); }
.skill-status--pending_review { color: var(--memory-candidate); }
.skill-status--draft { color: var(--memory-personal); }
.skill-status--rejected { color: var(--memory-conflict); }
.skill-status--published { color: var(--memory-team); }
.memory-editor { display: grid; gap: 18px; }
.memory-editor > label { display: grid; gap: 7px; color: var(--ta-shell-text, #374151); font-size: 12px; font-weight: 650; }
.memory-editor textarea, .memory-editor > label > input { width: 100%; border: 1px solid var(--ta-shell-border-strong, #d1d5db); border-radius: 7px; padding: 10px 11px; background: var(--ta-shell-surface, #fff); color: var(--ta-shell-text, #374151); font: inherit; font-weight: 400; resize: vertical; }
.memory-editor small, .memory-editor__notice { margin: 0; color: var(--ta-shell-muted, #6b7280); font-size: 11px; font-weight: 400; }
.memory-editor fieldset { margin: 0; border: 1px solid var(--ta-shell-border, #e5e7eb); border-radius: 8px; padding: 11px; }
.memory-editor legend { padding: 0 5px; color: var(--ta-shell-text, #374151); font-size: 12px; font-weight: 650; }
.memory-radio { display: inline-flex; align-items: center; gap: 6px; margin-right: 18px; font-size: 12px; }
.memory-switch { grid-template-columns: auto 1fr !important; align-items: center; }
.memory-switch span { display: grid; }
.skill-draft { font-family: ui-monospace, SFMono-Regular, Consolas, monospace !important; font-size: 12px !important; line-height: 1.6; }
.memory-detail__header { display: grid; gap: 4px; padding-right: 20px; }
.memory-detail__header span { color: var(--ta-shell-muted, #6b7280); font-size: 11px; }
.memory-detail__header strong { color: var(--ta-shell-header-text, #111827); font-size: 14px; line-height: 1.4; }
.memory-detail { color: var(--ta-shell-text, #374151); }
.memory-detail__summary { padding: 14px; border: 1px solid var(--ta-shell-border, #e5e7eb); border-radius: 9px; background: var(--ta-shell-surface, #fff); }
.memory-detail__summary p { margin: 12px 0 5px; font-size: 13px; line-height: 1.7; white-space: pre-wrap; }
.memory-detail__summary small { color: var(--ta-shell-muted, #6b7280); }
.memory-detail h2 { margin: 25px 0 13px; font-size: 13px; }
.evidence-rail { position: relative; display: grid; gap: 0; padding-left: 7px; }
.evidence-rail::before { position: absolute; top: 14px; bottom: 18px; left: 20px; width: 1px; content: ""; background: var(--ta-shell-border-strong, #d1d5db); }
.evidence-node { position: relative; display: grid; grid-template-columns: 28px 1fr; gap: 12px; padding-bottom: 19px; }
.evidence-node__dot { z-index: 1; display: grid; place-items: center; width: 28px; height: 28px; border: 1px solid var(--ta-shell-border-strong, #d1d5db); border-radius: 50%; background: var(--ta-shell-surface, #fff); color: var(--ta-shell-muted, #6b7280); }
.evidence-node--observation .evidence-node__dot { border-color: var(--memory-candidate); color: var(--memory-candidate); }
.evidence-node--confirmation .evidence-node__dot { border-color: var(--memory-team); color: var(--memory-team); }
.evidence-node--usage .evidence-node__dot { border-color: var(--memory-personal); color: var(--memory-personal); }
.evidence-node--change .evidence-node__dot { border-color: var(--memory-conflict); color: var(--memory-conflict); }
.evidence-node strong { color: var(--ta-shell-header-text, #111827); font-size: 12px; }
.evidence-node p { margin: 5px 0; font-size: 12px; line-height: 1.55; }
.evidence-node small { color: var(--ta-shell-muted, #6b7280); font-size: 10px; }
.evidence-session-link, .evidence-session-unavailable { display: block; width: fit-content; margin-top: 7px; font-size: 10px; }
.evidence-session-link { color: var(--memory-personal); font-weight: 650; text-decoration: none; }
.evidence-session-link:hover { text-decoration: underline; }
.evidence-session-unavailable { color: var(--ta-shell-muted, #6b7280); }
.memory-detail__actions { position: sticky; bottom: 0; display: flex; flex-wrap: wrap; gap: 7px; margin: 10px -20px -20px; padding: 12px 20px; border-top: 1px solid var(--ta-shell-border, #e5e7eb); background: color-mix(in srgb, var(--ta-shell-surface, #fff) 94%, transparent); backdrop-filter: blur(8px); }
.spinning { animation: memory-spin .9s linear infinite; }
@keyframes memory-spin { to { transform: rotate(360deg); } }
:global(.dark) .memory-center { --ta-shell-surface: #17191f; --ta-shell-header-text: #f3f4f6; --ta-shell-text: #d1d5db; --ta-shell-muted: #9ca3af; --ta-shell-border: #2d313a; --ta-shell-border-strong: #3f4551; --ta-shell-hover: rgba(255,255,255,.06); --ta-shell-canvas: #111318; }
/* Drawer/Dialog 会 Teleport 到 body，需要在完整全局选择器上重新提供页面主题变量。 */
:global(.dark .memory-detail-drawer),
:global(.dark .memory-editor-dialog) {
  --ta-shell-surface: #17191f;
  --ta-shell-header-text: #f3f4f6;
  --ta-shell-text: #d1d5db;
  --ta-shell-muted: #9ca3af;
  --ta-shell-border: #2d313a;
  --ta-shell-border-strong: #3f4551;
  --ta-shell-hover: rgba(255,255,255,.06);
  --el-bg-color: #17191f;
  --el-dialog-bg-color: #17191f;
  --el-drawer-bg-color: #17191f;
  --el-text-color-primary: #f3f4f6;
  --el-text-color-regular: #d1d5db;
  --el-border-color: #2d313a;
  background: #17191f;
  color: #d1d5db;
}
:global(.dark .memory-detail-drawer.el-drawer),
:global(.dark .memory-detail-drawer.el-drawer .el-drawer__header),
:global(.dark .memory-detail-drawer.el-drawer .el-drawer__body),
:global(.dark .memory-editor-dialog.el-dialog),
:global(.dark .memory-editor-dialog.el-dialog .el-dialog__header),
:global(.dark .memory-editor-dialog.el-dialog .el-dialog__body),
:global(.dark .memory-editor-dialog.el-dialog .el-dialog__footer) {
  background: #17191f;
  color: #d1d5db;
}
@media (max-width: 640px) {
  .memory-center { padding: 16px 10px; }
  .memory-hero { align-items: flex-start; }
  .memory-hero p { display: none; }
  .memory-refresh { min-width: 34px; padding: 0 9px; font-size: 0; }
  .memory-tabs button { font-size: 11px; }
  .memory-toolbar { align-items: flex-start; }
  .memory-metrics { flex-direction: column; gap: 2px; }
  .skill-proposal-card { padding: 12px; }
  .skill-proposal-card__icon { display: none; }
  :global(.memory-detail-drawer) { width: 100vw !important; }
}
@media (prefers-reduced-motion: reduce) {
  .spinning { animation: none; }
  *, *::before, *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; animation-duration: .01ms !important; animation-iteration-count: 1 !important; }
}
</style>
