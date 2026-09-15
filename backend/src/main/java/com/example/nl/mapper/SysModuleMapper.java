package com.example.nl.mapper;

import com.example.nl.entity.SysModule;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 系统模块 Mapper
 */
@Mapper
public interface SysModuleMapper {

    /**
     * 查询所有模块（按排序号升序）
     */
    @Select("SELECT id, module_key AS moduleKey, title, description, icon, path, " +
            "status, sort_order AS sortOrder, create_time AS createTime, update_time AS updateTime " +
            "FROM sys_module ORDER BY sort_order ASC, id ASC")
    List<SysModule> selectAll();

    /**
     * 查询所有启用的模块
     */
    @Select("SELECT id, module_key AS moduleKey, title, description, icon, path, " +
            "status, sort_order AS sortOrder, create_time AS createTime, update_time AS updateTime " +
            "FROM sys_module WHERE status = 1 ORDER BY sort_order ASC, id ASC")
    List<SysModule> selectActive();

    /**
     * 根据ID查询
     */
    @Select("SELECT id, module_key AS moduleKey, title, description, icon, path, " +
            "status, sort_order AS sortOrder, create_time AS createTime, update_time AS updateTime " +
            "FROM sys_module WHERE id = #{id}")
    SysModule selectById(@Param("id") Long id);

    /**
     * 根据 moduleKey 查询
     */
    @Select("SELECT id, module_key AS moduleKey, title, description, icon, path, " +
            "status, sort_order AS sortOrder, create_time AS createTime, update_time AS updateTime " +
            "FROM sys_module WHERE module_key = #{moduleKey}")
    SysModule selectByKey(@Param("moduleKey") String moduleKey);

    /**
     * 新增模块
     */
    @Insert("INSERT INTO sys_module (module_key, title, description, icon, path, status, sort_order) " +
            "VALUES (#{moduleKey}, #{title}, #{description}, #{icon}, #{path}, #{status}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysModule module);

    /**
     * 更新模块信息
     */
    @Update("UPDATE sys_module SET title = #{title}, description = #{description}, " +
            "icon = #{icon}, path = #{path}, sort_order = #{sortOrder}, update_time = NOW() " +
            "WHERE id = #{id}")
    int update(SysModule module);

    /**
     * 更新模块状态（启用/禁用）
     */
    @Update("UPDATE sys_module SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 删除模块
     */
    @Delete("DELETE FROM sys_module WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
