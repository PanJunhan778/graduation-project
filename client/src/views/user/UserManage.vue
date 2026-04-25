<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import PageTableSkeleton from '@/components/common/PageTableSkeleton.vue'
import { useDelayedLoading } from '@/composables/useDelayedLoading'
import { getUserList, createStaff, updateUserStatus, resetPassword } from '@/api/user'
import type { UserVO, StaffCreateForm, ResetPasswordForm } from '@/types'
import type { FormInstance, FormRules } from 'element-plus'

const loading = ref(false)
const hasLoaded = ref(false)
const tableData = ref<UserVO[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const showInitialSkeleton = useDelayedLoading(() => loading.value && !hasLoaded.value)

async function fetchList() {
  loading.value = true
  try {
    const res = await getUserList({
      page: currentPage.value,
      size: pageSize.value,
      keyword: keyword.value || undefined,
    })
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    hasLoaded.value = true
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

// ---- 创建员工 ----
const staffDialogVisible = ref(false)
const staffFormRef = ref<FormInstance>()
const staffSubmitting = ref(false)
const staffForm = reactive<StaffCreateForm>({
  username: '',
  password: '',
  realName: '',
})

const staffRules: FormRules = {
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

function openStaffDialog() {
  staffForm.username = ''
  staffForm.password = ''
  staffForm.realName = ''
  staffDialogVisible.value = true
}

async function submitStaff() {
  if (!staffFormRef.value) return
  await staffFormRef.value.validate()
  staffSubmitting.value = true
  try {
    await createStaff(staffForm)
    ElMessage.success('员工账号创建成功')
    staffDialogVisible.value = false
    fetchList()
  } finally {
    staffSubmitting.value = false
  }
}

// ---- 启禁用 ----
async function handleStatusChange(row: UserVO, newStatus: boolean) {
  const statusVal = newStatus ? 1 : 0
  const action = statusVal === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(
      `确定要${action}员工「${row.realName || row.username}」的账号吗？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
    await updateUserStatus(row.id, statusVal)
    ElMessage.success(`已${action}`)
    fetchList()
  } catch {
    fetchList()
  }
}

// ---- 重置密码 ----
const resetDialogVisible = ref(false)
const resetFormRef = ref<FormInstance>()
const resetSubmitting = ref(false)
const resetTargetUser = ref<UserVO | null>(null)
const resetForm = reactive<ResetPasswordForm>({
  newPassword: '',
})

const resetRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d).{8,}$/,
      message: '至少8位，须包含大写字母、小写字母和数字',
      trigger: 'blur',
    },
  ],
}

function openResetDialog(user: UserVO) {
  resetTargetUser.value = user
  resetForm.newPassword = ''
  resetDialogVisible.value = true
}

async function submitReset() {
  if (!resetFormRef.value || !resetTargetUser.value) return
  await resetFormRef.value.validate()
  resetSubmitting.value = true
  try {
    await resetPassword(resetTargetUser.value.id, resetForm)
    ElMessage.success('密码重置成功')
    resetDialogVisible.value = false
  } finally {
    resetSubmitting.value = false
  }
}

function getRoleLabel(role: string) {
  return role === 'owner' ? '负责人' : '员工'
}

function getRoleType(role: string) {
  return role === 'owner' ? '' : 'info'
}

onMounted(fetchList)
</script>

<template>
  <PageTableSkeleton
    v-if="showInitialSkeleton"
    title="用户管理"
    :action-count="1"
    :filter-count="0"
    :row-count="7"
  />
  <div v-else class="crud-page">
    <div class="crud-card">
      <div class="crud-page-header">
        <div>
          <h2 class="crud-page-title">用户管理</h2>
          <p class="crud-page-subtitle">管理系统账户及分配对应的系统权限。</p>
        </div>
      </div>

      <div class="crud-toolbar">
        <div class="crud-toolbar-left">
          <span class="guide-inline-anchor" data-guide="owner-user-create">
            <el-button type="primary" :icon="Plus" @click="openStaffDialog">创建员工账号</el-button>
          </span>
        </div>
        <div class="crud-toolbar-right">
          <el-input
            v-model="keyword"
            placeholder="搜索用户名或姓名"
            clearable
            :prefix-icon="Search"
            style="width: 240px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </div>
      </div>

      <div class="crud-table-section">
        <el-table
          :data="tableData"
          stripe
          style="width: 100%"
        >
      <el-table-column prop="username" label="用户名" min-width="150" show-overflow-tooltip />
      <el-table-column prop="realName" label="真实姓名" min-width="130" show-overflow-tooltip />
      <el-table-column label="角色" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="getRoleType(row.role)" size="small" round>
            {{ getRoleLabel(row.role) }}
          </el-tag>
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
          <template v-if="row.role === 'staff'">
            <el-button type="primary" link size="small" @click="openResetDialog(row)">
              重置密码
            </el-button>
            <el-switch
              :model-value="row.status === 1"
              inline-prompt
              active-text="启"
              inactive-text="禁"
              @change="(val: boolean) => handleStatusChange(row, val)"
            />
          </template>
          <span v-else class="text-muted">--</span>
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

    <!-- 创建员工对话框 -->
    <el-dialog v-model="staffDialogVisible" title="创建员工账号" width="480px" destroy-on-close>
      <el-form
        ref="staffFormRef"
        :model="staffForm"
        :rules="staffRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="staffForm.username" placeholder="登录账号（3-50个字符）" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="staffForm.password"
            type="password"
            show-password
            placeholder="至少8位，含大写、小写和数字"
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="staffForm.realName" placeholder="员工真实姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="staffDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="staffSubmitting" @click="submitStaff">
          确定创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog
      v-model="resetDialogVisible"
      :title="`重置「${resetTargetUser?.realName || resetTargetUser?.username}」的密码`"
      width="440px"
      destroy-on-close
    >
      <el-form
        ref="resetFormRef"
        :model="resetForm"
        :rules="resetRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="resetForm.newPassword"
            type="password"
            show-password
            placeholder="至少8位，含大写、小写和数字"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="submitReset">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.crud-page-header {
  margin-bottom: 16px;
}

.guide-inline-anchor {
  display: inline-flex;
  align-items: center;
}

.text-muted {
  color: #a39e98;
  font-size: 13px;
}
</style>
