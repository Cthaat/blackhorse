<template>
  <div class="app-container lab-page">
    <div class="page-heading">
      <div><h1>预约限制与申诉</h1><p>查看限制原因与期限，保留每一次事实、申诉和处理记录。</p></div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert title="限制按实验室生效，不禁用登录。多条限制同时有效时，解除其中一条不会解除其他条。" type="info" :closable="false" show-icon />
    <div class="toolbar">
      <el-select v-if="allowed('list')" v-model="query.mine" aria-label="查看范围" @change="search">
        <el-option label="我的限制" :value="true" /><el-option label="管理范围" :value="false" />
      </el-select>
      <el-select v-model="query.status" clearable aria-label="限制状态" placeholder="全部状态" @change="search">
        <el-option label="生效中" value="ACTIVE" /><el-option label="已到期" value="EXPIRED" /><el-option label="已解除" value="REVOKED" />
      </el-select>
      <el-select v-if="allowed('list')" v-model="query.laboratoryId" filterable clearable aria-label="筛选实验室" placeholder="全部实验室" @change="search">
        <el-option v-for="lab in laboratories" :key="lab.id" :value="String(lab.id)" :label="lab.name" />
      </el-select>
      <el-select v-if="allowed('list') && !query.mine" v-model="query.userId" filterable clearable aria-label="筛选受限用户" placeholder="全部学生" @change="search">
        <el-option v-for="person in users" :key="person.id" :value="person.id" :label="person.label" />
      </el-select>
      <el-button v-if="allowed('manual')" type="primary" :disabled="!!optionsError" @click="act('manual')">登记限制</el-button>
      <el-button v-if="allowed('rule')" @click="ruleOpen = true">爽约规则版本</el-button>
    </div>
    <el-alert v-if="optionsError" :title="optionsError" type="warning" :closable="false"><el-button link @click="loadOptions">重新加载选项</el-button></el-alert>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <p class="lab-table-hint">左右滑动表格可查看完整信息与操作</p>
    <el-table :data="rows" v-loading="loading" empty-text="当前范围没有符合条件的限制记录">
      <el-table-column prop="id" label="编号" width="85" />
      <el-table-column prop="laboratoryName" label="实验室" min-width="150" />
      <el-table-column prop="userName" label="受限用户" min-width="130" />
      <el-table-column label="来源" width="110"><template #default="{row}">{{ row.source === 'NO_SHOW' ? '预约爽约' : '手工登记' }}</template></el-table-column>
      <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="110"><template #default="{row}"><el-tag>{{ restrictionState(row) }}</el-tag></template></el-table-column>
      <el-table-column label="截止时间" min-width="175"><template #default="{row}">{{ parseTime(row.endsAt) }}</template></el-table-column>
      <el-table-column label="操作" width="170"><template #default="{row}">
        <el-button link type="primary" @click="detail(row.id)">详情／申诉</el-button>
        <el-button v-if="allowed('revoke') && !isOwner(row) && row.status !== 'REVOKED'" link type="danger" @click="act('revoke', row)">解除</el-button>
      </template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="query.pageNum" v-model:limit="query.pageSize" @pagination="load" />
    <el-dialog v-model="detailOpen" title="限制详情与处理记录" width="850px" append-to-body>
      <el-skeleton v-if="detailLoading" :rows="5" animated aria-label="正在加载限制详情" />
      <RestrictionDetail v-else-if="selected" :row="selected" :owner="isOwner(selected)" :can-appeal="allowed('appeal')" :can-review="allowed('review')" @action="mode => act(mode, selected)" />
    </el-dialog>
    <RestrictionCommand v-model="commandOpen" :mode="commandMode" :row="commandRow" :laboratories="laboratories" :users="users" @completed="completed" />
    <RestrictionRules v-model="ruleOpen" :laboratories="laboratories" @publish="act('rule')" :revision="revision" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import useUserStore from '@/store/modules/user'
import { useRoute } from 'vue-router'
import { parseTime } from '@/utils/ruoyi'
import { loadAllOptions } from '@/utils/labOptions'
import { listLaboratory } from '@/api/lab/laboratory'
import { listLabUserOptions } from '@/api/lab/options'
import { getRestriction, listRestrictions } from '@/api/lab/restrictions'
import { restrictionState } from './presentation'
import RestrictionCommand from './components/RestrictionCommand.vue'
import RestrictionDetail from './components/RestrictionDetail.vue'
import RestrictionRules from './components/RestrictionRules.vue'

const user = useUserStore()
const route = useRoute()
const allowed = suffix => user.permissions.includes('*:*:*') || user.permissions.includes(`lab:restriction:${suffix}`)
const isOwner = row => String(row.userId) === String(user.id)
const query = reactive({ mine: route.path.endsWith('/my-restrictions') || !allowed('list'), status: '', laboratoryId: '', userId: '', pageNum: 1, pageSize: 10 })
const rows = ref([]), total = ref(0), loading = ref(false), error = ref(''), optionsError = ref('')
const laboratories = ref([]), users = ref([]), selected = ref(null), detailOpen = ref(false)
const commandMode = ref('manual'), commandRow = ref(null), commandOpen = ref(false), ruleOpen = ref(false), revision = ref(0)
const detailLoading = ref(false)
let serial = 0, detailSerial = 0
async function load() {
  const current = ++serial
  loading.value = true; error.value = ''
  try {
    const result = await listRestrictions(query)
    if (current === serial) { rows.value = result.rows; total.value = Number(result.total) }
  } catch (e) { if (current === serial) { rows.value = []; total.value = 0; error.value = e.message || '限制列表加载失败' } }
  finally { if (current === serial) loading.value = false }
}
function search() { query.pageNum = 1; load() }
async function loadOptions() {
  if (!allowed('list') && !allowed('manual') && !allowed('rule')) return
  optionsError.value = ''
  try {
    const labs = await loadAllOptions(listLaboratory)
    laboratories.value = labs.rows
    if (allowed('manual') || allowed('list')) {
      const people = await listLabUserOptions({ roleKey: 'lab_student' })
      users.value = people.data.filter(person => String(person.id) !== String(user.id)).map(person => ({
        id: String(person.id), label: `${person.displayName || person.userName}（${person.userName}）`
      }))
    }
  } catch { optionsError.value = '管理选项加载失败，请重试；未加载完整前不能登记限制'; laboratories.value = []; users.value = [] }
}
async function detail(id) {
  const current = ++detailSerial
  detailLoading.value = true
  selected.value = null
  detailOpen.value = true
  try {
    const result = (await getRestriction(String(id))).data
    if (current === detailSerial) selected.value = { ...result.restriction, appeal: result.appeal, history: result.history }
  }
  catch (e) { if (current === detailSerial) { error.value = e.message || '详情加载失败'; detailOpen.value = false } }
  finally { if (current === detailSerial) detailLoading.value = false }
}
function act(mode, row = null) { commandMode.value = mode; commandRow.value = row; commandOpen.value = true }
async function completed() {
  revision.value++
  await load()
  if (detailOpen.value && selected.value) await detail(selected.value.id)
}
onMounted(() => { load(); loadOptions() })
</script>

<style scoped>
.toolbar { display: flex; flex-wrap: wrap; gap: 12px; margin: 20px 0; }
.toolbar .el-select { width: 200px; }
.el-alert { margin-bottom: 12px; }
@media (max-width: 640px) { .toolbar .el-select { width: 100%; } }
</style>
