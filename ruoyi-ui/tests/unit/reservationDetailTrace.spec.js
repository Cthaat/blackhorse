import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ReservationDetail from '@/views/lab/reservation/detail.vue'

const { getReservation, getReservationTrace } = vi.hoisted(() => ({ getReservation: vi.fn(), getReservationTrace: vi.fn() }))
vi.mock('@/api/lab/reservation', () => ({ getReservation }))
vi.mock('@/api/lab/trace', () => ({ getReservationTrace }))
const traceData = status => ({ reservation: { id: '7', status }, usage: null, repair: null,
  qualification: null, hazards: { items: [] }, notifications: { items: [] }, history: { items: [] } })
const slot = { template: '<div><slot /></div>' }
function mountDetail() {
  return mount(ReservationDetail, { props: { modelValue: true, reservationId: '7' }, global: {
    directives: { loading: {} }, stubs: { ElDrawer: slot, ElAlert: slot, ElButton: slot, ElEmpty: true,
      ElText: slot, ElTag: slot, LabDescriptions: slot, ElDescriptionsItem: slot, ElSkeleton: true }
  } })
}
beforeEach(() => {
  vi.resetAllMocks()
})

it('refreshes trace when reopening the same reservation after its status changes', async () => {
  getReservation.mockResolvedValue({ data: { id: '7', status: 'PENDING' } })
  getReservationTrace.mockResolvedValueOnce({ data: traceData('PENDING') })
    .mockResolvedValueOnce({ data: traceData('APPROVED') })
  const wrapper = mountDetail()
  await flushPromises()
  expect(wrapper.text()).toContain('等待审批结果')
  await wrapper.setProps({ modelValue: false })
  getReservation.mockResolvedValue({ data: { id: '7', status: 'APPROVED' } })
  await wrapper.setProps({ modelValue: true })
  await flushPromises()
  expect(getReservationTrace).toHaveBeenCalledTimes(2)
  expect(wrapper.text()).toContain('在预约时段内按领用流程领取设备')
  expect(wrapper.text()).not.toContain('等待审批结果')
  wrapper.unmount()
})

it('does not replace fresh detail and trace with a delayed prior reservation response', async () => {
  let resolveOld
  getReservation.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
    .mockResolvedValueOnce({ data: { id: '8', reservationNo: '新预约', status: 'APPROVED' } })
  getReservationTrace.mockResolvedValue({ data: traceData('APPROVED') })
  const wrapper = mountDetail()
  await wrapper.setProps({ reservationId: '8' })
  await flushPromises()
  resolveOld({ data: { id: '7', reservationNo: '旧预约', status: 'PENDING' } })
  await flushPromises()
  expect(wrapper.text()).toContain('新预约')
  expect(wrapper.text()).not.toContain('旧预约')
  expect(getReservationTrace).toHaveBeenCalledTimes(1)
  expect(getReservationTrace).toHaveBeenLastCalledWith('8')
  wrapper.unmount()
})
