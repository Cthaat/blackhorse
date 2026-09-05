<template>
  <div>
    <el-alert v-if="error && !visible" :title="error" type="error" :closable="false" />
    <el-table :data="rows" empty-text="暂无维护周期，启用计划到期后由后台生成">
      <el-table-column prop="id" label="周期编号" width="110" />
      <el-table-column label="设备" min-width="190"><template #default="{row}">{{ row.deviceName || '设备' }} #{{ row.deviceId }}</template></el-table-column>
      <el-table-column label="类型" width="130"><template #default="{row}">{{ kinds[row.kind] }}</template></el-table-column>
      <el-table-column label="到期时间" min-width="180"><template #default="{row}">{{ parseTime(row.dueAt) }}</template></el-table-column>
      <el-table-column label="状态" width="140"><template #default="{row}"><el-tag>{{ cycleStates[row.status] || row.status }}</el-tag></template></el-table-column>
      <el-table-column label="停用窗口" min-width="250"><template #default="{row}">{{ row.windowStart ? `${parseTime(row.windowStart)} → ${parseTime(row.windowEnd)}` : '尚未安排，不自动停机' }}</template></el-table-column>
      <el-table-column label="操作" min-width="250"><template #default="{row}">
        <el-button v-if="canSchedule && ['PLANNED','SCHEDULED'].includes(row.status)" link type="primary" @click="open(row, 'window')">安排窗口</el-button>
        <el-button v-if="canStart && row.status === 'SCHEDULED'" link type="warning" @click="open(row, 'start')">启动执行</el-button>
        <el-button v-if="row.repairId" link type="primary" @click="router.push(`/lab/repair/detail/${row.repairId}`)">处理／验收工单</el-button>
        <el-button v-if="row.reportAttachmentId" link type="success" :loading="reportBusy === String(row.reportAttachmentId)" @click="downloadReport(row)">验收报告 #{{ row.reportAttachmentId }}</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="mode === 'window' ? '安排停用窗口' : '启动维护执行'" width="min(820px,94vw)" append-to-body :show-close="!busy" :before-close="beforeClose" :close-on-click-modal="false" :close-on-press-escape="!busy">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <p v-if="selected">目标：{{ selected.deviceName || '设备' }} #{{ selected.deviceId }} · 周期 #{{ selected.id }} · {{ kinds[selected.kind] }}</p>
      <el-alert :title="mode === 'window' ? '有预约或候补占位冲突时不会生效，也不会自动取消预约。请明确处置冲突后重试。' : '当前不能有未归还使用记录或其他开放维修；只有到达停用窗口后才可启动。执行和验收复用维修工单。'" type="info" :closable="false" />
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item v-if="mode === 'window'" label="停用开始"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" :disabled="busy" /></el-form-item>
        <el-form-item v-if="mode === 'window'" label="停用结束"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" :disabled="busy" /></el-form-item>
        <el-form-item label="操作原因（必填）"><el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit :disabled="busy" /></el-form-item>
      </el-form>
      <el-table v-if="conflicts.length" :data="conflicts" aria-label="停用窗口冲突">
        <el-table-column label="冲突类型"><template #default="{row}">{{ conflictNames[row.kind] || row.kind }}</template></el-table-column>
        <el-table-column prop="id" label="业务编号" />
        <el-table-column label="开始" min-width="175"><template #default="{row}">{{ parseTime(row.startTime) }}</template></el-table-column>
        <el-table-column label="结束" min-width="175"><template #default="{row}">{{ parseTime(row.endTime) }}</template></el-table-column>
      </el-table>
      <template #footer><el-button :disabled="busy" @click="visible = false">关闭</el-button><el-button type="primary" :loading="busy" @click="submit">确认{{ mode === 'window' ? '安排' : '启动' }}</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveAs } from 'file-saver'
import { listAttachment, downloadAttachment } from '@/api/lab/attachment'
import { checkPermi } from '@/utils/permission'
import { parseTime } from '@/utils/ruoyi'
import { maintenanceCycleCommand } from '@/api/lab/maintenance'
import { kinds, cycleStates, reasonText, windowPayload } from './presentation'
defineProps({ rows: { type: Array, default: () => [] } })
const emit = defineEmits(['changed'])
const router = useRouter(), canSchedule = checkPermi(['lab:maintenance:schedule']), canStart = checkPermi(['lab:maintenance:start'])
const conflictNames = { RESERVATION: '预约', WAITLIST: '候补确认占位', MAINTENANCE: '维护窗口' }
const selected = ref(null), mode = ref('window'), visible = ref(false), busy = ref(false), error = ref(''), conflicts = ref([])
const reportBusy = ref('')
const form = reactive({ startTime: '', endTime: '', reason: '', expectedVersion: 0 })
function open(row, action) {
  if (busy.value) return
  selected.value = { ...row }; mode.value = action; error.value = ''; conflicts.value = []
  Object.assign(form, { startTime: row.windowStart || '', endTime: row.windowEnd || '', reason: '', expectedVersion: row.version })
  visible.value = true
}
function beforeClose(done) { if (!busy.value) done() }
async function downloadReport(row) {
  if (reportBusy.value) return
  const reportId = String(row.reportAttachmentId), repairId = String(row.repairId)
  reportBusy.value = reportId; error.value = ''
  try {
    const files = await listAttachment('REPAIR_ORDER', repairId)
    const report = files.data.find(file => String(file.id) === reportId)
    if (!report) throw new Error('验收报告当前不可访问，请联系管理员')
    saveAs(await downloadAttachment(reportId), report.originalName)
  } catch (failure) { error.value = failure.message || '报告下载失败' }
  finally { reportBusy.value = '' }
}
async function submit() {
  if (busy.value) return
  error.value = ''; conflicts.value = []
  let payload
  try { payload = mode.value === 'window' ? windowPayload(form) : { expectedVersion: form.expectedVersion, reason: reasonText(form.reason) } }
  catch (failure) { error.value = failure.message; return }
  const id = selected.value.id, action = mode.value
  busy.value = true
  try {
    await ElMessageBox.confirm('请核对设备与停用安排，操作会保留追溯记录。是否继续？', '确认维护操作')
    const response = await maintenanceCycleCommand(id, action, payload)
    if (action === 'window' && response.data.scheduled === false) {
      conflicts.value = response.data.conflicts || []; error.value = '窗口存在冲突，尚未生效'; return
    }
    visible.value = false; emit('changed'); ElMessage.success(action === 'window' ? '停用窗口已安排' : '执行工单已建立，请进入维修工单继续处理')
  } catch (failure) { if (failure !== 'cancel' && failure !== 'close') error.value = failure.message || '操作失败，请刷新周期后重试' }
  finally { busy.value = false }
}
</script>
