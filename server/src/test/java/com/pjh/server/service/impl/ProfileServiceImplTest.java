package com.pjh.server.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import com.pjh.server.dashboard.HomeAiSummarySnapshotInvalidationPublisher;
import com.pjh.server.dto.ChangePasswordDTO;
import com.pjh.server.dto.UpdateCompanySettingsDTO;
import com.pjh.server.dto.UpdateProfileDTO;
import com.pjh.server.entity.Company;
import com.pjh.server.entity.User;
import com.pjh.server.exception.BusinessException;
import com.pjh.server.mapper.CompanyMapper;
import com.pjh.server.mapper.UserMapper;
import com.pjh.server.util.CurrentSessionService;
import com.pjh.server.vo.ProfileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private CompanyMapper companyMapper;

    @Mock
    private CurrentSessionService currentSessionService;

    @Mock
    private HomeAiSummarySnapshotInvalidationPublisher homeAiSummarySnapshotInvalidationPublisher;

    private ProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(
                userMapper,
                companyMapper,
                currentSessionService,
                homeAiSummarySnapshotInvalidationPublisher
        );
    }

    @Test
    void getCurrentProfileShouldReturnUserAndCompanyInfo() {
        User user = buildUser();
        Company company = buildCompany();
        when(currentSessionService.requireCurrentUserId()).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(companyMapper.selectById(9L)).thenReturn(company);

        ProfileVO result = profileService.getCurrentProfile();

        assertEquals("owner01", result.getUsername());
        assertEquals("张涵", result.getRealName());
        assertEquals("深圳XX贸易有限公司", result.getCompanyName());
        assertEquals("主营跨境贸易与供应链服务", result.getCompanyDescription());
    }

    @Test
    void updateCurrentProfileShouldTrimRealNameAndPersist() {
        User user = buildUser();
        when(currentSessionService.requireCurrentUserId()).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(companyMapper.selectById(9L)).thenReturn(buildCompany());

        UpdateProfileDTO dto = new UpdateProfileDTO();
        dto.setRealName("  新负责人  ");

        ProfileVO result = profileService.updateCurrentProfile(dto);

        assertEquals("新负责人", user.getRealName());
        assertEquals("新负责人", result.getRealName());
        verify(userMapper).updateById(user);
    }

    @Test
    void changePasswordShouldRejectWrongOldPassword() {
        User user = buildUser();
        user.setPassword(BCrypt.hashpw("OldPass123"));
        when(currentSessionService.requireCurrentUserId()).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(user);

        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setOldPassword("WrongPass123");
        dto.setNewPassword("NewPass123");

        BusinessException exception = assertThrows(BusinessException.class, () -> profileService.changePassword(dto));
        assertEquals("旧密码错误", exception.getMessage());
        verify(userMapper, never()).updateById(any());
    }

    @Test
    void changePasswordShouldValidateComplexityBeforePersisting() {
        User user = buildUser();
        user.setPassword(BCrypt.hashpw("OldPass123"));
        when(currentSessionService.requireCurrentUserId()).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(user);

        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setOldPassword("OldPass123");
        dto.setNewPassword("simple");

        BusinessException exception = assertThrows(BusinessException.class, () -> profileService.changePassword(dto));
        assertEquals("密码至少 8 位，且须包含大写字母、小写字母和数字", exception.getMessage());
        verify(userMapper, never()).updateById(any());
    }

    @Test
    void updateCurrentCompanyShouldRejectNonOwner() {
        when(currentSessionService.requireCurrentRole()).thenReturn("staff");

        UpdateCompanySettingsDTO dto = new UpdateCompanySettingsDTO();
        dto.setName("深圳XX贸易有限公司");

        BusinessException exception = assertThrows(BusinessException.class, () -> profileService.updateCurrentCompany(dto));
        assertEquals("只有企业负责人可修改公司配置", exception.getMessage());
        verify(companyMapper, never()).updateById(any());
    }

    @Test
    void updateCurrentCompanyShouldPersistNormalizedFields() {
        User user = buildUser();
        Company company = buildCompany();
        when(currentSessionService.requireCurrentRole()).thenReturn("owner");
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(7L);
        when(companyMapper.selectById(9L)).thenReturn(company);
        when(userMapper.selectById(7L)).thenReturn(user);

        UpdateCompanySettingsDTO dto = new UpdateCompanySettingsDTO();
        dto.setName("  新公司名称 ");
        dto.setIndustry("  智能制造 ");
        dto.setTaxpayerType("  一般纳税人  ");
        dto.setDescription("   ");

        ProfileVO result = profileService.updateCurrentCompany(dto);

        assertEquals("新公司名称", company.getName());
        assertEquals("智能制造", company.getIndustry());
        assertEquals("一般纳税人", company.getTaxpayerType());
        assertNull(company.getDescription());
        assertEquals("新公司名称", result.getCompanyName());
        verify(companyMapper).updateById(company);
        verify(homeAiSummarySnapshotInvalidationPublisher).publish(9L);
    }

    @Test
    void updateCurrentCompanyShouldAllowDescriptionOnlyUpdates() {
        User user = buildUser();
        Company company = buildCompany();
        when(currentSessionService.requireCurrentRole()).thenReturn("owner");
        when(currentSessionService.requireCurrentCompanyId()).thenReturn(9L);
        when(currentSessionService.requireCurrentUserId()).thenReturn(7L);
        when(companyMapper.selectById(9L)).thenReturn(company);
        when(userMapper.selectById(7L)).thenReturn(user);

        UpdateCompanySettingsDTO dto = new UpdateCompanySettingsDTO();
        dto.setDescription("  新的企业画像摘要  ");

        ProfileVO result = profileService.updateCurrentCompany(dto);

        assertEquals("深圳XX贸易有限公司", company.getName());
        assertEquals("新的企业画像摘要", company.getDescription());
        assertEquals("新的企业画像摘要", result.getCompanyDescription());
        verify(companyMapper).updateById(company);
        verify(homeAiSummarySnapshotInvalidationPublisher).publish(9L);
    }

    private User buildUser() {
        User user = new User();
        user.setId(7L);
        user.setCompanyId(9L);
        user.setUsername("owner01");
        user.setRealName("张涵");
        user.setRole("owner");
        user.setPassword(BCrypt.hashpw("OldPass123"));
        return user;
    }

    private Company buildCompany() {
        Company company = new Company();
        company.setId(9L);
        company.setName("深圳XX贸易有限公司");
        company.setCompanyCode("A1B2C3");
        company.setIndustry("跨境贸易");
        company.setTaxpayerType("一般纳税人");
        company.setDescription("主营跨境贸易与供应链服务");
        return company;
    }
}
