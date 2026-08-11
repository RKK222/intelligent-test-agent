import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UserNotification } from '@test-agent/shared-types'
import UserNotificationCenter from '../src/components/UserNotificationCenter.vue'

const notifications: UserNotification[] = [
  {
    notificationId: 'ntf_active',
    type: 'SESSION_SHARED',
    actorUserId: 'usr_owner',
    title: '会话所属人 向你分享了对话',
    body: '登录失败排查 · 只读',
    actionType: 'SESSION_SHARE',
    actionTargetId: 'shr_active',
    status: 'ACTIVE',
    invalidationReason: null,
    actionAvailable: true,
    unread: true,
    expiresAt: '2026-08-11T09:00:00Z',
    readAt: null,
    createdAt: '2026-08-10T09:00:00Z',
    updatedAt: '2026-08-10T09:00:00Z',
  },
  {
    notificationId: 'ntf_invalid',
    type: 'SESSION_SHARED',
    actorUserId: 'usr_owner',
    title: '另一位同事 向你分享了对话',
    body: '已撤销会话 · 可对话',
    actionType: 'SESSION_SHARE',
    actionTargetId: 'shr_invalid',
    status: 'INVALIDATED',
    invalidationReason: 'REVOKED',
    actionAvailable: false,
    unread: false,
    expiresAt: '2026-08-11T09:00:00Z',
    readAt: null,
    createdAt: '2026-08-10T08:00:00Z',
    updatedAt: '2026-08-10T08:30:00Z',
  },
]

const disposeNotifications: UserNotification[] = [
  {
    notificationId: 'ntf_pending',
    type: 'AGENT_CONFIG_DISPOSE_PENDING',
    actorUserId: null,
    title: 'Agent 配置等待生效',
    body: '配置已更新，正在等待当前任务结束后生效。',
    actionType: 'NONE',
    actionTargetId: 'rollout_pending',
    status: 'ACTIVE',
    invalidationReason: null,
    actionAvailable: false,
    unread: true,
    expiresAt: null,
    readAt: null,
    createdAt: '2026-08-10T08:00:00Z',
    updatedAt: '2026-08-11T08:10:00Z',
  },
  {
    notificationId: 'ntf_failed',
    type: 'AGENT_CONFIG_DISPOSE_FAILED',
    actorUserId: null,
    title: 'Agent 配置生效失败',
    body: '配置运行态更新失败，可以重启进程后重试。',
    actionType: 'RESTART_OWN_PROCESS',
    actionTargetId: 'rollout_failed',
    status: 'ACTIVE',
    invalidationReason: null,
    actionAvailable: true,
    unread: true,
    expiresAt: null,
    readAt: null,
    createdAt: '2026-08-10T08:00:00Z',
    updatedAt: '2026-08-11T08:20:00Z',
  },
  {
    notificationId: 'ntf_succeeded',
    type: 'AGENT_CONFIG_DISPOSE_SUCCEEDED',
    actorUserId: null,
    title: 'Agent 配置已生效',
    body: '新的 Agent 配置已应用到当前进程。',
    actionType: 'NONE',
    actionTargetId: 'rollout_succeeded',
    status: 'ACTIVE',
    invalidationReason: null,
    actionAvailable: false,
    unread: false,
    expiresAt: null,
    readAt: '2026-08-11T08:25:00Z',
    createdAt: '2026-08-10T08:00:00Z',
    updatedAt: '2026-08-11T08:25:00Z',
  },
  {
    notificationId: 'ntf_unknown',
    type: 'FUTURE_NOTIFICATION_TYPE',
    actorUserId: null,
    title: '未来通知',
    body: '客户端尚不支持该通知。',
    actionType: 'FUTURE_ACTION',
    actionTargetId: 'opaque_target',
    status: 'ACTIVE',
    invalidationReason: null,
    actionAvailable: true,
    unread: true,
    expiresAt: null,
    readAt: null,
    createdAt: '2026-08-10T08:00:00Z',
    updatedAt: '2026-08-11T08:30:00Z',
  },
]

describe('UserNotificationCenter', () => {
  afterEach(() => vi.restoreAllMocks())

  it('shows the ICBC-red unread badge, filters, paginates and only opens available actions', async () => {
    const wrapper = mount(UserNotificationCenter, {
      props: {
        notifications,
        unreadCount: 7,
        filter: 'ALL',
        hasMore: true,
      },
      attachTo: document.body,
    })

    const trigger = wrapper.get('[data-testid="notification-center-trigger"]')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(wrapper.get('.user-notification-center__badge').text()).toBe('7')

    await trigger.trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('[data-testid="notification-center-panel"]').attributes('role')).toBe('dialog')
    expect(wrapper.text()).toContain('登录失败排查')
    expect(wrapper.text()).toContain('只读')
    expect(wrapper.text()).toContain('分享已失效')

    const tabs = wrapper.findAll('[role="tab"]')
    await tabs[1]!.trigger('click')
    expect(wrapper.emitted('update:filter')?.[0]).toEqual(['UNREAD'])

    const itemButtons = wrapper.findAll('.user-notification-center__item > button')
    await itemButtons[0]!.trigger('click')
    expect(wrapper.emitted('open-notification')?.[0]).toEqual([notifications[0]])
    expect(itemButtons[1]!.attributes('disabled')).toBeDefined()
    await itemButtons[1]!.trigger('click')
    expect(wrapper.emitted('open-notification')).toHaveLength(1)

    await wrapper.get('.user-notification-center__footer button').trigger('click')
    expect(wrapper.emitted('load-more')).toHaveLength(1)
    wrapper.unmount()
  })

  it('closes with Escape or outside click and restores focus to the bell', async () => {
    const wrapper = mount(UserNotificationCenter, {
      props: { notifications, unreadCount: 1 },
      attachTo: document.body,
    })
    const trigger = wrapper.get<HTMLButtonElement>('[data-testid="notification-center-trigger"]')

    await trigger.trigger('click')
    expect(document.activeElement).toBe(wrapper.get('[data-testid="notification-center-panel"]').element)
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="notification-center-panel"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)

    await trigger.trigger('click')
    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('[data-testid="notification-center-panel"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders loading, error retry and unread empty states', async () => {
    const wrapper = mount(UserNotificationCenter, {
      props: { loading: true, notifications: [] },
    })
    await wrapper.get('[data-testid="notification-center-trigger"]').trigger('click')
    expect(wrapper.text()).toContain('正在读取通知')

    await wrapper.setProps({ loading: false, error: '网络暂不可用' })
    expect(wrapper.text()).toContain('通知暂时加载失败')
    expect(wrapper.text()).toContain('网络暂不可用')
    await wrapper.get('.user-notification-center__state button').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(2)

    await wrapper.setProps({ error: null, filter: 'UNREAD' })
    expect(wrapper.text()).toContain('未读消息已经处理完')
  })

  it('renders dispose states and only emits controlled mark-read or restart actions', async () => {
    const wrapper = mount(UserNotificationCenter, {
      props: { notifications: disposeNotifications, unreadCount: 3 },
    })

    await wrapper.get('[data-testid="notification-center-trigger"]').trigger('click')
    expect(wrapper.text()).toContain('等待生效')
    expect(wrapper.text()).toContain('配置运行态更新失败')
    expect(wrapper.text()).toContain('重启进程')
    expect(wrapper.text()).toContain('已生效')

    const pendingButton = wrapper.get('[data-testid="notification-item-ntf_pending"]')
    expect(pendingButton.text()).toContain('标记已读')
    await pendingButton.trigger('click')
    expect(wrapper.emitted('open-notification')?.[0]).toEqual([disposeNotifications[0]])

    const failedButton = wrapper.get('[data-testid="notification-item-ntf_failed"]')
    await failedButton.trigger('click')
    expect(wrapper.emitted('open-notification')?.[1]).toEqual([disposeNotifications[1]])

    expect(wrapper.get('[data-testid="notification-item-ntf_succeeded"]').attributes('disabled')).toBeDefined()
    const unknownButton = wrapper.get('[data-testid="notification-item-ntf_unknown"]')
    expect(unknownButton.attributes('disabled')).toBeDefined()
    await unknownButton.trigger('click')
    expect(wrapper.emitted('open-notification')).toHaveLength(2)
  })
})
