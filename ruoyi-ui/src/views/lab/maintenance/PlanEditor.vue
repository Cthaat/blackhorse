<template>
  <el-dialog :model-value="modelValue" :title="row ? '发布维护计划新版本' : '新建维护／校准计划'" width="min(660px, 94vw)" append-to-body :show-close="!busy" :close-on-click-modal="false" :close-on-press-escape="!busy" @update:model-value="close">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="设备（必选）"><el-select v-model="form.deviceId" filterable :disabled="!!row || busy" aria-label="计划设备"><el-option v-for="device in devices" :key="device.id" :value="String(device.id)" :label="`${device.assetNo} · ${device.name}`" /></el-select></el-form-item>
      <el-form-item label="计划类型"><el-select v-model="form.kind" :disabled="busy"><el-option v-for="(label, value) in kinds" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="周期天数（1～3650）"><el-input-number v-model="form.periodDays" :min="1" :max="3650" :precision="0" :disabled="busy" /></el-form-item>
      <el-form-item label="首次到期时间"><el-date-picker v-model="form.firstDueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" :disabled="busy" /></el-form-item>
      <el-form-item label="执行负责人（维修人员）"><el-select v-model="form.responsibleId" filterable :disabled="busy" aria-label="执行负责人"><el-option v-for="person in people" :key="person.id" :value="String(person.id)" :label="`${person.displayName || person.userName}（${person.userName}）`" /></el-select></el-form-item>
      <el-form-item label="工作内容"><el-input v-model="form.description" type="textarea" maxlength="1000" show-word-limit :disabled="busy" /></el-form-item>
      <el-form-item label="版本变更原因（必填）"><el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit :disabled="busy" /></el-form-item>
      <el-alert title="修改保留历史版本；已经生成的周期保留原快照。到期不会自动停机。" type="info" :closable="false" />
    </el-form>
    <template #footer><el-button :disabled="busy" @click="close(false)">取消</el-button><el-button type="primary" :loading="busy" @click="submit">保存版本</el-button></template>
  </el-dialog>
</template>
<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { saveMaintenancePlan } from '@/api/lab/maintenance'
import { kinds, planPayload } from './presentation'
const props = defineProps({ modelValue: Boolean, row: Object, devices: { type: Array, default: () => [] }, people: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue', 'saved'])
const form = reactive({}), busy = ref(false), error = ref('')
watch(() => [props.modelValue, props.row], () => {
  if (!props.modelValue || busy.value) return
  const row = props.row
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, row ? { ...row, expectedVersion: row.version, reason: '' } : { deviceId: '', kind: 'MAINTENANCE', periodDays: 30, firstDueAt: '', responsibleId: '', description: '', reason: '' })
  error.value = ''
}, { immediate: true })
function close(value) { if (!busy.value) emit('update:modelValue', value) }
async function submit() {
  if (busy.value) return
  error.value = ''
  let payload
  try { payload = planPayload(form) } catch (failure) { error.value = failure.message; return }
  const id = props.row?.id
  busy.value = true
  try { await saveMaintenancePlan(id, payload); ElMessage.success('计划版本已保存'); emit('saved'); emit('update:modelValue', false) }
  catch (failure) { error.value = failure.message || '保存失败，请刷新版本后重试' }
  finally { busy.value = false }
}
</script>
