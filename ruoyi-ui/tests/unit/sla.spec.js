import { describe, it, expect, vi } from 'vitest'
import { rulePayload, businessTarget, remainingTime } from '@/views/lab/sla/presentation'
import { slaCommand } from '@/api/lab/sla'
import request from '@/utils/request'
vi.mock('@/utils/request', () => ({ default: vi.fn() }))
describe('SLA contracts', () => {
  it('freezes processing remainder at pause time but keeps response running', () => {
    const record = { responseDueAt: '2026-09-05T11:00:00+08:00', processingDueAt: '2026-09-05T12:00:00+08:00', pausedAt: '2026-09-05T10:00:00+08:00' }
    const now = new Date('2026-09-05T13:00:00+08:00').getTime()
    expect(remainingTime(record, 'PROCESSING', now)).toBe('暂停时剩余 2 小时 0 分')
    expect(remainingTime(record, 'RESPONSE', now)).toBe('已超时 2 小时 0 分')
    expect(remainingTime({ ...record, closedAt: '2026-09-05T10:30:00+08:00' }, 'PROCESSING', now)).toBe('已关闭')
  })
  it('validates bounded natural-hour rules', () => {
    const form = { laboratoryId: '1', businessType: 'REPAIR', risk: 'HIGH', responseHours: 4, processingHours: 24, reason: ' 高风险规则 ' }
    expect(rulePayload(form).reason).toBe('高风险规则')
    expect(() => rulePayload({ ...form, responseHours: 0 })).toThrow()
    expect(() => rulePayload({ ...form, processingHours: 3 })).toThrow()
    expect(() => rulePayload({ ...form, risk: 'unknown' })).toThrow()
  })
  it('keeps object IDs intact and limits commands', () => {
    slaCommand('9007199254740993', 'pause', { expectedVersion: 0, reason: '等待备件' })
    expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ url: '/lab/sla/records/9007199254740993/commands/pause' }))
    expect(() => slaCommand('1', 'close', {})).toThrow()
    expect(businessTarget({ objectType: 'REPAIR_ORDER', objectId: '9007199254740993' })).toEqual({ path: '/lab/repair/detail/9007199254740993' })
    expect(businessTarget({ objectType: 'EXTERNAL', objectId: 'https://example.com' })).toBeNull()
    expect(businessTarget({ objectType: 'MAINTENANCE_CYCLE', objectId: '3', repairId: '7' }, false)).toEqual({ path: '/lab/repair/detail/7' })
    expect(businessTarget({ objectType: 'MAINTENANCE_CYCLE', objectId: '3' }, false)).toBeNull()
  })
})
