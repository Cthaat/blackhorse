import request from '@/utils/request'

const rules = '/lab/reservation-rules'
const waitlist = '/lab/reservation-waitlist'
const id = value => encodeURIComponent(String(value))
export const getCalendar = params => request({ url: `${rules}/calendar`, params })
export const listRules = params => request({ url: rules, params })
export const createRule = data => request({ url: rules, method: 'post', data })
export const updateRule = (key, data) => request({ url: `${rules}/${id(key)}`, method: 'put', data })
export const ruleCommand = (key, command, revision) => request({ url: `${rules}/${id(key)}/commands/${command}`, method: 'post', data: { expectedVersion: revision } })
export const ruleImpact = (key, params) => request({ url: `${rules}/${id(key)}/impact`, params })
export const simulateRule = (data, ruleId) => request({ url: `${rules}/simulation`, method: 'post', params: { ruleId }, data })
export const listWaitlist = params => request({ url: waitlist, params })
export const joinWaitlist = (data, key) => request({ url: waitlist, method: 'post', headers: { 'X-Idempotency-Key': key, repeatSubmit: false }, data })
export const waitlistCommand = (key, command, version) => request({ url: `${waitlist}/${id(key)}/commands/${command}`, method: 'post', data: { expectedVersion: version } })
