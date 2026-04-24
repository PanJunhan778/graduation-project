<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import AiWorkbenchSkeleton from '@/components/common/AiWorkbenchSkeleton.vue'
import { useDelayedLoading } from '@/composables/useDelayedLoading'
import {
  chatAi,
  confirmAiAction,
  deleteAiSession,
  listAiMessages,
  listAiSessions,
  streamAiChat,
} from '@/api/ai'
import type {
  AiActionMetadata,
  AiChatMessageVO,
  AiChatStreamDoneEvent,
  AiChatStreamErrorEvent,
  AiSessionVO,
} from '@/types'
import {
  Delete,
  Expand,
  Fold,
  Plus,
  Promotion,
  RefreshRight,
} from '@element-plus/icons-vue'

const COMPACT_HISTORY_MEDIA = '(max-width: 1100px)'

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const promptChips = [
  '分析本月各项支出占比',
  '查询当前待缴税金',
  '看一下各部门薪资结构',
  '总结最近的关键经营风险',
]

const sessions = ref<AiSessionVO[]>([])
const messages = ref<AiChatMessageVO[]>([])
const activeSessionId = ref('')
const inputMessage = ref('')
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const isThinking = ref(false)
const activeChatRequestId = ref(0)
const requestAbortController = ref<AbortController | null>(null)
const confirmingActionId = ref<number | null>(null)
const deletingSessionId = ref('')
const isHistoryCollapsed = ref(false)
const isCompactScreen = ref(
  typeof window !== 'undefined' ? window.matchMedia(COMPACT_HISTORY_MEDIA).matches : false,
)
const isCompactHistoryOpen = ref(false)
const messageStreamRef = ref<HTMLElement | null>(null)
const hasInitialized = ref(false)

let compactHistoryMediaQuery: MediaQueryList | null = null

const activeSession = computed(
  () => sessions.value.find((item) => item.sessionId === activeSessionId.value) || null,
)
const pageTitle = computed(() => activeSession.value?.title || '开始新对话')
const isEmptyConversation = computed(() => messages.value.length === 0)
const toolbarMeta = computed(() => (activeSessionId.value ? '当前会话' : '新对话'))
const toolbarStatus = computed(() => {
  if (isThinking.value) {
    return '正在思考...'
  }
  return activeSessionId.value ? '会话已连接' : '等待你的第一条消息'
})
const showInitialSkeleton = useDelayedLoading(
  () => !hasInitialized.value && (loadingSessions.value || loadingMessages.value),
)
const showHistoryDetails = computed(() => isCompactScreen.value || !isHistoryCollapsed.value)
const showToolbarHistoryToggle = computed(() => isCompactScreen.value || isHistoryCollapsed.value)
const historyToggleTitle = computed(() => {
  if (isCompactScreen.value) {
    return isCompactHistoryOpen.value ? '关闭历史' : '展开历史'
  }
  return isHistoryCollapsed.value ? '展开历史' : '收起历史'
})

watch(
  messages,
  async () => {
    await nextTick()
    scrollToBottom()
  },
  { deep: true },
)

watch(isThinking, async (value) => {
  if (!value) return
  await nextTick()
  scrollToBottom()
})

onMounted(async () => {
  setupCompactHistoryMedia()
  await initializePage()
})

onBeforeUnmount(() => {
  cancelActiveRequest()
  tearDownCompactHistoryMedia()
})

async function initializePage() {
  loadingSessions.value = true
  try {
    await refreshSessions(undefined, { reloadMessages: true })
  } finally {
    loadingSessions.value = false
    hasInitialized.value = true
  }
}

async function refreshSessions(
  preferredSessionId?: string,
  options: { reloadMessages?: boolean; keepBlankState?: boolean } = {},
) {
  const res = await listAiSessions()
  sessions.value = res.data

  const preferredExists =
    Boolean(preferredSessionId) &&
    sessions.value.some((item) => item.sessionId === preferredSessionId)
  const currentExists =
    Boolean(activeSessionId.value) &&
    sessions.value.some((item) => item.sessionId === activeSessionId.value)
  const shouldStayBlank =
    Boolean(options.keepBlankState) && !preferredExists && !currentExists

  const nextSessionId = shouldStayBlank
    ? ''
    : preferredExists
      ? preferredSessionId!
      : currentExists
        ? activeSessionId.value
        : sessions.value[0]?.sessionId || ''

  if (!nextSessionId) {
    activeSessionId.value = ''
    messages.value = []
    return
  }

  const sessionChanged = nextSessionId !== activeSessionId.value
  activeSessionId.value = nextSessionId

  if (options.reloadMessages || sessionChanged || messages.value.length === 0) {
    await loadMessages(nextSessionId)
  }
}

async function handleRefreshSessions() {
  loadingSessions.value = true
  try {
    await refreshSessions(activeSessionId.value || undefined, {
      reloadMessages: Boolean(activeSessionId.value),
      keepBlankState: !activeSessionId.value,
    })
    ElMessage.success('会话列表已刷新')
  } finally {
    loadingSessions.value = false
  }
}

async function loadMessages(sessionId: string) {
  if (!sessionId) {
    messages.value = []
    return
  }

  loadingMessages.value = true
  try {
    const res = await listAiMessages(sessionId)
    messages.value = res.data
  } finally {
    loadingMessages.value = false
  }
}

async function selectSession(sessionId: string) {
  if (sessionId === activeSessionId.value && messages.value.length > 0) {
    closeCompactHistory()
    return
  }

  cancelActiveRequest()
  activeSessionId.value = sessionId
  await loadMessages(sessionId)
  closeCompactHistory()
}

function startNewConversation() {
  cancelActiveRequest()
  activeSessionId.value = ''
  messages.value = []
  inputMessage.value = ''
  closeCompactHistory()
}

async function handleSendMessage() {
  const text = inputMessage.value.trim()
  if (!text || isThinking.value) {
    return
  }

  const userMessage: AiChatMessageVO = {
    id: -Date.now(),
    role: 'user',
    messageType: 'text',
    content: text,
    metadata: null,
    createTime: new Date().toISOString(),
  }

  const requestId = activeChatRequestId.value + 1
  activeChatRequestId.value = requestId
  let resolvedSessionId = activeSessionId.value

  messages.value.push(userMessage)
  inputMessage.value = ''
  isThinking.value = true

  const abortController = new AbortController()
  requestAbortController.value = abortController

  try {
    const res = await chatAi(
      {
        sessionId: activeSessionId.value || undefined,
        message: text,
      },
      abortController.signal,
    )

    if (requestId !== activeChatRequestId.value) {
      return
    }

    const turn = res.data
    resolvedSessionId = turn.sessionId
    if (!activeSessionId.value) {
      activeSessionId.value = turn.sessionId
    }

    if (turn.resultType === 'action_required' && turn.actionRequired) {
      await loadMessages(turn.sessionId)
      await refreshSessions(turn.sessionId, { reloadMessages: false })
      return
    }

    messages.value.push({
      id: turn.messageId || -(Date.now() + Math.floor(Math.random() * 1000)),
      role: 'assistant',
      messageType: turn.messageType || 'markdown',
      content: turn.content || '',
      metadata: null,
      createTime: new Date().toISOString(),
    })
    await refreshSessions(resolvedSessionId, { reloadMessages: false })
  } catch (error) {
    if (requestId !== activeChatRequestId.value) {
      return
    }

    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      ElMessage.error(error instanceof Error ? error.message : 'AI 服务暂时不可用')
    }
  } finally {
    if (requestId === activeChatRequestId.value) {
      isThinking.value = false
      requestAbortController.value = null
    }
  }
}

void handleSendMessage

async function handleSendMessageStream() {
  const text = inputMessage.value.trim()
  if (!text || isThinking.value) {
    return
  }

  const userMessage: AiChatMessageVO = {
    id: -Date.now(),
    role: 'user',
    messageType: 'text',
    content: text,
    metadata: null,
    createTime: new Date().toISOString(),
  }

  const requestId = activeChatRequestId.value + 1
  activeChatRequestId.value = requestId
  let resolvedSessionId = activeSessionId.value
  const assistantPlaceholderId = -(Date.now() + Math.floor(Math.random() * 1000) + 1)
  let doneEvent: AiChatStreamDoneEvent | null = null
  let actionRequiredSessionId = ''
  let streamError: AiChatStreamErrorEvent | null = null

  messages.value.push(userMessage)
  messages.value.push({
    id: assistantPlaceholderId,
    role: 'assistant',
    messageType: 'markdown',
    content: '',
    metadata: null,
    createTime: new Date().toISOString(),
  })
  inputMessage.value = ''
  isThinking.value = true

  const abortController = new AbortController()
  requestAbortController.value = abortController

  try {
    await streamAiChat(
      {
        sessionId: activeSessionId.value || undefined,
        message: text,
      },
      {
        onStart: (payload) => {
          if (requestId !== activeChatRequestId.value) return
          resolvedSessionId = payload.sessionId
          if (!activeSessionId.value) {
            activeSessionId.value = payload.sessionId
          }
        },
        onToken: (payload) => {
          if (requestId !== activeChatRequestId.value) return
          appendAssistantDelta(assistantPlaceholderId, payload.delta)
        },
        onActionRequired: (payload) => {
          if (requestId !== activeChatRequestId.value) return
          actionRequiredSessionId = payload.sessionId
          resolvedSessionId = payload.sessionId
          removeMessageById(assistantPlaceholderId)
        },
        onError: (payload) => {
          if (requestId !== activeChatRequestId.value) return
          streamError = payload
          markAssistantMessageInterrupted(assistantPlaceholderId)
        },
        onDone: (payload) => {
          if (requestId !== activeChatRequestId.value) return
          doneEvent = payload
          if (payload.sessionId) {
            resolvedSessionId = payload.sessionId
          }
        },
      },
      abortController.signal,
    )

    if (requestId !== activeChatRequestId.value) {
      return
    }

    const completedEvent = doneEvent as AiChatStreamDoneEvent | null
    const latestStreamError = streamError as AiChatStreamErrorEvent | null

    if (completedEvent && completedEvent.reason === 'message') {
      finalizeAssistantMessage(
        assistantPlaceholderId,
        completedEvent.messageId ?? assistantPlaceholderId,
        completedEvent.messageType || 'markdown',
      )
      await refreshSessions(resolvedSessionId || undefined, { reloadMessages: false })
      return
    }

    if (completedEvent && completedEvent.reason === 'action_required') {
      const targetSessionId = actionRequiredSessionId || resolvedSessionId
      if (targetSessionId) {
        await loadMessages(targetSessionId)
        await refreshSessions(targetSessionId, { reloadMessages: false })
      }
      return
    }

    if (completedEvent && completedEvent.reason === 'error') {
      await refreshSessions(resolvedSessionId || undefined, {
        reloadMessages: false,
        keepBlankState: !resolvedSessionId,
      })
      if (latestStreamError) {
        ElMessage.error(latestStreamError.message)
      }
    }
  } catch (error) {
    if (requestId !== activeChatRequestId.value) {
      return
    }

    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      markAssistantMessageInterrupted(assistantPlaceholderId)
      ElMessage.error(error instanceof Error ? error.message : 'AI 服务暂时不可用')
    }
  } finally {
    if (requestId === activeChatRequestId.value) {
      isThinking.value = false
      requestAbortController.value = null
    }
  }
}

async function handleConfirmAction(message: AiChatMessageVO, isApproved: boolean) {
  const metadata = getActionMetadata(message)
  if (!metadata?.confirmToken || confirmingActionId.value === metadata.actionId) {
    return
  }

  confirmingActionId.value = metadata.actionId
  try {
    const res = await confirmAiAction({
      confirmToken: metadata.confirmToken,
      isApproved,
    })
    ElMessage.success(res.data.resultMessage)
    if (activeSessionId.value) {
      await loadMessages(activeSessionId.value)
      await refreshSessions(activeSessionId.value)
    }
  } finally {
    confirmingActionId.value = null
  }
}

async function handleDeleteSession(session: AiSessionVO) {
  if (!session.sessionId || deletingSessionId.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      `删除“${session.title}”后，这段对话历史将从列表中移除。`,
      '删除对话',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  const isDeletingCurrent = session.sessionId === activeSessionId.value
  const shouldKeepBlank = isDeletingCurrent || !activeSessionId.value

  if (isDeletingCurrent) {
    cancelActiveRequest()
  }

  deletingSessionId.value = session.sessionId
  loadingSessions.value = true
  try {
    await deleteAiSession(session.sessionId)

    if (isDeletingCurrent) {
      activeSessionId.value = ''
      messages.value = []
      inputMessage.value = ''
    }

    await refreshSessions(isDeletingCurrent ? undefined : activeSessionId.value || undefined, {
      keepBlankState: shouldKeepBlank,
      reloadMessages: false,
    })
    ElMessage.success('对话已删除')
  } finally {
    deletingSessionId.value = ''
    loadingSessions.value = false
  }
}

function cancelActiveRequest() {
  activeChatRequestId.value += 1
  requestAbortController.value?.abort()
  requestAbortController.value = null
  isThinking.value = false
}

function appendAssistantDelta(messageId: number, delta: string) {
  const message = findMessageById(messageId)
  if (!message || !delta) {
    return
  }
  message.content += delta
}

function finalizeAssistantMessage(
  messageId: number,
  nextId: number,
  messageType: AiChatMessageVO['messageType'],
) {
  const message = findMessageById(messageId)
  if (!message) {
    return
  }
  message.id = nextId
  message.messageType = messageType
}

function markAssistantMessageInterrupted(messageId: number) {
  const message = findMessageById(messageId)
  if (!message) {
    return
  }

  if (!message.content.trim()) {
    removeMessageById(messageId)
    return
  }

  if (!message.content.includes('生成中断，未保存')) {
    message.content = `${message.content}\n\n> 生成中断，未保存`
  }
}

function removeMessageById(messageId: number) {
  messages.value = messages.value.filter((message) => message.id !== messageId)
}

function findMessageById(messageId: number) {
  return messages.value.find((message) => message.id === messageId) || null
}

function toggleHistoryRail() {
  if (isCompactScreen.value) {
    isCompactHistoryOpen.value = !isCompactHistoryOpen.value
    return
  }
  isHistoryCollapsed.value = !isHistoryCollapsed.value
}

function closeCompactHistory() {
  if (isCompactScreen.value) {
    isCompactHistoryOpen.value = false
  }
}

function handleChipClick(chip: string) {
  inputMessage.value = chip
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void handleSendMessageStream()
  }
}

function getActionMetadata(message: AiChatMessageVO) {
  return (message.metadata || null) as AiActionMetadata | null
}

function isActionPending(message: AiChatMessageVO) {
  return getActionMetadata(message)?.status === 'pending'
}

function isActionProcessing(message: AiChatMessageVO) {
  return confirmingActionId.value === getActionMetadata(message)?.actionId
}

function formatActionStatus(message: AiChatMessageVO) {
  const status = getActionMetadata(message)?.status
  if (status === 'approved') return '已同意'
  if (status === 'rejected') return '已拒绝'
  if (status === 'expired') return '已过期'
  return '待确认'
}

function renderMarkdown(content: string) {
  return DOMPurify.sanitize(markdown.render(content || ''))
}

function formatTime(value?: string) {
  if (!value) return ''

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function getSessionInitial(title: string) {
  const normalized = title.trim()
  return normalized ? normalized.slice(0, 1).toUpperCase() : 'A'
}

function scrollToBottom() {
  const container = messageStreamRef.value
  if (!container) return
  container.scrollTop = container.scrollHeight
}

function setupCompactHistoryMedia() {
  if (typeof window === 'undefined') return

  compactHistoryMediaQuery = window.matchMedia(COMPACT_HISTORY_MEDIA)
  syncCompactHistoryMode(compactHistoryMediaQuery.matches)
  compactHistoryMediaQuery.addEventListener('change', handleCompactHistoryMediaChange)
}

function tearDownCompactHistoryMedia() {
  compactHistoryMediaQuery?.removeEventListener('change', handleCompactHistoryMediaChange)
  compactHistoryMediaQuery = null
}

function handleCompactHistoryMediaChange(event: MediaQueryListEvent) {
  syncCompactHistoryMode(event.matches)
}

function syncCompactHistoryMode(matches: boolean) {
  isCompactScreen.value = matches
  isCompactHistoryOpen.value = false
}
</script>

<template>
  <AiWorkbenchSkeleton v-if="showInitialSkeleton" />
  <div
    v-else
    class="ai-chat-shell"
    :class="{
      'ai-chat-shell--history-collapsed': !isCompactScreen && isHistoryCollapsed,
      'ai-chat-shell--compact': isCompactScreen,
    }"
  >
    <div
      v-if="isCompactScreen && isCompactHistoryOpen"
      class="ai-chat-shell__backdrop"
      @click="closeCompactHistory"
    />

    <aside
      class="history-pane"
      :class="{
        'history-pane--collapsed': !isCompactScreen && isHistoryCollapsed,
        'history-pane--compact': isCompactScreen,
        'history-pane--compact-open': isCompactScreen && isCompactHistoryOpen,
      }"
    >
      <header class="history-pane__header">
        <div v-if="showHistoryDetails" class="history-pane__heading">
          <p class="eyebrow">AI Workspace</p>
          <h2>历史会话</h2>
        </div>
        <div v-else class="history-pane__brand">AI</div>

        <el-button circle :title="historyToggleTitle" @click="toggleHistoryRail">
          <el-icon>
            <Expand v-if="isCompactScreen ? !isCompactHistoryOpen : isHistoryCollapsed" />
            <Fold v-else />
          </el-icon>
        </el-button>
      </header>

      <el-button class="history-pane__new" type="primary" title="发起新对话" @click="startNewConversation">
        <el-icon><Plus /></el-icon>
        <span v-if="showHistoryDetails">新对话</span>
      </el-button>

      <div class="history-list">
        <template v-if="loadingSessions && sessions.length === 0">
          <div
            v-for="item in 5"
            :key="`history-skeleton-${item}`"
            class="history-item history-item--skeleton"
            :class="{ 'history-item--collapsed': !showHistoryDetails }"
          >
            <div class="history-item__button">
              <span class="history-item__avatar history-item__avatar--skeleton" />
              <div v-if="showHistoryDetails" class="history-item__content">
                <div class="history-item__title-row">
                  <span class="history-skeleton history-skeleton--title" />
                </div>
                <span class="history-skeleton history-skeleton--line" />
              </div>
            </div>
          </div>
        </template>

        <template v-else>
          <div
            v-for="session in sessions"
            :key="session.sessionId"
            class="history-item"
            :class="{
              active: activeSessionId === session.sessionId,
              'history-item--collapsed': !showHistoryDetails,
            }"
          >
            <button
              class="history-item__button"
              :title="session.title"
              :disabled="deletingSessionId === session.sessionId"
              @click="selectSession(session.sessionId)"
            >
              <span class="history-item__avatar">{{ getSessionInitial(session.title) }}</span>

              <div v-if="showHistoryDetails" class="history-item__content">
                <div class="history-item__title-row">
                  <span class="history-item__title">{{ session.title }}</span>
                  <span class="history-item__time">{{ formatTime(session.lastMessageTime) }}</span>
                </div>
                <p class="history-item__preview">{{ session.lastMessagePreview || '暂无消息' }}</p>
              </div>
            </button>

            <el-button
              v-if="showHistoryDetails"
              class="history-item__delete"
              text
              circle
              title="删除对话"
              :loading="deletingSessionId === session.sessionId"
              :disabled="deletingSessionId === session.sessionId"
              @click.stop="handleDeleteSession(session)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>

          <div
            v-if="!loadingSessions && sessions.length === 0"
            class="history-empty"
            :class="{ 'history-empty--collapsed': !showHistoryDetails }"
          >
            <template v-if="showHistoryDetails">
              <p>暂无历史对话</p>
              <span>从右侧发起一轮新对话后，这里会自动沉淀历史记录。</span>
            </template>
            <template v-else>
              <span class="history-empty__dot" />
            </template>
          </div>
        </template>
      </div>
    </aside>

    <section class="chat-pane">
      <header class="chat-pane__toolbar">
        <div class="chat-pane__title-block">
          <p class="chat-pane__meta">{{ toolbarMeta }}</p>
          <div class="chat-pane__headline">
            <h1>{{ pageTitle }}</h1>
            <span class="chat-pane__status">{{ toolbarStatus }}</span>
          </div>
        </div>

        <div class="chat-pane__actions">
          <button
            v-if="showToolbarHistoryToggle"
            class="action-btn"
            :title="historyToggleTitle"
            @click="toggleHistoryRail"
          >
            <el-icon>
              <Expand v-if="isCompactScreen ? !isCompactHistoryOpen : isHistoryCollapsed" />
              <Fold v-else />
            </el-icon>
          </button>

          <button class="action-btn" :disabled="loadingSessions" title="刷新会话列表" @click="handleRefreshSessions">
            <el-icon :class="{ 'is-loading': loadingSessions }"><RefreshRight /></el-icon>
            <span>刷新</span>
          </button>
        </div>
      </header>

      <section ref="messageStreamRef" class="chat-pane__messages">
        <div class="chat-pane__thread">
          <div v-if="loadingMessages && messages.length === 0" class="message-skeleton-stack">
            <div class="message-skeleton message-skeleton--assistant">
              <span class="message-skeleton__line long" />
              <span class="message-skeleton__line medium" />
              <span class="message-skeleton__line short" />
            </div>
            <div class="message-skeleton message-skeleton--user">
              <span class="message-skeleton__line medium" />
              <span class="message-skeleton__line short" />
            </div>
            <div class="message-skeleton message-skeleton--assistant">
              <span class="message-skeleton__line long" />
              <span class="message-skeleton__line long" />
              <span class="message-skeleton__line medium" />
            </div>
          </div>

          <div v-else-if="isEmptyConversation" class="empty-state">
            <div class="empty-state__eyebrow">Owner AI Workspace</div>
            <h3>今天想看哪些经营结论？</h3>
            <p>
              直接提问支出结构、待缴税金、薪资变化或业务风险，AI 会结合当前公司数据给出结论与解释。
            </p>

            <div class="empty-state__chips">
              <button
                v-for="chip in promptChips"
                :key="chip"
                class="prompt-chip"
                @click="handleChipClick(chip)"
              >
                {{ chip }}
              </button>
            </div>
          </div>

          <article
            v-for="message in messages"
            :key="message.id"
            class="message-row"
            :class="`message-row--${message.role}`"
          >
            <div v-if="message.messageType === 'action_required'" class="hitl-card">
              <div class="hitl-card__header">
                <span class="hitl-card__title">AI 请求更新企业画像</span>
                <span class="hitl-card__status">{{ formatActionStatus(message) }}</span>
              </div>

              <div class="hitl-card__body">
                <div class="hitl-column hitl-column--old">
                  <span class="hitl-label">当前内容</span>
                  <p>{{ getActionMetadata(message)?.oldValue || '暂无企业画像' }}</p>
                </div>

                <div class="hitl-column hitl-column--new">
                  <span class="hitl-label">建议更新为</span>
                  <p>{{ getActionMetadata(message)?.proposedValue }}</p>
                </div>
              </div>

              <div class="hitl-card__footer">
                <el-button
                  :disabled="!isActionPending(message) || isActionProcessing(message)"
                  @click="handleConfirmAction(message, false)"
                >
                  拒绝修改
                </el-button>
                <el-button
                  type="primary"
                  :loading="isActionProcessing(message)"
                  :disabled="!isActionPending(message)"
                  @click="handleConfirmAction(message, true)"
                >
                  同意更新
                </el-button>
              </div>
            </div>

            <div
              v-else-if="message.role === 'assistant'"
              class="assistant-stack"
            >
              <div class="message-bubble message-bubble--assistant">
                <div class="assistant-content" v-html="renderMarkdown(message.content)" />
              </div>
            </div>

            <div v-else class="message-bubble message-bubble--user">
              {{ message.content }}
            </div>
          </article>

          <div v-if="isThinking" class="assistant-thinking">
            正在思考...
          </div>
        </div>
      </section>

      <footer class="chat-pane__composer">
        <div class="chat-composer__surface">
          <div class="chat-composer__input">
            <el-input
              v-model="inputMessage"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="直接问：本月支出结构、待缴税金、员工成本变化，或者让 AI 帮你理解经营波动。"
              @keydown="handleKeydown"
            />
            <div class="chat-composer__hint">Enter 发送，Shift + Enter 换行</div>
          </div>

          <div class="chat-composer__actions">
            <el-button
              type="primary"
              circle
              title="发送消息"
              :disabled="!inputMessage.trim() || isThinking"
              @click="handleSendMessageStream"
            >
              <el-icon><Promotion /></el-icon>
            </el-button>
          </div>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.ai-chat-shell {
  position: relative;
  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border-radius: 24px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow:
    rgba(0, 0, 0, 0.04) 0px 8px 32px,
    rgba(0, 0, 0, 0.02) 0px 2px 10px;
  isolation: isolate;
  transition: grid-template-columns 0.24s ease;
}

.ai-chat-shell::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #0075de 0%, #3d92ff 40%, #213183 100%);
  z-index: 10;
}

.ai-chat-shell--history-collapsed {
  grid-template-columns: 72px minmax(0, 1fr);
}

.ai-chat-shell--compact {
  grid-template-columns: minmax(0, 1fr);
}

.ai-chat-shell__backdrop {
  position: absolute;
  inset: 0;
  z-index: 2;
  background: rgba(15, 23, 42, 0.18);
  backdrop-filter: blur(2px);
}

.history-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 18px 14px 14px;
  box-sizing: border-box;
  background: rgba(247, 248, 250, 0.4);
}

.history-pane--collapsed {
  padding: 18px 10px 14px;
}

.history-pane--compact {
  position: absolute;
  inset: 0 auto 0 0;
  width: min(86vw, 320px);
  z-index: 3;
  transform: translateX(calc(-100% - 16px));
  transition:
    transform 0.24s ease,
    box-shadow 0.24s ease;
  box-shadow: none;
}

.history-pane--compact-open {
  transform: translateX(0);
  box-shadow: 20px 0 42px rgba(15, 23, 42, 0.16);
}

.history-pane__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.history-pane--collapsed .history-pane__header {
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.history-pane__heading h2 {
  margin: 6px 0 0;
  font-size: 24px;
  line-height: 1.12;
  color: rgba(15, 23, 42, 0.96);
}

.history-pane__brand {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: #eef4ff;
  color: #0f62d6;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.eyebrow {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #9b948e;
}

.history-pane__new {
  width: 100%;
  margin-top: 20px;
  height: 42px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #0075de 0%, #1473e6 100%);
  box-shadow: 0 4px 12px rgba(0, 117, 222, 0.2);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.history-pane__new:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(0, 117, 222, 0.3);
  filter: brightness(1.05);
}

.history-pane__new:active {
  transform: translateY(0);
}

.history-pane--collapsed .history-pane__new {
  width: 42px;
  height: 42px;
  padding: 0;
  border-radius: 12px;
  align-self: center;
}

.history-list {
  flex: 1;
  min-height: 0;
  margin-top: 18px;
  padding-right: 2px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
}

.history-item {
  position: relative;
}

.history-item__button {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  border: none;
  background: transparent;
  border-radius: 18px;
  padding: 12px 44px 12px 12px;
  box-sizing: border-box;
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
  transition:
    background-color 0.18s ease,
    transform 0.18s ease;
}

.history-item__button:hover {
  background: rgba(15, 98, 214, 0.06);
}

.history-item.active .history-item__button {
  background: rgba(15, 98, 214, 0.08);
  box-shadow: inset 0 0 0 1px rgba(15, 98, 214, 0.14);
}

.history-item__button:disabled {
  cursor: wait;
  opacity: 0.72;
}

.history-item--collapsed .history-item__button {
  justify-content: center;
  padding: 8px 0;
}

.history-item__avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 12px;
  background: #f1f5fa;
  color: #0f62d6;
  font-size: 14px;
  font-weight: 700;
}

.history-item.active .history-item__avatar {
  background: #0f62d6;
  color: #ffffff;
}

.history-item--skeleton .history-item__button {
  cursor: default;
}

.history-item__avatar--skeleton,
.history-skeleton,
.message-skeleton__line {
  position: relative;
  overflow: hidden;
  background: #efece9;
}

.history-item__avatar--skeleton::after,
.history-skeleton::after,
.message-skeleton__line::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.95), transparent);
  animation: shimmer 1.4s ease infinite;
}

.history-item__avatar--skeleton {
  border-radius: 12px;
}

.history-skeleton {
  display: block;
  border-radius: 999px;
}

.history-skeleton--title {
  width: 72%;
  height: 14px;
}

.history-skeleton--line {
  width: 100%;
  height: 12px;
  margin-top: 8px;
}

.history-item__content {
  flex: 1;
  min-width: 0;
}

.history-item__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.history-item__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: rgba(15, 23, 42, 0.94);
}

.history-item__time {
  flex-shrink: 0;
  font-size: 11px;
  color: #9a938c;
}

.history-item__preview {
  margin: 5px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 1.4;
  color: #7d7771;
}

.history-item__delete {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.18s ease;
}

.history-item:hover .history-item__delete,
.history-item.active .history-item__delete {
  opacity: 1;
  pointer-events: auto;
}

.history-empty {
  margin-top: auto;
  padding: 18px 10px;
  text-align: center;
  color: #8a837d;
}

.history-empty p {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.history-empty span {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
}

.history-empty--collapsed {
  display: grid;
  place-items: center;
  padding: 18px 0;
}

.history-empty__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #d0d7e2;
}

.chat-pane {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: transparent;
}

.chat-pane__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 24px 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.chat-pane__title-block,
.chat-pane__actions {
  min-width: 0;
}

.chat-pane__meta {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #9b948e;
}

.chat-pane__headline {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
  margin-top: 6px;
}

.chat-pane__headline h1 {
  margin: 0;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 22px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.96);
}

.chat-pane__status {
  flex-shrink: 0;
  font-size: 12px;
  color: #8f8882;
}

.chat-pane__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: rgba(15, 23, 42, 0.65);
  font-size: 13px;
  font-weight: 500;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.04);
  color: rgba(15, 23, 42, 0.95);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chat-pane__messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 26px 24px 180px;
  box-sizing: border-box;
  overscroll-behavior: contain;
}

.chat-pane__thread {
  width: min(100%, 960px);
  margin: 0 auto;
}

.message-skeleton-stack {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.message-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 72%;
  padding: 18px;
  border-radius: 24px;
}

.message-skeleton--assistant {
  background: #f6f7f9;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.message-skeleton--user {
  align-self: flex-end;
  background: linear-gradient(135deg, rgba(43, 127, 255, 0.24), rgba(15, 98, 214, 0.2));
}

.message-skeleton__line {
  display: block;
  height: 14px;
  border-radius: 999px;
}

.message-skeleton__line.long {
  width: 100%;
}

.message-skeleton__line.medium {
  width: 74%;
}

.message-skeleton__line.short {
  width: 48%;
}

.empty-state {
  min-height: 100%;
  display: grid;
  align-content: center;
  justify-items: center;
  padding: 44px 24px 56px;
  text-align: center;
  color: #6f6964;
}

.empty-state__eyebrow {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(15, 98, 214, 0.08);
  color: #0f62d6;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.empty-state h3 {
  margin: 22px 0 0;
  max-width: 12ch;
  font-size: 38px;
  line-height: 1.12;
  color: rgba(15, 23, 42, 0.96);
}

.empty-state p {
  margin: 16px 0 0;
  max-width: 680px;
  font-size: 15px;
  line-height: 1.8;
}

.empty-state__chips {
  margin-top: 28px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  max-width: 760px;
}

.message-row {
  display: flex;
  width: 100%;
  margin-bottom: 22px;
  animation: messageSpringFadeUp 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.1) both;
}

.message-row:last-child {
  margin-bottom: 0;
}

.message-row--user {
  justify-content: flex-end;
}

.message-row--assistant,
.message-row--system {
  justify-content: flex-start;
}

.assistant-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: min(78%, 820px);
}

.message-bubble {
  max-width: min(74%, 760px);
  padding: 16px 18px;
  font-size: 15px;
  line-height: 1.78;
  overflow-wrap: anywhere;
}

.message-bubble--assistant {
  width: 100%;
  max-width: 100%;
  padding: 0;
  border-radius: 24px 24px 24px 10px;
  background: transparent;
  border: none;
  color: rgba(15, 23, 42, 0.9);
  overflow: hidden;
}

.assistant-content {
  padding: 12px 0;
}

.assistant-thinking {
  display: inline-flex;
  align-items: center;
  margin-left: 10px;
  color: #8e8882;
  font-size: 12px;
  line-height: 1.6;
  animation: thinkingPulse 1.3s ease-in-out infinite;
}

.assistant-content :deep(table) {
  display: block;
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  margin: 16px 0;
  border-collapse: collapse;
  font-size: 13px;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.assistant-content :deep(th) {
  background: rgba(0, 0, 0, 0.03);
  font-weight: 600;
}

.assistant-content :deep(th),
.assistant-content :deep(td) {
  border: 1px solid rgba(0, 0, 0, 0.08);
  padding: 10px 14px;
  text-align: left;
  white-space: nowrap;
}

.assistant-content :deep(blockquote) {
  margin: 14px 0;
  padding: 12px 18px;
  border-left: 4px solid #0075de;
  background: rgba(0, 117, 222, 0.04);
  border-radius: 4px 8px 8px 4px;
  color: rgba(0, 0, 0, 0.7);
}

.assistant-content :deep(pre) {
  background: #1e293b;
  color: #f8fafc;
  padding: 16px;
  border-radius: 12px;
  overflow-x: auto;
  margin: 14px 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
}

.assistant-content :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  background: rgba(0, 0, 0, 0.05);
  padding: 3px 6px;
  border-radius: 6px;
  font-size: 0.9em;
  color: #d946ef;
}

.assistant-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: inherit;
}

.assistant-content :deep(p),
.assistant-content :deep(ul),
.assistant-content :deep(ol) {
  margin: 0;
}

.assistant-content :deep(ul),
.assistant-content :deep(ol) {
  padding-left: 1.35em;
}

.assistant-content :deep(li + li) {
  margin-top: 6px;
}

.assistant-content :deep(p + p),
.assistant-content :deep(p + table),
.assistant-content :deep(table + p),
.assistant-content :deep(ul + p),
.assistant-content :deep(ol + p),
.assistant-content :deep(ul + ul),
.assistant-content :deep(ol + ol),
.assistant-content :deep(p + ul),
.assistant-content :deep(p + ol),
.assistant-content :deep(ul + table),
.assistant-content :deep(ol + table) {
  margin-top: 10px;
}

.message-bubble--user {
  border-radius: 22px 22px 8px 22px;
  background: linear-gradient(135deg, #4292ff 0%, #005bab 100%);
  color: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 98, 214, 0.25);
  white-space: pre-wrap;
}

.hitl-card {
  position: relative;
  overflow: hidden;
  width: min(100%, 860px);
  border: 1px solid rgba(221, 91, 0, 0.25);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.95);
  padding: 20px 20px 20px 24px;
  box-sizing: border-box;
  box-shadow: 0 16px 32px rgba(221, 91, 0, 0.08);
}

.hitl-card::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 4px;
  background: #dd5b00;
}

.hitl-card__header,
.hitl-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.hitl-card__title {
  font-size: 15px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.94);
}

.hitl-card__status {
  font-size: 12px;
  color: #9a938c;
}

.hitl-card__body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.hitl-column {
  padding: 16px;
  border-radius: 16px;
}

.hitl-column--old {
  background: #f6f5f3;
}

.hitl-column--new {
  background: #edf5ff;
}

.hitl-label {
  display: inline-block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 700;
  color: #7d7771;
}

.hitl-column--new .hitl-label {
  color: #0f62d6;
}

.hitl-column p {
  margin: 0;
  font-size: 14px;
  line-height: 1.72;
  color: rgba(15, 23, 42, 0.82);
}

.hitl-card__footer {
  margin-top: 16px;
  justify-content: flex-end;
}

.prompt-chip {
  border: none;
  border-radius: 999px;
  padding: 9px 16px;
  background: rgba(255, 255, 255, 0.95);
  color: rgba(15, 23, 42, 0.84);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
  transition:
    transform 0.24s cubic-bezier(0.175, 0.885, 0.32, 1.275),
    background-color 0.24s ease,
    color 0.24s ease,
    box-shadow 0.24s ease;
}

.prompt-chip:hover {
  transform: translateY(-2px) scale(1.02);
  background: #ffffff;
  color: #0f62d6;
  box-shadow: 0 8px 20px rgba(15, 98, 214, 0.12), inset 0 0 0 1px rgba(15, 98, 214, 0.2);
}

.chat-pane__composer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 10;
  pointer-events: none;
  padding: 40px 22px 20px;
  background: linear-gradient(to bottom, rgba(255, 255, 255, 0) 0%, rgba(255, 255, 255, 0.8) 30%, rgba(255, 255, 255, 1) 60%);
}

.chat-composer__surface {
  pointer-events: auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: end;
  padding: 14px 16px 12px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.4),
    0 12px 32px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.chat-composer__surface:focus-within {
  transform: translateY(-2px);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.6),
    0 16px 40px rgba(0, 0, 0, 0.12);
}

.chat-composer__input {
  min-width: 0;
}

.chat-composer__hint {
  margin-top: 8px;
  padding-left: 4px;
  font-size: 12px;
  color: #928b85;
}

.chat-composer__actions {
  display: flex;
  align-items: center;
  align-self: stretch;
  padding-bottom: 4px;
}

.chat-composer__actions :deep(.el-button) {
  width: 44px;
  height: 44px;
  border: none;
  background: linear-gradient(135deg, #60a5fa 0%, #2563eb 100%);
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.3);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.chat-composer__actions :deep(.el-button:not(:disabled):hover) {
  transform: scale(1.08) rotate(-5deg);
  box-shadow: 0 6px 20px rgba(37, 99, 235, 0.45);
  filter: brightness(1.1);
}

.chat-composer__actions :deep(.el-button:disabled) {
  background: #e2e8f0;
  box-shadow: none;
  color: #94a3b8;
}

.chat-composer__actions :deep(.el-icon) {
  font-size: 20px;
  transition: transform 0.3s ease;
}

.chat-composer__actions :deep(.el-button:not(:disabled):hover .el-icon) {
  transform: translateX(2px) translateY(-2px);
}

.chat-composer__surface :deep(.el-textarea__wrapper) {
  padding: 0;
  box-shadow: none;
  background: transparent;
}

.chat-composer__surface :deep(.el-textarea__inner) {
  min-height: 72px !important;
  max-height: 220px;
  padding: 6px 4px;
  border: none;
  box-shadow: none;
  background: transparent;
  color: rgba(15, 23, 42, 0.92);
  font-size: 15px;
  line-height: 1.7;
}

.chat-composer__surface :deep(.el-textarea__inner::placeholder) {
  color: #9d9791;
}

@keyframes messageSpringFadeUp {
  0% {
    opacity: 0;
    transform: translateY(16px) scale(0.98);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes thinkingPulse {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 1;
  }
}

@keyframes shimmer {
  100% {
    transform: translateX(100%);
  }
}

@media (max-width: 1100px) {
  .ai-chat-shell {
    border-radius: 26px;
  }

  .chat-pane__toolbar {
    padding: 16px 20px 12px;
  }

  .chat-pane__messages {
    padding: 22px 20px 16px;
  }

  .chat-pane__composer {
    padding: 14px 18px 18px;
  }

  .chat-pane__thread,
  .assistant-stack,
  .message-bubble,
  .hitl-card {
    max-width: 100%;
    width: 100%;
  }

  .message-skeleton {
    max-width: 82%;
  }

  .message-bubble--user {
    max-width: min(84%, 640px);
  }
}

@media (max-width: 768px) {
  .ai-chat-shell {
    border-radius: 22px;
  }

  .history-pane--compact {
    width: min(90vw, 320px);
  }

  .chat-pane__toolbar {
    padding: 14px 16px 12px;
    gap: 12px;
  }

  .chat-pane__headline {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .chat-pane__headline h1 {
    font-size: 20px;
    white-space: normal;
  }

  .chat-pane__messages {
    padding: 20px 16px 14px;
  }

  .empty-state {
    padding: 28px 8px 36px;
  }

  .empty-state h3 {
    font-size: 30px;
  }

  .message-row {
    margin-bottom: 18px;
  }

  .message-skeleton,
  .message-bubble,
  .message-bubble--user,
  .assistant-stack,
  .hitl-card {
    max-width: 100%;
    width: 100%;
  }

  .hitl-card__body {
    grid-template-columns: 1fr;
  }

  .chat-pane__composer {
    padding: 12px 14px 14px;
  }

  .chat-composer__surface {
    grid-template-columns: 1fr;
  }

  .chat-composer__actions {
    justify-content: flex-end;
  }
}
</style>
