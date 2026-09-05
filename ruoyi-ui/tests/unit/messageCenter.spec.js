import { describe, it, expect, vi } from 'vitest'
vi.mock('@/utils/request', () => ({ default: vi.fn(config => Promise.resolve(config)) }))
import { listDeliveries, replayDelivery, saveTemplate, updatePreferences } from '@/api/lab/messageCenter'
describe('message center API boundaries', () => {
  it('uses command routes and preserves reason while current-user preferences have no receiver', async () => {
    expect((await replayDelivery('42', '数据库恢复后重放')).url).toBe('/lab/deliveries/42/commands/replay')
    expect((await replayDelivery('42', '数据库恢复后重放')).data).toEqual({ reason: '数据库恢复后重放' })
    expect((await listDeliveries({ pageNum: 2 })).params).toEqual({ pageNum: 2 })
    expect((await saveTemplate(null, { eventType: 'WAITLIST_OFFERED' })).method).toBe('post')
    expect((await updatePreferences(false)).data).toEqual({ optionalReminders: false })
  })
})
