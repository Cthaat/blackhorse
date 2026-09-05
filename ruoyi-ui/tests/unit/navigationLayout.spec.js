import { beforeEach, expect, it, vi } from 'vitest'
import { nextTick, onMounted, reactive, ref, watchEffect } from 'vue'
import { mount } from '@vue/test-utils'
import AppMain from '@/layout/components/AppMain.vue'
import Breadcrumb from '@/components/Breadcrumb/index.vue'

const mocks = vi.hoisted(() => ({ route: null, routes: [] }))
vi.mock('@/store/modules/tagsView', () => ({ default: () => ({ cachedViews: [], addIframeView: vi.fn() }) }))
vi.mock('@/store/modules/permission', () => ({ default: () => ({ defaultRoutes: mocks.routes }) }))
vi.mock('@/layout/components/Copyright/index.vue', () => ({ default: { template: '<span />' } }))
vi.mock('@/layout/components/IframeToggle/index.vue', () => ({ default: { template: '<span />' } }))
for (const [name, value] of Object.entries({ ref, onMounted, watchEffect, useRoute: () => mocks.route, useRouter: () => ({ push: vi.fn() }) })) vi.stubGlobal(name, value)
beforeEach(() => { mocks.route = reactive({ path: '/index', meta: {}, matched: [] }) })

it('resets the nested content scroll when navigating to another page', async () => {
  const wrapper = mount(AppMain, { global: { stubs: { RouterView: true } } })
  wrapper.element.scrollTop = 500
  mocks.route.path = '/lab/assets/devices'
  await nextTick()
  expect(wrapper.element.scrollTop).toBe(0)
  wrapper.unmount()
})

it('keeps hyphenated menu segments intact in breadcrumbs', () => {
  mocks.route.path = '/lab/assets/my-qualifications'
  mocks.routes = [{ path: '/lab', meta: { title: '实验室管理' }, children: [{ path: 'assets', meta: { title: '实验室资产' }, children: [{ path: 'my-qualifications', meta: { title: '我的资格' } }] }] }]
  const wrapper = mount(Breadcrumb, { global: { stubs: { ElBreadcrumb: { template: '<nav><slot /></nav>' }, ElBreadcrumbItem: { template: '<span><slot /></span>' } } } })
  expect(wrapper.text()).toContain('我的资格')
  wrapper.unmount()
})
