import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const requestUse = vi.fn()
  const responseUse = vi.fn()
  const service = {
    interceptors: {
      request: { use: requestUse },
      response: { use: responseUse }
    },
    post: vi.fn()
  }
  const axios = vi.fn()
  axios.defaults = { headers: {} }
  axios.create = vi.fn(() => service)

  const message = vi.fn()
  message.error = vi.fn()

  return {
    axios,
    service,
    requestUse,
    responseUse,
    message,
    notificationError: vi.fn(),
    confirm: vi.fn(),
    loadingService: vi.fn(() => ({ close: vi.fn() })),
    logOut: vi.fn(),
    getToken: vi.fn(),
    getSession: vi.fn(),
    setSession: vi.fn(),
    saveAs: vi.fn(),
    blobValidate: vi.fn()
  }
})

vi.mock('axios', () => ({ default: mocks.axios }))
vi.mock('element-plus', () => ({
  ElNotification: { error: mocks.notificationError },
  ElMessageBox: { confirm: mocks.confirm },
  ElMessage: mocks.message,
  ElLoading: { service: mocks.loadingService }
}))
vi.mock('@/utils/auth', () => ({ getToken: mocks.getToken }))
vi.mock('@/utils/errorCode', () => ({
  default: {
    401: '认证失败，无法访问系统资源',
    403: '当前操作没有权限',
    404: '访问资源不存在',
    default: '系统未知错误，请反馈给管理员'
  }
}))
vi.mock('@/utils/ruoyi', () => ({
  tansParams: vi.fn(() => ''),
  blobValidate: mocks.blobValidate
}))
vi.mock('@/plugins/cache', () => ({
  default: {
    session: {
      getJSON: mocks.getSession,
      setJSON: mocks.setSession
    }
  }
}))
vi.mock('file-saver', () => ({ saveAs: mocks.saveAs }))
vi.mock('@/store/modules/user', () => ({
  default: () => ({ logOut: mocks.logOut })
}))

async function loadResponseHandlers() {
  vi.resetModules()
  mocks.responseUse.mockClear()
  const requestModule = await import('@/utils/request')
  const registration = mocks.responseUse.mock.calls.at(-1)
  return {
    requestModule,
    onFulfilled: registration[0],
    onRejected: registration[1]
  }
}

function textBlob(text, type = 'application/json') {
  return {
    type,
    size: new TextEncoder().encode(text).length,
    text: vi.fn().mockResolvedValue(text)
  }
}

describe('request response security contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.confirm.mockRejectedValue(new Error('cancelled'))
    mocks.logOut.mockResolvedValue(undefined)
    mocks.blobValidate.mockReturnValue(true)
    vi.stubGlobal('location', { href: 'http://localhost/' })
  })

  it('coalesces HTTP and legacy 401 responses while preserving each original rejection', async () => {
    let confirmRelogin
    mocks.confirm.mockReturnValue(new Promise(resolve => {
      confirmRelogin = resolve
    }))
    const { onFulfilled, onRejected } = await loadResponseHandlers()
    const httpError = {
      message: 'Request failed with status code 401: internal gateway',
      response: { status: 401, data: { msg: 'raw authentication details' } }
    }
    const legacyResponse = {
      status: 200,
      data: { code: 401, msg: 'raw legacy authentication details' },
      request: {}
    }

    const httpRejection = onRejected(httpError).catch(reason => reason)
    const legacyRejection = onFulfilled(legacyResponse).catch(reason => reason)

    expect(await httpRejection).toBe(httpError)
    expect(await legacyRejection).toBe(legacyResponse)
    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.logOut).not.toHaveBeenCalled()

    confirmRelogin()
    await vi.waitFor(() => expect(mocks.logOut).toHaveBeenCalledTimes(1))
    await vi.waitFor(() => expect(globalThis.location.href).toBe('/index'))
  })

  it('starts a new relogin flow after the previous prompt is cancelled', async () => {
    const { onRejected } = await loadResponseHandlers()
    const firstError = { response: { status: 401 } }
    const secondError = { response: { status: 401 } }

    await onRejected(firstError).catch(reason => reason)
    await vi.waitFor(() => expect(mocks.confirm).toHaveBeenCalledTimes(1))
    await new Promise(resolve => setTimeout(resolve, 0))
    await onRejected(secondError).catch(reason => reason)
    await vi.waitFor(() => expect(mocks.confirm).toHaveBeenCalledTimes(2))

    expect(mocks.logOut).not.toHaveBeenCalled()
  })

  it('shows only the approved exact conflict message and rejects the original HTTP error', async () => {
    const { onRejected } = await loadResponseHandlers()
    const error = {
      message: 'SQL: SELECT * FROM lab_reservation',
      response: {
        status: 409,
        data: {
          errorCode: 'LAB_RESERVATION_TIME_CONFLICT',
          msg: '该设备在所选时段已被预约'
        }
      }
    }

    const rejection = await onRejected(error).catch(reason => reason)

    expect(rejection).toBe(error)
    expect(mocks.message).toHaveBeenCalledWith(expect.objectContaining({
      message: '该设备在所选时段已被预约',
      type: 'error'
    }))
  })

  it('never renders arbitrary HTTP error messages', async () => {
    const { onRejected } = await loadResponseHandlers()
    const error = {
      message: 'Request failed: database password is secret',
      response: { status: 500, data: { msg: 'java.lang.IllegalStateException at Booking.java:42' } }
    }

    expect(await onRejected(error).catch(reason => reason)).toBe(error)
    expect(mocks.message).toHaveBeenCalledWith(expect.objectContaining({
      message: '系统处理失败，请稍后重试'
    }))
    expect(JSON.stringify(mocks.message.mock.calls)).not.toContain('Booking.java')
    expect(JSON.stringify(mocks.message.mock.calls)).not.toContain('database password')
  })

  it('uses a fixed message for a non-success legacy body and rejects the response itself', async () => {
    const { onFulfilled } = await loadResponseHandlers()
    const response = {
      status: 200,
      data: { code: 500, msg: 'SELECT password FROM sys_user' },
      request: {}
    }

    expect(await onFulfilled(response).catch(reason => reason)).toBe(response)
    expect(mocks.message).toHaveBeenCalledWith(expect.objectContaining({
      message: '系统处理失败，请稍后重试'
    }))
  })

  it('safely parses a JSON download body, rejects it, and closes loading in finally', async () => {
    const close = vi.fn()
    mocks.loadingService.mockReturnValue({ close })
    const data = textBlob(
      JSON.stringify({ code: 500, msg: '数据库地址10.0.0.8和密码secret' }),
      'application/json;charset=utf-8'
    )
    mocks.service.post.mockResolvedValue(data)
    mocks.blobValidate.mockReturnValue(true)
    const { requestModule } = await loadResponseHandlers()

    await expect(requestModule.download('/export', {}, 'labs.xlsx')).rejects.toBe(data)

    expect(mocks.saveAs).not.toHaveBeenCalled()
    expect(mocks.message.error).toHaveBeenCalledWith('系统处理失败，请稍后重试')
    expect(JSON.stringify(mocks.message.error.mock.calls)).not.toContain('10.0.0.8')
    expect(close).toHaveBeenCalledTimes(1)
  })

  it('routes a legacy 401 download body through the same relogin flight', async () => {
    let confirmRelogin
    mocks.confirm.mockReturnValue(new Promise(resolve => {
      confirmRelogin = resolve
    }))
    const close = vi.fn()
    mocks.loadingService.mockReturnValue({ close })
    const data = textBlob(JSON.stringify({ code: 401, msg: 'raw authentication details' }))
    mocks.service.post.mockResolvedValue(data)
    mocks.blobValidate.mockReturnValue(false)
    const { onRejected, requestModule } = await loadResponseHandlers()
    const httpError = { response: { status: 401 } }

    const requestRejection = onRejected(httpError).catch(reason => reason)
    const downloadRejection = requestModule.download('/export', {}, 'labs.xlsx').catch(reason => reason)

    expect(await requestRejection).toBe(httpError)
    expect(await downloadRejection).toBe(data)
    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.message.error).not.toHaveBeenCalled()
    expect(close).toHaveBeenCalledTimes(1)

    confirmRelogin()
    await vi.waitFor(() => expect(mocks.logOut).toHaveBeenCalledTimes(1))
  })
})
