<template>
  <div class="app-container lab-page">
    <div class="page-heading"><div><h1>设备资产</h1><p>查看设备状态与占用情况，管理资产档案和设备生命周期。</p></div></div>
    <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="关键词" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="资产编号或设备名称"
          clearable
          style="width: 220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item v-if="canListLaboratory" label="实验室" prop="laboratoryId">
        <el-select v-model="queryParams.laboratoryId" clearable filterable placeholder="全部实验室" style="width: 220px">
          <el-option v-for="item in laboratoryOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="设备类别" prop="categoryCode">
        <el-select v-model="queryParams.categoryCode" clearable filterable placeholder="全部类别" style="width: 180px">
          <el-option v-for="dict in lab_device_category" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 160px">
          <el-option v-for="dict in lab_device_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['lab:device:add']">
          新增设备
        </el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="mb12"
    >
      <template #default><el-button link type="primary" @click="getList">重新加载</el-button></template>
    </el-alert>

    <p class="lab-table-hint">左右滑动表格可查看完整信息与操作</p>

    <el-table v-loading="loading" :data="deviceList" @row-dblclick="handleDetail">
      <el-table-column label="资产编号" prop="assetNo" min-width="150" show-overflow-tooltip />
      <el-table-column label="设备名称" prop="name" min-width="150" show-overflow-tooltip />
      <el-table-column label="设备类别" width="140" show-overflow-tooltip>
        <template #default="scope"><dict-tag :options="lab_device_category" :value="scope.row.categoryCode" /></template>
      </el-table-column>
      <el-table-column label="型号" prop="model" width="130" show-overflow-tooltip />
      <el-table-column label="风险等级" width="110" align="center">
        <template #default="scope"><dict-tag :options="lab_risk_level" :value="scope.row.riskLevel" /></template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="scope"><dict-tag :options="lab_device_status" :value="scope.row.status" /></template>
      </el-table-column>
      <el-table-column label="位置" prop="location" min-width="150" show-overflow-tooltip />
      <el-table-column label="创建时间" width="180">
        <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['lab:device:query']">
            详情
          </el-button>
          <el-button link type="primary" icon="Edit" @click="handleEdit(scope.row)" v-hasPermi="['lab:device:edit']">
            修改
          </el-button>
          <el-button
            v-if="statusTargets(scope.row.status).length"
            link
            type="warning"
            icon="Switch"
            @click="handleStatus(scope.row)"
            v-hasPermi="['lab:device:status']"
          >状态</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="errorMessage ? '设备列表不可用' : '暂无符合条件的设备'" />
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
      <el-form ref="deviceRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :sm="12" :xs="24">
            <el-form-item label="资产编号" prop="assetNo">
              <el-input v-model="form.assetNo" maxlength="64" placeholder="请输入唯一资产编号" />
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="设备名称" prop="name">
              <el-input v-model="form.name" maxlength="100" placeholder="请输入设备名称" />
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="所属实验室" prop="laboratoryId">
              <el-select v-model="form.laboratoryId" filterable placeholder="请选择实验室" style="width: 100%">
                <el-option v-for="item in laboratoryOptions" :key="item.id" :label="`${item.labCode} · ${item.name}`" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="负责人" prop="managerId">
              <el-select
                v-model="form.managerId"
                filterable
                placeholder="请选择设备负责人"
                style="width: 100%"
              >
                <el-option v-for="item in userOptions" :key="item.id" :label="item.label" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="设备类别" prop="categoryCode">
              <el-select v-model="form.categoryCode" filterable placeholder="请选择设备类别" style="width: 100%">
                <el-option v-for="dict in lab_device_category" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="型号" prop="model">
              <el-input v-model="form.model" maxlength="100" placeholder="可选" />
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="风险等级" prop="riskLevel">
              <el-select v-model="form.riskLevel" placeholder="请选择风险等级" style="width: 100%">
                <el-option v-for="dict in lab_risk_level" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :sm="12" :xs="24">
            <el-form-item label="存放位置" prop="location">
              <el-input v-model="form.location" maxlength="200" placeholder="请输入存放位置" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="设备说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusOpen" title="变更设备状态" width="520px" append-to-body>
      <el-form ref="statusRef" :model="statusForm" :rules="statusRules" label-width="90px">
        <el-form-item label="当前状态">
          <dict-tag :options="lab_device_status" :value="statusRow?.status" />
        </el-form-item>
        <el-form-item label="目标状态" prop="targetStatus">
          <el-select v-model="statusForm.targetStatus" style="width: 100%">
            <el-option
              v-for="status in statusTargets(statusRow?.status)"
              :key="status"
              :label="statusLabel(status)"
              :value="status"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因" prop="reason">
          <el-input v-model="statusForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusOpen = false">取消</el-button>
        <el-button type="primary" :loading="statusSubmitting" @click="submitStatus">确认变更</el-button>
      </template>
    </el-dialog>

    <device-detail ref="detailRef" :laboratory-options="laboratoryOptions" :user-options="userOptions" />
  </div>
</template>

<script setup name="LabDevices">
import { loadAllOptions } from '@/utils/labOptions'
import { parseTime, selectDictLabel } from '@/utils/ruoyi'
import { listLaboratory } from '@/api/lab/laboratory'
import { listLabUserOptions } from '@/api/lab/options'
import {
  addDevice,
  changeDeviceStatus,
  getDevice,
  listDevice,
  updateDevice
} from '@/api/lab/device'
import DeviceDetail from './detail.vue'

const { proxy } = getCurrentInstance()
const route = useRoute()
const { lab_device_status, lab_risk_level, lab_device_category } = useDict(
  'lab_device_status',
  'lab_risk_level',
  'lab_device_category'
)

const loading = ref(false)
const errorMessage = ref('')
const showSearch = ref(true)
const deviceList = ref([])
const total = ref(0)
const laboratoryOptions = ref([])
const userOptions = ref([])
const optionsError = ref('')
const formOpen = ref(false)
const formTitle = ref('')
const submitting = ref(false)
const statusOpen = ref(false)
const statusSubmitting = ref(false)
const statusRow = ref(null)
const detailRef = ref()

const canListLaboratory = computed(() => proxy.$auth.hasPermi('lab:laboratory:list'))

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    laboratoryId: undefined,
    categoryCode: undefined,
    status: undefined,
    keyword: undefined,
    sortBy: 'createTime',
    sortDirection: 'desc'
  },
  form: {},
  statusForm: {
    targetStatus: '',
    reason: ''
  }
})
const { queryParams, form, statusForm } = toRefs(data)

const positiveStringId = (_rule, value, callback) => {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) callback(new Error('请选择有效对象'))
  else callback()
}

const rules = {
  assetNo: [{ required: true, message: '资产编号不能为空', trigger: 'blur' }],
  name: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }],
  laboratoryId: [{ validator: positiveStringId, trigger: 'change' }],
  categoryCode: [{ required: true, message: '设备类别不能为空', trigger: 'blur' }],
  riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }],
  location: [{ required: true, message: '存放位置不能为空', trigger: 'blur' }],
  managerId: [{ validator: positiveStringId, trigger: 'change' }]
}

const statusRules = {
  targetStatus: [{ required: true, message: '请选择目标状态', trigger: 'change' }],
  reason: [
    { required: true, message: '变更原因不能为空', trigger: 'blur' },
    { min: 1, max: 500, message: '变更原因长度为 1 到 500 个字符', trigger: 'blur' }
  ]
}

function normalizeDevice(row) {
  return {
    ...row,
    id: String(row.id),
    laboratoryId: String(row.laboratoryId),
    managerId: String(row.managerId)
  }
}

async function getList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listDevice(queryParams.value)
    deviceList.value = (response.rows || []).map(normalizeDevice)
    total.value = response.total || 0
  } catch {
    deviceList.value = []
    total.value = 0
    errorMessage.value = '设备列表加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  optionsError.value = ''
  const tasks = [listLabUserOptions({ roleKey: 'lab_manager' })
    .then(response => {
      userOptions.value = (response.data || []).map(item => ({
        id: String(item.id),
        label: `${item.displayName || item.userName}（${item.userName}）`
      }))
    })]
  if (canListLaboratory.value) {
    tasks.push(loadAllOptions(listLaboratory, { sortBy: 'name', sortDirection: 'asc' })
      .then(response => {
        laboratoryOptions.value = (response.rows || []).map(item => ({
          ...item,
          id: String(item.id)
        }))
      }))
  }
  try {
    await Promise.all(tasks)
  } catch {
    optionsError.value = '部分下拉选项加载失败，请关闭窗口后重试'
  }
}

function resetForm() {
  form.value = {
    id: '',
    assetNo: '',
    laboratoryId: '',
    name: '',
    categoryCode: '',
    model: '',
    riskLevel: 'LOW',
    location: '',
    managerId: '',
    description: '',
    expectedVersion: 0
  }
  proxy.resetForm('deviceRef')
}

async function handleAdd() {
  resetForm()
  formTitle.value = '新增设备'
  formOpen.value = true
  await loadOptions()
}

async function handleEdit(row) {
  resetForm()
  formTitle.value = '修改设备'
  formOpen.value = true
  await loadOptions()
  try {
    const response = await getDevice(String(row.id))
    form.value = normalizeDevice(response.data)
  } catch {
    formOpen.value = false
  }
}

function devicePayload() {
  return {
    assetNo: form.value.assetNo,
    laboratoryId: form.value.laboratoryId,
    name: form.value.name,
    categoryCode: form.value.categoryCode,
    model: form.value.model || null,
    riskLevel: form.value.riskLevel,
    location: form.value.location,
    managerId: form.value.managerId,
    description: form.value.description || null,
    ...(form.value.id ? { expectedVersion: form.value.version } : {})
  }
}

async function submitForm() {
  const valid = await proxy.$refs.deviceRef.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (form.value.id) await updateDevice(form.value.id, devicePayload())
    else await addDevice(devicePayload())
    proxy.$modal.msgSuccess(form.value.id ? '设备修改成功' : '设备新增成功')
    formOpen.value = false
    await getList()
  } finally {
    submitting.value = false
  }
}

function statusTargets(status) {
  const transitions = {
    AVAILABLE: ['DISABLED'],
    FAULT: ['DISABLED'],
    DISABLED: ['AVAILABLE']
  }
  return transitions[status] || []
}

function statusLabel(status) {
  return selectDictLabel(lab_device_status.value, status) || status
}

function handleStatus(row) {
  statusRow.value = row
  statusForm.value = {
    targetStatus: statusTargets(row.status)[0] || '',
    reason: ''
  }
  statusOpen.value = true
  nextTick(() => proxy.resetForm('statusRef'))
}

async function submitStatus() {
  const valid = await proxy.$refs.statusRef.validate().catch(() => false)
  if (!valid) return
  statusSubmitting.value = true
  try {
    await changeDeviceStatus(statusRow.value.id, {
      targetStatus: statusForm.value.targetStatus,
      reason: statusForm.value.reason.trim()
    })
    proxy.$modal.msgSuccess('设备状态变更成功')
    statusOpen.value = false
    await getList()
  } finally {
    statusSubmitting.value = false
  }
}

function handleDetail(row) {
  detailRef.value?.open(String(row.id))
}

watch(
  () => [route.name, route.params.id],
  ([name, id]) => {
    if (name === 'LabDeviceDetail' && id) {
      nextTick(() => detailRef.value?.open(String(id)))
    }
  },
  { immediate: true }
)

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

if (canListLaboratory.value) loadOptions()
getList()
</script>

<style scoped>
.mb12 {
  margin-bottom: 12px;
}

.mb16 {
  margin-bottom: 16px;
}
</style>
