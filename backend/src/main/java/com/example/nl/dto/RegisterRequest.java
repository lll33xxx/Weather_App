package com.example.nl.dto;

import lombok.Data;

/**
 * 注册请求参数
 */
@Data
public class RegisterRequest {
    private String username;
    private String password;
}
