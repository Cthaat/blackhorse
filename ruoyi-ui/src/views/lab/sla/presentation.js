export const types = { REPAIR: '故障维修', MAINTENANCE: '维护／校准', HAZARD: '隐患整改' }
export const risks = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', MAJOR: '重大风险' }
export const states = { OPEN: '计时中', NEAR_DUE: '临期', OVERDUE: '已逾期', PAUSED: '处理计时暂停', CLOSED: '已关闭' }
export const stages = { NEAR_DUE: '临期提醒', DUE: '到期提醒', ESCALATED: '逾期 24 小时升级' }
export const phases = { RESPONSE: '响应', PROCESSING: '处理' }
export function remainingTime(record, phase, now = Date.now()) {
  if (record.closedAt) return '已关闭'
  const processing = phase === 'PROCESSING'
  if (processing ? record.completedAt : record.respondedAt) return processing ? '已提交完成' : '已响应'
  const paused = processing && record.pausedAt
  const due = Date.parse(processing ? record.processingDueAt : record.responseDueAt)
  const anchor = paused ? Date.parse(record.pausedAt) : now
  if (!Number.isFinite(due) || !Number.isFinite(anchor)) return '-'
  const delta = due - anchor, minutes = Math.ceil(Math.abs(delta) / 60000)
  const prefix = delta <= 0 ? '已超时' : paused ? '暂停时剩余' : '剩余'
  return `${prefix} ${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`
}
export function rulePayload(form) {
  const responseHours = Number(form.responseHours), processingHours = Number(form.processingHours)
  if (!/^[1-9]\d{0,18}$/.test(String(form.laboratoryId))) throw new Error('请选择实验室')
  if (!types[form.businessType] || !risks[form.risk]) throw new Error('请选择有效业务类型和风险等级')
  if (!Number.isInteger(responseHours) || responseHours < 1 || responseHours > 720) throw new Error('响应期限应为 1～720 整数小时')
  if (!Number.isInteger(processingHours) || processingHours < responseHours || processingHours > 8760) throw new Error('处理期限不能短于响应期限，最多 8760 小时')
  const reason = String(form.reason || '').trim()
  if (!reason || reason.length > 500) throw new Error('请填写 1～500 字发布原因')
  return { laboratoryId: String(form.laboratoryId), businessType: form.businessType, risk: form.risk, responseHours, processingHours, reason }
}
export function businessTarget(record, canViewMaintenance = true) {
  if (!/^[1-9]\d{0,18}$/.test(String(record.objectId))) return null
  if (record.objectType === 'REPAIR_ORDER') return { path: `/lab/repair/detail/${record.objectId}` }
  if (record.objectType === 'HAZARD') return { path: `/lab/hazard/detail/${record.objectId}` }
  if (record.objectType === 'MAINTENANCE_CYCLE') {
    if (!canViewMaintenance) return /^[1-9]\d{0,18}$/.test(String(record.repairId)) ? { path: `/lab/repair/detail/${record.repairId}` } : null
    return { path: '/lab/maintenance', query: { ...(record.deviceId ? { deviceId: String(record.deviceId) } : {}), view: 'cycles' } }
  }
  return null
}
