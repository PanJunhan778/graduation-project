# 软件需求规格说明书 (SRS)

**项目名称**：智能轻量化企业管理系统 

**版本号**：V3.3 

**面向对象**：后端开发工程师、前端开发工程师、测试工程师、系统架构师

**文档日期**：2026-04-09

## 1. 引言 (Introduction)

### 1.1 编写目的

本文档旨在明确“智能轻量化企业管理系统”的业务需求、功能规格及非功能性约束。本文档将作为前后端开发、接口定义及系统测试的唯一标准依据。系统的底层数据库物理模型设计将另行在《系统详细设计说明书 (SDD)》中独立约束。任何偏离本文档的开发行为必须经过需求变更评审。

### 1.2 术语表 (Glossary)

| 术语 / 缩写                  | 定义                                                         |
| ---------------------------- | ------------------------------------------------------------ |
| **多租户隔离**               | 依靠数据行级标识（`company_id`）实现不同企业客户间数据绝对物理/逻辑隔离的架构。 |
| **逻辑删除**                 | 软删除。数据不在物理磁盘上抹除，仅通过修改 `is_deleted` 字段标识其失效状态。 |
| **HITL (Human-in-the-loop)** | 人类在环机制。指系统（特别是 AI）在执行敏感的数据修改操作前，必须挂起流程，等待人类用户的二次确认。 |
| **AOP 审计**                 | 面向切面编程。在不侵入主业务代码的前提下，通过底层拦截器自动抓取和记录数据变更日志的技术。 |

## 2. 总体约束与技术选型 (Overall Description)

### 2.1 技术栈基准

- **前端**：Vite + Vue 3 (Composition API) + Element Plus + ECharts。
- **后端**：Java 21 + Spring Boot 3.x + MyBatis-Plus。
- **安全认证**：Sa-Token + JWT（严禁引入 Spring Security 避免依赖过重）。
- **AI 核心库**：LangChain4j。
- **数据库**：MySQL 8.0.17。

### 2.2 运行环境与终端适配

- **终端类型**：本期仅支持 PC 端 Web 浏览器（推荐 Chrome 100+ 或 Edge 100+）。
- **布局约束**：前端 UI 必须基于 Flex 弹性盒或 CSS Grid 实现。严禁硬编码绝对像素宽度，为未来移动端拓展预留空间。

### 2.3 全局工程规范

- **金额精度**：所有涉及货币金额的变量、参数、数据流转，必须统一采用高精度类型，在 Java 中强制映射为 `java.math.BigDecimal`，严禁使用浮点型（`Float`/`Double`）。
- **缓存方案**：HITL `confirm_token` 等短期凭证采用 Caffeine 本地缓存（TTL 5 分钟）。本系统为单机部署架构，无需引入 Redis。

## 3. 系统核心功能规格 (Functional Requirements)

### 3.1 权限与租户隔离模块

- **FR-3.1.1 租户鉴权**：系统无状态登录，后端基于 JWT 生成 Token。Token 载荷（Payload）中必须明文包含 `user_id`、`role` 与 `company_id`。
- **FR-3.1.2 隐式租户路由**：前端所有受保护的 Axios 请求必须在 Header 携带 `Authorization: Bearer <token>`。后端严禁通过 Request Body 或 Params 接收 `company_id`，必须由 Sa-Token 拦截器从 Token 中解析 `company_id`，并通过 MyBatis-Plus 的 `TenantLineInnerInterceptor` 隐式拼接至底层 SQL 的 `WHERE` 条件中。

**【验收标准 (Acceptance Criteria)】**

- **AC-3.1.1 (Token 安全载荷)**：测试工具解码返回的 JWT Token，能精准读取 `user_id` 和 `company_id`，且不可包含 password 等敏感字段。
- **AC-3.1.2 (防越权访问拦截)**：使用 A 公司的合法 Token，强行调用接口试图修改或查询 B 公司的 target_id 数据时，系统必须拦截并返回 403 / 404 / 列表为空；审查底层执行的 SQL，其 WHERE 语句中必须自动且强制包含 `company_id = A公司ID`。

### 3.2 财务与人事核心录入模块

- **FR-3.2.1 财务明细管理**：支持单笔收支录入，必填项：收支类型、金额（>0）、分类、发生日期。
- **FR-3.2.2 税务记录管理**：支持按期录入已确认的税务支出。**约束**：系统不内置任何国家法定税率计算逻辑，全量依赖人工录入实际缴税结果。必填项：所属期（需正则校验格式如 `YYYY-MM` 或 `YYYY-Qx`）、税种、纳税状态、税额（允许录入负数代表退税）。
- **FR-3.2.3 批量数据导入与容错**：支持基于标准化 Excel 模板（Hutool-poi 解析）导入历史财务/人事数据。
  - **异常处理规约**：执行强校验，若遇非法格式，接口返回 HTTP 200，但业务状态码标记失败，`data` 数组返回具体报错明细（例：`[{"row": 5, "error": "金额必须大于0"}]`）。
  - **容错补救机制**：前端列表需提供 Checkbox 勾选框，支持批量逻辑删除（`POST /api/finance/batch-delete`）。

**【验收标准 (Acceptance Criteria)】**

- **AC-3.2.1 (金额边界校验)**：录入财务流水时，前端与后端需双向拦截，当 `amount` 为 `0` 或 `-100` 时，系统拒绝入库并抛出非法参数提示。
- **AC-3.2.2 (Excel 批量导入原子性与错误反馈)**：上传包含 100 条记录的模板，若第 5 行金额格式非法，系统必须执行**全量回滚**，并精确返回包含 `{"row": 5, "error": "XXX"}` 的 JSON 错误集合。
- **AC-3.2.3 (批量删除隔离验证)**：前端勾选 3 条数据发起批量删除后，重新刷新页面列表，被删除数据不再展示，且不影响系统其他模块同 ID 的数据。

### 3.3 AOP 系统审计模块

- **FR-3.3.1 拦截范围**：针对财务表与人事表的 `UPDATE` 操作。
- **FR-3.3.2 抓取逻辑**：拦截器在修改前执行 `SELECT` 获取原对象，修改后获取新对象，比对字段差异。序列化发生变更的英文字段名、`old_value` 和 `new_value`，异步写入审计日志。
- **FR-3.3.3 软关联查询**：前端请求日志列表时，查询条件必须联合传递业务模块标识 `module` 与目标数据主键 `target_id`，以解决底层多表自增主键冲突问题。

**【验收标准 (Acceptance Criteria)】**

- **AC-3.3.1 (精准差异抓取)**：修改某条财务记录的 `remark` 字段（金额与日期不变），审查日志记录，其 `new_value` 和 `old_value` 内部仅包含被修改的 `remark` 字段差异，不得包含所有字段。
- **AC-3.3.2 (多态查询验证)**：查询日志接口时，若未携带 `module` 参数仅提供 `target_id`，接口必须直接返回参数缺失错误（HTTP 400）。

## 4. AI 数字助理集成规约 (AI Integration Specifications)

### 4.1 记忆控制与上下文注入

- **令牌（Token）保护机制**：利用 LangChain4j 的 `MessageWindowChatMemory`。每次发往 LLM 的负载仅限：`[系统全局 Prompt]` + `[最近 5 轮对话记录]` + `[当前用户输入]`。
- **全局上下文组装**：系统 Prompt 必须动态注入当前登录企业的业务描述画像（Description）与纳税人性质（Taxpayer Type）。

### 4.2 智能体工具库设计 (Function Calling API)

后端必须注册并精确描述以下 8 个 `@Tool`，禁止 LLM 执行数学运算，所有聚合指标必须由 Java 后台计算后下发。

| 模块层级        | 工具名称 (Tool ID)               | 方法入参 (Parameters)                      | 预期返回数据结构 (Returns)    | 核心职责                                                     |
| --------------- | -------------------------------- | ------------------------------------------ | ----------------------------- | ------------------------------------------------------------ |
| **L1 原子查询** | `query_financial_records`        | `startDate`, `endDate`, `type`, `category` | `List<FinanceDTO>` (上限50条) | 查询底层财务明细表                                           |
|                 | `query_employee_list`            | `department`, `status`                     | `List<EmployeeDTO>`           | 查询花名册                                                   |
|                 | `query_tax_records`              | `taxPeriod`, `taxType`, `status`           | `List<TaxDTO>`                | 查询税务明细与状态                                           |
|                 | `query_audit_logs`               | `module`, `startDate`, `endDate`           | `List<AuditLogDTO>`           | 查询操作溯源日志                                             |
| **L2 聚合计算** | `calculate_financial_sum`        | `startDate`, `endDate`, `type`, `groupBy`  | `Map<String, BigDecimal>`     | 按条件分组求和 (如按分类统计支出)                            |
|                 | `calculate_tax_sum`              | `startPeriod`, `endPeriod`, `status`       | `BigDecimal`                  | 求和指定周期内的总税额                                       |
| **L3 宏观诊断** | `get_business_snapshot`          | `yearMonth` (如 "2026-03")                 | `BusinessSnapshotJSON`        | 联合多模块，一次性返回当月总收支、利润、已缴与待缴税金等全局快照 |
| **L4 HITL**    | `update_company_description`     | `newDescription`                           | HITL 挂起响应                 | 更新企业业务画像（触发 HITL 二次确认流，不直接落库）          |

### 4.3 动态业务上下文学习 (HITL 工作流)

当大模型识别出业务变化并决议调用 `update_company_description` 工具时，系统必须遵循以下时序：

1. **挂起执行**：Java 工具类内不触发任何底层更新语句。
2. **生成凭证**：服务端在缓存中生成安全凭证 `confirm_token`，存活期 5 分钟。
3. **阻断响应**：向上游抛出包含 `REQUIRE_CONFIRM` 动作、旧值与新值提议的特殊 HTTP 响应体。
4. **人工介入**：前端渲染包含新旧值对比的确认组件。
5. **落地执行**：用户点击确认，前端请求专用确认接口，后端核验 Token 后持久化入库，更新企业档案。

## 5. RESTful API 接口规约 (API Specifications)

本章节定义前后端数据交互的核心规范。所有未在本章穷举的接口，均需严格遵守本章的全局响应约定。

### 5.1 全局响应与鉴权约定 (Global API Rules)

**1. 请求头注入 (Headers)** 前端发起的任何非登录业务请求，必须携带身份鉴权 Token： `Authorization: Bearer <jwt_token>`

**2. 统一响应包装体 (Unified Response Wrapper)** 后端接口无论成功或失败，HTTP Status 均尽量返回 `200 OK`（鉴权失败除外），具体的业务状态由 JSON 根部的 `code` 决定。必须采用以下结构体 `Result<T>`：

```
{
  "code": 200,                // 业务状态码 (200: 成功, 400: 参数错误, 500: 服务器异常)
  "message": "操作成功",       // 友好的前端提示文案
  "data": { ... }             // 泛型数据负载 (可为 Object, Array 或 null)
}
```

### 5.2 核心业务模块接口定义 (高优示例)

#### 5.2.1 财务明细分页查询

- **接口地址**：`GET /api/finance/list`

- **功能描述**：获取财务流水列表，支持多条件检索。**注意：禁止前端传入 company_id。**

- **请求参数 (Query Parameters)**：`page`, `size`, `type`, `startDate`, `endDate`

- **成功响应示例 (Response)**：

  ```
  {
    "code": 200,
    "message": "查询成功",
    "data": {
      "total": 156,
      "records": [
        {
          "id": "1001",
          "type": "expense",
          "amount": "5000.00",
          "category": "采购支出",
          "date": "2026-04-01",
          "remark": "采购办公电脑"
        }
      ]
    }
  }
  ```

#### 5.2.2 批量删除（容错补救）

- **接口地址**：`POST /api/finance/batch-delete`
- **功能描述**：用于 Excel 批量导入错误后的快速撤销，执行底层逻辑删除。
- **请求体 (Request Body: application/json)**：`{ "ids": [1001, 1002, 1003] }`

### 5.3 AI 与 HITL 核心交互接口

#### 5.3.1 AI 对话发起接口 (SSE 流式)

- **接口地址**：`POST /api/ai/chat`

- **协议**：**Server-Sent Events (SSE)**。响应 Content-Type 为 `text/event-stream`，实现打字机流式渲染。后端基于 LangChain4j 的 `StreamingChatLanguageModel` 驱动令牌逐步推送。

- **请求体 (Request Body)**：`{ "message": "我们最近新增了迪拜海外业务项目" }`

- **SSE 事件流定义**：

  **事件 A：正常文本流式推送**

  ```
  event: token
  data: {"content": "好的"}

  event: token
  data: {"content": "老板"}

  event: token
  data: {"content": "，我已经"}

  ...

  event: done
  data: {"content": "[完整回复文本]"}
  ```

  前端通过 `EventSource` 或 `fetch + ReadableStream` 逐条接收 `event: token` 并追加渲染。收到 `event: done` 后关闭连接并持久化完整消息。

  **事件 B：触发 HITL (拦截并要求人工确认) 【核心】**

  当大模型试图调用更新企业画像的工具时，后端阻断落库操作，在 SSE 流中插入特殊事件类型：

  ```
  event: action_required
  data: {"tool_name": "update_company_description", "old_value": "主营国内电商", "proposed_value": "主营国内电商，近期新增迪拜海外业务项目", "confirm_token": "secure_token_xyz789"}
  ```

  前端收到 `event: action_required` 后渲染 HITL 确认卡片，流正常结束。

  **事件 C：错误处理**

  ```
  event: error
  data: {"code": 500, "message": "AI 服务暂时不可用"}
  ```

#### 5.3.2 AI 动作二次确认接口 (Confirm Action)

- **接口地址**：`POST /api/ai/confirm-action`

- **功能描述**：前端弹出确认卡片，用户点击“同意”后，调用此接口真正更新数据库。

- **请求体 (Request Body)**：

  ```
  {
    "confirm_token": "jwt_or_redis_token_xyz789",
    "is_approved": true
  }
  ```

### 5.4 认证与用户管理接口

#### 5.4.1 统一登录接口

- **接口地址**：`POST /api/auth/login`
- **功能描述**：用户输入账号密码登录，后端校验凭据并根据数据库中存储的角色返回 Token 与权限信息。**前端角色选择器仅作为 UI 引导，不作为鉴权依据。**
- **请求体 (Request Body)**：`{ "username": "admin01", "password": "Abc@123456" }`
- **成功响应示例**：

  ```
  {
    "code": 200,
    "message": "登录成功",
    "data": {
      "token": "eyJhbGciOiJIUzI1NiJ9...",
      "role": "owner",
      "realName": "张总",
      "companyName": "深圳XX贸易有限公司"
    }
  }
  ```

#### 5.4.2 员工自主注册接口

- **接口地址**：`POST /api/auth/register`
- **功能描述**：仅限 Staff 角色使用。注册时必须携带 6 位企业码，后端校验企业码有效性后自动归入对应租户。
- **请求体 (Request Body)**：`{ "username": "staff01", "password": "Abc@123456", "realName": "李录入", "companyCode": "A1B2C3" }`

#### 5.4.3 用户管理 (Owner 管理 Staff 账号)

- **获取列表**：`GET /api/user/list` — 分页查询当前公司下的用户列表
- **创建账号**：`POST /api/user` — Owner 为下属 Staff 创建账号
- **修改状态**：`PUT /api/user/{id}/status` — 启用/禁用账号
- **重置密码**：`PUT /api/user/{id}/reset-password` — Owner 重置下属密码

### 5.5 基础数据 CRUD 接口

以下接口均遵循统一响应包装体，前端严禁传入 `company_id`。

#### 5.5.1 财务记录

- **分页查询**：`GET /api/finance/list` — 参数：`page`, `size`, `type`, `category`, `startDate`, `endDate`
- **新增**：`POST /api/finance` — 单笔录入
- **修改**：`PUT /api/finance/{id}` — 单笔编辑（触发 AOP 审计）
- **单笔删除**：`DELETE /api/finance/{id}` — 逻辑删除
- **批量删除**：`POST /api/finance/batch-delete` — 请求体 `{ "ids": [...] }`
- **Excel 导入**：`POST /api/finance/import` — Multipart 文件上传
- **模板下载**：`GET /api/finance/template` — 下载标准 `.xlsx` 导入模板

#### 5.5.2 员工记录

- **分页查询**：`GET /api/employee/list` — 参数：`page`, `size`, `department`, `status`
- **新增**：`POST /api/employee`
- **修改**：`PUT /api/employee/{id}`（触发 AOP 审计）
- **单笔删除**：`DELETE /api/employee/{id}`
- **批量删除**：`POST /api/employee/batch-delete`
- **Excel 导入**：`POST /api/employee/import`
- **模板下载**：`GET /api/employee/template`

#### 5.5.3 税务记录

- **分页查询**：`GET /api/tax/list` — 参数：`page`, `size`, `taxType`, `paymentStatus`, `taxPeriod`
- **新增**：`POST /api/tax`
- **修改**：`PUT /api/tax/{id}`
- **单笔删除**：`DELETE /api/tax/{id}`
- **批量删除**：`POST /api/tax/batch-delete`

### 5.6 数据看板与大屏聚合接口

#### 5.6.1 首页驾驶舱数据

- **接口地址**：`GET /api/dashboard/home`
- **功能描述**：一次性返回首页大屏所需的全部聚合指标。
- **响应 data 结构**：

  ```
  {
    "totalIncome": "150000.00",
    "totalExpense": "98000.00",
    "netProfit": "52000.00",
    "unpaidTax": "3200.00",
    "hasUnpaidWarning": true,
    "monthlyTrend": [ { "month": "2025-11", "income": "...", "expense": "...", "profit": "..." }, ... ],
    "taxCalendar": [ { "taxPeriod": "2026-Q1", "taxType": "增值税", "status": 0, "amount": "3200.00" }, ... ]
  }
  ```

#### 5.6.2 数据看板 - 财务剖析

- **接口地址**：`GET /api/dashboard/finance`
- **功能描述**：返回支出分类占比、前五大收入来源等看板数据。

#### 5.6.3 数据看板 - 人事洞察

- **接口地址**：`GET /api/dashboard/hr`
- **功能描述**：返回各部门薪资占比、人效趋势等看板数据。

#### 5.6.4 数据看板 - 税务健康

- **接口地址**：`GET /api/dashboard/tax`
- **功能描述**：返回综合税负率、各税种结构等健康指标。

### 5.7 审计日志接口

- **接口地址**：`GET /api/audit/list`
- **请求参数**：`module`（必填）, `targetId`（必填）, `page`, `size`
- **功能描述**：查询指定模块、指定数据的操作变更历史。`module` 与 `targetId` 必须联合传递，缺一返回 HTTP 400。

### 5.8 AI 历史对话接口

#### 5.8.1 获取会话列表

- **接口地址**：`GET /api/ai/sessions`
- **功能描述**：返回当前用户所有的 AI 对话会话，按最新消息时间倒序。

#### 5.8.2 获取会话消息

- **接口地址**：`GET /api/ai/sessions/{sessionId}/messages`
- **请求参数**：`page`, `size`
- **功能描述**：分页拉取某个会话的历史消息记录，用于前端瀑布流回溯。

### 5.9 Admin 租户管理接口

以下接口仅限 `admin` 角色访问，通过 Sa-Token 角色鉴权拦截。

- **公司列表**：`GET /api/admin/company/list` — 分页查询所有公司
- **创建公司**：`POST /api/admin/company` — 录入公司档案并生成 6 位企业码
- **创建 Owner**：`POST /api/admin/company/{companyId}/owner` — 为公司创建初始老板账号
- **禁用公司**：`PUT /api/admin/company/{id}/status` — 启用/禁用租户

## 6. 非功能性需求 (Non-Functional Requirements)

### 6.1 安全规约

- **密码加密**：用户密码必须使用 BCrypt 算法加盐加密存储，严禁明文或 MD5。
- **JWT 过期策略**：Token 有效期设为 **24 小时**，过期后前端跳转登录页重新认证。本期不实现 Refresh Token 机制。
- **密码复杂度**：至少 8 位，必须包含大写字母、小写字母和数字。
- **登录安全**：同一账号连续 5 次密码错误后，锁定 15 分钟。
- **CORS**：后端必须配置跨域白名单，仅允许前端开发域名（`localhost:5173`）和生产域名访问。

### 6.2 性能指标

- **API 响应**：常规 CRUD 接口 P95 响应时间 < 500ms（不含 AI 模块）。
- **AI 首 Token 延迟**：SSE 流式首个 `token` 事件需在 3 秒内返回。
- **并发能力**：单实例支撑 50 并发用户（毕业设计演示规模）。
- **单租户数据容量**：财务记录上限 10 万条，员工记录上限 1000 条。

### 6.3 PDF 导出

- **技术方案**：前端采用 `html2canvas` + `jsPDF` 纯客户端方案，将当前大屏/看板 DOM 截图并拼接为 PDF 文件。
- **约束**：导出时需显示加载遮罩，防止用户在截图过程中操作界面导致数据偏移。