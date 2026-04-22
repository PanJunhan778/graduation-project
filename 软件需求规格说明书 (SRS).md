# 智能轻量化企业管理系统软件需求规格说明书（SRS）

- 文档版本：V3.4
- 更新日期：2026-04-22
- 对齐基准：以前后端当前实现代码为唯一事实源
- 说明：本版 SRS 重点修正文档与代码之间的历史偏差，旧版未落地主体验不再作为当前规范

## 1. 文档范围

本文档描述当前系统已经实现并可被验证的前后端结构、接口、权限、租户隔离、AI 集成协议、数据模型与配置约束。凡与旧版文档不一致之处，以当前代码实现为准。

## 2. 当前实现概览

### 2.1 技术栈

前端：

1. Vue 3
2. TypeScript
3. Vue Router
4. Pinia
5. Element Plus
6. Axios

后端：

1. Spring Boot
2. MyBatis-Plus
3. Sa-Token
4. MySQL
5. LangChain4j

### 2.2 当前主布局

当前主布局由 `MainLayout.vue` 驱动，采用：

1. 固定左侧侧栏
2. 中间页面工作区
3. 侧栏底部个人入口弹层

`Topbar.vue` 与旧版 `ProfileCenterDrawer.vue` 仍保留在代码中，但不属于当前主布局默认交互路径，因此不作为现行体验事实写入本版主流程规范。

## 3. 前端路由与角色访问矩阵

### 3.1 路由定义

| 路由 | 页面 | 角色 |
| --- | --- | --- |
| `/login` | 登录页 | 公开 |
| `/register` | 注册页 | 公开 |
| `/home` | 首页 | `owner` |
| `/dashboard` | 数据看板 | `owner` |
| `/ai-chat` | AI 智能助理 | `owner` |
| `/admin/company` | 租户管理 | `admin` |
| `/users` | 用户管理 | `owner` |
| `/finance` | 财务账本 | `owner` / `staff` |
| `/tax` | 税务档案 | `owner` / `staff` |
| `/employee` | 员工名册 | `owner` / `staff` |
| `/audit` | 审计日志 | `owner` |

### 3.2 默认跳转规则

1. `admin` 登录后默认进入 `/admin/company`
2. `owner` 登录后默认进入 `/home`
3. `staff` 登录后默认进入 `/finance`

### 3.3 当前侧栏菜单规则

1. `admin` 仅显示“租户管理”
2. `owner` 显示首页、数据看板、财务账本、税务档案、员工名册、审计日志、用户管理、AI 智能助理
3. `staff` 仅显示财务账本、税务档案、员工名册

## 4. 认证、会话与租户隔离

### 4.1 认证方式

1. 前端通过 `Authorization: Bearer <token>` 携带登录令牌
2. 后端使用 Sa-Token 进行登录态管理
3. 登录与注册接口位于 `/api/auth`

### 4.2 JWT / 登录态附加信息

登录成功后，后端在登录态 `extra` 中写入：

1. `user_id`
2. `role`
3. `company_id`

`CurrentSessionService` 通过上述附加信息读取当前用户、角色与租户。

### 4.3 多租户隔离实现

系统使用 MyBatis-Plus `TenantLineInnerInterceptor` 自动注入 `company_id` 条件。当前租户字段为：

1. 列名：`company_id`

当前被显式忽略租户拦截的表：

1. `company`
2. `user`
3. `audit_log`
4. `ai_chat_log`
5. `home_ai_summary_snapshot`

这些表不依赖自动租户注入，需在业务层显式按 `company_id` 过滤。

### 4.4 AI 异步流程中的租户上下文

AI 流式对话运行在异步线程池中。后端通过 `TenantContextHolder` 在 AI 对话分发前注入 `company_id`，在完成后清理上下文，以保证 AI 相关数据查询仍能按租户执行。

## 5. 后端通用返回规范

### 5.1 统一响应包装

除文件下载与 SSE 之外，普通接口统一返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

说明：

1. `code` 为业务状态码
2. `message` 为提示文本
3. `data` 为业务数据

### 5.2 分页接口

分页查询接口返回 MyBatis-Plus `IPage<T>` 的序列化结果。前端当前主要消费：

1. `records`
2. `total`

## 6. 业务接口规格

### 6.1 认证模块

#### `POST /api/auth/login`

请求体：

```json
{
  "username": "string",
  "password": "string"
}
```

返回核心字段：

1. `token`
2. `role`
3. `realName`
4. `companyName`
5. `companyCode`
6. `industry`
7. `taxpayerType`

实现约束：

1. 连续登录失败会触发短期锁定
2. 被禁用用户不可登录
3. 被禁用企业下的用户不可登录

#### `POST /api/auth/register`

请求体：

```json
{
  "username": "string",
  "password": "string",
  "realName": "string",
  "companyCode": "string"
}
```

实现约束：

1. 注册成功后角色固定为 `staff`
2. `companyCode` 必须对应真实企业
3. 密码必须满足强度规则

### 6.2 平台租户管理模块（`admin`）

接口：

1. `GET /api/admin/company/list`
2. `POST /api/admin/company`
3. `POST /api/admin/company/{companyId}/owner`
4. `PUT /api/admin/company/{id}/status`

能力说明：

1. 查询企业列表
2. 创建企业
3. 为企业创建 `owner`
4. 启用或禁用企业

### 6.3 企业用户管理模块（`owner`）

接口：

1. `GET /api/user/list`
2. `POST /api/user`
3. `PUT /api/user/{id}/status`
4. `PUT /api/user/{id}/reset-password`

能力说明：

1. 仅 `owner` 可访问
2. 当前主要管理员工账号
3. 支持创建、启停和密码重置

### 6.4 财务模块（`owner` / `staff`）

接口：

1. `GET /api/finance/list`
2. `GET /api/finance/categories`
3. `GET /api/finance/recycle-bin/list`
4. `POST /api/finance`
5. `PUT /api/finance/{id}`
6. `DELETE /api/finance/{id}`
7. `POST /api/finance/batch-delete`
8. `POST /api/finance/recycle-bin/{id}/restore`
9. `POST /api/finance/recycle-bin/batch-restore`
10. `POST /api/finance/import`
11. `GET /api/finance/template`

查询参数：

1. `page`
2. `size`
3. `type`
4. `category`
5. `startDate`
6. `endDate`
7. `keyword`

权限约束：

1. 财务主接口允许 `owner` 与 `staff`
2. 回收站查询与恢复只允许 `owner`

实现说明：

1. 删除为逻辑删除
2. 恢复从回收站执行
3. 导入接口使用 `multipart/form-data`
4. 模板接口返回文件流

### 6.5 员工模块（`owner` / `staff`）

接口：

1. `GET /api/employee/list`
2. `GET /api/employee/recycle-bin/list`
3. `POST /api/employee`
4. `PUT /api/employee/{id}`
5. `DELETE /api/employee/{id}`
6. `POST /api/employee/batch-delete`
7. `POST /api/employee/recycle-bin/{id}/restore`
8. `POST /api/employee/recycle-bin/batch-restore`
9. `POST /api/employee/import`
10. `GET /api/employee/template`

查询参数：

1. `page`
2. `size`
3. `keyword`
4. `department`
5. `status`

权限约束：

1. 员工主接口允许 `owner` 与 `staff`
2. 回收站查询与恢复只允许 `owner`

### 6.6 税务模块（`owner` / `staff`）

接口：

1. `GET /api/tax/list`
2. `GET /api/tax/recycle-bin/list`
3. `POST /api/tax`
4. `PUT /api/tax/{id}`
5. `DELETE /api/tax/{id}`
6. `POST /api/tax/batch-delete`
7. `POST /api/tax/recycle-bin/{id}/restore`
8. `POST /api/tax/recycle-bin/batch-restore`
9. `POST /api/tax/import`
10. `GET /api/tax/template`

查询参数：

1. `page`
2. `size`
3. `keyword`
4. `paymentStatus`
5. `taxPeriod`
6. `taxType`

权限约束：

1. 税务主接口允许 `owner` 与 `staff`
2. 回收站查询与恢复只允许 `owner`

## 7. 首页与数据看板规格

### 7.1 首页接口

#### `GET /api/dashboard/home`

权限：

1. 仅 `owner`

返回核心字段：

1. `totalIncome`
2. `totalExpense`
3. `netProfit`
4. `unpaidTax`
5. `hasUnpaidWarning`
6. `monthlyTrend[]`
7. `departmentHeadcount[]`
8. `taxCalendar[]`
9. `setupStatus`

说明：

1. `monthlyTrend` 当前固定覆盖近 6 个自然月
2. `taxCalendar` 返回近期税务节点
3. `setupStatus` 当前至少包含 `hasStaffAccount`、`hasFinanceRecord`

#### `GET /api/dashboard/home-ai-summary`

权限：

1. 仅 `owner`

返回结构：

1. `summaryLines`
2. `generatedAt`
3. `status`

状态值：

1. `ready`
2. `refreshing`
3. `empty`
4. `failed`

实现约束：

1. 首页 AI 摘要持久化到 `home_ai_summary_snapshot`
2. 快照保存 `summary_lines_json`、`status`、`is_dirty`、`generated_at`、`refresh_started_at`、`last_error`
3. 摘要刷新采用异步事件触发
4. 当前刷新超时阈值为 90 秒
5. 当存在旧摘要且刷新失败时，接口可继续返回旧摘要并将快照标记为脏

### 7.2 财务看板接口

#### `GET /api/dashboard/finance`

权限：

1. 仅 `owner`

参数：

1. `range`，支持 `last3months`、`last6months`、`last12months`、`all`

返回核心字段：

1. `totalExpense`
2. `totalIncome`
3. `expenseBreakdown[]`
4. `topIncomeSources[]`
5. `monthlyTrend[]`
6. `incomeConcentration`
7. `periodComparison`

### 7.3 人事看板接口

#### `GET /api/dashboard/hr`

权限：

1. 仅 `owner`

参数：

1. `range`，支持 `last3months`、`last6months`、`last12months`、`all`

返回核心字段：

1. `activeEmployeeCount`
2. `activeSalaryTotal`
3. `departmentSalaryShare[]`
4. `monthlyTrend[]`

### 7.4 税务看板接口

#### `GET /api/dashboard/tax`

权限：

1. 仅 `owner`

参数：

1. `range` 当前前端主流程按年份字符串传递，如 `2026`

返回核心字段：

1. `taxBurdenRate`
2. `positiveTaxAmount`
3. `incomeBase`
4. `unpaidTaxAmount`
5. `availableYears[]`
6. `selectedYear`
7. `taxTypeStructure[]`
8. `statusSummary[]`
9. `periodComparison`
10. `recentOutstanding[]`

补充说明：

1. 后端兼容部分旧范围值，但当前主交互以“年度选择”为准
2. `periodComparison` 用于展示与上一基线周期的差异

## 8. 审计日志规格

### 8.1 接口

#### `GET /api/audit/list`

权限：

1. 仅 `owner`

参数：

1. `page`
2. `size`
3. `module`
4. `operationType`
5. `startDate`
6. `endDate`

### 8.2 当前支持的筛选值

模块：

1. `finance`
2. `employee`
3. `tax`

操作类型：

1. `CREATE`
2. `UPDATE`
3. `DELETE`
4. `RESTORE`

### 8.3 返回结构

每条聚合操作返回：

1. `id`
2. `module`
3. `operationType`
4. `targetId`
5. `operationTime`
6. `userId`
7. `operatorName`
8. `changeCount`
9. `changes[]`

`changes[]` 每项包含：

1. `fieldName`
2. `oldValue`
3. `newValue`

### 8.4 实现说明

1. 当前审计不是只记录 `UPDATE`
2. 新增、编辑、删除、恢复都已纳入审计范围
3. 接口当前不要求也不支持“`module + target_id` 组合查询”作为前置条件
4. 查询以聚合操作视角返回，不是逐行裸审计表直接透出

## 9. AI 智能助理规格

### 9.1 权限与入口

1. AI 模块仅 `owner` 可访问
2. 前端主入口为 `/ai-chat` 全屏工作台

### 9.2 接口

1. `POST /api/ai/chat`
2. `POST /api/ai/confirm-action`
3. `GET /api/ai/sessions`
4. `GET /api/ai/sessions/{sessionId}/messages`
5. `DELETE /api/ai/sessions/{sessionId}`

### 9.3 流式对话协议

`POST /api/ai/chat` 返回 `text/event-stream`，当前事件名固定为：

1. `start`
2. `token`
3. `action_required`
4. `error`
5. `done`

事件载荷：

#### `start`

```json
{ "sessionId": "uuid" }
```

#### `token`

```json
{ "delta": "..." }
```

#### `action_required`

```json
{
  "sessionId": "uuid",
  "actionRequired": {
    "actionId": 1,
    "toolName": "update_company_description",
    "oldValue": "...",
    "proposedValue": "...",
    "confirmToken": "token"
  }
}
```

#### `error`

```json
{
  "code": 500,
  "message": "..."
}
```

#### `done`

```json
{
  "sessionId": "uuid",
  "reason": "message|action_required|error",
  "messageId": 123,
  "messageType": "markdown"
}
```

### 9.4 会话与消息模型

会话列表项：

1. `sessionId`
2. `title`
3. `lastMessagePreview`
4. `lastMessageTime`

消息项：

1. `id`
2. `role`
3. `messageType`
4. `content`
5. `metadata`
6. `createTime`

当前消息类型：

1. `text`
2. `markdown`
3. `action_required`
4. `action_result`

### 9.5 HITL 人工确认

当前敏感动作通过 `ai_pending_action` 表持久化，核心字段包括：

1. `action_type`
2. `confirm_token`
3. `old_value`
4. `proposed_value`
5. `status`
6. `expires_at`

当前动作状态：

1. `pending`
2. `approved`
3. `rejected`
4. `expired`

当前确认令牌有效期为：

1. 5 分钟

### 9.6 当前 AI 工具清单

当前真实工具只有以下 9 个：

| 工具名 | 作用 | 关键参数 |
| --- | --- | --- |
| `query_financial_records` | 查询财务明细样本，最多 50 条 | `startDate` `endDate` `type` `category` |
| `query_employee_list` | 查询员工列表 | `department` `status` |
| `query_tax_records` | 查询税务明细样本，最多 50 条 | `taxPeriod` `taxType` `status` |
| `query_audit_logs` | 查询审计日志样本，最多 50 条 | `module` `startDate` `endDate` |
| `get_current_datetime` | 获取后端当前日期时间与期间边界 | 无 |
| `calculate_financial_sum` | 计算权威财务汇总 | `startDate` `endDate` `type` `groupBy` |
| `calculate_tax_sum` | 计算税期区间税额汇总 | `startPeriod` `endPeriod` `status` |
| `get_business_snapshot` | 生成某月经营快照 | `yearMonth` |
| `update_company_description` | 发起企业画像更新确认流程 | `newDescription` |

说明：
1. AI 回答涉及具体财务或税务数字时，系统提示要求附加统一脚注

### 9.7 当前 AI 行为约束

1. 固定使用简体中文回答
2. 遇到“今天 / 本月 / 本季度 / 今年”等相对时间问题时，应优先调用 `get_current_datetime`
3. 遇到财务汇总类问题，应优先调用 `calculate_financial_sum`
4. 当前记忆窗口为最近 5 轮对话
5. 当前最多执行 4 轮工具调用循环

## 10. 个人中心与公司配置接口

接口：

1. `GET /api/profile/me`
2. `PUT /api/profile/me`
3. `PUT /api/profile/password`
4. `PUT /api/profile/company`

权限说明：

1. `/api/profile/me` 与 `/api/profile/password` 登录用户可用
2. `/api/profile/company` 仅 `owner`

字段说明：

`GET /api/profile/me` 当前返回：

1. `id`
2. `username`
3. `realName`
4. `role`
5. `companyName`
6. `companyCode`
7. `industry`
8. `taxpayerType`
9. `companyDescription`

`PUT /api/profile/company` 当前支持：

1. `name`
2. `industry`
3. `taxpayerType`
4. `description`

补充说明：

1. 当前主布局默认使用侧栏弹层，已稳定接入修改密码与企业画像快改
2. 代码中保留的旧 Drawer 已接入更完整的公司信息表单，但不属于当前主布局主路径

## 11. 数据持久化模型

当前核心表：

1. `company`
2. `user`
3. `finance_record`
4. `employee`
5. `tax_record`
6. `audit_log`
7. `ai_chat_log`
8. `ai_pending_action`
9. `home_ai_summary_snapshot`

通用约束：

1. 业务表采用逻辑删除字段 `is_deleted`
2. 财务、员工、税务记录通过逻辑删除进入回收站
3. 审计、AI 会话、首页 AI 摘要均有独立持久化表

## 12. 配置与安全要求

### 12.1 文档示例必须脱敏

文档中不得继续直接引用本地 `application.yml` 中的真实数据库账号、密码或 AI Key。示例配置应统一改为占位符或环境变量形式。

推荐写法：

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
    base-url: ${AI_BASE_URL:https://example.com/compatible-mode/v1}
    api-key: ${AI_API_KEY:replace_me}
    model: ${AI_MODEL:qwen3.5-flash}
```

### 12.2 运行约束

1. 前端请求超时当前为 15 秒
2. AI 请求超时当前为 60 秒
3. Sa-Token 当前使用 Bearer 风格令牌

## 13. 验证基线

本版文档对齐完成后，当前代码基线验证要求如下：

1. 前端构建命令：`npm.cmd run build`
2. 后端测试命令：`./mvnw.cmd test`

当前基线结果：

1. 前端构建通过
2. 后端测试通过
3. 测试总数为 129

