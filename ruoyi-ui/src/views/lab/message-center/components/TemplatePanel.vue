<template>
  <section aria-label="消息模板">
    <p class="message-note">发布版本不可修改。新投递使用最新发布版本；历史投递保留原始快照。没有自定义模板时使用内置版本 builtin:1。</p>
    <div class="message-toolbar">
      <el-input v-model="eventType" placeholder="按事件类型查版本" clearable maxlength="32" aria-label="模板事件类型" @keyup.enter="filter" />
      <el-button :loading="loading" @click="filter">查询版本</el-button>
      <el-button v-if="canEdit" type="primary" @click="editing = null; editorOpen = true">新建草稿</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link @click="load">重新加载</el-button></el-alert>
    <el-table v-loading="loading" :data="rows" empty-text="尚无自定义模板，当前使用内置版本">
      <el-table-column prop="id" label="版本编号" width="90" />
      <el-table-column prop="eventType" label="事件类型" min-width="220" />
      <el-table-column prop="title" label="标题模板" min-width="200" />
      <el-table-column label="状态" width="95"><template #default="{ row }">{{ row.status === 'DRAFT' ? '草稿' : '已发布' }}</template></el-table-column>
      <el-table-column prop="publishTime" label="发布时间" min-width="180" />
      <el-table-column label="操作" min-width="190"><template #default="{ row }">
        <el-button link @click="editing = row; editorOpen = true">{{ row.status === 'DRAFT' && canEdit ? '编辑 / 预览' : '查看版本' }}</el-button>
        <el-button v-if="row.status === 'DRAFT' && canEdit" link type="primary" :disabled="busy" @click="publish(row)">发布</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-if="total" v-model:current-page="page" :page-size="10" :total="total" layout="prev, pager, next" @current-change="load" />
    <TemplateEditor v-model="editorOpen" :template="editing" :can-edit="canEdit" @saved="load" />
  </section>
</template>
<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import { listTemplateVersions, publishTemplate } from '@/api/lab/messageCenter'
import TemplateEditor from './TemplateEditor.vue'
const canEdit = checkPermi(['lab:message-template:edit'])
const rows = ref([]), total = ref(0), page = ref(1), eventType = ref(''), loading = ref(false), busy = ref(false), error = ref('')
const editing = ref(null), editorOpen = ref(false)
let sequence = 0
async function load() {
  const current = ++sequence; loading.value = true; error.value = ''
  try { const result = await listTemplateVersions({ eventType: eventType.value, pageNum: page.value, pageSize: 10 }); if (current === sequence) { rows.value = result.rows || []; total.value = result.total || 0 } }
  catch (failure) { if (current === sequence) { error.value = failure?.message || '模板加载失败'; rows.value = []; total.value = 0 } }
  finally { if (current === sequence) loading.value = false }
}
function filter() { page.value = 1; load() }
async function publish(row) {
  if (busy.value) return
  busy.value = true
  try { await ElMessageBox.confirm('发布后内容不可修改，新登记的消息将使用此版本。是否发布？', '发布模板'); await publishTemplate(row.id); ElMessage.success('模板已发布'); await load() }
  catch (failure) { if (failure !== 'cancel' && failure !== 'close') error.value = failure?.message || '发布失败' }
  finally { busy.value = false }
}
onMounted(load)
onBeforeUnmount(() => sequence++)
</script>
