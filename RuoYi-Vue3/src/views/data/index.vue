﻿<template>
    <div class="app-container data-workspace-page">
        <div class="data-workspace-layout">
            <aside class="workspace-sidebar">
                <div class="workspace-sidebar-shell">
                    <div class="workspace-sidebar__search">
                        <el-input v-model="name" placeholder="搜索项目或试验名称" clearable prefix-icon="Search" class="workspace-tree-search"></el-input>
                    </div>
                    <div class="workspace-sidebar__tree">
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
                    <DataQueryPanel
                        :show-search="showSearch"
                        :query-params="queryParams"
                        :date-range="dateRange"
                        :project-options="projectOptions"
                        :ids-length="ids.length"
                        :multiple="multiple"
                        @update:date-range="dateRange = $event"
                        @add-project="handleAddProject"
                        @add-experiment="handleAddExperiment"
                        @open-import="openFileManager"
                        @export-data="handleExportData"
                        @rename-data="handleRenameData"
                        @compare-data="handleCompareData"
                        @restore-data="handleRestoreData"
                        @delete-data="handleDelete()"
                        @search="handleQuery"
                        @reset="resetQuery"
                    />

                    <DataTablePanel
                        :loading="loading"
                        :business-list="businessList"
                        :total="total"
                        :page-num="queryParams.pageNum"
                        :page-size="queryParams.pageSize"
                        :format-time="formatListTime"
                        @selection-change="handleSelectionChange"
                        @update:page-num="queryParams.pageNum = $event"
                        @update:page-size="queryParams.pageSize = $event"
                        @pagination="getList"
                        @backup="handleBackupData"
                        @view="handleView"
                        @update="handleUpdate"
                        @delete="handleDelete"
                    />
                </div>
            </div>

        <ComparePreviewDialog
            v-model="compareDialogVisible"
            :items="comparePreviewItems"
            :max-height="comparePreviewDialogBodyMaxHeight"
            :item-height="comparePreviewItemHeight"
            @pagination="handleComparePreviewPagination"
            @closed="handleCompareDialogClosed"
        />

        <BackupDataDialog
            v-model="backupDialogVisible"
            :loading="backupLoading"
            :backup-list="backupList"
            :total="backupTotal"
            :query-params="backupQueryParams"
            :date-range="backupDateRange"
            :restoring-id="restoringBackupId"
            :format-time="formatListTime"
            @update:date-range="backupDateRange = $event"
            @search="handleBackupQuery"
            @reset="resetBackupQuery"
            @restore="handleRestoreBackupRecord"
            @pagination="getBackupList"
        />

        <ProjectInfoDialog
            v-model="projectInfoDialogOpen"
            :loading="projectInfoSubmitLoading"
            :form-model="projectInfoForm"
            :rules="projectInfoRules"
            @submit="submitProjectInfoForm"
            @closed="resetProjectInfoForm"
        />

        <ExperimentInfoDialog
            v-model="experimentInfoDialogOpen"
            :loading="experimentInfoSubmitLoading"
            :form-model="experimentInfoForm"
            :rules="experimentInfoRules"
            :project-options="projectOptions"
            :target-type-options="targetTypeOptions"
            :draft-files="experimentDraftFiles"
            :progress="experimentUploadProgress"
            :accept="EXPERIMENT_UPLOAD_ACCEPT"
            :format-size="formatExperimentFileSize"
            @submit="submitExperimentInfoForm"
            @closed="resetExperimentInfoForm"
            @draft-change="handleExperimentDraftChange"
            @folder-change="handleExperimentFolderChange"
            @clear-files="clearExperimentDraftFiles"
            @remove-file="removeExperimentDraftFile"
        />

        <TreeDetailDrawer
            v-model="treeDetailOpen"
            :form-data="treeDetailForm"
            :get-status-type="getTreeDetailStatusType"
            :get-status-text="getTreeDetailStatusText"
            :get-info-type-label="getTreeInfoTypeLabel"
            :format-start-time="formatTreeStartTime"
            @closed="resetTreeDetailForm"
        />

        <TreeEditDrawer
            v-model="treeEditOpen"
            :title="treeEditTitle"
            :loading="treeSubmitLoading"
            :form-model="treeEditForm"
            :rules="infoRules"
            :project-options="projectOptions"
            :target-type-options="targetTypeOptions"
            :get-info-type-label="getTreeInfoTypeLabel"
            :format-create-time="formatTreeCreateTime"
            @submit="submitTreeEdit"
            @closed="resetTreeEditForm"
        />

        <DataEditDialog
            v-model="open"
            :title="title"
            :form-model="form"
            :rules="rules"
            :move-path-tree-options="movePathTreeOptions"
            :selected-move-path-node-id="selectedMovePathNodeId"
            :move-target-experiment-name="moveTargetExperimentName"
            :move-target-project-name="moveTargetProjectName"
            :format-time="formatListTime"
            @update:selected-move-path-node-id="selectedMovePathNodeId = $event"
            @move-path-change="handleMovePathChange"
            @submit="submitForm"
            @closed="cancel"
        />

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

        <DataImportDialog
            v-model="importVisible"
            :loading="fileLoading"
            :form-model="uploadDataForm"
            :rules="uploadDataRules"
            :selectable-tree-options="selectableTreeOptions"
            :target-type-options="targetTypeOptions"
            :draft-files="businessDraftFiles"
            :progress="businessUploadProgress"
            :accept="EXPERIMENT_UPLOAD_ACCEPT"
            :single-upload-name-enabled="businessSingleUploadNameEnabled"
            :format-size="formatExperimentFileSize"
            @submit="submitUpload"
            @closed="resetUploadData"
            @target-change="handleTargetChange"
            @draft-change="handleBusinessDraftChange"
            @folder-change="handleBusinessFolderChange"
            @clear-files="clearBusinessDraftFiles"
            @remove-file="removeBusinessDraftFile"
        />

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
                <div v-else-if="isDetailImageFile" class="detail-media-preview">
                    <div v-loading="detailPreviewLoading" class="detail-media-preview__container" :style="{ height: detailPreviewContentHeight }">
                        <img v-if="detailMediaUrl" :src="detailMediaUrl" :alt="detailFile?.name || '图片预览'" class="detail-media-preview__image" />
                        <el-empty v-else :description="detailPreviewMessage || '暂无图片预览内容'" />
                    </div>
                </div>
                <div v-else-if="isDetailAudioFile" class="detail-media-preview">
                    <div
                        v-loading="detailPreviewLoading"
                        class="detail-media-preview__container detail-media-preview__container--audio"
                        :style="{ height: detailPreviewContentHeight }"
                    >
                        <audio v-if="detailMediaUrl" :src="detailMediaUrl" controls class="detail-media-preview__audio" />
                        <el-empty v-else :description="detailPreviewMessage || '暂无音频预览内容'" />
                    </div>
                </div>
                <div v-else-if="isDetailVideoFile" class="detail-media-preview">
                    <div v-loading="detailPreviewLoading" class="detail-media-preview__container" :style="{ height: detailPreviewContentHeight }">
                        <video v-if="detailMediaUrl" :src="detailMediaUrl" controls class="detail-media-preview__video" />
                        <el-empty v-else :description="detailPreviewMessage || '暂无视频预览内容'" />
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
                                <div v-else-if="isDetailImageFile" class="detail-media-preview">
                                    <div v-loading="detailPreviewLoading" class="detail-media-preview__container" :style="{ height: detailPreviewContentHeight }">
                                        <img v-if="detailMediaUrl" :src="detailMediaUrl" :alt="detailFile?.name || '图片预览'" class="detail-media-preview__image" />
                                        <el-empty v-else :description="detailPreviewMessage || '暂无图片预览内容'" />
                                    </div>
                                </div>
                                <div v-else-if="isDetailAudioFile" class="detail-media-preview">
                                    <div
                                        v-loading="detailPreviewLoading"
                                        class="detail-media-preview__container detail-media-preview__container--audio"
                                        :style="{ height: detailPreviewContentHeight }"
                                    >
                                        <audio v-if="detailMediaUrl" :src="detailMediaUrl" controls class="detail-media-preview__audio" />
                                        <el-empty v-else :description="detailPreviewMessage || '暂无音频预览内容'" />
                                    </div>
                                </div>
                                <div v-else-if="isDetailVideoFile" class="detail-media-preview">
                                    <div v-loading="detailPreviewLoading" class="detail-media-preview__container" :style="{ height: detailPreviewContentHeight }">
                                        <video v-if="detailMediaUrl" :src="detailMediaUrl" controls class="detail-media-preview__video" />
                                        <el-empty v-else :description="detailPreviewMessage || '暂无视频预览内容'" />
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
import {getdataList,getdataDetail,getMovePathTree,updatedata,deldata,adddata,previewData,downloadData,RenameDataName,backupData,getBackupDataList,restoreBackupData} from '@/api/data/bussiness'
import { getExperimentTree, addProjectInfo, addExperimentInfo, getInfo, updateInfo, delInfo } from "@/api/data/info"
import { addDateRange, blobValidate } from "@/utils/ruoyi"
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import {
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
import { useRoute, useRouter } from 'vue-router'
import DataQueryPanel from './components/DataQueryPanel.vue'
import DataTablePanel from './components/DataTablePanel.vue'
import ComparePreviewDialog from './components/ComparePreviewDialog.vue'
import ProjectInfoDialog from './components/ProjectInfoDialog.vue'
import ExperimentInfoDialog from './components/ExperimentInfoDialog.vue'
import TreeDetailDrawer from './components/TreeDetailDrawer.vue'
import TreeEditDrawer from './components/TreeEditDrawer.vue'
import DataEditDialog from './components/DataEditDialog.vue'
import DataImportDialog from './components/DataImportDialog.vue'
import BackupDataDialog from './components/BackupDataDialog.vue'
const route = useRoute()
const router = useRouter()
const AUTO_QUERY_ROUTE_KEYS = ['autoQuery', 'source', 'projectId', 'projectName', 'experimentId', 'experimentName', 'dataFilePath']
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

const name = ref('')
const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const businessList = ref([])
const ids = ref([])
const selectedBusinessRows = ref([])
const single = ref(true)
const multiple = ref(true)
const compareDialogVisible = ref(false)
const comparePreviewItems = ref([])
const COMPARE_PREVIEW_PAGE_SIZE = 20
const backupDialogVisible = ref(false)
const backupLoading = ref(false)
const backupTotal = ref(0)
const backupList = ref([])
const backupDateRange = ref([])
const restoringBackupId = ref(null)
const dataPageInitialized = ref(false)

// 文件管理器相关状态
const fileLoading = ref(false)
const importVisible = ref(false)
const businessDraftFiles = ref([])
const businessUploadProgress = reactive({
  visible: false,
  percentage: 0,
  status: '',
  text: ''
})
const experimentDraftFiles = ref([])
const experimentUploadProgress = reactive({
  visible: false,
  percentage: 0,
  status: '',
  text: ''
})
const fileManagerPreviewFile = ref(null)
const EXPERIMENT_UPLOAD_ACCEPT = '.zip,.csv,.xls,.xlsx,.txt,.json,.doc,.docx,.pdf,.bin,.dat,.raw,.png,.jpg,.jpeg,.mp3,.mp4'
const experimentAllowedExtensions = new Set(['zip', 'csv', 'xls', 'xlsx', 'txt', 'json', 'doc', 'docx', 'pdf', 'bin', 'dat', 'raw','png','jpg','jpeg','mp3','mp4'])
let experimentDraftUid = 0
let businessDraftUid = 0

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
const detailMediaUrl = ref('')
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
  resetUploadData()
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
const createBackupQueryParams = () => ({
  pageNum: 1,
  pageSize: 10,
  dataName: undefined,
  experimentName: undefined,
  projectName: undefined,
  deleteBy: undefined,
  isRestored: undefined
})
const backupQueryParams = reactive(createBackupQueryParams())
const uploadDataRules = {
  experimentId: [{ required: true, message: "请选择试验", trigger: "change" }],
  targetId: [{ required: true, message: "请选择试验目标", trigger: "change" }],
  dataType: [{ required: true, message: "请选择数据种类", trigger: "change" }],
  isSimulation: [{ required: true, message: "请选择数据类型", trigger: "change" }]
}

const createDataQueryParams = () => ({
    id: null,
    pageNum: 1,
    pageSize: 10,
    dataName: undefined,
    dataFilePath: undefined,
    experimentId: undefined,
    experimentName: undefined,
    projectId: undefined,
    projectName: undefined,
    createBy: undefined,
    isSimulation: undefined,
    dataType: undefined,
    startTime: undefined,
    endTime: undefined,
    workStatus: undefined,
    dataCategory: undefined,
    createTime: undefined,
})

const data = reactive({
    form: {},
    queryParams: createDataQueryParams(),
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

function normalizeRouteQueryText(value) {
    const rawValue = Array.isArray(value) ? value[value.length - 1] : value
    const normalizedValue = String(rawValue ?? '').trim()
    if (!normalizedValue || normalizedValue === 'undefined' || normalizedValue === 'null') {
        return undefined
    }
    return normalizedValue
}

function resolveProjectIdByName(projectName) {
    const normalizedProjectName = String(projectName || '').trim()
    if (!normalizedProjectName) {
        return undefined
    }

    const project = projectOptions.value.find(item => String(item?.projectName || '').trim() === normalizedProjectName)
    return project?.projectId != null ? String(project.projectId) : undefined
}

function extractAutoQueryFilters(routeQuery = route.query) {
    if (normalizeRouteQueryText(routeQuery?.autoQuery) !== '1') {
        return null
    }

    const projectName = normalizeRouteQueryText(routeQuery?.projectName)
    const experimentId = normalizeRouteQueryText(routeQuery?.experimentId)
    const experimentName = normalizeRouteQueryText(routeQuery?.experimentName)
    const dataFilePath = normalizeRouteQueryText(routeQuery?.dataFilePath)

    if (!projectName || !experimentId || !experimentName || !dataFilePath) {
        return null
    }

    return {
        projectId: normalizeRouteQueryText(routeQuery?.projectId),
        projectName,
        experimentId,
        experimentName,
        dataFilePath
    }
}

function applyAutoQueryFilters(routeQuery = route.query) {
    const filters = extractAutoQueryFilters(routeQuery)
    if (!filters) {
        return false
    }

    const resolvedProjectId = filters.projectId != null
      ? String(filters.projectId)
      : resolveProjectIdByName(filters.projectName)

    queryParams.value = {
        ...createDataQueryParams(),
        pageSize: queryParams.value?.pageSize || 10,
        dataName: String(filters.dataFilePath).replace(/^\/+/, '') || undefined,
        dataFilePath: filters.dataFilePath,
        experimentId: filters.experimentId,
        experimentName: filters.experimentName,
        projectId: resolvedProjectId,
        projectName: resolvedProjectId ? undefined : filters.projectName
    }
    dateRange.value = []
    getList()
    return true
}

function clearAutoQueryRoute() {
    if (normalizeRouteQueryText(route.query?.autoQuery) !== '1') {
        return
    }

    const nextQuery = { ...route.query }
    AUTO_QUERY_ROUTE_KEYS.forEach(key => {
        delete nextQuery[key]
    })
    router.replace({
        path: route.path,
        query: nextQuery
    })
}

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
function handleRenameData() {
  if (ids.value.length === 0) {
    ElMessage.warning('请选择要规范重命名的数据')
    return Promise.resolve()
  }

  const selectedRows = businessList.value.filter(item => ids.value.includes(item.id))
  const renameRows = selectedRows
    .filter(item => item?.id && item?.experimentId && item?.dataName && item?.projectName && item?.experimentName)
    .map(item => ({
      id: item.id,
      experimentId: item.experimentId,
      dataName: item.dataName,
      projectName: item.projectName,
      experimentName: item.experimentName
    }))

  if (renameRows.length === 0) {
    ElMessage.warning('选中的数据缺少规范重命名所需信息')
    return Promise.resolve()
  }

  return RenameDataName(renameRows).then(res => {
    if (res.code === 200) {
      ElMessage.success('重命名成功')
      getTreeData()
      getList()
    } else {
      ElMessage.error(res.msg || '重命名失败')
    }
  }).catch(err => {
    //showInfoRequestError(err, '重命名失败')
  })
}

async function handleCompareData() {
  if (selectedBusinessRows.value.length < 2) {
    ElMessage.warning('请至少选择两条数据进行比对')
    return
  }

  handleCompareDialogClosed()
  comparePreviewItems.value = selectedBusinessRows.value.map(row => createComparePreviewItem(row))
  compareDialogVisible.value = true

  await Promise.allSettled(comparePreviewItems.value.map(item => loadComparePreviewItem(item)))
}

function handleRestoreData() {
  backupDialogVisible.value = true
  getBackupList()
}

function handleBackupQuery() {
  backupQueryParams.pageNum = 1
  getBackupList()
}

function resetBackupQuery() {
  Object.assign(backupQueryParams, createBackupQueryParams())
  backupDateRange.value = []
  getBackupList()
}

function getBackupList() {
  backupLoading.value = true
  const query = addDateRange({ ...backupQueryParams }, backupDateRange.value)
  return getBackupDataList(query, { silent: true }).then(response => {
    backupList.value = response.rows || (response.data && response.data.rows) || []
    backupTotal.value = response.total || (response.data && response.data.total) || 0
  }).catch(error => {
    ElMessage.error(error?.message || '查询备份数据失败')
  }).finally(() => {
    backupLoading.value = false
  })
}

async function handleBackupData(row) {
  const dataId = row?.id
  if (!dataId) {
    ElMessage.warning('未获取到要备份的数据')
    return
  }

  try {
    await ElMessageBox.confirm(`确认备份数据“${row.dataName || dataId}”吗？`, '备份确认', {
      type: 'warning',
      confirmButtonText: '确认备份',
      cancelButtonText: '取消'
    })
  } catch (error) {
    return
  }

  try {
    const response = await backupData(dataId, { silent: true })
    if (response?.code === 200) {
      proxy.$modal.msgSuccess(response?.msg || '备份成功')
      if (backupDialogVisible.value) {
        getBackupList()
      }
      return
    }

    ElMessage.error(response?.msg || '备份失败')
  } catch (error) {
    const errorMessage = error?.message || error?.response?.data?.msg || '备份失败'
    ElMessage.error(errorMessage)
  }
}

async function handleRestoreBackupRecord(row) {
  const backupId = row?.id
  if (!backupId) {
    ElMessage.warning('未获取到要还原的备份数据')
    return
  }

  try {
    await ElMessageBox.confirm(`确认还原备份数据“${row.dataName || backupId}”吗？`, '还原确认', {
      type: 'warning',
      confirmButtonText: '确认还原',
      cancelButtonText: '取消'
    })
  } catch (error) {
    return
  }

  restoringBackupId.value = backupId
  try {
    const response = await restoreBackupData(backupId, { silent: true })
    if (response?.code === 200) {
      proxy.$modal.msgSuccess(response?.msg || '还原成功')
      await Promise.all([
        getBackupList(),
        getList()
      ])
      return
    }

    ElMessage.error(response?.msg || '还原失败')
  } catch (error) {
    const errorMessage = error?.message || error?.response?.data?.msg || '还原失败'
    ElMessage.error(errorMessage)
  } finally {
    restoringBackupId.value = null
  }
}

function resetProjectInfoForm() {
  Object.assign(projectInfoForm, createProjectInfoForm())
}

function resetExperimentInfoForm() {
  Object.assign(experimentInfoForm, createExperimentInfoForm())
  clearExperimentDraftFiles()
}

function resetTreeDetailForm() {
  Object.assign(treeDetailForm, createInfoForm())
}

function resetTreeEditForm() {
  Object.assign(treeEditForm, createInfoForm())
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

function clearExperimentDraftFiles() {
  experimentDraftFiles.value = []
  resetExperimentUploadProgress()
}

function removeExperimentDraftFile(uid) {
  experimentDraftFiles.value = experimentDraftFiles.value.filter(item => item.uid !== uid)
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

function handleExperimentFolderChange(event) {
  const rawFiles = Array.from(event?.target?.files || [])
  addExperimentDraftFiles(rawFiles)
}

function formatExperimentFileSize(size) {
  const value = Number(size) || 0
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`
  return `${(value / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

function clearBusinessDraftFiles() {
  businessDraftFiles.value = []
  resetBusinessUploadProgress()
}

function removeBusinessDraftFile(uid) {
  businessDraftFiles.value = businessDraftFiles.value.filter(item => item.uid !== uid)
}

function resetBusinessUploadProgress() {
  businessUploadProgress.visible = false
  businessUploadProgress.percentage = 0
  businessUploadProgress.status = ''
  businessUploadProgress.text = ''
}

function updateBusinessUploadProgress(event, fileCount) {
  const total = Number(event?.total) || 0
  if (!total) return
  const loaded = Number(event?.loaded) || 0
  const percentage = Math.min(99, Math.max(1, Math.round((loaded / total) * 100)))
  businessUploadProgress.visible = true
  businessUploadProgress.percentage = percentage
  businessUploadProgress.status = ''
  businessUploadProgress.text = `正在上传 ${fileCount} 个文件... ${percentage}%`
}

function createBusinessDraftFile(rawFile, relativePath) {
  businessDraftUid += 1
  return {
    uid: `business-draft-${businessDraftUid}`,
    name: relativePath,
    size: Number(rawFile?.size) || 0,
    status: 'ready',
    raw: rawFile,
    relativePath
  }
}

function addBusinessDraftFiles(rawFiles = []) {
  if (!Array.isArray(rawFiles) || rawFiles.length === 0) return

  const existingKeys = new Set(
    businessDraftFiles.value.map(item => buildExperimentDraftKey(item.raw, item.relativePath || item.name))
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
    nextFiles.push(createBusinessDraftFile(rawFile, relativePath))
  })

  if (nextFiles.length) {
    businessDraftFiles.value = businessDraftFiles.value.concat(nextFiles)
  }
  if (invalidFiles.length) {
    ElMessage.warning(`已跳过不支持的文件：${summarizeExperimentFileNames(invalidFiles)}`)
  }
  if (duplicateFiles.length) {
    ElMessage.warning(`已忽略重复文件：${summarizeExperimentFileNames(duplicateFiles)}`)
  }
}

function handleBusinessDraftChange(uploadFile) {
  if (!uploadFile?.raw) return
  addBusinessDraftFiles([uploadFile.raw])
}

function handleBusinessFolderChange(event) {
  const rawFiles = Array.from(event?.target?.files || [])
  addBusinessDraftFiles(rawFiles)
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

function buildBusinessUploadFormData(data) {
  const formData = new FormData()

  Object.entries(data || {}).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    formData.append(key, value)
  })

  businessDraftFiles.value.forEach(file => {
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

function formatListTime(time) {
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
    parentId: resData.projectId != null ? String(resData.projectId) : (row.parentId != null ? String(row.parentId) : null),
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
  if (treeSubmitLoading.value) return

  treeSubmitLoading.value = true
  ;(async () => {
    try {
      const submitData = JSON.parse(JSON.stringify(treeEditForm))
      submitData.startTime = formatDateForSubmit(submitData.startTime)
      if (submitData.type === 'project') {
        submitData.parentId = 0
        submitData.targetId = null
        submitData.targetType = null
      } else {
        submitData.parentId = submitData.parentId != null && submitData.parentId !== '' ? Number(submitData.parentId) : null
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
  })()
}

function submitProjectInfoForm() {
  if (projectInfoSubmitLoading.value) return

  projectInfoSubmitLoading.value = true
  ;(async () => {
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
  })()
}

function submitExperimentInfoForm() {
  if (experimentInfoSubmitLoading.value) return

  experimentInfoSubmitLoading.value = true
  const uploadCount = experimentDraftFiles.value.length
  resetExperimentUploadProgress()
  if (uploadCount > 0) {
    experimentUploadProgress.visible = true
    experimentUploadProgress.text = `准备上传 ${uploadCount} 个文件...`
  }

  ;(async () => {
    try {
      const submitData = JSON.parse(JSON.stringify(experimentInfoForm))
      submitData.type = 'experiment'
      submitData.startTime = formatDateForSubmit(submitData.startTime)
      submitData.parentId = submitData.parentId != null && submitData.parentId !== '' ? Number(submitData.parentId) : null
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
  })()
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

const comparePreviewDialogBodyMaxHeight = computed(() => {
  const viewportHeight = detailViewport.height || window.innerHeight || 900
  return `${Math.max(viewportHeight - 210, 360)}px`
})

const comparePreviewItemHeight = computed(() => {
  const count = Math.max(comparePreviewItems.value.length, 1)
  const viewportHeight = detailViewport.height || window.innerHeight || 900
  const availableHeight = Math.max(viewportHeight - 280, 360)
  const nextHeight = Math.floor(availableHeight / count)
  return `${Math.max(Math.min(nextHeight, 420), 180)}px`
})


const detailTableColumns = computed(() => Object.keys(detailTableRows.value[0] || {}))
const isDetailTabularFile = computed(() => isTabularFile(detailFile.value))
const isDetailTextFile = computed(() => isTextFile(detailFile.value))
const isDetailPdfFile = computed(() => isPdfFile(detailFile.value))
const isDetailImageFile = computed(() => isImageFile(detailFile.value))
const isDetailAudioFile = computed(() => isAudioFile(detailFile.value))
const isDetailVideoFile = computed(() => isVideoFile(detailFile.value))
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

function isImageFile(file) {
  const extension = getPreviewFileExtension(file)
  return extension === 'jpg' || extension === 'jpeg' || extension === 'png'
}

function isAudioFile(file) {
  return getPreviewFileExtension(file) === 'mp3'
}

function isVideoFile(file) {
  return getPreviewFileExtension(file) === 'mp4'
}

function isBinaryFile(file) {
  const extension = getPreviewFileExtension(file)
  return extension === 'bin' || extension === 'dat' || extension === 'raw'
}

function getPreviewMimeType(file) {
  const extension = getPreviewFileExtension(file)
  if (extension === 'jpg' || extension === 'jpeg') return 'image/jpeg'
  if (extension === 'png') return 'image/png'
  if (extension === 'mp3') return 'audio/mpeg'
  if (extension === 'mp4') return 'video/mp4'
  if (extension === 'pdf') return 'application/pdf'
  return 'application/octet-stream'
}

function getPreviewFileLabel(file) {
  return file?.dataName || file?.name || file?.dataFilePath?.split(/[\\/]/).pop() || '未命名文件'
}

function getDetailPreviewTitle(file) {
  const fileName = getPreviewFileName(file)
  const fileLabel = getPreviewFileLabel(file)

  if (fileName.endsWith('.csv')) return `CSV文件预览: ${fileLabel}`
  if (fileName.endsWith('.xlsx') || fileName.endsWith('.xls')) return `Excel文件预览: ${fileLabel}`
  if (fileName.endsWith('.txt')) return `Txt文件预览: ${fileLabel}`
  if (fileName.endsWith('.json')) return `JSON文件预览: ${fileLabel}`
  if (fileName.endsWith('.doc') || fileName.endsWith('.docx')) return `Word文件预览: ${fileLabel}`
  if (fileName.endsWith('.pdf')) return `PDF文件预览: ${fileLabel}`
  if (isImageFile(file)) return `图片文件预览: ${fileLabel}`
  if (isAudioFile(file)) return `音频文件预览: ${fileLabel}`
  if (isVideoFile(file)) return `视频文件预览: ${fileLabel}`
  return `文件预览: ${fileLabel}`
}

function resolveComparePreviewType(file) {
  if (isTabularFile(file)) return 'table'
  if (isTextFile(file)) return 'text'
  if (isPdfFile(file)) return 'pdf'
  if (isImageFile(file)) return 'image'
  if (isAudioFile(file)) return 'audio'
  if (isVideoFile(file)) return 'video'
  return 'unsupported'
}

function getComparePreviewTypeLabel(previewType) {
  if (previewType === 'table') return '表格'
  if (previewType === 'text') return '文本'
  if (previewType === 'pdf') return 'PDF'
  if (previewType === 'image') return '图片'
  if (previewType === 'audio') return '音频'
  if (previewType === 'video') return '视频'
  return '未支持'
}

function getCompareTableColumns(item) {
  return Object.keys(item?.rows?.[0] || {})
}

function getCompareTextLines(item) {
  return (item?.rows || []).map(row => {
    if (row == null) return ''
    if (typeof row === 'string') return row
    if (typeof row === 'object') return Object.values(row).join(' ')
    return String(row)
  })
}

function getComparePreviewMeta(item) {
  const previewLabel = getComparePreviewTypeLabel(item.previewType)
  let fileTypeLabel = '暂无内容'

  if (item.previewType === 'table' || item.previewType === 'text') {
    fileTypeLabel = item.total > 0 ? `展示 ${item.rows.length}/${item.total}` : '暂无内容'
  } else if (item.objectUrl) {
    fileTypeLabel = '已加载预览'
  } else if (item.loading) {
    fileTypeLabel = '加载中'
  }

  return `${previewLabel} · ${fileTypeLabel}`
}

function createComparePreviewItem(row) {
  return {
    id: row.id,
    title: row.dataName || row.name || '未命名数据',
    row,
    loading: true,
    previewType: resolveComparePreviewType(row),
    rows: [],
    total: 0,
    pageNum: 1,
    pageSize: COMPARE_PREVIEW_PAGE_SIZE,
    message: '',
    objectUrl: ''
  }
}

function revokeComparePreviewItemUrl(item) {
  if (!item?.objectUrl) return
  URL.revokeObjectURL(item.objectUrl)
  item.objectUrl = ''
}

function revokeComparePreviewUrls() {
  comparePreviewItems.value.forEach(item => revokeComparePreviewItemUrl(item))
}

function revokeDetailPdfUrl() {
  if (!detailPdfUrl.value) return
  URL.revokeObjectURL(detailPdfUrl.value)
  detailPdfUrl.value = ''
}

function revokeDetailMediaUrl() {
  if (!detailMediaUrl.value) return
  URL.revokeObjectURL(detailMediaUrl.value)
  detailMediaUrl.value = ''
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
  revokeDetailMediaUrl()
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

async function loadDetailMediaPreview() {
  if (!detailFile.value || !detailFile.value.dataFilePath || !detailFile.value.experimentId) return
  detailPreviewLoading.value = true
  detailPreviewMessage.value = ''
  revokeDetailMediaUrl()

  try {
    const data = await downloadData({
      id: detailFile.value.id,
      experimentId: detailFile.value.experimentId,
      dataFilePath: detailFile.value.dataFilePath
    })

    if (blobValidate(data)) {
      const mimeType = getPreviewMimeType(detailFile.value)
      const mediaBlob = data.type === mimeType ? data : new Blob([data], { type: mimeType })
      detailMediaUrl.value = URL.createObjectURL(mediaBlob)
      return
    }

    const responseText = await data.text()
    const responseObj = JSON.parse(responseText)
    detailPreviewMessage.value = responseObj.msg || '媒体预览加载失败，请下载后查看'
  } catch (error) {
    detailPreviewMessage.value = error?.message || '媒体预览加载失败，请下载后查看'
  } finally {
    detailPreviewLoading.value = false
  }
}

async function loadCompareMediaPreview(item) {
  const currentRow = item.row
  const previewType = resolveComparePreviewType(currentRow)

  try {
    const data = await downloadData({
      id: currentRow.id,
      experimentId: currentRow.experimentId,
      dataFilePath: currentRow.dataFilePath
    })

    if (blobValidate(data)) {
      const mimeType = getPreviewMimeType(currentRow)
      const mediaBlob = data.type === mimeType ? data : new Blob([data], { type: mimeType })
      item.objectUrl = URL.createObjectURL(mediaBlob)
      item.previewType = previewType
      item.message = ''
      item.total = 1
      return
    }

    const responseText = await data.text()
    const responseObj = JSON.parse(responseText)
    item.previewType = previewType
    item.message = responseObj.msg || '预览加载失败，请下载后查看'
  } catch (error) {
    item.previewType = previewType
    item.message = error?.message || '预览加载失败，请下载后查看'
  }
}

async function loadComparePreviewItem(item) {
  const currentRow = item.row
  item.loading = true
  item.message = ''
  revokeComparePreviewItemUrl(item)

  if (!currentRow?.experimentId || !currentRow?.dataFilePath) {
    item.previewType = 'unsupported'
    item.rows = []
    item.total = 0
    item.message = '缺少比对预览所需的文件参数'
    item.loading = false
    return
  }

  try {
    if (isTabularFile(currentRow) || isTextFile(currentRow)) {
      const response = await previewData({
        experimentId: currentRow.experimentId,
        dataFilePath: currentRow.dataFilePath,
        pageNum: Number(item.pageNum) || 1,
        pageSize: Number(item.pageSize) || COMPARE_PREVIEW_PAGE_SIZE
      })

      if (response.code === 200) {
        const pageData = response.data || {}
        item.previewType = pageData.previewType || resolveComparePreviewType(currentRow)
        item.rows = Array.isArray(pageData.rows) ? pageData.rows : []
        item.total = Number(pageData.total) || 0
        item.pageNum = Number(pageData.pageNum) || Number(item.pageNum) || 1
        item.pageSize = Number(pageData.pageSize) || Number(item.pageSize) || COMPARE_PREVIEW_PAGE_SIZE
        item.message = pageData.message || ''
      } else {
        item.previewType = resolveComparePreviewType(currentRow)
        item.rows = []
        item.total = 0
        item.message = response.msg || '预览失败，请下载后查看'
      }
      return
    }

    if (isPdfFile(currentRow) || isImageFile(currentRow) || isAudioFile(currentRow) || isVideoFile(currentRow)) {
      await loadCompareMediaPreview(item)
      return
    }

    item.previewType = 'unsupported'
    item.rows = []
    item.total = 0
    item.message = isBinaryFile(currentRow)
      ? '暂不支持比对二进制文件，请下载后查看'
      : '暂不支持在线比对该文件'
  } catch (error) {
    item.previewType = resolveComparePreviewType(currentRow)
    item.rows = []
    item.total = 0
    item.message = error?.message || '预览失败，请下载后查看'
  } finally {
    item.loading = false
  }
}

function handleComparePreviewPagination(item) {
  if (!item) return
  return loadComparePreviewItem(item)
}

function handleCompareDialogClosed() {
  revokeComparePreviewUrls()
  comparePreviewItems.value = []
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

const businessSingleUploadNameEnabled = computed(() => {
  if (businessDraftFiles.value.length !== 1) {
    return false
  }

  const file = businessDraftFiles.value[0]
  const relativePath = normalizeExperimentUploadPath(file?.relativePath || file?.name || '')
  if (!relativePath || getExperimentFileExtension(relativePath) === 'zip') {
    return false
  }
  return extractDirPath(`/${relativePath}`) === '/'
})

const resetUploadData = () => {
  uploadDataForm.dataName = ''
  uploadDataForm.experimentId = null
  uploadDataForm.targetId = null
  uploadDataForm.targetType = null
  uploadDataForm.dataType = ''
  uploadDataForm.isSimulation = true
  clearBusinessDraftFiles()
  fileLoading.value = false
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
  if (fileLoading.value) return

  const selectedFiles = businessDraftFiles.value.filter(file => file?.raw)
  if (selectedFiles.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }

  fileLoading.value = true
  resetBusinessUploadProgress()
  businessUploadProgress.visible = true
  businessUploadProgress.text = `准备上传 ${selectedFiles.length} 个文件...`
  try {
    const businessData = {
      dataName: uploadDataForm.dataName,
      experimentId: uploadDataForm.experimentId,
      targetId: uploadDataForm.targetId,
      targetType: uploadDataForm.targetType,
      dataType: uploadDataForm.dataType,
      isSimulation: uploadDataForm.isSimulation
    }
    const formData = buildBusinessUploadFormData(businessData)
    await adddata(formData, {
      silent: true,
      onUploadProgress: event => updateBusinessUploadProgress(event, selectedFiles.length)
    })
    businessUploadProgress.visible = true
    businessUploadProgress.percentage = 100
    businessUploadProgress.status = 'success'
    businessUploadProgress.text = `上传完成，已处理 ${selectedFiles.length} 个文件`
    proxy.$modal.msgSuccess(selectedFiles.length > 1 ? `成功导入 ${selectedFiles.length} 个文件` : '数据导入成功')
    importVisible.value = false
    await getList()
  } catch (error) {
    businessUploadProgress.visible = true
    businessUploadProgress.status = 'exception'
    businessUploadProgress.text = error?.message || '上传失败，请重试'
    showInfoRequestError(error, '数据导入失败')
  } finally {
    fileLoading.value = false
  }
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

watch(
  () => route.query,
  (currentQuery) => {
    if (!dataPageInitialized.value) {
      return
    }
    applyAutoQueryFilters(currentQuery)
  },
  { deep: true }
)

function handleNodeClick(data) {
    if(data.type==="experiment"){
        queryParams.value.id=undefined
        queryParams.value.dataFilePath=undefined
        queryParams.value.experimentId=data.experimentId ?? data.id
        queryParams.value.experimentName=data.name ?? data.label
        queryParams.value.projectId=data.parentId != null ? String(data.parentId) : undefined
        queryParams.value.projectName=undefined
    }
    else if(data.type==="project"){
        queryParams.value.id=undefined
        queryParams.value.dataFilePath=undefined
        queryParams.value.experimentId=undefined
        const project = projectOptions.value.find(item => item.projectId == data.id)
        queryParams.value.projectId = project?.projectId != null ? String(project.projectId) : (data.id != null ? String(data.id) : undefined)
        queryParams.value.experimentName=undefined
        queryParams.value.projectName=undefined
    }

    handleQuery()
}

function resetQuery() {
    dateRange.value = []
    queryParams.value = createDataQueryParams()
    clearAutoQueryRoute()
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
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  selectedBusinessRows.value = selection
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

    resetDetailPreviewState()
    detailTitle.value = getDetailPreviewTitle(row)

    if (isTabularFile(row) || isTextFile(row)) {
      detailVisible.value = true
      loadDetailTablePreview()
      return
    }

    if (isPdfFile(row)) {
      detailVisible.value = true
      loadDetailPdfPreview()
      return
    }

    if (isImageFile(row) || isAudioFile(row) || isVideoFile(row)) {
      detailVisible.value = true
      loadDetailMediaPreview()
      return
    }

    if (isBinaryFile(row)) {
      detailPreviewMessage.value = '暂不支持预览二进制文件，请下载后查看'
      detailVisible.value = true
      return
    }

    detailPreviewMessage.value = '暂不支持在线预览该文件'
    detailVisible.value = true
  } else {
    ElMessage.warning("该数据没有关联的文件路径")
  }
}

function handleQuery(){
    queryParams.value.pageNum = 1
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
onMounted(async () => {
    updateDetailDialogViewport()
    window.addEventListener('resize', updateDetailDialogViewport)
    await Promise.allSettled([getTreeData(), getProjects()])
    dataPageInitialized.value = true
    if (!applyAutoQueryFilters(route.query)) {
      getList()
    }
})

onBeforeUnmount(() => {
    stopDetailDialogDrag()
    revokeDetailPdfUrl()
    revokeDetailMediaUrl()
    revokeComparePreviewUrls()
    window.removeEventListener('resize', updateDetailDialogViewport)
})

</script>
<style scoped>
.data-workspace-page {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: calc(100vh - 148px);
  overflow: visible;
  margin: -20px;
  padding: 2px 2px 16px;
  box-sizing: border-box;
}

.table-surface__eyebrow {
  margin: 0;
  color: #405efe;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.data-workspace-layout {
  display: grid;
  grid-template-columns: minmax(280px, 296px) minmax(0, 1fr);
  align-items: stretch;
  gap: 2px;
  flex: 1 1 auto;
  min-height: 0;
}

.workspace-sidebar {
  width: 100%;
  max-width: 296px;
  min-width: 0;
}

.workspace-sidebar-shell,
.data-pane {
  min-height: 0;
}

.workspace-sidebar-shell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  height: 100%;
  min-height: calc(100vh - 148px);
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
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
  padding: 0;
}

.workspace-tree-search :deep(.el-input__wrapper) {
  min-height: 44px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 0 0 1px rgba(203, 213, 225, 0.8) inset;
}

.workspace-tree-search :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 1px rgba(64, 94, 254, 0.42) inset, 0 0 0 4px rgba(64, 94, 254, 0.12);
}

.workspace-sidebar__tree {
  flex: 1;
  min-height: 0;
  padding: 6px 4px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(226, 232, 240, 0.68);
  overflow: auto;
}

.workspace-sidebar__tree :deep(.el-tree) {
  background: transparent;
}

.workspace-sidebar__tree :deep(.el-tree-node__content) {
  height: 42px;
  border-radius: 14px;
  padding-right: 8px;
  transition: background-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.workspace-sidebar__tree :deep(.el-tree-node__content:hover) {
  background: #f4f7fd;
  transform: translateX(2px);
}

.workspace-sidebar__tree :deep(.is-current > .el-tree-node__content) {
  background: rgba(64, 94, 254, 0.1);
  box-shadow: inset 0 0 0 1px rgba(64, 94, 254, 0.18);
}

.workspace-sidebar__tree :deep(.is-current > .el-tree-node__content) .tree-node-content__label {
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

.workspace-sidebar__tree :deep(.el-tree-node__content:hover) .tree-node-content__action,
.workspace-sidebar__tree :deep(.is-current > .el-tree-node__content) .tree-node-content__action {
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

.toolbar-query-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.toolbar-query-actions :deep(.el-button) {
  min-width: 128px;
  height: 42px;
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
    max-width: 288px;
  }

  .data-workspace-layout {
    grid-template-columns: minmax(268px, 288px) minmax(0, 1fr);
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
  .data-workspace-page {
    margin: -16px;
    padding: 2px 2px 12px;
  }

  .data-workspace-layout {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .workspace-sidebar {
    width: 100%;
    max-width: none;
  }

  .workspace-sidebar-shell {
    min-height: auto;
  }

  .action-surface__toolbar,
  .table-surface__header {
    flex-direction: column;
    align-items: stretch;
  }

  .action-surface__meta {
    justify-content: flex-start;
    margin-left: 0;
  }

  .global-actions-row {
    flex: 1 1 auto;
  }

  .toolbar-query-actions {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .action-surface,
  .table-surface {
    padding: 16px;
  }

  .workspace-sidebar__tree {
    border-radius: 16px;
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

.detail-media-preview__container {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  overflow: auto;
  background: #fafafa;
}

.detail-media-preview__container--audio {
  padding: 24px;
}

.detail-media-preview__image {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.detail-media-preview__audio {
  width: min(100%, 720px);
}

.detail-media-preview__video {
  display: block;
  max-width: 100%;
  max-height: 100%;
  border-radius: 6px;
  background: #000;
}

.compare-preview-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.compare-preview-dialog__title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.compare-preview-dialog__subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #6b7280;
}

.compare-preview-dialog__body {
  overflow-y: auto;
  padding-right: 4px;
}

.compare-preview-list {
  display: flex;
  flex-direction: column;
}

.compare-preview-item {
  padding: 0 0 18px;
}

.compare-preview-item + .compare-preview-item {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #e5e7eb;
}

.compare-preview-item__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.compare-preview-item__title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
  word-break: break-word;
}

.compare-preview-item__meta {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}

.compare-preview-item__body {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
}

.compare-preview-item__empty,
.compare-media-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 16px;
  background: #fafafa;
}

.compare-media-preview--audio {
  padding: 24px;
}

.compare-media-preview__frame {
  width: 100%;
  height: 100%;
  border: none;
}

.compare-media-preview__image {
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.compare-media-preview__audio {
  width: min(100%, 720px);
}

.compare-media-preview__video {
  display: block;
  max-width: 100%;
  max-height: 100%;
  border-radius: 8px;
  background: #000;
}

.compare-text-preview,
.compare-text-preview__body {
  height: 100%;
}

.compare-text-preview__body {
  overflow-y: auto;
  background: #fafafa;
}

.compare-text-preview__line {
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 12px;
  padding: 8px 14px;
  border-bottom: 1px solid #eef2f7;
}

.compare-text-preview__line:last-child {
  border-bottom: none;
}

.compare-text-preview__line-number {
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.7;
  text-align: right;
  user-select: none;
}

.compare-text-preview__line-content {
  margin: 0;
  color: #111827;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: Consolas, 'Courier New', monospace;
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

.action-surface,
.table-surface,
.detail-card,
.action-surface__pill {
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
