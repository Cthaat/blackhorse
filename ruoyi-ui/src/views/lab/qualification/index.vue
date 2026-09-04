<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="用户" prop="userId">
        <el-select
          v-model="queryParams.userId"
          clearable
          filterable
          placeholder="全部学生"
          style="width: 250px"
        >
          <el-option v-for="item in userOptions" :key="item.id" :label="item.label" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="适用范围" prop="scopeType">
        <el-select v-model="queryParams.scopeType" clearable placeholder="全部范围" style="width: 180px">
          <el-option v-for="dict in lab_qualification_scope_type" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['lab:qualification:add']">
          新增资格
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="mb12">
      <template #default><el-button link type="primary" @click="getList">重新加载</el-button></template>
    </el-alert>

    <el-table v-loading="loading" :data="qualificationList" @row-dblclick="handleDetail">
      <el-table-column label="用户" min-width="170" show-overflow-tooltip>
        <template #default="scope">{{ userLabel(scope.row.userId) }}</template>
      </el-table-column>
      <el-table-column label="适用范围" width="130">
        <template #default="scope"><dict-tag :options="lab_qualification_scope_type" :value="scope.row.scopeType" /></template>
      </el-table-column>
      <el-table-column label="范围对象" min-width="180" show-overflow-tooltip>
        <template #default="scope">{{ scopeLabel(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="生效时间" width="180">
        <template #default="scope">{{ parseTime(scope.row.validFrom) }}</template>
      </el-table-column>
      <el-table-column label="失效时间" width="180">
        <template #default="scope">{{ parseTime(scope.row.validUntil) }}</template>
      </el-table-column>
      <el-table-column label="当前状态" width="110" align="center">
        <template #default="scope">
          <el-tag :type="statusMeta(scope.row.status).type">{{ statusMeta(scope.row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['lab:qualification:query']">
            详情
          </el-button>
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['lab:qualification:edit']">
            修改
          </el-button>
          <el-button
            v-if="scope.row.status !== 'REVOKED'"
            link
            type="danger"
            icon="CircleClose"
            @click="handleRevoke(scope.row)"
            v-hasPermi="['lab:qualification:revoke']"
          >撤销</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="errorMessage ? '资格列表不可用' : '暂无符合条件的资格'" />
      </template>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="formOpen" :title="formTitle" width="680px" append-to-body>
      <el-alert v-if="optionsError" :title="optionsError" type="warning" show-icon :closable="false" class="mb16" />
      <el-form ref="qualificationRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户" prop="userId">
          <el-select
            v-model="form.userId"
            filterable
            placeholder="请选择学生"
            style="width: 100%"
          >
            <el-option v-for="item in userOptions" :key="item.id" :label="item.label" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="适用范围" prop="scopeType">
          <el-radio-group v-model="form.scopeType" @change="handleScopeTypeChange">
            <el-radio-button v-for="dict in lab_qualification_scope_type" :key="dict.value" :label="dict.value">
              {{ dict.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所属实验室" prop="laboratoryId">
          <el-select
            v-model="form.laboratoryId"
            filterable
            placeholder="请选择实验室"
            style="width: 100%"
            @change="handleLaboratoryChange"
          >
            <el-option v-for="item in laboratoryOptions" :key="item.id" :label="`${item.labCode} · ${item.name}`" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scopeType === 'DEVICE_CATEGORY'" label="设备类别" prop="scopeId">
          <el-select
            v-model="form.scopeId"
            filterable
            placeholder="请选择设备类别"
            style="width: 100%"
          >
            <el-option v-for="dict in lab_device_category" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期" prop="validityRange">
          <el-date-picker
            v-model="form.validityRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="生效时间"
            end-placeholder="失效时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="revokeOpen" title="撤销资格" width="520px" append-to-body>
      <el-form ref="revokeRef" :model="revokeForm" :rules="revokeRules" label-width="90px">
        <el-form-item label="资格对象">{{ revokeRow ? scopeLabel(revokeRow) : '—' }}</el-form-item>
        <el-form-item label="撤销原因" prop="reason">
          <el-input v-model="revokeForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revokeOpen = false">取消</el-button>
        <el-button type="danger" :loading="revokeSubmitting" @click="submitRevoke">确认撤销</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailOpen" title="资格详情" size="min(860px, 92vw)" append-to-body>
      <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false" class="mb16">
        <template #default><el-button link type="primary" @click="loadDetail">重新加载</el-button></template>
      </el-alert>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <el-descriptions :column="2" border class="mb20">
            <el-descriptions-item label="用户">{{ userLabel(detail.userId) }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <el-tag :type="statusMeta(detail.status).type">{{ statusMeta(detail.status).label }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="适用范围">
              <dict-tag :options="lab_qualification_scope_type" :value="detail.scopeType" />
            </el-descriptions-item>
            <el-descriptions-item label="授权对象">{{ scopeLabel(detail) }}</el-descriptions-item>
            <el-descriptions-item label="生效时间">{{ parseTime(detail.validFrom) }}</el-descriptions-item>
            <el-descriptions-item label="失效时间">{{ parseTime(detail.validUntil) }}</el-descriptions-item>
            <el-descriptions-item label="撤销时间">{{ parseTime(detail.revokedAt) || '—' }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
            <el-descriptions-item label="撤销原因" :span="2">{{ detail.revokeReason || '—' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ parseTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ parseTime(detail.updateTime) || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-tabs>
            <el-tab-pane v-if="canReadAttachment" label="附件">
              <attachment-panel business-type="QUALIFICATION" :business-id="detail.id" :can-manage="canManageAttachment" />
            </el-tab-pane>
            <el-tab-pane label="状态历史">
              <status-history object-type="QUALIFICATION" :object-id="detail.id" />
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-empty v-else-if="!detailLoading && !detailError" description="未找到资格信息" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="LabQualifications">
import { parseTime } from '@/utils/ruoyi'
import { listLaboratory } from '@/api/lab/laboratory'
import { listLabUserOptions } from '@/api/lab/options'
import {
  addQualification,
  getQualification,
  listQualification,
  revokeQualification,
  updateQualification
} from '@/api/lab/qualification'
import AttachmentPanel from '@/components/lab/AttachmentPanel.vue'
import StatusHistory from '@/components/lab/StatusHistory.vue'

const { proxy } = getCurrentInstance()
const { lab_qualification_scope_type, lab_device_category } = useDict(
  'lab_qualification_scope_type',
  'lab_device_category'
)

const loading = ref(false)
const errorMessage = ref('')
const showSearch = ref(true)
const qualificationList = ref([])
const total = ref(0)
const userOptions = ref([])
const userLabels = ref(new Map())
const laboratoryOptions = ref([])
const laboratoryLabels = ref(new Map())
const optionsError = ref('')
const formOpen = ref(false)
const formTitle = ref('')
const submitting = ref(false)
const revokeOpen = ref(false)
const revokeSubmitting = ref(false)
const revokeRow = ref(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailId = ref('')
const detail = ref(null)

const canQuery = computed(() => proxy.$auth.hasPermi('lab:qualification:query'))
const canListLaboratory = computed(() => proxy.$auth.hasPermi('lab:laboratory:list'))
const canReadAttachment = computed(() => proxy.$auth.hasPermi('lab:attachment:read'))
const canManageAttachment = computed(() => proxy.$auth.hasPermi('lab:attachment:manage')
  && proxy.$auth.hasPermi('lab:qualification:edit'))

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    userId: undefined,
    scopeType: undefined,
    sortBy: 'createTime',
    sortDirection: 'desc'
  },
  form: {},
  revokeForm: { reason: '' }
})
const { queryParams, form, revokeForm } = toRefs(data)

const positiveStringId = (_rule, value, callback) => {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) callback(new Error('请输入有效的正整数 ID'))
  else callback()
}

const scopeIdValidator = (_rule, value, callback) => {
  if (typeof value !== 'string' || !value.trim() || value.trim().length > 64) {
    callback(new Error('范围对象长度为 1 到 64 个字符'))
  } else if (form.value.scopeType === 'LABORATORY' && !/^[1-9]\d*$/.test(value)) {
    callback(new Error('请输入有效的实验室 ID'))
  } else {
    callback()
  }
}

const rules = {
  userId: [{ validator: positiveStringId, trigger: 'change' }],
  scopeType: [{ required: true, message: '请选择适用范围', trigger: 'change' }],
  laboratoryId: [{ validator: positiveStringId, trigger: 'change' }],
  scopeId: [{ validator: scopeIdValidator, trigger: ['blur', 'change'] }],
  validityRange: [{ type: 'array', required: true, len: 2, message: '请选择完整有效期', trigger: 'change' }]
}

const revokeRules = {
  reason: [
    { required: true, message: '撤销原因不能为空', trigger: 'blur' },
    { min: 1, max: 500, message: '撤销原因长度为 1 到 500 个字符', trigger: 'blur' }
  ]
}

const qualificationStatuses = {
  VALID: { label: '有效', type: 'success' },
  NOT_EFFECTIVE: { label: '未生效', type: 'info' },
  EXPIRED: { label: '已过期', type: 'warning' },
  REVOKED: { label: '已撤销', type: 'danger' }
}

function statusMeta(status) {
  return qualificationStatuses[status] || { label: status || '未知', type: 'info' }
}

function normalizeQualification(row) {
  return {
    ...row,
    id: String(row.id),
    userId: String(row.userId),
    laboratoryId: String(row.laboratoryId),
    scopeId: String(row.scopeId)
  }
}

function userLabel(id) {
  return userLabels.value.get(String(id)) || `用户 ${id}`
}

function scopeLabel(row) {
  const laboratory = row.laboratoryName
    || laboratoryLabels.value.get(String(row.laboratoryId))
    || '未命名实验室'
  if (row.scopeType === 'LABORATORY') {
    return `${laboratory} · 全实验室`
  }
  const category = lab_device_category.value.find(item => item.value === row.scopeId)
  return `${laboratory} · ${category?.label || row.scopeId}`
}

async function getList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listQualification(queryParams.value)
    qualificationList.value = (response.rows || []).map(normalizeQualification)
    total.value = response.total || 0
  } catch {
    qualificationList.value = []
    total.value = 0
    errorMessage.value = '资格列表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  optionsError.value = ''
  const tasks = [listLabUserOptions({ roleKey: 'lab_student' }).then(response => {
      const labels = new Map()
      userOptions.value = (response.data || []).map(item => {
        const id = String(item.id)
        const label = `${item.displayName || item.userName}（${item.userName}）`
        labels.set(id, label)
        return { id, label }
      })
      userLabels.value = labels
    })]
  if (canListLaboratory.value) {
    tasks.push(listLaboratory({ pageNum: 1, pageSize: 200, sortBy: 'name', sortDirection: 'asc' }).then(response => {
      const labels = new Map()
      laboratoryOptions.value = (response.rows || []).map(item => {
        const normalized = { ...item, id: String(item.id) }
        labels.set(normalized.id, `${item.labCode} · ${item.name}`)
        return normalized
      })
      laboratoryLabels.value = labels
    }))
  }
  const results = await Promise.allSettled(tasks)
  if (results.some(item => item.status === 'rejected')) {
    optionsError.value = '学生或实验室选项加载失败，请稍后重试'
  }
}

function resetForm() {
  form.value = {
    id: '',
    userId: '',
    scopeType: 'LABORATORY',
    laboratoryId: '',
    scopeId: '',
    validityRange: []
  }
  proxy.resetForm('qualificationRef')
}

async function handleAdd() {
  resetForm()
  formTitle.value = '新增资格'
  formOpen.value = true
  await loadOptions()
}

function localDateTime(value) {
  return value ? String(value).replace(/(?:Z|[+-]\d{2}:\d{2})$/, '') : ''
}

async function handleEdit(row) {
  resetForm()
  formTitle.value = '修改资格'
  formOpen.value = true
  await loadOptions()
  try {
    const response = await getQualification(String(row.id))
    const current = normalizeQualification(response.data)
    form.value = {
      ...current,
      validityRange: [localDateTime(current.validFrom), localDateTime(current.validUntil)]
    }
  } catch {
    formOpen.value = false
  }
}

function qualificationPayload() {
  const laboratoryId = String(form.value.laboratoryId)
  return {
    userId: form.value.userId,
    scopeType: form.value.scopeType,
    laboratoryId,
    scopeId: form.value.scopeType === 'LABORATORY'
      ? laboratoryId
      : form.value.scopeId.trim(),
    validFrom: form.value.validityRange[0],
    validUntil: form.value.validityRange[1],
    ...(form.value.id ? { expectedVersion: form.value.version } : {})
  }
}

function handleScopeTypeChange(scopeType) {
  form.value.scopeId = scopeType === 'LABORATORY' ? form.value.laboratoryId : ''
  nextTick(() => proxy.$refs.qualificationRef?.clearValidate(['scopeId']))
}

function handleLaboratoryChange(laboratoryId) {
  if (form.value.scopeType === 'LABORATORY') {
    form.value.scopeId = laboratoryId
  }
}

async function submitForm() {
  const valid = await proxy.$refs.qualificationRef.validate().catch(() => false)
  if (!valid) return
  if (form.value.validityRange[1] <= form.value.validityRange[0]) {
    proxy.$modal.msgWarning('失效时间必须晚于生效时间')
    return
  }
  submitting.value = true
  try {
    if (form.value.id) await updateQualification(form.value.id, qualificationPayload())
    else await addQualification(qualificationPayload())
    proxy.$modal.msgSuccess(form.value.id ? '资格修改成功' : '资格新增成功')
    formOpen.value = false
    await getList()
  } finally {
    submitting.value = false
  }
}

function handleRevoke(row) {
  revokeRow.value = row
  revokeForm.value = { reason: '' }
  revokeOpen.value = true
  nextTick(() => proxy.resetForm('revokeRef'))
}

async function submitRevoke() {
  const valid = await proxy.$refs.revokeRef.validate().catch(() => false)
  if (!valid) return
  revokeSubmitting.value = true
  try {
    await revokeQualification(revokeRow.value.id, {
      reason: revokeForm.value.reason.trim(),
      expectedVersion: revokeRow.value.version
    })
    proxy.$modal.msgSuccess('资格已撤销')
    revokeOpen.value = false
    await getList()
  } finally {
    revokeSubmitting.value = false
  }
}

async function handleDetail(row) {
  if (!canQuery.value) return
  detailId.value = String(row.id)
  detailOpen.value = true
  await loadOptions()
  await loadDetail()
}

async function loadDetail() {
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const response = await getQualification(detailId.value)
    detail.value = response.data ? normalizeQualification(response.data) : null
  } catch {
    detailError.value = '资格详情加载失败，请稍后重试'
  } finally {
    detailLoading.value = false
  }
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

loadOptions()
getList()
</script>

<style scoped>
.detail-body { min-height: 240px; }
.mb12 { margin-bottom: 12px; }
.mb16 { margin-bottom: 16px; }
.mb20 { margin-bottom: 20px; }
</style>
