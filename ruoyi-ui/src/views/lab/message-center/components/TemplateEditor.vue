<template>
  <el-dialog :model-value="modelValue" :title="readonly ? '模板历史版本' : '模板草稿'" width="min(720px, 94vw)" append-to-body @update:model-value="$emit('update:modelValue', $event)">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-form label-position="top" @submit.prevent="save">
      <el-form-item label="事件类型"><el-input v-model="form.eventType" :disabled="readonly || !!template?.id" maxlength="32" placeholder="例如 RESERVATION_APPROVED" /></el-form-item>
      <p>支持 RESERVATION、REPAIR_ORDER、INSPECTION_TASK、HAZARD 的状态事件及 WAITLIST_OFFERED。</p>
      <p>仅支持纯文本变量：<code v-for="variable in variables" :key="variable">{{ '${' + variable + '}' }} </code>；不支持 HTML 或表达式。</p>
      <el-form-item label="标题模板"><el-input v-model="form.title" :readonly="readonly" maxlength="128" show-word-limit /></el-form-item>
      <el-form-item label="正文模板"><el-input v-model="form.content" type="textarea" :readonly="readonly" :rows="5" maxlength="500" show-word-limit /></el-form-item>
    </el-form>
    <el-card v-if="preview" shadow="never"><h3>{{ preview.title }}</h3><p class="preview-content">{{ preview.content }}</p><small>示例数据预览，实际投递使用来源事实。渲染超出收件箱长度时使用内置模板。</small></el-card>
    <template #footer><el-button @click="$emit('update:modelValue', false)">关闭</el-button><el-button v-if="canEdit" :loading="busy" @click="render">示例预览</el-button><el-button v-if="!readonly" type="primary" :loading="busy" @click="save">保存草稿</el-button></template>
  </el-dialog>
</template>
<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { saveTemplate, previewTemplate } from '@/api/lab/messageCenter'
const props = defineProps({ modelValue: Boolean, template: Object, canEdit: Boolean })
const emit = defineEmits(['update:modelValue', 'saved'])
const form = reactive({ eventType: '', title: '${title}', content: '${content}' })
const variables = ['eventType', 'businessType', 'businessId', 'title', 'content']
const readonly = computed(() => !props.canEdit || props.template?.status === 'PUBLISHED')
const busy = ref(false), error = ref(''), preview = ref(null)
watch(() => props.modelValue, open => { if (open) { Object.assign(form, { eventType: props.template?.eventType || '', title: props.template?.title || '${title}', content: props.template?.content || '${content}' }); error.value = ''; preview.value = null } })
async function render() { if (busy.value) return; busy.value = true; error.value = ''; try { preview.value = (await previewTemplate({ ...form })).data } catch (failure) { error.value = failure?.message || '预览失败' } finally { busy.value = false } }
async function save() { if (busy.value) return; busy.value = true; error.value = ''; try { await saveTemplate(props.template?.id, { ...form }); emit('saved'); emit('update:modelValue', false) } catch (failure) { error.value = failure?.message || '保存失败' } finally { busy.value = false } }
</script>
<style scoped>.preview-content { white-space: pre-wrap; overflow-wrap: anywhere; } code { display: inline-block; margin-right: 8px; }</style>
