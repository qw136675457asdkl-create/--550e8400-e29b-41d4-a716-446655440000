<template>
  <section class="table-surface">
    <div class="table-surface__header">
      <div>
        <h4 class="table-surface__title">数据清单</h4>
      </div>
    </div>

    <div class="table-surface__table-scroll">
      <el-table
        v-loading="loading"
        :data="businessList"
        class="data-table"
        style="width: 100%"
        table-layout="auto"
        empty-text="暂无符合条件的数据"
        @selection-change="selection => emit('selection-change', selection)"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="ID" align="center" prop="id" min-width="90" />
        <el-table-column label="数据名称" align="center" prop="dataName" min-width="170" :show-overflow-tooltip="true" />
        <el-table-column label="是否模拟" align="center" prop="isSimulation" min-width="130">
          <template #default="scope">
            <el-tag class="status-chip" effect="light" round :type="scope.row.isSimulation === true ? 'warning' : scope.row.isSimulation === false ? 'warning' : 'info'">
              <span v-if="scope.row.isSimulation === true">仿真</span>
              <span v-else-if="scope.row.isSimulation === false">真实</span>
              <span v-else>未知类型</span>
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属试验" align="center" prop="experimentName" min-width="170" :show-overflow-tooltip="true" />
        <el-table-column label="所属项目" align="center" prop="projectName" min-width="170" :show-overflow-tooltip="true" />
        <el-table-column label="试验目标" align="center" prop="targetType" min-width="170" :show-overflow-tooltip="true" />
        <el-table-column label="试验时间" align="center" prop="startTime" min-width="150">
          <template #default="scope">
            <span>{{ formatTime(scope.row.startTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="试验地点" align="center" prop="location" min-width="150" :show-overflow-tooltip="true" />
        <el-table-column label="试验描述" align="center" prop="contentDesc" min-width="180" :show-overflow-tooltip="true" />
        <el-table-column label="创建人" align="center" prop="createBy" min-width="110" />
        <el-table-column label="创建时间" align="center" prop="createTime" min-width="170">
          <template #default="scope">
            <span>{{ formatTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数据种类" align="center" prop="dataType" min-width="170" :show-overflow-tooltip="true">
          <template #default="scope">
            <span class="data-type-pill">{{ scope.row.dataType || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width table-action-cell" width="176" fixed="right">
          <template #default="scope">
            <div class="table-action-group">
              <el-tooltip content="备份" placement="top">
                <el-button link type="primary" :icon="DocumentCopy" @click="emit('backup', scope.row)" v-hasPermi="['dataInfo:info:backup']"></el-button>
              </el-tooltip>
              <el-tooltip content="详情" placement="top">
                <el-button link type="primary" icon="View" @click="emit('view', scope.row)" v-hasPermi="['dataInfo:info:query']"></el-button>
              </el-tooltip>
              <el-tooltip content="修改" placement="top">
                <el-button link type="primary" icon="Edit" @click="emit('update', scope.row)" v-hasPermi="['dataInfo:info:update']"></el-button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button link type="danger" icon="Delete" @click="emit('delete', scope.row)" v-hasPermi="['dataInfo:info:delete']"></el-button>
              </el-tooltip>
            </div>
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
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { DocumentCopy } from '@element-plus/icons-vue'

const props = defineProps({
  loading: {
    type: Boolean,
    default: false
  },
  businessList: {
    type: Array,
    default: () => []
  },
  total: {
    type: Number,
    default: 0
  },
  pageNum: {
    type: Number,
    default: 1
  },
  pageSize: {
    type: Number,
    default: 10
  },
  formatTime: {
    type: Function,
    default: value => value || ''
  }
})

const emit = defineEmits([
  'selection-change',
  'backup',
  'view',
  'update',
  'delete',
  'update:pageNum',
  'update:pageSize',
  'pagination'
])

const pageModel = computed({
  get: () => props.pageNum,
  set: value => emit('update:pageNum', value)
})

const limitModel = computed({
  get: () => props.pageSize,
  set: value => emit('update:pageSize', value)
})
</script>

<style scoped>
.table-surface {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  gap: 16px;
  padding: 20px 22px;
  border-radius: 10px;
}

.table-surface__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.table-surface__title {
  margin: 0;
  color: #1f2937;
  font-size: 18px;
  font-weight: 700;
}

.table-surface__table-scroll {
  min-width: 0;
  overflow: hidden;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.96) 100%);
  box-shadow: inset 0 0 0 1px rgba(226, 232, 240, 0.72);
}

.data-table {
  width: 100%;
  border-radius: 18px;
  overflow: hidden;
}

.data-table :deep(.el-table) {
  --el-table-border-color: #eef2f7;
  --el-table-row-hover-bg-color: #f8fbff;
}

.data-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.data-table :deep(.el-scrollbar__bar.is-horizontal) {
  height: 10px;
  bottom: 8px;
}

.data-table :deep(.el-table__fixed-right-patch) {
  background: #f8fafc;
}

.data-table :deep(.el-scrollbar__bar.is-horizontal .el-scrollbar__thumb) {
  background: rgba(148, 163, 184, 0.55);
  border-radius: 999px;
}

.data-table :deep(.el-scrollbar__bar.is-horizontal .el-scrollbar__thumb:hover) {
  background: rgba(100, 116, 139, 0.72);
}

.data-table :deep(th.el-table__cell) {
  background: #f8fafc;
  color: #64748b;
  font-weight: 700;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
}

.data-table :deep(td.el-table__cell) {
  color: #334155;
  border-bottom: 1px solid #eef2f7;
}

.data-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: #f8fbff;
}

.status-chip {
  font-weight: 600;
}

.data-type-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  max-width: 100%;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(241, 245, 249, 0.96);
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.table-action-group {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 140px;
  white-space: nowrap;
}

.table-action-group :deep(.el-button) {
  margin: 0;
  min-width: 28px;
  height: 28px;
  padding: 0 4px;
  border-radius: 6px;
}

.table-action-cell :deep(.cell) {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: visible;
}

.table-surface :deep(.pagination-container) {
  padding-left: 0;
  padding-right: 0;
  margin-top: 2px;
}

@media (max-width: 992px) {
  .table-surface__header {
    flex-direction: column;
    align-items: stretch;
  }
}

@media (max-width: 768px) {
  .table-surface {
    padding: 16px;
  }
}
</style>
