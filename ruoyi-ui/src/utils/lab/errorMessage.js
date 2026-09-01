const DEFAULT_MESSAGE = '系统处理失败，请稍后重试'

const STATUS_MESSAGES = {
  400: '请求参数不正确',
  401: '登录状态已失效',
  403: '没有执行该操作的权限',
  404: '请求的业务对象不存在',
  409: '当前状态或数据已发生冲突',
  500: DEFAULT_MESSAGE
}

const SAFE_BUSINESS_MESSAGE_PATTERN = /^[\u3400-\u4DBF\u4E00-\u9FFF][\u3400-\u4DBF\u4E00-\u9FFFA-Za-z0-9 ，。！？、：；（）《》“”‘’·—…,.!?:;()_%％+\-]*$/
const SQL_PATTERN = /\b(?:sqlstate|sql|select|insert|update|delete|drop|alter|truncate|with|create|merge|replace|upsert|call|exec(?:ute)?|grant|revoke|union|pragma)\b/i
const EXCEPTION_PATTERN = /(?:\b(?:[A-Za-z_$][\w$]*\.)*(?:[A-Za-z_$][\w$]*(?:Exception|Error)|Exception|Error)\b|\b(?:[A-Za-z_]\w*::)*[A-Za-z_]\w*_(?:error|exception)\b)/i
const STACK_FRAME_PATTERN = /(?:\b(?:traceback|caused by)\b|(?:^|\s)at\s+(?:new\s+)?[\w$.<>]+(?:\s|\()|(?:^|\s)File\s+["'][^"']+["'],\s*line\s+\d+)/i

function getSafeServerMessage(error) {
  const message = error?.response?.data?.msg
  if (typeof message !== 'string') {
    return undefined
  }

  const normalizedMessage = message.trim()
  if (
    !SAFE_BUSINESS_MESSAGE_PATTERN.test(normalizedMessage) ||
    SQL_PATTERN.test(normalizedMessage) ||
    EXCEPTION_PATTERN.test(normalizedMessage) ||
    STACK_FRAME_PATTERN.test(normalizedMessage)
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
