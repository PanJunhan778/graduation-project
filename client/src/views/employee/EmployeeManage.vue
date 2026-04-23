<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Delete, Edit, Loading, Plus, RefreshLeft, Search, Upload, Warning } from '@element-plus/icons-vue'
import PageTableSkeleton from '@/components/common/PageTableSkeleton.vue'
import RecycleBinDrawer from '@/components/common/RecycleBinDrawer.vue'
import { useDelayedLoading } from '@/composables/useDelayedLoading'
import {
  batchDeleteEmployee,
  batchRestoreEmployee,
  createEmployee,
  deleteEmployee,
  downloadEmployeeTemplate,
  getEmployeeList,
  getEmployeeRecycleBinList,
  importEmployeeExcel,
  restoreEmployee,
  updateEmployee,
} from '@/api/employee'
import { useUserStore } from '@/store/user'
import type { EmployeeForm, EmployeeRecordVO, EmployeeRecycleBinVO, ImportError } from '@/types'
import { downloadImportErrorReport } from '@/utils/excel'
import type { FormInstance, FormRules } from 'element-plus'

const userStore = useUserStore()
const loading = ref(false)
const hasLoaded = ref(false)
const tableData = ref<EmployeeRecordVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedRows = ref<EmployeeRecordVO[]>([])
const showInitialSkeleton = useDelayedLoading(() => loading.value && !hasLoaded.value)

const keyword = ref('')
const filterStatus = ref<number | undefined>(undefined)

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

const recycleBinVisible = ref(false)
const recycleBinLoading = ref(false)
const recycleBinTableData = ref<EmployeeRecycleBinVO[]>([])
const recycleBinTotal = ref(0)
const recycleBinCurrentPage = ref(1)
const recycleBinPageSize = ref(10)
const recycleBinSelectedRows = ref<EmployeeRecycleBinVO[]>([])

const importDialogVisible = ref(false)
const importLoading = ref(false)
const importErrors = ref<ImportError[]>([])
const importHasError = ref(false)

const drawerRules: FormRules = {
  name: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }],
  department: [{ required: true, message: '请输入所属部门', trigger: 'blur' }],
  salary: [
    { required: true, message: '请输入基础薪资', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback(new Error('请输入基础薪资'))
        const num = Number(value)
        if (Number.isNaN(num) || num < 0) {
          return callback(new Error('基础薪资不能小于 0'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  hireDate: [{ required: true, message: '请选择入职日期', trigger: 'change' }],
  status: [{ required: true, message: '请选择在职状态', trigger: 'change' }],
}

const hasBatchSelection = computed(() => selectedRows.value.length > 0)
const hasRecycleBatchSelection = computed(() => recycleBinSelectedRows.value.length > 0)

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: currentPage.value,
      size: pageSize.value,
    }
    const trimmedKeyword = keyword.value.trim()
    if (trimmedKeyword) {
      params.keyword = trimmedKeyword
    }
    if (filterStatus.value !== undefined) {
      params.status = filterStatus.value
    }
    const res = await getEmployeeList(params as Parameters<typeof getEmployeeList>[0])
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    hasLoaded.value = true
    loading.value = false
  }
}

async function fetchRecycleBinList() {
  recycleBinLoading.value = true
  try {
    const res = await getEmployeeRecycleBinList({
      page: recycleBinCurrentPage.value,
      size: recycleBinPageSize.value,
    })
    recycleBinTableData.value = res.data.records
    recycleBinTotal.value = res.data.total

    if (recycleBinCurrentPage.value > 1 && recycleBinTableData.value.length === 0 && recycleBinTotal.value > 0) {
      recycleBinCurrentPage.value -= 1
      await fetchRecycleBinList()
    }
  } finally {
    recycleBinLoading.value = false
  }
}

function handleFilter() {
  currentPage.value = 1
  void fetchList()
}

function handlePageChange(page: number) {
  currentPage.value = page
  void fetchList()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  void fetchList()
}

function handleSelectionChange(rows: EmployeeRecordVO[]) {
  selectedRows.value = rows
}

function handleRecycleBinSelectionChange(rows: EmployeeRecycleBinVO[]) {
  recycleBinSelectedRows.value = rows
}

function handleRecycleBinPageChange(page: number) {
  recycleBinCurrentPage.value = page
  void fetchRecycleBinList()
}

function handleRecycleBinSizeChange(size: number) {
  recycleBinPageSize.value = size
  recycleBinCurrentPage.value = 1
  void fetchRecycleBinList()
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

function openRecycleBinDrawer() {
  recycleBinVisible.value = true
  recycleBinCurrentPage.value = 1
  recycleBinSelectedRows.value = []
  void fetchRecycleBinList()
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
    void fetchList()
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
    void fetchList()
  } catch {
    // cancelled
  }
}

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
    selectedRows.value = []
    ElMessage.success('批量删除成功')
    void fetchList()
  } catch {
    // cancelled
  }
}

async function handleRestoreFromRecycleBin(row: EmployeeRecycleBinVO) {
  try {
    await ElMessageBox.confirm(
      `确定要恢复员工「${row.name}」的记录吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await restoreEmployee(row.id)
    recycleBinSelectedRows.value = []
    await Promise.all([fetchRecycleBinList(), fetchList()])
    ElMessage.success('恢复成功')
  } catch {
    // cancelled
  }
}

async function handleBatchRestoreFromRecycleBin() {
  if (!recycleBinSelectedRows.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定要恢复选中的 ${recycleBinSelectedRows.value.length} 条员工记录吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    const ids = recycleBinSelectedRows.value.map((item) => item.id)
    const res = await batchRestoreEmployee(ids)
    recycleBinSelectedRows.value = []
    await Promise.all([fetchRecycleBinList(), fetchList()])
    ElMessage.success(`已恢复 ${res.data || ids.length} 条记录`)
  } catch {
    // cancelled
  }
}

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
    void fetchList()
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

function formatSalary(row: { salary: number }) {
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
  <PageTableSkeleton
    v-if="showInitialSkeleton"
    title="员工名册"
    :action-count="userStore.isOwner ? 4 : 3"
    :filter-count="2"
    :row-count="8"
  />
  <div v-else class="crud-page">
    <div class="crud-card">
      <div class="crud-page-header">
        <div>
          <h2 class="crud-page-title">员工名册</h2>
          <p class="crud-page-subtitle">维护企业员工的基本信息、薪资及在职状态。</p>
        </div>
      </div>

      <div class="crud-toolbar">
        <div class="crud-toolbar-left">
          <el-button type="primary" :icon="Plus" @click="openCreateDrawer">单笔新增</el-button>
          <el-button :icon="Upload" @click="openImportDialog">Excel 批量导入</el-button>
          <el-button v-if="userStore.isOwner" :icon="RefreshLeft" @click="openRecycleBinDrawer">回收站</el-button>
          <a class="template-link" @click="handleTemplateDownload">下载导入模板</a>
        </div>
        <div class="crud-toolbar-right">
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

      <div class="crud-filters">
        <el-input
          v-model="keyword"
          placeholder="搜索姓名、部门、职位或备注"
          clearable
          :prefix-icon="Search"
          style="width: 280px"
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

      <div class="crud-table-section">
        <el-table
          :data="tableData"
          stripe
          style="width: 100%"
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
      </div>

      <div class="crud-pagination">
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
        class="crud-drawer-form"
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
          <el-input v-model="drawerForm.remark" type="textarea" :rows="3" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="crud-drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="drawerSubmitting" @click="submitDrawer">确认</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="importDialogVisible"
      title="Excel 批量导入"
      width="560px"
      destroy-on-close
      :class="{ 'crud-import-error-dialog': importHasError }"
    >
      <div v-if="!importHasError" class="crud-import-upload-area">
        <el-upload drag accept=".xlsx,.xls" :auto-upload="true" :show-file-list="false" :http-request="handleImportUpload">
          <div class="crud-upload-content">
            <el-icon :size="48" color="#a39e98"><Upload /></el-icon>
            <p class="crud-upload-text">将 Excel 文件拖拽到此处，或 <em>点击上传</em></p>
            <p class="crud-upload-hint">仅支持 .xlsx 格式，请先下载导入模板</p>
          </div>
        </el-upload>
      </div>
      <div v-else class="crud-import-error-area">
        <div class="crud-error-header">
          <el-icon color="#e03e3e" :size="20"><Warning /></el-icon>
          <span>导入失败，以下数据存在问题（共 {{ importErrors.length }} 条错误）</span>
        </div>
        <el-table :data="importErrors" max-height="300" size="small">
          <el-table-column prop="row" label="行号" width="80" align="center" />
          <el-table-column prop="error" label="错误原因" />
        </el-table>
        <div class="crud-error-actions">
          <el-button @click="handleErrorReportDownload">下载 Error_Report.xlsx</el-button>
          <el-button @click="importHasError = false">重新上传</el-button>
          <el-button @click="importDialogVisible = false">关闭</el-button>
        </div>
      </div>
      <div v-if="importLoading" class="crud-import-loading-mask">
        <el-icon class="is-loading" :size="32" color="#0075de"><Loading /></el-icon>
        <span>正在导入...</span>
      </div>
    </el-dialog>

    <RecycleBinDrawer
      v-if="userStore.isOwner"
      v-model="recycleBinVisible"
      title="员工回收站"
      :data="recycleBinTableData"
      :loading="recycleBinLoading"
      :total="recycleBinTotal"
      :page="recycleBinCurrentPage"
      :size="recycleBinPageSize"
      :selected-count="hasRecycleBatchSelection ? recycleBinSelectedRows.length : 0"
      @selection-change="handleRecycleBinSelectionChange"
      @page-change="handleRecycleBinPageChange"
      @size-change="handleRecycleBinSizeChange"
      @restore="handleRestoreFromRecycleBin"
      @batch-restore="handleBatchRestoreFromRecycleBin"
    >
      <template #columns>
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
      </template>
    </RecycleBinDrawer>
  </div>
</template>

<style scoped>
.crud-page-header {
  margin-bottom: 16px;
}

.salary-cell {
  font-weight: 600;
  font-size: 14px;
  color: #0f766e;
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'lnum';
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
</style>
