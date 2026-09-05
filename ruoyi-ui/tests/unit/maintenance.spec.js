import { describe, it, expect, vi } from 'vitest'
import { planPayload, windowPayload } from '@/views/lab/maintenance/presentation'
import { saveMaintenancePlan, maintenanceCycleCommand } from '@/api/lab/maintenance'
import request from '@/utils/request'
vi.mock('@/utils/request', () => ({ default: vi.fn() }))

describe('maintenance form and API contracts', () => {
  const form = { deviceId: '9007199254740993', kind: 'CALIBRATION', periodDays: 30, firstDueAt: '2026-10-01T09:00:00+08:00', responsibleId: '9', description: ' 校准说明 ', reason: ' 新建计划 ' }
  it('preserves large IDs, normalizes text and bounds versions and cycles', () => {
    expect(planPayload(form)).toMatchObject({ deviceId: form.deviceId, description: '校准说明', reason: '新建计划' })
    expect(() => planPayload({ ...form, periodDays: 0 })).toThrow()
    expect(() => planPayload({ ...form, reason: ' ' })).toThrow()
    expect(() => planPayload({ ...form, firstDueAt: 'invalid' })).toThrow()
    expect(() => windowPayload({ startTime: form.firstDueAt, endTime: form.firstDueAt, reason: '安排', expectedVersion: 0 })).toThrow()
  })
  it('uses explicit versioned commands and never coerces large IDs', () => {
    saveMaintenancePlan(form.deviceId, { ...form, expectedVersion: 2 })
    expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ url: '/lab/maintenance/plans/9007199254740993', method: 'put' }))
    maintenanceCycleCommand('7', 'window', { reason: '安排' })
    expect(request).toHaveBeenLastCalledWith(expect.objectContaining({ url: '/lab/maintenance/cycles/7/commands/window', method: 'post' }))
    expect(() => maintenanceCycleCommand('7', 'delete', {})).toThrow()
  })
})
