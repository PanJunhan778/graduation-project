package com.pjh.server.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import com.pjh.server.common.Constants;
import com.pjh.server.dashboard.HomeAiSummarySnapshotInvalidationPublisher;
import com.pjh.server.dto.ChangePasswordDTO;
import com.pjh.server.dto.UpdateCompanySettingsDTO;
import com.pjh.server.dto.UpdateProfileDTO;
import com.pjh.server.entity.Company;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.service.ProfileService;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.ProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final CurrentSessionService currentSessionService;
    private final HomeAiSummarySnapshotInvalidationPublisher homeAiSummarySnapshotInvalidationPublisher;

    @Override
    public ProfileVO getCurrentProfile() {
        User user = requireCurrentUser();
        return toProfileVO(user, loadCompany(user.getCompanyId()));
    }

    @Override
    @Transactional
    public ProfileVO updateCurrentProfile(UpdateProfileDTO dto) {
        User user = requireCurrentUser();
        user.setRealName(dto.getRealName().trim());
        userMapper.updateById(user);
        return toProfileVO(user, loadCompany(user.getCompanyId()));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordDTO dto) {
        User user = requireCurrentUser();
        if (!BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }
        if (!dto.getNewPassword().matches(Constants.PASSWORD_PATTERN)) {
            throw new BusinessException("密码至少 8 位，且须包含大写字母、小写字母和数字");
        }
        user.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public ProfileVO updateCurrentCompany(UpdateCompanySettingsDTO dto) {
        String role = currentSessionService.requireCurrentRole();
        if (!Constants.ROLE_OWNER.equals(role)) {
            throw new BusinessException(403, "只有企业负责人可修改公司配置");
        }

        Long companyId = currentSessionService.requireCurrentCompanyId();
        Company company = companyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException("当前公司不存在");
        }

        if (StringUtils.hasText(dto.getName())) {
            company.setName(dto.getName().trim());
        }
        if (dto.getIndustry() != null) {
            company.setIndustry(normalizeOptionalText(dto.getIndustry()));
        }
        if (dto.getTaxpayerType() != null) {
            company.setTaxpayerType(normalizeOptionalText(dto.getTaxpayerType()));
        }
        if (dto.getDescription() != null) {
            company.setDescription(normalizeOptionalText(dto.getDescription()));
        }
        companyMapper.updateById(company);
        homeAiSummarySnapshotInvalidationPublisher.publish(companyId);

        return toProfileVO(requireCurrentUser(), company);
    }

    private User requireCurrentUser() {
        Long userId = currentSessionService.requireCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("当前用户不存在");
        }
        return user;
    }

    private Company loadCompany(Long companyId) {
        if (companyId == null) {
            return null;
        }
        return companyMapper.selectById(companyId);
    }

    private ProfileVO toProfileVO(User user, Company company) {
        ProfileVO vo = new ProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());

        if (company != null) {
            vo.setCompanyName(company.getName());
            vo.setCompanyCode(company.getCompanyCode());
            vo.setIndustry(company.getIndustry());
            vo.setTaxpayerType(company.getTaxpayerType());
            vo.setCompanyDescription(company.getDescription());
        }
        return vo;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
