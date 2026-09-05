const DEVICE_PATH = '/lab/device/detail/'

export function assetOrigin(value = import.meta.env.VITE_APP_ASSET_ORIGIN || window.location.origin) {
  const url = new URL(value)
  if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password
    || url.pathname !== '/' || url.search || url.hash || ![url.origin, `${url.origin}/`].includes(value)) {
    throw new Error('资产二维码地址配置无效：请配置完整站点来源，不包含路径或参数')
  }
  return url.origin
}

export function assetDeviceUrl(id, origin = assetOrigin()) {
  if (typeof id !== 'string' || !/^[1-9]\d{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n) {
    throw new Error('设备编号无效')
  }
  return `${assetOrigin(origin)}${DEVICE_PATH}${id}`
}

export function parseAssetCode(value, origin = assetOrigin()) {
  const url = new URL(value)
  const id = url.pathname.slice(DEVICE_PATH.length)
  if (!url.pathname.startsWith(DEVICE_PATH) || value !== assetDeviceUrl(id, origin)) {
    throw new Error('请扫描本站设备资产二维码')
  }
  return `${DEVICE_PATH}${id}`
}
