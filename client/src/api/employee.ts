import request from './request'
import type {
  EmployeeForm,
  EmployeeRecordVO,
  EmployeeRecycleBinVO,
  ImportError,
  PageResult,
  Result,
} from '@/types'

type TemplateDownloadError = {
  code?: number
  message: string
  data?: unknown
}

const EXCEL_MIME_TYPES = [
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-excel',
]

export function getEmployeeList(params: {
  page: number
  size: number
  department?: string
  status?: number
}): Promise<Result<PageResult<EmployeeRecordVO>>> {
  return request.get('/employee/list', { params })
}

export function createEmployee(data: EmployeeForm): Promise<Result<null>> {
  return request.post('/employee', data)
}

export function updateEmployee(id: number, data: EmployeeForm): Promise<Result<null>> {
  return request.put(`/employee/${id}`, data)
}

export function deleteEmployee(id: number): Promise<Result<null>> {
  return request.delete(`/employee/${id}`)
}

export function batchDeleteEmployee(ids: number[]): Promise<Result<null>> {
  return request.post('/employee/batch-delete', { ids })
}

export function getEmployeeRecycleBinList(params: {
  page: number
  size: number
}): Promise<Result<PageResult<EmployeeRecycleBinVO>>> {
  return request.get('/employee/recycle-bin/list', { params })
}

export function restoreEmployee(id: number): Promise<Result<null>> {
  return request.post(`/employee/recycle-bin/${id}/restore`)
}

export function batchRestoreEmployee(ids: number[]): Promise<Result<number>> {
  return request.post('/employee/recycle-bin/batch-restore', { ids })
}

export function importEmployeeExcel(file: File): Promise<Result<ImportError[] | null>> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/employee/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

export async function downloadEmployeeTemplate(): Promise<void> {
  const response = await request.get('/employee/template', { responseType: 'blob' })
  const blob: Blob = response.data
  const contentType = String(response.headers['content-type'] || blob.type || '').toLowerCase()
  if (!isExcelBlob(contentType)) {
    throw await parseTemplateDownloadError(blob)
  }
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '员工导入模板.xlsx'
  link.click()
  window.URL.revokeObjectURL(url)
}

function isExcelBlob(contentType: string): boolean {
  return EXCEL_MIME_TYPES.some((mimeType) => contentType.includes(mimeType))
}

async function parseTemplateDownloadError(blob: Blob): Promise<TemplateDownloadError> {
  const fallbackMessage = '模板下载失败'

  try {
    const text = (await blob.text()).trim()
    if (!text) {
      return { message: fallbackMessage }
    }

    try {
      const parsed = JSON.parse(text) as TemplateDownloadError
      return {
        code: parsed.code,
        message: parsed.message || fallbackMessage,
        data: parsed.data,
      }
    } catch {
      return { message: text }
    }
  } catch {
    return { message: fallbackMessage }
  }
}
