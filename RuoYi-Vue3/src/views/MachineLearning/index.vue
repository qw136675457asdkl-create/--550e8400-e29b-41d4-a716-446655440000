<template>
  <div class="app-container">
    <el-form
      ref="formRef"
      v-loading="loading"
      class="machine-learning-form"
      :model="formData"
      :rules="rules"
      size="default"
      label-width="150px"
    >
      <el-row :gutter="20" class="config-grid">
        <el-col :xs="24" :lg="12">
          <div class="config-panel config-panel--runner">
            <div class="config-panel__title">Python 代码执行器</div>

            <div class="runner-section runner-section--python">
              <div class="runner-section__header">
                <div class="runner-section__subtitle">
                  输入 Python 代码并发送到后端执行，结果会显示在下方。
                </div>
              </div>

              <div class="runner-editor-wrap" @keydown.ctrl.enter.prevent="runPythonExecutor">
                <el-input
                  v-model="pythonRunner.code"
                  type="textarea"
                  class="runner-editor"
                  resize="vertical"
                  :autosize="{ minRows: 11, maxRows: 18 }"
                  placeholder="例如：print(&quot;Hello, World!&quot;)"
                />
              </div>

              <div class="runner-actions">
                <el-button
                  type="primary"
                  :loading="pythonRunner.loading"
                  v-hasPermi="['system:machineLearning:execute']"
                  @click="runPythonExecutor"
                >
                  发送代码
                </el-button>
                <el-button :disabled="pythonRunner.loading" @click="clearRunner(pythonRunner)">
                  清空
                </el-button>
              </div>

              <el-alert
                v-if="pythonRunner.statusMessage"
                class="runner-status"
                :title="pythonRunner.statusMessage"
                :type="pythonRunner.statusType"
                :closable="false"
                show-icon
              />

              <div v-if="pythonRunner.resultVisible" class="runner-result">
                <div class="runner-result__header">
                  <div class="runner-result__title">
                    {{ pythonRunner.resultSuccess ? '执行成功' : '执行失败' }}
                  </div>
                  <div class="runner-result__meta">
                    {{ buildResultMeta(pythonRunner) || '等待结果' }}
                  </div>
                </div>

                <div class="runner-result__body">
                  <div class="runner-result__section">
                    <div class="runner-result__section-title">stdout</div>
                    <pre class="runner-result__pre">{{ pythonRunner.stdout || '(无输出)' }}</pre>
                  </div>

                  <div class="runner-result__section">
                    <div class="runner-result__section-title">stderr</div>
                    <pre class="runner-result__pre runner-result__pre--error">{{ pythonRunner.stderr || '(无错误输出)' }}</pre>
                  </div>
                </div>
              </div>

              <div class="runner-tip">支持 `Ctrl + Enter` 快速发送。</div>
            </div>
          </div>
        </el-col>

        <el-col :xs="24" :lg="12">
          <div class="config-panel config-panel--runner">
            <div class="config-panel__title">Matlab 代码执行器</div>

            <div class="runner-section runner-section--matlab">
              <div class="runner-section__header">
                <div class="runner-section__subtitle">
                  输入 Matlab 代码并发送到后端执行，可在下方查看 stdout 与 stderr。
                </div>
              </div>

              <div class="runner-examples">
                <span class="runner-examples__label">示例：</span>
                <button
                  type="button"
                  class="runner-example"
                  @click="applyMatlabExample('basic')"
                >
                  基础计算
                </button>
                <button
                  type="button"
                  class="runner-example"
                  @click="applyMatlabExample('matrix')"
                >
                  矩阵运算
                </button>
                <button
                  type="button"
                  class="runner-example"
                  @click="applyMatlabExample('plot')"
                >
                  绘图
                </button>
              </div>

              <div class="runner-editor-wrap" @keydown.ctrl.enter.prevent="runMatlabExecutor">
                <el-input
                  v-model="matlabRunner.code"
                  type="textarea"
                  class="runner-editor"
                  resize="vertical"
                  :autosize="{ minRows: 12, maxRows: 20 }"
                  placeholder="例如：disp('Hello Matlab')"
                />
              </div>

              <div class="runner-actions">
                <el-button
                  type="primary"
                  :loading="matlabRunner.loading"
                  v-hasPermi="['system:machineLearning:execute']"
                  @click="runMatlabExecutor"
                >
                  发送代码
                </el-button>
                <el-button :disabled="matlabRunner.loading" @click="clearRunner(matlabRunner)">
                  清空
                </el-button>
              </div>

              <el-alert
                v-if="matlabRunner.statusMessage"
                class="runner-status"
                :title="matlabRunner.statusMessage"
                :type="matlabRunner.statusType"
                :closable="false"
                show-icon
              />

              <div v-if="matlabRunner.resultVisible" class="runner-result">
                <div class="runner-result__header">
                  <div class="runner-result__title">
                    {{ matlabRunner.resultSuccess ? '执行成功' : '执行失败' }}
                  </div>
                  <div class="runner-result__meta">
                    {{ buildResultMeta(matlabRunner) || '等待结果' }}
                  </div>
                </div>

                <div class="runner-result__body">
                  <div class="runner-result__section">
                    <div class="runner-result__section-title">stdout</div>
                    <pre class="runner-result__pre">{{ matlabRunner.stdout || '(无输出)' }}</pre>
                  </div>

                  <div class="runner-result__section">
                    <div class="runner-result__section-title">stderr</div>
                    <pre class="runner-result__pre runner-result__pre--error">{{ matlabRunner.stderr || '(无错误输出)' }}</pre>
                  </div>
                </div>
              </div>

              <div class="runner-tip">支持 `Ctrl + Enter` 快速发送。</div>
            </div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="config-grid config-grid--stacked">
        <el-col :xs="24" :lg="12">
          <div class="config-panel">
            <div class="config-panel__title">Python 环境配置</div>

            <el-form-item label="Python 解释器路径" prop="field119">
              <el-input
                v-model="formData.field119"
                type="text"
                placeholder="请输入 Python 解释器路径（如 /usr/local/python313/bin/python3.13）"
                clearable
                :style="fullWidthStyle"
              />
            </el-form-item>

            <el-form-item label="Python 版本选择" prop="field104">
              <el-input
                v-model="formData.field104"
                type="text"
                placeholder="请输入 Python 版本"
                clearable
                :style="fullWidthStyle"
              />
            </el-form-item>

            <el-form-item label="自动安装依赖库" prop="field105">
              <el-checkbox-group v-model="formData.field105">
                <el-checkbox
                  v-for="item in pythonDependencyOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="依赖库安装源" prop="field107">
              <el-select
                v-model="formData.field107"
                placeholder="请选择依赖库安装源"
                clearable
                :style="fullWidthStyle"
              >
                <el-option
                  v-for="item in pythonSourceOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="虚拟环境启用" prop="field108">
              <el-checkbox-group v-model="formData.field108">
                <el-checkbox
                  v-for="item in pythonEnvOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="虚拟环境路径" prop="field109">
              <el-input
                v-model="formData.field109"
                type="text"
                placeholder="请输入虚拟环境根目录"
                :disabled="true"
                clearable
                :style="fullWidthStyle"
              />
            </el-form-item>

            <el-form-item label="虚拟环境自动创建" prop="field111">
              <el-checkbox-group v-model="formData.field111" :disabled="true">
                <el-checkbox
                  v-for="item in pythonAutoCreateOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="常用库版本锁定" prop="field112">
              <el-checkbox-group v-model="formData.field112">
                <el-checkbox
                  v-for="item in pythonLockOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="Python 自动启动" prop="field113">
              <el-checkbox-group v-model="formData.field113">
                <el-checkbox
                  v-for="item in pythonAutoStartOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="配置后自动验证" prop="field114">
              <el-checkbox-group v-model="formData.field114">
                <el-checkbox
                  v-for="item in pythonVerifyOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="环境配置日志" prop="field115">
              <el-checkbox-group v-model="formData.field115">
                <el-checkbox
                  v-for="item in pythonLogOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </div>
        </el-col>

        <el-col :xs="24" :lg="12">
          <div class="config-panel">
            <div class="config-panel__title">Matlab 环境配置</div>

            <el-form-item label="Matlab 安装路径" prop="field124">
              <el-input
                v-model="formData.field124"
                type="text"
                placeholder="请输入 Matlab 可执行文件路径（如 /usr/local/bin/matlab）"
                clearable
                :style="fullWidthStyle"
              />
            </el-form-item>

            <el-form-item label="Matlab 版本选择" prop="field125">
              <el-input
                v-model="formData.field125"
                type="text"
                placeholder="请输入 Matlab 版本"
                clearable
                :style="fullWidthStyle"
              />
            </el-form-item>

            <el-form-item label="Matlab 引擎 API 启用" prop="field126">
              <el-checkbox-group v-model="formData.field126">
                <el-checkbox
                  v-for="item in matlabEngineOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="Matlab 自动启动" prop="field127">
              <el-checkbox-group v-model="formData.field127">
                <el-checkbox
                  v-for="item in matlabAutoStartOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="缓存自动清理" prop="field128">
              <el-checkbox-group v-model="formData.field128">
                <el-checkbox
                  v-for="item in matlabCacheOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="自动配置环境变量" prop="field129">
              <el-checkbox-group v-model="formData.field129">
                <el-checkbox
                  v-for="item in matlabEnvVarOptions"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="启动执行脚本" prop="field131">
              <el-input
                v-model="formData.field131"
                type="text"
                placeholder="请输入 Matlab 启动脚本路径（.m 文件，可选）"
                :disabled="true"
                clearable
                :style="fullWidthStyle"
              />
            </el-form-item>
          </div>
        </el-col>
      </el-row>

      <el-form-item class="action-bar">
        <el-button type="primary" @click="submitForm" v-hasPermi="['system:machineLearning:save']">保存环境配置</el-button>
        <el-button @click="resetForm" v-hasPermi="['system:machineLearning:reset']">恢复默认配置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import {
  cancelMatlabTask,
  executeMatlabCode,
  executePythonCode,
  getMachineLearningConfiguration,
  resetMachineLearningConfiguration,
  saveMachineLearningConfiguration
} from '@/api/machineLearning'

const fullWidthStyle = { width: '100%' }
const DEFAULT_PYTHON_PATH = '/usr/local/python313/bin/python3.13'
const DEFAULT_PYTHON_VERSION = 'Python 3.13'
const DEFAULT_MATLAB_PATH = '/usr/local/bin/matlab'
const DEFAULT_MATLAB_VERSION = 'R2024A'
const MACHINE_LEARNING_PERMISSION = Object.freeze({
  execute: 'system:machineLearning:execute',
  save: 'system:machineLearning:save',
  reset: 'system:machineLearning:reset'
})
const MACHINE_LEARNING_LOAD_PERMISSIONS = Object.freeze([
  MACHINE_LEARNING_PERMISSION.execute,
  MACHINE_LEARNING_PERMISSION.save,
  MACHINE_LEARNING_PERMISSION.reset
])

const DEFAULT_PYTHON_CODE = `print("Hello, World!")
for i in range(3):
    print(f"这是第 {i + 1} 行输出")`

const DEFAULT_MATLAB_CODE = `% 简单的 MATLAB 示例
x = 1:10;
y = x.^2;
disp('x 的值:');
disp(x);
disp('x 的平方:');
disp(y);

avg = mean(y);
fprintf('y 的平均值是: %.2f\\n', avg);`

const MATLAB_EXAMPLES = {
  basic: `% 基础计算示例
a = 5;
b = 10;
c = a + b;
disp(['a + b = ', num2str(c)]);

for i = 1:5
    fprintf('第 %d 次循环\\n', i);
end`,
  matrix: `% 矩阵运算示例
A = [1 2 3; 4 5 6; 7 8 9];
B = [9 8 7; 6 5 4; 3 2 1];

disp('矩阵 A:');
disp(A);
disp('矩阵 B:');
disp(B);

C = A + B;
disp('A + B =');
disp(C);

D = A * B;
disp('A * B =');
disp(D);

eigA = eig(A);
disp('A 的特征值:');
disp(eigA);`,
  plot: `% 绘图示例
x = 0:0.1:2*pi;
y1 = sin(x);
y2 = cos(x);

figure;
plot(x, y1, 'b-', 'LineWidth', 2);
hold on;
plot(x, y2, 'r--', 'LineWidth', 2);
grid on;
xlabel('x');
ylabel('y');
title('正弦和余弦函数');
legend('sin(x)', 'cos(x)');

disp('图形已绘制，请在 MATLAB 图形窗口中查看。')`
}

const pythonDependencyOptions = [
  {
    label: '配置完成后自动安装常用机器学习库（numpy、pandas、scikit-learn 等）',
    value: '配置完成后自动安装常用机器学习库（numpy、pandas、scikit-learn 等）'
  }
]

const pythonSourceOptions = [
  { label: '官方源（默认）', value: 1 },
  { label: '阿里镜像源', value: 2 },
  { label: '清华大学镜像源', value: 3 },
  { label: '中国科学技术大学镜像源', value: 4 }
]

const pythonEnvOptions = [
  {
    label: '使用独立虚拟环境（venv/conda）',
    value: '使用独立虚拟环境（venv/conda）'
  }
]

const pythonAutoCreateOptions = [
  {
    label: '若路径不存在，自动创建虚拟环境',
    value: '若路径不存在，自动创建虚拟环境'
  }
]

const pythonLockOptions = [
  {
    label: '锁定核心库版本，避免版本冲突',
    value: '锁定核心库版本，避免版本冲突'
  }
]

const pythonAutoStartOptions = [
  {
    label: '软件启动时自动启动 Python 后台服务（无需手动启动）',
    value: '软件启动时自动启动 Python 后台服务（无需手动启动）'
  }
]

const pythonVerifyOptions = [
  {
    label: '保存配置后，自动验证 Python 环境是否可用',
    value: '保存配置后，自动验证 Python 环境是否可用'
  }
]

const pythonLogOptions = [
  {
    label: '生成环境配置日志文件（便于排查问题）',
    value: '生成环境配置日志文件（便于排查问题）'
  }
]

const matlabEngineOptions = [
  {
    label: '启用 Matlab Engine API for Python（支持 Python 调用 Matlab 函数）',
    value: '启用 Matlab Engine API for Python（支持 Python 调用 Matlab 函数）'
  }
]

const matlabAutoStartOptions = [
  {
    label: '软件启动时自动启动 Matlab 后台服务',
    value: '软件启动时自动启动 Matlab 后台服务'
  }
]

const matlabCacheOptions = [
  {
    label: '关闭软件时自动清理 Matlab 临时缓存文件',
    value: '关闭软件时自动清理 Matlab 临时缓存文件'
  }
]

const matlabEnvVarOptions = [
  {
    label: '自动添加 Matlab 路径到系统环境变量（无需手动配置）',
    value: '自动添加 Matlab 路径到系统环境变量（无需手动配置）'
  }
]

function createDefaultFormData() {
  return {
    field119: DEFAULT_PYTHON_PATH,
    field104: DEFAULT_PYTHON_VERSION,
    field105: [pythonDependencyOptions[0].value],
    field107: pythonSourceOptions[0].value,
    field108: [pythonEnvOptions[0].value],
    field109: undefined,
    field111: [pythonAutoCreateOptions[0].value],
    field112: [pythonLockOptions[0].value],
    field113: [],
    field114: [pythonVerifyOptions[0].value],
    field115: [pythonLogOptions[0].value],
    field124: DEFAULT_MATLAB_PATH,
    field125: DEFAULT_MATLAB_VERSION,
    field126: [matlabEngineOptions[0].value],
    field127: [],
    field128: [matlabCacheOptions[0].value],
    field129: [matlabEnvVarOptions[0].value],
    field131: undefined
  }
}

function createRunnerState(defaultCode) {
  return {
    code: defaultCode,
    loading: false,
    statusMessage: '',
    statusType: 'success',
    resultVisible: false,
    resultSuccess: false,
    stdout: '',
    stderr: '',
    exitCode: null,
    durationMs: null
  }
}

const rules = {
  field119: [{ required: true, message: '请输入 Python 解释器路径', trigger: 'blur' }],
  field104: [{ required: true, message: '请输入 Python 版本', trigger: 'blur' }],
  field105: [],
  field107: [{ required: true, message: '请选择依赖库安装源', trigger: 'change' }],
  field108: [],
  field109: [],
  field111: [],
  field112: [],
  field113: [],
  field114: [],
  field115: [],
  field124: [{ required: true, message: '请输入 Matlab 可执行文件路径', trigger: 'blur' }],
  field125: [{ required: true, message: '请输入 Matlab 版本', trigger: 'blur' }],
  field126: [],
  field127: [],
  field128: [],
  field129: [],
  field131: []
}

const formRef = ref()
const loading = ref(false)
const formData = reactive(createDefaultFormData())
const pythonRunner = reactive(createRunnerState(DEFAULT_PYTHON_CODE))
const matlabRunner = reactive(createRunnerState(DEFAULT_MATLAB_CODE))

function clearRunnerResult(runner) {
  runner.resultVisible = false
  runner.resultSuccess = false
  runner.stdout = ''
  runner.stderr = ''
  runner.exitCode = null
  runner.durationMs = null
}

function clearRunner(runner) {
  runner.code = ''
  runner.statusMessage = ''
  runner.statusType = 'success'
  clearRunnerResult(runner)
}

function setRunnerStatus(runner, message, type = 'success') {
  runner.statusMessage = message
  runner.statusType = type
}

function normalizeRunnerPayload(payload) {
  if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
    return {
      success: payload.success !== false,
      stdout: payload.stdout == null ? '' : String(payload.stdout),
      stderr: payload.stderr == null ? '' : String(payload.stderr),
      exitCode: payload.exitCode ?? null,
      durationMs: payload.durationMs ?? null
    }
  }

  return {
    success: true,
    stdout: payload == null ? '' : String(payload),
    stderr: '',
    exitCode: null,
    durationMs: null
  }
}

function applyRunnerResult(runner, payload) {
  const normalized = normalizeRunnerPayload(payload)
  runner.resultVisible = true
  runner.resultSuccess = normalized.success
  runner.stdout = normalized.stdout
  runner.stderr = normalized.stderr
  runner.exitCode = normalized.exitCode
  runner.durationMs = normalized.durationMs
}

function buildResultMeta(runner) {
  const parts = []
  if (runner.exitCode !== null && runner.exitCode !== undefined) {
    parts.push(`exitCode=${runner.exitCode}`)
  }
  if (runner.durationMs !== null && runner.durationMs !== undefined) {
    parts.push(`耗时=${runner.durationMs}ms`)
  }
  return parts.join(' · ')
}

function ensurePermission(permission, message) {
  if (checkPermi([permission])) {
    return true
  }
  ElMessage.error(message)
  return false
}

function hasAnyPermission(permissions) {
  return permissions.some((permission) => checkPermi([permission]))
}

async function handleRunnerFailure(failureHandler) {
  if (typeof failureHandler !== 'function') {
    return
  }

  try {
    await failureHandler()
  } catch (cancelError) {
    console.warn('Failed to cancel MATLAB task after runner failure:', cancelError)
  }
}

async function executeRunner(runner, languageLabel, executor, failureHandler) {
  const code = runner.code.trim()
  if (!code) {
    setRunnerStatus(runner, `请输入 ${languageLabel} 代码后再发送`, 'warning')
    clearRunnerResult(runner)
    return
  }

  runner.loading = true
  setRunnerStatus(runner, '')
  clearRunnerResult(runner)

  try {
    const payload = await executor(code)
    const normalized = normalizeRunnerPayload(payload)
    setRunnerStatus(
      runner,
      normalized.success ? `${languageLabel} 代码执行完成` : `${languageLabel} 代码执行失败`,
      normalized.success ? 'success' : 'error'
    )
    if (!normalized.success) {
      await handleRunnerFailure(failureHandler)
    }
    applyRunnerResult(runner, normalized)
  } catch (error) {
    await handleRunnerFailure(failureHandler)
    const message =
      error?.response?.data?.stderr ||
      error?.response?.data?.msg ||
      error?.message ||
      '请求失败，请稍后重试'

    setRunnerStatus(runner, `${languageLabel} 代码发送失败：${message}`, 'error')
    applyRunnerResult(runner, {
      success: false,
      stdout: '',
      stderr: String(message),
      exitCode: null,
      durationMs: null
    })
  } finally {
    runner.loading = false
  }
}

function runPythonExecutor() {
  if (!ensurePermission(MACHINE_LEARNING_PERMISSION.execute, '暂无代码执行权限')) {
    return Promise.resolve()
  }
  return executeRunner(pythonRunner, 'Python', executePythonCode)
}

function runMatlabExecutor() {
  if (!ensurePermission(MACHINE_LEARNING_PERMISSION.execute, '暂无代码执行权限')) {
    return Promise.resolve()
  }
  return executeRunner(matlabRunner, 'Matlab', executeMatlabCode, cancelMatlabTask)
}

function applyMatlabExample(type) {
  matlabRunner.code = MATLAB_EXAMPLES[type] || MATLAB_EXAMPLES.basic
  matlabRunner.statusMessage = ''
  clearRunnerResult(matlabRunner)
}

function submitForm() {
  if (!ensurePermission(MACHINE_LEARNING_PERMISSION.save, '暂无保存权限')) {
    return
  }
  formRef.value.validate((valid) => {
    if (!valid) {
      return
    }

    loading.value = true
    saveMachineLearningConfiguration(formData)
      .then(() => {
        ElMessage.success('保存成功')
      })
      .catch((error) => {
        console.error('保存机器学习环境配置失败:', error)
        ElMessage.error('保存失败，请稍后重试')
      })
      .finally(() => {
        loading.value = false
      })
  })
}

function resetForm() {
  if (!ensurePermission(MACHINE_LEARNING_PERMISSION.reset, '暂无重置权限')) {
    return
  }
  ElMessageBox.confirm('确定要恢复默认配置吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      loading.value = true
      resetMachineLearningConfiguration()
        .then(() => {
          ElMessage.success('已恢复默认配置')
          loadConfiguration()
        })
        .catch((error) => {
          console.error('重置机器学习环境配置失败:', error)
          ElMessage.error('重置失败，请稍后重试')
        })
        .finally(() => {
          loading.value = false
        })
    })
    .catch(() => {})
}

function loadConfiguration() {
  Object.assign(formData, createDefaultFormData())
  if (!hasAnyPermission(MACHINE_LEARNING_LOAD_PERMISSIONS)) {
    return
  }

  loading.value = true
  getMachineLearningConfiguration()
    .then((res) => {
      const config = res && res.data ? res.data : res
      Object.assign(formData, createDefaultFormData(), config || {})
    })
    .catch((error) => {
      console.error('加载机器学习环境配置失败:', error)
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
.machine-learning-form {
  width: 100%;
}

.config-grid {
  align-items: stretch;
}

.config-grid--stacked {
  margin-top: 20px;
}

.config-panel {
  height: 100%;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.config-panel__title {
  position: relative;
  margin-bottom: 20px;
  padding-left: 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.config-panel__title::before {
  position: absolute;
  top: 3px;
  left: 0;
  width: 4px;
  height: 18px;
  border-radius: 999px;
  background: var(--el-color-primary);
  content: '';
}

.config-panel--runner .runner-section {
  margin-top: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.machine-learning-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.machine-learning-form :deep(.el-form-item__content) {
  min-width: 0;
}

.machine-learning-form :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 0;
}

.runner-section {
  --runner-accent: var(--el-color-primary);
}

.runner-section--python {
  --runner-accent: #4f7cff;
}

.runner-section--matlab {
  --runner-accent: #14b8a6;
}

.runner-section__header {
  margin-bottom: 14px;
}

.runner-section__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.runner-section__subtitle {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.runner-examples {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.runner-examples__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.runner-example {
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--runner-accent) 24%, white);
  border-radius: 999px;
  background: color-mix(in srgb, var(--runner-accent) 8%, white);
  color: var(--runner-accent);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.runner-example:hover {
  background: var(--runner-accent);
  color: #fff;
}

.runner-editor-wrap {
  width: 100%;
}

.runner-editor :deep(.el-textarea__inner) {
  min-height: 260px !important;
  border-radius: 12px;
  background: #f8fafc;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.65;
  box-shadow: none;
}

.runner-editor :deep(.el-textarea__inner:focus) {
  border-color: var(--runner-accent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--runner-accent) 28%, white);
}

.runner-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
}

.runner-status {
  margin-top: 16px;
}

.runner-result {
  margin-top: 16px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}

.runner-result__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 12px;
  padding: 12px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
}

.runner-result__title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.runner-result__meta {
  font-size: 12px;
  color: #6b7280;
}

.runner-result__body {
  display: grid;
  gap: 10px;
  padding: 12px 14px 14px;
}

.runner-result__section {
  overflow: hidden;
  border: 1px solid #eef2f7;
  border-radius: 10px;
}

.runner-result__section-title {
  padding: 8px 10px;
  border-bottom: 1px solid #eef2f7;
  background: #f9fafb;
  font-size: 12px;
  font-weight: 700;
  color: #374151;
}

.runner-result__pre {
  margin: 0;
  padding: 10px;
  overflow: auto;
  max-height: 260px;
  background: #0b1020;
  color: #e5e7eb;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.runner-result__pre--error {
  background: #1f0b0b;
  color: #fecaca;
}

.runner-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.action-bar {
  margin-top: 20px;
}

.machine-learning-form :deep(.action-bar .el-form-item__content) {
  justify-content: center;
  margin-left: 0 !important;
}

@media (max-width: 1199px) {
  .config-panel {
    margin-bottom: 20px;
  }
}
</style>
