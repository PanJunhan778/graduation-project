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
import { BarChart, GaugeChart, LineChart, PieChart } from 'echarts/charts'
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
  PieChart,
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
  range: 'last6months' as HrDashboardRange,
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

const hrRangeOptions: Array<{ label: string; value: HrDashboardRange }> = [
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

const hrRangeLabelMap: Record<HrDashboardRange, string> = {
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
  hrDepartment: '',
  hrTrend: '',
  taxGauge: '',
  taxType: '',
})

const companyLabel = computed(() => userStore.companyName || '当前企业')
const activeTabLabel = computed(() => tabLabelMap[activeTab.value])
const activeRangeLabel = computed(() => {
  if (activeTab.value === 'finance') return financeRangeLabelMap[financeState.range]
  if (activeTab.value === 'hr') return hrRangeLabelMap[hrState.range]
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
    data.departmentSalaryShare.length > 0 ||
    data.monthlyTrend.length > 0
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
      description: '录入员工名册后，这里会生成部门薪资占比和当前在职团队的月度趋势。',
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

async function handleHrRangeChange() {
  await fetchHrData()
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
    disposeCharts('finance-expense', 'finance-income')
    return
  }

  renderFinanceExpenseChart(financeState.data)
  renderFinanceIncomeChart(financeState.data)
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
  if (!financeExpenseChartRef.value || !data.expenseBreakdown.length) {
    disposeChart('finance-expense')
    return
  }

  setChartOption('finance-expense', financeExpenseChartRef.value, {
    color: ['#0075de', '#2a9d99', '#e09f3e', '#d1495b', '#7b61ff', '#8f5b34'],
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: { name: string; value: number; percent: number }) =>
        `${params.name}<br/>${formatCurrency(params.value)}<br/>占比 ${params.percent.toFixed(1)}%`,
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#615d59' },
    },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: {
          color: '#615d59',
          formatter: '{b|{b}}\n{c|{d}%}',
          rich: {
            b: { fontSize: 12, fontWeight: 600, color: 'rgba(0,0,0,0.88)' },
            c: { fontSize: 12, color: '#615d59', lineHeight: 18 },
          },
        },
        labelLine: { length: 10, length2: 10 },
        data: data.expenseBreakdown.map((item) => ({
          name: item.name,
          value: item.amount,
        })),
      },
    ],
  })
}

function renderFinanceIncomeChart(data: FinanceDashboardVO) {
  if (!financeIncomeChartRef.value || !data.topIncomeSources.length) {
    disposeChart('finance-income')
    return
  }

  const sources = [...data.topIncomeSources].reverse()
  setChartOption('finance-income', financeIncomeChartRef.value, {
    color: ['#2a9d99'],
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
        return `${first.name}<br/>收入 ${formatCurrency(first.value)}`
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
      splitLine: {
        lineStyle: { color: 'rgba(0,0,0,0.06)' },
      },
    },
    yAxis: {
      type: 'category',
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: {
        color: 'rgba(0,0,0,0.88)',
        formatter: (value: string) => truncateLabel(value, 8),
      },
      data: sources.map((item) => item.name),
    },
    series: [
      {
        type: 'bar',
        barWidth: 16,
        borderRadius: [8, 8, 8, 8],
        data: sources.map((item) => item.amount),
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

function renderHrDepartmentChart(data: HrDashboardVO) {
  if (!hrDepartmentChartRef.value || !data.departmentSalaryShare.length) {
    disposeChart('hr-department')
    return
  }

  setChartOption('hr-department', hrDepartmentChartRef.value, {
    color: ['#0075de', '#2a9d99', '#7b61ff', '#dd5b00', '#d1495b', '#523410'],
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: { name: string; value: number; percent: number; data: { employeeCount: number } }) =>
        `${params.name}<br/>基础薪资 ${formatCurrency(params.value)}<br/>人数 ${formatCount(params.data.employeeCount)} 人<br/>占比 ${params.percent.toFixed(1)}%`,
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#615d59' },
    },
    series: [
      {
        type: 'pie',
        roseType: 'radius',
        radius: ['18%', '72%'],
        center: ['50%', '42%'],
        itemStyle: {
          borderRadius: 8,
        },
        label: {
          color: 'rgba(0,0,0,0.88)',
          formatter: '{b|{b}}\n{c|{d}%}',
          rich: {
            b: { fontSize: 12, fontWeight: 600, color: 'rgba(0,0,0,0.9)' },
            c: { fontSize: 12, color: '#615d59', lineHeight: 18 },
          },
        },
        data: data.departmentSalaryShare.map((item) => ({
          name: item.department,
          value: item.salaryAmount,
          employeeCount: item.employeeCount,
        })),
      },
    ],
  })
}

function renderHrTrendChart(data: HrDashboardVO) {
  if (!hrTrendChartRef.value || !data.monthlyTrend.length) {
    disposeChart('hr-trend')
    return
  }

  const hasLongTimeline = data.monthlyTrend.length > 12
  setChartOption('hr-trend', hrTrendChartRef.value, {
    color: ['#0075de', '#dd5b00'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      formatter: (params: Array<{ seriesName: string; value: number; axisValue: string; marker: string }>) => {
        const title = formatMonthTitle(params[0]?.axisValue || '')
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
      bottom: hasLongTimeline ? 56 : 12,
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
    dataZoom: hasLongTimeline
      ? [
          {
            type: 'inside',
            startValue: Math.max(data.monthlyTrend.length - 12, 0),
            endValue: data.monthlyTrend.length - 1,
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
        name: '基础薪资',
        type: 'bar',
        yAxisIndex: 0,
        barWidth: 18,
        borderRadius: [8, 8, 0, 0],
        data: data.monthlyTrend.map((item) => item.salaryAmount),
      },
      {
        name: '在职人数',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3 },
        data: data.monthlyTrend.map((item) => item.employeeCount),
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
  const maxGaugeValue = Math.max(30, Math.ceil(ratePercent / 10) * 10 || 10)

  setChartOption('tax-gauge', taxGaugeChartRef.value, {
    series: [
      {
        type: 'gauge',
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max: maxGaugeValue,
        splitNumber: Math.max(maxGaugeValue / 10, 3),
        progress: {
          show: true,
          width: 14,
          roundCap: true,
        },
        axisLine: {
          lineStyle: {
            width: 14,
            color: [
              [Math.min(10 / maxGaugeValue, 1), '#1aae39'],
              [Math.min(20 / maxGaugeValue, 1), '#dd5b00'],
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
          formatter: (value: number) => `${value.toFixed(1)}%`,
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
            value: ratePercent,
            name: toNumber(data.incomeBase) > 0 ? '综合税负率' : '暂无收入基线',
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
        return `${first.name}<br/>税额 ${formatCurrency(first.value)}`
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
          color: '#615d59',
          formatter: (params: { value: number }) => formatShortCurrency(params.value),
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
    <section class="hero-card ds-card">
      <div class="hero-meta">
        <span class="hero-badge">Owner Analytics</span>
        <span class="hero-period">{{ companyLabel }} · 深度经营分析台</span>
      </div>
      <div class="hero-content">
        <div>
          <h1>数据看板</h1>
          <p>
            这里把财务、人事和税务拆成三条可决策分析链路。你可以按主题切换视角，也可以在每个 Tab 内切换统计范围。
          </p>
        </div>

        <div class="hero-actions">
          <div class="hero-note">
            <span class="note-label">当前口径</span>
            <strong>财务/税务按范围聚合，人事按当前在职状态回看趋势</strong>
            <span class="note-subtitle">支持导出当前 Tab 的经营分析快照，便于会议汇报与离线分享</span>
          </div>

          <el-button
            type="primary"
            plain
            :icon="Download"
            :loading="exporting"
            :disabled="!canExportDashboard"
            data-pdf-hide
            @click="handleExportDashboard"
          >
            导出 PDF 报告
          </el-button>
        </div>
      </div>
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
            <div>
              <h2>支出切片与收入来源</h2>
              <p>从成本结构和收入集中度两个角度看经营质量。</p>
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
            <section class="summary-grid">
              <article class="summary-card ds-card">
                <span class="summary-label">范围内总收入</span>
                <strong class="summary-value income">{{ formatCurrency(financeState.data.totalIncome) }}</strong>
                <p>当前统计范围下全部收入合计。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">范围内总支出</span>
                <strong class="summary-value expense">{{ formatCurrency(financeState.data.totalExpense) }}</strong>
                <p>按发生日期落入当前范围的全部支出。</p>
              </article>
            </section>

            <section class="panel-grid two-column">
              <article class="panel-card ds-card">
                <div class="panel-header">
                  <div>
                    <h3>支出切片环图</h3>
                    <p>空分类会自动归入“未分类支出”。</p>
                  </div>
                </div>
                <div v-if="financeState.data.expenseBreakdown.length" ref="financeExpenseChartRef" class="chart-box" />
                <div v-else class="panel-empty">
                  <h4>暂无支出结构</h4>
                  <p>当前范围内没有支出记录。</p>
                </div>
              </article>

              <article class="panel-card ds-card">
                <div class="panel-header">
                  <div>
                    <h3>前五大收入来源</h3>
                    <p>优先使用项目名，缺失时回退分类。</p>
                  </div>
                </div>
                <div v-if="financeState.data.topIncomeSources.length" ref="financeIncomeChartRef" class="chart-box" />
                <div v-else class="panel-empty">
                  <h4>暂无收入来源</h4>
                  <p>当前范围内还没有收入记录。</p>
                </div>
              </article>
            </section>
          </template>
        </section>
      </el-tab-pane>

      <el-tab-pane label="人事洞察" name="hr" lazy>
        <section class="tab-shell">
          <div class="tab-toolbar">
            <div>
              <h2>团队结构与薪资趋势</h2>
              <p>看当前在职团队的薪资重心，以及在所选范围内的规模变化。</p>
            </div>

            <div class="toolbar-actions">
              <span v-if="hrState.loading" class="loading-pill">更新中</span>
              <el-select
                v-model="hrState.range"
                size="small"
                class="range-select"
                :disabled="hrState.loading || exporting"
                @change="handleHrRangeChange"
              >
                <el-option
                  v-for="option in hrRangeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
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
            <p>录入员工名册后，这里会生成部门薪资占比和当前在职团队的月度趋势。</p>
          </div>

          <template v-else-if="hrState.data">
            <section class="summary-grid">
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
            </section>

            <section class="panel-grid two-column">
              <article class="panel-card ds-card">
                <div class="panel-header">
                  <div>
                    <h3>各部门薪资占比</h3>
                    <p>仅统计当前在职员工，空部门会归入“未分配部门”。</p>
                  </div>
                </div>
                <div v-if="hrState.data.departmentSalaryShare.length" ref="hrDepartmentChartRef" class="chart-box" />
                <div v-else class="panel-empty">
                  <h4>暂无部门结构</h4>
                  <p>当前没有可用于聚合的在职员工。</p>
                </div>
              </article>

              <article class="panel-card ds-card">
                <div class="panel-header">
                  <div>
                    <h3>人数与薪资趋势</h3>
                    <p>基于当前在职员工的入职时间向过去回看，不追溯离职历史快照。</p>
                  </div>
                </div>
                <div v-if="hrState.data.monthlyTrend.length" ref="hrTrendChartRef" class="chart-box tall" />
                <div v-else class="panel-empty">
                  <h4>暂无趋势序列</h4>
                  <p>当前没有在职员工可用于生成时间趋势。</p>
                </div>
              </article>
            </section>
          </template>
        </section>
      </el-tab-pane>

      <el-tab-pane label="税务健康" name="tax" lazy>
        <section class="tab-shell">
          <div class="tab-toolbar">
            <div>
              <h2>税负强度与缴纳状态</h2>
              <p>重点看综合税负率、待缴风险和税种结构，不输出行业化结论。</p>
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
            <section class="summary-grid">
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

            <section class="panel-grid tax-layout">
              <article class="panel-card ds-card">
                <div class="panel-header">
                  <div>
                    <h3>综合税负率</h3>
                    <p>
                      口径 = 正向税额 / 正向收入基线。
                      <span class="panel-inline-note" :class="`tone-${taxBurdenTone}`">
                        {{ toNumber(taxState.data.incomeBase) > 0 ? `当前为 ${formatRatio(taxState.data.taxBurdenRate)}` : '暂无收入基线' }}
                      </span>
                    </p>
                  </div>
                </div>
                <div ref="taxGaugeChartRef" class="chart-box gauge" />
                <div class="gauge-notes">
                  <div class="note-chip tone-healthy">0%-10% 稳定区</div>
                  <div class="note-chip tone-warning">10%-20% 关注区</div>
                  <div class="note-chip tone-danger">20%+ 高压区</div>
                </div>
              </article>

              <article class="panel-card ds-card">
                <div class="panel-header">
                  <div>
                    <h3>税种结构</h3>
                    <p>负数退税不进入结构分布，但会保留在下方状态摘要的金额里。</p>
                  </div>
                </div>
                <div v-if="taxState.data.taxTypeStructure.length" ref="taxTypeChartRef" class="chart-box" />
                <div v-else class="panel-empty">
                  <h4>暂无税种结构</h4>
                  <p>当前范围内没有正向税额。</p>
                </div>
              </article>
            </section>

            <section class="status-grid">
              <article
                v-for="item in taxState.data.statusSummary"
                :key="item.status"
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
            <span class="note-label">深度经营分析台</span>
            <h2>{{ activeTabLabel }}</h2>
            <p>当前导出为 {{ activeRangeLabel }} 口径下的经营分析快照，适合会议汇报与离线阅读。</p>
          </div>
          <div class="pdf-hero-note">
            <span class="note-label">当前口径</span>
            <strong>财务/税务按范围聚合，人事按当前在职状态回看趋势</strong>
            <span class="note-subtitle">导出按模块分页，避免图表与卡片跨页截断</span>
          </div>
        </section>

        <template v-if="currentTabHasData">
          <template v-if="activeTab === 'finance' && financeState.data">
            <section class="pdf-summary-grid">
              <article class="summary-card ds-card">
                <span class="summary-label">范围内总收入</span>
                <strong class="summary-value income">{{ formatCurrency(financeState.data.totalIncome) }}</strong>
                <p>当前统计范围下全部收入合计。</p>
              </article>

              <article class="summary-card ds-card">
                <span class="summary-label">范围内总支出</span>
                <strong class="summary-value expense">{{ formatCurrency(financeState.data.totalExpense) }}</strong>
                <p>按发生日期落入当前范围的全部支出。</p>
              </article>
            </section>

            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h3>支出切片环图</h3>
                  <p>空分类会自动归入“未分类支出”。</p>
                </div>
              </div>

              <div v-if="exportChartImages.financeExpense" class="pdf-chart-frame">
                <img :src="exportChartImages.financeExpense" alt="支出切片环图" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
                <h4>暂无支出结构</h4>
                <p>当前范围内没有支出记录。</p>
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
            </section>

            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h3>各部门薪资占比</h3>
                  <p>仅统计当前在职员工，空部门会归入“未分配部门”。</p>
                </div>
              </div>

              <div v-if="exportChartImages.hrDepartment" class="pdf-chart-frame">
                <img :src="exportChartImages.hrDepartment" alt="各部门薪资占比图" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
                <h4>暂无部门结构</h4>
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
          <article class="pdf-card ds-card">
            <div class="panel-header">
              <div>
                <h3>前五大收入来源</h3>
                <p>优先使用项目名，缺失时回退分类。</p>
              </div>
            </div>

            <div v-if="exportChartImages.financeIncome" class="pdf-chart-frame">
              <img :src="exportChartImages.financeIncome" alt="前五大收入来源图" class="pdf-chart-image" />
            </div>
            <div v-else class="panel-empty compact">
              <h4>暂无收入来源</h4>
              <p>当前范围内还没有收入记录。</p>
            </div>
          </article>
        </template>

        <template v-else-if="activeTab === 'hr' && hrState.data">
          <article class="pdf-card ds-card">
            <div class="panel-header">
              <div>
                <h3>人数与薪资趋势</h3>
                <p>基于当前在职员工的入职时间向过去回看，不追溯离职历史快照。</p>
              </div>
            </div>

            <div v-if="exportChartImages.hrTrend" class="pdf-chart-frame">
              <img :src="exportChartImages.hrTrend" alt="人数与薪资趋势图" class="pdf-chart-image" />
            </div>
            <div v-else class="panel-empty compact">
              <h4>暂无趋势序列</h4>
              <p>当前没有在职员工可用于生成时间趋势。</p>
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
  gap: 20px;
}

.hero-card {
  padding: 28px 32px;
  overflow: hidden;
  background:
    radial-gradient(circle at top right, rgba(0, 117, 222, 0.14), transparent 32%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 44%, #f6f5f4 100%);
}

.hero-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
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

.hero-period {
  color: #615d59;
  font-size: 13px;
  font-weight: 500;
}

.hero-content {
  margin-top: 22px;
  display: flex;
  justify-content: space-between;
  gap: 24px;
}

.hero-content h1 {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -1px;
  color: rgba(0, 0, 0, 0.95);
}

.hero-content p {
  margin-top: 12px;
  max-width: 680px;
  color: #615d59;
  font-size: 15px;
  line-height: 1.7;
}

.hero-actions {
  min-width: 260px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 14px;
}

.hero-note {
  width: 100%;
  padding: 18px 20px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: rgba(0, 0, 0, 0.04) 0 12px 36px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.note-label {
  font-size: 12px;
  font-weight: 600;
  color: #a39e98;
  letter-spacing: 0.125px;
}

.hero-note strong {
  font-size: 15px;
  color: rgba(0, 0, 0, 0.92);
  line-height: 1.6;
}

.note-subtitle {
  font-size: 13px;
  color: #615d59;
  line-height: 1.6;
}

.dashboard-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.dashboard-tabs :deep(.el-tabs__nav-wrap::after) {
  background: rgba(0, 0, 0, 0.08);
}

.dashboard-tabs :deep(.el-tabs__item) {
  height: 42px;
  font-weight: 600;
}

.tab-shell {
  padding-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.tab-toolbar h2 {
  font-size: 24px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.95);
  letter-spacing: -0.25px;
}

.tab-toolbar p {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.7;
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

.panel-grid {
  display: grid;
  gap: 16px;
}

.panel-grid.two-column {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.panel-grid.tax-layout {
  grid-template-columns: minmax(320px, 0.9fr) minmax(0, 1.1fr);
}

.panel-card {
  padding: 24px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
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

.chart-box {
  margin-top: 20px;
  width: 100%;
  height: 340px;
}

.chart-box.tall {
  height: 360px;
}

.chart-box.gauge {
  height: 320px;
}

.panel-empty {
  margin-top: 20px;
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

.status-grid {
  display: grid;
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

@media (max-width: 1200px) {
  .panel-grid.two-column,
  .panel-grid.tax-layout,
  .status-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .hero-content,
  .tab-toolbar {
    flex-direction: column;
  }

  .hero-actions {
    width: 100%;
    min-width: 0;
    align-items: flex-start;
  }

  .summary-grid,
  .loading-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .hero-card,
  .panel-card,
  .summary-card,
  .state-panel {
    padding: 20px;
  }

  .hero-content h1 {
    font-size: 26px;
  }

  .summary-value {
    font-size: 26px;
  }

  .toolbar-actions {
    width: 100%;
    justify-content: space-between;
  }

  .range-select {
    width: 132px;
  }

  .chart-box,
  .chart-box.tall {
    height: 320px;
  }
}
</style>
