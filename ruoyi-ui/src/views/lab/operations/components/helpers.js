export const time = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '未知'
export const number = value => value === null || value === undefined ? '未知' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
export const bytes = value => value === null || value === undefined ? '未知' : `${(value / 1024 / 1024).toFixed(1)} MiB`
export const statuses = { UP: '正常', DOWN: '不可用', DEGRADED: '降级', UNKNOWN: '未知' }
