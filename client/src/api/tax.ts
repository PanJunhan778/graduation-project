import request from './request'
import type { ImportError, PageResult, Result, TaxForm, TaxRecordVO } from '@/types'

type TemplateDownloadError = {
  code?: number
  message: string
  data?: unknown
}

const EXCEL_MIME_TYPES = [
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-excel',
]

export function getTaxList(params: {
  page: number
  size: number
  taxType?: string
  paymentStatus?: number
  taxPeriod?: string
}): Promise<Result<PageResult<TaxRecordVO>>> {
  return request.get('/tax/list', { params })
}

export function createTax(data: TaxForm): Promise<Result<null>> {
  return request.post('/tax', data)
}

export function updateTax(id: number, data: TaxForm): Promise<Result<null>> {
  return request.put(`/tax/${id}`, data)
}

export function deleteTax(id: number): Promise<Result<null>> {
  return request.delete(`/tax/${id}`)
}

export function batchDeleteTax(ids: number[]): Promise<Result<null>> {
  return request.post('/tax/batch-delete', { ids })
}

export function importTaxExcel(file: File): Promise<Result<ImportError[] | null>> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/tax/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

export async function downloadTaxTemplate(): Promise<void> {
  const response = await request.get('/tax/template', { responseType: 'blob' })
  const blob: Blob = response.data
  const contentType = String(response.headers['content-type'] || blob.type || '').toLowerCase()

  if (!isExcelBlob(contentType)) {
    throw await parseTemplateDownloadError(blob)
  }

  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = '税务导入模板.xlsx'
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