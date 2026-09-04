const DEFAULT_MESSAGE = '系统处理失败，请稍后重试'
const NETWORK_MESSAGE = '后端接口连接异常'
const TIMEOUT_MESSAGE = '系统接口请求超时'
const MAX_ERROR_BODY_SIZE = 64 * 1024

const STATUS_MESSAGES = {
  400: '请求参数不正确',
  401: '登录状态已失效',
  403: '没有执行该操作的权限',
  404: '请求的业务对象不存在',
  409: '当前状态或数据已发生冲突',
  500: DEFAULT_MESSAGE
}

const RESERVATION_CONFLICT = {
  errorCode: 'LAB_RESERVATION_TIME_CONFLICT',
  message: '该设备在所选时段已被预约'
}

function getResponse(value) {
  if (value === null || typeof value !== 'object') {
    return undefined
  }
  return value.response ?? value
}

function getMappedStatus(error) {
  const response = getResponse(error)
  const httpStatus = response?.status
  if (typeof httpStatus === 'number' && httpStatus !== 200) {
    return httpStatus
  }
  const legacyCode = response?.data?.code
  if (typeof legacyCode === 'number') {
    return legacyCode
  }
  return typeof httpStatus === 'number' ? httpStatus : undefined
}

function getApprovedServerMessage(error) {
  const response = getResponse(error)
  const data = response?.data
  if (response?.status !== 409 || data === null || typeof data !== 'object') {
    return undefined
  }
  if (data.errorCode !== RESERVATION_CONFLICT.errorCode || data.msg !== RESERVATION_CONFLICT.message) {
    return undefined
  }
  return RESERVATION_CONFLICT.message
}

export function isUnauthorizedResponse(value) {
  const response = getResponse(value)
  return response?.status === 401 || response?.data?.code === 401
}

export function isJsonBlob(data) {
  if (data === null || typeof data !== 'object' || typeof data.type !== 'string') {
    return false
  }
  const mediaType = data.type.split(';', 1)[0].trim().toLowerCase()
  return mediaType === 'application/json' || mediaType.endsWith('+json')
}

export async function parseJsonBlobSafely(data) {
  if (data === null || typeof data !== 'object' || typeof data.text !== 'function') {
    return undefined
  }
  if (typeof data.size === 'number' && data.size > MAX_ERROR_BODY_SIZE) {
    return undefined
  }
  try {
    const text = await data.text()
    if (typeof text !== 'string' || text.length > MAX_ERROR_BODY_SIZE) {
      return undefined
    }
    const parsed = JSON.parse(text)
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return undefined
    }
    return parsed
  } catch {
    return undefined
  }
}

export function resolveLabErrorMessage(error) {
  const approvedMessage = getApprovedServerMessage(error)
  if (approvedMessage) {
    return approvedMessage
  }

  const status = getMappedStatus(error)
  if (Object.hasOwn(STATUS_MESSAGES, status)) {
    return STATUS_MESSAGES[status]
  }
  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') {
    return TIMEOUT_MESSAGE
  }
  if (error?.message === 'Network Error') {
    return NETWORK_MESSAGE
  }
  return DEFAULT_MESSAGE
}
