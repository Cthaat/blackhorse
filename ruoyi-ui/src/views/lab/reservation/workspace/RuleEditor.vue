<template>
  <el-dialog :model-value="modelValue" :title="rule ? '编辑规则草稿' : '新建规则草稿'" width="min(720px, calc(100vw - 24px))" append-to-body :close-on-click-modal="false" @update:model-value="value => !busy && emit('update:modelValue', value)">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <p>设备规则只能收紧全局边界，发布时间不会自动取消已有预约。</p>
    <el-form label-position="top" :disabled="busy">
      <el-form-item label="规则名称"><el-input v-model="form.name" maxlength="80" /></el-form-item>
      <el-form-item label="每周开放日"><el-checkbox-group v-model="form.weekdays"><el-checkbox v-for="(day, index) in days" :key="day" :value="index + 1">周{{ day }}</el-checkbox></el-checkbox-group></el-form-item>
      <div class="rule-grid">
        <el-form-item label="每日开放时间"><el-time-select v-model="form.opensAt" start="00:00" end="23:30" step="00:30" /></el-form-item>
        <el-form-item label="每日关闭时间"><el-time-select v-model="form.closesAt" start="00:30" end="23:59" step="00:30" include-end-time /></el-form-item>
        <el-form-item v-for="(label, key) in fields" :key="key" :label="label">
          <el-input-number v-model="form[key]" :min="key === 'minLeadMinutes' ? 0 : 1" :precision="0" />
          <small v-if="bounds?.[key] != null">全局边界：{{ bounds[key] }}</small>
        </el-form-item>
      </div>
      <el-form-item label="临时关闭日期">
        <div v-for="(item, index) in form.closedDays" :key="index" class="workspace-toolbar">
          <el-date-picker v-model="item.date" value-format="YYYY-MM-DD" placeholder="关闭日期" aria-label="关闭日期" />
          <el-input v-model="item.reason" placeholder="关闭原因" maxlength="120" aria-label="关闭原因" />
          <el-button @click="form.closedDays.splice(index, 1)">移除</el-button>
        </div>
        <el-button @click="form.closedDays.push({ date: '', reason: '' })">添加关闭日期</el-button>
      </el-form-item>
    </el-form>
    <template #footer><el-button :disabled="busy" @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="busy" @click="save">保存草稿</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, watch } from 'vue'
import { createRule, updateRule } from '@/api/lab/reservationWorkspace'
import { fields, messageOf } from './helpers'
const props = defineProps({ modelValue: Boolean, rule: Object, deviceId: String, bounds: Object })
const emit = defineEmits(['update:modelValue', 'saved'])
const days = [...'一二三四五六日']
const form = ref({ closedDays: [] }), busy = ref(false), error = ref('')
watch(() => props.modelValue, open => {
  if (!open) return
  error.value = ''
  form.value = props.rule ? JSON.parse(JSON.stringify(props.rule.definition)) : {
    name: '', weekdays: [1, 2, 3, 4, 5], opensAt: '09:00', closesAt: '17:00', closedDays: [],
    minLeadMinutes: props.bounds?.minLeadMinutes ?? 30, maxAdvanceDays: props.bounds?.maxAdvanceDays ?? 7,
    minDurationMinutes: props.bounds?.minDurationMinutes ?? 30, maxDurationMinutes: props.bounds?.maxDurationMinutes ?? 240, invitationMinutes: 15
  }
})
async function save() {
  if (busy.value) return
  const definition = form.value
  error.value = ''
  if (!definition.name.trim() || !definition.weekdays.length || !definition.opensAt || !definition.closesAt || definition.opensAt >= definition.closesAt || definition.closedDays.some(item => !item.date)) {
    error.value = '请填写名称、开放日、有效的同日开放时间及关闭日期'; return
  }
  if (definition.closedDays.some(item => !item.reason?.trim() || item.reason.trim().length > 120)) {
    error.value = '请填写每个关闭日期的原因，最多 120 字'; return
  }
  definition.name = definition.name.trim()
  definition.closedDays = definition.closedDays.map(item => ({ ...item, reason: item.reason.trim() }))
  busy.value = true
  try {
    const data = { deviceId: props.deviceId, definition }
    if (props.rule) await updateRule(props.rule.id, { ...data, expectedVersion: props.rule.revision })
    else await createRule(data)
    emit('saved'); emit('update:modelValue', false)
  } catch (failure) { error.value = messageOf(failure) }
  finally { busy.value = false }
}
</script>
<style scoped>
.rule-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 0 16px; }
small { display: block; color: var(--el-text-color-secondary); }
</style>
