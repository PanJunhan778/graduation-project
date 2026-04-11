package com.pjh.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.dto.CompanyCreateDTO;
import com.pjh.server.dto.OwnerCreateDTO;
import com.pjh.server.vo.CompanyVO;

public interface AdminCompanyService {

    IPage<CompanyVO> listCompanies(int page, int size, String keyword);

    void createCompany(CompanyCreateDTO dto);

    void createOwner(Long companyId, OwnerCreateDTO dto);

    void updateCompanyStatus(Long id, Integer status);
}
