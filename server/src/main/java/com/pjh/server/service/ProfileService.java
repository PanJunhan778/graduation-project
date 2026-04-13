package com.pjh.server.service;

import com.pjh.server.dto.ChangePasswordDTO;
import com.pjh.server.dto.UpdateCompanySettingsDTO;
import com.pjh.server.dto.UpdateProfileDTO;
import com.pjh.server.vo.ProfileVO;

public interface ProfileService {

    ProfileVO getCurrentProfile();

    ProfileVO updateCurrentProfile(UpdateProfileDTO dto);

    void changePassword(ChangePasswordDTO dto);

    ProfileVO updateCurrentCompany(UpdateCompanySettingsDTO dto);
}
