// Consume the same authorized route tree as the sidebar; never infer permissions.
export function buildWorkspaceGroups(routes = []) {
  return routes.flatMap(root => {
    const items = []
    function visit(route, parentPath = '', section = '') {
      if (route.hidden || /^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(route.path || '')) return
      const path = (route.path?.startsWith('/') ? route.path : `${parentPath}/${route.path || ''}`).replace(/\/{2,}/g, '/')
      if (route.children?.length) {
        route.children.forEach(child => visit(child, path, route.meta?.title || section))
        return
      }
      if (!route.meta?.title || path === '/index' || path === '/' || path.includes(':')) return
      let query = route.query || {}
      if (typeof query === 'string') {
        try { query = JSON.parse(query) } catch { query = {} }
      }
      if (!query || typeof query !== 'object' || Array.isArray(query)) query = {}
      items.push({ title: route.meta.title, icon: route.meta.icon || 'form', section, to: { path, query } })
    }
    visit(root)
    return items.length ? [{ title: root.meta?.title || '常用功能', items }] : []
  })
}
