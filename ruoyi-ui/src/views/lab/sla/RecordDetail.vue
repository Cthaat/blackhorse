<template>
  <section>
    <h3>{{ record.title || `${types[record.businessType]} #${record.objectId}` }}</h3>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="record.pausedAt" :title="`处理计时已暂停：${record.pauseReason || '已记录原因'}。响应期限和设备安全阻断不受影响。`" type="warning" :closable="false" />
    <el-descriptions :column="1" border>
      <el-descriptions-item label="当前状态">{{ states[record.state] || record.state }}</el-descriptions-item>
      <el-descriptions-item label="责任人">{{ record.ownerName || `用户 #${record.ownerId}` }}</el-descriptions-item>
      <el-descriptions-item label="规则快照">版本 #{{ record.ruleVersionId }} · {{ risks[record.risk] }} · 响应 {{ record.responseHours }} 小时 / 处理 {{ record.processingHours }} 小时</el-descriptions-item>
      <el-descriptions-item label="接入计时">{{ parseTime(record.openedAt) }}</el-descriptions-item>
      <el-descriptions-item label="响应截止">{{ parseTime(record.responseDueAt) }} · 已响应：{{ parseTime(record.respondedAt) || '尚未响应' }}</el-descriptions-item>
      <el-descriptions-item label="处理截止">{{ parseTime(record.processingDueAt) }} · 已暂停累计 {{ record.totalPausedSeconds || 0 }} 秒</el-descriptions-item>
      <el-descriptions-item label="剩余时长（本次刷新）">响应：{{ remainingTime(record, 'RESPONSE') }}；处理：{{ remainingTime(record, 'PROCESSING') }}</el-descriptions-item>
      <el-descriptions-item label="开始 / 提交完成">{{ parseTime(record.startedAt) || '未开始' }} / {{ parseTime(record.completedAt) || '未提交' }}</el-descriptions-item>
      <el-descriptions-item label="关闭时间">{{ parseTime(record.closedAt) || '业务尚未关闭' }}</el-descriptions-item>
    </el-descriptions>
    <div class="sla-actions"><el-button v-if="target" @click="router.push(target)">打开关联业务</el-button><el-button v-if="record.canManage && !record.closedAt && !record.completedAt" type="warning" :loading="busy" @click="changeClock">{{ record.pausedAt ? '恢复处理计时' : '暂停处理计时' }}</el-button></div>
    <h3>计时与操作记录</h3>
    <el-table :data="history" empty-text="暂无额外操作"><el-table-column label="动作"><template #default="{row}">{{ actionLabels[row.action] || row.action }}</template></el-table-column><el-table-column prop="reason" label="原因" min-width="220" /><el-table-column prop="operatorId" label="操作人编号" /><el-table-column label="时间" min-width="180"><template #default="{row}">{{ parseTime(row.createdAt) }}</template></el-table-column></el-table>
    <h3>提醒与升级事实</h3>
    <el-table :data="alerts" empty-text="尚未触发提醒阶段"><el-table-column label="计时阶段"><template #default="{row}">{{ phases[row.phase] }}</template></el-table-column><el-table-column label="提醒"><template #default="{row}">{{ stages[row.stage] }}</template></el-table-column><el-table-column label="登记时间"><template #default="{row}">{{ parseTime(row.createdAt) }}</template></el-table-column></el-table>
  </section>
</template>
<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { parseTime } from '@/utils/ruoyi'
import { checkPermi } from '@/utils/permission'
import { slaCommand } from '@/api/lab/sla'
import { types, risks, states, stages, phases, businessTarget, remainingTime } from './presentation'
const props = defineProps({ record: { type: Object, required: true }, history: { type: Array, default: () => [] }, alerts: { type: Array, default: () => [] } })
const emit = defineEmits(['changed'])
const actionLabels = { OPENED: '开始计时', RESPONDED: '已响应', STARTED: '开始处理', COMPLETED: '提交完成', CLOSED: '业务关闭', REOPENED: '退回处理', PAUSED: '暂停处理计时', PAUSE_ENDED: '暂停结束', RESUMED: '恢复处理计时', OWNER_CHANGED: '责任人变更' }
const router = useRouter(), target = computed(() => businessTarget(props.record, checkPermi(['lab:maintenance:list']))), busy = ref(false), error = ref('')
async function changeClock() {
  if (busy.value || !props.record.canManage || props.record.closedAt || props.record.completedAt) return
  const id = props.record.id, expectedVersion = props.record.version, action = props.record.pausedAt ? 'resume' : 'pause'
  busy.value = true; error.value = ''
  try {
    const { value } = await ElMessageBox.prompt('请说明原因；操作不解除设备安全阻断，不改变响应期限。', action === 'pause' ? '暂停处理计时' : '恢复处理计时', { inputValidator: value => !!value?.trim() && value.trim().length <= 500 || '请填写 1～500 字原因' })
    await slaCommand(id, action, { expectedVersion, reason: value.trim() }); ElMessage.success('计时状态已更新'); emit('changed', id)
  } catch (failure) { if (failure !== 'cancel' && failure !== 'close' && String(props.record.id) === String(id)) error.value = failure.message || '操作失败，请刷新记录' }
  finally { busy.value = false }
}
</script>
<style scoped>.sla-actions { display: flex; gap: 12px; flex-wrap: wrap; margin: 16px 0; }.el-alert { margin-bottom: 12px; }</style>
