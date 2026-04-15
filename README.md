# 数据可视化及AI辅助决策的轻量化企业管理系统

## 项目简介

本项目是一个面向中小企业/小微企业的毕业设计项目，定位为前后端分离的轻量化企业管理系统。系统围绕财务、员工、人事与税务等核心经营数据展开，帮助企业老板和管理者完成日常数据录入、经营看板查看、风险追踪与信息维护。

项目的核心价值在于将传统分散在 Excel、手工表格和零散记录中的经营数据进行结构化管理，并结合 **ECharts 数据可视化** 与 **AI 辅助分析能力**，让管理者更直观地掌握企业经营状况，辅助进行日常经营判断与决策。

当前仓库采用前后端分离架构：

- `client`：Vue 3 + Vite 前端项目
- `server`：Spring Boot 3 后端项目

## 核心功能

根据当前源码与 PRD，可归纳出本项目已落地的功能模块如下：

- **用户登录与身份认证**
  - 支持账号密码登录
  - 支持员工通过企业码注册
  - 基于 Sa-Token + JWT 进行认证与会话管理
- **三类角色权限**
  - `Admin`：平台管理员，仅负责公司/租户管理
  - `Owner`：企业老板/负责人，可使用全部经营数据功能
  - `Staff`：数据录入人员，仅可处理基础业务数据
- **公司/租户管理**
  - 平台管理员可创建公司、维护企业档案、启用/禁用租户
  - 平台管理员可为企业创建初始 `Owner` 账号
- **用户管理**
  - `Owner` 可查看本公司用户列表
  - 可创建 `Staff` 账号、启用/禁用员工账号、重置员工密码
- **财务数据管理**
  - 财务流水分页查询、筛选、新增、编辑、删除
  - 支持批量删除
  - 支持 Excel 导入与模板下载
- **员工数据管理**
  - 员工信息分页查询、筛选、新增、编辑、删除
  - 支持批量删除
  - 支持 Excel 导入与模板下载
- **税务数据管理**
  - 税务记录分页查询、筛选、新增、编辑、删除
  - 支持批量删除
  - 支持 Excel 导入与模板下载
- **首页驾驶舱与数据看板**
  - 首页经营快照
  - 财务剖析、人事洞察、税务健康三类深度分析看板
  - 基于 ECharts 呈现趋势图、结构图、仪表盘等可视化内容
- **AI 智能助理**
  - 支持多轮对话、会话列表、历史消息查看、会话删除
  - 支持企业经营摘要、数据查询与辅助分析
  - 支持 HITL（Human-in-the-loop）人工确认流程
  - 当前代码中 `POST /api/ai/chat` 为**同步接口**，返回普通消息或 `action_required` 结果，而非 SSE 流式输出
- **PDF 报告导出**
  - 首页驾驶舱支持导出 PDF
  - 深度数据看板支持导出 PDF
  - 基于前端 `html2canvas + jsPDF` 完成页面快照导出
- **操作日志与审计**
  - 审计覆盖 `finance`、`employee`、`tax` 三个业务模块
  - 支持按 `module`、`operationType`、日期范围查询
- **个人资料与公司设置**
  - 用户可查看和修改个人资料
  - 支持修改密码
  - `Owner` 可维护当前公司基础信息与企业描述

## 技术栈

### 前端

- Vue 3
- TypeScript
- Vite 8
- Element Plus
- ECharts
- Pinia
- Vue Router
- Axios
- html2canvas
- jsPDF
- Markdown-It
- Sass

### 后端

- Java 21
- Spring Boot 3.5.13
- MyBatis-Plus 3.5.5
- Sa-Token 1.37.0
- sa-token-jwt 1.37.0
- LangChain4j 0.29.0
- Hutool
- Apache POI
- Caffeine
- Lombok

### 数据库

- MySQL 8.0.17

### AI 集成

- OpenAI 兼容接口
- 当前配置接入 DashScope / Qwen，请根据实际情况修改

### 构建与开发工具

- Maven 3.9.14（仓库自带 Maven Wrapper）
- npm

## 项目结构

> 以下目录树仅展示源码目录和关键配置文件，已省略 `node_modules`、`target`、`dist`、`build` 等依赖或构建产物。

```text
.
├─ client
│  ├─ src
│  │  ├─ api
│  │  ├─ assets
│  │  ├─ components
│  │  ├─ composables
│  │  ├─ layouts
│  │  ├─ router
│  │  ├─ store
│  │  ├─ styles
│  │  ├─ types
│  │  ├─ utils
│  │  └─ views
│  ├─ package.json
│  └─ vite.config.ts
├─ server
│  ├─ src
│  │  ├─ main
│  │  │  ├─ java
│  │  │  └─ resources
│  │  │     ├─ application.yml
│  │  │     └─ schema.sql
│  │  └─ test
│  │     └─ java
│  ├─ pom.xml
│  ├─ mvnw
│  └─ mvnw.cmd
├─ 面向用户需求文档 (PRD).md
├─ 软件需求规格说明书 (SRS).md
└─ 进度.md
```

## 环境要求

请根据当前项目配置准备本地开发环境：

- JDK 21
- Maven 3.9.14（可直接使用仓库自带 `mvnw.cmd`）
- MySQL 8.0.17
- Node.js：需与 Vite 8 兼容，推荐 Node.js 20+，请根据实际情况修改
- npm：请根据本机 Node.js 版本配套安装

## 本地运行步骤

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd code
```

### 2. 初始化数据库

在 MySQL 中执行后端提供的初始化脚本：

```sql
SOURCE server/src/main/resources/schema.sql;
```

该脚本会：

- 创建 `ems` 数据库
- 初始化核心业务表
- 插入一个预置管理员账号

如需调整数据库名或初始化数据，请根据实际情况修改。

### 3. 修改后端配置

打开：

```text
server/src/main/resources/application.yml
```

重点修改以下配置为你本机环境的实际值：

- 数据库连接地址、用户名、密码
- JWT/Sa-Token 密钥
- AI 平台地址、模型名、API Key

建议不要把真实密钥直接提交到仓库，优先改为环境变量或本地覆盖配置。

可参考如下占位示例：

```yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ems?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
    username: your_db_user
    password: your_db_password

sa-token:
  jwt-secret-key: ${JWT_SECRET:replace-with-your-secret}

app:
  ai:
    enabled: true
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: ${AI_API_KEY:replace-with-your-api-key}
    model: qwen3.5-flash
```

### 4. 启动后端

进入后端目录并启动 Spring Boot：

```bash
cd server
mvnw.cmd spring-boot:run
```

后端默认启动地址：

```text
http://localhost:8080
```

### 5. 启动前端

打开新的终端，进入前端目录：

```bash
cd client
npm install
npm run dev
```

前端默认启动地址：

```text
http://localhost:5173
```

### 6. 浏览器访问

打开浏览器访问：

```text
http://localhost:5173
```

当前 `Vite` 已配置开发代理，前端请求的 `/api` 会转发到：

```text
http://localhost:8080
```

## 配置说明

项目后端主配置文件位于：

```text
server/src/main/resources/application.yml
```

### `spring.datasource`

用于配置 MySQL 数据源：

- `url`：数据库连接地址
- `username`：数据库用户名
- `password`：数据库密码
- `driver-class-name`：MySQL JDBC 驱动

### `mybatis-plus`

用于配置 MyBatis-Plus 行为：

- `map-underscore-to-camel-case`：开启下划线转驼峰映射
- `log-impl`：配置 SQL 日志输出实现
- `logic-delete-field`：逻辑删除字段名
- `logic-delete-value` / `logic-not-delete-value`：逻辑删除取值定义

### `sa-token`

用于配置认证与会话管理：

- `token-name`：令牌名称
- `token-prefix`：请求头中的令牌前缀，当前为 `Bearer`
- `timeout`：会话超时时间，当前配置为 86400 秒（24 小时）
- `token-style`：令牌生成风格
- `jwt-secret-key`：JWT 密钥，务必替换为你自己的安全值

### `app.ai`

用于配置 AI 模块：

- `enabled`：是否启用 AI 功能
- `base-url`：OpenAI 兼容接口地址
- `api-key`：AI 平台密钥
- `model`：当前使用的模型名称
- `timeout`：AI 请求超时时间

### 安全提醒

当前仓库中的 `application.yml` 已出现真实敏感配置示例，包括：

- 数据库密码
- JWT Secret
- AI API Key

如果你准备公开仓库或继续长期维护，建议尽快改为：

- 环境变量注入
- 本地私有配置文件覆盖
- CI/CD Secret 管理

## 角色说明

### Admin

- 仅负责平台级公司/租户管理
- 可创建公司、创建初始 `Owner` 账号、启用/禁用租户
- 不参与企业日常财务、员工、税务、看板与 AI 业务操作

### Owner

- 当前企业的核心管理者
- 可访问首页驾驶舱、深度数据看板、AI 助理、审计日志、用户管理、公司设置
- 可管理本公司财务、员工、税务全部业务数据
- 可创建和管理 `Staff` 账号

### Staff

- 主要负责基础数据录入与维护
- 可访问财务、员工、税务相关页面及个人资料功能
- 不可访问首页驾驶舱、数据看板、AI 助理、审计日志、租户管理与用户管理

## 项目亮点

- **前后端分离架构**：前端基于 Vue 3 + Vite，后端基于 Spring Boot 3，结构清晰，便于维护与扩展。
- **多角色权限控制**：围绕 `Admin / Owner / Staff` 建立明确的角色边界。
- **多租户数据隔离**：基于 `company_id` 与 MyBatis-Plus 租户拦截机制进行数据隔离。
- **财务 / 人事 / 税务一体化管理**：围绕企业核心经营数据统一建模与维护。
- **ECharts 数据可视化**：通过经营驾驶舱与分析看板展示趋势、结构与健康指标。
- **AI 辅助经营分析**：接入 OpenAI 兼容模型，结合企业上下文、业务快照与工具调用完成辅助分析。
- **Excel 批量导入与校验**：财务、员工、税务模块均支持模板下载、Excel 导入与错误反馈。
- **PDF 快照导出**：首页与看板支持一键导出 PDF，便于演示、汇报与归档。
- **操作日志与审计**：对关键业务修改进行审计记录，便于追踪变更。
- **HITL 人工确认机制**：涉及企业描述等敏感更新时，通过人工确认后再落库，降低误操作风险。

## 注意事项

- 请不要提交 `node_modules`、`target`、`dist`、`build` 等依赖或构建产物目录。
- 请不要提交真实数据库密码、API Key、JWT Secret 等敏感信息。
- 当前项目中的部分产品描述来自 PRD / SRS，但 README 已尽量以**当前代码现状**为准。
- AI 对话接口目前按同步结果处理，不应在 README 中描述为尚未落地的 SSE 流式实现。
- 本项目主要用于毕业设计、学习与演示，如需用于生产环境，请进一步补充部署、安全、监控与运维能力。
