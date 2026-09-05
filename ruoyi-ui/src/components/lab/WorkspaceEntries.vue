<template>
  <section class="workspace-entries" aria-labelledby="workspace-entries-heading">
    <div class="section-heading">
      <div><h2 id="workspace-entries-heading">业务与管理</h2><p>当前账号可使用的全部功能，从这里直接开始。</p></div>
      <span class="entry-count">{{ entryCount }} 个入口</span>
    </div>
    <div v-for="group in groups" :key="group.title" class="entry-group">
      <h3>{{ group.title }}</h3>
      <div class="entry-list">
        <router-link v-for="item in group.items" :key="item.to.path" :to="item.to" class="workspace-entry">
          <span class="entry-icon" aria-hidden="true"><svg-icon :icon-class="item.icon" /></span>
          <span class="entry-copy"><strong>{{ item.title }}</strong><small>{{ item.section || group.title }}</small></span>
          <span class="entry-arrow" aria-hidden="true">↗</span>
        </router-link>
      </div>
    </div>
    <el-empty v-if="!entryCount" description="当前账号尚未分配功能，请联系系统管理员" :image-size="64" />
  </section>
</template>

<script setup>
import { computed } from 'vue'
import usePermissionStore from '@/store/modules/permission'
import { buildWorkspaceGroups } from '@/utils/lab/workspaceNavigation'

const permissionStore = usePermissionStore()
const groups = computed(() => buildWorkspaceGroups(permissionStore.sidebarRouters))
const entryCount = computed(() => groups.value.reduce((total, group) => total + group.items.length, 0))
</script>

<style scoped>
.workspace-entries { padding: 24px; background: var(--lab-surface); border: 1px solid var(--lab-border); border-radius: 12px; }
.section-heading { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
h2 { margin: 0; font-size: 18px; font-weight: 650; }
.section-heading p { margin: 8px 0 0; font-size: 13px; color: var(--lab-muted); line-height: 1.6; }
.entry-count { font-size: 12px; color: var(--lab-muted); white-space: nowrap; }
.entry-group { margin-top: 24px; }
h3 { margin: 0 0 12px; color: var(--lab-muted); font-size: 12px; font-weight: 500; letter-spacing: 1px; }
.entry-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(210px, 100%), 1fr)); gap: 8px; }
.workspace-entry { display: flex; align-items: center; gap: 12px; min-width: 0; padding: 12px; border: 1px solid var(--lab-border); border-radius: 8px; transition: background .15s, border-color .15s; }
.workspace-entry:hover { background: var(--lab-soft); border-color: var(--lab-accent); }
.entry-icon { display: grid; place-items: center; width: 36px; height: 36px; flex-shrink: 0; color: var(--lab-accent); background: var(--lab-soft); border-radius: 8px; font-size: 18px; }
.entry-copy { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.entry-copy strong { font-size: 14px; font-weight: 600; color: var(--lab-ink); }
.entry-copy small { font-size: 11px; color: var(--lab-muted); }
.entry-arrow { margin-left: auto; color: var(--lab-muted); }
@media (max-width: 767px) { .workspace-entries { padding: 16px; } .section-heading { flex-wrap: wrap; } }
</style>
