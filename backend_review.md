# 后端代码优化审查报告

> 审查范围：`server/` 全部 10 个 Controller、15 个 Service（含 19 个 impl）、9 个 Mapper、9 个 Entity、15 个 DTO、26 个 VO、11 个 Config、以及 AI / Audit / Dashboard 三个独立模块

---

## 总体评价

后端架构**非常扎实**：多租户自动隔离（MyBatis-Plus TenantLine）、基于 Sa-Token JWT 的 RBAC 鉴权、AOP 审计日志、异步事件解耦、AI 工具调用 + HITL 确认流程——对于毕业设计来说完成度和设计水平都很高。以下问题同样属于"从 80 分打到 95 分"的打磨。

---

## 🔴 优先级 P0 — 安全隐患

### 1. API Key 硬编码在 `application.yml` 中

```yaml
app:
  ai:
    api-key: "sk-487a0c643a72430383216179ac593226"
```

这个 AI 服务的 API Key **明文**写在配置文件中，且已经提交到 Git 仓库。如果仓库是公开的（或将来公开），Key 会立刻泄漏。

**建议**：
- 将 api-key 移到环境变量或 `.env` 文件中：`api-key: ${AI_API_KEY:}`
- 在 `.gitignore` 中加入 `.env`
- **立刻在阿里云控制台轮换（rotate）这个 Key**

---

### 2. 数据库密码硬编码

```yaml
spring:
  datasource:
    password: mysql
```

虽然是本地开发环境的默认密码，但如果毕设答辩时演示或部署到外网服务器，应使用环境变量注入：

```yaml
password: ${DB_PASSWORD:mysql}
```

---

### 3. JWT Secret Key 强度不足且硬编码

```yaml
sa-token:
  jwt-secret-key: ems-graduation-project-jwt-secret-2026
```

这个 key 是一个**可预测的英文短语**，存在被暴力猜解/伪造 token 的风险。

**建议**：替换为一个 32+ 字节的随机字符串，并通过环境变量注入。

---

### 4. `ResetPasswordDTO` 缺少密码强度校验

```java
public class ResetPasswordDTO {
    @NotBlank(message = "新密码不能为空")
    private String newPassword;  // ← 没有 @Pattern
}
```

目前只在 `UserServiceImpl.resetPassword()` 中做了业务层校验 `dto.getNewPassword().matches(Constants.PASSWORD_PATTERN)`，但如果未来有其他地方复用这个 DTO，就会遗漏校验。

**建议**：在 DTO 上加 `@Pattern(regexp = Constants.PASSWORD_PATTERN)`，与 `RegisterDTO` 保持一致。同样的问题也存在于 `ChangePasswordDTO`，其 `newPassword` 也缺少 `@Pattern`。

---

### 5. `FinanceCreateDTO.type` 缺少枚举校验

```java
@NotBlank(message = "收支类型不能为空")
private String type;  // ← 没有 @Pattern 限制合法值
```

虽然 `FinanceServiceImpl` 中有 `validateType()` 做业务层校验，但 DTO 层没有约束，意味着任意字符串都能穿透到 Service 层。

**建议**：在 DTO 上加 `@Pattern(regexp = "^(income|expense)$", message = "收支类型只能为 income 或 expense")`。

---

## 🟡 优先级 P1 — 代码质量与一致性

### 6. `UserServiceImpl` 重复实现了 `getCurrentCompanyId()`

[UserServiceImpl.java](file:///d:/graduation_project/code/server/src/main/java/com/pjh/server/service/impl/UserServiceImpl.java#L26-L32) 中自己写了一个 `getCurrentCompanyId()` 方法：

```java
private Long getCurrentCompanyId() {
    Object companyId = StpUtil.getExtra(Constants.JWT_COMPANY_ID_KEY);
    ...
}
```

但项目中已经有 `CurrentSessionService.requireCurrentCompanyId()` 做同样的事。`FinanceServiceImpl`、`ProfileServiceImpl` 等都在用 `CurrentSessionService`。

**建议**：删除 `UserServiceImpl` 的 `getCurrentCompanyId()` 私有方法，统一注入 `CurrentSessionService`。

---

### 7. `AdminCompanyController.updateStatus` 使用 `Map<String, Integer>` 接收参数

```java
@PutMapping("/{id}/status")
public Result<Void> updateStatus(
    @PathVariable Long id,
    @RequestBody Map<String, Integer> body) {
    Integer status = body.get("status");
    ...
}
```

同样的问题也存在于 `UserController.updateStatus`。使用裸 Map 会导致：
- 无法享受 `@Valid` 校验
- Swagger/OpenAPI 文档无法生成正确的 request schema
- 调用方不知道需要传什么字段

**建议**：创建一个简单的 `UpdateStatusDTO`：
```java
@Data
public class UpdateStatusDTO {
    @NotNull(message = "状态值不能为空")
    @Min(0) @Max(1)
    private Integer status;
}
```

---

### 8. `EmployeeController.list` 中 `department` 参数处理逻辑不正确

```java
@GetMapping("/list")
public Result<IPage<EmployeeVO>> list(
    @RequestParam(required = false) String keyword,
    @RequestParam(required = false) String department,
    @RequestParam(required = false) Integer status) {
    String normalizedKeyword = StrUtil.trimToNull(keyword);
    if (normalizedKeyword == null) {
        normalizedKeyword = StrUtil.trimToNull(department);  // ← 把 department 当 keyword 用
    }
    return Result.success(employeeService.listEmployees(page, size, normalizedKeyword, status));
}
```

前端传了 `department` 和 `keyword` 两个独立筛选条件，但后端只有一个 `keyword` 参数，把 `department` 当做 fallback keyword 来用。这会导致：**如果同时传了 keyword 和 department，department 筛选完全不生效**。

同样的问题也存在于 `TaxController.list`，其中 `taxPeriod` 和 `taxType` 也被降级为 keyword 的 fallback。

**建议**：让 Service 层接收独立参数，在 SQL 的 WHERE 条件中分别处理。

---

### 9. SQL 日志在生产环境应关闭

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

`StdOutImpl` 会把每条 SQL 打印到 stdout，在生产环境下会：
- 严重影响性能
- 输出敏感数据（如密码 hash）

**建议**：通过 profile 区分，开发环境使用 `StdOutImpl`，生产环境移除或设为 `NoLoggingImpl`：

```yaml
# application-dev.yml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

# application-prod.yml (不配置 log-impl)
```

---

### 10. `GlobalExceptionHandler` 未设定 HTTP 状态码

所有异常处理方法返回的 HTTP 状态码始终是 **200 OK**，错误码只在 JSON body 的 `code` 字段中。

例如未登录的请求返回：
```
HTTP/1.1 200 OK
{"code": 401, "message": "未登录或登录已过期", "data": null}
```

这不符合 RESTful 规范。虽然前端可以正常工作（因为看的是 body.code），但如果未来需要对接其他系统或做 API 网关，这会是个问题。

**建议**：使用 `@ResponseStatus` 或 `ResponseEntity` 让 HTTP 状态码与业务状态码保持一致：

```java
@ExceptionHandler(NotLoginException.class)
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public Result<Void> handleNotLogin(NotLoginException e) {
    return Result.fail(401, "未登录或登录已过期");
}
```

---

### 11. 没有分页参数上限保护

所有列表接口的 `size` 参数只有默认值没有上限：

```java
@RequestParam(defaultValue = "20") int size
```

如果客户端传 `size=999999`，可能导致一次加载全部数据，引发内存溢出或慢查询。

**建议**：在 Controller 或 Service 层加入上限约束：
```java
int safeSize = Math.min(size, 100);
```

---

### 12. `DashboardServiceImpl` 文件体量过大

`DashboardServiceImpl.java` 有 **61KB / ~1800+ 行**。它同时承载了首页看板、财务分析、HR 分析、税务分析四个完全不同的领域逻辑。

**建议**（仅供参考）：按领域拆分为：
- `HomeDashboardService`
- `FinanceDashboardService`  
- `HrDashboardService`
- `TaxDashboardService`

`DashboardController` 可以保持不变，分别注入不同的 Service。

---

### 13. 遗留的无用 `graphql-dgs-codegen-gradle` 依赖

`pom.xml` 中有一个 Netflix GraphQL 代码生成依赖：

```xml
<dependency>
    <groupId>com.netflix.graphql.dgs.codegen</groupId>
    <artifactId>graphql-dgs-codegen-gradle</artifactId>
    <version>8.3.0</version>
</dependency>
```

项目中没有任何 GraphQL 相关代码，这个依赖是多余的，会增加构建体积。

**建议**：直接删除。

---

## 🟢 优先级 P2 — 细节增强

### 14. 缺少接口文档（Swagger/OpenAPI）

项目没有引入 `springdoc-openapi` 或 `swagger`。对毕业设计来说，有一份自动生成的 API 文档会是**很好的加分项**。

**建议**：添加 `springdoc-openapi-starter-webmvc-ui` 依赖，即可在 `/swagger-ui.html` 看到所有接口文档。

---

### 15. Entity 缺少数据库约束注解

Entity 类只用了 `@TableName`、`@TableId`、`@TableField`、`@TableLogic`，但没有 `@Column` 等注解来体现数据库约束（如 `nullable`、`length`）。虽然 MyBatis-Plus 不强制使用这些，但如果用 JPA 或需要生成 DDL 时会有帮助。

对于毕业设计而言这不重要，但值得了解。

---

### 16. `schema.sql` 的 `INSERT INTO user` 缺少 `ON DUPLICATE KEY`

```sql
INSERT INTO `user` ... VALUES ('admin', ..., 'admin', '系统管理员', 1);
```

如果数据库已经有 admin 账号，重复执行 schema.sql 会报唯一键冲突。

**建议**：改为 `INSERT IGNORE INTO ...` 或 `ON DUPLICATE KEY UPDATE`。

---

### 17. 缺少单元测试

`server/src/test/java/` 下只有 Spring Boot 自动生成的骨架，没有实际的测试用例。对毕设答辩来说，至少应该有：
- AuthService 的登录/注册流程测试
- FinanceService 的 CRUD 测试
- 权限拦截测试

**建议**：使用 `@SpringBootTest` + `@MockBean` 编写核心业务流程的测试覆盖。

---

### 18. CORS 白名单过于宽泛

```java
config.setAllowedOriginPatterns(List.of("http://localhost:*"));
```

目前只允许 `localhost` 的任意端口，开发阶段没问题。但如果部署时需要真实域名，记得更新此配置，或用环境变量控制。

---

### 19. `TenantContextHolder` 使用 `ThreadLocal` 但未做异步线程传播

当使用 `@Async` 执行异步任务时（如审计日志事件、AI 摘要刷新），`ThreadLocal` 中的 `companyId` 会丢失。目前代码通过在异步方法的参数中显式传递 `companyId` 来规避了这个问题（如 `AuditLogEventListener`），但如果将来有开发者不注意，就可能踩坑。

**建议**：在 `AsyncConfig` 中配置 `TaskDecorator` 来自动传播 `TenantContextHolder`：

```java
executor.setTaskDecorator(runnable -> {
    Long companyId = TenantContextHolder.getCompanyId();
    return () -> {
        try {
            TenantContextHolder.setCompanyId(companyId);
            runnable.run();
        } finally {
            TenantContextHolder.clear();
        }
    };
});
```

---

### 20. 登录失败锁定基于内存缓存，重启即失效

```java
private final Cache<String, Integer> loginFailCache = Caffeine.newBuilder()
    .expireAfterWrite(Constants.LOGIN_LOCK_MINUTES, TimeUnit.MINUTES)
    .build();
```

Caffeine 是进程内缓存，应用重启后锁定状态会全部丢失。如果攻击者知道这一点，可以通过"撞库 → 等重启 → 继续撞"来绕过锁定。

对毕设来说这不是大问题，但生产环境应改用 Redis 来持久化登录失败计数。

---

## 📊 总结优先级矩阵

| 级别 | 编号 | 简述 | 工作量 |
|---|---|---|---|
| 🔴 P0 | 1 | API Key 明文移除 + 环境变量 | ~15 分钟 |
| 🔴 P0 | 2 | 数据库密码环境变量化 | ~5 分钟 |
| 🔴 P0 | 3 | JWT Secret 加固 | ~5 分钟 |
| 🔴 P0 | 4 | ResetPasswordDTO / ChangePasswordDTO 加 @Pattern | ~10 分钟 |
| 🔴 P0 | 5 | FinanceCreateDTO.type 加 @Pattern | ~5 分钟 |
| 🟡 P1 | 6 | UserServiceImpl 统一用 CurrentSessionService | ~10 分钟 |
| 🟡 P1 | 7 | Map\<String, Integer\> → UpdateStatusDTO | ~15 分钟 |
| 🟡 P1 | 8 | Employee/Tax 筛选参数独立化 | ~30 分钟 |
| 🟡 P1 | 9 | SQL 日志按 profile 隔离 | ~10 分钟 |
| 🟡 P1 | 10 | GlobalExceptionHandler 加 HTTP 状态码 | ~20 分钟 |
| 🟡 P1 | 11 | 分页 size 上限保护 | ~10 分钟 |
| 🟡 P1 | 12 | DashboardServiceImpl 拆分（可选） | ~2 小时 |
| 🟡 P1 | 13 | 删除 graphql-dgs-codegen 依赖 | ~1 分钟 |
| 🟢 P2 | 14 | 引入 Swagger/OpenAPI 文档 | ~30 分钟 |
| 🟢 P2 | 15-20 | 各类细节打磨 | ~2-3 小时 |

---

> 如果你想让我直接动手修复其中某些问题，按优先级告诉我即可。
