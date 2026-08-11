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
    notificationId: 'ntf_read',
    type: 'SESSION_SHARED',
    actorUserId: 'usr_owner',
    title: '已读同事 向你分享了对话',
    body: '已查看会话 · 可对话',
    actionType: 'SESSION_SHARE',
    actionTargetId: 'shr_read',
    status: 'ACTIVE',
    invalidationReason: null,
    actionAvailable: true,
    unread: false,
    expiresAt: '2026-08-11T09:00:00Z',
    readAt: '2026-08-10T08:30:00Z',
    createdAt: '2026-08-10T08:00:00Z',
    updatedAt: '2026-08-10T08:30:00Z',
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
    expect(wrapper.findAll('.user-notification-center__item').map((item) => item.attributes('data-read-state')))
      .toEqual(['unread', 'read', 'inactive'])
    expect(wrapper.findAll('.user-notification-center__item-icon').map((item) => item.attributes('title')))
      .toEqual(['未读通知', '已读通知', '已失效通知'])
    expect(wrapper.findAll('.user-notification-center__session-title').filter((item) => item.text() === '登录失败排查'))
      .toHaveLength(1)

    const tabs = wrapper.findAll('[role="tab"]')
    await tabs[1]!.trigger('click')
    expect(wrapper.emitted('update:filter')?.[0]).toEqual(['UNREAD'])

    const itemButtons = wrapper.findAll('.user-notification-center__item > button')
    await itemButtons[0]!.trigger('click')
    expect(wrapper.emitted('open-notification')?.[0]).toEqual([notifications[0]])
    expect(itemButtons[0]!.attributes('aria-label')).toContain('未读通知')
    expect(itemButtons[1]!.attributes('aria-label')).toContain('已读通知')
    expect(itemButtons[2]!.attributes('disabled')).toBeDefined()
    expect(itemButtons[2]!.attributes('aria-label')).toContain('已失效通知')
    await itemButtons[2]!.trigger('click')
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
})
