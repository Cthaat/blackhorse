import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import Preferences from '@/views/lab/message-center/preferences.vue'
import TemplateEditor from '@/views/lab/message-center/components/TemplateEditor.vue'

vi.mock('@/api/lab/messageCenter', () => ({ getPreferences: vi.fn(), updatePreferences: vi.fn(), saveTemplate: vi.fn(), previewTemplate: vi.fn() }))
import { getPreferences, updatePreferences } from '@/api/lab/messageCenter'

describe('message center views', () => {
  beforeEach(() => vi.clearAllMocks())
  it('shows a recoverable load error and does not offer saving an unknown preference', async () => {
    getPreferences.mockRejectedValue(new Error('服务暂不可用'))
    const wrapper = mount(Preferences, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('服务暂不可用')
    expect(wrapper.text()).toContain('重新加载')
    expect(wrapper.text()).not.toContain('保存偏好')
    expect(updatePreferences).not.toHaveBeenCalled()
    wrapper.unmount()
  })
  it('published template versions have no mutable save action', async () => {
    const wrapper = mount(TemplateEditor, { props: { modelValue: true, canEdit: true, template: { id: '12', status: 'PUBLISHED', eventType: 'WAITLIST_OFFERED', title: '邀请', content: '请确认' } }, global: { plugins: [ElementPlus], stubs: { ElDialog: { props: ['title'], template: '<section>{{ title }}<slot /><slot name="footer" /></section>' } } } })
    await flushPromises()
    expect(wrapper.text()).toContain('模板历史版本')
    expect(wrapper.text()).not.toContain('保存草稿')
    wrapper.unmount()
  })
})
