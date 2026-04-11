package com.pjh.server.service;

import com.pjh.server.dto.LoginDTO;
import com.pjh.server.dto.RegisterDTO;
import com.pjh.server.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    void register(RegisterDTO dto);
}
