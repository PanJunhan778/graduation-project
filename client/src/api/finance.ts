import request from './request'
import type { Result, PageResult, FinanceRecordVO, FinanceForm, ImportError } from '@/types'

type TemplateDownloadError = {
  code?: number
  message: string
  data?: unknown
}

const EXCEL_MIME_TYPES = [
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-excel',
]

export function getFinanceList(params: {
  page: number
  size: number
  type?: string
  category?: string
  startDate?: string
  endDate?: string
}): Promise<Result<PageResult<FinanceRecordVO>>> {
  return request.get('/finance/list', { params })
}

export function createFinance(data: FinanceForm): Promise<Result<null>> {
  return request.post('/finance', data)
}

export function updateFinance(id: number, data: FinanceForm): Promise<Result<null>> {
  return request.put(`/finance/${id}`, data)
}

export function deleteFinance(id: number): Promise<Result<null>> {
  return request.delete(`/finance/${id}`)
}

export function batchDeleteFinance(ids: number[]): Promise<Result<null>> {
  return request.post('/finance/batch-delete', { ids })
}

export function importFinanceExcel(
  file: File,
): Promise<Result<ImportError[] | null>> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/finance/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

export async function downloadFinanceTemplate(): Promise<void> {
  const response = await request.get('/finance/template', { responseType: 'blob' })
  const blob: Blob = response.data
  const contentType = String(response.headers['content-type'] || blob.type || '').toLowerCase()

  if (!isExcelBlob(contentType)) {
    throw await parseTemplateDownloadError(blob)
  }

  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '财务导入模板.xlsx'
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
