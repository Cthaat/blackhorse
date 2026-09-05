import request from '@/utils/request'

export function getReservationTrace(id) {
  return request({ url: `/lab/reservations/${id}/trace`, method: 'get' })
}
