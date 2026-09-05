import { expect, it } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import LabDescriptions from '@/components/lab/Descriptions.vue'

it('stacks detail fields on mobile while restoring the requested desktop columns', async () => {
  window.innerWidth = 1440
  const wrapper = mount(LabDescriptions, { props: { column: 3 }, global: { stubs: { ElDescriptions: { name: 'ElDescriptions', props: ['column'], template: '<div><slot /></div>' } } } })
  expect(wrapper.findComponent({ name: 'ElDescriptions' }).props('column')).toBe(3)
  window.innerWidth = 390
  window.dispatchEvent(new Event('resize'))
  await nextTick()
  expect(wrapper.findComponent({ name: 'ElDescriptions' }).props('column')).toBe(1)
  wrapper.unmount()
})
