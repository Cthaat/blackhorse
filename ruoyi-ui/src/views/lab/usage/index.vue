<template>
  <div class="app-container lab-page usage-page">
    <div class="page-heading">
      <div>
        <h1>设备领用归还</h1>
        <p>依据已批准预约办理领用，记录归还状况并自动衔接异常报修。</p>
      </div>
    </div>

    <el-form ref="queryRef" :model="queryParams" :inline="true">
      <el-form-item label="预约编号" prop="reservationNo">
        <el-input v-model="queryParams.reservationNo" clearable placeholder="请输入预约编号" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="资产编号" prop="assetNo">
        <el-input v-model="queryParams.assetNo" clearable placeholder="请输入资产编号" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="归还状态" prop="returnCondition">
        <el-select v-model="queryParams.returnCondition" clearable placeholder="全部状态" class="query-select">
          <el-option v-for="dict in lab_return_condition" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="领用时间">
        <el-date-picker
          v-model="queryParams.checkedOutRange"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          format="YYYY-MM-DD HH:mm"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="TakeawayBox" v-hasPermi="['lab:usage:checkout']" @click="openCheckout">
          办理领用
        </el-button>
      </el-col>
      <right-toolbar :search="false" @queryTable="getList" />
    </el-row>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="mb16">
      <template #default><el-button link type="primary" @click="getList">重新加载</el-button></template>
    </el-alert>

    <p class="lab-table-hint">左右滑动表格可查看完整信息与操作</p>

    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column label="记录 ID" prop="id" min-width="118" />
      <el-table-column label="预约编号" prop="reservationNo" min-width="170" show-overflow-tooltip />
      <el-table-column label="资产编号" prop="assetNo" min-width="140" />
      <el-table-column label="设备名称" prop="deviceName" min-width="150" show-overflow-tooltip />
      <el-table-column label="领用时间" min-width="166">
        <template #default="scope">{{ formatDateTime(scope.row.checkedOutAt) }}</template>
      </el-table-column>
      <el-table-column label="归还时间" min-width="166">
        <template #default="scope">{{ formatDateTime(scope.row.returnedAt) }}</template>
      </el-table-column>
      <el-table-column label="归还状况" width="108" align="center">
        <template #default="scope">
          <el-tag v-if="!scope.row.returnedAt" type="primary" effect="light">使用中</el-tag>
          <dict-tag v-else :options="lab_return_condition" :value="scope.row.returnCondition" />
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" min-width="176" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="View" v-hasPermi="['lab:usage:query']" @click="openDetail(scope.row)">详情</el-button>
          <el-button
            v-if="!scope.row.returnedAt"
            link type="success" icon="Finished"
            v-hasPermi="['lab:usage:return']"
            @click="openReturn(scope.row)"
          >归还</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty :description="errorMessage ? '数据加载失败' : '暂无领用记录'" /></template>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog v-model="checkoutOpen" title="办理设备领用" width="620px" append-to-body :close-on-click-modal="false">
      <el-alert v-if="checkoutError" :title="checkoutError" type="error" show-icon :closable="false" class="mb16" />
      <el-form ref="checkoutRef" :model="checkoutForm" :rules="checkoutRules" label-width="96px">
        <el-form-item label="批准预约" prop="reservationId">
          <el-select
            v-model="checkoutForm.reservationId"
            filterable
            clearable
            class="full-width"
            placeholder="请选择已批准预约"
            :loading="reservationLoading"
            no-data-text="暂无可领用预约"
          >
            <el-option
              v-for="item in approvedReservations"
              :key="String(item.id)"
              :value="String(item.id)"
              :label="`${item.reservationNo} · 设备 ${item.deviceId}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="领用备注" prop="note">
          <el-input v-model="checkoutForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="可记录交接状态（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="checkoutOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCheckout">确认领用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="returnOpen" title="设备归还" width="620px" append-to-body :close-on-click-modal="false">
      <el-alert v-if="returnError" :title="returnError" type="error" show-icon :closable="false" class="mb16" />
      <el-form ref="returnRef" :model="returnForm" :rules="returnRules" label-width="96px">
        <el-form-item label="归还状况" prop="condition">
          <el-radio-group v-model="returnForm.condition">
            <el-radio v-for="dict in lab_return_condition" :key="dict.value" :value="dict.value">{{ dict.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="归还备注" prop="note">
          <el-input v-model="returnForm.note" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item v-if="returnForm.condition && returnForm.condition !== 'NORMAL'" label="故障描述" prop="faultDescription">
          <el-input v-model="returnForm.faultDescription" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="请描述损坏或故障现象，归还后将自动生成维修工单" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="returnOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReturn">确认归还</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailOpen" title="领用详情" size="min(680px, 94vw)" append-to-body>
      <div v-loading="detailLoading" class="detail-body">
        <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false">
          <template #default><el-button link type="primary" @click="loadDetail">重新加载</el-button></template>
        </el-alert>
        <el-empty v-else-if="!detailLoading && !detail" description="未找到领用记录" />
        <lab-descriptions v-else-if="detail" :column="2" border>
          <el-descriptions-item label="记录 ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="预约 ID">{{ detail.reservationId }}</el-descriptions-item>
          <el-descriptions-item label="预约编号">{{ detail.reservationNo }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="设备">{{ detail.assetNo }} · {{ detail.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="设备 ID">{{ detail.deviceId }}</el-descriptions-item>
          <el-descriptions-item label="领用时间">{{ formatDateTime(detail.checkedOutAt) }}</el-descriptions-item>
          <el-descriptions-item label="归还时间">{{ formatDateTime(detail.returnedAt) }}</el-descriptions-item>
          <el-descriptions-item label="领用备注" :span="2">{{ detail.checkoutNote || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归还状况">{{ conditionLabel(detail.returnCondition) }}</el-descriptions-item>
          <el-descriptions-item label="关联维修 ID">{{ detail.repairOrderId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归还备注" :span="2">{{ detail.returnNote || '-' }}</el-descriptions-item>
        </lab-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="LabUsage">
import { loadAllOptions } from '@/utils/labOptions'
import { listReservations } from '@/api/lab/reservation'
import { checkOutDevice, getUsageRecord, listUsageRecords, returnDevice } from '@/api/lab/usage'

const { proxy } = getCurrentInstance()
const route = useRoute()
const { lab_return_condition } = useDict('lab_return_condition')
const queryRef = ref()
const checkoutRef = ref()
const returnRef = ref()
const loading = ref(false)
const errorMessage = ref('')
const rows = ref([])
const total = ref(0)
const submitting = ref(false)

const checkoutOpen = ref(false)
const checkoutError = ref('')
const reservationLoading = ref(false)
const approvedReservations = ref([])
const checkoutForm = reactive({ reservationId: '', note: '' })
const checkoutRules = {
  reservationId: [{ required: true, message: '请选择已批准预约', trigger: 'change' }]
}

const returnOpen = ref(false)
const returnError = ref('')
const returnId = ref('')
const returnForm = reactive({ condition: 'NORMAL', note: '', faultDescription: '' })
const returnRules = {
  condition: [{ required: true, message: '请选择归还状况', trigger: 'change' }],
  faultDescription: [{
    validator: (_rule, value, callback) => {
      if (returnForm.condition !== 'NORMAL' && !value?.trim()) callback(new Error('异常归还必须填写故障描述'))
      else callback()
    },
    trigger: 'blur'
  }]
}

const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailId = ref('')
const detail = ref()

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  reservationNo: '',
  assetNo: typeof route.query.assetNo === 'string' ? route.query.assetNo : '',
  returnCondition: '',
  checkedOutRange: [],
  sortBy: 'id',
  sortDirection: 'desc'
})

function requestQuery() {
  return {
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
    reservationNo: queryParams.reservationNo || undefined,
    assetNo: queryParams.assetNo || undefined,
    returnCondition: queryParams.returnCondition || undefined,
    checkedOutFrom: queryParams.checkedOutRange?.[0] || undefined,
    checkedOutTo: queryParams.checkedOutRange?.[1] || undefined,
    sortBy: queryParams.sortBy,
    sortDirection: queryParams.sortDirection
  }
}

async function getList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listUsageRecords(requestQuery())
    rows.value = Array.isArray(response.rows) ? response.rows : []
    total.value = response.total ?? 0
  } catch (error) {
    rows.value = []
    total.value = 0
    errorMessage.value = messageOf(error, '领用记录加载失败')
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

watch(() => route.query.assetNo, assetNo => {
  queryParams.assetNo = typeof assetNo === 'string' ? assetNo : ''
  handleQuery()
})

function resetQuery() {
  queryRef.value?.resetFields()
  queryParams.checkedOutRange = []
  handleQuery()
}

async function openCheckout() {
  checkoutOpen.value = true
  checkoutError.value = ''
  checkoutForm.reservationId = ''
  checkoutForm.note = ''
  reservationLoading.value = true
  try {
    const deviceId = typeof route.query.deviceId === 'string' && /^[1-9]\d*$/.test(route.query.deviceId) ? route.query.deviceId : undefined
    const response = await loadAllOptions(listReservations, { deviceId, status: 'APPROVED', sortBy: 'startTime', sortDirection: 'asc' })
    approvedReservations.value = Array.isArray(response.rows) ? response.rows : []
  } catch (error) {
    approvedReservations.value = []
    checkoutError.value = messageOf(error, '已批准预约加载失败')
  } finally {
    reservationLoading.value = false
    nextTick(() => checkoutRef.value?.clearValidate())
  }
}

async function submitCheckout() {
  if (!await checkoutRef.value?.validate().catch(() => false)) return
  submitting.value = true
  checkoutError.value = ''
  try {
    await checkOutDevice({
      reservationId: String(checkoutForm.reservationId),
      note: checkoutForm.note.trim() || null
    })
    proxy.$modal.msgSuccess('设备领用成功')
    checkoutOpen.value = false
    await getList()
  } catch (error) {
    checkoutError.value = messageOf(error, '设备领用失败')
  } finally {
    submitting.value = false
  }
}

function openReturn(row) {
  returnId.value = String(row.id)
  returnForm.condition = 'NORMAL'
  returnForm.note = ''
  returnForm.faultDescription = ''
  returnError.value = ''
  returnOpen.value = true
  nextTick(() => returnRef.value?.clearValidate())
}

async function submitReturn() {
  if (!await returnRef.value?.validate().catch(() => false)) return
  submitting.value = true
  returnError.value = ''
  try {
    await returnDevice(returnId.value, {
      condition: returnForm.condition,
      note: returnForm.note.trim() || null,
      faultDescription: returnForm.condition === 'NORMAL' ? null : returnForm.faultDescription.trim()
    })
    proxy.$modal.msgSuccess(returnForm.condition === 'NORMAL' ? '设备归还成功' : '设备已归还并生成维修工单')
    returnOpen.value = false
    await getList()
  } catch (error) {
    returnError.value = messageOf(error, '设备归还失败')
  } finally {
    submitting.value = false
  }
}

function openDetail(row) {
  detailId.value = String(row.id)
  detailOpen.value = true
  loadDetail()
}

async function loadDetail() {
  if (!detailId.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    const response = await getUsageRecord(detailId.value)
    detail.value = response.data
  } catch (error) {
    detail.value = undefined
    detailError.value = messageOf(error, '领用详情加载失败')
  } finally {
    detailLoading.value = false
  }
}

function conditionLabel(value) {
  return lab_return_condition.value?.find(item => item.value === value)?.label ?? value ?? '未归还'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

function messageOf(error, fallback) {
  return error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? fallback
}

onMounted(getList)
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

.detail-body {
  min-height: 220px;
}

@media (max-width: 640px) {
  :deep(.el-dialog) {
    width: calc(100% - 24px) !important;
  }
}
</style>
