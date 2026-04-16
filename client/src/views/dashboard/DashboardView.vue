<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useUserStore } from '@/store/user'
import { useDelayedLoading } from '@/composables/useDelayedLoading'
import {
  getFinanceDashboard,
  getHrDashboard,
  getTaxDashboard,
} from '@/api/dashboard'
import { exportSectionsToPdf, formatPdfTimestamp } from '@/utils/pdf'
import type {
  FinanceDashboardRange,
  FinanceDashboardVO,
  HrDashboardRange,
  HrDashboardVO,
  TaxDashboardRange,
  TaxDashboardVO,
  TaxPaymentStatus,
} from '@/types'
import { Download, RefreshRight } from '@element-plus/icons-vue'
import { use, init, type EChartsType } from 'echarts/core'
import { BarChart, GaugeChart, LineChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  MarkLineComponent,
  MarkPointComponent,
  ToolboxComponent,
} from 'echarts/components'

use([
  BarChart,
  GaugeChart,
  LineChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  DataZoomComponent,
  MarkLineComponent,
  MarkPointComponent,
  ToolboxComponent,
  CanvasRenderer,
])

type AnalyticsTab = 'finance' | 'hr' | 'tax'
type TaxDashboardSelectableRange = TaxDashboardRange | ''
type FinanceTrendViewMode = 'mixed' | 'line' | 'bar'

const userStore = useUserStore()

const dashboardRootRef = ref<HTMLDivElement | null>(null)
const activeTab = ref<AnalyticsTab>('finance')
const exporting = ref(false)
const showExportStage = ref(false)
const financeTrendMode = ref<FinanceTrendViewMode>('mixed')

const financeExpenseChartRef = ref<HTMLDivElement | null>(null)
const financeIncomeChartRef = ref<HTMLDivElement | null>(null)
const financeTrendChartRef = ref<HTMLDivElement | null>(null)
const hrDepartmentChartRef = ref<HTMLDivElement | null>(null)
const hrTrendChartRef = ref<HTMLDivElement | null>(null)
const taxGaugeChartRef = ref<HTMLDivElement | null>(null)
const taxTypeChartRef = ref<HTMLDivElement | null>(null)
const dashboardExportPageOneRef = ref<HTMLDivElement | null>(null)
const dashboardExportPageTwoRef = ref<HTMLDivElement | null>(null)

const financeState = reactive({
  loaded: false,
  loading: false,
  error: '',
  range: 'last6months' as FinanceDashboardRange,
  data: null as FinanceDashboardVO | null,
})

const hrState = reactive({
  loaded: false,
  loading: false,
  error: '',
  range: 'all' as HrDashboardRange,
  data: null as HrDashboardVO | null,
})

const taxState = reactive({
  loaded: false,
  loading: false,
  error: '',
  range: '' as TaxDashboardSelectableRange,
  data: null as TaxDashboardVO | null,
})

const financeRangeOptions: Array<{ label: string; value: FinanceDashboardRange }> = [
  { label: '近 3 个月', value: 'last3months' },
  { label: '近 6 个月', value: 'last6months' },
  { label: '近 12 个月', value: 'last12months' },
  { label: '全部历史', value: 'all' },
]

const financeTrendModeOptions: Array<{ label: string; value: FinanceTrendViewMode }> = [
  { label: '混合', value: 'mixed' },
  { label: '折线', value: 'line' },
  { label: '柱状', value: 'bar' },
]

const taxRangeOptions = computed<Array<{ label: string; value: TaxDashboardRange }>>(() => {
  const availableYears = taxState.data?.availableYears || []
  if (availableYears.length) {
    return availableYears.map((year) => ({
      label: formatTaxRangeLabel(year),
      value: String(year) as TaxDashboardRange,
    }))
  }

  const fallbackYear = parseTaxRangeYear(taxState.range)
  if (!fallbackYear) return []
  return [
    {
      label: formatTaxRangeLabel(fallbackYear),
      value: String(fallbackYear) as TaxDashboardRange,
    },
  ]
})

const tabLabelMap: Record<AnalyticsTab, string> = {
  finance: '财务剖析',
  hr: '人事洞察',
  tax: '税务健康',
}

const financeRangeLabelMap: Record<FinanceDashboardRange, string> = {
  last3months: '近 3 个月',
  last6months: '近 6 个月',
  last12months: '近 12 个月',
  all: '全部历史',
}

const chartMap = new Map<string, EChartsType>()
let resizeObserver: ResizeObserver | null = null
const exportChartImages = reactive({
  financeExpense: '',
  financeIncome: '',
  financeTrend: '',
  hrDepartment: '',
  hrTrend: '',
  taxGauge: '',
  taxType: '',
})

const companyLabel = computed(() => userStore.companyName || '当前企业')
const activeTabLabel = computed(() => tabLabelMap[activeTab.value])
const activeRangeLabel = computed(() => {
  if (activeTab.value === 'finance') return financeRangeLabelMap[financeState.range]
  if (activeTab.value === 'hr') return '当前在职口径'
  return formatTaxRangeLabel(taxState.data?.selectedYear ?? parseTaxRangeYear(taxState.range))
})
const currentTabLoaded = computed(() => {
  if (activeTab.value === 'finance') return financeState.loaded || Boolean(financeState.data)
  if (activeTab.value === 'hr') return hrState.loaded || Boolean(hrState.data)
  return taxState.loaded || Boolean(taxState.data)
})
const currentTabLoading = computed(() => {
  if (activeTab.value === 'finance') return financeState.loading
  if (activeTab.value === 'hr') return hrState.loading
  return taxState.loading
})
const showCurrentLoadingSkeleton = useDelayedLoading(() => currentTabLoading.value && !currentTabLoaded.value)
const canExportDashboard = computed(() =>
  currentTabLoaded.value &&
  !currentTabLoading.value &&
  !exporting.value,
)

const financeHasData = computed(() => {
  const data = financeState.data
  if (!data) return false
  return (
    toNumber(data.totalIncome) > 0 ||
    toNumber(data.totalExpense) > 0 ||
    data.expenseBreakdown.length > 0 ||
    data.topIncomeSources.length > 0
  )
})

const hrHasData = computed(() => {
  const data = hrState.data
  if (!data) return false
  return (
    toNumber(data.activeEmployeeCount) > 0 ||
    toNumber(data.activeSalaryTotal) > 0 ||
    data.departmentSalaryShare.length > 0
  )
})

const taxHasData = computed(() => {
  const data = taxState.data
  if (!data) return false
  return (
    toNumber(data.positiveTaxAmount) > 0 ||
    toNumber(data.unpaidTaxAmount) > 0 ||
    data.taxTypeStructure.length > 0 ||
    data.statusSummary.some((item) => toNumber(item.count) > 0 || toNumber(item.amount) !== 0)
  )
})

const currentTabHasData = computed(() => {
  if (activeTab.value === 'finance') return financeHasData.value
  if (activeTab.value === 'hr') return hrHasData.value
  return taxHasData.value
})

const currentTabEmptyState = computed(() => {
  if (activeTab.value === 'finance') {
    return {
      title: '还没有可分析的财务数据',
      description: '先录入财务账本中的收入与支出流水，这里会自动切出成本结构和前五大收入来源。',
    }
  }

  if (activeTab.value === 'hr') {
    return {
      title: '还没有可分析的员工数据',
      description: '录入员工名册后，这里会生成当前团队的部门结构、薪资负担和重点部门画像。',
    }
  }

  return {
    title: '还没有可分析的税务数据',
    description: '先录入税务档案或补充收入流水，这里会自动形成税负率与缴纳状态画像。',
  }
})

const taxBurdenTone = computed(() => {
  const rate = toNumber(taxState.data?.taxBurdenRate)
  if (rate >= 0.2) return 'danger'
  if (rate >= 0.1) return 'warning'
  return 'healthy'
})

const taxGaugeHasBaseline = computed(() => toNumber(taxState.data?.incomeBase) > 0)

const taxGaugeSummaryTone = computed(() => (taxGaugeHasBaseline.value ? taxBurdenTone.value : 'muted'))

const taxGaugeSummaryValue = computed(() => {
  if (!taxGaugeHasBaseline.value) return '暂无基线'
  if (taxBurdenTone.value === 'danger') return '高压区'
  if (taxBurdenTone.value === 'warning') return '关注区'
  return '低压区'
})

const taxGaugeSummaryNote = computed(() => {
  if (!taxGaugeHasBaseline.value) return '等待正向收入基线'
  if (taxBurdenTone.value === 'danger') return '超过 20% 高压线'
  if (taxBurdenTone.value === 'warning') return '进入 10%-20% 关注线'
  return '低于 10% 观察线'
})

const taxGaugeSummaryTitle = computed(() => `风险判断 ${taxGaugeSummaryValue.value} · ${taxGaugeSummaryNote.value}`)

const financeNetSpread = computed(() => {
  if (!financeState.data) return 0
  return toNumber(financeState.data.totalIncome) - toNumber(financeState.data.totalExpense)
})

const financeCoverageRatio = computed(() => {
  const income = toNumber(financeState.data?.totalIncome)
  if (income <= 0) return 0
  return toNumber(financeState.data?.totalExpense) / income
})

const financeTop1Share = computed(() => toNumber(financeState.data?.incomeConcentration?.top1Share))

const financeTop3Share = computed(() => toNumber(financeState.data?.incomeConcentration?.top3Share))

const financeSourceCount = computed(() => toNumber(financeState.data?.incomeConcentration?.sourceCount))

const financeTop1Tone = computed(() => {
  if (!financeState.data || financeSourceCount.value <= 0) return 'muted'
  if (financeTop1Share.value >= 0.5) return 'warning'
  if (financeTop1Share.value >= 0.3) return 'focus'
  return 'calm'
})

const financeTop1Note = computed(() => {
  if (!financeState.data || financeSourceCount.value <= 0) {
    return '等待来源形成'
  }
  if (financeTop1Share.value >= 0.5) {
    return '单一来源依赖偏高'
  }
  if (financeTop1Share.value >= 0.3) {
    return '核心来源需要持续盯住'
  }
  return '依赖较分散'
})

const financeProfitChange = computed(() => toNumber(financeState.data?.periodComparison?.profitChange))

const financeComparisonTone = computed(() => {
  if (!financeState.data?.periodComparison) return 'neutral'
  if (financeProfitChange.value > 0) return 'income'
  if (financeProfitChange.value < 0) return 'expense'
  return 'neutral'
})

const financeConcentrationHeadline = computed(() => {
  if (!financeState.data || financeSourceCount.value <= 0) {
    return '等待收入来源形成后，再判断经营依赖是否集中。'
  }
  if (financeTop3Share.value >= 0.8) {
    return `前三大来源已贡献 ${formatRatio(financeTop3Share.value)}，收入依赖明显偏高。`
  }
  if (financeTop3Share.value >= 0.6) {
    return `前三大来源贡献 ${formatRatio(financeTop3Share.value)}，建议持续盯住核心来源稳定性。`
  }
  return `当前收入分散到 ${formatCount(financeSourceCount.value)} 个来源，集中风险相对可控。`
})

const financeCoverageHeadline = computed(() => {
  if (!financeState.data || toNumber(financeState.data.totalIncome) <= 0) {
    return '当前范围内暂无收入基线，先补齐收入后再判断成本吞噬。'
  }
  if (financeCoverageRatio.value >= 0.9) {
    return `成本已吞掉 ${formatRatio(financeCoverageRatio.value)} 的收入，利润弹性偏紧。`
  }
  if (financeCoverageRatio.value >= 0.7) {
    return `成本吞噬率为 ${formatRatio(financeCoverageRatio.value)}，利润空间开始承压。`
  }
  return `成本吞噬率为 ${formatRatio(financeCoverageRatio.value)}，当前利润空间仍有缓冲。`
})

const financeComparisonHeadline = computed(() => {
  const comparison = financeState.data?.periodComparison
  if (!comparison) {
    return '全历史范围不提供等长周期对比。'
  }
  if (financeProfitChange.value > 0) {
    return `${comparison.baselineLabel}，利润改善 ${formatShortCurrency(financeProfitChange.value)}。`
  }
  if (financeProfitChange.value < 0) {
    return `${comparison.baselineLabel}，利润收窄 ${formatShortCurrency(Math.abs(financeProfitChange.value))}。`
  }
  return `${comparison.baselineLabel}，利润与上一周期基本持平。`
})

const financeTrendPanelTitle = computed(() =>
  financeTrendMode.value === 'bar' ? '月度收入、支出与利润走势' : '月度收支规模与利润率',
)

const financeTrendPanelDescription = computed(() => {
  if (financeTrendMode.value === 'line') {
    return '把收支改成折线后，更适合连续观察利润率拐点是否正在形成。'
  }
  if (financeTrendMode.value === 'bar') {
    return '切回金额柱后，适合直接比较每个月的收入、支出和利润差。'
  }
  return '默认先看收支规模，再看利润率是否沿时间线走弱。'
})

const financeTrendEmptyDescription = computed(() =>
  financeTrendMode.value === 'bar'
    ? '当前范围内还没有形成可读的收入、支出与利润序列。'
    : '当前范围内还没有形成可读的收支与利润率序列。',
)

void financeCoverageHeadline

const hrAverageSalary = computed(() => {
  const count = toNumber(hrState.data?.activeEmployeeCount)
  if (count <= 0) return 0
  return toNumber(hrState.data?.activeSalaryTotal) / count
})

type HrDepartmentInsight = {
  department: string
  employeeCount: number
  salaryAmount: number
  ratio: number
  averageSalary: number
}

const hrDepartmentInsights = computed<HrDepartmentInsight[]>(() => {
  if (!hrState.data) return []
  return getHrDepartmentInsights(hrState.data)
})

const hrTopDepartment = computed(() => hrDepartmentInsights.value[0] ?? null)

const hrDepartmentSummaryTone = computed(() => {
  const ratio = toNumber(hrTopDepartment.value?.ratio)
  if (!hrTopDepartment.value) return 'muted'
  if (ratio >= 0.25) return 'warning'
  if (ratio >= 0.15) return 'focus'
  return 'calm'
})

const hrDepartmentSummaryDepartment = computed(() => hrTopDepartment.value?.department || '等待部门形成')

const hrDepartmentSummaryTitle = computed(() =>
  hrTopDepartment.value
    ? `压力集中在 ${hrTopDepartment.value.department} · ${formatRatio(hrTopDepartment.value.ratio)}`
    : '等待部门形成',
)

const taxUnpaidRatio = computed(() => {
  const positiveTaxAmount = toNumber(taxState.data?.positiveTaxAmount)
  if (positiveTaxAmount <= 0) return 0
  return toNumber(taxState.data?.unpaidTaxAmount) / positiveTaxAmount
})

const taxStatusTotalCount = computed(() =>
  (taxState.data?.statusSummary || []).reduce((sum, item) => sum + toNumber(item.count), 0),
)

const taxUnpaidRecordRatio = computed(() => {
  if (taxStatusTotalCount.value <= 0) return 0
  const unpaidItem = taxState.data?.statusSummary.find((item) => item.status === 0)
  return toNumber(unpaidItem?.count) / taxStatusTotalCount.value
})

const taxTopTaxType = computed(() => taxState.data?.taxTypeStructure[0] ?? null)

const taxTopTaxTypeShare = computed(() => {
  const positiveTaxAmount = toNumber(taxState.data?.positiveTaxAmount)
  if (positiveTaxAmount <= 0) return 0
  return toNumber(taxTopTaxType.value?.amount) / positiveTaxAmount
})

const taxTopTaxTypeTone = computed(() => {
  if (!taxTopTaxType.value) return 'muted'
  if (taxTopTaxTypeShare.value >= 0.5) return 'warning'
  if (taxTopTaxTypeShare.value >= 0.3) return 'focus'
  return 'calm'
})

const taxTopTaxTypeSummaryName = computed(() => taxTopTaxType.value?.taxType || '等待结构形成')

const taxTopTaxTypeSummaryTitle = computed(() =>
  taxTopTaxType.value
    ? `压力主要来自 ${taxTopTaxType.value.taxType} · ${formatRatio(taxTopTaxTypeShare.value)}`
    : '等待结构形成',
)

const taxComparisonTone = computed(() => {
  const comparison = taxState.data?.periodComparison
  if (!comparison) return 'neutral'
  const delta = toNumber(comparison.burdenRateDelta)
  if (delta > 0) return 'warning'
  if (delta < 0) return 'healthy'
  return 'neutral'
})

const taxComparisonLabel = computed(() => {
  const comparison = taxState.data?.periodComparison
  if (!comparison) return '全历史范围不提供周期比较'
  return `${comparison.baselineLabel} ${formatSignedPercentagePoint(comparison.burdenRateDelta)}`
})

const taxStatusBreakdown = computed(() =>
  (taxState.data?.statusSummary || []).map((item) => ({
    status: item.status,
    label: getTaxStatusLabel(item.status),
    count: toNumber(item.count),
    amount: toNumber(item.amount),
    ratio: taxStatusTotalCount.value > 0 ? toNumber(item.count) / taxStatusTotalCount.value : 0,
  })),
)

const taxOutstandingItems = computed(() => taxState.data?.recentOutstanding || [])

void taxComparisonLabel
void taxComparisonTone
void taxOutstandingItems

const taxRiskHeadline = computed(() => {
  const data = taxState.data
  if (!data) return '用税负率和待缴情形一起判断当下风险节奏。'

  if (toNumber(data.incomeBase) <= 0) {
    return '当前没有正向收入基线，先补齐收入后再判断税负强度。'
  }

  const hasUnpaid = toNumber(data.unpaidTaxAmount) > 0
  const burdenRising = toNumber(data.periodComparison?.burdenRateDelta) > 0
  if (hasUnpaid && burdenRising) {
    return '待缴情形叠加税负抬升，优先处理最新欠税并复盘申报节奏。'
  }
  if (hasUnpaid) {
    return '主风险来自待缴情形，优先消化最近税期的待缴事项。'
  }
  if (burdenRising) {
    return '税负强度正在抬头，适合结合税种结构复盘本期经营压力。'
  }
  if (taxTopTaxType.value && taxTopTaxTypeShare.value >= 0.5) {
    return `税负压力高度集中在 ${taxTopTaxType.value.taxType}，需要持续盯住单一税种波动。`
  }
  return '税负与缴纳节奏整体稳定，更适合持续观察税种结构变化。'
})

watch(
  activeTab,
  async (tab) => {
    await ensureTabLoaded(tab)
    await nextTick()
    renderActiveCharts()
  },
)

watch(financeTrendMode, (mode, previousMode) => {
  if (mode === previousMode) return
  if (activeTab.value !== 'finance' || !financeState.data || !financeHasData.value) return
  renderFinanceTrendChart(financeState.data)
})

onMounted(async () => {
  await ensureTabLoaded(activeTab.value)
  observeDashboardSize()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  chartMap.forEach((chart) => chart.dispose())
  chartMap.clear()
})

async function ensureTabLoaded(tab: AnalyticsTab) {
  if (tab === 'finance' && !financeState.loaded && !financeState.loading) {
    await fetchFinanceData()
  }
  if (tab === 'hr' && !hrState.loaded && !hrState.loading) {
    await fetchHrData()
  }
  if (tab === 'tax' && !taxState.loaded && !taxState.loading) {
    await fetchTaxData()
  }
}

async function fetchFinanceData() {
  financeState.loading = true
  financeState.error = ''
  try {
    const res = await getFinanceDashboard(financeState.range)
    financeState.data = normalizeFinanceDashboard(res.data)
    financeState.loaded = true
    if (activeTab.value === 'finance') {
      await nextTick()
      renderFinanceCharts()
    }
  } catch (error) {
    financeState.error = getErrorMessage(error, '财务剖析加载失败，请稍后重试')
  } finally {
    financeState.loading = false
  }
}

async function fetchHrData() {
  hrState.loading = true
  hrState.error = ''
  try {
    const res = await getHrDashboard(hrState.range)
    hrState.data = normalizeHrDashboard(res.data)
    hrState.loaded = true
    if (activeTab.value === 'hr') {
      await nextTick()
      renderHrCharts()
    }
  } catch (error) {
    hrState.error = getErrorMessage(error, '人事洞察加载失败，请稍后重试')
  } finally {
    hrState.loading = false
  }
}

async function fetchTaxData() {
  taxState.loading = true
  taxState.error = ''
  try {
    const res = await getTaxDashboard(taxState.range || undefined)
    taxState.data = normalizeTaxDashboard(res.data)
    taxState.range = taxState.data.selectedYear ? (String(taxState.data.selectedYear) as TaxDashboardRange) : ''
    taxState.loaded = true
    if (activeTab.value === 'tax') {
      await nextTick()
      renderTaxCharts()
    }
  } catch (error) {
    taxState.error = getErrorMessage(error, '税务健康加载失败，请稍后重试')
  } finally {
    taxState.loading = false
  }
}

function handleTabChange(name: string | number) {
  activeTab.value = name as AnalyticsTab
}

async function handleFinanceRangeChange() {
  await fetchFinanceData()
}

async function handleTaxRangeChange() {
  await fetchTaxData()
}

function handleBeforeTabLeave(_newName: string | number, _oldName: string | number) {
  return !exporting.value
}

async function handleExportDashboard() {
  if (!canExportDashboard.value) return

  exporting.value = true

  try {
    await ensureTabLoaded(activeTab.value)
    await nextTick()
    renderActiveCharts()
    prepareDashboardExportAssets()
    showExportStage.value = true
    await nextTick()

    const sections = [dashboardExportPageOneRef.value, currentTabHasData.value ? dashboardExportPageTwoRef.value : null]
      .filter((element): element is HTMLDivElement => Boolean(element))
      .map((element, index) => ({
        element,
        fitToPage: true,
        pageBreakBefore: index > 0,
      }))

    await exportSectionsToPdf({
      sections,
      fileNameParts: [
        companyLabel.value,
        '数据看板',
        activeTabLabel.value,
        activeRangeLabel.value,
        formatPdfTimestamp(),
      ],
      loadingText: '正在生成数据看板 PDF 报告...',
      orientation: 'portrait',
    })

    ElMessage.success('数据看板 PDF 报告已开始下载')
  } catch (error) {
    ElMessage.error((error as { message?: string })?.message || '数据看板 PDF 导出失败，请稍后重试')
  } finally {
    showExportStage.value = false
    clearDashboardExportAssets()
    exporting.value = false
  }
}

function observeDashboardSize() {
  if (!dashboardRootRef.value || resizeObserver) return

  resizeObserver = new ResizeObserver(() => {
    chartMap.forEach((chart) => chart.resize())
  })
  resizeObserver.observe(dashboardRootRef.value)
}

function prepareDashboardExportAssets() {
  clearDashboardExportAssets()

  if (activeTab.value === 'finance' && financeHasData.value) {
    exportChartImages.financeExpense = captureChartImage('finance-expense')
    exportChartImages.financeIncome = captureChartImage('finance-income')
    exportChartImages.financeTrend = captureChartImage('finance-trend')
  } else if (activeTab.value === 'hr' && hrHasData.value) {
    exportChartImages.hrDepartment = captureChartImage('hr-department')
    exportChartImages.hrTrend = captureChartImage('hr-trend')
  } else if (activeTab.value === 'tax' && taxHasData.value) {
    exportChartImages.taxGauge = captureChartImage('tax-gauge')
    exportChartImages.taxType = captureChartImage('tax-structure')
  }
}

function clearDashboardExportAssets() {
  exportChartImages.financeExpense = ''
  exportChartImages.financeIncome = ''
  exportChartImages.financeTrend = ''
  exportChartImages.hrDepartment = ''
  exportChartImages.hrTrend = ''
  exportChartImages.taxGauge = ''
  exportChartImages.taxType = ''
}

function captureChartImage(key: string) {
  const chart = chartMap.get(key)
  if (!chart) return ''

  return chart.getDataURL({
    pixelRatio: 2,
    backgroundColor: '#ffffff',
  })
}

function renderActiveCharts() {
  if (activeTab.value === 'finance') {
    renderFinanceCharts()
  } else if (activeTab.value === 'hr') {
    renderHrCharts()
  } else {
    renderTaxCharts()
  }
}

function renderFinanceCharts() {
  if (!financeState.data || !financeHasData.value) {
    disposeCharts('finance-expense', 'finance-income', 'finance-trend')
    return
  }

  renderFinanceIncomeChart(financeState.data)
  renderFinanceTrendChart(financeState.data)
  renderFinanceExpenseChart(financeState.data)
}

function renderHrCharts() {
  if (!hrState.data || !hrHasData.value) {
    disposeCharts('hr-department', 'hr-trend')
    return
  }

  renderHrDepartmentChart(hrState.data)
  renderHrTrendChart(hrState.data)
}

function renderTaxCharts() {
  if (!taxState.data || !taxHasData.value) {
    disposeCharts('tax-gauge', 'tax-structure')
    return
  }

  renderTaxGaugeChart(taxState.data)
  renderTaxTypeChart(taxState.data)
}

function renderFinanceExpenseChart(data: FinanceDashboardVO) {
  const items = [...getFinanceExpenseFocusItems(data)].reverse()
  if (!financeExpenseChartRef.value || !items.length) {
    disposeChart('finance-expense')
    return
  }

  setChartOption('finance-expense', financeExpenseChartRef.value, {
    color: ['#dd5b00'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: Array<{ name: string; value: number }>) => {
        const first = params[0]
        if (!first) return ''
        const totalExpense = toNumber(data.totalExpense)
        const ratio = totalExpense > 0 ? first.value / totalExpense : 0
        return `${first.name}<br/>支出 ${formatCurrency(first.value)}<br/>占比 ${formatRatio(ratio)}`
      },
    },
    grid: {
      top: 12,
      left: 12,
      right: 18,
      bottom: 12,
      containLabel: true,
    },
    xAxis: {
      type: 'value',
      axisLabel: {
        color: '#615d59',
        formatter: (value: number) => formatAxisCurrency(value),
      },
      splitLine: { lineStyle: { type: 'dashed', color: 'rgba(0,0,0,0.08)' } },
    },
    yAxis: {
      type: 'category',
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        color: 'rgba(0,0,0,0.88)',
        formatter: (value: string) => truncateLabel(value, 8),
      },
      data: items.map((item) => item.name),
    },
    series: [
      {
        type: 'bar',
        barWidth: 16,
        borderRadius: [8, 8, 8, 8],
        itemStyle: {
          color: '#dd5b00',
          shadowColor: 'rgba(221, 91, 0, 0.18)',
          shadowBlur: 14,
          shadowOffsetY: 8,
        },
        data: items.map((item) => item.amount),
        label: {
          show: true,
          position: 'right',
          color: '#615d59',
          formatter: (params: { value: number }) => formatShortCurrency(params.value),
        },
      },
    ],
  })
}

function renderFinanceIncomeChart(data: FinanceDashboardVO) {
  const items = getFinanceIncomeParetoItems(data)
  if (!financeIncomeChartRef.value || !items.length) {
    disposeChart('finance-income')
    return
  }

  const totalIncome = toNumber(data.totalIncome)
  const itemRatios = items.map((item) => (totalIncome > 0 ? item.amount / totalIncome : 0))
  const cumulativeRatios: number[] = []
  let runningAmount = 0
  for (const item of items) {
    runningAmount += item.amount
    cumulativeRatios.push(totalIncome > 0 ? runningAmount / totalIncome : 0)
  }

  const incomeAmountName = '收入金额'
  const cumulativeRatioName = '累计占比'
  const cumulativeTailName = '累计补齐'
  const hasOtherBucket = items.at(-1)?.name === '其他来源' && items.length >= 2
  const mainLineData = hasOtherBucket ? [...cumulativeRatios.slice(0, -1), null] : [...cumulativeRatios]
  const topSourceEndIndex = hasOtherBucket ? items.length - 1 : items.length
  const topSourceIndex = items
    .slice(0, topSourceEndIndex)
    .reduce((bestIndex, currentItem, currentIndex, sourceItems) => {
      if (!sourceItems[bestIndex] || currentItem.amount > sourceItems[bestIndex].amount) {
        return currentIndex
      }
      return bestIndex
    }, 0)
  const tailLineData = items.map<null | { value: number; symbolSize?: number; itemStyle?: { color: string } }>((_, index) => {
    if (!hasOtherBucket) return null
    const tailStartIndex = items.length - 2
    const tailEndIndex = items.length - 1
    if (index === tailStartIndex) {
      return {
        value: cumulativeRatios[index],
        symbolSize: 0,
        itemStyle: { color: 'rgba(33, 49, 131, 0.42)' },
      }
    }
    if (index === tailEndIndex) {
      return {
        value: cumulativeRatios[index],
        symbolSize: 5,
        itemStyle: { color: 'rgba(33, 49, 131, 0.42)' },
      }
    }
    return null
  })
  const focusSeriesCount = hasOtherBucket ? items.length - 1 : items.length
  const highlightCount = Math.min(3, focusSeriesCount)
  const highlightIndex = Math.max(0, highlightCount - 1)

  setChartOption('finance-income', financeIncomeChartRef.value, {
    color: ['#2a9d99', '#213183'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (
        params: Array<{ seriesName: string; value: number; axisValue: string; marker: string; dataIndex: number }>,
      ) => {
        {
          const amountItem = params.find((item) => item.seriesName === incomeAmountName)
          const ratioItem = params.find((item) => item.seriesName === cumulativeRatioName || item.seriesName === cumulativeTailName)
          if (!amountItem) return ''
          const currentRatio = itemRatios[amountItem.dataIndex] ?? 0
          const cumulativeLabel = amountItem.axisValue === '其他来源' ? '累计至全部来源' : cumulativeRatioName
          return `<div style="min-width: 190px;">
            <div style="font-weight: 700; margin-bottom: 8px;">${amountItem.axisValue}</div>
            <div>${amountItem.marker}${amountItem.seriesName}<span style="float:right;margin-left:18px;font-weight:600;">${formatCurrency(amountItem.value)}</span></div>
            <div>${amountItem.marker}当前来源占比<span style="float:right;margin-left:18px;font-weight:600;">${formatRatio(currentRatio)}</span></div>
            ${ratioItem ? `<div>${ratioItem.marker}${cumulativeLabel}<span style="float:right;margin-left:18px;font-weight:600;">${formatRatio(ratioItem.value)}</span></div>` : ''}
          </div>`
        }
      },
    },
    legend: {
      top: 0,
      data: [incomeAmountName, cumulativeRatioName],
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: '#615d59' },
    },
    grid: {
      top: 18,
      left: 16,
      right: 18,
      bottom: 28,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } },
      axisLabel: {
        color: '#615d59',
        formatter: (value: string) => truncateLabel(value, 7),
      },
      data: items.map((item) => item.name),
    },
    yAxis: [
      {
        type: 'value',
        axisLabel: {
          color: '#615d59',
          formatter: (value: number) => formatAxisCurrency(value),
        },
        splitLine: { lineStyle: { type: 'dashed', color: 'rgba(0,0,0,0.08)' } },
      },
      {
        type: 'value',
        min: 0,
        max: 1,
        axisLabel: {
          color: '#615d59',
          formatter: (value: number) => formatRatio(value),
        },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: incomeAmountName,
        type: 'bar',
        barWidth: 24,
        borderRadius: [12, 12, 0, 0],
        data: items.map((item, index) => ({
          value: item.amount,
          itemStyle:
            index === topSourceIndex
              ? {
                  color: '#0f6cc8',
                  shadowColor: 'rgba(19, 117, 209, 0.32)',
                  shadowBlur: 22,
                  shadowOffsetY: 12,
                }
              : hasOtherBucket && index === items.length - 1
                ? {
                    color: 'rgba(19, 117, 209, 0.76)',
                    shadowColor: 'rgba(19, 117, 209, 0.16)',
                    shadowBlur: 16,
                    shadowOffsetY: 10,
                  }
                : {
                    color: '#1375d1',
                    shadowColor: 'rgba(19, 117, 209, 0.22)',
                    shadowBlur: 18,
                    shadowOffsetY: 10,
                  },
        })),
        label: {
          show: true,
          position: 'top',
          formatter: (params: { value: number; dataIndex: number }) =>
            params.dataIndex === topSourceIndex
              ? `{top1|第一来源}\n{amount|${formatShortCurrency(params.value)}}`
              : `{amount|${formatShortCurrency(params.value)}}`,
          rich: {
            top1: {
              color: '#0f6cc8',
              fontSize: 11,
              fontWeight: 600,
              padding: [4, 8],
              borderRadius: 999,
              backgroundColor: 'rgba(19, 117, 209, 0.10)',
              lineHeight: 22,
            },
            amount: {
              color: '#615d59',
              fontSize: 13,
              fontWeight: 500,
              lineHeight: 18,
            },
          },
        },
      },
      {
        name: cumulativeRatioName,
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 8,
        lineStyle: { width: 4 },
        itemStyle: { color: '#213183' },
        areaStyle: { color: 'rgba(33, 49, 131, 0.12)' },
        data: mainLineData,
        label: {
          show: true,
          position: 'top',
          formatter: (params: { dataIndex: number; value: number }) =>
            params.dataIndex === highlightIndex
              ? `{summary|前三累计 ${formatRatio(cumulativeRatios[params.dataIndex])}}`
              : '',
          rich: {
            summary: {
              color: 'rgba(33, 49, 131, 0.82)',
              fontSize: 11,
              fontWeight: 600,
              padding: [4, 8],
              borderRadius: 999,
              backgroundColor: 'rgba(33, 49, 131, 0.08)',
            },
          },
        },
      },
      ...(hasOtherBucket
        ? [
            {
              name: cumulativeTailName,
              type: 'line',
              yAxisIndex: 1,
              smooth: true,
              showSymbol: true,
              symbolSize: 5,
              z: 2,
              lineStyle: {
                width: 3,
                type: 'dashed',
                color: 'rgba(33, 49, 131, 0.38)',
              },
              itemStyle: { color: 'rgba(33, 49, 131, 0.42)' },
              data: tailLineData,
            },
          ]
        : []),
    ],
  })
}

function renderFinanceTrendChart(data: FinanceDashboardVO) {
  if (!financeTrendChartRef.value || !data.monthlyTrend.length) {
    disposeChart('finance-trend')
    return
  }

  const incomeName = '\u6536\u5165'
  const expenseName = '\u652f\u51fa'
  const profitName = '\u5229\u6da6'
  const profitMarginName = '\u5229\u6da6\u7387'
  const isBarMode = financeTrendMode.value === 'bar'
  const isLineMode = financeTrendMode.value === 'line'
  const months = data.monthlyTrend.map((item) => item.month)
  const profitMarginSeries = data.monthlyTrend.map((item) => (item.income > 0 ? item.profit / item.income : null))

  setChartOption('finance-trend', financeTrendChartRef.value, {
    color: ['#2a9d99', '#dd5b00', '#213183'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: isBarMode ? 'shadow' : 'cross' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: Array<{ seriesName: string; value: number | null; axisValue: string; marker: string }>) => {
        const axisValue = params[0]?.axisValue || ''
        const title = formatMonthTitle(axisValue)
        const currentPoint = data.monthlyTrend.find((item) => item.month === axisValue)
        if (!currentPoint) return ''

        const markerMap = new Map(params.map((item) => [item.seriesName, item.marker]))
        const incomeMarker = markerMap.get(incomeName) || buildTooltipMarker('#2a9d99')
        const expenseMarker = markerMap.get(expenseName) || buildTooltipMarker('#dd5b00')
        const profitMarker = isBarMode
          ? markerMap.get(profitName) || buildTooltipMarker(currentPoint.profit >= 0 ? '#213183' : '#d1495b')
          : markerMap.get(profitMarginName) || buildTooltipMarker('#213183')

        const lines = [
          `${incomeMarker}${incomeName}<span style="float:right;margin-left:18px;font-weight:600;">${formatCurrency(currentPoint.income)}</span>`,
          `${expenseMarker}${expenseName}<span style="float:right;margin-left:18px;font-weight:600;">${formatCurrency(currentPoint.expense)}</span>`,
          isBarMode
            ? `${profitMarker}${profitName}<span style="float:right;margin-left:18px;font-weight:600;">${formatCurrency(currentPoint.profit)}</span>`
            : `${profitMarker}${profitMarginName}<span style="float:right;margin-left:18px;font-weight:600;">${formatFinanceTrendRatio(currentPoint.income > 0 ? currentPoint.profit / currentPoint.income : null)}</span>`,
        ]
        return `<div style="min-width: 210px;">
          <div style="font-weight: 700; margin-bottom: 8px;">${title}</div>
          ${lines.join('')}
        </div>`
      },
    },
    legend: {
      top: 0,
      data: [incomeName, expenseName, isBarMode ? profitName : profitMarginName],
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: '#615d59' },
    },
    grid: {
      top: 44,
      left: 14,
      right: isBarMode ? 14 : 64,
      bottom: 40,
      containLabel: true,
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      },
      {
        type: 'slider',
        height: 16,
        bottom: 8,
        brushSelect: false,
        borderColor: 'transparent',
        fillerColor: 'rgba(33, 49, 131, 0.08)',
        handleStyle: { color: '#213183' }
      }
    ],
    xAxis: {
      type: 'category',
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } },
      axisLabel: {
        color: '#615d59',
        formatter: (value: string) => formatMonthTick(value),
      },
      data: months,
    },
    yAxis: isBarMode
      ? {
          type: 'value',
          axisLabel: {
            color: '#615d59',
            formatter: (value: number) => formatAxisCurrency(value),
          },
          splitLine: { lineStyle: { type: 'dashed', color: 'rgba(0,0,0,0.08)' } },
        }
      : [
          {
            type: 'value',
            axisLabel: {
              color: '#615d59',
              formatter: (value: number) => formatAxisCurrency(value),
            },
            splitLine: { lineStyle: { type: 'dashed', color: 'rgba(0,0,0,0.08)' } },
          },
          {
            type: 'value',
            position: 'right',
            axisLabel: {
              color: '#615d59',
              formatter: (value: number) => formatRatio(value),
            },
            splitLine: { show: false },
          },
        ],
    series: [
      isLineMode
        ? {
            name: incomeName,
            type: 'line',
            smooth: true,
            showSymbol: true,
            symbolSize: 6,
            itemStyle: { color: '#2a9d99' },
            lineStyle: { width: 4, color: '#2a9d99' },
            areaStyle: { color: 'rgba(42, 157, 153, 0.10)' },
            data: data.monthlyTrend.map((item) => item.income),
          }
        : {
            name: incomeName,
            type: 'bar',
            barWidth: isBarMode ? 14 : 18,
            itemStyle: {
              color: '#2a9d99',
              borderRadius: [8, 8, 0, 0],
            },
            data: data.monthlyTrend.map((item) => item.income),
          },
      isLineMode
        ? {
            name: expenseName,
            type: 'line',
            smooth: true,
            showSymbol: true,
            symbolSize: 6,
            itemStyle: { color: '#dd5b00' },
            lineStyle: { width: 4, color: '#dd5b00' },
            areaStyle: { color: 'rgba(221, 91, 0, 0.08)' },
            data: data.monthlyTrend.map((item) => item.expense),
          }
        : {
            name: expenseName,
            type: 'bar',
            barWidth: isBarMode ? 14 : 18,
            itemStyle: {
              color: '#dd5b00',
              borderRadius: [8, 8, 0, 0],
            },
            data: data.monthlyTrend.map((item) => item.expense),
          },
      isBarMode
        ? {
            name: profitName,
            type: 'bar',
            barWidth: 14,
            itemStyle: {
              color: (params: { value: number }) => (params.value >= 0 ? '#213183' : '#d1495b'),
              shadowColor: 'rgba(33, 49, 131, 0.18)',
              shadowBlur: 16,
              shadowOffsetY: 8,
              borderRadius: [8, 8, 0, 0],
            },
            data: data.monthlyTrend.map((item) => item.profit),
          }
        : {
            name: profitMarginName,
            type: 'line',
            yAxisIndex: 1,
            smooth: true,
            showSymbol: true,
            connectNulls: false,
            symbolSize: 6,
            z: 3,
            itemStyle: { color: '#213183' },
            lineStyle: { width: 3, color: '#213183' },
            areaStyle: { color: 'rgba(33, 49, 131, 0.08)' },
            data: profitMarginSeries,
          },
    ],
  })
}

function getFinanceIncomeParetoItems(data: FinanceDashboardVO) {
  const items = data.topIncomeSources.map((item) => ({
    name: item.name,
    amount: toNumber(item.amount),
  }))
  const topFiveTotal = items.reduce((sum, item) => sum + item.amount, 0)
  const otherAmount = Math.max(toNumber(data.totalIncome) - topFiveTotal, 0)
  if (otherAmount > 0) {
    items.push({ name: '其他来源', amount: otherAmount })
  }
  return items
}

function getFinanceExpenseFocusItems(data: FinanceDashboardVO) {
  const items = [...data.expenseBreakdown]
    .sort((left, right) => right.amount - left.amount)
    .map((item) => ({
      name: item.name,
      amount: toNumber(item.amount),
    }))

  if (items.length <= 5) {
    return items
  }

  const visibleItems = items.slice(0, 5)
  const otherAmount = items.slice(5).reduce((sum, item) => sum + item.amount, 0)
  if (otherAmount > 0) {
    visibleItems.push({ name: '其他支出', amount: otherAmount })
  }
  return visibleItems
}

function getHrDepartmentInsights(data: HrDashboardVO): HrDepartmentInsight[] {
  return [...data.departmentSalaryShare]
    .map((item) => ({
      department: item.department,
      employeeCount: toNumber(item.employeeCount),
      salaryAmount: toNumber(item.salaryAmount),
      ratio: toNumber(item.ratio),
      averageSalary: toNumber(item.employeeCount) > 0 ? toNumber(item.salaryAmount) / toNumber(item.employeeCount) : 0,
    }))
    .sort((left, right) => {
      const compareSalary = right.salaryAmount - left.salaryAmount
      if (compareSalary !== 0) return compareSalary
      return left.department.localeCompare(right.department, 'zh-CN')
    })
}

function renderHrDepartmentChart(data: HrDashboardVO) {
  const rankedItems = getHrDepartmentInsights(data)
  const items = rankedItems.slice().reverse()
  if (!hrDepartmentChartRef.value || !items.length) {
    disposeChart('hr-department')
    return
  }
  const topDepartment = rankedItems[0] ?? null
  const topDepartmentIndex = topDepartment ? items.findIndex((item) => item.department === topDepartment.department) : -1

  setChartOption('hr-department', hrDepartmentChartRef.value, {
    color: ['#1375d1'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: Array<{ name: string; value: number; data: HrDepartmentInsight }>) => {
        const first = params[0]
        if (!first) return ''
        return `${first.name}<br/>基础薪资 ${formatCurrency(first.value)}<br/>在职人数 ${formatCount(first.data.employeeCount)} 人<br/>占比 ${formatRatio(first.data.ratio)}`
      },
    },
    grid: {
      top: 14,
      left: 12,
      right: 104,
      bottom: 12,
      containLabel: true,
    },
    xAxis: {
      type: 'value',
      axisLabel: {
        color: '#615d59',
        formatter: (value: number) => formatAxisCurrency(value),
      },
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
    },
    yAxis: {
      type: 'category',
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        color: 'rgba(0,0,0,0.88)',
        formatter: (value: string) => truncateLabel(value, 8),
      },
      data: items.map((item) => item.department),
    },
    series: [
      {
        type: 'bar',
        barWidth: 18,
        borderRadius: [9, 9, 9, 9],
        label: {
          show: true,
          position: 'right',
          formatter: (params: { value: number; data: HrDepartmentInsight; dataIndex: number }) =>
            params.dataIndex === topDepartmentIndex
              ? `{flag|第一负担部门}\n{value|${formatShortCurrency(params.value)} · ${formatRatio(params.data.ratio)}}`
              : `{value|${formatShortCurrency(params.value)} · ${formatRatio(params.data.ratio)}}`,
          rich: {
            flag: {
              color: '#0f6cc8',
              fontSize: 11,
              fontWeight: 600,
              padding: [4, 8],
              borderRadius: 999,
              backgroundColor: 'rgba(19, 117, 209, 0.10)',
              lineHeight: 22,
            },
            value: {
              color: '#615d59',
              fontSize: 13,
              fontWeight: 500,
              lineHeight: 18,
            },
          },
        },
        data: items.map((item, index) => ({
          value: item.salaryAmount,
          itemStyle:
            index === topDepartmentIndex
              ? {
                  color: '#0f6cc8',
                  shadowColor: 'rgba(19, 117, 209, 0.28)',
                  shadowBlur: 20,
                  shadowOffsetY: 10,
                }
              : {
                  color: '#1375d1',
                  shadowColor: 'rgba(19, 117, 209, 0.18)',
                  shadowBlur: 18,
                  shadowOffsetY: 10,
                },
          ...item,
        })),
      },
    ],
  })
}

function renderHrTrendChart(data: HrDashboardVO) {
  const items = getHrDepartmentInsights(data)
  if (!hrTrendChartRef.value || !items.length) {
    disposeChart('hr-trend')
    return
  }

  const hasManyDepartments = items.length > 8
  setChartOption('hr-trend', hrTrendChartRef.value, {
    color: ['#0075de', '#dd5b00'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: Array<{ seriesName: string; value: number; axisValue: string; marker: string }>) => {
        const title = params[0]?.axisValue || ''
        const lines = params.map((item) =>
          `${item.marker}${item.seriesName}<span style="float:right;margin-left:18px;font-weight:600;">${item.seriesName === '在职人数' ? `${formatCount(item.value)} 人` : formatCurrency(item.value)}</span>`,
        )
        return `<div style="min-width: 190px;">
          <div style="font-weight: 700; margin-bottom: 8px;">${title}</div>
          ${lines.join('')}
        </div>`
      },
    },
    legend: {
      top: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: '#615d59' },
    },
    grid: {
      top: 44,
      left: 12,
      right: 12,
      bottom: hasManyDepartments ? 56 : 16,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } },
      axisLabel: {
        color: '#615d59',
        formatter: (value: string) => truncateLabel(value, 4),
        rotate: items.length > 5 ? 18 : 0,
      },
      data: items.map((item) => item.department),
    },
    yAxis: [
      {
        type: 'value',
        axisLabel: {
          color: '#615d59',
          formatter: (value: number) => formatAxisCurrency(value),
        },
        splitLine: { lineStyle: { type: 'dashed', color: 'rgba(0,0,0,0.08)' } },
      },
      {
        type: 'value',
        axisLabel: {
          color: '#615d59',
          formatter: (value: number) => `${value}`,
        },
        splitLine: { show: false },
      },
    ],
    dataZoom: hasManyDepartments
      ? [
          {
            type: 'inside',
            startValue: Math.max(items.length - 8, 0),
            endValue: items.length - 1,
          },
          {
            type: 'slider',
            height: 18,
            bottom: 10,
            brushSelect: false,
          },
        ]
      : [],
    series: [
      {
        name: '在职人数',
        type: 'bar',
        yAxisIndex: 1,
        barWidth: 18,
        borderRadius: [8, 8, 0, 0],
        itemStyle: {
          color: '#1375d1',
          shadowColor: 'rgba(19, 117, 209, 0.14)',
          shadowBlur: 12,
          shadowOffsetY: 8,
        },
        data: items.map((item) => item.employeeCount),
      },
      {
        name: '人均基础薪资',
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        symbolSize: 8,
        lineStyle: { width: 4 },
        areaStyle: { color: 'rgba(221, 91, 0, 0.12)' },
        itemStyle: { color: '#dd5b00' },
        data: items.map((item) => item.averageSalary),
      },
    ],
  })
}

function renderTaxGaugeChart(data: TaxDashboardVO) {
  if (!taxGaugeChartRef.value) {
    disposeChart('tax-gauge')
    return
  }

  const ratePercent = Number((toNumber(data.taxBurdenRate) * 100).toFixed(1))
  const displayRatePercent = Math.min(ratePercent, 30)
  const hasIncomeBase = toNumber(data.incomeBase) > 0
  const progressColor = hasIncomeBase
    ? ratePercent >= 20
      ? '#e03e3e'
      : ratePercent >= 10
        ? '#dd5b00'
        : '#1aae39'
    : '#a39e98'

  setChartOption('tax-gauge', taxGaugeChartRef.value, {
    series: [
      {
        type: 'gauge',
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max: 30,
        splitNumber: 3,
        progress: {
          show: hasIncomeBase,
          width: 18,
          roundCap: true,
          itemStyle: { color: progressColor },
        },
        axisLine: {
          lineStyle: {
            width: 18,
            color: [
              [10 / 30, 'rgba(26, 174, 57, 0.18)'],
              [20 / 30, 'rgba(221, 91, 0, 0.16)'],
              [1, 'rgba(224, 62, 62, 0.16)'],
            ],
          },
        },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: {
          distance: -22,
          length: 16,
          lineStyle: {
            color: 'rgba(255,255,255,0.96)',
            width: 3,
          },
        },
        axisLabel: {
          distance: 8,
          color: '#615d59',
          fontSize: 12,
          fontWeight: 500,
          formatter: (value: number) => ([0, 10, 20, 30].includes(value) ? `${value}%` : ''),
        },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '46%'],
          formatter: () => (hasIncomeBase ? `${ratePercent.toFixed(1)}%` : '—'),
          color: 'rgba(0,0,0,0.95)',
          fontSize: 34,
          fontWeight: 700,
        },
        title: {
          offsetCenter: [0, '64%'],
          color: '#615d59',
          fontSize: 13,
          fontWeight: 500,
        },
        data: [
          {
            value: displayRatePercent,
            name: hasIncomeBase ? '当前税负率' : '暂无收入基线',
          },
        ],
      },
    ],
  })
}

function renderTaxTypeChart(data: TaxDashboardVO) {
  if (!taxTypeChartRef.value || !data.taxTypeStructure.length) {
    disposeChart('tax-structure')
    return
  }

  const items = [...data.taxTypeStructure].reverse()
  const topTaxType = data.taxTypeStructure[0] ?? null
  const topTaxTypeIndex = topTaxType ? items.findIndex((item) => item.taxType === topTaxType.taxType) : -1
  const topTaxTypeShareValue = toNumber(topTaxType?.ratio)
  const topTaxTypeLabelColor = topTaxTypeShareValue >= 0.5 ? '#dd5b00' : '#213183'
  const topTaxTypeLabelBackground = topTaxTypeShareValue >= 0.5 ? 'rgba(221, 91, 0, 0.10)' : 'rgba(33, 49, 131, 0.08)'
  setChartOption('tax-structure', taxTypeChartRef.value, {
    color: ['#213183'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: Array<{ name: string; value: number }>) => {
        const first = params[0]
        if (!first) return ''
        const currentItem = items.find((item) => item.taxType === first.name)
        return `${first.name}<br/>税额 ${formatCurrency(first.value)}<br/>占比 ${formatRatio(toNumber(currentItem?.ratio))}`
      },
    },
    grid: {
      top: 18,
      left: 12,
      right: 104,
      bottom: 12,
      containLabel: true,
    },
    xAxis: {
      type: 'value',
      axisLabel: {
        color: '#615d59',
        formatter: (value: number) => formatAxisCurrency(value),
      },
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
    },
    yAxis: {
      type: 'category',
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        color: 'rgba(0,0,0,0.88)',
        formatter: (value: string) => truncateLabel(value, 7),
      },
      data: items.map((item) => item.taxType),
    },
    series: [
      {
        type: 'bar',
        barWidth: 16,
        borderRadius: [8, 8, 8, 8],
        label: {
          show: true,
          position: 'right',
          formatter: (params: { value: number; dataIndex: number }) => {
            const currentItem = items[params.dataIndex]
            return params.dataIndex === topTaxTypeIndex
              ? `{flag|第一税种}\n{amount|${formatShortCurrency(params.value)}}\n{ratio|${formatRatio(toNumber(currentItem?.ratio))}}`
              : `{amount|${formatShortCurrency(params.value)}}\n{ratio|${formatRatio(toNumber(currentItem?.ratio))}}`
          },
          rich: {
            flag: {
              color: topTaxTypeLabelColor,
              fontSize: 11,
              fontWeight: 600,
              padding: [4, 8],
              borderRadius: 999,
              backgroundColor: topTaxTypeLabelBackground,
              lineHeight: 22,
            },
            amount: {
              color: '#615d59',
              fontSize: 12,
              fontWeight: 600,
              lineHeight: 18,
            },
            ratio: {
              color: '#8d877f',
              fontSize: 11,
              lineHeight: 15,
            },
          },
        },
        data: items.map((item, index) => ({
          value: item.amount,
          itemStyle:
            index === topTaxTypeIndex
              ? topTaxTypeShareValue >= 0.5
                ? {
                    color: '#dd5b00',
                    shadowColor: 'rgba(221, 91, 0, 0.20)',
                    shadowBlur: 18,
                    shadowOffsetY: 8,
                  }
                : {
                    color: '#213183',
                    shadowColor: 'rgba(33, 49, 131, 0.18)',
                    shadowBlur: 18,
                    shadowOffsetY: 8,
                  }
              : {
                  color: 'rgba(33, 49, 131, 0.94)',
                  shadowColor: 'rgba(33, 49, 131, 0.10)',
                  shadowBlur: 12,
                  shadowOffsetY: 6,
                },
        })),
      },
    ],
  })
}

function setChartOption(key: string, container: HTMLDivElement, option: any) {
  let chart = chartMap.get(key)
  if (!chart) {
    chart = init(container)
    chartMap.set(key, chart)
  }
  chart.setOption(option, true)
  chart.resize()
}

function disposeCharts(...keys: string[]) {
  keys.forEach((key) => disposeChart(key))
}

function disposeChart(key: string) {
  const chart = chartMap.get(key)
  if (!chart) return
  chart.dispose()
  chartMap.delete(key)
}

function normalizeFinanceDashboard(data: FinanceDashboardVO): FinanceDashboardVO {
  return {
    totalExpense: toNumber(data.totalExpense),
    totalIncome: toNumber(data.totalIncome),
    expenseBreakdown: (data.expenseBreakdown || []).map((item) => ({
      name: item.name,
      amount: toNumber(item.amount),
      ratio: toNumber(item.ratio),
    })),
    topIncomeSources: (data.topIncomeSources || []).map((item) => ({
      name: item.name,
      amount: toNumber(item.amount),
    })),
    monthlyTrend: (data.monthlyTrend || []).map((item) => ({
      month: item.month,
      income: toNumber(item.income),
      expense: toNumber(item.expense),
      profit: toNumber(item.profit),
    })),
    incomeConcentration: {
      top1Share: toNumber(data.incomeConcentration?.top1Share),
      top3Share: toNumber(data.incomeConcentration?.top3Share),
      top5Share: toNumber(data.incomeConcentration?.top5Share),
      otherShare: toNumber(data.incomeConcentration?.otherShare),
      sourceCount: toNumber(data.incomeConcentration?.sourceCount),
    },
    periodComparison: data.periodComparison
      ? {
          incomeChange: toNumber(data.periodComparison.incomeChange),
          expenseChange: toNumber(data.periodComparison.expenseChange),
          profitChange: toNumber(data.periodComparison.profitChange),
          baselineLabel: data.periodComparison.baselineLabel,
        }
      : null,
  }
}

function normalizeHrDashboard(data: HrDashboardVO): HrDashboardVO {
  return {
    activeEmployeeCount: toNumber(data.activeEmployeeCount),
    activeSalaryTotal: toNumber(data.activeSalaryTotal),
    departmentSalaryShare: (data.departmentSalaryShare || []).map((item) => ({
      department: item.department,
      employeeCount: toNumber(item.employeeCount),
      salaryAmount: toNumber(item.salaryAmount),
      ratio: toNumber(item.ratio),
    })),
    monthlyTrend: (data.monthlyTrend || []).map((item) => ({
      month: item.month,
      employeeCount: toNumber(item.employeeCount),
      salaryAmount: toNumber(item.salaryAmount),
    })),
  }
}

function normalizeTaxDashboard(data: TaxDashboardVO): TaxDashboardVO {
  const summaryMap = new Map<TaxPaymentStatus, { status: TaxPaymentStatus; count: number; amount: number }>([
    [0, { status: 0, count: 0, amount: 0 }],
    [1, { status: 1, count: 0, amount: 0 }],
    [2, { status: 2, count: 0, amount: 0 }],
  ])

  for (const item of data.statusSummary || []) {
    summaryMap.set(item.status, {
      status: item.status,
      count: toNumber(item.count),
      amount: toNumber(item.amount),
    })
  }

  return {
    taxBurdenRate: toNumber(data.taxBurdenRate),
    positiveTaxAmount: toNumber(data.positiveTaxAmount),
    incomeBase: toNumber(data.incomeBase),
    unpaidTaxAmount: toNumber(data.unpaidTaxAmount),
    availableYears: (data.availableYears || [])
      .map((year) => toNumber(year))
      .filter((year) => Number.isInteger(year) && year > 0),
    selectedYear: data.selectedYear == null ? null : toNumber(data.selectedYear),
    taxTypeStructure: (data.taxTypeStructure || []).map((item) => ({
      taxType: item.taxType,
      amount: toNumber(item.amount),
      ratio: toNumber(item.ratio),
    })),
    statusSummary: Array.from(summaryMap.values()),
    periodComparison: data.periodComparison
      ? {
          baselineLabel: data.periodComparison.baselineLabel,
          previousTaxBurdenRate: toNumber(data.periodComparison.previousTaxBurdenRate),
          burdenRateDelta: toNumber(data.periodComparison.burdenRateDelta),
          previousUnpaidTaxAmount: toNumber(data.periodComparison.previousUnpaidTaxAmount),
          unpaidTaxAmountDelta: toNumber(data.periodComparison.unpaidTaxAmountDelta),
          previousPositiveTaxAmount: toNumber(data.periodComparison.previousPositiveTaxAmount),
          positiveTaxAmountDelta: toNumber(data.periodComparison.positiveTaxAmountDelta),
        }
      : null,
    recentOutstanding: (data.recentOutstanding || []).map((item) => ({
      taxPeriod: item.taxPeriod,
      taxType: item.taxType,
      amount: toNumber(item.amount),
    })),
  }
}

function getErrorMessage(error: unknown, fallback: string) {
  return (error as { message?: string })?.message || fallback
}

function formatCurrency(value: number) {
  return `¥${toNumber(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function parseTaxRangeYear(range: string | null | undefined) {
  if (!range || !/^\d{4}$/.test(range)) return null
  const year = Number.parseInt(range, 10)
  return Number.isInteger(year) ? year : null
}

function formatTaxRangeLabel(year: number | null | undefined) {
  return typeof year === 'number' && Number.isInteger(year) ? `${year}年度` : '暂无年度'
}

function formatSignedCurrency(value: number) {
  const numeric = toNumber(value)
  if (numeric === 0) return formatCurrency(0)
  return `${numeric > 0 ? '+' : '-'}${formatCurrency(Math.abs(numeric))}`
}

function formatSignedPercentagePoint(value: number) {
  const numeric = toNumber(value) * 100
  if (numeric === 0) return '0.0pct'
  return `${numeric > 0 ? '+' : '-'}${Math.abs(numeric).toFixed(1)}pct`
}

function formatShortCurrency(value: number) {
  const numeric = toNumber(value)
  if (Math.abs(numeric) >= 10000) {
    return `¥${(numeric / 10000).toFixed(1)}万`
  }
  return formatCurrency(numeric)
}

function formatAxisCurrency(value: number) {
  const numeric = toNumber(value)
  if (Math.abs(numeric) >= 10000) {
    return `¥${(numeric / 10000).toFixed(1)}万`
  }
  return `¥${numeric.toLocaleString('zh-CN')}`
}

function formatRatio(rate: number) {
  return `${(toNumber(rate) * 100).toFixed(1)}%`
}

function formatCount(value: number) {
  return toNumber(value).toLocaleString('zh-CN')
}

function formatMonthTick(value: string) {
  const month = value.split('-')[1]
  return `${month}月`
}

function formatMonthTitle(value: string) {
  const [year, month] = value.split('-')
  return `${year} 年 ${month} 月`
}

function formatFinanceTrendRatio(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return '暂无基线'
  return formatRatio(value)
}

function buildTooltipMarker(color: string) {
  return `<span style="display:inline-block;margin-right:6px;border-radius:999px;width:10px;height:10px;background:${color};"></span>`
}

function truncateLabel(value: string, maxLength: number) {
  if (value.length <= maxLength) return value
  return `${value.slice(0, maxLength)}…`
}

function getTaxStatusLabel(status: TaxPaymentStatus) {
  if (status === 1) return '已缴纳'
  if (status === 2) return '免征 / 零申报'
  return '待缴纳'
}

function getTaxStatusClass(status: TaxPaymentStatus) {
  if (status === 1) return 'is-paid'
  if (status === 2) return 'is-exempt'
  return 'is-unpaid'
}

function toNumber(value: number | string | undefined | null) {
  const numeric = Number(value ?? 0)
  return Number.isFinite(numeric) ? numeric : 0
}
</script>

<template>
  <div ref="dashboardRootRef" class="dashboard-view">
    <section class="dashboard-header ds-card">
      <div class="dashboard-header__main">
        <div class="dashboard-header__meta">
          <span class="hero-badge">Owner Analytics</span>
          <span class="header-chip header-chip--company">{{ companyLabel }}</span>
          <span class="header-chip">{{ activeTabLabel }}</span>
          <span class="header-chip">{{ activeRangeLabel }}</span>
        </div>

        <div class="dashboard-header__copy">
          <h1>数据看板</h1>
          <p>让数据说话，让决策有据。</p>
        </div>
      </div>

      <el-button
        type="primary"
        plain
        :icon="Download"
        :loading="exporting"
        :disabled="!canExportDashboard"
        class="dashboard-header__export"
        data-pdf-hide
        @click="handleExportDashboard"
      >
        导出 PDF 报告
      </el-button>
    </section>

    <el-tabs
      v-model="activeTab"
      class="dashboard-tabs"
      :before-leave="handleBeforeTabLeave"
      @tab-change="handleTabChange"
    >
      <el-tab-pane label="财务剖析" name="finance" lazy>
        <section class="tab-shell">
          <div class="tab-toolbar">
            <div class="tab-toolbar__copy">
              <span class="tab-eyebrow">当前视角</span>
              <h2>收入集中度与利润弹性</h2>
              <p>先回答经营依赖是否过高，再判断成本吞噬和利润趋势有没有开始收紧。</p>
            </div>

            <div class="toolbar-actions">
              <span v-if="financeState.loading" class="loading-pill">更新中</span>
              <el-select
                v-model="financeState.range"
                size="small"
                class="range-select"
                :disabled="financeState.loading || exporting"
                @change="handleFinanceRangeChange"
              >
                <el-option
                  v-for="option in financeRangeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </div>
          </div>

          <div v-if="financeState.loading && !financeState.data && showCurrentLoadingSkeleton" class="loading-grid">
            <div class="loading-card ds-card" />
            <div class="loading-card ds-card" />
            <div class="loading-panel ds-card" />
            <div class="loading-panel ds-card" />
          </div>

          <div v-else-if="financeState.error && !financeState.data" class="state-panel ds-card">
            <h3>财务剖析暂时不可用</h3>
            <p>{{ financeState.error }}</p>
            <el-button type="primary" :icon="RefreshRight" @click="fetchFinanceData">重新加载</el-button>
          </div>

          <div v-else-if="!financeHasData" class="state-panel ds-card">
            <h3>还没有可分析的财务数据</h3>
            <p>先录入财务账本中的收入与支出流水，这里会自动切出成本结构和前五大收入来源。</p>
          </div>

          <template v-else-if="financeState.data">
            <!-- 顶区：KPI卡片 -->
            <section class="signal-grid-top">
              <article class="signal-card signal-card--finance-structure ds-card">
                <span class="signal-label">Top3 收入占比</span>
                <strong class="signal-value">{{ financeSourceCount > 0 ? formatRatio(financeTop3Share) : '—' }}</strong>
                <p>看收入依赖高不高</p>
              </article>

              <article class="signal-card signal-card--finance-pressure ds-card" :class="financeCoverageRatio >= 0.7 ? 'is-alert' : ''">
                <span class="signal-label">成本吞噬率</span>
                <strong class="signal-value" :class="financeCoverageRatio >= 0.7 ? 'expense' : ''">
                  {{ toNumber(financeState.data.totalIncome) > 0 ? formatRatio(financeCoverageRatio) : '—' }}
                </strong>
                <p>看利润空间是否收窄</p>
              </article>

              <article class="signal-card signal-card--finance-balance ds-card" :class="financeNetSpread < 0 ? 'is-alert' : 'is-positive'">
                <span class="signal-label">净流差</span>
                <strong class="signal-value" :class="financeNetSpread >= 0 ? 'income' : 'expense'">
                  {{ formatSignedCurrency(financeNetSpread) }}
                </strong>
                <p>直接看当前盈亏余量</p>
              </article>

              <article
                class="signal-card signal-card--finance-delta ds-card"
                :class="financeComparisonTone === 'income' ? 'is-positive' : financeComparisonTone === 'expense' ? 'is-alert' : ''"
              >
                <span class="signal-label">利润较上一周期变化</span>
                <strong
                  class="signal-value"
                  :class="financeComparisonTone === 'income' ? 'income' : financeComparisonTone === 'expense' ? 'expense' : ''"
                >
                  {{ financeState.data.periodComparison ? formatSignedCurrency(financeProfitChange) : '—' }}
                </strong>
                <p>对比上一周期弹性</p>
              </article>
            </section>

            <!-- 中区：全宽趋势大图 -->
            <article class="panel-card ds-card panel-card--full panel-card--wide panel-card--finance-trend dashboard-spacer">
              <div class="panel-header panel-header--trend">
                <div>
                  <h3>{{ financeTrendPanelTitle }}</h3>
                  <p>{{ financeTrendPanelDescription }}</p>
                </div>
                <div
                  v-if="financeState.data.monthlyTrend.length"
                  class="chart-view-switch"
                  role="tablist"
                  aria-label="财务趋势图视图切换"
                >
                  <button
                    v-for="option in financeTrendModeOptions"
                    :key="option.value"
                    type="button"
                    class="chart-view-switch__button"
                    :class="{ 'is-active': financeTrendMode === option.value }"
                    :aria-pressed="financeTrendMode === option.value"
                    @click="financeTrendMode = option.value"
                  >
                    {{ option.label }}
                  </button>
                </div>
              </div>
              <div
                v-if="financeState.data.monthlyTrend.length"
                ref="financeTrendChartRef"
                class="chart-box chart-box--flagship chart-box--trend-secondary"
              />
              <div v-else class="panel-empty panel-empty--compact">
                <h4>暂无月度趋势</h4>
                <p>{{ financeTrendEmptyDescription }}</p>
              </div>
            </article>

            <!-- 底部：双列并排 -->
            <section class="dashboard-secondary-grid">
              <article class="panel-card ds-card panel-card--secondary">
                <div class="panel-header panel-header--feature">
                  <div>
                    <h3>收入集中度</h3>
                    <p>{{ financeConcentrationHeadline }}</p>
                  </div>
                  <div class="panel-inline-metric" :class="`is-${financeTop1Tone}`">
                    <span class="panel-inline-metric__label">Top1 占比</span>
                    <strong>{{ financeSourceCount > 0 ? formatRatio(financeTop1Share) : '—' }}</strong>
                    <span v-if="financeSourceCount > 0" class="panel-inline-metric__divider" aria-hidden="true">·</span>
                    <span class="panel-inline-metric__note">{{ financeTop1Note }}</span>
                  </div>
                </div>
                <div
                  v-if="financeState.data.topIncomeSources.length"
                  ref="financeIncomeChartRef"
                  class="chart-box chart-box--secondary chart-box--pareto"
                />
                <div v-else class="panel-empty">
                  <h4>暂无收入集中度图</h4>
                  <p>当前范围内还没有形成可用于判断依赖度的收入来源。</p>
                </div>
              </article>

              <article class="panel-card ds-card panel-card--secondary panel-card--finance-expense">
                <div class="panel-header">
                  <div>
                    <h3>支出压力排序</h3>
                    <p>把成本按金额重新排队，帮助快速判断该先盯住哪一块支出。</p>
                  </div>
                </div>
                <div
                  v-if="financeState.data.expenseBreakdown.length"
                  ref="financeExpenseChartRef"
                  class="chart-box chart-box--secondary chart-box--compact"
                />
                <div v-else class="panel-empty panel-empty--compact">
                  <h4>暂无支出排序</h4>
                  <p>当前范围内没有支出记录，暂时无法判断成本压力来源。</p>
                </div>
              </article>
            </section>
          </template>
        </section>
      </el-tab-pane>

      <el-tab-pane label="人事洞察" name="hr" lazy>
        <section class="tab-shell">
          <div class="tab-toolbar">
            <div class="tab-toolbar__copy">
              <span class="tab-eyebrow">当前视角</span>
              <h2>当前团队结构画像</h2>
              <p>只基于现有在职名册，先回答当前薪资包主要压在哪些部门、团队单价是否偏高。</p>
            </div>

            <div v-if="hrState.loading" class="toolbar-actions">
              <span class="loading-pill">更新中</span>
            </div>
          </div>

          <div v-if="hrState.loading && !hrState.data && showCurrentLoadingSkeleton" class="loading-grid">
            <div class="loading-card ds-card" />
            <div class="loading-card ds-card" />
            <div class="loading-panel ds-card" />
            <div class="loading-panel ds-card" />
          </div>

          <div v-else-if="hrState.error && !hrState.data" class="state-panel ds-card">
            <h3>人事洞察暂时不可用</h3>
            <p>{{ hrState.error }}</p>
            <el-button type="primary" :icon="RefreshRight" @click="fetchHrData">重新加载</el-button>
          </div>

          <div v-else-if="!hrHasData" class="state-panel ds-card">
            <h3>还没有可分析的员工数据</h3>
            <p>录入员工名册后，这里会生成当前团队的部门结构、薪资负担和重点部门画像。</p>
          </div>

          <template v-else-if="hrState.data">
            <!-- 顶区：KPI卡片 -->
            <section class="signal-grid-top">
              <article class="signal-card ds-card signal-card--hr-size">
                <span class="signal-label">当前在职人数</span>
                <strong class="signal-value">{{ formatCount(hrState.data.activeEmployeeCount) }} 人</strong>
                <p>当前在职口径统计</p>
              </article>

              <article class="signal-card ds-card signal-card--hr-cost">
                <span class="signal-label">当前基础薪资总额</span>
                <strong class="signal-value income">{{ formatCurrency(hrState.data.activeSalaryTotal) }}</strong>
                <p>观察固定人力成本</p>
              </article>

              <article class="signal-card ds-card signal-card--hr-average">
                <span class="signal-label">人均基础薪资</span>
                <strong class="signal-value">{{ formatCurrency(hrAverageSalary) }}</strong>
                <p>判断团队单价高低</p>
              </article>

              <article class="signal-card ds-card signal-card--hr-focus">
                <span class="signal-label">最高负担部门</span>
                <strong class="signal-value">{{ hrTopDepartment ? formatRatio(hrTopDepartment.ratio) : '—' }}</strong>
                <p>看成本重心压在哪</p>
              </article>
            </section>

            <!-- 底部：双列对照 -->
            <section class="dashboard-secondary-grid">
              <article class="panel-card ds-card panel-card--secondary">
                <div class="panel-header panel-header--feature">
                  <div>
                    <h3>部门薪资负担排序</h3>
                    <p>先看当前基础薪资主要压在哪些部门，再判断人力成本是否集中。</p>
                  </div>
                  <div
                    class="panel-inline-metric panel-inline-metric--hr"
                    :class="`is-${hrDepartmentSummaryTone}`"
                    :title="hrDepartmentSummaryTitle"
                  >
                    <span class="panel-inline-metric__label">压力集中在</span>
                    <span class="panel-inline-metric__entity">{{ hrDepartmentSummaryDepartment }}</span>
                    <template v-if="hrTopDepartment">
                      <span class="panel-inline-metric__divider" aria-hidden="true">·</span>
                      <strong>{{ formatRatio(hrTopDepartment.ratio) }}</strong>
                    </template>
                  </div>
                </div>

                <div
                  v-if="hrState.data.departmentSalaryShare.length"
                  ref="hrDepartmentChartRef"
                  class="chart-box chart-box--secondary chart-box--hr-burden"
                />
                <div v-else class="panel-empty panel-empty--compact">
                  <h4>暂无团队结构</h4>
                  <p>当前没有可用于聚合的在职员工。</p>
                </div>
              </article>

              <article class="panel-card ds-card panel-card--secondary panel-card--hr-balance">
                <div class="panel-header">
                  <div>
                    <h3>部门人数与人均薪资对比</h3>
                    <p>把体量和单价放在一起看，到底是人数更大，还是单价更高。</p>
                  </div>
                </div>
                <div
                  v-if="hrState.data.departmentSalaryShare.length"
                  ref="hrTrendChartRef"
                  class="chart-box chart-box--secondary chart-box--hr-balance"
                />
                <div v-else class="panel-empty panel-empty--compact">
                  <h4>暂无部门对比</h4>
                  <p>当前没有足够的部门数据可用于比较人数和人均薪资。</p>
                </div>
              </article>
            </section>
          </template>
        </section>
      </el-tab-pane>

      <el-tab-pane label="税务健康" name="tax" lazy>
        <section class="tab-shell">
          <div class="tab-toolbar">
            <div class="tab-toolbar__copy">
              <span class="tab-eyebrow">当前视角</span>
              <h2>税负强度与待缴风险</h2>
              <p>先判断税负是否过重，再看风险是不是已经累积到需要处理的阶段。</p>
            </div>

            <div class="toolbar-actions">
              <span v-if="taxState.loading" class="loading-pill">更新中</span>
              <el-select
                v-model="taxState.range"
                size="small"
                class="range-select"
                :disabled="taxState.loading || exporting || !taxRangeOptions.length"
                @change="handleTaxRangeChange"
              >
                <el-option
                  v-for="option in taxRangeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </div>
          </div>

          <div v-if="taxState.loading && !taxState.data && showCurrentLoadingSkeleton" class="loading-grid">
            <div class="loading-card ds-card" />
            <div class="loading-card ds-card" />
            <div class="loading-panel ds-card" />
            <div class="loading-panel ds-card" />
          </div>

          <div v-else-if="taxState.error && !taxState.data" class="state-panel ds-card">
            <h3>税务健康暂时不可用</h3>
            <p>{{ taxState.error }}</p>
            <el-button type="primary" :icon="RefreshRight" @click="fetchTaxData">重新加载</el-button>
          </div>

          <div v-else-if="!taxHasData" class="state-panel ds-card">
            <h3>还没有可分析的税务数据</h3>
            <p>先录入税务档案或补充收入流水，这里会自动形成税负率与缴纳状态画像。</p>
          </div>

          <template v-else-if="taxState.data">
            <!-- 顶区：KPI卡片 -->
            <section class="signal-grid-top">
              <article class="signal-card signal-card--tax-rate ds-card">
                <span class="signal-label">当前税负率</span>
                <strong class="signal-value" :class="taxBurdenTone === 'danger' ? 'expense' : taxBurdenTone === 'healthy' ? 'income' : ''">
                  {{ toNumber(taxState.data.incomeBase) > 0 ? formatRatio(taxState.data.taxBurdenRate) : '暂无基线' }}
                </strong>
                <p>看税负强度高不高</p>
              </article>

              <article class="signal-card signal-card--tax-overdue ds-card" :class="{ 'is-alert': taxUnpaidRatio > 0 }">
                <span class="signal-label">待缴情形占比</span>
                <strong class="signal-value" :class="taxUnpaidRatio > 0 ? 'expense' : ''">
                  {{ toNumber(taxState.data.positiveTaxAmount) > 0 ? formatRatio(taxUnpaidRatio) : '0.0%' }}
                </strong>
                <p>看待缴压力重不重</p>
              </article>

              <article class="signal-card signal-card--tax-records ds-card" :class="{ 'is-alert': taxUnpaidRecordRatio > 0 }">
                <span class="signal-label">待缴情形记录占比</span>
                <strong class="signal-value" :class="taxUnpaidRecordRatio > 0 ? 'expense' : ''">
                  {{ taxStatusTotalCount > 0 ? formatRatio(taxUnpaidRecordRatio) : '0.0%' }}
                </strong>
                <p>看事项是否在堆积</p>
              </article>

              <article class="signal-card signal-card--tax-focus ds-card">
                <span class="signal-label">第一大税种占比</span>
                <strong class="signal-value" :class="taxTopTaxTypeShare >= 0.5 ? 'expense' : ''">
                  {{ taxTopTaxType ? formatRatio(taxTopTaxTypeShare) : '—' }}
                </strong>
                <p>锁定主要税种压力</p>
              </article>
            </section>

            <!-- 中间：主结构与状态合围 -->
            <section class="dashboard-secondary-grid" style="grid-template-columns: minmax(0, 1fr) minmax(320px, 1.25fr);">
              <!-- 左侧大卡：综合税负率 -->
              <article class="panel-card ds-card panel-card--secondary feature-primary--tax" style="display: flex; flex-direction: column;">
                <div class="panel-header panel-header--feature">
                  <div>
                    <span class="panel-kicker">旗舰主图</span>
                    <h3>综合税负率</h3>
                    <p>
                      用正向税额对正向收入基线，判断税负强度。
                    </p>
                  </div>
                  <div
                    class="panel-inline-metric panel-inline-metric--tax"
                    :class="`is-${taxGaugeSummaryTone}`"
                    :title="taxGaugeSummaryTitle"
                  >
                    <span class="panel-inline-metric__label">风险判断</span>
                    <span class="panel-inline-metric__entity">{{ taxGaugeSummaryValue }}</span>
                    <span class="panel-inline-metric__divider" aria-hidden="true">·</span>
                    <span class="panel-inline-metric__note">{{ taxGaugeSummaryNote }}</span>
                  </div>
                </div>
                <!-- 注入 flex-grow 以拉伸吸收右边可能造成的高落差 -->
                <div ref="taxGaugeChartRef" class="chart-box chart-box--secondary chart-box--gauge chart-box--tax-gauge" style="flex-grow: 1; min-height: 240px;" />
                <div class="gauge-notes gauge-notes--tax">
                  <div class="note-chip tone-healthy">0%-10%</div>
                  <div class="note-chip tone-warning">10%-20%</div>
                  <div class="note-chip tone-danger">20%+</div>
                </div>
                <p class="gauge-disclaimer">10% / 20% 为内部观察线，仅用于经营观察，不代表税务合规判定。</p>
                <p class="feature-caption feature-caption--tax" style="margin-top: 12px;">{{ taxRiskHeadline }}</p>
              </article>

              <!-- 右侧组合：税种结构 + 缴纳状态 -->
              <div class="tax-right-column" style="display: flex; flex-direction: column; gap: 16px;">
                <!-- 右上：税种结构 -->
                <article class="panel-card ds-card panel-card--secondary panel-card--tax-structure" style="flex: 1;">
                  <div class="panel-header">
                    <div>
                      <h3>税种结构排序</h3>
                      <p>确认压力主要来自哪里，退税暂不纳入结构统计。</p>
                    </div>
                    <div
                      class="panel-inline-metric panel-inline-metric--tax-type"
                      :class="`is-${taxTopTaxTypeTone}`"
                      :title="taxTopTaxTypeSummaryTitle"
                    >
                      <span class="panel-inline-metric__label">压力主要来自</span>
                      <span class="panel-inline-metric__entity">{{ taxTopTaxTypeSummaryName }}</span>
                      <template v-if="taxTopTaxType">
                        <span class="panel-inline-metric__divider" aria-hidden="true">·</span>
                        <strong>{{ formatRatio(taxTopTaxTypeShare) }}</strong>
                      </template>
                    </div>
                  </div>
                  <div
                    v-if="taxState.data.taxTypeStructure.length"
                    ref="taxTypeChartRef"
                    class="chart-box chart-box--secondary chart-box--tax-structure"
                  />
                  <div v-else class="panel-empty panel-empty--compact">
                    <h4>暂无税种结构</h4>
                    <p>当前范围内没有正向税额。</p>
                  </div>
                </article>

                <!-- 右下：缴情状态组成 -->
                <section class="status-overview ds-card">
                  <div class="panel-header">
                    <div>
                      <h3>缴情状态组成</h3>
                      <p>先判断风险是金额问题，还是待缴情记录开始堆积。</p>
                    </div>
                    <span class="status-overview__count">{{ formatCount(taxStatusTotalCount) }} 笔</span>
                  </div>

                  <div v-if="taxStatusTotalCount > 0" class="status-composition">
                    <div class="status-composition__track">
                      <span
                        v-for="item in taxStatusBreakdown"
                        :key="'status-segment-' + item.status"
                        class="status-composition__segment"
                        :class="getTaxStatusClass(item.status)"
                        :style="{ width: item.ratio * 100 + '%' }"
                      />
                    </div>
                    <div class="status-composition__legend">
                      <div
                        v-for="item in taxStatusBreakdown"
                        :key="'status-legend-' + item.status"
                        class="status-legend-item"
                      >
                        <span class="status-dot" :class="getTaxStatusClass(item.status)" />
                        <div class="status-legend-copy">
                          <span>{{ item.label }}</span>
                          <strong>{{ formatCount(item.count) }} 笔</strong>
                        </div>
                        <span class="status-legend-ratio">{{ formatRatio(item.ratio) }}</span>
                      </div>
                    </div>
                  </div>
                  <div v-else class="outstanding-empty">当前范围内暂无缴纳状态记录。</div>
                </section>
              </div>
            </section>
          </template>
        </section>
      </el-tab-pane>
    </el-tabs>

    <div v-if="showExportStage" class="pdf-export-stage" aria-hidden="true">
      <section ref="dashboardExportPageOneRef" class="pdf-page">
        <div class="pdf-page-header">
          <span class="hero-badge">Owner Analytics</span>
          <span class="hero-period">{{ companyLabel }} · {{ activeTabLabel }} · {{ activeRangeLabel }}</span>
        </div>

        <section class="pdf-hero-card ds-card">
          <div class="pdf-hero-copy">
            <span class="note-label">图表优先视图</span>
            <h2>{{ activeTabLabel }}</h2>
            <p>当前导出聚焦 {{ activeRangeLabel }} 口径下的主图与关键信号，适合会议汇报与离线阅读。</p>
          </div>
        </section>

          <template v-if="currentTabHasData">
          <template v-if="activeTab === 'finance' && financeState.data">
            <section class="pdf-summary-grid">
              <article class="summary-card ds-card">
                <span class="summary-label">Top3 收入占比</span>
                <strong class="summary-value">{{ financeSourceCount > 0 ? formatRatio(financeTop3Share) : '—' }}</strong>
                <p>先判断当前经营是否过度依赖少数来源。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">成本吞噬率</span>
                <strong class="summary-value" :class="financeCoverageRatio >= 0.7 ? 'expense' : ''">
                  {{ toNumber(financeState.data.totalIncome) > 0 ? formatRatio(financeCoverageRatio) : '—' }}
                </strong>
                <p>收入被成本吃掉的比例，直接反映利润弹性。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">净流差</span>
                <strong class="summary-value" :class="financeNetSpread >= 0 ? 'income' : 'expense'">
                  {{ formatSignedCurrency(financeNetSpread) }}
                </strong>
                <p>收入减支出的直接结果，适合会议汇报时快速判断空间。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">利润较上一周期变化</span>
                <strong
                  class="summary-value"
                  :class="financeComparisonTone === 'income' ? 'income' : financeComparisonTone === 'expense' ? 'expense' : ''"
                >
                  {{ financeState.data.periodComparison ? formatSignedCurrency(financeProfitChange) : '—' }}
                </strong>
                <p>{{ financeComparisonHeadline }}</p>
              </article>
            </section>

            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h3>收入集中度</h3>
                  <p>把收入金额与累计贡献放在同一张图里，第一眼就能判断依赖是否过高。</p>
                </div>
              </div>

              <div v-if="exportChartImages.financeIncome" class="pdf-chart-frame">
                <img :src="exportChartImages.financeIncome" alt="收入集中度图" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
                <h4>暂无收入集中度图</h4>
                <p>当前范围内还没有收入记录。</p>
              </div>
            </article>
          </template>

          <template v-else-if="activeTab === 'hr' && hrState.data">
            <section class="pdf-summary-grid">
              <article class="summary-card ds-card">
                <span class="summary-label">当前在职人数</span>
                <strong class="summary-value">{{ formatCount(hrState.data.activeEmployeeCount) }} 人</strong>
                <p>按当前 `status=1` 的员工记录统计。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">当前基础薪资总额</span>
                <strong class="summary-value income">{{ formatCurrency(hrState.data.activeSalaryTotal) }}</strong>
                <p>用于观察固定人力成本的当前压力。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">人均基础薪资</span>
                <strong class="summary-value">{{ formatCurrency(hrAverageSalary) }}</strong>
                <p>帮助判断当前团队的人力单价是否偏高。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">最高负担部门</span>
                <strong class="summary-value">{{ hrTopDepartment ? formatRatio(hrTopDepartment.ratio) : '—' }}</strong>
                <p>
                  {{
                    hrTopDepartment
                      ? hrTopDepartment.department + ' 当前承担最多基础薪资负担。'
                      : '等待部门结构形成后再判断负担重心。'
                  }}
                </p>
              </article>
            </section>

            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h3>部门薪资负担排序</h3>
                  <p>第一页先看当前基础薪资主要压在哪些部门，再判断人力成本是否过度集中。</p>
                </div>
              </div>

              <div v-if="exportChartImages.hrDepartment" class="pdf-chart-frame">
                <img :src="exportChartImages.hrDepartment" alt="部门薪资负担排序图" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
                <h4>暂无团队结构</h4>
                <p>当前没有可用于聚合的在职员工。</p>
              </div>
            </article>
          </template>

          <template v-else-if="taxState.data">
            <section class="pdf-summary-grid">
              <article class="summary-card ds-card">
                <span class="summary-label">正向税额合计</span>
                <strong class="summary-value">{{ formatCurrency(taxState.data.positiveTaxAmount) }}</strong>
                <p>只统计大于 0 的税额，用于税负率和税种结构。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">待缴税额</span>
                <strong class="summary-value expense">{{ formatCurrency(taxState.data.unpaidTaxAmount) }}</strong>
                <p>当前范围内待缴且税额为正的风险金额。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">当前税负率</span>
                <strong class="summary-value" :class="taxBurdenTone === 'danger' ? 'expense' : taxBurdenTone === 'healthy' ? 'income' : ''">
                  {{ toNumber(taxState.data.incomeBase) > 0 ? formatRatio(taxState.data.taxBurdenRate) : '暂无基线' }}
                </strong>
                <p>判断当前税负强度是否已经产生经营压力。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">待缴情形占比</span>
                <strong class="summary-value" :class="taxUnpaidRatio > 0 ? 'expense' : ''">
                  {{ toNumber(taxState.data.positiveTaxAmount) > 0 ? formatRatio(taxUnpaidRatio) : '0.0%' }}
                </strong>
                <p>待缴税额占产生正向税额的比例，确认风险影响面。</p>
              </article>
            </section>

            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h3>综合税负率</h3>
                  <p>口径 = 正向税额 / 正向收入基线。</p>
                </div>
              </div>

              <div v-if="exportChartImages.taxGauge" class="pdf-chart-frame">
                <img :src="exportChartImages.taxGauge" alt="综合税负率仪表盘" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
                <h4>暂无税负率图表</h4>
                <p>当前范围内没有足够数据生成税负率图表。</p>
              </div>

              <div class="gauge-notes">
                <div class="note-chip tone-healthy">0%-10% 观察线</div>
                <div class="note-chip tone-warning">10%-20% 关注线</div>
                <div class="note-chip tone-danger">20%+ 高压线</div>
              </div>
            </article>
          </template>
        </template>

        <article v-else class="pdf-card ds-card pdf-state-card">
          <div class="panel-header">
            <div>
              <h3>{{ currentTabEmptyState.title }}</h3>
              <p>{{ currentTabEmptyState.description }}</p>
            </div>
          </div>
        </article>
      </section>

      <section
        v-if="currentTabHasData"
        ref="dashboardExportPageTwoRef"
        :class="['pdf-page', { 'pdf-page--finance-secondary': activeTab === 'finance' && financeState.data }]"
      >
        <div class="pdf-page-header">
          <div>
            <span class="note-label">续页</span>
            <h3 class="pdf-section-title">{{ activeTabLabel }}</h3>
          </div>
          <span class="hero-period">{{ activeRangeLabel }}</span>
        </div>

        <template v-if="activeTab === 'finance' && financeState.data">
          <section class="pdf-finance-stack">
            <article class="pdf-card ds-card pdf-card--finance-secondary">
              <div class="panel-header panel-header--pdf-compact">
                <div>
                  <h3>{{ financeTrendPanelTitle }}</h3>
                  <p>{{ financeTrendPanelDescription }}</p>
                </div>
              </div>

              <div v-if="exportChartImages.financeTrend" class="pdf-chart-frame pdf-chart-frame--finance-secondary">
                <img
                  :src="exportChartImages.financeTrend"
                  alt="财务月度趋势图"
                  class="pdf-chart-image pdf-chart-image--finance-secondary"
                />
              </div>
              <div v-else class="panel-empty compact panel-empty--pdf-finance">
                <h4>暂无月度趋势</h4>
                <p>{{ financeTrendEmptyDescription }}</p>
              </div>
            </article>

            <article class="pdf-card ds-card pdf-card--finance-secondary">
              <div class="panel-header panel-header--pdf-compact">
                <div>
                  <h3>支出压力排序</h3>
                  <p>第二层看成本主要压在哪，帮助快速决定先盯住哪一项支出。</p>
                </div>
              </div>

              <div
                v-if="exportChartImages.financeExpense"
                class="pdf-chart-frame pdf-chart-frame--finance-secondary"
              >
                <img
                  :src="exportChartImages.financeExpense"
                  alt="支出压力排序图"
                  class="pdf-chart-image pdf-chart-image--finance-secondary"
                />
              </div>
              <div v-else class="panel-empty compact panel-empty--pdf-finance">
                <h4>暂无支出排序</h4>
                <p>当前范围内没有支出记录。</p>
              </div>
            </article>
          </section>
        </template>

        <template v-else-if="activeTab === 'hr' && hrState.data">
          <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h3>部门人数与人均基础薪资</h3>
                  <p>第二层把部门体量与人均单价放在一起看，判断成本究竟由规模还是岗位单价驱动。</p>
                </div>
              </div>

              <div v-if="exportChartImages.hrTrend" class="pdf-chart-frame">
                <img :src="exportChartImages.hrTrend" alt="部门人数与人均基础薪资图" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
                <h4>暂无部门对比</h4>
                <p>当前没有足够的部门数据可用于比较人数和人均薪资。</p>
              </div>
          </article>
        </template>

        <template v-else-if="taxState.data">
          <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h3>税种结构</h3>
                  <p>负数退税不进入结构分布，但会保留在下方状态摘要金额里。</p>
                </div>
              </div>

              <div v-if="exportChartImages.taxType" class="pdf-chart-frame">
                <img :src="exportChartImages.taxType" alt="税种结构图" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
                <h4>暂无税种结构</h4>
                <p>当前范围内没有正向税额。</p>
              </div>
          </article>

          <section class="pdf-status-grid">
            <article
              v-for="item in taxState.data.statusSummary"
              :key="'pdf-' + item.status"
              class="status-card ds-card"
              :class="getTaxStatusClass(item.status)"
            >
              <div class="status-top">
                <span class="status-label">{{ getTaxStatusLabel(item.status) }}</span>
                <span class="status-count">{{ formatCount(item.count) }} 笔</span>
              </div>
              <div class="status-value">{{ formatCurrency(item.amount) }}</div>
              <p>展示当前统计范围内该缴纳状态的记录金额总和。</p>
            </article>
          </section>
        </template>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dashboard-header {
  min-height: 108px;
  padding: 18px 22px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px 18px;
  align-items: end;
  overflow: hidden;
  background:
    radial-gradient(circle at top right, rgba(0, 117, 222, 0.14), transparent 32%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 44%, #f6f5f4 100%);
}

.dashboard-header__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dashboard-header__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 9999px;
  background: #f2f9ff;
  color: #097fe8;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.125px;
}

.header-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 4px 10px;
  border-radius: 9999px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.74);
  color: #615d59;
  font-size: 12px;
  font-weight: 600;
}

.header-chip--company {
  border-color: rgba(9, 127, 232, 0.14);
  background: rgba(242, 249, 255, 0.9);
  color: #097fe8;
}

.dashboard-header__copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dashboard-header__copy h1 {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.875px;
  color: rgba(0, 0, 0, 0.95);
}

.dashboard-header__copy p {
  max-width: 56ch;
  color: #615d59;
  font-size: 14px;
  line-height: 1.6;
}

.note-label {
  font-size: 12px;
  font-weight: 600;
  color: #a39e98;
  letter-spacing: 0.125px;
}

.dashboard-header__export.el-button {
  min-height: 42px;
  padding-inline: 18px;
  border-radius: 14px;
  align-self: end;
  justify-self: end;
  border-color: rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.04);
}

.hero-period {
  color: #615d59;
  font-size: 13px;
  font-weight: 500;
}

.dashboard-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.dashboard-tabs :deep(.el-tabs__nav-wrap::after) {
  background: rgba(0, 0, 0, 0.08);
}

.dashboard-tabs :deep(.el-tabs__item) {
  height: 40px;
  font-weight: 600;
  color: #615d59;
}

.dashboard-tabs :deep(.el-tabs__item.is-active) {
  color: rgba(0, 0, 0, 0.95);
}

.dashboard-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 9999px;
}

.tab-shell {
  padding-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}

.tab-toolbar__copy {
  max-width: 60ch;
}

.tab-eyebrow {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #8892a5;
}

.tab-toolbar h2 {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.95);
  letter-spacing: -0.25px;
}

.tab-toolbar p {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: #615d59;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.range-select {
  width: 138px;
}

.loading-pill {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 9999px;
  background: #f6f5f4;
  color: #615d59;
  font-size: 12px;
  font-weight: 600;
}

.feature-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.88fr);
  gap: 16px;
  align-items: stretch;
}

.feature-layout--finance-diagnostic {
  grid-template-columns: minmax(0, 1.4fr) minmax(340px, 0.92fr);
}

.feature-layout--hr {
  grid-template-columns: minmax(0, 1.32fr) minmax(320px, 0.92fr);
}

.feature-primary--finance {
  position: relative;
  overflow: hidden;
  border-color: rgba(19, 117, 209, 0.14);
  background:
    radial-gradient(circle at 16% 18%, rgba(19, 117, 209, 0.12), transparent 30%),
    radial-gradient(circle at 88% 12%, rgba(42, 157, 153, 0.1), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 250, 255, 0.98));
  box-shadow:
    0 24px 56px rgba(19, 117, 209, 0.08),
    0 12px 28px rgba(15, 23, 42, 0.06);
}

.feature-primary--finance::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 4px;
  background: linear-gradient(90deg, rgba(19, 117, 209, 0.92), rgba(42, 157, 153, 0.76));
}

.feature-primary--finance .panel-header h3 {
  font-size: 24px;
  letter-spacing: -0.4px;
}

.feature-primary--finance .panel-header p {
  max-width: 34ch;
}

.feature-primary--hr {
  position: relative;
  overflow: hidden;
  border-color: rgba(19, 117, 209, 0.12);
  background:
    radial-gradient(circle at 14% 18%, rgba(19, 117, 209, 0.1), transparent 28%),
    radial-gradient(circle at 90% 12%, rgba(221, 91, 0, 0.08), transparent 26%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 250, 255, 0.98));
  box-shadow:
    0 20px 48px rgba(19, 117, 209, 0.06),
    0 12px 26px rgba(15, 23, 42, 0.05);
}

.feature-primary--hr::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 4px;
  background: linear-gradient(90deg, rgba(19, 117, 209, 0.9), rgba(221, 91, 0, 0.68));
}

.feature-primary--hr .panel-header h3 {
  font-size: 24px;
  letter-spacing: -0.4px;
}

.feature-primary--hr .panel-header p {
  max-width: 35ch;
}

.feature-layout--tax {
  grid-template-columns: minmax(0, 1.18fr) minmax(300px, 0.92fr);
}

.feature-primary,
.panel-card {
  padding: 22px 24px;
}

.feature-primary--tax {
  background:
    radial-gradient(circle at top right, rgba(0, 117, 222, 0.08), transparent 34%),
    linear-gradient(180deg, #ffffff 0%, #fbfcff 100%);
}

.feature-sidebar {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 16px;
}

.feature-sidebar--finance {
  gap: 0;
}

.feature-sidebar--hr {
  gap: 16px;
}

.signal-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  flex-grow: 1;
  align-content: stretch;
}

.signal-grid--finance {
  align-items: stretch;
}

.signal-grid--hr {
  gap: 14px;
}

.finance-secondary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.85fr);
  gap: 16px;
}

.signal-grid-top {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
}

@media (max-width: 1200px) {
  .signal-grid-top {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.dashboard-secondary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.dashboard-spacer {
  margin-bottom: 18px;
}

.signal-card {
  position: relative;
  padding: 16px 18px 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 245, 244, 0.92));
  min-height: 140px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.signal-card::before {
  content: '';
  position: absolute;
  left: 18px;
  top: 0;
  width: 42px;
  height: 3px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.08);
}

.signal-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 30px rgba(15, 23, 42, 0.07);
}

.signal-card--finance-structure {
  border-color: rgba(19, 117, 209, 0.12);
  background: linear-gradient(180deg, rgba(248, 252, 255, 0.98), rgba(241, 248, 255, 0.96));
}

.signal-card--finance-structure::before {
  background: rgba(19, 117, 209, 0.78);
}

.signal-card--finance-pressure {
  border-color: rgba(221, 91, 0, 0.1);
  background: linear-gradient(180deg, rgba(255, 251, 247, 0.98), rgba(252, 245, 239, 0.96));
}

.signal-card--finance-pressure::before {
  background: rgba(221, 91, 0, 0.72);
}

.signal-card--finance-balance::before {
  background: rgba(42, 157, 153, 0.7);
}

.signal-card--finance-delta {
  background: linear-gradient(180deg, rgba(249, 250, 255, 0.98), rgba(243, 245, 252, 0.96));
}

.signal-card--finance-delta::before {
  background: rgba(33, 49, 131, 0.72);
}

.signal-card--hr-size {
  border-color: rgba(19, 117, 209, 0.12);
  background: linear-gradient(180deg, rgba(248, 252, 255, 0.98), rgba(241, 248, 255, 0.96));
}

.signal-card--hr-size::before {
  background: rgba(19, 117, 209, 0.78);
}

.signal-card--hr-cost {
  border-color: rgba(42, 157, 153, 0.1);
  background: linear-gradient(180deg, rgba(247, 252, 251, 0.98), rgba(240, 248, 246, 0.96));
}

.signal-card--hr-cost::before {
  background: rgba(42, 157, 153, 0.72);
}

.signal-card--hr-average {
  border-color: rgba(33, 49, 131, 0.08);
  background: linear-gradient(180deg, rgba(249, 250, 255, 0.98), rgba(243, 245, 252, 0.96));
}

.signal-card--hr-average::before {
  background: rgba(33, 49, 131, 0.72);
}

.signal-card--hr-focus {
  border-color: rgba(221, 91, 0, 0.1);
  background: linear-gradient(180deg, rgba(255, 251, 247, 0.98), rgba(252, 245, 239, 0.96));
}

.signal-card--hr-focus::before {
  background: rgba(221, 91, 0, 0.72);
}

.signal-card--tax-rate {
  border-color: rgba(19, 117, 209, 0.12);
  background: linear-gradient(180deg, rgba(248, 252, 255, 0.98), rgba(241, 248, 255, 0.96));
}

.signal-card--tax-rate::before {
  background: rgba(19, 117, 209, 0.78);
}

.signal-card--tax-overdue {
  border-color: rgba(221, 91, 0, 0.1);
  background: linear-gradient(180deg, rgba(255, 251, 247, 0.98), rgba(252, 245, 239, 0.96));
}

.signal-card--tax-overdue::before {
  background: rgba(221, 91, 0, 0.72);
}

.signal-card--tax-overdue.is-alert {
  border-color: rgba(221, 91, 0, 0.16);
  box-shadow: 0 14px 30px rgba(221, 91, 0, 0.08);
}

.signal-card--tax-records {
  border-color: rgba(33, 49, 131, 0.08);
  background: linear-gradient(180deg, rgba(249, 250, 255, 0.98), rgba(243, 245, 252, 0.96));
}

.signal-card--tax-records::before {
  background: rgba(33, 49, 131, 0.72);
}

.signal-card--tax-records.is-alert {
  border-color: rgba(33, 49, 131, 0.14);
  box-shadow: 0 14px 30px rgba(33, 49, 131, 0.08);
}

.signal-card--tax-focus {
  border-color: rgba(42, 157, 153, 0.1);
  background: linear-gradient(180deg, rgba(247, 252, 251, 0.98), rgba(240, 248, 246, 0.96));
}

.signal-card--tax-focus::before {
  background: rgba(42, 157, 153, 0.72);
}

.signal-card.is-positive {
  border-color: rgba(42, 157, 153, 0.14);
}

.signal-card.is-alert {
  border-color: rgba(209, 73, 91, 0.16);
  box-shadow: 0 14px 30px rgba(209, 73, 91, 0.08);
}

.signal-label {
  width: 100%;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.45;
  color: #615d59;
}

.signal-value {
  margin-top: 0;
  display: block;
  width: 100%;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.12;
  letter-spacing: -0.4px;
  color: rgba(0, 0, 0, 0.95);
  font-variant-numeric: tabular-nums;
}

.signal-value.income {
  color: #2a9d99;
}

.signal-value.expense {
  color: #e03e3e;
}

.signal-card p {
  margin-top: 0;
  width: 100%;
  font-size: 12px;
  line-height: 1.6;
  color: #615d59;
  max-width: 100%;
}

.panel-card--secondary {
  background: linear-gradient(180deg, #ffffff 0%, #faf9f8 100%);
}

.panel-card--full {
  padding: 22px 24px;
}

.panel-card--wide {
  min-width: 0;
}

.panel-card--finance-trend {
  border-color: rgba(33, 49, 131, 0.1);
  background:
    radial-gradient(circle at top right, rgba(33, 49, 131, 0.06), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 248, 252, 0.98));
}

.panel-card--finance-expense {
  border-color: rgba(221, 91, 0, 0.1);
  background:
    radial-gradient(circle at top right, rgba(221, 91, 0, 0.06), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(251, 248, 245, 0.98));
}

.panel-card--hr-balance {
  border-color: rgba(33, 49, 131, 0.08);
  background:
    radial-gradient(circle at top right, rgba(33, 49, 131, 0.06), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 248, 252, 0.98));
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.summary-card {
  padding: 22px 24px;
}

.summary-label {
  font-size: 13px;
  font-weight: 600;
  color: #615d59;
}

.summary-value {
  margin-top: 10px;
  display: block;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.5px;
  color: rgba(0, 0, 0, 0.95);
  font-variant-numeric: tabular-nums;
}

.summary-value.income {
  color: #2a9d99;
}

.summary-value.expense {
  color: #e03e3e;
}

.summary-card p {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #615d59;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.panel-header--feature {
  align-items: flex-start;
}

.panel-header--trend {
  align-items: flex-start;
  flex-wrap: wrap;
}

.panel-kicker {
  display: inline-flex;
  margin-bottom: 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #8892a5;
}

.panel-header h3 {
  font-size: 20px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.95);
  letter-spacing: -0.25px;
}

.panel-header p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #615d59;
}

.chart-view-switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 9999px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(246, 245, 244, 0.92);
  flex-shrink: 0;
}

.chart-view-switch__button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: #615d59;
  padding: 8px 12px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  cursor: pointer;
  transition:
    background 0.18s ease,
    color 0.18s ease,
    box-shadow 0.18s ease;
}

.chart-view-switch__button:hover {
  color: #213183;
}

.chart-view-switch__button.is-active {
  color: #213183;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.panel-inline-note {
  display: inline-flex;
  align-items: center;
  margin-left: 6px;
  padding: 2px 8px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
}

.panel-inline-metric {
  flex-shrink: 0;
  max-width: min(100%, 320px);
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 9999px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(246, 245, 244, 0.76);
}

.panel-inline-metric__label,
.panel-inline-metric__entity,
.panel-inline-metric__note,
.panel-inline-metric__divider {
  font-size: 12px;
  line-height: 1.4;
}

.panel-inline-metric__label {
  font-weight: 600;
  color: #615d59;
}

.panel-inline-metric strong {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.25px;
  color: rgba(0, 0, 0, 0.95);
  font-variant-numeric: tabular-nums;
}

.panel-inline-metric__entity {
  min-width: 0;
  font-weight: 600;
  color: #615d59;
}

.panel-inline-metric__divider {
  color: rgba(97, 93, 89, 0.58);
}

.panel-inline-metric__note {
  color: #615d59;
}

.panel-inline-metric--hr {
  max-width: min(100%, 360px);
  min-width: 0;
  flex-wrap: nowrap;
}

.panel-inline-metric--hr .panel-inline-metric__entity {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-inline-metric--tax {
  max-width: min(100%, 340px);
  min-width: 0;
}

.panel-inline-metric--tax-type {
  max-width: min(100%, 360px);
  min-width: 0;
  flex-wrap: nowrap;
}

.panel-inline-metric--tax-type .panel-inline-metric__entity {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-inline-metric.is-calm {
  border-color: rgba(42, 157, 153, 0.12);
  background: rgba(247, 252, 251, 0.88);
}

.panel-inline-metric.is-calm strong,
.panel-inline-metric.is-calm .panel-inline-metric__entity,
.panel-inline-metric.is-calm .panel-inline-metric__note {
  color: #2a9d99;
}

.panel-inline-metric.is-focus {
  border-color: rgba(19, 117, 209, 0.12);
  background: rgba(248, 252, 255, 0.9);
}

.panel-inline-metric.is-focus strong,
.panel-inline-metric.is-focus .panel-inline-metric__entity,
.panel-inline-metric.is-focus .panel-inline-metric__note {
  color: #1375d1;
}

.panel-inline-metric.is-warning {
  border-color: rgba(221, 91, 0, 0.14);
  background: rgba(255, 251, 247, 0.92);
}

.panel-inline-metric.is-warning strong,
.panel-inline-metric.is-warning .panel-inline-metric__entity,
.panel-inline-metric.is-warning .panel-inline-metric__note {
  color: #dd5b00;
}

.panel-inline-metric.is-healthy {
  border-color: rgba(26, 174, 57, 0.14);
  background: rgba(247, 252, 251, 0.9);
}

.panel-inline-metric.is-healthy strong,
.panel-inline-metric.is-healthy .panel-inline-metric__entity,
.panel-inline-metric.is-healthy .panel-inline-metric__note {
  color: #1aae39;
}

.panel-inline-metric.is-danger {
  border-color: rgba(224, 62, 62, 0.16);
  background: rgba(254, 247, 247, 0.94);
}

.panel-inline-metric.is-danger strong,
.panel-inline-metric.is-danger .panel-inline-metric__entity,
.panel-inline-metric.is-danger .panel-inline-metric__note {
  color: #e03e3e;
}

.panel-inline-metric.is-muted {
  color: #615d59;
}

.panel-metric {
  min-width: 152px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(0, 0, 0, 0.06);
  background: rgba(246, 245, 244, 0.92);
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.panel-metric span {
  font-size: 12px;
  font-weight: 600;
  color: #615d59;
}

.panel-metric strong {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.4px;
  color: rgba(0, 0, 0, 0.95);
  font-variant-numeric: tabular-nums;
}

.panel-metric.is-income strong {
  color: #2a9d99;
}

.panel-metric.is-expense strong,
.panel-metric.is-danger strong {
  color: #e03e3e;
}

.panel-metric.is-warning strong {
  color: #dd5b00;
}

.panel-metric.is-healthy strong {
  color: #1aae39;
}

.chart-box {
  margin-top: 18px;
  width: 100%;
  height: 340px;
  border-radius: 20px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

.chart-box--flagship {
  height: 420px;
}

.chart-box--pareto {
  height: 432px;
  background:
    linear-gradient(180deg, rgba(248, 252, 255, 0.96), rgba(255, 255, 255, 0.9)),
    linear-gradient(90deg, rgba(19, 117, 209, 0.04), transparent 36%);
  border-color: rgba(19, 117, 209, 0.08);
}

.chart-box--trend {
  height: 430px;
}

.chart-box--hr-burden {
  height: 432px;
  background:
    linear-gradient(180deg, rgba(247, 251, 255, 0.98), rgba(255, 255, 255, 0.92)),
    linear-gradient(90deg, rgba(19, 117, 209, 0.05), transparent 36%);
  border-color: rgba(19, 117, 209, 0.08);
}

.chart-box--trend-secondary {
  height: 360px;
  background:
    linear-gradient(180deg, rgba(248, 249, 255, 0.98), rgba(255, 255, 255, 0.92)),
    linear-gradient(90deg, rgba(33, 49, 131, 0.04), transparent 40%);
}

.chart-box--secondary {
  height: 320px;
}

.chart-box--compact {
  height: 300px;
  background:
    linear-gradient(180deg, rgba(255, 251, 247, 0.96), rgba(255, 255, 255, 0.92)),
    linear-gradient(90deg, rgba(221, 91, 0, 0.05), transparent 38%);
  border-color: rgba(221, 91, 0, 0.08);
}

.chart-box--hr-balance {
  height: 340px;
  background:
    linear-gradient(180deg, rgba(249, 250, 255, 0.98), rgba(255, 255, 255, 0.92)),
    linear-gradient(90deg, rgba(33, 49, 131, 0.04), transparent 40%);
  border-color: rgba(33, 49, 131, 0.08);
}

.chart-box--gauge {
  height: 360px;
}

.chart-box--tax-gauge {
  background:
    radial-gradient(circle at 50% 100%, rgba(0, 117, 222, 0.06), transparent 42%),
    linear-gradient(180deg, rgba(248, 250, 255, 0.98), rgba(255, 255, 255, 0.94));
  border-color: rgba(0, 117, 222, 0.08);
}

.chart-box--tax-structure {
  background:
    linear-gradient(180deg, rgba(249, 250, 255, 0.98), rgba(255, 255, 255, 0.94)),
    linear-gradient(90deg, rgba(33, 49, 131, 0.04), transparent 42%);
  border-color: rgba(33, 49, 131, 0.08);
}

.panel-empty {
  margin-top: 18px;
  min-height: 280px;
  border-radius: 16px;
  background: #f6f5f4;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px;
}

.panel-empty h4 {
  font-size: 18px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.92);
}

.panel-empty p {
  margin-top: 10px;
  max-width: 280px;
  font-size: 14px;
  line-height: 1.7;
  color: #615d59;
}

.panel-empty--compact {
  min-height: 230px;
}

.state-panel {
  padding: 28px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.state-panel h3 {
  font-size: 22px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.95);
}

.state-panel p {
  font-size: 14px;
  line-height: 1.7;
  color: #615d59;
}

.loading-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.loading-card,
.loading-panel {
  position: relative;
  overflow: hidden;
  background: #f1efee;
}

.loading-card {
  height: 148px;
}

.loading-panel {
  height: 388px;
}

.loading-card::after,
.loading-panel::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.92), transparent);
  animation: shimmer 1.5s ease infinite;
}

.status-overview {
  padding: 26px;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.status-overview__count {
  font-size: 13px;
  font-weight: 600;
  color: #a39e98;
}

.status-composition {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.status-composition__track {
  height: 8px;
  display: flex;
  border-radius: 9999px;
  overflow: hidden;
  background: #f6f5f4;
}

.status-composition__segment {
  height: 100%;
  min-width: 2px;
  transition: width 0.3s ease;
}
.status-composition__segment.is-unpaid { background-color: #dd5b00; }
.status-composition__segment.is-paid { background-color: #a39e98; }
.status-composition__segment.is-exempt { background-color: #1aae39; }

.status-composition__legend {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.status-legend-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.is-unpaid { background-color: #dd5b00; }
.status-dot.is-paid { background-color: #a39e98; }
.status-dot.is-exempt { background-color: #1aae39; }

.status-legend-copy {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #615d59;
}

.status-legend-copy strong {
  font-weight: 600;
  color: rgba(0, 0, 0, 0.92);
}

.status-legend-ratio {
  font-size: 13px;
  font-weight: 600;
  color: #8892a5;
  font-variant-numeric: tabular-nums;
  width: 48px;
  text-align: right;
}

.outstanding-empty {
  padding: 16px 0;
  text-align: center;
  font-size: 13px;
  color: #a39e98;
}

.status-stack,
.status-grid {
  display: grid;
  gap: 12px;
}

.status-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.status-card {
  padding: 20px 22px;
}

.status-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.status-label,
.status-count {
  font-size: 13px;
  font-weight: 600;
}

.status-value {
  margin-top: 16px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.4px;
  font-variant-numeric: tabular-nums;
}

.status-card p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #615d59;
}

.status-card--compact {
  padding: 16px 18px;
}

.status-card--compact .status-value {
  margin-top: 12px;
  font-size: 22px;
}

.status-card--compact p {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.55;
}

.gauge-notes {
  margin-top: 10px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.gauge-notes--tax {
  margin-top: 14px;
  gap: 8px;
}

.gauge-notes--tax .note-chip {
  padding: 5px 10px;
  font-size: 11px;
}

.note-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
}

.tone-healthy {
  color: #1aae39;
  background: rgba(26, 174, 57, 0.14);
}

.tone-warning {
  color: #dd5b00;
  background: rgba(221, 91, 0, 0.12);
}

.tone-danger {
  color: #e03e3e;
  background: rgba(224, 62, 62, 0.12);
}

.gauge-disclaimer {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: #615d59;
}

.feature-caption {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #615d59;
}

.status-card.is-unpaid,
.status-card.is-unpaid .status-label,
.status-card.is-unpaid .status-count,
.status-card.is-unpaid .status-value {
  color: #dd5b00;
}

.status-card.is-paid,
.status-card.is-paid .status-label,
.status-card.is-paid .status-count,
.status-card.is-paid .status-value {
  color: #615d59;
}

.status-card.is-exempt,
.status-card.is-exempt .status-label,
.status-card.is-exempt .status-count,
.status-card.is-exempt .status-value {
  color: #1aae39;
}

.pdf-export-stage {
  position: fixed;
  left: -200vw;
  top: 0;
  width: 794px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  pointer-events: none;
  z-index: -1;
}

.pdf-page {
  width: 794px;
  min-height: 1123px;
  box-sizing: border-box;
  padding: 36px;
  background: #f5f5f4;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.pdf-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.pdf-section-title {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.625px;
  color: rgba(0, 0, 0, 0.95);
}

.pdf-hero-card,
.pdf-card,
.pdf-state-card {
  padding: 28px;
}

.pdf-hero-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pdf-hero-copy {
  max-width: 58ch;
}

.pdf-hero-card h2 {
  margin-top: 10px;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.875px;
  color: rgba(0, 0, 0, 0.95);
}

.pdf-hero-card p {
  margin-top: 14px;
  color: #615d59;
  font-size: 15px;
  line-height: 1.75;
}

.pdf-summary-grid,
.pdf-status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.pdf-page--finance-secondary {
  gap: 16px;
}

.pdf-finance-stack {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

.pdf-status-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.pdf-card,
.pdf-state-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pdf-card--finance-secondary {
  flex: 1;
  gap: 12px;
  padding: 24px 26px;
}

.panel-header--pdf-compact {
  gap: 10px;
}

.panel-header--pdf-compact h3 {
  font-size: 18px;
}

.panel-header--pdf-compact p {
  margin-top: 6px;
  max-width: 48ch;
  line-height: 1.6;
}

.pdf-chart-frame {
  border-radius: 18px;
  background: #ffffff;
  padding: 12px;
}

.pdf-chart-frame--finance-secondary {
  flex: 1;
  min-height: 290px;
  display: flex;
  align-items: stretch;
  padding: 10px;
}

.pdf-chart-image {
  display: block;
  width: 100%;
  border-radius: 12px;
}

.pdf-chart-image--finance-secondary {
  height: 100%;
  object-fit: contain;
  object-position: center;
}

.pdf-state-card {
  margin-top: auto;
}

.panel-empty.compact {
  min-height: 180px;
}

.panel-empty.compact.panel-empty--pdf-finance {
  margin-top: 0;
  flex: 1;
  min-height: 290px;
}

@keyframes shimmer {
  100% {
    transform: translateX(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .signal-card {
    transition: none;
  }

  .signal-card:hover {
    transform: none;
  }
}

@media (max-width: 1200px) {
  .feature-layout,
  .finance-secondary-grid,
  .feature-layout--tax,
  .status-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-header {
    grid-template-columns: 1fr;
  }

  .dashboard-header__export.el-button {
    justify-self: start;
    min-width: 180px;
  }
}

@media (max-width: 960px) {
  .tab-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-header__export.el-button {
    width: 100%;
  }

  .signal-grid,
  .summary-grid,
  .loading-grid,
  .pdf-summary-grid,
  .pdf-status-grid {
    grid-template-columns: 1fr;
  }

  .panel-metric {
    align-items: flex-start;
  }

  .panel-inline-metric {
    justify-content: flex-start;
    max-width: 100%;
  }

  .chart-view-switch {
    width: 100%;
  }

  .chart-view-switch__button {
    flex: 1;
  }
}

@media (max-width: 640px) {
  .dashboard-header,
  .feature-primary,
  .panel-card,
  .signal-card,
  .summary-card,
  .state-panel {
    padding: 20px;
  }

  .dashboard-header__copy h1 {
    font-size: 24px;
  }

  .signal-value {
    font-size: 28px;
  }

  .summary-value {
    font-size: 26px;
  }

  .toolbar-actions {
    width: 100%;
    justify-content: space-between;
  }

  .panel-inline-metric {
    border-radius: 16px;
    padding: 10px 12px;
  }

  .range-select {
    width: 128px;
  }

  .chart-box--flagship,
  .chart-box--trend,
  .chart-box--hr-burden,
  .chart-box--hr-balance,
  .chart-box--gauge,
  .chart-box,
  .chart-box--secondary,
  .chart-box--compact {
    height: 320px;
  }
}
</style>
