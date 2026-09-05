export function restrictionState(row, now = Date.now()) {
  if (row.status === 'REVOKED') return '已解除'
  if (row.status === 'EXPIRED' || new Date(row.endsAt).getTime() <= now) return '已到期'
  return row.status === 'ACTIVE' ? '生效中' : '状态未知'
}

export function validateRestrictionReason(value) {
  const reason = typeof value === 'string' ? value.trim() : ''
  if (!reason || reason.length > 500) throw new Error('请填写 1～500 字的原因或说明')
  return reason
}

export function validateRestrictionDays(value, maximum) {
  if (!Number.isInteger(value) || value < 1 || value > maximum) {
    throw new Error(`期限必须为 1～${maximum} 的整数天`)
  }
  return value
}
