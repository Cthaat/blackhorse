export const kinds = { MAINTENANCE: '预防性维护', CALIBRATION: '计量校准' }
export const cycleStates = { PLANNED: '待安排窗口', SCHEDULED: '窗口已安排', STARTED: '执行中', COMPLETED: '已验收完成' }
export function reasonText(value) {
  const text = String(value || '').trim()
  if (!text || text.length > 500) throw new Error('请填写 1～500 字操作原因')
  return text
}
function businessId(value) {
  if (!/^[1-9]\d{0,18}$/.test(String(value))) throw new Error('请选择有效设备和负责人')
  return String(value)
}
function timestamp(value) {
  if (typeof value !== 'string' || !/(?:Z|[+-]\d{2}:\d{2})$/.test(value) || !Number.isFinite(Date.parse(value))) throw new Error('请选择带时区的有效时间')
  return value
}
export function planPayload(form) {
  const periodDays = Number(form.periodDays)
  if (!Number.isInteger(periodDays) || periodDays < 1 || periodDays > 3650) throw new Error('周期应为 1～3650 整数天')
  if (!kinds[form.kind]) throw new Error('请选择维护或校准类型')
  const description = String(form.description || '').trim()
  if (description.length > 1000) throw new Error('说明不能超过 1000 字')
  return { deviceId: businessId(form.deviceId), kind: form.kind, periodDays, firstDueAt: timestamp(form.firstDueAt),
    responsibleId: businessId(form.responsibleId), description, reason: reasonText(form.reason),
    ...(form.expectedVersion !== undefined ? { expectedVersion: form.expectedVersion } : {}) }
}
export function windowPayload(form) {
  const startTime = timestamp(form.startTime), endTime = timestamp(form.endTime)
  if (Date.parse(endTime) <= Date.parse(startTime)) throw new Error('结束时间必须晚于开始时间')
  return { startTime, endTime, expectedVersion: form.expectedVersion, reason: reasonText(form.reason) }
}
