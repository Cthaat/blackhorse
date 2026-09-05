<template>
  <section aria-label="限制详情">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="实验室">{{ row.laboratoryName }}（{{ row.laboratoryId }}）</el-descriptions-item>
      <el-descriptions-item label="用户">{{ row.userName }}（{{ row.userId }}）</el-descriptions-item>
      <el-descriptions-item label="状态">{{ restrictionState(row) }}</el-descriptions-item>
      <el-descriptions-item label="限制原因">{{ row.reason }}</el-descriptions-item>
      <el-descriptions-item label="生效区间">{{ parseTime(row.startsAt) }} 至 {{ parseTime(row.endsAt) }}</el-descriptions-item>
      <el-descriptions-item label="来源预约">{{ row.sourceReservationId || '手工登记' }}</el-descriptions-item>
      <el-descriptions-item label="规则版本">{{ row.ruleVersionId || '非自动规则' }} · {{ row.ruleSnapshot || '无' }}</el-descriptions-item>
      <el-descriptions-item v-if="row.revokedAt" label="解除记录">{{ parseTime(row.revokedAt) }} · {{ row.revokeReason }}</el-descriptions-item>
    </el-descriptions>
    <section class="section" aria-label="申诉">
      <h3>申诉与审核</h3>
      <template v-if="row.appeal">
        <p>状态：{{ appealStates[row.appeal.status] || row.appeal.status }}</p>
        <p class="reason">{{ row.appeal.reason }}</p>
        <p v-if="row.appeal.reviewReason">审核结论：{{ row.appeal.reviewReason }} · {{ parseTime(row.appeal.reviewedAt) }}</p>
        <el-button v-if="canReview && !owner && row.appeal.status === 'PENDING'" type="primary" @click="$emit('action', 'review')">审核申诉</el-button>
      </template>
      <template v-else>
        <p>尚未提交申诉。可先上传证据，提交后证据冻结，每条限制仅可申诉一次。</p>
        <el-button v-if="owner && canAppeal" type="primary" :disabled="evidenceBusy" @click="$emit('action', 'appeal')">{{ evidenceBusy ? '请等待证据处理完成' : '填写申诉' }}</el-button>
      </template>
    </section>
    <AttachmentPanel class="section" business-type="RESTRICTION" :business-id="String(row.id)" :can-manage="owner && !row.appeal && canAppeal" @busy="evidenceBusy = $event" />
    <section class="section" aria-label="限制处理历史">
      <h3>处理历史</h3>
      <el-table :data="row.history || []" empty-text="暂无历史记录">
        <el-table-column label="时间" min-width="180"><template #default="{row: event}">{{ parseTime(event.createTime) }}</template></el-table-column>
        <el-table-column prop="operatorName" label="操作者" min-width="130" />
        <el-table-column prop="toStatus" label="处理状态" min-width="120" />
        <el-table-column prop="reason" label="原因" min-width="220" />
        <el-table-column prop="traceId" label="关联号" min-width="260" />
      </el-table>
    </section>
  </section>
</template>
<script setup>
import { parseTime } from '@/utils/ruoyi'
import { ref } from 'vue'
import AttachmentPanel from '@/components/lab/AttachmentPanel.vue'
import { restrictionState } from '../presentation'
defineProps({ row: { type: Object, required: true }, owner: Boolean, canAppeal: Boolean, canReview: Boolean })
defineEmits(['action'])
const appealStates = { PENDING: '待审核', APPROVED: '申诉通过', REJECTED: '申诉驳回' }
const evidenceBusy = ref(false)
</script>
<style scoped>
.section { margin-top: 24px; }
.reason { white-space: pre-wrap; overflow-wrap: anywhere; }
p { line-height: 1.8; }
</style>
