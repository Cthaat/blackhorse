<template>
  <div class="app-container lab-page sla-workspace">
    <div class="page-heading"><div><h1>业务时效与 SLA</h1><p>集中查看维修、维护和整改的响应、处理期限及升级记录。</p></div><el-button :loading="loading" @click="load">刷新队列</el-button></div>
    <el-alert title="仅接入上线后新建的业务，历史对象不回填。按连续自然小时计时；暂停只影响处理计时，超时不会自动验收、销号或恢复设备可用。" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-card shadow="never">
      <el-tabs v-model="tab"><el-tab-pane label="临期／逾期队列" name="records" /><el-tab-pane v-if="canRule" label="规则版本" name="rules" /></el-tabs>
      <template v-if="tab === 'records'">
        <div class="sla-toolbar">
          <el-select v-model="query.businessType" clearable placeholder="全部业务" aria-label="SLA 业务类型" @change="search"><el-option v-for="(label,value) in types" :key="value" :label="label" :value="value" /></el-select>
          <el-select v-model="query.state" clearable placeholder="全部状态" aria-label="SLA 状态" @change="search"><el-option v-for="(label,value) in states" :key="value" :label="label" :value="value" /></el-select>
          <el-checkbox v-model="query.mine" @change="search">仅本人负责</el-checkbox><el-button @click="search">查询</el-button>
        </div>
        <el-table :data="rows" v-loading="loading" empty-text="当前范围没有符合条件的 SLA 记录">
          <el-table-column prop="id" label="编号" width="95" /><el-table-column prop="title" label="业务对象" min-width="220" />
          <el-table-column label="业务" width="130"><template #default="{row}">{{ types[row.businessType] }}</template></el-table-column>
          <el-table-column label="状态" width="150"><template #default="{row}"><el-tag :type="row.state === 'OVERDUE' ? 'danger' : row.state === 'NEAR_DUE' ? 'warning' : 'info'">{{ states[row.state] }}</el-tag></template></el-table-column>
          <el-table-column label="责任人" min-width="130"><template #default="{row}">{{ row.ownerName || `用户 #${row.ownerId}` }}</template></el-table-column>
          <el-table-column label="响应截止" min-width="180"><template #default="{row}">{{ parseTime(row.responseDueAt) }}</template></el-table-column>
          <el-table-column label="处理截止" min-width="180"><template #default="{row}">{{ parseTime(row.processingDueAt) }}</template></el-table-column>
          <el-table-column label="操作" width="140"><template #default="{row}"><el-button link type="primary" @click="detail(row.id)">详情／计时</el-button></template></el-table-column>
        </el-table>
        <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
      </template>
      <RulePanel v-else-if="canRule" :laboratories="laboratories" />
    </el-card>
    <el-dialog v-model="detailOpen" title="SLA 快照与追溯" width="min(940px,96vw)" append-to-body>
      <el-skeleton v-if="detailLoading" :rows="6" animated /><el-alert v-if="detailError" :title="detailError" type="error" :closable="false" />
      <RecordDetail v-if="selected && !detailLoading" :record="selected.record" :history="selected.history" :alerts="selected.alerts" @changed="changed" />
    </el-dialog>
  </div>
</template>
<script setup>
import { onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { checkPermi } from '@/utils/permission'
import { parseTime } from '@/utils/ruoyi'
import { loadAllOptions } from '@/utils/labOptions'
import { listLaboratory } from '@/api/lab/laboratory'
import { listSlaRecords, getSlaRecord } from '@/api/lab/sla'
import { types, states } from './presentation'
import RulePanel from './RulePanel.vue'
import RecordDetail from './RecordDetail.vue'
const canRule = checkPermi(['lab:sla:rule']), tab = ref('records'), laboratories = ref([])
const route = useRoute()
const rows = ref([]), total = ref(0), loading = ref(false), error = ref('')
const detailOpen = ref(false), detailLoading = ref(false), detailError = ref(''), selected = ref(null)
const query = reactive({ businessType: '', state: '', mine: false, pageNum: 1, pageSize: 10 })
let sequence = 0, detailSequence = 0, disposed = false
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''
  try { const response = await listSlaRecords({ ...query, businessType: query.businessType || undefined, state: query.state || undefined }); if (current === sequence) { rows.value = response.rows; total.value = Number(response.total) } }
  catch (failure) { if (current === sequence) { rows.value = []; total.value = 0; error.value = failure.message || 'SLA 队列加载失败' } }
  finally { if (current === sequence) loading.value = false }
}
function search() { query.pageNum = 1; load() }
async function detail(id) {
  const current = ++detailSequence; selected.value = null; detailOpen.value = true; detailLoading.value = true; detailError.value = ''
  try { const response = await getSlaRecord(id); if (current === detailSequence) selected.value = response.data }
  catch (failure) { if (current === detailSequence) detailError.value = failure.message || 'SLA 详情加载失败' }
  finally { if (current === detailSequence) detailLoading.value = false }
}
function changed(id) { load(); if (detailOpen.value && String(selected.value?.record.id) === String(id)) detail(id) }
watch(() => route.query.recordId, id => {
  if (typeof id === 'string' && /^[1-9]\d{0,18}$/.test(id)) { tab.value = 'records'; detail(id) }
}, { immediate: true })
onMounted(async () => {
  load()
  if (canRule) { try { const response = await loadAllOptions(listLaboratory); if (!disposed) laboratories.value = response.rows } catch (failure) { if (!disposed) error.value = failure.message || '规则实验室选项加载失败' } }
})
onBeforeUnmount(() => { disposed = true; ++sequence; ++detailSequence })
</script>
<style scoped>.sla-workspace > .el-alert { margin-bottom: 16px; }.sla-toolbar { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; margin-bottom: 16px; }.sla-toolbar .el-select { width: 230px; max-width: 100%; }</style>
