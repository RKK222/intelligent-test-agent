<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import {
  Bell,
  CircleOff,
  ExternalLink,
  Inbox,
  LoaderCircle,
  Mail,
  MailOpen,
  RefreshCw,
} from 'lucide-vue-next'
import type { UserNotification } from '@test-agent/shared-types'
import { parseLocalClientNotificationUpdate } from './local-client-notification-update'

export type UserNotificationFilter = 'ALL' | 'UNREAD'
type NotificationVisualState = 'UNREAD' | 'READ' | 'INACTIVE'

const props = withDefaults(defineProps<{
  notifications?: UserNotification[]
  unreadCount?: number
  filter?: UserNotificationFilter
  loading?: boolean
  loadingMore?: boolean
  hasMore?: boolean
  error?: string | null
}>(), {
  notifications: () => [],
  unreadCount: 0,
  filter: 'UNREAD',
  loading: false,
  loadingMore: false,
  hasMore: false,
  error: null,
})

const emit = defineEmits<{
  (event: 'update:filter', filter: UserNotificationFilter): void
  (event: 'refresh'): void
  (event: 'load-more'): void
  (event: 'open-notification', notification: UserNotification): void
}>()

const open = ref(false)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const badgeText = computed(() => props.unreadCount > 99 ? '99+' : String(props.unreadCount))

function toggle() {
  if (open.value) {
    close()
    return
  }
  open.value = true
  emit('refresh')
  document.addEventListener('pointerdown', handleOutside, true)
  document.addEventListener('keydown', handleDocumentKeydown)
  void nextTick(() => panel.value?.focus())
}

function close() {
  if (!open.value) return
  open.value = false
  document.removeEventListener('pointerdown', handleOutside, true)
  document.removeEventListener('keydown', handleDocumentKeydown)
  void nextTick(() => trigger.value?.focus())
}

function handleOutside(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) close()
}

function handleDocumentKeydown(event: KeyboardEvent) {
  if (event.key !== 'Escape') return
  event.preventDefault()
  close()
}

function selectFilter(filter: UserNotificationFilter) {
  if (filter !== props.filter) emit('update:filter', filter)
}

function openNotification(notification: UserNotification) {
  if (!canOpenNotification(notification)) return
  emit('open-notification', notification)
}

type NotificationKind =
  | 'SESSION_SHARE'
  | 'DISPOSE_PENDING'
  | 'DISPOSE_SUCCEEDED'
  | 'DISPOSE_FAILED'
  | 'DISPOSE_SUPERSEDED'
  | 'OPENCODE_CAPACITY'
  | 'LOCAL_CLIENT_UPDATE'
  | 'LOCAL_CLIENT_PUBLIC_CAPABILITY_UPDATE'
  | 'UNKNOWN'

/** 未知类型或类型/动作组合必须失败关闭，不能把 actionTargetId 当作 URL 或其它命令。 */
function notificationKind(notification: UserNotification): NotificationKind {
  if (notification.type === 'SESSION_SHARED' && notification.actionType === 'SESSION_SHARE') return 'SESSION_SHARE'
  if (notification.type === 'AGENT_CONFIG_DISPOSE_PENDING' && notification.actionType === 'NONE') return 'DISPOSE_PENDING'
  if (notification.type === 'AGENT_CONFIG_DISPOSE_SUCCEEDED' && notification.actionType === 'NONE') return 'DISPOSE_SUCCEEDED'
  if (notification.type === 'AGENT_CONFIG_DISPOSE_FAILED' && notification.actionType === 'RESTART_OWN_PROCESS') return 'DISPOSE_FAILED'
  if (notification.type === 'AGENT_CONFIG_DISPOSE_SUPERSEDED' && notification.actionType === 'NONE') return 'DISPOSE_SUPERSEDED'
  if (notification.type === 'OPENCODE_CAPACITY_WARNING' && notification.actionType === 'NONE') return 'OPENCODE_CAPACITY'
  if (notification.type === 'LOCAL_CLIENT_UPDATE_AVAILABLE' && notification.actionType === 'LOCAL_CLIENT_UPDATE') return 'LOCAL_CLIENT_UPDATE'
  if (notification.type === 'LOCAL_CLIENT_PUBLIC_CAPABILITY_AVAILABLE'
    && notification.actionType === 'LOCAL_CLIENT_PUBLIC_CAPABILITY_UPDATE') return 'LOCAL_CLIENT_PUBLIC_CAPABILITY_UPDATE'
  return 'UNKNOWN'
}

function isDisposeNotification(notification: UserNotification) {
  return notificationKind(notification).startsWith('DISPOSE_')
}

function canOpenNotification(notification: UserNotification) {
  const kind = notificationKind(notification)
  if (kind === 'SESSION_SHARE') return notification.actionAvailable
  if (kind === 'DISPOSE_FAILED') return notification.actionAvailable
  if (kind === 'LOCAL_CLIENT_UPDATE') return parseLocalClientNotificationUpdate(notification) !== null
  if (kind === 'LOCAL_CLIENT_PUBLIC_CAPABILITY_UPDATE') return notification.actionAvailable
  if (kind === 'DISPOSE_PENDING' || kind === 'DISPOSE_SUCCEEDED' || kind === 'DISPOSE_SUPERSEDED'
    || kind === 'OPENCODE_CAPACITY') {
    return notification.unread
  }
  return false
}

function shouldDimNotification(notification: UserNotification) {
  const kind = notificationKind(notification)
  return kind === 'UNKNOWN'
    || (kind === 'SESSION_SHARE' && !notification.actionAvailable)
    || (kind === 'LOCAL_CLIENT_UPDATE' && !canOpenNotification(notification))
    || (kind === 'LOCAL_CLIENT_PUBLIC_CAPABILITY_UPDATE' && !canOpenNotification(notification))
}

function notificationTimestamp(notification: UserNotification) {
  return isDisposeNotification(notification) ? notification.updatedAt : notification.createdAt
}

/** 配置通知按类型使用当前用户文案，让数据库中的历史技术文案也能直接展示为易懂内容。 */
function notificationTitle(notification: UserNotification) {
  switch (notificationKind(notification)) {
    case 'DISPOSE_PENDING': return '智能体配置正在更新'
    case 'DISPOSE_SUCCEEDED': return '智能体配置更新成功'
    case 'DISPOSE_FAILED': return '智能体配置更新失败'
    case 'DISPOSE_SUPERSEDED': return '这次配置更新已结束'
    default: return notification.title
  }
}

function notificationBody(notification: UserNotification) {
  switch (notificationKind(notification)) {
    case 'SESSION_SHARE': return sessionTitle(notification)
    case 'DISPOSE_PENDING': return '当前任务结束后会自动加载新配置。'
    case 'DISPOSE_SUCCEEDED': return '新配置已经加载，可以正常使用。'
    case 'DISPOSE_FAILED': return '新配置暂未加载，请重启智能体后再试。'
    case 'DISPOSE_SUPERSEDED': return '已有更新的配置，这条通知不用处理。'
    default: return notification.body
  }
}

function notificationStateLabel(notification: UserNotification) {
    switch (notificationKind(notification)) {
      case 'SESSION_SHARE': return permissionLabel(notification)
      case 'DISPOSE_PENDING': return '更新中'
      case 'DISPOSE_SUCCEEDED': return '已更新'
      case 'DISPOSE_FAILED': return '更新失败'
      case 'DISPOSE_SUPERSEDED': return '不用处理'
      case 'OPENCODE_CAPACITY': return '容量预警'
      case 'LOCAL_CLIENT_UPDATE': return localClientUpdateDirectionLabel(notification) ?? '版本信息不可用'
      case 'LOCAL_CLIENT_PUBLIC_CAPABILITY_UPDATE': return '等待确认'
      default: return '暂不支持'
  }
}

function notificationActionLabel(notification: UserNotification) {
  switch (notificationKind(notification)) {
    case 'SESSION_SHARE':
      return notification.actionAvailable ? formatExpiry(notification.expiresAt) : invalidationLabel(notification)
    case 'DISPOSE_FAILED':
      return notification.actionAvailable ? '重启智能体' : '暂时无法重启'
    case 'DISPOSE_PENDING':
    case 'DISPOSE_SUCCEEDED':
    case 'DISPOSE_SUPERSEDED':
    case 'OPENCODE_CAPACITY':
      return notification.unread ? '标记已读' : '已读'
    case 'LOCAL_CLIENT_UPDATE': {
      const direction = localClientUpdateDirectionLabel(notification)
      return direction ? `立即${direction}` : '暂不可用'
    }
    case 'LOCAL_CLIENT_PUBLIC_CAPABILITY_UPDATE':
      return notification.actionAvailable ? '更新公共能力' : '暂不可用'
    default:
      return '不支持的通知动作'
  }
}

function localClientUpdateDirectionLabel(notification: UserNotification) {
  const direction = parseLocalClientNotificationUpdate(notification)?.direction
  if (direction === 'UPDATE') return '更新'
  if (direction === 'ROLLBACK') return '回退'
  return null
}

function notificationAriaLabel(notification: UserNotification) {
  // 会话分享保留既有“新标签页打开”语义，避免类型扩展改变辅助技术和自动化定位契约。
  if (notificationKind(notification) === 'SESSION_SHARE' && notification.actionAvailable) {
    return `${notificationTitle(notification)}，${notificationVisualLabel(notification)}，在新标签页打开`
  }
  return `${notificationTitle(notification)}，${notificationVisualLabel(notification)}，${notificationActionLabel(notification)}`
}

function permissionLabel(notification: UserNotification) {
  const segments = notification.body.split(' · ')
  return segments.at(-1) || '查看分享'
}

function sessionTitle(notification: UserNotification) {
  const segments = notification.body.split(' · ')
  return segments.length > 1 ? segments.slice(0, -1).join(' · ') : notification.body
}

function invalidationLabel(notification: UserNotification) {
  if (notification.status === 'INVALIDATED') return '分享已失效'
  if (notification.expiresAt && Date.parse(notification.expiresAt) <= Date.now()) return '分享已过期'
  return '暂不可打开'
}

/**
 * 已失效或过期但从未打开的通知不能伪装成“已读”；这类记录使用独立停用图标。
 * 有效通知沿用后端 unread，已成功访问的历史通知则以 readAt 展示打开信封。
 */
function notificationVisualState(notification: UserNotification): NotificationVisualState {
  if (notification.unread) return 'UNREAD'
  if (notification.readAt) return 'READ'
  return 'INACTIVE'
}

function notificationVisualLabel(notification: UserNotification) {
  const state = notificationVisualState(notification)
  if (state === 'UNREAD') return '未读通知'
  if (state === 'READ') return '已读通知'
  return '已失效通知'
}

function formatTime(value: string) {
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return value
  const diff = Date.now() - timestamp
  if (diff >= 0 && diff < 60_000) return '刚刚'
  if (diff >= 0 && diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff >= 0 && diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(timestamp))
}

function formatExpiry(value?: string | null) {
  if (!value) return '长期有效'
  const timestamp = Date.parse(value)
  if (!Number.isFinite(timestamp)) return value
  return `${new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(timestamp))} 到期`
}

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleOutside, true)
  document.removeEventListener('keydown', handleDocumentKeydown)
})
</script>

<template>
  <div ref="root" class="user-notification-center" @click.stop>
    <button
      ref="trigger"
      type="button"
      :class="['user-notification-center__trigger', open && 'is-open']"
      data-testid="notification-center-trigger"
      aria-label="打开通知中心"
      :aria-expanded="open"
      aria-haspopup="dialog"
      aria-controls="user-notification-center-panel"
      title="通知中心"
      @click="toggle"
    >
      <Bell :size="20" :stroke-width="1.5" aria-hidden="true" />
      <span
        v-if="unreadCount > 0"
        class="user-notification-center__badge"
        :aria-label="`${unreadCount} 条未读通知`"
      >{{ badgeText }}</span>
    </button>

    <section
      v-if="open"
      id="user-notification-center-panel"
      ref="panel"
      class="user-notification-center__panel"
      role="dialog"
      aria-modal="false"
      aria-labelledby="user-notification-center-title"
      tabindex="-1"
      data-testid="notification-center-panel"
    >
      <header class="user-notification-center__header">
        <div>
          <h2 id="user-notification-center-title">通知</h2>
          <p>{{ unreadCount > 0 ? `${unreadCount} 条未读` : '暂无未读通知' }}</p>
        </div>
        <button type="button" aria-label="刷新通知" title="刷新" :disabled="loading" @click="emit('refresh')">
          <RefreshCw :size="16" :class="{ 'is-spinning': loading }" />
        </button>
      </header>

      <div class="user-notification-center__tabs" role="tablist" aria-label="通知筛选">
        <button
          type="button"
          role="tab"
          :aria-selected="filter === 'UNREAD'"
          :class="{ 'is-active': filter === 'UNREAD' }"
          @click="selectFilter('UNREAD')"
        >未读 <span v-if="unreadCount > 0">{{ badgeText }}</span></button>
        <button
          type="button"
          role="tab"
          :aria-selected="filter === 'ALL'"
          :class="{ 'is-active': filter === 'ALL' }"
          @click="selectFilter('ALL')"
        >全部</button>
      </div>

      <div class="user-notification-center__content" role="tabpanel" :aria-busy="loading">
        <div v-if="loading && notifications.length === 0" class="user-notification-center__state">
          <LoaderCircle :size="22" class="is-spinning" />
          <span>正在读取通知…</span>
        </div>
        <div v-else-if="error && notifications.length === 0" class="user-notification-center__state is-error">
          <RefreshCw :size="22" />
          <strong>通知暂时加载失败</strong>
          <span>{{ error }}</span>
          <button type="button" @click="emit('refresh')">重试</button>
        </div>
        <div v-else-if="notifications.length === 0" class="user-notification-center__state">
          <Inbox :size="26" />
          <strong>{{ filter === 'UNREAD' ? '未读消息已经处理完' : '暂时没有通知' }}</strong>
          <span>{{ filter === 'UNREAD' ? '新的分享和配置状态会第一时间出现在这里' : '会话分享和 Agent 配置状态会出现在这里' }}</span>
        </div>
        <div v-else class="user-notification-center__list" role="list">
          <article
            v-for="notification in notifications"
            :key="notification.notificationId"
            :class="[
              'user-notification-center__item',
              notification.unread && 'is-unread',
              notificationVisualState(notification) === 'READ' && 'is-read',
              `is-${notificationKind(notification).toLowerCase().replaceAll('_', '-')}`,
              shouldDimNotification(notification) && 'is-invalid'
            ]"
            :data-read-state="notificationVisualState(notification).toLowerCase()"
            role="listitem"
          >
            <button
              type="button"
              :data-testid="`notification-item-${notification.notificationId}`"
              :disabled="!canOpenNotification(notification)"
              :aria-label="notificationAriaLabel(notification)"
              @click="openNotification(notification)"
            >
              <span
                class="user-notification-center__item-icon"
                :data-notification-state="notificationVisualState(notification).toLowerCase()"
                :title="notificationVisualLabel(notification)"
              >
                <Mail v-if="notificationVisualState(notification) === 'UNREAD'" :size="17" aria-hidden="true" />
                <MailOpen v-else-if="notificationVisualState(notification) === 'READ'" :size="17" aria-hidden="true" />
                <CircleOff v-else :size="17" aria-hidden="true" />
                <span class="user-notification-center__sr-only">{{ notificationVisualLabel(notification) }}</span>
              </span>
              <span class="user-notification-center__item-main">
                <span class="user-notification-center__item-heading">
                  <strong>{{ notificationTitle(notification) }}</strong>
                  <time :datetime="notificationTimestamp(notification)">{{ formatTime(notificationTimestamp(notification)) }}</time>
                </span>
                <span class="user-notification-center__session-title">{{ notificationBody(notification) }}</span>
                <span class="user-notification-center__meta">
                  <em>{{ notificationStateLabel(notification) }}</em>
                  <span>{{ notificationActionLabel(notification) }}</span>
                </span>
              </span>
              <ExternalLink
                v-if="notificationKind(notification) === 'SESSION_SHARE' && canOpenNotification(notification)"
                class="user-notification-center__open-icon"
                :size="15"
                aria-hidden="true"
              />
              <RefreshCw
                v-else-if="notificationKind(notification) === 'DISPOSE_FAILED' && canOpenNotification(notification)"
                class="user-notification-center__open-icon"
                :size="15"
                aria-hidden="true"
              />
              <RefreshCw
                v-else-if="notificationKind(notification) === 'LOCAL_CLIENT_UPDATE' && canOpenNotification(notification)"
                class="user-notification-center__open-icon"
                :size="15"
                aria-hidden="true"
              />
              <RefreshCw
                v-else-if="notificationKind(notification) === 'LOCAL_CLIENT_PUBLIC_CAPABILITY_UPDATE' && canOpenNotification(notification)"
                class="user-notification-center__open-icon"
                :size="15"
                aria-hidden="true"
              />
            </button>
          </article>
        </div>
      </div>

      <footer v-if="notifications.length > 0 || error" class="user-notification-center__footer">
        <span v-if="error">更新失败，可稍后重试</span>
        <button v-if="hasMore" type="button" :disabled="loadingMore" @click="emit('load-more')">
          <LoaderCircle v-if="loadingMore" :size="14" class="is-spinning" />
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </button>
        <span v-else-if="!error">已经到底了</span>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.user-notification-center { position: relative; display: inline-flex; }
.user-notification-center__trigger { position: relative; display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border: 0; border-radius: 9px; background: transparent; color: var(--ta-shell-muted, #6b7280); cursor: pointer; padding: 0; transition: background-color .14s ease, color .14s ease; }
.user-notification-center__trigger:hover, .user-notification-center__trigger:focus-visible { background: var(--ta-shell-hover, #f3f4f6); color: var(--ta-shell-text, #1f2937); outline: none; }
.user-notification-center__trigger.is-open { background: var(--ta-shell-accent-soft, #fdf2f2); color: var(--ta-shell-accent-strong, #991b1b); }
.user-notification-center__trigger.is-open::before { position: absolute; top: 6px; left: 0; width: 3px; height: 16px; border-radius: 0 999px 999px 0; background: var(--ta-shell-accent, #c8161d); content: ''; }
.user-notification-center__badge { position: absolute; top: -4px; right: -7px; display: grid; min-width: 17px; height: 17px; place-content: center; border: 2px solid #fff; border-radius: 999px; background: var(--ta-shell-accent, #c8161d); color: #fff; font-size: 9px; font-style: normal; font-weight: 700; line-height: 1; padding: 0 3px; }
.user-notification-center__panel { position: absolute; top: calc(100% + 10px); right: -138px; z-index: 90; display: flex; width: min(400px, calc(100vw - 24px)); max-height: min(650px, calc(100vh - 76px)); flex-direction: column; overflow: hidden; border: 1px solid #e5e7eb; border-radius: 14px; outline: none; background: #fff; box-shadow: 0 18px 44px rgb(17 24 39 / 16%), 0 3px 10px rgb(17 24 39 / 8%); color: #1f2937; }
.user-notification-center__panel::before { position: absolute; top: 0; left: 0; width: 4px; height: 52px; border-radius: 14px 0 4px; background: var(--ta-shell-accent, #c8161d); content: ''; }
.user-notification-center__header { display: flex; min-height: 60px; align-items: center; justify-content: space-between; border-bottom: 1px solid #eceff3; padding: 10px 14px 9px 18px; }
.user-notification-center__header h2, .user-notification-center__header p { margin: 0; }
.user-notification-center__header h2 { font-size: 16px; line-height: 22px; }
.user-notification-center__header p { margin-top: 1px; color: #6b7280; font-size: 11px; }
.user-notification-center__header button { display: grid; width: 30px; height: 30px; place-content: center; border: 0; border-radius: 8px; background: transparent; color: #6b7280; cursor: pointer; }
.user-notification-center__header button:hover, .user-notification-center__header button:focus-visible { background: #f3f4f6; color: #991b1b; outline: none; }
.user-notification-center__tabs { display: flex; gap: 18px; border-bottom: 1px solid #eceff3; padding: 0 16px; }
.user-notification-center__tabs button { position: relative; display: inline-flex; min-height: 38px; align-items: center; gap: 5px; border: 0; background: transparent; color: #6b7280; cursor: pointer; font-size: 12px; font-weight: 600; padding: 0 2px; }
.user-notification-center__tabs button.is-active { color: #991b1b; }
.user-notification-center__tabs button.is-active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; background: #c8161d; content: ''; }
.user-notification-center__tabs button:focus-visible { border-radius: 4px; outline: 2px solid rgb(200 22 29 / 28%); outline-offset: 2px; }
.user-notification-center__tabs span { min-width: 17px; border-radius: 999px; background: #fdf2f2; color: #991b1b; font-size: 9px; line-height: 17px; padding: 0 4px; }
.user-notification-center__content { min-height: 210px; flex: 1; overflow: auto; background: #f8f9fa; }
.user-notification-center__state { display: flex; min-height: 250px; align-items: center; justify-content: center; flex-direction: column; gap: 7px; color: #9ca3af; text-align: center; padding: 30px; }
.user-notification-center__state strong { color: #4b5563; font-size: 13px; }
.user-notification-center__state span { max-width: 250px; font-size: 11px; line-height: 1.6; }
.user-notification-center__state.is-error { color: #b91c1c; }
.user-notification-center__state button { margin-top: 3px; border: 1px solid #fecaca; border-radius: 7px; background: #fff; color: #991b1b; cursor: pointer; padding: 6px 13px; }
.user-notification-center__list { display: grid; gap: 1px; background: #e9edf2; }
.user-notification-center__item { position: relative; background: #fff; }
.user-notification-center__item.is-unread::before { position: absolute; z-index: 1; top: 13px; bottom: 13px; left: 0; width: 3px; border-radius: 0 3px 3px 0; background: #c8161d; content: ''; }
.user-notification-center__item > button { display: grid; width: 100%; grid-template-columns: 34px minmax(0, 1fr) 16px; align-items: start; gap: 10px; border: 0; background: transparent; color: inherit; cursor: pointer; padding: 13px 14px 13px 16px; text-align: left; }
.user-notification-center__item > button:hover, .user-notification-center__item > button:focus-visible { background: #fdf7f7; outline: none; }
.user-notification-center__item > button:focus-visible { box-shadow: inset 0 0 0 2px rgb(200 22 29 / 25%); }
.user-notification-center__item.is-invalid > button { cursor: default; opacity: .62; }
.user-notification-center__item.is-invalid > button:hover { background: transparent; }
.user-notification-center__item-icon { display: grid; width: 34px; height: 34px; place-content: center; border-radius: 9px; transition: background-color .14s ease, color .14s ease; }
.user-notification-center__item-icon[data-notification-state='unread'] { background: #fdf2f2; color: #991b1b; }
.user-notification-center__item-icon[data-notification-state='read'] { background: #f1f3f5; color: #667085; }
.user-notification-center__item-icon[data-notification-state='inactive'] { background: #f3f4f6; color: #9ca3af; }
.user-notification-center__item-main { display: grid; min-width: 0; gap: 4px; }
.user-notification-center__item-heading { display: flex; min-width: 0; align-items: baseline; justify-content: space-between; gap: 8px; }
.user-notification-center__item-heading strong { min-width: 0; overflow: hidden; color: #1f2937; font-size: 12px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.user-notification-center__item.is-read .user-notification-center__item-heading strong { color: #4b5563; font-weight: 550; }
.user-notification-center__item-heading time { flex: 0 0 auto; color: #9ca3af; font-size: 10px; font-weight: 400; }
.user-notification-center__session-title { overflow: hidden; color: #4b5563; font-size: 12px; line-height: 18px; text-overflow: ellipsis; white-space: nowrap; }
.user-notification-center__meta { display: flex; min-width: 0; align-items: center; gap: 7px; color: #9ca3af; font-size: 10px; }
.user-notification-center__meta em { flex: 0 0 auto; border-radius: 4px; background: #f3f4f6; color: #6b7280; font-style: normal; padding: 2px 5px; }
.user-notification-center__meta > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-notification-center__open-icon { align-self: center; color: #9ca3af; }
.user-notification-center__sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); clip-path: inset(50%); white-space: nowrap; }
.user-notification-center__footer { display: flex; min-height: 40px; align-items: center; justify-content: center; border-top: 1px solid #eceff3; background: #fff; color: #9ca3af; font-size: 10px; padding: 7px 12px; }
.user-notification-center__footer button { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: #991b1b; cursor: pointer; font-size: 11px; font-weight: 600; padding: 5px 10px; }
.user-notification-center__footer button:focus-visible { border-radius: 5px; outline: 2px solid rgb(200 22 29 / 25%); }
.is-spinning { animation: user-notification-spin .8s linear infinite; }
@keyframes user-notification-spin { to { transform: rotate(360deg); } }
@media (max-width: 720px) { .user-notification-center__panel { position: fixed; top: 54px; right: 12px; left: 12px; width: auto; max-height: calc(100vh - 66px); } }
@media (prefers-reduced-motion: reduce) { .is-spinning { animation-duration: 1.8s; } }
</style>
