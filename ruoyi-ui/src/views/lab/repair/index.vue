<template>
  <div class="app-container repair-page">
    <div class="page-heading">
      <div>
        <h1>维修工单</h1>
        <p>覆盖故障报告、人员分派、维修处理与验收闭环。</p>
      </div>
    </div>

    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="维修编号" prop="repairNo">
        <el-input v-model="queryParams.repairNo" clearable placeholder="请输入维修编号" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="设备" prop="deviceId">
        <el-select v-model="queryParams.deviceId" clearable filterable placeholder="全部设备" style="width: 220px">
          <el-option v-for="device in devices" :key="String(device.id)" :value="String(device.id)" :label="`${device.assetNo} · ${device.name}`" />
        </el-select>
      </el-form-item>
      <el-form-item label="工单状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="全部状态" class="query-select">
          <el-option v-for="dict in lab_repair_status" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Warning" v-hasPermi="['lab:repair:report']" @click="openReport">
          报告故障
        </el-button>
      </el-col>
      <right-toolbar :show-search="false" @queryTable="getList" />
    </el-row>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="mb16">
      <template #default><el-button link type="primary" @click="getList">重新加载</el-button></template>
    </el-alert>

    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column label="维修编号" prop="repairNo" min-width="176" show-overflow-tooltip />
      <el-table-column label="资产编号" prop="assetNo" min-width="132" />
      <el-table-column label="设备名称" prop="deviceName" min-width="150" show-overflow-tooltip />
      <el-table-column label="故障描述" prop="faultDescription" min-width="210" show-overflow-tooltip />
      <el-table-column label="来源" width="110" align="center">
        <template #default="scope">{{ sourceLabel(scope.row.sourceType) }}</template>
      </el-table-column>
      <el-table-column label="维修人员" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ assigneeLabel(scope.row.assigneeId) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="108" align="center">
        <template #default="scope"><dict-tag :options="lab_repair_status" :value="scope.row.status" /></template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="164">
        <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" min-width="330" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="View" v-hasPermi="['lab:repair:query']" @click="openDetail(scope.row)">详情</el-button>
          <el-button v-if="scope.row.status === 'WAIT_ASSIGN'" link type="primary" icon="User" v-hasPermi="['lab:repair:assign']" :loading="actionId === String(scope.row.id)" @click="handleAssign(scope.row)">分派</el-button>
          <el-button v-if="scope.row.status === 'WAIT_REPAIR'" link type="primary" icon="Tools" v-hasPermi="['lab:repair:process']" :loading="actionId === String(scope.row.id)" @click="handleStart(scope.row)">开始维修</el-button>
          <el-button v-if="scope.row.status === 'IN_PROGRESS'" link type="success" icon="DocumentChecked" v-hasPermi="['lab:repair:process']" :loading="actionId === String(scope.row.id)" @click="handleSubmitResult(scope.row)">提交结果</el-button>
          <el-button v-if="scope.row.status === 'WAIT_ACCEPTANCE'" link type="success" icon="CircleCheck" v-hasPermi="['lab:repair:accept']" @click="openAccept(scope.row)">验收</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty :description="errorMessage ? '数据加载失败' : '暂无维修工单'" /></template>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="reportOpen" title="报告设备故障" width="640px" append-to-body :close-on-click-modal="false">
      <el-alert v-if="reportError" :title="reportError" type="error" show-icon :closable="false" class="mb16" />
      <el-form ref="reportRef" :model="reportForm" :rules="reportRules" label-width="96px">
        <el-form-item label="故障设备" prop="deviceId">
          <el-select v-model="reportForm.deviceId" class="full-width" filterable clearable :loading="deviceLoading" placeholder="请选择设备">
            <el-option
              v-for="device in devices"
              :key="String(device.id)"
              :value="String(device.id)"
              :label="`${device.assetNo} · ${device.name}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述" prop="description">
          <el-input v-model="reportForm.description" type="textarea" :rows="5" maxlength="1000" show-word-limit placeholder="请描述故障现象、发生条件及当前设备状态" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="reportOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReport">提交报修</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignOpen" title="分派维修人员" width="520px" append-to-body :close-on-click-modal="false">
      <el-form ref="assignRef" :model="assignForm" :rules="assignRules" label-width="96px">
        <el-form-item label="维修工单">{{ assignRow?.repairNo || '-' }}</el-form-item>
        <el-form-item label="维修人员" prop="assigneeId">
          <el-select v-model="assignForm.assigneeId" filterable placeholder="请选择维修人员" class="full-width">
            <el-option v-for="item in workerOptions" :key="item.id" :value="item.id" :label="item.label" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="assignOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAssign">确认分派</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="acceptOpen" title="维修验收" width="620px" append-to-body :close-on-click-modal="false">
      <el-alert v-if="acceptError" :title="acceptError" type="error" show-icon :closable="false" class="mb16" />
      <el-form ref="acceptRef" :model="acceptForm" :rules="acceptRules" label-width="96px">
        <el-form-item label="验收结论" prop="passed">
          <el-radio-group v-model="acceptForm.passed">
            <el-radio :value="true">通过并关闭</el-radio>
            <el-radio :value="false">退回维修</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="验收说明" prop="reason">
          <el-input v-model="acceptForm.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="请说明验收依据或退回原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="acceptOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAccept">确认验收</el-button>
      </template>
    </el-dialog>

    <RepairDetail v-model="detailOpen" :repair-id="detailId" :worker-options="workerOptions" />
  </div>
</template>

<script setup name="LabRepair">
import { loadAllOptions } from '@/utils/labOptions'
import RepairDetail from './detail.vue'
import {
  acceptRepair,
  assignRepair,
  listRepairDevices,
  listRepairOrders,
  reportFault,
  startRepair,
  submitRepairResult
} from '@/api/lab/repair'
import { listLabUserOptions } from '@/api/lab/options'

const { proxy } = getCurrentInstance()
const route = useRoute()
const { lab_repair_status } = useDict('lab_repair_status')
const queryRef = ref()
const reportRef = ref()
const acceptRef = ref()
const assignRef = ref()
const loading = ref(false)
const errorMessage = ref('')
const rows = ref([])
const total = ref(0)
const actionId = ref('')
const submitting = ref(false)

const detailOpen = ref(false)
const detailId = ref('')
const reportOpen = ref(false)
const reportError = ref('')
const deviceLoading = ref(false)
const devices = ref([])
const workerOptions = ref([])
const reportForm = reactive({ deviceId: '', description: '' })
const reportRules = {
  deviceId: [{ required: true, message: '请选择故障设备', trigger: 'change' }],
  description: [{ required: true, whitespace: true, message: '请填写故障描述', trigger: 'blur' }]
}

const assignOpen = ref(false)
const assignRow = ref(null)
const assignForm = reactive({ assigneeId: '' })
const assignRules = {
  assigneeId: [{ required: true, message: '请选择维修人员', trigger: 'change' }]
}

const acceptOpen = ref(false)
const acceptId = ref('')
const acceptError = ref('')
const acceptForm = reactive({ passed: true, reason: '' })
const acceptRules = {
  passed: [{ required: true, message: '请选择验收结论', trigger: 'change' }],
  reason: [{ required: true, whitespace: true, message: '请填写验收说明', trigger: 'blur' }]
}

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  repairNo: '',
  deviceId: '',
  status: '',
  sortBy: 'createTime',
  sortDirection: 'desc'
})

async function getList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listRepairOrders({
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      repairNo: queryParams.repairNo || undefined,
      deviceId: queryParams.deviceId || undefined,
      status: queryParams.status || undefined,
      sortBy: queryParams.sortBy,
      sortDirection: queryParams.sortDirection
    })
    rows.value = Array.isArray(response.rows) ? response.rows : []
    total.value = response.total ?? 0
  } catch (error) {
    rows.value = []
    total.value = 0
    errorMessage.value = messageOf(error, '维修工单加载失败')
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryRef.value?.resetFields()
  handleQuery()
}

function openDetail(row) {
  detailId.value = String(row.id)
  detailOpen.value = true
}

watch(
  () => [route.name, route.params.id],
  ([name, id]) => {
    if (name === 'LabRepairDetail' && id) {
      detailId.value = String(id)
      detailOpen.value = true
    }
  },
  { immediate: true }
)

async function openReport() {
  reportForm.deviceId = ''
  reportForm.description = ''
  reportError.value = ''
  reportOpen.value = true
  if (!devices.value.length) await loadDeviceOptions(reportError)
  nextTick(() => reportRef.value?.clearValidate())
}

async function submitReport() {
  if (!await reportRef.value?.validate().catch(() => false)) return
  submitting.value = true
  reportError.value = ''
  try {
    await reportFault({
      deviceId: String(reportForm.deviceId),
      description: reportForm.description.trim()
    })
    proxy.$modal.msgSuccess('故障已报告')
    reportOpen.value = false
    await getList()
  } catch (error) {
    reportError.value = messageOf(error, '故障报告提交失败')
  } finally {
    submitting.value = false
  }
}

async function handleAssign(row) {
  assignRow.value = row
  assignForm.assigneeId = ''
  assignOpen.value = true
  if (!workerOptions.value.length) await loadWorkerOptions()
  nextTick(() => assignRef.value?.clearValidate())
}

async function submitAssign() {
  if (!await assignRef.value?.validate().catch(() => false)) return
  submitting.value = true
  actionId.value = String(assignRow.value.id)
  try {
    await assignRepair(actionId.value, { assigneeId: assignForm.assigneeId })
    proxy.$modal.msgSuccess('维修任务已分派')
    assignOpen.value = false
    await getList()
  } catch (error) {
    errorMessage.value = messageOf(error, '维修分派失败')
  } finally {
    actionId.value = ''
    submitting.value = false
  }
}

async function loadDeviceOptions(errorTarget) {
  deviceLoading.value = true
  try {
    const response = await loadAllOptions(listRepairDevices, { sortBy: 'assetNo', sortDirection: 'asc' })
    devices.value = Array.isArray(response.rows) ? response.rows : []
  } catch (error) {
    devices.value = []
    if (errorTarget) errorTarget.value = messageOf(error, '设备列表加载失败')
  } finally {
    deviceLoading.value = false
  }
}

async function loadWorkerOptions() {
  try {
    const response = await listLabUserOptions({ roleKey: 'lab_repair_worker' })
    workerOptions.value = (response.data || []).map(item => ({
      id: String(item.id),
      label: `${item.displayName || item.userName}（${item.userName}）`
    }))
  } catch (error) {
    errorMessage.value = messageOf(error, '维修人员列表加载失败')
    workerOptions.value = []
  }
}

function assigneeLabel(id) {
  if (!id) return '-'
  return workerOptions.value.find(item => item.id === String(id))?.label || `用户 ${id}`
}

async function handleStart(row) {
  try {
    await proxy.$modal.confirm(`确认开始处理维修工单 ${row.repairNo}？`)
    actionId.value = String(row.id)
    await startRepair(String(row.id))
    proxy.$modal.msgSuccess('维修已开始')
    await getList()
  } catch (error) {
    if (error && error !== 'cancel' && error !== 'close') errorMessage.value = messageOf(error, '开始维修失败')
  } finally {
    actionId.value = ''
  }
}

async function handleSubmitResult(row) {
  try {
    const { value } = await proxy.$modal.prompt('请输入维修结果')
    const result = value?.trim()
    if (!result) {
      proxy.$modal.msgWarning('维修结果不能为空')
      return
    }
    actionId.value = String(row.id)
    await submitRepairResult(String(row.id), { result })
    proxy.$modal.msgSuccess('维修结果已提交，等待验收')
    await getList()
  } catch (error) {
    if (error && error !== 'cancel' && error !== 'close') errorMessage.value = messageOf(error, '维修结果提交失败')
  } finally {
    actionId.value = ''
  }
}

function openAccept(row) {
  acceptId.value = String(row.id)
  acceptForm.passed = true
  acceptForm.reason = ''
  acceptError.value = ''
  acceptOpen.value = true
  nextTick(() => acceptRef.value?.clearValidate())
}

async function submitAccept() {
  if (!await acceptRef.value?.validate().catch(() => false)) return
  submitting.value = true
  acceptError.value = ''
  try {
    await acceptRepair(acceptId.value, {
      passed: acceptForm.passed,
      reason: acceptForm.reason.trim()
    })
    proxy.$modal.msgSuccess(acceptForm.passed ? '维修验收通过' : '维修已退回处理')
    acceptOpen.value = false
    await getList()
  } catch (error) {
    acceptError.value = messageOf(error, '维修验收失败')
  } finally {
    submitting.value = false
  }
}

function sourceLabel(source) {
  return { ACTIVE_REPORT: '主动报修', ABNORMAL_RETURN: '异常归还' }[source] ?? source ?? '-'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

function messageOf(error, fallback) {
  return error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? fallback
}

onMounted(() => {
  getList()
  loadDeviceOptions()
  if (proxy.$auth.hasPermi('lab:repair:assign')) loadWorkerOptions()
})
</script>

<style scoped>
.page-heading {
  margin-bottom: 18px;
}

.page-heading h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 22px;
}

.page-heading p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}

.query-select {
  width: 176px;
}

.full-width {
  width: 100%;
}

@media (max-width: 640px) {
  :deep(.el-dialog) {
    width: calc(100% - 24px) !important;
  }
}
</style>
