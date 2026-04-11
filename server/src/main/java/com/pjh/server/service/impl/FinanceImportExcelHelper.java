package com.pjh.server.service.impl;

import cn.hutool.core.util.StrUtil;
import com.pjh.server.common.Result;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.FinanceRecordMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class FinanceImportExcelHelper {

    private FinanceImportExcelHelper() {
    }

    static Result<?> importExcel(MultipartFile file, FinanceRecordMapper financeRecordMapper) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要导入的文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (StrUtil.isBlank(originalFilename) || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return Result.fail("仅支持导入 .xlsx 格式文件");
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<FinanceRecord> records = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(FinanceImportTemplateSupport.DATA_SHEET_NAME);
            if (sheet == null) {
                return Result.fail(
                        400,
                        "模板结构不正确",
                        List.of(errorEntry(1, "缺少“导入数据”工作表，请重新下载系统模板"))
                );
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return Result.fail(
                        400,
                        "模板结构不正确",
                        List.of(errorEntry(1, "导入数据工作表缺少表头，请重新下载系统模板"))
                );
            }

            Map<String, Integer> headerIndexes = resolveHeaderIndexes(headerRow, formatter);
            List<String> missingHeaders = FinanceImportTemplateSupport.IMPORT_COLUMNS.stream()
                    .filter(FinanceImportTemplateSupport.ImportColumn::requiredHeader)
                    .filter(column -> !headerIndexes.containsKey(column.key()))
                    .map(FinanceImportTemplateSupport.ImportColumn::header)
                    .toList();

            if (!missingHeaders.isEmpty()) {
                return Result.fail(
                        400,
                        "模板表头缺失",
                        List.of(errorEntry(1, "缺少必需表头：" + String.join("、", missingHeaders)))
                );
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row, headerIndexes, formatter)) {
                    continue;
                }

                int excelRowNum = rowIndex + 1;
                List<String> rowErrors = new ArrayList<>();

                String typeRaw = readCellValue(row, headerIndexes.get(FinanceImportTemplateSupport.TYPE_COLUMN.key()), formatter);
                String amountRaw = readCellValue(row, headerIndexes.get(FinanceImportTemplateSupport.AMOUNT_COLUMN.key()), formatter);
                String category = readCellValue(row, headerIndexes.get(FinanceImportTemplateSupport.CATEGORY_COLUMN.key()), formatter);
                String project = readCellValue(row, headerIndexes.get(FinanceImportTemplateSupport.PROJECT_COLUMN.key()), formatter);
                Cell dateCell = readCell(row, headerIndexes.get(FinanceImportTemplateSupport.DATE_COLUMN.key()));
                String remark = readCellValue(row, headerIndexes.get(FinanceImportTemplateSupport.REMARK_COLUMN.key()), formatter);

                String type = FinanceImportTemplateSupport.normalizeType(typeRaw);
                if (type == null) {
                    rowErrors.add("收支类型必须为 收入、支出 或 income/expense");
                }

                BigDecimal amount = null;
                if (StrUtil.isBlank(amountRaw)) {
                    rowErrors.add("金额不能为空");
                } else {
                    try {
                        amount = new BigDecimal(amountRaw.replace(",", ""));
                    } catch (NumberFormatException ex) {
                        rowErrors.add("金额格式不正确");
                    }
                }
                if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
                    rowErrors.add("金额必须大于 0");
                }

                if (StrUtil.isBlank(category)) {
                    rowErrors.add("财务分类不能为空");
                }

                LocalDate date = null;
                if (!hasCellValue(dateCell, formatter)) {
                    rowErrors.add("发生日期不能为空");
                } else {
                    try {
                        date = parseDateCell(dateCell, formatter);
                    } catch (BusinessException ex) {
                        rowErrors.add(ex.getMessage());
                    }
                }

                if (!rowErrors.isEmpty()) {
                    errors.add(errorEntry(excelRowNum, String.join("；", rowErrors)));
                    continue;
                }

                FinanceRecord record = new FinanceRecord();
                record.setType(type);
                record.setAmount(amount);
                record.setCategory(category.trim());
                record.setProject(FinanceImportTemplateSupport.trimToNull(project));
                record.setDate(date);
                record.setRemark(FinanceImportTemplateSupport.trimToNull(remark));
                records.add(record);
            }
        } catch (Exception e) {
            return Result.fail("文件解析失败，请检查文件格式是否为 .xlsx");
        }

        if (records.isEmpty() && errors.isEmpty()) {
            return Result.fail("文件中没有可导入的数据");
        }

        if (!errors.isEmpty()) {
            return Result.fail(400, "数据校验失败，全部未导入", errors);
        }

        for (FinanceRecord record : records) {
            financeRecordMapper.insert(record);
        }

        return Result.success("成功导入 " + records.size() + " 条记录", null);
    }

    private static Map<String, Integer> resolveHeaderIndexes(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> indexes = new HashMap<>();
        for (Cell cell : headerRow) {
            String headerText = formatter.formatCellValue(cell).trim();
            if (headerText.isEmpty()) {
                continue;
            }

            for (FinanceImportTemplateSupport.ImportColumn column : FinanceImportTemplateSupport.IMPORT_COLUMNS) {
                if (column.aliases().contains(headerText)) {
                    indexes.put(column.key(), cell.getColumnIndex());
                    break;
                }
            }
        }
        return indexes;
    }

    private static boolean isBlankRow(Row row, Map<String, Integer> headerIndexes, DataFormatter formatter) {
        if (row == null) {
            return true;
        }

        for (Integer columnIndex : headerIndexes.values()) {
            if (hasCellValue(readCell(row, columnIndex), formatter)) {
                return false;
            }
        }
        return true;
    }

    private static Cell readCell(Row row, Integer columnIndex) {
        if (row == null || columnIndex == null) {
            return null;
        }
        return row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private static String readCellValue(Row row, Integer columnIndex, DataFormatter formatter) {
        Cell cell = readCell(row, columnIndex);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean hasCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return false;
        }
        return StrUtil.isNotBlank(formatter.formatCellValue(cell));
    }

    private static LocalDate parseDateCell(Cell cell, DataFormatter formatter) {
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            Instant instant = cell.getDateCellValue().toInstant();
            return instant.atZone(ZoneId.systemDefault()).toLocalDate();
        }

        String raw = formatter.formatCellValue(cell).trim();
        try {
            return LocalDate.parse(raw, FinanceImportTemplateSupport.DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("发生日期格式不正确，请使用 yyyy-MM-dd");
        }
    }

    private static Map<String, Object> errorEntry(int row, String error) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("row", row);
        entry.put("error", error);
        return entry;
    }
}
