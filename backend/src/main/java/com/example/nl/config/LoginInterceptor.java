package com.example.nl.config;

import com.example.nl.controller.AuthController;
import com.example.nl.vo.UserVO;
// [技术栈] Jackson — JSON 序列化/反序列化库，Spring Boot 默认集成
import com.fasterxml.jackson.databind.ObjectMapper;
// [技术栈] Jakarta Servlet API — Servlet 标准接口（Spring Boot 3 使用 jakarta 命名空间，替代旧的 javax）
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
// [技术栈] Spring Core — @Component 将类注册为 Spring 容器管理的 Bean
import org.springframework.stereotype.Component;
// [技术栈] Spring MVC — HandlerInterceptor 拦截器接口，可在请求处理前后插入逻辑
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录拦截器
 * 检查 Session 中是否存在已登录用户，未登录则返回 401
 *
 * 【整体技术栈】
 * - Spring MVC HandlerInterceptor：请求拦截机制
 * - Jakarta Servlet HttpSession：服务端会话存储
 * - Jackson ObjectMapper：构造 JSON 响应体
 * - Spring @Component：交由 Spring 容器管理，供 WebConfig 注入
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    // [技术栈] Jackson — 用于将 Map 序列化为 JSON 字符串写入响应
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [技术栈] Spring MVC — preHandle 是拦截器生命周期方法，
     * 在 Controller 方法执行之前调用。返回 true 放行，返回 false 中断请求。
     *
     * @param request  [技术栈] Jakarta Servlet — HTTP 请求对象，获取请求方法、Session
     * @param response [技术栈] Jakarta Servlet — HTTP 响应对象，设置状态码、写入响应体
     * @param handler  [技术栈] Spring MVC — 目标处理器（通常是 Controller 方法）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // [技术栈] CORS 跨域 — OPTIONS 是浏览器跨域预检请求，不带 Session，必须放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // [技术栈] Jakarta Servlet — getSession(false)：有 Session 则返回，没有则返回 null（不创建新的）
        HttpSession session = request.getSession(false);
        if (session != null) {
            // [技术栈] Jakarta Servlet — 从 Session 中取出登录时存入的用户对象
            // Session key 常量定义在 AuthController 中，登录成功后 setAttribute 存入
            UserVO user = (UserVO) session.getAttribute(AuthController.SESSION_USER_KEY);
            if (user != null) {
                return true;
            }
        }

        // [技术栈] Jakarta Servlet — 设置 HTTP 状态码 401（未授权）
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // [技术栈] Jakarta Servlet — 设置响应内容类型为 JSON
        response.setContentType("application/json;charset=UTF-8");
        // [技术栈] Jackson — 构造响应体 Map，后续序列化为 JSON
        Map<String, Object> body = new HashMap<>();
        body.put("code", 401);
        body.put("message", "未登录或登录已过期，请先登录");
        body.put("data", null);
        // [技术栈] Jackson + Jakarta Servlet — ObjectMapper 将 Map 转 JSON 字符串，通过 response.getWriter() 写入响应
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
