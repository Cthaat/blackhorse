import { describe, it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus, { ElDatePicker, ElMessageBox } from 'element-plus'
import PlanEditor from '@/views/lab/maintenance/PlanEditor.vue'
import CycleTable from '@/views/lab/maintenance/CycleTable.vue'
import { saveMaintenancePlan, maintenanceCycleCommand } from '@/api/lab/maintenance'
import { listAttachment, downloadAttachment } from '@/api/lab/attachment'
import { saveAs } from 'file-saver'
vi.mock('@/api/lab/maintenance', () => ({ saveMaintenancePlan: vi.fn(), maintenanceCycleCommand: vi.fn() }))
vi.mock('@/api/lab/attachment', () => ({ listAttachment: vi.fn(), downloadAttachment: vi.fn() }))
vi.mock('file-saver', () => ({ saveAs: vi.fn() }))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
const global = { plugins: [ElementPlus], stubs: { ElDialog: { template: '<section><slot/><slot name="footer"/></section>' } } }

describe('maintenance business views', () => {
  it('does not publish an invalid plan', async () => {
    const wrapper = mount(PlanEditor, { props: { modelValue: true }, global })
    await wrapper.findAll('button').find(button => button.text() === '保存版本').trigger('click')
    expect(saveMaintenancePlan).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请选择有效设备和负责人')
    wrapper.unmount()
  })
  it('shows a rejected downtime window without claiming success', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    maintenanceCycleCommand.mockResolvedValue({ data: { scheduled: false, conflicts: [{ kind: 'RESERVATION', id: '8', startTime: '2026-10-01T09:00:00+08:00', endTime: '2026-10-01T10:00:00+08:00' }] } })
    const wrapper = mount(CycleTable, { props: { rows: [{ id: '3', status: 'PLANNED', version: 1, kind: 'MAINTENANCE' }] }, global })
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '安排窗口').trigger('click')
    wrapper.findAllComponents(ElDatePicker)[0].vm.$emit('update:modelValue', '2026-10-01T09:00:00+08:00')
    wrapper.findAllComponents(ElDatePicker)[1].vm.$emit('update:modelValue', '2026-10-01T11:00:00+08:00')
    await wrapper.find('textarea').setValue('安排维护')
    await wrapper.vm.$nextTick()
    await wrapper.findAll('button').find(button => button.text() === '确认安排').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('窗口存在冲突，尚未生效')
    expect(wrapper.emitted('changed')).toBeUndefined()
    expect(maintenanceCycleCommand).toHaveBeenCalledWith('3', 'window', expect.objectContaining({ expectedVersion: 1 }))
    wrapper.unmount()
  })
  it('shows the exact accepted report and downloads only that private attachment', async () => {
    listAttachment.mockResolvedValue({ data: [{ id: '92', originalName: 'accepted.pdf' }, { id: '93', originalName: 'draft.pdf' }] })
    const blob = new Blob(['report'])
    downloadAttachment.mockResolvedValue(blob)
    const wrapper = mount(CycleTable, { props: { rows: [{ id: '3', deviceId: '7', deviceName: '校准设备', status: 'COMPLETED', kind: 'CALIBRATION', repairId: '5', reportAttachmentId: '92' }] }, global })
    await flushPromises()
    expect(wrapper.text()).toContain('校准设备 #7')
    await wrapper.findAll('button').find(button => button.text() === '验收报告 #92').trigger('click')
    await flushPromises()
    expect(downloadAttachment).toHaveBeenCalledWith('92')
    expect(saveAs).toHaveBeenCalledWith(blob, 'accepted.pdf')
    wrapper.unmount()
  })
})
