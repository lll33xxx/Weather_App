package com.example.nl.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统功能模块实体
 * 对应表 sys_module，用于管理首页展示的功能模块
 */
@Data
public class SysModule {
    /** 主键ID */
    private Long id;
    /** 模块唯一标识（如 weather、geo） */
    private String moduleKey;
    /** 模块标题 */
    private String title;
    /** 模块描述 */
    private String description;
    /** 图标（emoji） */
    private String icon;
    /** 路由路径 */
    private String path;
    /** 状态：1-启用，0-禁用 */
    private Integer status;
    /** 排序号（越小越靠前） */
    private Integer sortOrder;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 更新时间 */
    private LocalDateTime updateTime;
}
