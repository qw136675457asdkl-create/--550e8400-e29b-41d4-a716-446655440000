<template>
  <el-dialog :title="title" v-model="dialogVisible" width="700px" append-to-body @closed="emit('closed')">
    <el-form ref="formRef" :model="formModel" :rules="rules" label-width="100px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="数据名称" prop="dataName">
            <el-input v-model="formModel.dataName" placeholder="请输入数据名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="是否模拟" prop="isSimulation">
            <el-select v-model="formModel.isSimulation" placeholder="请选择">
              <el-option label="仿真" :value="true" />
              <el-option label="真实" :value="false" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="数据种类" prop="dataType">
            <el-select v-model="formModel.dataType" placeholder="请选择数据种类">
              <el-option label="ADS-B目标元数据" value="ADS-B目标元数据" />
              <el-option label="AIS目标元数据" value="AIS目标元数据" />
              <el-option label="通信情报日志元数据" value="通信情报日志元数据" />
              <el-option label="交战闭锁元数据" value="交战闭锁元数据" />
              <el-option label="电子战截获元数据" value="电子战截获元数据" />
              <el-option label="载机姿态元数据" value="载机姿态元数据" />
              <el-option label="惯导状态元数据" value="惯导状态元数据" />
              <el-option label="被动探测元数据" value="被动探测元数据" />
              <el-option label="雷达系统航迹元数据" value="雷达系统航迹元数据" />
              <el-option label="目标牵引询问元数据" value="目标牵引询问元数据" />
              <el-option label="其他元数据" value="其他元数据" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="文件名称" prop="fileName" v-if="title === '修改数据'">
            <el-input v-model="formModel.fileName" placeholder="请输入文件名称" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-if="title === '修改数据'">
        <el-col :span="24">
          <el-form-item label="修改目录">
            <el-tree-select
              v-model="selectedMoveNodeModel"
              :data="movePathTreeOptions"
              :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
              value-key="id"
              placeholder="请选择修改的目标文件目录"
              check-strictly
              clearable
              filterable
              @change="emit('move-path-change', $event)"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider />
      <el-row>
        <el-col :span="12">
          <el-form-item label="ID">
            <el-input :value="formModel.id" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="所属试验">
            <el-input :value="moveTargetExperimentName || formModel.experimentName" disabled />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="所属项目">
            <el-input :value="moveTargetProjectName || formModel.projectName" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="试验目标">
            <el-input :value="formModel.targetType" disabled />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="24">
          <el-form-item label="当前路径">
            <el-input :value="formModel.fullPath" disabled />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="试验时间">
            <el-input :value="formatTime(formModel.startTime)" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="试验地点">
            <el-input :value="formModel.location" disabled />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="24">
          <el-form-item label="内容描述">
            <el-input :value="formModel.contentDesc" type="textarea" disabled />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="创建人">
            <el-input :value="formModel.createBy" disabled />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="创建时间">
            <el-input :value="formatTime(formModel.createTime)" disabled />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleSubmit">确 定</el-button>
        <el-button @click="dialogVisible = false">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  formModel: { type: Object, required: true },
  rules: { type: Object, default: () => ({}) },
  movePathTreeOptions: { type: Array, default: () => [] },
  selectedMovePathNodeId: { type: [String, Number, null], default: null },
  moveTargetExperimentName: { type: String, default: '' },
  moveTargetProjectName: { type: String, default: '' },
  formatTime: { type: Function, default: value => value || '' }
})

const emit = defineEmits(['update:modelValue', 'update:selectedMovePathNodeId', 'move-path-change', 'submit', 'closed'])
const formRef = ref(null)

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const selectedMoveNodeModel = computed({
  get: () => props.selectedMovePathNodeId,
  set: value => emit('update:selectedMovePathNodeId', value)
})

function handleSubmit() {
  formRef.value?.validate(valid => {
    if (valid) emit('submit')
  })
}
</script>

<style scoped>
.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; }
</style>
