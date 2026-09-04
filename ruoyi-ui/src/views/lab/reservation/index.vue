<template>
  <div class="app-container reservation-page">
    <div class="page-heading">
      <div>
        <h1>{{ approvalMode ? '预约审批' : '我的预约' }}</h1>
        <p>{{ approvalMode ? '审核数据范围内的实验室预约申请。' : '查看申请进度，并提交或取消自己的预约。' }}</p>
      </div>
      <el-tag :type="approvalMode ? 'warning' : 'primary'" effect="plain">
        {{ approvalMode ? '审批模式' : '个人模式' }}
      </el-tag>
    </div>

    <el-form ref="queryRef" :model="queryParams" :inline="true" class="query-form">
      <el-form-item label="预约编号" prop="reservationNo">
        <el-input v-model="queryParams.reservationNo" clearable placeholder="请输入预约编号" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="设备 ID" prop="deviceId">
        <el-input v-model="queryParams.deviceId" clearable placeholder="请输入设备 ID" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item v-if="approvalMode" label="申请人 ID" prop="applicantId">
        <el-input v-model="queryParams.applicantId" clearable placeholder="请输入申请人 ID" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="全部状态" class="query-select">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="预约日期">
        <el-date-picker
          v-model="queryParams.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col v-if="!approvalMode" :span="1.5">
        <el-button type="primary" plain icon="Plus" v-hasPermi="['lab:reservation:apply']" @click="applyOpen = true">
          申请预约
        </el-button>
      </el-col>
      <right-toolbar :show-search="false" @queryTable="getList" />
    </el-row>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="mb16"
    >
      <template #default><el-button link type="primary" @click="getList">重新加载</el-button></template>
    </el-alert>

    <el-table v-loading="loading" :data="rows" row-key="id">
      <el-table-column label="预约编号" prop="reservationNo" min-width="176" show-overflow-tooltip />
      <el-table-column label="设备 ID" prop="deviceId" min-width="120" />
      <el-table-column v-if="approvalMode" label="申请人 ID" prop="applicantId" min-width="120" />
      <el-table-column label="预约时段" min-width="285">
        <template #default="scope">
          <span>{{ formatDateTime(scope.row.startTime) }} — {{ formatDateTime(scope.row.endTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="用途" prop="purpose" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)" effect="light">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="164">
        <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" min-width="270" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="View" v-hasPermi="['lab:reservation:list', 'lab:reservation:mine']" @click="openDetail(scope.row)">详情</el-button>
          <el-button
            v-if="!approvalMode && ['PENDING', 'APPROVED'].includes(scope.row.status)"
            link type="danger" icon="Close"
            :loading="actionId === String(scope.row.id)"
            v-hasPermi="['lab:reservation:cancel']"
            @click="handleCancel(scope.row)"
          >取消</el-button>
          <template v-if="approvalMode && scope.row.status === 'PENDING'">
            <el-button link type="success" icon="Select" :loading="actionId === String(scope.row.id)" v-hasPermi="['lab:reservation:approve']" @click="handleDecision(scope.row, true)">批准</el-button>
            <el-button link type="danger" icon="CloseBold" :loading="actionId === String(scope.row.id)" v-hasPermi="['lab:reservation:reject']" @click="handleDecision(scope.row, false)">驳回</el-button>
          </template>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="errorMessage ? '数据加载失败' : (approvalMode ? '暂无待处理预约' : '暂无预约记录')" />
      </template>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <ReservationApply v-model="applyOpen" @saved="getList" />
    <ReservationDetail v-model="detailOpen" :reservation-id="detailId" />
  </div>
</template>

<script setup name="LabReservation">
import ReservationApply from './apply.vue'
import ReservationDetail from './detail.vue'
import {
  approveReservation,
  cancelReservation,
  listReservations,
  rejectReservation
} from '@/api/lab/reservation'

const route = useRoute()
const { proxy } = getCurrentInstance()
const queryRef = ref()
const loading = ref(false)
const errorMessage = ref('')
const rows = ref([])
const total = ref(0)
const applyOpen = ref(false)
const detailOpen = ref(false)
const detailId = ref('')
const actionId = ref('')

const approvalMode = computed(() => route.query.mode === 'approval')
const statusOptions = [
  { value: 'PENDING', label: '待审批' },
  { value: 'APPROVED', label: '已批准' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'NO_SHOW', label: '已爽约' },
  { value: 'CHECKED_OUT', label: '使用中' },
  { value: 'COMPLETED', label: '已完成' }
]

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  reservationNo: '',
  deviceId: '',
  applicantId: '',
  status: '',
  dateRange: [],
  sortBy: 'createTime',
  sortDirection: 'desc'
})

function withShanghaiOffset(value) {
  return value ? `${value}+08:00` : undefined
}

function requestQuery() {
  return {
    pageNum: queryParams.pageNum,
    pageSize: queryParams.pageSize,
    reservationNo: queryParams.reservationNo || undefined,
    deviceId: queryParams.deviceId || undefined,
    applicantId: approvalMode.value && queryParams.applicantId ? queryParams.applicantId : undefined,
    status: queryParams.status || undefined,
    from: withShanghaiOffset(queryParams.dateRange?.[0] ? `${queryParams.dateRange[0]}T00:00:00` : ''),
    to: withShanghaiOffset(queryParams.dateRange?.[1] ? `${queryParams.dateRange[1]}T23:59:59` : ''),
    sortBy: queryParams.sortBy,
    sortDirection: queryParams.sortDirection
  }
}

async function getList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listReservations(requestQuery())
    rows.value = Array.isArray(response.rows) ? response.rows : []
    total.value = response.total ?? 0
  } catch (error) {
    rows.value = []
    total.value = 0
    errorMessage.value = messageOf(error, '预约列表加载失败')
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
  queryParams.dateRange = []
  queryParams.status = approvalMode.value ? 'PENDING' : ''
  handleQuery()
}

function openDetail(row) {
  detailId.value = String(row.id)
  detailOpen.value = true
}

watch(
  () => [route.name, route.params.id],
  ([name, id]) => {
    if (name === 'LabReservationApply') {
      applyOpen.value = true
    }
    if (name === 'LabReservationDetail' && id) {
      detailId.value = String(id)
      detailOpen.value = true
    }
  },
  { immediate: true }
)

async function handleCancel(row) {
  try {
    const { value } = await proxy.$modal.prompt('请输入取消原因（可留空）')
    actionId.value = String(row.id)
    await cancelReservation(String(row.id), {
      expectedVersion: row.version,
      reason: value?.trim() || null
    })
    proxy.$modal.msgSuccess('预约已取消')
    await getList()
  } catch (error) {
    if (error && error !== 'cancel' && error !== 'close') errorMessage.value = messageOf(error, '取消预约失败')
  } finally {
    actionId.value = ''
  }
}

async function handleDecision(row, approved) {
  try {
    const { value } = await proxy.$modal.prompt(`请输入${approved ? '批准' : '驳回'}原因`)
    const reason = value?.trim()
    if (!reason) {
      proxy.$modal.msgWarning('审批原因不能为空')
      return
    }
    actionId.value = String(row.id)
    const command = { expectedVersion: row.version, reason }
    if (approved) await approveReservation(String(row.id), command)
    else await rejectReservation(String(row.id), command)
    proxy.$modal.msgSuccess(approved ? '预约已批准' : '预约已驳回')
    await getList()
  } catch (error) {
    if (error && error !== 'cancel' && error !== 'close') errorMessage.value = messageOf(error, '审批操作失败')
  } finally {
    actionId.value = ''
  }
}

function statusLabel(status) {
  return statusOptions.find(item => item.value === status)?.label ?? status ?? '-'
}

function statusType(status) {
  return {
    PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info',
    EXPIRED: 'info', NO_SHOW: 'danger', CHECKED_OUT: 'primary', COMPLETED: 'success'
  }[status] ?? 'info'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

function messageOf(error, fallback) {
  return error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? fallback
}

watch(approvalMode, value => {
  queryParams.status = value ? 'PENDING' : ''
  queryParams.applicantId = ''
  queryParams.pageNum = 1
  getList()
}, { immediate: true })
</script>

<style scoped>
.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.query-form :deep(.el-input),
.query-select {
  width: 190px;
}

@media (max-width: 768px) {
  .page-heading {
    flex-direction: column;
  }
}
</style>
