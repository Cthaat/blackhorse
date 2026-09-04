<template>
  <div v-loading="loading" class="rectification-timeline">
    <el-empty v-if="!loading && !rounds.length" description="暂无整改提交记录" :image-size="72" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="round in rounds"
        :key="round.id"
        :timestamp="parseTime(round.submittedAt)"
        :type="timelineType(round.reviewResult)"
        placement="top"
      >
        <el-card shadow="never">
          <template #header>
            <div class="round-header">
              <span>第 {{ round.roundNo }} 轮整改</span>
              <el-tag :type="tagType(round.reviewResult)" size="small">
                {{ reviewText(round.reviewResult) }}
              </el-tag>
            </div>
          </template>
          <p class="description">{{ round.description }}</p>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="提交人">{{ round.submitterId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="复核人">{{ round.reviewerId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="复核时间">{{ parseTime(round.reviewedAt) || '-' }}</el-descriptions-item>
            <el-descriptions-item label="复核意见">{{ round.reviewReason || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="$slots.actions" class="actions">
            <slot name="actions" :round="round" />
          </div>
        </el-card>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup>
defineProps({
  rounds: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

function reviewText(result) {
  return { PASSED: '复核通过', REJECTED: '退回整改' }[result] || '等待复核'
}

function tagType(result) {
  return { PASSED: 'success', REJECTED: 'danger' }[result] || 'warning'
}

function timelineType(result) {
  return { PASSED: 'success', REJECTED: 'danger' }[result] || 'primary'
}
</script>

<style scoped>
.round-header { display: flex; align-items: center; justify-content: space-between; font-weight: 600; }
.description { margin: 0 0 14px; white-space: pre-wrap; line-height: 1.7; }
.actions { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>
