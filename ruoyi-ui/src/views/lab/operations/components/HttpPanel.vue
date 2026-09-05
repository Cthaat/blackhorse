<template>
  <el-card shadow="never">
    <template #header><h2>HTTP 请求 · 最近 5 分钟</h2></template>
    <el-empty v-if="!http" description="HTTP 采样未知" />
    <template v-else>
      <p class="sample-note">{{ time(http.windowStart) }} 至 {{ time(http.sampledAt) }}；只统计当前实例，进程启动不足 5 分钟时展示已有窗口。</p>
      <div class="metric-grid">
        <dl><dt>请求总数</dt><dd>{{ number(http.requestCount) }}</dd></dl>
        <dl><dt>4xx 请求拒绝</dt><dd>{{ number(http.clientRefusals) }}</dd></dl>
        <dl><dt>其中 400 / 409 / 422 校验与冲突拒绝</dt><dd>{{ number(http.businessRefusals) }}</dd></dl>
        <dl><dt>5xx 服务端错误</dt><dd>{{ number(http.systemErrors) }}</dd></dl>
        <dl><dt>服务端错误率</dt><dd>{{ http.systemErrorRate == null ? '无样本' : number(http.systemErrorRate * 100) + '%' }}</dd></dl>
        <dl><dt>P95 延迟（毫秒）</dt><dd>{{ http.p95Millis == null ? '无样本' : number(http.p95Millis) }}</dd></dl>
      </div>
      <el-alert v-if="http.latencyTruncated" title="延迟样本达到容量上限，P95 仅代表窗口内最近保留样本；请求计数仍覆盖整个窗口。" type="warning" :closable="false" />
      <h3>路由模板与状态类别</h3>
      <el-table :data="http.routes || []" empty-text="窗口内尚无路由请求">
        <el-table-column prop="template" label="MVC 路由模板" min-width="260" />
        <el-table-column prop="requestCount" label="请求" width="90" />
        <el-table-column prop="success" label="2xx" width="80" />
        <el-table-column prop="redirects" label="3xx" width="80" />
        <el-table-column prop="clientErrors" label="4xx" width="80" />
        <el-table-column prop="serverErrors" label="5xx" width="80" />
      </el-table>
      <p class="sample-note">最多保留 100 个 MVC 模板；未匹配路由、认证前拒绝及超出容量的模板合并为 OTHER，不记录对象编号或原始 URL。</p>
      <p class="sample-note">延迟样本 {{ number(http.latencySampleCount) }} / {{ number(http.latencySampleLimit) }}；按响应状态分类，400/409/422 可能包含非业务校验拒绝。来源：{{ http.source }}。</p>
    </template>
  </el-card>
</template>
<script setup>
import { number, time } from './helpers'
defineProps({ http: Object })
</script>
