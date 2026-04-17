import request from './request'
import type { AuditModule, AuditOperationType, AuditOperationVO, PageResult, Result } from '@/types'

export function getAuditLogList(params: {
  page: number
  size: number
  module?: AuditModule
  operationType?: AuditOperationType
  startDate?: string
  endDate?: string
}): Promise<Result<PageResult<AuditOperationVO>>> {
  return request.get('/audit/list', { params })
}
