import { expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ReservationWorkspace from '@/views/lab/reservation/workspace/ReservationWorkspace.vue'
const { checkPermi } = vi.hoisted(() => ({ checkPermi: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi }))
vi.mock('@/api/lab/reservationWorkspace', () => ({}))
function render(permissions) {
  checkPermi.mockImplementation(values => values.some(value => permissions.includes(value)))
  return mount(ReservationWorkspace, { props: { modelValue: true, deviceOptions: [{ id: '1', label: '设备' }] }, global: { stubs: {
    ElDialog: { template: '<div><slot /></div>' }, ElTabs: { template: '<div><slot /></div>' },
    ElTabPane: { template: '<section><slot /></section>' }, ElSelect: true, ElOption: true, ElEmpty: true,
    CalendarPanel: true, IntervalActions: true, WaitlistPanel: true, RulesPanel: true
  } } })
}
it('does not mount personal waitlist or simulation for a list and approve manager', () => {
  const wrapper = render(['lab:reservation:list', 'lab:reservation:approve'])
  expect(wrapper.findComponent({ name: 'CalendarPanel' }).exists()).toBe(true)
  expect(wrapper.findComponent({ name: 'WaitlistPanel' }).exists()).toBe(false)
  expect(wrapper.findComponent({ name: 'IntervalActions' }).exists()).toBe(false)
  wrapper.unmount()
})
it('does not mount calendar endpoints for a mine-only user', () => {
  const wrapper = render(['lab:reservation:mine'])
  expect(wrapper.findComponent({ name: 'WaitlistPanel' }).exists()).toBe(true)
  expect(wrapper.findComponent({ name: 'CalendarPanel' }).exists()).toBe(false)
  wrapper.unmount()
})
