<template>
  <div class="app-container lab-page maintenance-workspace">
    <div class="page-heading"><div><h1>维护与计量校准</h1><p>按周期保养设备，保留计划版本、停用安排和验收报告。</p></div><el-button v-if="canEdit" type="primary" @click="edit(null)">新建计划</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重试</el-button></el-alert>
    <el-alert v-if="optionsError" :title="optionsError" type="warning" :closable="false"><el-button link @click="loadOptions">重载选项</el-button></el-alert>
    <el-card shadow="never">
      <div class="maintenance-toolbar">
        <el-select v-model="query.deviceId" filterable clearable placeholder="全部设备" aria-label="维护设备筛选" @change="search"><el-option v-for="item in devices" :key="item.id" :label="`${item.assetNo} · ${item.name}`" :value="String(item.id)" /></el-select>
        <el-select v-if="tab === 'plans'" v-model="query.due" clearable placeholder="全部到期状态" aria-label="维护到期筛选" @change="search"><el-option label="七天内到期" value="SOON" /><el-option label="已逾期" value="OVERDUE" /></el-select>
        <el-select v-if="tab === 'plans'" v-model="query.enabled" clearable placeholder="全部启停状态" @change="search"><el-option label="启用" :value="true" /><el-option label="停用" :value="false" /></el-select>
        <el-select v-else v-model="query.status" clearable placeholder="全部执行状态" @change="search"><el-option v-for="(label,value) in cycleStates" :key="value" :label="label" :value="value" /></el-select>
        <el-button :loading="loading" @click="search">查询</el-button>
      </div>
      <el-tabs v-model="tab" @tab-change="search"><el-tab-pane label="计划与到期工作台" name="plans" /><el-tab-pane label="周期执行" name="cycles" /></el-tabs>
      <el-table v-if="tab === 'plans'" :data="rows" v-loading="loading" empty-text="暂无符合条件的维护计划">
        <el-table-column prop="assetNo" label="资产编号" min-width="160" /><el-table-column prop="deviceName" label="设备" min-width="160" />
        <el-table-column label="类型" width="130"><template #default="{row}">{{ kinds[row.kind] }}</template></el-table-column>
        <el-table-column label="周期" width="95"><template #default="{row}">{{ row.periodDays }} 天</template></el-table-column>
        <el-table-column label="下次到期" min-width="180"><template #default="{row}">{{ parseTime(row.nextDueAt) }}</template></el-table-column>
        <el-table-column label="启停" width="95"><template #default="{row}"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" min-width="220"><template #default="{row}"><el-button link type="primary" @click="detail(row.id)">档案／版本</el-button><el-button v-if="canEdit" link @click="edit(row)">修改</el-button><el-button v-if="canEdit" link :disabled="busy" @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button></template></el-table-column>
      </el-table>
      <CycleTable v-else v-loading="loading" :rows="rows" @changed="load" />
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    </el-card>
    <PlanEditor v-model="editorOpen" :row="editing" :devices="devices" :people="people" @saved="load" />
    <el-dialog v-model="detailOpen" title="维护档案与不可变版本" width="min(1080px,96vw)" append-to-body>
      <el-skeleton v-if="detailLoading" :rows="5" animated />
      <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
      <template v-if="selected">
        <h3>{{ selected.plan.assetNo }} · {{ selected.plan.deviceName }}</h3><p class="pre-wrap">{{ selected.plan.description || '无补充说明' }}</p>
        <el-tabs v-model="detailTab">
          <el-tab-pane label="历史周期（最近100条）" name="cycles"><CycleTable :rows="selected.cycles" @changed="refreshDetail" /></el-tab-pane>
          <el-tab-pane label="版本记录" name="versions"><el-table :data="selected.versions"><el-table-column prop="id" label="版本编号" /><el-table-column prop="periodDays" label="周期天数" /><el-table-column prop="responsibleId" label="负责人编号" /><el-table-column prop="description" label="工作内容" min-width="220" /><el-table-column prop="reason" label="变更原因" min-width="200" /><el-table-column label="发布时间" min-width="180"><template #default="{row}">{{ parseTime(row.createdAt) }}</template></el-table-column></el-table></el-tab-pane>
          <el-tab-pane label="业务追溯" name="history"><el-table :data="selected.history"><el-table-column prop="toStatus" label="动作" /><el-table-column prop="reason" label="原因" min-width="250" /><el-table-column prop="operatorName" label="操作人" /><el-table-column label="时间" min-width="180"><template #default="{row}">{{ parseTime(row.createTime) }}</template></el-table-column></el-table></el-tab-pane>
        </el-tabs>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import { parseTime } from '@/utils/ruoyi'
import { loadAllOptions } from '@/utils/labOptions'
import { listDevice } from '@/api/lab/device'
import { listLabUserOptions } from '@/api/lab/options'
import { listMaintenancePlans, listMaintenanceCycles, getMaintenancePlan, toggleMaintenancePlan } from '@/api/lab/maintenance'
import { kinds, cycleStates, reasonText } from './presentation'
import PlanEditor from './PlanEditor.vue'
import CycleTable from './CycleTable.vue'
const route = useRoute(), canEdit = checkPermi(['lab:maintenance:edit'])
const tab = ref(route.query.view === 'cycles' ? 'cycles' : 'plans'), rows = ref([]), total = ref(0), loading = ref(false), error = ref(''), busy = ref(false)
const devices = ref([]), people = ref([]), optionsError = ref(''), editorOpen = ref(false), editing = ref(null)
const detailOpen = ref(false), detailLoading = ref(false), detailError = ref(''), selected = ref(null), detailTab = ref('cycles')
const query = reactive({ deviceId: typeof route.query.deviceId === 'string' ? route.query.deviceId : '', enabled: '', due: '', status: '', pageNum: 1, pageSize: 10 })
let sequence = 0, detailSequence = 0, disposed = false
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''
  const params = { deviceId: query.deviceId || undefined, pageNum: query.pageNum, pageSize: query.pageSize }
  if (tab.value === 'plans') Object.assign(params, { enabled: query.enabled === '' ? undefined : query.enabled, due: query.due || undefined })
  else params.status = query.status || undefined
  try { const result = await (tab.value === 'plans' ? listMaintenancePlans : listMaintenanceCycles)(params); if (current === sequence) { rows.value = result.rows; total.value = Number(result.total) } }
  catch (failure) { if (current === sequence) { error.value = failure.message || '维护列表加载失败'; rows.value = []; total.value = 0 } }
  finally { if (current === sequence) loading.value = false }
}
function search() { query.pageNum = 1; load() }
async function loadOptions() {
  optionsError.value = ''
  try { const [assets, users] = await Promise.all([loadAllOptions(listDevice), listLabUserOptions({ roleKey: 'lab_repair_worker' })]); if (!disposed) { devices.value = assets.rows; people.value = users.data } }
  catch (failure) { if (!disposed) optionsError.value = failure.message || '选项加载失败' }
}
function edit(row) { editing.value = row ? { ...row } : null; editorOpen.value = true }
async function detail(id) {
  const current = ++detailSequence; detailOpen.value = true; detailLoading.value = true; detailError.value = ''; selected.value = null
  try { const response = await getMaintenancePlan(id); if (current === detailSequence) selected.value = response.data }
  catch (failure) { if (current === detailSequence) detailError.value = failure.message || '维护档案加载失败' }
  finally { if (current === detailSequence) detailLoading.value = false }
}
function refreshDetail() { const id = selected.value?.plan.id; load(); if (id) detail(id) }
async function toggle(row) {
  if (busy.value) return
  const id = row.id, enabled = !row.enabled, expectedVersion = row.version
  busy.value = true
  try { const { value } = await ElMessageBox.prompt('启停不会取消已生成周期，请填写原因。', enabled ? '启用计划' : '停用计划', { inputValidator: value => { try { reasonText(value); return true } catch (failure) { return failure.message } } }); await toggleMaintenancePlan(id, { enabled, expectedVersion, reason: reasonText(value) }); ElMessage.success('计划状态已更新'); await load() }
  catch (failure) { if (failure !== 'cancel' && failure !== 'close') error.value = failure.message || '状态变更失败' }
  finally { busy.value = false }
}
onMounted(() => { load(); loadOptions() })
watch(() => [route.query.deviceId, route.query.view], ([value, view]) => {
  query.deviceId = typeof value === 'string' ? value : ''
  tab.value = view === 'cycles' ? 'cycles' : 'plans'
  search()
})
onBeforeUnmount(() => { disposed = true; ++sequence; ++detailSequence })
</script>
<style scoped>
.maintenance-toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.maintenance-toolbar .el-select { width: 230px; max-width: 100%; }
.pre-wrap { white-space: pre-wrap; overflow-wrap: anywhere; }
.el-alert { margin-bottom: 12px; }
</style>
