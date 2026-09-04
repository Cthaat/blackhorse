<template>
  <lab-dashboard v-if="canViewDashboard" />
  <div v-else class="app-container home-page">
    <el-card shadow="never" class="welcome-card">
      <h1>实验室安全与设备管理系统</h1>
      <p>欢迎回来。请从左侧菜单进入您有权限使用的实验室业务或系统管理功能。</p>
    </el-card>
  </div>
</template>

<script setup name="Index">
import LabDashboard from '@/views/lab/dashboard/index.vue'
import useUserStore from '@/store/modules/user'

const userStore = useUserStore()
const canViewDashboard = computed(() => userStore.permissions.some(permission =>
  permission === '*:*:*' || permission === 'lab:dashboard:view'
))
</script>

<style scoped>
.home-page {
  min-height: calc(100vh - 84px);
  background: var(--el-bg-color-page);
}

.welcome-card {
  max-width: 880px;
  margin: 48px auto 0;
  padding: 28px;
  text-align: center;
}

.welcome-card h1 {
  margin: 0 0 18px;
  color: var(--el-text-color-primary);
  font-size: 30px;
}

.welcome-card p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 16px;
  line-height: 1.8;
}
</style>
