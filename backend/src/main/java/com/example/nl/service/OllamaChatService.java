package com.example.nl.service;

import java.util.Map;

/**
 * Ollama 本地大模型对话服务接口
 * 通过 Function Calling 自动调用天气数据查询接口
 */
public interface OllamaChatService {

    /**
     * 发送用户消息，通过本地大模型处理并返回回答
     *
     * @param userId         用户标识
     * @param message        用户消息
     * @param conversationId 会话ID（多轮对话，首轮可为空）
     * @return { reply: 回答内容, conversationId: 会话ID }
     */
    Map<String, String> chat(String userId, String message, String conversationId);
}
