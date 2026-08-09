<script setup lang="ts">
import { inject, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { BackendApiClient } from "@test-agent/backend-api";
import type { MemoryAdminHealth, MemorySettingsView, MemoryWhitelistView } from "@test-agent/shared-types";
import {
  BrainCircuit,
  CheckCircle2,
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
const chatModelId = ref("");
const allowRunModelFallback = ref(false);

onMounted(() => void load());

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
    allowRunModelFallback.value = settingsView.currentRunModelFallbackEnabled;
  } catch {
    error.value = "记忆管理数据加载失败";
  } finally {
    loading.value = false;
  }
}

async function saveSettings() {
  if (!settings.value) return;
  saving.value = true;
  try {
    settings.value = await api.updateQaMemorySettings({
      primaryChatModelId: chatModelId.value.trim() || null,
      currentRunModelFallbackEnabled: allowRunModelFallback.value,
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

async function addWhitelistUser() {
  try {
    const result = await ElMessageBox.prompt("记忆能力默认不开放；请输入需要灰度启用的平台用户 ID。", "添加白名单用户", {
      inputPlaceholder: "userId",
      inputValidator: (value) => Boolean(value?.trim()) || "请输入用户 ID"
    });
    await api.enableQaMemoryUser(result.value.trim());
    ElMessage.success("用户已加入记忆白名单");
    await load();
  } catch (caught) {
    if (caught === "cancel" || caught === "close") return;
    ElMessage.error(caught instanceof Error ? caught.message : "添加失败");
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
        <div class="memory-admin__eyebrow"><BrainCircuit :size="15" /> QA MEMORY CONTROL</div>
        <h2 id="memory-admin-title">记忆能力</h2>
        <p>检查 Mem0、向量模型、抽取 CHAT 模型和异步学习队列，并按用户灰度开放。</p>
      </div>
      <button type="button" :disabled="loading" @click="load"><RefreshCw :size="15" :class="{ spinning: loading }" />刷新</button>
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
        <article :class="{ healthy: health.embedding.provider === 'LOCAL_BGE' }" data-testid="memory-health-embedding">
          <span class="memory-health-icon"><Cpu :size="20" /></span>
          <div><small>EMBEDDING PROFILE</small><strong>{{ health.embedding.provider }}</strong><p>{{ health.embedding.model }} · {{ health.embedding.dimension }} 维 · {{ health.embedding.device }}</p></div>
          <CheckCircle2 class="health-check" :size="18" />
        </article>
        <article :class="{ healthy: Boolean(health.primaryChatModelId) }" data-testid="memory-health-chat">
          <span class="memory-health-icon"><MessageSquareText :size="20" /></span>
          <div><small>FIXED CHAT MODEL</small><strong>{{ health.primaryChatModelId || "未配置" }}</strong><p>当前 Run 内部模型回退：{{ health.currentRunModelFallbackEnabled ? "允许" : "关闭" }}</p></div>
          <CheckCircle2 v-if="health.primaryChatModelId" class="health-check" :size="18" />
          <CircleAlert v-else class="health-alert" :size="18" />
        </article>
        <article :class="{ healthy: health.queueDead === 0 }" data-testid="memory-health-queue">
          <span class="memory-health-icon"><BrainCircuit :size="20" /></span>
          <div><small>LEARNING OUTBOX</small><strong>{{ health.queuePending }} 待处理</strong><p>{{ health.queueProcessing }} 处理中 · {{ health.queueDead }} 死信</p></div>
          <CheckCircle2 v-if="health.queueDead === 0" class="health-check" :size="18" />
          <CircleAlert v-else class="health-alert" :size="18" />
        </article>
      </div>

      <div class="memory-admin__columns">
        <section class="memory-admin-card">
          <div class="memory-admin-card__title"><div><small>EXTRACTION POLICY</small><h3>抽取模型策略</h3></div><Save :size="18" /></div>
          <label class="memory-admin-field">
            <span>固定内部 CHAT 模型</span>
            <input v-model="chatModelId" type="text" placeholder="例如：internal-provider/model-id" />
            <small>固定模型优先；留空时只有满足内部目录和健康探测条件才可使用当前 Run 模型。</small>
          </label>
          <label class="memory-admin-switch">
            <input v-model="allowRunModelFallback" type="checkbox" />
            <span><strong>允许当前 Run 内部模型回退</strong><small>外部模型永远不会获得短期 mfg_ 授权。</small></span>
          </label>
          <dl class="embedding-details">
            <div><dt>模型 revision</dt><dd>{{ health.embedding.revision }}</dd></div>
            <div><dt>Collection version</dt><dd>{{ health.embedding.collectionVersion }}</dd></div>
            <div><dt>归一化</dt><dd>{{ health.embedding.normalized ? "是" : "否" }}</dd></div>
          </dl>
          <button class="memory-admin-primary" type="button" :disabled="saving" @click="saveSettings"><Save :size="15" />{{ saving ? "保存中" : "保存策略" }}</button>
        </section>

        <section class="memory-admin-card">
          <div class="memory-admin-card__title">
            <div><small>ROLLOUT</small><h3>用户白名单</h3></div>
            <button type="button" data-testid="add-memory-whitelist-user" @click="addWhitelistUser"><Plus :size="15" />添加用户</button>
          </div>
          <p class="memory-admin-card__description">白名单为空时，不学习、不检索，也不会改变任何现有 QA 对话。</p>
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
.memory-health-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; max-width: 1180px; margin: 0 auto 14px; }
.memory-health-grid article { position: relative; display: grid; grid-template-columns: 38px 1fr auto; gap: 10px; min-width: 0; padding: 14px; border: 1px solid var(--memory-border); border-radius: 8px; background: var(--memory-surface); }
.memory-health-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 8px; background: var(--memory-hover); color: var(--memory-muted); }
.memory-health-grid article.healthy .memory-health-icon { background: color-mix(in srgb, var(--memory-team) 14%, transparent); color: var(--memory-team); }
.memory-health-grid small { display: block; overflow: hidden; color: var(--memory-soft); font: 700 9px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .06em; text-overflow: ellipsis; white-space: nowrap; }
.memory-health-grid strong { display: block; overflow: hidden; margin-top: 2px; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.memory-health-grid p { overflow: hidden; margin: 4px 0 0; color: var(--memory-muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.health-check { color: var(--memory-team); }
.health-alert { color: var(--memory-warn); }
.memory-admin__columns { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px; max-width: 1180px; margin: 0 auto; }
.memory-admin-card { padding: 18px; border: 1px solid var(--memory-border); border-radius: 8px; background: var(--memory-surface); }
.memory-admin-card__title { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 16px; }
.memory-admin-card__title small { color: var(--memory-blue); font: 700 9px/1.3 ui-monospace, SFMono-Regular, Consolas, monospace; letter-spacing: .08em; }
.memory-admin-card__title h3 { margin: 2px 0 0; font-size: 15px; }
.memory-admin-card__description { margin: -7px 0 13px; color: var(--memory-muted); font-size: 11px; line-height: 1.6; }
.memory-admin-field { display: grid; gap: 6px; font-size: 12px; font-weight: 650; }
.memory-admin-field input { height: 36px; border: 1px solid var(--memory-border-strong); border-radius: 6px; padding: 0 10px; background: var(--memory-surface); color: var(--memory-text); }
.memory-admin-field small, .memory-admin-switch small { color: var(--memory-muted); font-size: 10px; font-weight: 400; line-height: 1.5; }
.memory-admin-switch { display: grid; grid-template-columns: auto 1fr; align-items: start; gap: 9px; margin: 17px 0; }
.memory-admin-switch input { margin-top: 3px; }
.memory-admin-switch span { display: grid; gap: 3px; font-size: 12px; }
.embedding-details { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 0 0 16px; border: 1px solid var(--memory-border); border-radius: 6px; }
.embedding-details div { min-width: 0; padding: 9px; border-right: 1px solid var(--memory-border); }
.embedding-details div:last-child { border-right: 0; }
.embedding-details dt { color: var(--memory-muted); font-size: 9px; }
.embedding-details dd { overflow: hidden; margin: 3px 0 0; font: 600 10px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
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
@media (max-width: 560px) { .memory-admin { padding: 16px 10px; } .memory-health-grid { grid-template-columns: 1fr; } .memory-admin__header p { display: none; } .embedding-details { grid-template-columns: 1fr; } .embedding-details div { border-right: 0; border-bottom: 1px solid var(--memory-border); } }
@media (prefers-reduced-motion: reduce) { .spinning { animation: none; } }
</style>
