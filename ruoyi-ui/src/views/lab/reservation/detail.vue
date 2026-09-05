<template>
  <el-drawer
    :model-value="modelValue"
    title="预约详情"
    size="min(680px, 94vw)"
    append-to-body
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <div v-loading="loading" class="detail-body">
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
      >
        <template #default>
          <el-button link type="primary" @click="loadDetail">重新加载</el-button>
        </template>
      </el-alert>
      <el-empty v-else-if="!loading && !detail" description="未找到预约记录" />
      <template v-else-if="detail">
        <div class="detail-heading">
          <div>
            <h2>{{ detail.reservationNo || '预约记录' }}</h2>
            <el-text type="info">预约 ID：{{ detail.id }}</el-text>
          </div>
          <el-tag :type="statusType(detail.status)" effect="light">{{ statusLabel(detail.status) }}</el-tag>
        </div>
        <lab-descriptions :column="2" border class="mt16">
          <el-descriptions-item label="设备">{{ optionLabel(deviceOptions, detail.deviceId, '设备') }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ optionLabel(applicantOptions, detail.applicantId, '用户') }}</el-descriptions-item>
          <el-descriptions-item label="提交方式" :span="2">{{ detail.submitterId && detail.submitterId !== detail.applicantId ? `管理员代办（提交人 ${detail.submitterId}）` : '本人申请' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatDateTime(detail.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatDateTime(detail.endTime) }}</el-descriptions-item>
          <el-descriptions-item label="预约用途" :span="2">{{ detail.purpose || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批人">{{ detail.approvalBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ formatDateTime(detail.approvalTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批原因" :span="2">{{ detail.approvalReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="取消时间">{{ formatDateTime(detail.cancelTime) }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item label="取消原因" :span="2">{{ detail.cancelReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ formatDateTime(detail.createTime) }}</el-descriptions-item>
        </lab-descriptions>
      </template>
    </div>
  </el-drawer>
</template>

<script setup name="LabReservationDetail">
import { getReservation } from '@/api/lab/reservation'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  reservationId: { type: [String, Number], default: '' },
  deviceOptions: { type: Array, default: () => [] },
  applicantOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])
const loading = ref(false)
const errorMessage = ref('')
const detail = ref()

function optionLabel(options, id, prefix) {
  return options.find(item => item.id === String(id))?.label || `${prefix} ${id}`
}

async function loadDetail() {
  if (!props.reservationId) return
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getReservation(String(props.reservationId))
    detail.value = response.data
  } catch (error) {
    detail.value = undefined
    errorMessage.value = error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? '预约详情加载失败'
  } finally {
    loading.value = false
  }
}

function statusLabel(status) {
  return {
    PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '已取消',
    EXPIRED: '已过期', NO_SHOW: '已爽约', CHECKED_OUT: '使用中', COMPLETED: '已完成'
  }[status] ?? status ?? '-'
}

function statusType(status) {
  return {
    PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info',
    EXPIRED: 'info', NO_SHOW: 'danger', CHECKED_OUT: 'primary', COMPLETED: 'success'
  }[status] ?? 'info'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

watch(() => [props.modelValue, String(props.reservationId ?? '')], ([visible]) => {
  if (visible) loadDetail()
}, { immediate: true })
</script>

<style scoped>
.detail-body {
  min-height: 220px;
}

.detail-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail-heading h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

@media (max-width: 640px) {
  :deep(.el-descriptions__body .el-descriptions__table) {
    table-layout: fixed;
  }
}
</style>
