<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="ID" prop="experimentId">
        <el-input v-model="queryParams.experimentId" placeholder="请输入数据ID" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="试验名称" prop="experimentName">
        <el-input v-model="queryParams.experimentName" placeholder="请输入试验名称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="所属项目" prop="projectId">
        <el-select v-model="queryParams.projectId" placeholder="请选择所属项目" clearable>
          <el-option v-for="item in projectOptions" :key="item.projectId" :label="item.projectName" :value="item.projectId" />
        </el-select>
      </el-form-item>
      <el-form-item label="创建人" prop="createBy">
        <el-input v-model="queryParams.createBy" placeholder="请输入创建人" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="创建时间" style="width: 308px">
        <el-date-picker v-model="dateRange" value-format="YYYY-MM-DD" type="daterange" range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="refresh">刷新</el-button>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
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
          <el-tooltip content="态势显示(播放视频)" placement="top">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)"></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

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
.video-container {
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #000;
  border-radius: 4px;
  overflow: hidden;
}
</style> 
