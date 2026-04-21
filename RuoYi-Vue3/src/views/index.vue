<template>
  <div class="app-container command-home">
    <section class="hero-panel">
      <div class="hero-orbit hero-orbit--one"></div>
      <div class="hero-orbit hero-orbit--two"></div>

      <div class="hero-copy">
        <p class="hero-copy__eyebrow">System Command Deck</p>
        <h1 class="hero-copy__title">
          体系协同多源识别
          <span>算法分析与优化软件</span>
        </h1>
<!--        <p class="hero-copy__summary">-->
<!--          妫ｆ牠銆夋稉宥呭晙閸欘亝妲搁垾婊勵偨鏉╁酣銆夐垾婵撶礉閼板本妲搁幎濠冩殶閹诡喗甯撮崗銉ｂ偓浣疯雹閻喐甯瑰鏂烩偓浣鼓侀崹瀣帳缂冾喓鈧焦妫╄箛妤€鐣炬担宥勭瑢閺堝秴濮熼幀浣稿◢閺€鑸靛珴閸掓澘鎮撴稉鈧鐘蹭紣娴ｆ粌褰存稉濞库偓?->
<!-       </p>-->

        <div class="hero-copy__meta">
          <span class="meta-pill">{{ greetingText }}，{{ userDisplayName }}</span>
          <span class="meta-pill">{{ todayLabel }}</span>
          <span class="meta-pill">{{ currentTimeLabel }}</span>
          <span class="meta-pill">{{ lastUpdatedText }}</span>
        </div>

        <div class="hero-copy__actions">
          <el-button type="primary" class="hero-action hero-action--primary" @click="goTo('/data')">
            进入数据工作台
          </el-button>
          <el-button plain class="hero-action hero-action--secondary" @click="goTo('/monitor/server')">
            查看服务监控
          </el-button>
        </div>
      </div>

      <div class="hero-dashboard">
        <div class="hero-score">
          <div>
            <span class="hero-score__label">系统健康度</span>
            <strong class="hero-score__value">{{ healthScore }}%</strong>
          </div>
          <div class="hero-score__status">
            <span class="hero-score__tag">{{ healthTone.label }}</span>
            <span class="hero-score__hint">{{ healthTone.description }}</span>
          </div>
        </div>

        <div class="hero-metrics">
          <div
            v-for="item in metricCards"
            :key="item.label"
            class="metric-card"
            :style="{ '--accent': item.accent }"
          >
            <div class="metric-card__head">
              <span>{{ item.label }}</span>
              <span>{{ item.value }}</span>
            </div>
            <div class="metric-card__detail">{{ item.detail }}</div>
            <div class="metric-card__bar">
              <span class="metric-card__bar-fill" :style="{ width: `${item.percent}%` }"></span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="home-grid">
      <el-card class="surface-card surface-card--trend" shadow="never">
        <div class="surface-card__head">
          <div>
            <p class="surface-card__eyebrow">Live Resource Trends</p>
            <h3 class="surface-card__title">资源态势</h3>
            <p class="surface-card__description">最近 {{ HISTORY_SIZE }} 个采样点内的 CPU、GPU、内存与网络吞吐变化。</p>
          </div>
          <el-button text class="surface-card__button" @click="refreshResourceData">
            <el-icon><component :is="refreshIcon" /></el-icon>
            手动刷新
          </el-button>
        </div>

        <div v-if="loadError" class="surface-card__notice">{{ loadError }}</div>
        <div ref="resourceTrendChartRef" class="resource-trend-chart"></div>

        <div class="snapshot-grid">
          <div
            v-for="item in snapshotItems"
            :key="item.label"
            class="snapshot-item"
          >
            <span class="snapshot-item__label">{{ item.label }}</span>
            <strong class="snapshot-item__value">{{ item.value }}</strong>
          </div>
        </div>
      </el-card>

      <el-card class="surface-card surface-card--command" shadow="never">
        <div class="surface-card__head">
          <div>
            <p class="surface-card__eyebrow">Quick Entry</p>
            <h3 class="surface-card__title">快捷入口</h3>
<!--            <p class="surface-card__description">閹跺﹤鐖堕悽銊ф畱婢堆囥€夐棃銏犲缂冾喖鍩屾＃鏍€夐敍灞藉櫤鐏忔垶娼甸崶鐐插瀼閹广垺鍨氶張顑锯偓?/p>-->
          </div>
        </div>

        <div class="command-grid">
          <button
            v-for="item in commandCards"
            :key="item.key"
            type="button"
            class="command-card"
            :style="{ '--accent': item.accent }"
            @click="goTo(item.path)"
          >
            <span class="command-card__icon">
              <el-icon><component :is="item.icon" /></el-icon>
            </span>
            <span class="command-card__body">
              <strong class="command-card__title">{{ item.title }}</strong>
              <span class="command-card__desc">{{ item.description }}</span>
            </span>
            <el-icon class="command-card__arrow"><component :is="arrowIcon" /></el-icon>
          </button>
        </div>
      </el-card>

    </section>
  </div>
</template>

<script setup name="Index">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import useUserStore from '@/store/modules/user'
import { getServer } from '@/api/monitor/server'

const POLL_INTERVAL = 3000
const HISTORY_SIZE = 10
const DEFAULT_HISTORY = Array.from({ length: HISTORY_SIZE }, () => ({
  label: '--:--:--',
  cpu: 0,
  gpu: 0,
  mem: 0,
  net: 0
}))

const router = useRouter()
const userStore = useUserStore()

const refreshIcon = ElementPlusIconsVue.RefreshRight || ElementPlusIconsVue.Refresh
const arrowIcon = ElementPlusIconsVue.ArrowRight || ElementPlusIconsVue.Right || ElementPlusIconsVue.Promotion

const commandCards = [
  {
    key: 'data',
    title: '数据管理',
    description: '统一管理项目、试验与数据资产。',
    path: '/data',
    accent: '#12b886',
    icon: ElementPlusIconsVue.FolderOpened || ElementPlusIconsVue.Document || ElementPlusIconsVue.Tickets
  },
  {
    key: 'simulation',
    title: '数据仿真',
    description: '生成多源模拟样本，快速组织实验输入。',
    path: '/DataSimulation',
    accent: '#339af0',
    icon: ElementPlusIconsVue.Share || ElementPlusIconsVue.Connection || ElementPlusIconsVue.Monitor
  },
  {
    key: 'ml',
    title: '数据分析',
    description: '配置模型参数、策略与训练方向。',
    path: '/dataAnalytical?url=http://10.1.22.71:8089/home/dataSourceAnaly&name=数据分析',
    accent: '#f08c00',
    icon: ElementPlusIconsVue.TrendCharts || ElementPlusIconsVue.Histogram || ElementPlusIconsVue.Cpu
  },
  {
    key: 'logs',
    title: '系统日志',
    description: '按文件、级别和内容定位问题线索。',
    path: '/system/log/systemLog',
    accent: '#e03131',
    icon: ElementPlusIconsVue.Document || ElementPlusIconsVue.Tickets || ElementPlusIconsVue.Monitor
  },
  {
    key: 'monitor',
    title: '服务监控',
    description: '查看主机、JVM、磁盘与运行状态。',
    path: '/monitor/server',
    accent: '#0c8599',
    icon: ElementPlusIconsVue.Monitor || ElementPlusIconsVue.Cpu
  }
]

const resourceTrendChartRef = ref(null)

const serverInfo = ref(null)
const resourceHistory = ref(DEFAULT_HISTORY)
const lastUpdatedAt = ref('')
const loadError = ref('')
const now = ref(new Date())

let workflowChart = null
let resourceTrendChart = null
let pollTimer = null
let clockTimer = null
let isRequestPending = false

const clampPercent = (value) => {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) {
    return 0
  }
  return Math.max(0, Math.min(100, Number(numericValue.toFixed(2))))
}

const normalizeRate = (value) => {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue) || numericValue < 0) {
    return 0
  }
  return Number(numericValue.toFixed(2))
}

const formatNumber = (value, digits = 1) => {
  const numericValue = Number(value)
  if (!Number.isFinite(numericValue)) {
    return '--'
  }
  return numericValue.toFixed(digits)
}

const formatPercent = (value) => `${clampPercent(value).toFixed(0)}%`

const formatRate = (value) => {
  const rate = normalizeRate(value)
  if (rate >= 1024 * 1024) {
    return `${(rate / (1024 * 1024)).toFixed(2)} MB/s`
  }
  if (rate >= 1024) {
    return `${(rate / 1024).toFixed(2)} KB/s`
  }
  return `${rate.toFixed(0)} B/s`
}

const formatRateAxis = (value) => {
  const rate = normalizeRate(value)
  if (rate >= 1024 * 1024) {
    return `${(rate / (1024 * 1024)).toFixed(1)}M`
  }
  if (rate >= 1024) {
    return `${(rate / 1024).toFixed(0)}K`
  }
  return `${rate.toFixed(0)}`
}

const formatTimeLabel = (value = new Date()) => {
  const date = value instanceof Date ? value : new Date(value)
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(date)
}

const formatDateLabel = (value = new Date()) => {
  const date = value instanceof Date ? value : new Date(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  }).format(date)
}

const userDisplayName = computed(() => userStore.nickName || userStore.name || '管理员')
const greetingText = computed(() => {
  const hour = now.value.getHours()
  if (hour < 6) return '夜间值守中'
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const todayLabel = computed(() => formatDateLabel(now.value))
const currentTimeLabel = computed(() => formatTimeLabel(now.value))

const lastUpdatedText = computed(() => lastUpdatedAt.value ? `最近采样 ${lastUpdatedAt.value}` : '等待首次采样')

const cpuUsage = computed(() => clampPercent(serverInfo.value?.cpu?.used))
const gpuUsage = computed(() => clampPercent(serverInfo.value?.gpu?.usage))
const memUsage = computed(() => clampPercent(serverInfo.value?.mem?.usage))

const peakDisk = computed(() => {
  const files = Array.isArray(serverInfo.value?.sysFiles) ? serverInfo.value.sysFiles : []
  return files.slice().sort((left, right) => clampPercent(right?.usage) - clampPercent(left?.usage))[0] || null
})

const diskUsage = computed(() => clampPercent(peakDisk.value?.usage))

const healthScore = computed(() => {
  const weightedLoad = cpuUsage.value * 0.32 + memUsage.value * 0.26 + gpuUsage.value * 0.18 + diskUsage.value * 0.24
  return Math.max(0, Math.min(100, Math.round(100 - weightedLoad)))
})

const healthTone = computed(() => {
  if (loadError.value) {
    return {
      label: '采集中断',
      description: '监控服务暂时不可用，请手动刷新或检查后端。'
    }
  }
  if (healthScore.value >= 85) {
    return {
      label: '运行平稳',
      description: '资源曲线健康，系统适合继续推进核心任务。'
    }
  }
  if (healthScore.value >= 70) {
    return {
      label: '轻度波动',
      description: '存在局部压力，但整体仍在安全范围内。'
    }
  }
  if (healthScore.value >= 50) {
    return {
      label: '需要关注',
      description: '建议优先检查资源瓶颈与关键服务状态。'
    }
  }
  return {
    label: '压力偏高',
    description: '建议先处置监控告警，再继续推进实验链路。'
  }
})

const metricCards = computed(() => [
  {
    label: 'CPU',
    value: formatPercent(cpuUsage.value),
    detail: `${serverInfo.value?.cpu?.cpuNum || '--'} 核处理资源`,
    percent: cpuUsage.value,
    accent: '#4ecdc4'
  },
  {
    label: 'GPU',
    value: formatPercent(gpuUsage.value),
    detail: serverInfo.value?.gpu ? '并行计算资源实时采集' : '当前未采集到 GPU 数据',
    percent: gpuUsage.value,
    accent: '#ffd166'
  },
  {
    label: '内存',
    value: formatPercent(memUsage.value),
    detail: `${formatNumber(serverInfo.value?.mem?.used)} / ${formatNumber(serverInfo.value?.mem?.total)} G`,
    percent: memUsage.value,
    accent: '#ff8787'
  },
  {
    label: '磁盘峰值',
    value: formatPercent(diskUsage.value),
    detail: peakDisk.value?.dirName ? `${peakDisk.value.dirName} 占用最高` : '暂无磁盘明细',
    percent: diskUsage.value,
    accent: '#74c0fc'
  }
])

const snapshotItems = computed(() => [
  {
    label: '主机名',
    value: serverInfo.value?.sys?.computerName || '--'
  },
  {
    label: '操作系统',
    value: [serverInfo.value?.sys?.osName, serverInfo.value?.sys?.osArch].filter(Boolean).join(' / ') || '--'
  },
  {
    label: 'JVM 版本',
    value: serverInfo.value?.jvm?.version || '--'
  },
  {
    label: '运行时长',
    value: serverInfo.value?.jvm?.runTime || '--'
  }
])

const getResourceTrendOption = (history = resourceHistory.value) => ({
  animationDuration: 600,
  color: ['#4ecdc4', '#ffd166', '#ff8787', '#4dabf7'],
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(10, 21, 42, 0.92)',
    borderWidth: 0,
    padding: 12,
    textStyle: {
      color: '#eef4ff'
    },
    formatter: (params) => {
      const point = params?.[0]?.axisValue || '--'
      const cpuItem = params.find((item) => item.seriesName === 'CPU')
      const gpuItem = params.find((item) => item.seriesName === 'GPU')
      const memItem = params.find((item) => item.seriesName === '内存')
      const netItem = params.find((item) => item.seriesName === '网络')
      return [
        point,
        `CPU：${formatPercent(cpuItem?.value)}`,
        `GPU：${formatPercent(gpuItem?.value)}`,
        `内存：${formatPercent(memItem?.value)}`,
        `网络：${formatRate(netItem?.value)}`
      ].join('<br/>')
    }
  },
  legend: {
    top: 0,
    right: 0,
    itemWidth: 10,
    itemHeight: 10,
    textStyle: {
      color: '#6b7a90',
      fontSize: 12
    }
  },
  grid: {
    top: 44,
    right: 64,
    bottom: 26,
    left: 44
  },
  xAxis: {
    type: 'category',
    boundaryGap: true,
    data: history.map((item) => item.label),
    axisTick: {
      show: false
    },
    axisLine: {
      lineStyle: {
        color: 'rgba(104, 124, 153, 0.22)'
      }
    },
    axisLabel: {
      color: '#73819a',
      fontSize: 11
    }
  },
  yAxis: [
    {
      type: 'value',
      min: 0,
      max: 100,
      splitNumber: 5,
      axisLabel: {
        color: '#73819a',
        formatter: '{value}%'
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(104, 124, 153, 0.12)'
        }
      }
    },
    {
      type: 'value',
      min: 0,
      axisLabel: {
        color: '#73819a',
        formatter: (value) => formatRateAxis(value)
      },
      splitLine: {
        show: false
      }
    }
  ],
  series: [
    {
      name: 'CPU',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: history.map((item) => item.cpu),
      lineStyle: {
        width: 2
      },
      itemStyle: {
        color: '#4ecdc4'
      }
    },
    {
      name: 'GPU',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: history.map((item) => item.gpu),
      lineStyle: {
        width: 2
      },
      itemStyle: {
        color: '#ffd166'
      }
    },
    {
      name: '内存',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: history.map((item) => item.mem),
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(255, 135, 135, 0.28)' },
          { offset: 1, color: 'rgba(255, 135, 135, 0.02)' }
        ])
      },
      lineStyle: {
        width: 2
      },
      itemStyle: {
        color: '#ff8787'
      }
    },
    {
      name: '网络',
      type: 'bar',
      yAxisIndex: 1,
      barWidth: 12,
      data: history.map((item) => item.net),
      itemStyle: {
        color: 'rgba(77, 171, 247, 0.4)',
        borderRadius: [8, 8, 0, 0]
      }
    }
  ]
})

const initResourceTrendChart = () => {
  if (!resourceTrendChartRef.value) {
    return
  }

  resourceTrendChart = echarts.init(resourceTrendChartRef.value)
  resourceTrendChart.setOption(getResourceTrendOption())
}

const updateResourceHistory = (data) => {
  const nextPoint = {
    label: formatTimeLabel(new Date()),
    cpu: clampPercent(data?.cpu?.used),
    gpu: clampPercent(data?.gpu?.usage),
    mem: clampPercent(data?.mem?.usage),
    net: normalizeRate(data?.net?.totalRate)
  }

  resourceHistory.value = [...resourceHistory.value.slice(-(HISTORY_SIZE - 1)), nextPoint]
  resourceTrendChart?.setOption(getResourceTrendOption(resourceHistory.value))
}

const applyServerData = (data) => {
  serverInfo.value = data
  lastUpdatedAt.value = formatTimeLabel(new Date())
  loadError.value = ''
  updateResourceHistory(data)
}

const getServerInfo = async () => {
  const { data } = await getServer()
  return data
}

const scheduleNextPoll = (delay = POLL_INTERVAL) => {
  if (pollTimer) {
    clearTimeout(pollTimer)
  }
  pollTimer = window.setTimeout(loadResourceData, delay)
}

const loadResourceData = async () => {
  if (isRequestPending) {
    return
  }

  isRequestPending = true

  try {
    const data = await getServerInfo()
    now.value = new Date()
    applyServerData(data)
  } catch (error) {
    console.error('Failed to load server info:', error)
    loadError.value = '资源采集失败，请检查监控服务或稍后重试。'
  } finally {
    isRequestPending = false
    scheduleNextPoll()
  }
}

const refreshResourceData = async () => {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
  await loadResourceData()
}

const handleResize = () => {
  resourceTrendChart?.resize()
}

const goTo = (path) => {
  if (!path) {
    return
  }

  router.push(path).catch((error) => {
    console.error('Failed to navigate:', path, error)
  })
}

onMounted(() => {
  initResourceTrendChart()
  refreshResourceData()
  window.addEventListener('resize', handleResize)
  clockTimer = window.setInterval(() => {
    now.value = new Date()
  }, 30000)
})

onBeforeUnmount(() => {
  if (pollTimer) {
    clearTimeout(pollTimer)
  }
  if (clockTimer) {
    clearInterval(clockTimer)
  }
  window.removeEventListener('resize', handleResize)
  resourceTrendChart?.dispose()
})
</script>

<style scoped lang="scss">
.command-home {
  min-height: calc(100vh - 84px);
  padding: 22px;
  background:
    radial-gradient(circle at top left, rgba(20, 184, 166, 0.12), transparent 30%),
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.14), transparent 28%),
    linear-gradient(180deg, #f4f8fb 0%, #edf3f8 100%);
  font-family: "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.hero-panel {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.9fr);
  gap: 24px;
  padding: 30px;
  border-radius: 28px;
  background: linear-gradient(135deg, #091c34 0%, #0b3345 42%, #113b62 100%);
  color: #f6fbff;
  overflow: hidden;
  box-shadow: 0 28px 60px rgba(14, 33, 61, 0.2);
}

.hero-orbit {
  position: absolute;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  pointer-events: none;
  animation: orbitFloat 10s ease-in-out infinite;
}

.hero-orbit--one {
  width: 280px;
  height: 280px;
  top: -90px;
  right: -40px;
}

.hero-orbit--two {
  width: 180px;
  height: 180px;
  bottom: -70px;
  left: 48%;
  animation-duration: 12s;
}

.hero-copy,
.hero-dashboard {
  position: relative;
  z-index: 1;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.hero-copy__eyebrow,
.surface-card__eyebrow,
.focus-card__eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.hero-copy__eyebrow {
  color: rgba(191, 249, 235, 0.82);
}

.hero-copy__title {
  margin: 0;
  font-family: "Bahnschrift", "DIN Alternate", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: clamp(34px, 5vw, 54px);
  line-height: 1.08;
  letter-spacing: 0.02em;

  span {
    display: block;
    margin-top: 8px;
    color: #8ce99a;
  }
}

.hero-copy__summary {
  max-width: 640px;
  margin: 20px 0 0;
  font-size: 15px;
  line-height: 1.85;
  color: rgba(228, 240, 255, 0.8);
}

.hero-copy__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 20px;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.09);
  color: rgba(240, 247, 255, 0.92);
  font-size: 12px;
}

.hero-copy__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.hero-action {
  min-width: 148px;
  height: 42px;
  border-radius: 14px;
  font-weight: 600;
}

.hero-action--primary {
  background: linear-gradient(135deg, #14b8a6 0%, #0ea5e9 100%);
  border-color: transparent;
}

.hero-action--secondary {
  border-color: rgba(255, 255, 255, 0.16);
  color: #eef7ff;
  background: rgba(255, 255, 255, 0.05);
}

.hero-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-score {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 22px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(12px);
}

.hero-score__label {
  display: block;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(194, 232, 255, 0.72);
}

.hero-score__value {
  display: block;
  margin-top: 10px;
  font-family: "Bahnschrift", "DIN Alternate", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 42px;
  line-height: 1;
}

.hero-score__status {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  max-width: 180px;
}

.hero-score__tag {
  display: inline-flex;
  align-items: center;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(140, 233, 154, 0.14);
  color: #b2f2bb;
  font-size: 12px;
  font-weight: 700;
}

.hero-score__hint {
  font-size: 12px;
  line-height: 1.6;
  text-align: right;
  color: rgba(228, 240, 255, 0.74);
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  position: relative;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.07);
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    left: 18px;
    right: 18px;
    top: 0;
    height: 3px;
    border-radius: 999px;
    background: var(--accent);
    opacity: 0.95;
  }
}

.metric-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  font-weight: 700;
  color: #f8fbff;
}

.metric-card__detail {
  min-height: 36px;
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: rgba(228, 240, 255, 0.72);
}

.metric-card__bar {
  height: 7px;
  margin-top: 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  overflow: hidden;
}

.metric-card__bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--accent);
}

.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 0.92fr);
  gap: 20px;
  margin-top: 20px;
}

.surface-card {
  border: none;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(250, 252, 255, 0.92) 100%);
  box-shadow: 0 18px 42px rgba(14, 33, 61, 0.08);
  animation: riseIn 0.6s ease both;

  :deep(.el-card__body) {
    display: flex;
    flex-direction: column;
    gap: 18px;
    height: 100%;
    padding: 24px;
  }
}

.surface-card__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.surface-card__eyebrow {
  color: #0c8599;
}

.surface-card__title {
  margin: 0;
  font-family: "Bahnschrift", "DIN Alternate", "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 24px;
  color: #0f172a;
  letter-spacing: 0.01em;
}

.surface-card__description {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: #64748b;
}

.surface-card__button {
  padding: 0;
  color: #0c8599;
  font-weight: 600;
}

.surface-card__notice {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(224, 49, 49, 0.08);
  color: #c92a2a;
  font-size: 13px;
  line-height: 1.6;
}

.resource-trend-chart {
  width: 100%;
  min-height: 330px;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.snapshot-item {
  padding: 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(244, 248, 251, 0.96) 0%, rgba(240, 245, 250, 0.82) 100%);
  border: 1px solid rgba(15, 23, 42, 0.04);
}

.snapshot-item__label {
  display: block;
  font-size: 12px;
  color: #66758c;
}

.snapshot-item__value {
  display: block;
  margin-top: 8px;
  font-size: 16px;
  line-height: 1.5;
  color: #0f172a;
  word-break: break-word;
}

.command-grid {
  display: grid;
  gap: 12px;
}

.command-card {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 16px 18px;
  border: none;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(241, 245, 249, 0.98) 0%, rgba(248, 250, 252, 0.92) 100%);
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 14px 24px rgba(15, 23, 42, 0.08);
  }
}

.command-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  flex: 0 0 auto;
  font-size: 18px;
  color: var(--accent);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.04);
}

.command-card__body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.command-card__title {
  color: #0f172a;
  font-size: 15px;
  line-height: 1.4;
}

.command-card__desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.command-card__arrow {
  flex: 0 0 auto;
  font-size: 16px;
  color: #94a3b8;
}

.hero-panel,
.hero-score,
.surface-card,
.workflow-chart {
  border-radius: 16px;
}

.metric-card,
.snapshot-item,
.command-card,
.workflow-step,
.focus-card,
.alert-chip {
  border-radius: 10px;
}

.hero-action,
.focus-action {
  border-radius: 8px;
}

@keyframes orbitFloat {
  0%,
  100% {
    transform: translate3d(0, 0, 0);
  }
  50% {
    transform: translate3d(0, 10px, 0);
  }
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1200px) {
  .hero-panel,
  .home-grid {
    grid-template-columns: 1fr;
  }

  .snapshot-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .command-home {
    padding: 14px;
  }

  .hero-panel {
    padding: 22px;
    border-radius: 24px;
  }

  .hero-metrics,
  .snapshot-grid,
  .workflow-step-list {
    grid-template-columns: 1fr;
  }

  .surface-card {
    :deep(.el-card__body) {
      padding: 18px;
    }
  }

  .surface-card__head,
  .hero-score {
    flex-direction: column;
  }

  .hero-score__status {
    align-items: flex-start;
    max-width: none;
  }
}
</style>


