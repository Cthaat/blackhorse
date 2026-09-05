<template>
  <button type="button" class="screenfull-button" :aria-label="isFullscreen ? '退出全屏' : '进入全屏'" @click="toggle">
    <svg-icon :icon-class="isFullscreen ? 'exit-fullscreen' : 'fullscreen'" />
  </button>
</template>

<script setup>
import { useFullscreen } from '@vueuse/core'

const { isFullscreen, toggle } = useFullscreen()
</script>

<style lang='scss' scoped>
.screenfull-button { border: 0; background: transparent; color: inherit; cursor: pointer; }
.screenfull-svg {
  display: inline-block;
  cursor: pointer;
  fill: #5a5e66;
  width: 20px;
  height: 20px;
  vertical-align: 10px;
}
</style>
