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
} from 'echarts/components'

use([
  BarChart,
  GaugeChart,
  LineChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  DataZoomComponent,
  CanvasRenderer,
])

type AnalyticsTab = 'finance' | 'hr' | 'tax'

const userStore = useUserStore()

const dashboardRootRef = ref<HTMLDivElement | null>(null)
const activeTab = ref<AnalyticsTab>('finance')
const exporting = ref(false)
const showExportStage = ref(false)

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
  range: 'thisYear' as TaxDashboardRange,
  data: null as TaxDashboardVO | null,
})

const financeRangeOptions: Array<{ label: string; value: FinanceDashboardRange }> = [
  { label: '近 3 个月', value: 'last3months' },
  { label: '近 6 个月', value: 'last6months' },
  { label: '近 12 个月', value: 'last12months' },
  { label: '全部历史', value: 'all' },
]

const taxRangeOptions: Array<{ label: string; value: TaxDashboardRange }> = [
  { label: '本年度', value: 'thisYear' },
  { label: '近 12 个月', value: 'last12months' },
  { label: '全部历史', value: 'all' },
]

const tabLabelMap: Record<AnalyticsTab, string> = {
  finance: '财务剖析',
  hr: '人事洞察',
  tax: '税务健康',
}

const financeRangeLabelMap: Record<FinanceDashboardRange, string> = {
  last3months: '近3个月',
  last6months: '近6个月',
  last12months: '近12个月',
  all: '全部历史',
}

const taxRangeLabelMap: Record<TaxDashboardRange, string> = {
  thisYear: '本年度',
  last12months: '近12个月',
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
  return taxRangeLabelMap[taxState.range]
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

const currentMethodologyNote = computed(() => {
  if (activeTab.value === 'finance') {
    return '财务按范围聚合，先看收入集中度，再看成本吞噬与利润趋势。'
  }
  if (activeTab.value === 'hr') {
    return '人事页只讲当前在职团队的结构与薪资负担，不把现有名册回推包装成历史快照。'
  }
  return '税负率以正向税额对正向收入基线，待缴风险单独显性展示。'
})

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

const hrDepartmentCount = computed(() => hrDepartmentInsights.value.length)

const hrTopDepartment = computed(() => hrDepartmentInsights.value[0] ?? null)

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
    return `税负压力高度集中在${taxTopTaxType.value.taxType}，需要持续盯住单一税种波动。`
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
    const res = await getTaxDashboard(taxState.range)
    taxState.data = normalizeTaxDashboard(res.data)
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

  const cumulativeRatios: number[] = []
  let runningAmount = 0
  for (const item of items) {
    runningAmount += item.amount
    cumulativeRatios.push(toNumber(data.totalIncome) > 0 ? runningAmount / toNumber(data.totalIncome) : 0)
  }

  setChartOption('finance-income', financeIncomeChartRef.value, {
    color: ['#2a9d99', '#213183'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: Array<{ seriesName: string; value: number; axisValue: string; marker: string }>) => {
        const amountItem = params.find((item) => item.seriesName === '收入金额')
        const ratioItem = params.find((item) => item.seriesName === '累计贡献')
        if (!amountItem) return ''
        return `<div style="min-width: 190px;">
          <div style="font-weight: 700; margin-bottom: 8px;">${amountItem.axisValue}</div>
          <div>${amountItem.marker}${amountItem.seriesName}<span style="float:right;margin-left:18px;font-weight:600;">${formatCurrency(amountItem.value)}</span></div>
          ${ratioItem ? `<div>${ratioItem.marker}${ratioItem.seriesName}<span style="float:right;margin-left:18px;font-weight:600;">${formatRatio(ratioItem.value)}</span></div>` : ''}
        </div>`
      },
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
        splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
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
        name: '收入金额',
        type: 'bar',
        barWidth: 24,
        borderRadius: [12, 12, 0, 0],
        itemStyle: {
          color: '#1375d1',
          shadowColor: 'rgba(19, 117, 209, 0.18)',
          shadowBlur: 18,
          shadowOffsetY: 10,
        },
        data: items.map((item) => item.amount),
        label: {
          show: true,
          position: 'top',
          color: '#615d59',
          formatter: (params: { value: number }) => formatShortCurrency(params.value),
        },
      },
      {
        name: '累计贡献',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbolSize: 8,
        lineStyle: { width: 3 },
        itemStyle: { color: '#213183' },
        areaStyle: { color: 'rgba(33, 49, 131, 0.08)' },
        data: cumulativeRatios,
        label: {
          show: true,
          position: 'top',
          color: '#213183',
          formatter: (params: { dataIndex: number; value: number }) =>
            params.dataIndex === Math.min(2, cumulativeRatios.length - 1)
              ? `Top${Math.min(3, cumulativeRatios.length)} ${formatRatio(cumulativeRatios[params.dataIndex])}`
              : '',
        },
      },
    ],
  })
}

function renderFinanceTrendChart(data: FinanceDashboardVO) {
  if (!financeTrendChartRef.value || !data.monthlyTrend.length) {
    disposeChart('finance-trend')
    return
  }

  setChartOption('finance-trend', financeTrendChartRef.value, {
    color: ['#2a9d99', '#dd5b00', '#213183'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: Array<{ seriesName: string; value: number; axisValue: string; marker: string }>) => {
        const title = formatMonthTitle(params[0]?.axisValue || '')
        const lines = params.map((item) =>
          `${item.marker}${item.seriesName}<span style="float:right;margin-left:18px;font-weight:600;">${formatCurrency(item.value)}</span>`,
        )
        return `<div style="min-width: 210px;">
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
      left: 14,
      right: 14,
      bottom: 12,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      axisTick: { show: false },
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } },
      axisLabel: {
        color: '#615d59',
        formatter: (value: string) => formatMonthTick(value),
      },
      data: data.monthlyTrend.map((item) => item.month),
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        color: '#615d59',
        formatter: (value: number) => formatAxisCurrency(value),
      },
      splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
    },
    series: [
      {
        name: '利润',
        type: 'bar',
        barWidth: 18,
        borderRadius: [8, 8, 0, 0],
        itemStyle: {
          color: (params: { value: number }) => (params.value >= 0 ? '#213183' : '#d1495b'),
          shadowColor: 'rgba(33, 49, 131, 0.12)',
          shadowBlur: 12,
          shadowOffsetY: 6,
        },
        data: data.monthlyTrend.map((item) => item.profit),
      },
      {
        name: '收入',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3 },
        areaStyle: { color: 'rgba(42, 157, 153, 0.08)' },
        data: data.monthlyTrend.map((item) => item.income),
      },
      {
        name: '支出',
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3 },
        areaStyle: { color: 'rgba(221, 91, 0, 0.06)' },
        data: data.monthlyTrend.map((item) => item.expense),
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
  const items = getHrDepartmentInsights(data).slice().reverse()
  if (!hrDepartmentChartRef.value || !items.length) {
    disposeChart('hr-department')
    return
  }

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
        itemStyle: {
          color: '#1375d1',
          shadowColor: 'rgba(19, 117, 209, 0.18)',
          shadowBlur: 18,
          shadowOffsetY: 10,
        },
        label: {
          show: true,
          position: 'right',
          color: '#615d59',
          formatter: (params: { value: number; data: HrDepartmentInsight }) =>
            `${formatShortCurrency(params.value)} · ${formatRatio(params.data.ratio)}`,
        },
        data: items.map((item) => ({
          value: item.salaryAmount,
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
        splitLine: { lineStyle: { color: 'rgba(0,0,0,0.06)' } },
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
        lineStyle: { width: 3 },
        areaStyle: { color: 'rgba(221, 91, 0, 0.08)' },
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

  setChartOption('tax-gauge', taxGaugeChartRef.value, {
    series: [
      {
        type: 'gauge',
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max: 30,
        splitNumber: 6,
        progress: {
          show: true,
          width: 14,
          roundCap: true,
        },
        axisLine: {
          lineStyle: {
            width: 14,
            color: [
              [10 / 30, '#1aae39'],
              [20 / 30, '#dd5b00'],
              [1, '#e03e3e'],
            ],
          },
        },
        pointer: {
          icon: 'path://M6 0 L-6 0 L0 82 z',
          length: '58%',
          width: 10,
          itemStyle: { color: '#213183' },
        },
        axisTick: { distance: -20, splitNumber: 5, lineStyle: { color: '#ffffff', width: 1 } },
        splitLine: { distance: -20, length: 12, lineStyle: { color: '#ffffff', width: 2 } },
        axisLabel: {
          distance: -34,
          color: '#615d59',
          fontSize: 11,
          formatter: (value: number) => `${value}%`,
        },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '52%'],
          formatter: () => `${ratePercent.toFixed(1)}%`,
          color: 'rgba(0,0,0,0.95)',
          fontSize: 30,
          fontWeight: 700,
        },
        title: {
          offsetCenter: [0, '76%'],
          color: '#615d59',
          fontSize: 13,
        },
        data: [
          {
            value: displayRatePercent,
            name: toNumber(data.incomeBase) > 0 ? '当前税负率' : '暂无收入基线',
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
        data: items.map((item) => item.amount),
        label: {
          show: true,
          position: 'right',
          formatter: (params: { value: number; dataIndex: number }) => {
            const currentItem = items[params.dataIndex]
            return `{amount|${formatShortCurrency(params.value)}}\n{ratio|${formatRatio(toNumber(currentItem?.ratio))}}`
          },
          rich: {
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
            <section class="feature-layout feature-layout--finance-diagnostic">
              <article class="feature-primary feature-primary--finance ds-card">
                <div class="panel-header panel-header--feature">
                  <div>
                    <span class="panel-kicker">旗舰主图</span>
                    <h3>收入集中度</h3>
                    <p>{{ financeConcentrationHeadline }}</p>
                  </div>
                  <div class="panel-metric" :class="financeTop1Share >= 0.5 ? 'is-warning' : 'is-income'">
                    <span>Top1 收入占比</span>
                    <strong>{{ financeSourceCount > 0 ? formatRatio(financeTop1Share) : '—' }}</strong>
                  </div>
                </div>

                <div
                  v-if="financeState.data.topIncomeSources.length"
                  ref="financeIncomeChartRef"
                  class="chart-box chart-box--flagship chart-box--pareto"
                />
                <div v-else class="panel-empty">
                  <h4>暂无收入集中度图</h4>
                  <p>当前范围内还没有形成可用于判断依赖度的收入来源。</p>
                </div>
              </article>

              <aside class="feature-sidebar feature-sidebar--finance">
                <section class="signal-grid signal-grid--finance">
                  <article class="signal-card signal-card--finance-structure ds-card">
                    <span class="signal-label">Top3 收入占比</span>
                    <strong class="signal-value">{{ financeSourceCount > 0 ? formatRatio(financeTop3Share) : '—' }}</strong>
                    <p>
                      {{
                        financeSourceCount > 0
                          ? `${formatCount(financeSourceCount)} 个来源里，前三大来源贡献了主要现金入口。`
                          : '等待收入来源形成后，再判断集中风险。'
                      }}
                    </p>
                  </article>

                  <article class="signal-card signal-card--finance-pressure ds-card" :class="financeCoverageRatio >= 0.7 ? 'is-alert' : ''">
                    <span class="signal-label">成本吞噬率</span>
                    <strong class="signal-value" :class="financeCoverageRatio >= 0.7 ? 'expense' : ''">
                      {{ toNumber(financeState.data.totalIncome) > 0 ? formatRatio(financeCoverageRatio) : '—' }}
                    </strong>
                    <p>{{ financeCoverageHeadline }}</p>
                  </article>

                  <article class="signal-card signal-card--finance-balance ds-card" :class="financeNetSpread < 0 ? 'is-alert' : 'is-positive'">
                    <span class="signal-label">净流差</span>
                    <strong class="signal-value" :class="financeNetSpread >= 0 ? 'income' : 'expense'">
                      {{ formatSignedCurrency(financeNetSpread) }}
                    </strong>
                    <p>
                      {{
                        toNumber(financeState.data.totalIncome) > 0
                          ? '收入减支出的直接结果，适合判断当前利润空间是否还充足。'
                          : '当前范围内暂无收入基线，净流差更偏向支出压力提醒。'
                      }}
                    </p>
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
                    <p>{{ financeComparisonHeadline }}</p>
                  </article>
                </section>
              </aside>
            </section>

            <section class="finance-secondary-grid">
              <article class="panel-card ds-card panel-card--secondary panel-card--wide panel-card--finance-trend">
                <div class="panel-header">
                  <div>
                    <h3>月度收入、支出与利润趋势</h3>
                    <p>第二层看经营压力是短期波动，还是已经沿着时间线持续抬升。</p>
                  </div>
                </div>
                <div
                  v-if="financeState.data.monthlyTrend.length"
                  ref="financeTrendChartRef"
                  class="chart-box chart-box--secondary chart-box--trend-secondary"
                />
                <div v-else class="panel-empty panel-empty--compact">
                  <h4>暂无月度趋势</h4>
                  <p>当前范围内还没有形成可读的收入、支出与利润序列。</p>
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
            <section class="feature-layout feature-layout--hr">
              <article class="feature-primary ds-card feature-primary--hr">
                <div class="panel-header panel-header--feature">
                  <div>
                    <span class="panel-kicker">旗舰主图</span>
                    <h3>部门薪资负担排序</h3>
                    <p>先看当前基础薪资主要压在哪些部门，再判断人力成本是否过度集中。</p>
                  </div>
                  <div class="panel-metric">
                    <span>覆盖部门数</span>
                    <strong>{{ formatCount(hrDepartmentCount) }}</strong>
                  </div>
                </div>

                <div
                  v-if="hrState.data.departmentSalaryShare.length"
                  ref="hrDepartmentChartRef"
                  class="chart-box chart-box--flagship chart-box--hr-burden"
                />
                <div v-else class="panel-empty">
                  <h4>暂无团队结构</h4>
                  <p>当前没有可用于聚合的在职员工。</p>
                </div>
                <p v-if="hrTopDepartment" class="feature-caption">
                  当前负担最重的部门是 {{ hrTopDepartment.department }}，承担了
                  {{ formatRatio(hrTopDepartment.ratio) }} 的基础薪资，总计
                  {{ formatCount(hrTopDepartment.employeeCount) }} 人。
                </p>
              </article>

              <aside class="feature-sidebar feature-sidebar--hr">
                <section class="signal-grid signal-grid--hr">
                  <article class="signal-card ds-card signal-card--hr-size">
                    <span class="signal-label">当前在职人数</span>
                    <strong class="signal-value">{{ formatCount(hrState.data.activeEmployeeCount) }} 人</strong>
                    <p>按当前 `status=1` 的员工记录统计。</p>
                  </article>

                  <article class="signal-card ds-card signal-card--hr-cost">
                    <span class="signal-label">当前基础薪资总额</span>
                    <strong class="signal-value income">{{ formatCurrency(hrState.data.activeSalaryTotal) }}</strong>
                    <p>用于观察固定人力成本的当前压力。</p>
                  </article>

                  <article class="signal-card ds-card signal-card--hr-average">
                    <span class="signal-label">人均基础薪资</span>
                    <strong class="signal-value">{{ formatCurrency(hrAverageSalary) }}</strong>
                    <p>帮助判断当前团队的人力单价是否已经抬高。</p>
                  </article>

                  <article class="signal-card ds-card signal-card--hr-focus">
                    <span class="signal-label">最高负担部门</span>
                    <strong class="signal-value">{{ hrTopDepartment ? formatRatio(hrTopDepartment.ratio) : '—' }}</strong>
                    <p>
                      {{
                        hrTopDepartment
                          ? `${hrTopDepartment.department} 当前承担最多基础薪资负担。`
                          : '等待部门结构形成后再判断负担重心。'
                      }}
                    </p>
                  </article>
                </section>
              </aside>
            </section>

            <article class="panel-card ds-card panel-card--full panel-card--wide panel-card--hr-balance">
              <div class="panel-header">
                <div>
                  <h3>部门人数与人均基础薪资</h3>
                  <p>把部门体量和人均单价放在一起看，区分到底是人数更大，还是岗位单价更高。</p>
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
                :disabled="taxState.loading || exporting"
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
            <section class="feature-layout feature-layout--tax">
              <article class="feature-primary ds-card feature-primary--tax">
                <div class="panel-header panel-header--feature">
                  <div>
                    <span class="panel-kicker">旗舰主图</span>
                    <h3>综合税负率</h3>
                    <p>
                      用正向税额对正向收入基线，先判断税负强度是否已经压住经营弹性。
                    </p>
                  </div>
                  <div class="panel-metric-stack">
                    <div class="panel-metric" :class="`is-${taxBurdenTone}`">
                      <span>当前税负率</span>
                      <strong>{{ toNumber(taxState.data.incomeBase) > 0 ? formatRatio(taxState.data.taxBurdenRate) : '暂无基线' }}</strong>
                    </div>
                    <span class="delta-pill" :class="`is-${taxComparisonTone}`">
                      {{ taxComparisonLabel }}
                    </span>
                  </div>
                </div>
                <div ref="taxGaugeChartRef" class="chart-box chart-box--flagship chart-box--gauge chart-box--tax-gauge" />
                <div class="gauge-notes gauge-notes--tax">
                  <div class="note-chip tone-healthy">0%-10%</div>
                  <div class="note-chip tone-warning">10%-20%</div>
                  <div class="note-chip tone-danger">20%+</div>
                </div>
                <p class="gauge-disclaimer">10% / 20% 为内部观察线，仅用于经营观察，不代表税务合规判定。</p>
                <p class="feature-caption feature-caption--tax">{{ taxRiskHeadline }}</p>
              </article>

              <aside class="feature-sidebar feature-sidebar--tax">
                <section class="signal-grid signal-grid--tax">
                  <article class="signal-card signal-card--tax-rate ds-card">
                    <span class="signal-label">当前税负率</span>
                    <strong class="signal-value" :class="taxBurdenTone === 'danger' ? 'expense' : taxBurdenTone === 'healthy' ? 'income' : ''">
                      {{ toNumber(taxState.data.incomeBase) > 0 ? formatRatio(taxState.data.taxBurdenRate) : '暂无基线' }}
                    </strong>
                    <p>{{ taxComparisonLabel }}</p>
                  </article>

                  <article class="signal-card signal-card--tax-overdue ds-card" :class="{ 'is-alert': taxUnpaidRatio > 0 }">
                    <span class="signal-label">待缴情形占比</span>
                    <strong class="signal-value" :class="taxUnpaidRatio > 0 ? 'expense' : ''">
                      {{ toNumber(taxState.data.positiveTaxAmount) > 0 ? formatRatio(taxUnpaidRatio) : '0.0%' }}
                    </strong>
                    <p>
                      {{
                        toNumber(taxState.data.positiveTaxAmount) > 0
                          ? `待缴正向税额 ${formatCurrency(taxState.data.unpaidTaxAmount)}`
                          : '当前范围内暂无正向税额基线'
                      }}
                    </p>
                  </article>

                  <article class="signal-card signal-card--tax-records ds-card" :class="{ 'is-alert': taxUnpaidRecordRatio > 0 }">
                    <span class="signal-label">待缴情形记录占比</span>
                    <strong class="signal-value" :class="taxUnpaidRecordRatio > 0 ? 'expense' : ''">
                      {{ taxStatusTotalCount > 0 ? formatRatio(taxUnpaidRecordRatio) : '0.0%' }}
                    </strong>
                    <p>
                      {{ taxStatusTotalCount > 0 ? `共 ${formatCount(taxStatusTotalCount)} 笔税务记录` : '当前范围内暂无状态记录' }}
                    </p>
                  </article>

                  <article class="signal-card signal-card--tax-focus ds-card">
                    <span class="signal-label">第一大税种占比</span>
                    <strong class="signal-value" :class="taxTopTaxTypeShare >= 0.5 ? 'expense' : ''">
                      {{ taxTopTaxType ? formatRatio(taxTopTaxTypeShare) : '—' }}
                    </strong>
                    <p>{{ taxTopTaxType ? `当前压力主要来自 ${taxTopTaxType.taxType}` : '当前范围内暂无税种结构' }}</p>
                  </article>
                </section>

                <section class="status-overview ds-card">
                  <div class="panel-header">
                    <div>
                      <h3>缴情状态组成</h3>
                      <p>先判断风险是金额问题，还是待缴记录开始堆积。</p>
                    </div>
                    <span class="status-overview__count">{{ formatCount(taxStatusTotalCount) }} 笔</span>
                  </div>

                  <div v-if="taxStatusTotalCount > 0" class="status-composition">
                    <div class="status-composition__track">
                      <span
                        v-for="item in taxStatusBreakdown"
                        :key="`status-segment-${item.status}`"
                        class="status-composition__segment"
                        :class="getTaxStatusClass(item.status)"
                        :style="{ width: `${item.ratio * 100}%` }"
                      />
                    </div>
                    <div class="status-composition__legend">
                      <div
                        v-for="item in taxStatusBreakdown"
                        :key="`status-legend-${item.status}`"
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

                <section class="outstanding-card ds-card">
                  <div class="panel-header">
                    <div>
                      <h3>最近待缴事项</h3>
                      <p>把最新待缴情形单独拉出来，适合安排本周处理顺序。</p>
                    </div>
                    <span class="status-overview__count">{{ formatCount(taxOutstandingItems.length) }} 条</span>
                  </div>

                  <ul v-if="taxOutstandingItems.length" class="outstanding-list">
                    <li v-for="item in taxOutstandingItems" :key="`${item.taxPeriod}-${item.taxType}`" class="outstanding-item">
                      <div class="outstanding-copy">
                        <span class="outstanding-period">{{ item.taxPeriod }}</span>
                        <strong class="outstanding-title">{{ item.taxType }}</strong>
                      </div>
                      <span class="outstanding-amount">{{ formatCurrency(item.amount) }}</span>
                    </li>
                  </ul>
                  <div v-else class="outstanding-empty">当前范围内没有待缴情形。</div>
                </section>
              </aside>
            </section>

            <article class="panel-card ds-card panel-card--full panel-card--tax-structure">
              <div class="panel-header">
                <div>
                  <h3>税种结构排序</h3>
                  <p>确认压力主要来自哪里，负数退税不进入结构分布，但仍保留在状态汇总里。</p>
                </div>
                <div class="panel-metric panel-metric--tax-structure" :class="taxTopTaxType ? 'is-warning' : ''">
                  <span>{{ taxTopTaxType ? taxTopTaxType.taxType : '暂无结构' }}</span>
                  <strong>{{ taxTopTaxType ? formatRatio(taxTopTaxTypeShare) : '—' }}</strong>
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
          <div>
            <span class="note-label">图表优先视图</span>
            <h2>{{ activeTabLabel }}</h2>
            <p>当前导出聚焦 {{ activeRangeLabel }} 口径下的主图与关键信号，适合会议汇报与离线阅读。</p>
          </div>
          <div class="pdf-hero-note">
            <span class="note-label">当前口径</span>
            <strong>{{ currentMethodologyNote }}</strong>
            <span class="note-subtitle">导出按模块分页，优先保留主图叙事，再补充次级图表与状态卡。</span>
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
                      ? `${hrTopDepartment.department} 当前承担最多基础薪资负担。`
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
                <div class="note-chip tone-healthy">0%-10% 稳定区</div>
                <div class="note-chip tone-warning">10%-20% 关注区</div>
                <div class="note-chip tone-danger">20%+ 高压区</div>
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

      <section v-if="currentTabHasData" ref="dashboardExportPageTwoRef" class="pdf-page">
        <div class="pdf-page-header">
          <div>
            <span class="note-label">续页</span>
            <h3 class="pdf-section-title">{{ activeTabLabel }}</h3>
          </div>
          <span class="hero-period">{{ activeRangeLabel }}</span>
        </div>

        <template v-if="activeTab === 'finance' && financeState.data">
          <section class="pdf-finance-grid">
            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h3>月度收入、支出与利润趋势</h3>
                  <p>用时间线确认利润压力是短期波动，还是已经形成趋势。</p>
                </div>
              </div>

              <div v-if="exportChartImages.financeTrend" class="pdf-chart-frame">
                <img :src="exportChartImages.financeTrend" alt="财务月度趋势图" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
                <h4>暂无月度趋势</h4>
                <p>当前范围内还没有形成可读的收入、支出与利润序列。</p>
              </div>
            </article>

            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h3>支出压力排序</h3>
                  <p>第二层看成本主要压在哪，帮助快速决定先盯住哪一项支出。</p>
                </div>
              </div>

              <div v-if="exportChartImages.financeExpense" class="pdf-chart-frame">
                <img :src="exportChartImages.financeExpense" alt="支出压力排序图" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
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
              :key="`pdf-${item.status}`"
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
  align-items: start;
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

.signal-card {
  position: relative;
  padding: 18px 18px 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 245, 244, 0.92));
  min-height: 170px;
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

.signal-card.is-positive {
  border-color: rgba(42, 157, 153, 0.14);
}

.signal-card.is-alert {
  border-color: rgba(209, 73, 91, 0.16);
  box-shadow: 0 14px 30px rgba(209, 73, 91, 0.08);
}

.signal-label {
  font-size: 12px;
  font-weight: 600;
  color: #615d59;
}

.signal-value {
  margin-top: 8px;
  display: block;
  font-size: 26px;
  font-weight: 700;
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
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #615d59;
  max-width: 30ch;
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

.panel-inline-note {
  display: inline-flex;
  align-items: center;
  margin-left: 6px;
  padding: 2px 8px;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 600;
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
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(260px, 0.85fr);
  gap: 20px;
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

.pdf-hero-note {
  border-radius: 18px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(246, 245, 244, 0.7);
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pdf-hero-note strong {
  font-size: 16px;
  color: rgba(0, 0, 0, 0.92);
  line-height: 1.6;
}

.pdf-summary-grid,
.pdf-status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.pdf-finance-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
  gap: 16px;
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

.pdf-chart-frame {
  border-radius: 18px;
  background: #ffffff;
  padding: 12px;
}

.pdf-chart-image {
  display: block;
  width: 100%;
  border-radius: 12px;
}

.pdf-state-card {
  margin-top: auto;
}

.panel-empty.compact {
  min-height: 180px;
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
  .status-grid,
  .pdf-finance-grid {
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

  .signal-value,
  .summary-value {
    font-size: 26px;
  }

  .toolbar-actions {
    width: 100%;
    justify-content: space-between;
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
