import request from '@/utils/request'
import { loadAllOptions } from '@/utils/labOptions'

/**
 * 返回实验室业务可选用户。接口仅暴露账号显示所需的最小字段。
 */
export function listLabUserOptions(params = {}) {
  return loadAllOptions(query => request({
    url: '/lab/options/users',
    method: 'get',
    params: query
  }), params)
}

/**
 * 返回当前登录人可用于实验室建档的部门。
 */
export function listLabDepartmentOptions() {
  return loadAllOptions(params => request({
    url: '/lab/options/departments',
    method: 'get',
    params
  }))
}
