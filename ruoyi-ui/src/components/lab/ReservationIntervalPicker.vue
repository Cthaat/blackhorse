<template>
  <div class="interval-picker">
    <el-date-picker
      :model-value="modelValue"
      type="datetimerange"
      value-format="YYYY-MM-DDTHH:mm:ss"
      format="YYYY-MM-DD HH:mm"
      range-separator="至"
      start-placeholder="开始时间"
      end-placeholder="结束时间"
      :disabled="disabled"
      :clearable="true"
      unlink-panels
      class="interval-input"
      @update:model-value="updateValue"
    />

    <section v-if="deviceId" class="occupied-panel" aria-live="polite">
      <div class="occupied-heading">
        <span>设备占用时段</span>
        <el-button link type="primary" :loading="loading" @click="loadRanges">刷新</el-button>
      </div>
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        :closable="false"
        show-icon
        class="mb8"
      />
      <el-skeleton v-if="loading" :rows="2" animated />
      <el-empty v-else-if="!ranges.length && !errorMessage" description="查询区间内暂无占用" :image-size="56" />
      <el-scrollbar v-else-if="ranges.length" max-height="168px">
        <ul class="occupied-list">
          <li v-for="(range, index) in ranges" :key="`${range.startTime}-${index}`">
            <el-tag size="small" type="warning" effect="plain">{{ statusLabel(range.reservationStatus) }}</el-tag>
            <span>{{ formatDateTime(range.startTime) }} 至 {{ formatDateTime(range.endTime) }}</span>
          </li>
        </ul>
      </el-scrollbar>
    </section>
    <el-text v-else type="info" size="small">选择设备后可查看未来占用时段</el-text>
  </div>
</template>

<script setup name="ReservationIntervalPicker">
import { getDeviceOccupiedRanges } from '@/api/lab/reservation'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  deviceId: { type: [String, Number], default: '' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])
const loading = ref(false)
const errorMessage = ref('')
const ranges = ref([])

function updateValue(value) {
  const normalized = Array.isArray(value) ? value : []
  emit('update:modelValue', normalized)
  emit('change', normalized)
}

function formatLocalDateTime(value) {
  const pad = part => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}

function queryWindow() {
  const selectedStart = props.modelValue?.[0]
  const selectedEnd = props.modelValue?.[1]
  if (selectedStart && selectedEnd) {
    const start = new Date(selectedStart)
    const end = new Date(selectedEnd)
    start.setDate(start.getDate() - 1)
    end.setDate(end.getDate() + 1)
    return {
      from: formatLocalDateTime(start),
      to: formatLocalDateTime(end)
    }
  }
  const now = new Date()
  const future = new Date(now)
  future.setDate(future.getDate() + 30)
  return {
    from: formatLocalDateTime(now),
    to: formatLocalDateTime(future)
  }
}

async function loadRanges() {
  if (!props.deviceId) {
    ranges.value = []
    errorMessage.value = ''
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getDeviceOccupiedRanges(String(props.deviceId), queryWindow())
    ranges.value = Array.isArray(response) ? response : []
  } catch (error) {
    ranges.value = []
    errorMessage.value = error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? '占用时段加载失败'
  } finally {
    loading.value = false
  }
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-'
}

function statusLabel(status) {
  return {
    PENDING: '待审批',
    APPROVED: '已批准',
    CHECKED_OUT: '使用中'
  }[status] ?? status ?? '已占用'
}

watch(() => String(props.deviceId ?? ''), loadRanges, { immediate: true })
</script>

<style scoped>
.interval-picker {
  width: 100%;
}

.interval-input {
  width: 100%;
}

.occupied-panel {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-lighter);
}

.occupied-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.occupied-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.occupied-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

@media (max-width: 640px) {
  .occupied-list li {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
