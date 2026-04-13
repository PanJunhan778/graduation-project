<template>
  <div class="ai-skeleton">
    <aside class="ai-skeleton__rail ds-card">
      <div class="sk-block sk-brand" />
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

    <section class="ai-skeleton__workbench ds-card">
      <div class="ai-skeleton__toolbar">
        <div>
          <div class="sk-block sk-toolbar-meta" />
          <div class="sk-block sk-toolbar-title" />
        </div>
        <div class="sk-block sk-toolbar-action" />
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
</template>

<style scoped>
.ai-skeleton {
  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  gap: 18px;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.ai-skeleton__rail,
.ai-skeleton__workbench {
  padding: 18px;
  min-height: 0;
}

.ai-skeleton__rail {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.ai-skeleton__list {
  display: flex;
  flex-direction: column;
  gap: 10px;
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

.ai-skeleton__workbench {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 16px;
  background:
    radial-gradient(circle at top, rgba(15, 98, 214, 0.05), transparent 34%),
    linear-gradient(180deg, #ffffff 0%, #fbfaf9 100%);
}

.ai-skeleton__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
}

.ai-skeleton__messages {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 12px 8px;
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

.sk-brand {
  width: 124px;
  height: 26px;
}

.sk-new-chat {
  width: 100%;
  height: 40px;
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
  border-radius: 28px;
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

@media (max-width: 960px) {
  .ai-skeleton {
    grid-template-columns: 1fr;
  }

  .ai-skeleton__rail {
    display: none;
  }

  .ai-skeleton__bubble {
    max-width: 100%;
  }
}
</style>
