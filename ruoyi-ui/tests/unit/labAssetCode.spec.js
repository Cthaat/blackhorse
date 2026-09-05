import { describe, it, expect } from 'vitest'
import { assetDeviceUrl, parseAssetCode, assetOrigin } from '@/utils/labAssetCode'

describe('asset QR trust boundary', () => {
  const origin = 'https://lab.example.edu'
  it('round trips a long string ID without precision loss', () => {
    const id = '9223372036854775807'
    expect(parseAssetCode(assetDeviceUrl(id, origin), origin)).toBe(`/lab/device/detail/${id}`)
  })
  it.each(['https://evil.test/lab/device/detail/1', 'https://user@lab.example.edu/lab/device/detail/1',
    'https://lab.example.edu/lab/device/detail/1?command=checkout', 'https://lab.example.edu/lab/device/detail/1#x',
    'https://lab.example.edu/lab/device/detail/01', 'https://lab.example.edu/lab/device/detail/9223372036854775808',
    '/lab/device/detail/1', 'javascript:alert(1)', 'https://lab.example.edu/a/../lab/device/detail/1'])('rejects %s', code => {
    expect(() => parseAssetCode(code, origin)).toThrow()
  })
  it.each(['https://lab.example.edu/path', 'https://u@lab.example.edu', 'https://lab.example.edu?q=1', 'ftp://lab.example.edu'])('rejects invalid configured origin %s', value => {
    expect(() => assetOrigin(value)).toThrow()
  })
  it('accepts an explicit origin and refuses a different origin', () => {
    expect(assetOrigin(origin + '/')).toBe(origin)
    expect(() => parseAssetCode('https://other.test/lab/device/detail/1', origin)).toThrow()
  })
})
