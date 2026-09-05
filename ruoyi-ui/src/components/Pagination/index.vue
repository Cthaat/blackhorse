<template>
  <div ref="container" :class="{ 'hidden': hidden }" class="pagination-container">
    <el-pagination
      :background="background"
      v-model:current-page="currentPage"
      v-model:page-size="pageSize"
      :layout="responsiveLayout"
      :page-sizes="pageSizes"
      :pager-count="responsivePagerCount"
      :total="total"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useWindowSize } from '@vueuse/core'

const props = defineProps({
  total: {
    required: true,
    type: Number
  },
  page: {
    type: Number,
    default: 1
  },
  limit: {
    type: Number,
    default: 20
  },
  pageSizes: {
    type: Array,
    default() {
      return [10, 20, 30, 50]
    }
  },
  // 移动端页码按钮的数量端默认值5
  pagerCount: {
    type: Number,
    default: undefined
  },
  layout: {
    type: String,
    default: undefined
  },
  background: {
    type: Boolean,
    default: true
  },
  autoScroll: {
    type: Boolean,
    default: true
  },
  hidden: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:page', 'update:limit', 'pagination'])
const container = ref(null)
const { width } = useWindowSize()
const responsivePagerCount = computed(() => props.pagerCount ?? (width.value < 992 ? 5 : 7))
const responsiveLayout = computed(() => props.layout ?? (width.value < 768
  ? 'total, prev, pager, next'
  : 'total, sizes, prev, pager, next, jumper'))
const currentPage = computed({
  get() {
    return props.page
  },
  set(val) {
    emit('update:page', val)
  }
})
const pageSize = computed({
  get() {
    return props.limit
  },
  set(val){
    emit('update:limit', val)
  }
})

function handleSizeChange(val) {
  const page = currentPage.value * val > props.total ? 1 : currentPage.value
  currentPage.value = page
  emit('pagination', { page, limit: val })
  scrollContent()
}

function handleCurrentChange(val) {
  emit('pagination', { page: val, limit: pageSize.value })
  scrollContent()
}

function scrollContent() {
  if (!props.autoScroll) return
  let target = container.value?.parentElement
  while (target && !/(auto|scroll)/.test(getComputedStyle(target).overflowY)) target = target.parentElement
  target ??= window
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  target.scrollTo({ top: 0, behavior: reducedMotion ? 'auto' : 'smooth' })
}
</script>

<style scoped>
.pagination-container {
  background: transparent;
}
.pagination-container.hidden {
  display: none;
}
</style>
