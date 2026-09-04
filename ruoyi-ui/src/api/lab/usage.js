import request from '@/utils/request'

function pathId(value) {
  if (value === undefined || value === null || String(value).trim() === '') {
    throw new TypeError('业务编号不能为空')
  }
  return encodeURIComponent(String(value))
}

export function listUsageRecords(query) {
  return request({
    url: '/lab/usage-records',
    method: 'get',
    params: query
  })
}

export function getUsageRecord(id) {
  return request({
    url: `/lab/usage-records/${pathId(id)}`,
    method: 'get'
  })
}

export function checkOutDevice(data) {
  return request({
    url: '/lab/usage-records/check-out',
    method: 'post',
    data
  })
}

export function returnDevice(id, data) {
  return request({
    url: `/lab/usage-records/${pathId(id)}/return`,
    method: 'post',
    data
  })
}
