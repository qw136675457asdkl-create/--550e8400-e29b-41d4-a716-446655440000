<template>
  <el-drawer
    v-model="drawerVisible"
    direction="rtl"
    size="560px"
    class="info-edit-drawer"
    append-to-body
    :with-header="false"
    @closed="handleClosed"
  >
    <div class="drawer-form-shell">
      <div class="drawer-form-shell__header">
        <div>
          <h3 class="drawer-form-shell__title">{{ title }}</h3>
          <p class="drawer-form-shell__subtitle">修改{{ getInfoTypeLabel(formModel.type) }}信息</p>
        </div>
      </div>

      <div class="drawer-form-shell__body">
        <el-form ref="formRef" :model="formModel" :rules="rules" label-width="84px" class="ant-form-layout">
          <el-form-item label="名称" prop="name">
            <el-input v-model="formModel.name" placeholder="请输入名称" />
          </el-form-item>
          <el-form-item label="所属项目" prop="parentId" v-if="formModel.type === 'experiment'">
            <el-select v-model="formModel.parentId" placeholder="请选择所属项目" filterable clearable>
              <el-option v-for="item in projectOptions" :key="item.projectId" :label="item.projectName" :value="String(item.projectId)" />
            </el-select>
          </el-form-item>
          <el-form-item label="试验目标" prop="targetId" v-if="formModel.type === 'experiment'">
            <el-select v-model="formModel.targetId" placeholder="请选择试验目标">
              <el-option v-for="item in targetTypeOptions" :key="item.targetId" :label="item.targetType" :value="item.targetId" />
            </el-select>
          </el-form-item>
          <el-form-item label="试验日期" prop="startTime" v-if="formModel.type === 'experiment'">
            <el-date-picker v-model="formModel.startTime" type="date" value-format="YYYY-MM-DD" placeholder="选择开始日期" clearable />
          </el-form-item>
          <el-form-item label="试验地点" prop="location" v-if="formModel.type === 'experiment'">
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
          <el-form-item label="路径" prop="fullPath">
            <el-input v-model="formModel.fullPath" placeholder="请输入路径" disabled />
          </el-form-item>
          <el-form-item label="创建人" v-if="formModel.createBy">
            <el-input :model-value="formModel.createBy" disabled />
          </el-form-item>
          <el-form-item label="创建时间" v-if="formModel.createTime">
            <el-input :model-value="formatCreateTime(formModel.createTime)" disabled />
          </el-form-item>
        </el-form>
      </div>

      <div class="drawer-form-shell__footer">
        <el-button type="primary" class="ant-confirm-btn" :loading="loading" @click="handleSubmit">保 存</el-button>
        <el-button class="ant-cancel-btn" :disabled="loading" @click="drawerVisible = false">取 消</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  formModel: { type: Object, required: true },
  rules: { type: Object, default: () => ({}) },
  projectOptions: { type: Array, default: () => [] },
  targetTypeOptions: { type: Array, default: () => [] },
  getInfoTypeLabel: { type: Function, required: true },
  formatCreateTime: { type: Function, required: true }
})

const emit = defineEmits(['update:modelValue', 'submit', 'closed'])
const formRef = ref(null)

const drawerVisible = computed({
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
  emit('closed')
}
</script>

<style scoped>
.info-edit-drawer :deep(.el-drawer__body) { padding: 0; }
.drawer-form-shell { height: 100%; display: flex; flex-direction: column; background: linear-gradient(180deg, #fcfdff 0%, #f8fafc 100%); }
.drawer-form-shell__header { padding: 24px 28px 16px; border-bottom: 1px solid #eaecf0; background: rgba(255, 255, 255, 0.94); }
.drawer-form-shell__title { margin: 0; color: #1f2937; font-size: 22px; font-weight: 700; letter-spacing: 0.01em; }
.drawer-form-shell__subtitle { margin: 8px 0 0; color: #667085; font-size: 13px; }
.drawer-form-shell__body { flex: 1; min-height: 0; overflow-y: auto; padding: 22px 28px 10px; }
.drawer-form-shell__footer { display: flex; justify-content: flex-end; gap: 12px; padding: 12px 28px 24px; border-top: 1px solid #eaecf0; background: rgba(255, 255, 255, 0.94); }
</style>
