<template>
  <div class="app-container lab-page dashboard-page" v-loading="loading">
    <div class="page-heading">
      <div><span class="workspace-eyebrow">LABORATORY WORKSPACE</span><h1>实验室工作台</h1><p>{{ userStore.nickName }}，欢迎回来。让每一次实验安全、有序地开展。</p></div>
      <el-tag effect="plain" type="info">按当前角色与管理范围展示</el-tag>
    </div>
    <section class="overview-section">
    <div class="toolbar">
      <div class="overview-title"><h2>业务概览</h2><p>待办为当前状态，统计默认近 30 天</p></div>
      <div class="filters">
        <el-date-picker v-model="dateRange" aria-label="统计时间范围" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ssZ" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" />
        <el-button type="primary" icon="Search" @click="loadSummary">统计</el-button>
        <el-button icon="Refresh" @click="resetWindow">近30天</el-button>
      </div>
    </div>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon class="mb16">
      <el-button link @click="loadSummary">重新加载</el-button>
    </el-alert>
    <el-row :gutter="16" class="todo-grid">
      <el-col v-for="card in todoCards" :key="card.key" :xs="12" :sm="8" :lg="4">
        <router-link class="metric-card" :to="card.path">
          <div class="metric-label">{{ card.label }} <span aria-hidden="true">↗</span></div><div class="metric-value">{{ errorMessage ? '—' : (summary[card.key] ?? 0) }}</div>
        </router-link>
      </el-col>
    </el-row>

    </section>
    <WorkspaceEntries class="dashboard-entries" />
    <h2 v-if="canViewStatus && !errorMessage" class="status-heading">运行状态</h2>
    <el-row v-if="canViewStatus && !errorMessage" :gutter="16" class="chart-grid">
      <el-col v-if="hasPermission('lab:device:list')" :xs="24" :lg="12"><MetricPanel title="设备状态" :labels="labels.device" :total="summary.totalDevices" :items="summary.deviceStatusCounts" /></el-col>
      <el-col v-if="canViewReservations" :xs="24" :lg="12"><MetricPanel title="预约状态" :labels="labels.reservation" :total="summary.totalReservations" :items="summary.reservationStatusCounts" /></el-col>
      <el-col v-if="hasPermission('lab:repair:list')" :xs="24" :lg="12"><MetricPanel title="维修状态" :labels="labels.repair" :total="summary.totalRepairs" :items="summary.repairStatusCounts" /></el-col>
      <el-col v-if="hasPermission('lab:hazard:list')" :xs="24" :lg="12"><MetricPanel title="隐患状态" :labels="labels.hazard" :total="summary.totalHazards" :items="summary.hazardStatusCounts">
        <template #extra><el-progress type="dashboard" :percentage="closureRate" :width="92"><template #default>销号率<br>{{ closureRate }}%</template></el-progress></template>
      </MetricPanel></el-col>
    </el-row>

    <el-row :gutter="16" class="summary-footer" v-if="canViewTotals && !errorMessage">
      <el-col v-if="hasPermission('lab:usage:list')" :xs="24" :sm="8"><el-statistic title="统计周期领用时长" :value="summary.usageMinutes || 0"><template #suffix>分钟</template></el-statistic></el-col>
      <el-col v-if="hasPermission('lab:hazard:list')" :xs="24" :sm="8"><el-statistic title="隐患总数" :value="summary.totalHazards || 0" /></el-col>
      <el-col v-if="hasPermission('lab:hazard:list')" :xs="24" :sm="8"><el-statistic title="已销号隐患" :value="summary.closedHazards || 0" /></el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, ref } from 'vue'
import { ElCard, ElEmpty, ElProgress, ElTag } from 'element-plus'
import { getDashboardSummary } from '@/api/lab/dashboard'
import WorkspaceEntries from '@/components/lab/WorkspaceEntries.vue'
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
const loading = ref(false)
const dateRange = ref([])
const summary = ref({})
const errorMessage = ref('')
const hasPermission = permission => userStore.permissions.includes('*:*:*') || userStore.permissions.includes(permission)
const canViewReservations = computed(() => hasPermission('lab:reservation:list') || hasPermission('lab:reservation:mine'))
const canViewStatus = computed(() => canViewReservations.value || ['lab:device:list', 'lab:repair:list', 'lab:hazard:list'].some(hasPermission))
const canViewTotals = computed(() => ['lab:usage:list', 'lab:hazard:list'].some(hasPermission))
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
.app-main > .dashboard-page { background: transparent; border: 0; padding: 0; }
.workspace-eyebrow { display: block; color: var(--lab-accent); font-size: 11px; font-weight: 700; letter-spacing: 2px; margin-bottom: 12px; }
.overview-section { border: 1px solid var(--lab-border); border-radius: 12px; padding: 24px; background: var(--lab-surface); }
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 24px; }
.toolbar h2, .status-heading { margin: 0; font-size: 18px; font-weight: 650; }
.toolbar p { margin: 8px 0 0; font-size: 12px; color: var(--lab-muted); }
.filters { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.filters :deep(.el-date-editor) { width: 340px; max-width: 100%; flex-grow: 0; }
.filters .el-button + .el-button { margin-left: 0; }
.overview-title { flex-shrink: 0; }
.todo-grid { row-gap: 12px; }
.metric-card { display: block; height: 100%; border-left: 3px solid var(--el-color-primary-light-5); border-radius: 4px; padding: 12px; background: var(--lab-canvas); transition: background .15s; }
.metric-card:hover { background: var(--lab-soft); }
.metric-label { font-size: 12px; line-height: 1.6; color: var(--lab-muted); min-height: 38px; }
.metric-label span { float: right; }
.metric-value { margin-top: 8px; font-size: 30px; font-weight: 650; color: var(--lab-ink); font-variant-numeric: tabular-nums; }
.dashboard-entries { margin: 20px 0 28px; }
.chart-grid { margin-top: 16px; row-gap: 16px; }
.chart-grid :deep(.status-panel) { border-color: var(--lab-border); border-radius: 12px; background: var(--lab-surface); }
.chart-grid :deep(.panel-header) { display: flex; justify-content: space-between; font-size: 14px; }
.chart-grid :deep(.panel-header strong) { font-size: 12px; color: var(--lab-muted); }
.chart-grid :deep(.panel-content) { display: flex; justify-content: space-between; gap: 20px; min-height: 120px; }
.chart-grid :deep(.status-list) { flex: 1; }
.chart-grid :deep(.status-row) { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--lab-border); }
.summary-footer { margin-top: 20px; padding: 20px; background: var(--lab-surface); border: 1px solid var(--lab-border); border-radius: 12px; text-align: center; row-gap: 20px; }
@media (max-width: 1100px) { .toolbar { flex-direction: column; } .filters { justify-content: flex-start; width: 100%; } }
@media (max-width: 767px) { .overview-section { padding: 16px; } .filters :deep(.el-date-editor) { width: 100%; } .metric-label { min-height: 38px; } }
</style>
