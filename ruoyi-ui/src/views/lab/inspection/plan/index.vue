<template>
  <div class="app-container">
    <el-alert v-if="optionsError" :title="optionsError" type="error" show-icon :closable="false" class="mb12" />
    <el-form v-show="showSearch" :model="query" inline>
      <el-form-item label="计划名称">
        <el-input v-model="query.keyword" clearable placeholder="计划名称" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="启用" value="ENABLED" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['lab:inspection:plan:add']" @click="openCreate">
          新增计划
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="loadPlans" />
    </el-row>

    <el-table v-loading="loading" :data="plans">
      <el-table-column label="计划名称" prop="planName" min-width="180" show-overflow-tooltip />
      <el-table-column label="实验室" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ laboratoryLabel(row.laboratoryId) }}</template>
      </el-table-column>
      <el-table-column label="频率" width="150">
        <template #default="{ row }">{{ frequencyText(row) }}</template>
      </el-table-column>
      <el-table-column label="执行时间" prop="executeTime" width="110" />
      <el-table-column label="负责人" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ userLabel(row.ownerId) }}</template>
      </el-table-column>
      <el-table-column label="下次执行" width="190">
        <template #default="{ row }">{{ parseTime(row.nextRunAt) || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : row.status === 'DISABLED' ? 'info' : 'warning'">
            {{ { DRAFT: '草稿', ENABLED: '启用', DISABLED: '停用' }[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-hasPermi="['lab:inspection:plan:edit']" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status !== 'ENABLED'"
            link type="success"
            v-hasPermi="['lab:inspection:plan:enable']"
            @click="changeStatus(row, true)"
          >启用</el-button>
          <el-button
            v-else link type="warning"
            v-hasPermi="['lab:inspection:plan:enable']"
            @click="changeStatus(row, false)"
          >停用</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="query.pageNum"
      v-model:limit="query.pageSize"
      @pagination="loadPlans"
    />

    <el-dialog v-model="dialog.open" :title="dialog.title" width="860px" append-to-body destroy-on-close>
      <el-form ref="planFormRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="计划名称" prop="planName"><el-input v-model="form.planName" maxlength="100" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="实验室" prop="laboratoryId">
              <el-select v-model="form.laboratoryId" filterable placeholder="请选择实验室" style="width: 100%">
                <el-option v-for="item in laboratoryOptions" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="频率" prop="frequencyType"><el-select v-model="form.frequencyType" @change="normalizeFrequency"><el-option label="每天" value="DAILY" /><el-option label="每周" value="WEEKLY" /><el-option label="每月" value="MONTHLY" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="间隔" prop="intervalValue"><el-input-number v-model="form.intervalValue" :min="1" :max="31" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="执行时间" prop="executeTime"><el-time-picker v-model="form.executeTime" value-format="HH:mm:ss" /></el-form-item></el-col>
          <el-col v-if="form.frequencyType === 'WEEKLY'" :span="8"><el-form-item label="星期" prop="dayOfWeek"><el-select v-model="form.dayOfWeek"><el-option v-for="day in 7" :key="day" :label="`星期${['一','二','三','四','五','六','日'][day - 1]}`" :value="day" /></el-select></el-form-item></el-col>
          <el-col v-if="form.frequencyType === 'MONTHLY'" :span="8"><el-form-item label="每月日期" prop="dayOfMonth"><el-input-number v-model="form.dayOfMonth" :min="1" :max="31" /></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item label="负责人" prop="ownerId">
              <el-select v-model="form.ownerId" filterable placeholder="请选择安全负责人" style="width: 100%">
                <el-option v-for="item in ownerOptions" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="截止偏移(分)" prop="deadlineOffsetMinutes"><el-input-number v-model="form.deadlineOffsetMinutes" :min="1" :max="43200" /></el-form-item></el-col>
        </el-row>

        <div class="section-title">
          <span>巡检项</span>
          <el-button type="primary" link icon="Plus" @click="addItem">添加检查项</el-button>
        </div>
        <el-table :data="form.items" border size="small">
          <el-table-column label="编码" width="150"><template #default="{ row }"><el-input v-model="row.itemCode" maxlength="32" /></template></el-table-column>
          <el-table-column label="检查内容"><template #default="{ row }"><el-input v-model="row.content" maxlength="500" /></template></el-table-column>
          <el-table-column label="排序" width="100"><template #default="{ row }"><el-input-number v-model="row.sortOrder" :min="0" controls-position="right" /></template></el-table-column>
          <el-table-column label="启用" width="80" align="center"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column>
          <el-table-column width="70"><template #default="{ $index }"><el-button link type="danger" @click="form.items.splice($index, 1)">删除</el-button></template></el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="dialog.open = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPlan">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { loadAllOptions } from '@/utils/labOptions'
import { getCurrentInstance, reactive, ref } from 'vue'
import {
  createInspectionPlan,
  disableInspectionPlan,
  enableInspectionPlan,
  getInspectionPlan,
  listInspectionPlans,
  updateInspectionPlan
} from '@/api/lab/inspection'
import { listLaboratory } from '@/api/lab/laboratory'
import { listLabUserOptions } from '@/api/lab/options'

const { proxy } = getCurrentInstance()
const loading = ref(false)
const submitting = ref(false)
const showSearch = ref(true)
const plans = ref([])
const total = ref(0)
const laboratoryOptions = ref([])
const ownerOptions = ref([])
const optionsError = ref('')
const planFormRef = ref()
const query = reactive({ pageNum: 1, pageSize: 10, keyword: undefined, status: undefined })
const dialog = reactive({ open: false, title: '' })
const form = reactive(emptyForm())
const rules = {
  planName: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  laboratoryId: [{ required: true, message: '请选择实验室', trigger: 'change' }],
  frequencyType: [{ required: true, message: '请选择频率', trigger: 'change' }],
  executeTime: [{ required: true, message: '请选择执行时间', trigger: 'change' }],
  ownerId: [{ required: true, message: '请选择负责人', trigger: 'change' }]
}

function laboratoryLabel(id) {
  return laboratoryOptions.value.find(item => item.id === String(id))?.label || `实验室 ${id}`
}

function userLabel(id) {
  return ownerOptions.value.find(item => item.id === String(id))?.label || `用户 ${id}`
}

async function loadOptions() {
  optionsError.value = ''
  const results = await Promise.allSettled([
    loadAllOptions(listLaboratory, { sortBy: 'name', sortDirection: 'asc' }).then(response => {
      laboratoryOptions.value = (response.rows || []).map(item => ({
        id: String(item.id),
        label: `${item.labCode} · ${item.name}`
      }))
    }),
    listLabUserOptions({ roleKey: 'lab_safety_officer' }).then(response => {
      ownerOptions.value = (response.data || []).map(item => ({
        id: String(item.id),
        label: `${item.displayName || item.userName}（${item.userName}）`
      }))
    })
  ])
  if (results.some(item => item.status === 'rejected')) {
    optionsError.value = '实验室或负责人选项加载失败，请刷新页面重试'
  }
}

function emptyForm() {
  return {
    id: undefined,
    version: 0,
    planName: '',
    laboratoryId: '',
    frequencyType: 'DAILY',
    intervalValue: 1,
    executeTime: '09:00:00',
    dayOfWeek: undefined,
    dayOfMonth: undefined,
    ownerId: '',
    deadlineRule: 'AFTER_SCHEDULED',
    deadlineOffsetMinutes: 1440,
    items: [{ itemCode: 'CHECK-1', content: '', sortOrder: 1, enabled: true }]
  }
}

function replaceForm(value) {
  Object.assign(form, emptyForm(), value)
  form.items = (value?.items || []).map(item => ({
    itemCode: item.itemCode,
    content: item.content,
    sortOrder: item.sortOrder,
    enabled: item.enabled === true || item.enabled === '1'
  }))
  if (!form.items.length) addItem()
}

async function loadPlans() {
  loading.value = true
  try {
    const response = await listInspectionPlans(query)
    plans.value = response.rows || []
    total.value = response.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() { query.pageNum = 1; void loadPlans() }
function resetQuery() { Object.assign(query, { pageNum: 1, keyword: undefined, status: undefined }); void loadPlans() }
function openCreate() { replaceForm(); dialog.title = '新增巡检计划'; dialog.open = true }

async function openEdit(row) {
  const response = await getInspectionPlan(row.id)
  const detail = response.data || {}
  replaceForm({ ...detail.plan, items: detail.items })
  dialog.title = '编辑巡检计划'
  dialog.open = true
}

function normalizeFrequency() {
  form.dayOfWeek = form.frequencyType === 'WEEKLY' ? (form.dayOfWeek || 1) : undefined
  form.dayOfMonth = form.frequencyType === 'MONTHLY' ? (form.dayOfMonth || 1) : undefined
}

function addItem() {
  form.items.push({ itemCode: `CHECK-${form.items.length + 1}`, content: '', sortOrder: form.items.length + 1, enabled: true })
}

function payload() {
  const { id, version, ...data } = form
  return { ...data, items: data.items.map((item, index) => ({ ...item, sortOrder: item.sortOrder ?? index + 1 })) }
}

async function submitPlan() {
  await planFormRef.value.validate()
  if (!form.items.length || form.items.some(item => !item.itemCode?.trim() || !item.content?.trim())) {
    proxy.$modal.msgWarning('请完整填写至少一个巡检项')
    return
  }
  submitting.value = true
  try {
    if (form.id) await updateInspectionPlan(form.id, form.version, payload())
    else await createInspectionPlan(payload())
    proxy.$modal.msgSuccess('保存成功')
    dialog.open = false
    await loadPlans()
  } finally {
    submitting.value = false
  }
}

async function changeStatus(row, enabled) {
  await proxy.$modal.confirm(`确认${enabled ? '启用' : '停用'}计划“${row.planName}”吗？`)
  if (enabled) await enableInspectionPlan(row.id)
  else await disableInspectionPlan(row.id)
  proxy.$modal.msgSuccess('操作成功')
  await loadPlans()
}

function frequencyText(row) {
  const prefix = { DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月' }[row.frequencyType] || row.frequencyType
  return `${prefix} / ${row.intervalValue || 1}`
}

loadOptions()
loadPlans()
</script>

<style scoped>
.section-title { display: flex; justify-content: space-between; align-items: center; margin: 8px 0 12px; font-weight: 600; }
.mb12 { margin-bottom: 12px; }
</style>
