import request from '@/utils/request'
import { encodeStringId, requireStringId } from './_contract'

export function listAttachment(businessType, businessId) {
  return request({
    url: '/lab/attachments',
    method: 'get',
    params: {
      businessType,
      businessId: requireStringId(businessId, 'businessId')
    }
  })
}

export function uploadAttachment(businessType, businessId, file, onUploadProgress) {
  const data = new FormData()
  data.append('businessType', businessType)
  data.append('businessId', requireStringId(businessId, 'businessId'))
  data.append('file', file)
  return request({
    url: '/lab/attachments',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data,
    onUploadProgress
  })
}

export function downloadAttachment(id) {
  return request({
    url: `/lab/attachments/${encodeStringId(id)}/content`,
    method: 'get',
    responseType: 'blob'
  })
}

export function delAttachment(id) {
  return request({
    url: `/lab/attachments/${encodeStringId(id)}`,
    method: 'delete'
  })
}
