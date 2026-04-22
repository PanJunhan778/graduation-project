# 智能轻量化企业管理系统

以当前代码实现为准的仓库首页与快速上手说明。

## 项目简介

这是一个面向中小企业与小微企业场景的毕业设计项目，采用前后端分离架构，围绕财务、税务、员工、审计、数据看板与 AI 辅助分析展开。系统目标不是替代大型 ERP，而是帮助企业负责人和录入人员更轻量地完成日常数据维护、经营观察与风险追踪。

当前系统的主体验已经稳定到以下形态：

1. 登录后使用“固定侧栏 + 页面工作区 + 侧栏底部个人入口”的主布局。
2. `Owner` 负责看经营全貌、管理企业数据、使用 AI 与审计能力。
3. `Staff` 负责财务、税务、员工三类基础数据的录入与维护。
4. `Admin` 只负责平台侧租户管理，不参与企业业务页。

当前仓库结构：

- `client`：Vue 3 + Vite 前端项目
- `server`：Spring Boot 3 后端项目

## 当前角色与模块边界

| 角色 | 默认进入页 | 当前可用模块 |
| --- | --- | --- |
| `Admin` | `/admin/company` | 租户管理 |
| `Owner` | `/home` | 首页、数据看板、财务账本、税务档案、员工名册、审计日志、用户管理、AI 智能助理、侧栏底部个人入口 |
| `Staff` | `/finance` | 财务账本、税务档案、员工名册 |

说明：

1. `Admin` 仅负责企业租户与初始 `Owner` 账号管理。
2. `Owner` 才拥有首页、看板、AI、审计、用户管理和回收站恢复权限。
3. `Staff` 不可访问首页、看板、AI、审计和用户管理。

## 核心功能

### 认证与租户体系

- 支持账号密码登录。
- 支持员工通过企业码注册，注册后角色固定为 `Staff`。
- 基于 Sa-Token + JWT 进行认证与会话管理。
- 业务数据按 `company_id` 进行租户隔离。

### 首页与数据看板

- 首页提供经营 KPI：本月收入、本月支出、本月净利润、当前待缴税额。
- 首页展示近 6 个月经营趋势、税务时间轴、部门人数分布、首次使用引导。
- 首页提供 AI 经营速记，并支持导出 PDF。
- 数据看板分为财务、人事、税务三类分析视图。
- 财务与人事支持区间切换，税务支持年度切换，并展示对比信息与风险提示。
- 数据看板支持导出 PDF。

### 基础数据管理

- 财务、员工、税务三类模块均支持分页查询、筛选、新增、编辑、删除、批量删除。
- 三类模块均支持 Excel 导入和模板下载。
- 三类模块均带有回收站抽屉。
- `Owner` 可执行单条恢复和批量恢复，`Staff` 无恢复权限。

### 用户管理与审计

- `Owner` 可查看企业账号列表。
- 支持创建 `Staff` 账号、启用或禁用账号、重置密码。
- 审计覆盖 `finance`、`employee`、`tax` 三个业务模块。
- 审计范围覆盖 `CREATE / UPDATE / DELETE / RESTORE`。
- 支持按模块、操作类型、日期范围查询聚合变更记录。

### AI 智能助理

- 提供全屏 AI 工作台。
- 支持会话历史、新建会话、删除会话、历史消息查看。
- 支持 Markdown 回复与流式输出。
- 能结合企业财务、税务、员工、审计数据进行问答和汇总。
- 在涉及敏感变更时支持 HITL（Human-in-the-loop）人工确认流程。

### 个人入口与公司信息

- 当前个人入口位于侧栏底部头像区域，不再以旧 `Topbar + Drawer` 作为主入口描述。
- 已稳定支持修改密码与退出登录。
- `Owner` 已稳定支持维护企业画像描述，供首页 AI 速记与 AI 助理复用。
- 系统同时保留公司名称、行业、纳税人类型、企业画像的完整配置能力。

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
- 当前配置接入 DashScope / Qwen

## 项目结构

```text
.
├─ client/                      # 前端项目（Vue 3 + Vite）
│  ├─ src/
│  │  ├─ api/                  # 前端接口请求封装
│  │  ├─ components/           # 通用组件
│  │  ├─ layouts/              # 页面布局与导航框架
│  │  ├─ router/               # 前端路由配置
│  │  ├─ store/                # Pinia 状态管理
│  │  ├─ types/                # TypeScript 类型定义
│  │  ├─ utils/                # 工具函数（如 Excel、PDF）
│  │  └─ views/                # 页面级视图模块
│  ├─ package.json             # 前端依赖与脚本
│  └─ vite.config.ts           # Vite 配置与开发代理
└─ server/                     # 后端项目（Spring Boot）
   ├─ src/
   │  ├─ main/
   │  │  ├─ java/              # controller/service/mapper 等
   │  │  └─ resources/
   │  │     ├─ application.yml # 主配置文件
   │  │     └─ schema.sql      # 数据库初始化脚本
   │  └─ test/
   │     └─ java/              # 后端测试代码
   ├─ pom.xml
   ├─ mvnw
   └─ mvnw.cmd
```

## 环境要求

- JDK 21
- MySQL 8.0.17
- Node.js 20+
- npm
- Maven 可直接使用仓库自带 Wrapper

## 本地运行步骤

### 1. 克隆项目

```bash
git clone <your-repo-url>
cd code
```

### 2. 初始化数据库

在 MySQL 中执行：

```sql
SOURCE server/src/main/resources/schema.sql;
```

该脚本会：

1. 创建 `ems` 数据库
2. 初始化核心业务表
3. 插入预置管理员账号

### 3. 配置后端环境

主配置文件位于：

```text
server/src/main/resources/application.yml
```

建议优先使用环境变量或本地覆盖配置，不要把真实数据库密码、JWT 密钥或 AI Key 直接提交到仓库。

推荐占位示例：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/ems?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=UTF-8}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:change_me}

sa-token:
  token-name: Authorization
  token-prefix: Bearer
  jwt-secret-key: ${JWT_SECRET:replace_me}

app:
  ai:
    enabled: ${AI_ENABLED:false}
    base-url: ${AI_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
    api-key: ${AI_API_KEY:replace_me}
    model: ${AI_MODEL:qwen3.5-flash}
```

### 4. 启动后端

```bash
cd server
./mvnw.cmd spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

### 5. 启动前端

```bash
cd client
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

说明：

1. 当前 `Vite` 已配置 `/api -> http://localhost:8080` 的开发代理。
2. 如果在 Windows PowerShell 下遇到执行策略拦截 `npm.ps1`，可改用 `npm.cmd install`、`npm.cmd run dev`。

### 6. 浏览器访问

```text
http://localhost:5173
```

## 配置说明

### `spring.datasource`

- `url`：MySQL 连接地址
- `username`：数据库用户名
- `password`：数据库密码

### `mybatis-plus`

- 开启下划线转驼峰映射
- 使用逻辑删除字段 `isDeleted`

### `sa-token`

- 当前使用 `Bearer` 风格令牌
- 会话超时当前为 24 小时
- `jwt-secret-key` 必须改为你自己的安全值

### `app.ai`

- `enabled`：是否启用 AI 能力
- `base-url`：OpenAI 兼容接口地址
- `api-key`：AI 平台密钥
- `model`：当前使用的模型名称
- `timeout`：AI 请求超时时间

## 推荐验证命令

下面这组命令是当前仓库已实际验证可用的 Windows 命令：

### 前端构建

```bash
cd client
npm.cmd run build
```

### 后端测试

```bash
cd server
./mvnw.cmd test
```

当前基线：

1. 前端构建可通过
2. 后端测试可通过
3. 后端测试总数为 129

## 项目亮点

- **多租户数据隔离**：围绕 `company_id` 实现企业级数据隔离。
- **固定侧栏主工作流**：角色入口清晰，当前主布局统一。
- **三类数据 + 回收站恢复**：财务、税务、员工均支持导入、批量删除与 Owner 恢复。
- **首页 AI 速记 + 流式 AI 工作台**：兼顾概览摘要与深度问答。
- **审计追踪**：覆盖新增、编辑、删除、恢复四类关键操作。
- **PDF 导出能力**：首页与看板均支持导出快照。
- **HITL 人工确认**：降低敏感 AI 更新直接落库的风险。

## 注意事项

- 本项目当前更适合毕业设计、课程展示、作品集与中小型场景验证。
- 如果要用于生产环境，还需要继续补充部署、安全、监控、备份与运维能力。
- 更完整的需求与实现约束，请结合仓库内的 `PRD` 与 `SRS` 一起阅读。
