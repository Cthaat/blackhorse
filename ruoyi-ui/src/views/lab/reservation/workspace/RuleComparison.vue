<template>
  <h3>所选版本与当前生效规则</h3>
  <el-table :data="rows" size="small">
    <el-table-column prop="label" label="字段" min-width="150" />
    <el-table-column prop="active" label="当前生效（无设备规则则取全局）" min-width="220" />
    <el-table-column label="所选版本" min-width="200"><template #default="{ row }"><el-text :type="row.active !== row.selected ? 'warning' : undefined">{{ row.selected }}{{ row.active !== row.selected ? '（有变化）' : '' }}</el-text></template></el-table-column>
  </el-table>
</template>
<script setup>
import { computed } from 'vue'
import { fields } from './helpers'
const props = defineProps({ selected: Object, active: Object, bounds: Object })
const rows = computed(() => {
  const current = props.active?.definition || { ...props.bounds, weekdays: [1, 2, 3, 4, 5, 6, 7], opensAt: '00:00', closesAt: '24:00', closedDays: [] }
  const labels = { name: '名称', weekdays: '开放日（周一至周日为1至7）', opensAt: '开放时间', closesAt: '关闭时间', closedDays: '临时关闭', ...fields }
  const display = value => Array.isArray(value) ? value.map(item => typeof item === 'object' ? `${item.date} ${item.reason || ''}` : item).join('、') || '无' : String(value ?? '—')
  return Object.entries(labels).map(([key, label]) => ({ label, active: display(current[key]), selected: display(props.selected?.definition?.[key]) }))
})
</script>
