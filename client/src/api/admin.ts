import request from './request'
import type { Result, PageResult, CompanyVO, CompanyCreateForm, OwnerCreateForm } from '@/types'

export function getCompanyList(params: {
  page: number
  size: number
  keyword?: string
}): Promise<Result<PageResult<CompanyVO>>> {
  return request.get('/admin/company/list', { params })
}

export function createCompany(data: CompanyCreateForm): Promise<Result<null>> {
  return request.post('/admin/company', data)
}

export function createOwner(companyId: number, data: OwnerCreateForm): Promise<Result<null>> {
  return request.post(`/admin/company/${companyId}/owner`, data)
}

export function updateCompanyStatus(id: number, status: number): Promise<Result<null>> {
  return request.put(`/admin/company/${id}/status`, { status })
}
