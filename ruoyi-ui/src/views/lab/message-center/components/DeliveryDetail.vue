<template>
  <el-dialog :model-value="modelValue" title="投递详情" width="min(900px, 94vw)" append-to-body @update:model-value="$emit('update:modelValue', $event)">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else-if="detail">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="编号 / 状态">{{ detail.delivery.id }} / {{ statuses[detail.delivery.status] }}</el-descriptions-item>
        <el-descriptions-item label="来源事实">{{ detail.delivery.sourceType }} #{{ detail.delivery.sourceId }} · 事件版本 {{ detail.delivery.eventVersion }}</el-descriptions-item>
        <el-descriptions-item label="模板快照版本">{{ detail.delivery.templateVersion }}</el-descriptions-item>
        <el-descriptions-item label="关联号">{{ detail.delivery.traceId || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="错误码">{{ detail.delivery.errorCode || '无' }}</el-descriptions-item>
        <el-descriptions-item label="已尝试 / 下次重试">{{ detail.delivery.attemptCount }} 次 / {{ detail.delivery.nextRetryAt || '无计划' }}</el-descriptions-item>
      </el-descriptions>
      <h3>最近 100 条投递与重放记录</h3>
      <el-table :data="detail.attempts" empty-text="尚无尝试记录">
        <el-table-column prop="action" label="动作" min-width="100" />
        <el-table-column prop="attemptNumber" label="次数" width="65" />
        <el-table-column prop="result" label="结果" min-width="130" />
        <el-table-column prop="reason" label="操作原因" min-width="180" />
        <el-table-column prop="operatorId" label="操作者编号" min-width="100" />
        <el-table-column prop="createTime" label="时间" min-width="180" />
      </el-table>
      <el-form v-if="canSubmit" label-position="top" @submit.prevent="replay">
        <el-alert v-if="earlyRetry" title="提前进入统一投递队列，保留已尝试次数和原始快照。" type="info" :closable="false" />
        <el-form-item label="操作原因（必填，最多200字）"><el-input v-model="reason" type="textarea" maxlength="200" show-word-limit :rows="3" /></el-form-item>
        <el-button type="primary" :loading="busy" :disabled="busy || !reason.trim()" @click="replay">{{ earlyRetry ? '提前重试' : '按原快照重新投递' }}</el-button>
      </el-form>
    </template>
    <template #footer><el-button @click="$emit('update:modelValue', false)">关闭</el-button></template>
  </el-dialog>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import { getDelivery, replayDelivery, retryDeliveryNow } from '@/api/lab/messageCenter'
import { statuses } from './helpers'
const props = defineProps({ modelValue: Boolean, deliveryId: [String, Number] })
const emit = defineEmits(['update:modelValue', 'replayed'])
const canReplay = checkPermi(['lab:delivery:retry'])
const detail = ref(null), error = ref(''), reason = ref(''), loading = ref(false), busy = ref(false)
const earlyRetry = computed(() => detail.value?.delivery.status === 'RETRY_WAIT' && detail.value.delivery.attemptCount < 5)
const canSubmit = computed(() => canReplay && (earlyRetry.value || detail.value?.delivery.status === 'MANUAL_REQUIRED'))
let sequence = 0
async function load() {
  const current = ++sequence; loading.value = true; detail.value = null; error.value = ''
  try { const result = await getDelivery(props.deliveryId); if (sequence === current) detail.value = result.data }
  catch (failure) { if (sequence === current) error.value = failure?.message || '详情加载失败' }
  finally { if (sequence === current) loading.value = false }
}
async function replay() {
  if (busy.value || !canSubmit.value || !reason.value.trim()) return
  const id = detail.value.delivery.id, submittedReason = reason.value.trim(), current = sequence
  const submit = earlyRetry.value ? retryDeliveryNow : replayDelivery
  busy.value = true; error.value = ''
  try {
    await submit(id, submittedReason)
    ElMessage.success('已进入统一投递队列'); emit('replayed')
    if (sequence === current) { reason.value = ''; await load() }
  }
  catch (failure) { if (sequence === current) error.value = failure?.message || '重试失败' }
  finally { busy.value = false }
}
watch(() => [props.modelValue, props.deliveryId], () => { if (props.modelValue && props.deliveryId) { reason.value = ''; load() } else sequence++ }, { immediate: true })
</script>
