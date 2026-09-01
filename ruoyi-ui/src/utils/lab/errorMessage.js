const DEFAULT_MESSAGE = '系统处理失败，请稍后重试'

const STATUS_MESSAGES = {
  400: '请求参数不正确',
  401: '登录状态已失效',
  403: '没有执行该操作的权限',
  404: '请求的业务对象不存在',
  409: '当前状态或数据已发生冲突',
  500: DEFAULT_MESSAGE
}

const APPROVED_SERVER_MESSAGES = {
  409: '该设备在所选时段已被预约'
}

function getApprovedServerMessage(error) {
  const status = error?.response?.status
  const message = error?.response?.data?.msg
  if (typeof message !== 'string' || !Object.hasOwn(APPROVED_SERVER_MESSAGES, status)) {
    return undefined
  }

  const normalizedMessage = message.trim()
  return normalizedMessage === APPROVED_SERVER_MESSAGES[status] ? normalizedMessage : undefined
}

export function resolveLabErrorMessage(error) {
  const status = error?.response?.status
  const fallbackMessage = Object.hasOwn(STATUS_MESSAGES, status) ? STATUS_MESSAGES[status] : DEFAULT_MESSAGE
  return getApprovedServerMessage(error) ?? fallbackMessage
}
