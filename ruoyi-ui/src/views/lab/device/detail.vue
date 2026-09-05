<template>
  <el-drawer v-model="visible" title="设备详情" size="min(920px, 92vw)" append-to-body>
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="mb16"
    >
      <template #default>
        <el-button link type="primary" @click="loadDevice">重新加载</el-button>
      </template>
    </el-alert>

    <div v-loading="loading" class="detail-body">
      <template v-if="device">
        <DeviceActions :device="device" />
        <lab-descriptions :column="2" border class="mb20">
          <el-descriptions-item label="资产编号">{{ device.assetNo }}</el-descriptions-item>
          <el-descriptions-item label="设备名称">{{ device.name }}</el-descriptions-item>
          <el-descriptions-item label="实验室">{{ laboratoryLabel(device.laboratoryId) }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ userLabel(device.managerId) }}</el-descriptions-item>
          <el-descriptions-item label="设备类别"><dict-tag :options="lab_device_category" :value="device.categoryCode" /></el-descriptions-item>
          <el-descriptions-item label="型号">{{ device.model || '—' }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <dict-tag :options="lab_risk_level" :value="device.riskLevel" />
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <dict-tag :options="lab_device_status" :value="device.status" />
          </el-descriptions-item>
          <el-descriptions-item label="位置" :span="2">{{ device.location }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">{{ device.description || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ parseTime(device.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ parseTime(device.updateTime) || '—' }}</el-descriptions-item>
        </lab-descriptions>

        <el-tabs v-model="activeTab">
          <el-tab-pane label="占用时间" name="occupied">
            <div class="range-toolbar">
              <el-date-picker
                v-model="occupiedWindow"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DDTHH:mm:ss"
                :clearable="false"
              />
              <el-button type="primary" icon="Search" :loading="occupiedLoading" @click="loadOccupiedRanges">
                查询
              </el-button>
            </div>
            <el-alert
              v-if="occupiedError"
              :title="occupiedError"
              type="error"
              show-icon
              :closable="false"
              class="mb12"
            />
            <el-table v-loading="occupiedLoading" :data="occupiedRanges" size="small">
              <el-table-column label="开始时间" min-width="180">
                <template #default="scope">{{ parseTime(scope.row.startTime) }}</template>
              </el-table-column>
              <el-table-column label="结束时间" min-width="180">
                <template #default="scope">{{ parseTime(scope.row.endTime) }}</template>
              </el-table-column>
              <el-table-column label="占用状态" width="130"><template #default="{ row }">{{ ({ MAINTENANCE_WINDOW: '维护停用窗口', WAITLIST_HOLD: '候补邀请保留', PENDING: '待审批', APPROVED: '已批准', CHECKED_OUT: '使用中' })[row.reservationStatus] || row.reservationStatus }}</template></el-table-column>
              <template #empty>
                <el-empty description="所选时间范围内暂无占用" :image-size="72" />
              </template>
            </el-table>
          </el-tab-pane>
          <el-tab-pane v-if="canReadAttachment" label="附件" name="attachments">
            <attachment-panel
              business-type="DEVICE"
              :business-id="device.id"
              :can-manage="canManageAttachment"
            />
          </el-tab-pane>
          <el-tab-pane label="状态历史" name="history">
            <status-history object-type="DEVICE" :object-id="device.id" />
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else-if="!loading && !errorMessage" description="未找到设备信息" />
    </div>
  </el-drawer>
</template>

<script setup name="LabDeviceDetail">
import { parseTime } from '@/utils/ruoyi'
import { getDevice, listOccupiedRanges } from '@/api/lab/device'
import AttachmentPanel from '@/components/lab/AttachmentPanel.vue'
import StatusHistory from '@/components/lab/StatusHistory.vue'
import DeviceActions from '@/components/lab/DeviceActions.vue'

const { proxy } = getCurrentInstance()
const { lab_device_status, lab_risk_level, lab_device_category } = useDict(
  'lab_device_status',
  'lab_risk_level',
  'lab_device_category'
)

const props = defineProps({
  laboratoryOptions: { type: Array, default: () => [] },
  userOptions: { type: Array, default: () => [] }
})

const visible = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const deviceId = ref('')
const device = ref(null)
const activeTab = ref('occupied')
const occupiedWindow = ref([])
const occupiedRanges = ref([])
const occupiedLoading = ref(false)
const occupiedError = ref('')

const canReadAttachment = computed(() => proxy.$auth.hasPermi('lab:attachment:read'))
const canManageAttachment = computed(() => proxy.$auth.hasPermi('lab:attachment:manage')
  && proxy.$auth.hasPermi('lab:device:edit'))

function laboratoryLabel(id) {
  const item = props.laboratoryOptions.find(option => option.id === String(id))
  return item ? `${item.labCode} · ${item.name}` : `实验室 ${id}`
}

function userLabel(id) {
  return props.userOptions.find(option => option.id === String(id))?.label || `用户 ${id}`
}

function localDateTime(value) {
  const pad = part => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`
    + `T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}

function resetWindow() {
  const from = new Date()
  const to = new Date(from.getTime() + 30 * 24 * 60 * 60 * 1000)
  occupiedWindow.value = [localDateTime(from), localDateTime(to)]
}

async function open(id) {
  deviceId.value = String(id)
  visible.value = true
  activeTab.value = 'occupied'
  device.value = null
  occupiedRanges.value = []
  resetWindow()
  await loadDevice()
}

async function loadDevice() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getDevice(deviceId.value)
    device.value = response.data
      ? {
          ...response.data,
          id: String(response.data.id),
          laboratoryId: String(response.data.laboratoryId),
          managerId: String(response.data.managerId)
        }
      : null
    if (device.value) {
      await loadOccupiedRanges()
    }
  } catch {
    device.value = null
    errorMessage.value = '设备详情加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadOccupiedRanges() {
  if (!deviceId.value || occupiedWindow.value.length !== 2) {
    proxy.$modal.msgWarning('请选择完整的占用查询时间范围')
    return
  }
  occupiedLoading.value = true
  occupiedError.value = ''
  try {
    const response = await listOccupiedRanges(
      deviceId.value,
      occupiedWindow.value[0],
      occupiedWindow.value[1]
    )
    occupiedRanges.value = Array.isArray(response) ? response : (response.data || [])
  } catch {
    occupiedRanges.value = []
    occupiedError.value = '占用时间加载失败，请稍后重试'
  } finally {
    occupiedLoading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.detail-body {
  min-height: 240px;
}

.range-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.mb12 {
  margin-bottom: 12px;
}

.mb16 {
  margin-bottom: 16px;
}

.mb20 {
  margin-bottom: 20px;
}

@media (max-width: 640px) {
  .range-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
