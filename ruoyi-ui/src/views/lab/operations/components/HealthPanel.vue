<template>
  <el-card shadow="never">
    <template #header><h2>服务与运行资源</h2></template>
    <el-table :data="healthRows" empty-text="健康数据未知">
      <el-table-column prop="name" label="组件" min-width="100" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.section?.status === 'UP' ? 'success' : 'warning'">{{ statuses[row.section?.status] || '未知' }}</el-tag></template></el-table-column>
      <el-table-column label="采样时间" min-width="180"><template #default="{ row }">{{ time(row.section?.sampledAt) }}</template></el-table-column>
      <el-table-column label="来源" min-width="220"><template #default="{ row }">{{ row.section?.source || '未知' }}</template></el-table-column>
    </el-table>
    <p class="sample-note">健康状态和资源指标为各组件所示时间的即时快照。</p>
    <div class="metric-grid">
      <dl><dt>JVM 堆使用 / 上限</dt><dd>{{ bytes(snapshot.jvm?.data?.heapUsedBytes) }} / {{ bytes(snapshot.jvm?.data?.heapMaxBytes) }}</dd></dl>
      <dl><dt>线程数</dt><dd>{{ number(snapshot.jvm?.data?.threadCount) }}</dd></dl>
      <dl><dt>运行时长（秒）</dt><dd>{{ number(snapshot.jvm?.data ? snapshot.jvm.data.uptimeMillis / 1000 : null) }}</dd></dl>
      <dl><dt>数据库活动连接</dt><dd>{{ number(snapshot.pool?.data?.activeConnections) }}</dd></dl>
      <dl><dt>数据库空闲连接</dt><dd>{{ number(snapshot.pool?.data?.idleConnections) }}</dd></dl>
      <dl><dt>数据库连接上限</dt><dd>{{ number(snapshot.pool?.data?.maximumConnections) }}</dd></dl>
    </div>
    <p class="sample-note">JVM：{{ snapshot.jvm?.source }} · {{ time(snapshot.jvm?.sampledAt) }}；连接池：{{ snapshot.pool?.source }} · {{ time(snapshot.pool?.sampledAt) }}</p>
  </el-card>
</template>
<script setup>
import { computed } from 'vue'
import { bytes, number, statuses, time } from './helpers'
const props = defineProps({ snapshot: { type: Object, required: true } })
const healthRows = computed(() => [{ name: '后端', section: props.snapshot.backend }, { name: '数据库', section: props.snapshot.database }, { name: 'Redis', section: props.snapshot.redis }])
</script>
