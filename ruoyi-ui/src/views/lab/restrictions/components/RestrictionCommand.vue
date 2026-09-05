<template>
  <el-dialog v-model="visible" :title="titles[mode]" width="600px" append-to-body :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" :before-close="closeSafely">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="mb16" />
    <el-form label-position="top" @submit.prevent="submit">
      <template v-if="mode === 'manual' || mode === 'rule'">
        <el-form-item label="实验室" required>
          <el-select v-model="form.laboratoryId" aria-label="操作实验室" filterable placeholder="选择管理范围内的实验室">
            <el-option v-for="lab in laboratories" :key="lab.id" :value="String(lab.id)" :label="lab.name" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="mode === 'manual'" label="受限学生" required>
          <el-select v-model="form.userId" aria-label="受限学生" filterable placeholder="选择学生，不可选择本人">
            <el-option v-for="person in users" :key="person.id" :value="String(person.id)" :label="person.label" />
          </el-select>
        </el-form-item>
        <el-form-item :label="mode === 'rule' ? '新爽约限制天数' : '手工限制天数'" required>
          <el-input-number v-model="form.days" aria-label="限制天数" :min="1" :max="mode === 'rule' ? 90 : 365" :precision="0" />
        </el-form-item>
        <p class="hint">{{ mode === 'rule' ? '发布后仅影响新发生的爽约，不修改已有处罚；历史版本不可修改。' : '立即生效，仅限制本实验室的预约及领用，不禁用登录、不撤销已归还业务。' }}</p>
      </template>
      <el-form-item v-if="mode === 'review'" label="审核结论" required>
        <el-radio-group v-model="form.approved" aria-label="审核结论">
          <el-radio :value="true">通过并解除对应限制</el-radio>
          <el-radio :value="false">驳回，保留限制</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="原因或说明" required>
        <el-input v-model="form.reason" aria-label="原因或说明" type="textarea" :rows="4" maxlength="500" show-word-limit />
      </el-form-item>
      <p v-if="mode === 'revoke'" class="hint">只解除当前这条限制，其他有效限制仍然生效。历史和申诉记录不会删除。</p>
      <p v-if="mode === 'appeal'" class="hint">每条限制仅可提交一次申诉。当前详情中的附件将作为证据提交；提交后冻结，等待审核期间限制仍生效。</p>
    </el-form>
    <template #footer>
      <el-button :disabled="busy" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="busy" @click="submit">确认提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createRestriction, revokeRestriction, appealRestriction, decideAppeal, publishRestrictionRule } from '@/api/lab/restrictions'
import { listAttachment } from '@/api/lab/attachment'
import { validateRestrictionDays, validateRestrictionReason } from '../presentation'

const props = defineProps({ modelValue: Boolean, mode: String, row: Object, laboratories: Array, users: Array })
const emit = defineEmits(['update:modelValue', 'completed'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const titles = { manual: '登记实验室限制', revoke: '解除限制', appeal: '提交申诉', review: '审核申诉', rule: '发布爽约规则' }
const form = reactive({ laboratoryId: '', userId: '', days: 7, reason: '', approved: false })
const busy = ref(false), error = ref('')
function closeSafely(done) { if (!busy.value) done() }
watch(() => props.modelValue, open => {
  if (open) { Object.assign(form, { laboratoryId: '', userId: '', days: 7, reason: '', approved: false }); error.value = '' }
})
async function submit() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  const mode = props.mode
  const targetId = props.row?.id == null ? null : String(props.row.id)
  const submission = { ...form }
  try {
    const reason = validateRestrictionReason(submission.reason)
    if (['manual', 'rule'].includes(mode)) {
      if (!submission.laboratoryId || (mode === 'manual' && !submission.userId)) throw new Error('请完整选择实验室和学生')
      validateRestrictionDays(submission.days, mode === 'rule' ? 90 : 365)
    }
    await ElMessageBox.confirm('请核对范围、原因与处理结果。提交后会保留审计记录，是否继续？', titles[mode])
    if (mode === 'manual') await createRestriction({ laboratoryId: submission.laboratoryId, userId: submission.userId, days: submission.days, reason })
    else if (mode === 'rule') await publishRestrictionRule({ laboratoryId: submission.laboratoryId, days: submission.days, reason })
    else if (mode === 'revoke') await revokeRestriction(targetId, reason)
    else if (mode === 'review') await decideAppeal(targetId, { approved: submission.approved, reason })
    else if (mode === 'appeal') {
      const evidence = await listAttachment('RESTRICTION', targetId)
      await appealRestriction(targetId, { reason, attachmentIds: evidence.data.map(item => String(item.id)) })
    }
    ElMessage.success('已提交')
    visible.value = false
    emit('completed')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') error.value = e.message || '操作失败，请刷新后重试'
  } finally { busy.value = false }
}
</script>

<style scoped>
.el-select { width: 100%; }
.hint { color: var(--el-text-color-secondary); line-height: 1.8; }
.mb16 { margin-bottom: 16px; }
</style>
