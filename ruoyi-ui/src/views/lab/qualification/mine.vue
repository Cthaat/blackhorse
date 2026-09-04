<template>
  <div class="app-container">
    <el-card shadow="never" class="summary-card">
      <div class="summary-content">
        <div>
          <div class="summary-title">我的实验室资格</div>
          <div class="summary-subtitle">查看当前账号的实验室或设备类别准入资格及有效期</div>
        </div>
        <el-button icon="Refresh" :loading="loading" @click="getList">刷新</el-button>
      </div>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="mb16">
      <template #default><el-button link type="primary" @click="getList">重新加载</el-button></template>
    </el-alert>

    <el-table v-loading="loading" :data="qualificationList" @row-dblclick="handleDetail">
      <el-table-column label="适用范围" width="140">
        <template #default="scope"><dict-tag :options="lab_qualification_scope_type" :value="scope.row.scopeType" /></template>
      </el-table-column>
      <el-table-column label="范围对象" min-width="200" show-overflow-tooltip>
        <template #default="scope">{{ scopeLabel(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="生效时间" min-width="180">
        <template #default="scope">{{ parseTime(scope.row.validFrom) }}</template>
      </el-table-column>
      <el-table-column label="失效时间" min-width="180">
        <template #default="scope">{{ parseTime(scope.row.validUntil) }}</template>
      </el-table-column>
      <el-table-column label="当前状态" width="120" align="center">
        <template #default="scope">
          <el-tag :type="statusMeta(scope.row.status).type">{{ statusMeta(scope.row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleDetail(scope.row)" v-hasPermi="['lab:qualification:mine']">
            详情
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty :description="errorMessage ? '资格信息不可用' : '当前账号暂无实验室资格'" />
      </template>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-drawer v-model="detailOpen" title="我的资格详情" size="min(820px, 92vw)" append-to-body>
      <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false" class="mb16">
        <template #default><el-button link type="primary" @click="loadDetail">重新加载</el-button></template>
      </el-alert>
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <el-descriptions :column="2" border class="mb20">
            <el-descriptions-item label="当前状态">
              <el-tag :type="statusMeta(detail.status).type">{{ statusMeta(detail.status).label }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="适用范围">
              <dict-tag :options="lab_qualification_scope_type" :value="detail.scopeType" />
            </el-descriptions-item>
            <el-descriptions-item label="范围对象" :span="2">{{ scopeLabel(detail) }}</el-descriptions-item>
            <el-descriptions-item label="生效时间">{{ parseTime(detail.validFrom) }}</el-descriptions-item>
            <el-descriptions-item label="失效时间">{{ parseTime(detail.validUntil) }}</el-descriptions-item>
            <el-descriptions-item label="撤销时间">{{ parseTime(detail.revokedAt) || '—' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ parseTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="撤销原因" :span="2">{{ detail.revokeReason || '—' }}</el-descriptions-item>
          </el-descriptions>
          <el-tabs>
            <el-tab-pane v-if="canReadAttachment" label="附件">
              <attachment-panel business-type="QUALIFICATION" :business-id="detail.id" :can-manage="false" />
            </el-tab-pane>
            <el-tab-pane label="状态历史">
              <status-history object-type="QUALIFICATION" :object-id="detail.id" />
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-empty v-else-if="!detailLoading && !detailError" description="未找到资格信息" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="MyLabQualifications">
import { parseTime } from '@/utils/ruoyi'
import { getQualification, listMyQualification } from '@/api/lab/qualification'
import AttachmentPanel from '@/components/lab/AttachmentPanel.vue'
import StatusHistory from '@/components/lab/StatusHistory.vue'

const { proxy } = getCurrentInstance()
const { lab_qualification_scope_type } = useDict('lab_qualification_scope_type')

const loading = ref(false)
const errorMessage = ref('')
const qualificationList = ref([])
const total = ref(0)
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailId = ref('')
const detail = ref(null)

const canReadAttachment = computed(() => proxy.$auth.hasPermi('lab:attachment:read'))
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  sortBy: 'createTime',
  sortDirection: 'desc'
})

const qualificationStatuses = {
  VALID: { label: '有效', type: 'success' },
  NOT_EFFECTIVE: { label: '未生效', type: 'info' },
  EXPIRED: { label: '已过期', type: 'warning' },
  REVOKED: { label: '已撤销', type: 'danger' }
}

function statusMeta(status) {
  return qualificationStatuses[status] || { label: status || '未知', type: 'info' }
}

function normalizeQualification(row) {
  return { ...row, id: String(row.id), userId: String(row.userId), scopeId: String(row.scopeId) }
}

function scopeLabel(row) {
  return row.scopeType === 'LABORATORY' ? `实验室 ${row.scopeId}` : `设备类别 ${row.scopeId}`
}

async function getList() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listMyQualification(queryParams)
    qualificationList.value = (response.rows || []).map(normalizeQualification)
    total.value = response.total || 0
  } catch {
    qualificationList.value = []
    total.value = 0
    errorMessage.value = '我的资格加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function handleDetail(row) {
  detailId.value = String(row.id)
  detailOpen.value = true
  await loadDetail()
}

async function loadDetail() {
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  try {
    const response = await getQualification(detailId.value)
    detail.value = response.data ? normalizeQualification(response.data) : null
  } catch {
    detailError.value = '资格详情加载失败，请稍后重试'
  } finally {
    detailLoading.value = false
  }
}

getList()
</script>

<style scoped>
.summary-card { margin-bottom: 16px; }
.summary-content { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.summary-title { color: var(--el-text-color-primary); font-size: 18px; font-weight: 600; }
.summary-subtitle { color: var(--el-text-color-secondary); font-size: 13px; margin-top: 6px; }
.detail-body { min-height: 240px; }
.mb16 { margin-bottom: 16px; }
.mb20 { margin-bottom: 20px; }
@media (max-width: 640px) {
  .summary-content { align-items: flex-start; flex-direction: column; }
}
</style>
