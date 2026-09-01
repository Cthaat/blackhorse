const DEFAULT_MESSAGE = '系统处理失败，请稍后重试'

const STATUS_MESSAGES = {
  400: '请求参数不正确',
  401: '登录状态已失效',
  403: '没有执行该操作的权限',
  404: '请求的业务对象不存在',
  409: '当前状态或数据已发生冲突',
  500: DEFAULT_MESSAGE
}

const SAFE_SERVER_MESSAGE_PATTERN = /^\p{Script=Han}[\p{Script=Han}\p{Nd} \u3000，。！？、：；（）·—…％,.!?:;()%+\-]{0,199}$/u
const INTERNAL_DETAIL_PATTERN = /(?:数据库|异常|堆栈|栈跟踪)/

function getSafeServerMessage(error) {
  const message = error?.response?.data?.msg
  if (typeof message !== 'string') {
    return undefined
  }

  const normalizedMessage = message.trim()
  if (
    !SAFE_SERVER_MESSAGE_PATTERN.test(normalizedMessage) ||
    INTERNAL_DETAIL_PATTERN.test(normalizedMessage)
  ) {
    return undefined
  }

  return normalizedMessage
}

export function resolveLabErrorMessage(error) {
  const status = error?.response?.status
  const fallbackMessage = Object.hasOwn(STATUS_MESSAGES, status) ? STATUS_MESSAGES[status] : DEFAULT_MESSAGE
  return getSafeServerMessage(error) ?? fallbackMessage
}
