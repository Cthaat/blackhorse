<template>
  <section aria-label="通知渠道">
    <p>目前只启用站内消息。外部渠道尚未接入，不能启用。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
    <el-table v-loading="loading" :data="rows" empty-text="暂无渠道信息"><el-table-column prop="name" label="渠道" /><el-table-column label="状态"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '已启用' : '未接入' }}</el-tag></template></el-table-column></el-table>
  </section>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { listChannels } from '@/api/lab/messageCenter'
const rows = ref([]), loading = ref(false), error = ref('')
async function load() { loading.value = true; error.value = ''; try { rows.value = (await listChannels()).data || [] } catch (failure) { error.value = failure?.message || '渠道加载失败' } finally { loading.value = false } }
onMounted(load)
</script>
