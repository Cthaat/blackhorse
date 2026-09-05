import { expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElMessageBox } from 'element-plus'
import Command from '@/views/lab/restrictions/components/RestrictionCommand.vue'
vi.mock('@/api/lab/attachment', () => ({ listAttachment: vi.fn() }))
vi.mock('@/api/lab/restrictions', () => ({ createRestriction: vi.fn(), revokeRestriction: vi.fn(), appealRestriction: vi.fn(), decideAppeal: vi.fn(), publishRestrictionRule: vi.fn() }))
import { listAttachment } from '@/api/lab/attachment'
import { appealRestriction } from '@/api/lab/restrictions'

it('binds the submission to the original record across asynchronous evidence lookup', async () => {
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
  let resolveEvidence
  listAttachment.mockReturnValue(new Promise(resolve => { resolveEvidence = resolve }))
  appealRestriction.mockResolvedValue({})
  const wrapper = mount(Command, { props: { modelValue: true, mode: 'appeal', row: { id: '1' }, laboratories: [], users: [] }, global: {
    plugins: [ElementPlus], stubs: { ElDialog: { template: '<section><slot/><slot name="footer"/></section>' } }
  } })
  await wrapper.find('textarea').setValue('A 的申诉')
  await wrapper.findAll('button').find(button => button.text() === '确认提交').trigger('click')
  await flushPromises()
  await wrapper.setProps({ row: { id: '2' } })
  resolveEvidence({ data: [] })
  await flushPromises()
  expect(appealRestriction).toHaveBeenCalledWith('1', { reason: 'A 的申诉', attachmentIds: [] })
  wrapper.unmount()
  vi.restoreAllMocks()
})
