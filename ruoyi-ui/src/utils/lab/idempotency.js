function canonicalize(value) {
  if (Array.isArray(value)) {
    return value.map(canonicalize)
  }
  if (value && typeof value === 'object') {
    return Object.keys(value)
      .sort()
      .reduce((result, key) => {
        result[key] = canonicalize(value[key])
        return result
      }, {})
  }
  return value
}

function fingerprint(payload) {
  return JSON.stringify(canonicalize(payload))
}

export function createIdempotencyKey() {
  const cryptoApi = globalThis.crypto
  if (typeof cryptoApi?.randomUUID === 'function') {
    return cryptoApi.randomUUID()
  }
  if (typeof cryptoApi?.getRandomValues !== 'function') {
    throw new Error('当前浏览器不支持安全随机数，无法提交预约')
  }
  const bytes = cryptoApi.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0'))
  return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10).join('')}`
}

export function createIdempotentSubmission() {
  let key
  let currentFingerprint

  function update(payload) {
    const nextFingerprint = fingerprint(payload)
    if (currentFingerprint !== nextFingerprint) {
      currentFingerprint = nextFingerprint
      key = undefined
    }
  }

  function prepare(payload) {
    update(payload)
    key ??= createIdempotencyKey()
    return key
  }

  function clear() {
    key = undefined
    currentFingerprint = undefined
  }

  function handleFailure(error) {
    const status = error?.response?.status
      ?? error?.status
      ?? error?.response?.data?.code
      ?? error?.data?.code
    if (Number.isInteger(status) && status >= 400 && status < 500) {
      clear()
    }
  }

  return {
    update,
    prepare,
    clear,
    handleFailure,
    hasPendingKey: () => Boolean(key)
  }
}
