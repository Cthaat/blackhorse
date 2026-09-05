import { beforeEach, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import IntervalActions from '@/views/lab/reservation/workspace/IntervalActions.vue'
const { joinWaitlist, simulateRule, success } = vi.hoisted(() => ({ joinWaitlist: vi.fn(), simulateRule: vi.fn(), success: vi.fn() }))
vi.mock('@/api/lab/reservationWorkspace', () => ({ joinWaitlist, simulateRule }))
vi.mock('element-plus', () => ({ ElMessage: { success } }))
const mountActions = () => mount(IntervalActions, { props: { deviceId: '9007199254740993' }, global: {
  directives: { hasPermi: () => {} }, stubs: {
    ElCheckbox: true,
    ElAlert: { props: ['title'], template: '<div>{{ title }}</div>' },
    ElForm: { template: '<form><slot /></form>' }, ElFormItem: { template: '<div><slot /></div>' },
    ElInput: { props: ['modelValue'], template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
    ElDatePicker: { template: '<button @click="$emit(\'update:modelValue\', [\'2026-09-06T09:00:00\', \'2026-09-06T10:00:00\'])">选择时间</button>' },
    ElButton: { template: '<button type="button"><slot /></button>' }
  }
} })
async function click(wrapper, text) { await wrapper.findAll('button').find(button => button.text() === text).trigger('click'); await flushPromises() }
beforeEach(() => vi.resetAllMocks())
it('keeps the retry key for network failures, resets for changed payload, and never reports false success', async () => {
  joinWaitlist.mockRejectedValue(new Error('网络中断'))
  const wrapper = mountActions()
  await click(wrapper, '选择时间')
  await wrapper.find('input').setValue('实验用途')
  await click(wrapper, '加入我的候补')
  const firstKey = joinWaitlist.mock.calls[0][1]
  expect(wrapper.text()).toContain('网络中断')
  expect(success).not.toHaveBeenCalled()
  expect(wrapper.emitted('joined')).toBeUndefined()
  await click(wrapper, '加入我的候补')
  expect(joinWaitlist.mock.calls[1][1]).toBe(firstKey)
  await wrapper.find('input').setValue('新的实验用途')
  await click(wrapper, '加入我的候补')
  expect(joinWaitlist.mock.calls[2][1]).not.toBe(firstKey)
  expect(joinWaitlist.mock.calls[2][0].deviceId).toBe('9007199254740993')
  wrapper.unmount()
})
it('invalidates an old simulation when the device changes', async () => {
  let resolve
  simulateRule.mockReturnValue(new Promise(done => { resolve = done }))
  const wrapper = mountActions()
  await click(wrapper, '选择时间')
  await wrapper.find('input').setValue('用途')
  await click(wrapper, '试算所选时段')
  await wrapper.setProps({ deviceId: '2' })
  resolve({ data: { allowed: true, message: '旧设备允许' } })
  await flushPromises()
  expect(wrapper.text()).not.toContain('旧设备允许')
  expect(wrapper.text()).toContain('正式提交仍需资格、安全检查和审批')
  wrapper.unmount()
})
it('does not submit overlapping joins when the device changes during a pending request', async () => {
  let resolve
  joinWaitlist.mockReturnValue(new Promise(done => { resolve = done }))
  const wrapper = mountActions()
  await click(wrapper, '选择时间')
  await wrapper.find('input').setValue('用途')
  await click(wrapper, '加入我的候补')
  await wrapper.setProps({ deviceId: '2' })
  await click(wrapper, '加入我的候补')
  expect(joinWaitlist).toHaveBeenCalledTimes(1)
  resolve({ data: { id: '1' } })
  await flushPromises()
  wrapper.unmount()
})
