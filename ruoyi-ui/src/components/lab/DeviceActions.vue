<template>
  <div class="device-actions">
    <el-button v-hasPermi="['lab:device:query']" @click="labels.open([device.id])">资产标签</el-button>
    <el-button v-hasPermi="['lab:reservation:apply']" type="primary" @click="go('/lab/reservation/apply')">预约设备</el-button>
    <el-button v-hasPermi="['lab:repair:report']" @click="go('/lab/repair')">报告故障</el-button>
    <el-button v-hasPermi="['lab:usage:checkout', 'lab:usage:return']" @click="go('/lab/usage', { assetNo: device.assetNo })">领用 / 归还</el-button>
    <AssetLabels ref="labels" />
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AssetLabels from './AssetLabels.vue'
const props = defineProps({ device: { type: Object, required: true } })
const router = useRouter(), labels = ref()
function go(path, extra = {}) { router.push({ path, query: { deviceId: props.device.id, ...extra } }) }
</script>
<style scoped>
.device-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; }
.device-actions .el-button { margin-left: 0; }
</style>
