<template>
  <el-dialog v-model="visible" title="设备资产标签" width="min(850px, 94vw)" append-to-body>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <p>标签仅包含资产编号、设备名称和设备详情地址。手机需能访问该站点，扫码后仍需登录。</p>
    <div v-loading="loading" class="asset-labels-preview">
      <article v-for="label in labels" :key="label.id" class="asset-label">
        <img :src="label.image" :alt="`${label.assetNo} 二维码`" width="180" height="180" />
        <strong>{{ label.assetNo }}</strong><span>{{ label.name }}</span>
      </article>
    </div>
    <template #footer><el-button @click="visible = false">关闭</el-button><el-button type="primary" :disabled="loading || !labels.length" @click="printLabels">打印标签</el-button></template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import QRCode from 'qrcode'
import { getAssetLabels } from '@/api/lab/device'
import { assetDeviceUrl } from '@/utils/labAssetCode'

const visible = ref(false), loading = ref(false), error = ref(''), labels = ref([])
let sequence = 0
async function open(ids) {
  const current = ++sequence
  visible.value = true
  labels.value = []
  error.value = ''
  loading.value = true
  try {
    const response = await getAssetLabels(ids)
    const generated = await Promise.all(response.data.map(async label => ({
      ...label, image: await QRCode.toDataURL(assetDeviceUrl(label.id), { width: 240, margin: 4, errorCorrectionLevel: 'M' })
    })))
    if (current === sequence) labels.value = generated
  } catch (failure) {
    if (current === sequence) error.value = failure.message || '标签生成失败，请重试'
  } finally {
    if (current === sequence) loading.value = false
  }
}
async function printLabels() {
  const frame = document.createElement('iframe')
  frame.title = '资产标签打印'
  frame.style.cssText = 'position:fixed;width:0;height:0;border:0'
  document.body.appendChild(frame)
  const doc = frame.contentDocument
  const style = doc.createElement('style')
  style.textContent = '@page{size:A4;margin:12mm}body{display:grid;grid-template-columns:repeat(3,1fr);gap:6mm;font:14px sans-serif}article{break-inside:avoid;border:1px solid #222;padding:4mm;text-align:center;display:flex;flex-direction:column;align-items:center;overflow-wrap:anywhere}img{width:42mm;height:42mm}'
  doc.head.appendChild(style)
  const images = labels.value.map(label => {
    const article = doc.createElement('article'), img = doc.createElement('img')
    img.src = label.image
    img.alt = label.assetNo
    article.appendChild(img)
    for (const value of [label.assetNo, label.name]) {
      const line = doc.createElement('div')
      line.textContent = value
      article.appendChild(line)
    }
    doc.body.appendChild(article)
    return img.decode()
  })
  try {
    await Promise.all(images)
    frame.contentWindow.addEventListener('afterprint', () => frame.remove(), { once: true })
    frame.contentWindow.focus()
    frame.contentWindow.print()
    setTimeout(() => frame.remove(), 60000)
  } catch {
    frame.remove()
    error.value = '打印图像加载失败，请重试'
  }
}
defineExpose({ open })
</script>

<style scoped>
.asset-labels-preview { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; }
.asset-label { display: flex; align-items: center; flex-direction: column; border: 1px solid var(--el-border-color); padding: 12px; overflow-wrap: anywhere; }
</style>
