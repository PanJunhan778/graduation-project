<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useDelayedLoading } from '@/composables/useDelayedLoading'
import { getHomeAiSummary, getHomeDashboard } from '@/api/dashboard'
import { exportSectionsToPdf, formatPdfTimestamp } from '@/utils/pdf'
import type { Component } from 'vue'
import type { HomeAiSummaryVO, HomeDashboardVO, TaxCalendarItem } from '@/types'
import {
  ChatDotRound,
  DataAnalysis,
  Download,
  Money,
  RefreshRight,
  TrendCharts,
  WarningFilled,
} from '@element-plus/icons-vue'
import { use, type EChartsType } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init } from 'echarts/core'

use([LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const userStore = useUserStore()
const router = useRouter()

const loading = ref(true)
const exporting = ref(false)
const showExportStage = ref(false)
const errorMessage = ref('')
const aiSummaryLoading = ref(false)
const aiSummaryError = ref('')
const dashboard = ref<HomeDashboardVO | null>(null)
const aiSummary = ref<HomeAiSummaryVO | null>(null)
const trendChartRef = ref<HTMLDivElement | null>(null)
const departmentChartRef = ref<HTMLDivElement | null>(null)
const homeExportHeroPageRef = ref<HTMLDivElement | null>(null)
const homeExportInsightPageRef = ref<HTMLDivElement | null>(null)
const homeTrendChartImage = ref('')
const homeDepartmentChartImage = ref('')

let trendChart: EChartsType | null = null
let departmentChart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null

const showLoadingSkeleton = useDelayedLoading(() => loading.value && !dashboard.value && !errorMessage.value)

const currentPeriodLabel = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}年${String(now.getMonth() + 1).padStart(2, '0')}月经营快照`
})

const exportPeriodLabel = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}年${String(now.getMonth() + 1).padStart(2, '0')}月首页驾驶舱`
})

const companyInlineMeta = computed(() => [
  `企业码 ${userStore.companyCode || '未填写'}`,
  `行业 ${userStore.industry || '未填写'}`,
  `纳税人 ${userStore.taxpayerType || '未填写'}`,
])

const hasAnyData = computed(() => {
  const data = dashboard.value
  if (!data) return false
  if (toNumber(data.totalIncome) > 0) return true
  if (toNumber(data.totalExpense) > 0) return true
  if (toNumber(data.unpaidTax) > 0) return true
  if ((data.departmentHeadcount || []).length > 0) return true
  return data.taxCalendar.length > 0 || data.monthlyTrend.some((point) =>
    toNumber(point.income) !== 0 || toNumber(point.expense) !== 0 || toNumber(point.profit) !== 0,
  )
})

const startupTasks = computed(() => {
  const setupStatus = dashboard.value?.setupStatus
  if (!setupStatus) return []

  return [
    {
      key: 'staff',
      title: '创建首个数据填写员账号',
      description: setupStatus.hasStaffAccount
        ? '录入账号已经准备好，可以继续安排财务和税务信息录入。'
        : '先去用户管理创建数据填写员账号，后续录入会更顺手。',
      actionText: '去用户管理',
      to: '/users',
      completed: setupStatus.hasStaffAccount,
    },
    {
      key: 'finance',
      title: '导入第一批财务流水',
      description: setupStatus.hasFinanceRecord
        ? '系统已经检测到财务记录，趋势图和 AI 摘要会自动更新。'
        : '打开财务账本导入收入和支出，首页才会形成经营趋势观察。',
      actionText: '去财务账本',
      to: '/finance',
      completed: setupStatus.hasFinanceRecord,
    },
  ]
})

const kpiCards = computed(() => {
  if (!dashboard.value) return []

  return [
    {
      key: 'income',
      label: '本月收入',
      value: dashboard.value.totalIncome,
      hint: '当前自然月入账汇总',
      icon: Money,
      tone: 'income',
      badge: '实时更新',
    },
    {
      key: 'expense',
      label: '本月支出',
      value: dashboard.value.totalExpense,
      hint: '当前自然月支出合计',
      icon: DataAnalysis,
      tone: 'expense',
      badge: '成本观察',
    },
    {
      key: 'profit',
      label: '本月净利润',
      value: dashboard.value.netProfit,
      hint: '收入减去支出',
      icon: TrendCharts,
      tone: toNumber(dashboard.value.netProfit) >= 0 ? 'income' : 'expense',
      badge: toNumber(dashboard.value.netProfit) >= 0 ? '经营向好' : '利润承压',
    },
    {
      key: 'tax',
      label: '待缴税额',
      value: dashboard.value.unpaidTax,
      hint: '全部未缴税额汇总',
      icon: WarningFilled,
      tone: dashboard.value.hasUnpaidWarning ? 'warning' : 'neutral',
      badge: dashboard.value.hasUnpaidWarning ? '需优先处理' : '税务平稳',
    },
  ] as Array<{
    key: string
    label: string
    value: number
    hint: string
    icon: Component
    tone: 'income' | 'expense' | 'warning' | 'neutral'
    badge: string
  }>
})

const chartMonths = computed(() => dashboard.value?.monthlyTrend ?? [])
const departmentHeadcount = computed(() => dashboard.value?.departmentHeadcount ?? [])
const taxCalendar = computed(() => dashboard.value?.taxCalendar ?? [])
const exportTaxCalendar = computed(() => taxCalendar.value.slice(0, 4))
const remainingTaxCalendarCount = computed(() => Math.max(taxCalendar.value.length - exportTaxCalendar.value.length, 0))
const canExportHome = computed(() => Boolean(dashboard.value) && !loading.value && !errorMessage.value && !exporting.value)
const aiSummaryLines = computed(() => aiSummary.value?.summaryLines?.filter((line) => line?.trim()) ?? [])
const homeAiSummaryLines = computed(() => aiSummaryLines.value.slice(0, 2))
const aiSummaryGeneratedText = computed(() =>
  aiSummary.value?.generatedAt ? formatGeneratedAt(aiSummary.value.generatedAt) : '',
)
const exportAiLines = computed(() => {
  if (aiSummaryLines.value.length) return aiSummaryLines.value
  if (aiSummaryLoading.value) return ['AI 总结生成中...']
  if (aiSummaryError.value) return ['AI 摘要暂时不可用，可进入 AI 助理继续追问。']
  return ['AI 会根据近 6 个完整月份的经营数据自动生成短摘要。']
})

watch(
  [loading, hasAnyData, chartMonths, departmentHeadcount],
  async () => {
    await syncCharts()
  },
  { deep: true, flush: 'post' },
)

onMounted(() => {
  void fetchDashboardData()
})

onBeforeUnmount(() => {
  disconnectChartObserver()
  disposeCharts()
})

async function fetchDashboardData() {
  loading.value = true
  errorMessage.value = ''
  aiSummary.value = null
  aiSummaryError.value = ''
  aiSummaryLoading.value = false
  disconnectChartObserver()
  disposeCharts()

  try {
    const res = await getHomeDashboard()
    dashboard.value = normalizeDashboard(res.data)
    loading.value = false
    await nextTick()
    await syncCharts()
    void fetchAiSummary()
  } catch (error) {
    errorMessage.value = (error as { message?: string })?.message || '首页数据加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function fetchAiSummary() {
  aiSummaryLoading.value = true
  aiSummaryError.value = ''

  try {
    const res = await getHomeAiSummary()
    aiSummary.value = normalizeAiSummary(res.data)
  } catch (error) {
    aiSummaryError.value = (error as { message?: string })?.message || 'AI 摘要暂时不可用'
  } finally {
    aiSummaryLoading.value = false
  }
}

async function handleExportHome() {
  if (!canExportHome.value) return

  exporting.value = true

  try {
    await syncCharts()
    homeTrendChartImage.value = getTrendChartDataUrl()
    homeDepartmentChartImage.value = getDepartmentChartDataUrl()
    showExportStage.value = true
    await nextTick()

    const sections = [homeExportHeroPageRef.value, homeExportInsightPageRef.value]
      .filter((element): element is HTMLDivElement => Boolean(element))
      .map((element, index) => ({
        element,
        fitToPage: true,
        pageBreakBefore: index > 0,
      }))

    await exportSectionsToPdf({
      sections,
      fileNameParts: [
        userStore.companyName || '当前企业',
        '首页驾驶舱',
        exportPeriodLabel.value,
        formatPdfTimestamp(),
      ],
      loadingText: '正在生成首页驾驶舱 PDF 报告...',
      orientation: 'portrait',
    })

    ElMessage.success('首页驾驶舱 PDF 报告已开始下载')
  } catch (error) {
    ElMessage.error((error as { message?: string })?.message || '首页 PDF 导出失败，请稍后重试')
  } finally {
    showExportStage.value = false
    homeTrendChartImage.value = ''
    homeDepartmentChartImage.value = ''
    exporting.value = false
  }
}

function normalizeDashboard(data: HomeDashboardVO): HomeDashboardVO {
  return {
    totalIncome: toNumber(data.totalIncome),
    totalExpense: toNumber(data.totalExpense),
    netProfit: toNumber(data.netProfit),
    unpaidTax: toNumber(data.unpaidTax),
    hasUnpaidWarning: Boolean(data.hasUnpaidWarning),
    setupStatus: {
      hasStaffAccount: Boolean(data.setupStatus?.hasStaffAccount),
      hasFinanceRecord: Boolean(data.setupStatus?.hasFinanceRecord),
    },
    monthlyTrend: (data.monthlyTrend || []).map((point) => ({
      month: point.month,
      income: toNumber(point.income),
      expense: toNumber(point.expense),
      profit: toNumber(point.profit),
    })),
    departmentHeadcount: (data.departmentHeadcount || []).map((item) => ({
      department: item.department,
      employeeCount: toNumber(item.employeeCount),
    })),
    taxCalendar: (data.taxCalendar || []).map((item) => ({
      taxPeriod: item.taxPeriod,
      taxType: item.taxType,
      status: item.status,
      amount: toNumber(item.amount),
    })),
  }
}

function normalizeAiSummary(data: HomeAiSummaryVO): HomeAiSummaryVO {
  return {
    generatedAt: data.generatedAt,
    summaryLines: (data.summaryLines || []).map((line) => line.trim()).filter(Boolean),
  }
}

function disposeCharts() {
  trendChart?.dispose()
  trendChart = null
  departmentChart?.dispose()
  departmentChart = null
}

function disconnectChartObserver() {
  resizeObserver?.disconnect()
  resizeObserver = null
}

async function syncCharts() {
  if (loading.value) return

  await nextTick()

  if (!trendChartRef.value && !departmentChartRef.value) {
    disconnectChartObserver()
    disposeCharts()
    return
  }

  renderTrendChart()
  renderDepartmentChart()
  observeChartContainers()

  requestAnimationFrame(() => {
    trendChart?.resize()
    departmentChart?.resize()
  })
}

function renderTrendChart() {
  if (!trendChartRef.value || !chartMonths.value.length) {
    trendChart?.dispose()
    trendChart = null
    return
  }

  if (!trendChart) {
    trendChart = init(trendChartRef.value)
  }

  trendChart.setOption({
    color: ['#1473e6', '#dd5a3b', '#2a9d99'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      extraCssText: 'box-shadow: rgba(0,0,0,0.08) 0 12px 36px; border-radius: 12px;',
      formatter(params: Array<{ axisValue: string; marker: string; seriesName: string; value: number }>) {
        const title = formatMonthTitle(params[0]?.axisValue || '')
        const lines = params.map((item) =>
          `${item.marker}${item.seriesName}<span style="float:right;margin-left:18px;font-weight:600;">${formatCurrency(item.value)}</span>`,
        )
        return `<div style="min-width: 180px;">
          <div style="font-weight:700;margin-bottom:8px;">${title}</div>
          ${lines.join('')}
        </div>`
      },
    },
    legend: {
      top: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 8,
      textStyle: {
        color: '#61656f',
        fontSize: 12,
      },
    },
    grid: {
      top: 44,
      left: 10,
      right: 12,
      bottom: 8,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chartMonths.value.map((point) => point.month),
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#61656f',
        hideOverlap: true,
        formatter: (value: string) => formatMonthTick(value),
      },
    },
    yAxis: {
      type: 'value',
      splitNumber: 4,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: '#61656f',
        formatter: (value: number) => formatAxisCurrency(value),
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(0,0,0,0.06)',
        },
      },
    },
    series: [
      {
        name: '收入',
        type: 'line',
        smooth: false,
        showSymbol: false,
        data: chartMonths.value.map((point) => point.income),
        lineStyle: { width: 3 },
        itemStyle: { color: '#1473e6' },
      },
      {
        name: '支出',
        type: 'line',
        smooth: false,
        showSymbol: false,
        data: chartMonths.value.map((point) => point.expense),
        lineStyle: { width: 3 },
        itemStyle: { color: '#dd5a3b' },
      },
      {
        name: '净利润',
        type: 'line',
        smooth: false,
        showSymbol: false,
        data: chartMonths.value.map((point) => point.profit),
        lineStyle: { width: 3 },
        itemStyle: { color: '#2a9d99' },
      },
    ],
  })
}

function renderDepartmentChart() {
  if (!departmentChartRef.value || !departmentHeadcount.value.length) {
    departmentChart?.dispose()
    departmentChart = null
    return
  }

  if (!departmentChart) {
    departmentChart = init(departmentChartRef.value)
  }

  departmentChart.setOption({
    color: ['#1473e6', '#2a9d99', '#dd5a3b', '#f4a261', '#6c8a9b', '#7aa95c'],
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: 'rgba(0,0,0,0.08)',
      borderWidth: 1,
      textStyle: { color: 'rgba(0,0,0,0.85)' },
      extraCssText: 'box-shadow: rgba(0,0,0,0.08) 0 12px 36px; border-radius: 12px;',
      formatter(params: { marker: string; name: string; value: number; percent: number }) {
        return `${params.marker}${params.name}<span style="float:right;margin-left:18px;font-weight:600;">${params.value} 人</span><br/><span style="color:#667085;">占比 ${params.percent}%</span>`
      },
    },
    legend: {
      orient: 'vertical',
      right: 6,
      top: 'middle',
      icon: 'circle',
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 14,
      textStyle: {
        color: '#61656f',
        fontSize: 12,
      },
    },
    series: [
      {
        name: '部门人数',
        type: 'pie',
        radius: '72%',
        center: ['33%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderColor: '#ffffff',
          borderWidth: 3,
        },
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          scale: true,
          label: { show: false },
        },
        data: departmentHeadcount.value.map((item) => ({
          name: item.department,
          value: item.employeeCount,
        })),
      },
    ],
  })
}

function observeChartContainers() {
  disconnectChartObserver()

  if (!trendChartRef.value && !departmentChartRef.value) return

  resizeObserver = new ResizeObserver(() => {
    trendChart?.resize()
    departmentChart?.resize()
  })
  if (trendChartRef.value) {
    resizeObserver.observe(trendChartRef.value)
  }
  if (departmentChartRef.value) {
    resizeObserver.observe(departmentChartRef.value)
  }
}

function getTrendChartDataUrl() {
  if (!chartMonths.value.length || !trendChart) return ''

  return trendChart.getDataURL({
    pixelRatio: 2,
    backgroundColor: '#ffffff',
  })
}

function getDepartmentChartDataUrl() {
  if (!departmentHeadcount.value.length || !departmentChart) return ''

  return departmentChart.getDataURL({
    pixelRatio: 2,
    backgroundColor: '#ffffff',
  })
}

function formatCurrency(value: number) {
  return `¥${toNumber(value).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function formatAxisCurrency(value: number) {
  const absolute = Math.abs(value)
  if (absolute >= 10000) {
    return `¥${(value / 10000).toFixed(1)}万`
  }
  return `¥${value.toLocaleString('zh-CN')}`
}

function formatMonthTick(value: string) {
  const [year, month] = value.split('-')
  return `${year.slice(2)}/${month}`
}

function formatMonthTitle(value: string) {
  const [year, month] = value.split('-')
  return `${year} 年 ${month} 月`
}

function formatGeneratedAt(value: string) {
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return `${parsed.getMonth() + 1}月${String(parsed.getDate()).padStart(2, '0')}日 ${String(parsed.getHours()).padStart(2, '0')}:${String(parsed.getMinutes()).padStart(2, '0')} 更新`
}

function getTaxStatusLabel(status: TaxCalendarItem['status']) {
  if (status === 1) return '已缴'
  if (status === 2) return '免征'
  return '待缴'
}

function getTaxStatusClass(status: TaxCalendarItem['status']) {
  if (status === 1) return 'is-paid'
  if (status === 2) return 'is-exempt'
  return 'is-unpaid'
}

function navigateTo(path: string) {
  router.push(path)
}

function toNumber(value: number | string | undefined | null) {
  const numeric = Number(value ?? 0)
  return Number.isFinite(numeric) ? numeric : 0
}
</script>

<template>
  <div class="home-view">
    <template v-if="showLoadingSkeleton">
      <section class="welcome-card welcome-card--skeleton ds-card">
        <div class="skeleton-line skeleton-chip" />
        <div class="welcome-skeleton-grid">
          <div class="welcome-skeleton-main">
            <div class="skeleton-line skeleton-title" />
            <div class="skeleton-line skeleton-body" />
            <div class="meta-skeleton-grid">
              <div v-for="item in 4" :key="item" class="meta-skeleton" />
            </div>
          </div>
          <div class="ai-skeleton-card">
            <div class="skeleton-line skeleton-chip" />
            <div class="skeleton-line skeleton-body" />
            <div class="skeleton-line skeleton-body short" />
            <div class="skeleton-line skeleton-body short" />
          </div>
        </div>
      </section>

      <section class="kpi-grid">
        <div v-for="item in 4" :key="item" class="kpi-card ds-card">
          <div class="skeleton-line skeleton-chip" />
          <div class="skeleton-line skeleton-number" />
          <div class="skeleton-line skeleton-body short" />
        </div>
      </section>

      <section class="home-main">
        <div class="insight-grid">
          <div class="panel-card ds-card">
            <div class="skeleton-line skeleton-body short" />
            <div class="chart-skeleton" />
          </div>
          <div class="panel-card ds-card">
            <div class="skeleton-line skeleton-body short" />
            <div class="chart-skeleton" />
          </div>
        </div>
        <div class="panel-card ds-card">
          <div class="skeleton-line skeleton-body short" />
          <div class="timeline-skeleton">
            <div v-for="item in 4" :key="item" class="timeline-skeleton-row" />
          </div>
        </div>
      </section>
    </template>

    <section v-else-if="errorMessage && !dashboard" class="state-panel ds-card">
      <h2>首页数据暂时不可用</h2>
      <p>{{ errorMessage }}</p>
      <el-button type="primary" :icon="RefreshRight" @click="fetchDashboardData">重新加载</el-button>
    </section>

    <template v-else-if="dashboard">
      <section class="welcome-card ds-card">
        <div class="welcome-grid">
          <div class="company-pane">
            <div class="welcome-meta company-pane__meta">
              <span class="welcome-badge">Owner Cockpit</span>
              <span class="welcome-period">{{ currentPeriodLabel }}</span>
            </div>
            <span class="eyebrow">欢迎回来，{{ userStore.realName || '老板' }}</span>
            <h1>{{ userStore.companyName || '当前企业' }}</h1>
            <div class="company-inline-meta">
              <span
                v-for="item in companyInlineMeta"
                :key="item"
              >
                {{ item }}
              </span>
            </div>

            <div class="company-pane__footer">
              <el-button
                class="company-export-button"
                plain
                :icon="Download"
                :loading="exporting"
                :disabled="!canExportHome"
                @click="handleExportHome"
              >
                导出 PDF 报告
              </el-button>
            </div>
          </div>

          <aside class="ai-brief">
            <div class="ai-brief__top">
              <div>
                <span class="eyebrow">AI 经营速记</span>
                <h2>近期两点观察</h2>
              </div>
              <span v-if="aiSummaryGeneratedText" class="ai-generated-at">{{ aiSummaryGeneratedText }}</span>
            </div>

            <div class="ai-brief__body">
              <div class="ai-brief__content">
                <template v-if="aiSummaryLoading">
                  <div class="ai-loading">
                    <div class="skeleton-line skeleton-body" />
                    <div class="skeleton-line skeleton-body short" />
                    <div class="skeleton-line skeleton-body short" />
                  </div>
                  <p class="ai-helper-text">AI 总结生成中...</p>
                </template>

                <template v-else-if="homeAiSummaryLines.length">
                  <div class="ai-summary-list">
                    <div
                      v-for="(line, index) in homeAiSummaryLines"
                      :key="line"
                      class="ai-summary-line"
                    >
                      <span class="ai-summary-line__index">{{ String(index + 1).padStart(2, '0') }}</span>
                      <span class="ai-summary-line__text">{{ line }}</span>
                    </div>
                  </div>
                </template>

                <div v-else class="ai-empty">
                  <p>{{ aiSummaryError || 'AI 摘要暂时不可用，仍可进入 AI 助理继续追问。' }}</p>
                </div>
              </div>

              <div class="ai-brief__action">
                <button type="button" class="ai-brief__cta" @click="navigateTo('/ai-chat')">
                  <span class="ai-brief__cta-badge">AI 助理</span>
                  <span class="ai-brief__cta-main">
                    <span class="ai-brief__cta-label">继续问 AI</span>
                    <el-icon class="ai-brief__cta-icon" :size="18"><ChatDotRound /></el-icon>
                  </span>
                </button>
              </div>
            </div>
          </aside>
        </div>
      </section>

      <section class="kpi-grid">
        <article
          v-for="card in kpiCards"
          :key="card.key"
          class="kpi-card ds-card"
          :class="[`tone-${card.tone}`]"
        >
          <div class="kpi-top">
            <div class="kpi-icon">
              <el-icon :size="18"><component :is="card.icon" /></el-icon>
            </div>
            <span class="kpi-badge">{{ card.badge }}</span>
          </div>
          <div class="kpi-label">{{ card.label }}</div>
          <div class="kpi-value">{{ formatCurrency(card.value) }}</div>
          <div class="kpi-hint">{{ card.hint }}</div>
        </article>
      </section>

      <section v-if="!hasAnyData" class="home-main home-main--empty">
        <div class="state-panel ds-card">
          <h2>首页已经就绪，先完成企业启动动作</h2>
          <p>只要补齐账号和第一批流水，趋势图、税务时间轴和 AI 经营速记都会自动生成。</p>

          <div class="startup-task-grid">
            <button
              v-for="task in startupTasks"
              :key="task.key"
              type="button"
              class="startup-task"
              :class="{ 'is-complete': task.completed }"
              @click="navigateTo(task.to)"
            >
              <div class="startup-task__top">
                <span class="startup-task__badge">{{ task.completed ? '已完成' : '待完成' }}</span>
                <span class="startup-task__cta">{{ task.actionText }}</span>
              </div>
              <h3>{{ task.title }}</h3>
              <p>{{ task.description }}</p>
            </button>
          </div>
        </div>
      </section>

      <section v-else class="home-main">
        <div class="insight-grid">
          <article class="chart-panel ds-card">
            <div class="panel-header">
              <div>
                <span class="eyebrow">经营走势</span>
                <h2>近半年盈亏趋势</h2>
              </div>
            </div>
            <div ref="trendChartRef" class="trend-chart" />
          </article>

          <article class="chart-panel chart-panel--pie ds-card">
            <div class="panel-header">
              <div>
                <span class="eyebrow">组织结构</span>
                <h2>各部门人数饼图</h2>
              </div>
            </div>
            <div v-if="departmentHeadcount.length" ref="departmentChartRef" class="department-chart" />
            <div v-else class="panel-empty compact">
              <h3>暂无部门人数数据</h3>
              <p>录入员工后，这里会自动汇总各部门当前在岗人数。</p>
            </div>
          </article>
        </div>

        <article class="timeline-panel ds-card">
          <div class="panel-header">
            <div>
              <span class="eyebrow">税务提醒</span>
              <h2>税务时间轴</h2>
            </div>
          </div>

          <div v-if="taxCalendar.length" class="timeline-scroll">
            <div class="timeline-list">
              <div
                v-for="item in taxCalendar"
                :key="`${item.taxPeriod}-${item.taxType}-${item.status}`"
                class="timeline-item"
              >
                <div class="timeline-rail">
                  <span class="timeline-dot" :class="getTaxStatusClass(item.status)" />
                </div>
                <div class="timeline-content">
                  <div class="timeline-top">
                    <span class="timeline-period">{{ item.taxPeriod }}</span>
                    <span class="timeline-status" :class="getTaxStatusClass(item.status)">
                      {{ getTaxStatusLabel(item.status) }}
                    </span>
                  </div>
                  <div class="timeline-title">{{ item.taxType }}</div>
                  <div class="timeline-amount">{{ formatCurrency(item.amount) }}</div>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="panel-empty">
            <h3>暂无税务节点</h3>
            <p>税务档案录入后，这里会自动汇总待缴、已缴和免征节点。</p>
          </div>
        </article>
      </section>

      <div v-if="showExportStage" class="pdf-export-stage" aria-hidden="true">
        <section ref="homeExportHeroPageRef" class="pdf-page pdf-page--hero">
          <div class="pdf-page-header">
            <span class="welcome-badge">Owner Cockpit</span>
            <span class="welcome-period">{{ exportPeriodLabel }}</span>
          </div>

          <section class="pdf-welcome-card ds-card">
            <div class="pdf-company-pane">
              <span class="eyebrow">欢迎回来，{{ userStore.realName || '老板' }}</span>
              <h2>{{ userStore.companyName || '当前企业' }}</h2>
              <div class="company-inline-meta company-inline-meta--pdf">
                <span
                  v-for="item in companyInlineMeta"
                  :key="`pdf-${item}`"
                >
                  {{ item }}
                </span>
              </div>
              <p class="pdf-company-brief">本页聚焦企业身份、本月经营指标，以及 AI 对近期经营动态的简短提炼。</p>
            </div>
          </section>

          <section class="pdf-ai-card ds-card">
            <div class="pdf-ai-card__head">
              <div>
                <span class="eyebrow">AI 经营速记</span>
                <h3>近期经营观察</h3>
              </div>
              <span v-if="aiSummaryGeneratedText" class="ai-generated-at">{{ aiSummaryGeneratedText }}</span>
            </div>

            <div class="pdf-ai-card__grid">
              <div
                v-for="(line, index) in exportAiLines"
                :key="`pdf-ai-${line}`"
                class="pdf-ai-line"
              >
                <span class="pdf-ai-line__index">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="pdf-ai-line__text">{{ line }}</span>
              </div>
            </div>
          </section>

          <section class="pdf-kpi-grid">
            <article
              v-for="card in kpiCards"
              :key="`pdf-${card.key}`"
              class="kpi-card ds-card"
              :class="[`tone-${card.tone}`]"
            >
              <div class="kpi-top">
                <div class="kpi-icon">
                  <el-icon :size="18"><component :is="card.icon" /></el-icon>
                </div>
                <span class="kpi-badge">{{ card.badge }}</span>
              </div>
              <div class="kpi-label">{{ card.label }}</div>
              <div class="kpi-value">{{ formatCurrency(card.value) }}</div>
              <div class="kpi-hint">{{ card.hint }}</div>
            </article>
          </section>
        </section>

        <section ref="homeExportInsightPageRef" class="pdf-page">
          <div class="pdf-page-header">
            <div>
              <span class="eyebrow">经营洞察</span>
              <h3 class="pdf-section-title">图表与税务摘要</h3>
            </div>
            <span class="welcome-period">{{ userStore.companyName || '当前企业' }}</span>
          </div>

          <template v-if="hasAnyData">
            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <span class="eyebrow">经营图表</span>
                  <h2>半年盈亏与部门人数</h2>
                </div>
              </div>

              <div class="pdf-chart-grid">
                <div class="pdf-chart-stack">
                  <span class="pdf-mini-title">近半年盈亏趋势</span>
                  <div v-if="homeTrendChartImage" class="pdf-chart-frame">
                    <img :src="homeTrendChartImage" alt="近半年盈亏趋势图" class="pdf-chart-image" />
                  </div>
                  <div v-else class="panel-empty compact">
                    <h3>暂无趋势图</h3>
                    <p>当前没有足够经营数据生成趋势图。</p>
                  </div>
                </div>

                <div class="pdf-chart-stack">
                  <span class="pdf-mini-title">各部门人数饼图</span>
                  <div v-if="homeDepartmentChartImage" class="pdf-chart-frame">
                    <img :src="homeDepartmentChartImage" alt="各部门人数饼图" class="pdf-chart-image" />
                  </div>
                  <div v-else class="panel-empty compact">
                    <h3>暂无部门人数图</h3>
                    <p>录入员工后，这里会自动生成各部门人数分布。</p>
                  </div>
                </div>
              </div>
            </article>

            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <span class="eyebrow">税务提醒</span>
                  <h2>税务时间轴摘要</h2>
                  <p>PDF 仅保留最近 4 条税务节点，避免报告页数过长。</p>
                </div>
              </div>

              <div v-if="exportTaxCalendar.length" class="pdf-timeline-summary">
                <div
                  v-for="item in exportTaxCalendar"
                  :key="`pdf-${item.taxPeriod}-${item.taxType}-${item.status}`"
                  class="pdf-timeline-row"
                >
                  <span class="timeline-dot" :class="getTaxStatusClass(item.status)" />
                  <div class="pdf-timeline-text">
                    <div class="pdf-timeline-top">
                      <span class="timeline-period">{{ item.taxPeriod }}</span>
                      <span class="timeline-status" :class="getTaxStatusClass(item.status)">
                        {{ getTaxStatusLabel(item.status) }}
                      </span>
                    </div>
                    <strong>{{ item.taxType }}</strong>
                    <span>{{ formatCurrency(item.amount) }}</span>
                  </div>
                </div>
              </div>
              <div v-else class="panel-empty compact">
                <h3>暂无税务节点</h3>
                <p>税务档案录入后，这里会自动汇总最近税务摘要。</p>
              </div>

              <p v-if="remainingTaxCalendarCount > 0" class="pdf-footnote">
                其余 {{ remainingTaxCalendarCount }} 条税务节点请在系统内查看完整时间轴。
              </p>
            </article>
          </template>

          <article v-else class="pdf-card ds-card pdf-empty-card">
            <div class="panel-header">
              <div>
                <h2>当前暂无可导出的经营洞察</h2>
                <p>先录入财务流水或税务档案，系统就会生成趋势图和税务摘要。</p>
              </div>
            </div>
          </article>
        </section>
      </div>
    </template>
  </div>
</template>

<style scoped>
.home-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 100%;
}

.welcome-card {
  padding: 20px 22px 22px;
  background:
    radial-gradient(circle at top right, rgba(0, 117, 222, 0.14), transparent 34%),
    linear-gradient(145deg, #ffffff 0%, #f5f9ff 52%, #f7f4f1 100%);
}

.welcome-top,
.welcome-meta,
.panel-header,
.kpi-top,
.timeline-top,
.pdf-page-header,
.pdf-timeline-top {
  display: flex;
  align-items: center;
}

.pdf-page-header,
.timeline-top,
.pdf-timeline-top {
  justify-content: space-between;
}

.welcome-meta {
  gap: 12px;
  flex-wrap: wrap;
}

.welcome-badge,
.kpi-badge,
.timeline-status,
.startup-task__badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  font-weight: 700;
}

.welcome-badge {
  padding: 6px 12px;
  background: rgba(0, 117, 222, 0.1);
  color: #0f6ccb;
  font-size: 12px;
  letter-spacing: 0.06em;
}

.welcome-period {
  font-size: 13px;
  color: #667085;
  font-weight: 600;
}

.welcome-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(380px, 1.18fr);
  gap: 16px;
  align-items: stretch;
}

.company-pane h1,
.panel-header h2,
.state-panel h2,
.pdf-company-pane h2 {
  color: rgba(15, 23, 42, 0.96);
  letter-spacing: -0.04em;
}

.company-pane {
  display: flex;
  flex-direction: column;
  min-height: 100%;
}

.company-pane__meta {
  margin-bottom: 10px;
}

.company-pane h1 {
  margin-top: 8px;
  font-size: 32px;
  font-weight: 800;
}

.company-inline-meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.6;
  color: #667085;
}

.company-inline-meta span {
  position: relative;
}

.company-inline-meta span:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 50%;
  right: -10px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(136, 146, 165, 0.62);
  transform: translateY(-50%);
}

.company-inline-meta--pdf {
  margin-top: 14px;
  gap: 8px 20px;
  font-size: 13px;
}

.company-pane__footer {
  margin-top: auto;
  padding-top: 14px;
}

.company-export-button.el-button {
  border-radius: 16px;
  border-color: rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.78);
  color: rgba(15, 23, 42, 0.82);
  font-weight: 700;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.company-export-button.el-button:hover,
.company-export-button.el-button:focus-visible {
  border-color: rgba(42, 157, 153, 0.22);
  background: rgba(255, 255, 255, 0.96);
  color: #0f6ccb;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #8892a5;
  text-transform: uppercase;
}

.welcome-copy,
.panel-header p,
.state-panel p,
.pdf-company-pane p,
.pdf-card p,
.ai-helper-text,
.panel-empty p,
.startup-task p {
  color: #667085;
  line-height: 1.7;
}

.welcome-copy {
  margin-top: 10px;
  max-width: 62ch;
  font-size: 13px;
  line-height: 1.65;
}

.company-meta-grid {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.company-meta-item {
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(15, 23, 42, 0.07);
}

.company-meta-item span {
  font-size: 12px;
  font-weight: 700;
  color: #8b93a3;
}

.company-meta-item strong {
  display: block;
  margin-top: 6px;
  font-size: 15px;
  color: rgba(15, 23, 42, 0.94);
}

.ai-brief,
.pdf-ai-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 15px 16px;
  border-radius: 24px;
  background:
    radial-gradient(circle at top left, rgba(42, 157, 153, 0.14), transparent 36%),
    linear-gradient(160deg, rgba(255, 255, 255, 0.92), rgba(245, 250, 255, 0.84));
  border: 1px solid rgba(42, 157, 153, 0.14);
}

.ai-brief__top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.ai-brief__top h2,
.pdf-ai-card__head h3 {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.96);
  letter-spacing: -0.04em;
}

.ai-generated-at {
  display: inline-flex;
  align-items: center;
  padding: 5px 9px;
  border-radius: 999px;
  background: rgba(42, 157, 153, 0.1);
  font-size: 11px;
  color: #5f8f8b;
  font-weight: 700;
}

.ai-brief__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: stretch;
  min-height: 0;
}

.ai-brief__content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.ai-summary-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-summary-line {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: flex-start;
  padding: 9px 10px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.74);
  border: 1px solid rgba(42, 157, 153, 0.1);
}

.ai-summary-line__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(42, 157, 153, 0.12);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #2a7f7b;
}

.ai-summary-line__text {
  font-size: 12.5px;
  line-height: 1.5;
  color: rgba(15, 23, 42, 0.92);
}

.ai-empty {
  display: flex;
  align-items: center;
  min-height: 88px;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  font-size: 12.5px;
  color: #667085;
}

.ai-brief__action {
  display: flex;
  min-width: 148px;
  align-self: stretch;
}

.ai-brief__cta {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(42, 157, 153, 0.1);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.74);
  color: rgba(15, 23, 42, 0.92);
  cursor: pointer;
  transition:
    transform 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}

.ai-brief__cta:hover,
.ai-brief__cta:focus-visible {
  transform: translateY(-1px);
  border-color: rgba(42, 157, 153, 0.22);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 12px 24px rgba(42, 157, 153, 0.12);
  outline: none;
}

.ai-brief__cta-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(42, 157, 153, 0.12);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #2a7f7b;
}

.ai-brief__cta-label {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
  text-align: left;
}

.ai-brief__cta-main {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.ai-brief__cta-icon {
  flex: none;
  color: #2a9d99;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.kpi-card {
  padding: 14px 16px 16px;
}

.kpi-top {
  justify-content: space-between;
  gap: 12px;
}

.kpi-icon {
  width: 34px;
  height: 34px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fb;
  color: #667085;
}

.kpi-badge {
  padding: 3px 8px;
  font-size: 11px;
  background: #f5f7fb;
  color: #667085;
}

.kpi-label {
  margin-top: 12px;
  font-size: 12px;
  font-weight: 600;
  color: #667085;
}

.kpi-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.96);
  letter-spacing: -0.05em;
  font-variant-numeric: tabular-nums;
}

.kpi-hint {
  margin-top: 6px;
  font-size: 11px;
  color: #9aa1af;
}

.tone-income .kpi-icon,
.tone-income .kpi-badge {
  background: rgba(20, 115, 230, 0.12);
  color: #1473e6;
}

.tone-expense .kpi-icon,
.tone-expense .kpi-badge {
  background: rgba(221, 90, 59, 0.12);
  color: #dd5a3b;
}

.tone-warning .kpi-icon,
.tone-warning .kpi-badge,
.is-unpaid {
  background: rgba(221, 91, 0, 0.12);
  color: #dd5b00;
}

.tone-neutral .kpi-icon,
.tone-neutral .kpi-badge,
.is-paid {
  background: rgba(102, 112, 133, 0.12);
  color: #667085;
}

.is-exempt {
  background: rgba(42, 157, 153, 0.12);
  color: #2a9d99;
}

.home-main {
  display: grid;
  grid-template-columns: minmax(0, 1.78fr) minmax(248px, 0.72fr);
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  min-height: 0;
}

.home-main--empty {
  grid-template-columns: 1fr;
}

.chart-panel,
.timeline-panel,
.state-panel,
.panel-card,
.pdf-card,
.pdf-welcome-card {
  padding: 18px;
}

.chart-panel,
.timeline-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.timeline-panel {
  padding: 20px 18px 20px 20px;
}

.panel-header {
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.panel-header h2,
.state-panel h2 {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 800;
}

.panel-header p,
.state-panel p {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
}

.trend-chart {
  margin-top: 12px;
  flex: 1;
  min-height: 252px;
  width: 100%;
}

.department-chart {
  margin-top: 12px;
  flex: 1;
  min-height: 252px;
  width: 100%;
}

.timeline-scroll {
  margin-top: 14px;
  min-height: 252px;
  max-height: 312px;
  overflow-y: auto;
  padding-right: 8px;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.timeline-item {
  display: flex;
  gap: 14px;
}

.timeline-rail {
  position: relative;
  width: 14px;
  display: flex;
  justify-content: center;
}

.timeline-rail::after {
  content: '';
  position: absolute;
  top: 18px;
  bottom: -18px;
  width: 1px;
  background: rgba(15, 23, 42, 0.08);
}

.timeline-item:last-child .timeline-rail::after {
  display: none;
}

.timeline-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-top: 6px;
  border: 3px solid #ffffff;
  box-shadow: 0 0 0 1px rgba(15, 23, 42, 0.08);
}

.timeline-dot.is-unpaid {
  background: #dd5b00;
}

.timeline-dot.is-paid {
  background: #667085;
}

.timeline-dot.is-exempt {
  background: #2a9d99;
}

.timeline-content {
  flex: 1;
  padding-bottom: 14px;
}

.timeline-period {
  font-size: 13px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.92);
}

.timeline-status {
  padding: 4px 10px;
  font-size: 12px;
}

.timeline-title {
  margin-top: 6px;
  font-size: 15px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.94);
}

.timeline-amount {
  margin-top: 6px;
  font-size: 16px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.96);
  font-variant-numeric: tabular-nums;
}

.panel-empty {
  margin-top: 18px;
  min-height: 260px;
  border-radius: 20px;
  background: #f6f7fa;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px;
  text-align: center;
}

.panel-empty h3 {
  font-size: 18px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.94);
}

.state-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.startup-task-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.startup-task {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.88);
  border-radius: 18px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.startup-task:hover {
  transform: translateY(-2px);
  border-color: rgba(20, 115, 230, 0.18);
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.08);
}

.startup-task.is-complete {
  background: linear-gradient(145deg, rgba(42, 157, 153, 0.08), #ffffff 64%);
  border-color: rgba(42, 157, 153, 0.16);
}

.startup-task__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.startup-task__badge {
  padding: 4px 10px;
  background: #f6f7fa;
  color: #667085;
  font-size: 12px;
}

.startup-task__cta {
  font-size: 12px;
  font-weight: 700;
  color: #1473e6;
}

.startup-task h3 {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.94);
}

.skeleton-line,
.meta-skeleton,
.timeline-skeleton-row,
.chart-skeleton {
  background: #eef1f5;
  border-radius: 999px;
}

.welcome-card--skeleton {
  overflow: hidden;
}

.welcome-skeleton-grid {
  margin-top: 22px;
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(320px, 0.95fr);
  gap: 20px;
}

.welcome-skeleton-main,
.ai-skeleton-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.meta-skeleton-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.meta-skeleton {
  height: 84px;
  border-radius: 20px;
}

.ai-skeleton-card {
  padding: 20px 22px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.72);
}

.skeleton-chip {
  width: 120px;
  height: 24px;
}

.skeleton-title {
  width: 54%;
  height: 36px;
}

.skeleton-body {
  width: 100%;
  height: 14px;
}

.skeleton-body.short {
  width: 72%;
}

.skeleton-number {
  width: 70%;
  height: 34px;
}

.chart-skeleton {
  margin-top: 16px;
  height: 260px;
  border-radius: 24px;
}

.timeline-skeleton {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.timeline-skeleton-row {
  height: 58px;
  border-radius: 18px;
}

.pdf-export-stage {
  position: fixed;
  left: -220vw;
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
  background: #f5f6f8;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.pdf-page--hero {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  gap: 16px;
}

.pdf-section-title {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: rgba(15, 23, 42, 0.96);
}

.pdf-welcome-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  justify-content: center;
}

.pdf-company-pane h2 {
  margin-top: 10px;
  font-size: 34px;
  font-weight: 800;
}

.pdf-company-pane p {
  margin-top: 12px;
  font-size: 15px;
}

.pdf-company-brief {
  max-width: 62ch;
  color: #667085;
  line-height: 1.7;
}

.pdf-company-grid,
.pdf-kpi-grid {
  display: grid;
  gap: 14px;
}

.pdf-company-grid {
  margin-top: 18px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.pdf-kpi-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-auto-rows: 1fr;
}

.pdf-kpi-grid .kpi-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  padding: 18px;
}

.pdf-ai-card {
  gap: 14px;
  padding: 18px;
}

.pdf-ai-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.pdf-ai-card__head h3 {
  font-size: 24px;
}

.pdf-ai-card__grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pdf-ai-line {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  min-width: 0;
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(42, 157, 153, 0.1);
  align-items: flex-start;
}

.pdf-ai-line__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  min-width: 32px;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(42, 157, 153, 0.12);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
  color: #2a7f7b;
}

.pdf-ai-line__text {
  font-size: 14px;
  line-height: 1.65;
  color: rgba(15, 23, 42, 0.92);
}

.pdf-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pdf-chart-frame {
  border-radius: 18px;
  background: #ffffff;
  padding: 12px;
}

.pdf-chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.pdf-chart-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pdf-mini-title {
  font-size: 14px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.92);
}

.pdf-chart-image {
  display: block;
  width: 100%;
  border-radius: 12px;
}

.pdf-timeline-summary {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pdf-timeline-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.pdf-timeline-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pdf-timeline-text strong {
  font-size: 16px;
  color: rgba(15, 23, 42, 0.94);
}

.pdf-timeline-text span:last-child {
  font-size: 14px;
  color: #667085;
}

.pdf-footnote {
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px dashed rgba(15, 23, 42, 0.08);
  font-size: 13px;
}

.pdf-empty-card {
  margin-top: auto;
}

.panel-empty.compact {
  min-height: 180px;
}

@media (max-width: 1180px) {
  .insight-grid,
  .pdf-chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1320px) {
  .welcome-grid,
  .welcome-skeleton-grid,
  .home-main {
    grid-template-columns: 1fr;
  }

  .ai-brief__body {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .ai-brief__action {
    justify-content: flex-start;
    min-width: 0;
  }
}

@media (max-height: 900px) {
  .home-view {
    gap: 10px;
  }

  .welcome-card {
    padding: 18px 20px 20px;
  }

  .welcome-grid {
    margin-top: 14px;
    gap: 14px;
  }

  .company-pane h1 {
    font-size: 29px;
  }

  .company-meta-grid {
    margin-top: 14px;
  }

  .ai-brief,
  .pdf-ai-card {
    gap: 12px;
    padding: 14px 16px;
  }

  .ai-brief__top h2,
  .pdf-ai-card__head h3 {
    font-size: 17px;
  }

  .ai-brief__body {
    min-height: 0;
  }

  .kpi-card {
    padding: 12px 14px 14px;
  }

  .kpi-value {
    font-size: 22px;
  }

  .chart-panel,
  .timeline-panel,
  .state-panel,
  .panel-card,
  .pdf-card,
  .pdf-welcome-card {
    padding: 16px;
  }

  .timeline-panel {
    padding: 18px 16px 18px 18px;
  }

  .panel-header h2,
  .state-panel h2 {
    font-size: 18px;
  }

  .trend-chart {
    min-height: 224px;
  }

  .timeline-scroll {
    min-height: 224px;
    max-height: 268px;
  }
}

@media (max-height: 820px) {
  .welcome-copy,
  .panel-header p,
  .state-panel p,
  .startup-task p,
  .ai-summary-line__text {
    line-height: 1.55;
  }

  .company-pane h1 {
    font-size: 27px;
  }

  .kpi-value {
    font-size: 20px;
  }

  .trend-chart {
    min-height: 208px;
  }

  .timeline-scroll {
    min-height: 208px;
    max-height: 244px;
  }
}

@media (max-width: 1080px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .welcome-card,
  .chart-panel,
  .timeline-panel,
  .state-panel {
    padding: 20px;
  }

  .company-pane h1 {
    font-size: 30px;
  }

  .company-meta-grid,
  .startup-task-grid,
  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .ai-brief__action {
    align-items: flex-start;
  }
}
</style>



