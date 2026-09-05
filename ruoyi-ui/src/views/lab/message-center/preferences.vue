<template>
  <div class="app-container lab-page">
    <div class="page-heading"><div><h1>通知偏好</h1><p>设置本人可选提醒，关键业务通知始终保留。</p></div></div>
    <el-card shadow="never">
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
      <el-skeleton v-if="loading" :rows="3" animated />
      <el-form v-else-if="loaded" label-position="top" @submit.prevent="save">
        <el-form-item label="巡检超期可选提醒"><el-switch v-model="enabled" active-text="接收提醒" inactive-text="关闭可选提醒" /></el-form-item>
        <p>审批结果、安全处置、候补限时邀请不受此设置影响，无法关闭。</p>
        <p>设置只影响新登记的消息；已登记的消息保留原投递状态。</p>
        <el-button type="primary" :loading="saving" @click="save">保存偏好</el-button>
      </el-form>
    </el-card>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getPreferences, updatePreferences } from '@/api/lab/messageCenter'
const enabled = ref(true), loading = ref(false), loaded = ref(false), saving = ref(false), error = ref('')
async function load() { loading.value = true; loaded.value = false; error.value = ''; try { enabled.value = (await getPreferences()).data.optionalReminders; loaded.value = true } catch (failure) { error.value = failure?.message || '偏好加载失败' } finally { loading.value = false } }
async function save() { if (saving.value) return; saving.value = true; error.value = ''; try { await updatePreferences(enabled.value); ElMessage.success('通知偏好已保存') } catch (failure) { error.value = failure?.message || '保存失败' } finally { saving.value = false } }
onMounted(load)
</script>
