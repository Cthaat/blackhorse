<template>
  <div class="app-container lab-page">
    <div class="page-heading"><div><h1>消息中心</h1><p>集中查看预约、维修、巡检和整改通知，及时跟进业务变化。</p></div></div>
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <div><span class="title">消息中心</span><el-badge :value="notificationStore.unreadCount" :hidden="notificationStore.unreadCount === 0" class="badge" /></div>
          <el-switch v-model="unreadOnly" active-text="仅看未读" @change="handleFilter" />
        </div>
      </template>
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false"><el-button link type="primary" @click="loadMessages">重新加载</el-button></el-alert>
      <el-skeleton v-else-if="loading" :rows="5" animated />
      <el-empty v-else-if="!messages.length" description="暂无消息" />
      <div v-else class="message-list">
        <article v-for="message in messages" :key="message.id" :class="['message-item', { unread: !message.readAt }]">
          <button type="button" class="message-main" @click="openMessage(message)">
            <div class="message-title"><span>{{ message.title }}</span><el-tag v-if="!message.readAt" type="danger" size="small">未读</el-tag></div>
            <p>{{ message.content }}</p>
            <div class="meta"><span>{{ typeText(message.notificationType) }}</span><span>{{ parseTime(message.createTime) }}</span></div>
          </button>
          <div class="message-actions">
            <el-button v-if="!message.readAt" link type="primary" v-hasPermi="['lab:notification:read']" @click="markRead(message)">标为已读</el-button>
            <el-button v-if="businessTarget(message)" link type="primary" @click="openBusiness(message)">查看业务</el-button>
          </div>
        </article>
      </div>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="loadMessages" />
    </el-card>

    <el-dialog v-model="detailOpen" title="消息详情" width="560px" append-to-body>
      <lab-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item label="标题">{{ selected.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeText(selected.notificationType) }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ parseTime(selected.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="内容"><div class="message-content">{{ selected.content }}</div></el-descriptions-item>
      </lab-descriptions>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button><el-button v-if="businessTarget(selected)" type="primary" @click="openBusiness(selected)">查看业务</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getNotification, listNotifications, markNotificationRead } from '@/api/lab/notification'
import useUserStore from '@/store/modules/user'
import useLabNotificationStore from '@/store/modules/labNotification'

const router = useRouter()
const notificationStore = useLabNotificationStore()
const userStore = useUserStore()
const canMarkRead = computed(() => userStore.permissions.some(permission => permission === '*:*:*' || permission === 'lab:notification:read'))
const errorMessage = ref('')
const loading = ref(false)
const unreadOnly = ref(false)
const messages = ref([])
const total = ref(0)
const detailOpen = ref(false)
const selected = ref()
const query = reactive({ pageNum: 1, pageSize: 10 })

async function loadMessages() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listNotifications({ ...query, unreadOnly: unreadOnly.value })
    messages.value = response.rows || []
    total.value = response.total || 0
    await notificationStore.refreshUnreadCount()
  } catch (error) {
    errorMessage.value = error?.message || '消息加载失败，请重试'
    messages.value = []
    total.value = 0
  } finally { loading.value = false }
}
function handleFilter() { query.pageNum = 1; void loadMessages() }
async function markRead(message) {
  if (!canMarkRead.value || message.readAt) return
  try {
    await markNotificationRead(message.id)
    message.readAt = new Date().toISOString()
    notificationStore.consumeOne()
    if (unreadOnly.value) await loadMessages()
  } catch (error) { errorMessage.value = error?.message || '标记已读失败' }
}
async function openMessage(message) {
  try {
    const response = await getNotification(message.id)
    selected.value = response.data
    detailOpen.value = true
    if (canMarkRead.value && !message.readAt) await markRead(message)
  } catch (error) { errorMessage.value = error?.message || '消息详情加载失败' }
}
function businessTarget(message) {
  if (!message?.businessId) return null
  if (message.businessType === 'SLA' && /^[1-9]\d{0,18}$/.test(String(message.businessId)) && userStore.permissions.some(permission => permission === '*:*:*' || permission === 'lab:sla:list')) return { path: '/lab/sla', query: { recordId: String(message.businessId) } }
  const names = { RESERVATION: 'LabReservationDetail', REPAIR_ORDER: 'LabRepairDetail', INSPECTION_TASK: 'LabInspectionTaskExecute', HAZARD: 'LabHazardDetail', DEVICE: 'LabDeviceDetail' }
  const name = names[message.businessType]
  return name && router.hasRoute(name) ? { name, params: { id: message.businessId } } : null
}
function openBusiness(message) {
  const target = businessTarget(message)
  if (target) router.push(target)
}
function typeText(value) {
  const categories = { SLA: '业务时效提醒', RESERVATION: '预约通知', REPAIR_ORDER: '维修通知', INSPECTION_TASK: '巡检通知', HAZARD: '隐患通知', DEVICE: '设备通知' }
  return Object.entries(categories).find(([key]) => value?.startsWith(key))?.[1] || '业务通知'
}
loadMessages()
</script>

<style scoped>
.header, .message-title, .meta { display: flex; align-items: center; justify-content: space-between; }
.title { font-size: 18px; font-weight: 600; }.badge { margin-left: 16px; }
.message-item { display: flex; gap: 20px; padding: 18px 12px; border-bottom: 1px solid var(--el-border-color-lighter); }
.message-item.unread { background: var(--el-color-primary-light-9); }.message-main { flex: 1; min-width: 0; cursor: pointer; padding: 0; border: 0; background: transparent; text-align: left; color: inherit; line-height: 1.6; }
.message-title { justify-content: flex-start; gap: 10px; font-weight: 600; }.message-main p { color: var(--el-text-color-regular); }
.meta { justify-content: flex-start; gap: 20px; color: var(--el-text-color-secondary); font-size: 12px; }
.message-actions { display: flex; align-items: center; }.message-content { white-space: pre-wrap; line-height: 1.7; }
.message-main p { overflow-wrap: anywhere; }
@media (max-width: 767px) {
  .message-item { flex-direction: column; gap: 12px; padding: 16px 0; }
  .message-actions { justify-content: flex-end; flex-wrap: wrap; }
  .header, .meta { flex-wrap: wrap; gap: 8px; }
}
</style>
