package com.pjh.server.service.impl;

import com.pjh.server.exception.BusinessException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class FinanceTemplateWorkbookHelper {

    private FinanceTemplateWorkbookHelper() {
    }

    static void downloadTemplate(HttpServletResponse response) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            buildGuideSheet(workbook);
            buildDataSheet(workbook);
            workbook.write(outputStream);

            String encodedFileName = URLEncoder.encode("财务导入模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
            response.setContentType(FinanceImportTemplateSupport.CONTENT_TYPE);
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);

            ServletOutputStream responseOutputStream = response.getOutputStream();
            responseOutputStream.write(outputStream.toByteArray());
            responseOutputStream.flush();
        } catch (Exception e) {
            throw new BusinessException("模板下载失败");
        }
    }

    private static void buildGuideSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(FinanceImportTemplateSupport.GUIDE_SHEET_NAME);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle sectionStyle = createSectionStyle(workbook);
        CellStyle tableHeaderStyle = createTableHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);
        CellStyle noteStyle = createNoteStyle(workbook);

        createCell(sheet.createRow(0), 0, "财务账本 Excel 导入说明", titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        createCell(sheet.createRow(2), 0, "导入前须知", sectionStyle);
        createCell(sheet.createRow(3), 0, "1. 仅支持 .xlsx 文件，请优先使用系统下载的模板。", noteStyle);
        createCell(sheet.createRow(4), 0, "2. 任意一行校验失败都会整批拒绝导入，请先修正错误后再重新上传。", noteStyle);
        createCell(sheet.createRow(5), 0, "3. 不要改动“导入数据”工作表的表头文字；可以调整列宽，但不要删除列。", noteStyle);
        createCell(sheet.createRow(6), 0, "4. 建议先确认收支类型、金额和日期格式，再批量填写业务分类与备注。", noteStyle);

        Row headerRow = sheet.createRow(8);
        String[] headers = {"字段名称", "是否必填", "填写格式", "允许值", "字段解释", "示例"};
        for (int i = 0; i < headers.length; i++) {
            createCell(headerRow, i, headers[i], tableHeaderStyle);
        }

        int rowIndex = 9;
        for (FinanceImportTemplateSupport.ImportColumn column : FinanceImportTemplateSupport.IMPORT_COLUMNS) {
            Row row = sheet.createRow(rowIndex++);
            createCell(row, 0, column.header(), bodyStyle);
            createCell(row, 1, column.requirementLabel(), bodyStyle);
            createCell(row, 2, column.format(), bodyStyle);
            createCell(row, 3, column.allowedValues(), bodyStyle);
            createCell(row, 4, column.description(), bodyStyle);
            createCell(row, 5, column.example(), bodyStyle);
        }

        createCell(sheet.createRow(rowIndex + 1), 0, "特别提示", sectionStyle);
        createCell(sheet.createRow(rowIndex + 2), 0, "· 收支类型支持中文“收入/支出”和英文“income/expense”，推荐直接使用下拉选项。", noteStyle);
        createCell(sheet.createRow(rowIndex + 3), 0, "· 金额必须大于 0，且不要添加货币符号；如输入 5,000.00，系统会自动识别。", noteStyle);
        createCell(sheet.createRow(rowIndex + 4), 0, "· 发生日期支持 Excel 日期单元格或 yyyy-MM-dd 文本格式。", noteStyle);
        createCell(sheet.createRow(rowIndex + 5), 0, "· 关联项目、备注均为选填，但建议对大额或特殊流水补充说明。", noteStyle);

        int[] columnWidths = {26, 14, 28, 34, 42, 24};
        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }
    }

    private static void buildDataSheet(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet(FinanceImportTemplateSupport.DATA_SHEET_NAME);
        CellStyle requiredHeaderStyle = createHeaderFillStyle(workbook, IndexedColors.LIGHT_YELLOW);
        CellStyle optionalHeaderStyle = createHeaderFillStyle(workbook, IndexedColors.GREY_25_PERCENT);

        Row headerRow = sheet.createRow(0);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        CreationHelper creationHelper = workbook.getCreationHelper();

        for (int i = 0; i < FinanceImportTemplateSupport.IMPORT_COLUMNS.size(); i++) {
            FinanceImportTemplateSupport.ImportColumn column = FinanceImportTemplateSupport.IMPORT_COLUMNS.get(i);
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(column.header());
            cell.setCellStyle(isRequiredColumn(column) ? requiredHeaderStyle : optionalHeaderStyle);
            addHeaderComment(drawing, creationHelper, cell, column);
        }

        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, FinanceImportTemplateSupport.IMPORT_COLUMNS.size() - 1));

        int[] columnWidths = {18, 16, 20, 22, 18, 28};
        for (int i = 0; i < columnWidths.length; i++) {
            sheet.setColumnWidth(i, columnWidths[i] * 256);
        }

        addDropDownValidation(
                sheet,
                1,
                1000,
                0,
                FinanceImportTemplateSupport.TYPE_OPTIONS.toArray(String[]::new)
        );
    }

    private static boolean isRequiredColumn(FinanceImportTemplateSupport.ImportColumn column) {
        return column == FinanceImportTemplateSupport.TYPE_COLUMN
                || column == FinanceImportTemplateSupport.AMOUNT_COLUMN
                || column == FinanceImportTemplateSupport.CATEGORY_COLUMN
                || column == FinanceImportTemplateSupport.DATE_COLUMN;
    }

    private static void addHeaderComment(
            Drawing<?> drawing,
            CreationHelper creationHelper,
            Cell cell,
            FinanceImportTemplateSupport.ImportColumn column
    ) {
        ClientAnchor anchor = creationHelper.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 3);
        anchor.setRow1(0);
        anchor.setRow2(5);

        Comment comment = drawing.createCellComment(anchor);
        String commentText = "字段：%s%n是否必填：%s%n格式：%s%n允许值：%s%n说明：%s%n示例：%s".formatted(
                column.header(),
                column.requirementLabel(),
                column.format(),
                column.allowedValues(),
                column.description(),
                column.example()
        );
        comment.setString(creationHelper.createRichTextString(commentText));
        comment.setAuthor("EMS");
        cell.setCellComment(comment);
    }

    private static void addDropDownValidation(Sheet sheet, int firstRow, int lastRow, int columnIndex, String[] values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        CellRangeAddressList regions = new CellRangeAddressList(firstRow, lastRow, columnIndex, columnIndex);
        DataValidation validation = helper.createValidation(constraint, regions);
        validation.setEmptyCellAllowed(true);
        validation.setSuppressDropDownArrow(false);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private static CellStyle createTitleStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 16);
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createSectionStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 12);
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createTableHeaderStyle(XSSFWorkbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setWrapText(true);
        return style;
    }

    private static CellStyle createBodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        return style;
    }

    private static CellStyle createNoteStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }

    private static CellStyle createHeaderFillStyle(XSSFWorkbook workbook, IndexedColors color) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private static void createCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
}
