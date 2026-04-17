import request from '@/api/request'
import router from '@/router'
import type {
  AiChatMessageVO,
  AiChatRequest,
  AiChatStreamActionRequiredEvent,
  AiChatStreamDoneEvent,
  AiChatStreamErrorEvent,
  AiChatStreamStartEvent,
  AiChatStreamTokenEvent,
  AiChatTurnVO,
  AiConfirmActionRequest,
  AiConfirmActionVO,
  AiSessionVO,
} from '@/types'
import { clearAuth, getToken } from '@/utils/auth'

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

interface AiChatStreamHandlers {
  onStart?: (payload: AiChatStreamStartEvent) => void
  onToken?: (payload: AiChatStreamTokenEvent) => void
  onActionRequired?: (payload: AiChatStreamActionRequiredEvent) => void
  onError?: (payload: AiChatStreamErrorEvent) => void
  onDone?: (payload: AiChatStreamDoneEvent) => void
}

const SSE_DELIMITER = '\n\n'

export function listAiSessions() {
  return request.get<AiSessionVO[]>('/ai/sessions')
}

export function listAiMessages(sessionId: string) {
  return request.get<AiChatMessageVO[]>(`/ai/sessions/${sessionId}/messages`)
}

export function confirmAiAction(payload: AiConfirmActionRequest) {
  return request.post<AiConfirmActionVO>('/ai/confirm-action', payload)
}

export function deleteAiSession(sessionId: string) {
  return request.delete<null>(`/ai/sessions/${sessionId}`)
}

export function chatAi(payload: AiChatRequest, signal?: AbortSignal) {
  return request.post<AiChatTurnVO>('/ai/chat', payload, {
    signal,
    timeout: 120000,
  })
}

export async function streamAiChat(
  payload: AiChatRequest,
  handlers: AiChatStreamHandlers,
  signal?: AbortSignal,
) {
  const token = getToken()
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal,
  })

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('text/event-stream')) {
    await throwNonStreamError(response)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('AI 流式响应不可用')
  }

  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let streamCompleted = false

  while (!streamCompleted) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })

    let delimiterIndex = buffer.indexOf(SSE_DELIMITER)
    while (delimiterIndex >= 0) {
      const chunk = buffer.slice(0, delimiterIndex)
      buffer = buffer.slice(delimiterIndex + SSE_DELIMITER.length)
      streamCompleted = dispatchSseChunk(chunk, handlers) || streamCompleted
      delimiterIndex = buffer.indexOf(SSE_DELIMITER)
    }

    if (done) {
      if (buffer.trim()) {
        dispatchSseChunk(buffer, handlers)
      }
      break
    }
  }
}

async function throwNonStreamError(response: Response): Promise<never> {
  const raw = await response.text()
  const fallbackMessage = response.statusText || 'AI 服务暂时不可用'

  try {
    const result = JSON.parse(raw) as ApiResult<unknown>
    if (typeof result?.code === 'number') {
      if (result.code === 401) {
        clearAuth()
        void router.push('/login')
      }
      throw new Error(result.message || fallbackMessage)
    }
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }
  }

  if (response.status === 401) {
    clearAuth()
    void router.push('/login')
  }
  throw new Error(fallbackMessage)
}

function dispatchSseChunk(chunk: string, handlers: AiChatStreamHandlers) {
  const trimmed = chunk.trim()
  if (!trimmed) {
    return false
  }

  const lines = trimmed.split(/\r?\n/)
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice('event:'.length).trim()
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim())
    }
  }

  const rawData = dataLines.join('\n')
  const payload = rawData ? JSON.parse(rawData) : null

  switch (eventName) {
    case 'start':
      handlers.onStart?.(payload as AiChatStreamStartEvent)
      return false
    case 'token':
      handlers.onToken?.(payload as AiChatStreamTokenEvent)
      return false
    case 'action_required':
      handlers.onActionRequired?.(payload as AiChatStreamActionRequiredEvent)
      return false
    case 'error':
      handlers.onError?.(payload as AiChatStreamErrorEvent)
      return false
    case 'done':
      handlers.onDone?.(payload as AiChatStreamDoneEvent)
      return true
    default:
      return false
  }
}
