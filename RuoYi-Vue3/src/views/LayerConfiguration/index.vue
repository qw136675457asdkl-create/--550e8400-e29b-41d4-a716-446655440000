<template>
  <div class="app-container">
    <el-form
      ref="formRef"
      v-loading="loading"
      class="layer-config-form"
      :model="formData"
      :rules="rules"
      label-width="120px"
      v-hasPermi="['system:layerConfiguration:query']"
    >
      <el-row :gutter="15">
        <el-col :span="4">
          <el-form-item label="图层默认透明度" prop="defaultOpacity">
            <el-input-number
              v-model="formData.defaultOpacity"
              :min="0"
              :max="100"
              controls-position="right"
              :style="{ width: '100%' }"
            />
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-row :gutter="15">
            <el-col :span="8">
              <el-form-item label="默认背景颜色">
                <el-checkbox v-model="formData.useTransBg">使用透明背景</el-checkbox>
              </el-form-item>
            </el-col>
            <el-col :span="10">
              <el-form-item label-width="240px" label="图层背景色（不启用透明时生效）" prop="bgColor">
                <el-color-picker v-model="formData.bgColor" :disabled="formData.useTransBg" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-col>

        <el-col :span="24">
          <el-form-item label="全局文字颜色" prop="textColor">
            <el-color-picker v-model="formData.textColor" />
          </el-form-item>
        </el-col>

        <el-col :span="4">
          <el-form-item label="全局文字大小" prop="textSize">
            <el-input-number
              v-model="formData.textSize"
              :min="1"
              :max="72"
              controls-position="right"
              :style="{ width: '100%' }"
            />
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="图层启用状态" prop="layerCodes">
            <div class="layer-status-container">
              <el-checkbox v-model="allLayersEnabled">启用所有功能图层</el-checkbox>
              <el-checkbox-group
                v-if="!allLayersEnabled"
                v-model="formData.layerCodes"
                class="layer-status-group"
              >
                <el-checkbox
                  v-for="option in specificLayerOptions"
                  :key="option.code"
                  :label="option.code"
                  :value="option.code"
                >
                  {{ option.name }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </el-form-item>
        </el-col>

        <el-col :span="4">
          <el-form-item label="图层刷新频率" prop="refreshRate">
            <el-input-number
              v-model="formData.refreshRate"
              :min="1"
              :max="3600"
              controls-position="right"
              :style="{ width: '100%' }"
            />
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="图层悬浮提示">
            <el-checkbox v-model="formData.showFloatTip">启用图层悬浮显示名称及状态提示</el-checkbox>
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="图层异常提示">
            <el-checkbox v-model="formData.showErrorWind">图层加载失败时显示弹窗提示</el-checkbox>
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="图层数据缓存">
            <el-checkbox v-model="formData.enableCache">启用图层数据本地缓存（提升重复加载速度）</el-checkbox>
          </el-form-item>
        </el-col>

        <el-col :span="4">
          <el-form-item label="缓存过期时间" prop="cacheExpireTime">
            <el-input-number
              v-model="formData.cacheExpireTime"
              :min="1"
              :max="10080"
              :disabled="!formData.enableCache"
              controls-position="right"
              :style="{ width: '100%' }"
            />
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item>
            <el-button type="primary" @click="submitForm" v-hasPermi="['system:layerConfiguration:save']">
              保存全局配置
            </el-button>
            <el-button @click="resetForm" v-hasPermi="['system:layerConfiguration:reset']">
              恢复默认配置
            </el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, toRefs } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getLayerConfiguration, resetLayerConfiguration, saveLayerConfiguration } from '@/api/layerConfiguration'

const LAYER_OPTIONS = [
  { code: 'ALL', name: '启用所有功能图层' },
  { code: 'RADAR', name: '飞机雷达图层' },
  { code: 'DASHBOARD', name: '仪表盘图层' },
  { code: 'ADSB', name: 'ADS-B应答数据面板图层' },
  { code: 'NAVIGATION', name: '导航图层' }
]

const DEFAULT_BG_COLOR = '#00EEFF'
const DEFAULT_TEXT_COLOR = '#000000'
const ALL_LAYER_CODE = 'ALL'
const SPECIFIC_LAYER_OPTIONS = LAYER_OPTIONS.filter((item) => item.code !== ALL_LAYER_CODE)
const ALL_SPECIFIC_LAYER_CODES = SPECIFIC_LAYER_OPTIONS.map((item) => item.code)

function createDefaultFormData() {
  return {
    defaultOpacity: 90,
    useTransBg: true,
    bgColor: DEFAULT_BG_COLOR,
    textColor: DEFAULT_TEXT_COLOR,
    textSize: 12,
    layerCodes: [ALL_LAYER_CODE],
    refreshRate: 10,
    showFloatTip: true,
    showErrorWind: true,
    enableCache: true,
    cacheExpireTime: 30
  }
}

const formRef = ref()
const loading = ref(false)
const lastSpecificLayerCodes = ref([...ALL_SPECIFIC_LAYER_CODES])

const data = reactive({
  formData: createDefaultFormData(),
  rules: {
    defaultOpacity: [{ required: true, message: '请输入图层默认透明度', trigger: 'blur' }],
    bgColor: [{ required: true, message: '请选择图层背景色', trigger: 'change' }],
    textColor: [{ required: true, message: '请选择全局文字颜色', trigger: 'change' }],
    textSize: [{ required: true, message: '请输入全局文字大小', trigger: 'blur' }],
    layerCodes: [{ validator: validateLayerCodes, trigger: 'change' }],
    refreshRate: [{ required: true, message: '请输入图层刷新频率', trigger: 'blur' }],
    cacheExpireTime: [{ required: true, message: '请输入缓存过期时间', trigger: 'blur' }]
  }
})

const { formData, rules } = toRefs(data)

const specificLayerOptions = computed(() => SPECIFIC_LAYER_OPTIONS)

const allLayersEnabled = computed({
  get() {
    return formData.value.layerCodes.includes(ALL_LAYER_CODE)
  },
  set(enabled) {
    if (enabled) {
      const currentSpecificCodes = normalizeSpecificLayerCodes(formData.value.layerCodes)
      if (currentSpecificCodes.length > 0) {
        lastSpecificLayerCodes.value = currentSpecificCodes
      }
      formData.value.layerCodes = [ALL_LAYER_CODE]
      return
    }
    formData.value.layerCodes = lastSpecificLayerCodes.value.length > 0
      ? [...lastSpecificLayerCodes.value]
      : []
  }
})

function validateLayerCodes(rule, value, callback) {
  if (Array.isArray(value) && value.length > 0) {
    callback()
    return
  }
  callback(new Error('请至少启用一个功能图层'))
}

function normalizeSpecificLayerCodes(layerCodes) {
  const selectedCodes = Array.isArray(layerCodes) ? layerCodes : []
  return SPECIFIC_LAYER_OPTIONS
    .map((item) => item.code)
    .filter((code) => selectedCodes.includes(code))
}

function resolveSelectedLayerCodes(layerStatuses) {
  if (!Array.isArray(layerStatuses) || layerStatuses.length === 0) {
    return [ALL_LAYER_CODE]
  }
  const selectedCodes = layerStatuses
    .filter((item) => item && item.isEnabled)
    .map((item) => item.layerCode)
    .filter(Boolean)

  if (selectedCodes.includes(ALL_LAYER_CODE)) {
    return [ALL_LAYER_CODE]
  }
  return normalizeSpecificLayerCodes(selectedCodes)
}

function buildLayerStatuses(layerCodes) {
  const selectedCodes = Array.isArray(layerCodes) ? layerCodes : []
  const enableAll = selectedCodes.includes(ALL_LAYER_CODE)
  return LAYER_OPTIONS.map((item) => ({
    layerCode: item.code,
    layerName: item.name,
    isEnabled: enableAll ? item.code === ALL_LAYER_CODE : selectedCodes.includes(item.code)
  }))
}

function buildFormData(config = {}) {
  const selectedLayerCodes = resolveSelectedLayerCodes(config.layerStatuses)
  const specificCodes = selectedLayerCodes.includes(ALL_LAYER_CODE)
    ? [...ALL_SPECIFIC_LAYER_CODES]
    : normalizeSpecificLayerCodes(selectedLayerCodes)

  lastSpecificLayerCodes.value = specificCodes

  return {
    defaultOpacity: Number.isInteger(config.defaultOpacity) ? config.defaultOpacity : 90,
    useTransBg: config.useTransBg !== false,
    bgColor: config.bgColor || DEFAULT_BG_COLOR,
    textColor: config.textColor || DEFAULT_TEXT_COLOR,
    textSize: Number.isInteger(config.textSize) ? config.textSize : 12,
    layerCodes: selectedLayerCodes,
    refreshRate: Number.isInteger(config.refreshRate) ? config.refreshRate : 10,
    showFloatTip: config.showFloatTip !== false,
    showErrorWind: config.showErrorWind !== false,
    enableCache: config.enableCache !== false,
    cacheExpireTime: Number.isInteger(config.cacheExpireTime) ? config.cacheExpireTime : 30
  }
}

function buildRequestPayload() {
  return {
    defaultOpacity: formData.value.defaultOpacity,
    useTransBg: formData.value.useTransBg,
    bgColor: formData.value.bgColor,
    textColor: formData.value.textColor,
    textSize: formData.value.textSize,
    refreshRate: formData.value.refreshRate,
    showFloatTip: formData.value.showFloatTip,
    showErrorWind: formData.value.showErrorWind,
    enableCache: formData.value.enableCache,
    cacheExpireTime: formData.value.cacheExpireTime,
    layerStatuses: buildLayerStatuses(formData.value.layerCodes)
  }
}

function submitForm() {
  formRef.value.validate((valid) => {
    if (!valid) {
      return
    }
    loading.value = true
    saveLayerConfiguration(buildRequestPayload())
      .then(() => {
        ElMessage.success('保存成功')
        loadConfiguration()
      })
      .catch((error) => {
        console.error('保存图层配置失败:', error)
        ElMessage.error('保存失败，请稍后重试')
      })
      .finally(() => {
        loading.value = false
      })
  })
}

function resetForm() {
  ElMessageBox.confirm('确定要恢复默认配置吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      loading.value = true
      resetLayerConfiguration()
        .then(() => {
          ElMessage.success('已恢复默认配置')
          loadConfiguration()
        })
        .catch((error) => {
          console.error('重置图层配置失败:', error)
          ElMessage.error('重置失败，请稍后重试')
        })
        .finally(() => {
          loading.value = false
        })
    })
    .catch(() => {})
}

function loadConfiguration() {
  loading.value = true
  getLayerConfiguration()
    .then((res) => {
      const config = res && res.data ? res.data : res
      Object.assign(formData.value, buildFormData(config || {}))
    })
    .catch((error) => {
      console.error('加载图层配置失败:', error)
      ElMessage.error('加载配置失败，请稍后重试')
    })
    .finally(() => {
      loading.value = false
    })
}

onMounted(() => {
  loadConfiguration()
})
</script>

<style scoped>
.layer-config-form {
  width: 100%;
}

.el-form-item {
  margin-bottom: 35px;
}

.layer-status-container {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.layer-status-group {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
</style>
