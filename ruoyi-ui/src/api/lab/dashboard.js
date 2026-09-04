import request from '@/utils/request'

export function getDashboardSummary(params) {
  return request({ url: '/lab/dashboard/summary', method: 'get', params })
}
