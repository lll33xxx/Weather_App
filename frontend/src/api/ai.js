import request from '@/api/weather'

// ========== AI 智能体聊天 API ==========

/**
 * 发送消息给 AI 智能体并获取回复
 * @param {string} message - 用户输入的自然语言消息
 * @param {string} [conversationId] - 会话ID（多轮对话时回传保持上下文，首轮可省略）
 * @returns {Promise<Object>} { reply, conversationId }
 */
export const sendAiChat = (message, conversationId) => {
  return request.post('/ai/chat', { message, conversationId })
}

/**
 * 上报浏览器定位到后端 Redis，供 AI 对话在用户未指定城市时使用
 * @param {number} lon - 经度
 * @param {number} lat - 纬度
 * @param {string} city - 城市名
 */
export const saveAiLocation = (lon, lat, city) => {
  return request.post('/ai/location', { lon, lat, city })
}
