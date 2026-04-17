package com.pjh.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.common.Result;
import com.pjh.server.dto.TaxUpsertDTO;
import com.pjh.server.vo.TaxRecordVO;
import com.pjh.server.vo.TaxRecycleBinVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TaxService {

    IPage<TaxRecordVO> listRecords(int page, int size, String taxType, Integer paymentStatus, String taxPeriod);

    IPage<TaxRecycleBinVO> listRecycleBinRecords(int page, int size);

    void createRecord(TaxUpsertDTO dto);

    void updateRecord(Long id, TaxUpsertDTO dto);

    void deleteRecord(Long id);

    void batchDelete(List<Long> ids);

    void restoreRecord(Long id);

    int batchRestore(List<Long> ids);

    Result<?> importExcel(MultipartFile file);

    void downloadTemplate(HttpServletResponse response);
}
