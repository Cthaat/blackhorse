import axios from 'axios'
import { ElNotification , ElMessageBox, ElMessage, ElLoading } from 'element-plus'
import { getToken } from '@/utils/auth'
import { tansParams, blobValidate } from '@/utils/ruoyi'
import cache from '@/plugins/cache'
import { saveAs } from 'file-saver'
import useUserStore from '@/store/modules/user'
import {
  isJsonBlob,
  isUnauthorizedResponse,
  parseJsonBlobSafely,
  resolveLabErrorMessage
} from '@/utils/lab/errorMessage'

let reloginFlight

function ensureRelogin() {
  if (!reloginFlight) {
    const flight = Promise.resolve()
      .then(() => ElMessageBox.confirm(
        '登录状态已过期，您可以继续留在该页面，或者重新登录',
        '系统提示',
        {
          confirmButtonText: '重新登录',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ))
      .then(() => useUserStore().logOut())
      .then(() => {
        globalThis.location.href = '/index'
      })
      .catch(() => undefined)
    reloginFlight = flight.finally(() => {
      reloginFlight = undefined
    })
  }
  return reloginFlight
}

export function handleUnauthorized(error) {
  void ensureRelogin()
  return Promise.reject(error)
}

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8'
// 创建axios实例
const service = axios.create({
  // axios中请求配置有baseURL选项，表示请求URL公共部分
  baseURL: import.meta.env.VITE_APP_BASE_API,
  // 超时
  timeout: 10000
})

// request拦截器
service.interceptors.request.use(config => {
  // 是否需要设置 token
  const isToken = (config.headers || {}).isToken === false
  // 是否需要防止数据重复提交
  const isRepeatSubmit = (config.headers || {}).repeatSubmit === false
  // 间隔时间(ms)，小于此时间视为重复提交
  const interval = (config.headers || {}).interval || 1000
  if (getToken() && !isToken) {
    config.headers['Authorization'] = 'Bearer ' + getToken() // 让每个请求携带自定义token 请根据实际情况自行修改
  }
  // get请求映射params参数
  if (config.method === 'get' && config.params) {
    let url = config.url + '?' + tansParams(config.params)
    url = url.slice(0, -1)
    config.params = {}
    config.url = url
  }
  if (!isRepeatSubmit && (config.method === 'post' || config.method === 'put')) {
    const requestObj = {
      url: config.url,
      data: typeof config.data === 'object' ? JSON.stringify(config.data) : config.data,
      time: new Date().getTime()
    }
    const requestSize = Object.keys(JSON.stringify(requestObj)).length // 请求数据大小
    const limitSize = 5 * 1024 * 1024 // 限制存放数据5M
    if (requestSize >= limitSize) {
      console.warn(`[${config.url}]: ` + '请求数据大小超出允许的5M限制，无法进行防重复提交验证。')
      return config
    }
    const sessionObj = cache.session.getJSON('sessionObj')
    if (sessionObj === undefined || sessionObj === null || sessionObj === '') {
      cache.session.setJSON('sessionObj', requestObj)
    } else {
      const s_url = sessionObj.url                // 请求地址
      const s_data = sessionObj.data              // 请求数据
      const s_time = sessionObj.time              // 请求时间
      if (s_data === requestObj.data && requestObj.time - s_time < interval && s_url === requestObj.url) {
        const message = '数据正在处理，请勿重复提交'
        console.warn(`[${s_url}]: ` + message)
        return Promise.reject(new Error(message))
      } else {
        cache.session.setJSON('sessionObj', requestObj)
      }
    }
  }
  return config
}, error => {
    return Promise.reject(error)
})

// 响应拦截器
service.interceptors.response.use(res => {
    const code = res?.data?.code ?? 200
    // 二进制数据则直接返回
    if (isUnauthorizedResponse(res)) {
      return handleUnauthorized(res)
    }
    if (res?.request?.responseType === 'blob' || res?.request?.responseType === 'arraybuffer') {
      return res.data
    }
    if (code === 500) {
      ElMessage({ message: resolveLabErrorMessage(res), type: 'error' })
      return Promise.reject(res)
    } else if (code === 601) {
      ElMessage({ message: resolveLabErrorMessage(res), type: 'warning' })
      return Promise.reject(res)
    } else if (code !== 200) {
      ElNotification.error({ title: resolveLabErrorMessage(res) })
      return Promise.reject(res)
    } else {
      return Promise.resolve(res.data)
    }
  },
  error => {
    if (isUnauthorizedResponse(error)) {
      return handleUnauthorized(error)
    }
    ElMessage({ message: resolveLabErrorMessage(error), type: 'error', duration: 5 * 1000 })
    return Promise.reject(error)
  }
)

async function handleDownloadData(data, filename) {
  const shouldParse = isJsonBlob(data) || !blobValidate(data)
  if (!shouldParse) {
    saveAs(new Blob([data]), filename)
    return
  }

  const payload = await parseJsonBlobSafely(data)
  const response = { status: 200, data: payload }
  if (isUnauthorizedResponse(response)) {
    return handleUnauthorized(data)
  }
  ElMessage.error(resolveLabErrorMessage(response))
  return Promise.reject(data)
}

// 通用下载方法
export function download(url, params, filename, config) {
  const loadingInstance = ElLoading.service({ text: '正在下载数据，请稍候', background: 'rgba(0, 0, 0, 0.7)' })
  return service.post(url, params, {
    transformRequest: [(params) => { return tansParams(params) }],
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    responseType: 'blob',
    ...config
  })
    .then(data => handleDownloadData(data, filename))
    .finally(() => loadingInstance.close())
}

export default service
