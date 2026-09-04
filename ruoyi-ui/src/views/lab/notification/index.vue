<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <div><span class="title">消息中心</span><el-badge :value="notificationStore.unreadCount" :hidden="notificationStore.unreadCount === 0" class="badge" /></div>
          <el-switch v-model="unreadOnly" active-text="仅看未读" @change="handleFilter" />
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="!messages.length" description="暂无消息" />
      <div v-else class="message-list">
        <article v-for="message in messages" :key="message.id" :class="['message-item', { unread: !message.readAt }]">
          <div class="message-main" @click="openMessage(message)">
            <div class="message-title"><span>{{ message.title }}</span><el-tag v-if="!message.readAt" type="danger" size="small">未读</el-tag></div>
            <p>{{ message.content }}</p>
            <div class="meta"><span>{{ typeText(message.notificationType) }}</span><span>{{ parseTime(message.createTime) }}</span></div>
          </div>
          <div class="message-actions">
            <el-button v-if="!message.readAt" link type="primary" v-hasPermi="['lab:notification:read']" @click="markRead(message)">标为已读</el-button>
            <el-button link type="primary" @click="openBusiness(message)">查看业务</el-button>
          </div>
        </article>
      </div>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="loadMessages" />
    </el-card>

    <el-dialog v-model="detailOpen" title="消息详情" width="560px" append-to-body>
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item label="标题">{{ selected.title }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeText(selected.notificationType) }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ parseTime(selected.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="内容"><div class="message-content">{{ selected.content }}</div></el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button @click="detailOpen = false">关闭</el-button><el-button type="primary" @click="openBusiness(selected)">查看业务</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getNotification, listNotifications, markNotificationRead } from '@/api/lab/notification'
import useLabNotificationStore from '@/store/modules/labNotification'

const router = useRouter()
const notificationStore = useLabNotificationStore()
const loading = ref(false)
const unreadOnly = ref(false)
const messages = ref([])
const total = ref(0)
const detailOpen = ref(false)
const selected = ref()
const query = reactive({ pageNum: 1, pageSize: 10 })

async function loadMessages() {
  loading.value = true
  try {
    const response = await listNotifications({ ...query, unreadOnly: unreadOnly.value })
    messages.value = response.rows || []
    total.value = response.total || 0
    await notificationStore.refreshUnreadCount()
  } finally { loading.value = false }
}
function handleFilter() { query.pageNum = 1; void loadMessages() }
async function markRead(message) { await markNotificationRead(message.id); message.readAt = new Date().toISOString(); notificationStore.consumeOne(); if (unreadOnly.value) await loadMessages() }
async function openMessage(message) {
  const response = await getNotification(message.id)
  selected.value = response.data
  detailOpen.value = true
  if (!message.readAt) await markRead(message)
}
function openBusiness(message) {
  if (!message) return
  const paths = {
    RESERVATION: `/lab/reservation/detail/${message.businessId}`,
    REPAIR_ORDER: `/lab/repair/detail/${message.businessId}`,
    INSPECTION_TASK: `/lab/inspection/task/execute/${message.businessId}`,
    HAZARD: `/lab/hazard/detail/${message.businessId}`,
    DEVICE: `/lab/device/detail/${message.businessId}`
  }
  const path = paths[message.businessType]
  if (path) router.push(path)
}
function typeText(value) { return value?.replaceAll('_', ' ') || '业务通知' }
loadMessages()
</script>

<style scoped>
.header, .message-title, .meta { display: flex; align-items: center; justify-content: space-between; }
.title { font-size: 18px; font-weight: 600; }.badge { margin-left: 16px; }
.message-item { display: flex; gap: 20px; padding: 18px 12px; border-bottom: 1px solid var(--el-border-color-lighter); }
.message-item.unread { background: var(--el-color-primary-light-9); }.message-main { flex: 1; min-width: 0; cursor: pointer; }
.message-title { justify-content: flex-start; gap: 10px; font-weight: 600; }.message-main p { color: var(--el-text-color-regular); }
.meta { justify-content: flex-start; gap: 20px; color: var(--el-text-color-secondary); font-size: 12px; }
.message-actions { display: flex; align-items: center; }.message-content { white-space: pre-wrap; line-height: 1.7; }
</style>
