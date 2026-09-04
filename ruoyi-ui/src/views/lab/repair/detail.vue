<template>
  <el-drawer
    :model-value="modelValue"
    title="维修工单详情"
    size="min(760px, 96vw)"
    append-to-body
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <div v-loading="loading" class="detail-body">
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="mb16">
        <template #default><el-button link type="primary" @click="loadDetail">重新加载</el-button></template>
      </el-alert>
      <el-empty v-else-if="!loading && !detail" description="未找到维修工单" />
      <template v-else-if="detail">
        <div class="detail-heading">
          <div>
            <h2>{{ detail.order.repairNo || '维修工单' }}</h2>
            <el-text type="info">工单 ID：{{ detail.order.id }}</el-text>
          </div>
          <el-tag :type="statusType(detail.order.status)" effect="light">{{ statusLabel(detail.order.status) }}</el-tag>
        </div>

        <el-descriptions :column="2" border class="mt16">
          <el-descriptions-item label="设备">{{ detail.order.assetNo }} · {{ detail.order.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="设备 ID">{{ detail.order.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceLabel(detail.order.sourceType) }}</el-descriptions-item>
          <el-descriptions-item label="来源 ID">{{ detail.order.sourceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报告人 ID">{{ detail.order.reporterId }}</el-descriptions-item>
          <el-descriptions-item label="维修人 ID">{{ detail.order.assigneeId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="故障描述" :span="2">{{ detail.order.faultDescription || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分派时间">{{ formatDateTime(detail.order.assignedAt) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatDateTime(detail.order.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="维修结果" :span="2">{{ detail.order.repairResult || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交结果时间">{{ formatDateTime(detail.order.resultSubmittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="验收人 ID">{{ detail.order.acceptedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="验收结论">{{ acceptanceLabel(detail.order.acceptanceResult) }}</el-descriptions-item>
          <el-descriptions-item label="验收时间">{{ formatDateTime(detail.order.acceptedAt) }}</el-descriptions-item>
          <el-descriptions-item label="验收原因" :span="2">{{ detail.order.acceptanceReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detail.order.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.order.version }}</el-descriptions-item>
        </el-descriptions>

        <section class="detail-section">
          <h3>处理时间线</h3>
          <RepairTimeline :history="detail.statusHistory" />
        </section>

        <section class="detail-section">
          <h3>相关附件</h3>
          <el-empty v-if="!detail.attachments?.length" description="暂无附件" :image-size="64" />
          <el-table v-else :data="detail.attachments" size="small" row-key="id">
            <el-table-column label="文件名" prop="originalName" min-width="190" show-overflow-tooltip />
            <el-table-column label="类型" prop="mimeType" min-width="130" show-overflow-tooltip />
            <el-table-column label="大小" width="100"><template #default="scope">{{ formatSize(scope.row.size) }}</template></el-table-column>
            <el-table-column label="上传时间" min-width="164"><template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template></el-table-column>
          </el-table>
        </section>
      </template>
    </div>
  </el-drawer>
</template>

<script setup name="LabRepairDetail">
import RepairTimeline from '@/components/lab/RepairTimeline.vue'
import { getRepairOrder } from '@/api/lab/repair'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  repairId: { type: [String, Number], default: '' }
})

const emit = defineEmits(['update:modelValue'])
const loading = ref(false)
const errorMessage = ref('')
const detail = ref()

async function loadDetail() {
  if (!props.repairId) return
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getRepairOrder(String(props.repairId))
    detail.value = response.data
  } catch (error) {
    detail.value = undefined
    errorMessage.value = error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? '维修详情加载失败'
  } finally {
    loading.value = false
  }
}

function statusLabel(status) {
  return {
    WAIT_ASSIGN: '待分派', WAIT_REPAIR: '待维修', IN_PROGRESS: '维修中',
    WAIT_ACCEPTANCE: '待验收', CLOSED: '已关闭'
  }[status] ?? status ?? '-'
}

function statusType(status) {
  return {
    WAIT_ASSIGN: 'warning', WAIT_REPAIR: 'warning', IN_PROGRESS: 'primary',
    WAIT_ACCEPTANCE: 'warning', CLOSED: 'success'
  }[status] ?? 'info'
}

function sourceLabel(source) {
  return { ACTIVE_REPORT: '主动报修', ABNORMAL_RETURN: '异常归还' }[source] ?? source ?? '-'
}

function acceptanceLabel(result) {
  return { PASSED: '验收通过', REJECTED: '验收退回' }[result] ?? result ?? '-'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

function formatSize(value) {
  const size = typeof value === 'number' ? value : parseFloat(value)
  if (!Number.isFinite(size)) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

watch(() => [props.modelValue, String(props.repairId ?? '')], ([visible]) => {
  if (visible) loadDetail()
}, { immediate: true })
</script>

<style scoped>
.detail-body {
  min-height: 260px;
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

.detail-section {
  margin-top: 24px;
}

.detail-section h3 {
  margin: 0 0 12px;
  color: var(--el-text-color-primary);
  font-size: 16px;
}
</style>
