import axios from 'axios'
import { ElLoading, ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'
import { getToken } from '@/utils/auth'
import { blobValidate } from '@/utils/ruoyi'
import { handleUnauthorized } from '@/utils/request'
import {
  isJsonBlob,
  isUnauthorizedResponse,
  parseJsonBlobSafely,
  resolveLabErrorMessage
} from '@/utils/lab/errorMessage'

const baseURL = import.meta.env.VITE_APP_BASE_API

function decodeFilename(value, fallback) {
  if (typeof value !== 'string' || value.length === 0) {
    return fallback
  }
  try {
    return decodeURIComponent(value)
  } catch {
    return fallback
  }
}

async function handleDownloadResponse(response, filename, contentType) {
  const data = response?.data
  const shouldParse = isJsonBlob(data) || !blobValidate(data)
  if (!shouldParse) {
    const blob = contentType ? new Blob([data], { type: contentType }) : new Blob([data])
    saveAs(blob, filename)
    return
  }

  const payload = await parseJsonBlobSafely(data)
  const responseForDisplay = { status: response?.status ?? 200, data: payload }
  if (isUnauthorizedResponse(responseForDisplay)) {
    return handleUnauthorized(response)
  }
  ElMessage.error(resolveLabErrorMessage(responseForDisplay))
  return Promise.reject(response)
}

async function requestDownload(config, getFilename, contentType) {
  try {
    const response = await axios(config)
    const filename = getFilename(response)
    return handleDownloadResponse(response, filename, contentType)
  } catch (error) {
    if (isUnauthorizedResponse(error)) {
      return handleUnauthorized(error)
    }
    ElMessage.error(resolveLabErrorMessage(error))
    return Promise.reject(error)
  }
}

export default {
  name(name, isDelete = true) {
    const url = baseURL + '/common/download?fileName=' + encodeURIComponent(name) + '&delete=' + isDelete
    return requestDownload({
      method: 'get',
      url: url,
      responseType: 'blob',
      headers: { 'Authorization': 'Bearer ' + getToken() }
    }, response => decodeFilename(response?.headers?.['download-filename'], name))
  },
  resource(resource) {
    const url = baseURL + '/common/download/resource?resource=' + encodeURIComponent(resource)
    return requestDownload({
      method: 'get',
      url: url,
      responseType: 'blob',
      headers: { 'Authorization': 'Bearer ' + getToken() }
    }, response => decodeFilename(response?.headers?.['download-filename'], 'download'))
  },
  zip(url, name) {
    const requestUrl = baseURL + url
    const loadingInstance = ElLoading.service({ text: '正在下载数据，请稍候', background: 'rgba(0, 0, 0, 0.7)' })
    return requestDownload({
      method: 'get',
      url: requestUrl,
      responseType: 'blob',
      headers: { 'Authorization': 'Bearer ' + getToken() }
    }, () => name, 'application/zip')
      .finally(() => loadingInstance.close())
  },
  saveAs(text, name, opts) {
    saveAs(text, name, opts)
  }
}

