<template>
  <div class="app-container" v-loading="loading">
    <el-page-header content="执行巡检" @back="router.back()" />
    <el-card v-if="task" class="task-card" shadow="never">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="任务编号">{{ task.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="实验室">{{ laboratoryLabel }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="task.status === 'COMPLETED' ? 'success' : 'primary'">{{ task.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="负责人">{{ assigneeLabel }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ parseTime(task.deadlineAt) }}</el-descriptions-item>
        <el-descriptions-item label="超期">{{ task.overdueFlag === '1' ? '是' : '否' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="task.status === 'PENDING'" class="task-actions">
        <el-button type="primary" :loading="submitting" v-hasPermi="['lab:inspection:task:execute']" @click="startTask">开始任务</el-button>
      </div>
    </el-card>

    <el-card v-for="(item, index) in items" :key="item.id" shadow="never" class="item-card">
      <template #header>
        <div class="item-title"><span>{{ index + 1 }}. {{ item.contentSnapshot }}</span><el-tag v-if="item.result" :type="item.result === 'PASS' ? 'success' : 'danger'">{{ item.result === 'PASS' ? '通过' : '不通过' }}</el-tag></div>
      </template>
      <el-form :model="drafts[item.id]" label-width="100px">
        <el-form-item label="检查结论">
          <el-radio-group v-model="drafts[item.id].result" :disabled="readonly">
            <el-radio value="PASS">通过</el-radio><el-radio value="FAIL">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="drafts[item.id].result === 'FAIL'">
          <el-form-item label="问题描述"><el-input v-model="drafts[item.id].description" type="textarea" :rows="3" maxlength="1000" show-word-limit :disabled="readonly" /></el-form-item>
          <el-row :gutter="12">
            <el-col :span="8"><el-form-item label="严重级别"><el-select v-model="drafts[item.id].severity" :disabled="readonly"><el-option label="一般" value="NORMAL" /><el-option label="重大" value="MAJOR" /></el-select></el-form-item></el-col>
            <el-col :span="8"><el-form-item label="目标类型"><el-select v-model="drafts[item.id].targetType" :disabled="readonly" @change="drafts[item.id].targetId = ''"><el-option label="实验室" value="LABORATORY" /><el-option label="设备" value="DEVICE" /></el-select></el-form-item></el-col>
            <el-col :span="8">
              <el-form-item label="问题对象">
                <el-select v-model="drafts[item.id].targetId" filterable :disabled="readonly" placeholder="请选择对象" style="width: 100%">
                  <el-option v-for="option in targetOptions(drafts[item.id])" :key="option.id" :label="option.label" :value="option.id" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <el-form-item v-if="!readonly">
          <el-button type="primary" plain :loading="savingId === item.id" @click="saveItem(item)">保存本项</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div v-if="task && task.status === 'IN_PROGRESS'" class="footer-actions">
      <el-button type="success" :loading="submitting" v-hasPermi="['lab:inspection:task:execute']" @click="completeTask">完成并提交巡检</el-button>
    </div>
    <el-card v-if="task" shadow="never" class="item-card">
      <StatusHistory object-type="INSPECTION_TASK" :object-id="String(route.params.id)" />
    </el-card>
  </div>
</template>

<script setup>
import { loadAllOptions } from '@/utils/labOptions'
import { computed, getCurrentInstance, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusHistory from '@/components/lab/StatusHistory.vue'
import {
  completeInspectionTask,
  getInspectionTask,
  listInspectionItems,
  recordInspectionItem,
  startInspectionTask
} from '@/api/lab/inspection'
import { listDevice } from '@/api/lab/device'
import { getLaboratory } from '@/api/lab/laboratory'
import { listLabUserOptions } from '@/api/lab/options'

const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()
const loading = ref(false)
const submitting = ref(false)
const savingId = ref()
const task = ref()
const items = ref([])
const drafts = reactive({})
const laboratory = ref()
const deviceOptions = ref([])
const userOptions = ref([])
const readonly = computed(() => task.value?.status === 'COMPLETED')
const laboratoryLabel = computed(() => laboratory.value
  ? `${laboratory.value.labCode} · ${laboratory.value.name}`
  : `实验室 ${task.value?.laboratoryId || '-'}`)
const assigneeLabel = computed(() => userOptions.value.find(item => item.id === String(task.value?.assigneeId))?.label
  || `用户 ${task.value?.assigneeId || '-'}`)

function targetOptions(draft) {
  if (draft.targetType === 'LABORATORY') {
    return task.value ? [{ id: String(task.value.laboratoryId), label: laboratoryLabel.value }] : []
  }
  return deviceOptions.value
}

async function loadContextOptions() {
  if (!task.value) return
  const results = await Promise.allSettled([
    getLaboratory(task.value.laboratoryId).then(response => { laboratory.value = response.data }),
    loadAllOptions(listDevice, { laboratoryId: task.value.laboratoryId, sortBy: 'assetNo', sortDirection: 'asc' }).then(response => {
      deviceOptions.value = (response.rows || []).map(item => ({ id: String(item.id), label: `${item.assetNo} · ${item.name}` }))
    }),
    listLabUserOptions({ roleKey: 'lab_safety_officer' }).then(response => {
      userOptions.value = (response.data || []).map(item => ({ id: String(item.id), label: `${item.displayName || item.userName}（${item.userName}）` }))
    })
  ])
  if (results.some(result => result.status === 'rejected')) proxy.$modal.msgWarning('部分巡检对象信息加载失败')
}

async function loadDetail() {
  loading.value = true
  try {
    const [taskResponse, itemResponse] = await Promise.all([
      getInspectionTask(route.params.id),
      listInspectionItems(route.params.id)
    ])
    task.value = taskResponse.data
    items.value = itemResponse.data || []
    items.value.forEach(item => {
      drafts[item.id] = {
        result: item.result || 'PASS',
        description: item.description || '',
        severity: item.severity || 'NORMAL',
        targetType: item.targetType || 'DEVICE',
        targetId: item.targetId || '',
        version: item.version
      }
    })
    await loadContextOptions()
  } finally { loading.value = false }
}

async function startTask() {
  submitting.value = true
  try {
    await startInspectionTask(route.params.id)
    proxy.$modal.msgSuccess('任务已开始')
    await loadDetail()
  } finally { submitting.value = false }
}

async function saveItem(item) {
  const draft = drafts[item.id]
  if (draft.result === 'FAIL' && (!draft.description.trim() || !draft.targetId)) {
    proxy.$modal.msgWarning('不通过时必须填写问题描述并选择问题对象')
    return
  }
  savingId.value = item.id
  try {
    const data = draft.result === 'PASS'
      ? { result: 'PASS', description: null, severity: null, targetType: null, targetId: null, version: draft.version }
      : { ...draft }
    await recordInspectionItem(route.params.id, item.id, data)
    proxy.$modal.msgSuccess('检查项已保存')
    await loadDetail()
  } finally { savingId.value = undefined }
}

async function completeTask() {
  if (items.value.some(item => !item.result)) {
    proxy.$modal.msgWarning('请先保存全部检查项')
    return
  }
  await proxy.$modal.confirm('完成后不可继续修改，确认提交吗？')
  submitting.value = true
  try {
    await completeInspectionTask(route.params.id)
    proxy.$modal.msgSuccess('巡检任务已完成')
    await loadDetail()
  } finally { submitting.value = false }
}

loadDetail()
</script>

<style scoped>
.task-card { margin-top: 18px; }
.task-actions, .footer-actions { display: flex; justify-content: flex-end; margin-top: 16px; }
.item-card { margin-top: 14px; }
.item-title { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.footer-actions { position: sticky; bottom: 12px; padding: 12px; background: var(--el-bg-color); border-radius: 6px; box-shadow: var(--el-box-shadow-light); }
</style>
