<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import PageTableSkeleton from '@/components/common/PageTableSkeleton.vue'
import { useDelayedLoading } from '@/composables/useDelayedLoading'
import { getAuditLogList } from '@/api/audit'
import type {
  AuditFieldChangeVO,
  AuditModule,
  AuditOperationType,
  AuditOperationVO,
} from '@/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const hasLoaded = ref(false)
const tableData = ref<AuditOperationVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const showInitialSkeleton = useDelayedLoading(() => loading.value && !hasLoaded.value)

const filters = reactive<{
  module: AuditModule | ''
  operationType: AuditOperationType | ''
  dateRange: [string, string] | null
}>({
  module: '',
  operationType: '',
  dateRange: null,
})

const moduleOptions: Array<{ label: string; value: AuditModule }> = [
  { label: '财务账本', value: 'finance' },
  { label: '员工名册', value: 'employee' },
  { label: '税务档案', value: 'tax' },
]

const operationTypeOptions: Array<{ label: string; value: AuditOperationType }> = [
  { label: '新增', value: 'CREATE' },
  { label: '编辑', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' },
  { label: '恢复', value: 'RESTORE' },
]

const pageSubtitle = computed(() => {
  if (!hasLoaded.value) {
    return '默认展示最近 7 天的新增、编辑、删除和恢复日志；查询行为不会被记录。'
  }
  if (!filters.module && !filters.operationType && !filters.dateRange) {
    return '当前展示全部审计日志，并按一次操作聚合展示字段变化。'
  }
  return '支持按模块、操作类型和日期范围筛选当前公司的增删改恢复日志。'
})

async function fetchList() {
  loading.value = true
  try {
    const params: Parameters<typeof getAuditLogList>[0] = {
      page: currentPage.value,
      size: pageSize.value,
    }

    if (filters.module) {
      params.module = filters.module
    }
    if (filters.operationType) {
      params.operationType = filters.operationType
    }
    if (filters.dateRange) {
      params.startDate = filters.dateRange[0]
      params.endDate = filters.dateRange[1]
    }

    const res = await getAuditLogList(params)
    tableData.value = res.data.records
    total.value = res.data.total
    hasLoaded.value = true
    syncQuery()
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  void fetchList()
}

function handleReset() {
  filters.module = ''
  filters.operationType = ''
  filters.dateRange = null
  currentPage.value = 1
  pageSize.value = 20
  tableData.value = []
  total.value = 0
  hasLoaded.value = false
  router.replace({ path: '/audit', query: {} })
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

function syncQuery() {
  const query: Record<string, string> = {}
  if (filters.module) {
    query.module = filters.module
  }
  if (filters.operationType) {
    query.operationType = filters.operationType
  }
  if (filters.dateRange) {
    query.startDate = filters.dateRange[0]
    query.endDate = filters.dateRange[1]
  }
  router.replace({ path: '/audit', query })
}

function initFromRouteQuery() {
  const module = typeof route.query.module === 'string' ? route.query.module : ''
  const operationType = typeof route.query.operationType === 'string' ? route.query.operationType : ''
  const startDate = typeof route.query.startDate === 'string' ? route.query.startDate : ''
  const endDate = typeof route.query.endDate === 'string' ? route.query.endDate : ''

  if (module === 'finance' || module === 'employee' || module === 'tax') {
    filters.module = module
  }
  if (
    operationType === 'CREATE'
    || operationType === 'UPDATE'
    || operationType === 'DELETE'
    || operationType === 'RESTORE'
  ) {
    filters.operationType = operationType
  }

  if (startDate && endDate) {
    filters.dateRange = [startDate, endDate]
  } else if (!module && !operationType && !startDate && !endDate) {
    filters.dateRange = getDefaultDateRange()
  }

  void fetchList()
}

function getDefaultDateRange(): [string, string] {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - 6)
  return [formatDate(start), formatDate(end)]
}

function formatDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getModuleLabel(module: AuditModule) {
  const map: Record<AuditModule, string> = {
    finance: '财务账本',
    employee: '员工名册',
    tax: '税务档案',
  }
  return map[module]
}

function getOperationTypeLabel(operationType: AuditOperationType) {
  const map: Record<AuditOperationType, string> = {
    CREATE: '新增',
    UPDATE: '编辑',
    DELETE: '删除',
    RESTORE: '恢复',
  }
  return map[operationType]
}

function getOperationTypeTag(operationType: AuditOperationType) {
  const map: Record<AuditOperationType, '' | 'success' | 'warning' | 'danger' | 'primary'> = {
    CREATE: 'success',
    UPDATE: 'warning',
    DELETE: 'danger',
    RESTORE: 'primary',
  }
  return map[operationType]
}

function formatFieldLabel(fieldName: string) {
  const fieldMap: Record<string, string> = {
    type: '收支类型',
    amount: '金额',
    category: '财务分类',
    project: '关联项目',
    date: '发生日期',
    remark: '备注',
    name: '姓名',
    department: '所属部门',
    position: '职位',
    salary: '基础薪资',
    hireDate: '入职日期',
    status: '在职状态',
    taxPeriod: '所属期',
    taxType: '税种',
    declarationType: '申报类型',
    taxAmount: '税额',
    paymentStatus: '缴纳状态',
    paymentDate: '缴纳日期',
  }
  return fieldMap[fieldName] || fieldName
}

function formatChangeSummary(changes: AuditFieldChangeVO[]) {
  if (!changes.length) {
    return '--'
  }

  const labels = [...new Set(changes.map((change) => formatFieldLabel(change.fieldName)))]
  if (labels.length <= 3) {
    return labels.join('、')
  }
  return `${labels.slice(0, 3).join('、')} 等 ${labels.length} 项`
}

function displayValue(value: string | null) {
  return value?.trim() ? value : '--'
}

onMounted(initFromRouteQuery)
</script>

<template>
  <PageTableSkeleton
    v-if="showInitialSkeleton"
    title="审计日志"
    subtitle="默认展示最近 7 天的新增、编辑、删除和恢复日志；查询行为不会被记录。"
    :action-count="2"
    :filter-count="3"
    :show-search="false"
    :row-count="8"
  />
  <div v-else class="audit-manage">
    <div class="page-header">
      <div>
        <h2 class="page-title">审计日志</h2>
        <p class="page-subtitle">{{ pageSubtitle }}</p>
      </div>
    </div>

    <div class="filter-panel">
      <div class="filter-row">
        <el-select
          v-model="filters.module"
          placeholder="全部模块"
          clearable
          style="width: 180px"
        >
          <el-option
            v-for="option in moduleOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          range-separator="至"
          value-format="YYYY-MM-DD"
          clearable
          style="width: 320px"
        />
        <el-select
          v-model="filters.operationType"
          placeholder="全部操作类型"
          clearable
          style="width: 180px"
        >
          <el-option
            v-for="option in operationTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
      </div>
      <p class="filter-tip">每一行代表一次操作，展开后可查看该次操作下的所有字段差异。</p>
    </div>

    <div v-if="!hasLoaded && !loading" class="empty-wrapper">
      <el-empty description="已清空筛选条件，点击查询可查看全部日志。" />
    </div>

    <template v-else>
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
        :row-style="{ height: '52px' }"
      >
        <el-table-column type="expand" width="56">
          <template #default="{ row }">
            <div class="expand-panel">
              <div class="expand-header">
                <span class="expand-title">字段变化明细</span>
                <span class="expand-count">{{ row.changeCount }} 项变化</span>
              </div>
              <el-table :data="row.changes" size="small" border class="change-table">
                <el-table-column label="变更字段" min-width="140">
                  <template #default="{ row: change }">
                    {{ formatFieldLabel(change.fieldName) }}
                  </template>
                </el-table-column>
                <el-table-column label="修改前" min-width="220" show-overflow-tooltip>
                  <template #default="{ row: change }">
                    <span class="value-before">{{ displayValue(change.oldValue) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="修改后" min-width="220" show-overflow-tooltip>
                  <template #default="{ row: change }">
                    <span class="value-after">{{ displayValue(change.newValue) }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="operationTime" label="操作时间" width="180" />
        <el-table-column prop="operatorName" label="操作人" width="140" show-overflow-tooltip />
        <el-table-column label="模块" width="120">
          <template #default="{ row }">
            <el-tag effect="plain" round>{{ getModuleLabel(row.module) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" width="110">
          <template #default="{ row }">
            <el-tag :type="getOperationTypeTag(row.operationType)" effect="light" round>
              {{ getOperationTypeLabel(row.operationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标记录 ID" width="120" />
        <el-table-column label="变更项数" width="110" align="center">
          <template #default="{ row }">
            <span class="change-count">{{ row.changeCount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="字段摘要" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatChangeSummary(row.changes) }}
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
    </template>
  </div>
</template>

<style scoped>
.audit-manage {
  padding: 0 4px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.95);
  letter-spacing: -0.25px;
}

.page-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #7a746d;
}

.filter-panel {
  padding: 16px 18px;
  margin-bottom: 16px;
  background: #ffffff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 10px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #7a746d;
}

.empty-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  background: #ffffff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 10px;
}

.expand-panel {
  padding: 12px 18px 18px;
  background: linear-gradient(180deg, rgba(15, 118, 110, 0.04), rgba(15, 118, 110, 0.01));
}

.expand-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.expand-title {
  font-size: 14px;
  font-weight: 600;
  color: #383430;
}

.expand-count,
.change-count {
  font-weight: 600;
  color: #0f766e;
}

.change-table {
  width: 100%;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 8px 0;
}

.value-before,
.value-after {
  display: inline-block;
  font-variant-numeric: tabular-nums;
  word-break: break-word;
}

.value-before {
  color: #7a746d;
}

.value-after {
  color: #0f766e;
  font-weight: 600;
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

:deep(.el-table__expanded-cell) {
  padding: 0 !important;
}

:deep(.change-table .el-table__cell) {
  background: rgba(255, 255, 255, 0.9);
}
</style>
