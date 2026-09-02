import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const axios = vi.fn()
  const message = vi.fn()
  message.error = vi.fn()
  return {
    axios,
    message,
    closeLoading: vi.fn(),
    loadingService: vi.fn(),
    saveAs: vi.fn(),
    getToken: vi.fn(() => 'token'),
    blobValidate: vi.fn(),
    handleUnauthorized: vi.fn(error => Promise.reject(error))
  }
})

mocks.loadingService.mockImplementation(() => ({ close: mocks.closeLoading }))

vi.mock('axios', () => ({ default: mocks.axios }))
vi.mock('element-plus', () => ({
  ElLoading: { service: mocks.loadingService },
  ElMessage: mocks.message
}))
vi.mock('file-saver', () => ({ saveAs: mocks.saveAs }))
vi.mock('@/utils/auth', () => ({ getToken: mocks.getToken }))
vi.mock('@/utils/ruoyi', () => ({ blobValidate: mocks.blobValidate }))
vi.mock('@/utils/request', () => ({ handleUnauthorized: mocks.handleUnauthorized }))

async function loadDownloadPlugin() {
  vi.resetModules()
  return (await import('@/plugins/download')).default
}

function textBlob(text, type = 'application/json') {
  return {
    type,
    size: new TextEncoder().encode(text).length,
    text: vi.fn().mockResolvedValue(text)
  }
}

describe('download plugin error handling', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.loadingService.mockImplementation(() => ({ close: mocks.closeLoading }))
    mocks.handleUnauthorized.mockImplementation(error => Promise.reject(error))
  })

  it('saves a validated binary response and always closes zip loading', async () => {
    const response = {
      status: 200,
      data: new Blob(['archive'], { type: 'application/zip' }),
      headers: {}
    }
    mocks.axios.mockResolvedValue(response)
    mocks.blobValidate.mockReturnValue(true)
    const download = await loadDownloadPlugin()

    await expect(download.zip('/export', 'labs.zip')).resolves.toBeUndefined()

    expect(mocks.saveAs).toHaveBeenCalledWith(expect.any(Blob), 'labs.zip')
    expect(mocks.closeLoading).toHaveBeenCalledTimes(1)
  })

  it('routes an HTTP 401 through the shared relogin flight and preserves the error', async () => {
    const error = {
      message: 'Request failed with secret gateway details',
      response: { status: 401, data: { msg: 'raw authentication details' } }
    }
    mocks.axios.mockRejectedValue(error)
    const download = await loadDownloadPlugin()

    await expect(download.zip('/export', 'labs.zip')).rejects.toBe(error)

    expect(mocks.handleUnauthorized).toHaveBeenCalledTimes(1)
    expect(mocks.handleUnauthorized).toHaveBeenCalledWith(error)
    expect(mocks.message.error).not.toHaveBeenCalled()
    expect(mocks.closeLoading).toHaveBeenCalledTimes(1)
  })

  it('safely parses a JSON blob with a charset and never displays its raw message', async () => {
    const response = {
      status: 200,
      data: textBlob(
        JSON.stringify({ code: 500, msg: 'SELECT password FROM sys_user' }),
        'application/json;charset=utf-8'
      ),
      headers: {}
    }
    mocks.axios.mockResolvedValue(response)
    mocks.blobValidate.mockReturnValue(true)
    const download = await loadDownloadPlugin()

    await expect(download.zip('/export', 'labs.zip')).rejects.toBe(response)

    expect(mocks.saveAs).not.toHaveBeenCalled()
    expect(mocks.message.error).toHaveBeenCalledWith('系统处理失败，请稍后重试')
    expect(JSON.stringify(mocks.message.error.mock.calls)).not.toContain('SELECT password')
    expect(mocks.closeLoading).toHaveBeenCalledTimes(1)
  })

  it('routes a legacy 401 JSON blob through the shared relogin flight', async () => {
    const response = {
      status: 200,
      data: textBlob(JSON.stringify({ code: 401, msg: 'raw legacy authentication details' })),
      headers: {}
    }
    mocks.axios.mockResolvedValue(response)
    mocks.blobValidate.mockReturnValue(false)
    const download = await loadDownloadPlugin()

    await expect(download.resource('/manual.pdf')).rejects.toBe(response)

    expect(mocks.handleUnauthorized).toHaveBeenCalledWith(response)
    expect(mocks.message.error).not.toHaveBeenCalled()
  })

  it('uses the fixed fallback for malformed JSON and closes loading in finally', async () => {
    const response = {
      status: 200,
      data: textBlob('{not-json'),
      headers: {}
    }
    mocks.axios.mockResolvedValue(response)
    mocks.blobValidate.mockReturnValue(false)
    const download = await loadDownloadPlugin()

    await expect(download.zip('/export', 'labs.zip')).rejects.toBe(response)

    expect(mocks.message.error).toHaveBeenCalledWith('系统处理失败，请稍后重试')
    expect(mocks.closeLoading).toHaveBeenCalledTimes(1)
  })

  it('allows only the exact HTTP conflict message', async () => {
    const error = {
      response: {
        status: 409,
        data: {
          errorCode: 'LAB_RESERVATION_TIME_CONFLICT',
          msg: '该设备在所选时段已被预约'
        }
      }
    }
    mocks.axios.mockRejectedValue(error)
    const download = await loadDownloadPlugin()

    await expect(download.name('report.xlsx')).rejects.toBe(error)

    expect(mocks.message.error).toHaveBeenCalledWith('该设备在所选时段已被预约')
    expect(mocks.handleUnauthorized).not.toHaveBeenCalled()
  })
})
