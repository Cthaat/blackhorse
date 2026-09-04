import request from '@/utils/request'
import {
  encodeStringId,
  normalizeListQuery,
  requireOptionalStringId,
  requireStringId,
  withStringIds
} from './_contract'

const SORT_FIELDS = ['validUntil', 'createTime']

export function listQualification(query = {}) {
  const params = normalizeListQuery(query, SORT_FIELDS, 'createTime')
  params.userId = requireOptionalStringId(params.userId, 'userId')
  return request({
    url: '/lab/qualifications/list',
    method: 'get',
    params
  })
}

export function listMyQualification(query = {}) {
  return request({
    url: '/lab/qualifications/mine',
    method: 'get',
    params: normalizeListQuery(query, SORT_FIELDS, 'createTime')
  })
}

export function getQualification(id) {
  return request({
    url: `/lab/qualifications/${encodeStringId(id)}`,
    method: 'get'
  })
}

function qualificationPayload(data) {
  const payload = withStringIds(data, ['userId'])
  if (typeof payload.scopeId !== 'string' || payload.scopeId.trim() === '') {
    throw new TypeError('scopeId must be a non-empty string')
  }
  if (payload.scopeType === 'LABORATORY') {
    payload.scopeId = requireStringId(payload.scopeId, 'scopeId')
  }
  return payload
}

export function addQualification(data) {
  return request({
    url: '/lab/qualifications',
    method: 'post',
    data: qualificationPayload(data)
  })
}

export function updateQualification(id, data) {
  return request({
    url: `/lab/qualifications/${encodeStringId(id)}`,
    method: 'put',
    data: qualificationPayload(data)
  })
}

export function revokeQualification(id, data) {
  return request({
    url: `/lab/qualifications/${encodeStringId(id)}/commands/revoke`,
    method: 'put',
    data
  })
}
