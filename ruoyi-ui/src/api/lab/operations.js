import request from '@/utils/request'

export const getOperations = () => request({ url: '/lab/operations', method: 'get' })
