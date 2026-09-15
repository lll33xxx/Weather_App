package com.example.nl.dto;

import lombok.Data;

/**
 * AI 聊天请求DTO - 前端调用 /api/ai/chat 时传入用户消息与会话标识
 */
@Data
public class AiChatRequest {

    /** 用户输入的自然语言消息 */
    private String message;

    /** Coze 会话ID（多轮对话时回传，保持上下文；首轮可为空） */
    private String conversationId;
}
