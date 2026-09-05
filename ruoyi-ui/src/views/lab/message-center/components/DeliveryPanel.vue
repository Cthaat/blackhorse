<template>
  <section aria-label="投递记录">
    <p class="message-note">此页仅显示投递元数据；消息正文和收件人由个人收件箱保护。自动投递最多尝试 5 次。</p>
    <div class="message-toolbar">
      <el-select v-model="query.status" clearable placeholder="全部状态" aria-label="投递状态" @change="filter"><el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" /></el-select>
      <el-input v-model="query.eventType" placeholder="事件类型" maxlength="32" clearable aria-label="事件类型" @keyup.enter="filter" />
      <el-button :loading="loading" @click="filter">查询</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button link @click="load">重新加载</el-button></el-alert>
    <el-table v-loading="loading" :data="rows" empty-text="暂无符合筛选条件的投递记录">
      <el-table-column prop="id" label="编号" min-width="85" />
      <el-table-column prop="eventType" label="事件" min-width="220" />
      <el-table-column label="状态" min-width="110"><template #default="{ row }"><el-tag :type="row.status === 'MANUAL_REQUIRED' ? 'danger' : row.status === 'DELIVERED' ? 'success' : 'info'">{{ statuses[row.status] || row.status }}</el-tag></template></el-table-column>
      <el-table-column prop="attemptCount" label="尝试次数" width="90" />
      <el-table-column prop="errorCode" label="错误码" min-width="160" />
      <el-table-column prop="nextRetryAt" label="下次重试" min-width="180" />
      <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="selectedId = row.id; detailOpen = true">详情</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-if="total" v-model:current-page="query.pageNum" :page-size="query.pageSize" :total="total" layout="prev, pager, next" @current-change="load" />
    <DeliveryDetail v-model="detailOpen" :delivery-id="selectedId" @replayed="load" />
  </section>
</template>
<script setup>
import { reactive, ref, onMounted, onBeforeUnmount } from 'vue'
import { listDeliveries } from '@/api/lab/messageCenter'
import DeliveryDetail from './DeliveryDetail.vue'
import { statuses } from './helpers'
const query = reactive({ status: '', eventType: '', pageNum: 1, pageSize: 10 })
const rows = ref([]), total = ref(0), loading = ref(false), error = ref(''), detailOpen = ref(false), selectedId = ref(null)
let sequence = 0
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''
  try { const result = await listDeliveries({ ...query }); if (current === sequence) { rows.value = result.rows || []; total.value = result.total || 0 } }
  catch (failure) { if (current === sequence) { error.value = failure?.message || '投递记录加载失败'; rows.value = []; total.value = 0 } }
  finally { if (current === sequence) loading.value = false }
}
function filter() { query.pageNum = 1; load() }
onMounted(load)
onBeforeUnmount(() => sequence++)
</script>
