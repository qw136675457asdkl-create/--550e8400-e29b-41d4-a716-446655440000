<template>
  <el-dialog
    v-model="dialogVisible"
    width="88%"
    top="4vh"
    class="compare-preview-dialog"
    append-to-body
    @closed="emit('closed')"
  >
    <template #header>
      <div class="compare-preview-dialog__header">
        <div>
          <div class="compare-preview-dialog__title">数据比对</div>
          <div class="compare-preview-dialog__subtitle">已选 {{ items.length }} 条数据，展示区域会根据数量自动调整</div>
        </div>
      </div>
    </template>

    <div class="compare-preview-dialog__body" :style="{ maxHeight }">
      <div v-if="items.length > 0" class="compare-preview-list">
        <section v-for="item in items" :key="item.id" class="compare-preview-item">
          <header class="compare-preview-item__header">
            <div class="compare-preview-item__title">{{ item.title }}</div>
            <div class="compare-preview-item__meta">{{ getComparePreviewMeta(item) }}</div>
          </header>

          <div v-loading="item.loading" class="compare-preview-item__body" :style="{ height: itemHeight }">
            <div v-if="item.previewType === 'table'" class="compare-preview-item__content">
              <el-table v-if="item.rows.length > 0" :data="item.rows" border stripe height="100%">
                <el-table-column
                  v-for="header in getCompareTableColumns(item)"
                  :key="header"
                  :prop="header"
                  :label="header"
                  show-overflow-tooltip
                />
              </el-table>
              <el-empty v-else :description="item.message || '暂无表格内容'" />
            </div>

            <div v-else-if="item.previewType === 'text'" class="compare-preview-item__content compare-text-preview">
              <div v-if="item.rows.length > 0" class="compare-text-preview__body">
                <div
                  v-for="(line, index) in getCompareTextLines(item)"
                  :key="`${item.id}-${item.pageNum || 1}-${index}`"
                  class="compare-text-preview__line"
                >
                  <span class="compare-text-preview__line-number">{{ getCompareLineNumber(item, index) }}</span>
                  <pre class="compare-text-preview__line-content">{{ line }}</pre>
                </div>
              </div>
              <el-empty v-else :description="item.message || '暂无文本内容'" />
            </div>

            <div v-else-if="item.previewType === 'pdf'" class="compare-media-preview">
              <iframe v-if="item.objectUrl" :src="item.objectUrl" class="compare-media-preview__frame" title="PDF比对预览" />
              <el-empty v-else :description="item.message || '暂无 PDF 预览内容'" />
            </div>

            <div v-else-if="item.previewType === 'image'" class="compare-media-preview">
              <img v-if="item.objectUrl" :src="item.objectUrl" :alt="item.title" class="compare-media-preview__image" />
              <el-empty v-else :description="item.message || '暂无图片预览内容'" />
            </div>

            <div v-else-if="item.previewType === 'audio'" class="compare-media-preview compare-media-preview--audio">
              <audio v-if="item.objectUrl" :src="item.objectUrl" controls class="compare-media-preview__audio" />
              <el-empty v-else :description="item.message || '暂无音频预览内容'" />
            </div>

            <div v-else-if="item.previewType === 'video'" class="compare-media-preview">
              <video v-if="item.objectUrl" :src="item.objectUrl" controls class="compare-media-preview__video" />
              <el-empty v-else :description="item.message || '暂无视频预览内容'" />
            </div>

            <div v-else class="compare-preview-item__empty">
              <el-empty :description="item.message || '暂不支持在线比对该文件'" />
            </div>
          </div>

          <pagination
            v-if="hasComparePagination(item)"
            class="compare-preview-item__pagination"
            :total="item.total"
            :page="item.pageNum"
            :limit="item.pageSize"
            :auto-scroll="false"
            @update:page="value => updateComparePage(item, value)"
            @update:limit="value => updateCompareLimit(item, value)"
            @pagination="emit('pagination', item)"
          />
        </section>
      </div>
      <el-empty v-else description="请选择需要比对的数据" />
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  items: { type: Array, default: () => [] },
  maxHeight: { type: String, default: '60vh' },
  itemHeight: { type: String, default: '240px' }
})

const emit = defineEmits(['update:modelValue', 'pagination', 'closed'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

function getComparePreviewTypeLabel(previewType) {
  if (previewType === 'table') return '表格'
  if (previewType === 'text') return '文本'
  if (previewType === 'pdf') return 'PDF'
  if (previewType === 'image') return '图片'
  if (previewType === 'audio') return '音频'
  if (previewType === 'video') return '视频'
  return '未支持'
}

function getCompareTableColumns(item) {
  return Object.keys(item?.rows?.[0] || {})
}

function getCompareTextLines(item) {
  return (item?.rows || []).map(row => {
    if (row == null) return ''
    if (typeof row === 'string') return row
    if (typeof row === 'object') return Object.values(row).join(' ')
    return String(row)
  })
}

function hasComparePagination(item) {
  return ['table', 'text'].includes(item?.previewType) && Number(item?.total) > 0
}

function updateComparePage(item, value) {
  item.pageNum = value
}

function updateCompareLimit(item, value) {
  item.pageSize = value
}

function getCompareLineNumber(item, index) {
  const pageNum = Number(item?.pageNum) || 1
  const pageSize = Number(item?.pageSize) || 0
  return (pageNum - 1) * pageSize + index + 1
}

function getComparePreviewMeta(item) {
  const previewLabel = getComparePreviewTypeLabel(item.previewType)
  let fileTypeLabel = '暂无内容'

  if (item.previewType === 'table' || item.previewType === 'text') {
    fileTypeLabel = item.total > 0 ? `展示 ${item.rows.length}/${item.total}` : '暂无内容'
  } else if (item.objectUrl) {
    fileTypeLabel = '已加载预览'
  } else if (item.loading) {
    fileTypeLabel = '加载中'
  }

  return `${previewLabel} · ${fileTypeLabel}`
}
</script>

<style scoped>
.compare-preview-dialog__header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.compare-preview-dialog__title { font-size: 18px; font-weight: 700; color: #111827; }
.compare-preview-dialog__subtitle { margin-top: 6px; font-size: 13px; color: #6b7280; }
.compare-preview-dialog__body { overflow-y: auto; padding-right: 4px; }
.compare-preview-list { display: flex; flex-direction: column; }
.compare-preview-item { padding: 0 0 18px; }
.compare-preview-item + .compare-preview-item { margin-top: 18px; padding-top: 18px; border-top: 1px solid #e5e7eb; }
.compare-preview-item__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 12px; }
.compare-preview-item__title { font-size: 15px; font-weight: 600; color: #111827; word-break: break-word; }
.compare-preview-item__meta { font-size: 12px; color: #6b7280; white-space: nowrap; }
.compare-preview-item__body {
  width: 100%;
  min-width: min(320px, 100%);
  min-height: 220px;
  max-width: 100%;
  max-height: 76vh;
  overflow: auto;
  resize: both;
  margin-right: auto;
  box-sizing: border-box;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
}
.compare-preview-item__body:hover { border-color: #cbd5e1; }
.compare-preview-item__body:active { border-color: #94a3b8; }
.compare-preview-item__content { width: 100%; height: 100%; }
.compare-preview-item__pagination { margin-top: 12px; }
.compare-preview-item__empty, .compare-media-preview { display: flex; align-items: center; justify-content: center; width: 100%; height: 100%; padding: 16px; background: #fafafa; }
.compare-media-preview--audio { padding: 24px; }
.compare-media-preview__frame { width: 100%; height: 100%; border: none; }
.compare-media-preview__image { display: block; max-width: 100%; max-height: 100%; object-fit: contain; }
.compare-media-preview__audio { width: min(100%, 720px); }
.compare-media-preview__video { display: block; max-width: 100%; max-height: 100%; border-radius: 8px; background: #000; }
.compare-text-preview, .compare-text-preview__body { height: 100%; }
.compare-text-preview__body { overflow-y: auto; background: #fafafa; }
.compare-text-preview__line { display: grid; grid-template-columns: 56px 1fr; gap: 12px; padding: 8px 14px; border-bottom: 1px solid #eef2f7; }
.compare-text-preview__line:last-child { border-bottom: none; }
.compare-text-preview__line-number { color: #9ca3af; font-size: 12px; line-height: 1.7; text-align: right; user-select: none; }
.compare-text-preview__line-content { margin: 0; color: #111827; font-size: 12px; line-height: 1.7; white-space: pre-wrap; word-break: break-word; font-family: Consolas, 'Courier New', monospace; }
.compare-preview-dialog :deep(.pagination-container) { padding-left: 0; padding-right: 0; }
.compare-preview-item__pagination :deep(.el-pagination) { justify-content: flex-end; flex-wrap: wrap; row-gap: 8px; }
.compare-preview-item__body :deep(.el-table) { width: 100%; }
.compare-preview-item__body :deep(.el-table__inner-wrapper),
.compare-preview-item__body :deep(.el-table__body-wrapper),
.compare-preview-item__body :deep(.el-scrollbar),
.compare-preview-item__body :deep(.el-scrollbar__wrap),
.compare-preview-item__body :deep(.el-scrollbar__view) {
  height: 100%;
}
</style>
