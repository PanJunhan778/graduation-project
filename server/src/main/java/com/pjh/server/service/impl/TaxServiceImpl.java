package com.pjh.server.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.audit.AuditOperationService;
import com.pjh.server.audit.AuditUpdate;
import com.pjh.server.audit.DeleteAuditMetadata;
import com.pjh.server.audit.DeleteAuditMetadataResolver;
import com.pjh.server.common.Result;
import com.pjh.server.dashboard.HomeAiSummarySnapshotInvalidationPublisher;
import com.pjh.server.dto.TaxUpsertDTO;
import com.pjh.server.entity.TaxRecord;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.TaxRecordMapper;
import com.pjh.server.service.TaxService;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.TaxRecordVO;
import com.pjh.server.vo.TaxRecycleBinVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaxServiceImpl implements TaxService {

    private static final Pattern TAX_PERIOD_PATTERN =
            Pattern.compile("^\\d{4}-(0[1-9]|1[0-2]|Q[1-4]|Annual)$");

    private static final Set<Integer> VALID_PAYMENT_STATUSES = Set.of(0, 1, 2);
    private static final String[] AUDIT_FIELDS = {
            "taxPeriod", "taxType", "declarationType", "taxAmount", "paymentStatus", "paymentDate", "remark"
    };

    private final TaxRecordMapper taxRecordMapper;
    private final AuditOperationService auditOperationService;
    private final HomeAiSummarySnapshotInvalidationPublisher homeAiSummarySnapshotInvalidationPublisher;
    private final DeleteAuditMetadataResolver deleteAuditMetadataResolver;
    private final CurrentSessionService currentSessionService;

    @Override
    public IPage<TaxRecordVO> listRecords(int page, int size, String taxType, Integer paymentStatus, String taxPeriod) {
        String normalizedTaxType = trimToNull(taxType);
        String normalizedTaxPeriod = trimToNull(taxPeriod);

        LambdaQueryWrapper<TaxRecord> wrapper = new LambdaQueryWrapper<TaxRecord>()
                .like(StrUtil.isNotBlank(normalizedTaxType), TaxRecord::getTaxType, normalizedTaxType)
                .eq(paymentStatus != null, TaxRecord::getPaymentStatus, paymentStatus)
                .likeRight(StrUtil.isNotBlank(normalizedTaxPeriod), TaxRecord::getTaxPeriod, normalizedTaxPeriod)
                .orderByDesc(TaxRecord::getUpdatedTime)
                .orderByDesc(TaxRecord::getId);

        IPage<TaxRecord> recordPage = taxRecordMapper.selectPage(new Page<>(page, size), wrapper);
        return recordPage.convert(this::toVO);
    }

    @Override
    public IPage<TaxRecycleBinVO> listRecycleBinRecords(int page, int size) {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        List<TaxRecord> deletedRecords = taxRecordMapper.selectDeletedByCompanyId(companyId);
        return buildRecycleBinPage(page, size, deletedRecords, companyId);
    }

    @Override
    @Transactional
    public void createRecord(TaxUpsertDTO dto) {
        TaxRecord record = new TaxRecord();
        applyUpsert(record, dto);
        taxRecordMapper.insert(record);
        auditOperationService.publishCreate("tax", record.getId(), record, AUDIT_FIELDS);
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    @AuditUpdate(
            module = "tax",
            fields = {"taxPeriod", "taxType", "declarationType", "taxAmount", "paymentStatus", "paymentDate", "remark"}
    )
    public void updateRecord(Long id, TaxUpsertDTO dto) {
        TaxRecord record = taxRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("税务记录不存在");
        }

        applyUpsert(record, dto);
        taxRecordMapper.updateById(record);
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    public void deleteRecord(Long id) {
        TaxRecord record = taxRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("税务记录不存在");
        }
        taxRecordMapper.deleteById(id);
        auditOperationService.publishDelete("tax", id, record, AUDIT_FIELDS);
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的数据");
        }
        List<TaxRecord> records = taxRecordMapper.selectBatchIds(ids);
        taxRecordMapper.deleteBatchIds(ids);
        records.forEach(record -> auditOperationService.publishDelete("tax", record.getId(), record, AUDIT_FIELDS));
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    public void restoreRecord(Long id) {
        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        TaxRecord deletedRecord = taxRecordMapper.selectDeletedById(companyId, id);
        if (deletedRecord == null) {
            throw new BusinessException("回收站中未找到该税务记录");
        }

        int affected = taxRecordMapper.restoreDeletedById(companyId, id, userId);
        if (affected <= 0) {
            throw new BusinessException("税务记录恢复失败");
        }

        auditOperationService.publishRestore("tax", id, deletedRecord, AUDIT_FIELDS);
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
    }

    @Override
    @Transactional
    public int batchRestore(List<Long> ids) {
        List<Long> normalizedIds = normalizeIds(ids);
        Long companyId = currentSessionService.requireCurrentCompanyId();
        Long userId = currentSessionService.requireCurrentUserId();
        List<TaxRecord> deletedRecords = taxRecordMapper.selectDeletedByIds(companyId, normalizedIds);
        if (deletedRecords.isEmpty()) {
            throw new BusinessException("未找到可恢复的税务记录");
        }

        List<Long> restorableIds = deletedRecords.stream()
                .map(TaxRecord::getId)
                .distinct()
                .toList();
        int affected = taxRecordMapper.restoreDeletedBatch(companyId, restorableIds, userId);
        if (affected <= 0) {
            throw new BusinessException("税务记录恢复失败");
        }

        deletedRecords.forEach(record -> auditOperationService.publishRestore("tax", record.getId(), record, AUDIT_FIELDS));
        homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
        return affected;
    }

    @Override
    @Transactional
    public Result<?> importExcel(MultipartFile file) {
        Result<?> result = TaxImportExcelHelper.importExcel(file, taxRecordMapper);
        if (result.getCode() == 200) {
            homeAiSummarySnapshotInvalidationPublisher.publishCurrentCompany();
        }
        return result;
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        TaxTemplateWorkbookHelper.downloadTemplate(response);
    }

    private void applyUpsert(TaxRecord record, TaxUpsertDTO dto) {
        validateUpsert(dto);

        record.setTaxPeriod(dto.getTaxPeriod().trim());
        record.setTaxType(dto.getTaxType().trim());
        record.setDeclarationType(trimToNull(dto.getDeclarationType()));
        record.setTaxAmount(dto.getTaxAmount());
        record.setPaymentStatus(dto.getPaymentStatus());
        record.setPaymentDate(dto.getPaymentStatus() == 1 ? dto.getPaymentDate() : null);
        record.setRemark(trimToNull(dto.getRemark()));
    }

    private void validateUpsert(TaxUpsertDTO dto) {
        if (dto == null) {
            throw new BusinessException("请求数据不能为空");
        }

        if (StrUtil.isBlank(dto.getTaxPeriod())) {
            throw new BusinessException("税款所属期不能为空");
        }
        if (!TAX_PERIOD_PATTERN.matcher(dto.getTaxPeriod().trim()).matches()) {
            throw new BusinessException("税款所属期格式不正确");
        }

        if (StrUtil.isBlank(dto.getTaxType())) {
            throw new BusinessException("税种不能为空");
        }

        if (dto.getTaxAmount() == null) {
            throw new BusinessException("税额不能为空");
        }

        if (dto.getPaymentStatus() == null || !VALID_PAYMENT_STATUSES.contains(dto.getPaymentStatus())) {
            throw new BusinessException("缴纳状态只能为待缴纳、已缴纳或免征");
        }

        if (dto.getPaymentStatus() == 1 && dto.getPaymentDate() == null) {
            throw new BusinessException("已缴纳状态必须填写缴纳日期");
        }
    }

    private TaxRecordVO toVO(TaxRecord record) {
        TaxRecordVO vo = new TaxRecordVO();
        vo.setId(record.getId());
        vo.setTaxPeriod(record.getTaxPeriod());
        vo.setTaxType(record.getTaxType());
        vo.setDeclarationType(record.getDeclarationType());
        vo.setTaxAmount(record.getTaxAmount());
        vo.setPaymentStatus(record.getPaymentStatus());
        vo.setPaymentDate(record.getPaymentDate());
        vo.setRemark(record.getRemark());
        vo.setCreatedTime(record.getCreatedTime());
        return vo;
    }

    private IPage<TaxRecycleBinVO> buildRecycleBinPage(
            int page,
            int size,
            List<TaxRecord> deletedRecords,
            Long companyId
    ) {
        List<Long> targetIds = deletedRecords.stream()
                .map(TaxRecord::getId)
                .toList();
        Map<Long, DeleteAuditMetadata> metadataMap = deleteAuditMetadataResolver.resolve("tax", companyId, targetIds);
        Map<Long, User> fallbackUserMap = deleteAuditMetadataResolver.loadUsers(
                deletedRecords.stream()
                        .map(TaxRecord::getUpdatedBy)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
        );

        List<TaxRecycleBinVO> recycleBinRecords = deletedRecords.stream()
                .map(record -> toRecycleBinVO(record, metadataMap.get(record.getId()), fallbackUserMap))
                .sorted(Comparator
                        .comparing(TaxRecycleBinVO::getDeletedTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TaxRecycleBinVO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return paginate(page, size, recycleBinRecords);
    }

    private TaxRecycleBinVO toRecycleBinVO(
            TaxRecord record,
            DeleteAuditMetadata metadata,
            Map<Long, User> fallbackUserMap
    ) {
        TaxRecycleBinVO vo = new TaxRecycleBinVO();
        vo.setId(record.getId());
        vo.setTaxPeriod(record.getTaxPeriod());
        vo.setTaxType(record.getTaxType());
        vo.setDeclarationType(record.getDeclarationType());
        vo.setTaxAmount(record.getTaxAmount());
        vo.setPaymentStatus(record.getPaymentStatus());
        vo.setPaymentDate(record.getPaymentDate());
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

    private String trimToNull(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
