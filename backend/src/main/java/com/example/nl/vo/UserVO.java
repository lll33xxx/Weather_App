package com.example.nl.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息VO（返回给前端，不包含密码）
 */
@Data
public class UserVO {
    private Long id;
    private String username;
    /** 角色：user-普通用户，admin-管理员 */
    private String role;
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    private String avatar;
}
