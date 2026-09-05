<template>
  <div class="app-container dashboard-page" v-loading="loading">
    <div class="toolbar">
      <div><h2>实验室工作台</h2><p>数据按当前角色和实验室范围实时汇总</p></div>
      <div class="filters">
        <el-date-picker v-model="dateRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ssZ" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" />
        <el-button type="primary" icon="Search" @click="loadSummary">统计</el-button>
        <el-button icon="Refresh" @click="resetWindow">近30天</el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="mb16">
      <el-button link @click="loadSummary">重新加载</el-button>
    </el-alert>
    <el-row :gutter="16" class="todo-grid">
      <el-col v-for="card in todoCards" :key="card.key" :xs="12" :sm="8" :lg="4">
        <el-card shadow="hover" class="metric-card" role="link" tabindex="0" :aria-label="card.label" @keydown.enter="router.push(card.path)" @click="router.push(card.path)">
          <div class="metric-label">{{ card.label }}</div><div class="metric-value">{{ summary[card.key] ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-grid">
      <el-col v-if="hasPermission('lab:device:list')" :xs="24" :lg="12"><MetricPanel title="设备状态" :labels="labels.device" :total="summary.totalDevices" :items="summary.deviceStatusCounts" /></el-col>
      <el-col v-if="canViewReservations" :xs="24" :lg="12"><MetricPanel title="预约状态" :labels="labels.reservation" :total="summary.totalReservations" :items="summary.reservationStatusCounts" /></el-col>
      <el-col v-if="hasPermission('lab:repair:list')" :xs="24" :lg="12"><MetricPanel title="维修状态" :labels="labels.repair" :total="summary.totalRepairs" :items="summary.repairStatusCounts" /></el-col>
      <el-col v-if="hasPermission('lab:hazard:list')" :xs="24" :lg="12"><MetricPanel title="隐患状态" :labels="labels.hazard" :total="summary.totalHazards" :items="summary.hazardStatusCounts">
        <template #extra><el-progress type="dashboard" :percentage="closureRate" :width="92"><template #default>销号率<br>{{ closureRate }}%</template></el-progress></template>
      </MetricPanel></el-col>
    </el-row>

    <el-row :gutter="16" class="summary-footer">
      <el-col v-if="hasPermission('lab:usage:list')" :xs="24" :sm="8"><el-statistic title="统计周期领用时长" :value="summary.usageMinutes || 0"><template #suffix>分钟</template></el-statistic></el-col>
      <el-col v-if="hasPermission('lab:hazard:list')" :xs="24" :sm="8"><el-statistic title="隐患总数" :value="summary.totalHazards || 0" /></el-col>
      <el-col v-if="hasPermission('lab:hazard:list')" :xs="24" :sm="8"><el-statistic title="已销号隐患" :value="summary.closedHazards || 0" /></el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, ref } from 'vue'
import { ElCard, ElEmpty, ElProgress, ElTag } from 'element-plus'
import { useRouter } from 'vue-router'
import { getDashboardSummary } from '@/api/lab/dashboard'
import useUserStore from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const dateRange = ref([])
const summary = ref({})
const errorMessage = ref('')
const hasPermission = permission => userStore.permissions.includes('*:*:*') || userStore.permissions.includes(permission)
const canViewReservations = computed(() => hasPermission('lab:reservation:list') || hasPermission('lab:reservation:mine'))
const labels = {
  device: { AVAILABLE: '可用', IN_USE: '使用中', FAULT: '故障', MAINTENANCE: '维修中', DISABLED: '停用' },
  reservation: { PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '已取消', EXPIRED: '已过期', NO_SHOW: '已爽约', CHECKED_OUT: '使用中', COMPLETED: '已完成' },
  repair: { WAIT_ASSIGN: '待派单', WAIT_REPAIR: '待维修', IN_PROGRESS: '维修中', WAIT_ACCEPTANCE: '待验收', CLOSED: '已关闭' },
  hazard: { PENDING_RECTIFICATION: '待整改', RECTIFYING: '整改中', PENDING_REVIEW: '待复查', CLOSED: '已销号' }
}
const dashboardCards = [
  { key: 'pendingReservations', label: '管理范围待审批预约', path: '/lab/reservations/approval', permission: 'lab:reservation:list' },
  { key: 'openUsage', label: '未归还领用', path: '/lab/usage', permission: 'lab:usage:list' },
  { key: 'openRepairs', label: '未关闭维修', path: '/lab/repair', permission: 'lab:repair:list' },
  { key: 'pendingInspections', label: '待执行巡检', path: '/lab/safety/inspection-tasks', permission: 'lab:inspection:task:list' },
  { key: 'openHazards', label: '未销号隐患', path: '/lab/safety/hazards', permission: 'lab:hazard:list' },
  { key: 'unreadNotifications', label: '未读消息', path: '/lab/notifications', permission: 'lab:notification:list' }
]
const todoCards = computed(() => {
  const cards = dashboardCards.filter(card => hasPermission(card.permission))
  if (!hasPermission('lab:reservation:list') && hasPermission('lab:reservation:mine')) {
    cards.unshift({ key: 'pendingReservations', label: '我的待审批／待领用', path: '/lab/reservations/mine' })
  }
  return cards
})
const closureRate = computed(() => Math.min(100, Math.max(0, Number(summary.value.hazardClosureRate || 0))))

const MetricPanel = defineComponent({
  props: { labels: { type: Object, default: () => ({}) }, title: String, total: [Number, String], items: { type: Array, default: () => [] } },
  setup(props, { slots }) {
    return () => h(ElCard, { shadow: 'never', class: 'status-panel' }, {
      header: () => h('div', { class: 'panel-header' }, [h('span', props.title), h('strong', `总计 ${props.total || 0}`)]),
      default: () => h('div', { class: 'panel-content' }, [
        h('div', { class: 'status-list' }, props.items.length
          ? props.items.map(item => h('div', { class: 'status-row', key: item.code }, [h(ElTag, { effect: 'plain' }, () => props.labels[item.code] || item.code), h('strong', item.value)]))
          : [h(ElEmpty, { description: '暂无数据', imageSize: 52 })]),
        slots.extra?.()
      ])
    })
  }
})

async function loadSummary() {
  loading.value = true
  errorMessage.value = ''
  try {
    const params = dateRange.value?.length === 2 ? { startTime: dateRange.value[0], endTime: dateRange.value[1] } : {}
    const response = await getDashboardSummary(params)
    summary.value = response.data || {}
  } catch (error) {
    summary.value = {}
    errorMessage.value = error?.response?.data?.msg || error?.message || '工作台加载失败'
  } finally { loading.value = false }
}
function resetWindow() { dateRange.value = []; void loadSummary() }
loadSummary()
</script>

<style scoped>
.dashboard-page { background: var(--el-bg-color-page); min-height: calc(100vh - 84px); }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }.toolbar h2 { margin: 0 0 6px; }.toolbar p { margin: 0; color: var(--el-text-color-secondary); }
.filters { display: flex; gap: 10px; }.todo-grid { row-gap: 16px; }.metric-card { cursor: pointer; border: 0; }.metric-label { color: var(--el-text-color-secondary); }.metric-value { margin-top: 8px; font-size: 30px; font-weight: 700; color: var(--el-color-primary); }
.chart-grid { margin-top: 16px; row-gap: 16px; }.panel-header { display: flex; justify-content: space-between; }.panel-content { display: flex; justify-content: space-between; gap: 18px; min-height: 150px; }.status-list { flex: 1; }.status-row { display: flex; justify-content: space-between; align-items: center; padding: 7px 0; border-bottom: 1px dashed var(--el-border-color-lighter); }
.summary-footer { margin-top: 16px; padding: 20px; background: var(--el-bg-color); border-radius: 6px; text-align: center; }
@media (max-width: 900px) { .toolbar { align-items: flex-start; flex-direction: column; gap: 12px; }.filters { width: 100%; flex-wrap: wrap; } }
</style>
