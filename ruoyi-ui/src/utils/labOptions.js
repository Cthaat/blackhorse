/** Load every authorized option page; never silently truncate a selector. */
export async function loadAllOptions(fetchPage, filters = {}) {
  const rows = []
  const seen = new Set()
  let first
  let key
  for (let pageNum = 1; ; pageNum++) {
    const response = await fetchPage({ ...filters, pageNum, pageSize: 100 })
    first ??= response
    key ??= Array.isArray(response.rows) ? 'rows' : 'data'
    const page = response[key]
    const total = Number(response.total)
    if (!Array.isArray(page) || !Number.isSafeInteger(total) || total < 0) {
      throw new Error('选项接口未返回有效分页数据，请刷新重试')
    }
    const before = rows.length
    for (const item of page) {
      if (!seen.has(item.id)) {
        seen.add(item.id)
        rows.push(item)
      }
    }
    if (rows.length >= total) return { ...first, [key]: rows, total: rows.length }
    if (!page.length || rows.length === before) {
      throw new Error('选项数据已变化或分页异常，请刷新重试')
    }
  }
}
