package com.pjh.server.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pjh.server.common.Constants;
import com.pjh.server.dto.CompanyCreateDTO;
import com.pjh.server.dto.OwnerCreateDTO;
import com.pjh.server.entity.Company;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.service.AdminCompanyService;
import com.pjh.server.vo.CompanyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCompanyServiceImpl implements AdminCompanyService {

    private final CompanyMapper companyMapper;
    private final UserMapper userMapper;

    @Override
    public IPage<CompanyVO> listCompanies(int page, int size, String keyword) {
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<Company>()
                .like(keyword != null && !keyword.isBlank(), Company::getName, keyword)
                .orderByDesc(Company::getCreatedTime);

        IPage<Company> companyPage = companyMapper.selectPage(new Page<>(page, size), wrapper);

        return companyPage.convert(company -> {
            CompanyVO vo = new CompanyVO();
            vo.setId(company.getId());
            vo.setName(company.getName());
            vo.setCompanyCode(company.getCompanyCode());
            vo.setIndustry(company.getIndustry());
            vo.setTaxpayerType(company.getTaxpayerType());
            vo.setDescription(company.getDescription());
            vo.setStatus(company.getStatus());
            vo.setCreatedTime(company.getCreatedTime());

            User owner = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getCompanyId, company.getId())
                            .eq(User::getRole, Constants.ROLE_OWNER)
                            .last("LIMIT 1")
            );
            if (owner != null) {
                vo.setOwnerName(owner.getRealName());
                vo.setOwnerUsername(owner.getUsername());
            }
            return vo;
        });
    }

    @Override
    @Transactional
    public void createCompany(CompanyCreateDTO dto) {
        Long codeCount = companyMapper.selectCount(
                new LambdaQueryWrapper<Company>().eq(Company::getCompanyCode, dto.getCompanyCode())
        );
        if (codeCount > 0) {
            throw new BusinessException("企业码已存在，请使用其他编码");
        }

        Company company = new Company();
        company.setName(dto.getName());
        company.setCompanyCode(dto.getCompanyCode());
        company.setIndustry(dto.getIndustry());
        company.setTaxpayerType(dto.getTaxpayerType());
        company.setDescription(dto.getDescription());
        company.setStatus(1);
        companyMapper.insert(company);
    }

    @Override
    @Transactional
    public void createOwner(Long companyId, OwnerCreateDTO dto) {
        Company company = companyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException("公司不存在");
        }

        Long ownerCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getCompanyId, companyId)
                        .eq(User::getRole, Constants.ROLE_OWNER)
        );
        if (ownerCount > 0) {
            throw new BusinessException("该公司已有负责人账号");
        }

        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        );
        if (usernameCount > 0) {
            throw new BusinessException("用户名已存在");
        }

        if (!dto.getPassword().matches(Constants.PASSWORD_PATTERN)) {
            throw new BusinessException("密码至少 8 位，须包含大写字母、小写字母和数字");
        }

        User owner = new User();
        owner.setCompanyId(companyId);
        owner.setUsername(dto.getUsername());
        owner.setPassword(BCrypt.hashpw(dto.getPassword()));
        owner.setRole(Constants.ROLE_OWNER);
        owner.setRealName(dto.getRealName());
        owner.setStatus(1);
        userMapper.insert(owner);
    }

    @Override
    @Transactional
    public void updateCompanyStatus(Long id, Integer status) {
        Company company = companyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException("公司不存在");
        }

        if (status != 0 && status != 1) {
            throw new BusinessException("状态值无效");
        }

        company.setStatus(status);
        companyMapper.updateById(company);
    }
}
