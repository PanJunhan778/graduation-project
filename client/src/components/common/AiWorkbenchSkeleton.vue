<template>
  <div class="ai-skeleton">
    <div class="ai-skeleton__shell ds-card">
      <aside class="ai-skeleton__history">
        <div class="ai-skeleton__history-head">
          <div>
            <div class="sk-block sk-eyebrow" />
            <div class="sk-block sk-brand" />
          </div>
          <div class="sk-block sk-round" />
        </div>

        <div class="sk-block sk-new-chat" />

        <div class="ai-skeleton__list">
          <div
            v-for="item in 6"
            :key="item"
            class="ai-skeleton__session"
          >
            <div class="sk-block sk-avatar" />
            <div class="ai-skeleton__session-text">
              <div class="sk-block sk-session-title" />
              <div class="sk-block sk-session-line" />
            </div>
          </div>
        </div>
      </aside>

      <section class="ai-skeleton__chat">
        <div class="ai-skeleton__toolbar">
          <div>
            <div class="sk-block sk-toolbar-meta" />
            <div class="sk-block sk-toolbar-title" />
          </div>
          <div class="ai-skeleton__toolbar-actions">
            <div class="sk-block sk-round" />
            <div class="sk-block sk-toolbar-action" />
          </div>
        </div>

        <div class="ai-skeleton__messages">
          <div class="ai-skeleton__bubble ai-skeleton__bubble--assistant">
            <div class="sk-block sk-bubble-line long" />
            <div class="sk-block sk-bubble-line medium" />
            <div class="sk-block sk-bubble-line short" />
          </div>
          <div class="ai-skeleton__bubble ai-skeleton__bubble--user">
            <div class="sk-block sk-bubble-line medium" />
            <div class="sk-block sk-bubble-line short" />
          </div>
          <div class="ai-skeleton__bubble ai-skeleton__bubble--assistant">
            <div class="sk-block sk-bubble-line long" />
            <div class="sk-block sk-bubble-line long" />
            <div class="sk-block sk-bubble-line medium" />
          </div>
        </div>

        <div class="ai-skeleton__composer">
          <div class="sk-block sk-composer" />
          <div class="sk-block sk-send" />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.ai-skeleton {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.ai-skeleton__shell {
  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border-radius: 32px;
  border-color: rgba(15, 23, 42, 0.08);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(251, 250, 248, 0.98) 100%);
}

.ai-skeleton__history {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 18px 14px 14px;
  border-right: 1px solid rgba(15, 23, 42, 0.08);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(247, 245, 241, 0.92) 100%);
}

.ai-skeleton__history-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ai-skeleton__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 18px;
}

.ai-skeleton__session {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 12px;
  border-radius: 18px;
  background: rgba(246, 245, 244, 0.72);
}

.ai-skeleton__session-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-skeleton__chat {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  min-height: 0;
  background:
    radial-gradient(circle at top, rgba(15, 98, 214, 0.05), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(251, 250, 249, 0.98) 100%);
}

.ai-skeleton__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding: 18px 24px 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.ai-skeleton__toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-skeleton__messages {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
}

.ai-skeleton__bubble {
  max-width: 72%;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px;
  border-radius: 24px;
}

.ai-skeleton__bubble--assistant {
  background: #f6f7f9;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.ai-skeleton__bubble--user {
  align-self: flex-end;
  background: linear-gradient(135deg, rgba(43, 127, 255, 0.24), rgba(15, 98, 214, 0.2));
}

.ai-skeleton__composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 16px 22px 20px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
}

.sk-block {
  position: relative;
  overflow: hidden;
  border-radius: 999px;
  background: #efece9;
}

.sk-block::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.95), transparent);
  animation: shimmer 1.4s ease infinite;
}

.sk-eyebrow {
  width: 92px;
  height: 12px;
}

.sk-brand {
  width: 124px;
  height: 26px;
  margin-top: 10px;
}

.sk-round {
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.sk-new-chat {
  width: 100%;
  height: 40px;
  margin-top: 18px;
}

.sk-avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
}

.sk-session-title {
  width: 72%;
  height: 14px;
}

.sk-session-line {
  width: 100%;
  height: 12px;
}

.sk-toolbar-meta {
  width: 88px;
  height: 12px;
}

.sk-toolbar-title {
  width: 260px;
  height: 24px;
  margin-top: 10px;
}

.sk-toolbar-action {
  width: 92px;
  height: 36px;
}

.sk-bubble-line {
  height: 14px;
}

.sk-bubble-line.long {
  width: 100%;
}

.sk-bubble-line.medium {
  width: 72%;
}

.sk-bubble-line.short {
  width: 48%;
}

.sk-composer {
  height: 82px;
  border-radius: 26px;
}

.sk-send {
  width: 44px;
  height: 44px;
}

@keyframes shimmer {
  100% {
    transform: translateX(100%);
  }
}

@media (max-width: 1100px) {
  .ai-skeleton__shell {
    grid-template-columns: 1fr;
    border-radius: 26px;
  }

  .ai-skeleton__history {
    display: none;
  }

  .ai-skeleton__toolbar {
    padding: 16px 20px 12px;
  }

  .ai-skeleton__messages {
    padding: 22px 20px;
  }

  .ai-skeleton__bubble {
    max-width: 82%;
  }

  .ai-skeleton__composer {
    padding: 14px 18px 18px;
  }
}

@media (max-width: 768px) {
  .ai-skeleton__shell {
    border-radius: 22px;
  }

  .ai-skeleton__toolbar {
    padding: 14px 16px 12px;
  }

  .ai-skeleton__messages {
    padding: 20px 16px;
  }

  .ai-skeleton__bubble {
    max-width: 100%;
  }

  .ai-skeleton__composer {
    grid-template-columns: 1fr;
    padding: 12px 14px 14px;
  }
}
</style>
