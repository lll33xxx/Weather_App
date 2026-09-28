<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sendAiChat, saveAiLocation } from '@/api/ai'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

// ========== 聊天状态 ==========
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const conversationId = ref('')
const chatBodyRef = ref(null)
const inputRef = ref(null)

// 欢迎消息
const welcomeMessage = {
  role: 'assistant',
  content: '你好，我是天气智能助手 🌤️\n\n我可以帮你：\n· 查询城市天气指标（气温、降水、湿度、风速、日照）\n· 查询天气指标城市排名\n· 推荐宜居城市 / 最不宜居城市\n· 查询某一天的详细天气\n· 分析历年天气变化趋势\n· 分析月内逐日天气变化\n\n点击下方快捷问题试试吧！'
}

// 快捷问题
const quickQuestions = [
  '北京2020年的降水量是多少？',
  '2020年平均气温最高的城市排名',
  '云南省哪个城市最适合居住？',
  '福建哪个城市最不适合居住？',
  '2020年7月15日泉州天气如何？',
  '北京2000-2020年气温变化趋势',
  '分析2020年1月泉州风速变化'
]

// ========== 滚动到底部 ==========
const scrollToBottom = async () => {
  await nextTick()
  if (chatBodyRef.value) {
    chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight
  }
}

// ========== 发送消息 ==========
const sendMessage = async (text) => {
  const msg = (text || inputText.value).trim()
  if (!msg || loading.value) return

  messages.value.push({ role: 'user', content: msg })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  try {
    const res = await sendAiChat(msg, conversationId.value)
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

const handleQuickQuestion = (q) => {
  sendMessage(q)
}

onMounted(() => {
  messages.value.push(welcomeMessage)
  // 从 localStorage 读取浏览器定位并上报后端，供 AI 在未指定城市时使用
  try {
    const cached = localStorage.getItem('nl_weather_location')
    if (cached) {
      const { lon, lat } = JSON.parse(cached)
      saveAiLocation(lon, lat, '').catch(() => {})
    }
  } catch (e) { /* 忽略定位上报失败 */ }
})
</script>

<template>
  <div class="ai-page">
    <!-- 快捷问题 -->
    <div class="quick-section">
      <div class="quick-label">试试这些问题：</div>
      <div class="quick-list">
        <button
          v-for="q in quickQuestions"
          :key="q"
          class="quick-btn"
          :disabled="loading"
          @click="handleQuickQuestion(q)"
        >
          {{ q }}
        </button>
      </div>
    </div>

    <!-- 聊天主区域 -->
    <div class="chat-container">
      <div ref="chatBodyRef" class="chat-body">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          class="msg-row"
          :class="msg.role"
        >
          <div class="msg-avatar">
            <img v-if="msg.role === 'user' && authStore.user?.avatar" :src="authStore.user.avatar" alt="头像" />
            <span v-else>{{ msg.role === 'user' ? '👤' : '🤖' }}</span>
          </div>
          <div class="msg-bubble">{{ msg.content }}</div>
        </div>

        <!-- 加载中 -->
        <div v-if="loading" class="msg-row assistant">
          <div class="msg-avatar">🤖</div>
          <div class="msg-bubble msg-loading">
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
            <span class="loading-dot"></span>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-footer">
        <textarea
          ref="inputRef"
          v-model="inputText"
          class="chat-input"
          rows="1"
          placeholder="输入你的问题，Enter 发送，Shift+Enter 换行"
          @keydown="handleKeydown"
        ></textarea>
        <button
          class="send-btn"
          :disabled="loading || !inputText.trim()"
          @click="sendMessage()"
        >
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-page {
  height: calc(100vh - 56px);
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

/* 快捷问题 */
.quick-section {
  margin-bottom: 12px;
}

.quick-label {
  font-size: 13px;
  color: #9ca3af;
  margin-bottom: 8px;
}

.quick-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-btn {
  padding: 6px 14px;
  border: 1px solid #bfdbfe;
  border-radius: 20px;
  background: #eff6ff;
  color: #3b82f6;
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.2s;
}

.quick-btn:hover:not(:disabled) {
  background: #dbeafe;
  border-color: #3b82f6;
}

.quick-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 聊天容器 */
.chat-container {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.chat-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.msg-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.msg-row.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.msg-avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.msg-bubble {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-row.user .msg-bubble {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #ffffff;
  border-top-right-radius: 4px;
}

.msg-row.assistant .msg-bubble {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  color: #1f2937;
  border-top-left-radius: 4px;
}

/* 加载动画 */
.msg-loading {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 14px 18px;
}

.loading-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #3b82f6;
  animation: bounce 1.2s infinite ease-in-out;
}

.loading-dot:nth-child(2) {
  animation-delay: 0.15s;
}

.loading-dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes bounce {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-5px);
    opacity: 1;
  }
}

/* 输入区 */
.chat-footer {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 14px 16px;
  background: #ffffff;
  border-top: 1px solid #e5e7eb;
}

.chat-input {
  flex: 1;
  min-height: 42px;
  max-height: 120px;
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #1f2937;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
  font-family: inherit;
}

.chat-input:focus {
  border-color: #3b82f6;
}

.chat-input::placeholder {
  color: #9ca3af;
}

.send-btn {
  height: 42px;
  padding: 0 24px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.send-btn:hover:not(:disabled) {
  opacity: 0.85;
}

.send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 滚动条 */
.chat-body::-webkit-scrollbar {
  width: 6px;
}

.chat-body::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

@media (max-width: 768px) {
  .ai-page {
    padding: 12px;
  }
  .msg-bubble {
    max-width: 80%;
  }
}
</style>
