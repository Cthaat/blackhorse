import { expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import Dashboard from '@/views/lab/dashboard/index.vue'

const mocks = vi.hoisted(() => ({ permissions: ['lab:dashboard:view', 'lab:notification:list'] }))
vi.mock('@/store/modules/user', () => ({ default: () => ({ permissions: mocks.permissions }) }))
vi.mock('@/components/lab/WorkspaceEntries.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/api/lab/dashboard', () => ({ getDashboardSummary: () => Promise.resolve({ data: {} }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({}) }))

it('does not show empty business status sections to system-only accounts', async () => {
  const wrapper = shallowMount(Dashboard, { global: {
    renderStubDefaultSlot: true,
    stubs: { WorkspaceEntries: true, RouterLink: true, ElTag: true, ElDatePicker: true, ElButton: true, ElAlert: true, ElRow: true, ElCol: true, ElStatistic: true },
    directives: { loading: {} }
  } })
  await flushPromises()
  expect(wrapper.text()).toContain('未读消息')
  expect(wrapper.find('.status-heading').exists()).toBe(false)
  expect(wrapper.find('.chart-grid').exists()).toBe(false)
  expect(wrapper.find('.summary-footer').exists()).toBe(false)
  wrapper.unmount()
})
