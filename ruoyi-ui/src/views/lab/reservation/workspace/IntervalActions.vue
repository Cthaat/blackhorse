<template>
  <section aria-label="预约时段试算与候补">
    <h3>选择预约时段</h3>
    <el-alert title="试算仅检查时间规则与占用冲突；正式提交仍需资格、安全检查和审批。" type="info" :closable="false" />
    <el-form label-position="top" class="workspace-table" :disabled="busy">
      <el-form-item label="预约时段（北京时间）"><el-date-picker v-model="interval" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间" /></el-form-item>
      <el-form-item label="用途"><el-input v-model="purpose" type="textarea" maxlength="200" show-word-limit /></el-form-item>
      <el-form-item label="备注"><el-input v-model="remark" maxlength="500" /></el-form-item>
      <el-form-item v-if="draft" label="试算规则"><el-checkbox v-model="useDraft">使用所选草稿：{{ draft.definition.name }}（v{{ draft.versionNumber }}）</el-checkbox></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-alert v-if="result" :title="result.message" :type="result.allowed ? 'success' : 'warning'" :closable="false" />
    <div class="workspace-toolbar">
      <el-button :loading="busy" @click="runSimulation">试算所选时段</el-button>
      <el-button v-hasPermi="['lab:reservation:apply']" type="primary" :disabled="busy" @click="apply">填写正式申请</el-button>
      <el-button v-hasPermi="['lab:reservation:apply']" :loading="busy" @click="join">加入我的候补</el-button>
    </div>
    <p class="workspace-muted">候补不等于预约成功；收到邀请后须在期限内确认，确认后仍待审批。</p>
  </section>
</template>
<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { simulateRule, joinWaitlist } from '@/api/lab/reservationWorkspace'
import { createIdempotentSubmission } from '@/utils/lab/idempotency'
import { messageOf } from './helpers'
const props = defineProps({ deviceId: String, draft: Object })
const emit = defineEmits(['apply', 'joined'])
const interval = ref([]), purpose = ref(''), remark = ref(''), useDraft = ref(false)
const busy = ref(false), result = ref(null), error = ref('')
const submission = createIdempotentSubmission()
let sequence = 0
const payload = computed(() => ({ deviceId: props.deviceId, startTime: `${interval.value?.[0] || ''}+08:00`, endTime: `${interval.value?.[1] || ''}+08:00`, purpose: purpose.value.trim(), remark: remark.value.trim() || null }))
function valid() {
  error.value = ''
  if (!props.deviceId || interval.value?.length !== 2 || Date.parse(payload.value.endTime) <= Date.parse(payload.value.startTime) || !purpose.value.trim()) {
    error.value = '请选择设备、有效起止时间并填写用途'
    return false
  }
  return true
}
function apply() { if (valid()) emit('apply', { ...payload.value }) }
async function runSimulation() {
  if (busy.value || !valid()) return
  const current = ++sequence
  busy.value = true
  result.value = null
  try {
    const response = await simulateRule(payload.value, useDraft.value ? props.draft?.id : undefined)
    if (current === sequence) result.value = response.data
  } catch (failure) { if (current === sequence) error.value = messageOf(failure) }
  finally { busy.value = false }
}
async function join() {
  if (busy.value || !valid()) return
  const current = ++sequence
  const data = { ...payload.value }, key = submission.prepare(data)
  busy.value = true
  try {
    await joinWaitlist(data, key)
    submission.clear()
    if (current !== sequence) return
    ElMessage.success('候补已登记，请在“我的候补”查看状态')
    emit('joined')
  } catch (failure) {
    submission.handleFailure(failure)
    if (current === sequence) error.value = messageOf(failure)
  } finally { busy.value = false }
}
watch([payload, useDraft, () => props.draft?.id], () => { sequence++; result.value = null; error.value = ''; submission.update(payload.value) })
onBeforeUnmount(() => sequence++)
</script>
