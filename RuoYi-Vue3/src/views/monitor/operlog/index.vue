<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="主机" prop="operIp">
        <el-input
            v-model="queryParams.operIp"
            placeholder="请输入主机IP"
            clearable
            style="width: 240px;"
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="系统模块" prop="title">
        <el-input
            v-model="queryParams.title"
            placeholder="请输入系统模块"
            clearable
            style="width: 240px;"
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="操作人员" prop="operName">
        <el-input
            v-model="queryParams.operName"
            placeholder="请输入操作人员"
            clearable
            style="width: 240px;"
            @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="操作类型" prop="businessType">
        <el-select
            v-model="queryParams.businessType"
            placeholder="操作类型"
            clearable
            style="width: 240px"
        >
          <el-option
              v-for="dict in sys_oper_type"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select
            v-model="queryParams.status"
            placeholder="操作状态"
            clearable
            style="width: 240px"
        >
          <el-option
              v-for="dict in sys_common_status"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="操作时间" style="width: 308px">
        <el-date-picker
            v-model="dateRange"
            value-format="YYYY-MM-DD HH:mm:ss"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
        ></el-date-picker>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            :disabled="multiple"
            @click="handleDelete"
            v-hasPermi="['monitor:operlog:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="danger"
            plain
            icon="Delete"
            @click="handleClean"
            v-hasPermi="['monitor:operlog:remove']"
        >清空</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="warning"
            plain
            icon="Download"
            @click="handleExport"
            v-hasPermi="['monitor:operlog:export']"
        >导出Excel</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="primary"
            plain
            icon="Document"
            @click="handleExportWord"
            v-hasPermi="['monitor:operlog:export']"
        >导出Word</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
            type="success"
            plain
            icon="Document"
            @click="handleExportPdf"
            v-hasPermi="['monitor:operlog:export']"
        >导出PDF</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table ref="operlogRef" v-loading="loading" :data="operlogList" @selection-change="handleSelectionChange" :default-sort="defaultSort" @sort-change="handleSortChange">
      <el-table-column type="selection" width="50" align="center" />
      <el-table-column label="日志编号" align="center" prop="operId" />
      <el-table-column label="系统模块" align="center" prop="title" :show-overflow-tooltip="true" />
      <el-table-column label="操作类型" align="center" prop="businessType">
        <template #default="scope">
          <dict-tag :options="sys_oper_type" :value="scope.row.businessType" />
        </template>
      </el-table-column>
      <el-table-column label="请求类型" align="center" prop="requestMethod" />
      <el-table-column label="操作人员" align="center" width="110" prop="operName" :show-overflow-tooltip="true" sortable="custom" :sort-orders="['descending', 'ascending']" />
      <el-table-column label="主机" align="center" prop="operIp" width="130" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" prop="status">
        <template #default="scope">
          <dict-tag :options="sys_common_status" :value="scope.row.status"/>
        </template>
      </el-table-column>
      <el-table-column label="操作时间" align="center" prop="operTime" width="180" sortable="custom"
                       :sort-orders="['descending', 'ascending']">
        <template #default="scope">
          <span>{{ parseTime(scope.row.operTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作详情" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row, scope.index)"
                     v-hasPermi="['monitor:operlog:query']">详细
          </el-button>
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

    <el-dialog title="审计日志详细" v-model="open" width="800px" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="操作模块：">{{ form.title }} / {{ typeFormat(form) }}</el-form-item>
            <el-form-item
                label="登录信息："
            >{{ form.operName }} / {{ form.operIp }} / {{ form.operLocation }}
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="请求地址：">{{ form.operUrl }}</el-form-item>
            <el-form-item label="请求方式：">{{ form.requestMethod }}</el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="操作描述：" style="word-break: break-all; white-space: pre-wrap;">
              {{ detailDescription }}
            </el-form-item>
          </el-col>
          <el-col :span="24" v-if="detailFacts">
            <el-form-item label="识别信息：" style="word-break: break-all; white-space: pre-wrap;">
              {{ detailFacts }}
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="操作状态：">
              <div v-if="form.status === 0">正常</div>
              <div v-else-if="form.status === 1">失败</div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="消耗时间：">{{ form.costTime }}毫秒</el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="操作时间：">{{ parseTime(form.operTime) }}</el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="异常信息：" v-if="form.status === 1">{{ form.errorMsg }}</el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="open = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Operlog">
import {list, delOperlog, cleanOperlog} from "@/api/monitor/operlog"

const {proxy} = getCurrentInstance()
const {sys_oper_type, sys_common_status} = proxy.useDict("sys_oper_type", "sys_common_status")

const operlogList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")
const dateRange = ref([])
const defaultSort = ref({prop: "operTime", order: "descending"})
const detailDescription = ref("")
const detailFacts = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    operIp: undefined,
    title: undefined,
    operName: undefined,
    businessType: undefined,
    status: undefined
  }
})

const {queryParams, form} = toRefs(data)

/** 查询审计日志 */
function getList() {
  loading.value = true
  list(proxy.addDateRange(queryParams.value, dateRange.value)).then(response => {
    operlogList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 操作日志类型字典翻译 */
function typeFormat(row, column) {
  return proxy.selectDictLabel(sys_oper_type.value, row.businessType)
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryRef")
  queryParams.value.pageNum = 1
  proxy.$refs["operlogRef"].sort(defaultSort.value.prop, defaultSort.value.order)
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.operId)
  multiple.value = !selection.length
}

/** 排序触发事件 */
function handleSortChange(column, prop, order) {
  queryParams.value.orderByColumn = column.prop
  queryParams.value.isAsc = column.order
  getList()
}

function businessActionText(businessType) {
  const actionMap = {
    1: "新增",
    2: "修改",
    3: "删除",
    4: "授权",
    5: "导出",
    6: "导入",
    7: "强退",
    8: "生成",
    9: "清空"
  }
  return actionMap[businessType] || "操作"
}

function safeJsonParse(text) {
  try {
    return JSON.parse(text)
  } catch (e) {
    return null
  }
}

function splitJsonLikeSegments(text) {
  const segments = []
  let start = -1
  let depth = 0
  let quote = false
  let escape = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (quote) {
      if (escape) {
        escape = false
        continue
      }
      if (ch === "\\") {
        escape = true
      } else if (ch === "\"") {
        quote = false
      }
      continue
    }
    if (ch === "\"") {
      quote = true
      continue
    }
    if (ch === "{" || ch === "[") {
      if (depth === 0) {
        start = i
      }
      depth++
      continue
    }
    if (ch === "}" || ch === "]") {
      depth--
      if (depth === 0 && start !== -1) {
        segments.push(text.slice(start, i + 1))
        start = -1
      }
    }
  }
  return segments
}

function parseOperParamValues(operParam) {
  if (!operParam) {
    return []
  }
  const raw = String(operParam).trim()
  if (!raw) {
    return []
  }
  const direct = safeJsonParse(raw)
  if (direct !== null) {
    return [direct]
  }
  const segments = splitJsonLikeSegments(raw)
  const parsed = []
  segments.forEach(seg => {
    const value = safeJsonParse(seg)
    if (value !== null) {
      parsed.push(value)
    }
  })
  return parsed
}

function pickFirst(obj, key, value) {
  if (!obj[key] && value !== undefined && value !== null && String(value).trim() !== "") {
    obj[key] = String(value).trim()
  }
}

function extractFromValue(value, bag) {
  if (value === null || value === undefined) {
    return
  }
  if (Array.isArray(value)) {
    if (value.length > 0 && value.every(item => typeof item !== "object")) {
      if (!bag.arrayValues) {
        bag.arrayValues = []
      }
      bag.arrayValues.push(value.map(item => String(item)).join(","))
    }
    value.forEach(item => extractFromValue(item, bag))
    return
  }
  if (typeof value !== "object") {
    return
  }

  Object.keys(value).forEach(k => {
    const key = String(k).toLowerCase()
    const current = value[k]
    if (key === "type") {
      pickFirst(bag, "type", current)
    } else if (key === "name") {
      pickFirst(bag, "name", current)
    } else if (key === "projectname") {
      pickFirst(bag, "projectName", current)
    } else if (key === "experimentname") {
      pickFirst(bag, "experimentName", current)
    } else if (key === "projectid") {
      pickFirst(bag, "projectId", current)
    } else if (key === "experimentid") {
      pickFirst(bag, "experimentId", current)
    } else if (key === "projectids") {
      pickFirst(bag, "projectIds", Array.isArray(current) ? current.join(",") : current)
    } else if (key === "experimentids") {
      pickFirst(bag, "experimentIds", Array.isArray(current) ? current.join(",") : current)
    } else if (key === "id") {
      pickFirst(bag, "id", current)
    }

    extractFromValue(current, bag)
  })
}

function extractFromOperUrl(operUrl, bag) {
  if (!operUrl) {
    return
  }
  const url = String(operUrl)
  const match = url.match(/\/data\/info\/([^/]+)\/project\/([^/]+)/)
  if (match) {
    pickFirst(bag, "experimentIds", match[1])
    pickFirst(bag, "projectIds", match[2])
  }
}

function buildOperDetail(row) {
  const bag = {}
  const parsedValues = parseOperParamValues(row.operParam)
  parsedValues.forEach(value => extractFromValue(value, bag))
  extractFromOperUrl(row.operUrl, bag)

  // 针对 delete 接口参数是两个数组的场景（["exp1","exp2"] [1,2]）
  if (!bag.experimentIds && !bag.projectIds && bag.arrayValues && bag.arrayValues.length > 0) {
    if (bag.arrayValues[0]) {
      bag.experimentIds = bag.arrayValues[0]
    }
    if (bag.arrayValues[1]) {
      bag.projectIds = bag.arrayValues[1]
    }
  }

  const actor = row.operName || "用户"
  const action = businessActionText(row.businessType)
  const moduleName = row.title || "当前模块"

  const targets = []
  if (bag.type === "project" && bag.name) {
    targets.push(`项目「${bag.name}」`)
  } else if (bag.type === "experiment" && bag.name) {
    targets.push(`试验「${bag.name}」`)
  }
  if (bag.projectName) {
    targets.push(`项目「${bag.projectName}」`)
  }
  if (bag.experimentName) {
    targets.push(`试验「${bag.experimentName}」`)
  }
  if (!targets.length && bag.name) {
    targets.push(`名称「${bag.name}」`)
  }

  const ids = []
  if (bag.experimentId) {
    ids.push(`试验ID=${bag.experimentId}`)
  }
  if (bag.projectId) {
    ids.push(`项目ID=${bag.projectId}`)
  }
  if (bag.experimentIds) {
    ids.push(`试验ID集合=${bag.experimentIds}`)
  }
  if (bag.projectIds) {
    ids.push(`项目ID集合=${bag.projectIds}`)
  }
  if (!ids.length && bag.id) {
    ids.push(`ID=${bag.id}`)
  }

  let description = `${actor}${action}了${moduleName}`
  if (targets.length > 0) {
    description += `，对象：${targets.join("，")}`
  }
  if (ids.length > 0) {
    description += `，标识：${ids.join("，")}`
  }
  if (row.status === 1) {
    description += "（执行失败）"
  }

  const facts = []
  if (bag.type) {
    facts.push(`类型=${bag.type}`)
  }
  if (bag.name) {
    facts.push(`名称=${bag.name}`)
  }
  if (bag.projectName) {
    facts.push(`项目名称=${bag.projectName}`)
  }
  if (bag.experimentName) {
    facts.push(`试验名称=${bag.experimentName}`)
  }
  if (bag.projectIds) {
    facts.push(`项目ID集合=${bag.projectIds}`)
  }
  if (bag.experimentIds) {
    facts.push(`试验ID集合=${bag.experimentIds}`)
  }

  return {
    description,
    facts: facts.join("；")
  }
}

/** 详细按钮操作 */
function handleView(row) {
  open.value = true
  form.value = row
  const detail = buildOperDetail(row)
  detailDescription.value = detail.description
  detailFacts.value = detail.facts
}

/** 删除按钮操作 */
function handleDelete(row) {
  const operIds = row.operId || ids.value
  proxy.$modal.confirm('是否确认删除日志编号为"' + operIds + '"的数据项?').then(function () {
    return delOperlog(operIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {
  })
}

/** 清空按钮操作 */
function handleClean() {
  proxy.$modal.confirm("是否确认清空所有审计日志数据项?").then(function () {
    return cleanOperlog()
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("清空成功")
  }).catch(() => {
  })
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download("monitor/operlog/export", {
    ...queryParams.value,
  }, `config_${new Date().getTime()}.xlsx`)
}

/** 导出Word按钮操作 */
function handleExportWord() {
  proxy.download("monitor/operlog/export/word", {
    ...queryParams.value,
  }, `operlog_${new Date().getTime()}.docx`)
}

/** 导出PDF按钮操作 */
function handleExportPdf() {
  proxy.download("monitor/operlog/export/pdf", {
    ...queryParams.value,
  }, `operlog_${new Date().getTime()}.pdf`)
}

getList()
</script>
