<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import {
  confirmAiAction,
  deleteAiSession,
  listAiMessages,
  listAiSessions,
  streamAiChat,
} from '@/api/ai'
import type {
  AiActionMetadata,
  AiActionRequiredPayload,
  AiChatMessageVO,
  AiDoneEventPayload,
  AiSessionVO,
  AiTokenEventPayload,
} from '@/types'
import {
  Delete,
  Expand,
  Fold,
  Plus,
  Promotion,
  RefreshRight,
  VideoPause,
} from '@element-plus/icons-vue'

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
const isStreaming = ref(false)
const confirmingActionId = ref<number | null>(null)
const deletingSessionId = ref('')
const isHistoryCollapsed = ref(false)
const streamAbortController = ref<AbortController | null>(null)
const messageStreamRef = ref<HTMLElement | null>(null)

const activeSession = computed(
  () => sessions.value.find((item) => item.sessionId === activeSessionId.value) || null,
)
const pageTitle = computed(() => activeSession.value?.title || '开始新对话')
const isEmptyConversation = computed(() => messages.value.length === 0)
const toolbarMeta = computed(() => (activeSessionId.value ? '当前会话' : '新对话'))
const toolbarStatus = computed(() => {
  if (isStreaming.value) {
    return 'AI 正在生成回复'
  }
  return activeSessionId.value ? '会话已连接' : '等待你的第一条消息'
})

watch(
  messages,
  async () => {
    await nextTick()
    scrollToBottom()
  },
  { deep: true },
)

onMounted(async () => {
  await initializePage()
})

onBeforeUnmount(() => {
  stopStreaming()
})

async function initializePage() {
  loadingSessions.value = true
  try {
    await refreshSessions(undefined, { reloadMessages: true })
  } finally {
    loadingSessions.value = false
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
    return
  }

  stopStreaming()
  activeSessionId.value = sessionId
  await loadMessages(sessionId)
}

function startNewConversation() {
  stopStreaming()
  activeSessionId.value = ''
  messages.value = []
  inputMessage.value = ''
}

async function handleSendMessage() {
  const text = inputMessage.value.trim()
  if (!text || isStreaming.value) {
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

  let assistantPlaceholder: AiChatMessageVO | null = null
  let resolvedSessionId = activeSessionId.value

  messages.value.push(userMessage)
  inputMessage.value = ''
  isStreaming.value = true

  const abortController = new AbortController()
  streamAbortController.value = abortController

  try {
    await streamAiChat(
      {
        sessionId: activeSessionId.value || undefined,
        message: text,
      },
      {
        onSession: ({ sessionId }) => {
          resolvedSessionId = sessionId
          if (!activeSessionId.value) {
            activeSessionId.value = sessionId
          }
        },
        onToken: (payload: AiTokenEventPayload) => {
          if (!assistantPlaceholder) {
            assistantPlaceholder = createAssistantPlaceholder()
            messages.value.push(assistantPlaceholder)
          }
          assistantPlaceholder.content += payload.content
        },
        onDone: async (payload: AiDoneEventPayload) => {
          if (!assistantPlaceholder) {
            assistantPlaceholder = createAssistantPlaceholder()
            messages.value.push(assistantPlaceholder)
          }
          assistantPlaceholder.id = payload.messageId
          assistantPlaceholder.content = payload.content
          await refreshSessions(resolvedSessionId)
        },
        onActionRequired: async (payload: AiActionRequiredPayload) => {
          const actionMessage: AiChatMessageVO = {
            id: -payload.actionId,
            role: 'assistant',
            messageType: 'action_required',
            content: 'AI 请求更新企业画像',
            metadata: {
              actionId: payload.actionId,
              toolName: payload.toolName,
              oldValue: payload.oldValue,
              proposedValue: payload.proposedValue,
              confirmToken: payload.confirmToken,
              status: 'pending',
            },
            createTime: new Date().toISOString(),
          }
          messages.value.push(actionMessage)
          await refreshSessions(resolvedSessionId)
        },
      },
      abortController.signal,
    )
  } catch (error) {
    messages.value = messages.value.filter(
      (item) => item.id !== userMessage.id && item.id !== assistantPlaceholder?.id,
    )

    if (!(error instanceof DOMException && error.name === 'AbortError')) {
      ElMessage.error(error instanceof Error ? error.message : 'AI 服务暂时不可用')
    }
  } finally {
    isStreaming.value = false
    streamAbortController.value = null
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
    stopStreaming()
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

function stopStreaming() {
  streamAbortController.value?.abort()
  streamAbortController.value = null
  isStreaming.value = false
}

function toggleHistoryRail() {
  isHistoryCollapsed.value = !isHistoryCollapsed.value
}

function handleChipClick(chip: string) {
  inputMessage.value = chip
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void handleSendMessage()
  }
}

function createAssistantPlaceholder(): AiChatMessageVO {
  return {
    id: -(Date.now() + Math.floor(Math.random() * 1000)),
    role: 'assistant',
    messageType: 'markdown',
    content: '',
    metadata: null,
    createTime: new Date().toISOString(),
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
</script>

<template>
  <div class="ai-chat-page" :class="{ 'ai-chat-page--collapsed': isHistoryCollapsed }">
    <aside class="history-rail" :class="{ 'history-rail--collapsed': isHistoryCollapsed }">
      <header class="history-rail__header">
        <div v-if="!isHistoryCollapsed" class="history-rail__heading">
          <p class="eyebrow">AI Workspace</p>
          <h2>历史会话</h2>
        </div>
        <div v-else class="history-rail__brand">AI</div>

        <el-button
          circle
          :title="isHistoryCollapsed ? '展开历史' : '收起历史'"
          @click="toggleHistoryRail"
        >
          <el-icon>
            <Expand v-if="isHistoryCollapsed" />
            <Fold v-else />
          </el-icon>
        </el-button>
      </header>

      <el-button class="history-rail__new" type="primary" title="发起新对话" @click="startNewConversation">
        <el-icon><Plus /></el-icon>
        <span v-if="!isHistoryCollapsed">新对话</span>
      </el-button>

      <div v-loading="loadingSessions" class="history-list">
        <div
          v-for="session in sessions"
          :key="session.sessionId"
          class="history-item"
          :class="{
            active: activeSessionId === session.sessionId,
            'history-item--collapsed': isHistoryCollapsed,
          }"
        >
          <button
            class="history-item__button"
            :title="session.title"
            :disabled="deletingSessionId === session.sessionId"
            @click="selectSession(session.sessionId)"
          >
            <span class="history-item__avatar">{{ getSessionInitial(session.title) }}</span>

            <div v-if="!isHistoryCollapsed" class="history-item__content">
              <div class="history-item__title-row">
                <span class="history-item__title">{{ session.title }}</span>
                <span class="history-item__time">{{ formatTime(session.lastMessageTime) }}</span>
              </div>
              <p class="history-item__preview">{{ session.lastMessagePreview || '暂无消息' }}</p>
            </div>
          </button>

          <el-button
            v-if="!isHistoryCollapsed"
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
          :class="{ 'history-empty--collapsed': isHistoryCollapsed }"
        >
          <template v-if="isHistoryCollapsed">
            <span class="history-empty__dot" />
          </template>
          <template v-else>
            <p>暂无历史对话</p>
            <span>从右侧发起一轮新对话后，这里会自动沉淀历史记录。</span>
          </template>
        </div>
      </div>
    </aside>

    <section class="chat-workbench">
      <header class="chat-workbench__toolbar">
        <div class="chat-workbench__title-block">
          <p class="chat-workbench__meta">{{ toolbarMeta }}</p>
          <div class="chat-workbench__headline">
            <h1>{{ pageTitle }}</h1>
            <span class="chat-workbench__status">{{ toolbarStatus }}</span>
          </div>
        </div>

        <div class="chat-workbench__actions">
          <el-button
            v-if="isHistoryCollapsed"
            circle
            title="展开历史"
            @click="toggleHistoryRail"
          >
            <el-icon><Expand /></el-icon>
          </el-button>

          <el-button :loading="loadingSessions" title="刷新会话列表" @click="handleRefreshSessions">
            <el-icon><RefreshRight /></el-icon>
            <span>刷新</span>
          </el-button>
        </div>
      </header>

      <section class="chat-workbench__body">
        <section ref="messageStreamRef" v-loading="loadingMessages" class="chat-workbench__messages">
          <div class="chat-workbench__thread">
            <div v-if="isEmptyConversation" class="empty-state">
              <div class="empty-state__badge">Owner AI Workspace</div>
              <h3>把经营问题直接交给 AI</h3>
              <p>
                你可以让它解释支出结构、待缴税金、薪资分布和业务风险，也可以基于现有数据整理出更清晰的企业画像。
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
                class="message-bubble message-bubble--assistant"
              >
                <div v-html="renderMarkdown(message.content)" />
              </div>

              <div v-else class="message-bubble message-bubble--user">
                {{ message.content }}
              </div>
            </article>
          </div>
        </section>

        <footer class="chat-composer-shell">
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
              <el-button v-if="isStreaming" circle title="停止生成" @click="stopStreaming">
                <el-icon><VideoPause /></el-icon>
              </el-button>

              <el-button
                v-else
                type="primary"
                circle
                title="发送消息"
                :disabled="!inputMessage.trim()"
                @click="handleSendMessage"
              >
                <el-icon><Promotion /></el-icon>
              </el-button>
            </div>
          </div>
        </footer>
      </section>
    </section>
  </div>
</template>

<style scoped>
.ai-chat-page {
  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  gap: 18px;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  transition: grid-template-columns 0.24s ease;
}

.ai-chat-page--collapsed {
  grid-template-columns: 72px minmax(0, 1fr);
}

.history-rail {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 18px 14px;
  box-sizing: border-box;
  border-radius: 28px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: linear-gradient(180deg, #ffffff 0%, #fbfaf8 100%);
  box-shadow:
    0 14px 30px rgba(15, 23, 42, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.9);
  overflow: hidden;
}

.history-rail--collapsed {
  padding: 18px 10px;
}

.history-rail__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.history-rail__heading h2 {
  margin: 6px 0 0;
  font-size: 24px;
  line-height: 1.15;
  color: rgba(15, 23, 42, 0.96);
}

.history-rail__brand {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  background: #eef5ff;
  color: #0f62d6;
  font-size: 14px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.eyebrow {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #9a938c;
}

.history-rail__new {
  margin-top: 18px;
  width: 100%;
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
  transition: background-color 0.2s ease, transform 0.2s ease;
  font: inherit;
  color: inherit;
}

.history-item__button:hover {
  background: rgba(15, 98, 214, 0.06);
}

.history-item.active .history-item__button {
  background: #eaf3ff;
  box-shadow: inset 0 0 0 1px rgba(15, 98, 214, 0.18);
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
  background: #f2f6fb;
  color: #0f62d6;
  font-size: 14px;
  font-weight: 700;
}

.history-item.active .history-item__avatar {
  background: #0f62d6;
  color: #ffffff;
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

.chat-workbench {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border-radius: 30px;
  background:
    radial-gradient(circle at top, rgba(15, 98, 214, 0.07), transparent 34%),
    linear-gradient(180deg, #ffffff 0%, #fbfaf9 100%);
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow:
    0 18px 38px rgba(15, 23, 42, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.92);
}

.chat-workbench__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 56px;
  padding: 14px 24px 10px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.chat-workbench__title-block,
.chat-workbench__actions {
  min-width: 0;
}

.chat-workbench__meta {
  margin: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #9a938c;
}

.chat-workbench__headline {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
  margin-top: 4px;
}

.chat-workbench__headline h1 {
  margin: 0;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 20px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.96);
}

.chat-workbench__status {
  flex-shrink: 0;
  font-size: 12px;
  color: #8e8882;
}

.chat-workbench__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-workbench__body {
  position: relative;
  min-height: 0;
  overflow: hidden;
}

.chat-workbench__messages {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 24px 232px;
  box-sizing: border-box;
}

.chat-workbench__thread {
  width: min(100%, 980px);
  margin: 0 auto;
}

.empty-state {
  min-height: 100%;
  display: grid;
  place-items: center;
  gap: 0;
  padding: 40px 24px 24px;
  text-align: center;
  color: #6f6964;
}

.empty-state__badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(15, 98, 214, 0.08);
  color: #0f62d6;
  font-size: 12px;
  font-weight: 700;
}

.empty-state h3 {
  margin: 18px 0 0;
  font-size: 34px;
  line-height: 1.16;
  color: rgba(15, 23, 42, 0.96);
}

.empty-state p {
  margin: 14px 0 0;
  max-width: 720px;
  font-size: 15px;
  line-height: 1.8;
}

.empty-state__chips {
  margin-top: 28px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  max-width: 780px;
}

.message-row {
  display: flex;
  width: 100%;
  margin-bottom: 22px;
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

.message-bubble {
  max-width: min(76%, 780px);
  padding: 16px 18px;
  font-size: 15px;
  line-height: 1.78;
}

.message-bubble--assistant {
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(15, 23, 42, 0.06);
  color: rgba(15, 23, 42, 0.9);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.04);
}

.message-bubble--assistant :deep(table) {
  width: 100%;
  margin: 12px 0;
  border-collapse: collapse;
  font-size: 13px;
}

.message-bubble--assistant :deep(th),
.message-bubble--assistant :deep(td) {
  border: 1px solid rgba(15, 23, 42, 0.08);
  padding: 8px 10px;
  text-align: left;
}

.message-bubble--assistant :deep(p),
.message-bubble--assistant :deep(ul),
.message-bubble--assistant :deep(ol) {
  margin: 0;
}

.message-bubble--assistant :deep(p + p),
.message-bubble--assistant :deep(p + table),
.message-bubble--assistant :deep(table + p),
.message-bubble--assistant :deep(ul + p),
.message-bubble--assistant :deep(ol + p),
.message-bubble--assistant :deep(ul + ul),
.message-bubble--assistant :deep(ol + ol) {
  margin-top: 10px;
}

.message-bubble--user {
  border-radius: 24px 24px 8px 24px;
  background: linear-gradient(135deg, #2b7fff 0%, #0f62d6 100%);
  color: #ffffff;
  box-shadow: 0 14px 34px rgba(15, 98, 214, 0.22);
  white-space: pre-wrap;
}

.hitl-card {
  width: min(100%, 860px);
  border: 1px solid rgba(221, 91, 0, 0.2);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
  padding: 20px;
  box-sizing: border-box;
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.05);
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
  padding: 9px 14px;
  background: rgba(255, 255, 255, 0.88);
  color: rgba(15, 23, 42, 0.82);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.08);
  transition:
    transform 0.18s ease,
    background-color 0.18s ease,
    color 0.18s ease;
}

.prompt-chip:hover {
  transform: translateY(-1px);
  background: #eaf3ff;
  color: #0f62d6;
}

.chat-composer-shell {
  position: absolute;
  left: 50%;
  right: auto;
  bottom: 20px;
  width: min(calc(100% - 36px), 940px);
  transform: translateX(-50%);
}

.chat-composer__surface {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: end;
  padding: 16px 16px 12px;
  border-radius: 28px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(18px);
  box-shadow:
    0 16px 42px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.92);
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
</style>
