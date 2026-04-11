package com.pjh.server.service.impl;

import cn.hutool.core.util.StrUtil;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

final class FinanceImportTemplateSupport {

    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    static final List<String> TYPE_OPTIONS = List.of("收入", "支出");
    static final Set<String> VALID_TYPES = Set.of("income", "expense");

    static final String DATA_SHEET_NAME = "导入数据";
    static final String GUIDE_SHEET_NAME = "填写说明";
    static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final ImportColumn TYPE_COLUMN = new ImportColumn(
            "type",
            "收支类型（必填）",
            List.of("收支类型（必填）", "收支类型"),
            true,
            "必填",
            "下拉选择 / 英文映射",
            "收入、支出 或 income/expense",
            "用于标识该条财务记录是收入还是支出",
            "收入"
    );
    static final ImportColumn AMOUNT_COLUMN = new ImportColumn(
            "amount",
            "金额（必填）",
            List.of("金额（必填）", "金额"),
            true,
            "必填",
            "大于 0 的数字",
            "例如 5000.00",
            "金额必须大于 0，不支持负数和零",
            "5000.00"
    );
    static final ImportColumn CATEGORY_COLUMN = new ImportColumn(
            "category",
            "财务分类（必填）",
            List.of("财务分类（必填）", "财务分类"),
            true,
            "必填",
            "文本",
            "如：销售收入、采购支出、办公费用",
            "用于区分财务流水归属的业务分类",
            "销售收入"
    );
    static final ImportColumn PROJECT_COLUMN = new ImportColumn(
            "project",
            "关联项目（选填）",
            List.of("关联项目（选填）", "关联项目"),
            true,
            "选填",
            "文本",
            "如：春季活动、A 客户项目",
            "用于记录这笔收支关联的具体项目或客户",
            "A 客户续费"
    );
    static final ImportColumn DATE_COLUMN = new ImportColumn(
            "date",
            "发生日期（必填）",
            List.of("发生日期（必填）", "发生日期"),
            true,
            "必填",
            "yyyy-MM-dd 或 Excel 日期",
            "例如 2026-04-01",
            "表示实际发生收支的日期",
            "2026-04-01"
    );
    static final ImportColumn REMARK_COLUMN = new ImportColumn(
            "remark",
            "备注（选填）",
            List.of("备注（选填）", "备注"),
            true,
            "选填",
            "文本",
            "建议控制在 255 字以内",
            "补充说明特殊情况、来源或用途",
            "季度合同回款"
    );

    static final List<ImportColumn> IMPORT_COLUMNS = List.of(
            TYPE_COLUMN,
            AMOUNT_COLUMN,
            CATEGORY_COLUMN,
            PROJECT_COLUMN,
            DATE_COLUMN,
            REMARK_COLUMN
    );

    private FinanceImportTemplateSupport() {
    }

    static String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    static String normalizeType(String rawValue) {
        if (StrUtil.isBlank(rawValue)) {
            return null;
        }

        String normalized = rawValue.trim();
        if ("收入".equals(normalized)) {
            return "income";
        }
        if ("支出".equals(normalized)) {
            return "expense";
        }

        normalized = normalized.toLowerCase();
        return VALID_TYPES.contains(normalized) ? normalized : null;
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
