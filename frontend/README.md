# 气象数据可视化分析平台 - 前端 (nl-f)

基于 Vue 3 + Vite + Pinia + Vue Router + Axios + ECharts + Element Plus 构建的气象数据可视化前端项目。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.x | 渐进式 JavaScript 框架（Composition API + `<script setup>`） |
| Vite | 5.4.x | 下一代前端构建工具 |
| Vue Router | 4.4.x | 官方路由管理器 |
| Pinia | 2.2.x | 新一代状态管理（Vue 官方推荐） |
| Axios | 1.7.x | HTTP 客户端 |
| ECharts | 5.5.x | 数据可视化图表库 |
| Element Plus | 2.8.x | Vue 3 组件库 |

## 项目结构

```
nl-f/
├── public/                  # 静态资源
├── src/
│   ├── api/
│   │   └── weather.js       # 气象数据 API 封装（axios 实例 + 5 个接口）
│   ├── assets/              # 资源文件
│   ├── components/          # 通用组件
│   ├── router/
│   │   └── index.js         # 路由配置（首页 + 气象仪表盘）
│   ├── stores/
│   │   └── weather.js       # Pinia 状态管理（指标/年份/城市/排名/趋势数据）
│   ├── views/
│   │   ├── Home.vue         # 首页 - 功能模块导航
│   │   └── weather/
│   │       └── WeatherDashboard.vue  # 气象数据可视化仪表盘
│   ├── App.vue              # 根组件
│   ├── main.js              # 应用入口
│   └── style.css            # 全局样式（深色主题 + Element Plus 覆盖）
├── index.html
├── package.json
├── vite.config.js           # Vite 配置（含 /api 代理到后端 8080）
└── .gitignore
```

## 快速开始

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

启动后访问 http://localhost:5173

> 注意：需要先启动后端服务（Spring Boot，端口 8080），并确保 MySQL 数据库 `weather_ads` 可访问。
> Vite 已配置 `/api` 代理到 `http://localhost:8080`。

### 构建生产版本

```bash
npm run build
```

### 预览生产构建

```bash
npm run preview
```

## 后端 API 接口

基础路径：`/api/weather`（Vite 代理至后端 8080）

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/metrics` | - | 获取所有指标元信息 |
| GET | `/years` | metric | 获取指定指标的可用年份 |
| GET | `/cities` | metric | 获取指定指标的可用城市 |
| GET | `/ranking` | metric, year, limit | 查询城市排名 |
| GET | `/trend` | metric, city | 查询城市历年趋势 |

### 气象指标

| 编码 | 名称 | 显示全称 | 单位 | 天数 |
|------|------|----------|------|------|
| rain | 降水 | 年总降水量 | mm | 降雨天数 |
| wind | 风速 | 年平均风速 | m/s | 大风天数 |
| temp | 气温 | 年平均气温 | ℃ | 高温天数 |
| sun | 日照 | 年总日照时长 | h | 晴好天数 |
| humidity | 湿度 | 年平均相对湿度 | % | 高湿天数 |

## 新增功能模块指南

1. 在 `src/views/` 下创建新的页面组件
2. 在 `src/router/index.js` 中添加路由配置
3. 在 `src/views/Home.vue` 的 `modules` 数组中添加功能卡片
4. 如需共享状态，在 `src/stores/` 下新建 Pinia store
5. 如需 API 接口，在 `src/api/` 下新建或扩展封装

## 后端项目

后端代码位于同级目录 `../nl/`，基于 Spring Boot 3.2.12 + Java 21 + MyBatis + MySQL。
