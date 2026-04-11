package com.pjh.server.controller;

import com.pjh.server.common.Result;
import com.pjh.server.dto.LoginDTO;
import com.pjh.server.dto.RegisterDTO;
import com.pjh.server.service.AuthService;
import com.pjh.server.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        LoginVO vo = authService.login(dto);
        return Result.success("登录成功", vo);
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO dto) {
        authService.register(dto);
        return Result.success("注册成功", null);
    }
}
