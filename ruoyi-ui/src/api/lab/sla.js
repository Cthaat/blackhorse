import request from '@/utils/request'
function id(value) {
  if (!/^[1-9]\d{0,18}$/.test(String(value))) throw new TypeError('SLA 编号无效')
  return String(value)
}
export const listSlaRecords = params => request({ url: '/lab/sla/records', method: 'get', params: { ...params } })
export const getSlaRecord = value => request({ url: `/lab/sla/records/${id(value)}`, method: 'get' })
export const listSlaRules = laboratoryId => request({ url: '/lab/sla/rules', method: 'get', params: { laboratoryId } })
export const publishSlaRule = data => request({ url: '/lab/sla/rules', method: 'post', data })
export function slaCommand(value, action, data) {
  if (!['pause', 'resume'].includes(action)) throw new TypeError('SLA 命令无效')
  return request({ url: `/lab/sla/records/${id(value)}/commands/${action}`, method: 'post', data })
}
