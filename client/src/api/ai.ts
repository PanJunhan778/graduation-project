import request from '@/api/request'
import { getToken } from '@/utils/auth'
import type {
  AiActionRequiredPayload,
  AiChatMessageVO,
  AiChatRequest,
  AiConfirmActionRequest,
  AiConfirmActionVO,
  AiDoneEventPayload,
  AiSessionEventPayload,
  AiSessionVO,
  AiTokenEventPayload,
} from '@/types'

export function listAiSessions() {
  return request.get<AiSessionVO[]>('/ai/sessions')
}

export function listAiMessages(sessionId: string) {
  return request.get<AiChatMessageVO[]>(`/ai/sessions/${sessionId}/messages`)
}

export function confirmAiAction(payload: AiConfirmActionRequest) {
  return request.post<AiConfirmActionVO>('/ai/confirm-action', payload)
}

export interface AiStreamCallbacks {
  onSession?: (payload: AiSessionEventPayload) => void
  onToken?: (payload: AiTokenEventPayload) => void
  onDone?: (payload: AiDoneEventPayload) => void
  onActionRequired?: (payload: AiActionRequiredPayload) => void
}

export async function streamAiChat(
  payload: AiChatRequest,
  callbacks: AiStreamCallbacks,
  signal?: AbortSignal,
) {
  const token = getToken()
  const response = await fetch('/api/ai/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
    signal,
  })

  if (!response.ok) {
    throw new Error(`AI 请求失败（HTTP ${response.status}）`)
  }

  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('text/event-stream')) {
    const fallback = await response.json().catch(() => null)
    throw new Error(fallback?.message || 'AI 服务返回了无效响应')
  }

  if (!response.body) {
    throw new Error('AI 流式响应不可用')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    buffer = consumeEventBuffer(buffer, callbacks)
  }

  if (buffer.trim()) {
    consumeEventBuffer(`${buffer}\n\n`, callbacks)
  }
}

function consumeEventBuffer(buffer: string, callbacks: AiStreamCallbacks) {
  let rest = buffer

  while (true) {
    const separatorIndex = rest.indexOf('\n\n')
    if (separatorIndex === -1) {
      return rest
    }

    const block = rest.slice(0, separatorIndex).trim()
    rest = rest.slice(separatorIndex + 2)
    if (!block) {
      continue
    }

    let eventName = 'message'
    const dataLines: string[] = []

    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim())
      }
    }

    const rawData = dataLines.join('\n')
    const payload = rawData ? JSON.parse(rawData) : {}
    dispatchEvent(eventName, payload, callbacks)
  }
}

function dispatchEvent(eventName: string, payload: unknown, callbacks: AiStreamCallbacks) {
  switch (eventName) {
    case 'session':
      callbacks.onSession?.(payload as AiSessionEventPayload)
      break
    case 'token':
      callbacks.onToken?.(payload as AiTokenEventPayload)
      break
    case 'done':
      callbacks.onDone?.(payload as AiDoneEventPayload)
      break
    case 'action_required':
      callbacks.onActionRequired?.(payload as AiActionRequiredPayload)
      break
    case 'error':
      throw new Error((payload as { message?: string })?.message || 'AI 服务暂时不可用')
    default:
      break
  }
}
