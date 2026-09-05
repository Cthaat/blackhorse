import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import RestrictionDetail from '@/views/lab/restrictions/components/RestrictionDetail.vue'

vi.mock('@/components/lab/AttachmentPanel.vue', () => ({ default: { props: ['canManage'], template: '<div :data-editable="canManage">证据附件</div>' } }))
const base = { id: '1', userId: '20', laboratoryId: '4', laboratoryName: '演示实验室', userName: '学生', status: 'ACTIVE', reason: '预约爽约', endsAt: '2099-01-01', history: [] }
describe('restriction detail role controls', () => {
  it('offers owner appeal before submission but never self review', () => {
    const wrapper = mount(RestrictionDetail, { props: { row: base, owner: true, canAppeal: true, canReview: true }, global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('填写申诉')
    expect(wrapper.find('[data-editable="true"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('审核申诉')
    wrapper.unmount()
  })
  it('freezes submitted evidence and offers review only to another manager', () => {
    const wrapper = mount(RestrictionDetail, { props: { row: { ...base, appeal: { status: 'PENDING', reason: '说明' } }, owner: false, canReview: true }, global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('审核申诉')
    expect(wrapper.text()).not.toContain('填写申诉')
    expect(wrapper.find('[data-editable="false"]').exists()).toBe(true)
    wrapper.unmount()
  })
  it('prevents appeal while evidence is still being uploaded', async () => {
    const wrapper = mount(RestrictionDetail, { props: { row: base, owner: true, canAppeal: true }, global: { plugins: [ElementPlus] } })
    const evidence = wrapper.find('[data-editable]').getCurrentComponent()
    evidence.emit('busy', true)
    await wrapper.vm.$nextTick()
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})
