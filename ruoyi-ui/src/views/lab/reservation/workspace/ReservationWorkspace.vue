<template>
  <el-dialog :model-value="modelValue" title="开放日历与候补" width="min(1120px, calc(100vw - 24px))" append-to-body destroy-on-close @update:model-value="value => emit('update:modelValue', value)">
    <div v-if="modelValue" class="reservation-workspace">
      <div class="workspace-toolbar"><label for="workspace-device">预约设备</label><el-select id="workspace-device" v-model="deviceId" filterable placeholder="请选择设备"><el-option v-for="device in deviceOptions" :key="device.id" :value="device.id" :label="device.label" /></el-select></div>
      <el-empty v-if="!deviceOptions.length" description="暂无可访问设备，请返回列表刷新设备选项" />
      <el-tabs v-else v-model="tab">
        <el-tab-pane v-if="canCalendar" label="开放日历" name="calendar">
          <CalendarPanel v-if="deviceId" ref="calendarRef" :device-id="deviceId" @loaded="calendar = $event" />
          <IntervalActions v-if="deviceId && canSimulate" :device-id="deviceId" :draft="draft" @apply="emit('apply', $event)" @joined="joined" />
        </el-tab-pane>
        <el-tab-pane v-if="canWaitlist" label="我的候补" name="waitlist"><WaitlistPanel ref="waitlistRef" :device-id="deviceId" @reservation="emit('reservation', $event)" @changed="changed" /></el-tab-pane>
        <el-tab-pane v-if="canManage" label="规则管理" name="rules"><RulesPanel v-if="deviceId" :device-id="deviceId" :active="calendar?.rule" :bounds="calendar?.global" @selected="draft = $event" @changed="changed" /></el-tab-pane>
      </el-tabs>
    </div>
  </el-dialog>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { checkPermi } from '@/utils/permission'
import CalendarPanel from './CalendarPanel.vue'
import IntervalActions from './IntervalActions.vue'
import WaitlistPanel from './WaitlistPanel.vue'
import RulesPanel from './RulesPanel.vue'
const props = defineProps({ modelValue: Boolean, deviceOptions: { type: Array, default: () => [] } })
const emit = defineEmits(['update:modelValue', 'apply', 'reservation', 'changed'])
const deviceId = ref(''), tab = ref('calendar'), calendar = ref(null), draft = ref(null), calendarRef = ref(), waitlistRef = ref()
const canManage = computed(() => checkPermi(['lab:device:edit']))
const canCalendar = computed(() => checkPermi(['lab:reservation:apply', 'lab:reservation:delegate', 'lab:reservation:list', 'lab:device:edit']))
const canSimulate = computed(() => checkPermi(['lab:reservation:apply', 'lab:reservation:delegate', 'lab:device:edit']))
const canWaitlist = computed(() => checkPermi(['lab:reservation:mine', 'lab:reservation:apply']))
watch([canCalendar, canWaitlist, canManage], () => {
  const available = [canCalendar.value && 'calendar', canWaitlist.value && 'waitlist', canManage.value && 'rules'].filter(Boolean)
  if (!available.includes(tab.value)) tab.value = available[0] || ''
}, { immediate: true })
watch(() => props.deviceOptions, options => { if (!options.some(item => item.id === deviceId.value)) deviceId.value = options[0]?.id || '' }, { immediate: true })
watch(deviceId, () => { calendar.value = null; draft.value = null })
function changed() { calendarRef.value?.refresh(); emit('changed') }
function joined() { waitlistRef.value?.refresh(); calendarRef.value?.refresh(); tab.value = 'waitlist' }
</script>
<style>
.reservation-workspace { color: var(--el-text-color-primary); }
.reservation-workspace h3 { font-size: 16px; margin: 24px 0 12px; }
.workspace-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin: 12px 0; }
.workspace-table { margin-top: 16px; }
.workspace-muted { color: var(--el-text-color-secondary); line-height: 1.6; }
.reservation-workspace .el-pagination { margin: 16px 0; }
.reservation-workspace .el-alert { margin: 12px 0; }
.reservation-workspace .el-date-editor { max-width: 100%; }
@media (max-width: 640px) {
  .reservation-workspace .workspace-toolbar > .el-select { width: 100%; }
  .reservation-workspace .el-date-editor { width: 100%; }
  .reservation-workspace .el-tabs__item { padding: 0 12px; }
}
</style>
