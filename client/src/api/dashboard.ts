import request from './request'
import type {
  FinanceDashboardRange,
  FinanceDashboardVO,
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

export function getFinanceDashboard(range: FinanceDashboardRange): Promise<Result<FinanceDashboardVO>> {
  return request.get('/dashboard/finance', { params: { range } })
}

export function getHrDashboard(range: HrDashboardRange): Promise<Result<HrDashboardVO>> {
  return request.get('/dashboard/hr', { params: { range } })
}

export function getTaxDashboard(range: TaxDashboardRange): Promise<Result<TaxDashboardVO>> {
  return request.get('/dashboard/tax', { params: { range } })
}
