<template>
  <div class="repair-timeline" aria-live="polite">
    <el-empty v-if="!items.length" description="暂无状态流转记录" :image-size="72" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="item in items"
        :key="String(item.id)"
        :timestamp="formatDateTime(item.createTime)"
        placement="top"
        :type="timelineType(item.toStatus)"
      >
        <div class="timeline-card">
          <div class="timeline-title">
            <strong>{{ transitionLabel(item) }}</strong>
            <el-tag size="small" :type="timelineType(item.toStatus)">{{ statusLabel(item.toStatus) }}</el-tag>
          </div>
          <p v-if="item.reason" class="timeline-reason">{{ item.reason }}</p>
          <div class="timeline-meta">
            <span>操作人：{{ item.operatorName || item.operatorId || '系统' }}</span>
            <span v-if="item.traceId">追踪号：{{ item.traceId }}</span>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup name="RepairTimeline">
const props = defineProps({
  history: { type: Array, default: () => [] }
})

const items = computed(() => [...props.history].sort((left, right) =>
  String(left.createTime ?? '').localeCompare(String(right.createTime ?? ''))
))

function statusLabel(status) {
  return {
    WAIT_ASSIGN: '待分派',
    WAIT_REPAIR: '待维修',
    IN_PROGRESS: '维修中',
    WAIT_ACCEPTANCE: '待验收',
    CLOSED: '已关闭'
  }[status] ?? status ?? '未知状态'
}

function transitionLabel(item) {
  if (!item.fromStatus) return `创建为${statusLabel(item.toStatus)}`
  return `${statusLabel(item.fromStatus)} → ${statusLabel(item.toStatus)}`
}

function timelineType(status) {
  return {
    WAIT_ASSIGN: 'warning',
    WAIT_REPAIR: 'warning',
    IN_PROGRESS: 'primary',
    WAIT_ACCEPTANCE: 'warning',
    CLOSED: 'success'
  }[status] ?? 'info'
}

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}
</script>

<style scoped>
.repair-timeline {
  padding: 8px 4px 0;
}

.timeline-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
}

.timeline-title,
.timeline-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.timeline-reason {
  margin: 8px 0;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  white-space: pre-wrap;
}

.timeline-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 640px) {
  .timeline-title,
  .timeline-meta {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
