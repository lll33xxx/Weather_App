package com.example.nl.controller;

import com.example.nl.dto.AiChatRequest;
import com.example.nl.service.OllamaChatService;
import com.example.nl.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 智能体聊天接口控制器
 * 基础路径: /api/ai
 * 前端用户调用（走登录拦截器），后端转发到本地 Ollama 大模型并返回回复
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiChatController {

    @Autowired
    private OllamaChatService ollamaChatService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** Redis 中浏览器定位的 key */
    private static final String LOCATION_KEY = "weather:user:location";

    /**
     * 存储浏览器定位信息
     * POST /api/ai/location
     * Body: { "lon": 119.30, "lat": 26.08, "city": "福州" }
     */
    @PostMapping("/location")
    public Result<Map<String, String>> saveLocation(@RequestBody Map<String, Object> body) {
        try {
            double lon = Double.parseDouble(body.get("lon").toString());
            double lat = Double.parseDouble(body.get("lat").toString());
            String city = body.getOrDefault("city", "").toString();
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("lon", lon);
            location.put("lat", lat);
            location.put("city", city);
            // 缓存 24 小时
            redisTemplate.opsForValue().set(LOCATION_KEY, location, 24, TimeUnit.HOURS);
            return Result.success(Map.of("message", "定位已保存"));
        } catch (Exception e) {
            return Result.error(400, "定位信息格式错误");
        }
    }

    /**
     * 发送消息给本地大模型并获取回复
     * POST /api/ai/chat
     * Body: { "message": "北京2020年天气怎么样？", "conversationId": "" }
     */
    @PostMapping("/chat")
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
