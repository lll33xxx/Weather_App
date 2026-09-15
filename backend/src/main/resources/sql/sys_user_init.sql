-- ============================================================
-- 系统用户表 sys_user 建表与测试数据脚本
-- 数据库：weather_ads（与现有气象数仓共用一个库）
-- 执行节点：node1（MySQL 主节点，192.168.88.151:3306）
-- 密码加密方式：SHA-256(盐 + 明文密码)，盐 = nl_weather_2026
-- ============================================================

-- 1. 创建用户表（如果不存在）
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username    VARCHAR(50)  NOT NULL COMMENT '用户名（唯一）',
    password    VARCHAR(100) NOT NULL COMMENT '密码（SHA-256加密）',
    role        VARCHAR(20)  NOT NULL DEFAULT 'user' COMMENT '角色：user-普通用户，admin-管理员',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 2. 插入测试用户（管理员不可通过注册接口创建，必须直接SQL插入）
--    管理员：admin / admin123
--    普通用户：user / user123
--    普通用户：test / test123
INSERT INTO sys_user (username, password, role) VALUES
    ('admin', '21aadb3522e6be2b3f32d534c5efe2fabe559bb8376ebb3fff85469f0143b5bc', 'admin'),
    ('user',  'ab47a2c5bc94c4efdea4777b5883385c50cf74b3bec36bb6c7e47bc0c0afd68f', 'user'),
    ('test',  'c8f31d95a892c4a1c58380cb1c348d9daddae566d2a22706660f733702645d9c', 'user');

-- 3. 验证数据
SELECT id, username, role, create_time FROM sys_user;
