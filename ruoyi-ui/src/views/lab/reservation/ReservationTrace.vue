<template>
  <section class="reservation-trace" aria-label="业务追踪" :aria-busy="loading">
    <header><h3>业务追踪</h3><p>按当前权限展示直接关联记录与补充背景。</p></header>
    <el-skeleton v-if="loading" :rows="4" animated />
    <div v-else-if="errorMessage" role="alert" class="trace-message">
      <p>{{ errorMessage }}</p><el-button @click="load">重试追踪</el-button>
    </div>
    <template v-else-if="trace">
      <p class="trace-next" role="status">{{ nextStep }}</p>
      <ol v-if="trace.history.items.length" class="trace-timeline" aria-label="状态时间线">
        <li v-for="event in trace.history.items" :key="event.id">
          <time>{{ formatTime(event.createTime) }}</time>
          <strong>{{ event.objectType === 'REPAIR_ORDER' ? '维修' : '预约' }} · {{ label(event.toStatus) }}</strong>
          <p>{{ event.reason || '状态已更新' }}<span v-if="event.operatorName"> · {{ event.operatorName }}</span></p>
        </li>
      </ol>
      <p v-if="trace.history.hasMore" class="trace-note">仅展示最近 20 条状态记录，更早历史请在对应业务详情中查看。</p>
      <div v-if="trace.usage || trace.repair" class="trace-cards">
        <article v-for="node in [trace.usage, trace.repair].filter(Boolean)" :key="`${node.title}-${node.id}`">
          <h4>{{ node.title }}</h4><strong>{{ label(node.status) }}</strong>
          <p>{{ node.reason || '请按当前业务状态继续处理。' }}</p><time>{{ formatTime(node.createTime) }}</time>
        </article>
      </div>
      <article v-if="trace.qualification" class="trace-context">
        <h4>当前资格匹配 · {{ trace.qualification.matchingCount }} 条</h4>
        <p>按设备所属实验室与设备类别匹配当前有效资格，不代表预约提交时的资格快照。</p>
        <time>核对时间：{{ formatTime(trace.qualification.evaluatedAt) }}</time>
      </article>
      <article v-if="trace.hazards.items.length" class="trace-context">
        <h4>同设备／实验室的隐患背景</h4>
        <p>这些记录仅与目标相同，不表示由本次预约产生。</p>
        <ul><li v-for="node in trace.hazards.items" :key="node.id">{{ node.title }} · {{ label(node.status) }}<p>{{ node.reason }}</p></li></ul>
        <p v-if="trace.hazards.hasMore">仅展示最近 20 条；可在隐患工作台查看其余可见记录。</p>
      </article>
      <article v-if="trace.notifications.items.length" class="trace-context">
        <h4>已发送给我的相关通知</h4>
        <ul><li v-for="node in trace.notifications.items" :key="node.id">{{ node.title }}<p>{{ node.reason }}</p><time>{{ formatTime(node.createTime) }}</time></li></ul>
        <p v-if="trace.notifications.hasMore">仅展示最近 20 条；可在通知中心查看其余通知。</p>
      </article>
      <p v-if="!hasRelated" class="trace-message">当前权限范围内没有可展示的关联记录。</p>
    </template>
  </section>
</template>

<script setup name="ReservationTrace">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getReservationTrace } from '@/api/lab/trace'

const props = defineProps({ reservationId: { type: [String, Number], required: true } })
const loading = ref(false)
const errorMessage = ref('')
const trace = ref(null)
let requestVersion = 0
const hasRelated = computed(() => trace.value && (trace.value.usage || trace.value.repair ||
  trace.value.qualification || trace.value.hazards.items.length || trace.value.notifications.items.length))
const nextStep = computed(() => ({
  PENDING: '下一步：等待审批结果，可在预约工作台核对申请信息。',
  APPROVED: '下一步：在预约时段内按领用流程领取设备。',
  CHECKED_OUT: '下一步：使用完毕后及时归还，并如实填写设备状况。',
  COMPLETED: '预约已完成，可核对归还记录及后续处理。',
  REJECTED: '申请未通过，请核对审批原因后调整申请。',
  CANCELLED: '预约已取消，如仍需使用设备，请重新选择时段。',
  EXPIRED: '预约已过期，请重新核对可用时段。',
  NO_SHOW: '预约已标记爽约，请联系管理人员核对。'
}[trace.value?.reservation.status] || '请核对当前状态与业务处理说明。'))

async function load() {
  const version = ++requestVersion
  trace.value = null
  errorMessage.value = ''
  if (!props.reservationId) { loading.value = false; return }
  loading.value = true
  try {
    const response = await getReservationTrace(String(props.reservationId))
    if (version === requestVersion) trace.value = response.data
  } catch (error) {
    if (version === requestVersion) errorMessage.value = error?.response?.data?.msg ?? error?.data?.msg ?? error?.message ?? '业务追踪加载失败'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
function formatTime(value) { return value ? String(value).replace('T', ' ').slice(0, 19) : '时间未记录' }
function label(value) {
  return ({ PENDING: '待审批', APPROVED: '已批准', REJECTED: '已驳回', CANCELLED: '已取消', EXPIRED: '已过期',
    NO_SHOW: '已爽约', CHECKED_OUT: '使用中', COMPLETED: '已完成', RETURNED: '已归还', WAIT_ASSIGN: '待派单',
    WAIT_REPAIR: '待维修', IN_PROGRESS: '维修中', WAIT_ACCEPTANCE: '待验收', CLOSED: '已关闭',
    PENDING_RECTIFICATION: '待整改', RECTIFYING: '整改中', PENDING_REVIEW: '待复核' })[value] || value || '待核对'
}
watch(() => props.reservationId, load, { immediate: true })
onBeforeUnmount(() => { requestVersion++ })
</script>

<style scoped>
.reservation-trace { margin-top: 24px; border-top: 1px solid var(--lab-border); padding-top: 20px; color: var(--lab-ink); }
header h3, h4 { margin: 0 0 8px; }
header p, .trace-note, time { color: var(--lab-muted); font-size: 12px; }
.trace-next, .trace-message { padding: 12px; background: var(--lab-soft); border-radius: 8px; line-height: 1.7; }
.trace-timeline { list-style: none; margin: 20px 0; padding-left: 16px; border-left: 2px solid var(--lab-border); }
.trace-timeline li { position: relative; padding: 0 0 16px 8px; }
.trace-timeline li::before { content: ''; position: absolute; left: -22px; top: 4px; width: 8px; height: 8px; border-radius: 50%; background: var(--lab-accent); }
.trace-timeline strong, .trace-timeline time { display: block; margin-bottom: 4px; }
.trace-timeline p, article p { margin: 8px 0; line-height: 1.6; overflow-wrap: anywhere; }
.trace-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr)); gap: 12px; }
article { padding: 16px; border: 1px solid var(--lab-border); border-radius: 8px; background: var(--lab-surface); }
.trace-context { margin-top: 12px; }
article ul { padding-left: 20px; }
article li + li { margin-top: 12px; }
</style>
