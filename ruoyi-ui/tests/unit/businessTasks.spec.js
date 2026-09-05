import { beforeEach, describe, expect, it, vi } from 'vitest'
const request=vi.hoisted(()=>vi.fn())
vi.mock('@/utils/request',()=>({default:request}))
import { exportTask, taskCommand, precheckTask } from '@/api/lab/businessTasks'
describe('business task contracts',()=>{
  beforeEach(()=>request.mockClear())
  it('keeps export filters and whitelist commands',()=>{
    exportTask('DEVICE',{status:'AVAILABLE'})
    expect(request.mock.calls[0][0].data).toEqual({kind:'DEVICE',filters:{status:'AVAILABLE'}})
    expect(()=>taskCommand('1','delete')).toThrow()
    taskCommand('9007199254740993','cancel')
    expect(request.mock.calls[1][0].url).toContain('9007199254740993/commands/cancel')
  })
  it('uploads multipart rather than serializing file bytes',()=>{
    const file=new File(['xlsx'],'template.xlsx');precheckTask('DEVICE',file)
    expect(request.mock.calls[0][0].data.get('file')).toBe(file)
    expect(request.mock.calls[0][0].headers['Content-Type']).toBe('multipart/form-data')
  })
})
