# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此代码仓库中工作时提供指导。

> **语言约定：** 始终使用简体中文与用户交互。

## 项目概述

这是一个面向中小企业的全栈企业管理系统（毕业设计项目）。前端为 Vue 3 SPA，后端为 Spring Boot 3 REST API。系统支持三种角色：**Admin**（平台级租户管理）、**Owner**（企业运营 + AI 助手）、**Staff**（财务/员工/税务模块数据录入）。

## 常用命令

### 后端（Spring Boot，Java 21）

```bash
# 启动后端开发服务器（在项目根目录或 server/ 目录下执行）
cd server && ./mvnw spring-boot:run          # Linux/Mac
cd server && mvnw.cmd spring-boot:run        # Windows

# 打包 JAR
cd server && ./mvnw clean package

# 运行单个测试类
cd server && ./mvnw test -Dtest=类名

# 初始化/重置数据库（需先启动 MySQL）
mysql -u root -p < server/src/main/resources/schema.sql
```

### 前端（Vue 3，Node 20+）

```bash
cd client
npm install
npm run dev      # 开发服务器，地址：http://localhost:5173
npm run build    # 生产构建 → client/dist/
```

### schema.sql 初始化后的默认账号
- 访问地址：`http://localhost:5173`
- 管理员登录：`admin` / `Admin@123`

## 架构说明

### 多租户模型

所有业务实体（财务、员工、税务记录）均含 `company_id` 字段。**MyBatis-Plus 租户拦截器**会自动在所有查询中注入 `WHERE company_id = ?`，因此 Service/Controller 层无需手动过滤租户。Admin 用户的 `company_id` 为 NULL，会绕过此过滤器。拦截器配置位于 `server/.../config/`。

### 认证与授权

使用 **Sa-Token + JWT** 替代 Spring Security，Token 无状态（有效期 24 小时）。Controller 方法通过 `@SaCheckRole("owner")` / `@SaCheckRole("staff")` 注解控制权限。前端通过 Pinia（`store/user.ts`）将 Token 存入 localStorage，并在每次 Axios 请求中通过 `api/request.ts` 自动注入 `Authorization: Bearer <token>` 请求头。

`router/index.ts` 中的路由守卫读取存储的角色，与每条路由的 `meta.roles` 数组对比后决定是否放行。

### 审计日志（AOP）

`@AuditUpdate` 是自定义注解，标注在 Service 层方法上。AOP 切面拦截调用，通过 `SnapshotProvider` 接口捕获操作前后快照，计算字段级差异，并写入 `audit_log` 表。相关逻辑集中在 `server/.../audit/`。为新模块添加审计需要：实现 `SnapshotProvider`、在 Service 方法上添加注解、注册 Provider。

### AI 集成（LangChain4j）

AI 助手使用 **LangChain4j 0.29.0**，对接 OpenAI 兼容接口（默认为阿里云 DashScope / Qwen 模型）。AI 可调用约 9 个业务工具（财务汇总、员工人数等），以 `@Tool` 注解方法定义。**HITL（人工确认）** 工作流将待执行的写操作持久化到 `ai_pending_action` 表，需用户确认后方可应用。AI 配置位于 `application.yml` 的 `app.ai.*` 节点。

### 前端 API 层

每个功能模块有独立的 API 文件（`client/src/api/*.ts`），导出带类型的异步函数。所有 HTTP 请求均通过 `api/request.ts` 中的共享 Axios 实例发出，该实例统一处理 Token 注入和错误响应。视图和组件中不直接调用 `axios.get/post`。

### 后端包结构约定

```
com.pjh.server/
├── controller/   REST 端点，职责单一，委托 Service 处理业务
├── service/      业务逻辑（接口 + 实现类分离）
├── mapper/       MyBatis-Plus Mapper 接口
├── entity/       表映射 POJO，含 @TableLogic 软删除
├── dto/          请求体，含 JSR 校验注解
├── vo/           响应体
├── ai/           AI Service、工具定义、Prompt 构建
├── audit/        AOP 通知、快照 Provider、差异逻辑
└── config/       Spring 配置（MyBatis 租户、Sa-Token、AI 模型、异步线程池）
```

所有 Controller 返回 `Result<T>` 统一包装类。`GlobalExceptionHandler` 将异常转换为带错误码的 `Result` 响应。

### 软删除与时间戳

MyBatis-Plus 的 `@TableLogic` 标注在 `is_deleted` 字段上，实现所有实体的逻辑删除。自定义 `MetaObjectHandler` 在插入/更新时自动填充 `created_time`、`updated_time`、`created_by`、`updated_by`，Service 层无需手动赋值。

## 关键配置

`server/src/main/resources/application.yml` 控制：
- MySQL 连接（`spring.datasource.*`）
- Sa-Token JWT 密钥与 Token 超时（`sa-token.*`）
- AI 模型接口地址与 API Key（`app.ai.*`）
- 异步线程池与 Caffeine 缓存配置

`client/vite.config.ts` 在开发模式下将 `/api/*` 代理到 `http://localhost:8080`，本地开发无需处理跨域问题。

## 数据库

Schema 由 `server/src/main/resources/schema.sql` 全量管理（DROP + CREATE），执行后会重置所有数据。共 9 张核心表：`company`、`user`、`finance_record`、`employee`、`tax_record`、`ai_chat_log`、`ai_pending_action`、`audit_log`、`home_ai_summary_snapshot`。
