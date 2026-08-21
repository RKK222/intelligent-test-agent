<script setup lang="ts">
import { computed, inject, onBeforeUnmount, ref, watch } from "vue";
import { ArrowDownToLine, RefreshCw, RotateCcw, ShieldCheck } from "lucide-vue-next";
import { ElMessage, ElMessageBox } from "element-plus";
import { Badge, Button, Input, Spinner } from "@test-agent/ui-kit";
import type { BackendApiClient } from "@test-agent/backend-api";
import type {
  CurrentUser,
  LocalClientGlobalPolicy,
  LocalClientRelease,
  LocalClientRollout,
  LocalClientUpdateAttempt,
  LocalClientUpdateDirection,
  LocalClientUserPolicy
} from "@test-agent/shared-types";

const props = defineProps<{
  currentUser: CurrentUser | null;
  pageActive: boolean;
}>();

const api = inject<BackendApiClient>("api")!;
const hasSuperAdmin = computed(() => props.currentUser?.roles?.includes("SUPER_ADMIN") === true);
const releases = ref<LocalClientRelease[]>([]);
const globalPolicy = ref<LocalClientGlobalPolicy | null>(null);
const userPolicies = ref<LocalClientUserPolicy[]>([]);
const rollouts = ref<LocalClientRollout[]>([]);
const attempts = ref<LocalClientUpdateAttempt[]>([]);
const selectedRolloutId = ref("");
const globalTargetDraft = ref("");
const userIdDraft = ref("");
const userTargetDraft = ref("");
const rolloutUserIdDraft = ref("");
const loading = ref(false);
const actionBusy = ref(false);
const attemptsLoading = ref(false);
const errorMessage = ref("");
let requestEpoch = 0;
let refreshTimer: ReturnType<typeof setInterval> | undefined;

const compatibleReleases = computed(() => releases.value.filter((release) => release.compatible));
const selectedRollout = computed(() =>
  rollouts.value.find((rollout) => rollout.rolloutId === selectedRolloutId.value) ?? null
);

/** 页面失活后停止轮询，并用请求代次丢弃已经发出的迟到响应。 */
watch(
  () => [hasSuperAdmin.value, props.pageActive] as const,
  ([allowed, active]) => {
    requestEpoch += 1;
    stopPolling();
    if (!allowed || !active) return;
    void loadOverview(false);
    refreshTimer = setInterval(() => void loadOverview(true), 10_000);
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  requestEpoch += 1;
  stopPolling();
});

function stopPolling() {
  if (!refreshTimer) return;
  clearInterval(refreshTimer);
  refreshTimer = undefined;
}

async function loadOverview(silent: boolean) {
  const epoch = requestEpoch;
  if (!silent) loading.value = true;
  errorMessage.value = "";
  try {
    const [releaseViews, policy, overrides, rolloutViews] = await Promise.all([
      api.listLocalClientReleases(),
      api.getLocalClientGlobalPolicy(),
      api.listLocalClientUserPolicies(),
      api.listLocalClientRollouts()
    ]);
    if (epoch !== requestEpoch || !props.pageActive || !hasSuperAdmin.value) return;
    releases.value = releaseViews;
    globalPolicy.value = policy;
    userPolicies.value = overrides;
    rollouts.value = rolloutViews;
    globalTargetDraft.value = policy.targetVersion ?? "";
    if (!userTargetDraft.value || !releaseViews.some((item) => item.version === userTargetDraft.value)) {
      userTargetDraft.value = releaseViews.find((item) => item.compatible)?.version ?? "";
    }
    if (selectedRolloutId.value && !rolloutViews.some((item) => item.rolloutId === selectedRolloutId.value)) {
      selectedRolloutId.value = "";
      attempts.value = [];
    }
  } catch (error) {
    if (epoch === requestEpoch && props.pageActive) errorMessage.value = formatError(error);
  } finally {
    if (!silent && epoch === requestEpoch) loading.value = false;
  }
}

async function loadAttempts(rolloutId: string) {
  selectedRolloutId.value = rolloutId;
  attemptsLoading.value = true;
  errorMessage.value = "";
  const epoch = requestEpoch;
  try {
    const result = await api.listLocalClientRolloutAttempts(rolloutId);
    if (epoch === requestEpoch && selectedRolloutId.value === rolloutId && props.pageActive) {
      attempts.value = result;
    }
  } catch (error) {
    if (epoch === requestEpoch) errorMessage.value = formatError(error);
  } finally {
    if (epoch === requestEpoch) attemptsLoading.value = false;
  }
}

async function syncReleases() {
  await runAction(async () => {
    const result = await api.syncLocalClientReleases();
    await loadOverview(true);
    ElMessage.success(`版本同步完成：新增 ${result.synced}，未变化 ${result.unchanged}`);
  }, "同步客户端版本失败");
}

async function saveGlobalTarget() {
  if (!globalTargetDraft.value) {
    ElMessage.warning("请选择兼容的全局目标版本");
    return;
  }
  await runAction(async () => {
    globalPolicy.value = await api.setLocalClientGlobalPolicy(globalTargetDraft.value);
    await loadOverview(true);
    ElMessage.success("全局目标版本已更新；客户端会在下次检查时收到新策略");
  }, "保存全局目标版本失败");
}

async function saveUserTarget() {
  const userId = userIdDraft.value.trim();
  if (!userId || !userTargetDraft.value) {
    ElMessage.warning("请输入用户 ID 并选择目标版本");
    return;
  }
  await runAction(async () => {
    await api.setLocalClientUserPolicy(userId, userTargetDraft.value);
    userIdDraft.value = "";
    await loadOverview(true);
    ElMessage.success("用户版本覆盖已保存");
  }, "保存用户版本覆盖失败");
}

async function clearUserTarget(policy: LocalClientUserPolicy) {
  try {
    await ElMessageBox.confirm(
      `清除后，用户 ${policy.userId} 将自动回到平台全局目标版本。`,
      "清除用户版本覆盖",
      { type: "warning", confirmButtonText: "清除覆盖", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await runAction(async () => {
    await api.clearLocalClientUserPolicy(policy.userId);
    await loadOverview(true);
    ElMessage.success("用户版本覆盖已清除");
  }, "清除用户版本覆盖失败");
}

async function createAllOnlineRollout() {
  try {
    await ElMessageBox.confirm(
      "将快照当前所有在线实例，并按每个用户当前生效策略立即静默切换。",
      "更新全部在线客户端",
      { type: "warning", confirmButtonText: "立即执行", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await runAction(async () => {
    await api.createLocalClientRollout({ scope: "ALL_ONLINE" });
    await loadOverview(true);
    ElMessage.success("全量在线更新任务已创建");
  }, "创建全量更新任务失败");
}

async function createUserRollout() {
  const userId = rolloutUserIdDraft.value.trim();
  if (!userId) {
    ElMessage.warning("请输入需要立即更新的用户 ID");
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将立即静默切换用户 ${userId} 的全部在线实例。`,
      "更新指定用户",
      { type: "warning", confirmButtonText: "立即执行", cancelButtonText: "取消" }
    );
  } catch {
    return;
  }
  await runAction(async () => {
    await api.createLocalClientRollout({ scope: "USER", userId });
    await loadOverview(true);
    ElMessage.success("指定用户更新任务已创建");
  }, "创建用户更新任务失败");
}

async function runAction(action: () => Promise<void>, fallback: string) {
  actionBusy.value = true;
  errorMessage.value = "";
  try {
    await action();
  } catch (error) {
    errorMessage.value = formatError(error) || fallback;
    ElMessage.error(errorMessage.value);
  } finally {
    actionBusy.value = false;
  }
}

function directionLabel(direction: LocalClientUpdateDirection) {
  if (direction === "ROLLBACK") return "回退";
  if (direction === "UPDATE") return "更新";
  return "一致";
}

function directionTone(direction: LocalClientUpdateDirection): "success" | "warning" | "neutral" {
  if (direction === "ROLLBACK") return "warning";
  if (direction === "UPDATE") return "success";
  return "neutral";
}

function statusTone(status: string): "success" | "warning" | "danger" | "neutral" {
  if (["SUCCEEDED", "COMPLETED"].includes(status)) return "success";
  if (["FAILED", "AUTO_ROLLED_BACK"].includes(status)) return "danger";
  if (["RUNNING", "SENT", "PREPARING", "PREPARED", "APPLYING"].includes(status)) return "warning";
  return "neutral";
}

function formatDate(value?: string | null) {
  if (!value) return "—";
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false
  }).format(new Date(timestamp));
}

function formatBytes(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KiB`;
  return `${(size / 1024 / 1024).toFixed(1)} MiB`;
}

function shortDigest(value?: string | null) {
  if (!value) return "—";
  return value.length > 16 ? `${value.slice(0, 8)}…${value.slice(-8)}` : value;
}

function formatError(error: unknown) {
  return error instanceof Error ? error.message : "本地客户端版本管理操作失败";
}
</script>

<template>
  <section class="local-version-panel">
    <div v-if="!hasSuperAdmin" class="local-version-empty">当前账号无本地客户端版本管理权限</div>
    <template v-else>
      <header class="local-version-header">
        <div>
          <span class="local-version-kicker">ARM64 客户端交付控制台</span>
          <h2>本地客户端版本管理</h2>
          <p>目标策略与立即 rollout 分离；升级和回退共用同一签名发布单元。</p>
        </div>
        <div class="local-version-header-actions">
          <Button type="button" :disabled="loading || actionBusy || !pageActive" @click="loadOverview(false)">
            <RefreshCw :size="14" aria-hidden="true" />刷新
          </Button>
          <Button type="button" variant="primary" :disabled="loading || actionBusy || !pageActive" @click="syncReleases">
            <ArrowDownToLine :size="14" aria-hidden="true" />同步版本
          </Button>
        </div>
      </header>

      <div v-if="errorMessage" class="local-version-error" role="alert">{{ errorMessage }}</div>
      <div v-if="loading" class="local-version-loading"><Spinner />正在读取版本策略…</div>

      <div class="local-version-grid">
        <section class="local-version-card release-card" aria-labelledby="local-release-title">
          <div class="card-heading">
            <div>
              <h3 id="local-release-title">签名发布版本</h3>
              <p>只显示平台已同步的清单；不兼容版本不能成为目标。</p>
            </div>
            <Badge tone="neutral">{{ releases.length }} 个版本</Badge>
          </div>
          <div v-if="releases.length" class="table-scroll">
            <table>
              <thead><tr><th>版本</th><th>平台</th><th>兼容性</th><th>制品</th><th>同步时间</th></tr></thead>
              <tbody>
                <tr v-for="release in releases" :key="release.version">
                  <td>
                    <strong class="version-code">{{ release.version }}</strong>
                    <small title="签名清单摘要">{{ shortDigest(release.manifestSha256) }}</small>
                  </td>
                  <td>{{ release.platform }} / {{ release.architecture }}<small>启动器 {{ release.launcherVersionMin }}–{{ release.launcherVersionMax }}</small></td>
                  <td><Badge :tone="release.compatible ? 'success' : 'danger'">{{ release.compatible ? "可选" : "不兼容" }}</Badge></td>
                  <td>
                    {{ release.artifacts.length }} 个制品
                    <small>{{ release.artifacts.map((item) => `${item.kind} ${formatBytes(item.size)}`).join(" · ") }}</small>
                  </td>
                  <td>{{ formatDate(release.syncedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else-if="!loading" class="card-empty">尚未同步签名发布版本，请先点击“同步版本”。</div>
        </section>

        <section class="local-version-card policy-card" aria-labelledby="local-policy-title">
          <div class="card-heading">
            <div>
              <h3 id="local-policy-title">生效版本策略</h3>
              <p>用户覆盖优先于全局策略；设置目标不会自动发起强制更新。</p>
            </div>
            <Badge tone="info">修订 {{ globalPolicy?.revision ?? 0 }}</Badge>
          </div>

          <div class="policy-global">
            <label for="local-global-target">全局目标版本</label>
            <select id="local-global-target" v-model="globalTargetDraft" aria-label="全局目标版本" :disabled="actionBusy">
              <option value="">请选择兼容版本</option>
              <option v-for="release in compatibleReleases" :key="release.version" :value="release.version">{{ release.version }}</option>
            </select>
            <Button type="button" variant="primary" :disabled="actionBusy || !globalTargetDraft" @click="saveGlobalTarget">保存全局目标</Button>
          </div>

          <div class="policy-user-form">
            <label for="local-policy-user">按用户覆盖</label>
            <Input id="local-policy-user" v-model="userIdDraft" aria-label="用户 ID" placeholder="用户 ID，例如 usr_..." />
            <select v-model="userTargetDraft" aria-label="用户目标版本" :disabled="actionBusy">
              <option value="">请选择兼容版本</option>
              <option v-for="release in compatibleReleases" :key="release.version" :value="release.version">{{ release.version }}</option>
            </select>
            <Button type="button" :disabled="actionBusy || !userIdDraft.trim() || !userTargetDraft" @click="saveUserTarget">保存用户覆盖</Button>
          </div>

          <div v-if="userPolicies.length" class="policy-list" aria-label="用户版本覆盖列表">
            <article v-for="policy in userPolicies" :key="policy.userId">
              <div><strong>{{ policy.userId }}</strong><small>修订 {{ policy.revision }} · {{ formatDate(policy.updatedAt) }}</small></div>
              <span class="version-transition"><code>全局</code><span aria-hidden="true">→</span><code>{{ policy.targetVersion }}</code></span>
              <Button type="button" size="sm" :aria-label="`清除 ${policy.userId} 覆盖`" :disabled="actionBusy" @click="clearUserTarget(policy)">清除</Button>
            </article>
          </div>
          <div v-else class="card-empty compact">当前没有用户版本覆盖。</div>
        </section>

        <section class="local-version-card rollout-card" aria-labelledby="local-rollout-title">
          <div class="card-heading">
            <div>
              <h3 id="local-rollout-title">立即静默切换</h3>
              <p>创建时快照在线实例；当前任务会被取消，客户端不会弹窗或再次要求 Client key。</p>
            </div>
            <ShieldCheck :size="19" aria-hidden="true" />
          </div>
          <div class="rollout-actions">
            <Button type="button" variant="primary" :disabled="actionBusy" @click="createAllOnlineRollout">更新全部在线客户端</Button>
            <Input v-model="rolloutUserIdDraft" aria-label="立即更新用户 ID" placeholder="指定用户 ID" />
            <Button type="button" :disabled="actionBusy || !rolloutUserIdDraft.trim()" @click="createUserRollout">立即更新指定用户</Button>
          </div>

          <div v-if="rollouts.length" class="rollout-list">
            <button
              v-for="rollout in rollouts"
              :key="rollout.rolloutId"
              type="button"
              :class="['rollout-row', selectedRolloutId === rollout.rolloutId && 'is-selected']"
              :aria-label="`查看 rollout ${rollout.rolloutId}`"
              @click="loadAttempts(rollout.rolloutId)"
            >
              <span><strong>{{ rollout.rolloutId }}</strong><small>{{ rollout.scope === "USER" ? `用户 ${rollout.requestedUserId}` : "全部在线实例" }}</small></span>
              <Badge :tone="statusTone(rollout.status)">{{ rollout.status }}</Badge>
              <span>{{ rollout.attemptCount ?? "—" }} 个实例</span>
              <time :datetime="rollout.createdAt">{{ formatDate(rollout.createdAt) }}</time>
            </button>
          </div>
          <div v-else-if="!loading" class="card-empty compact">尚未创建立即更新任务。</div>

          <div v-if="selectedRollout" class="attempt-panel">
            <div class="attempt-heading">
              <div><RotateCcw :size="14" aria-hidden="true" /><strong>{{ selectedRollout.rolloutId }}</strong> 实例进度</div>
              <Button type="button" size="sm" :disabled="attemptsLoading" @click="loadAttempts(selectedRollout.rolloutId)">刷新详情</Button>
            </div>
            <div v-if="attemptsLoading" class="local-version-loading compact"><Spinner />读取实例状态…</div>
            <div v-else-if="attempts.length" class="table-scroll">
              <table>
                <thead><tr><th>实例 / 用户</th><th>版本方向</th><th>状态</th><th>代次 / 策略</th><th>最近更新</th></tr></thead>
                <tbody>
                  <tr v-for="attempt in attempts" :key="attempt.commandId">
                    <td><strong>{{ attempt.clientInstanceId }}</strong><small>{{ attempt.userId }} · {{ attempt.commandId }}</small></td>
                    <td>
                      <Badge :tone="directionTone(attempt.direction)">{{ directionLabel(attempt.direction) }}</Badge>
                      <span class="attempt-transition">{{ attempt.currentVersion }} → {{ attempt.targetVersion }}</span>
                    </td>
                    <td><Badge :tone="statusTone(attempt.status)">{{ attempt.status }}</Badge><small v-if="attempt.errorCode">{{ attempt.errorCode }}</small></td>
                    <td>G{{ attempt.connectionGeneration }} / R{{ attempt.policyRevision }}</td>
                    <td>{{ formatDate(attempt.updatedAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="card-empty compact">该 rollout 没有实例 attempt。</div>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.local-version-panel {
  --local-accent: var(--ta-shell-accent, #c8161d);
  --local-accent-strong: var(--ta-shell-accent-strong, #991b1b);
  --local-accent-soft: var(--ta-shell-accent-soft, #fdf2f2);
  --local-border: var(--ta-shell-border, #e5e7eb);
  --local-border-strong: var(--ta-shell-border-strong, #d1d5db);
  --local-text: var(--ta-shell-text, #1f2937);
  --local-muted: var(--ta-shell-muted, #6b7280);
  min-height: 0;
  height: 100%;
  overflow: auto;
  background: #f7f8fa;
  color: var(--local-text);
  font-family: "Geist Sans", "Noto Sans SC", sans-serif;
}
.local-version-empty { margin: 16px; padding: 16px; border: 1px solid var(--local-border); border-radius: 8px; background: #fff; color: var(--local-muted); font-size: 13px; }
.local-version-header { position: sticky; z-index: 3; top: 0; display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 14px 18px; border-bottom: 1px solid var(--local-border); background: rgba(255, 255, 255, .96); backdrop-filter: blur(10px); }
.local-version-kicker { color: var(--local-accent-strong); font-size: 10px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.local-version-header h2 { margin: 2px 0 0; font-size: 17px; font-weight: 700; }
.local-version-header p, .card-heading p { margin: 3px 0 0; color: var(--local-muted); font-size: 12px; line-height: 1.45; }
.local-version-header-actions, .card-heading, .policy-global, .policy-user-form, .rollout-actions, .attempt-heading, .attempt-heading > div { display: flex; align-items: center; }
.local-version-header-actions, .rollout-actions { gap: 8px; }
.local-version-error { margin: 12px 18px 0; padding: 9px 11px; border: 1px solid #e9b9b7; border-radius: 7px; background: #fff5f4; color: #9e3b34; font-size: 12px; }
.local-version-loading { display: flex; align-items: center; gap: 8px; padding: 14px 18px; color: var(--local-muted); font-size: 12px; }
.local-version-loading.compact { padding: 10px 0; }
.local-version-grid { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(320px, .8fr); gap: 12px; padding: 12px 18px 20px; }
.local-version-card { min-width: 0; border: 1px solid var(--local-border); border-radius: 9px; background: #fff; box-shadow: 0 4px 14px rgba(31, 41, 55, .035); }
.release-card { grid-column: 1; }
.policy-card { grid-column: 2; grid-row: 1 / span 2; align-self: start; }
.rollout-card { grid-column: 1; }
.card-heading { justify-content: space-between; gap: 12px; padding: 12px 14px; border-bottom: 1px solid var(--local-border); }
.card-heading h3 { margin: 0; font-size: 14px; font-weight: 680; }
.table-scroll { overflow: auto; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th, td { padding: 9px 11px; border-bottom: 1px solid #f0f1f3; text-align: left; vertical-align: middle; white-space: nowrap; }
th { background: #fafbfc; color: var(--local-muted); font-size: 10px; font-weight: 650; letter-spacing: .035em; }
td strong, td small { display: block; }
td small, .policy-list small, .rollout-row small { margin-top: 2px; color: var(--local-muted); font-size: 10px; }
.version-code, code, .attempt-transition { font-family: "Geist Mono", ui-monospace, monospace; }
.card-empty { padding: 22px 14px; color: var(--local-muted); font-size: 12px; text-align: center; }
.card-empty.compact { padding: 13px; }
.policy-global, .policy-user-form { flex-wrap: wrap; gap: 8px; padding: 12px 14px; }
.policy-user-form { border-top: 1px solid var(--local-border); }
.policy-global label, .policy-user-form label { flex: 0 0 100%; color: var(--local-muted); font-size: 11px; font-weight: 600; }
.policy-global select, .policy-user-form select { min-width: 170px; height: 32px; flex: 1 1 170px; border: 1px solid var(--local-border-strong); border-radius: 5px; background: #fff; padding: 0 8px; color: var(--local-text); font: 12px "Geist Mono", ui-monospace, monospace; }
.policy-user-form :deep(input) { flex: 1 1 150px; }
.policy-list { border-top: 1px solid var(--local-border); }
.policy-list article { display: grid; grid-template-columns: minmax(100px, 1fr) auto auto; align-items: center; gap: 9px; padding: 9px 14px; border-bottom: 1px solid #f0f1f3; font-size: 12px; }
.policy-list article:last-child { border-bottom: 0; }
.policy-list strong, .policy-list small { display: block; }
.version-transition { display: flex; align-items: center; gap: 5px; padding: 4px 7px; border-radius: 6px; background: var(--local-accent-soft); color: var(--local-accent-strong); font-size: 10px; }
.rollout-actions { flex-wrap: wrap; padding: 11px 14px; border-bottom: 1px solid var(--local-border); }
.rollout-actions :deep(input) { width: 210px; }
.rollout-list { padding: 6px 8px; }
.rollout-row { display: grid; grid-template-columns: minmax(220px, 1fr) 105px 90px 150px; align-items: center; gap: 10px; width: 100%; padding: 8px 9px; border: 0; border-left: 3px solid transparent; border-radius: 5px; background: transparent; color: var(--local-text); font: inherit; text-align: left; cursor: pointer; }
.rollout-row:hover, .rollout-row:focus-visible { background: #f7f8fa; outline: none; }
.rollout-row.is-selected { border-left-color: var(--local-accent); background: var(--local-accent-soft); }
.rollout-row strong, .rollout-row small { display: block; }
.attempt-panel { margin: 8px; border: 1px solid var(--local-border-strong); border-radius: 7px; overflow: hidden; }
.attempt-heading { justify-content: space-between; gap: 10px; padding: 8px 10px; border-bottom: 1px solid var(--local-border); background: #fafbfc; font-size: 12px; }
.attempt-heading > div { gap: 6px; }
.attempt-transition { display: inline-block; margin-top: 4px; color: var(--local-accent-strong); font-size: 11px; }
@media (max-width: 1050px) {
  .local-version-grid { grid-template-columns: 1fr; }
  .release-card, .policy-card, .rollout-card { grid-column: 1; grid-row: auto; }
}
@media (prefers-reduced-motion: reduce) {
  .local-version-panel * { scroll-behavior: auto !important; transition-duration: 0s !important; }
}
</style>
