<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useDelayedLoading } from '@/composables/useDelayedLoading'
import { getHomeDashboard } from '@/api/dashboard'
import { exportSectionsToPdf, formatPdfTimestamp } from '@/utils/pdf'
import type { Component } from 'vue'
import type { HomeDashboardVO, TaxCalendarItem } from '@/types'
import {
  DataAnalysis,
  Download,
  Money,
  TrendCharts,
  WarningFilled,
  RefreshRight,
} from '@element-plus/icons-vue'
import { use, type EChartsType } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init } from 'echarts/core'

use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const userStore = useUserStore()
const router = useRouter()

const loading = ref(true)
const exporting = ref(false)
const showExportStage = ref(false)
const errorMessage = ref('')
const dashboard = ref<HomeDashboardVO | null>(null)
const trendChartRef = ref<HTMLDivElement | null>(null)
const homeExportHeroPageRef = ref<HTMLDivElement | null>(null)
const homeExportInsightPageRef = ref<HTMLDivElement | null>(null)
const homeTrendChartImage = ref('')

let trendChart: EChartsType | null = null
let resizeObserver: ResizeObserver | null = null

const currentPeriodLabel = computed(() => {
  const now = new Date()
  const monthLabel = `${now.getFullYear()} 年 ${String(now.getMonth() + 1).padStart(2, '0')} 月`
  return `${monthLabel}经营快照`
})
const showLoadingSkeleton = useDelayedLoading(() => loading.value && !dashboard.value && !errorMessage.value)

const hasAnyData = computed(() => {
  const data = dashboard.value
  if (!data) return false
  if (toNumber(data.totalIncome) > 0) return true
  if (toNumber(data.totalExpense) > 0) return true
  if (toNumber(data.unpaidTax) > 0) return true
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
      title: '创建财务账号',
      description: setupStatus.hasStaffAccount
        ? '已存在可录入的员工账号，可以继续安排录入流程。'
        : '先去用户管理为录入同学创建账号，后续才能分工录入流水。',
      actionText: '去用户管理',
      to: '/users',
      completed: setupStatus.hasStaffAccount,
    },
    {
      key: 'finance',
      title: '导入第一批流水',
      description: setupStatus.hasFinanceRecord
        ? '财务账本已有流水，首页将自动生成趋势和税务提醒。'
        : '打开财务账本，用 Excel 批量导入第一批收入与支出流水。',
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
      badge: '实时汇总',
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
      hint: '本月收入减去支出',
      icon: TrendCharts,
      tone: toNumber(dashboard.value.netProfit) >= 0 ? 'income' : 'expense',
      badge: toNumber(dashboard.value.netProfit) >= 0 ? '正向经营' : '利润承压',
    },
    {
      key: 'tax',
      label: '待缴税额',
      value: dashboard.value.unpaidTax,
      hint: '全部未缴情项汇总',
      icon: WarningFilled,
      tone: dashboard.value.hasUnpaidWarning ? 'warning' : 'neutral',
      badge: dashboard.value.hasUnpaidWarning ? '需关注' : '正常',
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
const taxCalendar = computed(() => dashboard.value?.taxCalendar ?? [])
const exportTaxCalendar = computed(() => taxCalendar.value.slice(0, 4))
const remainingTaxCalendarCount = computed(() => Math.max(taxCalendar.value.length - exportTaxCalendar.value.length, 0))
const canExportHome = computed(() => Boolean(dashboard.value) && !loading.value && !errorMessage.value && !exporting.value)
const exportPeriodLabel = computed(() => {
  const now = new Date()
  return `${now.getFullYear()}年${String(now.getMonth() + 1).padStart(2, '0')}月经营快照`
})

async function fetchDashboardData() {
  loading.value = true
  errorMessage.value = ''
  disconnectTrendObserver()
  disposeTrendChart()

  try {
    const res = await getHomeDashboard()
    dashboard.value = normalizeDashboard(res.data)
    loading.value = false
    await nextTick()
    await syncTrendChart()
  } catch (error) {
    const message = (error as { message?: string })?.message || '首页数据加载失败，请稍后重试'
    errorMessage.value = message
  } finally {
    loading.value = false
  }
}

async function handleExportHome() {
  if (!canExportHome.value) return

  exporting.value = true

  try {
    await syncTrendChart()
    homeTrendChartImage.value = getTrendChartDataUrl()
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
    taxCalendar: (data.taxCalendar || []).map((item) => ({
      taxPeriod: item.taxPeriod,
      taxType: item.taxType,
      status: item.status,
      amount: toNumber(item.amount),
    })),
  }
}

function disposeTrendChart() {
  trendChart?.dispose()
  trendChart = null
}

function disconnectTrendObserver() {
  resizeObserver?.disconnect()
  resizeObserver = null
}

async function syncTrendChart() {
  if (loading.value) return

  await nextTick()

  if (!trendChartRef.value || !chartMonths.value.length) {
    disconnectTrendObserver()
    disposeTrendChart()
    return
  }

  renderTrendChart()
  observeTrendContainer()

  requestAnimationFrame(() => {
    trendChart?.resize()
  })
}

function renderTrendChart() {
  if (!trendChartRef.value || !chartMonths.value.length) {
    disposeTrendChart()
    return
  }

  if (!trendChart) {
    trendChart = init(trendChartRef.value)
  }

  trendChart.setOption({
    color: ['#2a9d99', '#e03e3e', '#0075de'],
    tooltip: {
      trigger: 'axis',
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
      textStyle: {
        color: '#615d59',
        fontSize: 12,
      },
    },
    grid: {
      top: 44,
      left: 12,
      right: 12,
      bottom: 12,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chartMonths.value.map((point) => point.month),
      axisLine: { lineStyle: { color: 'rgba(0,0,0,0.08)' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#615d59',
        formatter: (value: string) => formatMonthTick(value),
      },
    },
    yAxis: {
      type: 'value',
      splitNumber: 4,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: '#615d59',
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
        smooth: true,
        showSymbol: false,
        data: chartMonths.value.map((point) => point.income),
        lineStyle: { width: 3 },
        areaStyle: { color: 'rgba(42, 157, 153, 0.16)' },
      },
      {
        name: '支出',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: chartMonths.value.map((point) => point.expense),
        lineStyle: { width: 3 },
        areaStyle: { color: 'rgba(224, 62, 62, 0.12)' },
      },
      {
        name: '净利润',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: chartMonths.value.map((point) => point.profit),
        lineStyle: { width: 3 },
      },
    ],
  })
}

function observeTrendContainer() {
  disconnectTrendObserver()

  if (!trendChartRef.value) return

  resizeObserver = new ResizeObserver(() => {
    trendChart?.resize()
  })
  resizeObserver.observe(trendChartRef.value)
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
  const month = value.split('-')[1]
  return `${month}月`
}

function formatMonthTitle(value: string) {
  const [year, month] = value.split('-')
  return `${year} 年 ${month} 月`
}

function getTrendChartDataUrl() {
  if (!hasAnyData.value || !trendChart) return ''

  return trendChart.getDataURL({
    pixelRatio: 2,
    backgroundColor: '#ffffff',
  })
}

function getTaxStatusLabel(status: TaxCalendarItem['status']) {
  if (status === 1) return '已缴纳'
  if (status === 2) return '免征'
  return '待缴纳'
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

watch(
  [loading, hasAnyData, chartMonths],
  async () => {
    await syncTrendChart()
  },
  { deep: true, flush: 'post' },
)

onMounted(() => {
  fetchDashboardData()
})

onBeforeUnmount(() => {
  disconnectTrendObserver()
  disposeTrendChart()
})
</script>

<template>
  <div class="home-view">
    <template v-if="showLoadingSkeleton">
      <div class="hero-card hero-card-skeleton ds-card">
        <div class="skeleton skeleton-pill" />
        <div class="skeleton skeleton-title" />
        <div class="skeleton skeleton-line" />
      </div>

      <div class="kpi-grid">
        <div v-for="item in 4" :key="item" class="kpi-card ds-card">
          <div class="skeleton skeleton-line short" />
          <div class="skeleton skeleton-number" />
          <div class="skeleton skeleton-line" />
        </div>
      </div>

      <div class="content-grid">
        <div class="chart-panel ds-card">
          <div class="skeleton skeleton-line medium" />
          <div class="chart-skeleton" />
        </div>
        <div class="timeline-panel ds-card">
          <div class="skeleton skeleton-line medium" />
          <div class="timeline-skeleton">
            <div v-for="item in 4" :key="item" class="timeline-skeleton-row">
              <span class="timeline-dot-skeleton" />
              <div class="timeline-skeleton-content">
                <div class="skeleton skeleton-line" />
                <div class="skeleton skeleton-line short" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <div v-else-if="errorMessage && !dashboard" class="state-panel ds-card">
      <h2>首页数据暂时不可用</h2>
      <p>{{ errorMessage }}</p>
      <el-button type="primary" :icon="RefreshRight" @click="fetchDashboardData">重新加载</el-button>
    </div>

    <template v-else-if="dashboard">
      <section class="hero-card ds-card">
        <div class="hero-meta">
          <span class="hero-badge">Owner Cockpit</span>
          <span class="hero-period">{{ currentPeriodLabel }}</span>
        </div>
        <div class="hero-content">
          <div>
            <h1>欢迎回来，{{ userStore.realName }}</h1>
            <p>首页聚焦本月经营命脉与待办税务风险，让你一眼看到收入、支出、利润和欠缴情况。</p>
          </div>
          <div class="hero-side-actions">
            <div class="hero-side-note">
              <span class="note-label">统计口径</span>
              <strong>自然月收入 / 支出 / 净利润</strong>
              <span class="note-subtitle">税额提醒覆盖全部未缴情项</span>
            </div>

            <el-button
              type="primary"
              plain
              :icon="Download"
              :loading="exporting"
              :disabled="!canExportHome"
              data-pdf-hide
              @click="handleExportHome"
            >
              导出 PDF 报告
            </el-button>
          </div>
        </div>
      </section>

      <section class="kpi-grid">
        <article
          v-for="card in kpiCards"
          :key="card.key"
          class="kpi-card ds-card"
          :class="[`tone-${card.tone}`]"
        >
          <div class="kpi-card-top">
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

      <div v-if="!hasAnyData" class="state-panel ds-card">
        <h2>驾驶舱已就绪，先完成企业启动动作</h2>
        <p>只要补齐账号和首批流水，首页会自动生成趋势图、利润快照和税务时间轴。</p>

        <div class="startup-task-grid">
          <button
            v-for="task in startupTasks"
            :key="task.key"
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

      <section v-else class="content-grid">
        <article class="chart-panel ds-card">
          <div class="panel-header">
            <div>
              <h2>近 6 个月盈亏趋势</h2>
              <p>收入与支出采用面积序列，净利润使用折线强调拐点变化。</p>
            </div>
          </div>
          <div ref="trendChartRef" class="trend-chart" />
        </article>

        <article class="timeline-panel ds-card">
          <div class="panel-header">
            <div>
              <h2>税务时间轴</h2>
              <p>按税款所属期排序，优先显示最近 8 条需关注或已完成的税务节点。</p>
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
            <p>税务档案录入后，这里会自动汇聚待缴、已缴与免征节点。</p>
          </div>
        </article>
      </section>

      <div v-if="showExportStage" class="pdf-export-stage" aria-hidden="true">
        <section ref="homeExportHeroPageRef" class="pdf-page">
          <div class="pdf-page-header">
            <span class="hero-badge">Owner Cockpit</span>
            <span class="hero-period">{{ exportPeriodLabel }}</span>
          </div>

          <section class="pdf-intro-card ds-card">
            <div class="pdf-intro-main">
              <span class="note-label">经营快照</span>
              <h2>{{ userStore.companyName || '当前企业' }}</h2>
              <p>首页聚焦本月经营命脉与待办税务风险，适合老板在例会场景中快速汇报核心经营面。</p>
            </div>

            <div class="pdf-intro-side">
              <span class="note-label">统计口径</span>
              <strong>自然月收入 / 支出 / 净利润</strong>
              <span class="note-subtitle">税额提醒覆盖全部未缴情项</span>
            </div>
          </section>

          <section class="pdf-kpi-grid">
            <article
              v-for="card in kpiCards"
              :key="`pdf-${card.key}`"
              class="kpi-card ds-card"
              :class="[`tone-${card.tone}`]"
            >
              <div class="kpi-card-top">
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
              <span class="note-label">经营洞察</span>
              <h3 class="pdf-section-title">趋势与税务摘要</h3>
            </div>
            <span class="hero-period">{{ userStore.companyName || '当前企业' }}</span>
          </div>

          <template v-if="hasAnyData">
            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h2>近 6 个月盈亏趋势</h2>
                  <p>收入与支出采用面积序列，净利润使用折线强调经营拐点。</p>
                </div>
              </div>

              <div v-if="homeTrendChartImage" class="pdf-chart-frame">
                <img :src="homeTrendChartImage" alt="近6个月盈亏趋势图" class="pdf-chart-image" />
              </div>
              <div v-else class="panel-empty compact">
                <h3>暂无趋势图</h3>
                <p>当前暂无足够经营数据生成趋势图。</p>
              </div>
            </article>

            <article class="pdf-card ds-card">
              <div class="panel-header">
                <div>
                  <h2>税务时间轴摘要</h2>
                  <p>PDF 仅保留最近 4 条税务节点，避免报告页数过长影响阅读。</p>
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
                <p>税务档案录入后，这里会自动汇聚最近的税务节点摘要。</p>
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
                <p>先录入财务流水或税务档案，系统会自动生成趋势图和税务摘要。</p>
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
  gap: 20px;
}

.hero-card {
  padding: 28px 32px;
  overflow: hidden;
  background:
    radial-gradient(circle at top right, rgba(0, 117, 222, 0.12), transparent 34%),
    linear-gradient(135deg, #ffffff 0%, #f8fbff 48%, #f6f5f4 100%);
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
  max-width: 640px;
  color: #615d59;
  font-size: 15px;
  line-height: 1.7;
}

.hero-side-note {
  width: 100%;
  min-width: 220px;
  padding: 18px 20px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: rgba(0, 0, 0, 0.04) 0 12px 36px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.hero-side-actions {
  min-width: 260px;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 14px;
}

.note-label {
  font-size: 12px;
  font-weight: 600;
  color: #a39e98;
  letter-spacing: 0.125px;
}

.hero-side-note strong {
  font-size: 16px;
  color: rgba(0, 0, 0, 0.92);
}

.note-subtitle {
  font-size: 13px;
  color: #615d59;
  line-height: 1.6;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.kpi-card {
  padding: 20px 22px;
}

.kpi-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.kpi-icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #f6f5f4;
  color: #615d59;
}

.kpi-badge {
  font-size: 12px;
  font-weight: 600;
  color: #615d59;
  background: #f6f5f4;
  border-radius: 9999px;
  padding: 4px 10px;
}

.kpi-label {
  margin-top: 18px;
  font-size: 13px;
  font-weight: 500;
  color: #615d59;
}

.kpi-value {
  margin-top: 10px;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.75px;
  color: rgba(0, 0, 0, 0.95);
  font-variant-numeric: tabular-nums;
}

.kpi-hint {
  margin-top: 8px;
  font-size: 13px;
  color: #a39e98;
}

.tone-income .kpi-icon,
.tone-income .kpi-badge {
  color: #2a9d99;
  background: rgba(42, 157, 153, 0.12);
}

.tone-expense .kpi-icon,
.tone-expense .kpi-badge {
  color: #e03e3e;
  background: rgba(224, 62, 62, 0.12);
}

.tone-warning .kpi-icon,
.tone-warning .kpi-badge {
  color: #dd5b00;
  background: rgba(221, 91, 0, 0.12);
}

.tone-neutral .kpi-icon,
.tone-neutral .kpi-badge {
  color: #615d59;
  background: #f6f5f4;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(320px, 0.9fr);
  gap: 16px;
}

.chart-panel,
.timeline-panel,
.state-panel {
  padding: 24px;
}

.timeline-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-header h2,
.state-panel h2 {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.25px;
  color: rgba(0, 0, 0, 0.95);
}

.panel-header p,
.state-panel p {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.7;
  color: #615d59;
}

.trend-chart {
  margin-top: 18px;
  height: 340px;
  width: 100%;
}

.timeline-scroll {
  margin-top: 20px;
  max-height: 340px;
  min-height: 0;
  overflow-y: auto;
  padding-right: 8px;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.timeline-item {
  display: flex;
  gap: 14px;
}

.timeline-rail {
  position: relative;
  width: 16px;
  display: flex;
  justify-content: center;
}

.timeline-rail::after {
  content: '';
  position: absolute;
  top: 18px;
  bottom: -18px;
  width: 1px;
  background: rgba(0, 0, 0, 0.08);
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
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.08);
}

.timeline-content {
  flex: 1;
  padding: 0 0 18px;
}

.timeline-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.timeline-period {
  font-size: 13px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.9);
}

.timeline-status {
  font-size: 12px;
  font-weight: 600;
  border-radius: 9999px;
  padding: 4px 10px;
}

.timeline-title {
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.92);
}

.timeline-amount {
  margin-top: 8px;
  font-size: 18px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.95);
  font-variant-numeric: tabular-nums;
}

.is-unpaid {
  background: rgba(221, 91, 0, 0.12);
  color: #dd5b00;
}

.timeline-dot.is-unpaid {
  background: #dd5b00;
}

.is-paid {
  background: rgba(163, 158, 152, 0.18);
  color: #615d59;
}

.timeline-dot.is-paid {
  background: #a39e98;
}

.is-exempt {
  background: rgba(26, 174, 57, 0.14);
  color: #1aae39;
}

.timeline-dot.is-exempt {
  background: #1aae39;
}

.panel-empty {
  margin-top: 22px;
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

.panel-empty h3 {
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
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.startup-task-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 8px;
}

.startup-task {
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #ffffff;
  border-radius: 18px;
  padding: 18px;
  text-align: left;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

.startup-task:hover {
  transform: translateY(-1px);
  border-color: rgba(0, 117, 222, 0.24);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.06);
}

.startup-task.is-complete {
  background: linear-gradient(135deg, rgba(42, 157, 153, 0.08), #ffffff 62%);
  border-color: rgba(42, 157, 153, 0.24);
}

.startup-task__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.startup-task__badge {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 9999px;
  background: #f6f5f4;
  color: #615d59;
  font-size: 12px;
  font-weight: 600;
}

.startup-task.is-complete .startup-task__badge {
  background: rgba(42, 157, 153, 0.14);
  color: #2a9d99;
}

.startup-task__cta {
  color: #0075de;
  font-size: 12px;
  font-weight: 700;
}

.startup-task h3 {
  margin-top: 14px;
  font-size: 18px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.94);
}

.startup-task p {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.75;
  color: #615d59;
}

.skeleton {
  position: relative;
  overflow: hidden;
  border-radius: 9999px;
  background: #f1efee;
}

.skeleton::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.92), transparent);
  animation: shimmer 1.5s ease infinite;
}

.hero-card-skeleton {
  padding: 28px 32px;
}

.skeleton-pill {
  width: 110px;
  height: 28px;
}

.skeleton-title {
  margin-top: 22px;
  width: 240px;
  height: 34px;
}

.skeleton-line {
  margin-top: 12px;
  width: 100%;
  height: 14px;
}

.skeleton-line.short {
  width: 48%;
}

.skeleton-line.medium {
  width: 38%;
}

.skeleton-number {
  margin-top: 16px;
  width: 72%;
  height: 34px;
}

.chart-skeleton {
  margin-top: 18px;
  height: 340px;
  border-radius: 20px;
  background: #f6f5f4;
}

.timeline-skeleton {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.timeline-skeleton-row {
  display: flex;
  gap: 14px;
}

.timeline-dot-skeleton {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-top: 6px;
  background: #ece8e6;
}

.timeline-skeleton-content {
  flex: 1;
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

.pdf-intro-card,
.pdf-card,
.pdf-empty-card {
  padding: 28px;
}

.pdf-intro-card {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(240px, 0.75fr);
  gap: 20px;
}

.pdf-intro-main h2 {
  margin-top: 10px;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.875px;
  color: rgba(0, 0, 0, 0.95);
}

.pdf-intro-main p {
  margin-top: 14px;
  color: #615d59;
  font-size: 15px;
  line-height: 1.75;
}

.pdf-intro-side {
  border-radius: 18px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(246, 245, 244, 0.7);
  padding: 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.pdf-intro-side strong {
  font-size: 16px;
  color: rgba(0, 0, 0, 0.92);
}

.pdf-kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
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
  color: rgba(0, 0, 0, 0.92);
}

.pdf-timeline-text span:last-child {
  color: #615d59;
  font-size: 14px;
}

.pdf-timeline-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pdf-footnote {
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px dashed rgba(0, 0, 0, 0.08);
  color: #615d59;
  font-size: 13px;
  line-height: 1.6;
}

.pdf-empty-card {
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
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }

  .startup-task-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .hero-content {
    flex-direction: column;
  }

  .hero-side-actions {
    width: 100%;
    min-width: 0;
    align-items: flex-start;
  }

  .hero-side-note {
    min-width: 0;
  }
}

@media (max-width: 640px) {
  .hero-card,
  .chart-panel,
  .timeline-panel,
  .state-panel {
    padding: 20px;
  }

  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .hero-content h1 {
    font-size: 26px;
  }

  .kpi-value {
    font-size: 26px;
  }

  .trend-chart {
    height: 300px;
  }

  .timeline-scroll {
    max-height: 300px;
  }
}
</style>
