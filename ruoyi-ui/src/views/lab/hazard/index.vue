<template>
  <div class="app-container">
    <el-form v-show="showSearch" :model="query" inline>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 170px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="级别">
        <el-select v-model="query.severity" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="责任人ID"><el-input v-model="query.ownerId" clearable placeholder="可选" /></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="Plus" v-hasPermi="['lab:hazard:add']" @click="openCreate">登记隐患</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="loadHazards" />
    </el-row>
    <el-table v-loading="loading" :data="hazards" row-key="id">
      <el-table-column label="隐患编号" prop="hazardNo" min-width="210" />
      <el-table-column label="目标" min-width="180"><template #default="{ row }">{{ targetText(row) }}</template></el-table-column>
      <el-table-column label="级别" width="100" align="center"><template #default="{ row }"><el-tag :type="severityType(row.severity)">{{ severityText(row.severity) }}</el-tag></template></el-table-column>
      <el-table-column label="责任人ID" prop="ownerId" min-width="140" />
      <el-table-column label="整改期限" width="190"><template #default="{ row }"><span :class="{ overdue: row.overdueFlag === '1' }">{{ parseTime(row.deadline) }}</span></template></el-table-column>
      <el-table-column label="状态" width="130" align="center"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="loadHazards" />

    <el-dialog v-model="dialogOpen" title="登记安全隐患" width="650px" append-to-body>
      <el-form ref="hazardFormRef" :model="form" :rules="rules" label-width="105px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="目标类型" prop="targetType"><el-select v-model="form.targetType"><el-option label="实验室" value="LABORATORY" /><el-option label="设备" value="DEVICE" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="目标ID" prop="targetId"><el-input v-model="form.targetId" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="严重级别" prop="severity"><el-select v-model="form.severity"><el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="责任人ID" prop="ownerId"><el-input v-model="form.ownerId" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="整改期限" prop="deadline"><el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选择未来时间" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="整改要求" prop="requirements"><el-input v-model="form.requirements" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="关联隐患ID"><el-input v-model="form.relatedHazardId" clearable placeholder="复发隐患可填写已销号记录ID" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialogOpen = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitCreate">提交</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { getCurrentInstance, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { createHazard, listHazards } from '@/api/lab/hazard'

const router = useRouter()
const { proxy } = getCurrentInstance()
const loading = ref(false)
const submitting = ref(false)
const showSearch = ref(true)
const dialogOpen = ref(false)
const hazardFormRef = ref()
const hazards = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, status: undefined, severity: undefined, ownerId: undefined })
const form = reactive(emptyForm())
const statusOptions = [
  { value: 'PENDING_RECTIFICATION', label: '待整改' }, { value: 'RECTIFYING', label: '整改中' },
  { value: 'PENDING_REVIEW', label: '待复核' }, { value: 'CLOSED', label: '已销号' }
]
const severityOptions = [
  { value: 'LOW', label: '低' }, { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' }, { value: 'MAJOR', label: '重大' }
]
const rules = {
  targetType: [{ required: true, message: '请选择目标类型', trigger: 'change' }],
  targetId: [{ required: true, message: '请输入目标ID', trigger: 'blur' }],
  severity: [{ required: true, message: '请选择严重级别', trigger: 'change' }],
  ownerId: [{ required: true, message: '请输入责任人ID', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择整改期限', trigger: 'change' }],
  requirements: [{ required: true, message: '请输入整改要求', trigger: 'blur' }]
}
function emptyForm() { return { targetType: 'DEVICE', targetId: '', severity: 'MEDIUM', ownerId: '', deadline: '', requirements: '', relatedHazardId: undefined } }
async function loadHazards() { loading.value = true; try { const response = await listHazards(query); hazards.value = response.rows || []; total.value = response.total || 0 } finally { loading.value = false } }
function handleQuery() { query.pageNum = 1; void loadHazards() }
function resetQuery() { Object.assign(query, { pageNum: 1, status: undefined, severity: undefined, ownerId: undefined }); void loadHazards() }
function openCreate() { Object.assign(form, emptyForm()); dialogOpen.value = true }
async function submitCreate() {
  await hazardFormRef.value.validate()
  submitting.value = true
  try {
    await createHazard({ ...form, relatedHazardId: form.relatedHazardId || null })
    proxy.$modal.msgSuccess('隐患登记成功')
    dialogOpen.value = false
    await loadHazards()
  } finally { submitting.value = false }
}
function openDetail(row) { router.push(`/lab/hazard/detail/${row.id}`) }
function targetText(row) { return `${row.targetType === 'DEVICE' ? '设备' : '实验室'} #${row.targetId}` }
function statusText(value) { return statusOptions.find(item => item.value === value)?.label || value }
function severityText(value) { return severityOptions.find(item => item.value === value)?.label || value }
function statusType(value) { return { PENDING_RECTIFICATION: 'warning', RECTIFYING: 'primary', PENDING_REVIEW: 'danger', CLOSED: 'success' }[value] || 'info' }
function severityType(value) { return { LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', MAJOR: 'danger' }[value] || 'info' }
loadHazards()
</script>

<style scoped>.overdue { color: var(--el-color-danger); font-weight: 600; }</style>
