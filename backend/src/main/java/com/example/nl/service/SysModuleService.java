package com.example.nl.service;

import com.example.nl.entity.SysModule;

import java.util.List;

/**
 * 系统模块业务层接口
 */
public interface SysModuleService {

    /**
     * 查询所有模块
     */
    List<SysModule> listAll();

    /**
     * 查询所有启用的模块（供首页展示）
     */
    List<SysModule> listActive();

    /**
     * 新增模块
     */
    SysModule create(SysModule module);

    /**
     * 更新模块信息
     */
    boolean update(SysModule module);

    /**
     * 启用/禁用模块
     */
    boolean updateStatus(Long id, Integer status);

    /**
     * 删除模块
     */
    boolean delete(Long id);
}
