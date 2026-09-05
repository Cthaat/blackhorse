import request from '@/utils/request'
import { encodeStringId } from './_contract'
export const listTasks = params => request({ url: '/lab/tasks', params })
export const getTask = id => request({ url: `/lab/tasks/${encodeStringId(id)}` })
export const taskRows = (id, params) => request({ url: `/lab/tasks/${encodeStringId(id)}/rows`, params })
export const taskCommand = (id, command) => {
  if (!['submit', 'cancel', 'retry'].includes(command)) throw new Error('不支持的任务操作')
  return request({ url: `/lab/tasks/${encodeStringId(id)}/commands/${command}`, method: 'post' })
}
export const precheckTask = (kind, file) => {
  const data = new FormData(); data.append('kind', kind); data.append('file', file)
  return request({ url: '/lab/tasks/precheck', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })
}
export const exportTask = (kind, filters = {}) => request({ url: '/lab/tasks/exports', method: 'post', data: { kind, filters }, timeout: 120000 })
export const downloadTask = (id, errors = false) => request({ url: `/lab/tasks/${encodeStringId(id)}/download`, params: { errors }, responseType: 'blob', timeout: 120000 })
export const taskTemplate = kind => request({ url: '/lab/tasks/template', params: { kind }, responseType: 'blob' })
