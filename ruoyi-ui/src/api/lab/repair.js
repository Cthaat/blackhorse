import request from '@/utils/request'

function pathId(value) {
  if (value === undefined || value === null || String(value).trim() === '') {
    throw new TypeError('业务编号不能为空')
  }
  return encodeURIComponent(String(value))
}

export function listRepairOrders(query) {
  return request({
    url: '/lab/repair-orders',
    method: 'get',
    params: query
  })
}

export function getRepairOrder(id) {
  return request({
    url: `/lab/repair-orders/${pathId(id)}`,
    method: 'get'
  })
}

export function reportFault(data) {
  return request({
    url: '/lab/repair-orders/report',
    method: 'post',
    data
  })
}

export function assignRepair(id, data) {
  return request({
    url: `/lab/repair-orders/${pathId(id)}/assign`,
    method: 'post',
    data
  })
}

export function startRepair(id) {
  return request({
    url: `/lab/repair-orders/${pathId(id)}/start`,
    method: 'post'
  })
}

export function submitRepairResult(id, data) {
  return request({
    url: `/lab/repair-orders/${pathId(id)}/submit-result`,
    method: 'post',
    data
  })
}

export function acceptRepair(id, data) {
  return request({
    url: `/lab/repair-orders/${pathId(id)}/accept`,
    method: 'post',
    data
  })
}

export function listRepairDevices(query) {
  return request({
    url: '/lab/devices/list',
    method: 'get',
    params: query
  })
}
