import request from '@/utils/request'
import { encodeStringId } from './_contract'

export const listDeliveries = params => request({ url: '/lab/deliveries', method: 'get', params })
export const getDelivery = id => request({ url: `/lab/deliveries/${encodeStringId(id)}`, method: 'get' })
export const replayDelivery = (id, reason) => request({ url: `/lab/deliveries/${encodeStringId(id)}/commands/replay`, method: 'post', data: { reason } })
export const retryDeliveryNow = (id, reason) => request({ url: `/lab/deliveries/${encodeStringId(id)}/commands/retry-now`, method: 'post', data: { reason } })
export const listChannels = () => request({ url: '/lab/deliveries/channels', method: 'get' })
export const listTemplates = params => request({ url: '/lab/message-templates', method: 'get', params })
export const listTemplateVersions = params => request({ url: '/lab/message-templates/versions', method: 'get', params })
export const saveTemplate = (id, data) => request({ url: id ? `/lab/message-templates/${encodeStringId(id)}` : '/lab/message-templates', method: id ? 'put' : 'post', data })
export const previewTemplate = data => request({ url: '/lab/message-templates/commands/preview', method: 'post', data })
export const publishTemplate = id => request({ url: `/lab/message-templates/${encodeStringId(id)}/commands/publish`, method: 'post' })
export const getPreferences = () => request({ url: '/lab/notification-preferences', method: 'get' })
export const updatePreferences = optionalReminders => request({ url: '/lab/notification-preferences', method: 'put', data: { optionalReminders } })
