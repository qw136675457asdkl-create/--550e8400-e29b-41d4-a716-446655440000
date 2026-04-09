<template>
  <div class="app-container">
    <el-form
      ref="queryRef"
      :model="queryParams"
      :inline="true"
      v-show="showSearch"
      label-width="76px"
      class="query-form"
    >
      <el-form-item label="ID" prop="experimentId">
        <el-input
          v-model="queryParams.experimentId"
          placeholder="请输入数据ID"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="试验名称" prop="experimentName">
        <el-input
          v-model="queryParams.experimentName"
          placeholder="请输入试验名称"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所属项目" prop="projectId">
        <el-select
          v-model="queryParams.projectId"
          placeholder="请选择所属项目"
          clearable
          style="width: 240px"
        >
          <el-option
            v-for="item in projectOptions"
            :key="item.projectId"
            :label="item.projectName"
            :value="item.projectId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="创建人" prop="createBy">
        <el-input
          v-model="queryParams.createBy"
          placeholder="请输入创建人"
          clearable
          style="width: 240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="创建时间" style="width: 308px">
        <el-date-picker
          v-model="dateRange"
          value-format="YYYY-MM-DD"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button icon="Refresh" @click="refresh">刷新</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="businessList">
        <el-table-column label="ID" align="center" prop="experimentId" />
        <el-table-column label="试验名称" align="center" prop="experimentName" :show-overflow-tooltip="true" />
        <el-table-column label="所属项目" align="center" prop="projectName" :show-overflow-tooltip="true" />
        <el-table-column label="试验目标" align="center" prop="targetType" :show-overflow-tooltip="true" />
        <el-table-column label="试验日期" align="center" prop="startTime">
          <template #default="scope">
            <span>{{ parseTime(scope.row.startTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="试验地点" align="center" prop="location" />
        <el-table-column label="试验描述" align="center" prop="contentDesc" />
        <el-table-column label="创建人" align="center" prop="createBy" />
        <el-table-column label="创建时间" align="center" prop="createTime">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="态势显示" placement="top">
              <el-button link type="primary" @click="handleView(scope.row)">查看</el-button>
            </el-tooltip>
          </template>
        </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <el-dialog
      v-if="false"
      v-model="dialogVisible"
      :fullscreen="isDialogFullscreen"
      :width="dialogWidth"
      :top="dialogTop"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
      :show-close="false"
      class="preview-dialog"
      @close="handleClose"
    >
      <template #header>
        <div class="preview-dialog-header">
          <div class="preview-dialog-title">态势显示</div>
          <div class="preview-dialog-actions">
            <el-button
              link
              class="preview-dialog-action"
              :class="{ 'is-active': dialogMode === 'compact' }"
              @click="setDialogMode('compact')"
            >
              最小化
            </el-button>
            <el-button
              link
              class="preview-dialog-action"
              :class="{ 'is-active': dialogMode === 'large' }"
              @click="setDialogMode('large')"
            >
              标准
            </el-button>
            <el-button
              link
              class="preview-dialog-action"
              :class="{ 'is-active': dialogMode === 'fullscreen' }"
              @click="toggleDialogFullscreen"
            >
              {{ isDialogFullscreen ? '还原' : '最大化' }}
            </el-button>
            <el-button
              link
              class="preview-dialog-action preview-dialog-action-close"
              @click="dialogVisible = false"
            >
              关闭
            </el-button>
          </div>
        </div>
      </template>

      <div class="module-frame-wrapper" :style="frameWrapperStyle">
        <iframe
          v-if="dialogVisible && moduleUrl"
          :src="moduleUrl"
          class="module-frame"
          frameborder="0"
          allowfullscreen
        />
      </div>
    </el-dialog>

    <teleport to="body">
      <div v-if="dialogVisible" class="preview-window-layer" :class="{ 'is-minimized': isDialogMinimized }">
        <div v-if="!isDialogMinimized" class="preview-window-overlay"></div>
        <section
          ref="previewWindowRef"
          class="preview-window"
          :class="{
            'is-maximized': isDialogMaximized,
            'is-minimized': isDialogMinimized,
            'is-dragging': isDragging
          }"
          :style="previewWindowStyle"
        >
          <header
            class="preview-window__header"
            :class="{ 'is-draggable': isDialogNormal }"
            @mousedown="startDrag"
            @dblclick="toggleDialogMaximize"
            @click="handleWindowHeaderClick"
          >
            <div class="preview-window__title-group">
              <div class="preview-window__title">态势显示</div>
              <div class="preview-window__subtitle">{{ activePreviewName || '实验态势预览' }}</div>
            </div>
            <div class="preview-window__controls" @mousedown.stop>
              <button
                type="button"
                class="preview-window__control preview-window__control--minimize"
                aria-label="最小化"
                @click.stop="minimizeDialogWindow"
              >
                <span class="preview-window__control-icon preview-window__control-icon--minimize"></span>
              </button>
              <button
                type="button"
                class="preview-window__control"
                :aria-label="isDialogMaximized ? '还原' : '最大化'"
                @click.stop="toggleDialogMaximize"
              >
                <span
                  class="preview-window__control-icon"
                  :class="isDialogMaximized ? 'preview-window__control-icon--restore' : 'preview-window__control-icon--maximize'"
                ></span>
              </button>
              <button
                type="button"
                class="preview-window__control preview-window__control--close"
                aria-label="关闭"
                @click.stop="closePreviewWindow"
              >
                <span class="preview-window__control-icon preview-window__control-icon--close"></span>
              </button>
            </div>
          </header>

          <div v-show="!isDialogMinimized" class="preview-window__body">
            <div class="module-frame-wrapper">
              <iframe
                v-if="dialogVisible && moduleUrl"
                :src="moduleUrl"
                class="module-frame"
                frameborder="0"
                allowfullscreen
              />
              <div v-else class="preview-window__empty">暂未获取到可显示的态势页面</div>
            </div>
          </div>
        </section>
      </div>
    </teleport>
  </div>
</template>

<script setup name="simulation">
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref, toRefs } from 'vue'
import 'splitpanes/dist/splitpanes.css'
import { useRoute } from 'vue-router'
import { addDateRange } from '@/utils/ruoyi'
import { getExperimentInfos, getInfo } from '@/api/data/info'

const route = useRoute()
const dateRange = ref([])
const { proxy } = getCurrentInstance()
const projectOptions = ref([])

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const businessList = ref([])

const dialogVisible = ref(false)
const moduleUrl = ref('')
const dialogMode = ref('large')

const dialogWidth = computed(() => {
  if (dialogMode.value === 'compact') {
    return 'min(72vw, 1180px)'
  }
  return 'min(94vw, 1680px)'
})

const dialogTop = computed(() => {
  if (dialogMode.value === 'compact') {
    return '8vh'
  }
  return '3vh'
})

const isDialogFullscreen = computed(() => dialogMode.value === 'fullscreen')

const frameWrapperStyle = computed(() => {
  if (dialogMode.value === 'fullscreen') {
    return { height: 'calc(100vh - 118px)' }
  }
  if (dialogMode.value === 'compact') {
    return { height: 'min(62vh, 620px)' }
  }
  return { height: 'min(80vh, 860px)' }
})

const previewWindowRef = ref(null)
const activePreviewName = ref('')
const dialogWindowState = ref('normal')
const dialogWindowStateBeforeMinimize = ref('normal')
const isDragging = ref(false)

const viewport = reactive({
  width: 0,
  height: 0
})

const dialogWindowRect = reactive({
  left: 0,
  top: 0,
  width: 0,
  height: 0
})

const lastNormalDialogRect = reactive({
  left: 0,
  top: 0,
  width: 0,
  height: 0
})

const dragState = reactive({
  startX: 0,
  startY: 0,
  originLeft: 0,
  originTop: 0
})

const isDialogMinimized = computed(() => dialogWindowState.value === 'minimized')
const isDialogMaximized = computed(() => dialogWindowState.value === 'maximized')
const isDialogNormal = computed(() => dialogWindowState.value === 'normal')

const previewWindowStyle = computed(() => {
  const viewportWidth = viewport.width || 1440
  const viewportHeight = viewport.height || 900

  if (isDialogMinimized.value) {
    const width = Math.min(360, Math.max(viewportWidth - 24, 280))
    return {
      left: `${Math.max(viewportWidth - width - 12, 12)}px`,
      top: `${Math.max(viewportHeight - 68, 12)}px`,
      width: `${width}px`,
      height: '56px'
    }
  }

  if (isDialogMaximized.value) {
    return {
      left: '12px',
      top: '12px',
      width: `${Math.max(viewportWidth - 24, 320)}px`,
      height: `${Math.max(viewportHeight - 24, 240)}px`
    }
  }

  return {
    left: `${dialogWindowRect.left}px`,
    top: `${dialogWindowRect.top}px`,
    width: `${dialogWindowRect.width}px`,
    height: `${dialogWindowRect.height}px`
  }
})

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function clampMetric(value, preferredMin, max) {
  const safeMax = Math.max(max, 280)
  const safeMin = Math.min(preferredMin, safeMax)
  return Math.min(Math.max(value, safeMin), safeMax)
}

function assignRect(target, source) {
  target.left = source.left
  target.top = source.top
  target.width = source.width
  target.height = source.height
}

function normalizeDialogRect(sourceRect) {
  const viewportWidth = viewport.width || window.innerWidth || 1440
  const viewportHeight = viewport.height || window.innerHeight || 900
  const width = clampMetric(Number(sourceRect.width) || Math.round(viewportWidth * 0.84), 960, Math.max(viewportWidth - 32, 320))
  const height = clampMetric(Number(sourceRect.height) || Math.round(viewportHeight * 0.82), 620, Math.max(viewportHeight - 32, 260))
  const maxLeft = Math.max(8, viewportWidth - width - 8)
  const maxTop = Math.max(8, viewportHeight - height - 8)

  return {
    left: clamp(Number(sourceRect.left) || 0, 8, maxLeft),
    top: clamp(Number(sourceRect.top) || 0, 8, maxTop),
    width,
    height
  }
}

function createDefaultDialogRect() {
  const viewportWidth = viewport.width || window.innerWidth || 1440
  const viewportHeight = viewport.height || window.innerHeight || 900
  const width = clampMetric(Math.round(viewportWidth * 0.84), 1120, Math.max(viewportWidth - 48, 360))
  const height = clampMetric(Math.round(viewportHeight * 0.82), 680, Math.max(viewportHeight - 48, 320))

  return normalizeDialogRect({
    left: Math.round((viewportWidth - width) / 2),
    top: Math.max(20, Math.round((viewportHeight - height) / 2)),
    width,
    height
  })
}

function syncNormalDialogRect() {
  assignRect(lastNormalDialogRect, dialogWindowRect)
}

function initializeDialogWindow() {
  const rect = createDefaultDialogRect()
  assignRect(dialogWindowRect, rect)
  syncNormalDialogRect()
  dialogWindowState.value = 'normal'
  dialogWindowStateBeforeMinimize.value = 'normal'
}

function updateViewport() {
  viewport.width = window.innerWidth
  viewport.height = window.innerHeight

  if (dialogVisible.value && isDialogNormal.value) {
    const nextRect = normalizeDialogRect(dialogWindowRect)
    assignRect(dialogWindowRect, nextRect)
    syncNormalDialogRect()
  }
}

function handleDragMove(event) {
  if (!isDragging.value) {
    return
  }

  const nextRect = normalizeDialogRect({
    left: dragState.originLeft + event.clientX - dragState.startX,
    top: dragState.originTop + event.clientY - dragState.startY,
    width: dialogWindowRect.width,
    height: dialogWindowRect.height
  })

  dialogWindowRect.left = nextRect.left
  dialogWindowRect.top = nextRect.top
  syncNormalDialogRect()
}

function stopDrag() {
  if (!isDragging.value) {
    return
  }

  isDragging.value = false
  document.removeEventListener('mousemove', handleDragMove)
  document.removeEventListener('mouseup', stopDrag)
  document.body.style.userSelect = ''
}

function startDrag(event) {
  if (!dialogVisible.value || !isDialogNormal.value || event.button !== 0) {
    return
  }

  isDragging.value = true
  dragState.startX = event.clientX
  dragState.startY = event.clientY
  dragState.originLeft = dialogWindowRect.left
  dragState.originTop = dialogWindowRect.top
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', handleDragMove)
  document.addEventListener('mouseup', stopDrag)
  event.preventDefault()
}

function handleWindowHeaderClick() {
  if (isDialogMinimized.value) {
    if (dialogWindowStateBeforeMinimize.value === 'maximized') {
      dialogWindowState.value = 'maximized'
      return
    }
    restoreDialogWindow()
  }
}

function restoreDialogWindow() {
  stopDrag()
  dialogWindowState.value = 'normal'
  const rectSource = lastNormalDialogRect.width ? lastNormalDialogRect : createDefaultDialogRect()
  const rect = normalizeDialogRect(rectSource)
  assignRect(dialogWindowRect, rect)
  syncNormalDialogRect()
}

function minimizeDialogWindow() {
  stopDrag()
  if (isDialogNormal.value) {
    syncNormalDialogRect()
  }
  dialogWindowStateBeforeMinimize.value = isDialogMaximized.value ? 'maximized' : 'normal'
  dialogWindowState.value = 'minimized'
}

function toggleDialogMaximize() {
  stopDrag()
  if (isDialogMaximized.value) {
    restoreDialogWindow()
    return
  }

  if (isDialogNormal.value) {
    syncNormalDialogRect()
  }
  dialogWindowState.value = 'maximized'
}

function closePreviewWindow() {
  stopDrag()
  dialogVisible.value = false
  moduleUrl.value = ''
  activePreviewName.value = ''
  dialogWindowState.value = 'normal'
  dialogWindowStateBeforeMinimize.value = 'normal'
}

const data = reactive({
  queryParams: {
    experimentId: null,
    pageNum: 1,
    pageSize: 10,
    experimentName: undefined,
    projectId: undefined,
    createBy: undefined,
    createTime: undefined
  }
})

const { queryParams } = toRefs(data)

function resetQuery() {
  proxy.resetForm('queryRef')
  dateRange.value = []
  handleQuery()
}

function getProjectList() {
  getInfo(null, 'experiment').then(response => {
    projectOptions.value = response.projects || []
  })
}

function refresh() {
  getList()
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function getList() {
  loading.value = true
  getExperimentInfos(addDateRange(queryParams.value, dateRange.value))
    .then(response => {
      businessList.value = response.rows || (response.data && response.data.rows) || []
      total.value = response.total || (response.data && response.data.total) || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function getBaseModuleUrl() {
  if (route.query && route.query.url) {
    return String(route.query.url)
  }
  return ''
}

function handleView(row) {
  const baseUrl = getBaseModuleUrl()
  const separator = baseUrl.includes('?') ? '&' : '?'
  moduleUrl.value = baseUrl ? `${baseUrl}${separator}workflowId=${row.experimentId}&type=ex` : ''
  activePreviewName.value = row.experimentName || `试验 ${row.experimentId || ''}`.trim()
  initializeDialogWindow()
  dialogMode.value = 'large'
  dialogVisible.value = true
}

function setDialogMode(mode) {
  dialogMode.value = mode
}

function toggleDialogFullscreen() {
  dialogMode.value = dialogMode.value === 'fullscreen' ? 'large' : 'fullscreen'
}

function handleClose() {
  moduleUrl.value = ''
  dialogMode.value = 'large'
  activePreviewName.value = ''
}

onMounted(() => {
  updateViewport()
  window.addEventListener('resize', updateViewport)
  getList()
  getProjectList()
})

onBeforeUnmount(() => {
  stopDrag()
  window.removeEventListener('resize', updateViewport)
})
</script>

<style scoped>
:deep(.preview-dialog .el-dialog) {
  border-radius: 16px;
  overflow: hidden;
}

:deep(.preview-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 18px 24px 14px;
  border-bottom: 1px solid #f0f0f0;
}

:deep(.preview-dialog .el-dialog__body) {
  padding: 0;
}

:deep(.preview-dialog .el-dialog.is-fullscreen) {
  border-radius: 0;
}

.preview-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.preview-dialog-title {
  color: #1f1f1f;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.preview-dialog-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.preview-dialog-action {
  min-height: 32px;
  padding: 0 10px;
  border-radius: 8px;
  color: #409eff;
}

.preview-dialog-action:hover,
.preview-dialog-action.is-active {
  background: #ecf5ff;
}

.preview-dialog-action.is-active {
  font-weight: 600;
}

.preview-dialog-action-close {
  color: #ff4d4f;
}

.preview-dialog-action-close:hover {
  background: #fff2f0;
}

.module-frame-wrapper {
  width: 100%;
  height: 100%;
  background: #f5f7fa;
  overflow: hidden;
}

.module-frame {
  width: 100%;
  height: 100%;
  min-height: 100%;
  border: none;
  display: block;
}

.preview-window-layer {
  position: fixed;
  inset: 0;
  z-index: 2100;
  pointer-events: none;
}

.preview-window-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.24);
  backdrop-filter: blur(4px);
  pointer-events: auto;
}

.preview-window {
  position: absolute;
  display: flex;
  flex-direction: column;
  min-width: 280px;
  min-height: 56px;
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 26px 60px rgba(15, 23, 42, 0.24);
  overflow: hidden;
  pointer-events: auto;
  transition: top 0.2s ease, left 0.2s ease, width 0.2s ease, height 0.2s ease, box-shadow 0.2s ease;
}

.preview-window.is-dragging {
  transition: none;
  box-shadow: 0 30px 72px rgba(15, 23, 42, 0.3);
}

.preview-window.is-minimized {
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.22);
}

.preview-window.is-minimized .preview-window__header {
  border-bottom: none;
}

.preview-window.is-minimized .preview-window__subtitle {
  display: none;
}

.preview-window__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding-left: 18px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.96);
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  user-select: none;
}

.preview-window__header.is-draggable {
  cursor: move;
}

.preview-window__title-group {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1 1 auto;
}

.preview-window__title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.preview-window__subtitle {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #64748b;
  font-size: 12px;
  line-height: 1;
}

.preview-window__controls {
  display: flex;
  align-items: stretch;
  margin-left: auto;
  flex-shrink: 0;
}

.preview-window__control {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  min-width: 46px;
  height: 56px;
  padding: 0;
  border: none;
  background: transparent;
  color: #475569;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.preview-window__control:hover {
  background: rgba(148, 163, 184, 0.16);
  color: #0f172a;
}

.preview-window__control--close:hover {
  background: #ef4444;
  color: #ffffff;
}

.preview-window__control-icon {
  position: relative;
  display: block;
  width: 12px;
  height: 12px;
}

.preview-window__control-icon--minimize::before {
  content: '';
  position: absolute;
  left: 1px;
  right: 1px;
  bottom: 2px;
  height: 1.8px;
  border-radius: 999px;
  background: currentColor;
}

.preview-window__control-icon--maximize {
  box-sizing: border-box;
  border: 1.6px solid currentColor;
  border-radius: 2px;
}

.preview-window__control-icon--restore::before,
.preview-window__control-icon--restore::after {
  content: '';
  position: absolute;
  box-sizing: border-box;
  width: 8px;
  height: 8px;
  border: 1.6px solid currentColor;
  border-radius: 2px;
  background: #ffffff;
}

.preview-window__control-icon--restore::before {
  top: 0;
  right: 0;
}

.preview-window__control-icon--restore::after {
  left: 0;
  bottom: 0;
}

.preview-window__control-icon--close::before,
.preview-window__control-icon--close::after {
  content: '';
  position: absolute;
  top: 5px;
  left: 0;
  width: 12px;
  height: 1.8px;
  border-radius: 999px;
  background: currentColor;
}

.preview-window__control-icon--close::before {
  transform: rotate(45deg);
}

.preview-window__control-icon--close::after {
  transform: rotate(-45deg);
}

.preview-window__body {
  flex: 1 1 auto;
  min-height: 0;
  background: #f5f7fa;
}

.preview-window__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 24px;
  color: #64748b;
  font-size: 14px;
  background: linear-gradient(180deg, #f8fafc 0%, #eef2f7 100%);
}

@media (max-width: 768px) {
  :deep(.query-form.el-form--inline .el-form-item) {
    display: flex;
    width: 100% !important;
    margin-right: 0;
  }

  :deep(.query-form.el-form--inline .el-form-item__content) {
    flex: 1;
    min-width: 0;
  }

  :deep(.query-form .el-input),
  :deep(.query-form .el-select),
  :deep(.query-form .el-date-editor) {
    width: 100% !important;
  }

  .preview-window {
    min-width: 0;
  }

  .preview-window__header {
    padding-left: 14px;
  }

  .preview-window__title-group {
    gap: 8px;
  }

  .preview-window__subtitle {
    display: none;
  }

  .preview-window__control {
    width: 42px;
    min-width: 42px;
  }

  .preview-dialog-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .preview-dialog-actions {
    width: 100%;
    justify-content: flex-start;
  }

  :deep(.preview-dialog .el-dialog) {
    width: 96vw !important;
    margin-top: 2vh !important;
  }
}
</style>
