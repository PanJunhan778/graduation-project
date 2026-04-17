package com.pjh.server.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.audit.AuditOperationService;
import com.pjh.server.audit.AuditUpdate;
import com.pjh.server.audit.DeleteAuditMetadata;
import com.pjh.server.audit.DeleteAuditMetadataResolver;
import com.pjh.server.common.Result;
import com.pjh.server.dashboard.HomeAiSummarySnapshotInvalidationPublisher;
import com.pjh.server.dto.FinanceCreateDTO;
import com.pjh.server.entity.FinanceRecord;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.FinanceRecordMapper;
import com.pjh.server.service.FinanceService;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.FinanceRecordVO;
import com.pjh.server.vo.FinanceRecycleBinVO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private static final String[] AUDIT_FIELDS = {"type", "amount", "category", "project", "date", "remark"};
    private static final Set<String> VALID_TYPES = Set.of("income", "expense");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final FinanceRecordMapper financeRecordMapper;
    private final AuditOperationService auditOperationService;
    private final HomeAiSummarySnapshotInvalidationPublisher homeAiSummarySnapshotInvalidationPublisher;
    private final DeleteAuditMetadataResolver deleteAuditMetadataResolver;
    private final CurrentSessionService currentSessionService;

    @Override
    public IPage<FinanceRecordVO> listRecords(
            int page,
            int size,
            String type,
            String category,
            LocalDate startDate,
            LocalDate endDate
    ) {
        LambdaQueryWrapper<FinanceRecord> wrapper = new LambdaQueryWrapper<FinanceRecord>()
                .eq(StrUtil.isNotBlank(type), FinanceRecord::getType, type)
                .eq(StrUtil.isNotBlank(category), FinanceRecord::getCategory, category)
                .ge(startDate != null, FinanceRecord::getDate, startDate)
                .le(endDate != null, FinanceRecord::getDate, endDate)
                .orderByDesc(FinanceRecord::getDate)
                .orderByDesc(FinanceRecord::getId);

        IPage<FinanceRecord> recordPage = financeRecordMapper.selectPage(new Page<>(page, size), wrapper);
        return recordPage.convert(this::toVO);
    }

    @Override
    public IPage<FinanceRecycleBinVO> listRecycleBinRecords(int page, int size) {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        List<FinanceRecord> deletedRecords = financeRecordMapper.selectDeletedByCompanyId(companyId);
        return buildRecycleBinPage(page, size, deletedRecords, companyId);
    }

    @Override
    @Transactional
    public void createRecord(FinanceCreateDTO dto) {
        FinanceRecord record = new FinanceRecord();
        applyUpsert(record, dto);
        financeRecordMapper.insert(record);
        auditOperationService.publishCreate("finance", record.getId(), record, AUDIT_FIELDS);
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    @AuditUpdate(
            module = "finance",
            fields = {"type", "amount", "category", "project", "date", "remark"}
    )
    public void updateRecord(Long id, FinanceCreateDTO dto) {
        FinanceRecord record = financeRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }

        applyUpsert(record, dto);
        financeRecordMapper.updateById(record);
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    public void deleteRecord(Long id) {
        FinanceRecord record = financeRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }
        financeRecordMapper.deleteById(id);
        auditOperationService.publishDelete("finance", id, record, AUDIT_FIELDS);
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的数据");
        }
        List<FinanceRecord> records = financeRecordMapper.selectBatchIds(ids);
        financeRecordMapper.deleteBatchIds(ids);
        records.forEach(record -> auditOperationService.publishDelete("finance", record.getId(), record, AUDIT_FIELDS));
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    public void restoreRecord(Long id) {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        FinanceRecord deletedRecord = financeRecordMapper.selectDeletedById(companyId, id);
        if (deletedRecord == null) {
            throw new BusinessException("回收站中未找到该财务记录");
        }

        int affected = financeRecordMapper.restoreDeletedById(companyId, id, userId);
        if (affected <= 0) {
            throw new BusinessException("财务记录恢复失败");
        }

        auditOperationService.publishRestore("finance", id, deletedRecord, AUDIT_FIELDS);
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    public int batchRestore(List<Long> ids) {
        List<Long> normalizedIds = normalizeIds(ids);
        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        List<FinanceRecord> deletedRecords = financeRecordMapper.selectDeletedByIds(companyId, normalizedIds);
        if (deletedRecords.isEmpty()) {
            throw new BusinessException("未找到可恢复的财务记录");
        }

        List<Long> restorableIds = deletedRecords.stream()
                .map(FinanceRecord::getId)
                .distinct()
                .toList();
        int affected = financeRecordMapper.restoreDeletedBatch(companyId, restorableIds, userId);
        if (affected <= 0) {
            throw new BusinessException("财务记录恢复失败");
        }

        deletedRecords.forEach(record -> auditOperationService.publishRestore("finance", record.getId(), record, AUDIT_FIELDS));
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
        return affected;
    }

    @Override
    @Transactional
    public Result<?> importExcel(MultipartFile file) {
        Result<?> result = FinanceImportExcelHelper.importExcel(file, financeRecordMapper);
        if (result.getCode() == 200) {
            homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
        }
        return result;
    }

    @SuppressWarnings("unused")
    private Result<?> importExcelLegacy(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要导入的文件");
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<FinanceRecord> records = new ArrayList<>();

        try (ExcelReader reader = ExcelUtil.getReader(file.getInputStream())) {
            List<Map<String, Object>> rows = reader.readAll();

            if (rows.isEmpty()) {
                return Result.fail("文件中没有数据");
            }

            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                int rowNum = i + 2;

                String type = Convert.toStr(row.get("收支类型"));
                Object amountObj = row.get("金额");
                String category = Convert.toStr(row.get("财务分类"));
                String project = Convert.toStr(row.get("关联项目"));
                Object dateObj = row.get("发生日期");
                String remark = Convert.toStr(row.get("备注"));

                if (StrUtil.isBlank(type)) {
                    errors.add(errorEntry(rowNum, "收支类型不能为空"));
                    continue;
                }
                type = type.trim();
                if ("收入".equals(type)) {
                    type = "income";
                } else if ("支出".equals(type)) {
                    type = "expense";
                } else {
                    type = type.toLowerCase();
                }
                if (!VALID_TYPES.contains(type)) {
                    errors.add(errorEntry(rowNum, "收支类型必须为 收入/支出 或 income/expense"));
                    continue;
                }

                if (amountObj == null) {
                    errors.add(errorEntry(rowNum, "金额不能为空"));
                    continue;
                }
                BigDecimal amount;
                try {
                    amount = Convert.toBigDecimal(amountObj);
                } catch (Exception e) {
                    errors.add(errorEntry(rowNum, "金额格式不正确"));
                    continue;
                }
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(errorEntry(rowNum, "金额必须大于 0"));
                    continue;
                }

                if (StrUtil.isBlank(category)) {
                    errors.add(errorEntry(rowNum, "财务分类不能为空"));
                    continue;
                }

                if (dateObj == null) {
                    errors.add(errorEntry(rowNum, "发生日期不能为空"));
                    continue;
                }
                LocalDate date;
                try {
                    if (dateObj instanceof java.util.Date dateValue) {
                        date = dateValue.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    } else {
                        date = LocalDate.parse(Convert.toStr(dateObj).trim(), DATE_FMT);
                    }
                } catch (Exception e) {
                    errors.add(errorEntry(rowNum, "日期格式不正确，请使用 yyyy-MM-dd"));
                    continue;
                }

                FinanceRecord record = new FinanceRecord();
                record.setType(type);
                record.setAmount(amount);
                record.setCategory(category.trim());
                record.setProject(StrUtil.isBlank(project) ? null : project.trim());
                record.setDate(date);
                record.setRemark(StrUtil.isBlank(remark) ? null : remark.trim());
                records.add(record);
            }
        } catch (Exception e) {
            log.error("Excel 解析失败", e);
            return Result.fail("文件解析失败，请检查文件格式是否为 .xlsx");
        }

        if (!errors.isEmpty()) {
            return Result.fail(400, "数据校验失败，全部未导入", errors);
        }

        for (FinanceRecord record : records) {
            financeRecordMapper.insert(record);
        }

        return Result.success("成功导入 " + records.size() + " 条记录", null);
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        FinanceTemplateWorkbookHelper.downloadTemplate(response);
    }

    @SuppressWarnings("unused")
    private void downloadTemplateLegacy(HttpServletResponse response) {
        try (ExcelWriter writer = ExcelUtil.getWriter(true)) {
            writer.addHeaderAlias("type", "收支类型");
            writer.addHeaderAlias("amount", "金额");
            writer.addHeaderAlias("category", "财务分类");
            writer.addHeaderAlias("project", "关联项目");
            writer.addHeaderAlias("date", "发生日期");
            writer.addHeaderAlias("remark", "备注");

            List<Map<String, Object>> sampleData = new ArrayList<>();
            Map<String, Object> sample = new LinkedHashMap<>();
            sample.put("收支类型", "income");
            sample.put("金额", "5000.00");
            sample.put("财务分类", "销售收入");
            sample.put("关联项目", "XX项目");
            sample.put("发生日期", "2026-04-01");
            sample.put("备注", "示例数据，请删除此行");
            sampleData.add(sample);

            writer.write(sampleData, true);
            writer.setColumnWidth(0, 15);
            writer.setColumnWidth(1, 15);
            writer.setColumnWidth(2, 18);
            writer.setColumnWidth(3, 18);
            writer.setColumnWidth(4, 18);
            writer.setColumnWidth(5, 25);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String fileName = URLEncoder.encode("财务导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

            ServletOutputStream out = response.getOutputStream();
            writer.flush(out);
            out.flush();
        } catch (Exception e) {
            log.error("模板下载失败", e);
            throw new BusinessException("模板下载失败");
        }
    }

    private void validateType(String type) {
        if (!VALID_TYPES.contains(type)) {
            throw new BusinessException("收支类型必须为 income 或 expense");
        }
    }

    private void applyUpsert(FinanceRecord record, FinanceCreateDTO dto) {
        String normalizedType = normalizeRequired(dto.getType());
        validateType(normalizedType);

        record.setType(normalizedType);
        record.setAmount(dto.getAmount());
        record.setCategory(normalizeRequired(dto.getCategory()));
        record.setProject(trimToNull(dto.getProject()));
        record.setDate(dto.getDate());
        record.setRemark(trimToNull(dto.getRemark()));
    }

    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private FinanceRecordVO toVO(FinanceRecord record) {
        FinanceRecordVO vo = new FinanceRecordVO();
        vo.setId(record.getId());
        vo.setType(record.getType());
        vo.setAmount(record.getAmount());
        vo.setCategory(record.getCategory());
        vo.setProject(record.getProject());
        vo.setDate(record.getDate());
        vo.setRemark(record.getRemark());
        vo.setCreatedTime(record.getCreatedTime());
        return vo;
    }

    private IPage<FinanceRecycleBinVO> buildRecycleBinPage(
            int page,
            int size,
            List<FinanceRecord> deletedRecords,
            Long companyId
    ) {
        List<Long> targetIds = deletedRecords.stream()
                .map(FinanceRecord::getId)
                .toList();
        Map<Long, DeleteAuditMetadata> metadataMap = deleteAuditMetadataResolver.resolve("finance", companyId, targetIds);
        Map<Long, User> fallbackUserMap = deleteAuditMetadataResolver.loadUsers(
                deletedRecords.stream()
                        .map(FinanceRecord::getUpdatedBy)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );

        List<FinanceRecycleBinVO> recycleBinRecords = deletedRecords.stream()
                .map(record -> toRecycleBinVO(record, metadataMap.get(record.getId()), fallbackUserMap))
                .sorted(Comparator
                        .comparing(FinanceRecycleBinVO::getDeletedTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(FinanceRecycleBinVO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return paginate(page, size, recycleBinRecords);
    }

    private FinanceRecycleBinVO toRecycleBinVO(
            FinanceRecord record,
            DeleteAuditMetadata metadata,
            Map<Long, User> fallbackUserMap
    ) {
        FinanceRecycleBinVO vo = new FinanceRecycleBinVO();
        vo.setId(record.getId());
        vo.setType(record.getType());
        vo.setAmount(record.getAmount());
        vo.setCategory(record.getCategory());
        vo.setProject(record.getProject());
        vo.setDate(record.getDate());
        vo.setRemark(record.getRemark());
        vo.setCreatedTime(record.getCreatedTime());

        Long deletedByUserId = metadata != null && metadata.getDeletedByUserId() != null
                ? metadata.getDeletedByUserId()
                : record.getUpdatedBy();
        vo.setDeletedTime(metadata != null && metadata.getDeletedTime() != null
                ? metadata.getDeletedTime()
                : record.getUpdatedTime());
        vo.setDeletedByUserId(deletedByUserId);
        vo.setDeletedByName(metadata != null && StrUtil.isNotBlank(metadata.getDeletedByName())
                ? metadata.getDeletedByName()
                : deleteAuditMetadataResolver.resolveOperatorName(deletedByUserId, fallbackUserMap.get(deletedByUserId)));
        return vo;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要恢复的数据");
        }
        List<Long> normalizedIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            throw new BusinessException("请选择要恢复的数据");
        }
        return normalizedIds;
    }

    private <T> IPage<T> paginate(int page, int size, List<T> records) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        Page<T> result = new Page<>(safePage, safeSize);
        result.setTotal(records.size());
        if (records.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }

        int fromIndex = Math.min((safePage - 1) * safeSize, records.size());
        int toIndex = Math.min(fromIndex + safeSize, records.size());
        result.setRecords(records.subList(fromIndex, toIndex));
        return result;
    }

    private Map<String, Object> errorEntry(int row, String error) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("row", row);
        entry.put("error", error);
        return entry;
    }
}
