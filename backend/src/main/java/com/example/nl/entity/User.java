package com.example.nl.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体
 * 对应表 sys_user
 */
@Data
public class User {
    /** 主键ID */
    private Long id;
    /** 用户名（唯一） */
    private String username;
    /** 密码（SHA-256 加密存储） */
    private String password;
    /** 角色：user-普通用户，admin-管理员 */
    private String role;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    private String avatar;
}
