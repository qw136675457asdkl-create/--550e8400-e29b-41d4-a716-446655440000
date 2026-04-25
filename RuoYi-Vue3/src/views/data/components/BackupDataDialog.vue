<template>
  <el-dialog
    v-model="dialogVisible"
    title="备份数据记录"
    width="88%"
    append-to-body
    class="backup-data-dialog"
    @closed="emit('closed')"
  >
    <div class="backup-data-dialog__body">
      <el-alert
        title="可查看未恢复的备份记录，并直接发起还原操作。"
        type="info"
        :closable="false"
        class="backup-data-dialog__alert"
      />

      <el-form :model="queryParams" :inline="true" label-width="88px" class="backup-query-form">
        <el-form-item label="数据名称">
          <el-input v-model="queryParams.dataName" placeholder="请输入数据名称" clearable @keyup.enter="emit('search')" />
        </el-form-item>
        <el-form-item label="试验名称">
          <el-input v-model="queryParams.experimentName" placeholder="请输入试验名称" clearable @keyup.enter="emit('search')" />
        </el-form-item>
        <el-form-item label="所属项目">
          <el-input v-model="queryParams.projectName" placeholder="请输入项目名称" clearable @keyup.enter="emit('search')" />
        </el-form-item>
        <el-form-item label="备份人">
          <el-input v-model="queryParams.deleteBy" placeholder="请输入备份人" clearable @keyup.enter="emit('search')" />
        </el-form-item>
        <el-form-item label="备份时间">
          <el-date-picker
            v-model="dateRangeModel"
            value-format="YYYY-MM-DD"
            type="daterange"
            range-separator="-"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item class="backup-query-form__actions">
          <el-button type="primary" icon="Search" @click="emit('search')">查询</el-button>
          <el-button icon="Refresh" @click="emit('reset')">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="backup-data-dialog__table-scroll">
        <el-table
          v-loading="loading"
          :data="backupList"
          class="backup-data-table"
          style="width: 100%; min-width: 1480px"
          table-layout="auto"
          empty-text="暂无备份数据"
        >
          <el-table-column label="数据名称" prop="dataName" min-width="180" show-overflow-tooltip />
          <el-table-column label="所属试验" prop="experimentName" min-width="180" show-overflow-tooltip />
          <el-table-column label="所属项目" prop="projectName" min-width="180" show-overflow-tooltip />
          <el-table-column label="数据种类" prop="dataType" min-width="160" show-overflow-tooltip />
          <el-table-column label="备份人" prop="deleteBy" min-width="120" show-overflow-tooltip />
          <el-table-column label="备份时间" min-width="180">
            <template #default="scope">
              <span>{{ formatTime(scope.row.deleteTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="恢复状态" width="120" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.isRestored === 1 ? 'success' : 'warning'" effect="light" round>
                {{ scope.row.isRestored === 1 ? '已恢复' : '未恢复' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="scope">
              <el-button
                link
                type="primary"
                :disabled="scope.row.isRestored === 1 || restoringId === scope.row.id"
                :loading="restoringId === scope.row.id"
                v-hasPermi="['dataInfo:info:restore']"
                @click="emit('restore', scope.row)"
              >
                还原
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="pageModel"
        v-model:limit="limitModel"
        @pagination="emit('pagination')"
      />
    </div>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  backupList: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  queryParams: { type: Object, required: true },
  dateRange: { type: Array, default: () => [] },
  restoringId: { type: [Number, null], default: null },
  formatTime: { type: Function, default: value => value || '' }
})

const emit = defineEmits([
  'update:modelValue',
  'update:dateRange',
  'search',
  'reset',
  'restore',
  'pagination',
  'closed'
])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const dateRangeModel = computed({
  get: () => props.dateRange,
  set: value => emit('update:dateRange', value)
})

const pageModel = computed({
  get: () => props.queryParams.pageNum,
  set: value => {
    props.queryParams.pageNum = value
  }
})

const limitModel = computed({
  get: () => props.queryParams.pageSize,
  set: value => {
    props.queryParams.pageSize = value
  }
})
</script>

<style scoped>
.backup-data-dialog :deep(.el-dialog__body) {
  padding-top: 14px;
}

.backup-data-dialog__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.backup-data-dialog__alert {
  margin-bottom: 2px;
}

.backup-query-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 18px;
}

.backup-query-form :deep(.el-form-item) {
  margin: 0;
}

.backup-query-form :deep(.el-form-item__content) {
  min-width: 0;
}

.backup-query-form :deep(.el-input),
.backup-query-form :deep(.el-select),
.backup-query-form :deep(.el-date-editor) {
  width: 100%;
}

.backup-query-form__actions {
  align-self: end;
}

.backup-data-dialog__table-scroll {
  overflow-x: auto;
  padding-bottom: 6px;
}

.backup-data-table {
  border-radius: 14px;
}

.backup-data-dialog :deep(.pagination-container) {
  padding-left: 0;
  padding-right: 0;
}

@media (max-width: 1200px) {
  .backup-query-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .backup-query-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
