<template>
    <div class="app-container">
        <splitpanes :horizontal="appStore.device === 'mobile'" class="default-theme">
            <!-- 左侧菜单栏 试验信息树形表 -->
            <pane size="16">
                <el-col>
                    <div class="head-container">
                        <el-input v-model="name" placeholder="请输入试验名称" clearable prefix-icon="Search" style="margin-bottom: 20px"></el-input>
                    </div>
                    <div class="body-container">
                        <el-tree :data="treeTableOptions" :props="{ label: 'label', children: 'children' }" :expand-on-click-node="false" :filter-node-method="filterNode" ref="TreeRef" node-key="id" highlight-current default-expand-all @node-click="handleNodeClick"></el-tree>
                    </div>
                </el-col>
            </pane>
            <!-- 右侧内容栏 试验信息列表 -->
            <pane>
                <div class="pane-content">
                    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="88px">
                        <el-form-item label="ID" prop="id">
                            <el-input v-model="queryParams.id" placeholder="请输入数据ID" clearable style="width: 200px" @keyup.enter="handleQuery" />
                        </el-form-item>
                        <el-form-item label="数据名称" prop="dataName">
                            <el-input v-model="queryParams.dataName" placeholder="请输入数据名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
                        </el-form-item>
                        <el-form-item label="试验名称" prop="experimentName">
                            <el-input v-model="queryParams.experimentName" placeholder="请输入试验名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
                        </el-form-item>
                        <el-form-item label="所属项目" prop="projectId">
                            <el-select v-model="queryParams.projectId" placeholder="请选择所属项目" clearable style="width: 200px">
                                <el-option v-for="item in projectOptions" :key="item.projectId" :label="item.projectName" :value="item.projectId" />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="创建人" prop="createBy">
                            <el-input v-model="queryParams.createBy" placeholder="请输入创建人" clearable style="width: 200px" @keyup.enter="handleQuery" />
                        </el-form-item>
                        <el-form-item label="是否模拟" prop="isSimulation">
                            <el-select v-model="queryParams.isSimulation" placeholder="请选择数据类型" clearable style="width: 200px">
                                <el-option label="真实" :value="true" />
                                <el-option label="模拟" :value="false" />
                            </el-select>
                        </el-form-item>
                        <el-form-item label="创建时间" style="width: 308px">
                            <el-date-picker
                                v-model="dateRange"
                                value-format="YYYY-MM-DD"
                                type="daterange"
                                range-separator="-"
                                start-placeholder="开始日期"
                                end-placeholder="结束日期"
                            />
                        </el-form-item>
                        <el-form-item label="数据状态" prop="workStatus">
                            <el-input v-model="queryParams.workStatus" placeholder="请输入数据状态" clearable style="width: 200px" @keyup.enter="handleQuery" />
                        </el-form-item>
                    </el-form>
                    <el-row :gutter="10" v-show="showSearch" style="margin-bottom: 10px;">
                        <el-col :span="24">
                            <div style="display: flex; justify-content: flex-start; gap: 10px;">
                                <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                                <el-button icon="Refresh" @click="resetQuery">重置</el-button>
                            </div>
                        </el-col>
                    </el-row>
                  <el-row :gutter="10" class="mb8">
                    <el-col :span="1.5">
                      <el-button
                          type="primary"
                          plain
                          icon="Plus"
                          @click="handleAddSimulationData"
                      >添加仿真数据</el-button>
                    </el-col>

                    <el-col :span="1.5">
                      <el-button
                          type="success"
                          plain
                          icon="DocumentAdd"
                          @click="handleAddProjectInfo"
                      >添加项目信息</el-button>
                    </el-col>

                    <el-col :span="1.5">
                      <el-button
                          type="info"
                          plain
                          icon="FolderAdd"
                          @click="handleAddExperimentInfo"
                      >添加试验信息</el-button>
                    </el-col>

                    <el-col :span="1.5">
                      <el-button
                          type="danger"
                          plain
                          icon="Delete"
                          :disabled="multiple"
                          @click="handleDelete"
                      >删除</el-button>
                    </el-col>

                    <el-col :span="1.5">
                      <el-button
                          type="warning"
                          plain
                          icon="Refresh"
                          @click="getList"
                      >刷新</el-button>
                    </el-col>

                    <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
                  </el-row>

                    <el-table v-loading="loading" :data="businessList" @selection-change="handleSelectionChange">
                        <el-table-column type="selection" width="55" align="center" />
                        <el-table-column label="ID" align="center" prop="id" />
                        <el-table-column label="数据名称" align="center" prop="dataName" :show-overflow-tooltip="true" />
                        <el-table-column label="是否模拟" align="center" prop="isSimulation" >
                            <template #default="scope">
                                <span v-if="scope.row.isSimulation === true">真实数据</span>
                                <span v-else-if="scope.row.isSimulation === false">模拟数据</span>
                                <span v-else>未知类型</span>
                            </template>
                        </el-table-column>
                        <el-table-column label="路径" align="center" prop="dataFilePath" :show-overflow-tooltip="true" />
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
                        <el-table-column label="数据种类" align="center" prop="dataType" :show-overflow-tooltip="true" />
                        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
                            <template #default="scope">
                            <el-tooltip content="详情" placement="top">
                                <el-button link type="primary" icon="View" @click="handleView(scope.row)"></el-button>
                            </el-tooltip>
                            <el-tooltip content="修改" placement="top">
                                <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
                            </el-tooltip>
                            <el-tooltip content="删除" placement="top">
                                <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)"></el-button>
                            </el-tooltip>
                            </template>
                        </el-table-column>
                    </el-table>
                    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
                </div>
            </pane>
        </splitpanes>

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
                            </el-select>
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
                            <el-input :value="form.experimentName" disabled />
                        </el-form-item>
                    </el-col>
                </el-row>
                <el-row>
                    <el-col :span="12">
                        <el-form-item label="所属项目">
                            <el-input :value="form.projectName" disabled />
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
                        <el-form-item label="路径">
                            <el-input :value="form.dataFilePath" disabled />
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

        <!-- 新增试验对话框 -->
        <el-dialog :title="addExperimentitle" v-model="openExperiment" width="500px" append-to-body>
            <el-form ref="infoRef" :model="experimentform" :rules="experimentRules" label-width="80px">
                <el-form-item label="编号" prop="id">
                    <el-input v-model="experimentform.id" placeholder="自动生成编号" disabled />
                </el-form-item>
                <el-form-item label="名称" prop="name">
                <el-input v-model="experimentform.name" placeholder="请输入名称" @blur="handleGeneratePath" />
                </el-form-item>
                <el-form-item label="所属项目" prop="parentId">
                <el-select v-model="experimentform.parentId" placeholder="请选择所属项目" filterable clearable>
                    <el-option
                    v-for="item in projectOptions"
                    :key="item.projectId"
                    :label="item.projectName"
                    :value="item.projectId"
                    />
                </el-select>
                </el-form-item>
            <el-form-item label="试验目标" prop="targetId">
                <el-select v-model="experimentform.targetId" placeholder="请选择试验目标">
                    <el-option
                    v-for="item in targetTypeOptions"
                    :key="item.targetId"
                    :label="item.targetType"
                    :value="item.targetId"
                    />
                </el-select>
            </el-form-item>
            <el-form-item label="试验日期" prop="startTime">
            <el-date-picker clearable
                v-model="experimentform.startTime"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择开始日期">
            </el-date-picker>
            </el-form-item>
            <el-form-item label="试验地点" prop="location">
            <el-input v-model="experimentform.location" placeholder="请输入地点" />
            </el-form-item>
            <el-form-item label="内容描述" prop="contentDesc">
            <el-input v-model="experimentform.contentDesc" type="textarea" placeholder="请输入内容" />
            </el-form-item>
            <el-form-item label="路径" prop="path">
            <el-input v-model="experimentform.path" placeholder="自动生成路径" disabled />
            </el-form-item>
            </el-form>
            <template #footer>
                <div class="dialog-footer">
                <el-button type="primary" @click="submitaddExperimentForm">确 定</el-button>
                <el-button @click="cancel">取 消</el-button>
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

                <!-- 路径导航和操作栏 - 统一高度对齐 -->
                <div class="import-path-bar">
                    <el-input v-model="currentPath" placeholder="输入文件夹路径" clearable @keyup.enter="loadFileList" class="import-path-input">
                        <template #append>
                            <el-button type="primary" @click="loadFileList">打开</el-button>
                        </template>
                    </el-input>
                    <div class="import-path-actions">
                        <el-button icon="ArrowUp" @click="handleGoUp" :disabled="currentPath === INITIAL_PATH">返回上级</el-button>
                        <el-button @click="handleNewFolder">新建文件夹</el-button>
                        <el-button @click="handleRefresh">刷新</el-button>
                    </div>
                </div>

                <!-- 文件列表 -->
                <el-table :data="fileList" v-loading="fileLoading" stripe class="import-file-table" height="220">
                    <el-table-column label="文件名" width="300" prop="name">
                        <template #default="{ row }">
                            <el-icon v-if="row.isDir" style="margin-right: 8px;"><el-icon-folder /></el-icon>
                            <el-icon v-else style="margin-right: 8px;"><el-icon-document /></el-icon>
                            <el-link type="primary" :underline="false" @click="handleFileOpen(row)">{{ row.name }}</el-link>
                        </template>
                    </el-table-column>
                    <el-table-column label="大小" width="100" prop="size">
                        <template #default="{ row }">{{ formatSize(row.size) }}</template>
                    </el-table-column>
                    <el-table-column label="修改时间" width="180" prop="modified">
                        <template #default="{ row }">{{ formatDate(row.modified) }}</template>
                    </el-table-column>
                    <el-table-column label="操作" width="200">
                        <template #default="{ row }">
                            <el-button link type="primary" size="small" @click="handleFilePreview(row)">预览</el-button>
                            <el-button link type="primary" size="small" @click="handleFileRename(row)">重命名</el-button>
                            <el-button link type="danger" size="small" @click="handleFileDelete(row)">删除</el-button>
                        </template>
                    </el-table-column>
                </el-table>

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

        <!-- 文件管理器预览对话框 -->
        <el-dialog v-model="fileManagerPreviewVisible" :title="`预览: ${fileManagerPreviewFile?.name || ''}`" width="70%" append-to-body>
            <component :is="fileManagerPreviewComponent" :file="fileManagerPreviewFileInfo" v-if="fileManagerPreviewFile" />
            <el-empty v-else description="暂无预览内容" />
            <template #footer>
                <el-button type="primary" @click="fileManagerPreviewVisible = false">关闭</el-button>
            </template>
        </el-dialog>

        <!-- 业务数据详情 (文件预览) 对话框 -->
        <el-dialog v-model="detailVisible" :title="detailTitle" width="70%" append-to-body>
            <div v-if="detailFile">
                <component :is="detailPreviewComponent" :file="detailFileInfo" />
            </div>
            <el-empty v-else description="暂无文件信息或路径无效" />
            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" @click="handleDownloadDetailFile" v-if="detailFile">下 载</el-button>
                    <el-button @click="detailVisible = false">关 闭</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 重命名对话框 -->
        <el-dialog v-model="renameVisible" title="重命名" width="40%" append-to-body>
            <el-input v-model="newFileName" placeholder="输入新文件名" />
            <template #footer>
                <el-button @click="renameVisible = false">取消</el-button>
                <el-button type="primary" @click="submitRename">确定</el-button>
            </template>
        </el-dialog>

        <!-- 新建文件夹对话框 -->
        <el-dialog v-model="newFolderVisible" title="新建文件夹" width="40%" append-to-body>
            <el-input v-model="newFolderName" placeholder="输入文件夹名称" />
            <template #footer>
                <el-button @click="newFolderVisible = false">取消</el-button>
                <el-button type="primary" @click="submitNewFolder">创建</el-button>
            </template>
        </el-dialog>
    </div>
</template>
<script setup name="Business">
import useAppStore from '@/store/modules/app'
import {Splitpanes, Pane} from 'splitpanes'
import 'splitpanes/dist/splitpanes.css'
import {getExperimentList,getdataList,getdataDetail,updatedata,deldata,adddata} from '@/api/DataSimulation/DataSimulation'
import {getInfo} from "@/api/data/info"
import {generatePath} from '@/utils/generatePath'
import { addDateRange } from "@/utils/ruoyi"
import request from '@/utils/request'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { UploadFilled, Folder as ElIconFolder, Document as ElIconDocument, ArrowUp } from '@element-plus/icons-vue'
import ImageViewer from '@/views/viewer/ImageViewer.vue'
import VideoViewer from '@/views/viewer/VideoViewer.vue'
import TextViewer from '@/views/viewer/TextViewer.vue'
import JsonViewer from '@/views/viewer/JsonViewer.vue'
import BinaryViewer from '@/views/viewer/BinaryViewer.vue'
import PDFViewer from '@/views/viewer/PDFViewer.vue'
import ExcelViewer from '@/views/viewer/ExcelViewer.vue'
import { submitDownloadTask, getDownloadTaskStatus, downloadFile } from '@/api/file'
import { get } from '@vueuse/core'

const dateRange = ref([])
const { proxy } = getCurrentInstance()
const treeTableOptions = ref(undefined)
const appStore = useAppStore()
const projectOptions = ref([])
const open = ref(false)
const openView = ref(false)
const openExperiment = ref(false)
const targetTypeOptions = ref([])
const addExperimentitle = ref("添加试验")
const title = ref("")

const name = ref('')
const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const businessList = ref([])
const ids = ref([])
const single = ref(true)
const multiple = ref(true)

// 文件管理器相关状态
const INITIAL_PATH = '/home/hyy1208/data'
const currentPath = ref(INITIAL_PATH)
const fileList = ref([])
const fileLoading = ref(false)
const importVisible = ref(false)
const renameVisible = ref(false)
const newFolderVisible = ref(false)
const uploadFiles = ref([])
const newFileName = ref('')
const newFolderName = ref('')
const fileManagerPreviewFile = ref(null)
const fileManagerPreviewVisible = ref(false)
const renameFile = ref(null)
const uploadDataFormRef = ref(null)

// 详情预览相关状态
const detailVisible = ref(false)
const detailFile = ref(null)
const detailTitle = ref("文件预览")

// /** 打开文件管理器 (导入) */
// function openFileManager() {
//   // 加载必要数据
//   if (!treeTableOptions.value || treeTableOptions.value.length === 0) {
//       getTreeData()
//   }
//   importVisible.value = true
//   getInfo(null, 'experiment').then(res => {
//     targetTypeOptions.value = res.targetTypes || []
//   }).catch(err => {
//     ElMessage.error('获取试验目标失败: ' + (err.message || '未知错误'))
//   })
//   loadFileList()
// }
//
// /** 导出数据 (下载) */
// function handleExportData() {
//   if (ids.value.length === 0) {
//     ElMessage.warning("请选择要导出的数据")
//     return
//   }

  // const selectedRows = businessList.value.filter(item => ids.value.includes(item.id))
  // // 获取所有非空的文件路径
  // const paths = selectedRows.map(item => item.dataFilePath).filter(p => p)

  // if (paths.length === 0) {
  //   ElMessage.warning("选中的数据中没有关联的文件路径")
  //   return
  // }

//   submitDownloadTask(paths).then(res => {
//     if (res.code === 200) pollProgress(res.data)
//     else ElMessage.error(res.msg)
//   }).catch(e => ElMessage.error('导出请求失败: ' + (e.message || '未知错误')))
// }

// function handleRename() {
//   console.log("重命名数据")
// }

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
        ]
    }
})
const experimentform = reactive({
    id: null,
    name: null,
    parentId: null,
    targetId: null,
    startTime: null,
    location: null,
    contentDesc: null,
    path: null
})
const experimentRules = {
     id: [
      { required: true, message: "编号不能为空", trigger: "blur" }
    ],
    name: [
      { required: true, message: "名称不能为空", trigger: "blur" }
    ],
    parentId: [
      { required: true, message: "所属项目不能为空", trigger: "change" }
    ],
    startTime: [
      { required: true, message: "时间不能为空", trigger: "blur" }
    ],
    targetId: [
      { required: true, message: "试验目标不能为空", trigger: "change" }
    ],
    location: [
      { required: true, message: "地点不能为空", trigger: "blur" }
    ],
    contentDesc: [
      { required: true, message: "内容描述不能为空", trigger: "blur" }
    ]
}

const {queryParams, form, rules} = toRefs(data)
const filterNode = (value, data) => {
    if (!value) return true
    return data.label && data.label.indexOf(value) !== -1
}

function getTreeData() {
    getExperimentList().then(response => {
        const transformedData = transformTreeData(response.data)
        treeTableOptions.value = transformedData
        console.log('Tree data loaded:', transformedData)
    }).catch(error => {
        console.error('Failed to load tree data:', error)
    })
}
/** 自动生成路径 */
async function handleGeneratePath() {
  if (experimentform.name) {
    experimentform.path = await generatePath(experimentform.name)
  }
}

function handleAddExperiment() {
  // 重置试验表单数据
  Object.assign(experimentform, {
    id: null,
    name: null,
    parentId: null,
    targetId: null,
    startTime: null,
    location: null,
    contentDesc: null,
    path: null
  })
  getInfo(null, 'experiment').then(res => {
    projectOptions.value = res.projects || []
    targetTypeOptions.value = res.targetTypes || []
  }).catch(err => {
    ElMessage.error('获取试验信息失败: ' + (err.message || '未知错误'))
  } )
  openExperiment.value = true
  addExperimentitle.value = "添加试验"
}

// --- 文件管理器逻辑开始 ---

const fileManagerPreviewFileInfo = computed(() => {
  if (!fileManagerPreviewFile.value) return null
  return {
    name: fileManagerPreviewFile.value.name,
    path: fileManagerPreviewFile.value.path,
    contentUrl: `/api/file/content?path=${encodeURIComponent(fileManagerPreviewFile.value.path)}`
  }
})

const fileManagerPreviewComponent = computed(() => getPreviewComponent(fileManagerPreviewFile.value))

const detailFileInfo = computed(() => {
  if (!detailFile.value) return null
  return {
    name: detailFile.value.name,
    path: detailFile.value.path,
    contentUrl: `/api/file/content?path=${encodeURIComponent(detailFile.value.path)}`
  }
})

const detailPreviewComponent = computed(() => getPreviewComponent(detailFile.value))

function getPreviewComponent(file) {
  if (!file) return null
  const fileName = file.name.toLowerCase()
  let type = 'binary'

  if (fileName.endsWith('.png') || fileName.endsWith('.jpg') || fileName.endsWith('.jpeg') ||
      fileName.endsWith('.gif') || fileName.endsWith('.bmp')) {
    type = 'image'
  } else if (fileName.endsWith('.mp4') || fileName.endsWith('.webm') || fileName.endsWith('.avi')) {
    type = 'video'
  } else if (fileName.endsWith('.txt') || fileName.endsWith('.csv') || fileName.endsWith('.json')) {
    type = 'text'
  } else if (fileName.endsWith('.json')) {
    type = 'json'
  } else if (fileName.endsWith('.pdf')) {
    type = 'pdf'
  } else if (fileName.endsWith('.xls') || fileName.endsWith('.xlsx')) {
    type = 'excel'
  }

  const componentMap = {
    image: ImageViewer,
    video: VideoViewer,
    text: TextViewer,
    json: JsonViewer,
    pdf: PDFViewer,
    excel: ExcelViewer,
    binary: BinaryViewer
  }
  return componentMap[type]
}

const loadFileList = async () => {
  fileLoading.value = true
  try {
    const res = await request({
      url: '/api/file/list',
      method: 'get',
      params: { path: currentPath.value }
    })
    fileList.value = (res.data || []).sort((a, b) => {
      if (a.isDir !== b.isDir) return b.isDir - a.isDir
      return a.name.localeCompare(b.name)
    })
  } catch (err) {
    ElMessage.error('加载文件列表失败: ' + err.message)
  } finally {
    fileLoading.value = false
  }
}

const handleFileOpen = (row) => {
  if (row.isDir) {
    currentPath.value = row.path
    loadFileList()
  }
}

const handleFilePreview = (row) => {
  if (!row.isDir) {
    fileManagerPreviewFile.value = row
    fileManagerPreviewVisible.value = true
  }
}

const handleFileRename = (row) => {
  renameFile.value = row
  newFileName.value = row.name
  renameVisible.value = true
}

const submitRename = async () => {
  try {
    await request({
      url: '/api/file/rename',
      method: 'post',
      data: {
        oldPath: renameFile.value.path,
        newName: newFileName.value
      }
    })
    ElMessage.success('重命名成功')
    renameVisible.value = false
    loadFileList()
  } catch (err) {
    ElMessage.error('重命名失败')
  }
}

const handleFileDelete = (row) => {
  ElMessageBox.confirm(
    `确定删除 "${row.name}" 吗?`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await request({
        url: '/api/file/delete',
        method: 'post',
        data: { path: row.path }
      })
      ElMessage.success('删除成功')
      loadFileList()
    } catch (err) {
      ElMessage.error('删除失败')
    }
  })
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
  currentPath.value = INITIAL_PATH
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
            const formData = new FormData()
            formData.append('file', file.raw)
            formData.append('path', currentPath.value)

            await request({
                url: '/api/file/upload',
                method: 'post',
                data: formData,
                headers: { 'Content-Type': 'multipart/form-data' }
            })

            // 上传成功后，保存业务数据
            const fullPath = currentPath.value + (currentPath.value.endsWith('\\') || currentPath.value.endsWith('/') ? '' : '/') + (file.raw.webkitRelativePath || file.name)

            const businessData = {
                dataName: uploadDataForm.dataName || file.name,
                experimentId: uploadDataForm.experimentId,
                targetId: uploadDataForm.targetId,
                targetType: uploadDataForm.targetType,
                dataType: uploadDataForm.dataType,
                isSimulation: uploadDataForm.isSimulation,
                dataFilePath: fullPath,
                // 生成随机解析结果
                sampleFrequency: Math.floor(Math.random() * 1000) + 1,
                workStatus: ['正常', '异常', '待机'][Math.floor(Math.random() * 3)]
            }

            await adddata(businessData)
        }
        ElMessage.success('数据导入成功')
        importVisible.value = false
        await getList() // 自动查询业务数据列表
    } catch (err) {
        ElMessage.error('导入失败: ' + (err.message || '未知错误'))
    }
  })
}

const handleNewFolder = () => {
  newFolderName.value = ''
  newFolderVisible.value = true
}

const submitNewFolder = async () => {
  if (!newFolderName.value.trim()) {
    ElMessage.warning('文件夹名称不能为空')
    return
  }

  try {
    await request({
      url: '/api/file/mkdir',
      method: 'post',
      data: {
        path: currentPath.value,
        dirName: newFolderName.value
      }
    })
    ElMessage.success('创建成功')
    newFolderVisible.value = false
    loadFileList()
  } catch (err) {
    ElMessage.error('创建失败')
  }
}

const handleRefresh = () => {
  loadFileList()
}

const handleGoUp = () => {
  if (currentPath.value === INITIAL_PATH) return
  const sep = currentPath.value.includes('/') ? '/' : '\\'
  const lastIndex = currentPath.value.lastIndexOf(sep)
  if (lastIndex > -1) {
    const parentPath = currentPath.value.substring(0, lastIndex)
    if (parentPath.length >= INITIAL_PATH.length) {
       currentPath.value = parentPath
       loadFileList()
    }
  }
}

const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const formatDate = (timestamp) => {
  return new Date(timestamp).toLocaleString('zh-CN')
}
const percent=ref(0)
const timer=ref(null)
/** 下载详情中的文件 */
const handleDownloadDetailFile = async (row) => {
  const target = row || detailFile.value
  if (!target || !target.dataFilePath) return

  try {
    const res = await submitDownloadTask([target.dataFilePath])
    const taskKey = res.data
    pollProgress(taskKey)
  } catch (e) {
    ElMessage.error('下载失败: ' + (e.message || '未知错误'))
  }
}
function pollProgress(taskKey) {
    if (timer.value) clearInterval(timer.value)

    const loadingInstance = ElLoading.service({
        lock: true,
        text: '正在打包下载，请稍候...',
        background: 'rgba(0, 0, 0, 0.7)',
    })

    timer.value = setInterval(() => {
        getDownloadTaskStatus(taskKey).then(res => {
          if (res.code !== 200) {
            clearInterval(timer.value);
            loadingInstance.close();
            ElMessage.error(res.msg);
            return;
          }
          const progress = res.data; // 后端返回的整数百分比
          percent.value = progress;
          // 3. 进度完成，触发真实下载
          if (progress >= 100) {
            clearInterval(timer.value);
            loadingInstance.close();
            const baseUrl = import.meta.env.VITE_APP_BASE_API
            window.location.href = baseUrl + "/api/file/download/" + taskKey;
          }
        }).catch(() => {
            clearInterval(timer.value);
            loadingInstance.close();
        });
    }, 1000); // 每1秒查询一次
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
    proxy.$refs["TreeRef"].filter(val)
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
  openExperiment.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    dataName: null,
    isSimulation: null,
    dataType: null,
    startTime: null,
    location: null,
    contentDesc: null
  }
  proxy.resetForm("dataRef")
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 按钮操作：添加仿真数据 */
function handleAddSimulationData() {
  // TODO: 在这里编写你的业务逻辑
  // 比如打开一个弹窗： open.value = true; title.value = "添加仿真数据";
  console.log("点击了添加仿真数据");
}

/** 按钮操作：添加项目信息 */
function handleAddProjectInfo() {
  // TODO: 在这里编写你的业务逻辑
  console.log("点击了添加项目信息");
}

/** 按钮操作：添加试验信息 */
function handleAddExperimentInfo() {
  // TODO: 在这里编写你的业务逻辑
  console.log("点击了添加试验信息");
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
function handleUpdate(row) {
  reset()
  const id = row.id
  getdataDetail(id).then(response => {
    form.value = response.data
    form.value.id = id
    open.value = true
    title.value = "修改数据"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["dataRef"].validate(valid => {
    if (valid) {
      updatedata(form.value).then(response => {
        proxy.$modal.msgSuccess("修改成功")
        open.value = false
        getList()
      })
    }
  })
}
/**提交新增试验表单 */
function submitaddExperimentForm() {
  proxy.$refs["infoRef"].validate(valid => {
    if (valid) {
      const submitData = { ...experimentform, type: 'experiment' }
      addInfo(submitData).then(response => {
        proxy.$modal.msgSuccess("添加成功")
        openExperiment.value = false
        getTreeData() // 刷新左侧树形结构
      })
    }
  })
}

/** 详情按钮操作 (打开文件预览) */
function handleView(row) {
  if (row.dataFilePath) {
    detailFile.value = {
      name: row.dataName,
      path: row.dataFilePath
    }
    detailTitle.value = `文件预览: ${row.dataName}`
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
    getdataList(addDateRange(queryParams.value, dateRange.value)).then(response => {
      businessList.value = response.rows || (response.data && response.data.rows) || [];
      total.value = response.total || (response.data && response.data.total) || 0;
      loading.value = false;
    });
}
onMounted(()=>{
    getList()
    getTreeData()
})

</script>
<style scoped>
/* 禁用分割面板初始加载时的宽度过渡，使分割符打开时直接位于正确位置 */
:deep(.splitpanes--vertical .splitpanes__pane) {
  transition: none;
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
</style>
