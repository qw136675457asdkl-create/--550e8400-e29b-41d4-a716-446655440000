<template>
  <div class="app-container">
    <div v-show="showSearch" class="filter-card">
      <el-form
        ref="queryRef"
        :model="queryParams"
        class="filter-form"
        label-position="top"
      >
        <el-row :gutter="16" class="filter-row">
          <el-col :xs="24" :sm="12" :md="12" :lg="6" :xl="6" class="filter-col">
            <el-form-item class="filter-item" label="ID" prop="experimentId">
              <el-input
                v-model="queryParams.experimentId"
                class="filter-control"
                placeholder="请输入数据ID"
                clearable
                @keyup.enter="handleQuery"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="12" :lg="6" :xl="6" class="filter-col">
            <el-form-item class="filter-item" label="试验名称" prop="experimentName">
              <el-input
                v-model="queryParams.experimentName"
                class="filter-control"
                placeholder="请输入试验名称"
                clearable
                @keyup.enter="handleQuery"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="12" :lg="6" :xl="6" class="filter-col">
            <el-form-item class="filter-item" label="所属项目" prop="projectId">
              <el-select v-model="queryParams.projectId" class="filter-control" placeholder="请选择所属项目" clearable>
                <el-option v-for="item in projectOptions" :key="item.projectId" :label="item.projectName" :value="item.projectId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="12" :lg="6" :xl="6" class="filter-col">
            <el-form-item class="filter-item" label="创建人" prop="createBy">
              <el-input
                v-model="queryParams.createBy"
                class="filter-control"
                placeholder="请输入创建人"
                clearable
                @keyup.enter="handleQuery"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" :md="16" :lg="8" :xl="8" class="filter-col filter-col-range">
            <el-form-item class="filter-item" label="创建时间">
              <el-date-picker
                v-model="dateRange"
                class="filter-control filter-control-range"
                value-format="YYYY-MM-DD"
                type="daterange"
                range-separator="-"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" :md="8" :lg="8" :xl="6" class="filter-col filter-col-actions">
            <el-form-item class="filter-item filter-item-actions">
              <div class="filter-action-group">
                <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                <el-button class="filter-reset-button" icon="Refresh" @click="resetQuery">重置</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <div class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-right">
          <el-tooltip content="刷新" placement="top">
            <el-button class="refresh-tool-button" circle icon="Refresh" @click="refresh" />
          </el-tooltip>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
        </div>
      </div>

      <el-table v-loading="loading" :data="businessList" class="simulation-table">
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
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width action-column">
          <template #default="scope">
            <el-tooltip content="态势显示(播放视频)" placement="top">
              <el-button link class="action-link action-link-view" @click="handleView(scope.row)">查看</el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        class="table-pagination"
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </div>

    <el-dialog
      title="态势显示 (视频预览)"
      v-model="dialogVisible"
      width="800px"
      append-to-body
      @close="handleClose"
    >
      <div class="video-container">
        <video
          ref="videoRef"
          :src="videoUrl"
          controls
          autoplay
          style="width: 100%; max-height: 550px; outline: none;"
        >
          您的浏览器不支持 HTML5 video 标签。
        </video>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="simulation">
import { ref, reactive, toRefs, getCurrentInstance, onMounted } from 'vue' // 确保你引入了 ref
import 'splitpanes/dist/splitpanes.css'
import { addDateRange } from "@/utils/ruoyi"
import { getExperimentInfos } from '@/api/data/info'
import { getInfo } from '@/api/data/info'

const dateRange = ref([])
const { proxy } = getCurrentInstance()
const projectOptions = ref([])

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const businessList = ref([])

// [新增] 视频弹窗相关变量
const dialogVisible = ref(false)
const videoUrl = ref('')
const videoRef = ref(null)

const data = reactive({
  queryParams: {
    id: null,
    pageNum: 1,
    pageSize: 10,
    experimentName: undefined,
    projectId: undefined,
    createBy: undefined,
    createTime: undefined,
  }
})
const { queryParams } = toRefs(data)

function resetQuery() {
  proxy.resetForm("queryRef")
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
  getList()
}

function getList() {
  loading.value = true
  getExperimentInfos(addDateRange(queryParams.value, dateRange.value)).then(response => {
    businessList.value = response.rows || (response.data && response.data.rows) || [];
    total.value = response.total || (response.data && response.data.total) || 0;
    loading.value = false;
  });
}

// [新增] 点击查看按钮触发的方法
function handleView(row) {
  // TODO: 这里需要根据你后端的实际数据进行替换。
  // 比如如果这行数据中包含视频地址，可能是 row.videoPath 或 row.url。
  // 下面这行代码的意思是：如果有视频字段就用该字段，如果没有就默认播放一个测试视频。
  videoUrl.value = row.videoUrl || 'https://www.w3schools.com/html/mov_bbb.mp4';

  // 打开弹窗
  dialogVisible.value = true;
}

// [新增] 弹窗关闭时的回调，用来停止视频声音
function handleClose() {
  if (videoRef.value) {
    videoRef.value.pause(); // 暂停视频播放
  }
  videoUrl.value = ''; // 清空地址
}

onMounted(() => {
  getList()
  getProjectList()
})
</script>

<style scoped>
.app-container {
  font-family: PingFang SC, Microsoft YaHei, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.filter-card,
.table-card {
  background: #ffffff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.filter-card {
  margin-bottom: 24px;
  padding: 16px 20px 4px;
}

.filter-form {
  width: 80%;
}

.filter-row {
  align-items: flex-start;
}

.filter-item {
  margin-bottom: 14px;
}

.filter-col {
  display: flex;
}

.filter-col :deep(.el-form-item) {
  width: 100%;
}

.filter-action-group {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  min-height: 32px;
  margin-left: 12px;
}

.filter-item-actions {
  padding-top: 27px;
}

.table-card {
  padding: 16px 20px 8px;
}

.table-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.table-toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.table-pagination {
  margin-top: 16px;
}

:deep(.filter-form .el-form-item__label) {
  padding-bottom: 6px;
  color: #1f1f1f;
  font-weight: 500;
  font-size: 13px;
  line-height: 1.4;
}

:deep(.filter-control.el-input),
:deep(.filter-control.el-select) {
  width: 100%;
  max-width: 220px;
}

:deep(.filter-control.el-date-editor) {
  width: 100%;
  max-width: 320px;
}

:deep(.filter-control .el-input__wrapper),
:deep(.filter-control .el-select__wrapper),
:deep(.filter-control.el-range-editor.el-input__wrapper) {
  min-height: 32px;
  border-radius: 6px;
  box-shadow: 0 0 0 1px #d9d9d9 inset;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

:deep(.filter-form .el-input__inner),
:deep(.filter-form .el-select__selected-item),
:deep(.filter-form .el-range-input) {
  font-size: 13px;
}

:deep(.filter-form .el-input__wrapper:hover),
:deep(.filter-form .el-select__wrapper:hover),
:deep(.filter-form .el-range-editor.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #1677ff inset;
}

:deep(.filter-form .el-input__wrapper.is-focus),
:deep(.filter-form .el-select__wrapper.is-focused),
:deep(.filter-form .el-range-editor.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.15), 0 0 0 1px #1677ff inset;
}

:deep(.filter-action-group .el-button) {
  min-height: 32px;
  padding: 0 16px;
  border-radius: 6px;
}

:deep(.filter-action-group .el-button--primary) {
  background: #1677ff;
  border-color: #1677ff;
}

.filter-reset-button {
  color: #1677ff;
  background: #ffffff;
  border-color: #d9d9d9;
}

.refresh-tool-button {
  border-color: #d9d9d9;
  color: #595959;
  background: #ffffff;
}

:deep(.refresh-tool-button:hover) {
  color: #1677ff;
  border-color: #1677ff;
  background: #f0f7ff;
}

:deep(.simulation-table) {
  --el-table-border-color: #f0f0f0;
  --el-table-header-bg-color: #fafafa;
  --el-table-row-hover-bg-color: #f5faff;
  border-radius: 8px;
}

:deep(.simulation-table .el-table__inner-wrapper::before) {
  height: 0;
}

:deep(.simulation-table th.el-table__cell) {
  background: #fafafa;
  color: #1f1f1f;
  font-weight: 600;
  border-bottom: 1px solid #f0f0f0;
}

:deep(.simulation-table td.el-table__cell),
:deep(.simulation-table th.el-table__cell.is-leaf) {
  border-right: none;
}

:deep(.simulation-table .el-table__row td.el-table__cell) {
  height: 54px;
  padding-top: 0;
  padding-bottom: 0;
  color: #434343;
  border-bottom: 1px solid #f0f0f0;
}

:deep(.simulation-table .el-table__body tr:hover > td.el-table__cell) {
  background: #f5faff !important;
}

:deep(.simulation-table .action-link) {
  padding: 0;
  font-weight: 500;
}

:deep(.simulation-table .action-link-view) {
  color: #1677ff;
}

.video-container {
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #000;
  border-radius: 4px;
  overflow: hidden;
}

@media (max-width: 1440px) {
  :deep(.filter-control.el-input),
  :deep(.filter-control.el-select) {
    max-width: 100%;
  }

  :deep(.filter-control.el-date-editor) {
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  .filter-card,
  .table-card {
    padding-left: 16px;
    padding-right: 16px;
  }

  .filter-action-group {
    justify-content: flex-start;
    margin-left: 0;
    flex-wrap: wrap;
  }

  .filter-item-actions {
    padding-top: 0;
  }
}
</style>
