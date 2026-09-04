import request from '@/utils/request'
import { encodeStringId } from './_contract'

export function listNotifications(params) {
  return request({ url: '/lab/notifications', method: 'get', params })
}

export function getNotification(id) {
  return request({ url: `/lab/notifications/${encodeStringId(id)}`, method: 'get' })
}

export function markNotificationRead(id) {
  return request({ url: `/lab/notifications/${encodeStringId(id)}/commands/read`, method: 'put' })
}
