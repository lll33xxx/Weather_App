package com.example.nl.controller;

import com.example.nl.service.UserService;
import com.example.nl.vo.PageResult;
import com.example.nl.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员 - 用户管理控制器
 * 基础路径: /api/admin/users
 * 需登录且为管理员（由拦截器 + 前端路由守卫双重控制）
 */
@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
public class AdminUserController {

    @Autowired
    private UserService userService;

    /**
     * 分页查询用户列表
     * GET /api/admin/users?pageNum=1&pageSize=10&keyword=xxx
     */
    @GetMapping
    public Result<PageResult<UserVO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        try {
            return Result.success(userService.getUserPage(pageNum, pageSize, keyword));
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 修改用户角色
     * PUT /api/admin/users/{id}/role
     * Body: {"role": "admin"}
     */
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String role = body.get("role");
            userService.updateRole(id, role);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 删除用户
     * DELETE /api/admin/users/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return Result.success(null);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
