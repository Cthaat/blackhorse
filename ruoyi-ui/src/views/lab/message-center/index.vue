<template>
  <div class="app-container lab-page message-workspace">
    <div class="page-heading"><div><h1>消息投递中心</h1><p>管理站内投递、模板版本与通知渠道，查看失败原因并按事实重放。</p></div></div>
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane v-if="canDelivery" label="投递记录" name="deliveries"><DeliveryPanel v-if="tab === 'deliveries'" /></el-tab-pane>
        <el-tab-pane v-if="canTemplate" label="消息模板" name="templates"><TemplatePanel v-if="tab === 'templates'" /></el-tab-pane>
        <el-tab-pane v-if="canDelivery" label="通知渠道" name="channels"><ChannelPanel v-if="tab === 'channels'" /></el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { checkPermi } from '@/utils/permission'
import DeliveryPanel from './components/DeliveryPanel.vue'
import TemplatePanel from './components/TemplatePanel.vue'
import ChannelPanel from './components/ChannelPanel.vue'
const canDelivery = checkPermi(['lab:delivery:list'])
const canTemplate = checkPermi(['lab:message-template:list'])
const tab = ref(canDelivery ? 'deliveries' : 'templates')
</script>
<style>
.message-workspace .message-toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; align-items: center; }
.message-workspace .message-toolbar .el-input, .message-workspace .message-toolbar .el-select { width: 220px; max-width: 100%; }
.message-workspace .message-note { color: var(--el-text-color-secondary); line-height: 1.6; }
.message-workspace .message-content { white-space: pre-wrap; overflow-wrap: anywhere; }
.message-workspace .el-alert { margin-bottom: 16px; }
.message-workspace .el-pagination { margin-top: 16px; }
</style>
