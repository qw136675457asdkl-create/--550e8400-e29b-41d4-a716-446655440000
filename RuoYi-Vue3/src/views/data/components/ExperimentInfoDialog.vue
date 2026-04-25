<template>
  <el-dialog
    title="新增试验"
    v-model="dialogVisible"
    width="620px"
    class="ant-form-dialog"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    append-to-body
    @closed="handleClosed"
  >
    <el-form ref="formRef" :model="formModel" :rules="rules" label-width="84px" class="ant-form-layout">
      <el-form-item label="名称" prop="name">
        <el-input v-model="formModel.name" placeholder="请输入试验名称" />
      </el-form-item>
      <el-form-item label="所属项目" prop="parentId">
        <el-select v-model="formModel.parentId" placeholder="请选择所属项目" filterable clearable>
          <el-option v-for="item in projectOptions" :key="item.projectId" :label="item.projectName" :value="String(item.projectId)" />
        </el-select>
      </el-form-item>
      <el-form-item label="试验目标" prop="targetId">
        <el-select v-model="formModel.targetId" placeholder="请选择试验目标">
          <el-option v-for="item in targetTypeOptions" :key="item.targetId" :label="item.targetType" :value="item.targetId" />
        </el-select>
      </el-form-item>
      <el-form-item label="试验日期" prop="startTime">
        <el-date-picker v-model="formModel.startTime" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" clearable />
      </el-form-item>
      <el-form-item label="试验地点" prop="location">
        <el-input v-model="formModel.location" placeholder="请输入地点" />
      </el-form-item>
      <el-form-item label="内容描述" prop="contentDesc">
        <el-input
          v-model="formModel.contentDesc"
          type="textarea"
          :maxlength="200"
          show-word-limit
          :autosize="{ minRows: 4, maxRows: 6 }"
          placeholder="请输入内容"
        />
      </el-form-item>
      <el-form-item label="路径" prop="path">
        <el-input v-model="formModel.path" placeholder="系统自动生成路径" disabled />
      </el-form-item>

      <el-divider content-position="left">试验文件上传</el-divider>
      <UploadDraftPanel
        ref="uploadPanelRef"
        :disabled="loading"
        :accept="accept"
        :draft-files="draftFiles"
        :progress="progress"
        alert-title="支持上传普通文件、二进制文件、文件夹和 ZIP 压缩包，文件夹结构会保留，ZIP 会自动解压到当前试验目录。"
        drop-text="拖拽试验文件到此处，或"
        tip-text="支持 ZIP、CSV、Excel、TXT、JSON、Word、PDF、BIN、DAT、RAW、PNG、JPG、JPEG 、MP3、MP4；如需上传整个文件夹，请使用下方“选择文件夹”。"
        :count-text="`已选择 ${draftFiles.length} 个待上传文件`"
        :format-size="formatSize"
        @draft-change="file => emit('draft-change', file)"
        @folder-change="event => emit('folder-change', event)"
        @clear-files="emit('clear-files')"
        @remove-file="uid => emit('remove-file', uid)"
      />
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" class="ant-confirm-btn" :loading="loading" @click="handleSubmit">确 定</el-button>
        <el-button class="ant-cancel-btn" :disabled="loading" @click="dialogVisible = false">取 消</el-button>
      </div>
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
  projectOptions: { type: Array, default: () => [] },
  targetTypeOptions: { type: Array, default: () => [] },
  draftFiles: { type: Array, default: () => [] },
  progress: { type: Object, default: () => ({}) },
  accept: { type: String, default: '' },
  formatSize: { type: Function, required: true }
})

const emit = defineEmits(['update:modelValue', 'submit', 'closed', 'draft-change', 'folder-change', 'clear-files', 'remove-file'])
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
.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; }
.ant-confirm-btn, .ant-cancel-btn { border-radius: 8px; }
</style>
