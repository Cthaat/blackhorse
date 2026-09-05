<template>
  <div class="app-container lab-page">
    <div class="page-heading"><div><h1>扫描资产</h1><p>扫描设备标签查看详情；领用、预约与报修均需在对应页面确认。</p></div></div>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb16" />
    <el-card shadow="never">
      <p>点击后才请求摄像头权限。摄像头需要 HTTPS 或本机安全环境，也可直接输入资产编号。</p>
      <el-button type="primary" :disabled="running || starting" @click="start">{{ starting ? '正在启动摄像头…' : '开启摄像头' }}</el-button>
      <el-button :disabled="!running && !starting" @click="stop">停止扫描</el-button>
      <video ref="video" v-show="running || starting" muted playsinline aria-label="资产二维码取景画面" />
    </el-card>
    <el-form class="search-form" @submit.prevent="search">
      <el-form-item label="资产编号"><el-input v-model="assetNo" maxlength="64" clearable placeholder="输入完整资产编号" /></el-form-item>
      <el-button type="primary" native-type="submit" :loading="searching">查找设备</el-button>
    </el-form>
    <el-empty v-if="notFound" description="未找到可访问的设备，请核对完整资产编号" />
  </div>
</template>

<script setup name="LabAssetScan">
import { ref, onBeforeUnmount, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import { BrowserQRCodeReader } from '@zxing/browser'
import { listDevice } from '@/api/lab/device'
import { loadAllOptions } from '@/utils/labOptions'
import { assetDeviceUrl, parseAssetCode } from '@/utils/labAssetCode'

const router = useRouter(), video = ref(), running = ref(false), starting = ref(false)
const error = ref(''), assetNo = ref(''), searching = ref(false), notFound = ref(false)
let controls, stream, sequence = 0, searchSequence = 0, acquiring = false
function stop() {
  ++sequence
  ++searchSequence
  searching.value = false
  controls?.stop()
  controls = undefined
  stream?.getTracks().forEach(track => track.stop())
  stream = undefined
  if (video.value) video.value.srcObject = null
  running.value = false
  starting.value = acquiring
}
async function start() {
  if (acquiring || starting.value || running.value) return
  error.value = ''
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    error.value = '当前环境无法使用摄像头，请使用 HTTPS 访问或输入资产编号'
    return
  }
  const current = ++sequence
  starting.value = true
  acquiring = true
  try {
    const acquired = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
    if (current !== sequence) { acquired.getTracks().forEach(track => track.stop()); return }
    stream = acquired
    const reader = new BrowserQRCodeReader()
    const started = await reader.decodeFromStream(stream, video.value, result => {
      if (!result || current !== sequence) return
      try {
        const path = parseAssetCode(result.getText())
        stop()
        router.push(path)
      } catch { error.value = '识别到的二维码不是本站设备标签，请重新扫描' }
    })
    if (current !== sequence) { started.stop(); return }
    controls = started
    running.value = true
  } catch (failure) {
    if (current !== sequence) return
    stop()
    error.value = failure.name === 'NotAllowedError'
      ? '摄像头权限被拒绝，请允许权限后重试，或输入资产编号'
      : '摄像头无法启动，请检查设备占用或输入资产编号'
  } finally { acquiring = false; starting.value = false }
}
async function search() {
  if (!assetNo.value.trim() || searching.value) return
  const submittedAssetNo = assetNo.value.trim()
  const current = ++searchSequence
  error.value = ''
  notFound.value = false
  searching.value = true
  try {
    const response = await loadAllOptions(listDevice, { keyword: submittedAssetNo })
    if (current !== searchSequence) return
    const device = response.rows?.find(row => row.assetNo === submittedAssetNo)
    if (!device) { notFound.value = true; return }
    stop()
    await router.push(parseAssetCode(assetDeviceUrl(device.id)))
  } catch { if (current === searchSequence) error.value = '设备查询失败，请确认有设备查询权限后重试' }
  finally { if (current === searchSequence) searching.value = false }
}
onBeforeUnmount(stop)
onDeactivated(stop)
</script>

<style scoped>
video { display: block; width: 100%; max-width: 560px; margin-top: 16px; }
.search-form { margin-top: 24px; max-width: 560px; }
.mb16 { margin-bottom: 16px; }
</style>
