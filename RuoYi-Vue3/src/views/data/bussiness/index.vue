<template>
    <div class="app-container">
        <splitpanes :horizontal="appStore.device === 'mobile'" class="default-theme">
            <!-- 左侧菜单栏 试验信息树形表 -->
            <pane size="16">
                <el-col>
                    <div class="head-container">
                        <el-input v-model="name" placeholder="请输入试验名称" clearable prefix-icon="Search" style="margin-bottom: 20px"></el-input>
                    </div>
                    <div class="body-container">
                        <el-tree :data="treeTableOptions" :props="{ label: 'label', children: 'children' }" :expand-on-click-node="false" :filter-node-method="filterNode" ref="TreeRef" node-key="id" highlight-current default-expand-all @node-click="handleNodeClick"></el-tree>
                    </div>
                </el-col>
            </pane>
            <!-- 右侧内容栏 试验信息列表 -->
            <pane>
                <div class="pane-content">
                    <div v-show="showSearch" class="query-section">
                        <div class="query-main">
                            <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="88px" class="query-form">
                                <el-form-item label="ID" prop="id">
                                    <el-input v-model="queryParams.id" placeholder="请输入数据ID" clearable class="query-control" @keyup.enter="handleQuery" />
                                </el-form-item>
                                <el-form-item label="数据名称" prop="dataName">
                                    <el-input v-model="queryParams.dataName" placeholder="请输入数据名称" clearable class="query-control" @keyup.enter="handleQuery" />
                                </el-form-item>
                                <el-form-item label="试验名称" prop="experimentName">
                                    <el-input v-model="queryParams.experimentName" placeholder="请输入试验名称" clearable class="query-control" @keyup.enter="handleQuery" />
                                </el-form-item>
                                <el-form-item label="所属项目" prop="projectId">
                                    <el-select v-model="queryParams.projectId" placeholder="请选择所属项目" clearable class="query-control">
                                        <el-option v-for="item in projectOptions" :key="item.projectId" :label="item.projectName" :value="item.projectId" />
                                    </el-select>
                                </el-form-item>

                                <template v-if="advancedSearchExpanded">
                                    <el-form-item label="创建人" prop="createBy">
                                        <el-input v-model="queryParams.createBy" placeholder="请输入创建人" clearable class="query-control" @keyup.enter="handleQuery" />
                                    </el-form-item>
                                    <el-form-item label="是否模拟" prop="isSimulation">
                                        <el-select v-model="queryParams.isSimulation" placeholder="请选择数据类型" clearable class="query-control">
                                            <el-option label="真实" :value="true" />
                                            <el-option label="模拟" :value="false" />
                                        </el-select>
                                    </el-form-item>
                                    <el-form-item label="数据状态" prop="workStatus">
                                        <el-input v-model="queryParams.workStatus" placeholder="请输入数据状态" clearable class="query-control" @keyup.enter="handleQuery" />
                                    </el-form-item>
                                    <el-form-item label="创建时间" class="query-date-item">
                                        <el-date-picker
                                            v-model="dateRange"
                                            value-format="YYYY-MM-DD"
                                            type="daterange"
                                            range-separator="-"
                                            start-placeholder="开始日期"
                                            end-placeholder="结束日期"
                                            class="query-date-control"
                                        />
                                    </el-form-item>
                                </template>
                            </el-form>

                            <div class="query-actions">
                                <el-button-group class="query-action-group">
                                    <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                                    <el-button icon="Refresh" @click="resetQuery">重置</el-button>
                                </el-button-group>
                                <el-button
                                    link
                                    type="primary"
                                    class="query-expand-btn"
                                    :icon="advancedSearchExpanded ? ArrowUp : ArrowDown"
                                    @click="toggleAdvancedSearch"
                                >
                                    {{ advancedSearchExpanded ? '收起' : '展开' }}
                                </el-button>
                            </div>
                        </div>
                    </div>

                    <div v-show="showSearch" class="toolbar-divider"></div>

                    <el-row :gutter="10" class="mb8 global-actions-row">
                        <el-col :span="1.5">
                            <el-button
                            type="primary"
                            plain
                            icon="Upload"
                            @click="openFileManager"
                            v-hasPermi="['dataInfo:info:insert']"
                            >导入</el-button>
                        </el-col>
                        <el-col :span="1.5">
                            <el-button
                            type="primary"
                            plain
                            icon="Download"
                            @click="handleExportData"
                            >导出</el-button>
                        </el-col>
                        <el-col :span="1.5">
                            <el-button
                            type="danger"
                            plain
                            icon="Delete"
                            :disabled="multiple"
                            @click="handleDelete()"
                            v-hasPermi="['dataInfo:info:delete']"
                            >删除</el-button>
                        </el-col>
                        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
                    </el-row>

                    <el-table v-loading="loading" :data="businessList" @selection-change="handleSelectionChange">
                        <el-table-column type="selection" width="55" align="center" />
                        <el-table-column label="ID" align="center" prop="id" />
                        <el-table-column label="数据名称" align="center" prop="dataName" :show-overflow-tooltip="true" />
                        <el-table-column label="是否模拟" align="center" prop="isSimulation" >
                            <template #default="scope">
                                <span v-if="scope.row.isSimulation === true">真实数据</span>
                                <span v-else-if="scope.row.isSimulation === false">模拟数据</span>
                                <span v-else>未知类型</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="路径" align="center" prop="dataFilePath" :show-overflow-tooltip="true" />
                        <el-table-column label="所属试验" align="center" prop="experimentName" :show-overflow-tooltip="true" />
                        <el-table-column label="所属项目" align="center" prop="projectName" :show-overflow-tooltip="true" />
                        <el-table-column label="试验目标" align="center" prop="targetType" :show-overflow-tooltip="true" />
                        <el-table-column label="试验时间" align="center" prop="startTime" >
                            <template #default="scope">
                                <span>{{ parseTime(scope.row.startTime) }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="试验地点" align="center" prop="location" :show-overflow-tooltip="true" />
                        <el-table-column label="试验描述" align="center" prop="contentDesc" :show-overflow-tooltip="true" />
                        <el-table-column label="创建人" align="center" prop="createBy" />
                        <el-table-column label="创建时间" align="center" prop="createTime" >
                            <template #default="scope">
                                <span>{{ parseTime(scope.row.createTime) }}</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="数据种类" align="center" prop="dataType" :show-overflow-tooltip="true" />
                        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
                            <template #default="scope">
                            <el-tooltip content="详情" placement="top">
                                <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['dataInfo:info:query']"></el-button>
                            </el-tooltip>
                            <el-tooltip content="修改" placement="top">
                                <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['dataInfo:info:update']"></el-button>
                            </el-tooltip>
                            <el-tooltip content="删除" placement="top">
                                <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['dataInfo:info:delete']"></el-button>
                            </el-tooltip>
                            </template>
                        </el-table-column>
                    </el-table>
                    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
                </div>
            </pane>
        </splitpanes>

        <!-- 添加或修改数据对话框 -->
        <el-dialog :title="title" v-model="open" width="700px" append-to-body>
            <el-form ref="dataRef" :model="form" :rules="rules" label-width="100px">
                <!-- 可编辑字段 -->
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="数据名称" prop="dataName">
                            <el-input v-model="form.dataName" placeholder="请输入数据名称" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="是否模拟" prop="isSimulation">
                            <el-select v-model="form.isSimulation" placeholder="请选择">
                                <el-option label="真实" :value="true" />
                                <el-option label="模拟" :value="false" />
                            </el-select>
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="数据种类" prop="dataType">
                            <el-select v-model="form.dataType" placeholder="请选择数据种类">
                                <el-option label="ADS-B目标元数据" value="ADS-B目标元数据" />
                                <el-option label="AIS目标元数据" value="AIS目标元数据" />
                                <el-option label="通信情报日志元数据" value="通信情报日志元数据" />
                                <el-option label="交战闭锁元数据" value="交战闭锁元数据" />
                                <el-option label="电子战截获元数据" value="电子战截获元数据" />
                                <el-option label="载机姿态元数据" value="载机姿态元数据" />
                                <el-option label="惯导状态元数据" value="惯导状态元数据" />
                                <el-option label="被动探测元数据" value="被动探测元数据"/>
                                <el-option label="雷达系统航迹元数据" value="雷达系统航迹元数据"/>
                                <el-option label="目标牵引询问元数据" value="目标牵引询问元数据"/>
                            </el-select>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="文件名称" prop="fileName" v-if="title === '修改数据'">
                            <el-input v-model="form.fileName" placeholder="请输入文件名称" />
                        </el-form-item>
                    </el-col>
                </el-row>

                <!-- 只读字段 -->
                <el-divider />
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="ID">
                            <el-input :value="form.id" disabled />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="所属试验">
                            <el-input :value="form.experimentName" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="所属项目">
                            <el-input :value="form.projectName" disabled />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="试验目标">
                            <el-input :value="form.targetType" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="24">
                        <el-form-item label="路径">
                            <el-input :value="form.dataFilePath" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="试验时间">
                            <el-input :value="parseTime(form.startTime)" disabled />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="试验地点">
                            <el-input :value="form.location" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="24">
                        <el-form-item label="内容描述">
                            <el-input :value="form.contentDesc" type="textarea" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="创建人">
                            <el-input :value="form.createBy" disabled />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="创建时间">
                            <el-input :value="parseTime(form.createTime)" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
            </el-form>
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" @click="submitForm">确 定</el-button>
                    <el-button @click="cancel">取 消</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 详情对话框 -->
        <el-dialog title="数据详情" v-model="openView" width="700px" append-to-body>
            <el-form :model="form" label-width="100px">
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="ID">{{ form.id }}</el-form-item>
                        <el-form-item label="数据名称">{{ form.dataName }}</el-form-item>
                        <el-form-item label="文件名称">{{ form.fileName }}</el-form-item>
                        <el-form-item label="是否模拟">
                            <span v-if="form.isSimulation === true">真实数据</span>
                            <span v-else-if="form.isSimulation === false">模拟数据</span>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="所属试验">{{ form.experimentName }}</el-form-item>
                        <el-form-item label="所属项目">{{ form.projectName }}</el-form-item>
                        <el-form-item label="试验目标">{{ form.targetType }}</el-form-item>
                    </el-col>
                    <el-col :span="24">
                        <el-form-item label="路径">{{ form.dataFilePath }}</el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="试验时间">{{ parseTime(form.startTime) }}</el-form-item>
                        <el-form-item label="试验地点">{{ form.location }}</el-form-item>
                    </el-col>
                    <el-col :span="24">
                        <el-form-item label="内容描述">{{ form.contentDesc }}</el-form-item>
                        <el-form-item label="创建人">{{ form.createBy }}</el-form-item>
                        <el-form-item label="创建时间">{{ parseTime(form.createTime) }}</el-form-item>
                    </el-col>
                </el-row>
            </el-form>

            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="openView = false">关 闭</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 文件管理器 (数据导入) 对话框 -->
        <el-dialog v-model="importVisible" title="数据导入" width="60%" append-to-body @closed="resetUploadData" class="import-dialog">
            <div class="import-dialog-body">
                <!-- 数据信息表单 -->
                <el-form ref="uploadDataFormRef" :model="uploadDataForm" :rules="uploadDataRules" label-width="100px" class="import-form">
                    <el-row>
                        <el-col :span="12">
                            <el-form-item label="数据名称" prop="dataName">
                                <el-input v-model="uploadDataForm.dataName" placeholder="为空则使用文件名" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="所属试验" prop="experimentId">
                                <el-tree-select
                                    v-model="uploadDataForm.experimentId"
                                    :data="selectableTreeOptions"
                                    :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
                                    value-key="id"
                                    placeholder="请选择所属试验"
                                    check-strictly
                                    filterable
                                />
                            </el-form-item>
                        </el-col>
                    </el-row>
                    <el-row>
                        <el-col :span="12">
                            <el-form-item label="试验目标" prop="targetId">
                                <el-select v-model="uploadDataForm.targetId" placeholder="请选择试验目标" @change="handleTargetChange">
                                    <el-option v-for="item in targetTypeOptions" :key="item.targetId" :label="item.targetType" :value="item.targetId" />
                                </el-select>
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="数据种类" prop="dataType">
                                <el-select v-model="uploadDataForm.dataType" placeholder="请选择数据种类">
                                    <el-option label="载机惯导数据" value="载机惯导数据" />
                                    <el-option label="载机姿态数据" value="载机姿态数据" />
                                    <el-option label="雷达航迹数据" value="雷达航迹数据" />
                                    <el-option label="电子战数据" value="电子战数据" />
                                    <el-option label="通侦数据" value="通侦数据" />
                                    <el-option label="ADS-B数据" value="ADS-B数据" />
                                    <el-option label="AIS数据" value="AIS数据" />
                                    <el-option label="目标牵引与询问数据" value="目标牵引与询问数据"/>
                                    <el-option label="方位数据" value="方位数据"/>
                                    <el-option label="闭锁信息数据" value="闭锁信息数据"/>
                                </el-select>
                            </el-form-item>
                        </el-col>
                    </el-row>
                    <el-row>
                        <el-col :span="12">
                            <el-form-item label="是否模拟" prop="isSimulation">
                                <el-radio-group v-model="uploadDataForm.isSimulation">
                                    <el-radio :label="true">真实</el-radio>
                                    <el-radio :label="false">模拟</el-radio>
                                </el-radio-group>
                            </el-form-item>
                        </el-col>
                    </el-row>
                </el-form>

                <!-- 文件上传 -->
                <el-divider>选择文件上传至当前文件夹</el-divider>
                <el-upload drag action="" :auto-upload="false" :on-change="handleFileSelect" v-model:file-list="uploadFiles" class="import-upload">
                    <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                    <div class="el-upload__text">拖到此处或<em>点击选择文件</em></div>
                </el-upload>
            </div>
            <template #footer>
                <el-button @click="cancelUpload">取消</el-button>
                <el-button type="primary" @click="submitUpload" :loading="fileLoading">导入数据</el-button>
            </template>
        </el-dialog>

        <!-- 文件管理器预览对话框 -->
        <el-dialog v-model="fileManagerPreviewVisible" :title="`预览: ${fileManagerPreviewFile?.name || ''}`" width="70%" append-to-body>
            <component :is="fileManagerPreviewComponent" :file="fileManagerPreviewFileInfo" v-if="fileManagerPreviewFile" />
            <el-empty v-else description="暂无预览内容" />
            <template #footer>
                <el-button type="primary" @click="fileManagerPreviewVisible = false">关闭</el-button>
            </template>
        </el-dialog>

        <!-- 业务数据详情 (文件预览) 对话框 -->
        <el-dialog v-model="detailVisible" :title="detailTitle" width="70%" append-to-body>
            <div v-if="detailFile">
                <!-- CSV/Excel走分页预览，避免一次性渲染造成卡顿 -->
                <div v-if="isDetailTabularFile">
                    <div v-loading="detailPreviewLoading">
                        <el-table v-if="detailTableRows.length > 0" :data="detailTableRows" border stripe height="500">
                            <el-table-column
                                v-for="header in detailTableColumns"
                                :key="header"
                                :prop="header"
                                :label="header"
                                show-overflow-tooltip
                            />
                        </el-table>
                        <el-empty v-else description="暂无预览数据" />
                    </div>
                    <pagination
                        v-show="detailPreviewTotal > 0"
                        :total="detailPreviewTotal"
                        v-model:page="detailPreviewPageNum"
                        v-model:limit="detailPreviewPageSize"
                        @pagination="loadDetailTablePreview"
                    />
                </div>
            </div>
            <el-empty v-else description="暂无文件信息或路径无效" />
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" @click="handleDownloadDetailFile(detailFile)" v-if="detailFile" v-hasPermi="['dataInfo:info:download']">下 载</el-button>
                    <el-button @click="detailVisible = false">关 闭</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 新建文件夹对话框 -->
        <el-dialog v-model="newFolderVisible" title="新建文件夹" width="40%" append-to-body>
            <el-input v-model="newFolderName" placeholder="输入文件夹名称" />
            <template #footer>
                <el-button @click="newFolderVisible = false">取消</el-button>
                <el-button type="primary" @click="submitNewFolder">创建</el-button>
            </template>
        </el-dialog>
    </div>
</template>
<script setup name="Business">
import useAppStore from '@/store/modules/app'
import {Splitpanes, Pane} from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import {getExperimentList,getdataList,getdataDetail,updatedata,deldata,adddata,previewData,downloadData} from '@/api/data/bussiness'
import {getInfo} from "@/api/data/info"
import { addDateRange, blobValidate } from "@/utils/ruoyi"
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { UploadFilled, Folder as ElIconFolder, Document as ElIconDocument, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import ImageViewer from '@/views/viewer/ImageViewer.vue'
import VideoViewer from '@/views/viewer/VideoViewer.vue'
import TextViewer from '@/views/viewer/TextViewer.vue'
import JsonViewer from '@/views/viewer/JsonViewer.vue'
import BinaryViewer from '@/views/viewer/BinaryViewer.vue'
import PDFViewer from '@/views/viewer/PDFViewer.vue'
import ExcelViewer from '@/views/viewer/ExcelViewer.vue'
import { saveAs } from 'file-saver'
import { submitDownloadTask, getDownloadTaskStatus } from '@/api/file'
const dateRange = ref([])
const { proxy } = getCurrentInstance()
const treeTableOptions = ref(undefined)
const appStore = useAppStore()
const projectOptions = ref([])
const open = ref(false)
const openView = ref(false)
const openExperiment = ref(false)
const targetTypeOptions = ref([])
const title = ref("")

const name = ref('')
const loading = ref(false)
const showSearch = ref(true)
const advancedSearchExpanded = ref(false)
const total = ref(0)
const businessList = ref([])
const ids = ref([])
const single = ref(true)
const multiple = ref(true)

// 文件管理器相关状态
const fileLoading = ref(false)
const importVisible = ref(false)
const renameVisible = ref(false)
const newFolderVisible = ref(false)
const uploadFiles = ref([])
const newFileName = ref('')
const newFolderName = ref('')
const fileManagerPreviewFile = ref(null)
const fileManagerPreviewVisible = ref(false)
const uploadDataFormRef = ref(null)

// 详情预览相关状态
const detailVisible = ref(false)
const detailFile = ref(null)
const detailTitle = ref("文件预览")
const detailPreviewLoading = ref(false)
const detailPreviewPageNum = ref(1)
const detailPreviewPageSize = ref(200)
const detailPreviewTotal = ref(0)
const detailTableRows = ref([])

/** 打开文件管理器 (导入) */
function openFileManager() {
  // 加载必要数据
  if (!treeTableOptions.value || treeTableOptions.value.length === 0) {
      getTreeData()
  }
  importVisible.value = true
  getInfo(null, 'experiment').then(res => {
    targetTypeOptions.value = res.targetTypes || []
  }).catch(err => {
    ElMessage.error('获取试验目标失败: ' + (err.message || '未知错误'))
  })
}

/** 导出数据 (下载) */
function handleExportData() {
  if (ids.value.length === 0) {
    ElMessage.warning("请选择要导出的数据")
    return
  }
  
  const selectedRows = businessList.value.filter(item => ids.value.includes(item.id))
  // 获取所有非空的文件路径
  const paths = selectedRows.map(item => item.dataFilePath).filter(p => p)
  
  if (paths.length === 0) {
    ElMessage.warning("选中的数据中没有关联的文件路径")
    return
  }
  
  submitDownloadTask(paths).then(res => {
    if (res.code === 200) pollProgress(res.data)
    else ElMessage.error(res.msg)
  }).catch(e => ElMessage.error('导出请求失败: ' + (e.message || '未知错误')))
}

const uploadDataForm = reactive({
  dataName: '',
  experimentId: null,
  targetId: null,
  targetType: null,
  dataType: '',
  isSimulation: true
})
const uploadDataRules = {
  experimentId: [{ required: true, message: "请选择试验", trigger: "change" }],
  targetId: [{ required: true, message: "请选择试验目标", trigger: "change" }],
  dataType: [{ required: true, message: "请选择数据种类", trigger: "change" }],
  isSimulation: [{ required: true, message: "请选择数据类型", trigger: "change" }]
}

const data = reactive({
    form: {},
    queryParams: {
        id: null,
        pageNum: 1,
        pageSize: 10,
        dataName: undefined,
        experimentName: undefined,
        projectId: undefined,
        createBy: undefined,
        dataType: undefined,
        startTime: undefined,
        endTime: undefined,
        workStatus: undefined,
        dataCategory: undefined,
        createTime: undefined,
    },
    rules: {
        dataName: [
            { required: true, message: "数据名称不能为空", trigger: "blur" }
        ],
        isSimulation: [
            { required: true, message: "数据类型不能为空", trigger: "change" }
        ],
        dataType: [
            { required: true, message: "数据种类不能为空", trigger: "blur" }
        ],
        fileName: [
            { required: true, message: "文件名称不能为空", trigger: "blur" }
        ]
    }
})

const {queryParams, form, rules} = toRefs(data)
const filterNode = (value, data) => {
    if (!value) return true
    return data.label && data.label.indexOf(value) !== -1
}

function getTreeData() {
    getExperimentList().then(response => {
        const transformedData = transformTreeData(response.data)
        treeTableOptions.value = transformedData
        console.log('Tree data loaded:', transformedData)
    }).catch(error => {
        console.error('Failed to load tree data:', error)
    })
}
function getProjects() {
  getInfo(null, 'experiment').then(res => {
    projectOptions.value = res.projects || []
  }).catch(err => {
    ElMessage.error('获取项目信息失败: ' + (err.message || '未知错误'))
  })
}

// --- 文件管理器逻辑开始 ---

const fileManagerPreviewFileInfo = computed(() => {
  if (!fileManagerPreviewFile.value) return null
  return {
    name: fileManagerPreviewFile.value.name,
    path: fileManagerPreviewFile.value.path,
    contentUrl: `/api/file/content?path=${encodeURIComponent(fileManagerPreviewFile.value.path)}`
  }
})

const fileManagerPreviewComponent = computed(() => getPreviewComponent(fileManagerPreviewFile.value))


const detailTableColumns = computed(() => Object.keys(detailTableRows.value[0] || {}))
const isDetailTabularFile = computed(() => isTabularFile(detailFile.value))
const detailPreviewComponent = computed(() => getPreviewComponent(detailFile.value))

function getPreviewFileName(file) {
  if (!file) return ''
  const candidates = [file.name, file.path, file.dataName, file.dataFilePath]
  const withSuffix = candidates.find(item => typeof item === 'string' && item.lastIndexOf('.') > -1)
  return (withSuffix || candidates.find(item => typeof item === 'string') || '').toLowerCase()
}

function isTabularFile(file) {
  const fileName = getPreviewFileName(file)
  return fileName.endsWith('.csv') || fileName.endsWith('.xls') || fileName.endsWith('.xlsx')
}

function getPreviewComponent(file) {
  if (!file) return null
  const fileName = getPreviewFileName(file)
  let type = 'binary'
  
  if (fileName.endsWith('.png') || fileName.endsWith('.jpg') || fileName.endsWith('.jpeg') || 
      fileName.endsWith('.gif') || fileName.endsWith('.bmp')) {
    type = 'image'
  } else if (fileName.endsWith('.mp4') || fileName.endsWith('.webm') || fileName.endsWith('.avi')) {
    type = 'video'
  } else if (fileName.endsWith('.txt')) {
    type = 'text'
  } else if (fileName.endsWith('.json')) {
    type = 'json'
  } else if (fileName.endsWith('.pdf')) {
    type = 'pdf'
  } else if (fileName.endsWith('.xls') || fileName.endsWith('.xlsx')) {
    type = 'excel'
  } else if (fileName.endsWith('.csv')) {
    type = 'csv'
  }

  const componentMap = {
    image: ImageViewer,
    video: VideoViewer,
    text: TextViewer,
    json: JsonViewer,
    pdf: PDFViewer,
    excel: ExcelViewer,
    csv: ExcelViewer,
    binary: BinaryViewer
  }
  return componentMap[type]
}

function resetDetailTablePreview() {
  detailPreviewPageNum.value = 1
  detailPreviewPageSize.value = 20
  detailPreviewTotal.value = 0
  detailTableRows.value = []
}

async function loadDetailTablePreview() {
  if (!detailFile.value || !detailFile.value.path || !detailFile.value.experimentId) return
  detailPreviewLoading.value = true
  try {
    const response = await previewData({
      experimentId: detailFile.value.experimentId,
      dataFilePath: detailFile.value.path,
      pageNum: detailPreviewPageNum.value,
      pageSize: detailPreviewPageSize.value
    })
    if (response.code === 200) {
      const pageData = response.data || {}
      detailTableRows.value = pageData.rows || []
      detailPreviewTotal.value = Number(pageData.total) || 0
      detailPreviewPageNum.value = Number(pageData.pageNum) || detailPreviewPageNum.value
      detailPreviewPageSize.value = Number(pageData.pageSize) || detailPreviewPageSize.value
    } else {
      detailTableRows.value = []
      detailPreviewTotal.value = 0
      ElMessage.error(response.msg || "预览失败")
    }
  } catch (error) {
    detailTableRows.value = []
    detailPreviewTotal.value = 0
    ElMessage.error("预览请求失败: " + (error.message || "未知错误"))
  } finally {
    detailPreviewLoading.value = false
  }
}


const selectableTreeOptions = computed(() => {
    const disableProjects = (nodes) => {
        return nodes.map(node => ({
            ...node,
            disabled: node.type === 'project',
            children: node.children ? disableProjects(node.children) : []
        }))
    }
    return treeTableOptions.value ? disableProjects(treeTableOptions.value) : []
})

const resetUploadData = () => {
  uploadDataForm.dataName = ''
  uploadDataForm.experimentId = null
  uploadDataForm.targetId = null
  uploadDataForm.targetType = null
  uploadDataForm.dataType = ''
  uploadDataForm.isSimulation = true
  uploadFiles.value = []
  if (uploadDataFormRef.value) {
    uploadDataFormRef.value.resetFields()
  }
}
const handleFileSelect = () => {
  // 文件选择后自动处理
}

/** 处理试验目标选择变化 - 获取对应的 targetType */
const handleTargetChange = (targetId) => {
  const target = targetTypeOptions.value.find(item => item.targetId === targetId)
  if (target) {
    uploadDataForm.targetType = target.targetType
  }
}
const cancelUpload = () => {
  importVisible.value = false
}
const submitUpload = async () => {
  // 校验表单
  if (!uploadDataFormRef.value) return
  await uploadDataFormRef.value.validate(async (valid) => {
    if (!valid) return
    
    if (uploadFiles.value.length === 0) {
        ElMessage.warning('请选择要上传的文件')
        return
    }

    try {
        for (const file of uploadFiles.value) {
            // 构建业务数据对象
            const businessData = {
                dataName: uploadDataForm.dataName || file.name,
                experimentId: uploadDataForm.experimentId,
                targetId: uploadDataForm.targetId,
                dataType: uploadDataForm.dataType,
                isSimulation: uploadDataForm.isSimulation
            }
            
            // 调用修改后的API，同时传递数据和文件
            await adddata(businessData, file.raw)
        }
        
        ElMessage.success('数据导入成功')
        importVisible.value = false
        await getList() // 自动查询业务数据列表
    } catch (err) {
        ElMessage.error('导入失败: ' + (err.message || '未知错误'))
    }
  })
}

const percent=ref(0)
const timer=ref(null)
/** 下载详情中的文件 */
const handleDownloadDetailFile = async (row) => {
  if(!row.experimentId || !row.dataFilePath){
    ElMessage.warning('缺少下载参数')
    return
  }
  try {
    const data = await downloadData({
      id: row?.id,
      experimentId: row?.experimentId,
      dataFilePath: row?.dataFilePath
    })
    if (blobValidate(data)) {
      const fileName = row.name || row.dataFilePath.substring(row.dataFilePath.lastIndexOf('/') + 1) || 'download'
      saveAs(new Blob([data]), fileName)
      return
    }
    const resText = await data.text()
    const rspObj = JSON.parse(resText)
    ElMessage.error(rspObj.msg || '下载失败')
  } catch (e) {
    ElMessage.error('下载失败: ' + (e.message || '未知错误'))
  }
}
// --- 文件管理器逻辑结束 ---

function transformTreeData(data) {
    if (!data || !Array.isArray(data)) return []
    
    return data.map(item => ({
        ...item,
        label: item.name, // 为树形组件添加label字段
        children: item.children && item.children.length > 0 
            ? transformTreeData(item.children) 
            : []
    }))
}

watch(name, val => {
    proxy.$refs["TreeRef"].filter(val)
})
function handleNodeClick(data) {
    if(data.type==="experiment"){
        queryParams.value.id=undefined
        queryParams.value.experimentName=data.label
        queryParams.value.projectId=data.parentId
    }
    else if(data.type==="project"){
        queryParams.value.id=undefined
        const project = projectOptions.value.find(item => item.projectId == data.id)
        queryParams.value.projectId = project ? project.projectId : data.id
        queryParams.value.experimentName=undefined
    }

    handleQuery()
}

function resetQuery() {
    proxy.resetForm("queryRef")
    dateRange.value = []
    advancedSearchExpanded.value = false
    queryParams.value = {
        id: null,
        pageNum: 1,
        pageSize: 10,
        dataName: undefined,
        experimentName: undefined,
        projectId: undefined,
        createBy: undefined,
        dataType: undefined,
        startTime: undefined,
        endTime: undefined,
        workStatus: undefined,
        dataCategory: undefined,
        createTime: undefined,
    }
    handleQuery()
}

// 取消按钮
function cancel() {
  open.value = false
  openExperiment.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    dataName: null,
    isSimulation: null,
    dataType: null,
    fileName: null,
    startTime: null,
    location: null,
    contentDesc: null
  }
  proxy.resetForm("dataRef")
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row ? [row.id] : ids.value
  proxy.$modal.confirm('是否确认删除ID为"' + _ids + '"的数据项？').then(function() {
    return deldata(_ids)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const id = row.id
  getdataDetail(id).then(response => {
    form.value = response.data
    form.value.id = id
    open.value = true
    title.value = "修改数据"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["dataRef"].validate(valid => {
    if (valid) {
      updatedata(form.value).then(response => {
        proxy.$modal.msgSuccess("修改成功")
        open.value = false
        getList()
      })
    }
  })
}

/** 详情按钮操作 (打开文件预览) */
function handleView(row) {
  if (row.dataFilePath) {
    detailFile.value = {
      id: row.id,
      name: row.dataName,
      path: row.dataFilePath,
      dataFilePath: row.dataFilePath,
      experimentId: row.experimentId
    }

    if (isTabularFile(row)) {
      const fileName = getPreviewFileName(row)
      if (fileName.endsWith('.csv')) {
        detailTitle.value = `CSV文件预览: ${row.dataName}`
      } else {
        detailTitle.value = `Excel文件预览: ${row.dataName}`
      }
      resetDetailTablePreview()
      detailVisible.value = true
      loadDetailTablePreview()
    } else {
      resetDetailTablePreview()
      detailTitle.value = `文件预览: ${row.dataName}`
      detailVisible.value = true
    }
  } else {
    ElMessage.warning("该数据没有关联的文件路径")
  }
}

function handleQuery(){
    getList()
}
function toggleAdvancedSearch() {
    advancedSearchExpanded.value = !advancedSearchExpanded.value
}
function getList(){
    loading.value = true
    getdataList(addDateRange(queryParams.value, dateRange.value)).then(response => {
      businessList.value = response.rows || (response.data && response.data.rows) || [];
      total.value = response.total || (response.data && response.data.total) || 0;
      loading.value = false;
    });
}
onMounted(()=>{
    getList()
    getTreeData()
    getProjects()
})

</script>
<style scoped>
/* 禁用分割面板初始加载时的宽度过渡，使分割符打开时直接位于正确位置 */
:deep(.splitpanes--vertical .splitpanes__pane) {
  transition: none;
}

.query-section {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
}

.query-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.query-form {
  flex: 1;
  min-width: 0;
}

.query-form :deep(.el-form-item) {
  margin-bottom: 12px;
  margin-right: 14px;
}

.query-control {
  width: 200px;
}

.query-date-item {
  width: 308px;
}

.query-date-control {
  width: 100%;
}

.query-actions {
  width: 178px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.query-action-group {
  display: inline-flex;
}

.query-expand-btn {
  padding: 0;
  font-size: 15px;
}

.toolbar-divider {
  margin: 20px 0;
  height: 1px;
  background: linear-gradient(90deg, rgba(144, 147, 153, 0), rgba(144, 147, 153, 0.32), rgba(144, 147, 153, 0));
}

.global-actions-row {
  margin-bottom: 12px;
}

.global-actions-row :deep(.right-toolbar) {
  margin-left: auto;
}

/* 数据导入弹窗 - 压缩尺寸 */
.import-dialog :deep(.el-dialog__body) {
  padding: 12px 20px;
  max-height: 70vh;
  overflow-y: auto;
}
.import-dialog-body {
  padding: 0;
}
.import-form {
  margin-bottom: 16px;
}
.import-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

/* 路径栏 - 路径框与按钮同一行、同高度对齐 */
.import-path-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.import-path-input {
  flex: 1;
  min-width: 0;
}
.import-path-bar :deep(.el-input-group__append) {
  padding: 0;
}
.import-path-bar :deep(.el-input-group__append .el-button) {
  margin: 0;
  border-radius: 0 4px 4px 0;
}
.import-path-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.import-file-table {
  margin-bottom: 16px;
}
.import-upload {
  margin-bottom: 0;
}

@media (max-width: 1200px) {
  .query-actions {
    width: 160px;
  }
}

@media (max-width: 992px) {
  .query-main {
    flex-direction: column;
    align-items: stretch;
  }

  .query-actions {
    width: 100%;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
  }
}

@media (max-width: 768px) {
  .query-form :deep(.el-form-item) {
    margin-right: 0;
    width: 100%;
  }

  .query-control,
  .query-date-item,
  .query-date-control {
    width: 100%;
  }

  .query-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
