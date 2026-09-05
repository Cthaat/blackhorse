import { it, expect } from 'vitest'
import QRCode from 'qrcode'
import { BinaryBitmap, HybridBinarizer, RGBLuminanceSource, QRCodeReader } from '@zxing/library'
import { assetDeviceUrl, parseAssetCode } from '@/utils/labAssetCode'

it('decodes real generated QR pixels for a representative device label', () => {
  const origin = 'https://lab.example.edu'
  const content = assetDeviceUrl('9007199254740993', origin)
  const qr = QRCode.create(content, { errorCorrectionLevel: 'M' })
  const scale = 4, margin = 4, size = (qr.modules.size + 2 * margin) * scale
  const pixels = new Uint8ClampedArray(size * size).fill(255)
  for (let y = 0; y < qr.modules.size; y++) for (let x = 0; x < qr.modules.size; x++) {
    if (!qr.modules.get(y, x)) continue
    for (let dy = 0; dy < scale; dy++) for (let dx = 0; dx < scale; dx++) {
      pixels[((y + margin) * scale + dy) * size + (x + margin) * scale + dx] = 0
    }
  }
  const decoded = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(pixels, size, size)))).getText()
  expect(decoded).toBe(content)
  expect(parseAssetCode(decoded, origin)).toBe('/lab/device/detail/9007199254740993')
})
