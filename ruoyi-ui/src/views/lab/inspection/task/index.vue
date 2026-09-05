<template>
  <div class="app-container lab-page">
    <div class="page-heading"><div><h1>巡检任务</h1><p>跟踪任务进度与截止时间，按检查项记录结果并处理异常。</p></div></div>
    <el-form v-show="showSearch" :model="query" inline>
      <el-form-item label="任务状态">
        <el-select v-model="query.status" clearable placeholder="全部" style="width: 170px">
          <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="负责人"><el-select v-model="query.assigneeId" clearable filterable placeholder="全部" style="width: 210px"><el-option v-for="item in userOptions" :key="item.id" :label="item.label" :value="item.id" /></el-select></el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8"><right-toolbar v-model:showSearch="showSearch" @queryTable="loadTasks" /></el-row>
    <p class="lab-table-hint">左右滑动表格可查看完整信息与操作</p>
    <el-table v-loading="loading" :data="tasks">
      <el-table-column label="任务编号" prop="taskNo" min-width="190" />
      <el-table-column label="实验室" min-width="180" show-overflow-tooltip><template #default="{ row }">{{ laboratoryLabel(row.laboratoryId) }}</template></el-table-column>
      <el-table-column label="负责人" min-width="170" show-overflow-tooltip><template #default="{ row }">{{ userLabel(row.assigneeId) }}</template></el-table-column>
      <el-table-column label="计划执行" width="190"><template #default="{ row }">{{ parseTime(row.scheduledAt) }}</template></el-table-column>
      <el-table-column label="截止时间" width="190"><template #default="{ row }"><span :class="{ overdue: row.overdueFlag === '1' }">{{ parseTime(row.deadlineAt) }}</span></template></el-table-column>
      <el-table-column label="状态" width="120" align="center">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-hasPermi="['lab:inspection:task:execute']" @click="openExecute(row)">
            {{ row.status === 'PENDING' ? '开始巡检' : row.status === 'IN_PROGRESS' ? '继续巡检' : '查看结果' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="loadTasks" />
  </div>
</template>

<script setup>
import { loadAllOptions } from '@/utils/labOptions'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listInspectionTasks } from '@/api/lab/inspection'
import { listLaboratory } from '@/api/lab/laboratory'
import { listLabUserOptions } from '@/api/lab/options'

const router = useRouter()
const loading = ref(false)
const showSearch = ref(true)
const tasks = ref([])
const total = ref(0)
const laboratoryOptions = ref([])
const userOptions = ref([])
const query = reactive({ pageNum: 1, pageSize: 10, status: undefined, assigneeId: undefined })
const statuses = [
  { value: 'PENDING', label: '待执行' },
  { value: 'IN_PROGRESS', label: '执行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'OVERDUE', label: '已超期' }
]

function laboratoryLabel(id) { return laboratoryOptions.value.find(item => item.id === String(id))?.label || `实验室 ${id}` }
function userLabel(id) { return userOptions.value.find(item => item.id === String(id))?.label || `用户 ${id}` }
async function loadOptions() {
  await Promise.allSettled([
    loadAllOptions(listLaboratory, { sortBy: 'name', sortDirection: 'asc' }).then(response => {
      laboratoryOptions.value = (response.rows || []).map(item => ({ id: String(item.id), label: `${item.labCode} · ${item.name}` }))
    }),
    listLabUserOptions({ roleKey: 'lab_safety_officer' }).then(response => {
      userOptions.value = (response.data || []).map(item => ({ id: String(item.id), label: `${item.displayName || item.userName}（${item.userName}）` }))
    })
  ])
}

async function loadTasks() {
  loading.value = true
  try {
    const response = await listInspectionTasks(query)
    tasks.value = response.rows || []
    total.value = response.total || 0
  } finally { loading.value = false }
}
function handleQuery() { query.pageNum = 1; void loadTasks() }
function resetQuery() { Object.assign(query, { pageNum: 1, status: undefined, assigneeId: undefined }); void loadTasks() }
function openExecute(row) { router.push(`/lab/inspection/task/execute/${row.id}`) }
function statusText(status) { return statuses.find(item => item.value === status)?.label || status }
function statusType(status) { return { PENDING: 'warning', IN_PROGRESS: 'primary', COMPLETED: 'success', OVERDUE: 'danger' }[status] || 'info' }
loadOptions()
loadTasks()
</script>

<style scoped>.overdue { color: var(--el-color-danger); font-weight: 600; }</style>
