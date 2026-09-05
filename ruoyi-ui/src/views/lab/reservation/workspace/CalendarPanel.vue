<template>
  <section aria-label="设备开放日历">
    <div class="workspace-toolbar">
      <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" aria-label="日历日期范围" />
      <el-button :loading="loading" @click="load">刷新日历</el-button>
      <span>最多 31 天 · 北京时间</span>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
    <el-skeleton v-if="loading" :rows="4" animated />
    <template v-else-if="calendar">
      <h3>{{ calendar.rule?.definition?.name || '全局预约规则' }}</h3>
      <RuleSummary :definition="calendar.rule?.definition" :bounds="calendar.global" />
      <el-table :data="calendar.days" class="workspace-table">
        <el-table-column prop="date" label="日期" min-width="120" />
        <el-table-column label="开放时间" min-width="160"><template #default="{ row }">{{ row.open ? `${row.opensAt || '00:00'}—${row.closesAt || '24:00'}` : `关闭：${row.closedReason || '非开放日'}` }}</template></el-table-column>
      </el-table>
      <h3>已占用时段（匿名）</h3>
      <el-table :data="calendar.occupied" empty-text="所选日期内暂无占用记录">
        <el-table-column label="开始" min-width="180"><template #default="{ row }">{{ timeText(row.startTime) }}</template></el-table-column>
        <el-table-column label="结束" min-width="180"><template #default="{ row }">{{ timeText(row.endTime) }}</template></el-table-column>
        <el-table-column label="占用类型" min-width="120"><template #default="{ row }">{{ row.reservationStatus === 'MAINTENANCE_WINDOW' ? '维护停用窗口' : row.reservationStatus === 'WAITLIST_HOLD' ? '候补邀请保留' : '预约占用' }}</template></el-table-column>
      </el-table>
    </template>
  </section>
</template>
<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { getCalendar } from '@/api/lab/reservationWorkspace'
import RuleSummary from './RuleSummary.vue'
import { messageOf, timeText, today } from './helpers'
const props = defineProps({ deviceId: String })
const emit = defineEmits(['loaded'])
const range = ref([today(), today()])
const calendar = ref(null)
const loading = ref(false)
const error = ref('')
let sequence = 0
async function load() {
  const current = ++sequence
  calendar.value = null
  error.value = ''
  emit('loaded', null)
  loading.value = false
  if (!props.deviceId) return
  if (!range.value?.[0] || !range.value?.[1] || Date.parse(range.value[1]) - Date.parse(range.value[0]) > 30 * 86400000) {
    error.value = '请选择最多 31 天的完整日期范围'
    return
  }
  loading.value = true
  try {
    const response = await getCalendar({ deviceId: props.deviceId, from: range.value[0], to: range.value[1] })
    if (current !== sequence) return
    calendar.value = response.data
    emit('loaded', response.data)
  } catch (failure) { if (current === sequence) error.value = messageOf(failure, '日历加载失败') }
  finally { if (current === sequence) loading.value = false }
}
watch(() => [props.deviceId, range.value], load, { immediate: true, deep: true })
onBeforeUnmount(() => sequence++)
defineExpose({ refresh: load })
</script>
