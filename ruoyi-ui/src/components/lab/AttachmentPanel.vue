<template>
  <section class="attachment-panel" aria-label="业务附件">
    <div class="section-heading">
      <div>
        <h3>附件</h3>
        <p>支持 JPG、JPEG、PNG、PDF，单个不超过 10 MiB，最多 5 个</p>
      </div>
      <div class="heading-actions">
        <span v-if="canManage" v-hasPermi="['lab:attachment:manage']">
          <el-upload
            :show-file-list="false"
            :accept="acceptedTypes"
            :disabled="uploading || attachments.length >= 5"
            :before-upload="beforeUpload"
            :http-request="handleUpload"
          >
            <el-button type="primary" plain icon="Upload" :loading="uploading">
              {{ attachments.length >= 5 ? '已达上限' : '上传附件' }}
            </el-button>
          </el-upload>
        </span>
        <el-button link type="primary" icon="Refresh" :loading="loading" @click="loadAttachments">
          刷新
        </el-button>
      </div>
    </div>

    <el-progress
      v-if="uploading"
      :percentage="uploadPercent"
      :stroke-width="8"
      class="upload-progress"
    />

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      show-icon
      :closable="false"
      class="mb12"
    />

    <el-table v-loading="loading" :data="attachments" size="small">
      <el-table-column label="文件名" prop="originalName" min-width="220" show-overflow-tooltip />
      <el-table-column label="类型" prop="mimeType" min-width="150" show-overflow-tooltip />
      <el-table-column label="大小" width="110" align="right">
        <template #default="scope">{{ formatSize(scope.row.size) }}</template>
      </el-table-column>
      <el-table-column label="上传人" prop="createBy" width="120" show-overflow-tooltip />
      <el-table-column label="上传时间" width="180">
        <template #default="scope">{{ parseTime(scope.row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            icon="Download"
            :loading="downloadingId === scope.row.id"
            @click="handleDownload(scope.row)"
          >下载</el-button>
          <el-button
            v-if="canManage"
            v-hasPermi="['lab:attachment:manage']"
            link
            type="danger"
            icon="Delete"
            @click="handleDelete(scope.row)"
          >删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无附件" :image-size="72" />
      </template>
    </el-table>
  </section>
</template>

<script setup>
import { saveAs } from 'file-saver'
import { parseTime } from '@/utils/ruoyi'
import {
  delAttachment,
  downloadAttachment,
  listAttachment,
  uploadAttachment
} from '@/api/lab/attachment'

const props = defineProps({
  businessType: {
    type: String,
    required: true
  },
  businessId: {
    type: String,
    default: ''
  },
  canManage: {
    type: Boolean,
    default: false
  }
})

const { proxy } = getCurrentInstance()
const acceptedTypes = '.jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf'
const attachments = ref([])
const loading = ref(false)
const uploading = ref(false)
const uploadPercent = ref(0)
const downloadingId = ref('')
const errorMessage = ref('')
let requestSerial = 0

async function loadAttachments() {
  if (!props.businessId) {
    attachments.value = []
    errorMessage.value = ''
    return
  }
  const serial = ++requestSerial
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listAttachment(props.businessType, props.businessId)
    if (serial === requestSerial) {
      attachments.value = Array.isArray(response.data)
        ? response.data.map(item => ({
            ...item,
            id: String(item.id),
            businessId: String(item.businessId)
          }))
        : []
    }
  } catch {
    if (serial === requestSerial) {
      attachments.value = []
      errorMessage.value = '附件列表加载失败，请稍后重试'
    }
  } finally {
    if (serial === requestSerial) {
      loading.value = false
    }
  }
}

function beforeUpload(file) {
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['jpg', 'jpeg', 'png', 'pdf'].includes(extension)) {
    proxy.$modal.msgError('仅支持 JPG、JPEG、PNG 和 PDF 文件')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    proxy.$modal.msgError('单个附件不能超过 10 MiB')
    return false
  }
  return true
}

async function handleUpload(options) {
  uploading.value = true
  uploadPercent.value = 0
  errorMessage.value = ''
  try {
    await uploadAttachment(props.businessType, props.businessId, options.file, event => {
      if (event.total) {
        uploadPercent.value = Math.min(99, Math.round(event.loaded * 100 / event.total))
        options.onProgress({ percent: uploadPercent.value })
      }
    })
    uploadPercent.value = 100
    options.onSuccess()
    proxy.$modal.msgSuccess('附件上传成功')
    await loadAttachments()
  } catch (error) {
    options.onError(error)
    errorMessage.value = '附件上传失败，请检查文件后重试'
  } finally {
    uploading.value = false
  }
}

async function handleDownload(row) {
  downloadingId.value = row.id
  errorMessage.value = ''
  try {
    const data = await downloadAttachment(row.id)
    const filename = (row.originalName || 'attachment').replace(/[\\/:*?"<>|]/g, '_')
    saveAs(data, filename)
  } catch {
    errorMessage.value = '附件下载失败，请稍后重试'
  } finally {
    downloadingId.value = ''
  }
}

async function handleDelete(row) {
  try {
    await proxy.$modal.confirm(`确认删除附件“${row.originalName}”吗？`)
    await delAttachment(row.id)
    proxy.$modal.msgSuccess('附件删除成功')
    await loadAttachments()
  } catch {
    // User cancellation and request errors are already handled globally.
  }
}

function formatSize(value) {
  const size = typeof value === 'number' ? value : Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KiB`
  return `${(size / 1024 / 1024).toFixed(1)} MiB`
}

watch(() => [props.businessType, props.businessId], loadAttachments, { immediate: true })

defineExpose({ refresh: loadAttachments })
</script>

<style scoped>
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-heading h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
}

.section-heading p {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upload-progress,
.mb12 {
  margin-bottom: 12px;
}

@media (max-width: 640px) {
  .section-heading {
    flex-direction: column;
  }
}
</style>
