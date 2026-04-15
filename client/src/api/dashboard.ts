import request from './request'
import type {
  FinanceDashboardRange,
  FinanceDashboardVO,
  HomeAiSummaryVO,
  HomeDashboardVO,
  HrDashboardRange,
  HrDashboardVO,
  Result,
  TaxDashboardRange,
  TaxDashboardVO,
} from '@/types'

export function getHomeDashboard(): Promise<Result<HomeDashboardVO>> {
  return request.get('/dashboard/home')
}

interface HomeAiSummaryRequestOptions {
  signal?: AbortSignal
}

export function getHomeAiSummary(options?: HomeAiSummaryRequestOptions): Promise<Result<HomeAiSummaryVO>> {
  return request.get('/dashboard/home-ai-summary', {
    timeout: 75000,
    signal: options?.signal,
    silentError: true,
  })
}

export function getFinanceDashboard(range: FinanceDashboardRange): Promise<Result<FinanceDashboardVO>> {
  return request.get('/dashboard/finance', { params: { range } })
}

export function getHrDashboard(range: HrDashboardRange): Promise<Result<HrDashboardVO>> {
  return request.get('/dashboard/hr', { params: { range } })
}

export function getTaxDashboard(range?: TaxDashboardRange): Promise<Result<TaxDashboardVO>> {
  return request.get('/dashboard/tax', { params: range ? { range } : {} })
}
