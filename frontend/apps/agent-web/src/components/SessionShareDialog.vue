<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Copy, Search, Share2, Trash2, UserPlus, X } from 'lucide-vue-next'
import { BackendApiError, type BackendApiClient } from '@test-agent/backend-api'
import type {
  Session,
  SessionCollaborationShare,
  SessionShareCandidate,
} from '@test-agent/shared-types'
import { copyTextToClipboard } from '@test-agent/ui-kit'
import {
  SESSION_SHARE_MAX_MEMBERS,
  absoluteSessionShareLink,
  activeSessionShareMembers,
  sessionShareDraftError,
  sessionShareExpiryAt,
} from './session-share-management'

type MemberDraft = SessionShareCandidate & { canChat: boolean }

const props = withDefaults(defineProps<{
  open: boolean
  session: Session | null
  api: BackendApiClient
  pendingTaskCount?: number
}>(), {
  pendingTaskCount: 0,
})

const emit = defineEmits<{
  (event: 'close'): void
  (event: 'updated', share: SessionCollaborationShare): void
}>()

const loading = ref(false)
const saving = ref(false)
const revoking = ref(false)
const share = ref<SessionCollaborationShare | null>(null)
const members = ref<MemberDraft[]>([])
const expiryDays = ref(7)
const searchQuery = ref('')
const candidates = ref<SessionShareCandidate[]>([])
const candidatesLoading = ref(false)
const loadError = ref('')
const copied = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null
let loadSequence = 0

const shareLink = computed(() => share.value ? absoluteSessionShareLink(share.value.sharePath) : '')
const selectedIds = computed(() => new Set(members.value.map((member) => member.userId)))
const visibleCandidates = computed(() => candidates.value.filter((candidate) => !selectedIds.value.has(candidate.userId)))
const memberLimitReached = computed(() => members.value.length >= SESSION_SHARE_MAX_MEMBERS)

watch(() => props.open, (open) => {
  if (!open) return
  void loadShare()
}, { immediate: true })

watch(searchQuery, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => void searchCandidates(), 250)
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

async function loadShare() {
  const sessionId = props.session?.sessionId
  if (!sessionId) return
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  copied.value = false
  searchQuery.value = ''
  candidates.value = []
  try {
    const current = await props.api.getSessionCollaborationShare(sessionId)
    if (sequence !== loadSequence) return
    share.value = current
    members.value = current ? activeSessionShareMembers(current.members) : []
    expiryDays.value = 7
    await searchCandidates()
  } catch (error) {
    if (sequence === loadSequence) loadError.value = errorMessage(error, '读取分享设置失败')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

async function searchCandidates() {
  if (!props.open) return
  candidatesLoading.value = true
  try {
    const page = await props.api.listSessionShareCandidates(searchQuery.value.trim() || undefined, 1, 30)
    candidates.value = page.items
  } catch (error) {
    loadError.value = errorMessage(error, '搜索用户失败')
  } finally {
    candidatesLoading.value = false
  }
}

function addMember(candidate: SessionShareCandidate) {
  if (memberLimitReached.value || selectedIds.value.has(candidate.userId)) return
  members.value = [...members.value, { ...candidate, canChat: false }]
}

function removeMember(userId: string) {
  members.value = members.value.filter((member) => member.userId !== userId)
}

async function saveShare() {
  const sessionId = props.session?.sessionId
  if (!sessionId || saving.value) return
  const draftError = sessionShareDraftError(members.value)
  if (draftError) {
    ElMessage.warning(draftError)
    return
  }
  saving.value = true
  try {
    const updated = await props.api.putSessionCollaborationShare(sessionId, {
      expectedVersion: share.value?.version ?? null,
      expiresAt: sessionShareExpiryAt(new Date(), expiryDays.value),
      members: members.value.map((member) => ({ userId: member.userId, canChat: member.canChat })),
    })
    share.value = updated
    members.value = activeSessionShareMembers(updated.members)
    emit('updated', updated)
    ElMessage.success(updated.status === 'ACTIVE' ? '分享设置已保存' : '分享已更新')
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存分享设置失败'))
    if (error instanceof BackendApiError && error.status === 409) await loadShare()
  } finally {
    saving.value = false
  }
}

async function revokeShare() {
  const sessionId = props.session?.sessionId
  const current = share.value
  if (!sessionId || !current || current.status !== 'ACTIVE' || revoking.value) return
  const taskWarning = props.pendingTaskCount > 0
    ? `该分享会话仍有 ${props.pendingTaskCount} 个待执行定时任务。取消分享不会取消任务，任务仍会按原计划以你的身份执行。`
    : '取消后，被分享人将立即失去查看和操作权限；以后重新启用仍复用当前链接。'
  try {
    await ElMessageBox.confirm(taskWarning, '确认取消分享？', {
      confirmButtonText: '取消分享',
      cancelButtonText: '暂不取消',
      type: 'warning',
    })
  } catch {
    return
  }
  revoking.value = true
  try {
    const updated = await props.api.revokeSessionCollaborationShare(sessionId, current.version)
    share.value = updated
    emit('updated', updated)
    ElMessage.success('分享已取消，原链接已保留')
  } catch (error) {
    ElMessage.error(errorMessage(error, '取消分享失败'))
    if (error instanceof BackendApiError && error.status === 409) await loadShare()
  } finally {
    revoking.value = false
  }
}

async function copyLink() {
  if (!shareLink.value) return
  copied.value = await copyTextToClipboard(shareLink.value)
  if (copied.value) ElMessage.success('分享链接已复制')
}

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof BackendApiError) return error.message || fallback
  return error instanceof Error ? error.message : fallback
}
</script>

<template>
  <el-dialog
    :model-value="open"
    width="680px"
    class="session-share-dialog"
    destroy-on-close
    :close-on-click-modal="false"
    @close="emit('close')"
  >
    <template #header>
      <div class="session-share-dialog__heading">
        <span class="session-share-dialog__icon"><Share2 :size="18" /></span>
        <div>
          <h2>协作分享</h2>
          <p>{{ session?.title || '当前会话' }}</p>
        </div>
      </div>
    </template>

    <div v-if="loading" class="session-share-dialog__state">正在读取分享设置…</div>
    <div v-else class="session-share-dialog__body">
      <p v-if="loadError" class="session-share-dialog__error">{{ loadError }}</p>

      <section v-if="share" class="session-share-dialog__link-card">
        <div>
          <span class="session-share-dialog__eyebrow">唯一分享链接</span>
          <strong>{{ share.status === 'ACTIVE' ? '当前有效' : '已取消，可重新启用' }}</strong>
        </div>
        <div class="session-share-dialog__link-row">
          <input :value="shareLink" readonly aria-label="唯一分享链接" />
          <button type="button" @click="copyLink">
            <Check v-if="copied" :size="15" />
            <Copy v-else :size="15" />
            {{ copied ? '已复制' : '复制' }}
          </button>
        </div>
      </section>

      <section class="session-share-dialog__section">
        <div class="session-share-dialog__section-head">
          <div>
            <h3>有效期</h3>
            <p>从本次保存时刻起计算，最长 7 天。</p>
          </div>
          <span v-if="share">当前到期：{{ new Date(share.expiresAt).toLocaleString('zh-CN') }}</span>
        </div>
        <div class="session-share-dialog__expiry" role="radiogroup" aria-label="分享有效期">
          <button
            v-for="days in [1, 3, 7]"
            :key="days"
            type="button"
            :class="{ 'is-active': expiryDays === days }"
            role="radio"
            :aria-checked="expiryDays === days"
            @click="expiryDays = days"
          >{{ days }} 天</button>
        </div>
      </section>

      <section class="session-share-dialog__section">
        <div class="session-share-dialog__section-head">
          <div>
            <h3>被分享人</h3>
            <p>逐人设置只读或完整代操作权限。</p>
          </div>
          <span>{{ members.length }} / {{ SESSION_SHARE_MAX_MEMBERS }}</span>
        </div>
        <div class="session-share-dialog__search">
          <Search :size="15" />
          <input v-model="searchQuery" placeholder="按姓名或统一认证号搜索" aria-label="搜索被分享人" />
          <span v-if="candidatesLoading">搜索中…</span>
        </div>
        <div v-if="searchQuery.trim() || visibleCandidates.length" class="session-share-dialog__candidates">
          <button
            v-for="candidate in visibleCandidates"
            :key="candidate.userId"
            type="button"
            :disabled="memberLimitReached"
            @click="addMember(candidate)"
          >
            <UserPlus :size="14" />
            <span><strong>{{ candidate.username }}</strong><small>{{ candidate.unifiedAuthId }}</small></span>
          </button>
          <span v-if="!candidatesLoading && visibleCandidates.length === 0">没有可添加的用户</span>
        </div>

        <div class="session-share-dialog__members">
          <p v-if="members.length === 0" class="session-share-dialog__empty">尚未添加被分享人</p>
          <article v-for="member in members" :key="member.userId">
            <div class="session-share-dialog__member-name">
              <span>{{ member.username.slice(0, 1).toUpperCase() }}</span>
              <div><strong>{{ member.username }}</strong><small>{{ member.unifiedAuthId }}</small></div>
            </div>
            <el-switch
              v-model="member.canChat"
              inactive-text="只读"
              active-text="可对话"
              class="session-share-dialog__permission-switch"
              :aria-label="`设置 ${member.username} 权限`"
            />
            <button type="button" class="session-share-dialog__remove" :aria-label="`移除 ${member.username}`" @click="removeMember(member.userId)">
              <X :size="15" />
            </button>
          </article>
        </div>
      </section>

      <p v-if="pendingTaskCount > 0" class="session-share-dialog__task-warning">
        当前会话有 {{ pendingTaskCount }} 个待执行定时任务；分享失效后仍将按原计划执行。
      </p>
    </div>

    <template #footer>
      <div class="session-share-dialog__footer">
        <button
          v-if="share?.status === 'ACTIVE'"
          type="button"
          class="session-share-dialog__revoke"
          :disabled="revoking || saving"
          @click="revokeShare"
        ><Trash2 :size="14" />{{ revoking ? '正在取消…' : '取消分享' }}</button>
        <span class="session-share-dialog__footer-spacer" />
        <el-button @click="emit('close')">关闭</el-button>
        <el-button type="primary" :loading="saving" :disabled="loading" @click="saveShare">
          {{ share?.status === 'REVOKED' ? '重新启用并保存' : share ? '保存修改' : '创建分享' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.session-share-dialog__heading { display: flex; align-items: center; gap: 11px; }
.session-share-dialog__heading h2, .session-share-dialog__heading p { margin: 0; }
.session-share-dialog__heading h2 { color: #17223b; font-size: 18px; }
.session-share-dialog__heading p { margin-top: 3px; color: #667085; font-size: 12px; }
.session-share-dialog__icon { display: grid; width: 36px; height: 36px; place-content: center; border-radius: 11px; background: #eaf0ff; color: #315ed8; }
.session-share-dialog__body { display: grid; gap: 18px; }
.session-share-dialog__state { padding: 50px; text-align: center; color: #667085; }
.session-share-dialog__error, .session-share-dialog__task-warning { margin: 0; border-radius: 8px; padding: 9px 11px; font-size: 12px; }
.session-share-dialog__error { background: #fff0f0; color: #b42318; }
.session-share-dialog__task-warning { background: #fff8e7; color: #8a4b08; }
.session-share-dialog__link-card { display: grid; gap: 9px; border: 1px solid #d8e2fb; border-radius: 12px; background: linear-gradient(135deg, #f7f9ff, #eef3ff); padding: 13px; }
.session-share-dialog__link-card > div:first-child { display: flex; justify-content: space-between; align-items: center; }
.session-share-dialog__link-card strong { color: #3159b8; font-size: 12px; }
.session-share-dialog__eyebrow { color: #475467; font-size: 12px; font-weight: 650; }
.session-share-dialog__link-row { display: flex; gap: 8px; }
.session-share-dialog__link-row input { min-width: 0; flex: 1; border: 1px solid #cbd7f2; border-radius: 8px; background: white; padding: 8px 10px; color: #344054; }
.session-share-dialog__link-row button, .session-share-dialog__expiry button, .session-share-dialog__candidates button, .session-share-dialog__revoke { display: inline-flex; align-items: center; gap: 5px; border: 1px solid #cbd7f2; border-radius: 8px; background: white; color: #3159b8; padding: 7px 10px; cursor: pointer; }
.session-share-dialog__section { display: grid; gap: 10px; }
.session-share-dialog__section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.session-share-dialog__section-head h3, .session-share-dialog__section-head p { margin: 0; }
.session-share-dialog__section-head h3 { color: #1d2939; font-size: 14px; }
.session-share-dialog__section-head p, .session-share-dialog__section-head > span { margin-top: 3px; color: #667085; font-size: 11px; }
.session-share-dialog__expiry { display: flex; gap: 8px; }
.session-share-dialog__expiry button.is-active { border-color: #315ed8; background: #315ed8; color: white; }
.session-share-dialog__search { display: flex; align-items: center; gap: 7px; border: 1px solid #d0d5dd; border-radius: 9px; padding: 0 10px; color: #667085; }
.session-share-dialog__search input { min-width: 0; flex: 1; border: 0; outline: 0; padding: 9px 0; }
.session-share-dialog__search span { font-size: 11px; }
.session-share-dialog__candidates { display: flex; max-height: 105px; flex-wrap: wrap; gap: 6px; overflow: auto; border-radius: 8px; background: #f8fafc; padding: 8px; color: #667085; font-size: 12px; }
.session-share-dialog__candidates button { padding: 5px 8px; color: #344054; }
.session-share-dialog__candidates button span { display: grid; text-align: left; }
.session-share-dialog__candidates small { color: #667085; }
.session-share-dialog__members { display: grid; max-height: 220px; gap: 6px; overflow: auto; }
.session-share-dialog__members article { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 10px; border: 1px solid #eaecf0; border-radius: 9px; padding: 8px 10px; }
.session-share-dialog__member-name { display: flex; min-width: 0; align-items: center; gap: 9px; }
.session-share-dialog__member-name > span { display: grid; width: 28px; height: 28px; flex: 0 0 auto; place-content: center; border-radius: 50%; background: #e8eefc; color: #3159b8; font-weight: 700; }
.session-share-dialog__member-name div { display: grid; min-width: 0; }
.session-share-dialog__member-name strong { overflow: hidden; color: #344054; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.session-share-dialog__member-name small { color: #667085; }
.session-share-dialog__permission-switch :deep(.el-switch__label) { font-size: 12px; color: #667085; font-weight: 500; user-select: none; }
.session-share-dialog__permission-switch :deep(.el-switch__label.is-active) { color: #315ed8; font-weight: 600; }
.session-share-dialog__permission-switch :deep(.el-switch__core) { border-color: #cbd7f2; }
.session-share-dialog__remove { display: grid; border: 0; background: transparent; color: #98a2b3; cursor: pointer; place-content: center; }
.session-share-dialog__empty { margin: 0; border: 1px dashed #d0d5dd; border-radius: 9px; padding: 15px; color: #98a2b3; text-align: center; }
.session-share-dialog__footer { display: flex; width: 100%; align-items: center; gap: 8px; }
.session-share-dialog__footer-spacer { flex: 1; }
.session-share-dialog__revoke { border-color: #fecdca; color: #b42318; }
</style>
