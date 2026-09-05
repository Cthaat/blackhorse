import { afterEach, expect, it } from 'vitest'
import { handleThemeStyle } from '@/utils/theme'

function luminance(hex) {
  const rgb = hex.match(/[\da-f]{2}/gi).map(channel => parseInt(channel, 16) / 255)
    .map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
  return rgb[0] * 0.2126 + rgb[1] * 0.7152 + rgb[2] * 0.0722
}
function contrast(a, b) {
  const values = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (values[0] + 0.05) / (values[1] + 0.05)
}
afterEach(() => { document.documentElement.classList.remove('dark'); document.documentElement.removeAttribute('style') })

it('keeps the workspace accent readable on dark surfaces and uses dark tint backgrounds', () => {
  document.documentElement.classList.add('dark')
  handleThemeStyle('#0f766e')
  const style = document.documentElement.style
  const primary = style.getPropertyValue('--el-color-primary')
  expect(contrast(primary, '#192a35')).toBeGreaterThanOrEqual(4.5)
  expect(contrast(primary, '#111c24')).toBeGreaterThanOrEqual(4.5)
  expect(luminance(style.getPropertyValue('--el-color-primary-light-9'))).toBeLessThan(0.1)
})
it('restores the configured accent when switching back to light mode', () => {
  handleThemeStyle('#0f766e')
  expect(document.documentElement.style.getPropertyValue('--el-color-primary')).toBe('#0f766e')
})
