<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Loading, Plus, Search, Upload, Warning } from '@element-plus/icons-vue'
import {
  batchDeleteTax,
  createTax,
  deleteTax,
  downloadTaxTemplate,
  getTaxList,
  importTaxExcel,
  updateTax,
} from '@/api/tax'
import { downloadImportErrorReport } from '@/utils/excel'
import type { FormInstance, FormRules } from 'element-plus'
import type { ImportError, TaxForm, TaxPaymentStatus, TaxRecordVO } from '@/types'

const loading = ref(false)
const tableData = ref<TaxRecordVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedRows = ref<TaxRecordVO[]>([])

const filterPaymentStatus = ref<TaxPaymentStatus | undefined>(undefined)
const filterTaxPeriod = ref('')
const filterTaxType = ref('')

const taxTypeOptions = [
  '增值税',
  '企业所得税',
  '个人所得税',
  '印花税',
  '城市维护建设税',
  '教育费附加',
]

const declarationTypeOptions = ['日常/预缴', '年度汇算清缴']

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      page: currentPage.value,
      size: pageSize.value,
    }
    if (filterPaymentStatus.value !== undefined) {
      params.paymentStatus = filterPaymentStatus.value
    }
    if (filterTaxPeriod.value.trim()) {
      params.taxPeriod = filterTaxPeriod.value.trim()
    }
    if (filterTaxType.value.trim()) {
      params.taxType = filterTaxType.value.trim()
    }
    const res = await getTaxList(params as Parameters<typeof getTaxList>[0])
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

function handleSelectionChange(rows: TaxRecordVO[]) {
  selectedRows.value = rows
}

const drawerVisible = ref(false)
const drawerTitle = ref('新增税务记录')
const drawerFormRef = ref<FormInstance>()
const drawerSubmitting = ref(false)
const editingId = ref<number | null>(null)
const drawerForm = reactive<TaxForm>({
  taxPeriod: '',
  taxType: '',
  declarationType: '',
  taxAmount: '',
  paymentStatus: 0,
  paymentDate: null,
  remark: '',
})

const taxPeriodPattern = /^\d{4}-(0[1-9]|1[0-2]|Q[1-4]|Annual)$/

const drawerRules: FormRules = {
  taxPeriod: [
    { required: true, message: '请输入税款所属期', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback(new Error('请输入税款所属期'))
        if (!taxPeriodPattern.test(String(value).trim())) {
          return callback(new Error('税款所属期格式不正确'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  taxType: [{ required: true, message: '请选择或输入税种', trigger: 'blur' }],
  taxAmount: [
    { required: true, message: '请输入税额', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value === '' || value === null || value === undefined) {
          return callback(new Error('请输入税额'))
        }
        if (Number.isNaN(Number(value))) {
          return callback(new Error('税额格式不正确'))
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  paymentStatus: [{ required: true, message: '请选择缴纳状态', trigger: 'change' }],
  paymentDate: [
    {
      validator: (_rule, value, callback) => {
        if (drawerForm.paymentStatus === 1 && !value) {
          return callback(new Error('已缴纳状态必须填写缴纳日期'))
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

function resetDrawerForm() {
  drawerForm.taxPeriod = ''
  drawerForm.taxType = ''
  drawerForm.declarationType = ''
  drawerForm.taxAmount = ''
  drawerForm.paymentStatus = 0
  drawerForm.paymentDate = null
  drawerForm.remark = ''
}

function openCreateDrawer() {
  editingId.value = null
  drawerTitle.value = '新增税务记录'
  resetDrawerForm()
  drawerVisible.value = true
}

function openEditDrawer(row: TaxRecordVO) {
  editingId.value = row.id
  drawerTitle.value = '编辑税务记录'
  drawerForm.taxPeriod = row.taxPeriod
  drawerForm.taxType = row.taxType
  drawerForm.declarationType = row.declarationType || ''
  drawerForm.taxAmount = String(row.taxAmount)
  drawerForm.paymentStatus = row.paymentStatus
  drawerForm.paymentDate = row.paymentDate
  drawerForm.remark = row.remark || ''
  drawerVisible.value = true
}

function handleTaxAmountBlur() {
  if (drawerForm.taxAmount !== '' && !Number.isNaN(Number(drawerForm.taxAmount))) {
    drawerForm.taxAmount = Number(drawerForm.taxAmount).toFixed(2)
  }
}

function handlePaymentStatusChange(status: TaxPaymentStatus) {
  if (status !== 1) {
    drawerForm.paymentDate = null
  }
  drawerFormRef.value?.clearValidate('paymentDate')
}

async function submitDrawer() {
  if (!drawerFormRef.value) return
  await drawerFormRef.value.validate()
  drawerSubmitting.value = true
  try {
    const payload: TaxForm = {
      taxPeriod: drawerForm.taxPeriod.trim(),
      taxType: drawerForm.taxType.trim(),
      declarationType: drawerForm.declarationType.trim(),
      taxAmount: Number(drawerForm.taxAmount).toFixed(2),
      paymentStatus: drawerForm.paymentStatus,
      paymentDate: drawerForm.paymentStatus === 1 ? drawerForm.paymentDate : null,
      remark: drawerForm.remark.trim(),
    }

    if (editingId.value) {
      await updateTax(editingId.value, payload)
      ElMessage.success('税务记录更新成功')
    } else {
      await createTax(payload)
      ElMessage.success('税务记录创建成功')
    }

    drawerVisible.value = false
    fetchList()
  } finally {
    drawerSubmitting.value = false
  }
}

async function handleDelete(row: TaxRecordVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${row.taxPeriod} / ${row.taxType}」这条税务记录吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await deleteTax(row.id)
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
      `确定要批量删除选中的 ${selectedRows.value.length} 条税务记录吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    const ids = selectedRows.value.map((item) => item.id)
    await batchDeleteTax(ids)
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
    await importTaxExcel(options.file)
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
    await downloadTaxTemplate()
  } catch (err: unknown) {
    const error = err as { message?: string }
    ElMessage.error(error?.message || '模板下载失败')
  }
}

function handleErrorReportDownload() {
  if (!importErrors.value.length) return
  downloadImportErrorReport(importErrors.value)
}

function formatTaxAmount(row: TaxRecordVO) {
  return `¥${Number(row.taxAmount).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function getTaxAmountClass(amount: number) {
  if (amount < 0) return 'text-tax-refund'
  if (amount > 0) return 'text-tax-payable'
  return 'text-tax-neutral'
}

function getPaymentStatusLabel(status: TaxPaymentStatus) {
  switch (status) {
    case 0:
      return '待缴纳'
    case 1:
      return '已缴纳'
    default:
      return '免征/零申报'
  }
}

function getPaymentStatusTagType(status: TaxPaymentStatus) {
  switch (status) {
    case 0:
      return 'warning'
    case 1:
      return 'info'
    default:
      return 'success'
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="tax-manage">
    <div class="page-header">
      <h2 class="page-title">税务档案</h2>
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
      <el-select
        v-model="filterPaymentStatus"
        placeholder="纳税状态"
        clearable
        style="width: 160px"
        @change="handleFilter"
      >
        <el-option label="待缴纳" :value="0" />
        <el-option label="已缴纳" :value="1" />
        <el-option label="免征/零申报" :value="2" />
      </el-select>
      <el-input
        v-model="filterTaxPeriod"
        placeholder="税款所属期，如 2026-Q1"
        clearable
        :prefix-icon="Search"
        style="width: 220px"
        @keyup.enter="handleFilter"
        @clear="handleFilter"
      />
      <el-input
        v-model="filterTaxType"
        placeholder="税种关键词"
        clearable
        :prefix-icon="Search"
        style="width: 220px"
        @keyup.enter="handleFilter"
        @clear="handleFilter"
      />
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
      <el-table-column prop="taxPeriod" label="税款所属期" width="150" />
      <el-table-column prop="taxType" label="税种" min-width="140" show-overflow-tooltip />
      <el-table-column prop="declarationType" label="申报类型" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.declarationType || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="税额" width="160" align="right">
        <template #default="{ row }">
          <span class="tax-amount-cell" :class="getTaxAmountClass(row.taxAmount)">
            {{ formatTaxAmount(row) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="缴纳状态" width="130" align="center">
        <template #default="{ row }">
          <el-tag :type="getPaymentStatusTagType(row.paymentStatus)" size="small" round>
            {{ getPaymentStatusLabel(row.paymentStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="paymentDate" label="缴纳日期" width="130">
        <template #default="{ row }">
          {{ row.paymentDate || '--' }}
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
        <el-form-item label="税款所属期" prop="taxPeriod">
          <el-input
            v-model="drawerForm.taxPeriod"
            placeholder="请输入 YYYY-MM、YYYY-Q1 或 YYYY-Annual"
          />
        </el-form-item>

        <el-form-item label="税种" prop="taxType">
          <el-select
            v-model="drawerForm.taxType"
            placeholder="请选择或输入税种"
            filterable
            allow-create
            default-first-option
            style="width: 100%"
          >
            <el-option v-for="item in taxTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>

        <el-form-item label="申报类型">
          <el-select
            v-model="drawerForm.declarationType"
            placeholder="请选择申报类型"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in declarationTypeOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="税额" prop="taxAmount">
          <el-input
            v-model="drawerForm.taxAmount"
            placeholder="可输入负数表示退税"
            class="tax-amount-input"
            @blur="handleTaxAmountBlur"
          >
            <template #prefix>¥</template>
          </el-input>
        </el-form-item>

        <el-form-item label="缴纳状态" prop="paymentStatus">
          <el-radio-group v-model="drawerForm.paymentStatus" @change="handlePaymentStatusChange">
            <el-radio :value="0">待缴纳</el-radio>
            <el-radio :value="1">已缴纳</el-radio>
            <el-radio :value="2">免征/零申报</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="缴纳日期" prop="paymentDate">
          <el-date-picker
            v-model="drawerForm.paymentDate"
            type="date"
            placeholder="请选择缴纳日期"
            value-format="YYYY-MM-DD"
            :disabled="drawerForm.paymentStatus !== 1"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input v-model="drawerForm.remark" type="textarea" :rows="3" placeholder="选填" />
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
          accept=".xlsx"
          :auto-upload="true"
          :show-file-list="false"
          :http-request="handleImportUpload"
        >
          <div class="upload-content">
            <el-icon :size="48" color="#a39e98"><Upload /></el-icon>
            <p class="upload-text">把 Excel 文件拖拽到此处，或<em>点击上传</em></p>
            <p class="upload-hint">仅支持 .xlsx 格式，请先下载税务导入模板后再填写</p>
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
.tax-manage {
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

.tax-amount-cell {
  font-weight: 600;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'lnum';
}

.text-tax-payable {
  color: #e03e3e;
}

.text-tax-refund {
  color: #2a9d99;
}

.text-tax-neutral {
  color: #615d59;
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

.tax-amount-input :deep(.el-input__inner) {
  font-size: 24px;
  font-weight: 700;
  text-align: right;
  height: 48px;
}

.tax-amount-input :deep(.el-input__prefix) {
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