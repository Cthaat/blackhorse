<template>
  <section aria-label="规则版本管理">
    <div class="workspace-toolbar"><el-button type="primary" :disabled="!bounds || !!busy" @click="editing = null; editorOpen = true">新建草稿</el-button><el-button :loading="loading" @click="load">刷新版本</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="rows" highlight-current-row @current-change="select" empty-text="尚无设备规则版本">
      <el-table-column prop="versionNumber" label="版本" width="75" />
      <el-table-column prop="definition.name" label="名称" min-width="160" />
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ labels[row.status] || row.status }}</template></el-table-column>
      <el-table-column label="操作" min-width="240"><template #default="{ row }">
        <el-button link :disabled="!!busy" @click.stop="select(row)">查看 / 对比</el-button>
        <el-button v-if="row.status === 'DRAFT'" link type="primary" :disabled="!!busy" @click.stop="editing = row; editorOpen = true">编辑</el-button>
        <el-button v-if="row.status === 'DRAFT'" link type="success" :disabled="!!busy" @click.stop="command(row, 'publish')">发布</el-button>
        <el-button v-if="row.status === 'PUBLISHED'" link type="danger" :disabled="!!busy" @click.stop="command(row, 'retire')">停用</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-if="total" v-model:current-page="page" :page-size="10" :total="total" layout="prev, pager, next" @current-change="load" />
    <template v-if="selected">
      <RuleComparison :selected="selected" :active="active" :bounds="bounds" />
      <p v-if="selected.status === 'DRAFT'">可切换到“开放日历”对所选草稿进行时段试算。</p>
      <RuleImpact v-if="canInspect" :rule-id="selected.id" :revision="selected.revision" />
    </template>
    <RuleEditor v-model="editorOpen" :rule="editing" :device-id="deviceId" :bounds="bounds" @saved="saved" />
  </section>
</template>
<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import { listRules, ruleCommand } from '@/api/lab/reservationWorkspace'
import RuleEditor from './RuleEditor.vue'
import RuleComparison from './RuleComparison.vue'
import RuleImpact from './RuleImpact.vue'
import { messageOf } from './helpers'
const props = defineProps({ deviceId: String, active: Object, bounds: Object })
const emit = defineEmits(['changed', 'selected'])
const canInspect = computed(() => checkPermi(['lab:reservation:list']))
const labels = { DRAFT: '草稿', PUBLISHED: '已发布', RETIRED: '已停用' }
const rows = ref([]), total = ref(0), page = ref(1), loading = ref(false), error = ref(''), busy = ref('')
const selected = ref(null), editing = ref(null), editorOpen = ref(false)
let sequence = 0
function select(row) { selected.value = row; emit('selected', row?.status === 'DRAFT' ? row : null) }
async function load() {
  const current = ++sequence
  loading.value = true; error.value = ''; rows.value = []; total.value = 0; select(null)
  try { const response = await listRules({ deviceId: props.deviceId, pageNum: page.value, pageSize: 10 }); if (current === sequence) { rows.value = response.rows || []; total.value = response.total || 0 } }
  catch (failure) { if (current === sequence) error.value = messageOf(failure) }
  finally { if (current === sequence) loading.value = false }
}
async function command(row, kind) {
  if (busy.value) return
  busy.value = row.id; error.value = ''
  try {
    await ElMessageBox.confirm(kind === 'retire' ? '停用后设备恢复全局预约规则。已有预约不自动取消，是否继续？' : '发布后此版本不可编辑，并替换当前生效规则。已有预约不自动取消，是否继续？', '确认规则变更')
    await ruleCommand(row.id, kind, row.revision)
    ElMessage.success(kind === 'retire' ? '规则已停用' : '规则已发布')
    await saved()
  } catch (failure) { if (failure !== 'cancel' && failure !== 'close') error.value = messageOf(failure) }
  finally { busy.value = '' }
}
async function saved() { await load(); emit('changed') }
watch(() => props.deviceId, () => { page.value = 1; editorOpen.value = false; load() }, { immediate: true })
onBeforeUnmount(() => sequence++)
</script>
