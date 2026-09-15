<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sendAiChat } from '@/api/ai'
import { getActiveModules } from '@/api/admin'

const route = useRoute()

const panelVisible = ref(false)

const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const conversationId = ref('')
const chatBodyRef = ref(null)
const inputRef = ref(null)

// ========== 从数据库加载 AI 模块图标 ==========
const assistantIcon = ref('🤖')
const isSvg = (val) => typeof val === 'string' && val.trim().startsWith('<svg')
const loadIcon = async () => {
  try {
    const modules = await getActiveModules()
    if (Array.isArray(modules)) {
      const ai = modules.find(m => m.path && m.path.startsWith('/ai'))
      if (ai && ai.icon) assistantIcon.value = ai.icon
    }
  } catch (e) { /* 失败用默认 */ }
}

const welcomeMessage = {
  role: 'assistant',
  content: '你好，我是天气智能助手 🌤️\n\n我可以帮你：\n· 查询某地某年的天气指标（如：北京2020年降水量）\n· 查询天气指标排名（如：2020年平均气温最高的城市）\n· 推荐宜居城市（如：云南哪个城市最适合居住）\n\n请问有什么可以帮你？'
}

const isAuthPage = () => route.path === '/login' || route.path === '/register'
const showAssistant = ref(false)
watch(
  () => route.path,
  () => {
    showAssistant.value = !isAuthPage()
    if (!showAssistant.value) {
      panelVisible.value = false
    } else {
      loadIcon()
    }
  },
  { immediate: true }
)

const scrollToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

const togglePanel = () => {
  panelVisible.value = !panelVisible.value
  if (panelVisible.value) {
    if (messages.value.length === 0) {
      messages.value.push(welcomeMessage)
    }
    calcPanelPos()
    scrollToBottom()
    nextTick(() => inputRef.value?.focus())
  }
}

// ========== 面板位置：根据 fab 位置智能展开 ==========
const panelStyle = ref({})
const calcPanelPos = () => {
  const W = 380
  const H = 520
  const fabSize = 56
  const margin = 8
  // fab 当前位置（用 right/bottom 偏移换算 left/top）
  const fabRight = pos.value.x
  const fabBottom = pos.value.y
  const fabLeft = window.innerWidth - fabRight - fabSize
  const fabTop = window.innerHeight - fabBottom - fabSize

  // 水平：fab 在右半屏 → 面板右对齐 fab（向左展开）；否则左对齐 fab（向右展开）
  const inRightHalf = fabLeft + fabSize / 2 > window.innerWidth / 2
  // 垂直：fab 在下半屏 → 面板底对齐 fab（向上展开）；否则顶对齐 fab（向下展开）
  const inBottomHalf = fabTop + fabSize / 2 > window.innerHeight / 2

  const style = {}
  if (inRightHalf) {
    style.right = Math.max(margin, fabRight) + 'px'
    // 水平方向再校验：面板左边是否超界
    const leftEdge = window.innerWidth - fabRight - W
    if (leftEdge < margin) {
      style.right = 'auto'
      style.left = margin + 'px'
    }
  } else {
    style.left = Math.max(margin, fabLeft) + 'px'
    const rightEdge = fabLeft + W
    if (rightEdge > window.innerWidth - margin) {
      style.left = 'auto'
      style.right = margin + 'px'
    }
  }
  if (inBottomHalf) {
    style.bottom = Math.max(margin, fabBottom) + 'px'
    const topEdge = window.innerHeight - fabBottom - H
    if (topEdge < margin) {
      style.bottom = 'auto'
      style.top = margin + 'px'
    }
  } else {
    style.top = Math.max(margin, fabTop) + 'px'
    const bottomEdge = fabTop + H
    if (bottomEdge > window.innerHeight - margin) {
      style.top = 'auto'
      style.bottom = margin + 'px'
    }
  }
  panelStyle.value = style
}

const sendMessage = async () => {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const res = await sendAiChat(text, conversationId.value)
    conversationId.value = res.conversationId || ''
    messages.value.push({ role: 'assistant', content: res.reply || '抱歉，没有获取到回答。' })
  } catch (e) {
    console.error('AI 聊天请求失败:', e)
    ElMessage.error(e.message || '请求失败，请稍后重试')
    messages.value.push({ role: 'assistant', content: '抱歉，请求失败，请检查后端服务是否正常后重试。' })
  } finally {
    loading.value = false
    scrollToBottom()
    nextTick(() => inputRef.value?.focus())
  }
}

const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

const handleGlobalKeydown = (e) => {
  if (e.ctrlKey && e.code === 'Space') {
    e.preventDefault()
    if (showAssistant.value) {
      togglePanel()
    }
  }
}

// ========== 点击外部关闭 ==========
const rootRef = ref(null)
const handleDocClick = (e) => {
  if (!panelVisible.value) return
  if (rootRef.value && !rootRef.value.contains(e.target)) {
    panelVisible.value = false
  }
}

// ========== 拖动 ==========
const pos = ref({ x: 24, y: 24 })  // 相对 right/bottom 的偏移，初始 80,80
let dragging = false
let dragStartX = 0
let dragStartY = 0
let dragStartRight = 0
let dragStartBottom = 0
let moved = false

const onDragStart = (e) => {
  // 只响应左键
  if (e.button !== 0) return
  const el = rootRef.value
  if (!el) return
  dragging = true
  moved = false
  dragStartX = e.clientX
  dragStartY = e.clientY
  // 当前 right/bottom 距离
  const rect = el.getBoundingClientRect()
  dragStartRight = window.innerWidth - rect.right
  dragStartBottom = window.innerHeight - rect.bottom
  e.preventDefault()
}

const onDragMove = (e) => {
  if (!dragging) return
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) moved = true
  // 反向：向右拖 right 减小，向下拖 bottom 减小
  let newRight = dragStartRight - dx
  let newBottom = dragStartBottom - dy
  // 边界限制
  const el = rootRef.value
  const w = el ? el.offsetWidth : 56
  const h = el ? el.offsetHeight : 56
  newRight = Math.max(-w + 40, Math.min(window.innerWidth - 40, newRight))
  newBottom = Math.max(-h + 40, Math.min(window.innerHeight - 40, newBottom))
  pos.value = { x: newRight, y: newBottom }
}

const onDragEnd = () => {
  dragging = false
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown)
  document.addEventListener('click', handleDocClick, true)
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
  loadIcon()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  document.removeEventListener('click', handleDocClick, true)
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
})
</script>

<template>
  <div
    v-if="showAssistant"
    ref="rootRef"
    class="ai-assistant"
    :style="{ right: pos.x + 'px', bottom: pos.y + 'px' }"
  >
    <button
      v-if="!panelVisible"
      class="ai-fab"
      title="天气智能助手（Ctrl+Space，可拖动）"
      @click="!moved && togglePanel()"
      @mousedown="onDragStart"
    >
      <span v-if="isSvg(assistantIcon)" class="fab-svg" v-html="assistantIcon"></span>
      <span v-else>{{ assistantIcon }}</span>
    </button>

    <div v-else class="ai-panel" :style="panelStyle">
      <div class="ai-panel-header" @mousedown="onDragStart">
        <div class="ai-title">
          <span v-if="isSvg(assistantIcon)" class="ai-title-icon fab-svg" v-html="assistantIcon"></span>
          <span v-else class="ai-title-icon">{{ assistantIcon }}</span>
          <span>天气智能助手</span>
        </div>
        <button class="ai-close-btn" title="收起（Ctrl+Space）" @click.stop="togglePanel">✕</button>
      </div>

      <div ref="chatBodyRef" class="ai-chat-body">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="ai-msg-row"
          :class="msg.role === 'user' ? 'user' : 'assistant'"
        >
          <div class="ai-msg-bubble">{{ msg.content }}</div>
        </div>

        <div v-if="loading" class="ai-msg-row assistant">
          <div class="ai-msg-bubble ai-loading">
            <span class="ai-loading-dot"></span>
            <span class="ai-loading-dot"></span>
            <span class="ai-loading-dot"></span>
          </div>
        </div>
      </div>

      <div class="ai-panel-footer">
        <textarea
          ref="inputRef"
          v-model="inputText"
          class="ai-input"
          rows="1"
          placeholder="输入你的问题，Enter 发送"
          @keydown="handleKeydown"
        ></textarea>
        <button
          class="ai-send-btn"
          :disabled="loading || !inputText.trim()"
          @click="sendMessage"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-assistant {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 9999;
  font-family: 'PingFang SC', 'Segoe UI', Arial, sans-serif;
}

.ai-fab {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  font-size: 26px;
  cursor: grab;
  transition: box-shadow 0.3s ease, border-color 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
}

.ai-fab:hover {
  border-color: #3b82f6;
}

.fab-svg {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.fab-svg :deep(svg) {
  width: 28px;
  height: 28px;
}

.ai-panel {
  position: fixed;
  width: 380px;
  max-width: calc(100vw - 32px);
  height: 520px;
  max-height: calc(100vh - 48px);
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
  z-index: 10000;
}

.ai-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
  cursor: grab;
  user-select: none;
}

.ai-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.ai-title-icon {
  font-size: 18px;
  display: inline-flex;
  align-items: center;
}
.ai-title-icon :deep(svg) { width: 18px; height: 18px; }

.ai-close-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #6b7280;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.ai-close-btn:hover {
  background: #fef2f2;
  color: #ef4444;
}

.ai-chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  scroll-behavior: smooth;
}

.ai-msg-row {
  display: flex;
}

.ai-msg-row.user {
  justify-content: flex-end;
}

.ai-msg-row.assistant {
  justify-content: flex-start;
}

.ai-msg-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 4px;
  font-size: 13.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-msg-row.user .ai-msg-bubble {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #ffffff;
  border-bottom-right-radius: 4px;
}

.ai-msg-row.assistant .ai-msg-bubble {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  color: #1f2937;
  border-bottom-left-radius: 4px;
}

.ai-loading {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 16px;
}

.ai-loading-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #3b82f6;
  animation: ai-bounce 1.2s infinite ease-in-out;
}

.ai-loading-dot:nth-child(2) {
  animation-delay: 0.15s;
}

.ai-loading-dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes ai-bounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.ai-panel-footer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px;
  background: #f9fafb;
  border-top: 1px solid #e5e7eb;
}

.ai-input {
  flex: 1;
  min-height: 38px;
  max-height: 100px;
  padding: 9px 12px;
  border-radius: 10px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #1f2937;
  font-size: 13.5px;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
}

.ai-input:focus {
  border-color: #3b82f6;
}

.ai-input::placeholder {
  color: #9ca3af;
}

.ai-send-btn {
  height: 38px;
  padding: 0 18px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #ffffff;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.ai-send-btn:hover:not(:disabled) {
  opacity: 0.85;
}

.ai-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ai-chat-body::-webkit-scrollbar {
  width: 6px;
}

.ai-chat-body::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

@media (max-width: 480px) {
  .ai-assistant {
    right: 12px;
    bottom: 12px;
  }
  .ai-panel {
    width: calc(100vw - 24px);
    height: calc(100vh - 24px);
    max-height: none;
  }
}
</style>
