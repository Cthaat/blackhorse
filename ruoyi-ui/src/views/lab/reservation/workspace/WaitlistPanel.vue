<template>
  <section aria-label="我的候补">
    <div class="workspace-toolbar">
      <el-select v-model="status" clearable placeholder="全部候补状态" aria-label="候补状态"><el-option v-for="(label, value) in labels" :key="value" :label="label" :value="value" /></el-select>
      <el-button :loading="loading" @click="load">刷新候补</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert title="仅显示本人的候补记录。邀请确认后生成待审批预约，并非直接批准。" type="info" :closable="false" />
    <el-table v-loading="loading" :data="rows" empty-text="暂无候补记录">
      <el-table-column label="时段" min-width="190"><template #default="{ row }">{{ timeText(row.startTime) }}<br />{{ timeText(row.endTime) }}</template></el-table-column>
      <el-table-column prop="purpose" label="用途" min-width="140" />
      <el-table-column label="状态 / 排位" min-width="140"><template #default="{ row }">{{ labels[row.status] || row.status }}<span v-if="row.status === 'WAITING'"> · 第 {{ row.position ?? '—' }} 位</span></template></el-table-column>
      <el-table-column label="邀请截止 / 剩余" min-width="190"><template #default="{ row }"><template v-if="row.offeredUntil">{{ timeText(row.offeredUntil) }}<br /><span v-if="row.status === 'OFFERED'">{{ countdown(row.offeredUntil, now) }}</span></template><span v-else>—</span></template></el-table-column>
      <el-table-column prop="reason" label="说明" min-width="140" />
      <el-table-column label="操作" min-width="160"><template #default="{ row }">
        <el-button v-if="row.status === 'OFFERED'" v-hasPermi="['lab:reservation:apply']" link type="primary" :disabled="!!action || Date.parse(row.offeredUntil) <= now" @click="command(row, 'confirm')">确认邀请</el-button>
        <el-button v-if="['WAITING', 'OFFERED'].includes(row.status)" v-hasPermi="['lab:reservation:cancel']" link type="danger" :disabled="!!action" @click="command(row, 'cancel')">取消候补</el-button>
        <el-button v-if="row.reservationId" link @click="emit('reservation', row.reservationId)">查看预约</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-if="total" v-model:current-page="page" :page-size="10" :total="total" layout="prev, pager, next" @current-change="load" />
  </section>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listWaitlist, waitlistCommand } from '@/api/lab/reservationWorkspace'
import { timeText, messageOf, countdown } from './helpers'
const props = defineProps({ deviceId: String })
const emit = defineEmits(['reservation', 'changed'])
const labels = { WAITING: '排队中', OFFERED: '待确认邀请', ACCEPTED: '已确认', CANCELLED: '已取消', EXPIRED: '已过期', INELIGIBLE: '条件不符' }
const rows = ref([]), total = ref(0), page = ref(1), status = ref(''), loading = ref(false), error = ref(''), action = ref(''), now = ref(Date.now())
let sequence = 0
const timer = setInterval(() => { now.value = Date.now() }, 1000)
async function load() {
  const current = ++sequence
  loading.value = true; error.value = ''; rows.value = []
  try {
    const response = await listWaitlist({ deviceId: props.deviceId || undefined, status: status.value || undefined, pageNum: page.value, pageSize: 10 })
    if (current === sequence) { rows.value = response.rows || []; total.value = response.total || 0 }
  } catch (failure) { if (current === sequence) { total.value = 0; error.value = messageOf(failure) } }
  finally { if (current === sequence) loading.value = false }
}
async function command(row, kind) {
  if (action.value) return
  action.value = row.id
  error.value = ''
  try {
    await ElMessageBox.confirm(kind === 'confirm' ? '确认邀请并生成待审批预约？' : '取消此候补？', '确认操作')
    await waitlistCommand(row.id, kind, row.version)
    ElMessage.success(kind === 'confirm' ? '邀请已确认，预约等待审批' : '候补已取消')
    await load(); emit('changed')
  } catch (failure) { if (failure !== 'cancel' && failure !== 'close') error.value = messageOf(failure) }
  finally { action.value = '' }
}
watch(() => [props.deviceId, status.value], () => { page.value = 1; load() }, { immediate: true })
onBeforeUnmount(() => { sequence++; clearInterval(timer) })
defineExpose({ refresh: load })
</script>
