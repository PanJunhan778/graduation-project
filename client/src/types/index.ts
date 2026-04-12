/** 统一响应包装体 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
export interface PageResult<T> {
  total: number
  records: T[]
}

/** 登录表单 */
export interface LoginForm {
  username: string
  password: string
}

/** 注册表单 */
export interface RegisterForm {
  username: string
  password: string
  confirmPassword: string
  realName: string
  companyCode: string
}

/** 登录响应 */
export interface LoginVO {
  token: string
  role: 'admin' | 'owner' | 'staff'
  realName: string
  companyName: string | null
  companyCode: string | null
  industry: string | null
  taxpayerType: string | null
}

/** 用户信息（存储在 store 中） */
export interface UserInfo {
  token: string
  role: 'admin' | 'owner' | 'staff'
  realName: string
  companyName: string | null
  companyCode: string | null
  industry: string | null
  taxpayerType: string | null
}

/** 角色引导选项 */
export type RoleGuide = 'admin' | 'owner' | 'staff'

/** 公司 VO（Admin 租户管理列表项） */
export interface CompanyVO {
  id: number
  name: string
  companyCode: string
  industry: string | null
  taxpayerType: string | null
  description: string | null
  status: number
  ownerName: string | null
  ownerUsername: string | null
  createdTime: string
}

/** 新建公司表单 */
export interface CompanyCreateForm {
  name: string
  companyCode: string
  industry: string
  taxpayerType: string
  description: string
}

/** 创建 Owner 表单 */
export interface OwnerCreateForm {
  username: string
  password: string
  realName: string
}

/** 用户 VO（用户管理列表项） */
export interface UserVO {
  id: number
  username: string
  realName: string
  role: 'owner' | 'staff'
  status: number
  createdTime: string
}

/** 创建员工账号表单 */
export interface StaffCreateForm {
  username: string
  password: string
  realName: string
}

/** 重置密码表单 */
export interface ResetPasswordForm {
  newPassword: string
}

/** 侧边栏菜单项 */
export interface MenuItem {
  path: string
  title: string
  icon: string
  roles: string[]
}

/** 财务记录 VO（财务账本列表项） */
export interface FinanceRecordVO {
  id: number
  type: 'income' | 'expense'
  amount: number
  category: string
  project: string | null
  date: string
  remark: string | null
  createdTime: string
}

/** 财务记录表单（新增/编辑） */
export interface FinanceForm {
  type: string
  amount: string
  category: string
  project: string
  date: string
  remark: string
}

/** 员工记录 VO（员工名册列表项） */
export interface EmployeeRecordVO {
  id: number
  name: string
  department: string
  position: string | null
  salary: number
  hireDate: string
  status: number
  remark: string | null
  createdTime: string
}

/** 员工记录表单（新增/编辑） */
export interface EmployeeForm {
  name: string
  department: string
  position: string
  salary: string
  hireDate: string
  status: number
  remark: string
}

/** 税务缴纳状态 */
export type TaxPaymentStatus = 0 | 1 | 2

/** 税务记录 VO（税务档案列表项） */
export interface TaxRecordVO {
  id: number
  taxPeriod: string
  taxType: string
  declarationType: string | null
  taxAmount: number
  paymentStatus: TaxPaymentStatus
  paymentDate: string | null
  remark: string | null
  createdTime: string
}

/** 税务记录表单（新增/编辑） */
export interface TaxForm {
  taxPeriod: string
  taxType: string
  declarationType: string
  taxAmount: string
  paymentStatus: TaxPaymentStatus
  paymentDate: string | null
  remark: string
}

/** Excel 导入错误条目 */
export interface ImportError {
  row: number
  error: string
}

/** 审计模块 */
export type AuditModule = 'finance' | 'employee' | 'tax'

/** 审计操作类型 */
export type AuditOperationType = 'CREATE' | 'UPDATE' | 'DELETE'

/** 审计日志列表项 */
export interface AuditLogVO {
  id: number
  module: AuditModule
  operationType: AuditOperationType
  targetId: number
  fieldName: string
  oldValue: string | null
  newValue: string | null
  operationTime: string
  userId: number
  operatorName: string
}

/** 首页趋势点 */
export interface MonthlyTrendPoint {
  month: string
  income: number
  expense: number
  profit: number
}

/** 首页税务时间轴项 */
export interface TaxCalendarItem {
  taxPeriod: string
  taxType: string
  status: TaxPaymentStatus
  amount: number
}

/** 首页驾驶舱聚合数据 */
export interface HomeDashboardVO {
  totalIncome: number
  totalExpense: number
  netProfit: number
  unpaidTax: number
  hasUnpaidWarning: boolean
  monthlyTrend: MonthlyTrendPoint[]
  taxCalendar: TaxCalendarItem[]
}

/** 数据看板时间范围 */
export type FinanceDashboardRange = 'last3months' | 'last6months' | 'last12months' | 'all'
export type HrDashboardRange = FinanceDashboardRange
export type TaxDashboardRange = 'thisYear' | 'last12months' | 'all'

/** 财务看板 - 支出切片项 */
export interface FinanceExpenseBreakdownItem {
  name: string
  amount: number
  ratio: number
}

/** 财务看板 - 收入来源项 */
export interface TopIncomeSourceItem {
  name: string
  amount: number
}

/** 财务剖析看板 */
export interface FinanceDashboardVO {
  totalExpense: number
  totalIncome: number
  expenseBreakdown: FinanceExpenseBreakdownItem[]
  topIncomeSources: TopIncomeSourceItem[]
}

/** 人事看板 - 部门薪资占比项 */
export interface DepartmentSalaryShareItem {
  department: string
  employeeCount: number
  salaryAmount: number
  ratio: number
}

/** 人事看板 - 月度趋势项 */
export interface HrMonthlyTrendItem {
  month: string
  employeeCount: number
  salaryAmount: number
}

/** 人事洞察看板 */
export interface HrDashboardVO {
  activeEmployeeCount: number
  activeSalaryTotal: number
  departmentSalaryShare: DepartmentSalaryShareItem[]
  monthlyTrend: HrMonthlyTrendItem[]
}

/** 税务看板 - 税种结构项 */
export interface TaxTypeStructureItem {
  taxType: string
  amount: number
  ratio: number
}

/** 税务看板 - 状态汇总项 */
export interface TaxStatusSummaryItem {
  status: TaxPaymentStatus
  count: number
  amount: number
}

/** 税务健康看板 */
export interface TaxDashboardVO {
  taxBurdenRate: number
  positiveTaxAmount: number
  incomeBase: number
  unpaidTaxAmount: number
  taxTypeStructure: TaxTypeStructureItem[]
  statusSummary: TaxStatusSummaryItem[]
}

export type AiMessageType = 'text' | 'markdown' | 'action_required' | 'action_result'
export type AiActorRole = 'user' | 'assistant' | 'system'
export type AiActionStatus = 'pending' | 'approved' | 'rejected' | 'expired'

export interface AiActionMetadata {
  actionId: number
  toolName: string
  oldValue: string
  proposedValue: string
  confirmToken?: string
  status: AiActionStatus
  expiresAt?: string
  processedAt?: string | null
}

export interface AiActionResultMetadata {
  actionId: number
  status: AiActionStatus
}

export interface AiChatRequest {
  sessionId?: string
  message: string
}

export interface AiConfirmActionRequest {
  confirmToken: string
  isApproved: boolean
}

export interface AiConfirmActionVO {
  actionId: number
  status: AiActionStatus
  resultMessage: string
}

export interface AiSessionVO {
  sessionId: string
  title: string
  lastMessagePreview: string
  lastMessageTime: string
}

export interface AiChatMessageVO {
  id: number
  role: AiActorRole
  messageType: AiMessageType
  content: string
  metadata: Record<string, unknown> | null
  createTime: string
}

export interface AiChatTurnVO {
  sessionId: string
  resultType: 'message' | 'action_required'
  messageId?: number
  messageType?: 'markdown'
  content?: string
  actionRequired?: AiActionRequiredPayload
}

export interface AiActionRequiredPayload {
  actionId: number
  toolName: string
  oldValue: string
  proposedValue: string
  confirmToken: string
}
