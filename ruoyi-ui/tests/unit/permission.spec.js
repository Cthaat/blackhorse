import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  beforeEach: vi.fn(),
  afterEach: vi.fn(),
  addRoute: vi.fn(),
  messageError: vi.fn(),
  progressStart: vi.fn(),
  progressDone: vi.fn(),
  getToken: vi.fn(() => 'token'),
  getInfo: vi.fn(),
  logOut: vi.fn(),
  setTitle: vi.fn(),
  generateRoutes: vi.fn(() => Promise.resolve([])),
  userStore: {
    roles: [],
    getInfo: vi.fn(),
    logOut: vi.fn()
  }
}))

vi.mock('@/router', () => ({
  default: {
    beforeEach: mocks.beforeEach,
    afterEach: mocks.afterEach,
    addRoute: mocks.addRoute
  }
}))
vi.mock('element-plus', () => ({
  ElMessage: { error: mocks.messageError }
}))
vi.mock('nprogress', () => ({
  default: {
    configure: vi.fn(),
    start: mocks.progressStart,
    done: mocks.progressDone
  }
}))
vi.mock('nprogress/nprogress.css', () => ({}))
vi.mock('@/utils/auth', () => ({ getToken: mocks.getToken }))
vi.mock('@/utils/validate', () => ({
  isHttp: vi.fn(() => false),
  isPathMatch: vi.fn((pattern, path) => pattern === path)
}))
vi.mock('@/store/modules/user', () => ({ default: () => mocks.userStore }))
vi.mock('@/store/modules/lock', () => ({ default: () => ({ isLock: false }) }))
vi.mock('@/store/modules/settings', () => ({ default: () => ({ setTitle: mocks.setTitle }) }))
vi.mock('@/store/modules/permission', () => ({
  default: () => ({ generateRoutes: mocks.generateRoutes })
}))

async function loadGuard() {
  vi.resetModules()
  mocks.beforeEach.mockClear()
  await import('@/permission')
  return mocks.beforeEach.mock.calls.at(-1)[0]
}

describe('permission guard error handling', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getToken.mockReturnValue('token')
    mocks.userStore.roles = []
    mocks.userStore.getInfo = mocks.getInfo
    mocks.userStore.logOut = mocks.logOut
  })

  it('does not duplicate logout or messages for a 401 already handled by the request layer', async () => {
    const error = {
      message: 'raw token details',
      response: { status: 401, data: { msg: 'raw authentication details' } }
    }
    mocks.getInfo.mockRejectedValue(error)
    const guard = await loadGuard()

    const result = await guard(
      { path: '/system/user', fullPath: '/system/user', meta: {} },
      { path: '/' }
    )

    expect(result).toBe(false)
    expect(mocks.logOut).not.toHaveBeenCalled()
    expect(mocks.messageError).not.toHaveBeenCalled()
  })

  it('cancels navigation and shows only a fixed message for another getInfo failure', async () => {
    const error = {
      message: 'database host 10.0.0.8 and password leaked',
      response: { status: 500, data: { msg: 'java.lang.IllegalStateException: secret' } }
    }
    mocks.getInfo.mockRejectedValue(error)
    const guard = await loadGuard()

    const result = await guard(
      { path: '/system/user', fullPath: '/system/user', meta: {} },
      { path: '/' }
    )

    expect(result).toBe(false)
    expect(mocks.logOut).not.toHaveBeenCalled()
    expect(mocks.messageError).toHaveBeenCalledWith('系统处理失败，请稍后重试')
    expect(JSON.stringify(mocks.messageError.mock.calls)).not.toContain('10.0.0.8')
    expect(JSON.stringify(mocks.messageError.mock.calls)).not.toContain('IllegalStateException')
  })
})
