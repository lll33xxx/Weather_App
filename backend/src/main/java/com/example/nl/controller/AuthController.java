package com.example.nl.controller;

import com.example.nl.dto.LoginRequest;
import com.example.nl.dto.RegisterRequest;
import com.example.nl.service.UserService;
import com.example.nl.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 - 登录、注册、登出、获取当前用户
 * 基础路径: /api/auth
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    /** Session 中存储当前登录用户的 key */
    public static final String SESSION_USER_KEY = "currentUser";

    @Autowired
    private UserService userService;

    /**
     * 登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<UserVO> login(@RequestBody LoginRequest req, HttpServletRequest request) {
        try {
            UserVO user = userService.login(req.getUsername(), req.getPassword());
            if (user == null) {
                return Result.error(401, "用户名或密码错误");
            }
            // 登录成功，将用户信息存入 Session
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_USER_KEY, user);
            return Result.success(user);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 注册（仅普通用户）
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody RegisterRequest req) {
        try {
            UserVO user = userService.register(req.getUsername(), req.getPassword());
            return Result.success(user);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 登出
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Result.success(null);
    }

    /**
     * 获取当前登录用户信息
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public Result<UserVO> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Result.error(401, "未登录");
        }
        UserVO user = (UserVO) session.getAttribute(SESSION_USER_KEY);
        if (user == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(user);
    }
    @PostMapping("/profile/username")
    public Result<UserVO> updateUsername(@RequestBody java.util.Map<String, String> body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        UserVO user = (UserVO) session.getAttribute(SESSION_USER_KEY);
        if (user == null) return Result.error(401, "未登录");
        try {
            UserVO updated = userService.updateUsername(user.getId(), body.get("username"));
            session.setAttribute(SESSION_USER_KEY, updated);
            return Result.success(updated);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/profile/password")
    public Result<Void> changePassword(@RequestBody java.util.Map<String, String> body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        UserVO user = (UserVO) session.getAttribute(SESSION_USER_KEY);
        if (user == null) return Result.error(401, "未登录");
        try {
            userService.changePassword(user.getId(), body.get("oldPassword"), body.get("newPassword"));
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
    @PostMapping("/profile/avatar")
    public Result<UserVO> uploadAvatar(@RequestParam("file") org.springframework.web.multipart.MultipartFile file, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        UserVO user = (UserVO) session.getAttribute(SESSION_USER_KEY);
        if (user == null) return Result.error(401, "未登录");
        try {
            String original = file.getOriginalFilename();
            String ext = original != null && original.contains(".") ? original.substring(original.lastIndexOf(".")) : ".png";
            String fileName = "avatar_" + user.getId() + "_" + System.currentTimeMillis() + ext;
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads/avatars");
            java.nio.file.Files.createDirectories(uploadDir);
            java.nio.file.Path dest = uploadDir.resolve(fileName);
            file.transferTo(dest.toAbsolutePath().toFile());
            String url = "/uploads/avatars/" + fileName;
            UserVO updated = userService.updateAvatar(user.getId(), url);
            session.setAttribute(SESSION_USER_KEY, updated);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
