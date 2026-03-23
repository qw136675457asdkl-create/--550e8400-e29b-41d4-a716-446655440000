<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <span>系统日志文件列表</span>
      </template>

      <el-table v-loading="loading" :data="fileList" border>
        <el-table-column label="序号" type="index" width="80" />

        <el-table-column label="文件名" min-width="260">
          <template #default="{ row }">
            <el-link type="primary" @click="handlePreview(row)">
              {{ getFileName(row) }}
            </el-link>
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="320">
          <template #default="{ row }">
            <el-button link type="primary" @click="handlePreview(row)" v-hasPermi="['monitor:systemLog:preview']">
              预览
            </el-button>
            <el-button link type="info" @click="handleDownload(row)" v-hasPermi="['monitor:systemLog:download']">
              下载原文件
            </el-button>
            <el-button link type="success" @click="handleExportWord(row)" v-hasPermi="['monitor:systemLog:download']">
              导出 Word
            </el-button>
            <el-button link type="warning" @click="handleExportPdf(row)" v-hasPermi="['monitor:systemLog:download']">
              导出 PDF
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="previewOpen" title="文件预览" width="70%">
      <pre v-if="previewContent" class="preview-box">{{ previewContent }}</pre>
      <div v-else>暂无内容</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { saveAs } from 'file-saver'
import { blobValidate } from '@/utils/ruoyi'
import { listSysLogs, previewLog, downloadLog, downloadLogWord, downloadLogPdf } from '@/api/monitor/sysLog'

const loading = ref(false)
const fileList = ref([])
const previewOpen = ref(false)
const previewContent = ref('')

onMounted(() => {
  getList()
})

function getList() {
  loading.value = true
  listSysLogs()
    .then((res) => {
      fileList.value = res.rows || []
    })
    .catch(() => {
      ElMessage.error('获取日志文件列表失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function getFileName(row) {
  return row.fileName || row.name || row
}

function formatPreviewLine(line) {
  if (line === undefined || line === null) {
    return ''
  }
  if (typeof line === 'object') {
    return JSON.stringify(line, null, 2)
  }
  return String(line)
}

function formatPreviewContent(rows) {
  if (Array.isArray(rows)) {
    return rows.map((line) => formatPreviewLine(line)).join('\n')
  }
  return formatPreviewLine(rows)
}

function handlePreview(row) {
  const fileName = getFileName(row)
  previewLog(fileName)
    .then((res) => {
      previewContent.value = formatPreviewContent(res.data?.rows)
      previewOpen.value = true
    })
    .catch(() => {
      ElMessage.error('预览失败')
    })
}

function handleDownload(row) {
  const fileName = getFileName(row)
  downloadBinary(downloadLog(fileName), fileName, '下载失败')
}

function handleExportWord(row) {
  const fileName = getFileName(row)
  downloadBinary(downloadLogWord(fileName), buildExportFileName(fileName, '.docx'), '导出 Word 失败')
}

function handleExportPdf(row) {
  const fileName = getFileName(row)
  downloadBinary(downloadLogPdf(fileName), buildExportFileName(fileName, '.pdf'), '导出 PDF 失败')
}

async function downloadBinary(requestPromise, downloadName, errorMessage) {
  try {
    const data = await requestPromise
    if (!blobValidate(data)) {
      const text = await data.text()
      const result = JSON.parse(text)
      ElMessage.error(result.msg || errorMessage)
      return
    }
    saveAs(data, downloadName)
  } catch (error) {
    ElMessage.error(errorMessage)
  }
}

function buildExportFileName(fileName, extension) {
  const lastDotIndex = fileName.lastIndexOf('.')
  const baseName = lastDotIndex > 0 ? fileName.slice(0, lastDotIndex) : fileName
  return `${baseName}${extension}`
}
</script>

<style scoped>
.preview-box {
  max-height: 600px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
}
</style>
