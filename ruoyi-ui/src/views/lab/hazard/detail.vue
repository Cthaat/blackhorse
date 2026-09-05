<template>
  <div class="app-container lab-page" v-loading="loading">
    <el-page-header class="lab-detail-header" @back="router.back()"><template #content><h1>隐患整改详情</h1></template></el-page-header>
    <el-card v-if="hazard" shadow="never" class="detail-card">
      <template #header><div class="header"><span>{{ hazard.hazardNo }}</span><el-tag :type="statusType(hazard.status)">{{ statusText(hazard.status) }}</el-tag></div></template>
      <lab-descriptions :column="3" border>
        <el-descriptions-item label="隐患ID">{{ hazard.id }}</el-descriptions-item>
        <el-descriptions-item label="目标">{{ hazard.targetType === 'LABORATORY' ? '实验室' : '设备' }} #{{ hazard.targetId }}</el-descriptions-item>
        <el-descriptions-item label="级别">{{ { LOW: '低', MEDIUM: '中', HIGH: '高', MAJOR: '重大' }[hazard.severity] || hazard.severity }}</el-descriptions-item>
        <el-descriptions-item label="责任人ID">{{ hazard.ownerId }}</el-descriptions-item>
        <el-descriptions-item label="整改期限">{{ parseTime(hazard.deadline) }}</el-descriptions-item>
        <el-descriptions-item label="是否超期">{{ hazard.overdueFlag === '1' ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item label="整改要求" :span="3"><div class="pre-wrap">{{ hazard.requirements }}</div></el-descriptions-item>
      </lab-descriptions>
      <div class="commands">
        <el-button v-if="hazard.status === 'PENDING_RECTIFICATION'" type="primary" v-hasPermi="['lab:hazard:rectify']" :loading="submitting" @click="beginRectification">开始整改</el-button>
        <el-button v-if="hazard.status === 'RECTIFYING'" type="success" v-hasPermi="['lab:hazard:rectify']" @click="submitDialog = true">提交复核</el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="detail-card">
      <template #header>整改轮次</template>
      <RectificationTimeline :rounds="rounds" :loading="roundLoading">
        <template #actions="{ round }">
          <el-button v-if="hazard?.status === 'PENDING_REVIEW' && !round.reviewResult" type="primary" link v-hasPermi="['lab:hazard:review']" @click="openReview(round)">复核</el-button>
        </template>
      </RectificationTimeline>
    </el-card>

    <el-card v-if="rounds.length" shadow="never" class="detail-card">
      <template #header>整改附件</template>
      <el-collapse accordion>
        <el-collapse-item v-for="round in rounds" :key="round.id" :name="round.id" :title="`第 ${round.roundNo} 轮整改附件`">
          <AttachmentPanel
            business-type="RECTIFICATION"
            :business-id="String(round.id)"
            :can-manage="!round.reviewResult && String(round.submitterId) === String(userStore.id)"
          />
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <el-card shadow="never" class="detail-card">
      <StatusHistory object-type="HAZARD" :object-id="String(route.params.id)" />
    </el-card>

    <el-dialog v-model="submitDialog" title="提交整改说明" width="560px" append-to-body>
      <el-input v-model="rectificationDescription" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="说明整改措施和完成情况" />
      <template #footer><el-button @click="submitDialog = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitForReview">提交</el-button></template>
    </el-dialog>

    <el-dialog v-model="reviewDialog" title="整改复核" width="560px" append-to-body>
      <el-form label-width="85px">
        <el-form-item label="复核结论"><el-radio-group v-model="reviewForm.passed"><el-radio :value="true">通过销号</el-radio><el-radio :value="false">退回整改</el-radio></el-radio-group></el-form-item>
        <el-form-item label="复核意见"><el-input v-model="reviewForm.reason" type="textarea" :rows="5" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="reviewDialog = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitReview">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { getCurrentInstance, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AttachmentPanel from '@/components/lab/AttachmentPanel.vue'
import RectificationTimeline from '@/components/lab/RectificationTimeline.vue'
import StatusHistory from '@/components/lab/StatusHistory.vue'
import { getHazard, listRectifications, reviewRectification, startRectification, submitRectification } from '@/api/lab/hazard'
import useUserStore from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()
const userStore = useUserStore()
const loading = ref(false)
const roundLoading = ref(false)
const submitting = ref(false)
const hazard = ref()
const rounds = ref([])
const submitDialog = ref(false)
const reviewDialog = ref(false)
const rectificationDescription = ref('')
const reviewForm = reactive({ roundId: '', passed: true, reason: '' })

async function loadDetail() {
  loading.value = true
  roundLoading.value = true
  try {
    const [hazardResponse, roundsResponse] = await Promise.all([getHazard(route.params.id), listRectifications(route.params.id)])
    hazard.value = hazardResponse.data
    rounds.value = roundsResponse.data || []
  } finally { loading.value = false; roundLoading.value = false }
}
async function beginRectification() { await proxy.$modal.confirm('确认开始处理该隐患吗？'); submitting.value = true; try { await startRectification(route.params.id); proxy.$modal.msgSuccess('已开始整改'); await loadDetail() } finally { submitting.value = false } }
async function submitForReview() {
  if (!rectificationDescription.value.trim()) { proxy.$modal.msgWarning('请输入整改说明'); return }
  submitting.value = true
  try { await submitRectification(route.params.id, { description: rectificationDescription.value }); proxy.$modal.msgSuccess('已提交复核'); submitDialog.value = false; rectificationDescription.value = ''; await loadDetail() } finally { submitting.value = false }
}
function openReview(round) { Object.assign(reviewForm, { roundId: round.id, passed: true, reason: '' }); reviewDialog.value = true }
async function submitReview() {
  if (!reviewForm.reason.trim()) { proxy.$modal.msgWarning('请输入复核意见'); return }
  submitting.value = true
  try { await reviewRectification(route.params.id, reviewForm.roundId, { passed: reviewForm.passed, reason: reviewForm.reason }); proxy.$modal.msgSuccess('复核完成'); reviewDialog.value = false; await loadDetail() } finally { submitting.value = false }
}
function statusText(value) { return { PENDING_RECTIFICATION: '待整改', RECTIFYING: '整改中', PENDING_REVIEW: '待复核', CLOSED: '已销号' }[value] || value }
function statusType(value) { return { PENDING_RECTIFICATION: 'warning', RECTIFYING: 'primary', PENDING_REVIEW: 'danger', CLOSED: 'success' }[value] || 'info' }
loadDetail()
</script>

<style scoped>
.detail-card { margin-top: 18px; }
.header { display: flex; justify-content: space-between; font-weight: 600; }
.commands { display: flex; justify-content: flex-end; margin-top: 16px; }
.pre-wrap { white-space: pre-wrap; line-height: 1.7; }
</style>
