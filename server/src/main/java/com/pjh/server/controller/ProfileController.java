package com.pjh.server.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.pjh.server.common.Result;
import com.pjh.server.dto.ChangePasswordDTO;
import com.pjh.server.dto.UpdateCompanySettingsDTO;
import com.pjh.server.dto.UpdateProfileDTO;
import com.pjh.server.service.ProfileService;
import com.pjh.server.vo.ProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public Result<ProfileVO> getCurrentProfile() {
        return Result.success(profileService.getCurrentProfile());
    }

    @PutMapping("/me")
    public Result<ProfileVO> updateCurrentProfile(@RequestBody @Valid UpdateProfileDTO dto) {
        return Result.success("个人信息更新成功", profileService.updateCurrentProfile(dto));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordDTO dto) {
        profileService.changePassword(dto);
        return Result.success("密码修改成功", null);
    }

    @PutMapping("/company")
    @SaCheckRole("owner")
    public Result<ProfileVO> updateCurrentCompany(@RequestBody @Valid UpdateCompanySettingsDTO dto) {
        return Result.success("公司配置更新成功", profileService.updateCurrentCompany(dto));
    }
}
