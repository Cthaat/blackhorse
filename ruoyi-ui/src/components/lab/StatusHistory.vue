<template>
  <section class="status-history" aria-label="状态历史">
    <div class="section-heading">
      <div>
        <h3>状态历史</h3>
        <p>按发生时间记录每一次业务状态变化</p>
      </div>
      <el-button link type="primary" icon="Refresh" :loading="loading" @click="loadHistory">
        刷新
      </el-button>
    </div>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="mb12"
    />

    <div v-loading="loading" class="history-body">
      <el-empty v-if="!loading && !errorMessage && history.length === 0" description="暂无状态变化记录" />
      <el-timeline v-else-if="history.length > 0">
        <el-timeline-item
          v-for="item in history"
          :key="item.id"
          :timestamp="parseTime(item.createTime) || item.createTime"
          placement="top"
        >
          <div class="history-card">
            <div class="status-line">
              <el-tag v-if="item.fromStatus" type="info" effect="plain">{{ item.fromStatus }}</el-tag>
              <span v-else class="initial-status">初始状态</span>
              <el-icon><Right /></el-icon>
              <el-tag type="primary">{{ item.toStatus }}</el-tag>
            </div>
            <p class="history-reason">{{ item.reason || '未填写原因' }}</p>
            <div class="history-meta">
              <span>操作人：{{ item.operatorName || item.operatorId || '系统' }}</span>
              <span v-if="item.traceId">追踪号：{{ item.traceId }}</span>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </section>
</template>

<script setup>
import { parseTime } from '@/utils/ruoyi'
import { listStatusHistory } from '@/api/lab/statusHistory'

const props = defineProps({
  objectType: {
    type: String,
    required: true
  },
  objectId: {
    type: String,
    default: ''
  }
})

const loading = ref(false)
const errorMessage = ref('')
const history = ref([])
let requestSerial = 0

async function loadHistory() {
  if (!props.objectId) {
    history.value = []
    errorMessage.value = ''
    return
  }
  const serial = ++requestSerial
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listStatusHistory(props.objectType, props.objectId)
    if (serial === requestSerial) {
      history.value = Array.isArray(response.data)
        ? response.data.map(item => ({
            ...item,
            id: String(item.id),
            objectId: String(item.objectId),
            operatorId: item.operatorId == null ? '' : String(item.operatorId)
          }))
        : []
    }
  } catch {
    if (serial === requestSerial) {
      history.value = []
      errorMessage.value = '状态历史加载失败，请稍后重试'
    }
  } finally {
    if (serial === requestSerial) {
      loading.value = false
    }
  }
}

watch(() => [props.objectType, props.objectId], loadHistory, { immediate: true })

defineExpose({ refresh: loadHistory })
</script>

<style scoped>
.status-history {
  min-height: 120px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-heading h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
}

.section-heading p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.history-body {
  min-height: 96px;
}

.history-card {
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-blank);
}

.status-line,
.history-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.initial-status,
.history-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.history-reason {
  margin: 10px 0;
  color: var(--el-text-color-primary);
  line-height: 1.6;
}

.history-meta {
  flex-wrap: wrap;
  justify-content: space-between;
}

.mb12 {
  margin-bottom: 12px;
}
</style>
