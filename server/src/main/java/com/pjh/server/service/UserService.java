package com.pjh.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pjh.server.dto.ResetPasswordDTO;
import com.pjh.server.dto.StaffCreateDTO;
import com.pjh.server.vo.UserVO;

public interface UserService {

    IPage<UserVO> listUsers(int page, int size, String keyword);

    void createStaff(StaffCreateDTO dto);

    void updateUserStatus(Long id, Integer status);

    void resetPassword(Long id, ResetPasswordDTO dto);
}
