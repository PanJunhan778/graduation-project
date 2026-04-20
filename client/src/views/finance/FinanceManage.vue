<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { Plus, Upload, Delete, Edit, Warning, Loading, RefreshLeft } from '@element-plus/icons-vue'
import FinanceOnboardingGuide from '@/components/common/FinanceOnboardingGuide.vue'
import PageTableSkeleton from '@/components/common/PageTableSkeleton.vue'
import RecycleBinDrawer from '@/components/common/RecycleBinDrawer.vue'
import { useDelayedLoading } from '@/composables/useDelayedLoading'
import {
  getFinanceList,
  getFinanceRecycleBinList,
  createFinance,
  updateFinance,
  deleteFinance,
  batchDeleteFinance,
  restoreFinance,
  batchRestoreFinance,
  importFinanceExcel,
  downloadFinanceTemplate,
} from '@/api/finance'
import { getEmployeeList } from '@/api/employee'
import { getTaxList } from '@/api/tax'
import { useUserStore } from '@/store/user'
import type { FinanceRecordVO, FinanceRecycleBinVO, FinanceForm, ImportError } from '@/types'
import type { FormInstance, FormRules } from 'element-plus'

const userStore = useUserStore()
const loading = ref(false)
const hasLoaded = ref(false)
const tableData = ref<FinanceRecordVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const selectedRows = ref<FinanceRecordVO[]>([])
const recycleBinVisible = ref(false)
const recycleBinLoading = ref(false)
const recycleBinTableData = ref<FinanceRecycleBinVO[]>([])
const recycleBinTotal = ref(0)
const recycleBinCurrentPage = ref(1)
const recycleBinPageSize = ref(10)
const recycleBinSelectedRows = ref<FinanceRecycleBinVO[]>([])
const showInitialSkeleton = useDelayedLoading(() => loading.value && !hasLoaded.value)
const actionBarRef = ref<HTMLElement | null>(null)
const filterBarRef = ref<HTMLElement | null>(null)
const tableSectionRef = ref<HTMLElement | null>(null)
const staffGuideVisible = ref(false)
const staffGuideBootstrapped = ref(false)

// ---- 筛选 ----
const filterType = ref('')
const filterCategory = ref('')
const filterDateRange = ref<[string, string] | null>(null)

const categoryOptions = [
  '销售收入', '服务收入', '投资收益', '其他收入',
  '采购支出', '人工成本', '房租水电', '营销推广', '办公费用', '差旅费用', '税费支出', '其他支出',
]

const staffGuideSteps = computed(() => [
  {
    title: '欢迎使用该系统',
    description: '接下来会带你快速认识数据录入端最常用的录入与查询功能。\n如果你已经熟悉这套页面，也可以直接点击跳过。',
    placement: 'center' as const,
    primaryText: '开始了解',
    width: 420,
  },
  {
    title: '左侧可以切换三类业务数据',
    description: '这里可以在财务账本、税务档案和员工名册之间切换。三个页面的录入逻辑和布局是一致的，先学会财务页，其他两页也会很好上手。',
    target: getGuideElement('staff-guide-sidebar-menu'),
    placement: 'right' as const,
    width: 400,
  },
  {
    title: '先认识这一排基础操作',
    description: '这里提供单笔新增、Excel 批量导入和导入模板下载。建议先下载模板确认字段，再决定逐条录入还是批量导入。',
    target: actionBarRef.value,
    placement: 'bottom' as const,
    width: 400,
  },
  {
    title: '筛选和查询都在这里完成',
    description: '可以按收支类型、财务分类和日期范围快速筛选，方便你只查看当前要处理的数据。',
    target: filterBarRef.value,
    placement: 'bottom' as const,
    width: 380,
  },
  {
    title: '数据录入后会显示在这里',
    description: '表格会集中展示记录内容，你可以继续查看、分页、编辑、删除，或者做批量删除等日常维护操作。',
    target: tableSectionRef.value,
    placement: 'top' as const,
    width: 400,
  },
])

function getGuideElement(id: string) {
  if (typeof document === 'undefined') return null
  return document.getElementById(id)
}

async function fetchList() {
  loading.value = true
  let financeTotalForGuide: number | null = null
  try {
    const params: Record<string, unknown> = {
      page: currentPage.value,
      size: pageSize.value,
    }
    if (filterType.value) params.type = filterType.value
    if (filterCategory.value) params.category = filterCategory.value
    if (filterDateRange.value) {
      params.startDate = filterDateRange.value[0]
      params.endDate = filterDateRange.value[1]
    }
    const res = await getFinanceList(params as Parameters<typeof getFinanceList>[0])
    tableData.value = res.data.records
    total.value = res.data.total
    financeTotalForGuide = res.data.total
  } finally {
    hasLoaded.value = true
    loading.value = false
    if (!staffGuideBootstrapped.value && financeTotalForGuide !== null) {
      await nextTick()
      void bootstrapStaffGuide(financeTotalForGuide)
    }
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

function handleSelectionChange(rows: FinanceRecordVO[]) {
  selectedRows.value = rows
}

async function fetchRecycleBinList() {
  recycleBinLoading.value = true
  try {
    const res = await getFinanceRecycleBinList({
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

function openRecycleBinDrawer() {
  recycleBinVisible.value = true
  recycleBinCurrentPage.value = 1
  recycleBinSelectedRows.value = []
  void fetchRecycleBinList()
}

function handleRecycleBinSelectionChange(rows: FinanceRecycleBinVO[]) {
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

// ---- 抽屉：新增/编辑 ----
const drawerVisible = ref(false)
const drawerTitle = ref('新增记录')
const drawerFormRef = ref<FormInstance>()
const drawerSubmitting = ref(false)
const editingId = ref<number | null>(null)
const drawerForm = reactive<FinanceForm>({
  type: 'expense',
  amount: '',
  category: '',
  project: '',
  date: '',
  remark: '',
})

const drawerRules: FormRules = {
  type: [{ required: true, message: '请选择收支类型', trigger: 'change' }],
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback(new Error('请输入金额'))
        const num = Number(value)
        if (isNaN(num) || num <= 0) return callback(new Error('金额必须大于0'))
        callback()
      },
      trigger: 'blur',
    },
  ],
  category: [{ required: true, message: '请选择或输入财务分类', trigger: 'blur' }],
  date: [{ required: true, message: '请选择发生日期', trigger: 'change' }],
}

function openCreateDrawer() {
  editingId.value = null
  drawerTitle.value = '新增记录'
  drawerForm.type = 'expense'
  drawerForm.amount = ''
  drawerForm.category = ''
  drawerForm.project = ''
  drawerForm.date = ''
  drawerForm.remark = ''
  drawerVisible.value = true
}

function openEditDrawer(row: FinanceRecordVO) {
  editingId.value = row.id
  drawerTitle.value = '编辑记录'
  drawerForm.type = row.type
  drawerForm.amount = String(row.amount)
  drawerForm.category = row.category
  drawerForm.project = row.project || ''
  drawerForm.date = row.date
  drawerForm.remark = row.remark || ''
  drawerVisible.value = true
}

function handleAmountBlur() {
  if (drawerForm.amount && !isNaN(Number(drawerForm.amount))) {
    drawerForm.amount = Number(drawerForm.amount).toFixed(2)
  }
}

async function submitDrawer() {
  if (!drawerFormRef.value) return
  await drawerFormRef.value.validate()
  drawerSubmitting.value = true
  try {
    const payload: FinanceForm = {
      ...drawerForm,
      amount: Number(drawerForm.amount).toFixed(2),
    }
    if (editingId.value) {
      await updateFinance(editingId.value, payload)
      ElMessage.success('记录更新成功')
    } else {
      await createFinance(payload)
      ElMessage.success('记录创建成功')
    }
    drawerVisible.value = false
    fetchList()
  } finally {
    drawerSubmitting.value = false
  }
}

// ---- 删除 ----
async function handleDelete(row: FinanceRecordVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除这条${row.type === 'income' ? '收入' : '支出'}记录吗？（金额：¥${row.amount}）`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await deleteFinance(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // cancelled
  }
}

// ---- 批量删除 ----
const hasBatchSelection = computed(() => selectedRows.value.length > 0)
const hasRecycleBatchSelection = computed(() => recycleBinSelectedRows.value.length > 0)

async function handleBatchDelete() {
  if (!selectedRows.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定要批量删除选中的 ${selectedRows.value.length} 条记录吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    const ids = selectedRows.value.map((r) => r.id)
    await batchDeleteFinance(ids)
    ElMessage.success('批量删除成功')
    selectedRows.value = []
    fetchList()
  } catch {
    // cancelled
  }
}

async function handleRestoreFromRecycleBin(row: FinanceRecycleBinVO) {
  try {
    await ElMessageBox.confirm(
      `确定要恢复这条${row.type === 'income' ? '收入' : '支出'}记录吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await restoreFinance(row.id)
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
      `确定要恢复选中的 ${recycleBinSelectedRows.value.length} 条财务记录吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    const ids = recycleBinSelectedRows.value.map((item) => item.id)
    const res = await batchRestoreFinance(ids)
    recycleBinSelectedRows.value = []
    await Promise.all([fetchRecycleBinList(), fetchList()])
    ElMessage.success(`已恢复 ${res.data || ids.length} 条记录`)
  } catch {
    // cancelled
  }
}

// ---- Excel 导入 ----
const importDialogVisible = ref(false)
const importLoading = ref(false)
const importErrors = ref<ImportError[]>([])
const importHasError = ref(false)

async function bootstrapStaffGuide(financeTotal: number) {
  staffGuideBootstrapped.value = true

  if (userStore.role !== 'staff' || financeTotal !== 0 || userStore.hasSeenStaffFinanceGuideInCurrentSession()) {
    return
  }

  try {
    const [employeeRes, taxRes] = await Promise.all([
      getEmployeeList({ page: 1, size: 1 }),
      getTaxList({ page: 1, size: 1 }),
    ])

    if (employeeRes.data.total !== 0 || taxRes.data.total !== 0) {
      return
    }

    await nextTick()
    staffGuideVisible.value = true
  } catch (error) {
    console.error('Failed to start staff finance onboarding guide.', error)
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
    await importFinanceExcel(options.file)
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
    await downloadFinanceTemplate()
  } catch (err: unknown) {
    const error = err as { message?: string }
    ElMessage.error(error?.message || '模板下载失败')
  }
}

// ---- 工具 ----
function formatAmount(row: FinanceRecordVO) {
  return `¥${Number(row.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function getTypeLabel(type: string) {
  return type === 'income' ? '收入' : '支出'
}

function getTypeTagType(type: string) {
  return type === 'income' ? 'success' : 'danger'
}

function dismissStaffGuide() {
  userStore.markStaffFinanceGuideSeen()
  staffGuideVisible.value = false
}

onMounted(fetchList)
</script>

<template>
  <PageTableSkeleton
    v-if="showInitialSkeleton"
    title="财务账本"
    :action-count="userStore.isOwner ? 3 : 2"
    :filter-count="3"
    :row-count="8"
  />
  <div v-else class="finance-manage">
    <div class="page-header">
      <h2 class="page-title">财务账本</h2>
    </div>

    <!-- 操作栏 -->
    <div ref="actionBarRef" class="action-bar">
      <div class="action-left">
        <el-button type="primary" :icon="Plus" @click="openCreateDrawer">单笔新增</el-button>
        <el-button :icon="Upload" @click="openImportDialog">Excel 批量导入</el-button>
        <el-button v-if="userStore.isOwner" :icon="RefreshLeft" @click="openRecycleBinDrawer">回收站</el-button>
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

    <!-- 筛选栏 -->
    <div ref="filterBarRef" class="filter-bar">
      <el-select
        v-model="filterType"
        placeholder="收支类型"
        clearable
        style="width: 140px"
        @change="handleFilter"
      >
        <el-option label="收入" value="income" />
        <el-option label="支出" value="expense" />
      </el-select>
      <el-select
        v-model="filterCategory"
        placeholder="财务分类"
        clearable
        filterable
        style="width: 180px"
        @change="handleFilter"
      >
        <el-option v-for="cat in categoryOptions" :key="cat" :label="cat" :value="cat" />
      </el-select>
      <el-date-picker
        v-model="filterDateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        style="width: 280px"
        @change="handleFilter"
      />
    </div>

    <!-- 数据表格 -->
    <div ref="tableSectionRef" class="table-section">
      <el-table
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
        <el-table-column label="收支类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.type)" size="small" round>
              {{ getTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="160" align="right">
          <template #default="{ row }">
            <span
              class="amount-cell"
              :class="row.type === 'income' ? 'text-income' : 'text-expense'"
            >
              {{ formatAmount(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="财务分类" min-width="130" show-overflow-tooltip />
        <el-table-column prop="project" label="关联项目" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.project || '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="date" label="发生日期" width="130" />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
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
    </div>

    <!-- 右侧抽屉：新增/编辑 -->
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
        <el-form-item label="收支类型" prop="type">
          <el-radio-group v-model="drawerForm.type">
            <el-radio value="income">收入</el-radio>
            <el-radio value="expense">支出</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="金额" prop="amount">
          <el-input
            v-model="drawerForm.amount"
            placeholder="请输入金额"
            class="amount-input"
            @blur="handleAmountBlur"
          >
            <template #prefix>¥</template>
          </el-input>
        </el-form-item>

        <el-form-item label="财务分类" prop="category">
          <el-select
            v-model="drawerForm.category"
            placeholder="请选择或输入分类"
            filterable
            allow-create
            style="width: 100%"
          >
            <el-option v-for="cat in categoryOptions" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>

        <el-form-item label="关联项目">
          <el-input v-model="drawerForm.project" placeholder="选填，如：XX项目" />
        </el-form-item>

        <el-form-item label="发生日期" prop="date">
          <el-date-picker
            v-model="drawerForm.date"
            type="date"
            placeholder="请选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
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

    <!-- Excel 导入弹窗 -->
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
          <el-button @click="importHasError = false">重新上传</el-button>
          <el-button @click="importDialogVisible = false">关闭</el-button>
        </div>
      </div>

      <div v-if="importLoading" class="import-loading-mask">
        <el-icon class="is-loading" :size="32" color="#0075de"><Loading /></el-icon>
        <span>正在导入...</span>
      </div>
    </el-dialog>
    <FinanceOnboardingGuide
      :visible="staffGuideVisible"
      :steps="staffGuideSteps"
      @skip="dismissStaffGuide"
      @finish="dismissStaffGuide"
    />
    <RecycleBinDrawer
      v-if="userStore.isOwner"
      v-model="recycleBinVisible"
      title="财务回收站"
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
        <el-table-column label="收支类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.type)" size="small" round>
              {{ getTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="160" align="right">
          <template #default="{ row }">
            <span
              class="amount-cell"
              :class="row.type === 'income' ? 'text-income' : 'text-expense'"
            >
              {{ formatAmount(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="财务分类" min-width="140" show-overflow-tooltip />
        <el-table-column prop="project" label="关联项目" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.project || '--' }}
          </template>
        </el-table-column>
        <el-table-column prop="date" label="发生日期" width="130" />
      </template>
    </RecycleBinDrawer>
  </div>
</template>

<style scoped>
.finance-manage {
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

/* 操作栏 */
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

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

/* 金额单元格 */
.amount-cell {
  font-weight: 600;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'lnum';
}

/* 分页器 */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 8px 0;
}

/* 抽屉表单 */
.drawer-form {
  padding: 0 4px;
}

.amount-input :deep(.el-input__inner) {
  font-size: 24px;
  font-weight: 700;
  text-align: right;
  height: 48px;
}

.amount-input :deep(.el-input__prefix) {
  font-size: 24px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.45);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 导入弹窗 */
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

/* 表格样式覆盖 */
:deep(.el-table) {
  --el-table-border-color: rgba(0, 0, 0, 0.06);
  --el-table-row-hover-bg-color: rgba(0, 117, 222, 0.03);
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
}

/* 抽屉样式覆盖 */
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

/* 导入弹窗样式覆盖 */
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

