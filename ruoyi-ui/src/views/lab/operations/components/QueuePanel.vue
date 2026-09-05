<template>
  <el-card shadow="never">
    <template #header><h2>后台处理</h2></template>
    <el-empty v-if="!section?.data" description="后台处理指标未知，未使用零值代替" />
    <template v-else>
      <div class="metric-grid">
        <dl><dt>待处理投递</dt><dd>{{ number(section.data.deliveryBacklog) }}</dd></dl>
        <dl><dt>最老积压年龄（秒）</dt><dd>{{ section.data.oldestDeliveryAgeSeconds == null ? '无积压' : number(section.data.oldestDeliveryAgeSeconds) }}</dd></dl>
        <dl><dt>24 小时成功 / 部分成功任务平均耗时（毫秒）</dt><dd>{{ section.data.successfulTaskDuration?.averageMillis == null ? '无样本' : number(section.data.successfulTaskDuration.averageMillis) }}</dd></dl>
      </div>
      <h3>定位积压与失败记录</h3>
      <ul class="queue-references">
        <li v-if="section.data.references?.oldestDeliveryId">最老投递：<router-link v-if="canDelivery" :to="deliveryLink(section.data.references.oldestDeliveryId)">#{{ section.data.references.oldestDeliveryId }} · 打开投递详情</router-link><span v-else>#{{ section.data.references.oldestDeliveryId }}</span></li>
        <li v-if="section.data.references?.failedDeliveryId">人工处理投递：<router-link v-if="canDelivery" :to="deliveryLink(section.data.references.failedDeliveryId)">#{{ section.data.references.failedDeliveryId }} · 打开投递详情</router-link><span v-else>#{{ section.data.references.failedDeliveryId }}</span></li>
        <li v-if="section.data.references?.oldestTaskId">最老活动任务：#{{ section.data.references.oldestTaskId }}</li>
        <li v-if="section.data.references?.failedTaskId">失败任务：#{{ section.data.references.failedTaskId }}</li>
      </ul>
      <p v-if="section.data.references?.oldestTaskId || section.data.references?.failedTaskId" class="sample-note"><router-link v-if="canTask" to="/lab/task-center">打开本人任务中心</router-link> 任务详情仍按本人权限控制；上述运维编号用于定位，不授予读取他人任务的权限。</p>
      <h3>投递状态</h3>
      <el-table :data="section.data.deliveries" empty-text="尚无投递记录"><el-table-column prop="status" label="状态" /><el-table-column prop="count" label="数量" /></el-table>
      <h3>任务状态</h3>
      <el-table :data="section.data.tasks" empty-text="尚无任务记录"><el-table-column prop="status" label="状态" /><el-table-column prop="count" label="数量" /></el-table>
      <p class="sample-note">成功耗时样本：{{ number(section.data.successfulTaskDuration?.completedCount) }}。状态数量为全库即时快照，耗时取最近 24 小时完成任务；不包括排队等待时间。</p>
    </template>
    <p class="sample-note">来源：{{ section?.source || '未知' }}；采样：{{ time(section?.sampledAt) }}。</p>
  </el-card>
</template>
<script setup>
import { number, time } from './helpers'
import { checkPermi } from '@/utils/permission'
defineProps({ section: Object })
const canDelivery = checkPermi(['lab:delivery:list'])
const canTask = checkPermi(['lab:task:list'])
const deliveryLink = deliveryId => ({ path: '/lab/message-center', query: { deliveryId } })
</script>
