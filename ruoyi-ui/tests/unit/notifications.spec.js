import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import Notifications from '@/views/lab/notification/index.vue'

const mocks = vi.hoisted(() => ({ list: vi.fn(), get: vi.fn(), read: vi.fn(), refresh: vi.fn(), push: vi.fn(), permissions: [] }))
vi.mock('@/api/lab/notification', () => ({ listNotifications: mocks.list, getNotification: mocks.get, markNotificationRead: mocks.read }))
vi.mock('@/store/modules/labNotification', () => ({ default: () => ({ unreadCount: 1, refreshUnreadCount: mocks.refresh, consumeOne: vi.fn() }) }))
vi.mock('@/store/modules/user', () => ({ default: () => ({ permissions: mocks.permissions }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push, hasRoute: () => false }) }))
function render() {
  return shallowMount(Notifications, { global: {
    renderStubDefaultSlot: true,
    mocks: { parseTime: value => value },
    stubs: { Pagination: true, LabDescriptions: true, ElBadge: true, ElSwitch: true, ElSkeleton: true, ElEmpty: true, ElTag: true, ElButton: true, ElCard: true, ElDescriptionsItem: true, ElDialog: true, ElAlert: true },
    directives: { hasPermi: {} }
  } })
}
beforeEach(() => { vi.clearAllMocks(); mocks.permissions = []; mocks.refresh.mockResolvedValue(); mocks.list.mockResolvedValue({ rows: [], total: 0 }) })

it('shows retry feedback instead of an empty inbox when loading fails', async () => {
  mocks.list.mockRejectedValueOnce(new Error('暂时不可用'))
  const wrapper = render()
  await flushPromises()
  expect(wrapper.find('el-alert-stub[type="error"]').exists()).toBe(true)
  expect(wrapper.find('el-empty-stub').exists()).toBe(false)
  wrapper.unmount()
})
it('does not mark messages read without the read permission or show unavailable business links', async () => {
  const message = { id: '1', title: '通知', content: '消息内容', businessType: 'DEVICE', businessId: '3' }
  mocks.list.mockResolvedValue({ rows: [message], total: 1 })
  mocks.get.mockResolvedValue({ data: message })
  const wrapper = render()
  await flushPromises()
  await wrapper.get('.message-main').trigger('click')
  await flushPromises()
  expect(mocks.read).not.toHaveBeenCalled()
  expect(wrapper.text()).not.toContain('查看业务')
  wrapper.unmount()
})
