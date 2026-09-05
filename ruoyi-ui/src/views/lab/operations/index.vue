<template>
  <div class="app-container lab-page operations-workspace">
    <div class="page-heading"><div><h1>运行状态</h1><p>查看当前实例的服务健康、请求趋势与后台处理积压。</p></div><el-button :loading="loading" @click="load">刷新采样</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-skeleton v-if="loading && !snapshot" :rows="8" animated />
    <template v-else-if="snapshot">
      <p class="sample-note">最近采样：{{ time(snapshot.sampledAt) }}。本页面只展示本机测量值，告警只在页面显示。</p>
      <el-alert v-for="alert in alerts" :key="alert" :title="alert" type="warning" :closable="false" show-icon />
      <HealthPanel :snapshot="snapshot" />
      <HttpPanel :http="snapshot.http" />
      <QueuePanel :section="snapshot.queues" />
    </template>
    <el-empty v-else-if="!error" description="尚未取得运行状态" />
  </div>
</template>
<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { getOperations } from '@/api/lab/operations'
import HealthPanel from './components/HealthPanel.vue'
import HttpPanel from './components/HttpPanel.vue'
import QueuePanel from './components/QueuePanel.vue'
import { time } from './components/helpers'
const loading = ref(false), error = ref(''), snapshot = ref(null)
let sequence = 0
const alerts = computed(() => {
  const value = snapshot.value
  if (!value) return []
  const result = []
  if (value.database?.status !== 'UP') result.push('数据库不可用或未知，业务处理指标暂不可确认。')
  if (value.redis?.status !== 'UP') result.push('Redis 降级或未知，请核查缓存服务。')
  const queues = value.queues?.data
  if (queues?.deliveryBacklog > 0) result.push(`当前有 ${queues.deliveryBacklog} 条消息等待投递，最老投递编号 ${queues.references?.oldestDeliveryId || '采样期间已变化'}。下方可打开对应详情。`)
  const failures = [...(queues?.deliveries || []), ...(queues?.tasks || [])].filter(row => ['MANUAL_REQUIRED', 'FAILED'].includes(row.status)).reduce((total, row) => total + row.count, 0)
  if (failures > 0) result.push(`当前存在 ${failures} 条人工处理或失败记录。人工处理投递编号：${queues.references?.failedDeliveryId || '无'}；失败任务编号：${queues.references?.failedTaskId || '无'}。`)
  if (value.http?.systemErrors > 0) result.push(`最近窗口发生 ${value.http.systemErrors} 次 HTTP 服务端错误。`)
  return result
})
async function load() {
  if (loading.value) return
  const current = ++sequence; loading.value = true; error.value = ''
  try { const response = await getOperations(); if (current === sequence) snapshot.value = response.data }
  catch (failure) { if (current === sequence) { error.value = failure?.message || '运行状态采样失败，请重试'; snapshot.value = null } }
  finally { if (current === sequence) loading.value = false }
}
onMounted(load)
onBeforeUnmount(() => sequence++)
</script>
<style>
.operations-workspace { display: grid; gap: 16px; }
.operations-workspace .page-heading { margin-bottom: 0; }
.operations-workspace .sample-note { color: var(--el-text-color-secondary); line-height: 1.6; overflow-wrap: anywhere; }
.operations-workspace .metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.operations-workspace .metric-grid dl { margin: 0; padding: 16px; background: var(--el-fill-color-light); }
.operations-workspace dt { color: var(--el-text-color-secondary); font-size: 14px; }
.operations-workspace dd { margin: 8px 0 0; font-size: 24px; font-weight: 600; overflow-wrap: anywhere; }
.operations-workspace h2 { margin: 0; font-size: 18px; }
.operations-workspace h3 { margin-top: 20px; font-size: 16px; }
@media (max-width: 768px) { .operations-workspace .metric-grid { grid-template-columns: 1fr; } }
</style>
