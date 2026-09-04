import request from '@/utils/request'
import { encodeStringId } from './_contract'

export function listHazards(params) {
  return request({ url: '/lab/hazards', method: 'get', params })
}

export function getHazard(id) {
  return request({ url: `/lab/hazards/${encodeStringId(id)}`, method: 'get' })
}

export function listRectifications(id) {
  return request({ url: `/lab/hazards/${encodeStringId(id)}/rectifications`, method: 'get' })
}

export function createHazard(data) {
  return request({ url: '/lab/hazards', method: 'post', data })
}

export function startRectification(id) {
  return request({ url: `/lab/hazards/${encodeStringId(id)}/start-rectification`, method: 'post' })
}

export function submitRectification(id, data) {
  return request({ url: `/lab/hazards/${encodeStringId(id)}/rectifications`, method: 'post', data })
}

export function reviewRectification(hazardId, roundId, data) {
  return request({
    url: `/lab/hazards/${encodeStringId(hazardId, 'hazardId')}/rectifications/${encodeStringId(roundId, 'roundId')}/review`,
    method: 'post',
    data
  })
}
