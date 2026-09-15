-- ============================================================
-- 新增 AI 智能助手模块到 sys_module 表
-- 数据库：weather_ads
-- 执行方式（务必加 --default-character-set=utf8mb4，否则 emoji 会乱码）：
--   mysql -u root -p --default-character-set=utf8mb4 weather_ads < sys_module_ai.sql
-- ============================================================

SET NAMES utf8mb4;

-- 如果之前插入过乱码数据，先删除旧记录
DELETE FROM sys_module WHERE module_key = 'ai';

-- 插入 AI 智能助手模块
INSERT INTO sys_module (module_key, title, description, icon, path, status, sort_order) VALUES
('ai', '天气智能助手', '基于 Coze 智能体的自然语言问答，支持天气指标咨询、指标排名查询与宜居城市推荐', '🤖', '/ai', 1, 3);

-- 验证：查询所有模块
SELECT id, module_key, title, icon, status, sort_order, path FROM sys_module ORDER BY sort_order;
