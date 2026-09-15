import request from '@/api/weather'

// ========== AI 智能体聊天 API ==========

/**
 * 发送消息给 Coze 智能体并获取回复
 * @param {string} message - 用户输入的自然语言消息
 * @param {string} [conversationId] - Coze 会话ID（多轮对话时回传保持上下文，首轮可省略）
 * @returns {Promise<Object>} { reply, conversationId }
 */
export const sendAiChat = (message, conversationId) => {
  return request.post('/ai/chat', { message, conversationId })
}
