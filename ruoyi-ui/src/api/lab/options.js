import request from '@/utils/request'

/**
 * 返回实验室业务可选用户。接口仅暴露账号显示所需的最小字段。
 */
export function listLabUserOptions(params = {}) {
  return request({
    url: '/lab/options/users',
    method: 'get',
    params
  })
}

/**
 * 返回当前登录人可用于实验室建档的部门。
 */
export function listLabDepartmentOptions() {
  return request({
    url: '/lab/options/departments',
    method: 'get'
  })
}
