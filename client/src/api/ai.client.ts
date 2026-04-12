import request from '@/api/request'
import type {
  AiChatMessageVO,
  AiChatRequest,
  AiChatTurnVO,
  AiConfirmActionRequest,
  AiConfirmActionVO,
  AiSessionVO,
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

export function deleteAiSession(sessionId: string) {
  return request.delete<null>(`/ai/sessions/${sessionId}`)
}

export function chatAi(payload: AiChatRequest, signal?: AbortSignal) {
  return request.post<AiChatTurnVO>('/ai/chat', payload, {
    signal,
    timeout: 120000,
  })
}
