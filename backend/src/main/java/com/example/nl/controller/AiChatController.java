package com.example.nl.controller;

import com.example.nl.dto.AiChatRequest;
import com.example.nl.service.OllamaChatService;
import com.example.nl.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 智能体聊天接口控制器
 * 基础路径: /api/ai/chat
 * 前端用户调用（走登录拦截器），后端转发到本地 Ollama 大模型并返回回复
 */
@RestController
@RequestMapping("/api/ai/chat")
@CrossOrigin(origins = "*")
public class AiChatController {

    @Autowired
    private OllamaChatService ollamaChatService;

    /**
     * 发送消息给本地大模型并获取回复
     * POST /api/ai/chat
     * Body: { "message": "北京2020年天气怎么样？", "conversationId": "" }
     */
    @PostMapping
    public Result<Map<String, String>> chat(@RequestBody AiChatRequest req, HttpServletRequest request) {
        try {
            if (req.getMessage() == null || req.getMessage().trim().isEmpty()) {
                return Result.error(400, "消息内容不能为空");
            }
            // 从 Session 获取登录用户名作为标识
            HttpSession session = request.getSession(false);
            String userId = "anonymous";
            if (session != null) {
                UserVO user = (UserVO) session.getAttribute(AuthController.SESSION_USER_KEY);
                if (user != null) {
                    userId = user.getUsername();
                }
            }
            Map<String, String> result = ollamaChatService.chat(
                    userId, req.getMessage().trim(), req.getConversationId());
            return Result.success(result);
        } catch (IllegalStateException e) {
            return Result.error(500, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
