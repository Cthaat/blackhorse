<template>
  <el-dialog
    :model-value="modelValue"
    :title="delegated ? '代学生提交预约' : '提交预约申请'"
    width="720px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="setVisible"
    @closed="handleClosed"
  >
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      :description="retryPending ? '可直接再次提交，系统会复用本次逻辑申请的幂等键。' : undefined"
      type="error"
      show-icon
      :closable="false"
      class="mb16"
    />

    <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" v-loading="deviceLoading">
      <el-alert v-if="delegated" title="代办后仍需其他审批人审批，学生资格及设备时间冲突照常校验。" type="info" :closable="false" class="mb16" />
      <el-form-item v-if="delegated" label="申请学生" prop="applicantId">
        <el-select v-model="form.applicantId" filterable clearable placeholder="请选择学生" class="full-width">
          <el-option v-for="student in students" :key="student.id" :value="String(student.id)"
            :label="`${student.displayName}（${student.userName}）`" />
        </el-select>
      </el-form-item>
      <el-form-item label="预约设备" prop="deviceId">
        <el-select
          v-model="form.deviceId"
          placeholder="请选择可用设备"
          filterable
          clearable
          class="full-width"
          :loading="deviceLoading"
          no-data-text="暂无可预约设备"
        >
          <el-option
            v-for="device in devices"
            :key="String(device.id)"
            :label="`${device.assetNo} · ${device.name}`"
            :value="String(device.id)"
          >
            <span>{{ device.assetNo }} · {{ device.name }}</span>
            <el-text type="info" size="small" class="device-location">{{ device.location || '未设置位置' }}</el-text>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="预约时段" prop="interval">
        <ReservationIntervalPicker v-model="form.interval" :device-id="form.deviceId" />
      </el-form-item>

      <el-form-item label="预约用途" prop="purpose">
        <el-input
          v-model="form.purpose"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请说明教学、实验或科研用途"
        />
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="可填写特殊准备事项（选填）"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button :disabled="submitting" @click="setVisible(false)">取消</el-button>
        <el-button type="primary" :disabled="deviceLoading" :loading="submitting" @click="submit">提交申请</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup name="LabReservationApply">
import { loadAllOptions } from '@/utils/labOptions'
import { listLabUserOptions } from '@/api/lab/options'
import ReservationIntervalPicker from '@/components/lab/ReservationIntervalPicker.vue'
import { applyReservation, delegateReservation, listReservationDevices } from '@/api/lab/reservation'
import { createIdempotentSubmission } from '@/utils/lab/idempotency'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  delegated: { type: Boolean, default: false },
  initialRequest: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'saved'])
const { proxy } = getCurrentInstance()
const formRef = ref()
const devices = ref([])
const students = ref([])
const deviceLoading = ref(false)
const submitting = ref(false)
const errorMessage = ref('')
const retryPending = ref(false)
const submission = createIdempotentSubmission()

const form = reactive({
  applicantId: '',
  deviceId: '',
  interval: [],
  purpose: '',
  remark: ''
})

const rules = {
  applicantId: [{ required: true, message: '请选择代办学生', trigger: 'change' }],
  deviceId: [{ required: true, message: '请选择预约设备', trigger: 'change' }],
  interval: [{
    validator: (_rule, value, callback) => {
      if (!Array.isArray(value) || value.length !== 2) callback(new Error('请选择完整预约时段'))
      else callback()
    },
    trigger: 'change'
  }],
  purpose: [{ required: true, whitespace: true, message: '请填写预约用途', trigger: 'blur' }]
}

function snapshot() {
  return {
    applicantId: form.applicantId,
    deviceId: form.deviceId,
    interval: [...form.interval],
    purpose: form.purpose,
    remark: form.remark
  }
}

function resetForm() {
  form.applicantId = ''
  form.deviceId = ''
  form.interval = []
  form.purpose = ''
  form.remark = ''
  errorMessage.value = ''
  retryPending.value = false
  submission.clear()
  nextTick(() => formRef.value?.clearValidate())
}

function setVisible(value) {
  if (!submitting.value) emit('update:modelValue', value)
}

function handleClosed() {
  resetForm()
}

function withShanghaiOffset(value) {
  const text = String(value ?? '')
  return /(?:Z|[+-]\d{2}:\d{2})$/.test(text) ? text : `${text}+08:00`
}

function payload() {
  return {
    ...(props.delegated ? { applicantId: String(form.applicantId) } : {}),
    deviceId: String(form.deviceId),
    startTime: withShanghaiOffset(form.interval[0]),
    endTime: withShanghaiOffset(form.interval[1]),
    purpose: form.purpose.trim(),
    remark: form.remark.trim() || null
  }
}

async function loadDevices() {
  deviceLoading.value = true
  errorMessage.value = ''
  try {
    if (props.delegated) students.value = (await listLabUserOptions({ roleKey: 'lab_student' })).data
    const response = await loadAllOptions(listReservationDevices, {
      status: 'AVAILABLE',
      sortBy: 'assetNo',
      sortDirection: 'asc'
    })
    devices.value = Array.isArray(response.rows) ? response.rows : []
  } catch (error) {
    devices.value = []
    errorMessage.value = messageOf(error, '设备列表加载失败')
  } finally {
    deviceLoading.value = false
  }
}

async function submit() {
  if (!await formRef.value?.validate().catch(() => false)) return
  const request = payload()
  const key = submission.prepare(request)
  submitting.value = true
  errorMessage.value = ''
  try {
    await (props.delegated ? delegateReservation : applyReservation)(request, key)
    submission.clear()
    retryPending.value = false
    proxy.$modal.msgSuccess('预约申请已提交')
    emit('saved')
    emit('update:modelValue', false)
  } catch (error) {
    submission.handleFailure(error)
    retryPending.value = submission.hasPendingKey()
    errorMessage.value = messageOf(error, '预约申请提交失败')
  } finally {
    submitting.value = false
  }
}

function messageOf(error, fallback) {
  return error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? fallback
}

watch(snapshot, value => {
  submission.update(value)
  retryPending.value = false
}, { deep: true })

watch(() => props.modelValue, open => {
  if (open) {
    if (props.initialRequest) {
      resetForm()
      const initial = props.initialRequest
      form.deviceId = String(initial.deviceId || '')
      form.interval = initial.startTime && initial.endTime
        ? [initial.startTime, initial.endTime].map(value => String(value).replace(/\+08:00$/, '')) : []
      form.purpose = initial.purpose || ''
      form.remark = initial.remark || ''
    }
    void loadDevices()
  }
}, { immediate: true })
</script>

<style scoped>
.full-width {
  width: 100%;
}

.device-location {
  float: right;
  margin-left: 16px;
}

@media (max-width: 640px) {
  :deep(.el-dialog) {
    width: calc(100% - 24px) !important;
  }
}
</style>
