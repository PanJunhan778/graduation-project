<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { getCompanyList, createCompany, createOwner, updateCompanyStatus } from '@/api/admin'
import type { CompanyVO, CompanyCreateForm, OwnerCreateForm } from '@/types'
import type { FormInstance, FormRules } from 'element-plus'

const loading = ref(false)
const tableData = ref<CompanyVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const keyword = ref('')

async function fetchList() {
  loading.value = true
  try {
    const res = await getCompanyList({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
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

// ---- 新建公司 ----
const companyDialogVisible = ref(false)
const companyFormRef = ref<FormInstance>()
const companySubmitting = ref(false)
const companyForm = reactive<CompanyCreateForm>({
  name: '',
  companyCode: '',
  industry: '',
  taxpayerType: '',
  description: '',
})

const companyRules: FormRules = {
  name: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
  companyCode: [
    { required: true, message: '请输入企业码', trigger: 'blur' },
    { pattern: /^[A-Z0-9]{6}$/, message: '企业码必须为6位大写字母或数字', trigger: 'blur' },
  ],
}

function openCompanyDialog() {
  companyForm.name = ''
  companyForm.companyCode = ''
  companyForm.industry = ''
  companyForm.taxpayerType = ''
  companyForm.description = ''
  companyDialogVisible.value = true
}

async function submitCompany() {
  if (!companyFormRef.value) return
  await companyFormRef.value.validate()
  companySubmitting.value = true
  try {
    await createCompany(companyForm)
    ElMessage.success('公司创建成功')
    companyDialogVisible.value = false
    fetchList()
  } finally {
    companySubmitting.value = false
  }
}

// ---- 创建 Owner ----
const ownerDialogVisible = ref(false)
const ownerFormRef = ref<FormInstance>()
const ownerSubmitting = ref(false)
const ownerTargetCompany = ref<CompanyVO | null>(null)
const ownerForm = reactive<OwnerCreateForm>({
  username: '',
  password: '',
  realName: '',
})

const ownerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d).{8,}$/,
      message: '至少8位，须包含大写字母、小写字母和数字',
      trigger: 'blur',
    },
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
}

function openOwnerDialog(company: CompanyVO) {
  ownerTargetCompany.value = company
  ownerForm.username = ''
  ownerForm.password = ''
  ownerForm.realName = ''
  ownerDialogVisible.value = true
}

async function submitOwner() {
  if (!ownerFormRef.value || !ownerTargetCompany.value) return
  await ownerFormRef.value.validate()
  ownerSubmitting.value = true
  try {
    await createOwner(ownerTargetCompany.value.id, ownerForm)
    ElMessage.success('负责人账号创建成功')
    ownerDialogVisible.value = false
    fetchList()
  } finally {
    ownerSubmitting.value = false
  }
}

// ---- 启禁用 ----
async function handleStatusChange(row: CompanyVO, newStatus: boolean) {
  const statusVal = newStatus ? 1 : 0
  const action = statusVal === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定要${action}「${row.name}」吗？${statusVal === 0 ? '禁用后该企业下所有用户将无法登录。' : ''}`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await updateCompanyStatus(row.id, statusVal)
    ElMessage.success(`已${action}`)
    fetchList()
  } catch {
    fetchList()
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="company-manage">
    <div class="page-header">
      <h2 class="page-title">租户管理</h2>
    </div>

    <div class="action-bar">
      <div class="action-left">
        <el-button type="primary" :icon="Plus" @click="openCompanyDialog">新建公司</el-button>
      </div>
      <div class="action-right">
        <el-input
          v-model="keyword"
          placeholder="搜索公司名称"
          clearable
          :prefix-icon="Search"
          style="width: 240px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </div>
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
    >
      <el-table-column prop="name" label="公司名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="companyCode" label="企业码" width="120" align="center">
        <template #default="{ row }">
          <span class="code-badge">{{ row.companyCode }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="industry" label="行业" width="130" show-overflow-tooltip />
      <el-table-column prop="taxpayerType" label="纳税人类型" width="140" show-overflow-tooltip />
      <el-table-column label="负责人" width="160">
        <template #default="{ row }">
          <template v-if="row.ownerName">
            <span>{{ row.ownerName }}</span>
            <span class="owner-username">（{{ row.ownerUsername }}）</span>
          </template>
          <span v-else class="text-muted">未创建</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" round>
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.ownerName"
            type="primary"
            link
            size="small"
            @click="openOwnerDialog(row)"
          >
            创建负责人
          </el-button>
          <el-switch
            :model-value="row.status === 1"
            inline-prompt
            active-text="启"
            inactive-text="禁"
            @change="(val: boolean) => handleStatusChange(row, val)"
          />
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

    <!-- 新建公司对话框 -->
    <el-dialog v-model="companyDialogVisible" title="新建公司" width="520px" destroy-on-close>
      <el-form
        ref="companyFormRef"
        :model="companyForm"
        :rules="companyRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="公司名称" prop="name">
          <el-input v-model="companyForm.name" placeholder="请输入公司名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="企业码" prop="companyCode">
          <el-input
            v-model="companyForm.companyCode"
            placeholder="6位大写字母或数字，如 A1B2C3"
            maxlength="6"
            @input="companyForm.companyCode = companyForm.companyCode.toUpperCase()"
          />
        </el-form-item>
        <el-form-item label="所属行业">
          <el-input v-model="companyForm.industry" placeholder="请输入所属行业" />
        </el-form-item>
        <el-form-item label="纳税人性质">
          <el-select v-model="companyForm.taxpayerType" placeholder="请选择" style="width: 100%">
            <el-option label="小规模纳税人" value="小规模纳税人" />
            <el-option label="一般纳税人" value="一般纳税人" />
            <el-option label="个体户" value="个体户" />
          </el-select>
        </el-form-item>
        <el-form-item label="公司描述">
          <el-input
            v-model="companyForm.description"
            type="textarea"
            :rows="3"
            placeholder="公司业务描述（将作为 AI 全局上下文）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="companyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="companySubmitting" @click="submitCompany">
          确定创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 创建 Owner 对话框 -->
    <el-dialog
      v-model="ownerDialogVisible"
      :title="`为「${ownerTargetCompany?.name}」创建负责人`"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="ownerFormRef"
        :model="ownerForm"
        :rules="ownerRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="ownerForm.username" placeholder="登录账号（3-50个字符）" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="ownerForm.password"
            type="password"
            show-password
            placeholder="至少8位，含大写、小写和数字"
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="ownerForm.realName" placeholder="负责人真实姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ownerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="ownerSubmitting" @click="submitOwner">
          确定创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.company-manage {
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

.code-badge {
  display: inline-block;
  padding: 2px 8px;
  background: #f2f9ff;
  color: #097fe8;
  font-size: 12px;
  font-weight: 600;
  border-radius: 9999px;
  letter-spacing: 0.5px;
  font-variant-numeric: tabular-nums;
}

.owner-username {
  color: #a39e98;
  font-size: 12px;
}

.text-muted {
  color: #a39e98;
  font-size: 13px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 8px 0;
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

:deep(.el-dialog__header) {
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
  padding-bottom: 16px;
}

:deep(.el-dialog__title) {
  font-size: 18px;
  font-weight: 700;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  padding-top: 16px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: rgba(0, 0, 0, 0.85);
}
</style>
