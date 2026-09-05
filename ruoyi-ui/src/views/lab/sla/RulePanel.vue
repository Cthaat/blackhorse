<template>
  <section>
    <p>按实验室、业务类型和风险发布不可变版本。新记录使用最新规则，历史截止时间不被规则修改覆盖；单位为连续自然小时。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-select v-model="laboratoryId" filterable placeholder="选择实验室查看最近 100 个版本" aria-label="SLA 规则实验室" @change="load"><el-option v-for="lab in laboratories" :key="lab.id" :label="lab.name" :value="String(lab.id)" /></el-select>
    <el-table :data="rows" v-loading="loading" :empty-text="laboratoryId ? '该实验室暂无已发布版本；首次新业务将持久化默认规则' : '选择实验室后查看规则版本'">
      <el-table-column prop="id" label="版本" width="90" /><el-table-column label="业务"><template #default="{row}">{{ types[row.businessType] }}</template></el-table-column>
      <el-table-column label="风险"><template #default="{row}">{{ risks[row.risk] }}</template></el-table-column>
      <el-table-column prop="responseHours" label="响应小时" /><el-table-column prop="processingHours" label="处理小时" /><el-table-column prop="reason" label="发布原因" min-width="230" />
    </el-table>
    <h3>发布新版本</h3>
    <el-form label-position="top" class="sla-rule-form" @submit.prevent="publish">
      <el-form-item label="业务类型"><el-select v-model="form.businessType" :disabled="busy"><el-option v-for="(label,value) in types" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="风险等级"><el-select v-model="form.risk" :disabled="busy"><el-option v-for="(label,value) in risks" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="响应期限（小时）"><el-input-number v-model="form.responseHours" :min="1" :max="720" :precision="0" :disabled="busy" /></el-form-item>
      <el-form-item label="处理期限（小时）"><el-input-number v-model="form.processingHours" :min="1" :max="8760" :precision="0" :disabled="busy" /></el-form-item>
      <el-form-item label="发布原因（必填）"><el-input v-model="form.reason" type="textarea" maxlength="500" show-word-limit :disabled="busy" /></el-form-item>
    </el-form>
    <el-button type="primary" :loading="busy" :disabled="!laboratoryId" @click="publish">发布规则版本</el-button>
  </section>
</template>
<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSlaRules, publishSlaRule } from '@/api/lab/sla'
import { types, risks, rulePayload } from './presentation'
defineProps({ laboratories: { type: Array, default: () => [] } })
const laboratoryId = ref(''), rows = ref([]), loading = ref(false), busy = ref(false), error = ref('')
const form = reactive({ businessType: 'REPAIR', risk: 'LOW', responseHours: 8, processingHours: 72, reason: '' })
let sequence = 0
async function load() {
  const current = ++sequence; rows.value = []; error.value = ''; if (!laboratoryId.value) return
  loading.value = true
  try { const response = await listSlaRules(laboratoryId.value); if (current === sequence) rows.value = response.data }
  catch (failure) { if (current === sequence) error.value = failure.message || '规则加载失败' }
  finally { if (current === sequence) loading.value = false }
}
async function publish() {
  if (busy.value) return
  let data
  try { data = rulePayload({ ...form, laboratoryId: laboratoryId.value }) } catch (failure) { error.value = failure.message; return }
  busy.value = true
  try { await ElMessageBox.confirm('新规则仅用于新 SLA 记录，不修改既有截止时间。确认发布？', '发布 SLA 规则'); await publishSlaRule(data); ElMessage.success('规则版本已发布'); form.reason = ''; await load() }
  catch (failure) { if (failure !== 'cancel' && failure !== 'close') error.value = failure.message || '发布失败' }
  finally { busy.value = false }
}
onBeforeUnmount(() => ++sequence)
</script>
<style scoped>
.sla-rule-form { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 0 20px; max-width: 780px; }
.el-select { max-width: 100%; width: 300px; }
@media (max-width: 640px) { .sla-rule-form { grid-template-columns: 1fr; } }
</style>
