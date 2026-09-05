import { describe, expect, it, vi } from 'vitest'
import { loadAllOptions } from '../../src/utils/labOptions'

describe('complete option loading', () => {
  it('loads later pages and preserves filters and the response envelope', async () => {
    const fetchPage = vi.fn(async ({ pageNum }) => ({ code: 200, total: 205,
      rows: Array.from({ length: pageNum === 3 ? 5 : 100 }, (_, i) => ({ id: (pageNum - 1) * 100 + i })) }))
    const result = await loadAllOptions(fetchPage, { laboratoryId: 7, pageSize: 500 })
    expect(result.rows).toHaveLength(205)
    expect(fetchPage).toHaveBeenLastCalledWith({ laboratoryId: 7, pageNum: 3, pageSize: 100 })
  })
  it('supports user option envelopes and empty results', async () => {
    expect(await loadAllOptions(async () => ({ data: [], total: 0 }))).toEqual({ data: [], total: 0 })
  })
  it('rejects stale or stalled pagination instead of returning partial choices', async () => {
    await expect(loadAllOptions(async () => ({ rows: [{ id: 1 }], total: 200 }))).rejects.toThrow('分页异常')
    await expect(loadAllOptions(async () => ({ rows: [] }))).rejects.toThrow('分页数据')
  })
})
