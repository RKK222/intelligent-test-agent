<script setup lang="ts">
import { computed, inject, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  InternalModelProviderModel,
  MemoryAdminHealth,
  MemorySettingsView,
  MemoryWhitelistView,
  UserManagementUser
} from "@test-agent/shared-types";
import {
  BrainCircuit,
  CheckCircle2,
  ChevronDown,
  CircleAlert,
  Cpu,
  Database,
  LoaderCircle,
  MessageSquareText,
  Plus,
  RefreshCw,
  Save,
  Trash2,
  UsersRound
} from "lucide-vue-next";

const api = inject<BackendApiClient>("api")!;
if (!api) throw new Error("MemoryAdminPanel requires backend api");

const loading = ref(true);
const saving = ref(false);
const error = ref("");
const health = ref<MemoryAdminHealth | null>(null);
const settings = ref<MemorySettingsView | null>(null);
const whitelist = ref<MemoryWhitelistView[]>([]);
const chatModelId = ref<string | null>("");
const enterpriseEmbeddingModelId = ref<string | null>("");
const chatModels = ref<ChatModelOption[]>([]);
const embeddingModels = ref<ChatModelOption[]>([]);
const chatModelsLoading = ref(false);
const chatModelsError = ref("");
const technicalDetailsOpen = ref(false);
const whitelistDialogOpen = ref(false);
const whitelistUsers = ref<UserManagementUser[]>([]);
const whitelistUsersLoading = ref(false);
const whitelistUserError = ref("");
const selectedWhitelistUserId = ref("");
const addingWhitelistUser = ref(false);
let chatModelRequest = 0;
let whitelistUserSearchRequest = 0;

type ChatModelOption = InternalModelProviderModel & {
  providerName: string;
  providerSortOrder: number;
};

const selectedChatModelUnavailable = computed(() => Boolean(chatModelId.value)
  && !chatModels.value.some((model) => model.modelId === chatModelId.value));
const selectedEmbeddingModelUnavailable = computed(() => Boolean(enterpriseEmbeddingModelId.value)
  && !embeddingModels.value.some((model) => model.modelId === enterpriseEmbeddingModelId.value));

onMounted(() => void refreshAll());

async function refreshAll() {
  await Promise.all([load(), loadChatModels()]);
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [healthView, settingsView, whitelistPage] = await Promise.all([
      api.getQaMemoryAdminHealth(),
      api.getQaMemorySettings(),
      api.listQaMemoryWhitelist(1, 100)
    ]);
    health.value = healthView;
    settings.value = settingsView;
    whitelist.value = whitelistPage.items;
    chatModelId.value = settingsView.primaryChatModelId ?? "";
    enterpriseEmbeddingModelId.value = settingsView.primaryEmbeddingModelId ?? "";
  } catch {
    error.value = "记忆管理数据加载失败";
  } finally {
    loading.value = false;
  }
}

/**
 * 固定抽取模型只能从已启用、已配置凭据且 CHAT 探测成功的内部模型中选择。
 * 后端仍会在保存与使用时再次校验，前端筛选仅用于避免管理员误填不可路由的模型 ID。
 */
async function loadChatModels() {
  const requestId = ++chatModelRequest;
  chatModelsLoading.value = true;
  chatModelsError.value = "";
  try {
    const response = await api.getInternalModelProviders();
    const providers = response.providers
      .filter((provider) => provider.enabled && provider.tokenConfigured !== false)
      .sort((left, right) => left.sortOrder - right.sortOrder);
    const results = await Promise.allSettled(providers.map(async (provider) => ({
      provider,
      models: await api.getInternalModelProviderModels(provider.providerId)
    })));
    if (requestId !== chatModelRequest) return;

    const options = new Map<string, ChatModelOption>();
    const embeddingOptions = new Map<string, ChatModelOption>();
    for (const result of results) {
      if (result.status !== "fulfilled") continue;
      const { provider, models } = result.value;
      for (const model of models) {
        if (!model.enabled) continue;
        const option = {
          ...model,
          providerName: provider.name,
          providerSortOrder: provider.sortOrder
        };
        if (model.probedCapabilities.includes("CHAT") && !options.has(model.modelId)) {
          options.set(model.modelId, option);
        }
        if (model.probedCapabilities.includes("EMBEDDING")
          && Number(model.embeddingDimension) > 0
          && !embeddingOptions.has(model.modelId)) {
          embeddingOptions.set(model.modelId, option);
        }
      }
    }
    chatModels.value = [...options.values()].sort((left, right) =>
      left.providerSortOrder - right.providerSortOrder
      || left.displayName.localeCompare(right.displayName, "zh-CN"));
    embeddingModels.value = [...embeddingOptions.values()].sort((left, right) =>
      left.providerSortOrder - right.providerSortOrder
      || left.displayName.localeCompare(right.displayName, "zh-CN"));
    if (results.some((result) => result.status === "rejected")) {
      chatModelsError.value = chatModels.value.length
        ? "部分内部模型目录暂不可用，已显示其余可选模型。"
        : "内部模型目录暂不可用，可保留当前配置后重试。";
    }
  } catch {
    if (requestId === chatModelRequest) {
      chatModels.value = [];
      chatModelsError.value = "内部模型目录暂不可用，可保留当前配置后重试。";
    }
  } finally {
    if (requestId === chatModelRequest) chatModelsLoading.value = false;
  }
}

async function saveSettings() {
  if (!settings.value) return;
  saving.value = true;
  try {
    settings.value = await api.updateQaMemorySettings({
      primaryChatModelId: chatModelId.value?.trim() || null,
      primaryEmbeddingModelId: enterpriseEmbeddingModelId.value?.trim() || null,
      expectedVersion: settings.value.version
    });
    ElMessage.success("记忆模型策略已保存");
    await load();
  } catch (caught) {
    ElMessage.error(caught instanceof Error ? caught.message : "设置保存失败");
  } finally {
    saving.value = false;
  }
}

function openWhitelistDialog() {
  selectedWhitelistUserId.value = "";
  whitelistUserError.value = "";
  whitelistDialogOpen.value = true;
  void loadWhitelistUsers();
}

/** 搜索平台用户时只保留可登录且尚未加入白名单的用户，并丢弃过期请求结果。 */
async function loadWhitelistUsers(keyword = "") {
  const requestId = ++whitelistUserSearchRequest;
  whitelistUsersLoading.value = true;
  whitelistUserError.value = "";
  try {
    const page = await api.listUsers({ keyword: keyword.trim(), page: 1, size: 30 });
    if (requestId !== whitelistUserSearchRequest) return;
    const enabledUserIds = new Set(whitelist.value.map((user) => user.userId));
    whitelistUsers.value = page.items.filter((user) =>
      user.status === "ACTIVE" && !enabledUserIds.has(user.userId));
  } catch (caught) {
    if (requestId === whitelistUserSearchRequest) {
      whitelistUsers.value = [];
      whitelistUserError.value = caught instanceof Error ? caught.message : "用户目录加载失败";
    }
  } finally {
    if (requestId === whitelistUserSearchRequest) whitelistUsersLoading.value = false;
  }
}

function whitelistUserOptionLabel(user: UserManagementUser) {
  return `${user.username} · ${user.unifiedAuthId || "无统一认证号"} · ${user.userId}`;
}

async function addWhitelistUser() {
  if (!selectedWhitelistUserId.value) return;
  addingWhitelistUser.value = true;
  try {
    await api.enableQaMemoryUser(selectedWhitelistUserId.value);
    ElMessage.success("用户已加入记忆白名单");
    whitelistDialogOpen.value = false;
    await load();
  } catch (caught) {
    whitelistUserError.value = caught instanceof Error ? caught.message : "添加失败";
  } finally {
    addingWhitelistUser.value = false;
  }
}

async function removeWhitelistUser(user: MemoryWhitelistView) {
  try {
    await ElMessageBox.confirm(`确定停止为 ${user.userId} 提供记忆能力吗？已有记忆不会被删除。`, "移出白名单", {
      type: "warning",
      confirmButtonText: "移出",
      cancelButtonText: "取消"
    });
  } catch {
    return;
  }
  try {
    await api.disableQaMemoryUser(user.userId);
    ElMessage.success("用户已移出白名单");
    await load();
  } catch (caught) {
    ElMessage.error(caught instanceof Error ? caught.message : "移除失败");
  }
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
</script>

<template>
  <section class="memory-admin" aria-labelledby="memory-admin-title" data-testid="memory-admin-panel">
    <header class="memory-admin__header">
      <div>
        <div class="memory-admin__eyebrow"><BrainCircuit :size="15" /> MEMORY CONTROL</div>
        <h2 id="memory-admin-title">记忆能力</h2>
        <p>检查 Mem0 多节点、双 Embedding profile、原生抽取 CHAT 模型和投影积压，并按用户灰度开放。</p>
      </div>
      <button type="button" :disabled="loading" @click="refreshAll"><RefreshCw :size="15" :class="{ spinning: loading }" />刷新</button>
    </header>

    <div v-if="loading" class="memory-admin__state"><LoaderCircle class="spinning" :size="22" />正在检查服务</div>
    <div v-else-if="error" class="memory-admin__state memory-admin__state--error"><CircleAlert :size="22" />{{ error }}<button type="button" @click="load">重试</button></div>
    <template v-else-if="health && settings">
      <div class="memory-health-grid">
        <article :class="{ healthy: health.memoryService.available }" data-testid="memory-health-mem0">
          <span class="memory-health-icon"><Database :size="20" /></span>
          <div><small>MEM0 + PGVECTOR</small><strong>{{ health.memoryService.available ? "就绪" : "不可用" }}</strong><p>{{ health.memoryService.status }} · {{ health.memoryService.version ?? "版本未知" }}</p></div>
          <CheckCircle2 v-if="health.memoryService.available" class="health-check" :size="18" />
          <CircleAlert v-else class="health-alert" :size="18" />
        </article>
        <article :class="{ healthy: health.memoryService.profiles.some((profile) => profile.available) }" data-testid="memory-health-embedding">
          <span class="memory-health-icon"><Cpu :size="20" /></span>
          <div><small>EMBEDDING PROFILES</small><strong>{{ health.memoryService.profiles.filter((profile) => profile.available).length }} / {{ health.memoryService.profiles.length }} 可用</strong><p>CPU 热备：{{ health.cpuEmbeddingModelId }}</p></div>
          <CheckCircle2 v-if="health.memoryService.profiles.some((profile) => profile.available)" class="health-check" :size="18" />
          <CircleAlert v-else class="health-alert" :size="18" />
        </article>
        <article :class="{ healthy: Boolean(health.primaryChatModelId) }" data-testid="memory-health-chat">
          <span class="memory-health-icon"><MessageSquareText :size="20" /></span>
          <div><small>FIXED CHAT MODEL</small><strong>{{ health.primaryChatModelId || "未配置" }}</strong><p>Mem0 原生 infer=true，不使用平台自定义抽取提示词</p></div>
          <CheckCircle2 v-if="health.primaryChatModelId" class="health-check" :size="18" />
          <CircleAlert v-else class="health-alert" :size="18" />
        </article>
        <article :class="{ healthy: health.queueDead === 0 }" data-testid="memory-health-queue">
          <span class="memory-health-icon"><BrainCircuit :size="20" /></span>
          <div><small>LEARNING OUTBOX</small><strong>{{ health.queuePending }} 待处理</strong><p>{{ health.queueProcessing }} 处理中 · {{ health.queueDead }} 死信</p></div>
          <CheckCircle2 v-if="health.queueDead === 0" class="health-check" :size="18" />
          <CircleAlert v-else class="health-alert" :size="18" />
        </article>
        <article :class="{ healthy: health.memoryService.projectionBacklog.dead === 0 }" data-testid="memory-health-projection">
          <span class="memory-health-icon"><Database :size="20" /></span>
          <div><small>PROJECTION OUTBOX</small><strong>{{ health.memoryService.projectionBacklog.pending }} 待投影</strong><p>{{ health.memoryService.projectionBacklog.processing }} 处理中 · {{ health.memoryService.projectionBacklog.dead }} 死信</p></div>
          <CheckCircle2 v-if="health.memoryService.projectionBacklog.dead === 0" class="health-check" :size="18" />
          <CircleAlert v-else class="health-alert" :size="18" />
        </article>
      </div>

      <div class="memory-admin__columns">
        <section class="memory-admin-card">
          <div class="memory-admin-card__title"><div><small>EXTRACTION POLICY</small><h3>抽取模型策略</h3></div><Save :size="18" /></div>
          <div class="memory-admin-field">
            <span>固定内部 CHAT 模型</span>
            <el-select
              v-model="chatModelId"
              aria-label="选择固定内部 CHAT 模型"
              class="memory-model-select"
              filterable
              :loading="chatModelsLoading"
              placeholder="选择已通过 CHAT 探测的内部模型"
              no-data-text="没有可用的内部 CHAT 模型"
            >
              <el-option
                v-if="selectedChatModelUnavailable"
                :label="`${chatModelId}（当前配置，目录中不可用）`"
                :value="chatModelId ?? ''"
                disabled
              />
              <el-option
                v-for="model in chatModels"
                :key="`${model.providerId}:${model.modelId}`"
                :label="`${model.displayName} · ${model.modelId}`"
                :value="model.modelId"
              >
                <div class="memory-model-option">
                  <strong>{{ model.displayName }}</strong>
                  <span>{{ model.providerName }}</span>
                  <code>{{ model.modelId }}</code>
                </div>
              </el-option>
            </el-select>
            <small>记忆提取优先使用该模型；这里只显示已启用且通过 CHAT 能力探测的内部模型。</small>
            <small v-if="chatModelsError" class="memory-inline-error">{{ chatModelsError }}</small>
          </div>
          <div class="memory-admin-field memory-admin-field--spaced">
            <span>企业 Embedding 模型（可空）</span>
            <el-select
              v-model="enterpriseEmbeddingModelId"
              aria-label="选择企业 Embedding 模型"
              class="memory-model-select"
              filterable
              clearable
              :loading="chatModelsLoading"
              placeholder="未配置时仅使用 CPU BGE"
              no-data-text="没有已配置维度并通过探测的 Embedding 模型"
            >
              <el-option
                v-if="selectedEmbeddingModelUnavailable"
                :label="`${enterpriseEmbeddingModelId}（当前配置，目录中不可用）`"
                :value="enterpriseEmbeddingModelId ?? ''"
                disabled
              />
              <el-option
                v-for="model in embeddingModels"
                :key="`${model.providerId}:${model.modelId}`"
                :label="`${model.displayName} · ${model.embeddingDimension} 维 · ${model.modelId}`"
                :value="model.modelId"
              >
                <div class="memory-model-option">
                  <strong>{{ model.displayName }}</strong>
                  <span>{{ model.embeddingDimension }} 维 · {{ model.providerName }}</span>
                  <code>{{ model.modelId }}</code>
                </div>
              </el-option>
            </el-select>
            <small>为空时只维护 CPU 集合；配置后维护企业主集合和 CPU 热备集合，原始相似度不会跨模型直接比较。</small>
          </div>
          <div class="memory-admin-field memory-admin-field--spaced">
            <span>CPU Embedding profile</span>
            <code>{{ settings.cpuEmbeddingModelId }}</code>
            <small>固定 512 维 BGE 服务；外网测试和企业内网复用同一离线镜像。</small>
          </div>
          <section class="embedding-technical" aria-labelledby="embedding-technical-title">
            <div>
              <strong id="embedding-technical-title">向量模型技术信息</strong>
              <span>向量模型负责按语义寻找相关记忆；这些字段只在升级或排障时使用。</span>
            </div>
            <button
              type="button"
              class="embedding-technical__toggle"
              :aria-expanded="technicalDetailsOpen"
              aria-controls="embedding-technical-details"
              @click="technicalDetailsOpen = !technicalDetailsOpen"
            >
              {{ technicalDetailsOpen ? "收起技术信息" : "查看技术信息" }}
              <ChevronDown :size="14" :class="{ open: technicalDetailsOpen }" />
            </button>
          </section>
          <dl v-if="technicalDetailsOpen" id="embedding-technical-details" class="embedding-details">
            <div v-for="profile in health.memoryService.profiles" :key="profile.profileKey">
              <dt>{{ profile.provider }} · {{ profile.primary ? "主集合" : "热备集合" }} · {{ profile.available ? "可用" : "不可用" }}</dt>
              <dd>{{ profile.model }} · {{ profile.dimension }} 维</dd>
              <small>{{ profile.collection }} · {{ profile.fingerprint }}</small>
            </div>
          </dl>
          <button class="memory-admin-primary" type="button" :disabled="saving" @click="saveSettings"><Save :size="15" />{{ saving ? "保存中" : "保存策略" }}</button>
        </section>

        <section class="memory-admin-card">
          <div class="memory-admin-card__title">
            <div><small>ROLLOUT</small><h3>用户白名单</h3></div>
            <button type="button" data-testid="add-memory-whitelist-user" @click="openWhitelistDialog"><Plus :size="15" />添加用户</button>
          </div>
          <p class="memory-admin-card__description">白名单为空时，不学习、不检索，也不会改变任何现有对话。</p>
          <div v-if="whitelist.length" class="memory-whitelist">
            <article v-for="user in whitelist" :key="user.userId">
              <span class="memory-user-icon"><UsersRound :size="16" /></span>
              <div><strong>{{ user.userId }}</strong><small>{{ formatTime(user.updatedAt) }} 更新</small></div>
              <span class="memory-enabled">已启用</span>
              <button type="button" :aria-label="`移出 ${user.userId}`" @click="removeWhitelistUser(user)"><Trash2 :size="15" /></button>
            </article>
          </div>
          <div v-else class="memory-whitelist-empty"><UsersRound :size="24" /><strong>白名单为空</strong><span>记忆能力不会影响任何用户。</span></div>
        </section>
      </div>
    </template>

    <el-dialog
      v-model="whitelistDialogOpen"
      class="memory-user-dialog"
      title="添加白名单用户"
      width="min(520px, calc(100vw - 32px))"
      append-to-body
      :close-on-click-modal="!addingWhitelistUser"
      :close-on-press-escape="!addingWhitelistUser"
    >
      <p class="memory-user-dialog__description">记忆能力默认不开放。请选择需要灰度启用的平台用户，系统会提交该用户的真实 ID。</p>
      <el-select
        v-model="selectedWhitelistUserId"
        aria-label="选择白名单用户"
        class="memory-user-select"
        filterable
        remote
        reserve-keyword
        :remote-method="loadWhitelistUsers"
        :loading="whitelistUsersLoading"
        :disabled="addingWhitelistUser"
        :fit-input-width="true"
        placement="bottom-start"
        popper-class="memory-user-select-popper"
        placeholder="输入姓名、用户 ID 或统一认证号"
        no-data-text="没有可添加的用户"
      >
        <el-option
          v-for="user in whitelistUsers"
          :key="user.userId"
          :label="whitelistUserOptionLabel(user)"
          :value="user.userId"
        >
          <div class="memory-user-option">
            <strong>{{ user.username }}</strong>
            <span>{{ user.unifiedAuthId || '无统一认证号' }}</span>
            <code>{{ user.userId }}</code>
          </div>
        </el-option>
      </el-select>
      <p v-if="whitelistUserError" class="memory-dialog-error" role="alert">{{ whitelistUserError }}</p>
      <template #footer>
        <el-button :disabled="addingWhitelistUser" @click="whitelistDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="addingWhitelistUser" :disabled="!selectedWhitelistUserId" @click="addWhitelistUser">确认添加</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.memory-admin {
  --memory-blue: #4f6bed;
  --memory-team: #0f8f88;
  --memory-warn: #b7791f;
  --memory-canvas: #f7f8fa;
  --memory-surface: #fff;
  --memory-text: #1f2937;
  --memory-muted: #6b7280;
  --memory-soft: #9ca3af;
  --memory-border: #e5e7eb;
  --memory-border-strong: #d1d5db;
  --memory-hover: #f3f4f6;
  min-height: 100%;
  overflow: auto;
  padding: 24px;
  background: var(--memory-canvas);
  color: var(--memory-text);
}
.memory-admin__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; max-width: 1180px; margin: 0 auto 20px; }
.memory-admin__eyebrow { display: flex; align-items: center; gap: 6px; color: var(--memory-blue); font: 700 10px/1.2 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .12em; }
.memory-admin h2 { margin: 5px 0; font-size: 25px; }
.memory-admin__header p { margin: 0; color: var(--memory-muted); font-size: 12px; }
.memory-admin button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 32px; padding: 0 11px; border: 1px solid var(--memory-border); border-radius: 6px; background: var(--memory-surface); color: var(--memory-text); font-size: 12px; cursor: pointer; }
.memory-admin button:hover { background: var(--memory-hover); }
.memory-admin button:focus-visible, .memory-admin input:focus-visible { outline: 2px solid var(--memory-blue); outline-offset: 2px; }
.memory-admin button:disabled { opacity: .5; cursor: not-allowed; }
.memory-admin__state { display: flex; align-items: center; justify-content: center; gap: 9px; min-height: 300px; color: var(--memory-muted); }
.memory-admin__state--error { color: #c2414b; }
.memory-health-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr)); gap: 10px; max-width: 1180px; margin: 0 auto 14px; }
.memory-health-grid article { position: relative; display: grid; grid-template-columns: 38px 1fr auto; gap: 10px; min-width: 0; padding: 14px; border: 1px solid var(--memory-border); border-radius: 8px; background: var(--memory-surface); }
.memory-health-grid article > div { min-width: 0; }
.memory-health-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 8px; background: var(--memory-hover); color: var(--memory-muted); }
.memory-health-grid article.healthy .memory-health-icon { background: color-mix(in srgb, var(--memory-team) 14%, transparent); color: var(--memory-team); }
.memory-health-grid small { display: block; overflow: hidden; color: var(--memory-soft); font: 700 9px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .06em; text-overflow: ellipsis; white-space: nowrap; }
.memory-health-grid strong { display: block; overflow: hidden; margin-top: 2px; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.memory-health-grid p { overflow: hidden; margin: 4px 0 0; color: var(--memory-muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.health-check { color: var(--memory-team); }
.health-alert { color: var(--memory-warn); }
.memory-admin__columns { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(340px, 100%), 1fr)); gap: 14px; max-width: 1180px; margin: 0 auto; }
.memory-admin-card { padding: 18px; border: 1px solid var(--memory-border); border-radius: 8px; background: var(--memory-surface); }
.memory-admin-card__title { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; }
.memory-admin-card__title small { color: var(--memory-blue); font: 700 9px/1.3 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .08em; }
.memory-admin-card__title h3 { margin: 2px 0 0; font-size: 15px; }
.memory-admin-card__description { margin: -7px 0 13px; color: var(--memory-muted); font-size: 11px; line-height: 1.6; }
.memory-admin-field { display: grid; gap: 6px; font-size: 12px; font-weight: 650; }
.memory-admin-field--spaced { margin-top: 17px; }
.memory-admin-field > code { overflow: hidden; padding: 9px 10px; border: 1px solid var(--memory-border); border-radius: 6px; background: var(--memory-hover); font: 10px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.memory-model-select { width: 100%; }
.memory-admin-field :deep(.el-select__wrapper) { min-height: 36px; border-radius: 6px; background: var(--memory-surface); box-shadow: 0 0 0 1px var(--memory-border-strong) inset; }
.memory-admin-field :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px var(--memory-blue) inset, 0 0 0 3px color-mix(in srgb, var(--memory-blue) 14%, transparent); }
.memory-admin-field :deep(.el-select__selected-item) { color: var(--memory-text); font-size: 12px; font-weight: 500; }
.memory-admin-field small, .memory-admin-switch small { color: var(--memory-muted); font-size: 10px; font-weight: 400; line-height: 1.5; }
.memory-admin-field .memory-inline-error { color: #c2414b; }
.memory-model-option, .memory-user-option { display: grid; grid-template-columns: minmax(120px, 1fr) minmax(90px, .7fr) minmax(150px, 1.2fr); align-items: center; gap: 10px; width: 100%; }
.memory-model-option strong, .memory-user-option strong { overflow: hidden; color: var(--el-text-color-primary, #1f2937); text-overflow: ellipsis; white-space: nowrap; }
.memory-model-option span, .memory-user-option span { overflow: hidden; color: var(--el-text-color-regular, #6b7280); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.memory-model-option code, .memory-user-option code { overflow: hidden; color: var(--el-text-color-secondary, #9ca3af); font: 10px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.memory-admin-switch { display: grid; grid-template-columns: auto 1fr; align-items: start; gap: 9px; margin: 17px 0; }
.memory-admin-switch input { margin-top: 3px; }
.memory-admin-switch span { display: grid; gap: 3px; font-size: 12px; }
.embedding-technical { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; padding-top: 13px; border-top: 1px solid var(--memory-border); }
.embedding-technical > div { display: grid; gap: 2px; }
.embedding-technical strong { font-size: 11px; }
.embedding-technical span { color: var(--memory-muted); font-size: 9px; line-height: 1.5; }
.memory-admin .embedding-technical__toggle { min-width: max-content; border: 0; color: var(--memory-blue); }
.embedding-technical__toggle svg { transition: transform .18s ease; }
.embedding-technical__toggle svg.open { transform: rotate(180deg); }
.embedding-details { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(260px, 100%), 1fr)); margin: 0 0 16px; border: 1px solid var(--memory-border); border-radius: 6px; }
.embedding-details div { min-width: 0; padding: 9px; border-right: 1px solid var(--memory-border); }
.embedding-details div:last-child { border-right: 0; }
.embedding-details dt { color: var(--memory-text); font-size: 9px; font-weight: 700; }
.embedding-details dd { overflow: hidden; margin: 3px 0 0; font: 600 10px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.embedding-details small { display: block; margin-top: 5px; color: var(--memory-muted); font-size: 8px; line-height: 1.45; }
.memory-admin .memory-admin-primary { border-color: var(--memory-blue); background: var(--memory-blue); color: #fff; }
.memory-whitelist { display: grid; gap: 6px; max-height: 310px; overflow: auto; }
.memory-whitelist article { display: grid; grid-template-columns: 32px minmax(0, 1fr) auto 32px; align-items: center; gap: 9px; padding: 8px; border: 1px solid var(--memory-border); border-radius: 6px; }
.memory-user-icon { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: var(--memory-hover); color: var(--memory-blue); }
.memory-whitelist article div { display: grid; min-width: 0; }
.memory-whitelist article strong { overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.memory-whitelist article small { color: var(--memory-soft); font-size: 9px; }
.memory-enabled { border-radius: 999px; padding: 2px 7px; background: color-mix(in srgb, var(--memory-team) 14%, transparent); color: var(--memory-team); font-size: 9px; font-weight: 700; }
.memory-whitelist article button { width: 30px; min-height: 30px; padding: 0; border: 0; color: var(--memory-soft); }
.memory-whitelist article button:hover { color: #c2414b; }
.memory-whitelist-empty { display: flex; min-height: 190px; flex-direction: column; align-items: center; justify-content: center; gap: 6px; border: 1px dashed var(--memory-border-strong); border-radius: 7px; color: var(--memory-soft); }
.memory-whitelist-empty strong { color: var(--memory-text); font-size: 12px; }
.memory-whitelist-empty span { font-size: 10px; }
.memory-user-dialog__description { margin: -4px 0 14px; color: var(--el-text-color-regular, #6b7280); font-size: 12px; line-height: 1.65; }
.memory-user-select { width: 100%; }
:global(.memory-user-dialog .el-dialog__body) { min-height: 220px; }
:global(.memory-user-select-popper .el-select-dropdown__wrap) { max-height: min(120px, 24vh); }
.memory-dialog-error { margin: 9px 0 0; color: #c2414b; font-size: 11px; }
.spinning { animation: memory-admin-spin .9s linear infinite; }
@keyframes memory-admin-spin { to { transform: rotate(360deg); } }
:global(.dark .memory-admin) {
  --memory-canvas: #111318;
  --memory-surface: #17191f;
  --memory-text: #e5e7eb;
  --memory-muted: #9ca3af;
  --memory-soft: #7d8491;
  --memory-border: #2d313a;
  --memory-border-strong: #3f4551;
  --memory-hover: rgba(255, 255, 255, .06);
}
@media (max-width: 980px) { .memory-health-grid { grid-template-columns: repeat(2, 1fr); } .memory-admin__columns { grid-template-columns: 1fr; } }
@media (max-width: 560px) { .memory-admin { padding: 16px 10px; } .memory-health-grid { grid-template-columns: 1fr; } .memory-admin__header p { display: none; } .embedding-technical { align-items: flex-start; flex-direction: column; } .embedding-details { grid-template-columns: 1fr; } .embedding-details div { border-right: 0; border-bottom: 1px solid var(--memory-border); } }
@media (prefers-reduced-motion: reduce) { .spinning { animation: none; } .embedding-technical__toggle svg { transition: none; } }
</style>
