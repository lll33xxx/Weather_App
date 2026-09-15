-- ============================================================
-- 系统功能模块表 sys_module 建表与初始化数据脚本
-- 数据库：weather_ads
-- 执行节点：node1（MySQL 192.168.88.151:3306）
-- ============================================================

-- 1. 创建系统模块表
CREATE TABLE IF NOT EXISTS sys_module (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    module_key  VARCHAR(50)  NOT NULL COMMENT '模块唯一标识',
    title       VARCHAR(100) NOT NULL COMMENT '模块标题',
    description VARCHAR(500) DEFAULT NULL COMMENT '模块描述',
    icon        VARCHAR(20)  DEFAULT NULL COMMENT '图标（emoji）',
    path        VARCHAR(100) DEFAULT NULL COMMENT '路由路径',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序号（越小越靠前）',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_key (module_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统功能模块表';

-- 2. 初始化首页两个功能模块
INSERT INTO sys_module (module_key, title, description, icon, path, status, sort_order) VALUES
    ('weather', '气象数据可视化', '全国城市降水、风速、气温、日照、湿度五大指标的排名与历年趋势分析', '🌤️', '/weather', 1, 1),
    ('geo', '地图可视化看板', '中国 → 省份 → 城市三级下钻地图，结合气象指标热力着色，点击查看城市详细数据与趋势', '🗺️', '/geo', 1, 2);

-- 3. 验证数据
SELECT id, module_key, title, status, sort_order, path FROM sys_module ORDER BY sort_order;
