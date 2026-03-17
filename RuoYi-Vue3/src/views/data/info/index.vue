<template>
  <div class="app-container">
    <div class="search-panel">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px" class="search-form">
      <el-form-item label="编号" prop="id">
        <el-input
          v-model="queryParams.id"
          placeholder="请输入编号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="创建人" prop="createBy">
        <el-input
          v-model="queryParams.createBy"
          placeholder="请输入创建人"
          clearable
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
        >
        </el-date-picker>
      </el-form-item>
      <el-form-item class="search-actions">
        <el-button type="primary" icon="Search" class="search-btn search-btn--primary" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" class="search-btn search-btn--default" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
    </div>

    <div class="table-panel">
    <el-row :gutter="10" class="mb8 table-toolbar">
      <el-col :span="1.5">
        <el-button
          type="primary"
          class="toolbar-btn toolbar-btn--primary"
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['data:info:add']"
        >新增项目</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          class="toolbar-btn toolbar-btn--primary"
          icon="Plus"
          @click="handleAddExperiment"
          v-hasPermi="['data:info:add']"
        >新增试验</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          class="toolbar-btn toolbar-btn--light"
          icon="Sort"
          @click="toggleExpandAll"
        >展开/折叠</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          class="toolbar-btn toolbar-btn--danger"
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete()"
          v-hasPermi="['data:info:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <div class="info-table-wrap">
    <el-table
      v-if="refreshTable"
      v-loading="loading"
      :data="infoList"
      ref="infoTableRef"
      row-key="id"
      :default-expand-all="isExpandAll"
      :tree-props="{children: 'children', hasChildren: 'hasChildren'}"
      :cell-style="cellStyle"
      @select="handleSelect"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="编号" align="center" prop="id" :show-overflow-tooltip="true" />
      <el-table-column label="试验名称" align="center" prop="name" :show-overflow-tooltip="true" />
      <el-table-column label="目标" align="center" prop="targetType" :show-overflow-tooltip="true" />
      <el-table-column label="日期" align="center" prop="startTime" width="180">
        <template #default="scope">
          <span>{{ formatStartTime(scope.row.startTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="地点" align="center" prop="location" :show-overflow-tooltip="true" />
      <el-table-column label="内容描述" align="center" prop="contentDesc" :show-overflow-tooltip="true" />
      <el-table-column label="创建人" align="center" prop="createBy" :show-overflow-tooltip="true" />
      <el-table-column label="数据类型" align="center" prop="type" :show-overflow-tooltip="true">
        <template #default="scope">
          <span>{{ scope.row.type === 'project' ? '项目' : '试验' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="路径" align="center" prop="path" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" width="210">
        <template #default="scope">
          <div class="action-group">
            <el-tooltip content="详情" placement="top">
              <el-button size="small" class="action-btn action-btn--primary" @click="handleView(scope.row)" v-hasPermi="['data:info:query']">详情</el-button>
            </el-tooltip>
            <el-tooltip content="修改" placement="top">
              <el-button size="small" class="action-btn action-btn--muted" @click="handleUpdate(scope.row)" v-hasPermi="['data:info:edit']">编辑</el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button size="small" class="action-btn action-btn--danger" @click="handleDelete(scope.row)" v-hasPermi="['data:info:remove']">删除</el-button>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>
    </div>
    </div>

    <!-- 添加或修改试验信息主对话框 -->
    <el-dialog
      :title="title"
      v-model="open"
      width="560px"
      class="ant-form-dialog"
      :close-on-click-modal="!submitLoading"
      :close-on-press-escape="!submitLoading"
      append-to-body
    >
      <el-form ref="infoRef" :model="form" :rules="rules" label-width="84px" class="ant-form-layout">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称"/>
          <template #error="{ error }">
            <transition name="form-error-slide">
              <div v-if="error" class="form-error-tip">{{ error }}</div>
            </transition>
          </template>
        </el-form-item>
        <el-form-item label="所属项目" prop="parentId" v-if="form.type === 'experiment'">
          <el-select v-model="form.parentId" placeholder="请选择所属项目" filterable clearable>
            <el-option
              v-for="item in projectOptions"
              :key="item.projectId"
              :label="item.projectName"
              :value="item.projectId"
            />
          </el-select>
          <template #error="{ error }">
            <transition name="form-error-slide">
              <div v-if="error" class="form-error-tip">{{ error }}</div>
            </transition>
          </template>
        </el-form-item>
       <el-form-item label="试验目标" prop="targetId" v-if="form.type === 'experiment'">
          <el-select v-model="form.targetId" placeholder="请选择试验目标">
            <el-option
              v-for="item in targetTypeOptions"
              :key="item.targetId"
              :label="item.targetType"
              :value="item.targetId"
            />
          </el-select>
          <template #error="{ error }">
            <transition name="form-error-slide">
              <div v-if="error" class="form-error-tip">{{ error }}</div>
            </transition>
          </template>
        </el-form-item>
        <el-form-item label="试验日期" prop="startTime" v-if="form.type === 'experiment'">
          <el-date-picker clearable
            v-model="form.startTime"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择开始日期">
          </el-date-picker>
          <template #error="{ error }">
            <transition name="form-error-slide">
              <div v-if="error" class="form-error-tip">{{ error }}</div>
            </transition>
          </template>
        </el-form-item>
        <el-form-item label="试验地点" prop="location" v-if="form.type === 'experiment'">
          <el-input v-model="form.location" placeholder="请输入地点" />
          <template #error="{ error }">
            <transition name="form-error-slide">
              <div v-if="error" class="form-error-tip">{{ error }}</div>
            </transition>
          </template>
        </el-form-item>
        <el-form-item label="内容描述" prop="contentDesc">
          <el-input
            v-model="form.contentDesc"
            type="textarea"
            :maxlength="200"
            show-word-limit
            :autosize="{ minRows: 4, maxRows: 6 }"
            placeholder="请输入内容"
          />
          <template #error="{ error }">
            <transition name="form-error-slide">
              <div v-if="error" class="form-error-tip">{{ error }}</div>
            </transition>
          </template>
        </el-form-item>
        <el-form-item label="创建人" prop="createBy" v-if="form.createBy">
          <el-input :model-value="form.createBy" disabled />
        </el-form-item>
        <el-form-item label="创建时间" prop="createTime" v-if="form.createTime">
          <el-date-picker
            v-model="form.createTime"
            type="datetime" 
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            readonly
            placeholder="无时间数据">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="路径" prop="path">
          <el-input
            v-model="form.path"
            :placeholder="isAdd ? '系统自动生成路径' : '请输入路径'"
            :disabled="isAdd"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" class="ant-confirm-btn" :loading="submitLoading" @click="submitForm">确 定</el-button>
          <el-button class="ant-cancel-btn" :disabled="submitLoading" @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="openView"
      direction="rtl"
      size="520px"
      class="info-detail-drawer"
      append-to-body
      :with-header="false"
    >
      <div class="detail-drawer">
        <div class="detail-drawer__header">
          <div class="detail-drawer__title-row">
            <h3 class="detail-drawer__title">详情信息</h3>
            <el-tag class="detail-status-tag" :type="getDetailStatusType()" effect="dark">
              {{ getDetailStatusText() }}
            </el-tag>
          </div>
          <p class="detail-drawer__id">编号：{{ form.id || '--' }}</p>
        </div>

        <transition name="drawer-content-fade" appear>
          <div v-if="openView" class="detail-drawer__body">
            <el-card class="detail-card" shadow="hover">
              <template #header>
                <div class="detail-card__header">基础信息</div>
              </template>
              <div class="detail-grid">
                <div class="detail-item">
                  <span class="detail-label">名称</span>
                  <span class="detail-value">{{ form.name || '--' }}</span>
                </div>
                <div class="detail-item" v-if="form.targetType">
                  <span class="detail-label">目标</span>
                  <span class="detail-value">{{ form.targetType }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">类型</span>
                  <span class="detail-value">{{ getTypeLabel(form.type) }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">地点</span>
                  <span class="detail-value">{{ form.location || '--' }}</span>
                </div>
              </div>
            </el-card>

            <el-card class="detail-card" shadow="hover">
              <template #header>
                <div class="detail-card__header">时间与人员</div>
              </template>
              <div class="detail-grid">
                <div class="detail-item">
                  <span class="detail-label">时间</span>
                  <span class="detail-value">{{ formatStartTime(form.startTime) || '--' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">创建时间</span>
                  <span class="detail-value">{{ form.createTime || '--' }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">创建人</span>
                  <span class="detail-value">{{ form.createBy || '暂无创建人' }}</span>
                </div>
              </div>
            </el-card>

            <el-card class="detail-card" shadow="hover">
              <template #header>
                <div class="detail-card__header">附加信息</div>
              </template>
              <div class="detail-stack">
                <div class="detail-item detail-item--column">
                  <span class="detail-label">路径</span>
                  <span class="detail-value">{{ form.path || '--' }}</span>
                </div>
                <div class="detail-item detail-item--column">
                  <span class="detail-label">内容描述</span>
                  <span class="detail-value">{{ form.contentDesc || '--' }}</span>
                </div>
              </div>
            </el-card>
          </div>
        </transition>

        <div class="detail-drawer__footer">
          <el-button @click="openView = false">关 闭</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup name="Info">
import { listInfo, getInfo, delInfo, addInfo, updateInfo} from "@/api/data/info"
import { addDateRange } from "@/utils/ruoyi"

const { proxy } = getCurrentInstance()

const infoList = ref([])
const projectOptions = ref([])
const targetTypeOptions = ref([])
const open = ref(false)
const openView = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const title = ref("")
const isExpandAll = ref(true)
const refreshTable = ref(true)
const dateRange = ref([])
const infoTableRef = ref(null)
const multiple = ref(true)
const selectedRows = ref([])
const submitLoading = ref(false)
const isAdd = computed(() => {
  return title.value === '添加项目' || title.value === '添加试验';
});
const data = reactive({
  form: {},
  queryParams: {
    id: null,
    name: null,
    createBy:null,
    createTime: null,
  },
  rules: {
    id: [
      { required: true, message: "编号不能为空", trigger: "blur" }
    ],
    name: [
      { required: true, message: "名称不能为空", trigger: "blur" }
    ],
    parentId: [
      { required: true, message: "所属项目不能为空", trigger: "change" }
    ],
    startTime: [
      { required: true, message: "时间不能为空", trigger: "blur" }
    ],
    targetId: [
      { required: true, message: "试验目标不能为空", trigger: "change" }
    ],
    location: [
      { required: true, message: "地点不能为空", trigger: "blur" }
    ],
    contentDesc: [
      { required: true, message: "内容描述不能为空", trigger: "blur" }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询试验信息树形表 */
function getList() {
  loading.value = true
  listInfo(addDateRange(queryParams.value, dateRange.value)).then(response => {
    infoList.value = response.data
    loading.value = false
  })
}
	
// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    name: null,
    startTime: null,
    contentDesc: null,
    createTime: null,
    location: null,
    targetType: null,
    targetId: null,
    type: 'project',
    parentId: 0,
    path: null
  }
  proxy.resetForm("infoRef")
}

/** 样式控制 */
function cellStyle({ row, column, rowIndex, columnIndex }) {
  if (column.property === 'id') {
    return { textAlign: row.type === 'project' ? 'left' : 'center' };
  }
}

/** 详情抽屉展示文案 */
function getTypeLabel(type) {
  if (type === 'project') return '项目'
  if (type === 'experiment') return '试验'
  return type || '--'
}

function getDetailStatusText() {
  return form.value?.createTime ? '已处理' : '待处理'
}

function getDetailStatusType() {
  return form.value?.createTime ? 'success' : 'warning'
}

/** 搜索按钮操作 */
function handleQuery() {
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 新增按钮操作 */
async function handleAdd(row) {
  reset()
  if (row != null && row.id) {
    // Adding child to a project -> Experiment
    form.value.parentId = row.id
    form.value.type = 'experiment'
  } else {
    form.value.parentId = 0
    form.value.type = 'project'
    title.value = "添加项目"
  }
  open.value = true
}

/** 新增试验按钮操作 */
async function handleAddExperiment() {
  reset()
  getInfo(null, 'experiment').then(response => {
    projectOptions.value = response.projects || []
    targetTypeOptions.value = response.targetTypes || []
  })
  form.value.type = 'experiment'
  form.value.parentId = null  
  title.value = "添加试验"
  open.value = true
}

/** 展开/折叠操作 */
function toggleExpandAll() {
  refreshTable.value = false
  isExpandAll.value = !isExpandAll.value
  nextTick(() => {
    refreshTable.value = true
  })
}

/** 修改按钮操作 */
async function handleUpdate(row) {
  reset()
  getInfo(row.id, row.type).then(response => {
    const resData = response.data
    projectOptions.value = response.projects || []
    targetTypeOptions.value = response.targetTypes || []
    if (row.type === 'project') {
      form.value = {
        id: resData.projectId,
        name: resData.projectName,
        startTime: parseCstTime(resData.startTime || row.startTime),
        createTime: resData.createTime,
        location: resData.location,
        contentDesc: resData.projectContentDesc,
        type: row.type,
        parentId: row.parentId,
        path: resData.path,
        createBy: resData.createBy,
        projectId: resData.projectId,   
        targetType: resData.targetType
      }
    } else {
      form.value = {
        id: resData.experimentId,
        name: resData.experimentName,
        startTime: parseCstTime(resData.startTime || row.startTime),
        createTime: resData.createTime,
        location: resData.location,
        contentDesc: resData.contentDesc,
        type: row.type,
        parentId: row.parentId,
        targetId: resData.targetId,
        path: resData.path,
        createBy: resData.createBy
      }
    }
    open.value = true
    title.value = "修改"+row.name
  })
}

/** 详情按钮操作 */
function handleView(row) {
  reset()
  getInfo(row.id, row.type).then(response => {
    const resData = response.data
    if (row.type === 'project') {
      form.value = {
        id: resData.projectId,
        name: resData.projectName,
        createTime: proxy.parseTime(resData.createTime),
        contentDesc: resData.projectContentDesc,
        type: row.type,
        path: './data' + (resData.path || ''),
        location: resData.location,
        startTime: parseCstTime(resData.startTime || row.startTime),
        createBy: resData.createBy
      }
    } else {
      let parentName = ''
      if (infoList.value) {
        const parent = infoList.value.find(p => p.id == row.parentId)
        if (parent) {
          parentName = parent.name
        }
      }
      form.value = {
        id: resData.experimentId,
        name: resData.experimentName,
        createTime: proxy.parseTime(resData.createTime),
        contentDesc: resData.contentDesc,
        type: row.type,
        path: './data/' + parentName + (resData.path || ''),
        location: resData.location,
        startTime: parseCstTime(resData.startTime || row.startTime),
        targetType: resData.targetType,
        createBy: resData.createBy
      }
    }
    openView.value = true
  })
}

/** 提交按钮 */
function submitForm() {
  if (submitLoading.value) return
  proxy.$refs["infoRef"].validate(async valid => {
    if (!valid) return
    submitLoading.value = true
    try {
      // 模拟确认按钮的加载反馈
      await new Promise(resolve => setTimeout(resolve, 500))

      // 在提交前格式化startTime为 yyyy-MM-dd 格式
      const submitData = JSON.parse(JSON.stringify(form.value))
      if (submitData.startTime) {
        submitData.startTime = formatDateForSubmit(submitData.startTime)
      }

      if (submitData.id != null && title.value.startsWith("修改")) {
        await updateInfo(submitData)
        proxy.$modal.msgSuccess("修改成功")
      } else {
        await addInfo(submitData)
        proxy.$modal.msgSuccess("新增成功")
      }
      open.value = false
      getList()
    } finally {
      submitLoading.value = false
    }
  })
}

/** 选中处理 */
function handleSelectionChange(selection) {
  selectedRows.value = selection
  multiple.value = !selection.length
}

/** 手动勾选父节点时，自动勾选/取消勾选子节点 */
function handleSelect(selection, row) {
  const isSelected = selection.some(item => item.id === row.id)
  if (row.children && row.children.length > 0) {
    toggleChildrenSelection(row.children, isSelected)
  }
}

function toggleChildrenSelection(children, isSelected) {
  children.forEach(child => {
    infoTableRef.value.toggleRowSelection(child, isSelected)
    if (child.children && child.children.length > 0) {
      toggleChildrenSelection(child.children, isSelected)
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const projectIds = []
  const experimentIds = []
  
  if (row) {
    // 单个删除
    if (row.type === 'project') {
      projectIds.push(row.id)
      // 如果是项目，自动包含其下所有试验数据
      if (row.children && row.children.length > 0) {
        const collectExperimentIds = (nodes) => {
          nodes.forEach(node => {
            if (node.type === 'experiment') experimentIds.push(node.id)
            if (node.children) collectExperimentIds(node.children)
          })
        }
        collectExperimentIds(row.children)
      }
    } else {
      experimentIds.push(row.id)
    }
  } else {
    // 批量删除
    selectedRows.value.forEach(item => {
      if (item.type === 'project') projectIds.push(item.id)
      else if (item.type === 'experiment') experimentIds.push(item.id)
    })
  }

  if (projectIds.length === 0 && experimentIds.length === 0) return

  proxy.$modal.confirm('是否确认删除选中的数据项？').then(function() {
    return delInfo(experimentIds.join(',') || 0, projectIds.join(',') || 0)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 格式化日期用于提交 - 转换为 yyyy-MM-dd 格式 */
function formatDateForSubmit(date) {
  if (!date) return null
  
  // 如果已经是字符串格式
  if (typeof date === 'string') {
    // 如果已经是 yyyy-MM-dd 格式，直接返回
    if (/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      return date
    }
    // 如果是CST格式，转换为 yyyy-MM-dd
    if (date.includes('CST')) {
      const parts = date.split(/\s+/).filter(p => p)
      if (parts.length >= 6) {
        const monthMap = {
          'Jan': '01', 'Feb': '02', 'Mar': '03', 'Apr': '04', 'May': '05', 'Jun': '06',
          'Jul': '07', 'Aug': '08', 'Sep': '09', 'Oct': '10', 'Nov': '11', 'Dec': '12'
        }
        const month = monthMap[parts[1]]
        const day = parts[2]
        const year = parts[5]
        if (month && day && year) {
          return `${year}-${month}-${day.padStart(2, '0')}`
        }
      }
    }
  }
  
  // 如果是Date对象
  if (date instanceof Date) {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }
  
  return date
}

/** 解析CST时间格式 */
function parseCstTime(time) {
  if (!time) return null
  const timeStr = String(time)
  // 处理 Java Date.toString() 格式: "Fri Jan 09 00:00:00 CST 2026"
  if (timeStr.includes('CST')) {
    // 使用正则表达式匹配 CST 格式
    const match = timeStr.match(/(\w+)\s+(\w+)\s+(\d+)\s+\d{2}:\d{2}:\d{2}\s+\w+\s+(\d{4})/)
    if (match) {
      const monthMap = {
        Jan: '01', Feb: '02', Mar: '03', Apr: '04', May: '05', Jun: '06',
        Jul: '07', Aug: '08', Sep: '09', Oct: '10', Nov: '11', Dec: '12'
      }
      const month = monthMap[match[2]]
      let day = match[3]
      const year = match[4]
      
      if (month) {
        day = day.padStart(2, '0')
        return `${year}-${month}-${day}`
      }
    }
  }
  return proxy.parseTime(time, '{y}-{m}-{d}')
}

/** 格式化startTime为 YYYY-MM-DD 格式 */
function formatStartTime(time) {
  if (!time) return ''
  const timeStr = String(time).trim()
  
  // 如果已经是 YYYY-MM-DD 格式，直接返回
  if (/^\d{4}-\d{2}-\d{2}$/.test(timeStr)) {
    return timeStr
  }
  
  // 处理 Java Date.toString() 格式: "Thu Jan 01 00:00:00 CST 2026"
  if (timeStr.includes('CST')) {
    const parts = timeStr.split(/\s+/).filter(p => p)
    
    if (parts.length >= 6) {
      const monthMap = {
        'Jan': '01', 'Feb': '02', 'Mar': '03', 'Apr': '04', 'May': '05', 'Jun': '06',
        'Jul': '07', 'Aug': '08', 'Sep': '09', 'Oct': '10', 'Nov': '11', 'Dec': '12'
      }
      const month = monthMap[parts[1]]
      const day = parts[2]
      const year = parts[5]
      
      if (month && day && year) {
        const dayStr = day.padStart(2, '0')
        return `${year}-${month}-${dayStr}`
      }
    }
  }
  
  // 尝试用 new Date 解析
  try {
    const date = new Date(timeStr)
    if (!isNaN(date.getTime())) {
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      return `${year}-${month}-${day}`
    }
  } catch (e) {
    // 解析失败，继续尝试
  }
  
  // 其他格式使用 parseTime
  try {
    return proxy.parseTime(time, '{y}-{m}-{d}')
  } catch (e) {
    return timeStr
  }
}

getList()
</script>

<style scoped>
.app-container {
  padding: 12px;
  background: #edf2f8;
  position: relative;
  isolation: isolate;
  overflow: hidden;
}

.app-container > * {
  position: relative;
  z-index: 1;
}

.app-container::before,
.app-container::after {
  content: "";
  position: absolute;
  top: 6px;
  bottom: 6px;
  pointer-events: none;
  z-index: 0;
  opacity: 0.4;
  will-change: background-position, opacity, transform;
}

.app-container::before {
  left: -12px;
  width: 178px;
  background-image:
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 180 240'%3E%3Cpath d='M22 0 C28 20 16 40 22 60 C28 80 16 100 22 120 C28 140 16 160 22 180 C28 200 16 220 22 240' fill='none' stroke='%231890ff' stroke-opacity='0.22' stroke-width='0.8'/%3E%3Cpath d='M34 0 C40 20 28 40 34 60 C40 80 28 100 34 120 C40 140 28 160 34 180 C40 200 28 220 34 240' fill='none' stroke='%231890ff' stroke-opacity='0.16' stroke-width='0.7'/%3E%3Cpath d='M46 0 C52 20 40 40 46 60 C52 80 40 100 46 120 C52 140 40 160 46 180 C52 200 40 220 46 240' fill='none' stroke='%231890ff' stroke-opacity='0.12' stroke-width='0.65'/%3E%3Cpath d='M58 0 C64 20 52 40 58 60 C64 80 52 100 58 120 C64 140 52 160 58 180 C64 200 52 220 58 240' fill='none' stroke='%231890ff' stroke-opacity='0.1' stroke-width='0.6'/%3E%3C/svg%3E"),
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 180 280'%3E%3Cpath d='M14 0 C22 26 8 52 14 78 C22 104 8 130 14 156 C22 182 8 208 14 234 C22 256 8 268 14 280' fill='none' stroke='%231890ff' stroke-opacity='0.08' stroke-width='0.8'/%3E%3Cpath d='M74 0 C82 26 68 52 74 78 C82 104 68 130 74 156 C82 182 68 208 74 234 C82 256 68 268 74 280' fill='none' stroke='%231890ff' stroke-opacity='0.06' stroke-width='0.75'/%3E%3C/svg%3E"),
    linear-gradient(90deg, rgba(24, 144, 255, 0.06) 0%, rgba(24, 144, 255, 0.02) 65%, rgba(24, 144, 255, 0) 100%);
  background-repeat: repeat-y, repeat-y, no-repeat;
  background-size: 178px 240px, 178px 280px, 100% 100%;
  background-position: 0 0, 0 120px, 0 0;
  -webkit-mask-image: linear-gradient(90deg, #000 0%, #000 62%, transparent 100%);
  mask-image: linear-gradient(90deg, #000 0%, #000 62%, transparent 100%);
  animation: flowEdgeLeft 60s linear infinite;
}

.app-container::after {
  right: -14px;
  width: 196px;
  background-image:
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 240'%3E%3Cpath d='M178 0 C172 20 184 40 178 60 C172 80 184 100 178 120 C172 140 184 160 178 180 C172 200 184 220 178 240' fill='none' stroke='%231890ff' stroke-opacity='0.22' stroke-width='0.8'/%3E%3Cpath d='M166 0 C160 20 172 40 166 60 C160 80 172 100 166 120 C160 140 172 160 166 180 C160 200 172 220 166 240' fill='none' stroke='%231890ff' stroke-opacity='0.16' stroke-width='0.7'/%3E%3Cpath d='M154 0 C148 20 160 40 154 60 C148 80 160 100 154 120 C148 140 160 160 154 180 C148 200 160 220 154 240' fill='none' stroke='%231890ff' stroke-opacity='0.12' stroke-width='0.65'/%3E%3Cpath d='M142 0 C136 20 148 40 142 60 C136 80 148 100 142 120 C136 140 148 160 142 180 C136 200 148 220 142 240' fill='none' stroke='%231890ff' stroke-opacity='0.1' stroke-width='0.6'/%3E%3C/svg%3E"),
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 280'%3E%3Cpath d='M188 0 C180 26 194 52 188 78 C180 104 194 130 188 156 C180 182 194 208 188 234 C180 256 194 268 188 280' fill='none' stroke='%231890ff' stroke-opacity='0.08' stroke-width='0.8'/%3E%3Cpath d='M126 0 C118 26 132 52 126 78 C118 104 132 130 126 156 C118 182 132 208 126 234 C118 256 132 268 126 280' fill='none' stroke='%231890ff' stroke-opacity='0.06' stroke-width='0.75'/%3E%3C/svg%3E"),
    linear-gradient(270deg, rgba(24, 144, 255, 0.06) 0%, rgba(24, 144, 255, 0.02) 65%, rgba(24, 144, 255, 0) 100%);
  background-repeat: repeat-y, repeat-y, no-repeat;
  background-size: 196px 240px, 196px 280px, 100% 100%;
  background-position: 0 0, 0 90px, 0 0;
  -webkit-mask-image: linear-gradient(270deg, #000 0%, #000 62%, transparent 100%);
  mask-image: linear-gradient(270deg, #000 0%, #000 62%, transparent 100%);
  animation: flowEdgeRight 60s linear infinite;
}

@keyframes flowEdgeLeft {
  0% {
    background-position: 0 0, 0 120px, 0 0;
    opacity: 0.32;
    transform: translate3d(0, 0, 0);
  }
  50% {
    background-position: 0 140px, 0 20px, 0 0;
    opacity: 0.5;
    transform: translate3d(2px, 0, 0);
  }
  100% {
    background-position: 0 280px, 0 -80px, 0 0;
    opacity: 0.32;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes flowEdgeRight {
  0% {
    background-position: 0 0, 0 90px, 0 0;
    opacity: 0.3;
    transform: translate3d(0, 0, 0);
  }
  50% {
    background-position: 0 -130px, 0 180px, 0 0;
    opacity: 0.48;
    transform: translate3d(-2px, 0, 0);
  }
  100% {
    background-position: 0 -260px, 0 270px, 0 0;
    opacity: 0.3;
    transform: translate3d(0, 0, 0);
  }
}

.search-panel,
.table-panel {
  background: #fff;
  border: 1px solid #e6ebf2;
  border-radius: 2px;
}

.search-panel {
  padding: 14px 16px 8px;
  margin-bottom: 12px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 8px;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.search-form :deep(.el-form-item__label) {
  color: #606a78;
  font-weight: 500;
}

.search-form :deep(.el-input__wrapper),
.search-form :deep(.el-date-editor.el-input__wrapper) {
  box-shadow: 0 0 0 1px #dfe5ee inset;
  border-radius: 2px;
}

.search-form :deep(.el-input__wrapper.is-focus),
.search-form :deep(.el-date-editor.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #1890ff inset;
}

.search-form :deep(.el-date-editor) {
  width: 308px;
}

.search-actions {
  padding-left: 12px;
}

.search-btn {
  border-radius: 2px;
  min-width: 72px;
}

.search-btn--primary {
  border-color: #1890ff;
  background: #1890ff;
}

.search-btn--default {
  color: #717b8b;
  border-color: #cfd7e3;
  background: #f1f4f8;
}

.search-btn--default:hover,
.search-btn--default:focus {
  color: #5f6978;
  border-color: #bcc7d6;
  background: #e8edf4;
}

.table-panel {
  padding: 12px 12px 8px;
}

.table-toolbar {
  margin-bottom: 10px;
}

.table-toolbar :deep(.el-col) {
  max-width: none;
}

.toolbar-btn {
  min-width: 82px;
  border-radius: 2px;
}

.toolbar-btn--primary {
  color: #fff;
  border-color: #1890ff;
  background: #1890ff;
}

.toolbar-btn--primary:hover,
.toolbar-btn--primary:focus {
  color: #fff;
  border-color: #40a9ff;
  background: #40a9ff;
}

.toolbar-btn--light {
  color: #5f6978;
  border-color: #d7dee9;
  background: #f7f9fc;
}

.toolbar-btn--light:hover,
.toolbar-btn--light:focus {
  color: #3d4756;
  border-color: #c5cedb;
  background: #eff3f8;
}

.toolbar-btn--danger {
  color: #5f6978;
  border-color: #d7dee9;
  background: #f7f9fc;
}

.toolbar-btn--danger:hover,
.toolbar-btn--danger:focus {
  color: #ef4444;
  border-color: #ef4444;
  background: #fff7f7;
}

.info-table-wrap {
  margin-bottom: 6px;
}

.info-table-wrap :deep(.el-table) {
  border: 1px solid #e6ebf2;
  border-radius: 2px;
}

.info-table-wrap :deep(.el-table__header th.el-table__cell) {
  height: 40px;
  color: #525f70;
  font-weight: 600;
  background: #f4f7fb;
}

.info-table-wrap :deep(.el-table .el-table__cell) {
  border-bottom-color: #edf1f6;
}

.info-table-wrap :deep(.el-table__row td.el-table__cell) {
  height: 42px;
  color: #586375;
}

.info-table-wrap :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: #f7fbff;
}

.info-table-wrap :deep(.el-table__body tr.current-row > td.el-table__cell) {
  background: #eef5ff;
}

.action-btn {
  min-width: 52px;
  padding: 2px 12px;
  font-size: 12px;
  border-radius: 2px;
}

.action-group {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px 10px;
}

.action-btn--primary {
  color: #fff;
  border-color: #1890ff;
  background: #1890ff;
}

.action-btn--primary:hover,
.action-btn--primary:focus {
  color: #fff;
  border-color: #40a9ff;
  background: #40a9ff;
}

.ant-form-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(208, 213, 221, 0.8);
  background: linear-gradient(180deg, #fcfdff 0%, #f8fafc 100%);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08), 0 4px 12px rgba(15, 23, 42, 0.04);
}

.ant-form-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 24px 28px 14px;
  border-bottom: 1px solid #eaecf0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.92) 100%);
}

.ant-form-dialog :deep(.el-dialog__title) {
  color: #1f2937;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.ant-form-dialog :deep(.el-dialog__body) {
  padding: 22px 28px 10px;
  background: transparent;
}

.ant-form-dialog :deep(.el-dialog__footer) {
  padding: 12px 28px 24px;
  border-top: none;
  background: transparent;
}

.ant-form-layout :deep(.el-form-item) {
  margin-bottom: 20px;
}

.ant-form-layout :deep(.el-form-item__label) {
  color: #667085;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.ant-form-layout :deep(.el-input__wrapper),
.ant-form-layout :deep(.el-textarea__inner),
.ant-form-layout :deep(.el-select__wrapper),
.ant-form-layout :deep(.el-date-editor.el-input__wrapper) {
  min-height: 42px;
  border-radius: 10px;
  color: #111827;
  background: #f9fafb;
  box-shadow: 0 0 0 1px #d0d5dd inset;
  transition: box-shadow 0.2s ease, background-color 0.2s ease, border-color 0.2s ease;
}

.ant-form-layout :deep(.el-input__inner),
.ant-form-layout :deep(.el-textarea__inner),
.ant-form-layout :deep(.el-select__selected-item),
.ant-form-layout :deep(.el-date-editor .el-input__inner) {
  color: #111827;
}

.ant-form-layout :deep(.el-input__inner::placeholder),
.ant-form-layout :deep(.el-textarea__inner::placeholder) {
  color: #98a2b3;
}

.ant-form-layout :deep(.el-select__placeholder),
.ant-form-layout :deep(.el-range-input::placeholder) {
  color: #98a2b3;
}

.ant-form-layout :deep(.el-input__prefix),
.ant-form-layout :deep(.el-input__suffix),
.ant-form-layout :deep(.el-select__caret),
.ant-form-layout :deep(.el-date-editor .el-input__prefix) {
  color: #98a2b3;
}

.ant-form-layout :deep(.el-input__wrapper.is-focus),
.ant-form-layout :deep(.el-select__wrapper.is-focused),
.ant-form-layout :deep(.el-date-editor.el-input__wrapper.is-focus),
.ant-form-layout :deep(.el-textarea__inner:focus) {
  background: #ffffff;
  box-shadow: 0 0 0 1px #3b82f6 inset, 0 0 0 3px rgba(59, 130, 246, 0.14);
}

.ant-form-layout :deep(.el-textarea__inner) {
  min-height: 112px;
  padding: 12px 14px 24px;
  background: #f9fafb;
}

.ant-form-layout :deep(.el-input__count) {
  right: 10px;
  bottom: 6px;
  color: #98a2b3;
  background: transparent;
}

.ant-form-layout :deep(.el-input.is-disabled .el-input__wrapper),
.ant-form-layout :deep(.el-textarea.is-disabled .el-textarea__inner),
.ant-form-layout :deep(.el-date-editor.is-disabled) {
  background: #f2f4f7;
  box-shadow: 0 0 0 1px #d0d5dd inset;
}

.ant-form-layout :deep(.el-input.is-disabled .el-input__inner),
.ant-form-layout :deep(.el-textarea.is-disabled .el-textarea__inner),
.ant-form-layout :deep(.el-input.is-disabled .el-input__inner::placeholder) {
  color: #98a2b3;
  -webkit-text-fill-color: #98a2b3;
}

.ant-form-layout :deep(.el-form-item.is-error .el-input__wrapper),
.ant-form-layout :deep(.el-form-item.is-error .el-select__wrapper),
.ant-form-layout :deep(.el-form-item.is-error .el-date-editor.el-input__wrapper),
.ant-form-layout :deep(.el-form-item.is-error .el-textarea__inner) {
  background: #fffefe;
  box-shadow: 0 0 0 1px #f04438 inset, 0 0 0 3px rgba(240, 68, 56, 0.1);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 6px;
}

.ant-confirm-btn {
  min-width: 116px;
  height: 42px;
  border: none;
  border-radius: 10px;
  font-weight: 600;
  color: #fff;
  background: #3b82f6;
  box-shadow: 0 8px 18px rgba(59, 130, 246, 0.2);
  transition: background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.ant-confirm-btn:hover,
.ant-confirm-btn:focus {
  color: #fff;
  background: #2563eb;
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.24);
  transform: translateY(-1px);
}

.ant-cancel-btn {
  min-width: 100px;
  height: 42px;
  border: 1px solid #d0d5dd;
  border-radius: 10px;
  color: #475467;
  background: #ffffff;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.ant-cancel-btn:hover,
.ant-cancel-btn:focus {
  color: #344054;
  border-color: #c1c7d0;
  background: #f9fafb;
}

.form-error-tip {
  margin-top: 4px;
  color: #f04438;
  font-size: 12px;
  line-height: 1.2;
}

.form-error-slide-enter-active,
.form-error-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.form-error-slide-enter-from,
.form-error-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.action-btn--muted {
  color: #717b8b;
  border-color: #cfd7e3;
  background: #f1f4f8;
}

.action-btn--muted:hover,
.action-btn--muted:focus {
  color: #5f6978;
  border-color: #bcc7d6;
  background: #e8edf4;
}

.action-btn--danger {
  color: #5f6978;
  border-color: #d7dee9;
  background: #f7f9fc;
}

.action-btn--danger:hover,
.action-btn--danger:focus {
  color: #ef4444;
  border-color: #ef4444;
  background: #fff7f7;
}

.info-detail-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.detail-drawer {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f4f7fb;
}

.detail-drawer__header {
  padding: 20px 22px 16px;
  border-bottom: 1px solid #e8edf5;
  background: #fff;
}

.detail-drawer__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-drawer__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #2f3a4a;
}

.detail-status-tag {
  flex-shrink: 0;
}

.detail-drawer__id {
  margin: 10px 0 0;
  color: #5c6778;
  font-size: 14px;
}

.detail-drawer__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-card {
  border: 1px solid #e5ebf3;
}

.detail-card :deep(.el-card__header) {
  padding: 12px 16px;
  border-bottom: 1px solid #edf1f6;
}

.detail-card :deep(.el-card__body) {
  padding: 16px;
}

.detail-card__header {
  font-size: 14px;
  font-weight: 600;
  color: #3d4a5d;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 16px;
}

.detail-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  line-height: 1.6;
}

.detail-item--column {
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  min-width: 72px;
  color: #8a94a6;
  font-size: 13px;
}

.detail-value {
  color: #2f3a4a;
  font-size: 14px;
  word-break: break-all;
}

.detail-drawer__footer {
  padding: 14px 20px;
  border-top: 1px solid #e8edf5;
  background: #fff;
  display: flex;
  justify-content: flex-end;
}

.drawer-content-fade-enter-active,
.drawer-content-fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.drawer-content-fade-enter-from,
.drawer-content-fade-leave-to {
  opacity: 0;
  transform: translateX(10px);
}

:deep(.right-toolbar) {
  margin-left: auto;
}

@media (max-width: 768px) {
  .app-container::before,
  .app-container::after {
    content: none;
  }

  .app-container {
    padding: 8px;
  }

  .search-form :deep(.el-form-item) {
    width: 100%;
  }

  .search-actions {
    padding-left: 0;
  }

  .table-toolbar :deep(.el-col) {
    flex: 0 0 50%;
    max-width: 50%;
    margin-bottom: 8px;
  }

  .detail-drawer__header {
    padding: 16px 16px 12px;
  }

  .detail-drawer__body {
    padding: 14px;
    gap: 14px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
