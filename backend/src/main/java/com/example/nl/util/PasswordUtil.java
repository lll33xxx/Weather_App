package com.example.nl.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码加密工具（SHA-256 + 固定盐）
 * 不引入额外依赖，使用 JDK 自带 MessageDigest
 */
public class PasswordUtil {

    /** 固定盐值（生产环境应使用随机盐并单独存储，此处为实训项目简化） */
    private static final String SALT = "nl_weather_2026";

    /**
     * 对明文密码进行 SHA-256 加密
     */
    public static String encrypt(String rawPassword) {
        if (rawPassword == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((SALT + rawPassword).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * 校验明文密码与加密后的密码是否匹配
     */
    public static boolean matches(String rawPassword, String encryptedPassword) {
        if (rawPassword == null || encryptedPassword == null) {
            return false;
        }
        return encrypt(rawPassword).equals(encryptedPassword);
    }
}
