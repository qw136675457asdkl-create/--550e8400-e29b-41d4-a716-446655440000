<template>
  <div class="app-container">
    <el-card v-show="showSearch" shadow="never" class="search-card">
      <el-form :model="queryParams" inline label-width="84px">
        <el-form-item label="任务名称">
          <el-input
            v-model="queryParams.taskName"
            placeholder="请输入任务名称"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="所属项目">
          <el-select
            v-model="queryParams.projectId"
            placeholder="请选择所属项目"
            clearable
            style="width: 220px"
          >
            <el-option
              v-for="item in projectOptions"
              :key="item.projectId"
              :label="item.projectName"
              :value="item.projectId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属试验">
          <el-select
            v-model="queryParams.experimentId"
            placeholder="请选择所属试验"
            clearable
            style="width: 220px"
          >
            <el-option
              v-for="item in experimentOptions"
              :key="item.experimentId"
              :label="item.experimentName"
              :value="item.experimentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="创建人">
          <el-input
            v-model="queryParams.createBy"
            placeholder="请输入创建人"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="toolbar">
          <div class="toolbar__left">
            <el-button type="primary" @click="openCreateDialog" v-hasPermi="['data:simulation:insert']"><svg-icon icon-class="button" /> 新增仿真任务</el-button>
            <el-button
              class="toolbar-delete-btn"
              type="danger"
              icon="Delete"
              v-hasPermi="['data:simulation:delete']"
              :disabled="deleteDisabled"
              @click="handleDelete()"
            >
              删除
            </el-button>
          </div>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
        </div>
      </template>

      <el-table v-loading="loading" :data="taskList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="任务ID" prop="id" width="120" align="center" />
        <el-table-column label="任务名称" prop="taskName" min-width="180" show-overflow-tooltip />
        <el-table-column label="所属项目" prop="projectName" min-width="160" show-overflow-tooltip />
        <el-table-column label="所属试验" prop="experimentName" min-width="180" show-overflow-tooltip />
        <el-table-column label="子任务概览" prop="dataCategorySummary" min-width="220" show-overflow-tooltip />
        <el-table-column label="创建人" prop="createBy" width="120" align="center" />
        <el-table-column label="创建时间" min-width="180" align="center">
          <template #default="{ row }">
            <span>{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getTaskStatusTag(row.status)" effect="plain">
              {{ getTaskStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="handleView(row)">详情</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
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
    </el-card>

    <el-dialog
      v-model="simulationDialogOpen"
      title="新增数据仿真任务"
      width="1560px"
      top="3vh"
      append-to-body
      class="simulation-dialog"
    >
      <div class="simulation-panel">
        <section class="simulation-section">
          <div class="section-title">基础配置</div>
          <div class="section-body">
            <div class="form-grid form-grid--three">
              <div class="field-item">
                <span class="field-label">任务名称：</span>
                <el-input v-model="simulationForm.taskName" placeholder="请输入" />
              </div>
              <div class="field-item">
                <span class="field-label">所属项目：</span>
                <el-select
                  v-model="simulationForm.projectId"
                  placeholder="请选择"
                  clearable
                  @change="handleSimulationProjectChange"
                >
                  <el-option
                    v-for="item in projectOptions"
                    :key="item.projectId"
                    :label="item.projectName"
                    :value="item.projectId"
                  />
                </el-select>
              </div>
              <div class="field-item">
                <span class="field-label">所属试验：</span>
                <el-select
                  v-model="simulationForm.experimentId"
                  placeholder="请选择"
                  clearable
                  @change="handleSimulationExperimentChange"
                >
                  <el-option
                    v-for="item in filteredSimulationExperiments"
                    :key="item.experimentId"
                    :label="item.experimentName"
                    :value="item.experimentId"
                  />
                </el-select>
              </div>
            </div>
          </div>
        </section>

        <section class="simulation-section">
          <div class="section-title">载机模型</div>
          <div class="section-body">
            <div class="form-grid form-grid--three">
              <div class="field-item">
                <span class="field-label">运动模型：</span>
                <el-select v-model="simulationForm.motionModel" placeholder="请选择">
                  <el-option
                    v-for="item in motionModelOptions"
                    :key="item"
                    :label="item"
                    :value="item"
                  />
                </el-select>
              </div>
              <div class="field-item field-item--stack coordinate-field">
                <span class="field-label">起点坐标：</span>
                <div class="triple-inputs coordinate-inputs coordinate-inputs--start">
                  <el-input-number
                    v-model="simulationForm.startCoordinate.lon"
                    :controls="false"
                    :precision="6"
                    :min="-180"
                    :max="180"
                    placeholder="经度"
                  />
                  <el-input-number
                    v-model="simulationForm.startCoordinate.lat"
                    :controls="false"
                    :precision="6"
                    :min="-90"
                    :max="90"
                    placeholder="纬度"
                  />
                  <el-input-number
                    v-model="simulationForm.startCoordinate.alt"
                    :controls="false"
                    :precision="2"
                    :min="-1000"
                    :max="50000"
                    placeholder="高度"
                  />
                </div>
              </div>
              <div class="field-item field-item--stack coordinate-field">
                <span class="field-label">终点坐标：</span>
                <div class="triple-inputs coordinate-inputs coordinate-inputs--end">
                  <el-input-number
                    v-model="simulationForm.endCoordinate.lon"
                    :controls="false"
                    :precision="6"
                    :min="-180"
                    :max="180"
                    placeholder="经度"
                  />
                  <el-input-number
                    v-model="simulationForm.endCoordinate.lat"
                    :controls="false"
                    :precision="6"
                    :min="-90"
                    :max="90"
                    placeholder="纬度"
                  />
                  <el-input-number
                    v-model="simulationForm.endCoordinate.alt"
                    :controls="false"
                    :precision="2"
                    :min="-1000"
                    :max="50000"
                    placeholder="高度"
                  />
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="simulation-section">
          <div class="section-title">数据参数</div>
          <div class="section-body">
            <el-tabs v-model="activeSimulationTab" class="simulation-tabs">
              <el-tab-pane
                v-for="tab in simulationTabs"
                :key="tab.code"
                :name="tab.code"
              >
                <template #label>
                  <div
                    class="simulation-tab-label"
                    :class="{ 'is-enabled': simulationForm.tabs[tab.code]?.enabled }"
                  >
                    <span class="simulation-tab-label__dot"></span>
                    <span class="simulation-tab-label__text">{{ tab.label }}</span>
                  </div>
                </template>
              </el-tab-pane>
            </el-tabs>

            <div class="tab-panel">
              <div class="tab-panel__header">
                <div class="tab-panel__title">{{ currentSimulationTab.label }}</div>
                <el-switch
                  class="tab-panel__switch"
                  v-model="currentSimulationTabState.enabled"
                  active-text="纳入本次任务"
                  inactive-text="暂不生成"
                />
              </div>

              <el-form label-width="100px" class="tab-form">
                <el-form-item label="数据名称">
                  <el-input
                    v-model="currentSimulationTabState.dataName"
                    placeholder="请输入，自动添加前缀mock-"
                  />
                </el-form-item>
                <el-form-item label="输出类型">
                  <el-radio-group v-model="currentSimulationTabState.outputType">
                    <el-radio value="csv">csv</el-radio>
                    <el-radio value="bit">bit</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item v-if="currentSimulationTab.showDataSource" label="数据模型">
                  <div class="data-source-row">
                    <el-radio-group v-model="currentSimulationTabState.dataSourceType">
                      <el-radio
                        v-for="option in currentSimulationDataSourceOptions"
                        :key="option.value"
                        :value="option.value"
                      >
                        {{ option.label }}
                      </el-radio>
                    </el-radio-group>
                    <el-select
                      v-model="currentSimulationTabState.sourceFileName"
                      class="file-select"
                      clearable
                      filterable
                      :disabled="currentSimulationTabState.dataSourceType !== 'existing'"
                      placeholder="列出所属试验文件夹下的文件"
                    >
                      <el-option
                        v-for="item in experimentFileOptions"
                        :key="item"
                        :label="item"
                        :value="item"
                      />
                    </el-select>
                  </div>
                </el-form-item>
                <el-form-item label="时长范围">
                  <el-date-picker
                    v-model="currentSimulationTabState.timeRange"
                    type="datetimerange"
                    value-format="YYYY-MM-DD HH:mm:ss"
                    range-separator="至"
                    start-placeholder="开始时间"
                    end-placeholder="结束时间"
                  />
                </el-form-item>
                <el-form-item label="数据帧率">
                  <el-input-number
                    v-model="currentSimulationTabState.frequencyHz"
                    :min="1"
                    :step="1"
                    :precision="0"
                    step-strictly
                    :controls="false"
                    placeholder="请输入整数"
                  />
                  <span class="unit-text">Hz</span>
                </el-form-item>
                <el-form-item v-if="currentSimulationTab.showTargetNum" label="目标数量">
                  <el-input-number
                    v-model="currentSimulationTabState.targetNum"
                    :min="0"
                    :max="32"
                    :controls="false"
                    placeholder="请输入整数"
                  />
                </el-form-item>
              </el-form>

              <div class="metric-table-card">
                <div class="metric-table-card__header">
                  <div class="metric-table-card__title">字段说明</div>
                  <el-button type="primary" link @click="handleAddSimulationMetric">
                    新增字段
                  </el-button>
                </div>
                <el-table
                  :data="currentSimulationMetrics"
                  border
                  max-height="360"
                  empty-text="当前页签暂未配置字段模板"
                >
                  <el-table-column label="字段名称" min-width="180">
                    <template #default="{ row }">
                      <el-input
                        v-if="row.isCustom"
                        v-model="row.fieldName"
                        class="metric-edit-input"
                        placeholder="请输入字段名称"
                      />
                      <span v-else class="metric-cell">{{ row.fieldName }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="数据类型" width="140" align="center">
                    <template #default="{ row }">
                      <el-select
                        v-model="row.dataType"
                        class="metric-edit-input"
                        filterable
                        allow-create
                        default-first-option
                        clearable
                        placeholder="请选择或输入"
                      >
                        <el-option
                          v-for="option in metricDataTypeOptions"
                          :key="option"
                          :label="option"
                          :value="option"
                        />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="推荐值" width="140" align="center">
                    <template #default="{ row }">
                      <el-input
                        v-if="isMetricValueEditable(row.recommendedValue)"
                        v-model="row.recommendedValue"
                        class="metric-edit-input"
                      />
                      <span v-else class="metric-cell metric-cell--center">{{ row.recommendedValue }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="波动域" width="140" align="center">
                    <template #default="{ row }">
                      <div v-if="isMetricValueEditable(row.fluctuationRange)" class="metric-fluctuation-input">
                        <span class="metric-fluctuation-prefix">&plusmn;</span>
                        <el-input
                          :model-value="row.fluctuationRange"
                          class="metric-edit-input metric-edit-input--fluctuation"
                          @update:model-value="value => updateFluctuationRange(row, value)"
                        />
                        <span class="metric-fluctuation-suffix">%</span>
                      </div>
                      <span v-else class="metric-cell metric-cell--center">{{ row.fluctuationRange }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="描述与战术意义（Description & Tactic）" min-width="360">
                    <template #default="{ row }">
                      <el-input
                        v-model="row.description"
                        type="textarea"
                        :rows="2"
                        resize="none"
                        class="metric-edit-input"
                        placeholder="请输入字段描述"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="88" align="center">
                    <template #default="{ row, $index }">
                      <el-button
                        v-if="row.isCustom"
                        link
                        type="danger"
                        class="metric-delete-btn"
                        @click="handleRemoveSimulationMetric($index)"
                      >
                        删除
                      </el-button>
                      <span v-else class="metric-cell metric-cell--center">--</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <div class="tab-actions">
                <el-button type="primary" class="save-page-btn" @click="handleSaveSimulationPage">
                  保存子页面
                </el-button>
              </div>
            </div>
          </div>
        </section>

        <div class="dialog-actions">
          <el-button @click="handleResetSimulationForm">全部重置</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleGenerateSimulation">
            确认生成
          </el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="detailDialogOpen"
      title="任务详情"
      width="1120px"
      top="6vh"
      append-to-body
    >
      <div class="detail-panel">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务名称">{{ detailDialogData.taskName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="任务状态">{{ getTaskStatusText(detailDialogData.status) }}</el-descriptions-item>
          <el-descriptions-item label="所属项目">{{ detailDialogData.projectName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="所属试验">{{ detailDialogData.experimentName || '--' }}</el-descriptions-item>
          <el-descriptions-item label="运动模型">{{ detailDialogData.motionModel || '--' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detailDialogData.createBy || '--' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(detailDialogData.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="输出目录" :span="2">{{ detailDialogData.path || '--' }}</el-descriptions-item>
          <el-descriptions-item label="子任务概览" :span="2">
            {{ detailDialogData.dataCategorySummary || '--' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-subtasks">
          <div class="detail-subtasks__title">子任务列表</div>
          <el-table :data="detailDialogData.dataGroups || []" border>
            <el-table-column label="子任务名称" prop="groupName" min-width="180" show-overflow-tooltip />
            <el-table-column label="输出类型" prop="outputType" width="120" align="center" />
            <el-table-column label="帧率(Hz)" prop="frequencyHz" width="120" align="center" />
            <el-table-column label="目标数量" prop="targetNum" width="120" align="center" />
            <el-table-column label="时间范围" min-width="240">
              <template #default="{ row }">
                {{ formatRange(row.startTimeMs, row.endTimeMs) }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="getTaskStatusTag(row.status)" effect="plain">
                  {{ getTaskStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="DataSimulation">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getInfo, getExperimentInfos } from '@/api/data/info'
import {
  deleteSimulationTask,
  getSimulationMetricTemplates,
  getSimulationTask,
  listExperimentFiles,
  listSimulationTasks,
  submitSimulationTask
} from '@/api/data/simulation'
import useUserStore from '@/store/modules/user'

const showSearch = ref(true)
const loading = ref(false)
const submitLoading = ref(false)
const total = ref(0)
const taskList = ref([])
const selectedTaskRows = ref([])
const simulationDialogOpen = ref(false)
const detailDialogOpen = ref(false)
const projectOptions = ref([])
const experimentOptions = ref([])
const experimentFileOptions = ref([])
const metricTemplateMap = ref({})
const taskSocket = ref(null)
const defaultOutputDirectory = 'csv_output/api_requests'
const userStore = useUserStore()
const MAX_SIMULATION_DURATION_MS = 3 * 24 * 60 * 60 * 1000
const DEFAULT_SIMULATION_TIME_RANGE_MS = 5 * 60 * 1000
const COORDINATE_RULES = [
  { key: 'lon', label: '\u7ecf\u5ea6', min: -180, max: 180, unit: 'deg' },
  { key: 'lat', label: '\u7eac\u5ea6', min: -90, max: 90, unit: 'deg' },
  { key: 'alt', label: '\u9ad8\u5ea6', min: -1000, max: 50000, unit: 'm' }
]

const motionModelOptions = ['直线模型', '盘旋模型', '折线模型', '机动模型']
const simulationTabs = [
  { code: 'INS', label: '载机惯导信息', showDataSource: false, showTargetNum: false },
  { code: 'ATTITUDE', label: '载机姿态信息', showDataSource: false, showTargetNum: false },
  { code: 'RADAR_TRACK', label: '雷达航迹数据', showDataSource: true, showTargetNum: true },
  { code: 'EW', label: '电子战数据', showDataSource: true, showTargetNum: true },
  { code: 'COMM', label: '通侦数据', showDataSource: true, showTargetNum: true },
  { code: 'ADSB', label: 'ADS-B 数据', showDataSource: true, showTargetNum: true },
  { code: 'AIS', label: 'AIS数据', showDataSource: true, showTargetNum: true },
  { code: 'ADS_B', label: '目标牵引询问数据', showDataSource: true, showTargetNum: true },
  { code: 'DATA9', label: '方位数据', showDataSource: true, showTargetNum: true },
  { code: 'DATA10', label: '闭锁信息', showDataSource: true, showTargetNum: true }
]
const TARGET_NUM_TAB_CODES = ['RADAR_TRACK', 'ADSB']
simulationTabs.forEach(tab => {
  tab.showTargetNum = TARGET_NUM_TAB_CODES.includes(tab.code)
})
const metricDataTypeOptions = ['String', 'Integer', 'Int', 'Long', 'Float', 'Double', 'BigInt', 'uint32', 'Boolean', 'Enum', 'Hex']

const SIMULATION_GROUP_NAME_SUBMIT_MAP = {
  INS: 'aircraft_inertial',
  ATTITUDE: 'attitude',
  RADAR_TRACK: 'radar_track',
  ADS_B: 'ads_b'
}
Object.assign(SIMULATION_GROUP_NAME_SUBMIT_MAP, {
  EW: 'electronic_warfare',
  COMM: 'communication_reconnaissance',
  ADSB: 'ads_b',
  AIS: 'ais',
  ADS_B: 'target_towing_inquiry',
  DATA9: 'bearing',
  DATA10: 'lock_information'
})

const SIMULATION_GROUP_NAME_DISPLAY_MAP = simulationTabs.reduce((map, tab) => {
  map[tab.code] = tab.label
  const submitName = SIMULATION_GROUP_NAME_SUBMIT_MAP[tab.code]
  if (submitName) {
    map[submitName] = tab.label
  }
  return map
}, {})

const attitudeMetricBlueprint = [
  { fieldName: 'timestamp', dataType: 'BigInt', recommendedValue: '/', fluctuationRange: '/', description: '时间戳' },
  { fieldName: 'euler_pitch', dataType: 'Float', recommendedValue: '/', fluctuationRange: '/', description: '俯仰角。' },
  { fieldName: 'euler_roll', dataType: 'Float', recommendedValue: '/', fluctuationRange: '/', description: '滚转角。' },
  { fieldName: 'euler_yaw', dataType: 'Float', recommendedValue: '/', fluctuationRange: '/', description: '偏航角。' },
  {
    fieldName: 'rate_p',
    dataType: 'Float',
    description: '滚转角速率',
    recommendedFactory: () => randomSignedFloat(30, 2),
    fluctuationFactory: () => createFluctuationText(0.5, 5, 2)
  },
  {
    fieldName: 'rate_q',
    dataType: 'Float',
    description: '俯仰角速率',
    recommendedFactory: () => randomSignedFloat(20, 2),
    fluctuationFactory: () => createFluctuationText(0.5, 4, 2)
  },
  {
    fieldName: 'rate_r',
    dataType: 'Float',
    description: '偏航角速率',
    recommendedFactory: () => randomSignedFloat(25, 2),
    fluctuationFactory: () => createFluctuationText(0.5, 4.5, 2)
  },
  {
    fieldName: 'flight_path_angle',
    dataType: 'Float',
    description: '预留',
    recommendedFactory: () => randomSignedFloat(15, 2),
    fluctuationFactory: () => createFluctuationText(0.2, 2.5, 2)
  },
  {
    fieldName: 'angle_of_attack',
    dataType: 'Float',
    description: '预留',
    recommendedFactory: () => randomFloat(0, 18, 2),
    fluctuationFactory: () => createFluctuationText(0.1, 2, 2)
  },
  { fieldName: 'sideslip_angle', dataType: 'Float', recommendedValue: '/', fluctuationRange: '/', description: '预留' },
  {
    fieldName: 'ahrs_status',
    dataType: 'Enum',
    recommendedValue: '/',
    fluctuationRange: '/',
    description: 'AHRS系统状态：ALIGNING（对准中）、COARSE（粗对准）'
  }
]

const localMetricTemplateMap = {
  INS: [
    { fieldName: 'timestamp', dataType: 'uint32', recommendedValue: '/', fluctuationRange: '/', description: '高精度时钟' },
    { fieldName: 'lat', dataType: 'Double', recommendedValue: '/', fluctuationRange: '/', description: '纬度' },
    { fieldName: 'lon', dataType: 'Double', recommendedValue: '/', fluctuationRange: '/', description: '经度' },
    { fieldName: 'alt', dataType: 'Double', recommendedValue: '/', fluctuationRange: '/', description: '高度' },
    {
      fieldName: 'true_airspeed',
      dataType: 'Float',
      description: '真空速，三速度求解',
      recommendedFactory: () => randomFloat(180, 320, 2),
      fluctuationFactory: () => createFluctuationText(1, 12, 2)
    },
    {
      fieldName: 'heading_true',
      dataType: 'Float',
      description: '真航向，机头指向相对于真北的角度',
      recommendedFactory: () => randomFloat(0, 359.99, 2),
      fluctuationFactory: () => createFluctuationText(0.5, 6, 2)
    },
    {
      fieldName: 'vel_north',
      dataType: 'Float',
      description: '北向速度',
      recommendedFactory: () => randomSignedFloat(250, 2),
      fluctuationFactory: () => createFluctuationText(0.5, 8, 2)
    },
    {
      fieldName: 'vel_east',
      dataType: 'Float',
      description: '东向速度',
      recommendedFactory: () => randomSignedFloat(250, 2),
      fluctuationFactory: () => createFluctuationText(0.5, 8, 2)
    },
    {
      fieldName: 'vel_vertical',
      dataType: 'Float',
      description: '地向速度，上升为正',
      recommendedFactory: () => randomSignedFloat(35, 2),
      fluctuationFactory: () => createFluctuationText(0.2, 3, 2)
    },
    {
      fieldName: 'nav_mode',
      dataType: 'Enum',
      recommendedValue: '/',
      fluctuationRange: '/',
      description: '导航模式状态：ALIGN（对准中）、INERTIAL（惯导）'
    },
    {
      fieldName: 'ins_status_w',
      dataType: 'Hex',
      recommendedValue: '/',
      fluctuationRange: '/',
      description: '系统状态字。包含BIT自检结果、传感器状态'
    }
  ],
  ATTITUDE: attitudeMetricBlueprint,
  RADAR_TRACK: attitudeMetricBlueprint,
  ADS_B: attitudeMetricBlueprint
}

const defaultActiveTab = 'INS'
const activeSimulationTab = ref(defaultActiveTab)

const queryParams = reactive({
  taskName: '',
  projectId: undefined,
  experimentId: undefined,
  createBy: '',
  pageNum: 1,
  pageSize: 10
})

const detailDialogData = ref(createEmptyTaskDetail())
const simulationForm = reactive(createSimulationForm())

const currentSimulationTab = computed(() => {
  return simulationTabs.find(item => item.code === activeSimulationTab.value) || simulationTabs[0]
})

const currentSimulationTabState = computed(() => {
  return simulationForm.tabs[activeSimulationTab.value]
})

const currentSimulationDataSourceOptions = computed(() => {
  const defaultOptions = [
    { value: 'simulate', label: '模拟生成' },
    { value: 'existing', label: '基于已有数据' }
  ]
    return defaultOptions
})

const currentSimulationMetrics = computed(() => {
  const metrics = currentSimulationTabState.value?.metrics || []
  return Array.isArray(metrics) ? metrics : []
})

const filteredSimulationExperiments = computed(() => {
  if (!simulationForm.projectId) {
    return experimentOptions.value
  }
  return experimentOptions.value.filter(item => String(item.projectId) === String(simulationForm.projectId))
})

const deleteDisabled = computed(() => selectedTaskRows.value.length === 0)

function createCoordinate() {
  return {
    lon: null,
    lat: null,
    alt: null
  }
}

function randomFloat(min, max, precision = 2) {
  return (Math.random() * (max - min) + min).toFixed(precision)
}

function randomSignedFloat(limit, precision = 2) {
  return ((Math.random() * 2 - 1) * limit).toFixed(precision)
}

function createFluctuationText(min, max, precision = 2) {
  return `±${randomFloat(min, max, precision)}`
}

function resolveMetricDisplayValue(rawValue, factory) {
  if (rawValue !== undefined && rawValue !== null && rawValue !== '') {
    return rawValue
  }
  return typeof factory === 'function' ? String(factory()) : ''
}

function isMetricValueEditable(value) {
  return String(value ?? '').trim() !== '/'
}

function createEmptyMetric(index = 0) {
  return {
    fieldName: '',
    dataType: '',
    recommendedValue: '',
    fluctuationRange: '',
    description: '',
    sortNo: index + 1,
    isCustom: true
  }
}

function resequenceMetrics(metrics = []) {
  if (!Array.isArray(metrics)) {
    return
  }

  metrics.forEach((metric, index) => {
    metric.sortNo = index + 1
  })
}

function handleAddSimulationMetric() {
  const metrics = currentSimulationTabState.value?.metrics
  if (!Array.isArray(metrics)) {
    return
  }

  metrics.push(createEmptyMetric(metrics.length))
}

function handleRemoveSimulationMetric(index) {
  const metrics = currentSimulationTabState.value?.metrics
  if (!Array.isArray(metrics) || index < 0 || index >= metrics.length) {
    return
  }

  metrics.splice(index, 1)
  resequenceMetrics(metrics)
}

function buildLocalMetric(metric = {}, index = 0) {
  return {
    fieldName: metric.fieldName || '',
    dataType: metric.dataType || '',
    recommendedValue: resolveMetricDisplayValue(metric.recommendedValue, metric.recommendedFactory),
    fluctuationRange: resolveMetricDisplayValue(metric.fluctuationRange, metric.fluctuationFactory),
    description: metric.description || '',
    sortNo: metric.sortNo || index + 1,
    isCustom: Boolean(metric.isCustom)
  }
}

function cloneMetric(metric = {}, index = 0) {
  return {
    fieldName: metric.fieldName || '',
    dataType: metric.dataType || '',
    recommendedValue: metric.recommendedValue ?? '',
    fluctuationRange: metric.fluctuationRange ?? '',
    description: metric.description || '',
    sortNo: metric.sortNo || index + 1,
    isCustom: Boolean(metric.isCustom)
  }
}

function normalizeFluctuationRangeValue(value) {
  if (String(value ?? '').trim() === '/') {
    return '/'
  }
  return String(value ?? '')
    .replace(/^[\s+\-\u00B1]+/, '')
    .replace(/[%％\s]+$/, '')
    .trim()
}

function updateFluctuationRange(row, value) {
  row.fluctuationRange = normalizeFluctuationRangeValue(value)
}

function formatFluctuationRangeForSubmit(value) {
  const normalized = normalizeFluctuationRangeValue(value)
  return normalized && normalized !== '/' ? `\u00B1${normalized}` : normalized
}

createFluctuationText = function(min, max, precision = 2) {
  return `\u00B1${randomFloat(min, max, precision)}`
}

buildLocalMetric = function(metric = {}, index = 0) {
  return {
    fieldName: metric.fieldName || '',
    dataType: metric.dataType || '',
    recommendedValue: resolveMetricDisplayValue(metric.recommendedValue, metric.recommendedFactory),
    fluctuationRange: normalizeFluctuationRangeValue(
      resolveMetricDisplayValue(metric.fluctuationRange, metric.fluctuationFactory)
    ),
    description: metric.description || '',
    sortNo: metric.sortNo || index + 1,
    isCustom: Boolean(metric.isCustom)
  }
}

cloneMetric = function(metric = {}, index = 0) {
  return {
    fieldName: metric.fieldName || '',
    dataType: metric.dataType || '',
    recommendedValue: metric.recommendedValue ?? '',
    fluctuationRange: normalizeFluctuationRangeValue(metric.fluctuationRange ?? ''),
    description: metric.description || '',
    sortNo: metric.sortNo || index + 1,
    isCustom: Boolean(metric.isCustom)
  }
}

function getMetricTemplate(tabCode) {
  const localMetrics = localMetricTemplateMap[tabCode]
  if (Array.isArray(localMetrics)) {
    return localMetrics.map((metric, index) => buildLocalMetric(metric, index))
  }

  const metrics = metricTemplateMap.value?.[tabCode] || metricTemplateMap.value?.INS || []
  return Array.isArray(metrics) ? metrics.map((metric, index) => cloneMetric(metric, index)) : []
}

function isPositiveInteger(value) {
  const normalizedValue = Number(value)
  return Number.isInteger(normalizedValue) && normalizedValue > 0
}

function createTabState(tab) {
  return {
    enabled: tab.code === defaultActiveTab,
    dataName: '',
    outputType: 'csv',
    dataSourceType: 'simulate',
    sourceFileName: '',
    timeRange: [],
    frequencyHz: 8,
    targetNum: tab.showTargetNum ? 1 : null,
    metrics: getMetricTemplate(tab.code)
  }
}

function createSimulationForm() {
  const tabs = {}
  simulationTabs.forEach(tab => {
    tabs[tab.code] = createTabState(tab)
  })

  return {
    taskName: '',
    projectId: undefined,
    experimentId: undefined,
    motionModel: motionModelOptions[0],
    startCoordinate: createCoordinate(),
    endCoordinate: createCoordinate(),
    tabs
  }
}

function createEmptyTaskDetail() {
  return {
    id: null,
    taskName: '',
    projectName: '',
    experimentName: '',
    motionModel: '',
    dataCategorySummary: '',
    createBy: '',
    createTime: '',
    path: '',
    status: '',
    dataGroups: []
  }
}

function resolveSimulationGroupName(tabCode) {
  const normalizedCode = String(tabCode || '').trim()
  return SIMULATION_GROUP_NAME_SUBMIT_MAP[normalizedCode] || normalizedCode
}

function formatSimulationGroupName(groupName) {
  const normalizedName = String(groupName || '').trim()
  if (!normalizedName) {
    return '--'
  }
  return SIMULATION_GROUP_NAME_DISPLAY_MAP[normalizedName] || normalizedName
}

function formatSimulationGroupSummary(summary) {
  const rawSummary = String(summary || '').trim()
  if (!rawSummary) {
    return '--'
  }

  const translated = rawSummary
    .split(/[、,，;；|]+/)
    .map(item => formatSimulationGroupName(item))
    .filter(item => item && item !== '--')
    .join('、')

  return translated || rawSummary
}

function normalizeTaskDataGroups(groups) {
  return Array.isArray(groups)
    ? groups.map(group => ({
        ...group,
        groupName: formatSimulationGroupName(group.groupName)
      }))
    : []
}

function getTaskStatusText(status) {
  const statusMap = {
    DRAFT: '待提交',
    RUNNING: '运行中',
    SUCCESS: '成功',
    FAILED: '失败',
    提交: '待提交',
    运行中: '运行中',
    成功: '成功',
    失败: '失败'
  }
  return statusMap[status] || status || '--'
}

function getTaskStatusTag(status) {
  const tagMap = {
    DRAFT: 'info',
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
    提交: 'info',
    运行中: 'warning',
    成功: 'success',
    失败: 'danger'
  }
  return tagMap[status] || 'info'
}

function formatDateTime(value) {
  if (!value) {
    return '--'
  }

  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hour = `${date.getHours()}`.padStart(2, '0')
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  const second = `${date.getSeconds()}`.padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

function formatRange(startTimeMs, endTimeMs) {
  if (!startTimeMs || !endTimeMs) {
    return '--'
  }
  return `${formatDateTime(startTimeMs)} 至 ${formatDateTime(endTimeMs)}`
}

function normalizeProjectOptions(projects) {
  return Array.isArray(projects) ? projects : []
}

function normalizeExperimentOptions(experiments) {
  return Array.isArray(experiments) ? experiments : []
}

function normalizeTaskRow(row) {
  const experiment = experimentOptions.value.find(item => String(item.experimentId) === String(row.experimentId))
  const project = projectOptions.value.find(item => String(item.projectId) === String(row.projectId))
  return {
    ...row,
    projectName: row.projectName || project?.projectName || '--',
    experimentName: row.experimentName || experiment?.experimentName || '--',
    dataCategorySummary: formatSimulationGroupSummary(row.dataCategorySummary)
  }
}

async function loadOptions() {
  const [infoResponse, experimentResponse] = await Promise.all([
    getInfo(null, 'experiment'),
    getExperimentInfos({ pageNum: 1, pageSize: 1000 })
  ])

  projectOptions.value = normalizeProjectOptions(infoResponse.projects)
  experimentOptions.value = normalizeExperimentOptions(
    experimentResponse.rows || experimentResponse.data?.rows
  )
}

async function loadExperimentFiles(experimentId) {
  if (!experimentId) {
    experimentFileOptions.value = []
    return
  }

  const response = await listExperimentFiles(experimentId)
  experimentFileOptions.value = Array.isArray(response.data) ? response.data : []
}

async function loadMetricTemplates() {
  const response = await getSimulationMetricTemplates()
  metricTemplateMap.value = response.data || {}
}

function buildTaskSocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const url = `${protocol}//${window.location.host}/websocket/${userStore.id}`
  console.log('WebSocket URL =', url)
  return url
}

function closeTaskSocket() {
  if (taskSocket.value) {
    taskSocket.value.close()
    taskSocket.value = null
  }
}

function handleTaskSocketMessage(message) {
  if (!message) {
    return
  }

  let payload
  try {
    payload = JSON.parse(message)
  } catch {
    return
  }

  if (payload?.type !== 'simulation_task_summary') {
    return
  }

  ElMessage.success(
    payload.message ||
      `\u4efb\u52a1\u5df2\u5b8c\u6210\uff0c\u6210\u529f ${payload.successCount || 0} \u6761\uff0c\u5931\u8d25 ${payload.failedCount || 0} \u6761`
  )
  getList()
}

function connectTaskSocket() {
  if (!userStore.id || taskSocket.value) {
    return
  }

  const socket = new WebSocket(buildTaskSocketUrl())

  socket.onopen = () => {
    console.log('WebSocket 连接成功')
  }

  socket.onerror = (e) => {
    console.error('WebSocket 连接失败', e)
  }

  socket.onmessage = (event) => handleTaskSocketMessage(event.data)

  socket.onclose = (e) => {
    console.log('WebSocket 已关闭', e)
    if (taskSocket.value === socket) {
      taskSocket.value = null
    }
  }

  taskSocket.value = socket
}

async function getList() {
  loading.value = true
  try {
    await loadOptions()
    const response = await listSimulationTasks(queryParams)
    taskList.value = Array.isArray(response.rows) ? response.rows.map(normalizeTaskRow) : []
    total.value = Number(response.total || 0)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.taskName = ''
  queryParams.projectId = undefined
  queryParams.experimentId = undefined
  queryParams.createBy = ''
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  getList()
}

function handleSelectionChange(selection) {
  selectedTaskRows.value = selection
}

async function openCreateDialog() {
  await Promise.all([loadOptions(), loadMetricTemplates()])
  handleResetSimulationForm()
  simulationDialogOpen.value = true
}

function handleResetSimulationForm() {
  Object.assign(simulationForm, createSimulationForm())
  activeSimulationTab.value = defaultActiveTab
  experimentFileOptions.value = []
}

function handleSimulationProjectChange() {
  const exists = filteredSimulationExperiments.value.some(
    item => String(item.experimentId) === String(simulationForm.experimentId)
  )

  if (!exists) {
    simulationForm.experimentId = undefined
    experimentFileOptions.value = []
  }
}

async function handleSimulationExperimentChange() {
  await loadExperimentFiles(simulationForm.experimentId)
}

function handleSaveSimulationPage() {
  ElMessage.success(`已暂存“${currentSimulationTab.value.label}”页签配置`)
}

function hasCoordinateValue(coordinate) {
  return coordinate.lon !== null && coordinate.lat !== null && coordinate.alt !== null
}

function isFiniteCoordinateNumber(value) {
  return typeof value === 'number' && Number.isFinite(value)
}

function validateCoordinateRange(coordinate, pointLabel) {
  for (const rule of COORDINATE_RULES) {
    const value = coordinate?.[rule.key]
    if (!isFiniteCoordinateNumber(value)) {
      ElMessage.warning(`${pointLabel}${rule.label}\u8bf7\u8f93\u5165\u5408\u89c4\u7684\u6570\u503c`)
      return false
    }
    if (value < rule.min || value > rule.max) {
      ElMessage.warning(
        `${pointLabel}${rule.label}\u9700\u5728 ${rule.min} ${rule.unit} \u81f3 ${rule.max} ${rule.unit} \u4e4b\u95f4`
      )
      return false
    }
  }
  return true
}

function toTimestamp(dateTimeText) {
  return dateTimeText ? new Date(dateTimeText).getTime() : null
}

function resolvePayloadTimeRange(timeRange, fallbackStartTimeMs = Date.now()) {
  const startTimeMs = toTimestamp(timeRange?.[0])
  const endTimeMs = toTimestamp(timeRange?.[1])

  if (Number.isFinite(startTimeMs) && Number.isFinite(endTimeMs) && endTimeMs > startTimeMs) {
    return {
      startTimeMs,
      endTimeMs
    }
  }

  return {
    startTimeMs: fallbackStartTimeMs,
    endTimeMs: fallbackStartTimeMs + DEFAULT_SIMULATION_TIME_RANGE_MS
  }
}

function getDurationMs(timeRange) {
  if (!Array.isArray(timeRange) || timeRange.length !== 2) {
    return null
  }

  const startTimeMs = toTimestamp(timeRange[0])
  const endTimeMs = toTimestamp(timeRange[1])
  if (!Number.isFinite(startTimeMs) || !Number.isFinite(endTimeMs)) {
    return null
  }

  return endTimeMs - startTimeMs
}

function getEnabledSimulationTabs() {
  return simulationTabs.filter(tab => simulationForm.tabs[tab.code].enabled)
}

async function confirmLongDurationWarning(enabledTabs) {
  const overlongTabs = enabledTabs.filter(tab => {
    const durationMs = getDurationMs(simulationForm.tabs[tab.code].timeRange)
    return durationMs !== null && durationMs > MAX_SIMULATION_DURATION_MS
  })

  if (!overlongTabs.length) {
    return true
  }

  const tabNames = overlongTabs.map(tab => tab.label).join(', ')
  try {
    await ElMessageBox.confirm(
      `\u4ee5\u4e0b\u5b50\u4efb\u52a1\u65f6\u957f\u5df2\u8d85\u8fc7 3 \u5929\uff1a${tabNames}\n\u6b64\u64cd\u4f5c\u53ef\u80fd\u5bfc\u81f4\u4eff\u771f\u65f6\u95f4\u8fc7\u957f\uff0c\u662f\u5426\u7ee7\u7eed\u63d0\u4ea4\uff1f`,
      '\u8b66\u544a',
      {
        type: 'warning',
        confirmButtonText: '\u7ee7\u7eed\u63d0\u4ea4',
        cancelButtonText: '\u53d6\u6d88'
      }
    )
    return true
  } catch {
    return false
  }
}

function buildMetricPayload(groupCode) {
  const metrics = simulationForm.tabs[groupCode]?.metrics
  if (!Array.isArray(metrics)) {
    return []
  }

  resequenceMetrics(metrics)

  return metrics.map((metric, index) => ({
    fieldName: metric.fieldName,
    dataType: metric.dataType,
    recommendedValue: metric.recommendedValue,
    fluctuationRange: formatFluctuationRangeForSubmit(metric.fluctuationRange),
    description: metric.description,
    sortNo: metric.sortNo || index + 1
  }))
}

function resolveDataName(tabLabel, dataName) {
  const rawValue = String(dataName || '').trim()
  if (!rawValue) {
    return `mock-${tabLabel}`
  }
  return rawValue.startsWith('mock-') ? rawValue : `mock-${rawValue}`
}

function buildRequestId(tabCode, timestamp) {
  return `mock-${tabCode}-${timestamp}`
}

function buildTaskPayload() {
  const timestamp = Date.now()
  const allTabs = simulationTabs
    .map((tab, index) => ({ ...tab, state: simulationForm.tabs[tab.code], sortNo: index + 1 }))

  return {
    taskName: simulationForm.taskName,
    projectId: simulationForm.projectId,
    experimentId: simulationForm.experimentId,
    motionModel: simulationForm.motionModel,
    startCoordinate: simulationForm.startCoordinate,
    endCoordinate: simulationForm.endCoordinate,
    dataGroups: allTabs.map(item => {
      const timeRange = resolvePayloadTimeRange(item.state.timeRange, timestamp)
      return {
        groupCode: item.code,
        groupName: resolveSimulationGroupName(item.code),
        sortNo: item.sortNo,
        enabled: item.state.enabled,
        items: [
          {
            dataName: resolveDataName(item.label, item.state.dataName),
            requestId: buildRequestId(item.code, timestamp),
            outputType: item.state.outputType,
            outputDirectory: defaultOutputDirectory,
            dataSourceType: item.state.dataSourceType,
            sourceFileName: item.state.sourceFileName || '',
            startTimeMs: timeRange.startTimeMs,
            endTimeMs: timeRange.endTimeMs,
            frequencyHz: item.state.frequencyHz,
            targetNum: item.state.targetNum,
            metrics: buildMetricPayload(item.code)
          }
        ]
      }
    })
  }
}

function legacyValidateSimulationForm() {
  if (!simulationForm.taskName) {
    ElMessage.warning('请输入任务名称')
    return false
  }
  if (!simulationForm.projectId || !simulationForm.experimentId) {
    ElMessage.warning('请选择所属项目和所属试验')
    return false
  }
  if (!hasCoordinateValue(simulationForm.startCoordinate) || !hasCoordinateValue(simulationForm.endCoordinate)) {
    ElMessage.warning('请完善起点坐标和终点坐标')
    return false
  }

  const enabledTabs = simulationTabs.filter(tab => simulationForm.tabs[tab.code].enabled)
  if (!enabledTabs.length) {
    ElMessage.warning('请至少启用一个子任务页签')
    return false
  }

  for (const tab of enabledTabs) {
    const state = simulationForm.tabs[tab.code]
    if (!isPositiveInteger(state.frequencyHz)) {
      ElMessage.warning(`请填写“${tab.label}”的数据帧率`)
      activeSimulationTab.value = tab.code
      return false
    }
    if (tab.showTargetNum && (state.targetNum === null || state.targetNum === undefined)) {
      ElMessage.warning(`请填写“${tab.label}”的目标数量`)
      activeSimulationTab.value = tab.code
      return false
    }
    if (!state.timeRange || state.timeRange.length !== 2) {
      ElMessage.warning(`请填写“${tab.label}”的时长范围`)
      activeSimulationTab.value = tab.code
      return false
    }
    if (tab.showDataSource && state.dataSourceType === 'existing' && !state.sourceFileName) {
      ElMessage.warning(`请选择“${tab.label}”的数据源文件`)
      activeSimulationTab.value = tab.code
      return false
    }
  }

  return true
}

async function legacyHandleGenerateSimulation() {
  if (!validateSimulationForm()) {
    return
  }

  submitLoading.value = true
  try {
    await submitSimulationTask(buildTaskPayload())
    ElMessage.success('仿真任务已提交，子任务已进入执行队列')
    simulationDialogOpen.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

function getMetricValidationMessage(tab) {
  const metrics = simulationForm.tabs[tab.code]?.metrics
  if (!Array.isArray(metrics)) {
    return ''
  }

  for (let index = 0; index < metrics.length; index += 1) {
    const metric = metrics[index]
    if (!String(metric?.fieldName || '').trim()) {
      return `请完善 ${tab.label} 第 ${index + 1} 行的字段名称`
    }
    if (!String(metric?.dataType || '').trim()) {
      return `请完善 ${tab.label} 第 ${index + 1} 行的数据类型`
    }
  }

  return ''
}

function validateSimulationForm(enabledTabs = getEnabledSimulationTabs()) {
  if (!simulationForm.taskName) {
    ElMessage.warning('\u8bf7\u8f93\u5165\u4efb\u52a1\u540d\u79f0')
    return false
  }
  if (!simulationForm.projectId || !simulationForm.experimentId) {
    ElMessage.warning('\u8bf7\u9009\u62e9\u6240\u5c5e\u9879\u76ee\u548c\u6240\u5c5e\u8bd5\u9a8c')
    return false
  }
  if (!hasCoordinateValue(simulationForm.startCoordinate) || !hasCoordinateValue(simulationForm.endCoordinate)) {
    ElMessage.warning('\u8bf7\u5b8c\u5584\u8d77\u70b9\u5750\u6807\u548c\u7ec8\u70b9\u5750\u6807')
    return false
  }
  if (!validateCoordinateRange(simulationForm.startCoordinate, '\u8d77\u70b9')) {
    return false
  }
  if (!validateCoordinateRange(simulationForm.endCoordinate, '\u7ec8\u70b9')) {
    return false
  }
  if (!enabledTabs.length) {
    ElMessage.warning('\u8bf7\u81f3\u5c11\u542f\u7528\u4e00\u4e2a\u5b50\u4efb\u52a1\u9875\u7b7e')
    return false
  }

  for (const tab of enabledTabs) {
    const state = simulationForm.tabs[tab.code]
    if (!isPositiveInteger(state.frequencyHz)) {
      ElMessage.warning(`\u8bf7\u586b\u5199 ${tab.label} \u7684\u6b63\u6574\u6570\u6570\u636e\u5e27\u7387`)
      activeSimulationTab.value = tab.code
      return false
    }
    if (tab.showTargetNum && (state.targetNum === null || state.targetNum === undefined)) {
      ElMessage.warning(`\u8bf7\u586b\u5199 ${tab.label} \u7684\u76ee\u6807\u6570\u91cf`)
      activeSimulationTab.value = tab.code
      return false
    }
    if (!state.timeRange || state.timeRange.length !== 2) {
      ElMessage.warning(`\u8bf7\u586b\u5199 ${tab.label} \u7684\u65f6\u957f\u8303\u56f4`)
      activeSimulationTab.value = tab.code
      return false
    }

    const durationMs = getDurationMs(state.timeRange)
    if (durationMs === null) {
      ElMessage.warning(`\u8bf7\u68c0\u67e5 ${tab.label} \u7684\u65f6\u95f4\u8303\u56f4`)
      activeSimulationTab.value = tab.code
      return false
    }
    if (durationMs <= 0) {
      ElMessage.warning(`\u8bf7\u786e\u4fdd ${tab.label} \u7684\u7ed3\u675f\u65f6\u95f4\u665a\u4e8e\u5f00\u59cb\u65f6\u95f4`)
      activeSimulationTab.value = tab.code
      return false
    }
    if (tab.showDataSource && state.dataSourceType === 'existing' && !state.sourceFileName) {
      ElMessage.warning(`\u8bf7\u9009\u62e9 ${tab.label} \u7684\u6570\u636e\u6e90\u6587\u4ef6`)
      activeSimulationTab.value = tab.code
      return false
    }

    const metricValidationMessage = getMetricValidationMessage(tab)
    if (metricValidationMessage) {
      ElMessage.warning(metricValidationMessage)
      activeSimulationTab.value = tab.code
      return false
    }
  }

  return true
}

async function handleGenerateSimulation() {
  const enabledTabs = getEnabledSimulationTabs()
  if (!validateSimulationForm(enabledTabs)) {
    return
  }
  if (!(await confirmLongDurationWarning(enabledTabs))) {
    return
  }

  submitLoading.value = true
  try {
    await submitSimulationTask(buildTaskPayload())
    ElMessage.success('\u4eff\u771f\u4efb\u52a1\u5df2\u63d0\u4ea4\uff0c\u5b50\u4efb\u52a1\u5df2\u8fdb\u5165\u6267\u884c\u961f\u5217')
    simulationDialogOpen.value = false
    await getList()
  } finally {
    submitLoading.value = false
  }
}

async function handleView(row) {
  const response = await getSimulationTask(row.id)
  const taskDetail = response.data || {}
  detailDialogData.value = normalizeTaskRow({
    ...createEmptyTaskDetail(),
    ...taskDetail
  })
  detailDialogData.value.dataGroups = normalizeTaskDataGroups(taskDetail.dataGroups)
  detailDialogOpen.value = true
}

async function handleDelete(row) {
  const rows = row ? [row] : selectedTaskRows.value
  if (!rows.length) {
    ElMessage.warning('请先选择需要删除的任务')
    return
  }

  await ElMessageBox.confirm(`确认删除选中的 ${rows.length} 条任务记录吗？`, '提示', {
    type: 'warning'
  })

  for (const item of rows) {
    await deleteSimulationTask(item.id)
  }

  ElMessage.success('删除成功')
  selectedTaskRows.value = []
  await getList()
}

onMounted(() => {
  getList()
  loadMetricTemplates()
})

watch(
  () => userStore.id,
  (userId) => {
    if (userId) {
      connectTaskSocket()
    } else {
      closeTaskSocket()
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  closeTaskSocket()
})
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar__left :deep(.toolbar-delete-btn.el-button) {
  color: #ffffff;
  background: #f04438;
  border-color: #f04438;
  box-shadow: 0 12px 24px rgba(240, 68, 56, 0.2);
}

.toolbar__left :deep(.toolbar-delete-btn.el-button:hover),
.toolbar__left :deep(.toolbar-delete-btn.el-button:focus) {
  color: #ffffff;
  background: #d92d20;
  border-color: #d92d20;
}

.toolbar__left :deep(.toolbar-delete-btn.el-button.is-disabled),
.toolbar__left :deep(.toolbar-delete-btn.el-button.is-disabled:hover) {
  color: rgba(255, 255, 255, 0.92);
  background: #fda29b;
  border-color: #fda29b;
  box-shadow: none;
}

.simulation-panel {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.simulation-section {
  display: flex;
  align-items: flex-start;
  gap: 28px;
}

.section-title {
  width: 120px;
  flex-shrink: 0;
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1.4;
}

.section-body {
  flex: 1;
  min-width: 0;
}

.form-grid {
  display: grid;
  gap: 18px 24px;
}

.form-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.field-item--stack {
  align-items: flex-start;
}

.coordinate-field {
  gap: 10px;
}

.coordinate-field .field-label {
  width: auto;
  font-weight: 600;
}

.field-label {
  width: 88px;
  flex-shrink: 0;
  color: #303133;
  font-size: 15px;
}

.field-item :deep(.el-select),
.field-item :deep(.el-input),
.field-item :deep(.el-input-number) {
  width: 100%;
}

.triple-inputs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.coordinate-inputs {
  padding: 0;
}

.coordinate-inputs :deep(.el-input-number) {
  width: 100%;
}

.coordinate-inputs :deep(.el-input-number .el-input__wrapper) {
  background: #fff;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  border-radius: 6px;
}

.quadruple-inputs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
}

.simulation-tabs {
  margin-bottom: 0;
}

.simulation-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 12px 12px 0;
  border: 1px solid #d7deea;
  border-bottom: none;
  border-radius: 16px 16px 0 0;
  background: linear-gradient(180deg, #eef3fb 0%, #e4ebf5 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.simulation-tabs :deep(.el-tabs__nav-wrap) {
  padding: 0;
}

.simulation-tabs :deep(.el-tabs__nav-wrap::after),
.simulation-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.simulation-tabs :deep(.el-tabs__nav-prev),
.simulation-tabs :deep(.el-tabs__nav-next) {
  width: 30px;
  color: #64748b;
}

.simulation-tabs :deep(.el-tabs__nav-prev:hover),
.simulation-tabs :deep(.el-tabs__nav-next:hover) {
  color: #1f2937;
}

.simulation-tabs :deep(.el-tabs__nav) {
  align-items: flex-end;
  border: none;
}

.simulation-tabs :deep(.el-tabs__item) {
  height: auto;
  padding: 0 4px 0 0;
  border: none;
  color: inherit;
}

.simulation-tabs :deep(.el-tabs__item.is-active) {
  color: inherit;
}

.simulation-tabs :deep(.el-tabs__content) {
  display: none;
}

.simulation-tab-label {
  position: relative;
  top: 1px;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 132px;
  max-width: 190px;
  padding: 11px 18px 10px;
  border: 1px solid rgba(148, 163, 184, 0.56);
  border-bottom: none;
  border-radius: 14px 14px 0 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.76) 0%, rgba(225, 232, 242, 0.96) 100%);
  color: #5b6473;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
  transition: background 0.2s ease, color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.simulation-tabs :deep(.el-tabs__item:hover .simulation-tab-label) {
  color: #334155;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9) 0%, rgba(233, 239, 247, 1) 100%);
}

.simulation-tabs :deep(.el-tabs__item.is-active .simulation-tab-label) {
  background: linear-gradient(180deg, #ffffff 0%, #ffffff 100%);
  color: #111827;
  border-color: #d7deea;
  box-shadow: 0 -10px 24px rgba(15, 23, 42, 0.06), inset 0 1px 0 rgba(255, 255, 255, 0.88);
  transform: translateY(1px);
  z-index: 2;
}

.simulation-tab-label__dot {
  position: relative;
  flex: none;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #c4ccd8;
  transition: background-color 0.2s ease, box-shadow 0.2s ease;
}

.simulation-tab-label.is-enabled .simulation-tab-label__dot {
  background: #409eff;
  box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.14);
}

.simulation-tab-label__text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 700;
}

.tab-panel {
  margin-top: -1px;
  border: 1px solid #d7deea;
  border-radius: 0 14px 14px 14px;
  padding: 24px 28px 28px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcfe 100%);
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.05);
}

.tab-panel__header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 18px;
  margin-bottom: 24px;
}

.tab-panel__title {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.tab-panel__switch {
  flex: none;
}

.tab-panel__switch :deep(.el-switch__label) {
  white-space: nowrap;
}

.tab-form {
  max-width: 860px;
}

.tab-form :deep(.el-date-editor) {
  width: 100%;
}

.metric-table-card {
  margin-top: 8px;
}

.metric-table-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.metric-table-card__title {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

.metric-table-card :deep(.el-table .cell) {
  white-space: normal;
}

.metric-cell {
  display: block;
  line-height: 1.6;
  word-break: break-word;
}

.metric-cell--center {
  text-align: center;
}

.metric-edit-input {
  width: 100%;
}

.metric-fluctuation-input {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}

.metric-fluctuation-prefix {
  flex: none;
  color: #606266;
  font-weight: 600;
  line-height: 1;
}

.metric-fluctuation-suffix {
  flex: none;
  color: #606266;
  font-weight: 600;
  line-height: 1;
}

.metric-edit-input--fluctuation {
  flex: 1;
}

.metric-edit-input :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #dcdfe6 inset;
}

.metric-edit-input :deep(.el-textarea__inner) {
  min-height: 54px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
}

.metric-delete-btn {
  padding: 0;
}

.data-source-row {
  display: flex;
  align-items: center;
  gap: 18px;
  width: 100%;
}

.file-select {
  flex: 1;
}

.unit-text {
  margin-left: 12px;
  color: #909399;
}

.tab-actions {
  display: flex;
  justify-content: center;
  margin-top: 36px;
}

.save-page-btn {
  min-width: 280px;
  background: linear-gradient(90deg, #1120ff 0%, #233bff 100%);
  border: none;
}

.dialog-actions {
  display: flex;
  justify-content: center;
  gap: 24px;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.detail-subtasks {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-subtasks__title {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

@media (max-width: 1440px) {
  .simulation-section {
    flex-direction: column;
    gap: 12px;
  }

  .section-title {
    width: auto;
  }

  .form-grid--three {
    grid-template-columns: 1fr;
  }

  .quadruple-inputs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .data-source-row,
  .tab-panel__header,
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .simulation-tab-label {
    min-width: 118px;
    max-width: 168px;
    padding-left: 14px;
    padding-right: 14px;
  }
}

@media (max-width: 768px) {
  .simulation-tabs :deep(.el-tabs__header) {
    padding-left: 8px;
    padding-right: 8px;
  }

  .simulation-tab-label {
    min-width: 108px;
    max-width: 144px;
    padding: 10px 12px 9px;
    border-radius: 12px 12px 0 0;
  }

  .simulation-tab-label__text {
    font-size: 13px;
  }

  .tab-panel {
    padding: 20px 16px 22px;
  }
}
</style>
