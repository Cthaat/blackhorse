<template>
  <el-dialog v-model="visible" title="爽约规则版本" width="780px" append-to-body>
    <p>默认新爽约限制为 7 天。发布的新版本不追罚历史爽约，也不修改已有处罚。</p>
    <div class="toolbar">
      <el-select v-model="laboratoryId" filterable aria-label="规则实验室" placeholder="选择实验室" @change="load">
        <el-option v-for="lab in laboratories" :key="lab.id" :label="lab.name" :value="String(lab.id)" />
      </el-select>
      <el-button type="primary" @click="$emit('publish')">发布新规则</el-button>
      <el-button :disabled="!laboratoryId" :loading="loading" @click="load">刷新版本</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-table :data="rows" v-loading="loading" :empty-text="laboratoryId ? '没有自定义版本，使用默认 7 天规则' : '请先选择实验室'">
      <el-table-column prop="id" label="版本" width="100" />
      <el-table-column prop="days" label="限制天数" width="110" />
      <el-table-column prop="reason" label="发布说明" min-width="180" />
      <el-table-column label="发布时间" min-width="180"><template #default="{row}">{{ parseTime(row.createdAt) }}</template></el-table-column>
    </el-table>
    <p>按发布时间倒序显示最近 100 个版本；已发布版本不可编辑。</p>
  </el-dialog>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { restrictionRules } from '@/api/lab/restrictions'
import { parseTime } from '@/utils/ruoyi'
const props = defineProps({ modelValue: Boolean, laboratories: Array, revision: Number })
const emit = defineEmits(['update:modelValue', 'publish'])
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const laboratoryId = ref(''), rows = ref([]), loading = ref(false), error = ref('')
let serial = 0
async function load() {
  if (!laboratoryId.value) return
  const current = ++serial
  loading.value = true; error.value = ''
  try { const r = await restrictionRules(laboratoryId.value); if (current === serial) rows.value = r.data }
  catch (e) { if (current === serial) { rows.value = []; error.value = e.message || '版本加载失败' } }
  finally { if (current === serial) loading.value = false }
}
watch(() => [props.modelValue, props.revision], () => { if (props.modelValue) load() })
</script>
<style scoped>
.toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
.el-select { width: 240px; }
p { color: var(--el-text-color-secondary); line-height: 1.8; }
</style>
