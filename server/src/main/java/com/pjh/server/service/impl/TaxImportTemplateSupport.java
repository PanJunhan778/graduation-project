package com.pjh.server.service.impl;

import cn.hutool.core.util.StrUtil;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class TaxImportTemplateSupport {

    static final Pattern TAX_PERIOD_PATTERN =
            Pattern.compile("^\\d{4}-(0[1-9]|1[0-2]|Q[1-4]|Annual)$");
    static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    static final List<String> DECLARATION_TYPE_OPTIONS = List.of("日常/预缴", "年度汇算清缴");
    static final List<String> PAYMENT_STATUS_OPTIONS = List.of("待缴纳", "已缴纳", "免征/零申报");
    static final Set<Integer> VALID_PAYMENT_STATUSES = Set.of(0, 1, 2);
    static final Set<String> VALID_DECLARATION_TYPES = Set.copyOf(DECLARATION_TYPE_OPTIONS);

    static final String DATA_SHEET_NAME = "导入数据";
    static final String GUIDE_SHEET_NAME = "填写说明";
    static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final ImportColumn TAX_PERIOD_COLUMN = new ImportColumn(
            "taxPeriod",
            "税款所属期（必填）",
            List.of("税款所属期（必填）", "税款所属期"),
            true,
            "必填",
            "支持 YYYY-MM、YYYY-Q1~Q4、YYYY-Annual",
            "2026-03 / 2026-Q2 / 2026-Annual",
            "表示税款所属的月份、季度或年度",
            "2026-Q2"
    );
    static final ImportColumn TAX_TYPE_COLUMN = new ImportColumn(
            "taxType",
            "税种（必填）",
            List.of("税种（必填）", "税种"),
            true,
            "必填",
            "文本",
            "如：增值税、企业所得税、个人所得税",
            "具体税费名称，作为后续统计与税务日历的基础维度",
            "增值税"
    );
    static final ImportColumn DECLARATION_TYPE_COLUMN = new ImportColumn(
            "declarationType",
            "申报类型（选填）",
            List.of("申报类型（选填）", "申报类型"),
            true,
            "选填",
            "下拉选择",
            "日常/预缴、年度汇算清缴",
            "用于描述该条税务记录对应的申报场景",
            "日常/预缴"
    );
    static final ImportColumn TAX_AMOUNT_COLUMN = new ImportColumn(
            "taxAmount",
            "税额（必填）",
            List.of("税额（必填）", "税额"),
            true,
            "必填",
            "数字，可为负数、零或正数",
            "例如 -200.00、0.00、1500.00",
            "负数表示退税，零表示零税额，正数表示正常应缴税额",
            "1500.00"
    );
    static final ImportColumn PAYMENT_STATUS_COLUMN = new ImportColumn(
            "paymentStatus",
            "缴纳状态（必填）",
            List.of("缴纳状态（必填）", "缴纳状态"),
            true,
            "必填",
            "下拉选择 / 数字映射",
            "待缴纳、已缴纳、免征/零申报 或 0/1/2",
            "用于决定是否允许填写缴纳日期，并影响后续税务健康统计",
            "已缴纳"
    );
    static final ImportColumn PAYMENT_DATE_COLUMN = new ImportColumn(
            "paymentDate",
            "缴纳日期（已缴纳时必填）",
            List.of("缴纳日期（已缴纳时必填）", "缴纳日期"),
            true,
            "条件必填",
            "yyyy-MM-dd 或 Excel 日期",
            "仅当缴纳状态为“已缴纳/1”时允许填写",
            "实际打款日期；待缴纳、免征、零申报时必须留空",
            "2026-04-15"
    );
    static final ImportColumn REMARK_COLUMN = new ImportColumn(
            "remark",
            "备注（选填）",
            List.of("备注（选填）", "备注"),
            true,
            "选填",
            "文本",
            "建议控制在 255 字以内",
            "补充说明特殊情况，如退税原因、免征说明等",
            "汇算清缴退税"
    );

    static final List<ImportColumn> IMPORT_COLUMNS = List.of(
            TAX_PERIOD_COLUMN,
            TAX_TYPE_COLUMN,
            DECLARATION_TYPE_COLUMN,
            TAX_AMOUNT_COLUMN,
            PAYMENT_STATUS_COLUMN,
            PAYMENT_DATE_COLUMN,
            REMARK_COLUMN
    );

    private TaxImportTemplateSupport() {
    }

    static String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    static Integer parsePaymentStatus(String rawValue) {
        if (StrUtil.isBlank(rawValue)) {
            return null;
        }

        return switch (rawValue.trim()) {
            case "待缴纳", "0" -> 0;
            case "已缴纳", "1" -> 1;
            case "免征", "零申报", "免征/零申报", "2" -> 2;
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