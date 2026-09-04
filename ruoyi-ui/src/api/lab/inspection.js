import request from '@/utils/request'
import { encodeStringId } from './_contract'

export function listInspectionPlans(params) {
  return request({ url: '/lab/inspection-plans', method: 'get', params })
}

export function getInspectionPlan(id) {
  return request({ url: `/lab/inspection-plans/${encodeStringId(id)}`, method: 'get' })
}

export function createInspectionPlan(data) {
  return request({ url: '/lab/inspection-plans', method: 'post', data })
}

export function updateInspectionPlan(id, expectedVersion, data) {
  return request({
    url: `/lab/inspection-plans/${encodeStringId(id)}`,
    method: 'put',
    params: { expectedVersion },
    data
  })
}

export function enableInspectionPlan(id) {
  return request({ url: `/lab/inspection-plans/${encodeStringId(id)}/enable`, method: 'post' })
}

export function disableInspectionPlan(id) {
  return request({ url: `/lab/inspection-plans/${encodeStringId(id)}/disable`, method: 'post' })
}

export function listInspectionTasks(params) {
  return request({ url: '/lab/inspection-tasks', method: 'get', params })
}

export function getInspectionTask(id) {
  return request({ url: `/lab/inspection-tasks/${encodeStringId(id)}`, method: 'get' })
}

export function listInspectionItems(id) {
  return request({ url: `/lab/inspection-tasks/${encodeStringId(id)}/items`, method: 'get' })
}

export function startInspectionTask(id) {
  return request({ url: `/lab/inspection-tasks/${encodeStringId(id)}/start`, method: 'post' })
}

export function recordInspectionItem(taskId, itemId, data) {
  return request({
    url: `/lab/inspection-tasks/${encodeStringId(taskId, 'taskId')}/items/${encodeStringId(itemId, 'itemId')}`,
    method: 'put',
    data
  })
}

export function completeInspectionTask(id) {
  return request({ url: `/lab/inspection-tasks/${encodeStringId(id)}/complete`, method: 'post' })
}
