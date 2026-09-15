# 气象数据可视化分析平台

一个前后端分离的气象数据可视化分析 Web 应用：整合和风天气实时/预报数据、2000–2020 年历史气象数据与本地大模型问答，提供实时大屏、历史趋势排名、全国地图下钻、AI 智能问答与后台管理等功能。

---

## 一、技术栈

### 后端 `backend/`

| 分类 | 技术 |
|---|---|
| 框架 | Spring Boot 3.2.12（Java 21） |
| 持久层 | MyBatis 3.0.3 + PageHelper 2.1.0 |
| 数据库 | MySQL（库名 `weather_ads`） |
| 缓存 | Redis（Spring Data Redis / Lettuce） |
| 接口风格 | RESTful，统一返回体 `Result<T>`，Session 登录 |
| 外部集成 | 和风天气 QWeather API、本地 Ollama（OpenAI 兼容接口） |
| 其他 | Lombok、RestTemplate、Spring Cache |

### 前端 `frontend/`

| 分类 | 技术 |
|---|---|
| 框架 | Vue 3（Composition API，`<script setup>`） |
| 构建 | Vite 5 |
| 状态管理 | Pinia |
| UI | Element Plus |
| 图表 | ECharts 5（含地图/散点/折线/柱图等） |
| 路由 | Vue Router 4 |
| HTTP | Axios（统一实例 + 拦截器） |

---

## 二、功能模块

- **用户认证**：注册 / 登录 / 登出、Session 鉴权、登录拦截器；个人中心修改用户名、密码、上传头像。
- **实时天气大屏（首页）**：实时天气、24 小时逐小时、7 天预报、预警、空气质量、生活指数聚合卡片，以及主要城市地图标注。
- **历史数据可视化**：多指标（降水/风速/气温/日照/湿度）城市排名、历年趋势、年份与城市筛选。
- **地图看板**：按省聚合 → 省 → 市三级下钻，地图 + 统计图表联动。
- **AI 智能问答**：基于 Ollama 本地大模型（`qwen2.5:7b`）的 Function Calling，可查询单城汇总、指标排名、宜居/不宜居城市推荐，会话历史走 Redis。
- **后台管理（仅 admin）**：用户管理（分页/改角色/删除）、数据管理（分页/多条件筛选）、系统配置（首页模块上下架）。

---

## 三、目录结构

```
项目根/
├── backend/                    # 后端 Spring Boot
│   └── src/main/
│       ├── java/com/example/nl/
│       │   ├── controller/     # REST 接口
│       │   ├── service/        # 业务接口 + impl 实现
│       │   ├── mapper/        # MyBatis Mapper
│       │   ├── entity/ vo/ dto/# 实体 / 视图对象 / 请求对象
│       │   ├── config/         # Web、Redis、拦截器、RestTemplate、属性
│       │   ├── enums/ util/    # 枚举与工具
│       │   └── NlApplication.java
│       └── resources/
│           ├── application.properties（本地，已 gitignore）
│           ├── application.properties.example（模板）
│           └── sql/            # 建表与初始化 SQL
└── frontend/                   # 前端 Vue 3
    └── src/
        ├── api/                # axios 接口封装
        ├── components/         # 通用组件（AI 助手、品牌侧栏等）
        ├── composables/       # 复用组合式函数
        ├── layouts/            # 布局
        ├── router/ stores/    # 路由与 Pinia
        ├── utils/              # 工具（城市坐标等）
        └── views/              # 各业务页面（auth/admin/geo/weather/ai/user）
```

---

## 四、环境要求

- JDK 21、Maven 3.8+
- Node.js 18+、npm
- MySQL 8.x
- Redis
- 本地 Ollama 并拉取模型 `qwen2.5:7b`
- 和风天气开发者账号（用于实时天气 API）

---

## 五、配置与启动

### 1. 初始化数据库

创建数据库 `weather_ads`，依次执行 `backend/src/main/resources/sql/` 下的初始化脚本：

```bash
mysql -u root -p -e "CREATE DATABASE weather_ads DEFAULT CHARSET utf8mb4;"
mysql -u root -p weather_ads < backend/src/main/resources/sql/sys_user_init.sql
mysql -u root -p weather_ads < backend/src/main/resources/sql/sys_module_init.sql
mysql -u root -p weather_ads < backend/src/main/resources/sql/sys_module_ai.sql
```

### 2. 修改后端配置

复制模板为实际配置并填入自己的值：

```bash
cp backend/src/main/resources/application.properties.example \
   backend/src/main/resources/application.properties
```

编辑 `backend/src/main/resources/application.properties`，关键项：

```properties
server.port=8080
spring.datasource.url=jdbc:mysql://<host>:3306/weather_ads?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=<your-user>
spring.datasource.password=<your-password>
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=<your-redis-password>
ollama.api-url=http://localhost:11434/v1/chat/completions
ollama.model=qwen2.5:7b
weather.qweather.host=<你的和风 API Host>
weather.qweather.api-key=<你的和风 API Key>
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8080`。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`，开发服务器已配置代理，将 `/api`、`/uploads` 转发到 `http://localhost:8080`。

生产构建：

```bash
npm run build      # 产物输出到 frontend/dist
```

---

## 六、测试账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123` |
| 普通用户 | `user` | `user123` |

---

## 七、主要接口（节选）

统一返回体：`{ "code": 200, "message": "success", "data": ... }`，非 200 时 `message` 为错误描述。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录（Session） |
| POST | `/api/auth/register` | 注册 |
| GET | `/api/weather/now` | 实时天气 |
| GET | `/api/weather/hourly` `/daily` | 24h / 7 天预报 |
| GET | `/api/weather/extras` | 预警 + 空气 + 指数聚合 |
| GET | `/api/weather/cities-overview` | 主要城市概览 |
| GET | `/api/weather/ranking` `/trend` `/metrics` | 历史排名/趋势/指标 |
| POST | `/api/ai/chat` | AI 天气问答 |
| GET | `/api/admin/users` | 用户管理（admin） |
| GET | `/api/admin/weather-data` | 数据管理（admin） |

---

## 八、鉴权说明

- 后端通过 `LoginInterceptor` 拦截 `/api/**`，放行 `/api/auth/login`、`/api/auth/register`；未登录返回 `401`。
- 登录成功后用户信息存入 HttpSession；前端 Axios 拦截器对 `401` 统一跳转登录页。
- 管理端接口在前端路由守卫中额外校验角色为 `admin`。
