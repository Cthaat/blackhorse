import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import CalendarPanel from '@/views/lab/reservation/workspace/CalendarPanel.vue'
const { getCalendar } = vi.hoisted(() => ({ getCalendar: vi.fn() }))
vi.mock('@/api/lab/reservationWorkspace', () => ({ getCalendar }))
const result = name => ({ data: { rule: { definition: { name } }, global: {}, days: [], occupied: [] } })
const mountPanel = () => mount(CalendarPanel, { props: { deviceId: '1' }, global: { stubs: {
  ElAlert: { props: ['title'], template: '<div>{{ title }}<slot /></div>' }, ElButton: { template: '<button><slot /></button>' },
  ElTable: true, ElTableColumn: true, ElDatePicker: true, ElSkeleton: true, RuleSummary: true
} } })
beforeEach(() => vi.resetAllMocks())
it('discards stale calendar data when the selected device changes', async () => {
  let old
  getCalendar.mockReturnValueOnce(new Promise(resolve => { old = resolve }))
  getCalendar.mockResolvedValueOnce(result('新规则'))
  const wrapper = mountPanel()
  await wrapper.setProps({ deviceId: '2' })
  await flushPromises()
  old(result('旧规则'))
  await flushPromises()
  expect(wrapper.text()).toContain('新规则')
  expect(wrapper.text()).not.toContain('旧规则')
  wrapper.unmount()
})
it('renders backend errors and allows a retry without claiming availability', async () => {
  getCalendar.mockRejectedValue({ response: { data: { msg: '设备不可见' } } })
  const wrapper = mountPanel()
  await flushPromises()
  expect(wrapper.text()).toContain('设备不可见')
  expect(wrapper.text()).toContain('重新加载')
  wrapper.unmount()
})
