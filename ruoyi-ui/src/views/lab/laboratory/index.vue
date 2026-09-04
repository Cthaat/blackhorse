<template>
  <div class="app-container">
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="关键词" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          clearable
          placeholder="实验室编码或名称"
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 160px">
          <el-option v-for="dict in lab_laboratory_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['lab:laboratory:add']">
          新增实验室
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="mb12">
      <template #default><el-button link type="primary" @click="getList">重新加载</el-button></template>
    </el-alert>

    <el-table v-loading="loading" :data="laboratoryList" @row-dblclick="handleDetail">
      <el-table-column label="实验室编码" prop="labCode" min-width="150" show-overflow-tooltip />
      <el-table-column label="实验室名称" prop="name" min-width="170" show-overflow-tooltip />
      <el-table-column label="所属部门" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ deptLabel(scope.row.deptId) }}</template>
      </el-table-column>
      <el-table-column label="负责人" min-width="150" show-overflow-tooltip>
        <template #default="scope">{{ userLabel(scope.row.managerId) }}</template>
      </el-table-column>
      <el-table-column label="位置" prop="location" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="110" align="center">
        <template #default="scope"><dict-tag :options="lab_laboratory_status" :value="scope.row.status" /></template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['lab:laboratory:query']">
            详情
          </el-button>
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['lab:laboratory:edit']">
            修改
          </el-button>
          <el-button
            link
            :type="scope.row.status === 'ENABLED' ? 'danger' : 'success'"
            icon="Switch"
            @click="handleStatus(scope.row)"
            v-hasPermi="['lab:laboratory:status']"
          >{{ scope.row.status === 'ENABLED' ? '停用' : '启用' }}</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="errorMessage ? '实验室列表不可用' : '暂无符合条件的实验室'" />
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
      <el-form ref="laboratoryRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :sm="12" :xs="24">
            <el-form-item label="实验室编码" prop="labCode">
              <el-input v-model="form.labCode" maxlength="32" placeholder="请输入唯一编码" />
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="实验室名称" prop="name">
              <el-input v-model="form.name" maxlength="100" placeholder="请输入实验室名称" />
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="所属部门" prop="deptId">
              <el-select
                v-model="form.deptId"
                filterable
                placeholder="请选择所属部门"
                style="width: 100%"
              >
                <el-option v-for="item in deptOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="负责人" prop="managerId">
              <el-select
                v-model="form.managerId"
                filterable
                placeholder="请选择实验室负责人"
                style="width: 100%"
              >
                <el-option v-for="item in userOptions" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="实验室位置" prop="location">
          <el-input v-model="form.location" maxlength="200" placeholder="请输入楼栋、楼层和房间号" />
        </el-form-item>
        <el-form-item label="实验室说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusOpen" :title="statusTitle" width="520px" append-to-body>
      <el-form ref="statusRef" :model="statusForm" :rules="statusRules" label-width="90px">
        <el-form-item label="实验室">{{ statusRow?.labCode }} · {{ statusRow?.name }}</el-form-item>
        <el-form-item label="变更原因" prop="reason">
          <el-input v-model="statusForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusOpen = false">取消</el-button>
        <el-button type="primary" :loading="statusSubmitting" @click="submitStatus">确认</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailOpen" title="实验室详情" size="min(860px, 92vw)" append-to-body>
      <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false" class="mb16">
        <template #default><el-button link type="primary" @click="loadDetail">重新加载</el-button></template>
      </el-alert>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <el-descriptions :column="2" border class="mb20">
            <el-descriptions-item label="实验室编码">{{ detail.labCode }}</el-descriptions-item>
            <el-descriptions-item label="实验室名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ deptLabel(detail.deptId) }}</el-descriptions-item>
            <el-descriptions-item label="负责人">{{ userLabel(detail.managerId) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <dict-tag :options="lab_laboratory_status" :value="detail.status" />
            </el-descriptions-item>
            <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
            <el-descriptions-item label="位置" :span="2">{{ detail.location }}</el-descriptions-item>
            <el-descriptions-item label="说明" :span="2">{{ detail.description || '—' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ parseTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ parseTime(detail.updateTime) || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-tabs>
            <el-tab-pane v-if="canReadAttachment" label="附件">
              <attachment-panel business-type="LABORATORY" :business-id="detail.id" :can-manage="canManageAttachment" />
            </el-tab-pane>
            <el-tab-pane label="状态历史">
              <status-history object-type="LABORATORY" :object-id="detail.id" />
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-empty v-else-if="!detailLoading && !detailError" description="未找到实验室信息" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="LabLaboratories">
import { parseTime } from '@/utils/ruoyi'
import {
  addLaboratory,
  disableLaboratory,
  enableLaboratory,
  getLaboratory,
  listLaboratory,
  updateLaboratory
} from '@/api/lab/laboratory'
import { listLabDepartmentOptions, listLabUserOptions } from '@/api/lab/options'
import AttachmentPanel from '@/components/lab/AttachmentPanel.vue'
import StatusHistory from '@/components/lab/StatusHistory.vue'

const { proxy } = getCurrentInstance()
const { lab_laboratory_status } = useDict('lab_laboratory_status')

const loading = ref(false)
const errorMessage = ref('')
const showSearch = ref(true)
const laboratoryList = ref([])
const total = ref(0)
const deptOptions = ref([])
const deptLabels = ref(new Map())
const userOptions = ref([])
const userLabels = ref(new Map())
const optionsError = ref('')
const formOpen = ref(false)
const formTitle = ref('')
const submitting = ref(false)
const statusOpen = ref(false)
const statusSubmitting = ref(false)
const statusRow = ref(null)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailId = ref('')
const detail = ref(null)

const canQuery = computed(() => proxy.$auth.hasPermi('lab:laboratory:query'))
const canReadAttachment = computed(() => proxy.$auth.hasPermi('lab:attachment:read'))
const canManageAttachment = computed(() => proxy.$auth.hasPermi('lab:attachment:manage')
  && proxy.$auth.hasPermi('lab:laboratory:edit'))
const statusTitle = computed(() => statusRow.value?.status === 'ENABLED' ? '停用实验室' : '启用实验室')

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    status: undefined,
    keyword: undefined,
    sortBy: 'createTime',
    sortDirection: 'desc'
  },
  form: {},
  statusForm: { reason: '' }
})
const { queryParams, form, statusForm } = toRefs(data)

const positiveStringId = (_rule, value, callback) => {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) callback(new Error('请选择有效对象'))
  else callback()
}

const rules = {
  labCode: [{ required: true, message: '实验室编码不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '实验室名称不能为空', trigger: 'blur' }],
  deptId: [{ validator: positiveStringId, trigger: 'change' }],
  managerId: [{ validator: positiveStringId, trigger: 'change' }],
  location: [{ required: true, message: '实验室位置不能为空', trigger: 'blur' }]
}

const statusRules = {
  reason: [
    { required: true, message: '变更原因不能为空', trigger: 'blur' },
    { min: 1, max: 500, message: '变更原因长度为 1 到 500 个字符', trigger: 'blur' }
  ]
}

function normalizeLaboratory(row) {
  return {
    ...row,
    id: String(row.id),
    deptId: String(row.deptId),
    managerId: String(row.managerId)
  }
}

function deptLabel(id) {
  return deptLabels.value.get(String(id)) || `部门 ${id}`
}

function userLabel(id) {
  return userLabels.value.get(String(id)) || `用户 ${id}`
}

async function getList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listLaboratory(queryParams.value)
    laboratoryList.value = (response.rows || []).map(normalizeLaboratory)
    total.value = response.total || 0
  } catch {
    laboratoryList.value = []
    total.value = 0
    errorMessage.value = '实验室列表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  optionsError.value = ''
  const tasks = [
    listLabDepartmentOptions().then(response => {
      deptOptions.value = (response.data || []).map(item => ({ ...item, id: String(item.id) }))
      deptLabels.value = new Map(deptOptions.value.map(item => [item.id, item.name]))
    }),
    listLabUserOptions({ roleKey: 'lab_manager' }).then(response => {
      const labels = new Map()
      userOptions.value = (response.data || []).map(item => {
        const id = String(item.id)
        const label = `${item.displayName || item.userName}（${item.userName}）`
        labels.set(id, label)
        return { id, label }
      })
      userLabels.value = labels
    })
  ]
  const results = await Promise.allSettled(tasks)
  if (results.some(item => item.status === 'rejected')) {
    optionsError.value = '部门或负责人选项加载失败，请稍后重试'
  }
}

function resetForm() {
  form.value = {
    id: '',
    labCode: '',
    name: '',
    deptId: '',
    managerId: '',
    location: '',
    description: ''
  }
  proxy.resetForm('laboratoryRef')
}

async function handleAdd() {
  resetForm()
  formTitle.value = '新增实验室'
  formOpen.value = true
  await loadOptions()
}

async function handleEdit(row) {
  resetForm()
  formTitle.value = '修改实验室'
  formOpen.value = true
  await loadOptions()
  try {
    const response = await getLaboratory(String(row.id))
    form.value = normalizeLaboratory(response.data)
  } catch {
    formOpen.value = false
  }
}

function laboratoryPayload() {
  return {
    labCode: form.value.labCode.trim(),
    name: form.value.name.trim(),
    deptId: form.value.deptId,
    managerId: form.value.managerId,
    location: form.value.location.trim(),
    description: form.value.description?.trim() || null,
    ...(form.value.id ? { expectedVersion: form.value.version } : {})
  }
}

async function submitForm() {
  const valid = await proxy.$refs.laboratoryRef.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (form.value.id) await updateLaboratory(form.value.id, laboratoryPayload())
    else await addLaboratory(laboratoryPayload())
    proxy.$modal.msgSuccess(form.value.id ? '实验室修改成功' : '实验室新增成功')
    formOpen.value = false
    await getList()
  } finally {
    submitting.value = false
  }
}

function handleStatus(row) {
  statusRow.value = row
  statusForm.value = { reason: '' }
  statusOpen.value = true
  nextTick(() => proxy.resetForm('statusRef'))
}

async function submitStatus() {
  const valid = await proxy.$refs.statusRef.validate().catch(() => false)
  if (!valid) return
  statusSubmitting.value = true
  try {
    const command = statusRow.value.status === 'ENABLED' ? disableLaboratory : enableLaboratory
    await command(statusRow.value.id, statusForm.value.reason.trim())
    proxy.$modal.msgSuccess(statusRow.value.status === 'ENABLED' ? '实验室已停用' : '实验室已启用')
    statusOpen.value = false
    await getList()
  } finally {
    statusSubmitting.value = false
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
    const response = await getLaboratory(detailId.value)
    detail.value = response.data ? normalizeLaboratory(response.data) : null
  } catch {
    detailError.value = '实验室详情加载失败，请稍后重试'
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
