<!--
<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import {
  listAiMessages,
  listAiSessions,
  legacyChatApi,
  confirmAiAction,
} from '@/api/ai'
import type {
  AiActionMetadata,
  AiActionRequiredPayload,
  AiChatMessageVO,
  LegacyDonePayload,
  AiSessionVO,
  LegacyTokenPayload,
} from '@/types'
import {
  ChatDotRound,
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

const sessions = ref<AiSessionVO[]>([])
const messages = ref<AiChatMessageVO[]>([])
const activeSessionId = ref('')
const inputMessage = ref('')
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const isStreaming = ref(false)
const confirmingActionId = ref<number | null>(null)
const streamAbortController = ref<AbortController | null>(null)
const messageStreamRef = ref<HTMLElement | null>(null)

const promptChips = [
  '分析本月各项支出占比',
  '查询当前待缴税金',
  '看一下各部门薪资结构',
  '总结最近的关键经营风险',
]

const activeSession = computed(() =>
  sessions.value.find((item) => item.sessionId === activeSessionId.value) || null,
)

const pageTitle = computed(() => activeSession.value?.title || 'AI 智能助理')

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

async function initializePage() {
  loadingSessions.value = true
  try {
    const res = await listAiSessions()
    sessions.value = res.data
    if (sessions.value[0]?.sessionId) {
      activeSessionId.value = sessions.value[0].sessionId
      await loadMessages(activeSessionId.value)
    }
  } finally {
    loadingSessions.value = false
  }
}

async function refreshSessions(preferredSessionId?: string) {
  const res = await listAiSessions()
  sessions.value = res.data
  const targetSessionId =
    preferredSessionId ||
    activeSessionId.value ||
    sessions.value[0]?.sessionId ||
    ''

  if (!targetSessionId) {
    activeSessionId.value = ''
    messages.value = []
    return
  }

  if (!activeSessionId.value) {
    activeSessionId.value = targetSessionId
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
    await legacyChatApi(
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
        onToken: (payload: LegacyTokenPayload) => {
          if (!assistantPlaceholder) {
            assistantPlaceholder = createAssistantPlaceholder()
            messages.value.push(assistantPlaceholder)
          }
          assistantPlaceholder.content += payload.content
        },
        onDone: async (payload: LegacyDonePayload) => {
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
            content: 'AI 请求更新企业档案',
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

function stopStreaming() {
  streamAbortController.value?.abort()
  streamAbortController.value = null
  isStreaming.value = false
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

function formatTime(value: string) {
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

function scrollToBottom() {
  const container = messageStreamRef.value
  if (!container) return
  container.scrollTop = container.scrollHeight
}
</script>

<template>
  <div class="ai-chat-page">
    <aside class="session-panel ds-card">
      <div class="session-panel__header">
        <div>
          <p class="eyebrow">Owner Workspace</p>
          <h2>AI 助理</h2>
        </div>
        <el-button circle @click="startNewConversation">
          <el-icon><Plus /></el-icon>
        </el-button>
      </div>

      <el-button class="new-chat-btn" type="primary" @click="startNewConversation">
        <el-icon><ChatDotRound /></el-icon>
        <span>发起新对话</span>
      </el-button>

      <div v-loading="loadingSessions" class="session-list">
        <button
          v-for="session in sessions"
          :key="session.sessionId"
          class="session-item"
          :class="{ active: activeSessionId === session.sessionId }"
          @click="selectSession(session.sessionId)"
        >
          <div class="session-item__title">{{ session.title }}</div>
          <div class="session-item__preview">{{ session.lastMessagePreview || '暂无消息' }}</div>
          <div class="session-item__time">{{ formatTime(session.lastMessageTime) }}</div>
        </button>

        <div v-if="!loadingSessions && sessions.length === 0" class="session-empty">
          <p>还没有历史会话</p>
          <span>从右侧输入一个问题，我们就能开始第一轮经营分析。</span>
        </div>
      </div>
    </aside>

    <main class="chat-panel">
      <header class="chat-panel__header ds-card">
        <div class="chat-panel__header-main">
          <div>
            <p class="eyebrow">Full Screen Conversation</p>
            <h1>{{ pageTitle }}</h1>
          </div>
        </div>

        <div class="chat-panel__header-actions">
          <el-button @click="refreshSessions(activeSessionId)">
            <el-icon><RefreshRight /></el-icon>
            <span>刷新会话</span>
          </el-button>
        </div>
      </header>

      <section ref="messageStreamRef" v-loading="loadingMessages" class="message-stream ds-card">
        <div v-if="messages.length === 0" class="empty-state">
          <div class="empty-state__badge">M11 已接入</div>
          <h3>把经营问题直接交给 AI</h3>
          <p>
            你可以问支出结构、待缴税金、部门薪资分布，也可以让它根据业务变化提出企业画像更新建议。
          </p>
        </div>

        <article
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="`message-row--${message.role}`"
        >
          <div v-if="message.messageType === 'action_required'" class="hitl-card">
            <div class="hitl-card__header">
              <span class="hitl-card__title">AI 请求更新企业档案</span>
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

          <div
            v-else
            class="message-bubble message-bubble--user"
          >
            {{ message.content }}
          </div>
        </article>
      </section>

      <footer class="input-console ds-card">
        <div class="chip-row">
          <button
            v-for="chip in promptChips"
            :key="chip"
            class="prompt-chip"
            @click="handleChipClick(chip)"
          >
            {{ chip }}
          </button>
        </div>

        <div class="composer">
          <el-input
            v-model="inputMessage"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="直接问：本月支出结构、待缴税金、员工成本变化，或者让 AI 帮你理解经营波动。"
            @keydown="handleKeydown"
          />

          <div class="composer-actions">
            <el-button
              v-if="isStreaming"
              circle
              @click="stopStreaming"
            >
              <el-icon><VideoPause /></el-icon>
            </el-button>

            <el-button
              v-else
              type="primary"
              circle
              :disabled="!inputMessage.trim()"
              @click="handleSendMessage"
            >
              <el-icon><Promotion /></el-icon>
            </el-button>
          </div>
        </div>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.ai-chat-page {
  flex: 1;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 20px;
  overflow: hidden;
}

.session-panel,
.chat-panel__header,
.message-stream,
.input-console {
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #ffffff;
}

.session-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 20px;
  box-sizing: border-box;
  overflow: hidden;
}

.session-panel__header,
.chat-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.session-panel__header h2,
.chat-panel__header h1 {
  margin: 6px 0 0;
  font-size: 24px;
  line-height: 1.15;
  color: rgba(0, 0, 0, 0.95);
}

.eyebrow {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #a39e98;
}

.new-chat-btn {
  margin-top: 20px;
  width: 100%;
}

.session-list {
  margin-top: 20px;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 10px;
  overflow-y: auto;
  padding-right: 4px;
}

.session-item {
  width: 100%;
  text-align: left;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #ffffff;
  border-radius: 14px;
  padding: 14px;
  box-sizing: border-box;
  cursor: pointer;
  transition: all 0.2s ease;
  font: inherit;
}

.session-item:hover {
  border-color: rgba(0, 117, 222, 0.22);
  transform: translateY(-1px);
}

.session-item.active {
  border-color: #0075de;
  background: #f2f9ff;
}

.session-item__title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.9);
}

.session-item__preview,
.session-item__time {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #7d7771;
}

.session-empty,
.empty-state {
  display: grid;
  place-items: center;
  text-align: center;
  color: #7d7771;
}

.session-empty {
  margin-top: 24px;
  padding: 24px 12px;
}

.session-empty p,
.empty-state h3 {
  margin: 0;
}

.session-empty span,
.empty-state p {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
}

.chat-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 16px;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.chat-panel__header {
  padding: 20px 24px;
  box-sizing: border-box;
}

.chat-panel__header-main,
.chat-panel__header-actions {
  display: flex;
  align-items: center;
  min-width: 0;
}

.chat-panel__header-main > div {
  min-width: 0;
}

.message-stream {
  min-height: 0;
  padding: 24px;
  box-sizing: border-box;
  overflow-y: auto;
  overscroll-behavior: contain;
  background:
    radial-gradient(circle at top left, rgba(0, 117, 222, 0.06), transparent 28%),
    linear-gradient(180deg, #ffffff 0%, #fdfcfb 100%);
}

.empty-state {
  min-height: 100%;
  padding: 48px 24px;
}

.empty-state__badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 9999px;
  background: #f2f9ff;
  color: #0075de;
  font-size: 12px;
  font-weight: 600;
}

.message-row {
  display: flex;
  margin-bottom: 16px;
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
  max-width: min(820px, 78%);
  padding: 12px 16px;
  font-size: 15px;
  line-height: 1.75;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.03),
    0 6px 20px rgba(0, 0, 0, 0.03);
}

.message-bubble--assistant {
  border-radius: 16px 16px 16px 4px;
  background: #f6f5f4;
  color: rgba(0, 0, 0, 0.88);
}

.message-bubble--assistant :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
}

.message-bubble--assistant :deep(th),
.message-bubble--assistant :deep(td) {
  border: 1px solid rgba(0, 0, 0, 0.08);
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
.message-bubble--assistant :deep(ol + p) {
  margin-top: 10px;
}

.message-bubble--user {
  border-radius: 16px 16px 4px 16px;
  background: #0075de;
  color: #ffffff;
}

.hitl-card {
  width: min(820px, 90%);
  border: 2px solid #dd5b00;
  border-radius: 12px;
  background: #ffffff;
  padding: 16px;
  box-sizing: border-box;
  box-shadow:
    0 1px 2px rgba(0, 0, 0, 0.03),
    0 8px 26px rgba(0, 0, 0, 0.05);
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
}

.hitl-card__status {
  font-size: 12px;
  color: #a39e98;
}

.hitl-card__body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.hitl-column {
  padding: 16px;
  border-radius: 10px;
}

.hitl-column--old {
  background: #f6f5f4;
}

.hitl-column--new {
  background: #f2f9ff;
}

.hitl-label {
  display: inline-block;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #7d7771;
}

.hitl-column--new .hitl-label {
  color: #0075de;
}

.hitl-column p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: rgba(0, 0, 0, 0.82);
}

.hitl-card__footer {
  margin-top: 14px;
  justify-content: flex-end;
}

.input-console {
  padding: 16px 20px;
  box-sizing: border-box;
  overflow: hidden;
}

.chip-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  overflow-x: auto;
}

.prompt-chip {
  flex-shrink: 0;
  border: none;
  border-radius: 9999px;
  padding: 8px 14px;
  background: #f6f5f4;
  color: rgba(0, 0, 0, 0.78);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.prompt-chip:hover {
  background: #f2f9ff;
  color: #0075de;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.composer-actions {
  display: flex;
  align-items: center;
}
</style>
-->

<script setup lang="ts">
import AiChatWorkbench from './components/AiChatWorkbenchChatgpt.vue'
</script>

<template>
  <AiChatWorkbench />
</template>
