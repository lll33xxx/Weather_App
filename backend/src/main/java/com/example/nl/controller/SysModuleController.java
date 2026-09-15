package com.example.nl.controller;

import com.example.nl.entity.SysModule;
import com.example.nl.service.SysModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统模块管理控制器
 * 基础路径: /api/admin/modules
 * 同时提供公开接口供首页获取启用的模块列表
 */
@RestController
@CrossOrigin(origins = "*")
public class SysModuleController {

    @Autowired
    private SysModuleService sysModuleService;

    // ========== 公开接口：首页获取启用的模块列表 ==========

    /**
     * 获取所有启用的模块（首页展示用，无需管理员权限）
     * GET /api/modules/active
     */
    @GetMapping("/api/modules/active")
    public Result<List<SysModule>> getActiveModules() {
        try {
            return Result.success(sysModuleService.listActive());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    // ========== 管理员接口：模块CRUD ==========

    /**
     * 查询所有模块
     * GET /api/admin/modules
     */
    @GetMapping("/api/admin/modules")
    public Result<List<SysModule>> list() {
        try {
            return Result.success(sysModuleService.listAll());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 新增模块
     * POST /api/admin/modules
     */
    @PostMapping("/api/admin/modules")
    public Result<SysModule> create(@RequestBody SysModule module) {
        try {
            return Result.success(sysModuleService.create(module));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 更新模块信息
     * PUT /api/admin/modules/{id}
     */
    @PutMapping("/api/admin/modules/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysModule module) {
        try {
            module.setId(id);
            sysModuleService.update(module);
            return Result.success(null);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 启用/禁用模块
     * PUT /api/admin/modules/{id}/status
     * Body: {"status": 0} 或 {"status": 1}
     */
    @PutMapping("/api/admin/modules/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        try {
            sysModuleService.updateStatus(id, body.get("status"));
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 删除模块
     * DELETE /api/admin/modules/{id}
     */
    @DeleteMapping("/api/admin/modules/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysModuleService.delete(id);
            return Result.success(null);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
