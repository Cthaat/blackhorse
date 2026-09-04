import request from '@/utils/request'

function pathId(value) {
  if (value === undefined || value === null || String(value).trim() === '') {
    throw new TypeError('业务编号不能为空')
  }
  return encodeURIComponent(String(value))
}

export function listReservations(query) {
  return request({
    url: '/lab/reservations',
    method: 'get',
    params: query
  })
}

export function getReservation(id) {
  return request({
    url: `/lab/reservations/${pathId(id)}`,
    method: 'get'
  })
}

export function applyReservation(data, idempotencyKey) {
  return request({
    url: '/lab/reservations',
    method: 'post',
    headers: {
      'X-Idempotency-Key': idempotencyKey,
      repeatSubmit: false
    },
    data
  })
}

export function approveReservation(id, data) {
  return request({
    url: `/lab/reservations/${pathId(id)}/commands/approve`,
    method: 'post',
    data
  })
}

export function rejectReservation(id, data) {
  return request({
    url: `/lab/reservations/${pathId(id)}/commands/reject`,
    method: 'post',
    data
  })
}

export function cancelReservation(id, data) {
  return request({
    url: `/lab/reservations/${pathId(id)}/commands/cancel`,
    method: 'post',
    data
  })
}

export function listReservationDevices(query) {
  return request({
    url: '/lab/devices/list',
    method: 'get',
    params: query
  })
}

export function getDeviceOccupiedRanges(deviceId, query) {
  return request({
    url: `/lab/devices/${pathId(deviceId)}/occupied-ranges`,
    method: 'get',
    params: query
  })
}
