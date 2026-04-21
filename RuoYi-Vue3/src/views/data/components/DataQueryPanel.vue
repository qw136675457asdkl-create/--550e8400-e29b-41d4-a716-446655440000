<template>
  <section class="action-surface action-surface--merged">
    <el-form v-show="showSearch" :model="queryParams" :inline="true" label-width="88px" class="query-form">
      <el-form-item label="ID" prop="id">
        <el-input v-model="queryParams.id" placeholder="请输入数据ID" clearable class="query-control" @keyup.enter="emit('search')" />
      </el-form-item>
      <el-form-item label="数据名称" prop="dataName">
        <el-input v-model="queryParams.dataName" placeholder="请输入数据名称" clearable class="query-control" @keyup.enter="emit('search')" />
      </el-form-item>
      <el-form-item label="试验名称" prop="experimentName">
        <el-input v-model="queryParams.experimentName" placeholder="请输入试验名称" clearable class="query-control" @keyup.enter="emit('search')" />
      </el-form-item>
      <el-form-item label="所属项目" prop="projectId">
        <el-select v-model="queryParams.projectId" placeholder="请选择所属项目" clearable class="query-control">
          <el-option v-for="item in projectOptions" :key="item.projectId" :label="item.projectName" :value="item.projectId" />
        </el-select>
      </el-form-item>

      <el-form-item label="创建人" prop="createBy">
        <el-input v-model="queryParams.createBy" placeholder="请输入创建人" clearable class="query-control" @keyup.enter="emit('search')" />
      </el-form-item>
      <el-form-item label="是否模拟" prop="isSimulation">
        <el-select v-model="queryParams.isSimulation" placeholder="请选择数据类型" clearable class="query-control">
          <el-option label="真实" :value="true" />
          <el-option label="模拟" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item label="数据状态" prop="workStatus">
        <el-input v-model="queryParams.workStatus" placeholder="请输入数据状态" clearable class="query-control" @keyup.enter="emit('search')" />
      </el-form-item>
      <el-form-item label="创建时间" class="query-date-item">
        <el-date-picker
          v-model="dateRangeModel"
          value-format="YYYY-MM-DD"
          type="daterange"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="query-date-control"
        />
      </el-form-item>
    </el-form>
    <div v-show="showSearch" class="action-surface__divider"></div>
    <div class="action-surface__toolbar">
      <div class="global-actions-row">
        <el-button class="toolbar-action-btn toolbar-action-btn--project" type="primary" @click="emit('add-project')" v-hasPermi="['data:info:addProject']">
          <template #icon>
            <svg-icon icon-class="input" />
          </template>
          新增项目
        </el-button>
        <el-button class="toolbar-action-btn toolbar-action-btn--experiment" type="primary" @click="emit('add-experiment')" v-hasPermi="['data:info:addExperiment']">
          <template #icon>
            <svg-icon icon-class="button" />
          </template>
          新增试验
        </el-button>
        <el-button class="toolbar-action-btn toolbar-action-btn--import" type="primary" @click="emit('open-import')" v-hasPermi="['dataInfo:info:insert']">
          <template #icon>
            <svg-icon icon-class="upload" />
          </template>
          数据导入
        </el-button>
        <el-button class="toolbar-action-btn toolbar-action-btn--import" type="primary" @click="emit('open-import')" v-hasPermi="['dataInfo:info:insert']">
          <template #icon>
            <svg-icon icon-class="upload" />
          </template>
          数据接收
        </el-button>
        <el-button class="toolbar-action-btn toolbar-action-btn--export" type="primary" @click="emit('export-data')" v-hasPermi="['dataInfo:info:download']">
          <template #icon>
            <svg-icon icon-class="download" />
          </template>
          导出
        </el-button>
        <el-button class="toolbar-action-btn toolbar-action-btn--export" type="primary" @click="emit('rename-data')" v-hasPermi="['dataInfo:info:Rename']">
          <template #icon>
            <svg-icon icon-class="example" />
          </template>
          规范重命名
        </el-button>
        <el-button class="toolbar-action-btn toolbar-action-btn--restore" type="primary" @click="emit('restore-data')" v-hasPermi="['dataInfo:info:backup', 'dataInfo:info:restore']">
          <template #icon>
            <svg-icon icon-class="documentation" />
          </template>
          数据还原
        </el-button>
        <el-button
          class="toolbar-action-btn toolbar-action-btn--compare"
          type="primary"
          :disabled="idsLength < 2"
          @click="emit('compare-data')"
          v-hasPermi="['dataInfo:info:compare']"
        >
          <template #icon>
            <svg-icon icon-class="eye-open" />
          </template>
          数据比对
        </el-button>
        <el-button
          class="toolbar-action-btn toolbar-action-btn--delete"
          type="danger"
          icon="Delete"
          :disabled="multiple"
          @click="emit('delete-data')"
          v-hasPermi="['dataInfo:info:delete']"
        >
          删除
        </el-button>
      </div>
      <div class="toolbar-query-actions">
        <el-button type="primary" icon="Search" @click="emit('search')">搜索</el-button>
        <el-button class="query-reset-btn" icon="Refresh" @click="emit('reset')">重置</el-button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  showSearch: {
    type: Boolean,
    default: true
  },
  queryParams: {
    type: Object,
    required: true
  },
  dateRange: {
    type: Array,
    default: () => []
  },
  projectOptions: {
    type: Array,
    default: () => []
  },
  idsLength: {
    type: Number,
    default: 0
  },
  multiple: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits([
  'update:dateRange',
  'add-project',
  'add-experiment',
  'open-import',
  'export-data',
  'rename-data',
  'restore-data',
  'compare-data',
  'delete-data',
  'search',
  'reset'
])

const dateRangeModel = computed({
  get: () => props.dateRange,
  set: value => emit('update:dateRange', value)
})
</script>

<style scoped>
.query-form,
.action-surface {
  min-width: 0;
}

.query-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px 24px;
  align-items: end;
  margin-top: 0;
}

.query-form :deep(.el-form-item) {
  margin: 0;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14px;
}

.query-form :deep(.el-form-item__label) {
  flex: 0 0 88px;
  max-width: 88px;
  justify-content: flex-end;
  padding: 0;
  line-height: 42px;
  color: #0f172a;
  font-weight: 700;
}

.query-form :deep(.el-form-item__content) {
  flex: 1 1 auto;
  min-width: 0;
  margin-left: 0 !important;
}

.query-control {
  width: 100%;
}

.query-form :deep(.el-input__wrapper),
.query-form :deep(.el-select__wrapper),
.query-form :deep(.el-date-editor.el-input__wrapper) {
  min-height: 42px;
  border-radius: 14px;
  background: #f8fafc;
  box-shadow: 0 0 0 1px rgba(203, 213, 225, 0.82) inset;
}

.query-form :deep(.el-input__wrapper.is-focus),
.query-form :deep(.el-select__wrapper.is-focused),
.query-form :deep(.el-date-editor.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 1px rgba(64, 94, 254, 0.42) inset, 0 0 0 4px rgba(64, 94, 254, 0.1);
}

.query-date-control {
  width: 100%;
}

.action-surface {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px 22px;
  border-radius: 10px;
}

.action-surface__divider {
  height: 1px;
  background: linear-gradient(90deg, rgba(203, 213, 225, 0), rgba(203, 213, 225, 0.88), rgba(203, 213, 225, 0));
}

.action-surface__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 18px;
}

.global-actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  flex: 1 1 720px;
  min-width: 0;
}

.toolbar-query-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-left: auto;
  flex: 0 0 auto;
}

.global-actions-row :deep(.el-button + .el-button),
.toolbar-query-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.toolbar-query-actions :deep(.el-button) {
  min-width: 128px;
  height: 42px;
}

.toolbar-action-btn {
  min-width: 138px;
  height: 42px;
  padding: 0 20px;
  border: none;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.14);
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
}

.toolbar-action-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.18);
  filter: brightness(1.03);
}

.toolbar-action-btn:active {
  transform: translateY(0);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.16);
}

.toolbar-action-btn:disabled {
  transform: none;
  box-shadow: none;
}

.toolbar-action-btn--import,
.toolbar-action-btn--project,
.toolbar-action-btn--experiment,
.toolbar-action-btn--export,
.toolbar-action-btn--compare {
  background: linear-gradient(135deg, #66b1ff, #409eff);
}

.toolbar-action-btn--import:hover,
.toolbar-action-btn--import:focus,
.toolbar-action-btn--project:hover,
.toolbar-action-btn--project:focus,
.toolbar-action-btn--experiment:hover,
.toolbar-action-btn--experiment:focus,
.toolbar-action-btn--export:hover,
.toolbar-action-btn--export:focus,
.toolbar-action-btn--compare:hover,
.toolbar-action-btn--compare:focus {
  background: linear-gradient(135deg, #79bbff, #53a8ff);
}

.toolbar-action-btn--delete {
  background: linear-gradient(135deg, #ef4444, #dc2626);
}

.toolbar-action-btn--delete:hover,
.toolbar-action-btn--delete:focus {
  background: linear-gradient(135deg, #f87171, #ef4444);
}

@media (max-width: 1480px) {
  .query-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px 20px;
  }
}

@media (max-width: 1520px) {
  .global-actions-row {
    flex: 1 1 100%;
  }

  .toolbar-query-actions {
    flex: 1 1 100%;
    margin-left: 0;
  }
}

@media (max-width: 992px) {
  .action-surface__toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .global-actions-row {
    flex: 1 1 auto;
  }

  .toolbar-query-actions {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .action-surface {
    padding: 16px;
  }

  .query-form {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .query-form :deep(.el-form-item) {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .query-form :deep(.el-form-item__label) {
    flex: 0 0 auto;
    max-width: none;
    line-height: 1.4;
    justify-content: flex-start;
  }

  .toolbar-query-actions :deep(.el-button) {
    flex: 1 1 136px;
  }
}
</style>
