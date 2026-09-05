<template>
  <lab-dashboard v-if="canViewDashboard" />
  <div v-else class="app-container home-page">
    <div class="page-heading"><div><h1>管理工作台</h1><p>{{ userStore.nickName }}，欢迎回来。选择功能开始处理管理事务。</p></div></div>
    <WorkspaceEntries />
  </div>
</template>

<script setup name="Index">
import WorkspaceEntries from '@/components/lab/WorkspaceEntries.vue'
import LabDashboard from '@/views/lab/dashboard/index.vue'
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
const canViewDashboard = computed(() => userStore.permissions.some(permission =>
  permission === '*:*:*' || permission === 'lab:dashboard:view'
))
</script>

<style scoped>
.app-main > .home-page { background: transparent; border: 0; padding: 0; }
</style>
