<template>
  <section>
    <h3>已有预约影响预检</h3>
    <el-alert title="仅判断已有预约若现在重新申请是否符合所选规则；不会自动取消或修改预约。" :closable="false" type="info" />
    <el-alert v-if="error" :title="error" :closable="false" type="error"><el-button link @click="load">重试</el-button></el-alert>
    <el-table v-loading="loading" :data="rows" empty-text="暂无可预检的预约">
      <el-table-column prop="reservationNo" label="预约编号" min-width="160" />
      <el-table-column label="时段" min-width="190"><template #default="{ row }">{{ timeText(row.startTime) }}<br />{{ timeText(row.endTime) }}</template></el-table-column>
      <el-table-column label="预检结果" min-width="130"><template #default="{ row }">{{ row.affected ? '不符合所选规则' : '符合所选规则' }}</template></el-table-column>
      <el-table-column prop="reason" label="原因" min-width="180" />
    </el-table>
    <el-pagination v-if="total" v-model:current-page="page" :total="total" :page-size="10" layout="prev, pager, next" @current-change="load" />
  </section>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { ruleImpact } from '@/api/lab/reservationWorkspace'
import { timeText, messageOf } from './helpers'
const props = defineProps({ ruleId: String, revision: Number })
const rows = ref([]), page = ref(1), total = ref(0), error = ref(''), loading = ref(false)
let sequence = 0
async function load() {
  const current = ++sequence
  loading.value = true; error.value = ''; rows.value = []; total.value = 0
  try { const response = await ruleImpact(props.ruleId, { pageNum: page.value, pageSize: 10 }); if (current === sequence) { rows.value = response.rows || []; total.value = response.total || 0 } }
  catch (failure) { if (current === sequence) error.value = messageOf(failure) }
  finally { if (current === sequence) loading.value = false }
}
watch(() => [props.ruleId, props.revision], () => { page.value = 1; load() }, { immediate: true })
onBeforeUnmount(() => sequence++)
</script>
