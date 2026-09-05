<template>
  <div class="sidebar-logo-container" :class="{ 'collapse': collapse }">
    <router-link class="sidebar-logo-link" to="/" :aria-label="title + '首页'">
      <span class="lab-brand-mark" aria-hidden="true">L<span>·</span></span>
      <span v-if="!collapse" class="lab-brand-copy"><strong>实验室工作台</strong><small>安全 · 设备 · 协作</small></span>
    </router-link>
  </div>
</template>

<script setup>
import useSettingsStore from '@/store/modules/settings'
import variables from '@/assets/styles/variables.module.scss'

defineProps({
  collapse: {
    type: Boolean,
    required: true
  }
})

const title = import.meta.env.VITE_APP_TITLE
const settingsStore = useSettingsStore()
const sideTheme = computed(() => settingsStore.sideTheme)

// 获取Logo背景色
const getLogoBackground = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-bg)'
  }
  if (settingsStore.navType == 3) {
    return variables.menuLightBg
  }
  return sideTheme.value === 'theme-dark' ? variables.menuBg : variables.menuLightBg
})

// 获取Logo文字颜色
const getLogoTextColor = computed(() => {
  if (settingsStore.isDark) {
    return 'var(--sidebar-logo-text)'
  }
  if (settingsStore.navType == 3) {
    return variables.menuLightText
  }
  return sideTheme.value === 'theme-dark' ? '#fff' : variables.menuLightText
})
</script>

<style lang="scss" scoped>
.sidebar-logo-container {
  height: 50px;
  background: v-bind(getLogoBackground);
  color: v-bind(getLogoTextColor);
  overflow: hidden;
  .sidebar-logo-link { display: flex !important; align-items: center; gap: 12px; height: 100%; padding: 0 16px; }
  &.collapse .sidebar-logo-link { padding: 0 10px; }
}
.lab-brand-mark { display: grid; grid-template-columns: auto auto; align-items: center; justify-content: center; flex: 0 0 32px; height: 32px; background: #d9f4eb; color: #115e59; border-radius: 8px; font-size: 26px; font-weight: 800; line-height: 1; }
.lab-brand-mark span { color: #16876e; }
.lab-brand-copy { display: flex; flex-direction: column; gap: 4px; white-space: nowrap; text-align: left; }
.lab-brand-copy strong { font-size: 15px; letter-spacing: 1px; }
.lab-brand-copy small { font-size: 10px; opacity: .75; letter-spacing: 2px; }
</style>
