import request from './request'
import type { AuditLogVO, AuditModule, AuditOperationType, PageResult, Result } from '@/types'

export function getAuditLogList(params: {
  page: number
  size: number
  module?: AuditModule
  operationType?: AuditOperationType
  startDate?: string
  endDate?: string
}): Promise<Result<PageResult<AuditLogVO>>> {
  return request.get('/audit/list', { params })
}
