import { describe, expect, it } from 'vitest'
import { restrictionState, validateRestrictionReason, validateRestrictionDays } from '@/views/lab/restrictions/presentation'

describe('restriction presentation and input boundaries', () => {
  it('treats the end instant as expired, and revocation as final', () => {
    const now = new Date('2026-09-05T12:00:00Z').getTime()
    expect(restrictionState({ status: 'ACTIVE', endsAt: '2026-09-05T12:00:00Z' }, now)).toBe('已到期')
    expect(restrictionState({ status: 'REVOKED', endsAt: '2026-09-07T12:00:00Z' }, now)).toBe('已解除')
    expect(restrictionState({ status: 'ACTIVE', endsAt: '2026-09-07T12:00:00Z' }, now)).toBe('生效中')
  })
  it('requires meaningful bounded reasons and whole day durations', () => {
    expect(() => validateRestrictionReason('  ')).toThrow()
    expect(() => validateRestrictionReason('a'.repeat(501))).toThrow()
    expect(validateRestrictionReason('  设备使用违约  ')).toBe('设备使用违约')
    expect(() => validateRestrictionDays(0, 90)).toThrow()
    expect(() => validateRestrictionDays(1.5, 90)).toThrow()
    expect(() => validateRestrictionDays(91, 90)).toThrow()
    expect(validateRestrictionDays(7, 90)).toBe(7)
  })
})
