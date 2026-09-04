import request from '@/utils/request'
import {
  encodeStringId,
  normalizeListQuery,
  withStringIds
} from './_contract'

const SORT_FIELDS = ['name', 'labCode', 'createTime']

export function listLaboratory(query = {}) {
  return request({
    url: '/lab/laboratories/list',
    method: 'get',
    params: normalizeListQuery(query, SORT_FIELDS, 'createTime')
  })
}

export function getLaboratory(id) {
  return request({
    url: `/lab/laboratories/${encodeStringId(id)}`,
    method: 'get'
  })
}

export function addLaboratory(data) {
  return request({
    url: '/lab/laboratories',
    method: 'post',
    data: withStringIds(data, ['deptId', 'managerId'])
  })
}

export function updateLaboratory(id, data) {
  return request({
    url: `/lab/laboratories/${encodeStringId(id)}`,
    method: 'put',
    data: withStringIds(data, ['deptId', 'managerId'])
  })
}

export function enableLaboratory(id, reason) {
  return request({
    url: `/lab/laboratories/${encodeStringId(id)}/commands/enable`,
    method: 'put',
    data: { reason }
  })
}

export function disableLaboratory(id, reason) {
  return request({
    url: `/lab/laboratories/${encodeStringId(id)}/commands/disable`,
    method: 'put',
    data: { reason }
  })
}
