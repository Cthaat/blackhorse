import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
vi.mock('@/api/lab/operations', () => ({ getOperations: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: vi.fn(() => true) }))
import { getOperations } from '@/api/lab/operations'
import Operations from '@/views/lab/operations/index.vue'
import QueuePanel from '@/views/lab/operations/components/QueuePanel.vue'
import HttpPanel from '@/views/lab/operations/components/HttpPanel.vue'

describe('operations state', () => {
  it('shows bounded route status categories and locatable queue identifiers', async () => {
    const queue = mount(QueuePanel, { props: { section: { data: { deliveryBacklog: 1, deliveries: [], tasks: [], references: { oldestDeliveryId: '88', failedTaskId: '99' } } } }, global: { plugins: [ElementPlus], stubs: { RouterLink: { props: ['to'], template: '<a :data-target="JSON.stringify(to)"><slot /></a>' } } } })
    expect(queue.text()).toContain('#88')
    expect(queue.text()).toContain('#99')
    expect(queue.find('a').attributes('data-target')).toContain('deliveryId')
    const http = mount(HttpPanel, { props: { http: { routes: [{ template: '/lab/deliveries/{id}', requestCount: 1, clientErrors: 1 }] } }, global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(http.text()).toContain('/lab/deliveries/{id}')
    expect(http.text()).toContain('4xx')
    expect(http.text()).toContain('OTHER')
    queue.unmount(); http.unmount()
  })
  it('does not turn a failed sampling request into healthy zero-valued metrics', async () => {
    getOperations.mockRejectedValue(new Error('采样服务不可用'))
    const wrapper = mount(Operations, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('采样服务不可用')
    expect(wrapper.text()).toContain('刷新')
    expect(wrapper.text()).not.toContain('0%')
    wrapper.unmount()
  })
})
