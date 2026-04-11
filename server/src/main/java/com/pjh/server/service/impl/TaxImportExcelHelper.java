package com.pjh.server.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pjh.server.common.Result;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.TaxRecordMapper;
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

final class TaxImportExcelHelper {

    private TaxImportExcelHelper() {
    }

    static Result<?> importExcel(MultipartFile file, TaxRecordMapper taxRecordMapper) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要导入的文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (StrUtil.isBlank(originalFilename) || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return Result.fail("仅支持导入 .xlsx 格式文件");
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<ImportCandidate> candidates = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheet(TaxImportTemplateSupport.DATA_SHEET_NAME);
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
            List<String> missingHeaders = TaxImportTemplateSupport.IMPORT_COLUMNS.stream()
                    .filter(TaxImportTemplateSupport.ImportColumn::requiredHeader)
                    .filter(column -> !headerIndexes.containsKey(column.key()))
                    .map(TaxImportTemplateSupport.ImportColumn::header)
                    .toList();

            if (!missingHeaders.isEmpty()) {
                return Result.fail(
                        400,
                        "模板表头缺失",
                        List.of(errorEntry(1, "缺少必需表头：" + String.join("、", missingHeaders)))
                );
            }

            Map<String, Integer> seenKeys = new LinkedHashMap<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row, headerIndexes, formatter)) {
                    continue;
                }

                int excelRowNum = rowIndex + 1;
                List<String> rowErrors = new ArrayList<>();

                String taxPeriod = readCellValue(row, headerIndexes.get(TaxImportTemplateSupport.TAX_PERIOD_COLUMN.key()), formatter);
                String taxType = readCellValue(row, headerIndexes.get(TaxImportTemplateSupport.TAX_TYPE_COLUMN.key()), formatter);
                String declarationType = readCellValue(row, headerIndexes.get(TaxImportTemplateSupport.DECLARATION_TYPE_COLUMN.key()), formatter);
                String taxAmountRaw = readCellValue(row, headerIndexes.get(TaxImportTemplateSupport.TAX_AMOUNT_COLUMN.key()), formatter);
                String paymentStatusRaw = readCellValue(row, headerIndexes.get(TaxImportTemplateSupport.PAYMENT_STATUS_COLUMN.key()), formatter);
                Cell paymentDateCell = readCell(row, headerIndexes.get(TaxImportTemplateSupport.PAYMENT_DATE_COLUMN.key()));
                String remark = readCellValue(row, headerIndexes.get(TaxImportTemplateSupport.REMARK_COLUMN.key()), formatter);

                if (StrUtil.isBlank(taxPeriod)) {
                    rowErrors.add("税款所属期不能为空");
                } else if (!TaxImportTemplateSupport.TAX_PERIOD_PATTERN.matcher(taxPeriod).matches()) {
                    rowErrors.add("税款所属期格式不正确，需为 YYYY-MM、YYYY-Q1~Q4 或 YYYY-Annual");
                }

                if (StrUtil.isBlank(taxType)) {
                    rowErrors.add("税种不能为空");
                }

                if (StrUtil.isNotBlank(declarationType)
                        && !TaxImportTemplateSupport.VALID_DECLARATION_TYPES.contains(declarationType)) {
                    rowErrors.add("申报类型只能填写“日常/预缴”或“年度汇算清缴”");
                }

                BigDecimal taxAmount = null;
                if (StrUtil.isBlank(taxAmountRaw)) {
                    rowErrors.add("税额不能为空");
                } else {
                    try {
                        taxAmount = new BigDecimal(taxAmountRaw.replace(",", ""));
                    } catch (NumberFormatException ex) {
                        rowErrors.add("税额格式不正确");
                    }
                }

                Integer paymentStatus = TaxImportTemplateSupport.parsePaymentStatus(paymentStatusRaw);
                if (paymentStatus == null) {
                    rowErrors.add("缴纳状态必须为 待缴纳、已缴纳、免征/零申报 或 0/1/2");
                }

                LocalDate paymentDate = null;
                boolean hasPaymentDateInput = hasCellValue(paymentDateCell, formatter);
                if (hasPaymentDateInput) {
                    try {
                        paymentDate = parseDateCell(paymentDateCell, formatter);
                    } catch (BusinessException ex) {
                        rowErrors.add(ex.getMessage());
                    }
                }

                if (paymentStatus != null) {
                    if (paymentStatus == 1 && paymentDate == null) {
                        rowErrors.add("已缴纳状态必须填写缴纳日期");
                    }
                    if (paymentStatus != 1 && hasPaymentDateInput) {
                        rowErrors.add("待缴纳、免征或零申报状态不能填写缴纳日期");
                    }
                }

                String dedupeKey = buildDuplicateKey(taxPeriod, taxType);
                if (StrUtil.isNotBlank(dedupeKey)) {
                    Integer firstRow = seenKeys.putIfAbsent(dedupeKey, excelRowNum);
                    if (firstRow != null) {
                        rowErrors.add("所属期+税种重复（与第 " + firstRow + " 行重复）");
                    }
                }

                if (!rowErrors.isEmpty()) {
                    errors.add(errorEntry(excelRowNum, String.join("；", rowErrors)));
                    continue;
                }

                TaxRecord record = new TaxRecord();
                record.setTaxPeriod(taxPeriod);
                record.setTaxType(taxType);
                record.setDeclarationType(TaxImportTemplateSupport.trimToNull(declarationType));
                record.setTaxAmount(taxAmount);
                record.setPaymentStatus(paymentStatus);
                record.setPaymentDate(paymentDate);
                record.setRemark(TaxImportTemplateSupport.trimToNull(remark));
                candidates.add(new ImportCandidate(excelRowNum, record));
            }
        } catch (Exception e) {
            return Result.fail("文件解析失败，请检查文件格式是否为 .xlsx");
        }

        if (candidates.isEmpty() && errors.isEmpty()) {
            return Result.fail("文件中没有可导入的数据");
        }

        if (!errors.isEmpty()) {
            return Result.fail(400, "数据校验失败，全部未导入", errors);
        }

        for (ImportCandidate candidate : candidates) {
            TaxRecord record = candidate.record();
            Long count = taxRecordMapper.selectCount(new LambdaQueryWrapper<TaxRecord>()
                    .eq(TaxRecord::getTaxPeriod, record.getTaxPeriod())
                    .eq(TaxRecord::getTaxType, record.getTaxType()));
            if (count != null && count > 0) {
                errors.add(errorEntry(candidate.rowNum(), "所属期+税种重复，系统中已存在相同记录"));
            }
        }

        if (!errors.isEmpty()) {
            return Result.fail(400, "数据校验失败，全部未导入", errors);
        }

        for (ImportCandidate candidate : candidates) {
            taxRecordMapper.insert(candidate.record());
        }

        return Result.success("成功导入 " + candidates.size() + " 条税务记录", null);
    }

    private static Map<String, Integer> resolveHeaderIndexes(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> indexes = new HashMap<>();
        for (Cell cell : headerRow) {
            String headerText = formatter.formatCellValue(cell).trim();
            if (headerText.isEmpty()) {
                continue;
            }

            for (TaxImportTemplateSupport.ImportColumn column : TaxImportTemplateSupport.IMPORT_COLUMNS) {
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
            return LocalDate.parse(raw, TaxImportTemplateSupport.DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("缴纳日期格式不正确，请使用 yyyy-MM-dd");
        }
    }

    private static String buildDuplicateKey(String taxPeriod, String taxType) {
        if (StrUtil.isBlank(taxPeriod) || StrUtil.isBlank(taxType)) {
            return null;
        }
        return taxPeriod + "||" + taxType;
    }

    private static Map<String, Object> errorEntry(int row, String error) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("row", row);
        entry.put("error", error);
        return entry;
    }

    private record ImportCandidate(int rowNum, TaxRecord record) {
    }
}