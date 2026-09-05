<template>
  <div class="app-container lab-page">
    <div class="page-heading"><div><h1>异步任务中心</h1><p>导入先预检，导出在后台生成。取消不会撤销已导入的数据，文件保留 7 天。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-card shadow="never" class="task-create">
      <div class="task-toolbar">
        <el-select v-model="kind" aria-label="业务类型" style="width:180px"><el-option v-for="item in kinds" :key="item.value" :label="item.label" :value="item.value" /></el-select>
        <el-button v-if="canImport" @click="template">下载导入模板</el-button>
        <label v-if="canImport" class="file-label">选择 XLSX 文件<input type="file" accept=".xlsx" :disabled="busy" @change="chooseFile" /></label>
        <el-button v-if="canExport" type="primary" :loading="busy" @click="createExport">生成业务报表</el-button>
      </div>
      <p class="task-hint">导入仅新增实验室或设备，最多 5 MiB／5,000 行；不要修改模板列名或添加公式。编号、负责人、部门和实验室 ID 可在对应业务页面查询。报表最多 50,000 行。</p>
    </el-card>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-card shadow="never" v-loading="loading">
      <el-table :data="rows" empty-text="还没有任务，可先下载模板或生成报表">
        <el-table-column prop="id" label="任务编号" width="100" />
        <el-table-column label="业务" width="120"><template #default="{row}">{{ names[row.kind] }}</template></el-table-column>
        <el-table-column label="方向" width="80"><template #default="{row}">{{ row.direction === 'IMPORT' ? '导入' : '导出' }}</template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag>{{ states[row.status] || row.status }}</el-tag></template></el-table-column>
        <el-table-column label="进度" min-width="180"><template #default="{row}">成功 {{ row.successCount }} / 错误 {{ row.failureCount }} / 总计 {{ row.totalCount }}</template></el-table-column>
        <el-table-column label="创建时间" min-width="175"><template #default="{row}">{{ parseTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" min-width="310"><template #default="{row}">
          <el-button link type="primary" @click="detail(row)">详情</el-button>
          <el-button v-if="row.status === 'PRECHECKED' && row.totalCount > row.failureCount" link type="primary" :disabled="busy" @click="command(row,'submit')">确认导入</el-button>
          <el-button v-if="['PRECHECKED','QUEUED','RUNNING'].includes(row.status)" link type="warning" :disabled="busy" @click="command(row,'cancel')">取消</el-button>
          <el-button v-if="['FAILED','PARTIAL','CANCELLED'].includes(row.status)" link type="primary" :disabled="busy" @click="command(row,'retry')">重试</el-button>
          <el-button v-if="row.resultAvailable" link type="primary" @click="download(row,false)">结果</el-button>
          <el-button v-if="row.errorAvailable" link type="danger" @click="download(row,true)">错误行</el-button>
        </template></el-table-column>
      </el-table>
      <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    </el-card>
    <el-dialog v-model="open" title="任务详情与逐行结果" width="760px" append-to-body>
      <template v-if="selected">
        <p>任务 {{ selected.id }} · {{ states[selected.status] }} · 关联号 {{ selected.traceId || '未记录' }}</p>
        <p>结果到期：{{ parseTime(selected.expiresAt) }}。错误码：{{ selected.errorCode || '无' }}。</p>
        <el-alert v-if="selected.status === 'PRECHECKED'" title="预检通过不代表已导入，确认后才写入合法行。错误行不写入，请修正后另行上传。" type="info" :closable="false" />
        <el-table :data="details" v-loading="detailLoading"><el-table-column prop="rowNo" label="原始行号" /><el-table-column prop="status" label="状态" /><el-table-column prop="objectId" label="对象编号" /><el-table-column prop="errorCode" label="错误码" min-width="160" /></el-table>
        <pagination :total="selected.totalCount" v-model:page="detailQuery.pageNum" v-model:limit="detailQuery.pageSize" @pagination="loadDetails" />
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveAs } from 'file-saver'
import useUserStore from '@/store/modules/user'
import { listTasks, getTask, taskRows, taskCommand, precheckTask, exportTask, downloadTask, taskTemplate } from '@/api/lab/businessTasks'
const user = useUserStore()
const allowed = p => user.permissions.includes('*:*:*') || user.permissions.includes(p)
const names = { LABORATORY:'实验室', DEVICE:'设备', RESERVATION:'预约', REPAIR:'维修', HAZARD:'隐患' }
const stems = { LABORATORY:'laboratory', DEVICE:'device', RESERVATION:'reservation', REPAIR:'repair', HAZARD:'hazard' }
const kinds = computed(() => Object.entries(names).filter(([k]) => allowed(`lab:${stems[k]}:list`) || (k === 'RESERVATION' && allowed('lab:reservation:mine'))).map(([value,label]) => ({value,label})))
const kind = ref(kinds.value[0]?.value || 'RESERVATION')
const canImport = computed(() => ['LABORATORY','DEVICE'].includes(kind.value) && allowed('lab:task:import') && allowed(`lab:${stems[kind.value]}:add`))
const canExport = computed(() => allowed('lab:task:export') && kinds.value.some(k => k.value === kind.value))
const states = { PRECHECKED:'待确认', QUEUED:'排队中', RUNNING:'执行中', CANCELLING:'取消中', CANCELLED:'已取消', SUCCEEDED:'成功', PARTIAL:'部分成功', FAILED:'失败' }
const rows=ref([]),total=ref(0),loading=ref(false),busy=ref(false),error=ref(''),open=ref(false),selected=ref(null),details=ref([]),detailLoading=ref(false)
const query=reactive({pageNum:1,pageSize:10}),detailQuery=reactive({pageNum:1,pageSize:10})
let timer,disposed=false
async function load() { if(loading.value)return;loading.value=true;error.value='';try {const r=await listTasks(query);rows.value=r.data.rows;total.value=r.data.total} catch(e){error.value=e.message||'加载失败'}finally{loading.value=false} }
async function run(action){busy.value=true;error.value='';try{await action();await load()}catch(e){if(e!=='cancel'&&e!=='close')error.value=e.message||'操作失败，请重试'}finally{busy.value=false}}
async function chooseFile(event){const file=event.target.files?.[0];event.target.value='';if(!file)return;if(file.size>5*1024*1024){error.value='文件不能超过 5 MiB';return}await run(async()=>{const r=await precheckTask(kind.value,file);await detail(r.data);ElMessage.success('预检完成，请查看逐行结果并确认导入')})}
async function template(){await run(async()=>saveAs(await taskTemplate(kind.value),`${kind.value}-template.xlsx`))}
async function createExport(){await run(async()=>{await exportTask(kind.value);ElMessage.success('报表任务已提交')})}
async function command(row,cmd){await run(async()=>{await ElMessageBox.confirm(cmd==='cancel'?'取消不会撤销已提交的导入数据，是否继续？':cmd==='submit'?`将导入 ${row.totalCount-row.failureCount} 行合法数据，错误行跳过。是否继续？`:'将创建新任务重试，保留原记录，是否继续？','确认操作');await taskCommand(row.id,cmd)})}
async function detail(row){selected.value=row;detailQuery.pageNum=1;open.value=true;await loadDetails()}
async function loadDetails(){detailLoading.value=true;try{const [t,r]=await Promise.all([getTask(selected.value.id),taskRows(selected.value.id,detailQuery)]);selected.value=t.data;details.value=r.data}catch(e){error.value=e.message||'详情加载失败'}finally{detailLoading.value=false}}
async function download(row,errors){await run(async()=>saveAs(await downloadTask(row.id,errors),`task-${row.id}${errors?'-errors':''}.xlsx`))}
async function poll(){await load();if(!disposed)timer=setTimeout(poll,5000)}
onMounted(poll);onBeforeUnmount(()=>{disposed=true;clearTimeout(timer)})
</script>
<style scoped>
.task-create{margin-bottom:16px}.task-toolbar{display:flex;flex-wrap:wrap;gap:12px;align-items:center}.task-hint{color:var(--el-text-color-secondary);line-height:1.8}.file-label{display:flex;flex-wrap:wrap;gap:8px;align-items:center}.file-label input{max-width:240px}.el-alert{margin-bottom:16px}
</style>
