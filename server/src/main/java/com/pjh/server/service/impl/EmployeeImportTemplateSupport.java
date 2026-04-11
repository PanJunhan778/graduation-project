package com.pjh.server.service.impl;

import cn.hutool.core.util.StrUtil;

import java.time.format.DateTimeFormatter;
import java.util.List;

final class EmployeeImportTemplateSupport {

    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    static final List<String> STATUS_OPTIONS = List.of("在职", "离职");

    static final String DATA_SHEET_NAME = "导入数据";
    static final String GUIDE_SHEET_NAME = "填写说明";
    static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final ImportColumn NAME_COLUMN = new ImportColumn(
            "name",
            "员工姓名（必填）",
            List.of("员工姓名（必填）", "员工姓名"),
            true,
            "必填",
            "文本",
            "如：张三",
            "员工的真实姓名，用于员工名册展示和后续账号绑定",
            "张三"
    );
    static final ImportColumn DEPARTMENT_COLUMN = new ImportColumn(
            "department",
            "所属部门（必填）",
            List.of("所属部门（必填）", "所属部门"),
            true,
            "必填",
            "文本",
            "如：市场部、财务部、行政部",
            "用于区分员工归属的组织部门",
            "市场部"
    );
    static final ImportColumn POSITION_COLUMN = new ImportColumn(
            "position",
            "职位（选填）",
            List.of("职位（选填）", "职位"),
            true,
            "选填",
            "文本",
            "如：招商主管、出纳、招商主管助理",
            "用于补充说明员工岗位职责",
            "招商主管"
    );
    static final ImportColumn SALARY_COLUMN = new ImportColumn(
            "salary",
            "基础薪资（必填）",
            List.of("基础薪资（必填）", "基础薪资"),
            true,
            "必填",
            "大于等于 0 的数字",
            "例如 8000.00",
            "表示员工当前基础薪资，不允许负数",
            "8000.00"
    );
    static final ImportColumn HIRE_DATE_COLUMN = new ImportColumn(
            "hireDate",
            "入职日期（必填）",
            List.of("入职日期（必填）", "入职日期"),
            true,
            "必填",
            "yyyy-MM-dd 或 Excel 日期",
            "例如 2026-04-01",
            "表示员工实际入职日期",
            "2026-04-01"
    );
    static final ImportColumn STATUS_COLUMN = new ImportColumn(
            "status",
            "在职状态（选填，默认在职）",
            List.of("在职状态（选填，默认在职）", "在职状态（选填）", "在职状态"),
            true,
            "选填，默认在职",
            "下拉选择 / 数字映射",
            "在职、离职 或 1/0",
            "留空时默认按“在职”导入",
            "在职"
    );
    static final ImportColumn REMARK_COLUMN = new ImportColumn(
            "remark",
            "备注（选填）",
            List.of("备注（选填）", "备注"),
            true,
            "选填",
            "文本",
            "建议控制在 255 字以内",
            "补充说明员工情况，如试用期、调岗说明等",
            "试用期三个月"
    );

    static final List<ImportColumn> IMPORT_COLUMNS = List.of(
            NAME_COLUMN,
            DEPARTMENT_COLUMN,
            POSITION_COLUMN,
            SALARY_COLUMN,
            HIRE_DATE_COLUMN,
            STATUS_COLUMN,
            REMARK_COLUMN
    );

    private EmployeeImportTemplateSupport() {
    }

    static String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    static Integer parseStatus(String rawValue) {
        if (StrUtil.isBlank(rawValue)) {
            return 1;
        }

        return switch (rawValue.trim()) {
            case "在职", "1" -> 1;
            case "离职", "0" -> 0;
            default -> null;
        };
    }

    record ImportColumn(
            String key,
            String header,
            List<String> aliases,
            boolean requiredHeader,
            String requirementLabel,
            String format,
            String allowedValues,
            String description,
            String example
    ) {
    }
}
