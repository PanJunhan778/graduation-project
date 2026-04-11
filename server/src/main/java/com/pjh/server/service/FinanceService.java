package com.pjh.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.common.Result;
import com.pjh.server.dto.FinanceCreateDTO;
import com.pjh.server.vo.FinanceRecordVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface FinanceService {

    IPage<FinanceRecordVO> listRecords(int page, int size, String type, String category,
                                       LocalDate startDate, LocalDate endDate);

    void createRecord(FinanceCreateDTO dto);

    void updateRecord(Long id, FinanceCreateDTO dto);

    void deleteRecord(Long id);

    void batchDelete(List<Long> ids);

    Result<?> importExcel(MultipartFile file);

    void downloadTemplate(HttpServletResponse response);
}
