﻿<template>
    <div class="app-container data-workspace-page">
        <div class="data-workspace-layout">
            <aside class="workspace-sidebar">
                <div class="workspace-sidebar-shell">
                    <div class="head-container workspace-sidebar__search">
                        <el-input v-model="name" placeholder="搜索项目或试验名称" clearable prefix-icon="Search" class="workspace-tree-search"></el-input>
                    </div>
                    <div class="body-container workspace-sidebar__tree">
                        <el-tree
                            :data="treeTableOptions"
                            :props="{ label: 'label', children: 'children' }"
                            :expand-on-click-node="false"
                            :filter-node-method="filterNode"
                            ref="TreeRef"
                            node-key="id"
                            highlight-current
                            default-expand-all
                            @node-click="handleNodeClick"
                        >
                            <template #default="{ data }">
                                <div class="tree-node-content">
                                    <span class="tree-node-content__label">{{ data.label }}</span>
                                    <el-dropdown
                                        trigger="click"
                                        placement="bottom-end"
                                        popper-class="tree-node-menu-popper"
                                        @command="command => handleTreeAction(command, data)"
                                    >
                                        <el-button class="tree-node-content__action" link @click.stop>
                                            <el-icon><MoreFilled /></el-icon>
                                        </el-button>
                                        <template #dropdown>
                                            <el-dropdown-menu>
                                                <el-dropdown-item command="detail" v-hasPermi="['data:info:query']">
                                                    <el-icon class="tree-node-menu__icon"><ViewIcon /></el-icon>
                                                    <span>详情</span>
                                                </el-dropdown-item>
                                                <el-dropdown-item command="edit" v-hasPermi="['data:info:edit']">
                                                    <el-icon class="tree-node-menu__icon"><EditIcon /></el-icon>
                                                    <span>编辑</span>
                                                </el-dropdown-item>
                                                <el-dropdown-item command="delete" class="tree-node-menu__item--danger" divided v-hasPermi="['data:info:remove']">
                                                    <el-icon class="tree-node-menu__icon"><DeleteIcon /></el-icon>
                                                    <span>删除</span>
                                                </el-dropdown-item>
                                            </el-dropdown-menu>
                                        </template>
                                    </el-dropdown>
                                </div>
                            </template>
                        </el-tree>
                    </div>
                </div>
            </aside>
            <div class="pane-content data-pane">
                    <section class="action-surface action-surface--merged">
                        <el-form v-show="showSearch" :model="queryParams" ref="queryRef" :inline="true" label-width="88px" class="query-form">
                            <el-form-item label="ID" prop="id">
                                <el-input v-model="queryParams.id" placeholder="请输入数据ID" clearable class="query-control" @keyup.enter="handleQuery" />
                            </el-form-item>
                            <el-form-item label="数据名称" prop="dataName">
                                <el-input v-model="queryParams.dataName" placeholder="请输入数据名称" clearable class="query-control" @keyup.enter="handleQuery" />
                            </el-form-item>
                            <el-form-item label="试验名称" prop="experimentName">
                                <el-input v-model="queryParams.experimentName" placeholder="请输入试验名称" clearable class="query-control" @keyup.enter="handleQuery" />
                            </el-form-item>
                            <el-form-item label="所属项目" prop="projectId">
                                <el-select v-model="queryParams.projectId" placeholder="请选择所属项目" clearable class="query-control">
                                    <el-option v-for="item in projectOptions" :key="item.projectId" :label="item.projectName" :value="item.projectId" />
                                </el-select>
                            </el-form-item>

                            <el-form-item label="创建人" prop="createBy">
                                <el-input v-model="queryParams.createBy" placeholder="请输入创建人" clearable class="query-control" @keyup.enter="handleQuery" />
                            </el-form-item>
                            <el-form-item label="是否模拟" prop="isSimulation">
                                <el-select v-model="queryParams.isSimulation" placeholder="请选择数据类型" clearable class="query-control">
                                    <el-option label="真实" :value="true" />
                                    <el-option label="模拟" :value="false" />
                                </el-select>
                            </el-form-item>
                            <el-form-item label="数据状态" prop="workStatus">
                                <el-input v-model="queryParams.workStatus" placeholder="请输入数据状态" clearable class="query-control" @keyup.enter="handleQuery" />
                            </el-form-item>
                            <el-form-item label="创建时间" class="query-date-item">
                                <el-date-picker
                                    v-model="dateRange"
                                    value-format="YYYY-MM-DD"
                                    type="daterange"
                                    range-separator="-"
                                    start-placeholder="开始日期"
                                    end-placeholder="结束日期"
                                    class="query-date-control"
                                />
                            </el-form-item>
                            <el-form-item class="query-action-item">
                                <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                                <el-button class="query-reset-btn" icon="Refresh" @click="resetQuery">重置</el-button>
                            </el-form-item>
                        </el-form>
                        <div v-show="showSearch" class="action-surface__divider"></div>
                        <div class="action-surface__toolbar">
                            <div class="global-actions-row">
                                <el-button
                                  class="toolbar-action-btn toolbar-action-btn--project"
                                  type="primary"
                                  icon="Plus"
                                  @click="handleAddProject"
                                  v-hasPermi="['data:info:addProject']"
                                >
                                  新增项目
                                </el-button>
                                <el-button
                                  class="toolbar-action-btn toolbar-action-btn--experiment"
                                  type="primary"
                                  icon="Plus"
                                  @click="handleAddExperiment"
                                  v-hasPermi="['data:info:addExperiment']"
                                >
                                  新增试验
                                </el-button>
                                <el-button
                                  class="toolbar-action-btn toolbar-action-btn--import"
                                  type="primary"
                                  icon="Upload"
                                  @click="openFileManager"
                                  v-hasPermi="['dataInfo:info:insert']"
                                >
                                  数据导入
                                </el-button>
                                <el-button
                                  class="toolbar-action-btn toolbar-action-btn--export"
                                  type="primary"
                                  icon="Download"
                                  @click="handleExportData"
                                  v-hasPermi="['dataInfo:info:download']"
                                >
                                  导出
                                </el-button>
                                <el-button
                                  class="toolbar-action-btn toolbar-action-btn--delete"
                                  type="danger"
                                  icon="Delete"
                                  :disabled="multiple"
                                  @click="handleDelete()"
                                  v-hasPermi="['dataInfo:info:delete']"
                                >
                                  删除
                                </el-button>
                            </div>
                            <div class="action-surface__meta">
                                <div class="action-surface__stats">
                                    <span class="action-surface__pill">已选 {{ selectedCount }} 项</span>
                                    <span class="action-surface__pill action-surface__pill--muted">试验 {{ treeExperimentCount }} 个</span>
                                </div>
                                <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
                            </div>
                        </div>
                    </section>

                    <section class="table-surface">
                        <div class="table-surface__header">
                            <div>
                                <p class="table-surface__eyebrow">Dataset View</p>
                                <h4 class="table-surface__title">数据清单</h4>
                            </div>
                            <div class="table-surface__meta">
                                <span class="table-meta-chip">总数 {{ total }}</span>
                                <span class="table-meta-chip table-meta-chip--real">真实 {{ realDataCount }}</span>
                                <span class="table-meta-chip table-meta-chip--simulation">模拟 {{ simulationDataCount }}</span>
                            </div>
                        </div>

                        <el-table v-loading="loading" :data="businessList" class="data-table" empty-text="暂无符合条件的数据" @selection-change="handleSelectionChange">
                            <el-table-column type="selection" width="55" align="center" />
                            <el-table-column label="ID" align="center" prop="id" />
                            <el-table-column label="数据名称" align="center" prop="dataName" :show-overflow-tooltip="true" />
                            <el-table-column label="是否模拟" align="center" prop="isSimulation" >
                                <template #default="scope">
                                    <el-tag class="status-chip" effect="light" round :type="scope.row.isSimulation === true ? 'success' : scope.row.isSimulation === false ? 'warning' : 'info'">
                                        <span v-if="scope.row.isSimulation === true">真实数据</span>
                                        <span v-else-if="scope.row.isSimulation === false">模拟数据</span>
                                        <span v-else>未知类型</span>
                                    </el-tag>
                                </template>
                            </el-table-column>
                            <el-table-column label="所属试验" align="center" prop="experimentName" :show-overflow-tooltip="true" />
                            <el-table-column label="所属项目" align="center" prop="projectName" :show-overflow-tooltip="true" />
                            <el-table-column label="试验目标" align="center" prop="targetType" :show-overflow-tooltip="true" />
                            <el-table-column label="试验时间" align="center" prop="startTime" >
                                <template #default="scope">
                                    <span>{{ parseTime(scope.row.startTime) }}</span>
                                </template>
                            </el-table-column>
                            <el-table-column label="试验地点" align="center" prop="location" :show-overflow-tooltip="true" />
                            <el-table-column label="试验描述" align="center" prop="contentDesc" :show-overflow-tooltip="true" />
                            <el-table-column label="创建人" align="center" prop="createBy" />
                            <el-table-column label="创建时间" align="center" prop="createTime" >
                                <template #default="scope">
                                    <span>{{ parseTime(scope.row.createTime) }}</span>
                                </template>
                            </el-table-column>
                            <el-table-column label="数据种类" align="center" prop="dataType" :show-overflow-tooltip="true">
                                <template #default="scope">
                                    <span class="data-type-pill">{{ scope.row.dataType || '--' }}</span>
                                </template>
                            </el-table-column>
                            <el-table-column label="操作" align="center" class-name="small-padding fixed-width table-action-cell" width="132">
                                <template #default="scope">
                                    <div class="table-action-group">
                                        <el-tooltip content="详情" placement="top">
                                            <el-button link type="primary" icon="View" @click="handleView(scope.row)" v-hasPermi="['dataInfo:info:query']"></el-button>
                                        </el-tooltip>
                                        <el-tooltip content="修改" placement="top">
                                            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['dataInfo:info:update']"></el-button>
                                        </el-tooltip>
                                        <el-tooltip content="删除" placement="top">
                                            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['dataInfo:info:delete']"></el-button>
                                        </el-tooltip>
                                    </div>
                                </template>
                            </el-table-column>
                        </el-table>
                        <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
                    </section>
                </div>
            </div>

        <!-- 添加项目信息对话框 -->
        <el-dialog
            title="新增项目"
            v-model="projectInfoDialogOpen"
            width="560px"
            class="ant-form-dialog"
            :close-on-click-modal="!projectInfoSubmitLoading"
            :close-on-press-escape="!projectInfoSubmitLoading"
            append-to-body
            @closed="resetProjectInfoForm"
        >
            <el-form ref="projectInfoFormRef" :model="projectInfoForm" :rules="projectInfoRules" label-width="84px" class="ant-form-layout">
                <el-form-item label="名称" prop="name">
                    <el-input v-model="projectInfoForm.name" placeholder="请输入项目名称" />
                    <template #error="{ error }">
                        <transition name="form-error-slide">
                            <div v-if="error" class="form-error-tip">{{ error }}</div>
                        </transition>
                    </template>
                </el-form-item>
                <el-form-item label="内容描述" prop="contentDesc">
                    <el-input
                        v-model="projectInfoForm.contentDesc"
                        type="textarea"
                        :maxlength="200"
                        show-word-limit
                        :autosize="{ minRows: 4, maxRows: 6 }"
                        placeholder="请输入内容"
                    />
                    <template #error="{ error }">
                        <transition name="form-error-slide">
                            <div v-if="error" class="form-error-tip">{{ error }}</div>
                        </transition>
                    </template>
                </el-form-item>
                <el-form-item label="路径" prop="path">
                    <el-input
                        v-model="projectInfoForm.path"
                        placeholder="系统自动生成路径"
                        disabled
                    />
                </el-form-item>
            </el-form>
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" class="ant-confirm-btn" :loading="projectInfoSubmitLoading" @click="submitProjectInfoForm">确 定</el-button>
                    <el-button class="ant-cancel-btn" :disabled="projectInfoSubmitLoading" @click="cancelProjectInfoDialog">取 消</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 添加试验信息对话框 -->
        <el-dialog
            title="新增试验"
            v-model="experimentInfoDialogOpen"
            width="620px"
            class="ant-form-dialog"
            :close-on-click-modal="!experimentInfoSubmitLoading"
            :close-on-press-escape="!experimentInfoSubmitLoading"
            append-to-body
            @closed="resetExperimentInfoForm"
        >
            <el-form ref="experimentInfoFormRef" :model="experimentInfoForm" :rules="experimentInfoRules" label-width="84px" class="ant-form-layout">
                <el-form-item label="名称" prop="name">
                    <el-input v-model="experimentInfoForm.name" placeholder="请输入试验名称" />
                    <template #error="{ error }">
                        <transition name="form-error-slide">
                            <div v-if="error" class="form-error-tip">{{ error }}</div>
                        </transition>
                    </template>
                </el-form-item>
                <el-form-item label="所属项目" prop="parentId">
                    <el-select v-model="experimentInfoForm.parentId" placeholder="请选择所属项目" filterable clearable>
                        <el-option
                            v-for="item in projectOptions"
                            :key="item.projectId"
                            :label="item.projectName"
                            :value="item.projectId"
                        />
                    </el-select>
                    <template #error="{ error }">
                        <transition name="form-error-slide">
                            <div v-if="error" class="form-error-tip">{{ error }}</div>
                        </transition>
                    </template>
                </el-form-item>
                <el-form-item label="试验目标" prop="targetId">
                    <el-select v-model="experimentInfoForm.targetId" placeholder="请选择试验目标">
                        <el-option
                            v-for="item in targetTypeOptions"
                            :key="item.targetId"
                            :label="item.targetType"
                            :value="item.targetId"
                        />
                    </el-select>
                    <template #error="{ error }">
                        <transition name="form-error-slide">
                            <div v-if="error" class="form-error-tip">{{ error }}</div>
                        </transition>
                    </template>
                </el-form-item>
                <el-form-item label="试验日期" prop="startTime">
                    <el-date-picker
                        v-model="experimentInfoForm.startTime"
                        type="date"
                        value-format="YYYY-MM-DD"
                        placeholder="选择开始日期"
                        clearable
                    />
                    <template #error="{ error }">
                        <transition name="form-error-slide">
                            <div v-if="error" class="form-error-tip">{{ error }}</div>
                        </transition>
                    </template>
                </el-form-item>
                <el-form-item label="试验地点" prop="location">
                    <el-input v-model="experimentInfoForm.location" placeholder="请输入地点" />
                    <template #error="{ error }">
                        <transition name="form-error-slide">
                            <div v-if="error" class="form-error-tip">{{ error }}</div>
                        </transition>
                    </template>
                </el-form-item>
                <el-form-item label="内容描述" prop="contentDesc">
                    <el-input
                        v-model="experimentInfoForm.contentDesc"
                        type="textarea"
                        :maxlength="200"
                        show-word-limit
                        :autosize="{ minRows: 4, maxRows: 6 }"
                        placeholder="请输入内容"
                    />
                    <template #error="{ error }">
                        <transition name="form-error-slide">
                            <div v-if="error" class="form-error-tip">{{ error }}</div>
                        </transition>
                    </template>
                </el-form-item>
                <el-form-item label="路径" prop="path">
                    <el-input
                        v-model="experimentInfoForm.path"
                        placeholder="系统自动生成路径"
                        disabled
                    />
                </el-form-item>

                <el-divider content-position="left">试验文件上传</el-divider>
                <el-alert
                    title="支持上传普通文件、二进制文件、文件夹和 ZIP 压缩包，文件夹结构会保留，ZIP 会自动解压到当前试验目录。"
                    type="info"
                    :closable="false"
                    show-icon
                    class="experiment-upload__alert"
                />
                <el-upload
                    ref="experimentUploadRef"
                    drag
                    action=""
                    :auto-upload="false"
                    :show-file-list="false"
                    multiple
                    :disabled="experimentInfoSubmitLoading"
                    :accept="EXPERIMENT_UPLOAD_ACCEPT"
                    class="experiment-upload"
                    :on-change="handleExperimentDraftChange"
                >
                    <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                    <div class="el-upload__text">拖拽试验文件到此处，或<em>点击选择文件</em></div>
                    <template #tip>
                        <div class="experiment-upload__tip">支持 ZIP、CSV、Excel、TXT、JSON、Word、PDF、BIN、DAT、RAW；如需上传整个文件夹，请使用下方“选择文件夹”。</div>
                    </template>
                </el-upload>
                <input
                    ref="experimentFolderInputRef"
                    class="experiment-upload__folder-input"
                    type="file"
                    webkitdirectory
                    multiple
                    :disabled="experimentInfoSubmitLoading"
                    :accept="EXPERIMENT_UPLOAD_ACCEPT"
                    @change="handleExperimentFolderChange"
                />
                <div class="experiment-upload__meta">
                    <span class="experiment-upload__count">已选择 {{ experimentDraftFiles.length }} 个待上传文件</span>
                    <div class="experiment-upload__actions">
                        <el-button link type="primary" :disabled="experimentInfoSubmitLoading" @click="openExperimentFolderPicker">
                            <el-icon><ElIconFolder /></el-icon>
                            <span>选择文件夹</span>
                        </el-button>
                        <el-button link type="primary" :disabled="!experimentDraftFiles.length || experimentInfoSubmitLoading" @click="clearExperimentDraftFiles">清空文件</el-button>
                    </div>
                </div>
                <div v-if="experimentUploadProgress.visible" class="experiment-upload__progress">
                    <div class="experiment-upload__progress-text">{{ experimentUploadProgress.text }}</div>
                    <el-progress :percentage="experimentUploadProgress.percentage" :status="experimentUploadProgress.status" :stroke-width="10" />
                </div>
                <div v-if="experimentDraftFiles.length" class="experiment-upload__list">
                    <div v-for="file in experimentDraftFiles" :key="file.uid" class="experiment-upload__list-item">
                        <div class="experiment-upload__list-main">
                            <el-icon class="experiment-upload__list-icon"><ElIconDocument /></el-icon>
                            <span class="experiment-upload__list-name" :title="file.name">{{ file.name }}</span>
                        </div>
                        <div class="experiment-upload__list-side">
                            <span class="experiment-upload__list-size">{{ formatExperimentFileSize(file.size) }}</span>
                            <el-button link type="danger" :disabled="experimentInfoSubmitLoading" @click="removeExperimentDraftFile(file.uid)">移除</el-button>
                        </div>
                    </div>
                </div>
            </el-form>
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" class="ant-confirm-btn" :loading="experimentInfoSubmitLoading" @click="submitExperimentInfoForm">确 定</el-button>
                    <el-button class="ant-cancel-btn" :disabled="experimentInfoSubmitLoading" @click="cancelExperimentInfoDialog">取 消</el-button>
                </div>
            </template>
        </el-dialog>

        <el-drawer
            v-model="treeDetailOpen"
            direction="rtl"
            size="520px"
            class="info-detail-drawer"
            append-to-body
            :with-header="false"
            @closed="resetTreeDetailForm"
        >
            <div class="detail-drawer">
                <div class="detail-drawer__header">
                    <div class="detail-drawer__title-row">
                        <h3 class="detail-drawer__title">详情信息</h3>
                        <el-tag class="detail-status-tag" :type="getTreeDetailStatusType()" effect="dark">
                            {{ getTreeDetailStatusText() }}
                        </el-tag>
                    </div>
                    <p class="detail-drawer__id">编号：{{ treeDetailForm.id || '--' }}</p>
                </div>

                <transition name="drawer-content-fade" appear>
                    <div v-if="treeDetailOpen" class="detail-drawer__body">
                        <el-card class="detail-card" shadow="hover">
                            <template #header>
                                <div class="detail-card__header">基础信息</div>
                            </template>
                            <div class="detail-grid">
                                <div class="detail-item">
                                    <span class="detail-label">名称</span>
                                    <span class="detail-value">{{ treeDetailForm.name || '--' }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">类型</span>
                                    <span class="detail-value">{{ getTreeInfoTypeLabel(treeDetailForm.type) }}</span>
                                </div>
                                <div class="detail-item" v-if="treeDetailForm.targetType">
                                    <span class="detail-label">目标</span>
                                    <span class="detail-value">{{ treeDetailForm.targetType }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">地点</span>
                                    <span class="detail-value">{{ treeDetailForm.location || '--' }}</span>
                                </div>
                            </div>
                        </el-card>

                        <el-card class="detail-card" shadow="hover">
                            <template #header>
                                <div class="detail-card__header">时间与人员</div>
                            </template>
                            <div class="detail-grid">
                                <div class="detail-item">
                                    <span class="detail-label">时间</span>
                                    <span class="detail-value">{{ formatTreeStartTime(treeDetailForm.startTime) || '--' }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">创建时间</span>
                                    <span class="detail-value">{{ treeDetailForm.createTime || '--' }}</span>
                                </div>
                                <div class="detail-item">
                                    <span class="detail-label">创建人</span>
                                    <span class="detail-value">{{ treeDetailForm.createBy || '暂无创建人' }}</span>
                                </div>
                            </div>
                        </el-card>

                        <el-card class="detail-card" shadow="hover">
                            <template #header>
                                <div class="detail-card__header">附加信息</div>
                            </template>
                            <div class="detail-stack">
                                <div class="detail-item detail-item--column">
                                    <span class="detail-label">路径</span>
                                    <span class="detail-value">{{ treeDetailForm.fullPath || '--' }}</span>
                                </div>
                                <div class="detail-item detail-item--column">
                                    <span class="detail-label">内容描述</span>
                                    <span class="detail-value">{{ treeDetailForm.contentDesc || '--' }}</span>
                                </div>
                            </div>
                        </el-card>
                    </div>
                </transition>

                <div class="detail-drawer__footer">
                    <el-button @click="treeDetailOpen = false">关 闭</el-button>
                </div>
            </div>
        </el-drawer>

        <el-drawer
            v-model="treeEditOpen"
            direction="rtl"
            size="560px"
            class="info-edit-drawer"
            append-to-body
            :with-header="false"
            @closed="resetTreeEditForm"
        >
            <div class="drawer-form-shell">
                <div class="drawer-form-shell__header">
                    <div>
                        <h3 class="drawer-form-shell__title">{{ treeEditTitle }}</h3>
                        <p class="drawer-form-shell__subtitle">修改{{ getTreeInfoTypeLabel(treeEditForm.type) }}信息</p>
                    </div>
                </div>

                <div class="drawer-form-shell__body">
                    <el-form ref="treeEditFormRef" :model="treeEditForm" :rules="infoRules" label-width="84px" class="ant-form-layout">
                        <el-form-item label="名称" prop="name">
                            <el-input v-model="treeEditForm.name" placeholder="请输入名称" />
                            <template #error="{ error }">
                                <transition name="form-error-slide">
                                    <div v-if="error" class="form-error-tip">{{ error }}</div>
                                </transition>
                            </template>
                        </el-form-item>
                        <el-form-item label="所属项目" prop="parentId" v-if="treeEditForm.type === 'experiment'">
                            <el-select v-model="treeEditForm.parentId" placeholder="请选择所属项目" filterable clearable>
                                <el-option
                                    v-for="item in projectOptions"
                                    :key="item.projectId"
                                    :label="item.projectName"
                                    :value="item.projectId"
                                />
                            </el-select>
                            <template #error="{ error }">
                                <transition name="form-error-slide">
                                    <div v-if="error" class="form-error-tip">{{ error }}</div>
                                </transition>
                            </template>
                        </el-form-item>
                        <el-form-item label="试验目标" prop="targetId" v-if="treeEditForm.type === 'experiment'">
                            <el-select v-model="treeEditForm.targetId" placeholder="请选择试验目标">
                                <el-option
                                    v-for="item in targetTypeOptions"
                                    :key="item.targetId"
                                    :label="item.targetType"
                                    :value="item.targetId"
                                />
                            </el-select>
                            <template #error="{ error }">
                                <transition name="form-error-slide">
                                    <div v-if="error" class="form-error-tip">{{ error }}</div>
                                </transition>
                            </template>
                        </el-form-item>
                        <el-form-item label="试验日期" prop="startTime" v-if="treeEditForm.type === 'experiment'">
                            <el-date-picker
                                v-model="treeEditForm.startTime"
                                type="date"
                                value-format="YYYY-MM-DD"
                                placeholder="选择开始日期"
                                clearable
                            />
                            <template #error="{ error }">
                                <transition name="form-error-slide">
                                    <div v-if="error" class="form-error-tip">{{ error }}</div>
                                </transition>
                            </template>
                        </el-form-item>
                        <el-form-item label="试验地点" prop="location" v-if="treeEditForm.type === 'experiment'">
                            <el-input v-model="treeEditForm.location" placeholder="请输入地点" />
                            <template #error="{ error }">
                                <transition name="form-error-slide">
                                    <div v-if="error" class="form-error-tip">{{ error }}</div>
                                </transition>
                            </template>
                        </el-form-item>
                        <el-form-item label="内容描述" prop="contentDesc">
                            <el-input
                                v-model="treeEditForm.contentDesc"
                                type="textarea"
                                :maxlength="200"
                                show-word-limit
                                :autosize="{ minRows: 4, maxRows: 6 }"
                                placeholder="请输入内容"
                            />
                            <template #error="{ error }">
                                <transition name="form-error-slide">
                                    <div v-if="error" class="form-error-tip">{{ error }}</div>
                                </transition>
                            </template>
                        </el-form-item>
                        <el-form-item label="路径" prop="fullPath">
                            <el-input v-model="treeEditForm.fullPath" placeholder="请输入路径" disabled/>
                        </el-form-item>
                        <el-form-item label="创建人" v-if="treeEditForm.createBy">
                            <el-input :model-value="treeEditForm.createBy" disabled />
                        </el-form-item>
                        <el-form-item label="创建时间" v-if="treeEditForm.createTime">
                            <el-input :model-value="formatTreeCreateTime(treeEditForm.createTime)" disabled />
                        </el-form-item>
                    </el-form>
                </div>

                <div class="drawer-form-shell__footer">
                    <el-button type="primary" class="ant-confirm-btn" :loading="treeSubmitLoading" @click="submitTreeEdit">保 存</el-button>
                    <el-button class="ant-cancel-btn" :disabled="treeSubmitLoading" @click="treeEditOpen = false">取 消</el-button>
                </div>
            </div>
        </el-drawer>

        <!-- 添加或修改数据对话框 -->
        <el-dialog :title="title" v-model="open" width="700px" append-to-body>
            <el-form ref="dataRef" :model="form" :rules="rules" label-width="100px">
                <!-- 可编辑字段 -->
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="数据名称" prop="dataName">
                            <el-input v-model="form.dataName" placeholder="请输入数据名称" />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="是否模拟" prop="isSimulation">
                            <el-select v-model="form.isSimulation" placeholder="请选择">
                                <el-option label="真实" :value="true" />
                                <el-option label="模拟" :value="false" />
                            </el-select>
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="数据种类" prop="dataType">
                            <el-select v-model="form.dataType" placeholder="请选择数据种类">
                                <el-option label="ADS-B目标元数据" value="ADS-B目标元数据" />
                                <el-option label="AIS目标元数据" value="AIS目标元数据" />
                                <el-option label="通信情报日志元数据" value="通信情报日志元数据" />
                                <el-option label="交战闭锁元数据" value="交战闭锁元数据" />
                                <el-option label="电子战截获元数据" value="电子战截获元数据" />
                                <el-option label="载机姿态元数据" value="载机姿态元数据" />
                                <el-option label="惯导状态元数据" value="惯导状态元数据" />
                                <el-option label="被动探测元数据" value="被动探测元数据"/>
                                <el-option label="雷达系统航迹元数据" value="雷达系统航迹元数据"/>
                                <el-option label="目标牵引询问元数据" value="目标牵引询问元数据"/>
                                <el-option label="其他元数据" value="其他元数据"/>
                            </el-select>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="文件名称" prop="fileName" v-if="title === '修改数据'">
                            <el-input v-model="form.fileName" placeholder="请输入文件名称" />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row v-if="title === '修改数据'">
                    <el-col :span="24">
                        <el-form-item label="修改目录">
                            <el-tree-select
                                v-model="selectedMovePathNodeId"
                                :data="movePathTreeOptions"
                                :props="{ label: 'label', children: 'children', disabled: 'disabled' }"
                                value-key="id"
                                placeholder="请选择修改的目标文件目录"
                                check-strictly
                                clearable
                                filterable
                                @change="handleMovePathChange"
                            />
                        </el-form-item>
                    </el-col>
                </el-row>

                <!-- 只读字段 -->
                <el-divider />
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="ID">
                            <el-input :value="form.id" disabled />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="所属试验">
                            <el-input :value="moveTargetExperimentName || form.experimentName" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="所属项目">
                            <el-input :value="moveTargetProjectName || form.projectName" disabled />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="试验目标">
                            <el-input :value="form.targetType" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="24">
                        <el-form-item label="当前路径">
                            <el-input :value="form.fullPath" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="试验时间">
                            <el-input :value="parseTime(form.startTime)" disabled />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="试验地点">
                            <el-input :value="form.location" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="24">
                        <el-form-item label="内容描述">
                            <el-input :value="form.contentDesc" type="textarea" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="创建人">
                            <el-input :value="form.createBy" disabled />
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="创建时间">
                            <el-input :value="parseTime(form.createTime)" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
            </el-form>
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" @click="submitForm">确 定</el-button>
                    <el-button @click="cancel">取 消</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 详情对话框 -->
        <el-dialog title="数据详情" v-model="openView" width="700px" append-to-body>
            <el-form :model="form" label-width="100px">
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="ID">{{ form.id }}</el-form-item>
                        <el-form-item label="数据名称">{{ form.dataName }}</el-form-item>
                        <el-form-item label="文件名称">{{ form.fileName }}</el-form-item>
                        <el-form-item label="是否模拟">
                            <span v-if="form.isSimulation === true">真实数据</span>
                            <span v-else-if="form.isSimulation === false">模拟数据</span>
                        </el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="所属试验">{{ form.experimentName }}</el-form-item>
                        <el-form-item label="所属项目">{{ form.projectName }}</el-form-item>
                        <el-form-item label="试验目标">{{ form.targetType }}</el-form-item>
                    </el-col>
                    <el-col :span="24">
                        <el-form-item label="路径">{{ form.dataFilePath }}</el-form-item>
                    </el-col>
                    <el-col :span="12">
                        <el-form-item label="试验时间">{{ parseTime(form.startTime) }}</el-form-item>
                        <el-form-item label="试验地点">{{ form.location }}</el-form-item>
                    </el-col>
                    <el-col :span="24">
                        <el-form-item label="内容描述">{{ form.contentDesc }}</el-form-item>
                        <el-form-item label="创建人">{{ form.createBy }}</el-form-item>
                        <el-form-item label="创建时间">{{ parseTime(form.createTime) }}</el-form-item>
                    </el-col>
                </el-row>
            </el-form>

            <template #footer>
                <div class="dialog-footer">
                    <el-button @click="openView = false">关 闭</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 文件管理器 (数据导入) 对话框 -->
        <el-dialog v-model="importVisible" title="数据导入" width="60%" append-to-body @closed="resetUploadData" class="import-dialog">
            <div class="import-dialog-body">
                <!-- 数据信息表单 -->
                <el-form ref="uploadDataFormRef" :model="uploadDataForm" :rules="uploadDataRules" label-width="100px" class="import-form">
                    <el-row>
                        <el-col :span="12">
                            <el-form-item label="数据名称" prop="dataName">
                                <el-input v-model="uploadDataForm.dataName" placeholder="为空则使用文件名" />
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="所属试验" prop="experimentId">
                                <el-tree-select
                                    v-model="uploadDataForm.experimentId"
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
                                <el-select v-model="uploadDataForm.targetId" placeholder="请选择试验目标" @change="handleTargetChange">
                                    <el-option v-for="item in targetTypeOptions" :key="item.targetId" :label="item.targetType" :value="item.targetId" />
                                </el-select>
                            </el-form-item>
                        </el-col>
                        <el-col :span="12">
                            <el-form-item label="数据种类" prop="dataType">
                                <el-select v-model="uploadDataForm.dataType" placeholder="请选择数据种类">
                                    <el-option label="载机惯导数据" value="载机惯导数据" />
                                    <el-option label="载机姿态数据" value="载机姿态数据" />
                                    <el-option label="雷达航迹数据" value="雷达航迹数据" />
                                    <el-option label="电子战数据" value="电子战数据" />
                                    <el-option label="通侦数据" value="通侦数据" />
                                    <el-option label="ADS-B数据" value="ADS-B数据" />
                                    <el-option label="AIS数据" value="AIS数据" />
                                    <el-option label="目标牵引与询问数据" value="目标牵引与询问数据"/>
                                    <el-option label="方位数据" value="方位数据"/>
                                    <el-option label="闭锁信息数据" value="闭锁信息数据"/>
                                </el-select>
                            </el-form-item>
                        </el-col>
                    </el-row>
                    <el-row>
                        <el-col :span="12">
                            <el-form-item label="是否模拟" prop="isSimulation">
                                <el-radio-group v-model="uploadDataForm.isSimulation">
                                    <el-radio :label="true">真实</el-radio>
                                    <el-radio :label="false">模拟</el-radio>
                                </el-radio-group>
                            </el-form-item>
                        </el-col>
                    </el-row>
                </el-form>

                <!-- 文件上传 -->
                <el-divider>选择文件上传至当前文件夹</el-divider>
                <el-upload drag action="" :auto-upload="false" :on-change="handleFileSelect" v-model:file-list="uploadFiles" class="import-upload">
                    <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                    <div class="el-upload__text">拖到此处或<em>点击选择文件</em></div>
                </el-upload>
            </div>
            <template #footer>
                <el-button @click="cancelUpload">取消</el-button>
                <el-button type="primary" @click="submitUpload" :loading="fileLoading">导入数据</el-button>
            </template>
        </el-dialog>

        <!-- 业务数据详情 (文件预览) 对话框 -->
        <el-dialog
            v-if="false"
            ref="detailDialogRef"
            v-model="detailVisible"
            :width="detailDialogWidth"
            :top="detailDialogTop"
            :fullscreen="detailDialogFullscreen"
            :modal="!detailDialogMinimized"
            :lock-scroll="!detailDialogMinimized"
            append-to-body
            draggable
            overflow
            :show-close="false"
            class="detail-preview-dialog"
            :class="{
                'is-window-minimized': detailDialogMinimized,
                'is-window-fullscreen': detailDialogFullscreen
            }"
            @closed="handleDetailDialogClosed"
        >
            <template #header>
                <div class="detail-preview-dialog__header">
                    <span class="detail-preview-dialog__title">{{ detailTitle }}</span>
                    <div class="detail-preview-dialog__actions" @mousedown.stop>
                        <el-button
                            circle
                            text
                            :title="detailDialogMinimized ? '还原' : '最小化'"
                            @click="toggleDetailDialogMinimize"
                        >
                            <el-icon>
                                <RestoreIcon v-if="detailDialogMinimized" />
                                <MinusIcon v-else />
                            </el-icon>
                        </el-button>
                        <el-button
                            circle
                            text
                            :title="detailDialogFullscreen ? '还原' : '最大化'"
                            @click="toggleDetailDialogFullscreen"
                        >
                            <el-icon>
                                <RestoreIcon v-if="detailDialogFullscreen" />
                                <FullScreenIcon v-else />
                            </el-icon>
                        </el-button>
                        <el-button circle text title="关闭" @click="detailVisible = false">
                            <el-icon><CloseIcon /></el-icon>
                        </el-button>
                    </div>
                </div>
            </template>
            <div v-if="detailFile">
                <div v-if="isDetailTabularFile">
                    <div v-loading="detailPreviewLoading">
                        <el-table v-if="detailTableRows.length > 0" :data="detailTableRows" border stripe :height="detailPreviewContentHeight">
                            <el-table-column
                                v-for="header in detailTableColumns"
                                :key="header"
                                :prop="header"
                                :label="header"
                                show-overflow-tooltip
                            />
                        </el-table>
                        <el-empty v-else :description="detailPreviewMessage || '暂无预览数据'" />
                    </div>
                    <pagination
                        v-show="detailPreviewTotal > 0"
                        :total="detailPreviewTotal"
                        v-model:page="detailPreviewPageNum"
                        v-model:limit="detailPreviewPageSize"
                        @pagination="loadDetailTablePreview"
                    />
                </div>
                <div v-else-if="isDetailTextFile">
                    <div v-loading="detailPreviewLoading">
                        <div v-if="detailTextLines.length > 0" class="detail-text-preview" :style="{ height: detailPreviewContentHeight }">
                            <div
                                v-for="(line, index) in detailTextLines"
                                :key="`${detailPreviewPageNum}-${index}`"
                                class="detail-text-preview__line"
                            >
                                <span class="detail-text-preview__line-number">{{ detailPreviewLineStart + index + 1 }}</span>
                                <pre class="detail-text-preview__line-content">{{ line }}</pre>
                            </div>
                        </div>
                        <el-empty v-else :description="detailPreviewMessage || '暂无预览数据'" />
                    </div>
                    <pagination
                        v-show="detailPreviewTotal > 0"
                        :total="detailPreviewTotal"
                        v-model:page="detailPreviewPageNum"
                        v-model:limit="detailPreviewPageSize"
                        @pagination="loadDetailTablePreview"
                    />
                </div>
                <div v-else-if="isDetailPdfFile" class="detail-pdf-preview">
                    <div v-loading="detailPreviewLoading" class="detail-pdf-preview__container" :style="{ height: detailPreviewContentHeight }">
                        <iframe v-if="detailPdfUrl" :src="detailPdfUrl" class="detail-pdf-preview__frame" title="PDF预览" />
                        <el-empty v-else :description="detailPreviewMessage || '暂无 PDF 预览内容'" />
                    </div>
                </div>
                <div v-else-if="isDetailBinaryFile" class="detail-preview__unsupported">
                    <el-empty :description="detailPreviewMessage || '暂不支持预览二进制文件，请下载后查看'" />
                </div>
                <div v-else class="detail-preview__unsupported">
                    <el-empty :description="detailPreviewMessage || '暂不支持在线预览该文件'" />
                </div>
            </div>
            <el-empty v-else description="暂无文件信息或路径无效" />
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" @click="handleDownloadDetailFile(detailFile)" v-if="detailFile" v-hasPermi="['dataInfo:info:download']">下 载</el-button>
                    <el-button @click="detailVisible = false">关 闭</el-button>
                </div>
            </template>
        </el-dialog>

        <teleport to="body">
            <div v-if="detailVisible" class="data-detail-window-layer" :class="{ 'is-minimized': detailDialogMinimized }">
                <div v-if="!detailDialogMinimized" class="data-detail-window-overlay"></div>
                <section
                    class="data-detail-window"
                    :class="{
                        'is-maximized': detailDialogFullscreen,
                        'is-minimized': detailDialogMinimized,
                        'is-dragging': isDetailDialogDragging
                    }"
                    :style="detailWindowStyle"
                >
                    <header
                        class="data-detail-window__header"
                        :class="{ 'is-draggable': isDetailDialogNormal }"
                        @mousedown="startDetailDialogDrag"
                        @dblclick="toggleDetailDialogFullscreen"
                        @click="handleDetailWindowHeaderClick"
                    >
                        <div class="data-detail-window__title-group">
                            <div class="data-detail-window__title">{{ detailTitle }}</div>
                            <div class="data-detail-window__subtitle">
                                {{ detailFile?.name || detailFile?.dataFilePath || '业务数据详情预览' }}
                            </div>
                        </div>
                        <div class="data-detail-window__controls" @mousedown.stop>
                            <button
                                type="button"
                                class="data-detail-window__control data-detail-window__control--minimize"
                                :aria-label="detailDialogMinimized ? '还原' : '最小化'"
                                @click.stop="toggleDetailDialogMinimize"
                            >
                                <span
                                    class="data-detail-window__control-icon"
                                    :class="detailDialogMinimized ? 'data-detail-window__control-icon--restore' : 'data-detail-window__control-icon--minimize'"
                                ></span>
                            </button>
                            <button
                                type="button"
                                class="data-detail-window__control"
                                :aria-label="detailDialogFullscreen ? '还原' : '最大化'"
                                @click.stop="toggleDetailDialogFullscreen"
                            >
                                <span
                                    class="data-detail-window__control-icon"
                                    :class="detailDialogFullscreen ? 'data-detail-window__control-icon--restore' : 'data-detail-window__control-icon--maximize'"
                                ></span>
                            </button>
                            <button
                                type="button"
                                class="data-detail-window__control data-detail-window__control--close"
                                aria-label="关闭"
                                @click.stop="closeDetailPreviewWindow"
                            >
                                <span class="data-detail-window__control-icon data-detail-window__control-icon--close"></span>
                            </button>
                        </div>
                    </header>

                    <div v-show="!detailDialogMinimized" class="data-detail-window__body">
                        <div class="data-detail-window__content">
                            <div v-if="detailFile" class="data-detail-window__content-inner">
                                <div v-if="isDetailTabularFile" class="detail-preview-pane">
                                    <div v-loading="detailPreviewLoading" class="detail-preview-pane__body">
                                        <el-table v-if="detailTableRows.length > 0" :data="detailTableRows" border stripe :height="detailPreviewContentHeight">
                                            <el-table-column
                                                v-for="header in detailTableColumns"
                                                :key="header"
                                                :prop="header"
                                                :label="header"
                                                show-overflow-tooltip
                                            />
                                        </el-table>
                                        <el-empty v-else :description="detailPreviewMessage || '暂无预览数据'" />
                                    </div>
                                    <pagination
                                        v-show="detailPreviewTotal > 0"
                                        class="detail-preview-pane__pagination"
                                        :total="detailPreviewTotal"
                                        v-model:page="detailPreviewPageNum"
                                        v-model:limit="detailPreviewPageSize"
                                        @pagination="loadDetailTablePreview"
                                    />
                                </div>
                                <div v-else-if="isDetailTextFile" class="detail-preview-pane">
                                    <div v-loading="detailPreviewLoading" class="detail-preview-pane__body">
                                        <div v-if="detailTextLines.length > 0" class="detail-text-preview" :style="{ height: detailPreviewContentHeight }">
                                            <div
                                                v-for="(line, index) in detailTextLines"
                                                :key="`${detailPreviewPageNum}-${index}`"
                                                class="detail-text-preview__line"
                                            >
                                                <span class="detail-text-preview__line-number">{{ detailPreviewLineStart + index + 1 }}</span>
                                                <pre class="detail-text-preview__line-content">{{ line }}</pre>
                                            </div>
                                        </div>
                                        <el-empty v-else :description="detailPreviewMessage || '暂无预览数据'" />
                                    </div>
                                    <pagination
                                        v-show="detailPreviewTotal > 0"
                                        class="detail-preview-pane__pagination"
                                        :total="detailPreviewTotal"
                                        v-model:page="detailPreviewPageNum"
                                        v-model:limit="detailPreviewPageSize"
                                        @pagination="loadDetailTablePreview"
                                    />
                                </div>
                                <div v-else-if="isDetailPdfFile" class="detail-pdf-preview">
                                    <div v-loading="detailPreviewLoading" class="detail-pdf-preview__container" :style="{ height: detailPreviewContentHeight }">
                                        <iframe v-if="detailPdfUrl" :src="detailPdfUrl" class="detail-pdf-preview__frame" title="PDF预览" />
                                        <el-empty v-else :description="detailPreviewMessage || '暂无 PDF 预览内容'" />
                                    </div>
                                </div>
                                <div v-else-if="isDetailBinaryFile" class="detail-preview__unsupported">
                                    <el-empty :description="detailPreviewMessage || '暂不支持预览二进制文件，请下载后查看'" />
                                </div>
                                <div v-else class="detail-preview__unsupported">
                                    <el-empty :description="detailPreviewMessage || '暂不支持在线预览该文件'" />
                                </div>
                            </div>
                            <el-empty v-else class="data-detail-window__empty" description="暂无文件信息或路径无效" />
                        </div>
                        <footer class="data-detail-window__footer">
                            <el-button type="primary" @click="handleDownloadDetailFile(detailFile)" v-if="detailFile" v-hasPermi="['dataInfo:info:download']">下 载</el-button>
                            <el-button @click="closeDetailPreviewWindow">关 闭</el-button>
                        </footer>
                    </div>
                </section>
            </div>
        </teleport>
    </div>
</template>
<script setup name="Business">
import {getdataList,getdataDetail,getMovePathTree,updatedata,deldata,adddata,previewData,downloadData} from '@/api/data/bussiness'
import { getExperimentTree, addProjectInfo, addExperimentInfo, getInfo, updateInfo, delInfo } from "@/api/data/info"
import { addDateRange, blobValidate } from "@/utils/ruoyi"
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import {
  UploadFilled,
  Folder as ElIconFolder,
  Document as ElIconDocument,
  MoreFilled,
  View as ViewIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  FullScreen as FullScreenIcon,
  ScaleToOriginal as RestoreIcon,
  Minus as MinusIcon,
  Close as CloseIcon
} from '@element-plus/icons-vue'
import { saveAs } from 'file-saver'
const dateRange = ref([])
const { proxy } = getCurrentInstance()
const treeTableOptions = ref(undefined)
const projectOptions = ref([])
const open = ref(false)
const openView = ref(false)
const targetTypeOptions = ref([])
const title = ref("")
const projectInfoDialogOpen = ref(false)
const experimentInfoDialogOpen = ref(false)
const projectInfoSubmitLoading = ref(false)
const experimentInfoSubmitLoading = ref(false)
const treeDetailOpen = ref(false)
const treeEditOpen = ref(false)
const treeSubmitLoading = ref(false)
const treeEditTitle = ref("")
const projectInfoFormRef = ref(null)
const experimentInfoFormRef = ref(null)
const treeEditFormRef = ref(null)

const name = ref('')
const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const businessList = ref([])
const ids = ref([])
const single = ref(true)
const multiple = ref(true)

// 文件管理器相关状态
const fileLoading = ref(false)
const importVisible = ref(false)
const uploadFiles = ref([])
const experimentUploadRef = ref(null)
const experimentFolderInputRef = ref(null)
const experimentDraftFiles = ref([])
const experimentUploadProgress = reactive({
  visible: false,
  percentage: 0,
  status: '',
  text: ''
})
const fileManagerPreviewFile = ref(null)
const uploadDataFormRef = ref(null)
const EXPERIMENT_UPLOAD_ACCEPT = '.zip,.csv,.xls,.xlsx,.txt,.json,.doc,.docx,.pdf,.bin,.dat,.raw'
const experimentAllowedExtensions = new Set(['zip', 'csv', 'xls', 'xlsx', 'txt', 'json', 'doc', 'docx', 'pdf', 'bin', 'dat', 'raw'])
let experimentDraftUid = 0

// 详情预览相关状态
const detailDialogRef = ref(null)
const detailVisible = ref(false)
const detailFile = ref(null)
const detailTitle = ref("文件预览")
const detailWindowState = ref('normal')
const detailWindowStateBeforeMinimize = ref('normal')
const isDetailDialogDragging = ref(false)
const detailPreviewLoading = ref(false)
const detailPreviewPageNum = ref(1)
const detailPreviewPageSize = ref(200)
const detailPreviewTotal = ref(0)
const detailTableRows = ref([])
const detailPreviewMessage = ref('')
const detailPdfUrl = ref('')
const movePathTreeOptions = ref([])
const movePathNodeMap = ref({})
const selectedMovePathNodeId = ref(null)
const selectedMovePathNode = ref(null)
const currentFileSuffix = ref('')
const detailViewport = reactive({
  width: 0,
  height: 0
})
const detailWindowRect = reactive({
  left: 0,
  top: 0,
  width: 0,
  height: 0
})
const lastNormalDetailWindowRect = reactive({
  left: 0,
  top: 0,
  width: 0,
  height: 0
})
const detailDragState = reactive({
  startX: 0,
  startY: 0,
  originLeft: 0,
  originTop: 0
})

const createInfoForm = () => ({
  id: null,
  name: null,
  startTime: null,
  contentDesc: null,
  createTime: null,
  location: null,
  targetType: null,
  targetId: null,
  type: 'project',
  parentId: 0,
  path: null,
  fullPath: null,
  createBy: null
})

const createProjectInfoForm = () => ({
  ...createInfoForm(),
  type: 'project',
  parentId: 0
})

const createExperimentInfoForm = () => ({
  ...createInfoForm(),
  type: 'experiment',
  parentId: null
})

const projectInfoForm = reactive(createProjectInfoForm())
const experimentInfoForm = reactive(createExperimentInfoForm())
const treeDetailForm = reactive(createInfoForm())
const treeEditForm = reactive(createInfoForm())
const infoRules = {
  name: [{ required: true, message: "名称不能为空", trigger: "blur" }],
  parentId: [{ required: true, message: "所属项目不能为空", trigger: "change" }],
  startTime: [{ required: true, message: "时间不能为空", trigger: "change" }],
  targetId: [{ required: true, message: "试验目标不能为空", trigger: "change" }],
  location: [{ required: true, message: "地点不能为空", trigger: "blur" }],
  contentDesc: [{ required: true, message: "内容描述不能为空", trigger: "blur" }]
}
const projectInfoRules = {
  name: [{ required: true, message: "项目名称不能为空", trigger: "blur" }],
  contentDesc: [{ required: true, message: "内容描述不能为空", trigger: "blur" }]
}
const experimentInfoRules = {
  name: [{ required: true, message: "试验名称不能为空", trigger: "blur" }],
  parentId: [{ required: true, message: "所属项目不能为空", trigger: "change" }],
  startTime: [{ required: true, message: "试验日期不能为空", trigger: "change" }],
  targetId: [{ required: true, message: "试验目标不能为空", trigger: "change" }],
  location: [{ required: true, message: "试验地点不能为空", trigger: "blur" }],
  contentDesc: [{ required: true, message: "内容描述不能为空", trigger: "blur" }]
}

function isInfoActionCancelled(error) {
  return error === 'cancel' || error === 'close'
}

function showInfoRequestError(error, fallbackMessage) {
  if (isInfoActionCancelled(error)) return
  ElMessage.error(error?.message || fallbackMessage || '操作失败')
}

/** 打开文件管理器 (导入) */
function openFileManager() {
  // 加载必要数据
  if (!treeTableOptions.value || treeTableOptions.value.length === 0) {
      getTreeData()
  }
  importVisible.value = true
  getInfo(null, 'experiment', { silent: true }).then(res => {
    targetTypeOptions.value = res.targetTypes || []
  }).catch(err => {
    showInfoRequestError(err, '获取试验目标失败')
  })
}

/** 导出数据 (下载) */
async function handleExportData() {
  if (ids.value.length === 0) {
    ElMessage.warning("请选择要导出的数据")
    return
  }

  const selectedRows = businessList.value.filter(item => ids.value.includes(item.id))
  const downloadableRows = selectedRows.filter(item => item.experimentId && item.dataFilePath)
  const skippedCount = selectedRows.length - downloadableRows.length

  if (downloadableRows.length === 0) {
    ElMessage.warning("选中的数据中没有关联的文件路径")
    return
  }

  const loadingInstance = ElLoading.service({
    lock: true,
    text: `正在下载(0/${downloadableRows.length})`,
    background: 'rgba(0, 0, 0, 0.4)'
  })

  let successCount = 0
  try {
    for (let i = 0; i < downloadableRows.length; i++) {
      loadingInstance.setText(`正在下载(${i + 1}/${downloadableRows.length})`)
      const row = downloadableRows[i]
      const ok = await handleDownloadDetailFile(row, { silent: true })
      if (ok) successCount++
    }
  } finally {
    loadingInstance.close()
  }

  const failCount = downloadableRows.length - successCount
  if (failCount === 0 && skippedCount === 0) {
    ElMessage.success(`已开始下载${successCount}个文件`)
    return
  }

  ElMessage.warning(`下载完成：成功${successCount}个，失败${failCount}个，跳过${skippedCount}个`)
}

const uploadDataForm = reactive({
  dataName: '',
  experimentId: null,
  targetId: null,
  targetType: null,
  dataType: '',
  isSimulation: true
})
const uploadDataRules = {
  experimentId: [{ required: true, message: "请选择试验", trigger: "change" }],
  targetId: [{ required: true, message: "请选择试验目标", trigger: "change" }],
  dataType: [{ required: true, message: "请选择数据种类", trigger: "change" }],
  isSimulation: [{ required: true, message: "请选择数据类型", trigger: "change" }]
}

const data = reactive({
    form: {},
    queryParams: {
        id: null,
        pageNum: 1,
        pageSize: 10,
        dataName: undefined,
        experimentName: undefined,
        projectId: undefined,
        createBy: undefined,
        isSimulation: undefined,
        dataType: undefined,
        startTime: undefined,
        endTime: undefined,
        workStatus: undefined,
        dataCategory: undefined,
        createTime: undefined,
    },
    rules: {
        dataName: [
            { required: true, message: "数据名称不能为空", trigger: "blur" }
        ],
        isSimulation: [
            { required: true, message: "数据类型不能为空", trigger: "change" }
        ],
        dataType: [
            { required: true, message: "数据种类不能为空", trigger: "blur" }
        ],
        fileName: [
            { required: true, message: "文件名称不能为空", trigger: "blur" }
        ]
    }
})

const {queryParams, form, rules} = toRefs(data)
const filterNode = (value, data) => {
    if (!value) return true
    return data.label && data.label.indexOf(value) !== -1
}

function getTreeData() {
    return getExperimentTree(null).then(response => {
        const transformedData = transformTreeData(response.data)
        treeTableOptions.value = transformedData
        console.log('Tree data loaded:', transformedData)
    }).catch(error => {
        console.error('Failed to load tree data:', error)
    })
}
function getProjects() {
  return getInfo(null, 'experiment', { silent: true }).then(res => {
    projectOptions.value = res.projects || []
  }).catch(err => {
    showInfoRequestError(err, '获取项目信息失败')
  })
}

function resetProjectInfoForm() {
  Object.assign(projectInfoForm, createProjectInfoForm())
  if (projectInfoFormRef.value) {
    projectInfoFormRef.value.clearValidate()
  }
}

function resetExperimentInfoForm() {
  Object.assign(experimentInfoForm, createExperimentInfoForm())
  clearExperimentDraftFiles()
  if (experimentInfoFormRef.value) {
    experimentInfoFormRef.value.clearValidate()
  }
}

function resetTreeDetailForm() {
  Object.assign(treeDetailForm, createInfoForm())
}

function resetTreeEditForm() {
  Object.assign(treeEditForm, createInfoForm())
  if (treeEditFormRef.value) {
    treeEditFormRef.value.clearValidate()
  }
}

async function loadInfoDialogOptions() {
  const response = await getInfo(null, 'experiment', { silent: true })
  projectOptions.value = response.projects || []
  targetTypeOptions.value = response.targetTypes || []
}

function getTreeInfoTypeLabel(type) {
  if (type === 'project') return '项目'
  if (type === 'experiment') return '试验'
  return type || '--'
}

function getTreeDetailStatusText() {
  return treeDetailForm.createTime ? '已处理' : '待处理'
}

function getTreeDetailStatusType() {
  return treeDetailForm.createTime ? 'success' : 'warning'
}

function handleAddProject() {
  resetProjectInfoForm()
  projectInfoDialogOpen.value = true
}

async function handleAddExperiment() {
  resetExperimentInfoForm()
  try {
    await loadInfoDialogOptions()
  } catch (error) {
    showInfoRequestError(error, '获取试验信息失败')
  }
  experimentInfoDialogOpen.value = true
}

function cancelProjectInfoDialog() {
  projectInfoDialogOpen.value = false
}

function cancelExperimentInfoDialog() {
  experimentInfoDialogOpen.value = false
}

function clearExperimentDraftFiles() {
  experimentDraftFiles.value = []
  resetExperimentUploadProgress()
  if (experimentUploadRef.value) {
    experimentUploadRef.value.clearFiles()
  }
  resetExperimentFolderInput()
}

function removeExperimentDraftFile(uid) {
  experimentDraftFiles.value = experimentDraftFiles.value.filter(item => item.uid !== uid)
}

function resetExperimentFolderInput() {
  if (experimentFolderInputRef.value) {
    experimentFolderInputRef.value.value = ''
  }
}

function resetExperimentUploadProgress() {
  experimentUploadProgress.visible = false
  experimentUploadProgress.percentage = 0
  experimentUploadProgress.status = ''
  experimentUploadProgress.text = ''
}

function updateExperimentUploadProgress(event, fileCount) {
  const total = Number(event?.total) || 0
  if (!total) return
  const loaded = Number(event?.loaded) || 0
  const percentage = Math.min(99, Math.max(1, Math.round((loaded / total) * 100)))
  experimentUploadProgress.visible = true
  experimentUploadProgress.percentage = percentage
  experimentUploadProgress.status = ''
  experimentUploadProgress.text = `正在上传 ${fileCount} 个文件... ${percentage}%`
}

function normalizeExperimentUploadPath(path) {
  let normalized = String(path || '').trim()
  if (!normalized) return ''
  if (/^[a-zA-Z]:[\\/]/.test(normalized)) {
    normalized = normalized.split(/[\\/]/).pop() || ''
  }
  normalized = normalized.replace(/\\/g, '/')
  normalized = normalized.replace(/^\/+/, '')
  const segments = normalized
    .split('/')
    .map(segment => segment.trim())
    .filter(segment => segment && segment !== '.')

  if (!segments.length || segments.some(segment => segment === '..')) {
    return ''
  }
  return segments.join('/')
}

function getExperimentFileExtension(path) {
  const normalized = normalizeExperimentUploadPath(path)
  const fileName = normalized.split('/').pop() || ''
  const dotIndex = fileName.lastIndexOf('.')
  return dotIndex > -1 ? fileName.substring(dotIndex + 1).toLowerCase() : ''
}

function isExperimentFileSupported(path) {
  return experimentAllowedExtensions.has(getExperimentFileExtension(path))
}

function buildExperimentDraftKey(rawFile, relativePath) {
  const size = Number(rawFile?.size) || 0
  const lastModified = Number(rawFile?.lastModified) || 0
  return `${relativePath}::${size}::${lastModified}`
}

function createExperimentDraftFile(rawFile, relativePath) {
  experimentDraftUid += 1
  return {
    uid: `experiment-draft-${experimentDraftUid}`,
    name: relativePath,
    size: Number(rawFile?.size) || 0,
    status: 'ready',
    raw: rawFile,
    relativePath
  }
}

function summarizeExperimentFileNames(fileNames) {
  if (!fileNames.length) return ''
  const previewNames = fileNames.slice(0, 3).join('、')
  return fileNames.length > 3 ? `${previewNames} 等 ${fileNames.length} 项` : previewNames
}

function addExperimentDraftFiles(rawFiles = []) {
  if (!Array.isArray(rawFiles) || rawFiles.length === 0) return

  const existingKeys = new Set(
    experimentDraftFiles.value.map(item => buildExperimentDraftKey(item.raw, item.relativePath || item.name))
  )
  const nextFiles = []
  const invalidFiles = []
  const duplicateFiles = []

  rawFiles.forEach(rawFile => {
    if (!(rawFile instanceof File)) return
    const relativePath = normalizeExperimentUploadPath(rawFile.webkitRelativePath || rawFile.name)
    if (!relativePath || !isExperimentFileSupported(relativePath)) {
      invalidFiles.push(rawFile.webkitRelativePath || rawFile.name || '未命名文件')
      return
    }
    const draftKey = buildExperimentDraftKey(rawFile, relativePath)
    if (existingKeys.has(draftKey)) {
      duplicateFiles.push(relativePath)
      return
    }
    existingKeys.add(draftKey)
    nextFiles.push(createExperimentDraftFile(rawFile, relativePath))
  })

  if (nextFiles.length) {
    experimentDraftFiles.value = experimentDraftFiles.value.concat(nextFiles)
  }
  if (invalidFiles.length) {
    ElMessage.warning(`已跳过不支持的文件：${summarizeExperimentFileNames(invalidFiles)}`)
  }
  if (duplicateFiles.length) {
    ElMessage.warning(`已忽略重复文件：${summarizeExperimentFileNames(duplicateFiles)}`)
  }
}

function handleExperimentDraftChange(uploadFile) {
  if (!uploadFile?.raw) return
  addExperimentDraftFiles([uploadFile.raw])
}

function openExperimentFolderPicker() {
  if (experimentInfoSubmitLoading.value) return
  experimentFolderInputRef.value?.click()
}

function handleExperimentFolderChange(event) {
  const rawFiles = Array.from(event?.target?.files || [])
  addExperimentDraftFiles(rawFiles)
  resetExperimentFolderInput()
}

function formatExperimentFileSize(size) {
  const value = Number(size) || 0
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`
  return `${(value / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

function buildExperimentInfoFormData(data) {
  const formData = new FormData()

  Object.entries(data || {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    formData.append(key, value)
  })

  experimentDraftFiles.value.forEach(file => {
    if (!file?.raw) return
    const relativePath = normalizeExperimentUploadPath(file.relativePath || file.name || file.raw.name)
    formData.append('files', file.raw, relativePath || file.raw.name)
    formData.append('relativePaths', relativePath || file.raw.name)
  })

  return formData
}

function formatDateForSubmit(date) {
  if (!date) return null
  if (typeof date === 'string') {
    if (/^\d{4}-\d{2}-\d{2}$/.test(date) || /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(date)) {
      return date
    }
    if (date.includes('CST')) {
      return parseCstTime(date)
    }
    return date
  }
  const value = new Date(date)
  if (Number.isNaN(value.getTime())) return date
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseCstTime(time) {
  if (!time) return null
  const timeStr = String(time).trim()
  if (/^\d{4}-\d{2}-\d{2}$/.test(timeStr)) {
    return timeStr
  }
  if (timeStr.includes('CST')) {
    const match = timeStr.match(/(\w+)\s+(\w+)\s+(\d+)\s+\d{2}:\d{2}:\d{2}\s+\w+\s+(\d{4})/)
    if (match) {
      const monthMap = {
        Jan: '01', Feb: '02', Mar: '03', Apr: '04', May: '05', Jun: '06',
        Jul: '07', Aug: '08', Sep: '09', Oct: '10', Nov: '11', Dec: '12'
      }
      const month = monthMap[match[2]]
      const day = String(match[3]).padStart(2, '0')
      const year = match[4]
      if (month) {
        return `${year}-${month}-${day}`
      }
    }
  }
  try {
    return proxy.parseTime(time, '{y}-{m}-{d}')
  } catch (error) {
    return timeStr
  }
}

function formatTreeStartTime(time) {
  return parseCstTime(time) || ''
}

function formatTreeCreateTime(time) {
  if (!time) return ''
  if (typeof time === 'string' && /^\d{4}-\d{2}-\d{2}( \d{2}:\d{2}:\d{2})?$/.test(time.trim())) {
    return time
  }
  try {
    return proxy.parseTime(time)
  } catch (error) {
    return String(time)
  }
}

function buildTreeInfoForm(row, resData, options = {}) {
  const { detailMode = false } = options
  if (row.type === 'project') {
    return {
      id: resData.projectId ?? row.id,
      name: resData.projectName ?? row.name ?? row.label,
      startTime: parseCstTime(resData.startTime || row.startTime),
      createTime: detailMode ? formatTreeCreateTime(resData.createTime) : resData.createTime,
      location: resData.location ?? row.location ?? null,
      contentDesc: resData.projectContentDesc ?? row.contentDesc ?? null,
      targetType: resData.targetType ?? row.targetType ?? null,
      targetId: null,
      type: 'project',
      parentId: 0,
      path: resData.path ?? row.path ?? null,
      createBy: resData.createBy ?? row.createBy ?? null,
      fullPath: resData.fullPath ?? row.fullPath ?? null,
    }
  }

  return {
    id: resData.experimentId ?? row.id,
    name: resData.experimentName ?? row.name ?? row.label,
    startTime: parseCstTime(resData.startTime || row.startTime),
    createTime: detailMode ? formatTreeCreateTime(resData.createTime) : resData.createTime,
    location: resData.location ?? row.location ?? null,
    contentDesc: resData.contentDesc ?? row.contentDesc ?? null,
    targetType: resData.targetType ?? row.targetType ?? null,
    targetId: resData.targetId ?? row.targetId ?? null,
    type: 'experiment',
    parentId: resData.projectId ?? row.parentId ?? null,
    path: resData.path ?? row.path ?? null,
    createBy: resData.createBy ?? row.createBy ?? null,
    fullPath: resData.fullPath ?? row.fullPath ?? null,
  }
}

function syncTreeEditTargetType() {
  if (treeEditForm.type !== 'experiment') {
    treeEditForm.targetType = null
    return
  }
  const target = targetTypeOptions.value.find(item => item.targetId === treeEditForm.targetId)
  treeEditForm.targetType = target ? target.targetType : null
}

async function openTreeDetailDrawer(row) {
  resetTreeDetailForm()
  try {
    treeEditOpen.value = false
    const response = await getInfo(row.id, row.type, { silent: true })
    Object.assign(treeDetailForm, buildTreeInfoForm(row, response.data || {}, { detailMode: true }))
    treeDetailOpen.value = true
  } catch (error) {
    showInfoRequestError(error, '加载详情失败')
  }
}

async function openTreeEditDrawer(row) {
  resetTreeEditForm()
  try {
    treeDetailOpen.value = false
    const response = await getInfo(row.id, row.type, { silent: true })
    if (row.type === 'experiment') {
      projectOptions.value = response.projects || []
      targetTypeOptions.value = response.targetTypes || []
    }
    Object.assign(treeEditForm, buildTreeInfoForm(row, response.data || {}))
    syncTreeEditTargetType()
    treeEditTitle.value = `编辑${getTreeInfoTypeLabel(row.type)}`
    treeEditOpen.value = true
  } catch (error) {
    showInfoRequestError(error, '加载编辑信息失败')
  }
}

async function removeTreeNode(row) {
  try {
    const nodeName = row.label || row.name || row.id
    await proxy.$modal.confirm(`是否确认删除“${nodeName}”？`)
    await delInfo(row.id, row.type, { silent: true })
    proxy.$modal.msgSuccess("删除成功")
    treeDetailOpen.value = false
    treeEditOpen.value = false
    await Promise.allSettled([getTreeData(), getProjects(), getList()])
  } catch (error) {
    showInfoRequestError(error, '删除失败')
  }
}

function handleTreeAction(command, row) {
  if (command === 'detail') {
    openTreeDetailDrawer(row)
    return
  }
  if (command === 'edit') {
    openTreeEditDrawer(row)
    return
  }
  if (command === 'delete') {
    removeTreeNode(row)
  }
}

function submitTreeEdit() {
  if (treeSubmitLoading.value || !treeEditFormRef.value) return
  treeEditFormRef.value.validate(async valid => {
    if (!valid) return

    treeSubmitLoading.value = true
    try {
      const submitData = JSON.parse(JSON.stringify(treeEditForm))
      submitData.startTime = formatDateForSubmit(submitData.startTime)
      if (submitData.type === 'project') {
        submitData.parentId = 0
        submitData.targetId = null
        submitData.targetType = null
      } else {
        const target = targetTypeOptions.value.find(item => item.targetId === submitData.targetId)
        submitData.targetType = target ? target.targetType : submitData.targetType
      }
      await updateInfo(submitData.id, submitData.type, submitData, { silent: true })
      proxy.$modal.msgSuccess("修改成功")
      treeDetailOpen.value = false
      treeEditOpen.value = false
      await Promise.allSettled([getTreeData(), getProjects(), getList()])
    } catch (error) {
      showInfoRequestError(error, '修改失败')
    } finally {
      treeSubmitLoading.value = false
    }
  })
}

function submitProjectInfoForm() {
  if (projectInfoSubmitLoading.value || !projectInfoFormRef.value) return
  projectInfoFormRef.value.validate(async valid => {
    if (!valid) return

    projectInfoSubmitLoading.value = true
    try {
      const submitData = JSON.parse(JSON.stringify(projectInfoForm))
      submitData.type = 'project'
      submitData.parentId = 0
      submitData.targetId = null
      submitData.targetType = null
      submitData.startTime = null
      submitData.location = null
      await addProjectInfo(submitData, { silent: true })
      proxy.$modal.msgSuccess("新增项目成功")
      projectInfoDialogOpen.value = false
      await Promise.allSettled([getList(), getTreeData(), getProjects()])
    } catch (error) {
      showInfoRequestError(error, '新增项目失败')
    } finally {
      projectInfoSubmitLoading.value = false
    }
  })
}

function submitExperimentInfoForm() {
  if (experimentInfoSubmitLoading.value || !experimentInfoFormRef.value) return
  experimentInfoFormRef.value.validate(async valid => {
    if (!valid) return

    experimentInfoSubmitLoading.value = true
    const uploadCount = experimentDraftFiles.value.length
    resetExperimentUploadProgress()
    if (uploadCount > 0) {
      experimentUploadProgress.visible = true
      experimentUploadProgress.text = `准备上传 ${uploadCount} 个文件...`
    }

    try {
      const submitData = JSON.parse(JSON.stringify(experimentInfoForm))
      submitData.type = 'experiment'
      submitData.startTime = formatDateForSubmit(submitData.startTime)
      const target = targetTypeOptions.value.find(item => item.targetId === submitData.targetId)
      submitData.targetType = target ? target.targetType : submitData.targetType
      const formData = buildExperimentInfoFormData(submitData)
      await addExperimentInfo(formData, {
        silent: true,
        onUploadProgress: event => updateExperimentUploadProgress(event, uploadCount)
      })
      if (uploadCount > 0) {
        experimentUploadProgress.visible = true
        experimentUploadProgress.percentage = 100
        experimentUploadProgress.status = 'success'
        experimentUploadProgress.text = `上传完成，已处理 ${uploadCount} 个文件`
      }
      proxy.$modal.msgSuccess(uploadCount > 0 ? "新增试验并上传文件成功" : "新增试验成功")
      experimentInfoDialogOpen.value = false
      await Promise.allSettled([getList(), getTreeData(), getProjects()])
    } catch (error) {
      if (uploadCount > 0) {
        experimentUploadProgress.visible = true
        experimentUploadProgress.status = 'exception'
        experimentUploadProgress.text = error?.message || '上传失败，请重试'
      }
      showInfoRequestError(error, '新增试验失败')
    } finally {
      experimentInfoSubmitLoading.value = false
    }
  })
}

function normalizeRelativePath(path) {
  let normalized = (path || '/').toString().trim().replace(/\\/g, '/')
  if (!normalized.startsWith('/')) {
    normalized = `/${normalized}`
  }
  normalized = normalized.replace(/\/+/g, '/')
  if (normalized.length > 1 && normalized.endsWith('/')) {
    normalized = normalized.slice(0, -1)
  }
  return normalized || '/'
}

function extractFileSuffix(path) {
  const normalized = normalizeRelativePath(path)
  const fileName = normalized.substring(normalized.lastIndexOf('/') + 1)
  const dotIndex = fileName.lastIndexOf('.')
  return dotIndex > -1 ? fileName.substring(dotIndex) : ''
}

function extractDirPath(path) {
  const normalized = normalizeRelativePath(path)
  const index = normalized.lastIndexOf('/')
  return index <= 0 ? '/' : normalized.substring(0, index)
}

function buildRelativeDataFilePath(directory, fileName, suffix) {
  const normalizedDir = normalizeRelativePath(directory || '/')
  const safeSuffix = suffix || ''
  return normalizedDir === '/' ? `/${fileName}${safeSuffix}` : `${normalizedDir}/${fileName}${safeSuffix}`
}

function buildMovePathNodeMap(nodes, map = {}) {
  ;(nodes || []).forEach(node => {
    if (!node) return
    if (node.id != null) {
      map[node.id] = node
    }
    if (Array.isArray(node.children) && node.children.length) {
      buildMovePathNodeMap(node.children, map)
    }
  })
  return map
}

async function loadMovePathTree() {
  try {
    const response = await getMovePathTree()
    const treeData = response.data || []
    movePathTreeOptions.value = treeData
    movePathNodeMap.value = buildMovePathNodeMap(treeData)
  } catch (error) {
    movePathTreeOptions.value = []
    movePathNodeMap.value = {}
    ElMessage.error('加载目标目录失败: ' + (error.message || '未知错误'))
  }
}

function handleMovePathChange(nodeId) {
  if (!nodeId) {
    selectedMovePathNode.value = null
    return
  }
  const node = movePathNodeMap.value[nodeId]
  selectedMovePathNode.value = node && (node.type === 'dir' || node.type === 'experiment') ? node : null
}

const moveTargetExperimentName = computed(() => selectedMovePathNode.value?.experimentName || '')
const moveTargetProjectName = computed(() => selectedMovePathNode.value?.projectName || '')
const treeExperimentCount = computed(() => {
  let count = 0

  const walk = (nodes = []) => {
    nodes.forEach(node => {
      if (node?.type === 'experiment') count += 1
      if (Array.isArray(node?.children) && node.children.length) {
        walk(node.children)
      }
    })
  }

  walk(treeTableOptions.value || [])
  return count
})
const selectedCount = computed(() => ids.value.length)
const realDataCount = computed(() => businessList.value.filter(item => item?.isSimulation === true).length)
const simulationDataCount = computed(() => businessList.value.filter(item => item?.isSimulation === false).length)
const detailDialogMinimized = computed(() => detailWindowState.value === 'minimized')
const detailDialogFullscreen = computed(() => detailWindowState.value === 'maximized')
const isDetailDialogNormal = computed(() => detailWindowState.value === 'normal')
const detailDialogWidth = computed(() => detailDialogMinimized.value ? '460px' : 'min(1440px, calc(100vw - 16px))')
const detailDialogTop = computed(() => detailDialogFullscreen.value ? '0' : '4vh')
const detailWindowStyle = computed(() => {
  const viewportWidth = detailViewport.width || 1440
  const viewportHeight = detailViewport.height || 900

  if (detailDialogMinimized.value) {
    const width = Math.min(420, Math.max(viewportWidth - 24, 300))
    return {
      left: `${Math.max(viewportWidth - width - 12, 12)}px`,
      top: `${Math.max(viewportHeight - 68, 12)}px`,
      width: `${width}px`,
      height: '56px'
    }
  }

  if (detailDialogFullscreen.value) {
    return {
      left: '12px',
      top: '12px',
      width: `${Math.max(viewportWidth - 24, 320)}px`,
      height: `${Math.max(viewportHeight - 24, 240)}px`
    }
  }

  return {
    left: `${detailWindowRect.left}px`,
    top: `${detailWindowRect.top}px`,
    width: `${detailWindowRect.width}px`,
    height: `${detailWindowRect.height}px`
  }
})
const detailPreviewContentHeight = computed(() => {
  const viewportHeight = detailViewport.height || window.innerHeight || 900
  const windowHeight = detailDialogFullscreen.value
    ? Math.max(viewportHeight - 24, 240)
    : detailWindowRect.height || Math.round(viewportHeight * 0.82)
  const paginationHeight = (isDetailTabularFile.value || isDetailTextFile.value) && detailPreviewTotal.value > 0 ? 56 : 0
  return `${Math.max(windowHeight - 56 - 76 - 40 - paginationHeight, 180)}px`
})
const previewDataFilePath = computed(() => {
  const originalPath = form.value?.dataFilePath
  if (!originalPath) return ''

  const fileName = (form.value?.fileName || '').trim()
  if (!fileName) return originalPath

  const suffix = currentFileSuffix.value || extractFileSuffix(originalPath)
  if (selectedMovePathNode.value && (selectedMovePathNode.value.type === 'dir' || selectedMovePathNode.value.type === 'experiment')) {
    return buildRelativeDataFilePath(selectedMovePathNode.value.relativePath, fileName, suffix)
  }
  return buildRelativeDataFilePath(extractDirPath(originalPath), fileName, suffix)
})


const detailTableColumns = computed(() => Object.keys(detailTableRows.value[0] || {}))
const isDetailTabularFile = computed(() => isTabularFile(detailFile.value))
const isDetailTextFile = computed(() => isTextFile(detailFile.value))
const isDetailPdfFile = computed(() => isPdfFile(detailFile.value))
const isDetailBinaryFile = computed(() => isBinaryFile(detailFile.value))
const detailTextLines = computed(() =>
  detailTableRows.value.map(row => {
    if (row == null) return ''
    if (typeof row === 'string') return row
    if (typeof row === 'object') return Object.values(row).join(' ')
    return String(row)
  })
)
const detailPreviewLineStart = computed(() => (detailPreviewPageNum.value - 1) * detailPreviewPageSize.value)

function clampDetailValue(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function clampDetailMetric(value, preferredMin, max) {
  const safeMax = Math.max(max, 280)
  const safeMin = Math.min(preferredMin, safeMax)
  return Math.min(Math.max(value, safeMin), safeMax)
}

function assignDetailRect(target, source) {
  target.left = source.left
  target.top = source.top
  target.width = source.width
  target.height = source.height
}

function normalizeDetailWindowRect(sourceRect) {
  const viewportWidth = detailViewport.width || window.innerWidth || 1440
  const viewportHeight = detailViewport.height || window.innerHeight || 900
  const width = clampDetailMetric(Number(sourceRect.width) || Math.round(viewportWidth * 0.84), 1120, Math.max(viewportWidth - 32, 360))
  const height = clampDetailMetric(Number(sourceRect.height) || Math.round(viewportHeight * 0.82), 720, Math.max(viewportHeight - 32, 320))
  const maxLeft = Math.max(8, viewportWidth - width - 8)
  const maxTop = Math.max(8, viewportHeight - height - 8)

  return {
    left: clampDetailValue(Number(sourceRect.left) || 0, 8, maxLeft),
    top: clampDetailValue(Number(sourceRect.top) || 0, 8, maxTop),
    width,
    height
  }
}

function createDefaultDetailWindowRect() {
  const viewportWidth = detailViewport.width || window.innerWidth || 1440
  const viewportHeight = detailViewport.height || window.innerHeight || 900
  const width = clampDetailMetric(Math.round(viewportWidth * 0.84), 1160, Math.max(viewportWidth - 48, 380))
  const height = clampDetailMetric(Math.round(viewportHeight * 0.82), 760, Math.max(viewportHeight - 48, 360))

  return normalizeDetailWindowRect({
    left: Math.round((viewportWidth - width) / 2),
    top: Math.max(20, Math.round((viewportHeight - height) / 2)),
    width,
    height
  })
}

function syncDetailDialogRect() {
  assignDetailRect(lastNormalDetailWindowRect, detailWindowRect)
}

function updateDetailDialogViewport() {
  detailViewport.width = window.innerWidth
  detailViewport.height = window.innerHeight

  if (detailVisible.value && isDetailDialogNormal.value) {
    const nextRect = normalizeDetailWindowRect(detailWindowRect)
    assignDetailRect(detailWindowRect, nextRect)
    syncDetailDialogRect()
  }
}

function initializeDetailDialogWindow() {
  updateDetailDialogViewport()
  const rect = createDefaultDetailWindowRect()
  assignDetailRect(detailWindowRect, rect)
  syncDetailDialogRect()
  detailWindowState.value = 'normal'
  detailWindowStateBeforeMinimize.value = 'normal'
}

function handleDetailDialogDragMove(event) {
  if (!isDetailDialogDragging.value) {
    return
  }

  const nextRect = normalizeDetailWindowRect({
    left: detailDragState.originLeft + event.clientX - detailDragState.startX,
    top: detailDragState.originTop + event.clientY - detailDragState.startY,
    width: detailWindowRect.width,
    height: detailWindowRect.height
  })

  detailWindowRect.left = nextRect.left
  detailWindowRect.top = nextRect.top
  syncDetailDialogRect()
}

function stopDetailDialogDrag() {
  if (!isDetailDialogDragging.value) {
    return
  }

  isDetailDialogDragging.value = false
  document.removeEventListener('mousemove', handleDetailDialogDragMove)
  document.removeEventListener('mouseup', stopDetailDialogDrag)
  document.body.style.userSelect = ''
}

function startDetailDialogDrag(event) {
  if (!detailVisible.value || !isDetailDialogNormal.value || event.button !== 0) {
    return
  }

  isDetailDialogDragging.value = true
  detailDragState.startX = event.clientX
  detailDragState.startY = event.clientY
  detailDragState.originLeft = detailWindowRect.left
  detailDragState.originTop = detailWindowRect.top
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', handleDetailDialogDragMove)
  document.addEventListener('mouseup', stopDetailDialogDrag)
  event.preventDefault()
}

function restoreDetailDialogWindow() {
  stopDetailDialogDrag()
  detailWindowState.value = 'normal'
  const rectSource = lastNormalDetailWindowRect.width ? lastNormalDetailWindowRect : createDefaultDetailWindowRect()
  const rect = normalizeDetailWindowRect(rectSource)
  assignDetailRect(detailWindowRect, rect)
  syncDetailDialogRect()
}

function handleDetailWindowHeaderClick() {
  if (!detailDialogMinimized.value) {
    return
  }

  if (detailWindowStateBeforeMinimize.value === 'maximized') {
    detailWindowState.value = 'maximized'
    return
  }

  restoreDetailDialogWindow()
}

function getPreviewFileName(file) {
  if (!file) return ''
  const candidates = [file.name, file.path, file.dataName, file.dataFilePath]
  const withSuffix = candidates.find(item => typeof item === 'string' && item.lastIndexOf('.') > -1)
  return (withSuffix || candidates.find(item => typeof item === 'string') || '').toLowerCase()
}

function getPreviewFileExtension(file) {
  const fileName = getPreviewFileName(file)
  const dotIndex = fileName.lastIndexOf('.')
  return dotIndex > -1 ? fileName.substring(dotIndex + 1) : ''
}

function isTabularFile(file) {
  const extension = getPreviewFileExtension(file)
  return extension === 'csv' || extension === 'xls' || extension === 'xlsx'
}

function isTextFile(file) {
  const extension = getPreviewFileExtension(file)
  return extension === 'txt' || extension === 'json' || extension === 'doc' || extension === 'docx'
}

function isPdfFile(file) {
  return getPreviewFileExtension(file) === 'pdf'
}

function isBinaryFile(file) {
  const extension = getPreviewFileExtension(file)
  return extension === 'bin' || extension === 'dat' || extension === 'raw'
}

function revokeDetailPdfUrl() {
  if (!detailPdfUrl.value) return
  URL.revokeObjectURL(detailPdfUrl.value)
  detailPdfUrl.value = ''
}

function resetDetailTablePreview() {
  detailPreviewPageNum.value = 1
  detailPreviewPageSize.value = 20
  detailPreviewTotal.value = 0
  detailTableRows.value = []
}

function resetDetailPreviewState() {
  detailPreviewLoading.value = false
  detailPreviewMessage.value = ''
  resetDetailTablePreview()
  revokeDetailPdfUrl()
}

function resetDetailDialogWindowState() {
  stopDetailDialogDrag()
  detailWindowState.value = 'normal'
  detailWindowStateBeforeMinimize.value = 'normal'
  const rect = createDefaultDetailWindowRect()
  assignDetailRect(detailWindowRect, rect)
  syncDetailDialogRect()
}

function handleDetailDialogClosed() {
  resetDetailDialogWindowState()
  resetDetailPreviewState()
  detailFile.value = null
  detailTitle.value = '文件预览'
}

function toggleDetailDialogMinimize() {
  if (detailDialogMinimized.value) {
    if (detailWindowStateBeforeMinimize.value === 'maximized') {
      detailWindowState.value = 'maximized'
      return
    }
    restoreDetailDialogWindow()
    return
  }
  stopDetailDialogDrag()
  if (isDetailDialogNormal.value) {
    syncDetailDialogRect()
  }
  detailWindowStateBeforeMinimize.value = detailDialogFullscreen.value ? 'maximized' : 'normal'
  detailWindowState.value = 'minimized'
}

function toggleDetailDialogFullscreen() {
  stopDetailDialogDrag()
  if (detailDialogFullscreen.value) {
    restoreDetailDialogWindow()
    return
  }

  if (isDetailDialogNormal.value) {
    syncDetailDialogRect()
  }
  detailWindowState.value = 'maximized'
}

function closeDetailPreviewWindow() {
  stopDetailDialogDrag()
  detailVisible.value = false
}

async function loadDetailTablePreview() {
  if (!detailFile.value || !detailFile.value.path || !detailFile.value.experimentId) return
  detailPreviewLoading.value = true
  detailPreviewMessage.value = ''
  try {
    const response = await previewData({
      experimentId: detailFile.value.experimentId,
      dataFilePath: detailFile.value.path,
      pageNum: detailPreviewPageNum.value,
      pageSize: detailPreviewPageSize.value
    })
    if (response.code === 200) {
      const pageData = response.data || {}
      detailTableRows.value = Array.isArray(pageData.rows) ? pageData.rows : []
      detailPreviewTotal.value = Number(pageData.total) || 0
      detailPreviewPageNum.value = Number(pageData.pageNum) || detailPreviewPageNum.value
      detailPreviewPageSize.value = Number(pageData.pageSize) || detailPreviewPageSize.value
      detailPreviewMessage.value = pageData.message || ''
    } else {
      detailTableRows.value = []
      detailPreviewTotal.value = 0
      detailPreviewMessage.value = response.msg || '预览失败，请下载后查看'
      ElMessage.error(response.msg || "预览失败")
    }
  } catch (error) {
    detailTableRows.value = []
    detailPreviewTotal.value = 0
    detailPreviewMessage.value = error?.message || '预览失败，请下载后查看'
  } finally {
    detailPreviewLoading.value = false
  }
}

async function loadDetailPdfPreview() {
  if (!detailFile.value || !detailFile.value.dataFilePath || !detailFile.value.experimentId) return
  detailPreviewLoading.value = true
  detailPreviewMessage.value = ''
  revokeDetailPdfUrl()

  try {
    const data = await downloadData({
      id: detailFile.value.id,
      experimentId: detailFile.value.experimentId,
      dataFilePath: detailFile.value.dataFilePath
    })

    if (blobValidate(data)) {
      const pdfBlob = data.type === 'application/pdf' ? data : new Blob([data], { type: 'application/pdf' })
      detailPdfUrl.value = URL.createObjectURL(pdfBlob)
      return
    }

    const responseText = await data.text()
    const responseObj = JSON.parse(responseText)
    detailPreviewMessage.value = responseObj.msg || 'PDF 预览加载失败，请下载后查看'
  } catch (error) {
    detailPreviewMessage.value = error?.message || 'PDF 预览加载失败，请下载后查看'
  } finally {
    detailPreviewLoading.value = false
  }
}


const selectableTreeOptions = computed(() => {
    const disableProjects = (nodes) => {
        return nodes.map(node => ({
            ...node,
            disabled: node.type === 'project',
            children: node.children ? disableProjects(node.children) : []
        }))
    }
    return treeTableOptions.value ? disableProjects(treeTableOptions.value) : []
})

const resetUploadData = () => {
  uploadDataForm.dataName = ''
  uploadDataForm.experimentId = null
  uploadDataForm.targetId = null
  uploadDataForm.targetType = null
  uploadDataForm.dataType = ''
  uploadDataForm.isSimulation = true
  uploadFiles.value = []
  if (uploadDataFormRef.value) {
    uploadDataFormRef.value.resetFields()
  }
}
const handleFileSelect = () => {
  // 文件选择后自动处理
}

/** 处理试验目标选择变化 - 获取对应的 targetType */
const handleTargetChange = (targetId) => {
  const target = targetTypeOptions.value.find(item => item.targetId === targetId)
  if (target) {
    uploadDataForm.targetType = target.targetType
  }
}
const cancelUpload = () => {
  importVisible.value = false
}
const submitUpload = async () => {
  // 校验表单
  if (!uploadDataFormRef.value) return
  await uploadDataFormRef.value.validate(async (valid) => {
    if (!valid) return

    if (uploadFiles.value.length === 0) {
        ElMessage.warning('请选择要上传的文件')
        return
    }

    try {
        for (const file of uploadFiles.value) {
            // 构建业务数据对象
            const businessData = {
                dataName: uploadDataForm.dataName || file.name,
                experimentId: uploadDataForm.experimentId,
                targetId: uploadDataForm.targetId,
                dataType: uploadDataForm.dataType,
                isSimulation: uploadDataForm.isSimulation
            }

            // 调用修改后的API，同时传递数据和文件
            await adddata(businessData, file.raw)
        }
        ElMessage.success('数据导入成功')
        importVisible.value = false
        await getList() // 自动查询业务数据列表
    } catch (err) {
        console.error('导入失败: ' + (err.message || '未知错误'))
    }
  })
}

/** 下载详情中的文件 */
const handleDownloadDetailFile = async (row, options = {}) => {
  const { silent = false } = options
  if(!row.experimentId || !row.dataFilePath){
    if (!silent) ElMessage.warning('缺少下载参数')
    return false
  }
  try {
    const data = await downloadData({
      id: row?.id,
      experimentId: row?.experimentId,
      dataFilePath: row?.dataFilePath
    })
    if (blobValidate(data)) {
      const fileName = row.name || row.dataName || row.dataFilePath.split(/[\\/]/).pop() || 'download'
      saveAs(new Blob([data]), fileName)
      return true
    }
    const resText = await data.text()
    const rspObj = JSON.parse(resText)
    if (!silent) ElMessage.error(rspObj.msg || '下载失败')
    return false
  } catch (e) {
    if (!silent) ElMessage.error('下载失败: ' + (e.message || '未知错误'))
    return false
  }
}
// --- 文件管理器逻辑结束 ---

function transformTreeData(data) {
    if (!data || !Array.isArray(data)) return []

    return data.map(item => ({
        ...item,
        label: item.name, // 为树形组件添加label字段
        children: item.children && item.children.length > 0
            ? transformTreeData(item.children)
            : []
    }))
}

watch(name, val => {
    if (proxy.$refs["TreeRef"]) {
        proxy.$refs["TreeRef"].filter(val)
    }
})

watch(
  () => [treeEditForm.type, treeEditForm.targetId],
  () => {
    syncTreeEditTargetType()
  }
)

watch(detailVisible, visible => {
  if (visible) {
    return
  }

  handleDetailDialogClosed()
})

function handleNodeClick(data) {
    if(data.type==="experiment"){
        queryParams.value.id=undefined
        queryParams.value.experimentName=data.label
        queryParams.value.projectId=data.parentId
    }
    else if(data.type==="project"){
        queryParams.value.id=undefined
        const project = projectOptions.value.find(item => item.projectId == data.id)
        queryParams.value.projectId = project ? project.projectId : data.id
        queryParams.value.experimentName=undefined
    }

    handleQuery()
}

function resetQuery() {
    proxy.resetForm("queryRef")
    dateRange.value = []
    queryParams.value = {
        id: null,
        pageNum: 1,
        pageSize: 10,
        dataName: undefined,
        experimentName: undefined,
        projectId: undefined,
        createBy: undefined,
        isSimulation: undefined,
        dataType: undefined,
        startTime: undefined,
        endTime: undefined,
        workStatus: undefined,
        dataCategory: undefined,
        createTime: undefined,
    }
    handleQuery()
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    dataName: null,
    isSimulation: null,
    dataType: null,
    fileName: null,
    startTime: null,
    location: null,
    contentDesc: null
  }
  selectedMovePathNodeId.value = null
  selectedMovePathNode.value = null
  currentFileSuffix.value = ''
  proxy.resetForm("dataRef")
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row ? [row.id] : ids.value
  proxy.$modal.confirm('是否确认删除ID为"' + _ids + '"的数据项？').then(function() {
    return deldata(_ids)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 修改按钮操作 */
async function handleUpdate(row) {
  reset()
  const id = row.id
  try {
    const [detailResponse] = await Promise.all([
      getdataDetail(id),
      loadMovePathTree()
    ])
    form.value = detailResponse.data
    form.value.id = id
    currentFileSuffix.value = extractFileSuffix(form.value.dataFilePath || '')
    selectedMovePathNodeId.value = null
    selectedMovePathNode.value = null
    open.value = true
    title.value = "修改数据"
  } catch (error) {
    ElMessage.error('加载修改信息失败: ' + (error.message || '未知错误'))
  }
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["dataRef"].validate(valid => {
    if (!valid) return

    const submitData = { ...form.value }
    const fileName = (submitData.fileName || '').trim()
    if (!fileName) {
      ElMessage.warning("文件名称不能为空")
      return
    }

    const suffix = currentFileSuffix.value || extractFileSuffix(submitData.dataFilePath || '')
    let targetDir = extractDirPath(submitData.dataFilePath || '/')

    if (selectedMovePathNode.value && (selectedMovePathNode.value.type === 'dir' || selectedMovePathNode.value.type === 'experiment')) {
      targetDir = selectedMovePathNode.value.relativePath
      submitData.experimentId = selectedMovePathNode.value.experimentId
    }

    submitData.fileName = fileName
    submitData.dataFilePath = buildRelativeDataFilePath(targetDir, fileName, suffix)

    updatedata(submitData).then(() => {
      proxy.$modal.msgSuccess("修改成功")
      open.value = false
      getList()
    }).catch(error => {
      console.error('修改失败: ' + (error.message || '未知错误'))
    })
  })
}

/** 详情按钮操作 (打开文件预览) */
function handleView(row) {
  if (row.dataFilePath) {
    resetDetailDialogWindowState()
    detailFile.value = {
      id: row.id,
      name: row.dataName,
      path: row.dataFilePath,
      dataFilePath: row.dataFilePath,
      experimentId: row.experimentId
    }

    const fileName = getPreviewFileName(row)
    const fileLabel = row.dataName || row.name || '未命名文件'
    resetDetailPreviewState()

    if (isTabularFile(row) || isTextFile(row)) {
      if (fileName.endsWith('.csv')) {
        detailTitle.value = `CSV文件预览: ${row.dataName}`
      } else if (fileName.endsWith('.xlsx') || fileName.endsWith('.xls')) {
        detailTitle.value = `Excel文件预览: ${row.dataName}`
      } else if (fileName.endsWith('.txt')) {
        detailTitle.value = `Txt文件预览: ${row.dataName}`
      } else if (fileName.endsWith('.json')) {
        detailTitle.value = `JSON文件预览: ${row.dataName}`
      } else if (fileName.endsWith('.doc') || fileName.endsWith('.docx')) {
        detailTitle.value = `Word文件预览: ${row.dataName}`
      } else {
        detailTitle.value = `文件预览: ${row.dataName}`
      }
      detailVisible.value = true
      loadDetailTablePreview()
      return
    }

    if (isPdfFile(row)) {
      detailTitle.value = `PDF文件预览: ${fileLabel}`
      detailVisible.value = true
      loadDetailPdfPreview()
      return
    }

    if (isBinaryFile(row)) {
      detailTitle.value = `文件预览: ${fileLabel}`
      detailPreviewMessage.value = '暂不支持预览二进制文件，请下载后查看'
      detailVisible.value = true
      return
    }

    detailTitle.value = `文件预览: ${fileLabel}`
    detailPreviewMessage.value = '暂不支持在线预览该文件'
    detailVisible.value = true
  } else {
    ElMessage.warning("该数据没有关联的文件路径")
  }
}

function handleQuery(){
    getList()
}
function getList(){
    loading.value = true
    return getdataList(addDateRange(queryParams.value, dateRange.value)).then(response => {
      businessList.value = response.rows || (response.data && response.data.rows) || [];
      total.value = response.total || (response.data && response.data.total) || 0;
    }).finally(() => {
      loading.value = false;
    });
}
onMounted(() => {
    updateDetailDialogViewport()
    window.addEventListener('resize', updateDetailDialogViewport)
    getList()
    getTreeData()
    getProjects()
})

onBeforeUnmount(() => {
    stopDetailDialogDrag()
    revokeDetailPdfUrl()
    window.removeEventListener('resize', updateDetailDialogViewport)
})

</script>
<style scoped>
.data-workspace-page {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: calc(100vh - 148px);
  overflow: visible;
}

.table-surface__eyebrow {
  margin: 0;
  color: #405efe;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.table-surface__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.table-meta-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(255, 255, 255, 0.9);
  color: #334155;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  backdrop-filter: blur(10px);
}

.data-workspace-layout {
  display: flex;
  align-items: stretch;
  gap: 20px;
  flex: 1 1 auto;
  min-height: 0;
}

.workspace-sidebar {
  flex: 0 0 340px;
  width: 340px;
  max-width: 100%;
  min-width: 0;
}

.workspace-sidebar-shell,
.data-pane {
  min-height: 0;
}

.workspace-sidebar-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  min-height: calc(100vh - 148px);
  padding: 20px;
  border: 1px solid rgba(226, 232, 240, 0.88);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.96) 100%);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08);
}

.action-surface,
.table-surface {
  border: 1px solid rgba(226, 232, 240, 0.88);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12px 34px rgba(15, 23, 42, 0.06);
}
.table-surface__title {
  margin: 10px 0 0;
  color: #0f172a;
  line-height: 1.25;
}
.table-surface__title {
  font-size: 18px;
}

.workspace-sidebar__search {
  margin: 0;
}

.workspace-tree-search :deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.96);
  box-shadow: 0 0 0 1px rgba(203, 213, 225, 0.85) inset;
}

.workspace-tree-search :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 1px rgba(64, 94, 254, 0.42) inset, 0 0 0 4px rgba(64, 94, 254, 0.12);
}

.workspace-sidebar__tree {
  flex: 1;
  min-height: 0;
  padding: 14px 12px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.96) 0%, rgba(255, 255, 255, 0.98) 100%);
  border: 1px solid rgba(226, 232, 240, 0.92);
  overflow: auto;
}

.body-container :deep(.el-tree) {
  background: transparent;
}

.body-container :deep(.el-tree-node__content) {
  height: 42px;
  border-radius: 14px;
  padding-right: 8px;
  transition: background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.body-container :deep(.el-tree-node__content:hover) {
  background: #f4f7fd;
  transform: translateX(2px);
}

.body-container :deep(.is-current > .el-tree-node__content) {
  background: rgba(64, 94, 254, 0.1);
  box-shadow: inset 0 0 0 1px rgba(64, 94, 254, 0.18);
}

.body-container :deep(.is-current > .el-tree-node__content) .tree-node-content__label {
  color: #2944db;
  font-weight: 700;
}

.tree-node-content {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding-right: 8px;
}

.tree-node-content__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #0f172a;
  font-size: 13px;
  font-weight: 500;
}

.tree-node-content__action {
  width: 28px;
  min-width: 28px;
  height: 28px;
  border-radius: 999px;
  color: #6b7280;
  background: #eef2f7;
  opacity: 0;
  transition: opacity 0.2s ease, background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.body-container :deep(.el-tree-node__content:hover) .tree-node-content__action,
.body-container :deep(.is-current > .el-tree-node__content) .tree-node-content__action {
  opacity: 1;
}

.tree-node-content__action:hover,
.tree-node-content__action:focus {
  color: #374151;
  background: #e2e8f0;
  transform: translateY(-1px);
}

:deep(.tree-node-menu-popper) {
  padding: 8px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  border-radius: 16px;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.14);
}

:deep(.tree-node-menu-popper .el-dropdown-menu) {
  min-width: 136px;
}

:deep(.tree-node-menu-popper .el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  border-radius: 10px;
  color: #111827;
  font-size: 14px;
}

:deep(.tree-node-menu-popper .el-dropdown-menu__item:not(.is-disabled):hover) {
  background: #f3f4f6;
}

:deep(.tree-node-menu-popper .tree-node-menu__item--danger) {
  color: #ef4444;
}

.tree-node-menu__icon {
  font-size: 14px;
}

.pane-content {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
}

.data-pane {
  flex: 1 1 auto;
  gap: 18px;
  padding-bottom: 28px;
}

.action-surface__pill--muted {
  background: rgba(248, 250, 252, 0.96);
  color: #64748b;
  border-color: rgba(226, 232, 240, 0.96);
}

.table-surface__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.query-form,
.action-surface,
.table-surface {
  min-width: 0;
}

.query-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.query-form :deep(.query-date-item) {
  grid-column: span 2;
}

.query-date-control {
  width: 100%;
}

.query-form :deep(.query-action-item) {
  grid-column: 1 / -1;
  justify-self: stretch;
  width: 100%;
  max-width: none;
}

.query-action-item :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
  gap: 12px;
}

.query-action-item :deep(.el-button) {
  min-width: 128px;
  height: 42px;
}

.query-action-item :deep(.query-reset-btn) {
  color: #8b6b2f;
  background: #fdf5e6;
  border-color: #f1dfbb;
}

.query-action-item :deep(.query-reset-btn:hover),
.query-action-item :deep(.query-reset-btn:focus) {
  color: #7a5d26;
  background: #fbefdc;
  border-color: #ead4a7;
}

.action-surface,
.table-surface {
  padding: 20px 22px;
}

.action-surface {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.action-surface__divider {
  height: 1px;
  background: linear-gradient(90deg, rgba(203, 213, 225, 0), rgba(203, 213, 225, 0.88), rgba(203, 213, 225, 0));
}

.action-surface__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.action-surface__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-left: auto;
}

.global-actions-row {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  flex: 1 1 720px;
}

.action-surface__stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.96), rgba(255, 255, 255, 0.98));
}

.action-surface__pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid rgba(226, 232, 240, 0.96);
  background: #ffffff;
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.action-surface__meta :deep(.right-toolbar) {
  margin-left: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.96), rgba(255, 255, 255, 0.98));
}

.action-surface__meta :deep(.right-toolbar .el-button) {
  width: 40px;
  min-width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 999px;
  background: #ffffff;
  color: #0f172a;
  box-shadow: none;
}

.action-surface__meta :deep(.right-toolbar .el-button:hover),
.action-surface__meta :deep(.right-toolbar .el-button:focus) {
  color: #409eff;
  border-color: rgba(64, 158, 255, 0.34);
  background: #f8fbff;
}

.table-surface {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  gap: 16px;
}

.table-surface__meta {
  align-items: center;
  justify-content: flex-end;
}

.table-meta-chip--real {
  background: rgba(16, 185, 129, 0.08);
  border-color: rgba(16, 185, 129, 0.16);
  color: #047857;
}

.table-meta-chip--simulation {
  background: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.18);
  color: #b45309;
}

.data-table {
  border-radius: 18px;
  overflow: hidden;
}

.data-table :deep(.el-table__inner-wrapper::before) {
  display: none;
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

.table-surface :deep(.pagination-container) {
  padding-left: 0;
  padding-right: 0;
}

/* 数据导入弹窗 - 压缩尺寸 */
.import-dialog :deep(.el-dialog__body) {
  padding: 12px 20px;
  max-height: 70vh;
  overflow-y: auto;
}
.import-dialog-body {
  padding: 0;
}
.import-form {
  margin-bottom: 16px;
}
.import-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

/* 路径栏 - 路径框与按钮同一行、同高度对齐 */
.import-path-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.import-path-input {
  flex: 1;
  min-width: 0;
}
.import-path-bar :deep(.el-input-group__append) {
  padding: 0;
}
.import-path-bar :deep(.el-input-group__append .el-button) {
  margin: 0;
  border-radius: 0 4px 4px 0;
}
.import-path-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.import-file-table {
  margin-bottom: 16px;
}
.import-upload {
  margin-bottom: 0;
}

@media (max-width: 1480px) {
  .workspace-sidebar {
    flex-basis: 312px;
    width: 312px;
  }

  .query-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px 20px;
  }

  .query-form :deep(.query-date-item) {
    grid-column: span 1;
  }

  .query-form :deep(.query-action-item) {
    grid-column: 1 / -1;
  }
}

@media (max-width: 992px) {
  .data-workspace-layout {
    flex-direction: column;
  }

  .workspace-sidebar {
    width: 100%;
    flex-basis: auto;
  }

  .workspace-sidebar-shell {
    min-height: auto;
  }

  .action-surface__toolbar,
  .table-surface__header {
    flex-direction: column;
    align-items: stretch;
  }

  .action-surface__meta,
  .table-surface__meta {
    justify-content: flex-start;
    margin-left: 0;
  }

  .global-actions-row {
    flex: 1 1 auto;
  }
}

@media (max-width: 768px) {
  .workspace-sidebar-shell,
  .action-surface,
  .table-surface {
    padding: 16px;
  }

  .detail-drawer__header {
    padding: 16px 16px 12px;
  }

  .detail-drawer__body {
    padding: 14px;
    gap: 14px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .drawer-form-shell__header,
  .drawer-form-shell__body,
  .drawer-form-shell__footer {
    padding-left: 16px;
    padding-right: 16px;
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

  .query-form :deep(.query-date-item),
  .query-form :deep(.query-action-item) {
    grid-column: span 1;
  }

  .query-action-item :deep(.el-form-item__content) {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .query-action-item :deep(.el-button) {
    flex: 1 1 136px;
  }
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

.toolbar-action-btn--import {
  background: linear-gradient(135deg, #66b1ff, #409eff);
}

.toolbar-action-btn--import:hover,
.toolbar-action-btn--import:focus {
  background: linear-gradient(135deg, #79bbff, #53a8ff);
}

.toolbar-action-btn--project {
  background: linear-gradient(135deg, #66b1ff, #409eff);
}

.toolbar-action-btn--project:hover,
.toolbar-action-btn--project:focus {
  background: linear-gradient(135deg, #79bbff, #53a8ff);
}

.toolbar-action-btn--experiment {
  background: linear-gradient(135deg, #66b1ff, #409eff);
}

.toolbar-action-btn--experiment:hover,
.toolbar-action-btn--experiment:focus {
  background: linear-gradient(135deg, #79bbff, #53a8ff);
}

.toolbar-action-btn--export {
  background: linear-gradient(135deg, #66b1ff, #409eff);
}

.toolbar-action-btn--export:hover,
.toolbar-action-btn--export:focus {
  background: linear-gradient(135deg, #79bbff, #53a8ff);
}

.toolbar-action-btn--delete {
  background: linear-gradient(135deg, #ef4444, #dc2626);
}

.toolbar-action-btn--delete:hover,
.toolbar-action-btn--delete:focus {
  background: linear-gradient(135deg, #f87171, #ef4444);
}

.table-action-group {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 96px;
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

.info-detail-drawer :deep(.el-drawer__body),
.info-edit-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.detail-drawer {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f4f7fb;
}

.detail-drawer__header {
  padding: 20px 22px 16px;
  border-bottom: 1px solid #e8edf5;
  background: #fff;
}

.detail-drawer__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-drawer__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #2f3a4a;
}

.detail-status-tag {
  flex-shrink: 0;
}

.detail-drawer__id {
  margin: 10px 0 0;
  color: #5c6778;
  font-size: 14px;
}

.detail-drawer__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-card {
  border: 1px solid #e5ebf3;
}

.detail-card :deep(.el-card__header) {
  padding: 12px 16px;
  border-bottom: 1px solid #edf1f6;
}

.detail-card :deep(.el-card__body) {
  padding: 16px;
}

.detail-card__header {
  font-size: 14px;
  font-weight: 600;
  color: #3d4a5d;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 16px;
}

.detail-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  line-height: 1.6;
}

.detail-item--column {
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  min-width: 72px;
  color: #8a94a6;
  font-size: 13px;
}

.detail-value {
  color: #2f3a4a;
  font-size: 14px;
  word-break: break-all;
}

.detail-drawer__footer {
  padding: 14px 20px;
  border-top: 1px solid #e8edf5;
  background: #fff;
  display: flex;
  justify-content: flex-end;
}

.drawer-form-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #fcfdff 0%, #f8fafc 100%);
}

.drawer-form-shell__header {
  padding: 24px 28px 16px;
  border-bottom: 1px solid #eaecf0;
  background: rgba(255, 255, 255, 0.94);
}

.drawer-form-shell__title {
  margin: 0;
  color: #1f2937;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.drawer-form-shell__subtitle {
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
}

.drawer-form-shell__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 22px 28px 10px;
}

.drawer-form-shell__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 28px 24px;
  border-top: 1px solid #eaecf0;
  background: rgba(255, 255, 255, 0.94);
}

.drawer-content-fade-enter-active,
.drawer-content-fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.drawer-content-fade-enter-from,
.drawer-content-fade-leave-to {
  opacity: 0;
  transform: translateX(10px);
}

.detail-text-preview {
  height: 62vh;
  padding: 8px 0;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: #fafafa;
}

.detail-text-preview__line {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 12px;
  align-items: start;
  padding: 8px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.detail-text-preview__line:last-child {
  border-bottom: none;
}

.detail-text-preview__line-number {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
  text-align: right;
  user-select: none;
}

.detail-text-preview__line-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  color: var(--el-text-color-primary);
  font-family: Consolas, 'Courier New', monospace;
}

.detail-pdf-preview__container {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.detail-pdf-preview__frame {
  width: 100%;
  height: 100%;
  border: none;
}

.detail-preview__unsupported {
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: #fafafa;
}

.data-detail-window-layer {
  position: fixed;
  inset: 0;
  z-index: 2100;
  pointer-events: none;
}

.data-detail-window-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.24);
  backdrop-filter: blur(4px);
  pointer-events: auto;
}

.data-detail-window {
  position: absolute;
  display: flex;
  flex-direction: column;
  min-width: 300px;
  min-height: 56px;
  border: 1px solid rgba(226, 232, 240, 0.96);
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 26px 60px rgba(15, 23, 42, 0.24);
  overflow: hidden;
  pointer-events: auto;
  transition: top 0.2s ease, left 0.2s ease, width 0.2s ease, height 0.2s ease, box-shadow 0.2s ease;
}

.data-detail-window.is-dragging {
  transition: none;
  box-shadow: 0 30px 72px rgba(15, 23, 42, 0.3);
}

.data-detail-window.is-minimized {
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.22);
}

.data-detail-window.is-minimized .data-detail-window__header {
  border-bottom: none;
}

.data-detail-window.is-minimized .data-detail-window__subtitle {
  display: none;
}

.data-detail-window__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  padding-left: 18px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.96);
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  user-select: none;
}

.data-detail-window__header.is-draggable {
  cursor: move;
}

.data-detail-window__title-group {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex: 1 1 auto;
}

.data-detail-window__title {
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.data-detail-window__subtitle {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #64748b;
  font-size: 12px;
  line-height: 1;
}

.data-detail-window__controls {
  display: flex;
  align-items: stretch;
  margin-left: auto;
  flex-shrink: 0;
}

.data-detail-window__control {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  min-width: 46px;
  height: 56px;
  padding: 0;
  border: none;
  background: transparent;
  color: #475569;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.data-detail-window__control:hover {
  background: rgba(148, 163, 184, 0.16);
  color: #0f172a;
}

.data-detail-window__control--close:hover {
  background: #ef4444;
  color: #ffffff;
}

.data-detail-window__control-icon {
  position: relative;
  display: block;
  width: 12px;
  height: 12px;
}

.data-detail-window__control-icon--minimize::before {
  content: '';
  position: absolute;
  left: 1px;
  right: 1px;
  bottom: 2px;
  height: 1.8px;
  border-radius: 999px;
  background: currentColor;
}

.data-detail-window__control-icon--maximize {
  box-sizing: border-box;
  border: 1.6px solid currentColor;
  border-radius: 2px;
}

.data-detail-window__control-icon--restore::before,
.data-detail-window__control-icon--restore::after {
  content: '';
  position: absolute;
  box-sizing: border-box;
  width: 8px;
  height: 8px;
  border: 1.6px solid currentColor;
  border-radius: 2px;
  background: #ffffff;
}

.data-detail-window__control-icon--restore::before {
  top: 0;
  right: 0;
}

.data-detail-window__control-icon--restore::after {
  left: 0;
  bottom: 0;
}

.data-detail-window__control-icon--close::before,
.data-detail-window__control-icon--close::after {
  content: '';
  position: absolute;
  top: 5px;
  left: 0;
  width: 12px;
  height: 1.8px;
  border-radius: 999px;
  background: currentColor;
}

.data-detail-window__control-icon--close::before {
  transform: rotate(45deg);
}

.data-detail-window__control-icon--close::after {
  transform: rotate(-45deg);
}

.data-detail-window__body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  background: #ffffff;
}

.data-detail-window__content {
  flex: 1 1 auto;
  min-height: 0;
  padding: 18px 20px 12px;
  overflow: hidden;
}

.data-detail-window__content-inner,
.detail-preview-pane {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.detail-preview-pane__body {
  flex: 1 1 auto;
  min-height: 0;
}

.detail-preview-pane__pagination {
  padding-top: 12px;
}

.data-detail-window__empty {
  height: 100%;
}

.data-detail-window__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 20px 20px;
  border-top: 1px solid rgba(226, 232, 240, 0.92);
  background: rgba(255, 255, 255, 0.98);
}

.experiment-upload__alert {
  margin-bottom: 14px;
}

.experiment-upload {
  width: 100%;
}

.experiment-upload :deep(.el-upload) {
  width: 100%;
}

.experiment-upload :deep(.el-upload-dragger) {
  width: 100%;
  border-radius: 14px;
  border-color: #cbd5e1;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.experiment-upload__tip {
  margin-top: 8px;
  color: #667085;
  line-height: 1.6;
}

.experiment-upload__folder-input {
  display: none;
}

.experiment-upload__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.experiment-upload__count {
  color: #667085;
  font-size: 13px;
}

.experiment-upload__actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.experiment-upload__progress {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  background: rgba(241, 245, 249, 0.9);
  border: 1px solid rgba(203, 213, 225, 0.9);
}

.experiment-upload__progress-text {
  margin-bottom: 10px;
  color: #475467;
  font-size: 13px;
}

.experiment-upload__list {
  margin-top: 14px;
  max-height: 220px;
  overflow: auto;
  padding-right: 4px;
}

.experiment-upload__list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(248, 250, 252, 0.96);
}

.experiment-upload__list-item + .experiment-upload__list-item {
  margin-top: 10px;
}

.experiment-upload__list-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.experiment-upload__list-icon {
  flex-shrink: 0;
  color: #2563eb;
}

.experiment-upload__list-name {
  min-width: 0;
  color: #1f2937;
  font-size: 13px;
  word-break: break-all;
}

.experiment-upload__list-side {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.experiment-upload__list-size {
  color: #667085;
  font-size: 12px;
}

.detail-preview-dialog :deep(.el-dialog) {
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 22px 50px rgba(15, 23, 42, 0.18);
}

.detail-preview-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 0;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98) 0%, rgba(241, 245, 249, 0.94) 100%);
}

.detail-preview-dialog :deep(.el-dialog__body) {
  padding: 20px 24px 12px;
}

.detail-preview-dialog :deep(.el-dialog__footer) {
  padding: 12px 24px 20px;
}

.detail-preview-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px 12px 20px;
  cursor: move;
  user-select: none;
}

.detail-preview-dialog__title {
  flex: 1;
  min-width: 0;
  color: #1f2937;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-preview-dialog__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: default;
}

.detail-preview-dialog__actions :deep(.el-button) {
  width: 32px;
  height: 32px;
  margin: 0;
  color: #475467;
}

.detail-preview-dialog__actions :deep(.el-button:hover) {
  background: rgba(15, 23, 42, 0.06);
  color: #111827;
}

.detail-preview-dialog.is-window-minimized :deep(.el-dialog) {
  width: min(480px, calc(100vw - 16px)) !important;
}

.detail-preview-dialog.is-window-minimized :deep(.el-dialog__header) {
  border-bottom: none;
}

.detail-preview-dialog.is-window-minimized :deep(.el-dialog__body),
.detail-preview-dialog.is-window-minimized :deep(.el-dialog__footer) {
  display: none;
}

.detail-preview-dialog.is-window-fullscreen :deep(.el-dialog) {
  border-radius: 0;
}

.detail-preview-dialog :deep(.pagination-container) {
  overflow-x: auto;
}

.detail-preview-dialog :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: flex-end;
  row-gap: 10px;
}

.ant-form-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(208, 213, 221, 0.8);
  background: linear-gradient(180deg, #fcfdff 0%, #f8fafc 100%);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08), 0 4px 12px rgba(15, 23, 42, 0.04);
}

.ant-form-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 24px 28px 14px;
  border-bottom: 1px solid #eaecf0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(248, 250, 252, 0.92) 100%);
}

.ant-form-dialog :deep(.el-dialog__title) {
  color: #1f2937;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.ant-form-dialog :deep(.el-dialog__body) {
  padding: 22px 28px 10px;
  background: transparent;
}

.ant-form-dialog :deep(.el-dialog__footer) {
  padding: 12px 28px 24px;
  border-top: none;
  background: transparent;
}

.ant-form-layout :deep(.el-form-item) {
  margin-bottom: 20px;
}

.ant-form-layout :deep(.el-form-item__label) {
  color: #667085;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.ant-form-layout :deep(.el-input__wrapper),
.ant-form-layout :deep(.el-textarea__inner),
.ant-form-layout :deep(.el-select__wrapper),
.ant-form-layout :deep(.el-date-editor.el-input__wrapper) {
  min-height: 42px;
  border-radius: 10px;
  color: #111827;
  background: #f9fafb;
  box-shadow: 0 0 0 1px #d0d5dd inset;
  transition: box-shadow 0.2s ease, background-color 0.2s ease, border-color 0.2s ease;
}

.ant-form-layout :deep(.el-input__inner),
.ant-form-layout :deep(.el-textarea__inner),
.ant-form-layout :deep(.el-select__selected-item),
.ant-form-layout :deep(.el-date-editor .el-input__inner) {
  color: #111827;
}

.ant-form-layout :deep(.el-input__inner::placeholder),
.ant-form-layout :deep(.el-textarea__inner::placeholder) {
  color: #98a2b3;
}

.ant-form-layout :deep(.el-select__placeholder) {
  color: #98a2b3;
}

.ant-form-layout :deep(.el-input__prefix),
.ant-form-layout :deep(.el-input__suffix),
.ant-form-layout :deep(.el-select__caret),
.ant-form-layout :deep(.el-date-editor .el-input__prefix) {
  color: #98a2b3;
}

.ant-form-layout :deep(.el-input__wrapper.is-focus),
.ant-form-layout :deep(.el-select__wrapper.is-focused),
.ant-form-layout :deep(.el-date-editor.el-input__wrapper.is-focus),
.ant-form-layout :deep(.el-textarea__inner:focus) {
  background: #ffffff;
  box-shadow: 0 0 0 1px #3b82f6 inset, 0 0 0 3px rgba(59, 130, 246, 0.14);
}

.ant-form-layout :deep(.el-textarea__inner) {
  min-height: 112px;
  padding: 12px 14px 24px;
  background: #f9fafb;
}

.ant-form-layout :deep(.el-input__count) {
  right: 10px;
  bottom: 6px;
  color: #98a2b3;
  background: transparent;
}

.ant-form-layout :deep(.el-input.is-disabled .el-input__wrapper),
.ant-form-layout :deep(.el-textarea.is-disabled .el-textarea__inner),
.ant-form-layout :deep(.el-date-editor.is-disabled) {
  background: #f2f4f7;
  box-shadow: 0 0 0 1px #d0d5dd inset;
}

.ant-form-layout :deep(.el-input.is-disabled .el-input__inner),
.ant-form-layout :deep(.el-textarea.is-disabled .el-textarea__inner),
.ant-form-layout :deep(.el-input.is-disabled .el-input__inner::placeholder) {
  color: #98a2b3;
  -webkit-text-fill-color: #98a2b3;
}

.ant-form-layout :deep(.el-form-item.is-error .el-input__wrapper),
.ant-form-layout :deep(.el-form-item.is-error .el-select__wrapper),
.ant-form-layout :deep(.el-form-item.is-error .el-date-editor.el-input__wrapper),
.ant-form-layout :deep(.el-form-item.is-error .el-textarea__inner) {
  background: #fffefe;
  box-shadow: 0 0 0 1px #f04438 inset, 0 0 0 3px rgba(240, 68, 56, 0.1);
}

.ant-confirm-btn {
  min-width: 116px;
  height: 42px;
  border: none;
  border-radius: 10px;
  font-weight: 600;
  color: #fff;
  background: #409eff;
  box-shadow: 0 8px 18px rgba(64, 158, 255, 0.2);
  transition: background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.ant-confirm-btn:hover,
.ant-confirm-btn:focus {
  color: #fff;
  background: #53a8ff;
  box-shadow: 0 10px 22px rgba(64, 158, 255, 0.24);
  transform: translateY(-1px);
}

.ant-cancel-btn {
  min-width: 100px;
  height: 42px;
  border: 1px solid #d0d5dd;
  border-radius: 10px;
  color: #475467;
  background: #ffffff;
  transition: background-color 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.ant-cancel-btn:hover,
.ant-cancel-btn:focus {
  color: #344054;
  border-color: #c1c7d0;
  background: #f9fafb;
}

.form-error-tip {
  margin-top: 4px;
  color: #f04438;
  font-size: 12px;
  line-height: 1.2;
}

.form-error-slide-enter-active,
.form-error-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.form-error-slide-enter-from,
.form-error-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.workspace-sidebar-shell,
.action-surface,
.table-surface,
.detail-card,
.action-surface__pill,
.table-meta-chip {
  border-radius: 10px;
}

.toolbar-action-btn,
.ant-confirm-btn,
.ant-cancel-btn {
  border-radius: 8px;
}

@media (max-width: 768px) {
  .detail-drawer__header {
    padding: 16px 16px 12px;
  }

  .detail-drawer__body {
    padding: 14px;
    gap: 14px;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }

  .drawer-form-shell__header,
  .drawer-form-shell__body,
  .drawer-form-shell__footer {
    padding-left: 16px;
    padding-right: 16px;
  }

  .data-detail-window {
    min-width: 0;
  }

  .data-detail-window__header {
    padding-left: 14px;
  }

  .data-detail-window__title-group {
    gap: 8px;
  }

  .data-detail-window__subtitle {
    display: none;
  }

  .data-detail-window__control {
    width: 42px;
    min-width: 42px;
  }

  .data-detail-window__content,
  .data-detail-window__footer {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
