package com.pjh.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.common.Result;
import com.pjh.server.dto.EmployeeUpsertDTO;
import com.pjh.server.vo.EmployeeVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface EmployeeService {

    IPage<EmployeeVO> listEmployees(int page, int size, String department, Integer status);

    void createEmployee(EmployeeUpsertDTO dto);

    void updateEmployee(Long id, EmployeeUpsertDTO dto);

    void deleteEmployee(Long id);

    void batchDelete(List<Long> ids);

    Result<?> importExcel(MultipartFile file);

    void downloadTemplate(HttpServletResponse response);
}
