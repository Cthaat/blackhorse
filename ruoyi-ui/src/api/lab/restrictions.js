import request from '@/utils/request'
import { encodeStringId, requireStringId, requireOptionalStringId, withStringIds } from './_contract'

const root = '/lab/restrictions'
export function listRestrictions(query = {}) {
  return request({ url: root, method: 'get', params: {
    pageNum: query.pageNum || 1, pageSize: query.pageSize || 10,
    mine: query.mine !== false, status: query.status || undefined,
    laboratoryId: requireOptionalStringId(query.laboratoryId, 'laboratoryId'),
    userId: requireOptionalStringId(query.userId, 'userId')
  } })
}
export function getRestriction(id) {
  return request({ url: `${root}/${encodeStringId(id)}`, method: 'get' })
}
export function createRestriction(data) {
  return request({ url: root, method: 'post', data: withStringIds(data, ['laboratoryId', 'userId']) })
}
export function revokeRestriction(id, reason) {
  return request({ url: `${root}/${encodeStringId(id)}/commands/revoke`, method: 'post', data: { reason } })
}
export function appealRestriction(id, data) {
  return request({ url: `${root}/${encodeStringId(id)}/appeal`, method: 'post', data: {
    reason: data.reason, attachmentIds: (data.attachmentIds || []).map(value => requireStringId(value, 'attachmentId'))
  } })
}
export function decideAppeal(id, data) {
  return request({ url: `${root}/${encodeStringId(id)}/appeal/decision`, method: 'post', data })
}
export function restrictionRules(laboratoryId) {
  return request({ url: `${root}/rules`, method: 'get', params: { laboratoryId: requireStringId(laboratoryId) } })
}
export function publishRestrictionRule(data) {
  return request({ url: `${root}/rules`, method: 'post', data: withStringIds(data, ['laboratoryId']) })
}
