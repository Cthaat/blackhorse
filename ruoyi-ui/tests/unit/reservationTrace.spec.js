import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import ReservationTrace from '@/views/lab/reservation/ReservationTrace.vue'

const { getReservationTrace } = vi.hoisted(() => ({ getReservationTrace: vi.fn() }))
vi.mock('@/api/lab/trace', () => ({ getReservationTrace }))
const empty = () => ({ items: [], hasMore: false })
const data = () => ({ reservation: { id: '7', status: 'PENDING' }, usage: null, repair: null,
  qualification: null, hazards: empty(), notifications: empty(), history: empty() })
const mountTrace = () => mount(ReservationTrace, { props: { reservationId: '7' }, global: {
  stubs: { ElSkeleton: true, ElAlert: { template: '<div><slot /></div>' }, ElButton: { template: '<button><slot /></button>' } }
} })
beforeEach(() => vi.resetAllMocks())

it('explains visibility without revealing hidden branch existence or links', async () => {
  getReservationTrace.mockResolvedValue({ data: data() })
  const wrapper = mountTrace()
  await flushPromises()
  expect(wrapper.text()).toContain('当前权限范围内没有可展示的关联记录')
  expect(wrapper.findAll('a')).toHaveLength(0)
  expect(wrapper.text()).toContain('等待审批结果')
})

it('labels current qualification and same-target hazards separately from causal history', async () => {
  const trace = data()
  trace.qualification = { basis: 'CURRENT_MATCHING_RECORDS', matchingCount: 2, evaluatedAt: '2026-09-05T12:00:00' }
  trace.hazards.items = [{ id: '99', title: '隐患记录', status: 'CLOSED', basis: 'SAME_TARGET_CONTEXT' }]
  getReservationTrace.mockResolvedValue({ data: trace })
  const wrapper = mountTrace()
  await flushPromises()
  expect(wrapper.text()).toContain('不代表预约提交时的资格快照')
  expect(wrapper.text()).toContain('不表示由本次预约产生')
  expect(wrapper.text()).toContain('2 条')
})

it('ignores a stale response after switching reservations', async () => {
  let resolveOld
  getReservationTrace.mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve }))
  getReservationTrace.mockResolvedValueOnce({ data: { ...data(), repair: { title: '新记录', status: 'CLOSED' } } })
  const wrapper = mountTrace()
  await wrapper.setProps({ reservationId: '8' })
  await flushPromises()
  resolveOld({ data: { ...data(), repair: { title: '旧记录', status: 'CLOSED' } } })
  await flushPromises()
  expect(wrapper.text()).toContain('新记录')
  expect(wrapper.text()).not.toContain('旧记录')
})
