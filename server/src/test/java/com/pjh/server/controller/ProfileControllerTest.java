package com.pjh.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pjh.server.service.ProfileService;
import com.pjh.server.vo.ProfileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService profileService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService)).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getCurrentProfileShouldReturnWrappedProfileData() throws Exception {
        when(profileService.getCurrentProfile()).thenReturn(buildProfile());

        mockMvc.perform(get("/api/profile/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("owner01"))
                .andExpect(jsonPath("$.data.realName").value("张总"))
                .andExpect(jsonPath("$.data.companyName").value("深圳XX贸易有限公司"));

        verify(profileService).getCurrentProfile();
    }

    @Test
    void updateCurrentProfileShouldReturnUpdatedProfile() throws Exception {
        when(profileService.updateCurrentProfile(any())).thenReturn(buildProfile());

        mockMvc.perform(put("/api/profile/me")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("realName", "张总"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("个人信息更新成功"))
                .andExpect(jsonPath("$.data.realName").value("张总"));

        verify(profileService).updateCurrentProfile(any());
    }

    @Test
    void changePasswordShouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(put("/api/profile/password")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "oldPassword", "OldPass123",
                                "newPassword", "NewPass123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("密码修改成功"));

        verify(profileService).changePassword(any());
    }

    @Test
    void updateCurrentCompanyShouldReturnUpdatedProfile() throws Exception {
        when(profileService.updateCurrentCompany(any())).thenReturn(buildProfile());

        mockMvc.perform(put("/api/profile/company")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "深圳XX贸易有限公司",
                                "industry", "跨境贸易",
                                "taxpayerType", "一般纳税人",
                                "description", "主营跨境贸易与供应链服务"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("公司配置更新成功"))
                .andExpect(jsonPath("$.data.companyDescription").value("主营跨境贸易与供应链服务"));

        verify(profileService).updateCurrentCompany(any());
    }

    private ProfileVO buildProfile() {
        ProfileVO profile = new ProfileVO();
        profile.setId(1L);
        profile.setUsername("owner01");
        profile.setRealName("张总");
        profile.setRole("owner");
        profile.setCompanyName("深圳XX贸易有限公司");
        profile.setCompanyCode("A1B2C3");
        profile.setIndustry("跨境贸易");
        profile.setTaxpayerType("一般纳税人");
        profile.setCompanyDescription("主营跨境贸易与供应链服务");
        return profile;
    }
}
