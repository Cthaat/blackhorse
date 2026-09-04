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

    <el-row :gutter="16" class="todo-grid">
      <el-col v-for="card in todoCards" :key="card.key" :xs="12" :sm="8" :lg="4">
        <el-card shadow="hover" class="metric-card" @click="card.path && router.push(card.path)">
          <div class="metric-label">{{ card.label }}</div><div class="metric-value">{{ summary[card.key] ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-grid">
      <el-col :xs="24" :lg="12"><MetricPanel title="设备状态" :total="summary.totalDevices" :items="summary.deviceStatusCounts" /></el-col>
      <el-col :xs="24" :lg="12"><MetricPanel title="预约状态" :total="summary.totalReservations" :items="summary.reservationStatusCounts" /></el-col>
      <el-col :xs="24" :lg="12"><MetricPanel title="维修状态" :total="summary.totalRepairs" :items="summary.repairStatusCounts" /></el-col>
      <el-col :xs="24" :lg="12"><MetricPanel title="隐患状态" :total="summary.totalHazards" :items="summary.hazardStatusCounts">
        <template #extra><el-progress type="dashboard" :percentage="closureRate" :width="92"><template #default>销号率<br>{{ closureRate }}%</template></el-progress></template>
      </MetricPanel></el-col>
    </el-row>

    <el-row :gutter="16" class="summary-footer">
      <el-col :xs="24" :sm="8"><el-statistic title="统计周期领用时长" :value="summary.usageMinutes || 0"><template #suffix>分钟</template></el-statistic></el-col>
      <el-col :xs="24" :sm="8"><el-statistic title="隐患总数" :value="summary.totalHazards || 0" /></el-col>
      <el-col :xs="24" :sm="8"><el-statistic title="已销号隐患" :value="summary.closedHazards || 0" /></el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, ref } from 'vue'
import { ElCard, ElEmpty, ElProgress, ElTag } from 'element-plus'
import { useRouter } from 'vue-router'
import { getDashboardSummary } from '@/api/lab/dashboard'

const router = useRouter()
const loading = ref(false)
const dateRange = ref([])
const summary = ref({})
const todoCards = [
  { key: 'pendingReservations', label: '待处理预约', path: '/lab/reservations/approval' },
  { key: 'openUsage', label: '未归还领用', path: '/lab/usage' },
  { key: 'openRepairs', label: '未关闭维修', path: '/lab/repair' },
  { key: 'pendingInspections', label: '待执行巡检', path: '/lab/safety/inspection-tasks' },
  { key: 'openHazards', label: '未销号隐患', path: '/lab/safety/hazards' },
  { key: 'unreadNotifications', label: '未读消息', path: '/lab/notifications' }
]
const closureRate = computed(() => Math.min(100, Math.max(0, Number(summary.value.hazardClosureRate || 0))))

const MetricPanel = defineComponent({
  props: { title: String, total: [Number, String], items: { type: Array, default: () => [] } },
  setup(props, { slots }) {
    return () => h(ElCard, { shadow: 'never', class: 'status-panel' }, {
      header: () => h('div', { class: 'panel-header' }, [h('span', props.title), h('strong', `总计 ${props.total || 0}`)]),
      default: () => h('div', { class: 'panel-content' }, [
        h('div', { class: 'status-list' }, props.items.length
          ? props.items.map(item => h('div', { class: 'status-row', key: item.code }, [h(ElTag, { effect: 'plain' }, () => item.code), h('strong', item.value)]))
          : [h(ElEmpty, { description: '暂无数据', imageSize: 52 })]),
        slots.extra?.()
      ])
    })
  }
})

async function loadSummary() {
  loading.value = true
  try {
    const params = dateRange.value?.length === 2 ? { startTime: dateRange.value[0], endTime: dateRange.value[1] } : {}
    const response = await getDashboardSummary(params)
    summary.value = response.data || {}
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
