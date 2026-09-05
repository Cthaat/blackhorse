import { afterEach, describe, expect, it, vi } from 'vitest'
import { computed, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import Pagination from '@/components/Pagination/index.vue'

vi.stubGlobal('computed', computed)
const paginationStub = {
  props: ['layout', 'pagerCount'],
  emits: ['size-change', 'current-change'],
  template: '<nav />'
}
const wrappers = []
function render(props = {}) {
  const wrapper = mount(Pagination, { props: { total: 200, ...props }, global: { stubs: { ElPagination: paginationStub } } })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => { wrappers.forEach(wrapper => wrapper.unmount()); wrappers.length = 0; document.body.innerHTML = '' })

describe('responsive pagination', () => {
  it('changes pager density when the viewport changes', async () => {
    window.innerWidth = 1440
    const wrapper = render()
    expect(wrapper.findComponent(paginationStub).props('pagerCount')).toBe(7)
    window.innerWidth = 390
    window.dispatchEvent(new Event('resize'))
    await nextTick()
    expect(wrapper.findComponent(paginationStub).props('pagerCount')).toBe(5)
    expect(wrapper.findComponent(paginationStub).props('layout')).toBe('total, prev, pager, next')
  })

  it('emits page one when a larger page size invalidates the current page', () => {
    const wrapper = render({ page: 10, autoScroll: false })
    wrapper.findComponent(paginationStub).vm.$emit('size-change', 50)
    expect(wrapper.emitted('update:page')[0]).toEqual([1])
    expect(wrapper.emitted('pagination')[0]).toEqual([{ page: 1, limit: 50 }])
  })

  it('scrolls the containing app content instead of the window', () => {
    const host = document.createElement('main')
    host.className = 'app-main'
    host.style.overflowY = 'auto'
    host.scrollTo = vi.fn()
    document.body.append(host)
    const wrapper = mount(Pagination, { attachTo: host, props: { total: 200 }, global: { stubs: { ElPagination: paginationStub } } })
    wrappers.push(wrapper)
    wrapper.findComponent(paginationStub).vm.$emit('current-change', 2)
    expect(host.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' })
  })
  it('uses window scrolling when the header is not fixed', () => {
    const host = document.createElement('main')
    host.className = 'app-main'
    host.style.overflowY = 'hidden'
    host.scrollTo = vi.fn()
    const scrollWindow = vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
    document.body.append(host)
    const wrapper = mount(Pagination, { attachTo: host, props: { total: 200 }, global: { stubs: { ElPagination: paginationStub } } })
    wrappers.push(wrapper)
    wrapper.findComponent(paginationStub).vm.$emit('current-change', 2)
    expect(host.scrollTo).not.toHaveBeenCalled()
    expect(scrollWindow).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' })
    scrollWindow.mockRestore()
  })
})
