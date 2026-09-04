import request from '@/utils/request'
import { requireStringId } from './_contract'

export function listStatusHistory(objectType, objectId) {
  return request({
    url: '/lab/status-histories',
    method: 'get',
    params: {
      objectType,
      objectId: requireStringId(objectId, 'objectId')
    }
  })
}
