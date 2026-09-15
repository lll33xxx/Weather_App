package com.example.nl.service.impl;

import com.example.nl.entity.SysModule;
import com.example.nl.mapper.SysModuleMapper;
import com.example.nl.service.SysModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统模块业务层实现
 */
@Service
public class SysModuleServiceImpl implements SysModuleService {

    @Autowired
    private SysModuleMapper sysModuleMapper;

    /**
     * 查询所有模块
     */
    @Cacheable(value = "module:all")
    @Override
    public List<SysModule> listAll() {
        return sysModuleMapper.selectAll();
    }

    /**
     * 查询所有启用的模块（供首页展示）
     */
    @Cacheable(value = "module:active")
    @Override
    public List<SysModule> listActive() {
        return sysModuleMapper.selectActive();
    }

    /**
     * 新增模块
     */
    @CacheEvict(value = {"module:all", "module:active"}, allEntries = true)
    @Override
    public SysModule create(SysModule module) {
        if (module.getModuleKey() == null || module.getModuleKey().isBlank()) {
            throw new IllegalArgumentException("模块标识不能为空");
        }
        if (module.getTitle() == null || module.getTitle().isBlank()) {
            throw new IllegalArgumentException("模块标题不能为空");
        }
        // 检查 moduleKey 是否已存在
        SysModule existing = sysModuleMapper.selectByKey(module.getModuleKey().trim());
        if (existing != null) {
            throw new IllegalArgumentException("模块标识已存在");
        }
        if (module.getStatus() == null) module.setStatus(1);
        if (module.getSortOrder() == null) module.setSortOrder(0);
        sysModuleMapper.insert(module);
        return module;
    }

    /**
     * 更新模块信息
     */
    @CacheEvict(value = {"module:all", "module:active"}, allEntries = true)
    @Override
    public boolean update(SysModule module) {
        if (module.getId() == null) return false;
        return sysModuleMapper.update(module) > 0;
    }

    /**
     * 启用/禁用模块
     */
    @CacheEvict(value = {"module:all", "module:active"}, allEntries = true)
    @Override
    public boolean updateStatus(Long id, Integer status) {
        if (id == null || status == null) return false;
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("状态只能是 0(禁用) 或 1(启用)");
        }
        return sysModuleMapper.updateStatus(id, status) > 0;
    }

    /**
     * 删除模块
     */
    @CacheEvict(value = {"module:all", "module:active"}, allEntries = true)
    @Override
    public boolean delete(Long id) {
        if (id == null) return false;
        return sysModuleMapper.deleteById(id) > 0;
    }
}
