import { beforeEach, expect, it, vi } from 'vitest'
const request = vi.hoisted(() => vi.fn())
vi.mock('@/utils/request', () => ({ default: request }))
import { listRestrictions, createRestriction, appealRestriction, decideAppeal } from '@/api/lab/restrictions'

beforeEach(() => request.mockClear())
it('keeps scoped filters and large identifiers as strings', () => {
  listRestrictions({ mine: false, laboratoryId: '12', pageNum: 2, pageSize: 10 })
  expect(request.mock.calls[0]?.[0].params).toMatchObject({ mine: false, laboratoryId: '12', pageNum: 2 })
  createRestriction({ laboratoryId: '12', userId: '9007199254740993', days: 7, reason: '事实说明' })
  expect(request.mock.calls[1]?.[0].data.userId).toBe('9007199254740993')
})
it('keeps appeal evidence and restricts identifier input', () => {
  appealRestriction('5', { reason: '申诉说明', attachmentIds: ['18'] })
  expect(request.mock.calls[0]?.[0].data.attachmentIds).toEqual(['18'])
  expect(() => decideAppeal('../other', { approved: true, reason: '复核结论' })).toThrow()
})
