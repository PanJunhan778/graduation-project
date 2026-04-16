export type PdfOrientation = 'portrait' | 'landscape'

export interface PdfExportSection {
  element: HTMLElement
  pageBreakBefore?: boolean
  fitToPage?: boolean
  backgroundColor?: string
}

interface ExportPdfOptions {
  sections: PdfExportSection[]
  fileNameParts: Array<string | number | null | undefined>
  loadingText?: string
  orientation?: PdfOrientation
  backgroundColor?: string
  hideSelectors?: string[]
  expandSelectors?: string[]
}

const TEMP_ATTRIBUTE = 'data-pdf-export-id'
const DEFAULT_HIDE_SELECTORS = ['[data-pdf-hide]']
const DEFAULT_EXPAND_SELECTORS = ['[data-pdf-expand]']
const PDF_MARGIN_MM = 8

export async function exportSectionsToPdf(options: ExportPdfOptions): Promise<string> {
  const sections = options.sections.filter((section) => Boolean(section.element))

  if (!sections.length) {
    throw new Error('未找到可导出的页面内容')
  }

  const exportId = `pdf-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const loading = ElLoading.service({
    lock: true,
    text: options.loadingText || '正在生成 PDF 报告...',
    background: 'rgba(246, 245, 244, 0.82)',
  })

  sections.forEach((section, index) => {
    section.element.setAttribute(TEMP_ATTRIBUTE, `${exportId}-${index}`)
  })

  try {
    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([import('html2canvas'), import('jspdf')])
    await waitForUiStable()

    const pdf = new jsPDF({
      orientation: options.orientation || 'portrait',
      unit: 'mm',
      format: 'a4',
      compress: true,
    })

    const pageWidth = pdf.internal.pageSize.getWidth()
    const pageHeight = pdf.internal.pageSize.getHeight()
    const contentWidth = pageWidth - PDF_MARGIN_MM * 2
    const contentHeight = pageHeight - PDF_MARGIN_MM * 2

    pdf.setProperties({
      title: buildPdfFileName(options.fileNameParts),
      creator: 'Intelligent Lightweight EMS',
    })

    let hasRendered = false
    let currentY = PDF_MARGIN_MM

    for (let index = 0; index < sections.length; index += 1) {
      const section = sections[index]
      const canvas = await html2canvas(section.element, {
        backgroundColor: section.backgroundColor || options.backgroundColor || '#f5f5f4',
        scale: Math.min(Math.max(window.devicePixelRatio || 1, 1), 2),
        useCORS: true,
        logging: false,
        scrollX: 0,
        scrollY: -window.scrollY,
        onclone: (clonedDocument) => {
          const clonedSection = clonedDocument.querySelector<HTMLElement>(
            `[${TEMP_ATTRIBUTE}="${exportId}-${index}"]`,
          )
          if (!clonedSection) return

          clonedSection.setAttribute('data-pdf-exporting', 'true')
          applyCloneTweaks(clonedDocument.body as HTMLElement, clonedSection, options)
        },
      })

      const imageData = canvas.toDataURL('image/png', 1)
      const naturalWidth = contentWidth
      const naturalHeight = (canvas.height * naturalWidth) / canvas.width
      const scale = section.fitToPage === false ? 1 : Math.min(1, contentHeight / naturalHeight)
      const renderWidth = naturalWidth * scale
      const renderHeight = naturalHeight * scale
      const renderX = PDF_MARGIN_MM + Math.max(0, (contentWidth - renderWidth) / 2)

      const shouldStartNewPage =
        (hasRendered && section.pageBreakBefore !== false) ||
        currentY + renderHeight > pageHeight - PDF_MARGIN_MM

      if (shouldStartNewPage) {
        pdf.addPage()
        currentY = PDF_MARGIN_MM
      }

      pdf.addImage(imageData, 'PNG', renderX, currentY, renderWidth, renderHeight, undefined, 'FAST')
      currentY += renderHeight
      hasRendered = true
    }

    const fileName = buildPdfFileName(options.fileNameParts)
    pdf.save(fileName)
    return fileName
  } finally {
    loading.close()
    sections.forEach((section) => {
      section.element.removeAttribute(TEMP_ATTRIBUTE)
    })
  }
}

export function formatPdfTimestamp(date = new Date()): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}${month}${day}-${hour}${minute}${second}`
}

function buildPdfFileName(parts: Array<string | number | null | undefined>): string {
  const normalized = parts
    .map((part) => sanitizeFileNamePart(part))
    .filter((part): part is string => Boolean(part))

  return `${normalized.join('-') || 'report'}.pdf`
}

function sanitizeFileNamePart(part: string | number | null | undefined): string {
  return String(part ?? '')
    .trim()
    .replace(/[\\/:*?"<>|]+/g, '-')
    .replace(/\s+/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
}

function applyCloneTweaks(
  documentRoot: HTMLElement,
  sectionRoot: HTMLElement,
  options: Pick<ExportPdfOptions, 'hideSelectors' | 'expandSelectors'>,
) {
  documentRoot.style.height = 'auto'
  documentRoot.style.maxHeight = 'none'
  documentRoot.style.overflow = 'visible'

  sectionRoot.style.height = 'auto'
  sectionRoot.style.maxHeight = 'none'
  sectionRoot.style.overflow = 'visible'

  const hideSelectors = [...DEFAULT_HIDE_SELECTORS, ...(options.hideSelectors || [])]
  const expandSelectors = [...DEFAULT_EXPAND_SELECTORS, ...(options.expandSelectors || [])]

  hideSelectors.forEach((selector) => {
    documentRoot.querySelectorAll<HTMLElement>(selector).forEach((node) => {
      node.style.display = 'none'
    })
  })

  expandSelectors.forEach((selector) => {
    sectionRoot.querySelectorAll<HTMLElement>(selector).forEach((node) => {
      node.style.height = 'auto'
      node.style.maxHeight = 'none'
      node.style.overflow = 'visible'
      node.style.paddingRight = '0'
    })
  })
}

async function waitForUiStable(): Promise<void> {
  await waitForAnimationFrames(2)
  await sleep(120)
  await waitForAnimationFrames(1)
}

function waitForAnimationFrames(count: number): Promise<void> {
  return new Promise((resolve) => {
    const step = (remaining: number) => {
      if (remaining <= 0) {
        resolve()
        return
      }

      window.requestAnimationFrame(() => step(remaining - 1))
    }

    step(count)
  })
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}
