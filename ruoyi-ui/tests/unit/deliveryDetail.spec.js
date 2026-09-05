import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import DeliveryDetail from '@/views/lab/message-center/components/DeliveryDetail.vue'
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('@/api/lab/messageCenter', () => ({ getDelivery: vi.fn(), replayDelivery: vi.fn(), retryDeliveryNow: vi.fn() }))
import { getDelivery, retryDeliveryNow } from '@/api/lab/messageCenter'

const detail = id => ({ data: { delivery: { id, status: 'RETRY_WAIT', attemptCount: 2 }, attempts: [] } })
const mountDetail = () => mount(DeliveryDetail, { props: { modelValue: false, deliveryId: '2' }, global: { plugins: [ElementPlus], stubs: { ElDialog: { template: '<section><slot /><slot name="footer" /></section>' } } } })
describe('early delivery retry', () => {
  beforeEach(() => { vi.clearAllMocks(); getDelivery.mockImplementation(id => Promise.resolve(detail(id))) })
  it('offers audited early retry without resetting the attempt budget', async () => {
    const wrapper = mountDetail(); await wrapper.setProps({ modelValue: true }); await flushPromises()
    expect(wrapper.text()).toContain('提前重试')
    await wrapper.find('textarea').setValue('确认恢复')
    await wrapper.findAll('button').find(button => button.text() === '提前重试').trigger('click')
    await flushPromises()
    expect(retryDeliveryNow).toHaveBeenCalledWith('2', '确认恢复')
    wrapper.unmount()
  })
  it('freezes the submitted target and does not overwrite another detail after a slow command', async () => {
    let finish
    retryDeliveryNow.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const wrapper = mountDetail(); await wrapper.setProps({ modelValue: true }); await flushPromises()
    await wrapper.find('textarea').setValue('原记录原因')
    await wrapper.findAll('button').find(button => button.text() === '提前重试').trigger('click')
    await wrapper.setProps({ deliveryId: '13' }); await flushPromises()
    await wrapper.find('textarea').setValue('新记录原因')
    finish(); await flushPromises()
    expect(retryDeliveryNow).toHaveBeenCalledWith('2', '原记录原因')
    expect(wrapper.find('textarea').element.value).toBe('新记录原因')
    expect(getDelivery.mock.calls.map(([id]) => id)).toEqual(['2', '13'])
    wrapper.unmount()
  })
})
