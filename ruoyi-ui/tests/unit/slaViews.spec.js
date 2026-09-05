import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import RecordDetail from '@/views/lab/sla/RecordDetail.vue'
import { slaCommand } from '@/api/lab/sla'
vi.mock('@/api/lab/sla', () => ({ slaCommand: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => false }))
const record = { id: '8', objectId: '5', objectType: 'REPAIR_ORDER', businessType: 'REPAIR', risk: 'LOW', version: 2, canManage: true, state: 'OPEN' }
const global = { plugins: [ElementPlus] }
afterEach(() => vi.restoreAllMocks())
describe('SLA clock controls', () => {
  it('does not offer mutation to readers or completed records', async () => {
    const wrapper = mount(RecordDetail, { props: { record: { ...record, canManage: false } }, global })
    expect(wrapper.text()).not.toContain('暂停处理计时')
    await wrapper.setProps({ record: { ...record, completedAt: '2026-09-05T10:00:00+08:00' } })
    expect(wrapper.text()).not.toContain('暂停处理计时')
    wrapper.unmount()
  })
  it('keeps the confirmed record and version when selection changes during the reason prompt', async () => {
    let confirm
    vi.spyOn(ElMessageBox, 'prompt').mockImplementation(() => new Promise(resolve => { confirm = resolve }))
    slaCommand.mockResolvedValue({ data: {} })
    const wrapper = mount(RecordDetail, { props: { record }, global })
    await wrapper.findAll('button').find(button => button.text() === '暂停处理计时').trigger('click')
    await wrapper.setProps({ record: { ...record, id: '9', version: 3 } })
    confirm({ value: ' 等待备件 ' })
    await flushPromises()
    expect(slaCommand).toHaveBeenCalledWith('8', 'pause', { expectedVersion: 2, reason: '等待备件' })
    expect(wrapper.emitted('changed')).toEqual([['8']])
    wrapper.unmount()
  })
})
