<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
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
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['data:info:add']"
        >新增项目</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAddExperiment"
          v-hasPermi="['data:info:add']"
        >新增试验</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="Sort"
          @click="toggleExpandAll"
        >展开/折叠</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete()"
          v-hasPermi="['data:info:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

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
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-tooltip content="详情" placement="top">
            <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['data:info:query']"></el-button>
          </el-tooltip>
          <el-tooltip content="修改" placement="top">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['data:info:edit']"></el-button>
          </el-tooltip>
          <el-tooltip content="删除" placement="top">
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['data:info:remove']"></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加或修改试验信息主对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="infoRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入名称"/>
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
        </el-form-item>
        <el-form-item label="试验日期" prop="startTime" v-if="form.type === 'experiment'">
          <el-date-picker clearable
            v-model="form.startTime"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择开始日期">
          </el-date-picker>
        </el-form-item>
        <el-form-item label="试验地点" prop="location" v-if="form.type === 'experiment'">
          <el-input v-model="form.location" placeholder="请输入地点" />
        </el-form-item>
        <el-form-item label="内容描述" prop="contentDesc">
          <el-input v-model="form.contentDesc" type="textarea" placeholder="请输入内容" />
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
            :placeholder="isAdd ? '自动生成路径' : '请输入路径'"
            :disabled="isAdd"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog title="详情" v-model="openView" width="500px" append-to-body>
      <el-form :model="form" label-width="80px">
        <el-form-item label="编号">{{ form.id }}</el-form-item>
        <el-form-item label="名称">{{ form.name }}</el-form-item>
        <el-form-item label="目标" v-if="form.targetType">{{ form.targetType }}</el-form-item>
        <el-form-item label="类型">{{ form.type }}</el-form-item>
        <el-form-item label="路径">{{ form.path }}</el-form-item>
        <el-form-item label="地点">{{ form.location }}</el-form-item>
        <el-form-item label="时间">{{ formatStartTime(form.startTime) }}</el-form-item>
        <el-form-item label="创建人">{{ form.createBy || '暂无创建人' }}</el-form-item>
        <el-form-item label="创建时间">{{ form.createTime }}</el-form-item>
        <el-form-item label="内容描述">{{ form.contentDesc }}</el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="openView = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
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
  proxy.$refs["infoRef"].validate(valid => {
    if (valid) {
      // 在提交前格式化startTime为 yyyy-MM-dd 格式
      const submitData = JSON.parse(JSON.stringify(form.value))
      if (submitData.startTime) {
        submitData.startTime = formatDateForSubmit(submitData.startTime)
      }
      
      if (submitData.id != null && title.value.startsWith("修改")) {
        updateInfo(submitData).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addInfo(submitData).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
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
</style>
