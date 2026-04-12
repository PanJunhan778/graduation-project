-- ============================================
-- 智能轻量化企业管理系统 - 数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS `ems` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `ems`;

-- 1. 公司表
CREATE TABLE IF NOT EXISTS `company` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(100) NOT NULL COMMENT '公司名称',
    `description`   TEXT         NULL COMMENT '公司描述（AI全局Prompt上下文）',
    `industry`      VARCHAR(50)  NULL COMMENT '所属行业',
    `company_code`  VARCHAR(6)   NOT NULL COMMENT '6位唯一企业码',
    `taxpayer_type` VARCHAR(20)  NULL COMMENT '纳税人性质',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    `created_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_company_code` (`company_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司表';

-- 2. 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id`    BIGINT       NULL COMMENT '公司ID（NULL代表系统Admin）',
    `username`      VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`      VARCHAR(255) NOT NULL COMMENT '加密密码',
    `role`          VARCHAR(20)  NOT NULL COMMENT '角色：admin/owner/staff',
    `real_name`     VARCHAR(50)  NULL COMMENT '真实姓名',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    `created_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `idx_username` (`username`),
    INDEX `idx_company_id` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 3. 财务流水表
CREATE TABLE IF NOT EXISTS `finance_record` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `company_id`    BIGINT        NOT NULL COMMENT '公司ID',
    `type`          VARCHAR(20)   NOT NULL COMMENT '收支类型：income/expense',
    `amount`        DECIMAL(15,2) NOT NULL COMMENT '交易金额（必须>0）',
    `category`      VARCHAR(50)   NOT NULL COMMENT '财务分类',
    `project`       VARCHAR(100)  NULL COMMENT '关联项目',
    `date`          DATE          NOT NULL COMMENT '业务发生日期',
    `remark`        VARCHAR(255)  NULL COMMENT '备注',
    `created_by`    BIGINT        NULL COMMENT '录入人ID',
    `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by`    BIGINT        NULL COMMENT '最后修改人ID',
    `updated_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_company_id` (`company_id`),
    INDEX `idx_category` (`category`),
    INDEX `idx_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务流水表';

-- 4. 员工表
CREATE TABLE IF NOT EXISTS `employee` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `company_id`    BIGINT        NOT NULL COMMENT '公司ID',
    `name`          VARCHAR(50)   NOT NULL COMMENT '员工姓名',
    `department`    VARCHAR(50)   NULL COMMENT '归属部门',
    `position`      VARCHAR(50)   NULL COMMENT '职位',
    `salary`        DECIMAL(15,2) NULL DEFAULT 0.00 COMMENT '基础薪资',
    `hire_date`     DATE          NULL COMMENT '入职日期',
    `user_id`       BIGINT        NULL COMMENT '关联系统账号',
    `status`        TINYINT       NOT NULL DEFAULT 1 COMMENT '1在职 0离职',
    `remark`        VARCHAR(255)  NULL COMMENT '备注',
    `created_by`    BIGINT        NULL,
    `created_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by`    BIGINT        NULL,
    `updated_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_company_id` (`company_id`),
    INDEX `idx_department` (`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工表';

-- 5. 税费表
CREATE TABLE IF NOT EXISTS `tax_record` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT,
    `company_id`       BIGINT        NOT NULL COMMENT '公司ID',
    `tax_period`       VARCHAR(20)   NOT NULL COMMENT '税款所属期',
    `tax_type`         VARCHAR(50)   NOT NULL COMMENT '税种',
    `declaration_type` VARCHAR(20)   NULL COMMENT '申报类型',
    `tax_amount`       DECIMAL(15,2) NOT NULL COMMENT '税额（负数代表退税）',
    `payment_status`   TINYINT       NOT NULL COMMENT '0待缴 1已缴 2免征',
    `payment_date`     DATE          NULL COMMENT '实际缴款日期',
    `remark`           VARCHAR(255)  NULL,
    `created_by`       BIGINT        NULL,
    `created_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_by`       BIGINT        NULL,
    `updated_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`       TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_company_period_type` (`company_id`, `tax_period`, `tax_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='税费表';

-- 6. 审计日志表
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id`      BIGINT       NOT NULL,
    `user_id`         BIGINT       NOT NULL COMMENT '操作人ID',
    `module`          VARCHAR(50)  NOT NULL COMMENT '模块：finance/employee/tax',
    `operation_type`  VARCHAR(20)  NOT NULL COMMENT '操作类型：UPDATE/DELETE',
    `target_id`       BIGINT       NOT NULL COMMENT '被操作数据主键',
    `field_name`      VARCHAR(50)  NULL COMMENT '变更字段名',
    `old_value`       TEXT         NULL,
    `new_value`       TEXT         NULL,
    `operation_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `remark`          VARCHAR(255) NULL,
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_company_id` (`company_id`),
    INDEX `idx_time` (`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

-- 7. AI 会话记录表
CREATE TABLE IF NOT EXISTS `ai_chat_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id`    BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL COMMENT '发起对话的用户ID',
    `session_id`    VARCHAR(36)  NOT NULL COMMENT '会话UUID',
    `role`          VARCHAR(20)  NOT NULL COMMENT 'user/assistant/system',
    `message_type`  VARCHAR(32)  NOT NULL DEFAULT 'text' COMMENT 'text/markdown/action_required/action_result',
    `content`       LONGTEXT     NOT NULL COMMENT '消息内容',
    `metadata_json` LONGTEXT     NULL COMMENT '元数据JSON',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_company_id` (`company_id`),
    INDEX `idx_session_id` (`session_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话记录表';

CREATE TABLE IF NOT EXISTS `ai_pending_action` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id`      BIGINT       NOT NULL COMMENT '公司ID',
    `user_id`         BIGINT       NOT NULL COMMENT '发起动作的用户ID',
    `session_id`      VARCHAR(36)  NOT NULL COMMENT '会话UUID',
    `chat_message_id` BIGINT       NULL COMMENT '关联卡片消息ID',
    `action_type`     VARCHAR(64)  NOT NULL COMMENT '动作类型',
    `confirm_token`   VARCHAR(64)  NOT NULL COMMENT '确认令牌',
    `old_value`       LONGTEXT     NULL COMMENT '旧值',
    `proposed_value`  LONGTEXT     NOT NULL COMMENT '拟更新值',
    `status`          VARCHAR(20)  NOT NULL COMMENT 'pending/approved/rejected/expired',
    `expires_at`      DATETIME     NOT NULL COMMENT '过期时间',
    `processed_by`    BIGINT       NULL COMMENT '处理人ID',
    `processed_at`    DATETIME     NULL COMMENT '处理时间',
    `created_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_confirm_token` (`confirm_token`),
    INDEX `idx_company_status` (`company_id`, `status`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI待确认动作表';

-- ============================================
-- 预置数据：系统管理员账号
-- 密码: Admin@123 (BCrypt加密)
-- ============================================
INSERT INTO `user` (`username`, `password`, `role`, `real_name`, `status`)
VALUES ('admin', '$2a$10$FNN1INPp5yCgsdxCgLyxRezZIyUO4ExJWr6ZO9lz5q46vbVCZ4pey', 'admin', '系统管理员', 1);
