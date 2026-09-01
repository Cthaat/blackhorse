import { describe, expect, it } from 'vitest'
import { resolveLabErrorMessage } from '@/utils/lab/errorMessage'

const DEFAULT_MESSAGE = '系统处理失败，请稍后重试'

describe('resolveLabErrorMessage', () => {
  it.each([
    [400, '请求参数不正确'],
    [401, '登录状态已失效'],
    [403, '没有执行该操作的权限'],
    [404, '请求的业务对象不存在'],
    [409, '当前状态或数据已发生冲突'],
    [500, DEFAULT_MESSAGE]
  ])('maps %s to a stable Chinese message', (status, message) => {
    expect(resolveLabErrorMessage({ response: { status } })).toBe(message)
  })

  it.each([418, 'toString', '__proto__', undefined])('uses the default message for unknown status %s', (status) => {
    expect(resolveLabErrorMessage({ response: { status } })).toBe(DEFAULT_MESSAGE)
  })

  it('prefers the server business message', () => {
    const error = { response: { status: 409, data: { msg: '该设备在所选时段已被预约' } } }
    expect(resolveLabErrorMessage(error)).toBe('该设备在所选时段已被预约')
  })

  it('ignores arbitrary error messages and stacks', () => {
    const error = {
      message: 'Network Error: internal address 10.0.0.8',
      stack: 'Error: internal failure\n    at reserve (booking.js:42:7)',
      response: { status: 404 }
    }

    expect(resolveLabErrorMessage(error)).toBe('请求的业务对象不存在')
    expect(resolveLabErrorMessage({ message: error.message, stack: error.stack })).toBe(DEFAULT_MESSAGE)
  })

  it.each([
    ['an empty message', '   '],
    ['a non-string message', { detail: 'internal failure' }],
    ['a stack trace', '预约失败\n    at lab.BookingService.reserve(BookingService.java:42)'],
    ['SQL details', 'SELECT password FROM sys_user WHERE user_id = 1'],
    ['a SQL common table expression', '数据库执行失败：WITH leaked AS (VALUES (secret))'],
    ['a SQL schema statement', '数据库执行失败：CREATE TABLE secrets (value VARCHAR(255))'],
    ['an exception class name', 'java.lang.IllegalStateException: booking failed'],
    ['a native exception class name', '系统异常：std::runtime_error: secret'],
    ['a one-line traceback', '系统异常：Traceback (most recent call last): File "booking.py", line 42'],
    ['a serialized JSON response body', '{"code":500,"data":{"secret":"value"}}'],
    ['a serialized XML response body', '响应内容：<error><message>internal failure</message></error>']
  ])('rejects %s from response.data.msg', (_label, msg) => {
    const error = { response: { status: 400, data: { msg } } }
    expect(resolveLabErrorMessage(error)).toBe('请求参数不正确')
  })

  it('does not stringify arbitrary response bodies', () => {
    const error = {
      response: {
        status: 418,
        data: {
          message: 'internal failure',
          sql: 'DELETE FROM lab_booking',
          detail: { exception: 'IllegalStateException' }
        }
      }
    }

    expect(resolveLabErrorMessage(error)).toBe(DEFAULT_MESSAGE)
  })
})
