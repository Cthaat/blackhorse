<template>
  <el-descriptions :column="width < 768 ? 1 : column">
    <template v-for="(_, name) in $slots" #[name]="slotProps"><slot :name="name" v-bind="slotProps || {}" /></template>
  </el-descriptions>
</template>

<script setup>
import { useWindowSize } from '@vueuse/core'
defineProps({ column: { type: Number, default: 2 } })
const { width } = useWindowSize()
</script>
