import { describe, expect, it } from 'vitest'
import { buildWorkspaceGroups } from '@/utils/lab/workspaceNavigation'

describe('authorized workspace navigation', () => {
  it('keeps nested paths and query parameters from the authorized menu', () => {
    expect(buildWorkspaceGroups([{ path: '/lab', meta: { title: '实验室业务' }, children: [
      { path: 'reservations', meta: { title: '预约管理' }, children: [
        { path: 'approval', query: '{"mode":"approval"}', meta: { title: '预约审批', icon: 'check' } }
      ] }
    ] }])).toEqual([{ title: '实验室业务', items: [
      { title: '预约审批', icon: 'check', section: '预约管理', to: { path: '/lab/reservations/approval', query: { mode: 'approval' } } }
    ] }])
  })
  it('excludes hidden parents, hidden details, external URLs and the homepage', () => {
    expect(buildWorkspaceGroups([
      { path: '/index', meta: { title: '首页' } },
      { path: '/hidden', hidden: true, children: [{ path: 'child', meta: { title: '私有' } }] },
      { path: '/lab', meta: { title: '实验室' }, children: [
        { path: 'detail/:id', hidden: true, meta: { title: '详情' } },
        { path: 'https://example.com', meta: { title: '外链' } },
        { path: 'devices', meta: { title: '设备' } }
      ] }
    ])[0].items.map(item => item.to.path)).toEqual(['/lab/devices'])
  })
  it('uses absolute child paths and tolerates malformed optional query metadata', () => {
    const groups = buildWorkspaceGroups([{ path: '/system', meta: { title: '系统管理' }, children: [
      { path: '/system/users', query: '{bad', meta: { title: '用户管理' } }
    ] }])
    expect(groups[0].items[0].to).toEqual({ path: '/system/users', query: {} })
  })
  it('does not invent menu entries for a role with no authorized routes', () => {
    expect(buildWorkspaceGroups([])).toEqual([])
  })
})
