<template>
  <el-dialog v-model="dialogVisible" title="数据导入" width="60%" append-to-body @closed="handleClosed" class="import-dialog">
    <div class="import-dialog-body">
      <el-form ref="formRef" :model="formModel" :rules="rules" label-width="100px" class="import-form">
        <el-row>
          <el-col :span="12">
            <el-form-item label="数据名称" prop="dataName">
              <el-input
                v-model="formModel.dataName"
                :disabled="draftFiles.length > 0 && !singleUploadNameEnabled"
                :placeholder="draftFiles.length === 0 || singleUploadNameEnabled ? '为空则使用文件名' : '批量、文件夹或 ZIP 上传时按实际文件名入库'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属试验" prop="experimentId">
              <el-tree-select
                v-model="formModel.experimentId"
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
              <el-select v-model="formModel.targetId" placeholder="请选择试验目标" @change="value => emit('target-change', value)">
                <el-option v-for="item in targetTypeOptions" :key="item.targetId" :label="item.targetType" :value="item.targetId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据种类" prop="dataType">
              <el-select v-model="formModel.dataType" placeholder="请选择数据种类">
                <el-option label="载机惯导数据" value="载机惯导数据" />
                <el-option label="载机姿态数据" value="载机姿态数据" />
                <el-option label="雷达航迹数据" value="雷达航迹数据" />
                <el-option label="电子战数据" value="电子战数据" />
                <el-option label="通侦数据" value="通侦数据" />
                <el-option label="ADS-B数据" value="ADS-B数据" />
                <el-option label="AIS数据" value="AIS数据" />
                <el-option label="目标牵引与询问数据" value="目标牵引与询问数据" />
                <el-option label="方位数据" value="方位数据" />
                <el-option label="闭锁信息数据" value="闭锁信息数据" />
                <el-option label="其他元数据" value="其他元数据" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="是否模拟" prop="isSimulation">
              <el-radio-group v-model="formModel.isSimulation">
                <el-radio :label="true">仿真</el-radio>
                <el-radio :label="false">真实</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-divider content-position="left">导入文件上传</el-divider>
      <UploadDraftPanel
        ref="uploadPanelRef"
        :disabled="loading"
        :accept="accept"
        :draft-files="draftFiles"
        :progress="progress"
        alert-title="支持上传单个文件、多个文件、文件夹和 ZIP 压缩包；文件夹结构会保留，ZIP 会自动解压到当前试验目录。批量、文件夹和 ZIP 解压场景下将按实际文件名入库。"
        drop-text="拖拽导入文件到此处，或"
        tip-text="支持 ZIP、CSV、Excel、TXT、JSON、Word、PDF、BIN、DAT、RAW、PNG、JPG、JPEG、MP3、MP4；如需上传整个文件夹，请使用下方“选择文件夹”。"
        :count-text="`已选择 ${draftFiles.length} 个待上传文件`"
        :format-size="formatSize"
        @draft-change="file => emit('draft-change', file)"
        @folder-change="event => emit('folder-change', event)"
        @clear-files="emit('clear-files')"
        @remove-file="uid => emit('remove-file', uid)"
      />
    </div>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit" :loading="loading">导入数据</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import UploadDraftPanel from './UploadDraftPanel.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  formModel: { type: Object, required: true },
  rules: { type: Object, default: () => ({}) },
  selectableTreeOptions: { type: Array, default: () => [] },
  targetTypeOptions: { type: Array, default: () => [] },
  draftFiles: { type: Array, default: () => [] },
  progress: { type: Object, default: () => ({}) },
  accept: { type: String, default: '' },
  singleUploadNameEnabled: { type: Boolean, default: false },
  formatSize: { type: Function, required: true }
})

const emit = defineEmits(['update:modelValue', 'submit', 'closed', 'target-change', 'draft-change', 'folder-change', 'clear-files', 'remove-file'])
const formRef = ref(null)
const uploadPanelRef = ref(null)

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

function handleSubmit() {
  if (props.loading) return
  formRef.value?.validate(valid => {
    if (valid) emit('submit')
  })
}

function handleClosed() {
  formRef.value?.clearValidate()
  uploadPanelRef.value?.resetPanel()
  emit('closed')
}
</script>

<style scoped>
.import-dialog :deep(.el-dialog__body) { padding: 12px 20px; max-height: 70vh; overflow-y: auto; }
.import-dialog-body { padding: 0; }
.import-form { margin-bottom: 16px; }
.import-form :deep(.el-form-item) { margin-bottom: 12px; }
</style>
