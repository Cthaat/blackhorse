<template>
  <el-descriptions :column="1" border size="small">
    <el-descriptions-item label="开放安排">{{ definition ? `周${definition.weekdays?.map(day => '一二三四五六日'[day - 1]).join('、')} ${definition.opensAt}—${definition.closesAt}` : '未设置设备专属规则，全天开放（受全局预约限制）' }}</el-descriptions-item>
    <el-descriptions-item v-for="(label, key) in fields" :key="key" :label="label">{{ definition?.[key] ?? bounds?.[key] ?? '—' }}<span v-if="definition && bounds?.[key] != null">（全局：{{ bounds[key] }}）</span></el-descriptions-item>
    <el-descriptions-item label="临时关闭">{{ definition?.closedDays?.map(item => `${item.date}：${item.reason || '关闭'}`).join('；') || '无' }}</el-descriptions-item>
  </el-descriptions>
</template>
<script setup>
import { fields } from './helpers'
defineProps({ definition: Object, bounds: Object })
</script>
