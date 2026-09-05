import request from '@/utils/request'
const base = '/lab/maintenance'
function id(value) {
  if (!/^[1-9]\d{0,18}$/.test(String(value))) throw new TypeError('业务编号无效')
  return String(value)
}
export const listMaintenancePlans = params => request({ url: `${base}/plans`, method: 'get', params: { ...params } })
export const getMaintenancePlan = value => request({ url: `${base}/plans/${id(value)}`, method: 'get' })
export const listMaintenanceCycles = params => request({ url: `${base}/cycles`, method: 'get', params: { ...params } })
export const saveMaintenancePlan = (value, data) => request({ url: value ? `${base}/plans/${id(value)}` : `${base}/plans`, method: value ? 'put' : 'post', data })
export const toggleMaintenancePlan = (value, data) => request({ url: `${base}/plans/${id(value)}/commands/toggle`, method: 'post', data })
export function maintenanceCycleCommand(value, action, data) {
  if (!['window', 'start'].includes(action)) throw new TypeError('维护命令无效')
  return request({ url: `${base}/cycles/${id(value)}/commands/${action}`, method: 'post', data })
}
