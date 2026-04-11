<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Loading, Plus, Search, Upload, Warning } from '@element-plus/icons-vue'
import {
  batchDeleteEmployee,
  createEmployee,
  deleteEmployee,
  downloadEmployeeTemplate,
  getEmployeeList,
  importEmployeeExcel,
  updateEmployee,
} from '@/api/employee'
import type { EmployeeForm, EmployeeRecordVO, ImportError } from '@/types'
import { downloadImportErrorReport } from '@/utils/excel'
import type { FormInstance, FormRules } from 'element-plus'

const loading = ref(false)
const tableData = ref<EmployeeRecordVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedRows = ref<EmployeeRecordVO[]>([])

const filterDepartment = ref('')
const filterStatus = ref<number | undefined>(undefined)

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: currentPage.value,
      size: pageSize.value,
    }
    if (filterDepartment.value.trim()) {
      params.department = filterDepartment.value.trim()
    }
    if (filterStatus.value !== undefined) {
      params.status = filterStatus.value
    }
    const res = await getEmployeeList(params as Parameters<typeof getEmployeeList>[0])
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  currentPage.value = 1
  fetchList()
}

function handlePageChange(page: number) {
  currentPage.value = page
  fetchList()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  fetchList()
}

function handleSelectionChange(rows: EmployeeRecordVO[]) {
  selectedRows.value = rows
}

const drawerVisible = ref(false)
const drawerTitle = ref('新增员工')
const drawerFormRef = ref<FormInstance>()
const drawerSubmitting = ref(false)
const editingId = ref<number | null>(null)
const drawerForm = reactive<EmployeeForm>({
  name: '',
  department: '',
  position: '',
  salary: '',
  hireDate: '',
  status: 1,
  remark: '',
})

const drawerRules: FormRules = {
  name: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }],
  department: [{ required: true, message: '请输入所属部门', trigger: 'blur' }],
  salary: [
    { required: true, message: '请输入基础薪资', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback(new Error('请输入基础薪资'))
        const num = Number(value)
        if (Number.isNaN(num) || num < 0) return callback(new Error('基础薪资不能小于0'))
        callback()
      },
      trigger: 'blur',
    },
  ],
  hireDate: [{ required: true, message: '请选择入职日期', trigger: 'change' }],
  status: [{ required: true, message: '请选择在职状态', trigger: 'change' }],
}

function resetDrawerForm() {
  drawerForm.name = ''
  drawerForm.department = ''
  drawerForm.position = ''
  drawerForm.salary = ''
  drawerForm.hireDate = ''
  drawerForm.status = 1
  drawerForm.remark = ''
}

function openCreateDrawer() {
  editingId.value = null
  drawerTitle.value = '新增员工'
  resetDrawerForm()
  drawerVisible.value = true
}

function openEditDrawer(row: EmployeeRecordVO) {
  editingId.value = row.id
  drawerTitle.value = '编辑员工'
  drawerForm.name = row.name
  drawerForm.department = row.department
  drawerForm.position = row.position || ''
  drawerForm.salary = String(row.salary)
  drawerForm.hireDate = row.hireDate
  drawerForm.status = row.status
  drawerForm.remark = row.remark || ''
  drawerVisible.value = true
}

function handleSalaryBlur() {
  if (drawerForm.salary && !Number.isNaN(Number(drawerForm.salary))) {
    drawerForm.salary = Number(drawerForm.salary).toFixed(2)
  }
}

async function submitDrawer() {
  if (!drawerFormRef.value) return
  await drawerFormRef.value.validate()
  drawerSubmitting.value = true
  try {
    const payload: EmployeeForm = {
      ...drawerForm,
      name: drawerForm.name.trim(),
      department: drawerForm.department.trim(),
      position: drawerForm.position.trim(),
      salary: Number(drawerForm.salary).toFixed(2),
      remark: drawerForm.remark.trim(),
    }
    if (editingId.value) {
      await updateEmployee(editingId.value, payload)
      ElMessage.success('员工记录更新成功')
    } else {
      await createEmployee(payload)
      ElMessage.success('员工记录创建成功')
    }
    drawerVisible.value = false
    fetchList()
  } finally {
    drawerSubmitting.value = false
  }
}

async function handleDelete(row: EmployeeRecordVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除员工「${row.name}」的名册记录吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await deleteEmployee(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // cancelled
  }
}

const hasBatchSelection = computed(() => selectedRows.value.length > 0)

async function handleBatchDelete() {
  if (!selectedRows.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定要批量删除选中的 ${selectedRows.value.length} 条员工记录吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    const ids = selectedRows.value.map((item) => item.id)
    await batchDeleteEmployee(ids)
    ElMessage.success('批量删除成功')
    selectedRows.value = []
    fetchList()
  } catch {
    // cancelled
  }
}

const importDialogVisible = ref(false)
const importLoading = ref(false)
const importErrors = ref<ImportError[]>([])
const importHasError = ref(false)

function openImportDialog() {
  importErrors.value = []
  importHasError.value = false
  importDialogVisible.value = true
}

async function handleImportUpload(options: { file: File }) {
  importLoading.value = true
  importErrors.value = []
  importHasError.value = false
  try {
    await importEmployeeExcel(options.file)
    ElMessage.success('导入成功')
    importDialogVisible.value = false
    fetchList()
  } catch (err: unknown) {
    const error = err as { code?: number; message?: string; data?: ImportError[] }
    if (error?.data && Array.isArray(error.data)) {
      importErrors.value = error.data
      importHasError.value = true
    } else {
      ElMessage.error(error?.message || '导入失败')
    }
  } finally {
    importLoading.value = false
  }
}

async function handleTemplateDownload() {
  try {
    await downloadEmployeeTemplate()
  } catch {
    ElMessage.error('模板下载失败')
  }
}

function handleErrorReportDownload() {
  if (!importErrors.value.length) return
  downloadImportErrorReport(importErrors.value)
}

function formatSalary(row: EmployeeRecordVO) {
  return `¥${Number(row.salary).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function getStatusLabel(status: number) {
  return status === 1 ? '在职' : '离职'
}

function getStatusTagType(status: number) {
  return status === 1 ? 'success' : 'info'
}

onMounted(fetchList)
</script>

<template>
  <div class="employee-manage">
    <div class="page-header">
      <h2 class="page-title">员工名册</h2>
    </div>

    <div class="action-bar">
      <div class="action-left">
        <el-button type="primary" :icon="Plus" @click="openCreateDrawer">单笔新增</el-button>
        <el-button :icon="Upload" @click="openImportDialog">Excel 批量导入</el-button>
        <a class="template-link" @click="handleTemplateDownload">下载导入模板</a>
      </div>
      <div class="action-right">
        <el-button
          :icon="Delete"
          :disabled="!hasBatchSelection"
          :class="{ 'batch-delete-active': hasBatchSelection }"
          @click="handleBatchDelete"
        >
          批量删除{{ hasBatchSelection ? ` (${selectedRows.length})` : '' }}
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="filterDepartment"
        placeholder="输入所属部门后回车搜索"
        clearable
        :prefix-icon="Search"
        style="width: 240px"
        @keyup.enter="handleFilter"
        @clear="handleFilter"
      />
      <el-select
        v-model="filterStatus"
        placeholder="在职状态"
        clearable
        style="width: 140px"
        @change="handleFilter"
      >
        <el-option label="在职" :value="1" />
        <el-option label="离职" :value="0" />
      </el-select>
      <el-button :icon="Search" @click="handleFilter">搜索</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="tableData"
      style="width: 100%"
      :header-cell-style="{
        background: '#f6f5f4',
        color: '#615d59',
        fontWeight: 600,
        fontSize: '13px',
        height: '44px',
      }"
      :row-style="{ height: '48px' }"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="48" align="center" />
      <el-table-column prop="name" label="姓名" min-width="120" show-overflow-tooltip />
      <el-table-column prop="department" label="所属部门" min-width="130" show-overflow-tooltip />
      <el-table-column prop="position" label="职位" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.position || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="基础薪资" width="160" align="right">
        <template #default="{ row }">
          <span class="salary-cell">{{ formatSalary(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="hireDate" label="入职日期" width="130" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.status)" size="small" round>
            {{ getStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.remark || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" :icon="Edit" @click="openEditDrawer(row)">
            编辑
          </el-button>
          <el-button type="danger" link size="small" :icon="Delete" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      direction="rtl"
      size="480px"
      destroy-on-close
    >
      <el-form
        ref="drawerFormRef"
        :model="drawerForm"
        :rules="drawerRules"
        label-position="top"
        class="drawer-form"
      >
        <el-form-item label="员工姓名" prop="name">
          <el-input v-model="drawerForm.name" placeholder="请输入员工姓名" />
        </el-form-item>

        <el-form-item label="所属部门" prop="department">
          <el-input v-model="drawerForm.department" placeholder="请输入所属部门" />
        </el-form-item>

        <el-form-item label="职位">
          <el-input v-model="drawerForm.position" placeholder="选填，如：招商主管" />
        </el-form-item>

        <el-form-item label="基础薪资" prop="salary">
          <el-input
            v-model="drawerForm.salary"
            placeholder="请输入基础薪资"
            class="salary-input"
            @blur="handleSalaryBlur"
          >
            <template #prefix>¥</template>
          </el-input>
        </el-form-item>

        <el-form-item label="入职日期" prop="hireDate">
          <el-date-picker
            v-model="drawerForm.hireDate"
            type="date"
            placeholder="请选择入职日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="在职状态" prop="status">
          <el-radio-group v-model="drawerForm.status">
            <el-radio :value="1">在职</el-radio>
            <el-radio :value="0">离职</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="drawerForm.remark"
            type="textarea"
            :rows="3"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="drawerSubmitting" @click="submitDrawer">
            确认
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="importDialogVisible"
      title="Excel 批量导入"
      width="560px"
      destroy-on-close
      :class="{ 'import-error-dialog': importHasError }"
    >
      <div v-if="!importHasError" class="import-upload-area">
        <el-upload
          drag
          accept=".xlsx,.xls"
          :auto-upload="true"
          :show-file-list="false"
          :http-request="handleImportUpload"
        >
          <div class="upload-content">
            <el-icon :size="48" color="#a39e98"><Upload /></el-icon>
            <p class="upload-text">将 Excel 文件拖拽到此处，或 <em>点击上传</em></p>
            <p class="upload-hint">仅支持 .xlsx 格式，请先下载导入模板</p>
          </div>
        </el-upload>
      </div>

      <div v-else class="import-error-area">
        <div class="error-header">
          <el-icon color="#e03e3e" :size="20"><Warning /></el-icon>
          <span>导入失败，以下数据存在问题（共 {{ importErrors.length }} 条错误）</span>
        </div>
        <el-table :data="importErrors" max-height="300" size="small">
          <el-table-column prop="row" label="行号" width="80" align="center" />
          <el-table-column prop="error" label="错误原因" />
        </el-table>
        <div class="error-actions">
          <el-button @click="handleErrorReportDownload">下载 Error_Report.xlsx</el-button>
          <el-button @click="importHasError = false">重新上传</el-button>
          <el-button @click="importDialogVisible = false">关闭</el-button>
        </div>
      </div>

      <div v-if="importLoading" class="import-loading-mask">
        <el-icon class="is-loading" :size="32" color="#0075de"><Loading /></el-icon>
        <span>正在导入...</span>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.employee-manage {
  padding: 0 4px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.95);
  letter-spacing: -0.25px;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  margin-bottom: 16px;
}

.action-left,
.action-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.template-link {
  font-size: 13px;
  font-weight: 500;
  color: #0075de;
  cursor: pointer;
  text-decoration: none;
}

.template-link:hover {
  text-decoration: underline;
}

.batch-delete-active {
  color: #e03e3e !important;
  border-color: #e03e3e !important;
}

.batch-delete-active:hover {
  color: #ffffff !important;
  background-color: #e03e3e !important;
  border-color: #e03e3e !important;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.salary-cell {
  font-weight: 600;
  font-size: 14px;
  color: #0f766e;
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'lnum';
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 8px 0;
}

.drawer-form {
  padding: 0 4px;
}

.salary-input :deep(.el-input__inner) {
  font-size: 24px;
  font-weight: 700;
  text-align: right;
  height: 48px;
}

.salary-input :deep(.el-input__prefix) {
  font-size: 24px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.45);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.import-upload-area {
  padding: 20px 0;
}

.upload-content {
  padding: 40px 0;
  text-align: center;
}

.upload-text {
  margin-top: 16px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.65);
}

.upload-text em {
  color: #0075de;
  font-style: normal;
}

.upload-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #a39e98;
}

.import-error-area {
  padding: 8px 0;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #e03e3e;
  margin-bottom: 16px;
}

.error-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.import-loading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.9);
  z-index: 10;
  font-size: 14px;
  color: #615d59;
}

:deep(.el-table) {
  --el-table-border-color: rgba(0, 0, 0, 0.06);
  --el-table-row-hover-bg-color: rgba(0, 117, 222, 0.03);
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
}

:deep(.el-drawer__header) {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  margin-bottom: 0;
  padding: 16px 24px;
}

:deep(.el-drawer__title) {
  font-size: 18px;
  font-weight: 700;
}

:deep(.el-drawer__body) {
  padding: 24px;
}

:deep(.el-drawer__footer) {
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  padding: 16px 24px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding-bottom: 16px;
}

:deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 700;
}

:deep(.el-dialog__body) {
  position: relative;
}

:deep(.import-error-dialog .el-dialog__header) {
  border-bottom-color: #e03e3e;
}

:deep(.el-upload-dragger) {
  border: 2px dashed rgba(0, 0, 0, 0.15);
  border-radius: 8px;
  transition: border-color 0.3s;
}

:deep(.el-upload-dragger:hover) {
  border-color: #0075de;
}
</style>
