export const messageOf = (error, fallback = '操作失败，请刷新后重试') => error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? fallback
export const timeText = value => value ? String(value).replace('T', ' ').slice(0, 19) : '—'
export const fields = { minLeadMinutes: '最短提前量（分钟）', maxAdvanceDays: '最远预约（天）', minDurationMinutes: '最短时长（分钟）', maxDurationMinutes: '最长时长（分钟）', invitationMinutes: '候补邀请有效期（分钟）' }
export function today() { return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date()) }
export function countdown(until, now) {
  const seconds = Math.max(0, Math.ceil((Date.parse(until) - now) / 1000))
  return Number.isFinite(seconds) && seconds > 0 ? `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒` : '已到期，请刷新'
}
