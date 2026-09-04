import request from '@/utils/request'
import {
  encodeStringId,
  normalizeListQuery,
  requireOptionalStringId,
  withStringIds
} from './_contract'

const SORT_FIELDS = ['assetNo', 'name', 'status', 'createTime']

export function listDevice(query = {}) {
  const params = normalizeListQuery(query, SORT_FIELDS, 'createTime')
  params.laboratoryId = requireOptionalStringId(params.laboratoryId, 'laboratoryId')
  return request({
    url: '/lab/devices/list',
    method: 'get',
    params
  })
}

export function getDevice(id) {
  return request({
    url: `/lab/devices/${encodeStringId(id)}`,
    method: 'get'
  })
}

export function addDevice(data) {
  return request({
    url: '/lab/devices',
    method: 'post',
    data: withStringIds(data, ['laboratoryId', 'managerId'])
  })
}

export function updateDevice(id, data) {
  return request({
    url: `/lab/devices/${encodeStringId(id)}`,
    method: 'put',
    data: withStringIds(data, ['laboratoryId', 'managerId'])
  })
}

export function changeDeviceStatus(id, data) {
  return request({
    url: `/lab/devices/${encodeStringId(id)}/commands/change-status`,
    method: 'put',
    data
  })
}

export function listOccupiedRanges(id, from, to) {
  return request({
    url: `/lab/devices/${encodeStringId(id)}/occupied-ranges`,
    method: 'get',
    params: { from, to }
  })
}
